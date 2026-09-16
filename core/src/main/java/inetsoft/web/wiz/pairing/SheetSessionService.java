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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

@Service
public class SheetSessionService {
   private static final Logger LOG = LoggerFactory.getLogger(SheetSessionService.class);

   public static final long TTL_MILLIS = 30 * 60_000L;

   /**
    * TTL for a session opened with no {@code runtimeId} yet (a portal-level session, established
    * directly at login rather than by pairing to an already-open Composer pane) -- shorter than
    * {@link #TTL_MILLIS} because an unattached session that is never subsequently used to open or
    * attach to an asset represents idle, unused-but-standing access, not an editor a human is
    * actively working in.
    */
   public static final long UNATTACHED_TTL_MILLIS = 10 * 60_000L;

   private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

   private final ConcurrentHashMap<String, JoinSession> sessions;

   /**
    * Follow Focus's stack storage -- keyed by {@code sessionToken}, holding the *previous*
    * {@code editorContext} values a session has been {@link #retarget}ed through, most recent on
    * top. An absent/empty deque means "nothing left to pop" -- {@link #popFocus} then leaves the
    * session exactly as it is, which is what makes an EXHAUSTED stack idempotent under repeated
    * pops, whatever the session's original scope happened to be (including a non-null one, for a
    * session that was itself opened already pane-scoped). {@code JoinSession} stays one flat
    * value, unaware this history exists.
    *
    * <p>Wrapped in {@link FocusFrame} rather than storing {@code EditorContext} directly:
    * {@link java.util.concurrent.ConcurrentLinkedDeque} refuses {@code null} elements, and the
    * FIRST retarget away from a whole-sheet session legitimately needs to push {@code null} (that
    * session's original, whole-sheet scope) -- {@code FocusFrame} is a non-null box around a
    * possibly-null value, exactly so "a frame exists, and it holds null" stays distinguishable
    * from "no frame exists at all".
    */
   private final ConcurrentHashMap<String, Deque<FocusFrame>> focusStacks;

   /** Non-null wrapper around a possibly-null {@code editorContext} -- see {@link #focusStacks}. */
   private record FocusFrame(EditorContext editorContext) {}

   private final SecureRandom random = new SecureRandom();
   private final LongSupplier clock;

   /**
    * The {@link SheetPairingService} whose {@code validateEditorContext} {@link #retarget}
    * reuses before moving a live session's target -- a retarget to a location the runtime
    * doesn't actually have must fail the same way a mint to one does today.
    */
   private final SheetPairingService pairing;

   /**
    * Notifies the Composer tab bar of session-ended events from {@link #evictExpired},
    * {@link #socketClosed}, and {@link #detach(String, EditorContext)} -- see those methods'
    * javadoc. {@code null} on every back-compat/test constructor below (guarded at each call
    * site): those fixtures do not exercise this feature, so they simply never broadcast, which
    * is correct -- not a degraded production path.
    */
   private final SheetAgentBroadcastService broadcast;

   /**
    * Used by {@link #syncToCurrentFocus} to re-verify runtime ownership before retargeting a
    * cross-sheet-follow session -- the same check {@code SheetJoinService.join}'s step 3b already
    * performs. {@code null} on every back-compat/test constructor below that doesn't take one
    * explicitly (guarded at the one call site): those fixtures do not exercise cross-sheet-follow,
    * so a {@code null} runtime owner is simply tolerated there, same as a genuinely-not-found
    * runtime.
    */
   private final SheetRuntimeAccess runtimeAccess;

   /** Production constructor -- Spring injects the real, runtime-validating SheetPairingService. */
   @Autowired
   public SheetSessionService(SheetPairingService pairing, SheetAgentBroadcastService broadcast,
                              SheetRuntimeAccess runtimeAccess)
   {
      this.clock = System::currentTimeMillis;
      this.pairing = pairing;
      this.broadcast = broadcast;
      this.runtimeAccess = runtimeAccess;
      this.sessions = new ConcurrentHashMap<>();
      this.focusStacks = new ConcurrentHashMap<>();
   }

   /**
    * Back-compat/convenience constructor: builds its own {@link SheetPairingService} using ITS
    * OWN back-compat constructor, which validates against no configured runtime (mirrors
    * {@code SheetPairingService()}'s own javadoc). Fine for the wide set of existing tests that
    * construct a bare {@code new SheetSessionService()} and never exercise {@link #retarget}'s
    * validation branch; retarget on one of these simply refuses any editorContext naming a real
    * assembly, exactly like an unconfigured mint would.
    */
   public SheetSessionService() { this(System::currentTimeMillis, new SheetPairingService()); }

