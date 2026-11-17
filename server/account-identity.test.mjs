import assert from "node:assert/strict";
import test from "node:test";
import { createAccountPlayVerifier, googleSubjectForAccount } from "./account-identity.mjs";
import { obfuscatedAccountId } from "./google-play.mjs";

function user(uid, subject = "stable-google-owner") {
  return { uid, disabled: false, emailVerified: true,
    providerData: [{ providerId: "google.com", uid: subject }] };
}

test("recreated Firebase accounts restore through the same verified Google purchase identity", async () => {
  const received = [];
  const auth = { async getUser(uid) { return user(uid); } };
  const play = createAccountPlayVerifier(auth, {
    async verifyLifetime(token, subject) {
      received.push([token, obfuscatedAccountId(subject)]);
      return { kind: "lifetime" };
    },
    async verifySubscription(token, subject, now) {
      received.push([token, obfuscatedAccountId(subject), now]);
      return { kind: "subscription" };
    },
  });
  await play.verifyLifetime("existing-purchase", "old-firebase-uid");
  await play.verifyLifetime("existing-purchase", "recreated-firebase-uid");
  assert.deepEqual(received[0], received[1]);
  await play.verifySubscription("subscription", "recreated-firebase-uid", 123);
  assert.equal(received[2][1], received[0][1]);
  assert.equal(received[2][2], 123);
  assert.notEqual(received[0][1], obfuscatedAccountId("another-google-owner"));
});

test("untrusted or ambiguous provider records cannot reach Google purchase verification", async () => {
  for (const record of [
    { ...user("owner"), uid: "other" },
    { ...user("owner"), disabled: true },
    { ...user("owner"), emailVerified: false },
    { ...user("owner"), tenantId: "another-tenant" },
    { ...user("owner"), providerData: [] },
    { ...user("owner"), providerData: [
      { providerId: "google.com", uid: "first" }, { providerId: "google.com", uid: "second" },
    ] },
    user("owner", ""), user("owner", "x".repeat(129)),
  ]) {
    const auth = { async getUser() { return record; } };
    await assert.rejects(googleSubjectForAccount(auth, "owner"), error => error.status === 403);
    const play = createAccountPlayVerifier(auth, {
      async verifyLifetime() { assert.fail("Invalid account reached Google verification"); },
    });
    await assert.rejects(play.verifyLifetime("proof", "owner"));
  }
});

test("an Auth lookup failure cannot substitute a client or Firebase UID as purchase identity", async () => {
  const auth = { async getUser() { throw new Error("Auth unavailable"); } };
  const play = createAccountPlayVerifier(auth, {
    async verifySubscription() { assert.fail("No identity fallback is allowed"); },
  });
  await assert.rejects(play.verifySubscription("proof", "owner", 123));
});
