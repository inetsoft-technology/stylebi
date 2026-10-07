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
package inetsoft.web.session;

/*
 * Redmine #76953 -- root-cause fix for the distributed session-removal race.
 *
 * The original report: an active user's session was force-removed mid-edit in the VS Wizard,
 * deregistering their principal cluster-wide ("did not log in" on every subsequent action). The
 * diagnosis chain (docs/teams/2026-09-23-bugs-76953) built up a theory around
 * IgniteSessionRepository.entryRemoved()'s "not expired" branch: any node observing a session-cache
 * removal independently re-derives its own expiry verdict from the removed MapSession and, on
 * disagreement, unconditionally calls logout() for that session's principal -- a structural defect
 * since a node's local view can be stale/racy relative to the session's real, more-current state.
 *
 * Auditing every removal call site (below) turned up something the diagnosis chain did not check:
 * entryRemoved()/entryExpired() both called logout(Session, String) with the RAW MapSession from
 * the Ignite cache event (event.getOldValue()) -- not the IgniteSession wrapper the rest of this
 * class uses everywhere else. IgniteSession.setAttribute()/getAttribute() only ever touch the
 * per-session DistributedMap (see getSessionAttributeMap()); they never write through to the
 * underlying MapSession's own attribute map. So the MapSession stored in (and read back out of) the
 * Ignite cache never has RepletRepository.PRINCIPAL_COOKIE set on it -- confirmed directly against
 * MapSession's bytecode (spring-session-core 3.2.1): getAttribute()/setAttribute() operate solely
 * on its own private `sessionAttrs` field, which nothing in this codebase ever populates for a
 * cache-stored session. That made logout()'s `session.getAttribute(PRINCIPAL_COOKIE)` resolve to
 * null whenever it was called from entryRemoved()/entryExpired(), for EVERY removal, regardless of
 * branch -- both branches called logout(), and both were no-ops.
 *
 * Fix round 1 addressed the one gap that had NO other deregistration path at all: invalidateSession()
 * (what EM -> Monitoring -> Users -> Sessions -> Remove calls) now calls logout() explicitly, using
 * the properly-wrapped IgniteSession it already holds.
 *
 * Fix round 1's commit message additionally claimed deleteById() (natural, per-node-driven session
 * expiry via findById()) was "already covered" because the same node's own entryExpired() reaction
 * would deregister the principal. An adversarial refute pass (docs/teams/.../07-refute-root-cause.md)
 * disproved this: entryExpired() had the EXACT SAME raw-MapSession bug as entryRemoved()'s "not
 * expired" branch, for BOTH of its trigger paths (the internal findById()->deleteById() dispatch,
 * and genuine native Ignite cache-level TTL expiry, which real Ignite dispatches DIRECTLY to
 * entryExpired(), bypassing entryRemoved() entirely). That meant ordinary, routine
 * session-expiry-driven deregistration was ALSO silently broken -- a materially bigger gap than the
 * one case (admin force-remove) fix round 1 actually addressed.
 *
 * Fix round 2 (this round) widens the fix to cover both of those:
 *   - deleteById() now calls logout() directly with the IgniteSession it already constructs before
 *     removing the cache entry (mirrors invalidateSession()'s round-1 fix).
 *   - entryExpired() now wraps its raw MapSession in a fresh IgniteSession before calling logout(),
 *     so the native-TTL-dispatch path (no other caller already holding a wrapped session) also
 *     resolves the principal correctly. This is NOT the same risk as "fixing" entryRemoved()'s "not
 *     expired" branch would be: by the time entryExpired() runs, the session is already being (or
 *     about to be) removed from the cache regardless of what logout() does, so this only keeps
 *     SecurityEngine.users consistent with a removal that is happening anyway.
 *
 * entryRemoved()'s "not expired" branch remains deliberately untouched and still passes the bare
 * MapSession (still an intentional no-op): that branch's own isExpired() re-check is a per-node,
 * possibly-stale/racy re-derivation of expiry, so making its logout() call resolve a real principal
 * would reintroduce exactly the false-positive cross-node logout this investigation is about -- see
 * logout()'s javadoc.
 *
 * Redmine #77178 -- websocket close-code regression, root-caused to the SAME raw-MapSession defect
 * described above, but on the EVENT side rather than the logout() side: entryRemoved()'s "not
 * expired" branch published its SessionDeletedEvent from the raw MapSession (deliberately, mirroring
 * its inert logout() call -- but the event has no such cross-node-false-logout risk, since
 * publishing it force-closes nothing by itself), and entryExpired() published its SessionExpiredEvent
 * from the raw `session` one line after wrapping a correctly-attributed IgniteSession for its own
 * logout() call just above (an oversight, not a deliberate choice, unlike entryRemoved()'s branch).
 * Either way, the published event's getPrincipalCookie()/getLoggedOutAttribute() always resolved
 * null, which SessionConnectionService cannot distinguish from a genuinely anonymous session --
 * whichever of this null-attributed event and the correctly-attributed sibling event (from
 * deleteById()/invalidateSession()) is processed first decides the websocket close code. The fix:
 * publish both events from a properly-wrapped IgniteSession (entryRemoved()'s branch builds one
 * purely for the event; entryExpired() reuses the wrapper it already built for logout()) without
 * changing either method's logout()/deregistration behavior at all.
 *
 * The tests below are structural/unit-level only, against a fully in-memory MockCluster (no real
 * Ignite, no real multi-node timing) -- they prove the claims above, not the live GKE incident.
 */

