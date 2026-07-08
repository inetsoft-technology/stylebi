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
package inetsoft.web.admin.cluster;

/*
 * Test strategy
 *
 * ClusterController has two shapes of behavior:
 *
 *   REST endpoints (tested here):
 *     getClusterNodes  — checks SreeEnv.getProperty("server.type"); returns null when
 *                        not "server_cluster", otherwise builds ClusterNodesModel.
 *     getClusterStatus — pure delegation to ClusterService.
 *     getClusterEnabled — pure delegation to ClusterService.
 *     pauseServer / resumeServer — pure delegation to ClusterService.
 *
 *   STOMP subscriber (subscribeClusterStatus) — permission guard tested separately;
 *     omitted here because the subscriber delegates through MonitoringDataService.
 *
 * Behavioral guarantees covered:
 *
 * [G1] getClusterNodes when server.type != "server_cluster" → returns null (not cluster mode).
 * [G2] getClusterStatus delegates to clusterService and returns the result.
 * [G3] getClusterEnabled delegates to clusterService and returns the result.
 * [G4] pauseServer delegates servers array to clusterService.pauseServers().
 * [G5] resumeServer delegates servers array to clusterService.resumeServers().
 * [G6] subscribeClusterStatus STOMP handler throws SecurityException when permission denied.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ClusterControllerTest {

   @Mock private ClusterService clusterService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private ClusterController controller;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      controller = new ClusterController(clusterService, monitoringDataService, securityEngine);
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("server.type")).thenReturn("standalone");
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   // [G1] non-cluster server type → getClusterNodes returns null
   @Test
   void getClusterNodes_notServerCluster_returnsNull() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("server.type")).thenReturn("standalone");

      ClusterNodesModel result = controller.getClusterNodes();

      assertNull(result);
   }

   // [G2] getClusterStatus delegates to clusterService
   @Test
   void getClusterStatus_delegatesToService() {
      List<ReportClusterNodeModel> expected = List.of();
      when(clusterService.getClusterStatus()).thenReturn(expected);

      List<ReportClusterNodeModel> result = controller.getClusterStatus();

      assertSame(expected, result);
      verify(clusterService).getClusterStatus();
   }

   // [G3] getClusterEnabled delegates to clusterService
   @Test
   void getClusterEnabled_delegatesToService() {
      ClusterEnabledModel expected = mock(ClusterEnabledModel.class);
      when(clusterService.getClusterEnabled()).thenReturn(expected);

      ClusterEnabledModel result = controller.getClusterEnabled();

      assertSame(expected, result);
   }

   // [G4] pauseServer delegates server array to clusterService.pauseServers
   @Test
   void pauseServer_delegatesToService() {
      String[] servers = { "node1", "node2" };

      controller.pauseServer(servers);

      verify(clusterService).pauseServers(servers);
   }

   // [G5] resumeServer delegates server array to clusterService.resumeServers
   @Test
   void resumeServer_delegatesToService() {
      String[] servers = { "node1" };

      controller.resumeServer(servers);

      verify(clusterService).resumeServers(servers);
   }

   // [G6] STOMP subscribe with denied permission throws SecurityException
   @Test
   void subscribeClusterStatus_permissionDenied_throwsSecurityException() {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/cluster"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeClusterStatus(stompHeaderAccessor, principal));

      verifyNoInteractions(monitoringDataService);
   }
}
