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
package inetsoft.web.admin.server;

/*
 * Test strategy
 *
 * ServerMonitoringController has many REST and STOMP endpoints. The REST endpoints
 * (getServerSummaryModel, getMonitoringChartLegends, etc.) call several static utility
 * methods (Tool.getReportVersion, SUtil.isCluster, SreeEnv.getProperty) and build
 * compound models, making pure unit testing impractical without extensive static mocking.
 *
 * The most isolated and highest-value behavior is the STOMP permission guard in
 * subscribeServerCharts, which has the same inline-check pattern as the other monitoring
 * controllers. The REST endpoint permission is enforced by @Secured (framework level).
 *
 * Behavioral guarantees covered:
 *
 * [G1] STOMP subscribeServerCharts: permission denied → SecurityException thrown;
 *      MonitoringDataService is never reached.
 * [G2] STOMP subscribeServerCharts: permission granted → MonitoringDataService.addSubscriber
 *      is invoked with the provided StompHeaderAccessor.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.storage.ExternalStorageService;
import inetsoft.util.FileSystemService;
import inetsoft.web.admin.cache.CacheService;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ServerMonitoringControllerTest {

   @Mock private ServerService serverService;
   @Mock private MonitoringDataService monitoringDataService;
   @Mock private CacheService cacheService;
   @Mock private ViewsheetService viewsheetService;
   @Mock private QueryService queryService;
   @Mock private SchedulerMonitoringService schedulerMonitoringService;
   @Mock private ServerClusterClient serverClusterClient;
   @Mock private UsageHistoryService usageHistoryService;
   @Mock private ClusterCacheUsageService clusterCacheUsageService;
   @Mock private Cluster cluster;
   @Mock private CustomThemesManager customThemesManager;
   @Mock private ScheduleClient scheduleClient;
   @Mock private ExternalStorageService externalStorageService;
   @Mock private FileSystemService fileSystemService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private StompHeaderAccessor stompHeaderAccessor;
   @Mock private Principal principal;

   private ServerMonitoringController controller;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("server.type")).thenReturn("standalone");
      lenient().when(scheduleClient.isCluster()).thenReturn(false);

      controller = new ServerMonitoringController(
         serverService, monitoringDataService, cacheService, viewsheetService, queryService,
         schedulerMonitoringService, serverClusterClient, usageHistoryService,
         clusterCacheUsageService, cluster, customThemesManager, scheduleClient,
         externalStorageService, fileSystemService, securityEngine);
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   // [G1] STOMP permission denied → SecurityException; addSubscriber never called
   @Test
   void subscribeServerCharts_permissionDenied_throwsSecurityException() {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/summary"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
         () -> controller.subscribeServerCharts(stompHeaderAccessor, principal));

      verifyNoInteractions(monitoringDataService);
   }

   // [G2] STOMP permission granted → MonitoringDataService.addSubscriber invoked
   @Test
   void subscribeServerCharts_permissionGranted_delegatesToMonitoringDataService()
      throws Exception
   {
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      when(securityProvider.checkPermission(
            eq(principal), eq(ResourceType.EM_COMPONENT),
            eq("monitoring/summary"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(monitoringDataService.addSubscriber(same(stompHeaderAccessor), any())).thenReturn(null);

      controller.subscribeServerCharts(stompHeaderAccessor, principal);

      verify(monitoringDataService).addSubscriber(same(stompHeaderAccessor), any());
   }
}
