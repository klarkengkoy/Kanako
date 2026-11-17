import assert from "node:assert/strict";
import test from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import {
  AccountServiceError, createAccountSessionHandler, createTrialStore, readTrial, trialDocumentId,
  MAX_LEASE_MILLIS, TRIAL_DURATION_MILLIS,
} from "./account-session.mjs";

const now = 1800000000000;
const claims = {
  uid: "google-account-a",
  email_verified: true,
  firebase: { sign_in_provider: "google.com" },
};
const jwt = "header.payload.signature";

function record(uid = claims.uid) {
  return {
    version: 1, userId: uid,
    startedAt: Timestamp.fromMillis(now),
    endsAt: Timestamp.fromMillis(now + TRIAL_DURATION_MILLIS),
  };
}

async function request(options = {}) {
  let authenticationCalls = 0;
  let writes = 0;
  let requestedUid;
  const auth = {
    async verifyIdToken(token, checkRevoked) {
      authenticationCalls++;
      assert.equal(token, jwt);
      assert.equal(checkRevoked, true);
      if (options.authError) throw options.authError;
      return options.claims ?? claims;
    },
  };
  const trials = {
    async session(uid) {
      writes++;
      requestedUid = uid;
      return { userId: uid, leaseDurationMillis: MAX_LEASE_MILLIS };
    },
    async startTrial(uid) {
      writes++;
      requestedUid = uid;
      return { userId: uid, trialStartedAtMillis: now, trialEndsAtMillis: now + TRIAL_DURATION_MILLIS };
    },
  };
  const result = { headers: {} };
  const response = {
    set(name, value) { result.headers[name] = value; return this; },
    status(value) { result.status = value; return this; },
    json(value) { result.body = value; return this; },
  };
  const headers = {
    authorization: "Bearer " + jwt,
    "content-type": "application/json; charset=utf-8",
    ...options.headers,
  };
  await createAccountSessionHandler({
    auth, trials, accounts: options.accounts, deletions: options.deletions,
    enabled: options.enabled ?? true, clock: () => now,
    testCheckoutUserIds: options.testCheckoutUserIds,
    reviewerUserIds: options.reviewerUserIds,
  })({
    method: "POST", path: "/session", query: {}, rawBody: Buffer.from("{}"), body: {},
    ...options.request,
    get: name => headers[name],
  }, response);
  return { ...result, authenticationCalls, writes, requestedUid };
}

test("reviewer credentials grant reading only, without trial or purchase records", async () => {
  const result = await request({
    reviewerUserIds: [claims.uid], testCheckoutUserIds: [claims.uid],
    claims: { ...claims, firebase: { sign_in_provider: "password" } },
    accounts: { async session() { assert.fail("Reviewers must not touch trial or purchase storage"); } },
  });
  assert.equal(result.status, 200);
  assert.equal(result.writes, 0);
  assert.equal(result.body.reviewerAccess, true);
  assert.equal(result.body.trialStartedAtMillis, 0);
  assert.equal(result.body.trialEndsAtMillis, 0);
  assert.deepEqual(result.body.paidAccess, { kind: "none" });
  assert.equal(result.body.testCheckoutAllowed, false);
  assert.equal(result.body.leaseDurationMillis, MAX_LEASE_MILLIS);
});

test("ordinary password accounts and forged reviewer claims cannot receive access", async () => {
  for (const reviewerUserIds of [[], ["different-fixture-account"]]) {
    const result = await request({
      reviewerUserIds,
      claims: { ...claims, reviewerAccess: true, firebase: { sign_in_provider: "password" } },
    });
    assert.equal(result.status, 403);
    assert.equal(result.writes, 0);
  }
  const ordinary = await request({ claims: { ...claims, reviewerAccess: true } });
  assert.equal(ordinary.status, 200);
  assert.notEqual(ordinary.body.reviewerAccess, true);
  assert.equal(ordinary.writes, 1);
});