import inetsoft.sree.RepletRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.security.AuthenticationProvider;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.audit.SessionRecord;
import inetsoft.web.admin.server.NodeProtectionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Uses a real (in-memory) Cluster bean via Spring only so the internal static
 * {@code Cluster.getInstance()} calls (used by {@code getSessionAttributeMap()} when constructing
 * an {@link IgniteSessionRepository.IgniteSession}) resolve. The repository under test gets its own
 * fresh {@link MockCluster} instance per test (not the shared Spring bean) so each test's replicated
 * map listener registration doesn't leak into the next test.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { inetsoft.test.BaseTestConfiguration.class },
                       initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class IgniteSessionRepositoryTest {
   @Mock private SecurityEngine securityEngine;
   @Mock private AuthenticationService authenticationService;
   @Mock private NodeProtectionService nodeProtectionService;

   private MockCluster cluster;
   private IgniteSessionRepository repository;

   @BeforeEach
   void setUp() {
      cluster = new MockCluster();
      repository = new IgniteSessionRepository(
         securityEngine, authenticationService, nodeProtectionService, cluster);
      repository.afterPropertiesSet();
   }

   @AfterEach
   void tearDown() throws Exception {
      repository.destroy();
   }

   /**
    * The confirmed, fixed gap: invalidateSession() (EM "Remove Session") previously relied
    * entirely on entryRemoved()'s downstream reaction to deregister the principal -- which the
    * sibling test below shows never actually resolves a principal. Now it does so directly.
    */
   @Test
   void invalidateSession_deregistersPrincipal() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      when(securityEngine.isActiveUser(principal)).thenReturn(true);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      assertNotNull(repository.findById(id), "session should exist before invalidation");

      repository.invalidateSession(id);

      verify(authenticationService).logout(same(principal), eq("127.0.0.1"), eq(""));
      assertNull(repository.findById(id), "session should be gone after invalidation");
   }

   /**
    * Central finding driving the fix design, still true after fix round 2: entryRemoved()'s "not
    * expired" branch -- the branch the original diagnosis blamed for spuriously deregistering an
    * active user -- still calls logout() with the bare MapSession from the cache event, whose
    * attribute map is never populated, and remains deliberately untouched. This reproduces exactly
    * the removal shape loggedOut() performs (a direct cache-level remove, no paired
    * SessionExpiredEvent/SessionLoggedOutEvent of its own) on a LIVE, properly-attributed session,
    * and asserts authenticationService is never touched -- proving the branch is still inert, not
    * merely redundant. This is exactly why the fix targets invalidateSession()/deleteById()/
    * entryExpired() directly instead of "fixing" entryRemoved()'s own "not expired" classification
    * logic (doing so would reintroduce the false-positive cross-node logout race -- see
    * logout()'s javadoc).
    */
   @Test
   void entryRemoved_notExpiredBranch_neverResolvesPrincipal_currentlyNoOp() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      // lenient: isActiveUser() must NOT even be reached for this call, but stub it anyway so a
      // regression that does reach it fails on the logout() verification below, not on a
      // Mockito "unnecessary stubbing" complaint unrelated to what this test is proving.
      lenient().when(securityEngine.isActiveUser(principal)).thenReturn(true);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      // Sanity check the premise: through the wrapped IgniteSession, attribute resolution works.
      assertSame(principal,
                 repository.findById(id).getAttribute(RepletRepository.PRINCIPAL_COOKIE),
                 "attribute resolution via the wrapped IgniteSession must work");

      // Simulate loggedOut()'s removal shape: a direct cache-level remove with no explicit event
      // of its own (unlike invalidateSession()/deleteById()), for a session that is NOT expired.
      // This is the same underlying map repository.sessions wraps (MockCluster caches maps by
      // name), so it fires entryRemoved() on `repository` exactly as a real removal would.
      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      boolean removed = rawSessions.remove(id);
      assertTrue(removed, "the raw cache entry should have existed");

      verifyNoInteractions(authenticationService);
   }

   /**
    * Bug #77178: entryRemoved()'s "not expired" branch deliberately keeps logout() inert (see the
    * test above), but the SessionDeletedEvent it publishes is consumed downstream (e.g.
    * SessionConnectionService, to pick a websocket close code) and must NOT be equally inert --
    * before this fix, the event was built directly from the raw MapSession, so its
    * getPrincipalCookie()/getLoggedOutAttribute() always resolved null, indistinguishable from a
    * genuinely anonymous session. This asserts the published event now resolves the real
    * principal via a properly-wrapped IgniteSession, while the sibling test above confirms
    * logout() itself is unaffected (still inert) by this change.
    */
   @Test
   void entryRemoved_notExpiredBranch_publishesEventWithResolvedPrincipal() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      AtomicReference<SessionDeletedEvent> captured = new AtomicReference<>();
      cluster.addMessageListener(event -> {
         if(event.getMessage() instanceof SessionDeletedEvent deletedEvent) {
            captured.set(deletedEvent);
         }
      });

      // Same removal shape as the sibling test: a direct cache-level remove, no explicit event
      // of its own, for a session that is NOT expired -- this is what triggers entryRemoved()'s
      // "not expired" branch.
      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      boolean removed = rawSessions.remove(id);
      assertTrue(removed, "the raw cache entry should have existed");

      SessionDeletedEvent event = captured.get();
      assertNotNull(event, "entryRemoved()'s \"not expired\" branch should publish a SessionDeletedEvent");
      assertSame(principal, event.getPrincipalCookie(),
                 "the published event must resolve the real principal, not null from the raw " +
                 "MapSession (bug #77178)");
   }

   /**
    * Fix round 2, gap 1: deleteById() (the internal findById()->deleteById() dispatch for a
    * per-node-observed-expired session) previously relied on the same node's own entryExpired()
    * reaction to deregister the principal -- which, pre-fix, was just as inert as
    * entryRemoved()'s "not expired" branch (see the class javadoc above). Now deleteById() calls
    * logout() directly with the IgniteSession it already constructs before removing the cache
    * entry.
    */
   @Test
   void deleteById_deregistersPrincipal() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      when(securityEngine.isActiveUser(principal)).thenReturn(true);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      assertNotNull(repository.findById(id), "session should exist before deletion");

      repository.deleteById(id);

      verify(authenticationService)
         .logout(same(principal), eq("127.0.0.1"), eq(SessionRecord.LOGOFF_SESSION_TIMEOUT));
      assertNull(repository.findById(id), "session should be gone after deleteById()");
   }

   /**
    * Fix round 2, gap 2: entryExpired() dispatched directly by a genuine native Ignite cache-level
    * TTL expiry (bypassing entryRemoved()/deleteById() entirely -- confirmed as a real dispatch
    * route in 07-refute-root-cause.md) previously received only the bare MapSession from the cache
    * event, so logout() always resolved a null principal there too. This calls entryExpired()
    * directly with a hand-built EntryEvent carrying a raw MapSession (the exact shape native
    * Ignite's CacheEventListenerAdapter constructs), with the underlying cache entry and the
    * per-session attribute map both still present -- i.e. nothing has already deregistered this
    * principal via any other path -- and verifies logout() now resolves it correctly.
    */
   @Test
   void entryExpired_nativeTtlDispatch_deregistersPrincipal() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      when(securityEngine.isActiveUser(principal)).thenReturn(true);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      org.springframework.session.MapSession rawSession = rawSessions.get(id);
      assertNotNull(rawSession, "raw MapSession backing the cache entry should exist");

      inetsoft.sree.internal.cluster.EntryEvent<String, org.springframework.session.MapSession>
         event = new inetsoft.sree.internal.cluster.EntryEvent<>(
            IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, id, rawSession, null);

      repository.entryExpired(event);

      verify(authenticationService)
         .logout(same(principal), eq("127.0.0.1"), eq(SessionRecord.LOGOFF_SESSION_TIMEOUT));
   }

   /**
    * Bug #77300: on the node that lazily reaps an expired session, deleteById()'s own logout() and
    * the same node's entryRemoved()/entryExpired() reaction to that removal run on different
    * threads and both pass the non-atomic isActiveUser() gate, writing two LOGOFF records. Here
    * entryExpired() (path B) is still inside authenticationService.logout() -- with the principal
    * still reported active -- when deleteById() (path A) runs on another thread; only one logout
    * may be issued.
    */
   @Test
   void concurrentTimeoutLogouts_forOneSession_logOutOnce() throws Exception {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      when(securityEngine.isActiveUser(principal)).thenReturn(true);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      org.springframework.session.MapSession rawSession = rawSessions.get(id);
      inetsoft.sree.internal.cluster.EntryEvent<String, org.springframework.session.MapSession>
         event = new inetsoft.sree.internal.cluster.EntryEvent<>(
            IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, id, rawSession, null);

      AtomicInteger calls = new AtomicInteger();
      AtomicReference<Throwable> otherFailure = new AtomicReference<>();
      AtomicBoolean otherFinished = new AtomicBoolean();

      doAnswer(invocation -> {
         if(calls.incrementAndGet() == 1) {
            Thread other = new Thread(() -> {
               try {
                  repository.deleteById(id);
                  otherFinished.set(true);
               }
               catch(Throwable e) {
                  otherFailure.set(e);
               }
            });
            other.start();
            other.join(TimeUnit.SECONDS.toMillis(10));
         }

         return null;
      }).when(authenticationService).logout(any(), anyString(), anyString());

      repository.entryExpired(event);

      assertNull(otherFailure.get(), "concurrent deleteById() should not fail");
      assertTrue(otherFinished.get(), "concurrent deleteById() should return without blocking");
      verify(authenticationService, times(1))
         .logout(same(principal), eq("127.0.0.1"), eq(SessionRecord.LOGOFF_SESSION_TIMEOUT));
   }

   /**
    * Bug #77300: the per-session claim is released once logout() returns, so a later caller for
    * the same session is not skipped by the claim -- it is stopped by isActiveUser(), which the
    * first logout (synchronously deregistering the principal) has turned false.
    */
   @Test
   void sequentialTimeoutLogouts_forOneSession_secondGatedByIsActiveUser() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      AtomicBoolean active = new AtomicBoolean(true);
      when(securityEngine.isActiveUser(principal)).thenAnswer(invocation -> active.get());
      doAnswer(invocation -> {
         active.set(false);
         return null;
      }).when(authenticationService).logout(any(), anyString(), anyString());

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      org.springframework.session.MapSession rawSession = rawSessions.get(id);
      inetsoft.sree.internal.cluster.EntryEvent<String, org.springframework.session.MapSession>
         event = new inetsoft.sree.internal.cluster.EntryEvent<>(
            IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, id, rawSession, null);

      repository.deleteById(id);
      repository.entryExpired(event);

      verify(securityEngine, times(2)).isActiveUser(principal);
      verify(authenticationService, times(1))
         .logout(same(principal), eq("127.0.0.1"), eq(SessionRecord.LOGOFF_SESSION_TIMEOUT));
   }

   /**
    * Bug #76973: the user edit path rewrites the roles of every live session principal of the
    * edited user (both cookies), so an existing session is corrected on every node that reads the
    * replicated session. Other users' principals are left alone.
    */
   @Test
   void updatePrincipalRolesAndGroups_rewritesLiveSessionPrincipals() {
      IdentityID user = new IdentityID("x", "host-org");
      IdentityID everyone = new IdentityID("Everyone", "host-org");
      IdentityID admin = new IdentityID("Administrator", null);
      SRPrincipal principal = new SRPrincipal(
         user, new IdentityID[] { everyone, admin }, new String[0], "host-org", 1L);
      SRPrincipal emPrincipal = new SRPrincipal(
         user, new IdentityID[] { everyone, admin }, new String[0], "host-org", 2L);
      SRPrincipal other = new SRPrincipal(
         new IdentityID("y", "host-org"), new IdentityID[] { everyone, admin }, new String[0],
         "host-org", 3L);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      session.setAttribute(RepletRepository.EM_PRINCIPAL_COOKIE, emPrincipal);
      repository.save(session);
      IgniteSessionRepository.IgniteSession otherSession = repository.createSession();
      otherSession.setAttribute(RepletRepository.PRINCIPAL_COOKIE, other);
      repository.save(otherSession);

      AuthenticationProvider provider = mock(AuthenticationProvider.class);
      when(provider.getAllRoles(any(IdentityID[].class))).thenAnswer(inv -> inv.getArgument(0));
      when(provider.getAllGroups(any(IdentityID[].class))).thenReturn(new IdentityID[0]);
      repository.updatePrincipalRolesAndGroups(
         user, new IdentityID[] { everyone }, new String[0], provider);

      IgniteSessionRepository.IgniteSession reread = repository.findById(session.getId());
      SRPrincipal updated = reread.getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      SRPrincipal updatedEm = reread.getAttribute(RepletRepository.EM_PRINCIPAL_COOKIE);
      SRPrincipal untouched =
         repository.findById(otherSession.getId()).getAttribute(RepletRepository.PRINCIPAL_COOKIE);
      assertArrayEquals(new IdentityID[] { everyone }, updated.getRoles());
      assertArrayEquals(new IdentityID[] { everyone }, updatedEm.getRoles());
      assertArrayEquals(new IdentityID[] { everyone, admin }, untouched.getRoles());
   }

   /**
    * Bug #77178: entryExpired() already wrapped the raw MapSession in an IgniteSession for its
    * logout() call (fix round 2 of #76953, see the test above), but one line later it published
    * the SessionExpiredEvent using the unwrapped raw `session` instead of reusing that wrapper --
    * an apparent oversight, not a deliberate choice (unlike entryRemoved()'s "not expired"
    * branch). That meant every SessionExpiredEvent this method published, for both dispatch
    * shapes (routed via entryRemoved()'s isExpired() re-check, and genuine native Ignite TTL
    * dispatch reproduced here), always resolved a null principal downstream, regardless of
    * logout() itself working correctly. This asserts the published event now resolves the real
    * principal too.
    */
   @Test
   void entryExpired_nativeTtlDispatch_publishesEventWithResolvedPrincipal() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      when(securityEngine.isActiveUser(principal)).thenReturn(true);

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();

      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      org.springframework.session.MapSession rawSession = rawSessions.get(id);
      assertNotNull(rawSession, "raw MapSession backing the cache entry should exist");

      AtomicReference<SessionExpiredEvent> captured = new AtomicReference<>();
      cluster.addMessageListener(evt -> {
         if(evt.getMessage() instanceof SessionExpiredEvent expiredEvent) {
            captured.set(expiredEvent);
         }
      });

      inetsoft.sree.internal.cluster.EntryEvent<String, org.springframework.session.MapSession>
         event = new inetsoft.sree.internal.cluster.EntryEvent<>(
            IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, id, rawSession, null);

      repository.entryExpired(event);

      SessionExpiredEvent expiredEvent = captured.get();
      assertNotNull(expiredEvent, "entryExpired() should publish a SessionExpiredEvent");
      assertSame(principal, expiredEvent.getPrincipalCookie(),
                 "the published event must resolve the real principal, not null from the raw " +
                 "MapSession (bug #77178)");
   }

   /**
    * Regression test for Bug #77306: {@code SESSION_ATTRIBUTE_MAPS} was a single static,
    * unsynchronized {@code HashMap} shared by every session on the node, mutated concurrently by
    * request threads ({@code createSessionAttributeMap()}/{@code getSessionAttributeMap()}'s
    * cold-path put) and the Ignite cache-event listener path ({@code destroySessionAttributeMap()}
    * from {@code entryExpired()}/{@code entryRemoved()}). The refuter demonstrated experimentally
    * that two threads concurrently {@code put}/{@code remove}-ing on a plain {@code HashMap} --
    * regardless of resize, bucket collision, or total map size -- reliably corrupts it badly
    * enough to spuriously null out an entirely unrelated key, with the corruption persisting for
    * the life of the map.
    *
    * <p>This exercises the exact static field via reflection (rather than routing through full
    * session/EntryEvent/Mockito plumbing, which is too slow to reach the operation volume needed
    * to reliably surface the corruption within a reasonable test time -- confirmed by hand before
    * writing this version, see the fix write-up), with several writer threads doing real
    * concurrent {@code put}/{@code remove} churn while a reader thread continuously reads one
    * untouched "victim" key, asserting it is never spuriously lost or corrupted.
    *
    * <p><b>Round 2 (independent verification finding, 04-verify.md):</b> the first version of this
    * test churned disjoint, sequential keys ({@code prefix + i}), which measured only ~90%
    * reliable against the un-fixed {@code HashMap} (18/20 fail, 2/20 false-negative over 20 runs)
    * -- with the map staying small (well under the 16-bucket/12-entry default resize threshold),
    * most churn keys land in a *different* bucket from the victim key purely by chance, and it is
    * corruption of the victim's own bucket that actually surfaces as a lost/corrupted read. This
    * version instead precomputes, per writer thread, a small set of keys forced (via
    * {@link #hashMapBucket}/{@link #findCollidingKeyIndex}) into the exact same {@code HashMap}
    * bucket as the victim key -- the same technique the refuter used in {@code 02-refute.md} --
    * making every churn {@code put()}/{@code remove()} a genuine same-bucket collision with the
    * victim key instead of leaving collision to chance. Manually reverting
    * {@code SESSION_ATTRIBUTE_MAPS} to a plain {@code HashMap} makes this test fail reliably
    * (confirmed across repeated runs); it passes reliably with the fixed
    * {@code ConcurrentHashMap}.
    */
   @Test
   void concurrentSessionAttributeMapAccess_neverCorruptsUnrelatedKey() throws Exception {
      Field field = IgniteSessionRepository.class.getDeclaredField("SESSION_ATTRIBUTE_MAPS");
      field.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<String, Object> map = (Map<String, Object>) field.get(null);

      String victimKey = "bug-77306-victim-" + UUID.randomUUID();
      Object victimValue = new Object();
      map.put(victimKey, victimValue);

      try {
         int writerThreads = 4;
         int iterations = 200_000;

         // Force every writer thread's churn keys into the SAME HashMap bucket as the victim key
         // (see the class-level javadoc "Round 2" note above) instead of leaving the collision to
         // chance.
         int capacity = expectedHashMapCapacity(map.size());
         int victimBucket = hashMapBucket(victimKey, capacity);
         int keysPerThread = 8;
         String[][] collidingKeys = new String[writerThreads][];

         for(int t = 0; t < writerThreads; t++) {
            String prefix = "bug-77306-churn-" + t + "-";
            String[] keys = new String[keysPerThread];
            int searchIndex = 0;

            for(int k = 0; k < keysPerThread; k++) {
               searchIndex = findCollidingKeyIndex(prefix, searchIndex, victimBucket, capacity);
               keys[k] = prefix + searchIndex;
               searchIndex++;
            }

            collidingKeys[t] = keys;
         }

         ExecutorService pool = Executors.newFixedThreadPool(writerThreads + 1);
         CountDownLatch start = new CountDownLatch(1);
         AtomicBoolean failed = new AtomicBoolean(false);
         AtomicReference<String> failureDetail = new AtomicReference<>();
         List<Future<?>> futures = new ArrayList<>();

         // Writer threads: repeatedly put(), then remove(), one of a small set of keys
         // precomputed to collide into the victim key's own HashMap bucket -- mirrors
         // createSessionAttributeMap()'s put() racing destroySessionAttributeMap()'s remove() for
         // unrelated sessions, forced into the exact bucket-collision scenario that reliably
         // corrupts a plain HashMap.
         for(int t = 0; t < writerThreads; t++) {
            String[] keys = collidingKeys[t];

            futures.add(pool.submit(() -> {
               try {
                  start.await();

                  for(int i = 0; i < iterations && !failed.get(); i++) {
                     String key = keys[i % keys.length];
                     map.put(key, new Object());
                     map.remove(key);
                  }
               }
               catch(Exception e) {
                  failed.set(true);
                  failureDetail.set("writer thread failed: " + e);
               }
            }));
         }

         // Reader thread: continuously reads the victim key -- this must never come back
         // null/wrong, regardless of how much unrelated put()/remove() churn is happening
         // concurrently on the shared static map.
         futures.add(pool.submit(() -> {
            try {
               start.await();

               for(int i = 0; i < writerThreads * iterations && !failed.get(); i++) {
                  Object read = map.get(victimKey);

                  if(read != victimValue) {
                     failed.set(true);
                     failureDetail.set(
                        "victim key was lost/corrupted at iteration " + i + ": got " + read);
                     break;
                  }
               }
            }
            catch(Exception e) {
               failed.set(true);
               failureDetail.set("reader thread failed: " + e);
            }
         }));

         start.countDown();

         for(Future<?> future : futures) {
            future.get(120, TimeUnit.SECONDS);
         }

         pool.shutdown();

         assertFalse(failed.get(), failureDetail.get());
      }
      finally {
         map.remove(victimKey);
      }
   }

   /**
    * Regression test for Bug #77306 review finding 2 (05-review-r1.md): getSessionAttributeMap()'s
    * cold path "fetch but don't cache" branch -- reached when the per-session replicated map still
    * exists in the cluster but the underlying session has already been swept from
    * DEFAULT_SESSION_MAP_NAME -- must be preserved by the computeIfAbsent() rewrite (fix part 2).
    * Before this test, only AbstractSecurityFilterSessionSweepTest exercised this cold path, and
    * only incidentally: it never asserts on caching behavior, so a subtly-broken reimplementation
    * (caching unconditionally, or never caching at all) would still pass it.
    */
   @Test
   void getSessionAttributeMap_fetchesWithoutCaching_whenUnderlyingSessionGone() throws Exception {
      String sessionId = "bug-77306-nocache-" + UUID.randomUUID();
      String mapName =
         IgniteSessionRepository.class.getName() + ".sessionAttributeMap." + sessionId;
      Cluster instanceCluster = Cluster.getInstance();
      inetsoft.sree.internal.cluster.DistributedMap<String, Object> attrMap =
         instanceCluster.getReplicatedMap(mapName);
      attrMap.put("k", "v");

      Map<String, Object> cacheField = sessionAttributeMapsField();

      try {
         Cache<String, Object> sessionCache =
            instanceCluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME);
         assertFalse(cacheField.containsKey(sessionId),
                     "sanity check: nothing should have cached this session id yet");
         assertFalse(sessionCache.containsKey(sessionId),
                     "sanity check: the underlying session must be absent for this to hit the " +
                     "intended branch");

         inetsoft.sree.internal.cluster.DistributedMap<String, Object> first =
            IgniteSessionRepository.getSessionAttributeMap(sessionId);
         assertNotNull(first, "the map still exists in the cluster and should be fetched");
         assertEquals("v", first.get("k"));
         assertFalse(cacheField.containsKey(sessionId),
                     "must not cache the map once the underlying session is confirmed gone " +
                     "(Bug #77306 review finding 2)");

         inetsoft.sree.internal.cluster.DistributedMap<String, Object> second =
            IgniteSessionRepository.getSessionAttributeMap(sessionId);
         assertNotNull(second, "a second call should still re-fetch, not fail");
         assertFalse(cacheField.containsKey(sessionId), "a second call must still not cache it");
      }
      finally {
         cacheField.remove(sessionId);
         instanceCluster.destroyReplicatedMap(mapName);
      }
   }

   /**
    * Regression test for Bug #77306 review finding 1/2 (05-review-r1.md): getAttribute(),
    * setAttribute(), removeAttribute(), and getAttributeNames() must all handle
    * getSessionAttributeMap() returning null gracefully (return null/empty, or no-op) instead of
    * NPE-ing. Exercises the null-guards this fix adds directly, rather than relying on the
    * swallowed-403 symptom (or a passing but non-asserting cold-path test) to ever hit them.
    */
   @Test
   void igniteSession_handlesMissingAttributeMapGracefully() throws Exception {
      IgniteSessionRepository.IgniteSession session = repository.createSession();
      String id = session.getId();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, "some-value");
      repository.save(session);

      assertEquals("some-value", session.getAttribute(RepletRepository.PRINCIPAL_COOKIE),
                   "sanity check: attribute resolution must work before the map is torn down");

      // Simulate the map being destroyed out from under a still-referenced IgniteSession ("could
      // be out of sync due to session expiration", per the fix's own comment): remove the cached
      // entry AND destroy the underlying replicated map so getSessionAttributeMap(id) genuinely
      // returns null rather than silently re-fetching it.
      String mapName = IgniteSessionRepository.class.getName() + ".sessionAttributeMap." + id;
      sessionAttributeMapsField().remove(id);
      Cluster.getInstance().destroyReplicatedMap(mapName);

      assertNull(session.getAttribute(RepletRepository.PRINCIPAL_COOKIE),
                 "getAttribute() must return null, not NPE, when the map is gone");
      assertDoesNotThrow(() -> session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, "other"),
                          "setAttribute() must no-op, not NPE, when the map is gone");
      assertDoesNotThrow(() -> session.removeAttribute(RepletRepository.PRINCIPAL_COOKIE),
                          "removeAttribute() must no-op, not NPE, when the map is gone");
      assertTrue(session.getAttributeNames().isEmpty(),
                 "getAttributeNames() must return empty, not NPE, when the map is gone");
   }

   /**
    * Bug #77886: in production the repository's session cache is a
    * {@code withExpiryPolicy(PropertyAccessedExpiryPolicy)} view, so every {@code get} through it
    * renews the session's Ignite TTL. A background scan that read each live session through
    * findById() (a {@code get}) kept an idle session's TTL from firing at its timeout. The scans
    * must read the iterated value instead and never {@code get} a live session.
    * ({@link MockCluster} ignores the expiry policy, so this counts {@code get} calls instead.)
    */
   @Test
   void backgroundScans_doNotReadLiveSessionsThroughRenewingGet() throws Exception {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);

      AtomicInteger gets = countGets();

      repository.checkSessions();
      assertEquals(1, repository.getActiveSessions().size(), "the live session is still listed");
      repository.findByIndexNameAndIndexValue(
         FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "admin");
      repository.updatePrincipalRolesAndGroups(
         new IdentityID("nobody", "host-org"), new IdentityID[0], new String[0],
         mock(AuthenticationProvider.class));

      assertEquals(0, gets.get(), "background scans must not renew a live session's TTL");
      verifyNoInteractions(authenticationService);
      assertNotNull(repository.findById(session.getId()), "the live session must not be deleted");
   }

   /**
    * Bug #77886: checkSessions() skipped sessions past their timeout, so nothing deleted an idle
    * session that no other reader touched; it lived until Ignite's TTL fired. It must now delete
    * the expired session and log it off with the session-timeout reason.
    */
   @Test
   void checkSessions_deletesExpiredSession() {
      SRPrincipal principal = mockPrincipal("admin", "127.0.0.1");
      // the logout deregisters the principal, as AuthenticationService does, so the cache-removal
      // listener's own reaction to the same deletion is gated by isActiveUser()
      AtomicBoolean active = new AtomicBoolean(true);
      when(securityEngine.isActiveUser(principal)).thenAnswer(invocation -> active.get());
      doAnswer(invocation -> {
         active.set(false);
         return null;
      }).when(authenticationService).logout(any(), anyString(), anyString());

      IgniteSessionRepository.IgniteSession session = repository.createSession();
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      String id = session.getId();
      session.setLastAccessedTime(Instant.now().minus(session.getMaxInactiveInterval())
                                     .minusSeconds(1));
      repository.save(session);

      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      assertTrue(rawSessions.get(id).isExpired(), "premise: the stored session is expired");

      repository.checkSessions();

      verify(authenticationService, times(1))
         .logout(same(principal), eq("127.0.0.1"), eq(SessionRecord.LOGOFF_SESSION_TIMEOUT));
      assertNull(rawSessions.get(id), "checkSessions() must delete the expired session");
   }

   /**
    * Bug #77886: getActiveSessions() no longer reads live sessions through the renewing get, but
    * it must still sweep expired ones. It is the periodic deleter at the default monitor level
    * (MonitorSchedulingTask -> UserService.updateMetrics0, every 30 s on every node), so a scan
    * that only listed sessions would leave an expired one to Ignite's TTL. The expired session must
    * be deleted and logged off with the session-timeout reason, and not listed; the live one must
    * be listed and kept.
    */
   @Test
   void getActiveSessions_deletesExpiredSessionAndKeepsLiveOne() {
      SRPrincipal expiredPrincipal = mockPrincipal("expired", "127.0.0.1");
      SRPrincipal livePrincipal = mockPrincipal("live", "127.0.0.2");
      AtomicBoolean active = new AtomicBoolean(true);
      when(securityEngine.isActiveUser(expiredPrincipal)).thenAnswer(invocation -> active.get());
      doAnswer(invocation -> {
         active.set(false);
         return null;
      }).when(authenticationService).logout(same(expiredPrincipal), anyString(), anyString());

      IgniteSessionRepository.IgniteSession expired = repository.createSession();
      expired.setAttribute(RepletRepository.PRINCIPAL_COOKIE, expiredPrincipal);
      repository.save(expired);
      String expiredId = expired.getId();
      expired.setLastAccessedTime(Instant.now().minus(expired.getMaxInactiveInterval())
                                     .minusSeconds(1));
      repository.save(expired);

      IgniteSessionRepository.IgniteSession live = repository.createSession();
      live.setAttribute(RepletRepository.PRINCIPAL_COOKIE, livePrincipal);
      repository.save(live);
      String liveId = live.getId();

      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);
      assertTrue(rawSessions.get(expiredId).isExpired(), "premise: the stored session is expired");

      List<SRPrincipal> listed = repository.getActiveSessions();

      assertEquals(1, listed.size(), "only the live session is listed");
      assertSame(livePrincipal, listed.get(0), "only the live session is listed");
      verify(authenticationService, times(1))
         .logout(same(expiredPrincipal), eq("127.0.0.1"), eq(SessionRecord.LOGOFF_SESSION_TIMEOUT));
      verify(authenticationService, never()).logout(same(livePrincipal), anyString(), anyString());
      assertNull(rawSessions.get(expiredId), "getActiveSessions() must delete the expired session");
      assertNotNull(rawSessions.get(liveId), "the live session must be kept");
   }

   /**
    * Bug #77886: the Ignite TTL of a session entry must outlast the session timeout by a margin.
    * A session is ended (LOGOFF record, license release, SessionExpiredEvent) by a sweep that finds
    * its expired MapSession still in the cache and deletes it. With TTL == timeout the entry
    * vanished the moment the session expired, so no sweep could see it and, because Ignite's own
    * expiry never reached entryExpired(), the session ended with no logout at all.
    */
   @Test
   void expiryPolicy_keepsEntryPastSessionTimeoutForTheSweep() {
      String oldTimeout = SreeEnv.getProperty("http.session.timeout");

      try {
         SreeEnv.setProperty("http.session.timeout", "60");
         PropertyAccessedExpiryPolicy policy = new PropertyAccessedExpiryPolicy();
         javax.cache.expiry.Duration ttl = new javax.cache.expiry.Duration(
            TimeUnit.SECONDS, 60 + PropertyAccessedExpiryPolicy.TTL_MARGIN_SECONDS);

         assertEquals(ttl, policy.getExpiryForCreation());
         assertEquals(ttl, policy.getExpiryForAccess());
         assertNull(policy.getExpiryForUpdate(), "an update must still leave the TTL unchanged");
         assertTrue(PropertyAccessedExpiryPolicy.TTL_MARGIN_SECONDS >= 40,
                    "the margin must cover several 10 s checkSessions periods");
         assertEquals(java.time.Duration.ofSeconds(60),
                      repository.createSession().getMaxInactiveInterval(),
                      "the session itself still times out at http.session.timeout");
      }
      finally {
         if(oldTimeout == null) {
            SreeEnv.remove("http.session.timeout");
         }
         else {
            SreeEnv.setProperty("http.session.timeout", oldTimeout);
         }
      }
   }

   /**
    * Bug #77886: checkSessions() now deletes expired sessions inside its scan. One failed deletion
    * must not abort the pass and leave the other expired sessions (and the expiring-soon warnings)
    * for the next pass.
    */
   @Test
   void checkSessions_continuesPassWhenOneDeletionFails() {
      doThrow(new IllegalStateException("audit store down"))
         .when(authenticationService).logout(any(), anyString(), anyString());
      when(securityEngine.isActiveUser(any())).thenReturn(true);
      List<String> ids = new ArrayList<>();

      for(int i = 0; i < 2; i++) {
         IgniteSessionRepository.IgniteSession session = repository.createSession();
         session.setAttribute(RepletRepository.PRINCIPAL_COOKIE,
                              mockPrincipal("user" + i, "127.0.0." + (i + 1)));
         repository.save(session);
         session.setLastAccessedTime(Instant.now().minus(session.getMaxInactiveInterval())
                                        .minusSeconds(1));
         repository.save(session);
         ids.add(session.getId());
      }

      assertDoesNotThrow(() -> repository.checkSessions());

      // two calls per session at most (deleteById, then the removal listener's entryExpired);
      // what matters is that the pass reached the second session after the first one failed
      verify(authenticationService, atLeast(2)).logout(any(), anyString(), anyString());
      @SuppressWarnings("unchecked")
      Cache<String, org.springframework.session.MapSession> rawSessions =
         cluster.getCache(IgniteSessionRepository.DEFAULT_SESSION_MAP_NAME, true, null);

      for(String id : ids) {
         assertNull(rawSessions.get(id), "every expired session must be deleted in one pass");
      }
   }

   /**
    * Replaces the repository's session cache with a proxy that counts {@code get} calls and
    * delegates everything to the original cache.
    */
   @SuppressWarnings("unchecked")
   private AtomicInteger countGets() throws Exception {
      Field field = IgniteSessionRepository.class.getDeclaredField("sessions");
      field.setAccessible(true);
      Cache<String, org.springframework.session.MapSession> delegate =
         (Cache<String, org.springframework.session.MapSession>) field.get(repository);
      AtomicInteger gets = new AtomicInteger();
      Object proxy = Proxy.newProxyInstance(
         Cache.class.getClassLoader(), new Class<?>[] { Cache.class },
         (p, method, args) -> {
            if("get".equals(method.getName())) {
               gets.incrementAndGet();
            }

            try {
               return method.invoke(delegate, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }
         });
      field.set(repository, proxy);
      return gets;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> sessionAttributeMapsField() throws Exception {
      Field field = IgniteSessionRepository.class.getDeclaredField("SESSION_ATTRIBUTE_MAPS");
      field.setAccessible(true);
      return (Map<String, Object>) field.get(null);
   }

   /**
    * Computes the bucket index a plain {@code java.util.HashMap} (default load factor 0.75,
    * default initial capacity 16, doubling on resize) would place {@code key} into, mirroring
    * {@code HashMap.hash()}'s own spread function ({@code h ^ (h >>> 16)}) and its
    * {@code (capacity - 1) & hash} bucket index formula.
    */
   private static int hashMapBucket(Object key, int capacity) {
      int h = key.hashCode();
      h ^= (h >>> 16);
      return h & (capacity - 1);
   }

   /** Mirrors HashMap's own resize decision (grow once size exceeds capacity * loadFactor). */
   private static int expectedHashMapCapacity(int size) {
      int capacity = 16;

      while(size > capacity * 0.75f) {
         capacity <<= 1;
      }

      return capacity;
   }

   /**
    * Finds the smallest {@code i >= startIndex} such that {@code prefix + i} falls into
    * {@code targetBucket} for the given (assumed) HashMap capacity, brute-force -- with a
    * ~1/capacity hit rate per candidate this resolves in a handful of iterations in practice.
    */
   private static int findCollidingKeyIndex(String prefix, int startIndex, int targetBucket,
                                             int capacity)
   {
      for(int i = startIndex; ; i++) {
         if(hashMapBucket(prefix + i, capacity) == targetBucket) {
            return i;
         }
      }
   }

   private static SRPrincipal mockPrincipal(String name, String ip) {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      ClientInfo user = new ClientInfo(new IdentityID(name, "host-org"), ip);
      when(principal.getUser()).thenReturn(user);
      return principal;
   }
}