   SheetSessionService(LongSupplier clock) { this(clock, new SheetPairingService()); }

   SheetSessionService(LongSupplier clock, SheetPairingService pairing) {
      this.clock = clock;
      this.pairing = pairing;
      this.broadcast = null;
      this.runtimeAccess = null;
      this.sessions = new ConcurrentHashMap<>();
      this.focusStacks = new ConcurrentHashMap<>();
   }

   /** Test-only constructor: exercises the {@link #broadcast} call sites without needing the
    *  full production constructor's {@link SheetPairingService} wiring. */
   SheetSessionService(LongSupplier clock, SheetAgentBroadcastService broadcast) {
      this.clock = clock;
      this.pairing = new SheetPairingService();
      this.broadcast = broadcast;
      this.runtimeAccess = null;
      this.sessions = new ConcurrentHashMap<>();
      this.focusStacks = new ConcurrentHashMap<>();
   }

   /** Test-only constructor: exercises {@link #syncToCurrentFocus}'s {@link #runtimeAccess} call
    *  site without needing the full production constructor's {@link SheetPairingService} wiring. */
   SheetSessionService(LongSupplier clock, SheetAgentBroadcastService broadcast,
                       SheetRuntimeAccess runtimeAccess)
   {
      this.clock = clock;
      this.pairing = new SheetPairingService();
      this.broadcast = broadcast;
      this.runtimeAccess = runtimeAccess;
      this.sessions = new ConcurrentHashMap<>();
      this.focusStacks = new ConcurrentHashMap<>();
   }

   /** Test constructor: shares the sessions map (and focus stacks) of an existing service with a
    *  different clock. */
   SheetSessionService(LongSupplier clock, SheetSessionService source) {
      this.clock = clock;
      this.pairing = source.pairing;
      this.broadcast = source.broadcast;
      this.runtimeAccess = source.runtimeAccess;
      this.sessions = source.sessions;
      this.focusStacks = source.focusStacks;
   }

   public JoinSession open(String runtimeId, String ownerIdentity, SheetType sheetType,
                           String socketSessionId, String socketUserName,
                           EditorContext editorContext)
   {
      String token = newToken();
      long ttl = runtimeId != null ? TTL_MILLIS : UNATTACHED_TTL_MILLIS;
      JoinSession s = new JoinSession(token, runtimeId, ownerIdentity, sheetType,
                                      clock.getAsLong(), ttl, JoinSession.ConnectionMode.PAIRED,
                                      socketSessionId, socketUserName, editorContext);
      sessions.put(token, s);
      return s;
   }

   /**
    * Attaches a directly-established (D10) session to {@code runtimeId}/{@code sheetType} IN
    * PLACE -- same {@code sessionToken}, same {@code ownerIdentity}/{@code connectionMode}/
    * {@code socketSessionId}/{@code socketUserName} (still {@code null} either way for a D10
    * session, per {@link #openEstablishedDirectly}), and same
    * {@code followFocusEnabled}/{@code establishedDirectly}/{@code crossSheetFollowEnabled} flags
    * carried through unchanged -- rather than minting an unrelated new session via {@link #open}.
    *
    * <p>{@code createWorksheet}/{@code createViewsheet}/{@code openBaseWorksheet} must call this
    * instead of {@link #open} whenever the acting session's {@link JoinSession#establishedDirectly()}
    * is {@code true}: {@link #open} always mints a brand-new token with both flags defaulted to
    * {@code false}, which would silently orphan whatever cross-sheet-follow state the caller's
    * D10 session already had (PSP-029) -- the exact same "reconstruct in place, same token" shape
    * {@link #syncToCurrentFocus} and {@link #resolve} already use, just driven by an explicit
    * attach rather than a focus report or a refresh. {@code editorContext} is reset to
    * {@code null} (a freshly opened/created sheet has no pane-level detail yet) and
    * {@code ttlMillis} follows {@link #open}'s own rule ({@link #TTL_MILLIS} once a
    * {@code runtimeId} is attached, {@link #UNATTACHED_TTL_MILLIS} otherwise).
    *
    * <p>Must only be called when {@code actingSession.establishedDirectly()} is {@code true}; a
    * pane-scoped (pairing-code-joined) acting session must keep minting a new session via
    * {@link #open}, unchanged.
    */
   public JoinSession attachEstablishedDirectly(JoinSession actingSession, String runtimeId,
                                                 SheetType sheetType)
   {
      long ttl = runtimeId != null ? TTL_MILLIS : UNATTACHED_TTL_MILLIS;
      JoinSession attached = new JoinSession(actingSession.sessionToken(), runtimeId,
                                             actingSession.ownerIdentity(), sheetType,
                                             clock.getAsLong(), ttl,
                                             actingSession.connectionMode(),
                                             actingSession.socketSessionId(),
                                             actingSession.socketUserName(), null,
                                             actingSession.followFocusEnabled(),
                                             actingSession.establishedDirectly(),
                                             actingSession.crossSheetFollowEnabled());
      sessions.put(actingSession.sessionToken(), attached);
      return attached;
   }