test("reviewer access cannot verify a purchase or delete a managed reviewer identity", async () => {
  for (const path of ["/purchases/verify", "/account/delete", "/trial/start"]) {
    const result = await request({
      reviewerUserIds: [claims.uid],
      claims: { ...claims, auth_time: now / 1000, firebase: { sign_in_provider: "password" } },
      request: { path },
      accounts: {
        async verify() { assert.fail("No reviewer purchase records"); },
        async startTrial() { assert.fail("No reviewer trial records"); },
      },
      deletions: { async deleteAccount() { assert.fail("Managed reviewer identity"); } },
    });
    assert.equal(result.status, 403);
    assert.equal(result.writes, 0);
  }
});

test("disabled, revoked and tenant reviewer tokens are refused", async () => {
  for (const options of [
    { authError: { code: "auth/id-token-revoked" } },
    { authError: { code: "auth/user-disabled" } },
    { claims: { ...claims, firebase: { sign_in_provider: "password", tenant: "foreign" } } },
  ]) {
    const result = await request({ reviewerUserIds: [claims.uid], ...options });
    assert.ok([401, 403].includes(result.status));
    assert.equal(result.writes, 0);
  }
});

test("only explicit starts create a trial; concurrent starts and later sessions preserve its deadline", async () => {
  const records = new Map();
  let queue = Promise.resolve();
  let time = now;
  const snapshot = reference => ({
    exists: records.has(reference.path), data: () => records.get(reference.path),
  });
  const db = {
    collection(name) {
      return { doc(id) {
        const reference = { path: name + "/" + id, async get() { return snapshot(reference); } };
        return reference;
      } };
    },
    runTransaction(action) {
      const result = queue.then(() => action({
        async getAll(...references) { return references.map(snapshot); },
        create(reference, data) {
          assert.equal(records.has(reference.path), false);
          records.set(reference.path, data);
        },
      }));
      queue = result.catch(() => {});
      return result;
    },
  };
  const firstDevice = createTrialStore(db, () => time);
  for (let attempt = 0; attempt < 2; attempt++) {
    const available = await firstDevice.session(claims.uid);
    assert.equal(available.trialStartedAtMillis, 0);
    assert.equal(available.trialEndsAtMillis, 0);
    assert.equal(records.size, 0);
  }
  const sessions = await Promise.all([firstDevice.startTrial(claims.uid), firstDevice.startTrial(claims.uid)]);
  for (const session of sessions) {
    assert.equal(session.trialStartedAtMillis, now);
    assert.equal(session.trialEndsAtMillis, now + TRIAL_DURATION_MILLIS);
  }
  time += TRIAL_DURATION_MILLIS + 1;
  const reinstalledDevice = await createTrialStore(db, () => time).session(claims.uid);
  assert.equal(reinstalledDevice.trialStartedAtMillis, now);
  assert.equal(reinstalledDevice.trialEndsAtMillis, now + TRIAL_DURATION_MILLIS);
  assert.equal(records.size, 1);
});

test("inactive service cannot authenticate or start trials", async () => {
  const result = await request({ enabled: false });
  assert.equal(result.status, 503);
  assert.equal(result.authenticationCalls, 0);
  assert.equal(result.writes, 0);
});

test("session uses only the verified account and disables response caching", async () => {
  const result = await request();
  assert.equal(result.status, 200);
  assert.equal(result.requestedUid, claims.uid);
  assert.equal(result.writes, 1);
  assert.equal(result.headers["Cache-Control"], "no-store");
  assert.equal(result.body.testCheckoutAllowed, false);
});

test("ordinary authenticated accounts can choose checkout without changing trial dates", async () => {
  const session = {
    userId: claims.uid, serverNowMillis: now, trialStartedAtMillis: now,
    trialEndsAtMillis: now + TRIAL_DURATION_MILLIS, paidAccess: { kind: "none" },
    leaseDurationMillis: MAX_LEASE_MILLIS,
  };
  for (const allowed of [[], ["another-user"], [claims.uid]]) {
    const result = await request({
      testCheckoutUserIds: allowed,
      accounts: { async session() { return { ...session }; } },
    });
    assert.equal(result.status, 200);
    assert.deepEqual(result.body, { ...session, testCheckoutAllowed: allowed.includes(claims.uid), checkoutAllowed: true });
  }
});

