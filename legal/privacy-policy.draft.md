# Kanako Privacy Policy — review draft

> Not published. This draft describes the intended release, including account-based trial and paid access that are not active yet. Configure the approved retention periods below, complete the associated deletion service, check the final release's data handling, and set the effective date before publication. Remove this draft notice only when those prerequisites are satisfied.

Developer: **Samoyed Dev Studio**  
Privacy and support contact: **[smyddevstudio@gmail.com](mailto:smyddevstudio@gmail.com)**  
Effective date: **To be set when the final policy is published.**

Kanako helps you read Japanese text by recognizing text and displaying reading guides. This policy explains the information Kanako accesses, how it is used, and how to request deletion of your Kanako account.

## Screenshots, camera frames and selected photos

Kanako processes screenshots, camera frames and photos you select on your device. Images can contain the text, pictures and personal information visible in the selected photo, camera view or captured screen. The images and recognized reading text are not sent to Samoyed Dev Studio's account service for recognition, trial checks or purchase verification.

Screen images stay in temporary memory and are discarded after use. Kanako does not save or upload screen images, and does not automatically save camera captures. Choosing a photo gives Kanako access to the selected item through Android's photo or document picker. A cloud photo provider may download that item under its own privacy policy. If you explicitly share an existing local image, it is passed to the app or recipient you choose.

## Floating reader and Android permissions

The floating reader uses Android Accessibility Service with your separate consent to capture the screen and place readings over another app. Android 14 and later use an application-window screenshot; Android 11–13 use a display screenshot. Screen reading requires Android 11 or later.

Kanako also uses app/window identifiers, scrolling and screen-change information, and keyboard/password-field indicators locally to move or clear old readings and pause capture around sensitive input. It does not read typed input-field text through accessibility node access. Capture is blocked when a locked screen or detected keyboard/password input makes reading inappropriate. These protections do not mean that every possible sensitive item in an image can be recognized automatically.

Automatic reading after a screen change is optional and requires its own agreement. You can stop the floating reader using its controls, Home or its notification, and revoke Accessibility and display-over-other-apps access in Android Settings. Camera permission is used for camera reading; selecting a photo does not require camera permission. Notification permission lets Kanako show its reader notification where Android requires permission.

## Google sign-in and account information

Google sign-in uses Android Credential Manager and Firebase Authentication. Google and Firebase process the identity information needed for sign-in, including your account identifiers, email address and authentication credentials. The sign-in provider may also supply basic profile information. Kanako uses its Firebase account identifier and email address to identify your account and show your signed-in identity. It does not ask for your Google password.