   /**
    * Opens (or reuses) a directly-established, unattached session for {@code ownerIdentity} --
    * the D10 login-triggered establish endpoint's own entry point (design doc section 8.5). Never
    * called with a {@code runtimeId}: this is the "no pairing code, no runtime yet" session a
    * verified SSO login mints directly, not a pairing-code join.
    *
    * <p>Idempotent: a caller invoking this a second time (e.g. a second {@code login_start}/
    * {@code login_complete} with {@code establishPortalSession: true}) reuses the SAME live
    * session -- looked up via {@link #findEstablishedDirectly} -- rather than minting a duplicate.
    */
   public JoinSession openEstablishedDirectly(String ownerIdentity) {
      JoinSession existing = findEstablishedDirectly(ownerIdentity);

      if(existing != null) {
         return existing;
      }

      String token = newToken();
      JoinSession s = new JoinSession(token, null, ownerIdentity, null, clock.getAsLong(),
                                      UNATTACHED_TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
                                      null, null, null, false, true);
      sessions.put(token, s);
      return s;
   }

   /**
    * Returns the unexpired, directly-established ({@link JoinSession#establishedDirectly()})
    * session owned by {@code ownerIdentity}, or {@code null} if none is live -- the idempotency
    * lookup {@link #openEstablishedDirectly} uses before minting a new one.
    */
   public JoinSession findEstablishedDirectly(String ownerIdentity) {
      return findByIdentityAndFlag(ownerIdentity, JoinSession::establishedDirectly);
   }

   /** Returns the session (TTL refreshed) iff present, unexpired, and owned by agentIdentity; else null. */
   public JoinSession resolve(String token, String agentIdentity) {
      if (token == null) return null;
      JoinSession s = sessions.get(token);
      if (s == null || s.isExpired(clock.getAsLong()) || !s.ownerIdentity().equals(agentIdentity)) return null;
      // Full 13-arg canonical constructor, NOT any back-compat overload -- those default
      // followFocusEnabled/establishedDirectly/crossSheetFollowEnabled to false, which would
      // silently un-opt-in every session (and forget it was directly established) on its very
      // next refresh. Must carry s.followFocusEnabled()/s.establishedDirectly()/
      // s.crossSheetFollowEnabled() through explicitly.
      JoinSession refreshed = new JoinSession(s.sessionToken(), s.runtimeId(), s.ownerIdentity(),
                                              s.sheetType(), clock.getAsLong(), s.ttlMillis(),
                                              s.connectionMode(), s.socketSessionId(),
                                              s.socketUserName(), s.editorContext(),
                                              s.followFocusEnabled(), s.establishedDirectly(),
                                              s.crossSheetFollowEnabled());
      sessions.put(token, refreshed);
      return refreshed;
   }

   public void close(String token) {
      if(token != null) {
         sessions.remove(token);
         focusStacks.remove(token);
      }
   }

   /**
    * Whether {@code token} still names a live (present, unexpired) session -- regardless of
    * owner. For internal cleanup use only (see {@code LayoutSessionService}'s own scheduled
    * sweep, which has no {@code Principal} to check against and no need for one: it is deciding
    * whether to free a resource, not authorizing an action). NOT an authorization check --
    * callers that need to verify the caller actually owns the session must still use
    * {@link #resolve}.
    */
   public boolean isLive(String token) {
      if(token == null) {
         return false;
      }

      JoinSession s = sessions.get(token);
      return s != null && !s.isExpired(clock.getAsLong());
   }

   /**
    * Ends every pane-scoped session bound to {@code socketSessionId} -- called when that
    * WebSocket session disconnects (crash, network drop, or a killed tab; see
    * {@code SheetSessionSocketCleanup}). A pane-scoped session (nullable
    * {@link JoinSession#editorContext()} non-null) is meant to live only "while a script pane
    * is active"; once its socket is gone, that is no longer knowable to be true, so it is
    * expired immediately rather than given a reconnect grace period. A user who legitimately
    * blips and reconnects just re-pairs -- cheap, one click -- versus leaving a live write
    * handle open for an unknown gap, which is the exact hole this task exists to close.
    *
    * <p>Whole-sheet (editorContext == null) sessions are deliberately left untouched here --
    * they keep today's TTL-only lifetime so a toolbar-paired user does not lose their session
    * to a transient socket blip. This is the regression this method must never cause.
    */
   public void socketClosed(String socketSessionId) {
      if(socketSessionId == null) return;
      List<JoinSession> ended = removeSessionsIf(
         s -> s.editorContext() != null && socketSessionId.equals(s.socketSessionId()));
      notifyAgentInactive(ended);
   }

