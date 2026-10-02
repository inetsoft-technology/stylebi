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
package inetsoft.web.security;

/*
 * Bug #77029: with server.type=server_cluster, every new login logged out users who had been
 * logged in longer than repository.user.timeout, even if they were active.
 *
 * AbstractSecurityFilter.createSession() hands the new principal to the session license manager
 * (addSession) and then stores it in the HTTP session (setAttribute), which is what stamps a
 * DestinationUserNameProviderPrincipal with its HTTP session id. ConcurrentSessionClusterService
 * puts the principal into an Ignite replicated map, which serializes it at put time. If that
 * happens before the stamp, the stored copy has no session id, so its getLastAccess() can never
 * reach the distributed per-session lastAccess field kept current by RequestPrincipalFilter and
 * stays frozen at the login time. The Bug #27464 sweep at the top of createSession() then logs the
 * user out on the next login once the timeout has passed.
 *
 * How this test models the production pieces:
 *  - the cluster license manager: AuthenticationService.addSession() stores a Java-serialization
 *    round-trip of the principal, which is what an Ignite put does (SRPrincipal is Externalizable).
 *    MockCluster would store the reference and hide the bug, so it is NOT used for the license map.
 *  - the non-server_cluster license manager (SessionLicenseService.ConcurrentSessionService): the
 *    original instance is stored.
 *  - the HTTP session: getId() returns a fixed id and setAttribute(PRINCIPAL_COOKIE, dunpp) stamps
 *    the principal, exactly like IgniteSessionRepository.IgniteSession.setAttribute().
 *  - the per-session attribute map: a MockCluster replicated map with the production name, so
 *    IgniteSessionRepository.getSessionAttributeMap(sid) resolves it via Cluster.mapExists(). Its
 *    values are Longs, so reference semantics there do not matter.
 *  - a later request: RequestPrincipalFilter works on a deserialized session copy of the principal
 *    and calls setLastAccess(now); the test does the same.
 */

