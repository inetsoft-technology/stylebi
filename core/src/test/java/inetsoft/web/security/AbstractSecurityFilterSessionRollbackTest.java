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
 * Bug #77219: after a login was rejected by the license, a later login in the same browser saw
 * the rejected user's session properties, including the OLAP login credentials.
 *
 * AbstractSecurityFilter.createSession() binds a DestinationUserNameProviderPrincipal to its HTTP
 * session before the license check (Bug #77029). The first bind copies the principal's properties,
 * parameters and fields into the per-session attribute map, which belongs to the HTTP session and
 * is shared by every principal bound to it (PRINCIPAL_COOKIE and EM_PRINCIPAL_COOKIE). A rejected
 * login keeps the HTTP session, so those entries have to be rolled back when the session is not
 * created.
 *
 * The HTTP session and the per-session attribute map are modelled like in
 * AbstractSecurityFilterSessionSweepTest; every login here uses the same session id, i.e. the same
 * browser. Overlapping logins are modelled either by a login started from inside the license
 * check of another one (same thread, so not serialized, like two requests on different cluster
 * nodes), or by two threads (same node).
 */

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.UnlicensedUserNameException;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.*;
import inetsoft.sree.web.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.session.SessionAttributeKey;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AbstractSecurityFilterSessionRollbackTest {
   private static final String SID = "sid-77219";
   private static final String OLAP_USER = "__OLAP_LOGIN_USER_NAME__";
   private static final String OLAP_PASSWORD = "__OLAP_LOGIN_USER_PASSWORD__";

   private MockedStatic<Cluster> clusterMock;
   private MockedStatic<SUtil> sUtilMock;
   private MockedStatic<XSessionService> xSessionServiceMock;

   private MockCluster cluster;
   private XSessionService sessionService;
   private AuthenticationService authenticationService;
   private TestSecurityFilter filter;
   private DistributedMap<String, Object> attributes;
   private volatile Throwable rejection;
   // license checks of specific users, by user name
   private final Map<String, LicenseCheck> licenseChecks = new ConcurrentHashMap<>();

   @FunctionalInterface
   private interface LicenseCheck {
      void check(SRPrincipal principal) throws Throwable;
   }

   @FunctionalInterface
   private interface Body {
      void run() throws Exception;
   }

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
      cluster = new MockCluster();
      clusterMock = mockStatic(Cluster.class);
      clusterMock.when(Cluster::getInstance).thenReturn(cluster);
      // IgniteSessionRepository.createSession() writes the marker that the session's attributes
      // exist
      cluster.<SessionAttributeKey, Object>getReplicatedMap(
         IgniteSessionRepository.class.getName() + ".sessionAttributes")
         .put(new SessionAttributeKey(SID, IgniteSessionRepository.class.getName() + ".created"), 0L);
      attributes = IgniteSessionRepository.getSessionAttributeMap(SID);

      sUtilMock = mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sUtilMock.when(SUtil::getUserSessionTimeout).thenReturn(0);
      sUtilMock.when(SUtil::isMultiTenant).thenReturn(false);

      sessionService = mock(XSessionService.class);
      lenient().when(sessionService.createSessionID(anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(0, String.class) + "-session");
      xSessionServiceMock = mockStatic(XSessionService.class);
      xSessionServiceMock.when(XSessionService::getService).thenReturn(sessionService);

      SessionLicenseManager licenseManager = mock(SessionLicenseManager.class);
      SessionLicenseServiceProvider provider = mock(SessionLicenseServiceProvider.class);
      when(provider.getSessionLicenseManager()).thenReturn(licenseManager);

      authenticationService = mock(AuthenticationService.class);
      doAnswer(inv -> {
         SRPrincipal p = inv.getArgument(0);
         // the license manager sees the bound principal (Bug #77029), and may write to it
         p.setProperty("__internal__", "false");
         LicenseCheck check = licenseChecks.get(p.getIdentityID().getName());

         if(check != null) {
            check.check(p);
         }
         else if(rejection != null) {
            throw rejection;
         }

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

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "named-user", "concurrent", "sessions-exceeded", "runtime", "error" })
   void rejectedLoginLeavesNoPropertiesForLaterLogin(String kind) throws Exception {
      rejection = rejection(kind);
      DestinationUserNameProviderPrincipal a = principal("userA");
      a.setProperty(OLAP_USER, "userA");
      a.setProperty(OLAP_PASSWORD, "A-secret");
      a.setProperty("a.only", "A-value");
      a.setParameter("a.param", "A-param");

      if(rejection instanceof Error) {
         assertThrows(AssertionError.class, () -> login(a));
      }
      else {
         assertThrows(AuthenticationFailureException.class, () -> login(a));
      }

      assertEquals(Map.of(), new HashMap<>(attributes),
         "a rejected login must not leave entries in the session attribute map");

      // a later SSO login in the same browser (createSSOSession wraps a new, unbound principal)
      rejection = null;
      DestinationUserNameProviderPrincipal b = principal("userB");
      b.setProperty("b.only", "B-value");
      login(b);

      assertNull(b.getProperty(OLAP_USER));
      assertNull(b.getProperty(OLAP_PASSWORD));
      assertNull(b.getProperty("a.only"));
      assertNull(b.getParameter("a.param"));
      assertEquals(Set.of("b.only", "__internal__"), b.getPropertyNames());
      assertEquals(Set.of(), b.getParameterNames());
   }

   @Test
   void rejectedLoginRestoresEntriesOfOtherPrincipalInSession() throws Exception {
      // an EM principal is already bound to the same HTTP session and shares the key namespace
      DestinationUserNameProviderPrincipal em = principal("em");
      em.setProperty("shared", "em-value");
      em.setProperty(SUtil.EM_USER, "true");
      em.setLastAccess(1000L);
      em.setHttpSessionId(SID);
      Map<String, Object> before = new HashMap<>(attributes);

      rejection = new UnlicensedUserNameException("not a named user");
      DestinationUserNameProviderPrincipal a = principal("userA");
      a.setProperty("shared", "A-value");
      a.setProperty("a.only", "A-value");
      a.setLastAccess(2000L);

      assertThrows(AuthenticationFailureException.class, () -> login(a));

      assertEquals(before, new HashMap<>(attributes));
      assertEquals("em-value", em.getProperty("shared"));
      assertNull(em.getProperty("a.only"));
      assertEquals(1000L, em.getLastAccess());
   }

   @Test
   void rejectedLoginKeepsWritesOfOtherPrincipalDuringLicenseCheck() throws Exception {
      DestinationUserNameProviderPrincipal em = principal("em");
      em.setProperty("shared", "em-value");
      em.setProperty(SUtil.EM_USER, "true");
      em.setLastAccess(1000L);
      em.setHttpSessionId(SID);

      DestinationUserNameProviderPrincipal a = principal("userA");
      a.setProperty("shared", "A-value");
      a.setProperty("a.only", "A-value");
      a.setLastAccess(2000L);
      licenseChecks.put("userA", p -> {
         // a request of the EM principal while the login is in its license check
         em.setLastAccess(5000L);
         em.setProperty("em.new", "em-new-value");
         throw new UnlicensedUserNameException("not a named user");
      });

      assertThrows(AuthenticationFailureException.class, () -> login(a));

      assertEquals(5000L, em.getLastAccess());
      assertEquals("em-new-value", em.getProperty("em.new"));
      assertEquals("em-value", em.getProperty("shared"));
      assertNull(em.getProperty("a.only"));
   }

   @Test
   void rejectedLoginOverlappingSuccessfulLoginKeepsItsEntries() throws Exception {
      DestinationUserNameProviderPrincipal b = principal("userB");
      b.setProperty("shared", "B-value");
      b.setProperty("b.only", "B-value");
      b.setLastAccess(3000L);

      DestinationUserNameProviderPrincipal a = principal("userA");
      a.setProperty("shared", "A-value");
      a.setProperty("a.only", "A-value");
      a.setProperty(OLAP_PASSWORD, "A-secret");
      a.setLastAccess(2000L);
      licenseChecks.put("userA", p -> {
         // B logs in and succeeds while A is in its license check
         login(b);
         throw new UnlicensedUserNameException("not a named user");
      });

      assertThrows(AuthenticationFailureException.class, () -> login(a));

      assertEquals("B-value", b.getProperty("shared"));
      assertEquals("B-value", b.getProperty("b.only"));
      assertEquals(3000L, b.getLastAccess());
      assertNull(b.getProperty("a.only"));
      assertNull(b.getProperty(OLAP_PASSWORD));
   }

   @Test
   void overlappingRejectedLoginsLeaveNoEntries() throws Exception {
      CountDownLatch a1InLicenseCheck = new CountDownLatch(1);
      CountDownLatch a2InLicenseCheck = new CountDownLatch(1);
      CountDownLatch a1Done = new CountDownLatch(1);
      licenseChecks.put("userA1", p -> {
         a1InLicenseCheck.countDown();
         // let the second login run into its own license check if it is not held back
         a2InLicenseCheck.await(1, TimeUnit.SECONDS);
         throw new UnlicensedUserNameException("not a named user");
      });
      licenseChecks.put("userA2", p -> {
         a2InLicenseCheck.countDown();
         // and be rejected after the first one
         a1Done.await(5, TimeUnit.SECONDS);
         throw new UnlicensedUserNameException("not a named user");
      });

      DestinationUserNameProviderPrincipal a1 = principal("userA1");
      a1.setProperty(OLAP_USER, "userA1");
      a1.setProperty(OLAP_PASSWORD, "A1-secret");
      DestinationUserNameProviderPrincipal a2 = principal("userA2");
      a2.setProperty(OLAP_USER, "userA2");
      a2.setProperty(OLAP_PASSWORD, "A2-secret");

      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
         Future<?> first = executor.submit(inThread(() -> {
            try {
               assertThrows(AuthenticationFailureException.class, () -> login(a1));
            }
            finally {
               a1Done.countDown();
            }
         }));
         assertTrue(a1InLicenseCheck.await(5, TimeUnit.SECONDS));
         Future<?> second = executor.submit(inThread(
            () -> assertThrows(AuthenticationFailureException.class, () -> login(a2))));

         first.get(10, TimeUnit.SECONDS);
         second.get(10, TimeUnit.SECONDS);
      }
      finally {
         executor.shutdownNow();
      }

      assertEquals(Map.of(), new HashMap<>(attributes),
         "overlapping rejected logins must not leave entries in the session attribute map");
   }

   @Test
   void rejectedLoginOfAlreadyBoundPrincipalTouchesNothing() throws Exception {
      DestinationUserNameProviderPrincipal a = principal("userA");
      a.setProperty("a.only", "A-value");
      a.setHttpSessionId(SID);
      Map<String, Object> before = new HashMap<>(attributes);
      before.put("DestinationUserNameProviderPrincipal.PROP.__internal__", "false");

      rejection = new LicenseException("sessions exceeded");
      assertThrows(AuthenticationFailureException.class, () -> login(a));

      // this call did not perform the first bind, so it does not undo it either
      assertEquals(before, new HashMap<>(attributes));
      assertEquals("A-value", a.getProperty("a.only"));
   }

   @Test
   void successfulLoginStillBindsPrincipal() throws Exception {
      DestinationUserNameProviderPrincipal b = principal("userB");
      b.setProperty(OLAP_USER, "userB");
      b.setLastAccess(3000L);

      HttpSession session = login(b);

      verify(session).setAttribute(RepletRepository.PRINCIPAL_COOKIE, b);
      assertEquals("userB", attributes.get("DestinationUserNameProviderPrincipal.PROP." + OLAP_USER));
      assertEquals(3000L, attributes.get("DestinationUserNameProviderPrincipal.FIELD.lastAccess"));
      assertEquals("false", b.getProperty("__internal__"));
   }

   private static Throwable rejection(String kind) {
      return switch(kind) {
         case "named-user" -> new UnlicensedUserNameException("not a named user");
         case "concurrent" -> new LicenseException("sessions exceeded");
         case "sessions-exceeded" -> new SessionsExceededException("limit", List.of());
         case "runtime" -> new IllegalStateException("failed");
         case "error" -> new AssertionError("failed");
         default -> throw new IllegalArgumentException(kind);
      };
   }

   /** Runs a login on another thread, which needs its own static mocks. */
   private Callable<Void> inThread(Body body) {
      return () -> {
         try(MockedStatic<Cluster> threadCluster = mockStatic(Cluster.class);
             MockedStatic<SUtil> threadSUtil = mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
             MockedStatic<XSessionService> threadSessionService =
                mockStatic(XSessionService.class))
         {
            threadCluster.when(Cluster::getInstance).thenReturn(cluster);
            threadSUtil.when(SUtil::getUserSessionTimeout).thenReturn(0);
            threadSUtil.when(SUtil::isMultiTenant).thenReturn(false);
            threadSessionService.when(XSessionService::getService).thenReturn(sessionService);
            body.run();
         }

         return null;
      };
   }

   private HttpSession login(DestinationUserNameProviderPrincipal principal) throws Exception {
      HttpSession session = mock(HttpSession.class);
      lenient().when(session.getId()).thenReturn(SID);
      doAnswer(inv -> {
         // same as IgniteSessionRepository.IgniteSession.setAttribute()
         if(RepletRepository.PRINCIPAL_COOKIE.equals(inv.getArgument(0)) &&
            inv.getArgument(1) instanceof DestinationUserNameProviderPrincipal p)
         {
            p.setHttpSessionId(SID);
         }

         return null;
      }).when(session).setAttribute(anyString(), any());

      HttpServletRequest request = mock(HttpServletRequest.class);
      lenient().when(request.getSession(true)).thenReturn(session);
      lenient().when(request.getRemoteHost()).thenReturn("localhost");

      filter.createSession(request, principal);
      return session;
   }

   private static DestinationUserNameProviderPrincipal principal(String name) {
      return new DestinationUserNameProviderPrincipal(
         new IdentityID(name, Organization.getDefaultOrganizationID()), new IdentityID[0],
         new String[0], Organization.getDefaultOrganizationID(), name.hashCode());
   }
}
