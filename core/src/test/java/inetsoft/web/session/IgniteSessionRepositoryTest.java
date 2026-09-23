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
 * entryRemoved()/entryExpired() both call logout(Session, String) with the RAW MapSession from the
 * Ignite cache event (event.getOldValue()) -- not the IgniteSession wrapper the rest of this class
 * uses everywhere else. IgniteSession.setAttribute()/getAttribute() only ever touch the per-session
 * DistributedMap (see getSessionAttributeMap()); they never write through to the underlying
 * MapSession's own attribute map. So the MapSession stored in (and read back out of) the Ignite
 * cache never has RepletRepository.PRINCIPAL_COOKIE set on it -- confirmed directly against
 * MapSession's bytecode (spring-session-core 3.2.1): getAttribute()/setAttribute() operate solely
 * on its own private `sessionAttrs` field, which nothing in this codebase ever populates for a
 * cache-stored session. That makes logout()'s `session.getAttribute(PRINCIPAL_COOKIE)` resolve to
 * null whenever it's called from entryRemoved()/entryExpired(), for EVERY removal, regardless of
 * which branch -- both branches call logout(), and both are currently no-ops.
 *
 * This means entryRemoved()'s "not expired" branch is not currently the live mechanism that
 * deregisters anyone; it's dead code (today). The one real, confirmed gap is invalidateSession()
 * (what EM -> Monitoring -> Users -> Sessions -> Remove calls): it removes the session but --
 * unlike deleteById() (paired, in the common case, with the SAME node's own agreeing entryExpired()
 * reaction) and loggedOut() (paired with SecurityEngine's OWN @EventListener on the same
 * SessionLoggedOutEvent, which deregisters the principal directly from event.getPrincipal(), not
 * from any session's attributes) -- has NO other path that deregisters the principal. Today, an
 * admin force-removing a session leaves that principal considered "active" indefinitely.
 *
 * Fix: invalidateSession() now calls logout() explicitly, using the properly-wrapped IgniteSession
 * it already holds (whose attribute resolution works correctly), instead of relying on
 * entryRemoved()'s downstream, independently-re-derived (and, for every other path, redundant)
 * reaction. entryRemoved()/entryExpired() are deliberately left untouched: since they operate on a
 * per-node, possibly-stale/racy re-derivation of "was this expired", making their logout() calls
 * actually resolve a principal would reintroduce exactly the false-positive cross-node logout this
 * investigation was about -- see logout()'s javadoc.
 *
 * The tests below are structural/unit-level only, against a fully in-memory MockCluster (no real
 * Ignite, no real multi-node timing) -- they prove the two claims above, not the live GKE incident.
 */

import inetsoft.sree.RepletRepository;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.web.admin.server.NodeProtectionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.cache.Cache;

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
    * Central finding driving the fix design: entryRemoved()'s "not expired" branch -- the branch
    * the original diagnosis blamed for spuriously deregistering an active user -- calls logout()
    * with the bare MapSession from the cache event, whose attribute map is never populated. This
    * reproduces exactly the removal shape loggedOut() performs (a direct cache-level remove, no
    * paired SessionExpiredEvent/SessionLoggedOutEvent of its own) on a LIVE, properly-attributed
    * session, and asserts authenticationService is never touched -- proving the branch is
    * currently inert, not merely redundant. This is exactly why the fix targets
    * invalidateSession() directly instead of "fixing" entryRemoved() to resolve the principal
    * correctly (doing so would reintroduce the false-positive cross-node logout race).
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

   private static SRPrincipal mockPrincipal(String name, String ip) {
      SRPrincipal principal = mock(SRPrincipal.class, withSettings().lenient());
      ClientInfo user = new ClientInfo(new IdentityID(name, "host-org"), ip);
      when(principal.getUser()).thenReturn(user);
      return principal;
   }
}