   /**
    * Ends the one pane-scoped session bound to both {@code socketSessionId} and
    * {@code editorContext}, if any -- called explicitly when the script pane or formula editor
    * that produced it is destroyed (its dialog closed or cancelled). Scoped to the exact
    * {@code editorContext} match, not just the socket, so closing one pane does not end a
    * session paired from a different pane/dialog still open on the same browser socket.
    *
    * <p>Distinct from {@link #socketClosed}, which reacts to the transport itself going away
    * (crash/network drop) rather than a deliberate, in-app close.
    */
   public void detach(String socketSessionId, EditorContext editorContext) {
      if(socketSessionId == null || editorContext == null) return;
      List<JoinSession> ended = removeSessionsIf(
         s -> editorContext.equals(s.editorContext()) && socketSessionId.equals(s.socketSessionId()));
      notifyAgentInactive(ended);
   }

   /**
    * Returns an unexpired session of {@code sheetType} owned by {@code ownerIdentity}, or null if
    * none is held.
    *
    * <p>Used to refuse silently replacing a session the user may be editing -- e.g.
    * {@code open_base_worksheet} refusing to open a second worksheet runtime out from under one
    * already paired, naming its runtime id instead of ending it with a success response.
    */
   public JoinSession findOpen(String ownerIdentity, SheetType sheetType) {
      if(ownerIdentity == null) {
         return null;
      }

      long now = clock.getAsLong();

      for(JoinSession s : sessions.values()) {
         if(!s.isExpired(now) && s.sheetType() == sheetType &&
            ownerIdentity.equals(s.ownerIdentity()))
         {
            return s;
         }
      }

      return null;
   }

   /**
    * Returns every unexpired session bound to {@code runtimeId}, across every owner and token --
    * advisory only (PSM-001 Change B), used to warn a joining agent when it has landed on a
    * document someone (possibly the same agent, via a different session) already has open. Unlike
    * {@link #findOpen}, which is scoped to one {@code (ownerIdentity, sheetType)} and used to
    * refuse a silent replace, this scans by runtime alone and is never a refusal ground -- it
    * exists purely to report a count.
    *
    * <p>Self-inclusive by construction: called from {@link SheetJoinService#join} right after
    * {@link #open} has already put the new session into {@link #sessions}, so a solo join returns
    * a list of size 1, not 0 -- there is no separate "am I the only one" branch to keep in sync.
    */
   public List<JoinSession> findAllOnRuntime(String runtimeId) {
      if(runtimeId == null) {
         return List.of();
      }

      long now = clock.getAsLong();
      List<JoinSession> result = new ArrayList<>();

      for(JoinSession s : sessions.values()) {
         if(!s.isExpired(now) && runtimeId.equals(s.runtimeId())) {
            result.add(s);
         }
      }

      return result;
   }

   /**
    * Returns the unexpired session bound to both {@code socketSessionId} and {@code runtimeId},
    * or {@code null} if none is held.
    *
    * <p>The browser-known identity Follow Focus retargets by (see the plan's "Design refinement"
    * section): the browser never holds the agent's own {@code sessionToken} -- only the agent
    * that joined does -- so a client-driven retarget/pop/toggle has to address a session by what
    * the browser DOES know: {@code socketSessionId} (server-derived from the STOMP session,
    * unspoofable by the client) plus {@code runtimeId} (the sheet it is editing). Sibling to
    * {@link #findOpen}, which keys on {@code (ownerIdentity, sheetType)} instead -- a different
    * lookup, not an overload of that one into ambiguity.
    */
   public JoinSession findBySocketAndRuntime(String socketSessionId, String runtimeId) {
      if(socketSessionId == null || runtimeId == null) {
         return null;
      }

      long now = clock.getAsLong();

      for(JoinSession s : sessions.values()) {
         if(!s.isExpired(now) && runtimeId.equals(s.runtimeId()) &&
            socketSessionId.equals(s.socketSessionId()))
         {
            return s;
         }
      }

      return null;
   }

