import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import test from "node:test";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { createTrialStore, trialDocumentId, TRIAL_DURATION_MILLIS } from "./account-session.mjs";

const emulator = process.env.FIRESTORE_EMULATOR_HOST;
const localEmulator = typeof emulator === "string" &&
  /^(localhost|127\.0\.0\.1):[0-9]{1,5}$/.test(emulator);

test("concurrent devices create one origin; another process and expired trial reuse it", {
  skip: !localEmulator,
}, async () => {
  // A local-only emulator and separate fixture project prevent touching Kanako's cloud database.
  const app = initializeApp({ projectId: "demo-kanako-account-trials" }, randomUUID());
  const db = getFirestore(app);
  const uid = "trial-fixture-" + randomUUID();
  const reference = db.collection("accountTrials").doc(trialDocumentId(uid));
  const start = 1800000000000;
  let clock = start;
  try {
    const store = createTrialStore(db, () => clock);
    const before = await store.session(uid);
    assert.equal(before.trialStartedAtMillis, 0);
    assert.equal((await reference.get()).exists, false);
    const results = await Promise.all(Array.from({ length: 12 }, () => store.startTrial(uid)));
    for (const session of results) {
      assert.equal(session.trialStartedAtMillis, start);
      assert.equal(session.trialEndsAtMillis, start + TRIAL_DURATION_MILLIS);
      assert.deepEqual(session.paidAccess, { kind: "none" });
    }
    clock += TRIAL_DURATION_MILLIS + 1;
    const restored = await createTrialStore(db, () => clock).session(uid);
    assert.equal(restored.trialStartedAtMillis, start);
    assert.equal(restored.trialEndsAtMillis, start + TRIAL_DURATION_MILLIS);
    assert.equal(restored.serverNowMillis, clock);
    assert.equal((await reference.get()).data().startedAt.toMillis(), start);
  } finally {
    try { await reference.delete(); }
    finally { await db.terminate(); await deleteApp(app); }
  }
});
