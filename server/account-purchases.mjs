import { createHash, randomUUID } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import {
  AccountServiceError, trialDocumentId, accountDeletionReference, requireAccountActive,
} from "./account-session.mjs";
import { PlayVerificationError } from "./google-play.mjs";

const TOKEN_LIMIT = 4096;
const OWNED_TOKEN_LIMIT = 32;

function requireValue(condition, status = 503, code = "invalid_purchase_record") {
  if (!condition) throw new AccountServiceError(status, code);
}

export function purchaseDocumentId(token) {
  requireValue(typeof token === "string" && token.length > 0 && token.length <= TOKEN_LIMIT,
    400, "invalid_purchase_token");
  return createHash("sha256").update(token, "utf8").digest("hex");
}

function readIndex(snapshot, uid) {
  if (!snapshot.exists) return [];
  const data = snapshot.data();
  requireValue(data.version === 1 && data.userId === uid && Array.isArray(data.tokens) &&
    data.tokens.length <= OWNED_TOKEN_LIMIT && new Set(data.tokens).size === data.tokens.length &&
    data.tokens.every(id => typeof id === "string" && /^[a-f0-9]{64}$/.test(id)));
  return data.tokens;
}

function readPurchase(snapshot, uid) {
  requireValue(snapshot.exists);
  const data = snapshot.data();
  requireValue(data.version === 1 && data.userId === uid &&
    (data.category === "subscription" || data.category === "lifetime") &&
    purchaseDocumentId(data.token) === snapshot.id &&
    typeof data.verificationId === "string" && data.verificationId.length === 36 &&
    (data.supersededBy === null || /^[a-f0-9]{64}$/.test(data.supersededBy)));
  return data;
}

export function combinedAccess(purchases, nowMillis) {
  if (purchases.some(value => value.kind === "lifetime"))
    return { kind: "lifetime", plan: "lifetime" };
  const subscriptions = purchases.filter(value => value.kind === "subscription" &&
    Number.isSafeInteger(value.expiresAtMillis) && value.expiresAtMillis > nowMillis);
  const latest = subscriptions.sort((a, b) => b.expiresAtMillis - a.expiresAtMillis)[0];
  return latest ? {
    kind: "subscription", plan: latest.plan, expiresAtMillis: latest.expiresAtMillis,
  } : { kind: "none" };
}