test("explicit trial start is authenticated, body-free and bound to the verified account", async () => {
  const started = await request({ request: { path: "/trial/start" } });
  assert.equal(started.status, 200);
  assert.equal(started.requestedUid, claims.uid);
  assert.equal(started.body.trialStartedAtMillis, now);
  for (const options of [
    { headers: { authorization: undefined }, request: { path: "/trial/start" } },
    { request: { path: "/trial/start", body: { userId: "other" } } },
    { request: { path: "/trial/start", query: { restart: "true" } } },
  ]) {
    const rejected = await request(options);
    assert.ok([400, 401].includes(rejected.status));
    assert.equal(rejected.writes, 0);
  }
});

test("missing credentials and revoked accounts cannot reach records", async () => {
  for (const options of [
    { headers: { authorization: undefined } },
    { authError: { code: "auth/id-token-revoked", message: "private-token-data" } },
    { authError: { code: "auth/user-disabled" } },
    { authError: { code: "auth/argument-error" } },
  ]) {
    const result = await request(options);
    assert.equal(result.status, 401);
    assert.equal(result.writes, 0);
    assert.deepEqual(result.body, { error: "sign_in_required" });
  }
});

test("temporary authentication failure is unavailable rather than invented access", async () => {
  const result = await request({ authError: new Error("private-diagnostics") });
  assert.equal(result.status, 503);
  assert.equal(result.writes, 0);
  assert.deepEqual(result.body, { error: "authentication_unavailable" });
});

test("unverified, anonymous and tenant accounts cannot create trials", async () => {
  for (const invalidClaims of [
    { ...claims, email_verified: false },
    { ...claims, firebase: { sign_in_provider: "anonymous" } },
    { ...claims, firebase: { sign_in_provider: "google.com", tenant: "another-tenant" } },
  ]) {
    const result = await request({ claims: invalidClaims });
    assert.equal(result.status, 403);
    assert.equal(result.writes, 0);
  }
});

test("client identity, trial dates and paid access are rejected", async () => {
  for (const body of [
    { userId: "another-user" }, { trialStartedAtMillis: now },
    { testCheckoutAllowed: true }, { reviewerAccess: true },
    { paidAccess: { kind: "lifetime" } }, [], null,
  ]) {
    const result = await request({ request: { body } });
    assert.equal(result.status, 400);
    assert.equal(result.writes, 0);
  }
});

test("wrong method, encoding, oversized body and query cannot reach records", async () => {
  for (const [options, status] of [
    [{ request: { method: "GET" } }, 405],
    [{ headers: { "content-type": "text/plain" } }, 415],
    [{ request: { rawBody: Buffer.alloc(8193) } }, 413],
    [{ request: { query: { userId: "another-user" } } }, 400],
  ]) {
    const result = await request(options);
    assert.equal(result.status, status);
    assert.equal(result.writes, 0);
  }
});

test("trial-only handler leaves purchase and deletion endpoints unavailable", async () => {
  for (const path of ["/purchases/verify", "/account/delete"]) {
    const result = await request({ request: { path } });
    assert.equal(result.status, 503);
    assert.equal(result.writes, 0);
  }
});

test("purchase endpoint authenticates and forwards only the verified account", async () => {
  let calls = 0;
  const body = { purchaseToken: "proof", productIds: ["product"] };
  const result = await request({
    request: { path: "/purchases/verify", body },
    accounts: {
      async verify(uid, evidence) {
        calls++;
        assert.equal(uid, claims.uid);
        assert.deepEqual(evidence, body);
        return { session: { userId: uid }, verifiedPurchase: null };
      },
    },
  });
  assert.equal(result.status, 200);
  assert.equal(calls, 1);
  assert.equal(result.authenticationCalls, 1);
  assert.equal(result.writes, 0);
  assert.equal(result.body.session.testCheckoutAllowed, false);
});

test("purchase responses preserve early checkout eligibility without inventing a verified purchase", async () => {
  const result = await request({
    testCheckoutUserIds: [claims.uid],
    request: { path: "/purchases/verify", body: { purchaseToken: "proof", productIds: ["product"] } },
    accounts: { async verify() {
      return { session: { userId: claims.uid, paidAccess: { kind: "none" } }, verifiedPurchase: null };
    } },
  });
  assert.equal(result.status, 200);
  assert.equal(result.body.session.testCheckoutAllowed, true);
  assert.deepEqual(result.body.session.paidAccess, { kind: "none" });
  assert.equal(result.body.verifiedPurchase, null);
});