   /**
    * Returns the unexpired session owned by {@code ownerIdentity} with
    * {@link JoinSession#crossSheetFollowEnabled()} {@code true}, or {@code null} if none is held.
    * Structurally like {@link #findEstablishedDirectly}, but this is the toggle-ON half of the
    * feature's own state rather than the {@code establishedDirectly} flag alone -- the whole
    * point of cross-sheet-follow is that the session's {@code runtimeId} is changing, so it
    * cannot be part of the lookup key (design doc section 7.2).
    *
    * <p>Looked up by identity, not {@code socketSessionId}: a directly-established (D10) session's
    * {@code socketSessionId} is permanently {@code null} (it is never paired to any one browser
    * tab -- see {@link #openEstablishedDirectly}), so an exact socket match could never find one.
    * {@code ownerIdentity} is instead resolved from the caller's own authenticated STOMP-frame
    * principal, which is unspoofable the same way an HTTP-authenticated request's principal is
    * (PSP-028; mirrors {@link SheetAgentBroadcastService#sendToComposerByIdentity}'s own
    * identity-based resolution, added for the sibling PSP-027 bug).
    *
    * <p>At most one such session is expected live per identity (mirroring section 2's own
    * assumption that a second portal mint from the same identity replaces, not coexists with, the
    * first) -- not explicitly re-confirmed for this specific case; the multi-tab/same-identity
    * edge case is an accepted, documented limitation (design doc section 7.7 item 4; see also
    * {@code SheetAgentBroadcastService.sendToComposerByIdentity}'s own "first match" note), not
    * silently solved here.
    */
   private JoinSession findCrossSheetFollowByIdentity(String ownerIdentity) {
      return findByIdentityAndFlag(ownerIdentity, JoinSession::crossSheetFollowEnabled);
   }

   /**
    * Shared identity-keyed lookup behind {@link #findEstablishedDirectly} and
    * {@link #findCrossSheetFollowByIdentity} -- both need "the one unexpired session owned by this
    * identity with some particular boolean flag set", differing only in which flag.
    */
   private JoinSession findByIdentityAndFlag(String ownerIdentity, Predicate<JoinSession> flag) {
      if(ownerIdentity == null) {
         return null;
      }

      long now = clock.getAsLong();

      for(JoinSession s : sessions.values()) {
         if(!s.isExpired(now) && flag.test(s) && ownerIdentity.equals(s.ownerIdentity())) {
            return s;
         }
      }

      return null;
   }

   /**
    * Turns Follow Focus on or off for the session bound to {@code (socketSessionId, runtimeId)}.
    * This is the server-side half of "explicit" (see the design doc): {@link #retarget} refuses
    * to move a session's target while this is {@code false}, independent of whatever the
    * Angular UI shows -- even a compromised or buggy browser tab cannot retarget a session that
    * never had Follow Focus turned on for it.
    *
    * <p>A no-op (returns {@code null}, touches nothing) if no session matches -- mirroring
    * {@link #detach}'s silent-no-op shape for a non-matching case, since the caller (the STOMP
    * toggle endpoint) is fire-and-forget with no reply to report a refusal on.
    */
   public JoinSession setFollowFocus(String socketSessionId, String runtimeId, boolean enabled) {
      JoinSession session = findBySocketAndRuntime(socketSessionId, runtimeId);

      if(session == null) {
         return null;
      }

      JoinSession updated = withFollowFocusEnabled(session, enabled);
      sessions.put(session.sessionToken(), updated);
      return updated;
   }

