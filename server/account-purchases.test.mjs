import assert from "node:assert/strict";
import test from "node:test";
import {
  combinedAccess, createPaidAccountService, requirePurchaseBody,
} from "./account-purchases.mjs";
import { createPlayNotificationHandler } from "./play-notifications.mjs";

test("only a purchase token and one product hint are accepted", () => {
  assert.doesNotThrow(() => requirePurchaseBody({ purchaseToken: "token", productIds: ["product"] }));
  for (const body of [
    null, [], {}, { purchaseToken: "", productIds: ["product"] },
    { purchaseToken: "x".repeat(4097), productIds: ["product"] },
    { purchaseToken: "token", productIds: [] },
    { purchaseToken: "token", productIds: ["first", "second"] },
    { purchaseToken: "token", productIds: ["product"], userId: "other" },
    { purchaseToken: "token", productIds: ["product"], kind: "lifetime" },
  ]) assert.throws(() => requirePurchaseBody(body));
});

test("Lifetime wins; otherwise only the latest unexpired subscription grants access", () => {
  const monthly = { kind: "subscription", plan: "monthly", expiresAtMillis: 2000 };
  const yearly = { kind: "subscription", plan: "yearly", expiresAtMillis: 3000 };
  assert.deepEqual(combinedAccess([monthly, yearly], 1000), yearly);
  assert.deepEqual(combinedAccess([monthly], 2000), { kind: "none" });
  assert.deepEqual(combinedAccess([monthly, { kind: "lifetime" }], 1000),
    { kind: "lifetime", plan: "lifetime" });
});

test("paid lookup failure cannot create a trial or issue a fresh unpaid lease", async () => {
  let starts = 0;
  const service = createPaidAccountService({
    async session() { starts++; return {}; },
  }, {
    async currentAccess() { throw new Error("Google unavailable"); },
  });
  await assert.rejects(service.session("user"));
  assert.equal(starts, 0);
});

test("starting a trial checks paid access first and never starts one for an active customer", async () => {
  let starts = 0;
  let paid = { kind: "none" };
  const service = createPaidAccountService({
    async startTrial(uid) {
      starts++;
      return { userId: uid, trialStartedAtMillis: 1000 };
    },
    async session(uid) { return { userId: uid, trialStartedAtMillis: 0, trialEndsAtMillis: 0 }; },
  }, { async currentAccess() { return paid; } });
  assert.equal((await service.session("reader")).trialStartedAtMillis, 0);
  assert.equal(starts, 0);
  assert.equal((await service.startTrial("reader")).trialStartedAtMillis, 1000);
  assert.equal(starts, 1);
  for (const access of [{ kind: "lifetime" }, { kind: "subscription", expiresAtMillis: 9999 }]) {
    paid = access;
    await assert.rejects(service.startTrial("reader"), error => error.status === 409);
    assert.equal(starts, 1);
  }
});

test("purchase response preserves account identity and reuses request-local verified evidence", async () => {
  const proof = { productId: "product", kind: "lifetime", plan: "lifetime" };
  let claims = 0;
  const service = createPaidAccountService({
    async session(uid) { return { userId: uid, trialStartedAtMillis: 123 }; },
  }, {
    async claim(uid, token, productId) {
      claims++;
      assert.equal(uid, "verified-user");
      assert.equal(token, "token");
      assert.equal(productId, "product");
      return proof;
    },
    async currentAccess(uid, confirmed) {
      assert.equal(uid, "verified-user");
      assert.deepEqual([...confirmed.values()], [proof]);
      return { kind: "lifetime", plan: "lifetime" };
    },
  });
  const result = await service.verify("verified-user", {
    purchaseToken: "token", productIds: ["product"],
  });
  assert.equal(claims, 1);
  assert.equal(result.session.userId, "verified-user");
  assert.deepEqual(result.verifiedPurchase, proof);
  assert.deepEqual(result.session.paidAccess, { kind: "lifetime", plan: "lifetime" });
});

function notification(payload) {
  return { data: { message: { data: Buffer.from(JSON.stringify(payload)).toString("base64") } } };
}

test("notifications use only token hints, never a payload entitlement or user ID", async () => {
  const received = [];
  const handler = createPlayNotificationHandler({
    async refreshKnownPurchase(token) { received.push(token); },
  });
  await handler(notification({
    packageName: "com.smyddevstudio.kanako",
    subscriptionNotification: { purchaseToken: "owned-token", notificationType: 99 },
    userId: "forged-user", kind: "lifetime",
  }));
  assert.deepEqual(received, ["owned-token"]);
});

test("test and unknown notification types do not mutate purchases", async () => {
  const handler = createPlayNotificationHandler({
    async refreshKnownPurchase() { assert.fail("Must not mutate records"); },
  });
  await handler(notification({ packageName: "com.smyddevstudio.kanako", testNotification: {} }));
  await handler(notification({ packageName: "com.smyddevstudio.kanako", futureNotification: {} }));
});

test("wrong-package, ambiguous and oversized notifications are rejected", async () => {
  const handler = createPlayNotificationHandler({
    async refreshKnownPurchase() { assert.fail("Must not mutate records"); },
  });
  await assert.rejects(handler(notification({
    packageName: "other.package", subscriptionNotification: { purchaseToken: "token" },
  })));
  await assert.rejects(handler(notification({
    packageName: "com.smyddevstudio.kanako",
    subscriptionNotification: { purchaseToken: "token" },
    voidedPurchaseNotification: { purchaseToken: "token" },
  })));
  await assert.rejects(handler({ data: { message: { data: "x".repeat(32769) } } }));
});
