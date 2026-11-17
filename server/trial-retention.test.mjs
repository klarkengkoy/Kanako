import assert from "node:assert/strict";
import test from "node:test";
import { Timestamp } from "firebase-admin/firestore";
import { TRIAL_DURATION_MILLIS } from "./account-session.mjs";
import { readRetainedTrial, trialRetentionUntil } from "./trial-retention.mjs";

const start = Date.parse("2026-11-10T05:06:07.123Z");
const requested = start + 8 * 24 * 60 * 60 * 1000;
const expiry = trialRetentionUntil(requested);
const record = {
  version: 1,
  startedAt: Timestamp.fromMillis(start),
  endsAt: Timestamp.fromMillis(start + TRIAL_DURATION_MILLIS),
  expiresAt: Timestamp.fromMillis(expiry),
};
const snapshot = data => ({ exists: true, id: "a".repeat(64), data: () => data });

test("retention lasts twelve calendar months in UTC, including leap-day requests", () => {
  assert.equal(new Date(trialRetentionUntil(Date.parse("2028-02-29T23:59:58.123Z"))).toISOString(),
    "2029-02-28T23:59:58.123Z");
  assert.equal(new Date(trialRetentionUntil(requested)).toISOString(), "2027-11-18T05:06:07.123Z");
  for (const invalid of [0, -1, NaN, Infinity, 1.5, 253402300799999])
    assert.throws(() => trialRetentionUntil(invalid), error => error.status === 503);
});

test("a retained trial stops restricting eligibility exactly at expiry even before physical cleanup", () => {
  assert.deepEqual(readRetainedTrial(snapshot(record), expiry - 1), {
    trialStartedAtMillis: start, trialEndsAtMillis: start + TRIAL_DURATION_MILLIS,
  });
  assert.equal(readRetainedTrial(snapshot(record), expiry), null);
  assert.equal(readRetainedTrial(snapshot(record), expiry + 1), null);
  assert.equal(readRetainedTrial({ exists: false }, requested), null);
});

test("malformed or unnecessarily identifying history fails closed instead of granting a new trial", () => {
  for (const data of [
    { ...record, userId: "should-not-be-stored" },
    { ...record, email: "should-not-be-stored@example.com" },
    { ...record, version: 2 },
    { ...record, expiresAt: expiry },
    { ...record, endsAt: Timestamp.fromMillis(start + 1) },
    { ...record, startedAt: Timestamp.fromMillis(requested + 1),
      endsAt: Timestamp.fromMillis(requested + 1 + TRIAL_DURATION_MILLIS) },
    { ...record, expiresAt: Timestamp.fromMillis(trialRetentionUntil(requested) + 60_001) },
  ]) assert.throws(() => readRetainedTrial(snapshot(data), requested), error => error.status === 503);
  assert.throws(() => readRetainedTrial({ ...snapshot(record), id: "raw-provider-id" }, requested),
    error => error.status === 503);
});
