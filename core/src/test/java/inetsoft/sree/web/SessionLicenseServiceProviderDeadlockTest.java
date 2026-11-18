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
package inetsoft.sree.web;

import inetsoft.report.internal.LicenseException;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedStatic;

import java.lang.management.ManagementFactory;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #79351: a login rejected at the concurrent session limit logs the user out from inside the
 * synchronized newSession() of the manager (and, in a cluster, while holding the distributed
 * license lock). The logout event reached SessionLicenseServiceProvider.onSessionLoggedOut(),
 * which took the provider lock and then the manager monitor, the reverse of the order of the
 * thread that held the monitor, so two threads deadlocked and the distributed lock leaked.
 *
 * The real provider and managers are used. The Spring event publication is mimicked by having the
 * SUtil.logout stub call the provider's listener on the calling thread, and the Cluster is a
 * stand-in backed by a ReentrantLock and a ConcurrentHashMap.
 */
@Tag("core")
class SessionLicenseServiceProviderDeadlockTest {
   @Test
   @Timeout(value = 60, unit = TimeUnit.SECONDS)
   void clusterLoginRejectedAtLimitDoesNotDeadlockWithConcurrentLogout() throws Exception {
      runScenario(true);
   }

   @Test
   @Timeout(value = 60, unit = TimeUnit.SECONDS)
   void nonClusterLoginRejectedAtLimitDoesNotDeadlockWithConcurrentLogout() throws Exception {
      runScenario(false);
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void runScenario(boolean cluster) throws Exception {
      Lock distributedLock = new ReentrantLock();
      Map<Object, Object> backing = new ConcurrentHashMap<>();
      DistributedMap map = mock(DistributedMap.class, withSettings().defaultAnswer(
         inv -> inv.getMethod().invoke(backing, inv.getArguments())));
      Cluster clusterMock = mock(Cluster.class);
      when(clusterMock.getLock(any())).thenReturn(distributedLock);
      doReturn(map).when(clusterMock).getReplicatedMap(any());

      LicenseManager licenseManager = mock(LicenseManager.class);
      when(licenseManager.getConcurrentSessionCount()).thenReturn(1);
      provider = new SessionLicenseServiceProvider(licenseManager);
      this.clusterMock = clusterMock;
      this.licenseManager = licenseManager;
      this.cluster = cluster;

      SessionLicenseManager manager = inContext(null, provider::getSessionLicenseManager);
      assertNotNull(manager);

      // fill the single slot
      SRPrincipal[] principals = inContext(null, () -> new SRPrincipal[] {
         principal("holder"), principal("rejected"), principal("other") });
      SRPrincipal holder = principals[0];
      SRPrincipal rejected = principals[1];
      SRPrincipal other = principals[2];
      inContext(null, () -> {
         manager.newSession(holder);
         return null;
      });

      CountDownLatch inLogout = new CountDownLatch(1);
      Thread[] bThread = new Thread[1];

      // thread A: a login rejected at the limit; SUtil.logout runs holding the manager monitor
      Callable<Object> a = () -> inContext(() -> {
         inLogout.countDown();

         // wait until thread B is blocked on the manager monitor held by this thread, so that B
         // holds the provider lock if it takes one
         long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

         while(System.nanoTime() < end) {
            Thread t = bThread[0];

            if(t != null && t.getState() == Thread.State.BLOCKED) {
               break;
            }

            Thread.sleep(5);
         }

         Thread.sleep(200);
      }, () -> {
         assertThrows(LicenseException.class, () -> manager.newSession(rejected));
         return null;
      });

      // thread B: any other logout; the listener runs on the thread of the logout
      Callable<Object> b = () -> {
         assertTrue(inLogout.await(10, TimeUnit.SECONDS));
         bThread[0] = Thread.currentThread();
         return inContext(null, () -> {
            provider.onSessionLoggedOut(new SessionLoggedOutEvent(this, other));
            return null;
         });
      };

      ExecutorService executor = Executors.newFixedThreadPool(2);

      try {
         Future<Object> fa = executor.submit(a);
         Future<Object> fb = executor.submit(b);

         try {
            fa.get(20, TimeUnit.SECONDS);
            fb.get(20, TimeUnit.SECONDS);
         }
         catch(TimeoutException e) {
            long[] dead = ManagementFactory.getThreadMXBean().findDeadlockedThreads();
            fail("Deadlock between login and logout, deadlocked threads: " +
                 (dead == null ? 0 : dead.length));
         }
      }
      finally {
         executor.shutdownNow();
      }

      // the distributed lock must be free again, and the slot still belongs to the holder
      assertTrue(distributedLock.tryLock());
      distributedLock.unlock();
      assertEquals(1, manager.getActiveSessions().size());
   }

   /**
    * Runs the task with the static mocks, which Mockito keeps per thread. The hook runs inside the
    * SUtil.logout stub before the listener is invoked.
    */
   private <T> T inContext(Hook logoutHook, Callable<T> task) throws Exception {
      OrganizationManager orgManager = mock(OrganizationManager.class);
      Catalog catalog = mock(Catalog.class);
      when(catalog.getString(any())).thenReturn("msg");
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.getActivePrincipalList()).thenReturn(activePrincipals);
      XSessionService xSessionService = mock(XSessionService.class);
      when(xSessionService.createSessionID(any(), any())).thenReturn("xs");

      try(MockedStatic<XSessionService> xSessionStatic = mockStatic(XSessionService.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class);
          MockedStatic<Cluster> clusterStatic = mockStatic(Cluster.class);
          MockedStatic<OrganizationManager> orgStatic = mockStatic(OrganizationManager.class);
          MockedStatic<Catalog> catalogStatic = mockStatic(Catalog.class);
          MockedStatic<LicenseManager> licenseStatic = mockStatic(LicenseManager.class);
          MockedStatic<SecurityEngine> securityStatic = mockStatic(SecurityEngine.class))
      {
         xSessionStatic.when(XSessionService::getService).thenReturn(xSessionService);
         sutil.when(SUtil::isCluster).thenReturn(cluster);
         sutil.when(() -> SUtil.logout(any(Principal.class))).thenAnswer(inv -> {
            if(logoutHook != null) {
               logoutHook.run();
            }

            provider.onSessionLoggedOut(
               new SessionLoggedOutEvent(this, inv.<Principal>getArgument(0)));
            return null;
         });
         clusterStatic.when(Cluster::getInstance).thenReturn(clusterMock);
         orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         catalogStatic.when(() -> Catalog.getCatalog(any(Principal.class))).thenReturn(catalog);
         licenseStatic.when(LicenseManager::getInstance).thenReturn(licenseManager);
         securityStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
         return task.call();
      }
   }

   private SRPrincipal principal(String user) {
      IdentityID id = new IdentityID(user, "host-org");
      ClientInfo client = new ClientInfo(id, "10.0.0.5", "sid-" + user);
      SRPrincipal principal = new SRPrincipal(client, new IdentityID[0], 1L);
      activePrincipals.add(principal);
      return principal;
   }

   @FunctionalInterface
   private interface Hook {
      void run() throws Exception;
   }

   private SessionLicenseServiceProvider provider;
   private Cluster clusterMock;
   private LicenseManager licenseManager;
   private boolean cluster;
   private final List<SRPrincipal> activePrincipals = new CopyOnWriteArrayList<>();
}