   /**
    * Retargets an opted-in session's {@code editorContext} in place, keyed by
    * {@code (socketSessionId, runtimeId)} -- see {@link #findBySocketAndRuntime}. This is the
    * mechanism that removes per-pane re-pairing friction: the session's {@code sessionToken}
    * never changes, so the agent's very next tool call against that SAME token simply resolves
    * against the new target, with no {@code connect_sheet} and no action on the agent side at
    * all (the Phase 1 exit criterion).
    *
    * <p>Refuses, named, rather than silently no-opping, and touches nothing on any refusal:
    * <ul>
    *   <li>no session matches {@code (socketSessionId, runtimeId)};</li>
    *   <li>the matched session has never had Follow Focus enabled ({@link #setFollowFocus}) --
    *       the server-side half of "explicit and visible" this whole mechanism depends on;</li>
    *   <li>{@code next} names a location the runtime does not actually have, per
    *       {@link SheetPairingService#validateEditorContext} -- reused, not re-derived, so a
    *       retarget to a location that does not exist fails the same way a mint to one does
    *       today, rather than silently storing garbage.</li>
    * </ul>
    *
    * <p>On success: the session's CURRENT {@code editorContext} (possibly {@code null}, for a
    * session still at its original whole-sheet scope) is pushed onto its focus stack before the
    * session is reconstructed with {@code editorContext = next} and stored back under the same
    * token -- the reconstruct-and-replace shape {@link #resolve} already uses to refresh
    * {@code lastAccess}.
    */
   public JoinSession retarget(String socketSessionId, String runtimeId, EditorContext next)
      throws PairingException
   {
      JoinSession session = findBySocketAndRuntime(socketSessionId, runtimeId);

      if(session == null) {
         throw new PairingException(PairingException.Kind.SESSION_EXPIRED,
            "No live session for socket " + socketSessionId + " and runtime " + runtimeId);
      }

      if(!session.followFocusEnabled()) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "Follow Focus is not enabled for this session -- enable it before retargeting.");
      }

      pairing.validateEditorContext(session.sheetType(), runtimeId, session.ownerIdentity(), next);

      focusStacks.computeIfAbsent(session.sessionToken(), k -> new ConcurrentLinkedDeque<>())
         .push(new FocusFrame(session.editorContext()));
      JoinSession retargeted = withEditorContext(session, next);
      sessions.put(session.sessionToken(), retargeted);
      return retargeted;
   }

   /**
    * Pops the session bound to {@code (socketSessionId, runtimeId)} back to whatever
    * {@code editorContext} preceded its most recent {@link #retarget}.
    *
    * <p>Popping past the bottom of an already-empty (or never-pushed) stack is a no-op that
    * leaves the session's {@code editorContext} <b>exactly as it already is</b> -- not an error
    * and not a further change. This is deliberately NOT "reset to null": a session's original
    * scope is whatever it was AT OPEN TIME, which is only null for a whole-sheet session --
    * forcing null here would incorrectly widen a session that was itself opened already
    * pane-scoped, the first time its stack ran out.
    *
    * <p>Returns {@code null} both when no session matches {@code (socketSessionId, runtimeId)}
    * and when a session matched but its stack was already exhausted. Those two cases differ in
    * what happened to STATE (nothing existed to touch, versus a genuine no-op on a real
    * session) but are identical in what a caller needs to do about the RETURN VALUE: nothing
    * moved, so there is nothing to react to. {@link SheetPairingController#popFocusViaSocket}
    * depends on this -- it broadcasts a {@code focusChanged} notice whenever this returns
    * non-null -- so returning the unchanged session on an exhausted stack (as an earlier
    * version of this method did) fired a spurious broadcast on every no-op pop.
    */
   public JoinSession popFocus(String socketSessionId, String runtimeId) {
      JoinSession session = findBySocketAndRuntime(socketSessionId, runtimeId);

      if(session == null) {
         return null;
      }

      Deque<FocusFrame> stack = focusStacks.get(session.sessionToken());
      FocusFrame frame = stack == null ? null : stack.poll();

      if(frame == null) {
         // Exhausted (or never pushed): the session's editorContext is left untouched -- see
         // the javadoc above -- but null is still returned, same as "no session matched", so a
         // caller with no other use for the return value (popFocusViaSocket) sees one signal
         // for "nothing changed" rather than having to compare the returned session against
         // what it already had.
         return null;
      }

      JoinSession restored = withEditorContext(session, frame.editorContext());
      sessions.put(session.sessionToken(), restored);
      return restored;
   }

   /**
    * Cross-sheet-follow's own retarget: when the browser reports a newly-focused
    * {@code runtimeId}/{@code sheetType} over {@code /wiz/pairing/current-focus}, this finds the
    * caller's own cross-sheet-follow-enabled session (if any) and mutates it IN PLACE to the new
    * runtime -- new {@code runtimeId}/{@code sheetType}, {@code editorContext} reset to
    * {@code null} (a freshly-focused sheet has no pane-level detail yet), {@code lastAccess}
    * refreshed -- rather than spawning a new session or token (design doc section 7.2). Unlike
    * {@link #retarget} (Follow Focus), which moves {@code editorContext} within one
    * {@code runtimeId}, this moves the {@code runtimeId} itself: the session's
    * {@code sessionToken} never changes, so the agent's very next tool call against that same
    * token simply resolves against the newly-focused sheet, with zero agent action.
    *
    * <p>Looked up by {@code ownerIdentity} (the caller's own authenticated STOMP-frame principal),
    * not {@code socketSessionId} -- see {@link #findCrossSheetFollowByIdentity}'s javadoc for why
    * a directly-established session's socket is permanently {@code null} and cannot be used as a
    * lookup key (PSP-028).
    *
    * <p><b>No match</b> (no cross-sheet-follow-enabled session for this identity, or {@code
    * runtimeId}/{@code sheetType} missing): silent no-op (returns {@code null}), mirroring
    * {@link #detach}/{@link #popFocus}'s existing fire-and-forget, nothing-to-report shape -- this
    * is a passive background sync the browser is not waiting on a reply for.
    *
    * <p><b>Ownership mismatch</b>: re-runs the exact ownership check
    * {@code SheetJoinService.join}'s step 3b already performs -- {@link SheetRuntimeAccess
    * #getRuntimeOwner(SheetType, String)} for the reported runtime, compared via
    * {@link PairingUtil#sameLogicalUser(String, Principal)} against the session's own recorded
    * {@code ownerIdentity} -- and drops the report silently (logged, session left unchanged) on a
    * genuine mismatch. A {@code null} runtime owner (not found on this node) is tolerated, not
    * treated as a mismatch, mirroring step 3b's own null-tolerant treatment: the focus report is
    * identity-scoped and therefore unspoofable by the agent, but it is still just the browser's own
    * assertion of what it has open, and this is the one place that re-verifies it against the
    * runtime's real recorded owner before trusting it (counter-assertion: "a focus report naming
    * a runtime the identity doesn't actually own is silently dropped, not trusted" -- charter
    * assertion 13).
    */
   public JoinSession syncToCurrentFocus(String ownerIdentity, String runtimeId, SheetType sheetType) {
      JoinSession session = findCrossSheetFollowByIdentity(ownerIdentity);

      if(session == null || runtimeId == null || sheetType == null) {
         return null;
      }

      Principal runtimeOwner = runtimeAccess == null ? null
         : runtimeAccess.getRuntimeOwner(sheetType, runtimeId);

      if(runtimeOwner != null && !PairingUtil.sameLogicalUser(session.ownerIdentity(), runtimeOwner)) {
         LOG.warn("Cross-sheet-follow sync rejected: runtime not owned by session's identity " +
                  "(runtimeId={}, sessionOwner={})", runtimeId, session.ownerIdentity());
         return null;
      }

      JoinSession synced = new JoinSession(session.sessionToken(), runtimeId, session.ownerIdentity(),
                                           sheetType, clock.getAsLong(), session.ttlMillis(),
                                           session.connectionMode(), session.socketSessionId(),
                                           session.socketUserName(), null,
                                           session.followFocusEnabled(), session.establishedDirectly(),
                                           session.crossSheetFollowEnabled());
      sessions.put(session.sessionToken(), synced);
      return synced;
   }

   /**
    * Turns cross-sheet-follow on or off for the caller's own directly-established session, found
    * by identity via {@link #findEstablishedDirectly} -- NOT by {@code socketSessionId}: a
    * directly-established (D10) session is never paired to any one browser tab, so its
    * {@code socketSessionId} is permanently {@code null} (see {@link #openEstablishedDirectly})
    * and can never be used to look one up. {@code ownerIdentity} is the caller's own authenticated
    * STOMP-frame principal, the same source of truth an HTTP-authenticated request's principal
    * already is elsewhere in this codebase -- not client-suppliable, so this is not a weaker check
    * than the socket-keyed lookups {@link #setFollowFocus}/{@link #retarget} use for a pane-scoped
    * session (PSP-028; mirrors {@link SheetAgentBroadcastService#sendToComposerByIdentity}'s own
    * identity resolution, added for the sibling PSP-027 bug).
    *
    * <p>Throws, named, rather than silently no-opping or silently succeeding, when no
    * directly-established session is found for this identity -- charter assertion 11's own
    * load-bearing check: a pane-scoped session (one whose {@link PairingGrant} named a non-null
    * {@code runtimeId} at mint time, i.e. {@link JoinSession#establishedDirectly()} {@code ==
    * false}) can never be the one {@link #findEstablishedDirectly} matches, since
    * {@code establishedDirectly} is recorded once at {@code open()} time and never changed
    * thereafter -- this is the load-bearing server-side check, not merely a UI restriction a
    * direct STOMP message could bypass.
    *
    * @throws PairingException {@code INVALID_ARGUMENT} if no directly-established session is
    *                          held for this identity
    */
   public JoinSession setCrossSheetFollow(String ownerIdentity, boolean enabled)
      throws PairingException
   {
      JoinSession session = findEstablishedDirectly(ownerIdentity);

      if(session == null) {
         throw new PairingException(PairingException.Kind.INVALID_ARGUMENT,
            "Cross-sheet-follow can only be enabled on a directly-established (portal) session " +
            "-- no such session is held for this connection.");
      }

      JoinSession updated = withCrossSheetFollowEnabled(session, enabled);
      sessions.put(session.sessionToken(), updated);
      return updated;
   }

   private static JoinSession withEditorContext(JoinSession s, EditorContext editorContext) {
      return new JoinSession(s.sessionToken(), s.runtimeId(), s.ownerIdentity(), s.sheetType(),
                             s.lastAccess(), s.ttlMillis(), s.connectionMode(),
                             s.socketSessionId(), s.socketUserName(), editorContext,
                             s.followFocusEnabled(), s.establishedDirectly(),
                             s.crossSheetFollowEnabled());
   }

   private static JoinSession withFollowFocusEnabled(JoinSession s, boolean enabled) {
      return new JoinSession(s.sessionToken(), s.runtimeId(), s.ownerIdentity(), s.sheetType(),
                             s.lastAccess(), s.ttlMillis(), s.connectionMode(),
                             s.socketSessionId(), s.socketUserName(), s.editorContext(), enabled,
                             s.establishedDirectly(), s.crossSheetFollowEnabled());
   }

   private static JoinSession withCrossSheetFollowEnabled(JoinSession s, boolean enabled) {
      return new JoinSession(s.sessionToken(), s.runtimeId(), s.ownerIdentity(), s.sheetType(),
                             s.lastAccess(), s.ttlMillis(), s.connectionMode(),
                             s.socketSessionId(), s.socketUserName(), s.editorContext(),
                             s.followFocusEnabled(), s.establishedDirectly(), enabled);
   }

   /**
    * Test-only: whether {@code token} still has a live {@link #focusStacks} entry. No
    * production caller needs this -- it exists purely so a leak-regression test can observe
    * {@link #removeSessionsIf}'s cleanup without a public accessor on production code.
    */
   boolean hasFocusStackEntryForTesting(String token) {
      return focusStacks.containsKey(token);
   }

   @Scheduled(fixedDelay = 10 * 60_000)
   void evictExpired() {
      long now = clock.getAsLong();
      List<JoinSession> ended = removeSessionsIf(s -> s.isExpired(now));
      notifyAgentInactive(ended);
   }

   /**
    * Removes every session matching {@code predicate} from {@link #sessions}, and its
    * {@link #focusStacks} entry alongside it, returning whatever was removed so callers can
    * broadcast over it. Every removal path in this class (explicit {@link #close},
    * socket-close/detach cleanup, and scheduled {@link #evictExpired}) must go through this
    * rather than calling {@code sessions.values().removeIf(...)} directly -- {@code sessionToken}s
    * are never reused, so a {@code focusStacks} entry left behind after its owning session is gone
    * is a permanent leak for the remaining life of the process, not a harmless stale value some
    * later {@code retarget} will overwrite.
    *
    * <p>{@link #close(String)} deliberately does NOT go through this helper -- it is called only
    * by the four REST controllers' {@code detach} endpoints, which already broadcast
    * {@code sendAgentInactive} explicitly at the controller layer (where the acting principal and
    * any error handling the endpoint wants live). If this helper also broadcast, every one of
    * those calls would double-broadcast for the exact same session.
    */
   private List<JoinSession> removeSessionsIf(Predicate<JoinSession> predicate) {
      List<JoinSession> removed = new ArrayList<>();
      sessions.values().removeIf(s -> {
         if(predicate.test(s)) {
            focusStacks.remove(s.sessionToken());
            removed.add(s);
            return true;
         }

         return false;
      });
      return removed;
   }

   /**
    * Best-effort tab-bar notification for every session {@link #removeSessionsIf} just removed.
    * A {@code null} {@link #broadcast} (every back-compat/test constructor) means this is simply
    * a no-op -- those fixtures never exercise this feature. Never throws: a failed notification
    * must never turn a real cleanup into a failure, mirroring {@code SheetJoinService.join}'s
    * treatment of {@code sendPairingJoined}/{@code sendAgentActive}.
    */
   private void notifyAgentInactive(List<JoinSession> ended) {
      if(broadcast == null) {
         return;
      }

      for(JoinSession s : ended) {
         try {
            broadcast.sendAgentInactive(s);
         }
         catch(Exception ex) {
            LOG.warn("Session ended, but notifying the tab bar failed (runtimeId={})",
                     s.runtimeId(), ex);
         }
      }
   }

   private String newToken() {
      StringBuilder sb = new StringBuilder(24);
      for (int i = 0; i < 24; i++) {
         sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
      }
      return sb.toString();
   }
}
