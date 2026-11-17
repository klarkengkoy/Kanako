import { createHash, timingSafeEqual } from "node:crypto";
import { GoogleAuth } from "google-auth-library";

const PACKAGE_NAME = "com.smyddevstudio.kanako";
const API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/";
const ACTIVE_SUBSCRIPTION_STATES = new Set([
  "SUBSCRIPTION_STATE_ACTIVE",
  "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
  "SUBSCRIPTION_STATE_CANCELED",
]);

export class PlayVerificationError extends Error {
  constructor(code, status = 0) {
    super(code);
    this.name = "PlayVerificationError";
    this.code = code;
    this.status = status;
  }
}

function requireValue(condition, code) {
  if (!condition) throw new PlayVerificationError(code);
}

function safeEqual(first, second) {
  if (typeof first !== "string" || typeof second !== "string") return false;
  const a = Buffer.from(first, "utf8");
  const b = Buffer.from(second, "utf8");
  return a.length === b.length && timingSafeEqual(a, b);
}

function purchaseToken(token) {
  requireValue(typeof token === "string" && token.length > 0 && token.length <= 4096, "invalid_token");
  return encodeURIComponent(token);
}

async function limitedJson(response) {
  if (!response.body) throw new PlayVerificationError("empty_play_response");
  const reader = response.body.getReader();
  const chunks = [];
  let length = 0;
  try {
    while (true) {
      const part = await reader.read();
      if (part.done) break;
      length += part.value.byteLength;
      requireValue(length <= 262144, "oversized_play_response");
      chunks.push(Buffer.from(part.value));
    }
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new PlayVerificationError("invalid_play_response");
  }
}

export function obfuscatedAccountId(googleSubject) {
  requireValue(typeof googleSubject === "string" && googleSubject.trim().length > 0 &&
    googleSubject.length <= 128, "invalid_account");
  // Stable across Firebase account deletion/recreation; never accept a client-supplied subject.
  return createHash("sha256").update("com.smyddevstudio.kanako/google/v1/" + googleSubject, "utf8")
    .digest("hex");
}

