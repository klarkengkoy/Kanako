# Closed Alpha account service and review bundle

This is owner-operated setup guidance. No deployment, identity creation, IAM change,
private signing or Play upload is performed by the build. Keep actual reviewer UIDs,
email/passwords and service-account values in private configuration, not this file.

## Explicit trial choice rollout

Deploy the updated account API before distributing the matching client. The matching
client uses POST /session/choice, which returns zero trial dates for an account that
has never started a trial, without creating a new trial. It never falls back to
/session: an older deployed API may ignore the explicit-choice header and start a
trial, but the new route will instead return 404 without starting one.

For clients already distributed during this change, /session with
X-Kanako-Trial-Choice: explicit remains read-only on the updated API. Older clients
without that header retain their original first-session trial start. Existing and
retained trial dates are reused, including expired dates. Checking retained
trial dates does not create a trial record for the current account. Updating an
API or client does not undo a trial already created by an earlier deployed API.

Authenticated POST /trial/start with an empty JSON object starts the original
seven-day window atomically and is safe to retry. Reviewer accounts cannot start a
trial, and active paid accounts are rejected by this endpoint.

Ordinary authenticated unpaid sessions now return checkoutAllowed=true so customers
can choose a paid plan before trial expiry. This does not grant reading access or
change trial dates. The legacy test-checkout allowlist remains compatible, but is no
longer required for ordinary customer checkout. Reviewers retain their separate
server-approved access and cannot use purchase endpoints. No data migration or
deletion of existing trial/purchase records is required. An older deployed API will
retain its automatic trial-start behavior until the owner deploys this update.

For this update to an existing deployment, preserve the live runtime identity,
service activation, and private reviewer/tester settings in the deploy parameters.
Deploy the three entry points that use the updated request or purchase handling:

    firebase deploy --project kanako-359da --config firebase.account.json --only "functions:kanako-account:accountApi,functions:kanako-account:playBillingChanged,functions:kanako-account:retryPlayAcknowledgements"

After deployment, send an empty JSON POST without credentials to /session/choice
and /trial/start. Both should return 401 sign_in_required without touching account
records. Then use the matching client to verify the choice flow with an authorized
test account.

## Runtime identity and functions

There was no explicit runtime identity in index.mjs or firebase.account.json before
this change, and no server .env file was found. A previously deployed service could
still have a Console-managed identity; local source is not proof of its live settings.

Set the required Firebase string parameter KANAKO_RUNTIME_SERVICE_ACCOUNT to the
full email of your dedicated Google Cloud service account. It has no default.
index.mjs passes the parameter as serviceAccount to every function below.
firebase.account.json selects the server source, codebase kanako-account and Node 22;
it does not choose an identity. The Firebase Admin SDK and Google Play verifier use
Application Default Credentials from the selected runtime identity. Do not install
a JSON account key or override GOOGLE_APPLICATION_CREDENTIALS in the runtime.

All functions run in asia-northeast1:

| Function | Purpose | Resources it uses |
| --- | --- | --- |
| accountApi | Authenticated /session, /session/choice, /trial/start, /purchases/verify and /account/delete | Firebase Auth, Firestore, Google Play verification/acknowledgment |
| playBillingChanged | Pub/Sub RTDN; rechecks current purchases with Google | Firestore, Firebase Auth identity lookup, Google Play |
| retryPlayAcknowledgements | Hourly retry of acknowledgments after durable delivery | Firestore, Firebase Auth identity lookup, Google Play |
| retryAccountDeletions | Hourly resumable deletion and retention cleanup, including outstanding acknowledgments | Firestore, Firebase Auth read/delete, Google Play |

Grant the runtime service account:
- Firestore data read/write access, normally roles/datastore.user, in the existing
  Kanako project. Preserve default-deny client Security Rules.
- Firebase Auth user lookup and deletion. A narrowly scoped custom role needs
  firebaseauth.users.get and firebaseauth.users.delete for this shared identity.
  roles/firebaseauth.admin is the broader predefined alternative.
- In Play Console, access limited to Kanako with “View financial data, orders, and
  cancellation survey responses” and “Manage orders and subscriptions”. Enable the
  Google Play Android Developer API in the service account's Cloud project.

The deployer separately needs permission to deploy functions and act as the runtime
account (roles/iam.serviceAccountUser on that account); this is not a runtime role.

## Notifications and authenticated invocations

1. Configure topic projects/kanako-359da/topics/kanako-play-billing.
2. Grant google-play-developer-notifications@system.gserviceaccount.com only
   roles/pubsub.publisher on that topic. In Kanako's Play Console monetization setup,
   enter this full topic and enable notifications for subscriptions and one-time products.