test("verification retries keep the same proof, return real errors and disable checkout once paid", async () => {
  const body = { purchaseToken: "proof", productIds: ["kanako_premium"] };
  const paidAccess = { kind: "subscription", plan: "yearly", expiresAtMillis: now + 60000 };
  const session = {
    userId: claims.uid, serverNowMillis: now, trialStartedAtMillis: 0,
    trialEndsAtMillis: 0, paidAccess, leaseDurationMillis: MAX_LEASE_MILLIS,
  };
  let unavailable = true;
  const options = {
    testCheckoutUserIds: [claims.uid],
    request: { path: "/purchases/verify", body },
    accounts: { async verify(uid, evidence) {
      assert.equal(uid, claims.uid);
      assert.deepEqual(evidence, body);
      if (unavailable) throw new AccountServiceError(503, "purchase_state_changed");
      return { session: { ...session }, verifiedPurchase: { productId: body.productIds[0], ...paidAccess } };
    } },
  };
  for (let attempt = 0; attempt < 4; attempt++) {
    const failed = await request(options);
    assert.equal(failed.status, 503);
    assert.deepEqual(failed.body, { error: "purchase_state_changed" });
  }
  unavailable = false;
  for (let attempt = 0; attempt < 2; attempt++) {
    const restored = await request(options);
    assert.equal(restored.status, 200);
    assert.deepEqual(restored.body, {
      session: { ...session, checkoutAllowed: false, testCheckoutAllowed: false },
      verifiedPurchase: { productId: body.productIds[0], ...paidAccess },
    });
  }
});

test("unauthenticated purchase proof cannot reach verification", async () => {
  const result = await request({
    headers: { authorization: undefined },
    request: { path: "/purchases/verify", body: { purchaseToken: "proof", productIds: ["product"] } },
    accounts: {
      async verify() { assert.fail("Unverified identity cannot submit proof"); },
    },
  });
  assert.equal(result.status, 401);
  assert.equal(result.writes, 0);
});

test("deletion requires recent Google authentication and targets only the verified UID", async () => {
  let calls = 0;
  const result = await request({
    request: { path: "/account/delete" },
    claims: { ...claims, auth_time: now / 1000 },
    deletions: { async deleteAccount(uid) {
      calls++;
      assert.equal(uid, claims.uid);
      return { deleted: true };
    } },
  });
  assert.equal(result.status, 200);
  assert.deepEqual(result.body, { deleted: true });
  assert.equal(result.authenticationCalls, 1);
  assert.equal(calls, 1);
  assert.equal(result.writes, 0);
});

test("refreshing an old sign-in token does not authorize deletion", async () => {
  for (const auth_time of [undefined, null, "1800000000", now / 1000 - 301, now / 1000 + 1]) {
    const result = await request({
      request: { path: "/account/delete" },
      claims: { ...claims, auth_time, iat: now / 1000 },
      deletions: { async deleteAccount() { assert.fail("Must reauthenticate first"); } },
    });
    assert.equal(result.status, 403);
    assert.deepEqual(result.body, { error: "recent_sign_in_required" });
  }
});

test("deletion rejects forged identity, revoked authentication and non-Google sign-in", async () => {
  for (const [options, status] of [
    [{ request: { body: { userId: "other" } } }, 400],
    [{ request: { body: { googleSubject: "other" } } }, 400],
    [{ headers: { authorization: undefined } }, 401],
    [{ authError: { code: "auth/id-token-revoked" } }, 401],
    [{ claims: { ...claims, email_verified: false } }, 403],
    [{ claims: { ...claims, firebase: { sign_in_provider: "anonymous" } } }, 403],
  ]) {
    const result = await request({
      ...options,
      claims: options.claims ?? { ...claims, auth_time: now / 1000 },
      request: { path: "/account/delete", ...options.request },
      deletions: { async deleteAccount() { assert.fail("Untrusted request reached deletion"); } },
    });
    assert.equal(result.status, status);
  }
});