Firebase also processes connection and app information for authentication and security, including IP addresses, authentication software/platform information and the Firebase app identifier. Google describes these practices in its [Firebase Android data-disclosure guidance](https://firebase.google.com/docs/android/play-data-disclosure#authentication).

## Trial and purchase information — planned release feature

When the account-based access service is enabled, it will process your Kanako account identifier, trial start/end times and current reading-access status. These records are used to maintain one seven-day trial per account across devices and reinstalls. The trial does not enroll you in a subscription or charge you automatically.

Google Play will handle checkout for Monthly, Yearly and the one-time Lifetime purchase. Kanako's access service will process purchase tokens, product/plan identifiers, purchase ownership, verification/acknowledgement status and subscription expiry or revocation information to grant, restore and protect purchased access. A coded identifier based on your Google sign-in will link the Google Play purchase to its owner. It is still personal data, rather than an anonymous identifier. This lets you restore a valid purchase after recreating a Kanako account using the same Google sign-in and the Google Play account that made the purchase. The service will recheck access and purchases, including when you reopen the app and when Google reports purchase changes.

Kanako's access service will not receive your full payment-card number or security code from Google Play. Google separately handles payment information and any records it must retain for its services. This section must be reconciled with the completed service before paid access is enabled or this policy is published.

## Information handled by recognition software

Kanako uses Google's on-device ML Kit text-recognition software. Local recognition does not mean that the software has no network activity. ML Kit processes device/app information, device or installation identifiers, and operational measurements such as recognition timing, configuration, model downloads and errors for diagnostics and usage analysis. This operational information is separate from uploading your images or recognized text. See [Google's ML Kit data-disclosure guidance](https://developers.google.com/ml-kit/android-data-disclosure) for the relevant software's practices.

## Local settings and device backup

Reading appearance, permission/disclosure choices and tutorial settings are stored on your device. Android backup or device transfer may retain app data according to your system settings and Android's behavior. Removing the app does not itself request deletion of your online Kanako account or cancel a Google Play subscription.

The planned access service also uses an encrypted local record of recently confirmed reading access. This record is excluded from normal Android backup, and Kanako clears it when you sign out or start account deletion. A local record does not replace verification of paid access by the service.

## Service providers, support and security

Google services provide account sign-in, recognition software and, for the intended release, checkout and cloud account/access processing. The planned account database is in Tokyo, Japan; that location does not mean that all Google authentication, payment, support or operational processing is restricted to Japan. Review [Google's privacy policy](https://policies.google.com/privacy) and [Firebase's privacy information](https://firebase.google.com/support/privacy) for those services.

If you contact Samoyed Dev Studio, we receive your email address and the information you include so we can answer or handle your request. Please do not send passwords, sign-in codes, payment-card details or screenshots containing private information that is unnecessary for your request.

Account-service communication uses HTTPS, and the local reading-access record is encrypted using Android's key storage. No storage or transmission method can guarantee complete security. This draft does not claim that every service provider's records can be erased directly by Samoyed Dev Studio.

## Keeping and deleting information

Images used for screen reading remain in memory only for processing and display. Local app data may remain on your device or in Android-managed backups until you remove it or the platform expires it.

Your Firebase account information is used while you maintain your Kanako account. The planned trial and purchase records support your trial and purchased access. The prepared deletion service is designed to remove the sign-in account, ordinary trial/access records and Kanako's full purchase-verification records, including stored purchase tokens and records of replaced purchases. It checks outstanding purchase acknowledgements before removing their retry information, so account deletion does not itself cause an unacknowledged purchase to be lost. Google keeps its own purchase records under its policies; deleting Kanako's copies does not cancel a subscription or request a refund.

A temporary deletion-job record contains the account identifier and request time while cleanup is unfinished, so interrupted work can be retried and old requests cannot recreate deleted data. After successful completion the raw account identifier is removed. Only a coded record and deletion timestamps remain for 24 hours, then become eligible for hourly cleanup. Service outages can delay that cleanup. This record is still personal data and is used only to complete and protect deletion.

To prevent repeated free trials through account deletion and recreation, the prepared paid service retains a coded identifier derived from your Google sign-in, your original trial start/end times, and an expiry date. This limited record does not contain your name, email address, raw Google/Firebase account identifiers, images, purchase tokens or full purchase records. The coded identifier is still personal data. It is used only to recognize the original trial if you recreate your account with the same Google sign-in.

The retention period is **12 calendar months from the accepted deletion request**, measured in UTC (a February 29 deadline falls on February 28 the following year). Signing in again, retrying deletion or deleting a recreated account while this record is unexpired does not extend that deadline. At expiry, the record stops restricting trial eligibility even if cleanup has not yet removed it. Expired records become eligible for hourly cleanup; outages or a cleanup backlog can delay physical removal. After expiry, a later account recreation may start a new trial. A later deletion may create a new limited record with its own deadline.

### Approved support, logs and backup periods — configuration pending

The developer has approved the following periods for the intended release. They are not a description of verified live settings and must be configured and checked before publication:

- **Support emails:** delete the conversation and unnecessary attachments within **90 days after the issue is resolved**, or sooner when they are no longer needed. This does not extend the maximum 14-day commitment for completing emailed account-deletion requests. Gmail cleanup must cover the developer-controlled copies in the inbox, sent mail and trash; archiving is not deletion.
- **Ordinary service logs:** use a **30-day retention period** for debugging and security. Infrastructure logs may contain request times, network addresses and response information. Do not intentionally log sign-in credentials, authorization headers, purchase tokens, request bodies, images or recognized text. Review logging destinations and exported copies before making this commitment.
- **Mandatory Google Cloud audit logs:** some administration and system audit records are retained in Google's required log storage for **400 days**, a period the developer cannot shorten. This is separate from ordinary application logs and does not mean Kanako retains every user's full account data for 400 days. See [Google Cloud log-retention guidance](https://docs.cloud.google.com/logging/docs/store-log-entries).
- **Database recovery backups:** use one daily Firestore backup with **7-day retention from its creation**. Backups can contain older copies of records removed from the working database, including trial and purchase information, until those backups expire. They are for restricted disaster recovery, not normal account access or another trial-history check. Any restore must stay isolated until account deletions are reapplied and expired records removed. Backups incur storage/restore charges and require the Blaze plan; no backup schedule or point-in-time recovery setting has been enabled by this preparation. See [Firestore backup guidance](https://firebase.google.com/docs/firestore/backups).

These approved periods cover developer-controlled support and account-service records. They do not set Google's separate Authentication, payment, ML Kit or infrastructure retention practices. Any legally required exception must have a specific reason and scope; this draft does not approve indefinite retention.

**Before publication:** configure and verify these approved support/log/backup periods, operational cleanup and recovery safeguards, plus the selected 12-month trial-history policy. Ensure emailed deletion requests use the same complete account/data deletion process. The prepared service is not deployed or active. No long-term storage of Kanako's full purchase records after account deletion is selected for the working database.

## Request account deletion

In Kanako, open **Settings → Your Kanako account → Delete account**, confirm the request and confirm your Google identity again. You can also email **smyddevstudio@gmail.com** with the subject **Delete my Kanako account**, preferably from the email used for Kanako. If that address is no longer available, contact us for help verifying ownership. You do not need to reinstall Kanako to send a request. See the separate [account-deletion draft](account-deletion.draft.md).

Deleting your Kanako account does not delete your Google account or automatically cancel a Google Play subscription. To stop future renewals, open **Google Play → profile picture → Payments & subscriptions → Subscriptions → Kanako → Cancel subscription**. You can request account deletion without first cancelling; contact support if you need help with both.

We normally complete emailed account-deletion requests in **7–14 days**, with a maximum of **14 days from receipt**. We verify account ownership before deleting the account.

**Before publication:** the in-app paid-account deletion route must be completed and checked before that service is activated.

## Your questions and choices

You can withdraw Android permissions, disable automatic reading, sign out, or contact us to ask about your personal information or request correction/deletion. The rights and response requirements that apply may depend on where you live. Do not use this draft as confirmation that every regional legal requirement has been reviewed.

We will publish an updated policy when our practices change. For privacy questions or account requests, contact **Samoyed Dev Studio** at **[smyddevstudio@gmail.com](mailto:smyddevstudio@gmail.com)**.
