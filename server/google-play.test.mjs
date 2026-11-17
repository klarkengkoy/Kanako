import assert from "node:assert/strict";
import test from "node:test";
import { GoogleAuth } from "google-auth-library";
import { billingCatalog as catalog } from "./billing-catalog.mjs";
import { createGooglePlayVerifier, obfuscatedAccountId } from "./google-play.mjs";

const subject = "verified-google-subject";
const now = 1800000000000;

function fixture(t, initial) {
  let body = initial;
  const calls = [];
  t.mock.method(GoogleAuth.prototype, "getClient", async () => ({
    async getAccessToken() { return { token: "test-only-access-token" }; },
  }));
  t.mock.method(globalThis, "fetch", async (url, options) => {
    assert.ok(url.startsWith("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/com.smyddevstudio.kanako/"));
    assert.equal(options.redirect, "error");
    calls.push({ url, method: options.method });
    return options.method === "POST" ? new Response(null, { status: 204 }) : Response.json(body);
  });
  return { play: createGooglePlayVerifier(catalog), calls, respond: value => { body = value; } };
}

function subscription(basePlanId = catalog.monthlyBasePlanId) {
  return {
    externalAccountIdentifiers: { obfuscatedExternalAccountId: obfuscatedAccountId(subject) },
    subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    lineItems: [{ productId: catalog.subscriptionId,
      offerDetails: { basePlanId }, autoRenewingPlan: {},
      expiryTime: new Date(now + 60_000).toISOString() }],
  };
}

function lifetime() {
  return {
    obfuscatedExternalAccountId: obfuscatedAccountId(subject),
    purchaseStateContext: { purchaseState: "PURCHASED" },
    acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
    productLineItem: [{ productId: catalog.lifetimeId,
      productOfferDetails: { purchaseOptionId: catalog.lifetimePurchaseOptionId,
        quantity: 1, refundableQuantity: 1, consumptionState: "CONSUMPTION_STATE_YET_TO_BE_CONSUMED" } }],
  };
}

test("Google's Monthly and Yearly responses grant only their verified unexpired plan", async t => {
  const f = fixture(t, subscription());
  for (const [id, plan] of [[catalog.monthlyBasePlanId, "monthly"], [catalog.yearlyBasePlanId, "yearly"]]) {
    f.respond(subscription(id));
    const result = await f.play.verifySubscription("test-purchase-token", subject, now);
    assert.equal(result.kind, "subscription");
    assert.equal(result.plan, plan);
    assert.equal(result.expiresAtMillis, now + 60_000);
    await f.play.acknowledgeDeliveredPurchase("test-purchase-token", result);
    assert.equal(f.calls.at(-1).method, "POST");
    assert.ok(f.calls.at(-1).url.endsWith(":acknowledge"));
  }
  assert.equal((await f.play.verifySubscription("test-purchase-token", subject, now + 60_000)).kind, "none");
});

test("Google's Lifetime response is non-consumable and can be acknowledged, never consumed", async t => {
  const f = fixture(t, lifetime());
  const result = await f.play.verifyLifetime("test-purchase-token", subject);
  assert.equal(result.kind, "lifetime");
  assert.equal(result.plan, "lifetime");
  await f.play.acknowledgeDeliveredPurchase("test-purchase-token", result);
  assert.equal(f.calls.at(-1).method, "POST");
  assert.ok(f.calls.at(-1).url.endsWith(":acknowledge"));
  assert.ok(f.calls.every(call => !call.url.includes(":consume")));
});

test("another Google identity cannot claim either purchase type", async t => {
  const f = fixture(t, subscription());
  await assert.rejects(f.play.verifySubscription("test-purchase-token", "another-account", now),
    error => error.code === "purchase_account_mismatch");
  f.respond(lifetime());
  await assert.rejects(f.play.verifyLifetime("test-purchase-token", "another-account"),
    error => error.code === "purchase_account_mismatch");
});

test("pending, held, revoked and refunded purchases do not grant paid access", async t => {
  const f = fixture(t, subscription());
  for (const state of ["PENDING", "ON_HOLD", "PAUSED", "EXPIRED", "REVOKED"]) {
    f.respond({ ...subscription(), subscriptionState: "SUBSCRIPTION_STATE_" + state });
    assert.equal((await f.play.verifySubscription("test-purchase-token", subject, now)).kind, "none");
  }
  for (const state of ["PENDING", "CANCELLED"]) {
    f.respond({ ...lifetime(), purchaseStateContext: { purchaseState: state } });
    assert.equal((await f.play.verifyLifetime("test-purchase-token", subject)).kind, "none");
  }
  const refunded = lifetime();
  refunded.productLineItem[0].productOfferDetails.refundableQuantity = 0;
  f.respond(refunded);
  assert.equal((await f.play.verifyLifetime("test-purchase-token", subject)).kind, "none");
});

test("unexpected products, base plans, rental and promotional offers are rejected", async t => {
  const f = fixture(t, subscription());
  for (const change of [
    data => { data.lineItems[0].productId = "other-product"; },
    data => { data.lineItems[0].offerDetails.basePlanId = "other-plan"; },
    data => { data.lineItems[0].offerDetails.offerId = "free-trial"; },
  ]) {
    const data = subscription();
    change(data);
    f.respond(data);
    await assert.rejects(f.play.verifySubscription("test-purchase-token", subject, now));
  }
  for (const offer of [{ purchaseOptionId: "other-option" }, { rentOfferDetails: {} }, { offerId: "promotion" }]) {
    const data = lifetime();
    Object.assign(data.productLineItem[0].productOfferDetails, offer);
    f.respond(data);
    await assert.rejects(f.play.verifyLifetime("test-purchase-token", subject));
  }
});