export function createGooglePlayVerifier(catalog) {
  for (const key of ["subscriptionId", "monthlyBasePlanId", "yearlyBasePlanId", "lifetimeId", "lifetimePurchaseOptionId"]) {
    requireValue(typeof catalog?.[key] === "string" && /^[a-zA-Z0-9_.-]{1,150}$/.test(catalog[key]), "invalid_catalog");
  }
  requireValue(catalog.subscriptionId !== catalog.lifetimeId, "invalid_catalog");
  requireValue(catalog.monthlyBasePlanId !== catalog.yearlyBasePlanId, "invalid_catalog");
  const products = Object.freeze({ ...catalog });
  const auth = new GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] });
  let client;

  async function request(path, method = "GET") {
    client ??= await auth.getClient();
    const result = await client.getAccessToken();
    const token = typeof result === "string" ? result : result?.token;
    requireValue(typeof token === "string" && token.length > 0, "play_auth_unavailable");
    let response;
    try {
      response = await fetch(API + encodeURIComponent(PACKAGE_NAME) + path, {
        method,
        headers: {
          Authorization: "Bearer " + token,
          Accept: "application/json",
          ...(method === "POST" ? { "Content-Type": "application/json" } : {}),
        },
        ...(method === "POST" ? { body: "{}" } : {}),
        redirect: "error",
        signal: AbortSignal.timeout(15000),
      });
    } catch {
      throw new PlayVerificationError("play_unavailable");
    }
    if (!response.ok) {
      await response.body?.cancel().catch(() => {});
      throw new PlayVerificationError("play_request_failed", response.status);
    }
    if (method === "POST") {
      await response.body?.cancel().catch(() => {});
      return null;
    }
    return limitedJson(response);
  }

  async function verifySubscription(token, googleSubject, nowMillis = Date.now()) {
    const data = await request("/purchases/subscriptionsv2/tokens/" + purchaseToken(token));
    requireValue(safeEqual(data.externalAccountIdentifiers?.obfuscatedExternalAccountId,
      obfuscatedAccountId(googleSubject)), "purchase_account_mismatch");
    requireValue(Array.isArray(data.lineItems) && data.lineItems.length > 0 && data.lineItems.length <= 10,
      "invalid_subscription_items");
    let selected;
    for (const item of data.lineItems) {
      requireValue(item.productId === products.subscriptionId, "unexpected_product");
      const basePlan = item.offerDetails?.basePlanId;
      requireValue(basePlan === products.monthlyBasePlanId || basePlan === products.yearlyBasePlanId,
        "unexpected_base_plan");
      requireValue(!item.offerDetails?.offerId && !item.prepaidPlan &&
        item.autoRenewingPlan && !item.autoRenewingPlan.installmentDetails, "unexpected_subscription_offer");
      const expiresAtMillis = Date.parse(item.expiryTime);
      requireValue(Number.isSafeInteger(expiresAtMillis) && expiresAtMillis > 0, "invalid_expiry");
      if (!selected || expiresAtMillis > selected.expiresAtMillis) {
        selected = {
          expiresAtMillis,
          plan: basePlan === products.monthlyBasePlanId ? "monthly" : "yearly",
        };
      }
    }
    const active = ACTIVE_SUBSCRIPTION_STATES.has(data.subscriptionState) &&
      selected.expiresAtMillis > nowMillis;
    return {
      productId: products.subscriptionId,
      kind: active ? "subscription" : "none",
      ...(active ? selected : {}),
      acknowledgementRequired: active && data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
      linkedPurchaseToken: data.linkedPurchaseToken ?? null,
    };
  }

  async function verifyLifetime(token, googleSubject) {
    const data = await request("/purchases/productsv2/tokens/" + purchaseToken(token));
    requireValue(safeEqual(data.obfuscatedExternalAccountId, obfuscatedAccountId(googleSubject)),
      "purchase_account_mismatch");
    requireValue(Array.isArray(data.productLineItem) && data.productLineItem.length === 1,
      "invalid_lifetime_items");
    const item = data.productLineItem[0];
    const offer = item.productOfferDetails;
    requireValue(item.productId === products.lifetimeId &&
      offer?.purchaseOptionId === products.lifetimePurchaseOptionId &&
      !offer.offerId && !offer.rentOfferDetails && !offer.preorderOfferDetails, "unexpected_lifetime_offer");
    const active = data.purchaseStateContext?.purchaseState === "PURCHASED" &&
      offer.quantity === 1 && offer.refundableQuantity > 0 &&
      offer.consumptionState === "CONSUMPTION_STATE_YET_TO_BE_CONSUMED";
    return {
      productId: products.lifetimeId,
      kind: active ? "lifetime" : "none",
      ...(active ? { plan: "lifetime" } : {}),
      acknowledgementRequired: active && data.acknowledgementState === "ACKNOWLEDGEMENT_STATE_PENDING",
      linkedPurchaseToken: null,
    };
  }

  async function acknowledgeDeliveredPurchase(token, verified) {
    requireValue(verified?.acknowledgementRequired === true, "acknowledgement_not_required");
    if (verified.kind === "subscription" && verified.productId === products.subscriptionId) {
      await request("/purchases/subscriptions/" + encodeURIComponent(products.subscriptionId) +
        "/tokens/" + purchaseToken(token) + ":acknowledge", "POST");
    } else if (verified.kind === "lifetime" && verified.productId === products.lifetimeId) {
      await request("/purchases/products/" + encodeURIComponent(products.lifetimeId) +
        "/tokens/" + purchaseToken(token) + ":acknowledge", "POST");
    } else {
      throw new PlayVerificationError("invalid_acknowledgement");
    }
  }

  return Object.freeze({ verifySubscription, verifyLifetime, acknowledgeDeliveredPurchase });
}
