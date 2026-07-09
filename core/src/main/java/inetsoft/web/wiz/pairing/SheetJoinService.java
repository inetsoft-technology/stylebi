/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.pairing;

import inetsoft.util.Tool;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pairing-authorized join. Validates the feature flag, consumes the single-use pairing code,
 * verifies the agent is the same logical user as the runtime owner, and opens a reusable JoinSession.
 *
 * Security contract:
 * - Feature flag checked FIRST — code is never consumed when the capability is disabled.
 * - Code is single-use (consumed by SheetPairingService.consume).
 * - Same-logical-user enforced via PairingUtil.sameLogicalUser (IdentityID name+org match).
 * - Every join attempt (success + failure) is audit-logged.
 * - Failed lookups (invalid/expired code or user mismatch) are throttled per caller: after
 *   {@link #MAX_FAILED_ATTEMPTS} failures within {@link SheetPairingService#TTL_MILLIS}, the
 *   caller is locked out for {@link #LOCKOUT_MILLIS} to blunt a guessing-driven load pattern
 *   against the pairing grant map. This is an in-memory, best-effort throttle — not a substitute
 *   for the code's own entropy, which already makes brute force impractical.
 */
@Service
public class SheetJoinService {
   private static final Logger LOG = LoggerFactory.getLogger(SheetJoinService.class);

   /** Failed lookups allowed within the attempt window before a caller is locked out. */
   private static final int MAX_FAILED_ATTEMPTS = 8;

   /** How long a caller is locked out once {@link #MAX_FAILED_ATTEMPTS} is exceeded. */
   private static final long LOCKOUT_MILLIS = 60_000L;

   @Autowired
   public SheetJoinService(SheetPairingService pairing,
                           SheetSessionService sessions,
                           SheetAgentFeature feature,
                           SheetRuntimeAccess runtimeAccess) {
      this.pairing = pairing;
      this.sessions = sessions;
      this.feature = feature;
      this.runtimeAccess = runtimeAccess;
   }

   /**
    * Validate flag + code + same-logical-user, consume it, open and return a reusable JoinSession.
    *
    * @param code      the single-use pairing code the agent presents
    * @param agentUser the agent's authenticated principal
    * @throws PairingException if the flag is off, code is invalid/expired, or user doesn't match
    */
   public JoinSession join(String code, Principal agentUser) throws PairingException {
      String throttleKey = throttleKey(agentUser);
      long now = System.currentTimeMillis();
      assertNotLockedOut(throttleKey, now);

      // 1. Gate FIRST: do NOT consume the code when the capability is disabled.
      if(!feature.isEnabled()) {
         LOG.warn("Sheet agent pairing join rejected: feature disabled (agent={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException(PairingException.Kind.FEATURE_DISABLED, "Sheet agent pairing is disabled");
      }

      // 2. Consume the code (single-use).
      // Peek at the grant type before consuming so we can reject unsupported sheet types
      // with a clean 4xx rather than a 500 from SheetAgentBroadcastService later.
      PairingGrant grant = pairing.consume(code);
      if(grant == null) {
         recordFailedAttempt(throttleKey, now);
         LOG.warn("Sheet agent pairing join rejected: invalid/expired code (agent={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED, "Invalid or expired pairing code");
      }

      // 3. Reject unsupported sheet types before the session is opened.
      if(grant.sheetType() == SheetType.VIEWSHEET) {
         LOG.warn("Sheet agent pairing join rejected: viewsheet not supported (agent={})",
                  agentUser == null ? "?" : agentUser.getName());
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "Viewsheet agent pairing is not yet supported — use a worksheet pairing code.");
      }

      // 4. Same-logical-user check: the agent must be the identity recorded at mint time.
      if(!PairingUtil.sameLogicalUser(grant.ownerIdentity(), agentUser)) {
         recordFailedAttempt(throttleKey, now);
         LOG.warn("Sheet agent pairing join rejected: user mismatch (owner={}, agent={})",
                  grant.ownerIdentity(), agentUser == null ? "?" : agentUser.getName());
         throw new PairingException(PairingException.Kind.USER_MISMATCH, "Pairing code does not belong to this user");
      }

      // 4b. Runtime-ownership check. Step 4 only proves agent == the identity recorded at mint;
      // that identity is the CALLER's, taken from the mint request, NOT from the runtime. So a
      // user could mint a code naming another user's runtimeId and pass step 4 (attacker ==
      // attacker). The pairing access path (SheetRuntimeAccess.getSheetForPairing) deliberately
      // bypasses the per-session matches() check, so this is the one place that binds the grant
      // to the runtime's real owner. Compare logical identity (name+org) against the runtime's
      // owner; a mismatch means the caller is trying to pair into a runtime they do not own.
      //
      // A null owner (runtime not in this node's cache / no user) is not treated as a mismatch:
      // getSheetDirect is node-local, so the runtime — and thus any data access — only exists on
      // its hosting node, where this lookup returns the real owner and the check bites.
      Principal runtimeOwner = runtimeAccess.getRuntimeOwner(grant.sheetType(), grant.runtimeId());

      if(runtimeOwner != null && !PairingUtil.sameLogicalUser(runtimeOwner, agentUser)) {
         recordFailedAttempt(throttleKey, now);
         LOG.warn("Sheet agent pairing join rejected: runtime not owned by agent " +
                  "(runtimeId={}, agent={})",
                  grant.runtimeId(), agentUser == null ? "?" : agentUser.getName());
         throw new PairingException(PairingException.Kind.USER_MISMATCH,
                                    "Pairing code does not belong to this user");
      }

      // 5. Open a reusable session, carrying the browser's socket session ID for broadcast.
      JoinSession session = sessions.open(grant.runtimeId(), grant.ownerIdentity(),
                                          grant.sheetType(), grant.socketSessionId(),
                                          grant.socketUserName());
      failedAttempts.remove(throttleKey);
      LOG.info("Sheet agent pairing join granted (runtimeId={}, sheetType={}, agent={})",
               grant.runtimeId(), grant.sheetType(), agentUser.getName());
      return session;
   }

   // -------------------------------------------------------------------------
   // Failed-attempt throttling
   // -------------------------------------------------------------------------

   /**
    * Tracks failed join lookups for a caller within a sliding window.
    *
    * @param failures    number of failed attempts recorded in the current window
    * @param windowStart when the current window started
    * @param lockedUntil non-zero once {@link #MAX_FAILED_ATTEMPTS} is reached; the caller is
    *                    rejected until this timestamp
    */
   private record AttemptWindow(int failures, long windowStart, long lockedUntil) {}

   private void assertNotLockedOut(String key, long now) throws PairingException {
      AttemptWindow window = failedAttempts.get(key);

      if(window != null && window.lockedUntil() != 0 && now < window.lockedUntil()) {
         LOG.warn("Sheet agent pairing join rejected: too many failed attempts (caller={})", key);
         throw new PairingException(PairingException.Kind.RATE_LIMITED,
            "Too many failed pairing attempts; try again shortly");
      }
   }

   private void recordFailedAttempt(String key, long now) {
      failedAttempts.compute(key, (k, prev) -> {
         boolean freshWindow = prev == null || now - prev.windowStart() > SheetPairingService.TTL_MILLIS;
         long windowStart = freshWindow ? now : prev.windowStart();
         int failures = freshWindow ? 1 : prev.failures() + 1;
         long lockedUntil = failures >= MAX_FAILED_ATTEMPTS ? now + LOCKOUT_MILLIS : 0L;
         return new AttemptWindow(failures, windowStart, lockedUntil);
      });
   }

   /** Removes attempt-tracking entries that are no longer relevant to any lockout decision. */
   @Scheduled(fixedDelay = 10 * 60_000)
   void evictStaleAttempts() {
      long now = System.currentTimeMillis();
      failedAttempts.values().removeIf(w ->
         now - w.windowStart() > SheetPairingService.TTL_MILLIS && now >= w.lockedUntil());
   }

   /**
    * Caller key for attempt throttling. Prefers the authenticated agent identity: {@code join} is
    * only reached with a JWT-validated principal (rebuilt by {@code WizServiceAuthenticationFilter}),
    * so the identity cannot be forged to reset the attempt window. The client IP is used only as a
    * fallback when there is no authenticated identity (e.g. direct/unit-test invocation), and is
    * deliberately NOT preferred: {@code Tool.getRemoteAddr} honors the client-supplied
    * {@code remote_ip} header, which an attacker could vary per request to defeat the lockout when
    * the deployment's edge does not strip it. Falls back to a constant key if neither is available.
    */
   private static String throttleKey(Principal agentUser) {
      if(agentUser != null && agentUser.getName() != null) {
         return "user:" + agentUser.getName();
      }

      try {
         ServletRequestAttributes attrs =
            (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
         HttpServletRequest request = attrs.getRequest();
         String addr = Tool.getRemoteAddr(request);

         if(addr != null && !addr.isBlank()) {
            return "ip:" + addr;
         }
      }
      catch(IllegalStateException e) {
         // No servlet request bound to this thread and no authenticated identity.
      }

      return "user:anonymous";
   }

   private final SheetPairingService pairing;
   private final SheetSessionService sessions;
   private final SheetAgentFeature feature;
   private final SheetRuntimeAccess runtimeAccess;
   private final ConcurrentHashMap<String, AttemptWindow> failedAttempts = new ConcurrentHashMap<>();
}