export function createPurchaseStore(db, play, catalog, clock = Date.now) {
  const owners = db.collection("playPurchases");
  const indexes = db.collection("accountPurchases");

  function categoryFor(productId) {
    if (productId === catalog.subscriptionId) return "subscription";
    if (productId === catalog.lifetimeId) return "lifetime";
    throw new AccountServiceError(400, "unknown_product");
  }

  async function googleState(token, uid, category) {
    try {
      return category === "subscription"
        ? await play.verifySubscription(token, uid, clock())
        : await play.verifyLifetime(token, uid);
    } catch (error) {
      // Google no longer recognises an expired/removed token. Never preserve its old grant.
      if (error instanceof PlayVerificationError &&
        error.code === "play_request_failed" && [404, 410].includes(error.status)) {
        return {
          productId: category === "subscription" ? catalog.subscriptionId : catalog.lifetimeId,
          kind: "none", acknowledgementRequired: false, linkedPurchaseToken: null,
        };
      }
      throw error;
    }
  }

  function validVerified(value, category) {
    const productId = category === "subscription" ? catalog.subscriptionId : catalog.lifetimeId;
    requireValue(value?.productId === productId && typeof value.acknowledgementRequired === "boolean");
    requireValue(value.kind === "none" || value.kind === category);
    if (value.kind === "subscription") requireValue(
      ["monthly", "yearly"].includes(value.plan) &&
      Number.isSafeInteger(value.expiresAtMillis) && value.expiresAtMillis > 0);
    if (value.kind === "lifetime") requireValue(value.plan === "lifetime");
    if (value.linkedPurchaseToken != null) {
      requireValue(category === "subscription");
      purchaseDocumentId(value.linkedPurchaseToken);
    }
  }

  async function claim(uid, token, productId) {
    requireAccountActive(await accountDeletionReference(db, uid).get());
    const category = categoryFor(productId);
    const id = purchaseDocumentId(token);
    const reference = owners.doc(id);
    const indexReference = indexes.doc(trialDocumentId(uid));
    const previous = await reference.get();
    if (previous.exists) {
      // A claimed token cannot be moved between Firebase accounts.
      requireValue(previous.data().userId === uid, 409, "purchase_already_assigned");
      const data = readPurchase(previous, uid);
      requireValue(data.category === category);
      if (data.supersededBy !== null) return { productId, kind: "none" };
    }

    const verified = await googleState(token, uid, category);
    validVerified(verified, category);
    const linkedToken = verified.linkedPurchaseToken;
    const linkedId = linkedToken == null ? null : purchaseDocumentId(linkedToken);
    requireValue(linkedId !== id);
    // Do not reserve new pending/invalid/absent purchases or acknowledge them.
    if (!previous.exists && verified.kind === "none") return { productId, kind: "none" };

    const verificationId = randomUUID();
    const committed = await db.runTransaction(async transaction => {
      const snapshots = await transaction.getAll(indexReference, reference,
        ...(linkedId ? [owners.doc(linkedId)] : []), accountDeletionReference(db, uid));
      requireAccountActive(snapshots.at(-1));
      const [indexSnapshot, purchaseSnapshot] = snapshots;
      const linkedSnapshot = linkedId ? snapshots[2] : null;
      let tokens = readIndex(indexSnapshot, uid);
      if (purchaseSnapshot.exists) {
        requireValue(purchaseSnapshot.data().userId === uid, 409, "purchase_already_assigned");
        const current = readPurchase(purchaseSnapshot, uid);
        requireValue(current.category === category);
        if (current.supersededBy !== null) return false;
      }
      // Google was queried against the earlier ownership snapshot. Never overwrite a newer result.
      requireValue(previous.exists === purchaseSnapshot.exists &&
        (!previous.exists || previous.updateTime.isEqual(purchaseSnapshot.updateTime)),
        503, "purchase_state_changed");
      if (linkedSnapshot?.exists) {
        requireValue(linkedSnapshot.data().userId === uid, 409, "linked_purchase_already_assigned");
        const linked = readPurchase(linkedSnapshot, uid);
        requireValue(linked.category === "subscription" &&
          (linked.supersededBy === null || linked.supersededBy === id),
          409, "linked_purchase_replaced");
      }
      tokens = tokens.filter(value => value !== linkedId);
      if (!tokens.includes(id)) tokens.push(id);
      requireValue(tokens.length <= OWNED_TOKEN_LIMIT, 409, "purchase_history_limit");
      const checkedAt = Timestamp.fromMillis(clock());
      transaction.set(reference, {
        version: 1, userId: uid, token, category, supersededBy: null, verificationId,
        observedKind: verified.kind,
        observedPlan: verified.kind === "none" ? null : verified.plan,
        observedExpiresAt: verified.kind === "subscription"
          ? Timestamp.fromMillis(verified.expiresAtMillis) : null,
        acknowledgementPending: verified.acknowledgementRequired,
        checkedAt,
      });
      if (linkedId) transaction.set(owners.doc(linkedId), {
        version: 1, userId: uid, token: linkedToken, category: "subscription", supersededBy: id, verificationId,
        observedKind: "none", acknowledgementPending: false, checkedAt,
      });
      transaction.set(indexReference, { version: 1, userId: uid, tokens });
      return true;
    });
    if (!committed) return { productId, kind: "none" };

    if (verified.kind !== "none" && verified.acknowledgementRequired) {
      // Durable verified delivery/ownership precedes acknowledgement.
      // On failure the pending marker remains for notification/scheduled/client retries.
      await play.acknowledgeDeliveredPurchase(token, verified);
      await db.runTransaction(async transaction => {
        const [snapshot, deletion] = await transaction.getAll(reference, accountDeletionReference(db, uid));
        requireAccountActive(deletion);
        const current = readPurchase(snapshot, uid);
        if (current.supersededBy === null && current.verificationId === verificationId)
          transaction.update(reference, { acknowledgementPending: false });
      });
    }
    return {
      productId, kind: verified.kind, confirmationId: verificationId,
      ...(verified.kind !== "none" ? { plan: verified.plan } : {}),
      ...(verified.kind === "subscription" ? { expiresAtMillis: verified.expiresAtMillis } : {}),
    };
  }

  async function currentAccess(uid, confirmed = new Map()) {
    requireAccountActive(await accountDeletionReference(db, uid).get());
    const indexReference = indexes.doc(trialDocumentId(uid));
    // A replaced-token index can change while Google requests are in flight.
    // Retry the whole snapshot rather than return access from a retired token.
    for (let attempt = 0; attempt < 3; attempt++) {
      const before = await indexReference.get();
      const ids = readIndex(before, uid);
      if (ids.length === 0) return { kind: "none" };
      const snapshots = await db.getAll(...ids.map(id => owners.doc(id)));
      const verified = await Promise.all(snapshots.map(async snapshot => {
        const data = readPurchase(snapshot, uid);
        if (data.supersededBy !== null) return { kind: "none" };
        const productId = data.category === "subscription" ? catalog.subscriptionId : catalog.lifetimeId;
        // Reuse only evidence verified within this request; persisted status never renews a lease.
        const proof = confirmed.get(snapshot.id);
        return proof?.confirmationId === data.verificationId
          ? proof : claim(uid, data.token, productId);
      }));
      const after = await indexReference.get();
      if (JSON.stringify(ids) === JSON.stringify(readIndex(after, uid)))
        return combinedAccess(verified, clock());
    }
    throw new AccountServiceError(503, "purchase_state_changed");
  }

  async function prepareForDeletion(uid, snapshot) {
    const data = readPurchase(snapshot, uid);
    if (data.supersededBy !== null || data.acknowledgementPending !== true) return;
    // Finish delivery acknowledgement before erasing its retry record, without granting new access.
    const verified = await googleState(data.token, uid, data.category);
    validVerified(verified, data.category);
    if (verified.kind !== "none" && verified.acknowledgementRequired)
      await play.acknowledgeDeliveredPurchase(data.token, verified);
  }

  async function refreshKnownPurchase(token) {
    const snapshot = await owners.doc(purchaseDocumentId(token)).get();
    if (!snapshot.exists) return; // A notification never establishes account ownership.
    const data = snapshot.data();
    const purchase = readPurchase(snapshot, data.userId);
    if ((await accountDeletionReference(db, purchase.userId).get()).exists) return;
    if (purchase.supersededBy !== null) return;
    const productId = purchase.category === "subscription" ? catalog.subscriptionId : catalog.lifetimeId;
    await claim(purchase.userId, token, productId);
  }

  async function retryAcknowledgements() {
    const pending = await owners.where("acknowledgementPending", "==", true).limit(100).get();
    let failed = false;
    for (let offset = 0; offset < pending.docs.length; offset += 10) {
      const results = await Promise.allSettled(pending.docs.slice(offset, offset + 10).map(snapshot =>
        refreshKnownPurchase(snapshot.data().token)));
      failed ||= results.some(result => result.status === "rejected");
    }
    if (failed) throw new AccountServiceError(503, "acknowledgement_retry_needed");
  }

  return { claim, currentAccess, prepareForDeletion, refreshKnownPurchase, retryAcknowledgements };
}