test("deletion cannot report success for a pending or failed cleanup", async () => {
  for (const deletion of [
    async () => ({ pending: true }),
    async () => { throw new Error("private-email-or-token"); },
  ]) {
    const result = await request({
      request: { path: "/account/delete" },
      claims: { ...claims, auth_time: now / 1000 },
      deletions: { deleteAccount: deletion },
    });
    assert.equal(result.status, 503);
    assert.equal(JSON.stringify(result.body).includes("private-email-or-token"), false);
  }
});

test("trial remains exactly seven days and survives expiration without resetting", () => {
  const original = record();
  const result = readTrial(original, claims.uid, now + TRIAL_DURATION_MILLIS + 1);
  assert.equal(result.trialStartedAtMillis, now);
  assert.equal(result.trialEndsAtMillis, now + TRIAL_DURATION_MILLIS);
});

test("corrupt, future and cross-account trial records fail without replacement", () => {
  for (const invalid of [
    { ...record(), userId: "another-user" },
    { ...record(), version: 2 },
    { ...record(), startedAt: now },
    { ...record(), endsAt: Timestamp.fromMillis(now + TRIAL_DURATION_MILLIS + 1) },
    { ...record(), startedAt: Timestamp.fromMillis(now + 1),
      endsAt: Timestamp.fromMillis(now + 1 + TRIAL_DURATION_MILLIS) },
  ]) {
    assert.throws(() => readTrial(invalid, claims.uid, now), AccountServiceError);
  }
  assert.match(trialDocumentId("uid/with/path"), /^[a-f0-9]{64}$/);
  assert.notEqual(trialDocumentId("user-a"), trialDocumentId("user-b"));
});

test("explicit-choice sessions do not start trials and later sign-ins preserve their deadline", async () => {
  let starts = 0;
  let session = {
    userId: claims.uid, serverNowMillis: now, trialStartedAtMillis: 0,
    trialEndsAtMillis: 0, paidAccess: { kind: "none" }, leaseDurationMillis: MAX_LEASE_MILLIS,
  };
  const accounts = {
    async session(uid) { assert.equal(uid, claims.uid); return { ...session }; },
    async startTrial(uid) {
      assert.equal(uid, claims.uid);
      starts++;
      session = { ...session, trialStartedAtMillis: now, trialEndsAtMillis: now + TRIAL_DURATION_MILLIS };
      return { ...session };
    },
  };
  for (const options of [
    { headers: { "x-kanako-trial-choice": "explicit" } },
    { request: { path: "/session/choice" } },
    { request: { path: "/session/choice" }, headers: { "x-kanako-trial-choice": "explicit" } },
  ]) {
    const result = await request({ accounts, ...options });
    assert.equal(result.status, 200);
    assert.equal(result.body.trialStartedAtMillis, 0);
    assert.equal(result.body.checkoutAllowed, true);
  }
  assert.equal(starts, 0);
  assert.equal((await request({ accounts, request: { path: "/trial/start" } })).status, 200);
  for (const headers of [{}, { "x-kanako-trial-choice": "explicit" }]) {
    const result = await request({ accounts, headers });
    assert.equal(result.body.trialStartedAtMillis, now);
    assert.equal(result.body.trialEndsAtMillis, now + TRIAL_DURATION_MILLIS);
  }
  assert.equal(starts, 1);
});

test("older clients retain first-session trials while paid sessions do not start a trial", async () => {
  for (const paidAccess of [{ kind: "none" }, { kind: "lifetime" }]) {
    let starts = 0;
    const result = await request({ accounts: {
      async session() {
        return { userId: claims.uid, trialStartedAtMillis: 0, trialEndsAtMillis: 0, paidAccess };
      },
      async startTrial() {
        starts++;
        return { userId: claims.uid, trialStartedAtMillis: now,
          trialEndsAtMillis: now + TRIAL_DURATION_MILLIS, paidAccess };
      },
    } });
    assert.equal(result.status, 200);
    assert.equal(starts, paidAccess.kind === "none" ? 1 : 0);
    assert.equal(result.body.trialStartedAtMillis, paidAccess.kind === "none" ? now : 0);
  }
});
