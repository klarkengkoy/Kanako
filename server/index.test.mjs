import assert from "node:assert/strict";
import test from "node:test";
import {
  accountApi, playBillingChanged, retryPlayAcknowledgements, retryAccountDeletions,
} from "./index.mjs";

test("all account functions declare the same required runtime identity at deployment", () => {
  const identity = accountApi.__endpoint.serviceAccountEmail;
  assert.equal(identity.name, "KANAKO_RUNTIME_SERVICE_ACCOUNT");
  const specification = identity.toSpec();
  assert.equal(specification.default, undefined, "Never fall back to the compute default identity");
  const pattern = new RegExp(specification.input.text.validationRegex);
  assert.ok(pattern.test("fixture-runtime@demo-kanako-check.iam.gserviceaccount.com"));
  for (const value of ["", "default", "fixture-runtime@", "not-an-email"]) {
    assert.equal(pattern.test(value), false);
  }
  for (const fn of [accountApi, playBillingChanged, retryPlayAcknowledgements, retryAccountDeletions]) {
    assert.equal(fn.__endpoint.serviceAccountEmail, identity);
    assert.deepEqual(fn.__endpoint.region, ["asia-northeast1"]);
  }
});

test("billing notifications and maintenance retain authenticated transport and retries", () => {
  assert.deepEqual(accountApi.__endpoint.httpsTrigger.invoker, ["public"]);
  assert.equal(playBillingChanged.__endpoint.httpsTrigger, undefined);
  assert.equal(playBillingChanged.__endpoint.eventTrigger.retry, true);
  assert.equal(playBillingChanged.__endpoint.eventTrigger.eventFilters.topic, "kanako-play-billing");
  for (const fn of [retryPlayAcknowledgements, retryAccountDeletions]) {
    assert.equal(fn.__endpoint.scheduleTrigger.schedule, "every 60 minutes");
    assert.equal(fn.__endpoint.scheduleTrigger.retryConfig.retryCount, 3);
  }
});
