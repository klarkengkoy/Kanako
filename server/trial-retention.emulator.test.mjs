import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import test from "node:test";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { createAccountDeletionService } from "./account-deletion.mjs";
import { createTrialStore, trialDocumentId, accountDeletionReference, TRIAL_DURATION_MILLIS } from "./account-session.mjs";
import { createTrialRetentionStore, trialRetentionUntil } from "./trial-retention.mjs";

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const localEmulator = typeof emulator === "string" &&
  /^(localhost|127\.0\.0\.1):[0-9]{1,5}$/.test(emulator);
const DAY = 24 * 60 * 60 * 1000;

async function fixture(action) {
  const app = initializeApp({ projectId: "demo-kanako-trial-retention" }, randomUUID());
  const db = getFirestore(app);
  const unique = randomUUID();
  const oldUid = "retention-old-" + unique;
  const newUid = "retention-new-" + unique;
  const otherUid = "retention-other-" + unique;
  const deleted = new Set();
  const state = { authFailure: false, authCalls: 0 };
  let now = Date.parse("2026-11-10T05:06:07.123Z");
  const trial = uid => db.collection("accountTrials").doc(trialDocumentId(uid));
  const auth = {
    async getUser(uid) {
      assert.ok([oldUid, newUid, otherUid].includes(uid));
      if (deleted.has(uid)) throw Object.assign(new Error("Deleted"), { code: "auth/user-not-found" });
      return { uid, disabled: false, emailVerified: true,
        providerData: [{ providerId: "google.com", uid: unique + (uid === otherUid ? "-other" : "-same") }] };
    },
    async deleteUser(uid) {
      state.authCalls++;
      assert.equal((await trial(uid).get()).exists, false);
      assert.equal((await db.collection("accountPurchases").doc(trialDocumentId(uid)).get()).exists, false);
      if (state.authFailure) throw new Error("Temporary Auth outage");
      deleted.add(uid);
    },
  };
  const retention = createTrialRetentionStore(db, auth, () => now);
  const history = await retention.referenceForAccount(oldUid);
  const otherHistory = await retention.referenceForAccount(otherUid);
  const trials = createTrialStore(db, () => now, retention);
  const service = createAccountDeletionService({
    db, auth, clock: () => now, prepareTrialRetention: retention.prepare,
    purchases: { async prepareForDeletion() { assert.fail("No purchase in this fixture"); } },
  });
  try {
    await action({ db, auth, state, oldUid, newUid, otherUid, history, trials, service, retention, trial,
      now: () => now, setNow: value => { now = value; }, advance: amount => { now += amount; } });
  } finally {
    try {
      const batch = db.batch().delete(history).delete(otherHistory);
      for (const uid of [oldUid, newUid, otherUid])
        batch.delete(trial(uid)).delete(accountDeletionReference(db, uid))
          .delete(db.collection("accountPurchases").doc(trialDocumentId(uid)));
      await batch.commit();
    } finally { await db.terminate(); await deleteApp(app); }
  }
}

test("recreated accounts retain only their original remaining or expired trial, across Firebase UIDs", {
  skip: !localEmulator,
}, async () => {
  for (const elapsed of [3 * DAY, 8 * DAY]) await fixture(async f => {
    const original = await f.trials.startTrial(f.oldUid);
    f.advance(elapsed);
    const requested = f.now();
    await f.service.deleteAccount(f.oldUid);
    const stored = (await f.history.get()).data();
    assert.deepEqual(Object.keys(stored).sort(), ["endsAt", "expiresAt", "startedAt", "version"]);
    assert.equal(stored.startedAt.toMillis(), original.trialStartedAtMillis);
    assert.equal(stored.endsAt.toMillis(), original.trialEndsAtMillis);
    assert.equal(stored.expiresAt.toMillis(), trialRetentionUntil(requested));
    const recreated = await f.trials.session(f.newUid);
    assert.equal(recreated.trialStartedAtMillis, original.trialStartedAtMillis);
    assert.equal(recreated.trialEndsAtMillis, original.trialEndsAtMillis);
    assert.equal(recreated.trialEndsAtMillis > f.now(), elapsed < TRIAL_DURATION_MILLIS);
    const recreatedRecord = f.db.collection("accountTrials").doc(trialDocumentId(f.newUid));
    assert.equal((await recreatedRecord.get()).exists, false);
    const repeatedStart = await f.trials.startTrial(f.newUid);
    assert.equal(repeatedStart.trialStartedAtMillis, original.trialStartedAtMillis);
    assert.equal(repeatedStart.trialEndsAtMillis, original.trialEndsAtMillis);
    const other = await f.trials.startTrial(f.otherUid);
    assert.equal(other.trialStartedAtMillis, f.now());
    assert.equal(other.trialEndsAtMillis, f.now() + TRIAL_DURATION_MILLIS);
  });
});

