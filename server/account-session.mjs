import { createHash } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";

export const TRIAL_DURATION_MILLIS = 7 * 24 * 60 * 60 * 1000;
export const MAX_LEASE_MILLIS = 24 * 60 * 60 * 1000;
export const RECENT_AUTH_MILLIS = 5 * 60 * 1000;

export class AccountServiceError extends Error {
  constructor(status, code) {
    super(code);
    this.name = "AccountServiceError";
    this.status = status;
    this.code = code;
  }
}

function requireValue(condition, status, code) {
  if (!condition) throw new AccountServiceError(status, code);
}

function validMillis(value) {
  return Number.isSafeInteger(value) && value > 0;
}

export function trialDocumentId(uid) {
  requireValue(typeof uid === "string" && uid.length > 0 && uid.length <= 128,
    401, "invalid_account");
  return createHash("sha256").update(uid, "utf8").digest("hex");
}

export function readTrial(data, uid, nowMillis) {
  requireValue(data?.version === 1 && data.userId === uid, 503, "invalid_trial_record");
  return readTrialDates(data, nowMillis);
}

export function readTrialDates(data, nowMillis) {
  requireValue(data.startedAt instanceof Timestamp && data.endsAt instanceof Timestamp,
    503, "invalid_trial_record");
  const start = data.startedAt.toMillis();
  const end = data.endsAt.toMillis();
  requireValue(validMillis(nowMillis) && validMillis(start) && validMillis(end) &&
    start <= nowMillis && end - start === TRIAL_DURATION_MILLIS, 503, "invalid_trial_record");
  return { trialStartedAtMillis: start, trialEndsAtMillis: end };
}

export function accountDeletionReference(db, uid) {
  return db.collection("accountDeletions").doc(trialDocumentId(uid));
}

export function requireAccountActive(snapshot) {
  if (snapshot.exists) throw new AccountServiceError(410, "account_deletion_in_progress");
}

export function createTrialStore(db, clock = Date.now, retention) {
  async function session(uid, startTrial) {
      const reference = db.collection("accountTrials").doc(trialDocumentId(uid));
      const historyReference = retention ? await retention.referenceForAccount(uid) : null;
      const trial = await db.runTransaction(async transaction => {
        const [snapshot, deletion, history] = await transaction.getAll(reference,
          accountDeletionReference(db, uid), ...(historyReference ? [historyReference] : []));
        requireAccountActive(deletion);
        const nowMillis = clock();
        requireValue(validMillis(nowMillis), 503, "clock_unavailable");
        if (snapshot.exists) return readTrial(snapshot.data(), uid, nowMillis);
        const retained = historyReference ? retention.read(history, nowMillis) : null;
        if (!startTrial) {
          return retained ?? { trialStartedAtMillis: 0, trialEndsAtMillis: 0 };
        }
        const start = retained?.trialStartedAtMillis ?? nowMillis;
        const end = retained?.trialEndsAtMillis ?? nowMillis + TRIAL_DURATION_MILLIS;
        requireValue(Number.isSafeInteger(end), 503, "clock_unavailable");
        const record = {
          version: 1,
          userId: uid,
          startedAt: Timestamp.fromMillis(start),
          endsAt: Timestamp.fromMillis(end),
        };
        // Creation is atomic across devices and retries. Never replace an existing origin.
        transaction.create(reference, record);
        return readTrial(record, uid, nowMillis);
      });
      requireAccountActive(await accountDeletionReference(db, uid).get());
      const serverNowMillis = clock();
      requireValue(validMillis(serverNowMillis) &&
        serverNowMillis >= trial.trialStartedAtMillis, 503, "clock_unavailable");
      return {
        userId: uid,
        serverNowMillis,
        ...trial,
        // This repository owns trial dates only; paid-account composition adds verified purchases.
        paidAccess: { kind: "none" },
        leaseDurationMillis: MAX_LEASE_MILLIS,
      };
  }
  return {
    session: uid => session(uid, false),
    startTrial: uid => session(uid, true),
  };
}

const INVALID_AUTH_CODES = new Set([
  "auth/argument-error", "auth/invalid-argument", "auth/invalid-id-token",
  "auth/id-token-expired", "auth/id-token-revoked", "auth/user-disabled",
  "auth/user-not-found", "auth/tenant-id-mismatch",
]);