import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MockCluster;
import inetsoft.sree.security.*;
import inetsoft.sree.web.SessionLicenseManager;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.session.IgniteSessionRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.*;
import java.util.*;
import java.security.Principal;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AbstractSecurityFilterSessionSweepTest {
   private static final int TIMEOUT = 60_000;
   private static final AtomicLong SEQ = new AtomicLong();

   private MockedStatic<Cluster> clusterMock;
   private MockedStatic<SUtil> sUtilMock;
   private MockedStatic<XSessionService> xSessionServiceMock;

   private AuthenticationService authenticationService;
   private TestSecurityFilter filter;
   private final Set<SRPrincipal> licenses = new HashSet<>();
   private boolean serializingLicenseManager;

   private static final class TestSecurityFilter extends AbstractSecurityFilter {
      TestSecurityFilter(SessionLicenseServiceProvider provider, AuthenticationService service) {
         super(provider, service);
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
         throws IOException, ServletException
      {
         chain.doFilter(request, response);
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      MockCluster cluster = new MockCluster();
      clusterMock = mockStatic(Cluster.class);
      clusterMock.when(Cluster::getInstance).thenReturn(cluster);

      sUtilMock = mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sUtilMock.when(SUtil::getUserSessionTimeout).thenReturn(TIMEOUT);
      // SRPrincipal.toString() consults it; Mockito calls toString() when reporting a failure
      sUtilMock.when(SUtil::isMultiTenant).thenReturn(false);

      XSessionService sessionService = mock(XSessionService.class);
      lenient().when(sessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-" + SEQ.incrementAndGet());
      xSessionServiceMock = mockStatic(XSessionService.class);
      xSessionServiceMock.when(XSessionService::getService).thenReturn(sessionService);

      SessionLicenseManager licenseManager = mock(SessionLicenseManager.class);
      when(licenseManager.getActiveSessions()).thenAnswer(inv -> new HashSet<>(licenses));
      SessionLicenseServiceProvider provider = mock(SessionLicenseServiceProvider.class);
      when(provider.getSessionLicenseManager()).thenReturn(licenseManager);

      authenticationService = mock(AuthenticationService.class);
      doAnswer(inv -> {
         SRPrincipal p = inv.getArgument(0);
         licenses.add(serializingLicenseManager ? roundTrip(p) : p);
         return null;
      }).when(authenticationService).addSession(any(Principal.class));

      filter = new TestSecurityFilter(provider, authenticationService);
   }

   @AfterEach
   void tearDown() {
      clusterMock.close();
      sUtilMock.close();
      xSessionServiceMock.close();
   }

   @Test
   void clusterLicenseCopyTracksLastAccessOfLaterRequests() throws Exception {
      serializingLicenseManager = true;
      long now = System.currentTimeMillis();
      DestinationUserNameProviderPrincipal u1 = principal("u1");
      u1.setLastAccess(now - 2L * TIMEOUT);
      login(u1);

      request(u1, now);

      SRPrincipal licenseCopy = licenses.iterator().next();
      assertNotSame(u1, licenseCopy);
      assertEquals(now, licenseCopy.getLastAccess(),
         "the license manager's serialized copy must see the lastAccess written by a later " +
            "request; it was frozen at the login time");
   }

   @ParameterizedTest(name = "serializing license manager (server_cluster) = {0}")
   @ValueSource(booleans = { true, false })
   void activeUserIsNotLoggedOutByLaterLogin(boolean serializing) throws Exception {
      serializingLicenseManager = serializing;
      long now = System.currentTimeMillis();
      DestinationUserNameProviderPrincipal u1 = principal("u1");
      u1.setLastAccess(now - 2L * TIMEOUT); // logged in longer ago than the timeout
      login(u1);
      request(u1, now);                      // but active just now

      login(principal("u4"));

      verify(authenticationService, never())
         .logout(argThat(p -> u1.equals(p)), any(), any(), anyBoolean());
   }

   @ParameterizedTest(name = "serializing license manager (server_cluster) = {0}")
   @ValueSource(booleans = { true, false })
   void idleUserIsStillLoggedOutByLaterLogin(boolean serializing) throws Exception {
      serializingLicenseManager = serializing;
      long now = System.currentTimeMillis();
      DestinationUserNameProviderPrincipal u2 = principal("u2");
      u2.setLastAccess(now - 2L * TIMEOUT);
      login(u2);                             // and no request since

      login(principal("u4"));

      // Bug #27464: the sweep is the only enforcement of repository.user.timeout, keep it
      verify(authenticationService)
         .logout(argThat(p -> u2.equals(p)), any(), any(), eq(true));
   }

   private void login(DestinationUserNameProviderPrincipal principal) throws Exception {
      String sid = "sid-" + SEQ.incrementAndGet();
      // IgniteSession's constructor creates the per-session attribute map for a new session
      Cluster.getInstance().getReplicatedMap(
         IgniteSessionRepository.class.getName() + ".sessionAttributeMap." + sid);

      HttpSession session = mock(HttpSession.class);
      lenient().when(session.getId()).thenReturn(sid);
      doAnswer(inv -> {
         // same as IgniteSessionRepository.IgniteSession.setAttribute()
         if(RepletRepository.PRINCIPAL_COOKIE.equals(inv.getArgument(0)) &&
            inv.getArgument(1) instanceof DestinationUserNameProviderPrincipal p)
         {
            p.setHttpSessionId(sid);
         }

         return null;
      }).when(session).setAttribute(anyString(), any());

      HttpServletRequest request = mock(HttpServletRequest.class);
      lenient().when(request.getSession(true)).thenReturn(session);
      lenient().when(request.getRemoteHost()).thenReturn("localhost");

      filter.createSession(request, principal);
   }

   /** Models RequestPrincipalFilter updating the deserialized session copy of the principal. */
   private static void request(DestinationUserNameProviderPrincipal principal, long now)
      throws Exception
   {
      roundTrip(principal).setLastAccess(now);
   }

   private static DestinationUserNameProviderPrincipal principal(String name) {
      return new DestinationUserNameProviderPrincipal(
         new IdentityID(name, Organization.getDefaultOrganizationID()), new IdentityID[0],
         new String[0], Organization.getDefaultOrganizationID(), SEQ.incrementAndGet());
   }

   @SuppressWarnings("unchecked")
   private static <T extends Serializable> T roundTrip(T value) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(value);
      }

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         return (T) in.readObject();
      }
   }
}
