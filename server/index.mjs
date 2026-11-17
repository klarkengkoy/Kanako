import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { defineBoolean, defineString } from "firebase-functions/params";
import { onRequest } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { createGooglePlayVerifier } from "./google-play.mjs";
import { createAccountPlayVerifier } from "./account-identity.mjs";
import { createAccountDeletionService } from "./account-deletion.mjs";
import { createTrialRetentionStore } from "./trial-retention.mjs";
import { billingCatalog } from "./billing-catalog.mjs";
import { createPurchaseStore, createPaidAccountService } from "./account-purchases.mjs";
import { createPlayNotificationHandler } from "./play-notifications.mjs";
import { createAccountSessionHandler, createTrialStore } from "./account-session.mjs";

// Required deploy-time parameter: every function that verifies/acknowledges purchases uses
// the same managed identity granted access in Play Console. Never download an account key.
const runtimeServiceAccount = defineString("KANAKO_RUNTIME_SERVICE_ACCOUNT", {
  description: "Full runtime service-account email granted Kanako Google Play API access.",
  input: { text: {
    nonEmpty: true,
    validationRegex: /^[a-z][a-z0-9-]{4,28}[a-z0-9]@[a-z][a-z0-9-]{4,28}[a-z0-9][.]iam[.]gserviceaccount[.]com$/,
    validationErrorMessage: "Use the full email of a dedicated Google Cloud service account.",
  } },
});

// No downloaded service-account key: use the deployment's managed identity.
const app = initializeApp({ projectId: "kanako-359da" });
const db = getFirestore(app);
const auth = getAuth(app);
const play = createAccountPlayVerifier(auth, createGooglePlayVerifier(billingCatalog));
const trialHistory = createTrialRetentionStore(db, auth);
const trials = createTrialStore(db, Date.now, trialHistory);
const purchases = createPurchaseStore(db, play, billingCatalog);
const accounts = createPaidAccountService(trials, purchases);
const deletions = createAccountDeletionService({
  db, auth, purchases, prepareTrialRetention: trialHistory.prepare,
});
// Deliberately false until purchases, deletion and final verification are complete.
const accountServiceEnabled = defineBoolean("KANAKO_ACCOUNT_SERVICE_ENABLED", { default: false });
// Private comma-separated Firebase UIDs; license-test payment access is configured separately in Play.
const testCheckoutUserIds = defineString("KANAKO_TEST_CHECKOUT_UIDS", { default: "" });
// Private Firebase account IDs, never client claims or a purchased entitlement.
const reviewerUserIds = defineString("KANAKO_REVIEWER_UIDS", { default: "" });

export const accountApi = onRequest({
  serviceAccount: runtimeServiceAccount,
  region: "asia-northeast1",
  minInstances: 0,
  maxInstances: 2,
  concurrency: 20,
  memory: "256MiB",
  timeoutSeconds: 60,
  cors: false,
  invoker: "public",
}, (request, response) => createAccountSessionHandler({
  auth,
  accounts,
  deletions,
  enabled: accountServiceEnabled.value(),
  testCheckoutUserIds: testCheckoutUserIds.value().split(",").map(uid => uid.trim()).filter(Boolean),
  reviewerUserIds: reviewerUserIds.value().split(",").map(uid => uid.trim()).filter(Boolean),
})(request, response));

export const retryAccountDeletions = onSchedule({
  serviceAccount: runtimeServiceAccount,
  schedule: "every 60 minutes",
  timeZone: "Etc/UTC",
  region: "asia-northeast1",
  maxInstances: 1,
  timeoutSeconds: 300,
  retryCount: 3,
}, async () => {
  if (!accountServiceEnabled.value()) return;
  // A failed pending account must not stop expiry cleanup for already-completed deletions.
  const results = await Promise.allSettled([
    deletions.retryPendingDeletions(), deletions.purgeCompletedDeletions(), trialHistory.purgeExpired(),
  ]);
  if (results.some(result => result.status === "rejected"))
    throw new Error("account_deletion_maintenance_failed");
});

// Restrict the topic publisher to Google Play through IAM before deployment/activation.
// An Eventarc/Pub/Sub trigger supplies transport authentication; this is not a public HTTP hook.
export const playBillingChanged = onMessagePublished({
  serviceAccount: runtimeServiceAccount,
  topic: "kanako-play-billing",
  region: "asia-northeast1",
  retry: true,
  maxInstances: 2,
  timeoutSeconds: 60,
}, async event => {
  if (!accountServiceEnabled.value()) return;
  await createPlayNotificationHandler(purchases)(event);
});

export const retryPlayAcknowledgements = onSchedule({
  serviceAccount: runtimeServiceAccount,
  schedule: "every 60 minutes",
  timeZone: "Etc/UTC",
  region: "asia-northeast1",
  maxInstances: 1,
  timeoutSeconds: 300,
  retryCount: 3,
}, async () => {
  if (!accountServiceEnabled.value()) return;
  await purchases.retryAcknowledgements();
});
