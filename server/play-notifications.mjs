import { AccountServiceError } from "./account-session.mjs";
import { purchaseDocumentId } from "./account-purchases.mjs";

const PACKAGE_NAME = "com.smyddevstudio.kanako";

export function createPlayNotificationHandler(purchases) {
  return async event => {
    const encoded = event.data?.message?.data;
    if (typeof encoded !== "string" || encoded.length > 32768)
      throw new AccountServiceError(400, "invalid_notification");
    let data;
    try {
      data = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
    } catch {
      throw new AccountServiceError(400, "invalid_notification");
    }
    if (data?.packageName !== PACKAGE_NAME)
      throw new AccountServiceError(400, "notification_package_mismatch");
    if (data.testNotification) return;
    const notices = [
      data.subscriptionNotification, data.oneTimeProductNotification, data.voidedPurchaseNotification,
    ].filter(value => value !== undefined && value !== null);
    // Unknown future notification types do not mutate access.
    if (notices.length === 0) return;
    if (notices.length !== 1)
      throw new AccountServiceError(400, "invalid_notification");
    const token = notices[0].purchaseToken;
    purchaseDocumentId(token);
    // Payloads are hints, never purchase proof. Google is re-queried using stored ownership.
    await purchases.refreshKnownPurchase(token);
  };
}
