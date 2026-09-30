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
 * [G3] Bug #77403: getServerSummaryModel requires EM_COMPONENT monitoring/summary ACCESS.
 *      A squash merge moved its @Secured onto getClusterCacheUsage, leaving it guarded only
 *      by @DeniedMultiTenancyOrgUser, which gates nothing in single-tenant mode.
 * [G4] Every HTTP-mapped method carries @Secured, so no REST endpoint is left unguarded.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.web.HttpServiceRequest;
import inetsoft.storage.ExternalStorageService;
import inetsoft.util.FileSystemService;
import inetsoft.web.admin.cache.CacheService;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.security.DeniedMultiTenancyOrgUser;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

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

   // [G3] Bug #77403: the summary endpoint requires monitoring/summary access
   @Test
   void getServerSummaryModel_requiresMonitoringSummaryAccess() throws Exception {
      Method method = ServerMonitoringController.class.getMethod(
         "getServerSummaryModel", String.class, HttpServiceRequest.class);
      Secured secured = method.getAnnotation(Secured.class);
      assertNotNull(secured, "getServerSummaryModel must be @Secured");
      assertEquals(1, secured.value().length);
      RequiredPermission permission = secured.value()[0];
      assertEquals(ResourceType.EM_COMPONENT, permission.resourceType());
      assertEquals("monitoring/summary", permission.resource());
      assertArrayEquals(new ResourceAction[] { ResourceAction.ACCESS }, permission.actions());
      assertNotNull(method.getAnnotation(DeniedMultiTenancyOrgUser.class),
                    "getServerSummaryModel must keep @DeniedMultiTenancyOrgUser");
   }

   // [G4] every HTTP-mapped method carries @Secured
   @Test
   void allHttpMappedMethods_areSecured() {
      List<String> mapped = new ArrayList<>();
      List<String> unsecured = new ArrayList<>();

      for(Method method : ServerMonitoringController.class.getDeclaredMethods()) {
         if(AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class)) {
            mapped.add(method.getName());

            if(method.getAnnotation(Secured.class) == null) {
               unsecured.add(method.getName());
            }
         }
      }

      assertFalse(mapped.isEmpty(), "no HTTP-mapped methods found");
      assertTrue(unsecured.isEmpty(), "HTTP-mapped methods without @Secured: " + unsecured);
   }
}
