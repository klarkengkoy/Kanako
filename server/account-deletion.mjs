import { Timestamp } from "firebase-admin/firestore";
import {
  AccountServiceError, accountDeletionReference, trialDocumentId,
} from "./account-session.mjs";

// Covers stale invocations after Auth deletion. Expiry is enforced by cleanup, not a grant.
export const DELETION_FENCE_MILLIS = 24 * 60 * 60 * 1000;
const PURCHASE_BATCH_SIZE = 100;

function requireValue(condition) {
  if (!condition) throw new AccountServiceError(503, "invalid_deletion_record");
}

function readJob(snapshot, uid) {
  requireValue(snapshot.exists);
  const data = snapshot.data();
  requireValue(data.version === 1 && data.requestedAt instanceof Timestamp &&
    (data.status === "pending" || data.status === "complete"));
  if (data.status === "pending") requireValue(data.userId === uid);
  else requireValue(data.completedAt instanceof Timestamp && data.purgeAfter instanceof Timestamp);
  return data;
}

export function createAccountDeletionService({
  db, auth, purchases, prepareTrialRetention, clock = Date.now,
}) {
  async function begin(uid) {
    const reference = accountDeletionReference(db, uid);
    const existing = await reference.get();
    if (existing.exists) { readJob(existing, uid); return; }
    // Fail closed if a future caller omits the approved trial-retention policy.
    if (typeof prepareTrialRetention !== "function")
      throw new AccountServiceError(503, "deletion_policy_not_configured");
    const retainTrial = await prepareTrialRetention(uid);
    requireValue(typeof retainTrial === "function");
    const trial = db.collection("accountTrials").doc(trialDocumentId(uid));
    await db.runTransaction(async transaction => {
      const [job, trialSnapshot] = await transaction.getAll(reference, trial);
      if (job.exists) { readJob(job, uid); return; }
      const now = clock();
      requireValue(Number.isSafeInteger(now) && now > 0);
      // The policy hook must read before writing; all trial preservation is atomic with the fence.
      await retainTrial(transaction, trialSnapshot, now);
      transaction.create(reference, {
        version: 1, userId: uid, status: "pending", requestedAt: Timestamp.fromMillis(now),
      });
    });
  }

  async function erasePurchaseBatch(uid) {
    // Query by owner, not just the active-token index: retired/replaced records must also disappear.
    const owned = await db.collection("playPurchases").where("userId", "==", uid)
      .limit(PURCHASE_BATCH_SIZE).get();
    if (owned.empty) return false;
    for (let offset = 0; offset < owned.docs.length; offset += 10) {
      await Promise.all(owned.docs.slice(offset, offset + 10).map(snapshot =>
        purchases.prepareForDeletion(uid, snapshot)));
    }
    await db.runTransaction(async transaction => {
      const reference = accountDeletionReference(db, uid);
      const snapshots = await transaction.getAll(reference, ...owned.docs.map(value => value.ref));
      readJob(snapshots[0], uid);
      for (const snapshot of snapshots.slice(1)) {
        if (!snapshot.exists) continue;
        requireValue(snapshot.data().userId === uid);
        transaction.delete(snapshot.ref);
      }
    });
    return true;
  }

  async function complete(uid) {
    const reference = accountDeletionReference(db, uid);
    const initial = await reference.get();
    if (readJob(initial, uid).status === "complete") return { deleted: true };
    // Keep each invocation bounded. The persistent job resumes if interrupted or too large.
    for (let batch = 0; batch < 4; batch++) {
      if (!await erasePurchaseBatch(uid)) break;
    }
    const remaining = await db.collection("playPurchases").where("userId", "==", uid).limit(1).get();
    if (!remaining.empty) throw new AccountServiceError(503, "account_deletion_pending");
    await db.runTransaction(async transaction => {
      const job = await transaction.get(reference);
      readJob(job, uid);
      transaction.delete(db.collection("accountTrials").doc(trialDocumentId(uid)));
      transaction.delete(db.collection("accountPurchases").doc(trialDocumentId(uid)));
    });
    // Auth is last: a database outage must not orphan data behind an already-deleted identity.
    try { await auth.deleteUser(uid); }
    catch (error) { if (error?.code !== "auth/user-not-found") throw error; }
    await db.runTransaction(async transaction => {
      const snapshot = await transaction.get(reference);
      const job = readJob(snapshot, uid);
      if (job.status === "complete") return;
      const now = clock();
      requireValue(Number.isSafeInteger(now) && now > 0 && now >= job.requestedAt.toMillis());
      // Drop the raw UID after completion; the short-lived document key remains a deletion fence.
      transaction.set(reference, {
        version: 1, status: "complete", requestedAt: job.requestedAt,
        completedAt: Timestamp.fromMillis(now),
        purgeAfter: Timestamp.fromMillis(now + DELETION_FENCE_MILLIS),
      });
    });
    return { deleted: true };
  }

  async function deleteAccount(uid) {
    await begin(uid);
    return complete(uid);
  }

  async function retryPendingDeletions() {
    const pending = await db.collection("accountDeletions").where("status", "==", "pending").limit(20).get();
    let failed = false;
    for (let offset = 0; offset < pending.docs.length; offset += 4) {
      const results = await Promise.allSettled(pending.docs.slice(offset, offset + 4).map(async snapshot => {
        const uid = snapshot.data().userId;
        requireValue(trialDocumentId(uid) === snapshot.id);
        return complete(uid);
      }));
      failed ||= results.some(value => value.status === "rejected");
    }
    if (failed) throw new AccountServiceError(503, "account_deletion_retry_needed");
  }

  async function purgeCompletedDeletions() {
    const now = clock();
    const expired = await db.collection("accountDeletions")
      .where("purgeAfter", "<=", Timestamp.fromMillis(now)).limit(100).get();
    if (expired.empty) return;
    const batch = db.batch();
    for (const snapshot of expired.docs) {
      const job = readJob(snapshot);
      requireValue(job.status === "complete" && job.purgeAfter.toMillis() <= now);
      batch.delete(snapshot.ref);
    }
    await batch.commit();
  }

  return { deleteAccount, retryPendingDeletions, purgeCompletedDeletions };
}
