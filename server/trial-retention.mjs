import { createHash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { AccountServiceError, readTrial, readTrialDates } from "./account-session.mjs";
import { googleSubjectForAccount } from "./account-identity.mjs";

function requireValue(condition) {
  if (!condition) throw new AccountServiceError(503, "invalid_trial_history");
}

export function trialRetentionUntil(requestedAtMillis) {
  requireValue(Number.isSafeInteger(requestedAtMillis) && requestedAtMillis > 0);
  const expiry = new Date(requestedAtMillis);
  const month = expiry.getUTCMonth();
  expiry.setUTCFullYear(expiry.getUTCFullYear() + 1);
  // February 29 expires on February 28 in the following non-leap year.
  if (expiry.getUTCMonth() !== month) expiry.setUTCDate(0);
  const result = expiry.getTime();
  requireValue(Number.isSafeInteger(result) && result > requestedAtMillis && result <= 253402300799999);
  return result;
}

export function readRetainedTrial(snapshot, nowMillis) {
  if (!snapshot.exists) return null;
  const data = snapshot.data();
  requireValue(data?.version === 1 && /^[a-f0-9]{64}$/.test(snapshot.id) &&
    Object.keys(data).length === 4 && Object.hasOwn(data, "startedAt") &&
    Object.hasOwn(data, "endsAt") && data.expiresAt instanceof Timestamp);
  const dates = readTrialDates(data, nowMillis);
  const expiry = data.expiresAt.toMillis();
  // A small validation allowance tolerates server clock skew; the written deadline never adds it.
  requireValue(Number.isSafeInteger(expiry) && expiry > dates.trialEndsAtMillis &&
    expiry <= trialRetentionUntil(nowMillis + 60_000));
  return expiry > nowMillis ? dates : null;
}

export function createTrialRetentionStore(db, auth, clock = Date.now) {
  const history = db.collection("trialHistory");

  async function referenceForAccount(uid) {
    const subject = await googleSubjectForAccount(auth, uid);
    const id = createHash("sha256")
      .update("com.smyddevstudio.kanako/trial-history/v1/" + subject, "utf8").digest("hex");
    return history.doc(id);
  }

  async function prepare(uid) {
    // Resolve the provider before entering a retryable transaction; never store the raw subject.
    const reference = await referenceForAccount(uid);
    return async (transaction, trialSnapshot, nowMillis) => {
      const previous = await transaction.get(reference);
      const retained = readRetainedTrial(previous, nowMillis);
      if (!trialSnapshot.exists) return; // Signing in without starting a trial creates no history.
      const dates = readTrial(trialSnapshot.data(), uid, nowMillis);
      if (retained) {
        requireValue(retained.trialStartedAtMillis === dates.trialStartedAtMillis &&
          retained.trialEndsAtMillis === dates.trialEndsAtMillis);
        return; // Recreated accounts and retries never roll an existing deadline forward.
      }
      transaction.set(reference, {
        version: 1,
        startedAt: Timestamp.fromMillis(dates.trialStartedAtMillis),
        endsAt: Timestamp.fromMillis(dates.trialEndsAtMillis),
        expiresAt: Timestamp.fromMillis(trialRetentionUntil(nowMillis)),
      });
    };
  }

  async function purgeExpired() {
    const nowMillis = clock();
    requireValue(Number.isSafeInteger(nowMillis) && nowMillis > 0);
    // Bounded work with a fresh transactional read: never erase a renewed record from a stale query.
    for (let pass = 0; pass < 5; pass++) {
      const expired = await history.where("expiresAt", "<=", Timestamp.fromMillis(nowMillis))
        .limit(100).get();
      if (expired.empty) return;
      await db.runTransaction(async transaction => {
        const current = await transaction.getAll(...expired.docs.map(snapshot => snapshot.ref));
        for (const snapshot of current) {
          if (!snapshot.exists) continue;
          const expiry = snapshot.data().expiresAt;
          requireValue(expiry instanceof Timestamp);
          if (expiry.toMillis() > nowMillis) continue;
          readRetainedTrial(snapshot, nowMillis);
          transaction.delete(snapshot.ref);
        }
      });
    }
  }

  return { referenceForAccount, read: readRetainedTrial, prepare, purgeExpired };
}
