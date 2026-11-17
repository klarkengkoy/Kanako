# Delete your Kanako account — review draft

> Not published. Complete the paid-account deletion service, configure and verify the approved retention periods, and replace the draft link with the final public privacy-policy link before publishing. The draft does not confirm that the future service can already delete account data.

**App:** Kanako  
**Developer:** Samoyed Dev Studio  
**Contact:** [smyddevstudio@gmail.com](mailto:smyddevstudio@gmail.com)

## Request deletion by email

You can request deletion even if Kanako is no longer installed.

1. Email **smyddevstudio@gmail.com** with the subject **Delete my Kanako account**.
2. Say that you want your Kanako account and associated data deleted. Send the request from the email you used to sign in to Kanako, if possible. If you cannot use that address, explain that you need help verifying ownership.
3. We will check ownership before deleting the account and contact you if more information is necessary. Never send your Google password, sign-in codes, payment-card number or security code.

[Start an account-deletion email](mailto:smyddevstudio@gmail.com?subject=Delete%20my%20Kanako%20account)

We normally complete emailed account-deletion requests in **7–14 days**, with a maximum of **14 days from receipt**. We verify account ownership before deleting the account.

## Request deletion inside Kanako

Open **Settings → Your Kanako account → Delete account**, confirm deletion and confirm your Google identity again. This deletes the Kanako account, not your Google account. For the intended paid release, this route must also handle its associated account-service data before the service is enabled.

## What the completed deletion process must remove

The prepared deletion service is designed to remove the sign-in account, ordinary trial/access records, and Kanako's purchase-verification records, including full stored purchase tokens and records of replaced purchases. It checks any outstanding purchase acknowledgement before removing its retry information. Removing the app or signing out alone does not request online account deletion.

Deleting the Kanako account does not cancel the underlying Google Play purchase. The prepared restoration flow recognizes the same Google sign-in after account recreation and still requires verification of a valid purchase. To restore, use the same Google sign-in and Google Play purchasing account.

Kanako does not hold uploaded screen images to delete: screen captures are processed locally in temporary memory. Local settings, existing local image files and Android-managed backups may require removal through your device or backup controls. Google separately controls its own payment/account records under its policies and legal obligations.

The deletion service uses a temporary job record to retry interrupted work and prevent old requests from recreating data. It holds the account identifier and request time while cleanup is unfinished. On completion, the raw account identifier is removed; a coded record and deletion timestamps remain for 24 hours before becoming eligible for hourly cleanup. Outages may delay cleanup. This record is still personal data.

To prevent repeated free trials after deletion and account recreation, the prepared paid service keeps only a coded identifier derived from your Google sign-in, the original trial start/end times, and an expiry date. The identifier is still personal data. This limited record contains no name, email address, raw Google/Firebase account identifiers, images, purchase tokens or full purchase records. It recognizes the original trial when you recreate your account using the same Google sign-in.

This record is retained for **12 calendar months from the accepted deletion request**, measured in UTC (February 29 becomes February 28 the following year). Signing in again, retries, and deletion of a recreated account while the record remains unexpired do not extend its deadline. At expiry it no longer restricts trial eligibility and becomes eligible for hourly cleanup. Outages or a cleanup backlog may delay physical removal. Account recreation after expiry may start a new trial. A later deletion may create a new limited record with a new deadline.

**Approved additional retention — configuration pending:** support emails will be deleted within 90 days after the issue is resolved; ordinary service logs will have 30-day retention. Some mandatory Google Cloud administration/system audit records have a fixed 400-day retention period. Daily database recovery backups will expire 7 days after creation, so deleted records could remain in restricted backup copies until expiry. Restoring a backup must not return a deleted account to service: deletion and expiry cleanup must be reapplied first. These are separate from the 12-month trial-history rule and Google's own payment records. See the [privacy-policy draft](privacy-policy.draft.md) for scope and the required safeguards.

**Before publication:** configure the approved additional periods and verify retention, cleanup jobs, backup recovery safeguards and the handling of emailed requests through the same deletion process. The prepared service is not deployed or active. No long-term retention of Kanako's full purchase records in the working database is selected.

## Stop subscription renewals separately

Deleting a Kanako account does not automatically cancel a Google Play subscription. To stop future renewals:

1. Open **Google Play** and tap your profile picture.
2. Choose **Payments & subscriptions → Subscriptions → Kanako**.
3. Choose **Cancel subscription** and follow Google's instructions.

Cancelling is separate from requesting account deletion. You can send a deletion request without first cancelling; tell us if you also need help finding the subscription. A one-time Lifetime purchase does not renew. Refund requests and payment records are handled separately from account deletion under Google Play's policies.

For questions, contact **Samoyed Dev Studio** at **[smyddevstudio@gmail.com](mailto:smyddevstudio@gmail.com)**. The final public privacy policy must be linked here; the current [privacy-policy draft](privacy-policy.draft.md) is for review only.
