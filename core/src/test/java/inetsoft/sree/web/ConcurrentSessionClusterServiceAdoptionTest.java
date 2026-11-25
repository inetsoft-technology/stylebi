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
import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Bug #82353: the license of a session created on a node that stopped was dropped after the
 * heartbeat timeout although the session (kept in replicated caches) was still logged in, so the
 * concurrent session limit could be exceeded. A stale license of a live session is now taken over
 * by a surviving node; a stale license of a session that is gone is dropped; the viewer pool,
 * which has its own release timer, keeps dropping stale licenses.
 *
 * The two service instances stand for two nodes sharing one replicated map. The heartbeat timeout
 * is shortened through the test constructor and the update is invoked directly.
 */
@Tag("core")
class ConcurrentSessionClusterServiceAdoptionTest {
   private static final long TIMEOUT = 20L;
   private static final String MAP = "test.licenseMap";

   @BeforeEach
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void setUp() {
      Map<Object, Object> backing = new ConcurrentHashMap<>();
      DistributedMap map = mock(DistributedMap.class, withSettings().defaultAnswer(
         inv -> inv.getMethod().invoke(backing, inv.getArguments())));
      Cluster cluster = mock(Cluster.class);
      when(cluster.getLock(any())).thenReturn(new ReentrantLock());
      doReturn(map).when(cluster).getReplicatedMap(any());

      SecurityEngine engine = mock(SecurityEngine.class);
      when(engine.isActiveUser(any())).thenAnswer(
         inv -> active.contains(inv.<Principal>getArgument(0)));
      Catalog catalog = mock(Catalog.class);
      when(catalog.getString(any())).thenReturn("msg");

      clusterStatic = mockStatic(Cluster.class);
      clusterStatic.when(Cluster::getInstance).thenReturn(cluster);
      securityStatic = mockStatic(SecurityEngine.class);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(engine);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance)
         .thenReturn(mock(OrganizationManager.class));
      sutilStatic = mockStatic(SUtil.class);
      XSessionService xSessionService = mock(XSessionService.class);
      when(xSessionService.createSessionID(any(), any())).thenReturn("xs");
      xSessionStatic = mockStatic(XSessionService.class);
      xSessionStatic.when(XSessionService::getService).thenReturn(xSessionService);
      catalogStatic = mockStatic(Catalog.class);
      catalogStatic.when(() -> Catalog.getCatalog(any(Principal.class))).thenReturn(catalog);
   }

   @AfterEach
   void tearDown() {
      for(ConcurrentSessionClusterService s : services) {
         s.dispose();
      }

      catalogStatic.close();
      xSessionStatic.close();
      sutilStatic.close();
      orgStatic.close();
      securityStatic.close();
      clusterStatic.close();
   }

   @Test
   void staleLicenseOfLiveSessionIsTakenOverAndStillCounts() throws Exception {
      ConcurrentSessionClusterService dead = service(2, true);
      ConcurrentSessionClusterService survivor = service(2, true);
      SRPrincipal a = principal("a");
      SRPrincipal b = principal("b");
      active.add(a);
      active.add(b);
      dead.newSession(a);
      dead.newSession(b);
      assertEquals(2, survivor.getActiveSessions().size());

      // the node of "dead" stops: it no longer refreshes; the survivor runs several updates
      Thread.sleep(TIMEOUT * 3);
      survivor.updateLicenses();
      Thread.sleep(TIMEOUT * 3);
      survivor.updateLicenses();

      assertEquals(2, survivor.getActiveSessions().size());
      assertThrows(LicenseException.class, () -> survivor.newSession(principal("c")));
   }

   @Test
   void staleLicenseOfEndedSessionIsDropped() throws Exception {
      ConcurrentSessionClusterService dead = service(2, true);
      ConcurrentSessionClusterService survivor = service(2, true);
      SRPrincipal a = principal("a");
      SRPrincipal b = principal("b");
      active.add(a);
      dead.newSession(a);
      dead.newSession(b);

      Thread.sleep(TIMEOUT * 3);
      survivor.updateLicenses();

      Set<SRPrincipal> sessions = survivor.getActiveSessions();
      assertEquals(1, sessions.size());
      assertTrue(sessions.contains(a));
   }

   @Test
   void freshLicenseOfOtherNodeIsUntouched() throws Exception {
      // large timeout: nothing may age out during the test, whatever the machine load
      ConcurrentSessionClusterService other = service(2, true, 60000L);
      ConcurrentSessionClusterService survivor = service(2, true, 60000L);
      SRPrincipal a = principal("a");
      SRPrincipal b = principal("b");
      // "a" is not logged in, but its license is fresh, so it must not be removed
      active.add(b);
      other.newSession(a);
      other.newSession(b);
      survivor.updateLicenses();

      assertEquals(2, survivor.getActiveSessions().size());
   }

   @Test
   void adoptedLicenseIsReleasedByLogout() throws Exception {
      ConcurrentSessionClusterService dead = service(1, true);
      ConcurrentSessionClusterService survivor = service(1, true);
      SRPrincipal a = principal("a");
      active.add(a);
      dead.newSession(a);

      Thread.sleep(TIMEOUT * 3);
      survivor.updateLicenses();
      assertEquals(1, survivor.getActiveSessions().size());

      // the session ends (logout or idle timeout) on any node: the slot is freed
      active.remove(a);
      survivor.releaseSession(a);
      assertEquals(0, survivor.getActiveSessions().size());
      survivor.newSession(principal("c"));
      assertEquals(1, survivor.getActiveSessions().size());
   }

   @Test
   void viewerPoolStillDropsStaleLicenseOfLiveSession() throws Exception {
      ConcurrentSessionClusterService dead = service(2, false);
      ConcurrentSessionClusterService survivor = service(2, false);
      SRPrincipal a = principal("a");
      active.add(a);
      dead.newSession(a);

      Thread.sleep(TIMEOUT * 3);
      survivor.updateLicenses();

      assertEquals(0, survivor.getActiveSessions().size());
   }

   @Test
   void failedSessionCheckLeavesLicenseForNextPass() throws Exception {
      ConcurrentSessionClusterService dead = service(2, true);
      ConcurrentSessionClusterService survivor = service(2, true);
      SRPrincipal a = principal("a");
      SRPrincipal b = principal("b");
      dead.newSession(a);
      dead.newSession(b);

      SecurityEngine failing = mock(SecurityEngine.class);
      when(failing.isActiveUser(a)).thenThrow(new IllegalStateException("boom"));
      when(failing.isActiveUser(b)).thenReturn(false);
      securityStatic.when(SecurityEngine::getSecurity).thenReturn(failing);

      Thread.sleep(TIMEOUT * 3);
      survivor.updateLicenses();

      // the failure of one entry neither aborts the pass (b is dropped) nor drops that entry
      Set<SRPrincipal> sessions = survivor.getActiveSessions();
      assertEquals(1, sessions.size());
      assertTrue(sessions.contains(a));
   }

   private ConcurrentSessionClusterService service(int max, boolean adopt) {
      return service(max, adopt, TIMEOUT);
   }

   private ConcurrentSessionClusterService service(int max, boolean adopt, long timeout) {
      ConcurrentSessionClusterService s = new ConcurrentSessionClusterService(
         () -> max, true, adopt ? MAP : MAP + ".viewer", adopt, timeout, 0L);
      services.add(s);
      return s;
   }

   private SRPrincipal principal(String user) {
      IdentityID id = new IdentityID(user, "host-org");
      ClientInfo client = new ClientInfo(id, "10.0.0.5", "sid-" + user);
      return new SRPrincipal(client, new IdentityID[0], 1L);
   }

   private final Set<SRPrincipal> active = ConcurrentHashMap.newKeySet();
   private final List<ConcurrentSessionClusterService> services = new ArrayList<>();
   private MockedStatic<Cluster> clusterStatic;
   private MockedStatic<SecurityEngine> securityStatic;
   private MockedStatic<OrganizationManager> orgStatic;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<XSessionService> xSessionStatic;
   private MockedStatic<Catalog> catalogStatic;
}
