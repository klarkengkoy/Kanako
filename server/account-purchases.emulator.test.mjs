import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import test from "node:test";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { createPurchaseStore, purchaseDocumentId } from "./account-purchases.mjs";
import { trialDocumentId } from "./account-session.mjs";
import { billingCatalog as catalog } from "./billing-catalog.mjs";

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const localEmulator = typeof emulator === "string" &&
  /^(localhost|127\.0\.0\.1):[0-9]{1,5}$/.test(emulator);

async function fixture(action) {
  const app = initializeApp({ projectId: "demo-kanako-account-purchases" }, randomUUID());
  const db = getFirestore(app);
  const uid = "purchase-owner-" + randomUUID();
  const other = "purchase-other-" + randomUUID();
  const token = "purchase-token-" + randomUUID();
  const replacement = "purchase-replacement-" + randomUUID();
  const now = 1800000000000;
  const state = {
    productId: catalog.subscriptionId, kind: "subscription", plan: "monthly",
    expiresAtMillis: now + 60000, acknowledgementRequired: true, linkedPurchaseToken: null,
  };
  let lookups = 0;
  let acknowledgementFailure = false;
  const play = {
    async verifySubscription() { lookups++; return { ...state }; },
    async verifyLifetime() { lookups++; return { ...state }; },
    async acknowledgeDeliveredPurchase(value) {
      const stored = await db.collection("playPurchases").doc(purchaseDocumentId(value)).get();
      assert.equal(stored.data().observedKind, state.kind);
      assert.equal(stored.data().acknowledgementPending, true);
      if (acknowledgementFailure) throw new Error("Temporary Google failure");
    },
  };
  const store = createPurchaseStore(db, play, catalog, () => now);
  try {
    await action({
      db, store, uid, other, token, replacement, state,
      get lookups() { return lookups; },
      failAcknowledgement(value) { acknowledgementFailure = value; },
    });
  } finally {
    try {
      await db.batch()
        .delete(db.collection("playPurchases").doc(purchaseDocumentId(token)))
        .delete(db.collection("playPurchases").doc(purchaseDocumentId(replacement)))
        .delete(db.collection("accountPurchases").doc(trialDocumentId(uid)))
        .delete(db.collection("accountPurchases").doc(trialDocumentId(other)))
        .commit();
    } finally { await db.terminate(); await deleteApp(app); }
  }
}

test("ownership persists before acknowledgement and cannot transfer to another account", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  const proof = await f.store.claim(f.uid, f.token, catalog.subscriptionId);
  const previousLookups = f.lookups;
  await assert.rejects(f.store.claim(f.other, f.token, catalog.subscriptionId),
    error => error.status === 409);
  assert.equal(f.lookups, previousLookups);
  const confirmed = new Map([[purchaseDocumentId(f.token), proof]]);
  assert.deepEqual(await f.store.currentAccess(f.uid, confirmed), {
    kind: "subscription", plan: "monthly", expiresAtMillis: f.state.expiresAtMillis,
  });
  assert.equal(f.lookups, previousLookups);
  f.state.kind = "none";
  f.state.acknowledgementRequired = false;
  await f.db.collection("playPurchases").doc(purchaseDocumentId(f.token)).update({
    verificationId: randomUUID(), observedKind: "none",
  });
  assert.deepEqual(await f.store.currentAccess(f.uid, confirmed), { kind: "none" });
  assert.equal(f.lookups, previousLookups + 1);
}));

test("replacement retires the old token; a linked token cannot cross accounts", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  await f.store.claim(f.uid, f.token, catalog.subscriptionId);
  f.state.linkedPurchaseToken = f.token;
  await assert.rejects(f.store.claim(f.other, f.replacement, catalog.subscriptionId),
    error => error.status === 409);
  await f.store.claim(f.uid, f.replacement, catalog.subscriptionId);
  assert.deepEqual(await f.store.claim(f.uid, f.token, catalog.subscriptionId),
    { productId: catalog.subscriptionId, kind: "none" });
  const index = await f.db.collection("accountPurchases").doc(trialDocumentId(f.uid)).get();
  assert.deepEqual(index.data().tokens, [purchaseDocumentId(f.replacement)]);
}));

test("failed acknowledgement keeps a retry marker; retry clears it after durable delivery", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  f.failAcknowledgement(true);
  await assert.rejects(f.store.claim(f.uid, f.token, catalog.subscriptionId));
  const reference = f.db.collection("playPurchases").doc(purchaseDocumentId(f.token));
  assert.equal((await reference.get()).data().acknowledgementPending, true);
  f.failAcknowledgement(false);
  await f.store.retryAcknowledgements();
  assert.equal((await reference.get()).data().acknowledgementPending, false);
}));

test("an older verification cannot overwrite a newer record", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  await f.store.claim(f.uid, f.token, catalog.subscriptionId);
  const reference = f.db.collection("playPurchases").doc(purchaseDocumentId(f.token));
  const racingPlay = {
    async verifySubscription() {
      await reference.update({ observedKind: "none", verificationId: randomUUID() });
      return { ...f.state, acknowledgementRequired: false };
    },
  };
  const racingStore = createPurchaseStore(f.db, racingPlay, catalog, () => 1800000000000);
  await assert.rejects(racingStore.claim(f.uid, f.token, catalog.subscriptionId),
    error => error.status === 503 && error.code === "purchase_state_changed");
  assert.equal((await reference.get()).data().observedKind, "none");
}));

test("an older acknowledgement cannot clear a newer verification's retry marker", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  const reference = f.db.collection("playPurchases").doc(purchaseDocumentId(f.token));
  const newerId = randomUUID();
  const racingStore = createPurchaseStore(f.db, {
    async verifySubscription() { return { ...f.state }; },
    async acknowledgeDeliveredPurchase() {
      await reference.update({ verificationId: newerId, acknowledgementPending: true });
    },
  }, catalog, () => 1800000000000);
  await racingStore.claim(f.uid, f.token, catalog.subscriptionId);
  const current = (await reference.get()).data();
  assert.equal(current.verificationId, newerId);
  assert.equal(current.acknowledgementPending, true);
}));

test("Lifetime is a non-consumable purchase and a later revoked state removes access", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  f.state.productId = catalog.lifetimeId;
  f.state.kind = "lifetime";
  f.state.plan = "lifetime";
  await f.store.claim(f.uid, f.token, catalog.lifetimeId);
  assert.deepEqual(await f.store.currentAccess(f.uid), { kind: "lifetime", plan: "lifetime" });
  f.state.kind = "none";
  f.state.acknowledgementRequired = false;
  assert.deepEqual(await f.store.currentAccess(f.uid), { kind: "none" });
}));

test("unverified new purchases create no ownership or account index", {
  skip: !localEmulator,
}, async () => fixture(async f => {
  f.state.kind = "none";
  f.state.acknowledgementRequired = false;
  await f.store.claim(f.uid, f.token, catalog.subscriptionId);
  assert.equal((await f.db.collection("playPurchases")
    .doc(purchaseDocumentId(f.token)).get()).exists, false);
  assert.equal((await f.db.collection("accountPurchases")
    .doc(trialDocumentId(f.uid)).get()).exists, false);
}));
