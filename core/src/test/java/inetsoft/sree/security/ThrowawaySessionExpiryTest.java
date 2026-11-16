/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.sree.security;

import com.nimbusds.jwt.SignedJWT;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.RepletRepository;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteDistributedMap;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.server.NodeProtectionService;
import inetsoft.web.wiz.security.SSOTokenService;
import inetsoft.web.wiz.security.WizServiceAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.cache.CacheAtomicityMode;
import org.apache.ignite.cache.CacheMode;
import org.apache.ignite.cache.CacheWriteSynchronizationMode;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #81146 (reopened): the HTTP session that the wiz plugin path creates for a cookieless
 * {@code Authorization: Bearer} request holds a principal rebuilt from the token. That principal
 * carries the browser's client session id, so its login key equals the browser's. When that
 * throwaway session expires, the repository's sweep deregisters the principal, and the browser's
 * login goes with it although the browser's own HTTP session is alive and in use.
 *
 * <p>Everything from the token to the browser's login state is real: the token comes from
 * {@link SSOTokenService#createSSOToken}, the throwaway session from
 * {@link WizServiceAuthenticationFilter#doFilter} (stored in a real
 * {@link IgniteSessionRepository} session), and its expiry from
 * {@link IgniteSessionRepository#checkSessions} through {@code findById}, {@code deleteById},
 * {@link AuthenticationService#logout} and {@link SecurityEngine#loggedOut}.
 * {@code SecurityEngine.users} is a real {@link IgniteDistributedMap} over a real local Ignite
 * cache (as in {@code SessionLimitEvictionTest}). The sessions cache is the {@code MockCluster}
 * one, so Ignite's own TTL callback ({@code entryExpired}) is not run; the sweep is the path that
 * fires first for every session in production (Bug #77886).
 *
 * <p>The cases other than {@link #throwawayExpiryMustNotDeregisterTheBrowserLogin} are controls:
 * they stay green on the unfixed code and show that the red case is red because of the
 * throwaway, not because of the harness.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class ThrowawaySessionExpiryTest {
   static final String IP = "192.168.65.1";
   static Ignite ignite;
   static Path workDir;
   static int seq;

   final String org = Organization.getDefaultOrganizationID();
   final IdentityID admin = new IdentityID("admin", org);
   final IdentityID other = new IdentityID("other", org);
   MockedStatic<SecurityEngine> engineStatic;
   SecurityEngine realEngine;
   SecurityProvider provider;
   AuthenticationService auth;
   IgniteSessionRepository repository;
   SSOTokenService tokenService;
   WizServiceAuthenticationFilter wizFilter;
   Principal threadPrincipal;

   @BeforeAll
   static void startIgnite() throws Exception {
      int discoPort = freePort();
      int commPort = freePort();
      TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
      ipFinder.setAddresses(Collections.singletonList("127.0.0.1:" + discoPort));
      TcpDiscoverySpi disco = new TcpDiscoverySpi();
      disco.setLocalAddress("127.0.0.1");
      disco.setLocalPort(discoPort);
      disco.setLocalPortRange(0);
      disco.setIpFinder(ipFinder);
      TcpCommunicationSpi comm = new TcpCommunicationSpi();
      comm.setLocalAddress("127.0.0.1");
      comm.setLocalPort(commPort);
      comm.setLocalPortRange(0);
      workDir = Files.createTempDirectory("ignite-81146-throwaway");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("throwaway-" + UUID.randomUUID());
      config.setWorkDirectory(workDir.toString());
      config.setLocalHost("127.0.0.1");
      config.setDiscoverySpi(disco);
      config.setCommunicationSpi(comm);
      config.setMetricsLogFrequency(0);
      config.setPeerClassLoadingEnabled(false);
      ignite = Ignition.start(config);
   }

   @AfterAll
   static void stopIgnite() throws Exception {
      if(ignite != null) {
         ignite.close();
      }

      if(workDir != null) {
         try(var paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
         }
      }
   }

   private static int freePort() throws Exception {
      try(ServerSocket s = new ServerSocket(0)) {
         return s.getLocalPort();
      }
   }

   @BeforeEach
   void setUp() throws Exception {
      threadPrincipal = ThreadContext.getContextPrincipal();
      provider = mock(SecurityProvider.class);
      AuthenticationProvider ap = mock(AuthenticationProvider.class);
      when(provider.getAuthenticationProvider()).thenReturn(ap);
      when(ap.isVirtual()).thenReturn(false);
      when(provider.authenticate(any(), any())).thenReturn(true);
      when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      when(provider.getUserGroups(any())).thenReturn(new String[0]);

      realEngine = new SecurityEngine(mock(inetsoft.report.internal.license.LicenseManager.class),
                                      mock(Cluster.class));
      CacheConfiguration<Object, Object> cc = new CacheConfiguration<>("users81146t-" + (seq++));
      cc.setCacheMode(CacheMode.REPLICATED);
      cc.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
      cc.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
      IgniteCache<Object, Object> cache = ignite.getOrCreateCache(cc);
      setField(realEngine, "users", new IgniteDistributedMap<>(cache));

      engineStatic = mockStatic(SecurityEngine.class,
                                withSettings().defaultAnswer(CALLS_REAL_METHODS));
      engineStatic.when(SecurityEngine::getSecurity).thenReturn(realEngine);

      // the real logout: AuthenticationService publishes a SessionLoggedOutEvent, which Spring
      // delivers to the @EventListener methods SecurityEngine.loggedOut and
      // IgniteSessionRepository.loggedOut
      ApplicationEventPublisher publisher = new ApplicationEventPublisher() {
         @Override
         public void publishEvent(Object event) {
            if(event instanceof SessionLoggedOutEvent logout) {
               realEngine.loggedOut(logout);
               repository.loggedOut(logout);
            }
         }
      };
      auth = new AuthenticationService(
         realEngine, null, null, null, null, mock(SessionLicenseServiceProvider.class),
         publisher, null);

      repository = new IgniteSessionRepository(
         realEngine, auth, mock(NodeProtectionService.class), Cluster.getInstance());
      repository.afterPropertiesSet();

      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair keys = gen.generateKeyPair();
      tokenService = new SSOTokenService(provider);
      setField(tokenService, "ssoKeyPair", keys);
      wizFilter = new WizServiceAuthenticationFilter(
         mock(SessionLicenseServiceProvider.class), auth);
      setField(wizFilter, "ssoKeyPair", keys);
   }

   @AfterEach
   void tearDown() {
      engineStatic.close();
      ThreadContext.setContextPrincipal(threadPrincipal);
   }

   /**
    * The defect. The browser is logged in and active; a plugin request without a cookie, carrying
    * a token minted from the browser's login, creates a throwaway session; only that session
    * expires. The browser must stay logged in.
    */
   @Test
   void throwawayExpiryMustNotDeregisterTheBrowserLogin() throws Exception {
      Browser browser = login(admin);
      String token = tokenService.createSSOToken(browser.principal, "http://localhost");

      // premise of the case: the token names the browser's own login session
      assertEquals(browser.sid, clientSessionOf(token));

      String throwawaySid = pluginRequestWithoutCookie(token);
      assertNotEquals(browser.sid, throwawaySid, "the throwaway is a separate HTTP session");
      assertTrue(realEngine.isActiveUser(browser.principal));

      expire(throwawaySid);
      touch(browser.sid);
      repository.checkSessions();

      assertNull(repository.findById(throwawaySid), "the sweep ended the throwaway session");
      assertNotNull(repository.findById(browser.sid), "the browser's HTTP session is alive");
      assertTrue(realEngine.isActiveUser(browser.principal),
                 "the browser's login was deregistered by the expiry of the throwaway session");
   }

   /** Control: the expiry of another user's session does not touch the browser. */
   @Test
   void controlExpiryOfAnotherUsersSessionLeavesTheBrowserAlone() throws Exception {
      Browser browser = login(admin);
      Browser stranger = login(other);

      expire(stranger.sid);
      touch(browser.sid);
      repository.checkSessions();

      assertNull(repository.findById(stranger.sid));
      assertFalse(realEngine.isActiveUser(stranger.principal), "the sweep does log out an expired "
         + "session (so the harness exercises the real expiry path)");
      assertTrue(realEngine.isActiveUser(browser.principal));
      assertNotNull(repository.findById(browser.sid));
   }

   /**
    * Control: a throwaway whose token names a login session that is not the browser's (a stale
    * token) expires without effect on the browser.
    */
   @Test
   void controlThrowawayWithADifferentSessionIdLeavesTheBrowserAlone() throws Exception {
      Browser browser = login(admin);
      ClientInfo stale = new ClientInfo(admin, IP, "a-stale-session-id");
      SRPrincipal stalePrincipal = new SRPrincipal(stale, new IdentityID[0], new String[0], org,
                                                   SecurityEngine.getRandom().nextLong());
      String token = tokenService.createSSOToken(stalePrincipal, "http://localhost");
      assertNotEquals(browser.sid, clientSessionOf(token));

      String throwawaySid = pluginRequestWithoutCookie(token);
      expire(throwawaySid);
      touch(browser.sid);
      repository.checkSessions();

      assertNull(repository.findById(throwawaySid), "the sweep ended the throwaway session");
      assertTrue(realEngine.isActiveUser(browser.principal));
      assertNotNull(repository.findById(browser.sid));
   }

   /** Control: while the throwaway lives, nothing is deregistered. */
   @Test
   void controlLiveThrowawayLeavesTheBrowserAlone() throws Exception {
      Browser browser = login(admin);
      String token = tokenService.createSSOToken(browser.principal, "http://localhost");
      String throwawaySid = pluginRequestWithoutCookie(token);

      touch(browser.sid);
      repository.checkSessions();

      assertNotNull(repository.findById(throwawaySid));
      assertTrue(realEngine.isActiveUser(browser.principal));
   }

   /**
    * The guard must not swallow a real logout. If the plugin request carries the browser's own
    * session cookie next to the Bearer token, {@code getSession(true)} returns the browser's
    * session and the filter overwrites its principal with the wiz principal. That session owns the
    * login (its id is the token's client session id), so its expiry must still deregister it.
    */
   @Test
   void expiryOfABrowserSessionOverwrittenByTheWizPrincipalStillDeregistersTheLogin()
      throws Exception
   {
      Browser browser = login(admin);
      String token = tokenService.createSSOToken(browser.principal, "http://localhost");

      pluginRequestIntoSession(token, repository.findById(browser.sid));
      assertTrue(SRPrincipal.isWizPrincipal(
         repository.findById(browser.sid).getAttribute(RepletRepository.PRINCIPAL_COOKIE)),
                 "the filter overwrote the browser session's principal");
      assertTrue(realEngine.isActiveUser(browser.principal));

      expire(browser.sid);
      repository.checkSessions();

      assertNull(repository.findById(browser.sid), "the sweep ended the browser session");
      assertFalse(realEngine.isActiveUser(browser.principal),
                  "the expiry of the session that owns the login must deregister it");
   }

   /**
    * Explicit logout of the browser, with a live throwaway present, still deregisters the login
    * (the login is what the guard could have affected; the HTTP session removed is not pinned).
    */
   @Test
   void explicitLogoutOfTheBrowserStillDeregistersTheLoginWithALiveThrowaway() throws Exception {
      Browser browser = login(admin);
      String token = tokenService.createSSOToken(browser.principal, "http://localhost");
      String throwawaySid = pluginRequestWithoutCookie(token);
      assertNotNull(repository.findById(throwawaySid));

      auth.logout(browser.principal, IP, "", true);

      assertFalse(realEngine.isActiveUser(browser.principal),
                  "an explicit logout must deregister the login");
      // Not asserted: which HTTP session loggedOut() removes. It removes the first session whose
      // principal equals the logged-out one, and the throwaway's wiz principal equals the
      // browser's, so with a live throwaway it may remove the throwaway instead (pre-existing).
   }

   /** The mechanics the case relies on: the wiz principal shares the browser's login key. */
   @Test
   void tokenCarriesTheBrowsersClientInfo() throws Exception {
      Browser browser = login(admin);
      String token = tokenService.createSSOToken(browser.principal, "http://localhost");

      assertEquals(browser.sid, clientSessionOf(token));
      assertEquals(IP, SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("clientAddress"));
   }

   private record Browser(String sid, SRPrincipal principal) {
   }

   /** A browser login: an HTTP session in the real repository, then the real authenticate. */
   private Browser login(IdentityID user) throws Exception {
      IgniteSessionRepository.IgniteSession session = repository.createSession();
      String sid = session.getId();
      ClientInfo info = new ClientInfo(user, IP, sid);
      SRPrincipal principal = (SRPrincipal) realEngine.authenticate(
         info, new DefaultTicket(user, "pw"), provider);
      session.setAttribute(RepletRepository.PRINCIPAL_COOKIE, principal);
      repository.save(session);
      return new Browser(sid, principal);
   }

   /**
    * A plugin request: no session cookie, {@code Authorization: Bearer}. The real filter builds the
    * principal from the token and asks the request for a new session; the mock hands out a real
    * repository session and writes the filter's attribute into it.
    *
    * @return the id of the throwaway session.
    */
   private String pluginRequestWithoutCookie(String token) throws Exception {
      return pluginRequestIntoSession(token, repository.createSession());
   }

   /**
    * A plugin request whose {@code getSession(true)} returns {@code session}: a new one for a
    * cookieless request, the browser's own when the request also carries its cookie.
    *
    * @return the id of that session.
    */
   private String pluginRequestIntoSession(String token,
                                           IgniteSessionRepository.IgniteSession session)
      throws Exception
   {
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpServletResponse response = mock(HttpServletResponse.class);
      FilterChain chain = mock(FilterChain.class);
      stubRequest(request, token);
      HttpSession http = mock(HttpSession.class);
      when(http.getId()).thenReturn(session.getId());
      doAnswer(inv -> {
         session.setAttribute(inv.getArgument(0), inv.getArgument(1));
         repository.save(session);
         return null;
      }).when(http).setAttribute(anyString(), any());
      when(request.getSession(true)).thenReturn(http);

      wizFilter.doFilter(request, response, chain);

      verify(response, never()).sendError(anyInt());
      verify(chain).doFilter(any(), any());
      assertNotNull(repository.findById(session.getId())
                       .getAttribute(RepletRepository.PRINCIPAL_COOKIE),
                    "the filter stored the token's principal in the session");
      return session.getId();
   }

   private void stubRequest(HttpServletRequest request, String token) {
      when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
      when(request.getContextPath()).thenReturn("");
      when(request.getServletPath()).thenReturn("/api/wiz/v1/sheets");
      when(request.getRequestURI()).thenReturn("/api/wiz/v1/sheets");
      when(request.getRemoteHost()).thenReturn("172.28.0.9");
   }

   /** Moves the last access of a session past the 30 minute timeout. */
   private void expire(String sid) {
      IgniteSessionRepository.IgniteSession session = repository.findById(sid);
      session.setLastAccessedTime(Instant.now().minus(Duration.ofMinutes(31)));
      repository.save(session);
   }

   /** A heartbeat of the browser: the session's last access is now. */
   private void touch(String sid) {
      IgniteSessionRepository.IgniteSession session = repository.findById(sid);
      session.setLastAccessedTime(Instant.now());
      repository.save(session);
   }

   private static String clientSessionOf(String token) throws Exception {
      return SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("clientSession");
   }

   private static void setField(Object target, String name, Object value) throws Exception {
      Class<?> c = target.getClass();

      while(c != null) {
         try {
            Field f = c.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
            return;
         }
         catch(NoSuchFieldException e) {
            c = c.getSuperclass();
         }
      }

      throw new NoSuchFieldException(name);
   }
}