async function authenticatedAccount(auth, authorization, {
  recent = false, nowMillis = Date.now(), reviewerUserIds = [],
} = {}) {
  requireValue(typeof authorization === "string" && authorization.length <= 16384,
    401, "sign_in_required");
  const match = /^Bearer ([A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)$/.exec(authorization);
  requireValue(match !== null, 401, "sign_in_required");
  let decoded;
  try {
    // The Admin app binds verification to Kanako's project, and checks revocation/disabled users.
    decoded = await auth.verifyIdToken(match[1], true);
  } catch (error) {
    if (INVALID_AUTH_CODES.has(error?.code))
      throw new AccountServiceError(401, "sign_in_required");
    throw new AccountServiceError(503, "authentication_unavailable");
  }
  trialDocumentId(decoded.uid);
  const provider = decoded.firebase?.sign_in_provider;
  const google = provider === "google.com" && decoded.email_verified === true;
  const reviewer = reviewerUserIds.includes(decoded.uid) && (provider === "password" || google);
  requireValue(!decoded.firebase?.tenant && (google || reviewer), 403, "google_sign_in_required");
  if (recent) {
    const authenticatedAt = decoded.auth_time * 1000;
    requireValue(Number.isSafeInteger(decoded.auth_time) && decoded.auth_time > 0 &&
      validMillis(nowMillis) && authenticatedAt <= nowMillis &&
      nowMillis - authenticatedAt <= RECENT_AUTH_MILLIS, 403, "recent_sign_in_required");
  }
  return { uid: decoded.uid, reviewer };
}

export function createAccountSessionHandler({
  auth, trials, accounts = trials, deletions, enabled = false, clock = Date.now,
  testCheckoutUserIds = [], reviewerUserIds = [],
}) {
  return async (request, response) => {
    response.set("Cache-Control", "no-store");
    response.set("X-Content-Type-Options", "nosniff");
    try {
      // Keep trial creation off until all account/purchase/deletion paths are ready and verified.
      requireValue(enabled === true, 503, "account_service_not_active");
      requireValue(request.method === "POST", 405, "post_required");
      const purchase = request.path === "/purchases/verify";
      const deleting = request.path === "/account/delete";
      const startingTrial = request.path === "/trial/start";
      if ((deleting && typeof deletions?.deleteAccount !== "function") ||
          (purchase && typeof accounts?.verify !== "function") ||
          (startingTrial && typeof accounts?.startTrial !== "function")) {
        throw new AccountServiceError(503, "account_service_incomplete");
      }
      requireValue(request.path === "/session" || request.path === "/session/choice" ||
        purchase || deleting || startingTrial, 404, "not_found");
      requireValue(/^application\/json(?:\s*;|$)/i.test(request.get("content-type") ?? ""),
        415, "json_required");
      requireValue(Buffer.isBuffer(request.rawBody) && request.rawBody.length <= 8192,
        413, "request_too_large");
      requireValue(Object.keys(request.query ?? {}).length === 0, 400, "invalid_request");
      if (!purchase) requireValue(request.body !== null && typeof request.body === "object" &&
        !Array.isArray(request.body) && Object.keys(request.body).length === 0,
        400, "empty_object_required");
      const { uid, reviewer } = await authenticatedAccount(auth, request.get("authorization"), {
        recent: deleting, nowMillis: clock(), reviewerUserIds,
      });
      if (reviewer) {
        requireValue(!purchase && !deleting && !startingTrial, 403, "reviewer_reading_only");
        const serverNowMillis = clock();
        requireValue(validMillis(serverNowMillis), 503, "clock_unavailable");
        response.status(200).json({
          userId: uid, serverNowMillis,
          trialStartedAtMillis: 0, trialEndsAtMillis: 0,
          paidAccess: { kind: "none" }, reviewerAccess: true,
          testCheckoutAllowed: false, checkoutAllowed: false, leaseDurationMillis: MAX_LEASE_MILLIS,
        });
        return;
      }
      const trialChoice = request.get("x-kanako-trial-choice");
      requireValue(trialChoice == null || trialChoice === "explicit", 400, "invalid_trial_choice");
      let result = deleting ? await deletions.deleteAccount(uid)
        : purchase ? await accounts.verify(uid, request.body)
        : startingTrial ? await accounts.startTrial(uid) : await accounts.session(uid);
      // Installed older clients cannot represent an unstarted trial or offer its explicit start.
      // Keep their original first-session behavior during rollout; new clients opt into choice.
      if (request.path === "/session" && trialChoice !== "explicit" &&
          result.trialStartedAtMillis === 0 && result.trialEndsAtMillis === 0 &&
          result.paidAccess?.kind === "none") {
        requireValue(typeof accounts.startTrial === "function", 503, "account_service_incomplete");
        result = await accounts.startTrial(uid);
      }
      if (deleting) requireValue(result?.deleted === true, 503, "account_deletion_pending");
      else {
        // This only opens checkout; it never changes trial dates or verified paid access.
        const session = purchase ? result.session : result;
        session.checkoutAllowed = session.paidAccess?.kind === "none";
        session.testCheckoutAllowed = session.checkoutAllowed && testCheckoutUserIds.includes(uid);
      }
      response.status(200).json(result);
    } catch (error) {
      const known = error instanceof AccountServiceError;
      // Do not serialize exceptions, credentials, request bodies or database contents.
      response.status(known ? error.status : 503).json({
        error: known ? error.code : "account_service_unavailable",
      });
    }
  };
}