export function createPaidAccountService(trials, purchases) {
  return {
    async session(uid) {
      // Query paid status first: failures must not issue a misleading fresh unpaid grant.
      const paidAccess = await purchases.currentAccess(uid);
      return { ...await trials.session(uid), paidAccess };
    },
    async startTrial(uid) {
      const paidAccess = await purchases.currentAccess(uid);
      requireValue(paidAccess.kind === "none", 409, "paid_access_active");
      return { ...await trials.startTrial(uid), paidAccess };
    },
    async verify(uid, body) {
      requirePurchaseBody(body);
      const verified = await purchases.claim(uid, body.purchaseToken, body.productIds[0]);
      const { confirmationId, ...publicVerified } = verified;
      return {
        session: {
          ...await trials.session(uid),
          paidAccess: await purchases.currentAccess(uid,
            new Map([[purchaseDocumentId(body.purchaseToken), verified]])),
        },
        verifiedPurchase: verified.kind === "none" ? null : publicVerified,
      };
    },
  };
}

export function requirePurchaseBody(body) {
  requireValue(body !== null && typeof body === "object" && !Array.isArray(body) &&
    Object.keys(body).length === 2 && Object.hasOwn(body, "purchaseToken") &&
    Object.hasOwn(body, "productIds"), 400, "invalid_purchase_request");
  purchaseDocumentId(body.purchaseToken);
  requireValue(Array.isArray(body.productIds) && body.productIds.length === 1 &&
    typeof body.productIds[0] === "string" && body.productIds[0].length <= 150,
    400, "invalid_product_ids");
}