3. Deploy playBillingChanged with its Pub/Sub/Eventarc trigger. Keep the event receiver
   private; do not grant allUsers invocation. The trigger's delivery identity needs
   roles/eventarc.eventReceiver in the project and roles/run.invoker on its target.
   Verify the actual trigger identity after deployment; it is a separate concern from
   the runtime identity. Preserve the Firebase-managed Pub/Sub/Eventarc service agents.
4. Deploy both hourly retry functions and verify their Cloud Scheduler jobs exist and
   use authenticated invocation. Only their scheduler caller needs target invocation
   permission; do not make retry handlers public. Firebase normally provisions this
   transport; inspect the generated job's authentication/service account and target IAM.
5. accountApi intentionally permits public HTTPS transport for Android Firebase bearer
   tokens. Its application handler still checks token project, revocation and identity.
   Public transport must not be copied to event/retry functions.

API/session refresh also reconciles subscriptions; notifications never grant access
from their payload alone. A successful Play “test notification” proves delivery only,
not real purchase verification or acknowledgment.

## Private parameters and deployment

Use the CLI's parameter prompts, or an ignored server/.env.kanako-359da file.
KANAKO_RUNTIME_SERVICE_ACCOUNT is required. Also configure:
- KANAKO_ACCOUNT_SERVICE_ENABLED=true when ready to activate.
- KANAKO_REVIEWER_UIDS: comma-separated dedicated Firebase reviewer UIDs.
- KANAKO_TEST_CHECKOUT_UIDS: optional ordinary Google-account UIDs allowed to see
  checkout before trial expiry. This neither grants reading nor enables Play sandbox payments.

In Firebase Authentication, keep Google enabled and enable Email/Password for the
dedicated reviewer account you create privately. Ordinary password identities cannot
start trials. Reviewer UIDs are checked server-side, skip trial/purchase storage and
cannot purchase. Reviewer access has no trial/subscription expiry, but uses the same
maximum 24-hour refreshed server confirmation as other access.

When ready, run from the repository root yourself:

    firebase deploy --project kanako-359da --config firebase.account.json --only functions:kanako-account

After deployment, verify all four functions' actual runtime identity, for example:

    gcloud functions describe accountApi --gen2 --region=asia-northeast1 --project=kanako-359da --format="value(serviceConfig.serviceAccountEmail)"

Repeat for playBillingChanged, retryPlayAcknowledgements and retryAccountDeletions.
Compare each result with the exact account invited in Play Console. Inspect
playBillingChanged's eventTrigger.serviceAccountEmail separately, and check the
Scheduler jobs' caller identities. Cloud Console function/runtime Security settings
provide the same inspection. Do not infer identity from the CLI login, build service
account, invoker, or Firebase Admin initializeApp project ID.

## Next Play bundle

Version codes 1 and 2 were uploaded. The default is now 3; confirm in Play's App
Bundle Explorer that 3 is unused before signing/uploading. Override with
-PkanakoVersionCode=<another-unused-integer> if needed. No local check can establish
global Play version-code availability.

Supply the deployed HTTPS accountApi base URL as kanakoAccountServerUrl, either in
your private user Gradle properties or as a -P argument. Include the function path
if the deployed URL has one; do not append /session. Keep kanakoGoogleWebClientId
and app/google-services.json for the existing Firebase Android registration.

    .\gradlew.bat :app:validatePlayConfiguration :app:bundleReview -PkanakoAccountServerUrl=<deployed-https-base-url> -PkanakoVersionCode=3

Review output: app/build/outputs/bundle/review/app-review.aab.
Review is non-debuggable, uses com.smyddevstudio.kanako, inherits release optimization
and exposes Firebase email/password reviewer sign-in alongside Google sign-in.
Normal release hides the email/password reviewer form and retains Google sign-in.

Review/release bundle packaging fails for absent Firebase configuration, missing/invalid
Google web client ID or invalid/missing HTTPS service URL. Validation checks local
configuration and URL syntax, not server availability, provider activation, live IAM,
reviewer membership or version-code availability. Policy/deletion URLs and App access
instructions must also be completed in Play Console before submission; the app retains
its offline policy dialog. Sign the final bundle with the existing private upload key
yourself. Unsigned build-verification artifacts cannot be uploaded directly.

Configure Play license testers separately from Firebase reviewer/early-checkout lists.
Use an ordinary Google sign-in for payment tests and a reviewer sign-in for unrestricted
reading tests. Register the actual Play App Signing fingerprints in Firebase as needed;
existing registered fingerprints and active product options do not need recreation.

References:
- https://firebase.google.com/docs/functions/manage-functions#set_a_service_account
- https://firebase.google.com/docs/functions/config-env
- https://developers.google.com/android-publisher/getting_started
- https://developer.android.com/google/play/billing/getting-ready
