import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import test from "node:test";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { createAccountDeletionService, DELETION_FENCE_MILLIS } from "./account-deletion.mjs";
import { createTrialStore, trialDocumentId, accountDeletionReference } from "./account-session.mjs";
import { createPurchaseStore, purchaseDocumentId } from "./account-purchases.mjs";
import { billingCatalog as catalog } from "./billing-catalog.mjs";

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const localEmulator = typeof emulator === "string" &&
  /^(localhost|127\.0\.0\.1):[0-9]{1,5}$/.test(emulator);

async function fixture(action) {
  const app = initializeApp({ projectId: "demo-kanako-account-deletion" }, randomUUID());
  const db = getFirestore(app);
  const uid = "deletion-owner-" + randomUUID();
  const token = "deletion-token-" + randomUUID();
  const staleToken = "deletion-stale-" + randomUUID();
  let now = 1800000000000;
  const trial = db.collection("accountTrials").doc(trialDocumentId(uid));
  const index = db.collection("accountPurchases").doc(trialDocumentId(uid));
  const job = accountDeletionReference(db, uid);
  const state = { authFailure: false, authCalls: 0, acknowledgementFailure: false,
    acknowledgements: 0, acknowledgementRequired: false, lookups: 0, wait: null };
  const play = {
    async verifyLifetime() {
      state.lookups++;
      if (state.wait) {
        const wait = state.wait;
        state.wait = null;
        wait.enter();
        await wait.promise;
      }
      return { productId: catalog.lifetimeId, kind: "lifetime", plan: "lifetime",
        acknowledgementRequired: state.acknowledgementRequired, linkedPurchaseToken: null };
    },
    async acknowledgeDeliveredPurchase() {
      state.acknowledgements++;
      if (state.acknowledgementFailure) throw new Error("Temporary Google outage");
    },
  };
  const purchases = createPurchaseStore(db, play, catalog, () => now);
  const trials = createTrialStore(db, () => now);
  const auth = { async deleteUser(value) {
    assert.equal(value, uid);
    state.authCalls++;
    assert.equal((await trial.get()).exists, false);
    assert.equal((await index.get()).exists, false);
    assert.equal((await db.collection("playPurchases").where("userId", "==", uid).get()).empty, true);
    if (state.authFailure) throw new Error("Temporary Auth outage");
  } };
  // Explicit no-retention fixture policy; this does not configure Kanako's live policy.
  const options = { db, auth, purchases, clock: () => now,
    prepareTrialRetention: async () => async () => {} };
  const service = createAccountDeletionService(options);
  try {
    await trials.startTrial(uid);
    await purchases.claim(uid, token, catalog.lifetimeId);
    await action({ db, uid, token, staleToken, state, trial, index, job, purchases, trials,
      service, options, advance: amount => { now += amount; } });
  } finally {
    try {
      const owned = await db.collection("playPurchases").where("userId", "==", uid).get();
      const cleanup = db.batch().delete(trial).delete(index).delete(job);
      for (const snapshot of owned.docs) cleanup.delete(snapshot.ref);
      cleanup.delete(db.collection("playPurchases").doc(purchaseDocumentId(staleToken)));
      await cleanup.commit();
    } finally { await db.terminate(); await deleteApp(app); }
  }
}

test("deletion removes active and retired purchase data before Auth and fences later requests", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  const active = await f.db.collection("playPurchases").doc(purchaseDocumentId(f.token)).get();
  const old = f.db.batch();
  for (let i = 0; i < 101; i++) {
    const token = f.token + "-retired-" + i;
    old.set(f.db.collection("playPurchases").doc(purchaseDocumentId(token)), {
      ...active.data(), token, supersededBy: active.id,
    });
  }
  await old.commit();
  assert.deepEqual(await f.service.deleteAccount(f.uid), { deleted: true });
  assert.equal(f.state.authCalls, 1);
  const record = (await f.job.get()).data();
  assert.equal(record.status, "complete");
  assert.equal(Object.hasOwn(record, "userId"), false);
  assert.equal(record.purgeAfter.toMillis() - record.completedAt.toMillis(), DELETION_FENCE_MILLIS);
  await assert.rejects(f.trials.session(f.uid), error => error.status === 410);
  await assert.rejects(f.purchases.claim(f.uid, f.token, catalog.lifetimeId), error => error.status === 410);
  await assert.rejects(f.purchases.currentAccess(f.uid), error => error.status === 410);
  assert.equal((await f.trial.get()).exists, false);
  assert.deepEqual(await f.service.deleteAccount(f.uid), { deleted: true });
  assert.equal(f.state.authCalls, 1);
  f.advance(DELETION_FENCE_MILLIS - 1);
  await f.service.purgeCompletedDeletions();
  assert.equal((await f.job.get()).exists, true);
  f.advance(1);
  await f.service.purgeCompletedDeletions();
  assert.equal((await f.job.get()).exists, false);
}));

test("Auth failure preserves a resumable job; the scheduled retry finishes without re-creating data", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  f.state.authFailure = true;
  await assert.rejects(f.service.deleteAccount(f.uid));
  assert.equal((await f.job.get()).data().status, "pending");
  assert.equal((await f.job.get()).data().userId, f.uid);
  await assert.rejects(f.trials.session(f.uid), error => error.status === 410);
  f.state.authFailure = false;
  await f.service.retryPendingDeletions();
  assert.equal((await f.job.get()).data().status, "complete");
  assert.equal((await f.trial.get()).exists, false);
  assert.equal(f.state.authCalls, 2);
}));

test("deletion does not discard an outstanding purchase acknowledgement when Google is unavailable", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  const purchase = f.db.collection("playPurchases").doc(purchaseDocumentId(f.token));
  await purchase.update({ acknowledgementPending: true });
  f.state.acknowledgementRequired = true;
  f.state.acknowledgementFailure = true;
  await assert.rejects(f.service.deleteAccount(f.uid));
  assert.equal((await purchase.get()).exists, true);
  assert.equal(f.state.authCalls, 0);
  f.state.acknowledgementFailure = false;
  await f.service.retryPendingDeletions();
  assert.equal((await purchase.get()).exists, false);
  assert.equal(f.state.authCalls, 1);
  assert.equal(f.state.acknowledgements, 2);
}));

test("a claim awaiting Google cannot recreate a purchase after account deletion", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  let entered;
  let release;
  const waiting = new Promise(resolve => { entered = resolve; });
  const promise = new Promise(resolve => { release = resolve; });
  f.state.wait = { enter: entered, promise };
  const outcome = f.purchases.claim(f.uid, f.staleToken, catalog.lifetimeId)
    .then(value => ({ value }), error => ({ error }));
  try {
    await waiting;
    await f.service.deleteAccount(f.uid);
  } finally { release(); }
  const result = await outcome;
  assert.equal(result.error?.status, 410);
  assert.equal((await f.db.collection("playPurchases").doc(purchaseDocumentId(f.staleToken)).get()).exists, false);
  assert.equal((await f.index.get()).exists, false);
}));

test("a missing or failed retention policy cannot start destructive work", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  for (const prepareTrialRetention of [undefined, async () => async () => {
    throw new Error("Trial retention unavailable");
  }]) {
    const service = createAccountDeletionService({ ...f.options, prepareTrialRetention });
    await assert.rejects(service.deleteAccount(f.uid));
    assert.equal((await f.job.get()).exists, false);
    assert.equal((await f.trial.get()).exists, true);
    assert.equal((await f.index.get()).exists, true);
    assert.equal(f.state.authCalls, 0);
  }
}));
