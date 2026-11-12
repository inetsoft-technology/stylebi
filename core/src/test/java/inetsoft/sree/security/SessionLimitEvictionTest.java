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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteDistributedMap;
import inetsoft.web.security.*;
import inetsoft.sree.web.SessionLicenseServiceProvider;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.SessionRecord;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.web.session.SessionAttributeKey;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81146: the per-client session limit ({@code http.session.maxPerClient}, default 5) in
 * {@link AbstractSecurityFilter#createSession}.
 *
 * <ul>
 *   <li>D1: the new login counted itself, so the limit tripped one login early (cap 4, not 5).</li>
 *   <li>D2: the limit ranked the copies stored in {@code SecurityEngine.users}, which are
 *       serialized at login before the HTTP session is bound, so it evicted the oldest login
 *       instead of the least recently used.</li>
 *   <li>D3/D4: the eviction used the unaudited, non-invalidating logout, with no reason for the
 *       browser.</li>
 * </ul>
 *
 * <p>{@code SecurityEngine.users} is a real {@link IgniteDistributedMap} over a real local Ignite
 * cache. A plain map hides both D1 and D2: it holds the live object, so identity comparison and
 * the live last access both appear to work. The real {@link SecurityEngine#authenticate}, the
 * real filter and the real {@link RequestPrincipalFilter#sessionAccessed} are used; only
 * {@link AuthenticationService} and the servlet objects are mocks, and the mocked logout forwards
 * to the real {@link SecurityEngine#loggedOut}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SessionLimitEvictionTest {
   static final String IP = "192.168.65.1";
   static Ignite ignite;
   static Path workDir;
   static int seq;

   final String org = Organization.getDefaultOrganizationID();
   final IdentityID admin = new IdentityID("admin", org);
   MockedStatic<SecurityEngine> engineStatic;
   SecurityEngine realEngine;
   SecurityProvider provider;
   AuthenticationService auth;
   TestFilter filter;
   final List<String> evicted = new ArrayList<>();
   final Map<String, SRPrincipal> bySid = new LinkedHashMap<>();
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
      workDir = Files.createTempDirectory("ignite-81146");
      IgniteConfiguration config = new IgniteConfiguration();
      config.setIgniteInstanceName("session-limit-" + UUID.randomUUID());
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
      CacheConfiguration<Object, Object> cc = new CacheConfiguration<>("users81146-" + (seq++));
      cc.setCacheMode(CacheMode.REPLICATED);
      cc.setAtomicityMode(CacheAtomicityMode.TRANSACTIONAL);
      cc.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
      IgniteCache<Object, Object> cache = ignite.getOrCreateCache(cc);
      Field users = SecurityEngine.class.getDeclaredField("users");
      users.setAccessible(true);
      users.set(realEngine, new IgniteDistributedMap<>(cache));

      engineStatic = mockStatic(SecurityEngine.class,
                                withSettings().defaultAnswer(CALLS_REAL_METHODS));
      engineStatic.when(SecurityEngine::getSecurity).thenReturn(realEngine);

      auth = mock(AuthenticationService.class, withSettings().lenient());
      // every logout form forwards to the real engine, so the test also observes an eviction made
      // with the unaudited one-argument form (the form used before the fix)
      doAnswer(inv -> {
         recordEviction(inv.getArgument(0));
         return null;
      }).when(auth).logout(any(Principal.class));
      doAnswer(inv -> {
         recordEviction(inv.getArgument(0));
         return null;
      }).when(auth).logout(any(Principal.class), anyString(), anyString(), anyBoolean(),
                           nullable(String.class));
      filter = new TestFilter(mock(SessionLicenseServiceProvider.class), auth);
   }

   @AfterEach
   void tearDown() {
      engineStatic.close();
      ThreadContext.setContextPrincipal(threadPrincipal);
   }

   /**
    * D1: with the default of 5, five sessions of one user and address coexist and the sixth login
    * evicts exactly one. Before the fix the fifth login already evicted, leaving 4.
    */
   @Test
   void capIsExactlyMaxPerClient() throws Exception {
      for(int i = 1; i <= 5; i++) {
         login("s" + i, true);
         assertEquals(List.of(), evicted, "login " + i + " must not evict anything");
         assertEquals(i, alive());
         Thread.sleep(20);
      }

      login("s6", true);
      assertEquals(List.of("s1"), evicted, "the sixth login evicts exactly the oldest idle one");
      assertEquals(5, alive());

      login("s7", true);
      assertEquals(List.of("s1", "s2"), evicted);
      assertEquals(5, alive());
   }

   /**
    * D2: s1 is the oldest login but is the one in use (websocket traffic), s2..s5 are idle. The
    * sixth login must evict s2, not s1. Before the fix the ranking read the login times stored
    * with the engine copies and evicted s1.
    */
   @Test
   void activeOlderSessionIsNotEvictedBeforeIdleNewerOne() throws Exception {
      SRPrincipal s1 = login("s1", true);
      Thread.sleep(30);

      for(int i = 2; i <= 5; i++) {
         login("s" + i, true);
         Thread.sleep(30);
      }

      touchLikeWebsocket(s1, "s1");

      login("s6", true);
      assertEquals(List.of("s2"), evicted);
      assertTrue(bySid.keySet().stream().filter(s -> !evicted.contains(s)).toList()
                    .contains("s1"));

      // s2 is gone, s1 keeps being used, so the next eviction is s3 and never s1
      touchLikeWebsocket(s1, "s1");
      login("s7", true);
      assertEquals(List.of("s2", "s3"), evicted);
   }

   /**
    * A plain principal (not a DestinationUserNameProviderPrincipal) has no session-shared value;
    * the ranking falls back to its own last access, so the oldest is still evicted first.
    */
   @Test
   void plainPrincipalsAreRankedByTheirOwnLastAccess() throws Exception {
      long base = System.currentTimeMillis();

      // s2 has the oldest last access although it logs in second
      login("s1", false, base - 1000);
      login("s2", false, base - 5000);
      login("s3", false, base - 3000);
      login("s4", false, base - 2000);
      login("s5", false, base - 4000);
      login("s6", false, base);

      assertEquals(List.of("s2"), evicted);
   }

   /**
    * D4/D3: the eviction goes through the audited logout, invalidates the session, and carries
    * the fixed limit reason for the browser.
    */
   @Test
   void evictionIsAuditedInvalidatingAndCarriesTheLimitReason() throws Exception {
      for(int i = 1; i <= 6; i++) {
         login("s" + i, true);
         Thread.sleep(20);
      }

      verify(auth, times(1)).logout(any(Principal.class), eq(IP),
                                    eq(SessionRecord.LOGOFF_SESSION_LIMIT), eq(true),
                                    eq(SessionLoggedOutEvent.REASON_LIMIT));
      verify(auth, never()).logout(any(Principal.class));
   }

   private SRPrincipal login(String sid, boolean bound) throws Exception {
      return login(sid, bound, 0L);
   }

   private SRPrincipal login(String sid, boolean dunpp, long lastAccess) throws Exception {
      ClientInfo info = new ClientInfo(admin, IP, sid);
      SRPrincipal p;

      if(dunpp) {
         p = (SRPrincipal) realEngine.authenticate(info, new DefaultTicket(admin, "pw"), provider);
      }
      else {
         SRPrincipal plain = new SRPrincipal(info, new IdentityID[0], new String[0], org,
                                             SecurityEngine.getRandom().nextLong());
         plain.setLastAccess(lastAccess);
         p = (SRPrincipal) realEngine.authenticate(info, plain, provider);
      }

      // what IgniteSessionRepository.createSession() writes for a new HTTP session
      Cluster.getInstance().<SessionAttributeKey, Object>getReplicatedMap(
            IgniteSessionRepository.class.getName() + ".sessionAttributes")
         .put(new SessionAttributeKey(
            sid, IgniteSessionRepository.class.getName() + ".created"), 0L);
      HttpSession session = mock(HttpSession.class);
      lenient().when(session.getId()).thenReturn(sid);
      HttpServletRequest req = mock(HttpServletRequest.class);
      lenient().when(req.getSession(true)).thenReturn(session);
      lenient().when(req.getRemoteHost()).thenReturn(IP);
      bySid.put(sid, p);
      filter.create(req, p);
      return p;
   }

   /**
    * A websocket message: the STOMP user is a deserialized copy of the session's principal, and
    * RequestPrincipalFilter stamps its last access.
    */
   private void touchLikeWebsocket(SRPrincipal sessionPrincipal, String sid) throws Exception {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();

      try(ObjectOutputStream out = new ObjectOutputStream(bytes)) {
         out.writeObject(sessionPrincipal);
      }

      SRPrincipal ws;

      try(ObjectInputStream in =
             new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())))
      {
         ws = (SRPrincipal) in.readObject();
      }

      Thread.sleep(40);
      new RequestPrincipalFilter(mock(SessionLicenseServiceProvider.class), auth)
         .sessionAccessed(new SessionAccessDispatcher.SessionAccessEvent(this, ws, sid, Map.of()));
   }

   private void recordEviction(Principal principal) {
      String sid = sidOf((SRPrincipal) principal);

      if(sid != null && !evicted.contains(sid)) {
         evicted.add(sid);
      }

      realEngine.loggedOut(new SessionLoggedOutEvent(this, principal, false));
   }

   private String sidOf(SRPrincipal p) {
      return bySid.entrySet().stream()
         .filter(e -> e.getValue().getSecureID() == p.getSecureID())
         .map(Map.Entry::getKey).findFirst().orElse(null);
   }

   private int alive() {
      Set<Long> ids = new HashSet<>();

      for(SRPrincipal p : realEngine.getActivePrincipalList()) {
         ids.add(p.getSecureID());
      }

      return ids.size();
   }

   static class TestFilter extends AbstractSecurityFilter {
      TestFilter(SessionLicenseServiceProvider provider, AuthenticationService auth) {
         super(provider, auth);
      }

      void create(HttpServletRequest request, SRPrincipal principal) throws Exception {
         createSession(request, principal);
      }

      @Override
      public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
      }
   }
}