test("failed deletion retries and account recreation never extend the retained history deadline", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  await f.trials.startTrial(f.oldUid);
  f.advance(8 * DAY);
  f.state.authFailure = true;
  await assert.rejects(f.service.deleteAccount(f.oldUid));
  const expiry = (await f.history.get()).data().expiresAt.toMillis();
  f.advance(DAY);
  f.state.authFailure = false;
  await f.service.retryPendingDeletions();
  assert.equal((await f.history.get()).data().expiresAt.toMillis(), expiry);
  await f.trials.session(f.newUid);
  f.advance(DAY);
  await f.service.deleteAccount(f.newUid);
  assert.equal((await f.history.get()).data().expiresAt.toMillis(), expiry);
  assert.equal(f.state.authCalls, 3);
}));

test("expired stored history allows a fresh trial and stale cleanup cannot erase its replacement", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  await f.trials.startTrial(f.oldUid);
  f.advance(8 * DAY);
  await f.service.deleteAccount(f.oldUid);
  const oldExpiry = (await f.history.get()).data().expiresAt.toMillis();
  f.setNow(oldExpiry);
  const fresh = await f.trials.startTrial(f.newUid);
  assert.equal(fresh.trialStartedAtMillis, oldExpiry);
  assert.equal(fresh.trialEndsAtMillis, oldExpiry + TRIAL_DURATION_MILLIS);
  assert.equal((await f.history.get()).exists, true);
  let replaced = false;
  const racingDb = {
    collection: name => f.db.collection(name),
    async runTransaction(action) {
      if (!replaced) {
        replaced = true;
        // The purge query has returned the old expired version; replace it before its transaction.
        await f.service.deleteAccount(f.newUid);
      }
      return f.db.runTransaction(action);
    },
  };
  await createTrialRetentionStore(racingDb, f.auth, f.now).purgeExpired();
  const replacement = (await f.history.get()).data();
  assert.equal(replacement.startedAt.toMillis(), oldExpiry);
  assert.equal(replacement.expiresAt.toMillis(), trialRetentionUntil(oldExpiry));
  f.setNow(replacement.expiresAt.toMillis());
  await f.retention.purgeExpired();
  assert.equal((await f.history.get()).exists, false);
}));

test("deleting an account that never started a trial creates no retained history", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  await f.service.deleteAccount(f.oldUid);
  assert.equal((await f.history.get()).exists, false);
  assert.equal(f.state.authCalls, 1);
}));

test("corrupt history blocks trial recreation and deletion before a marker or Auth removal", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  await f.trials.startTrial(f.oldUid);
  await f.history.set({ version: 1, email: "unexpected@example.com" });
  await assert.rejects(f.trials.session(f.newUid), error => error.status === 503);
  assert.equal((await f.trial(f.newUid).get()).exists, false);
  await assert.rejects(f.service.deleteAccount(f.oldUid), error => error.status === 503);
  assert.equal((await accountDeletionReference(f.db, f.oldUid).get()).exists, false);
  assert.equal((await f.trial(f.oldUid).get()).exists, true);
  assert.equal(f.state.authCalls, 0);
  const other = await f.trials.startTrial(f.otherUid);
  assert.equal(other.trialStartedAtMillis, f.now());
}));
