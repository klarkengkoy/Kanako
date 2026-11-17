import { AccountServiceError, trialDocumentId } from "./account-session.mjs";

export async function googleSubjectForAccount(auth, uid) {
  trialDocumentId(uid);
  const user = await auth.getUser(uid);
  const providers = user.providerData?.filter(value => value.providerId === "google.com");
  const subject = providers?.length === 1 ? providers[0].uid : null;
  if (user.uid !== uid || user.disabled || user.tenantId || user.emailVerified !== true ||
      typeof subject !== "string" || subject.trim().length === 0 || subject.length > 128)
    throw new AccountServiceError(403, "google_sign_in_required");
  return subject;
}

export function createAccountPlayVerifier(auth, play) {
  // Google provider identity comes from Admin Auth, never an HTTP body or a stored purchase claim.
  return {
    async verifySubscription(token, uid, nowMillis) {
      return play.verifySubscription(token, await googleSubjectForAccount(auth, uid), nowMillis);
    },
    async verifyLifetime(token, uid) {
      return play.verifyLifetime(token, await googleSubjectForAccount(auth, uid));
    },
    acknowledgeDeliveredPurchase: (...args) => play.acknowledgeDeliveredPurchase(...args),
  };
}
