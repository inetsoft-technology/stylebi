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
package inetsoft.web.admin.server;

/*
 * Bug #77403: with security on in single-tenant mode, GET /em/monitoring/server/summary returned
 * 200 (java home, class path, proxy headers) to a user without the monitoring/summary grant,
 * because the endpoint had lost its @Secured and @DeniedMultiTenancyOrgUser gates nothing when
 * multi-tenancy is off. These tests drive the real controller through a real class proxy carrying
 * both aspects, behind MockMvc with AdminExceptionHandler, so they check the HTTP status the
 * request actually gets rather than only the presence of the annotation.
 */

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.ScheduleClient;
import inetsoft.sree.security.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.AdminExceptionHandler;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.reportviewer.service.HttpServletRequestWrapperArgumentResolver;
import inetsoft.web.security.DeniedMultiTenancyOrgUserAspect;
import inetsoft.web.security.SecuredAspect;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Tag("core")
class ServerSummarySecuredProxyTest {
   private AnalyticRepository repository;
   private ViewsheetService viewsheetService;
   private MockMvc mvc;
   private Principal user;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedConstruction<ServerClusterClient> clusterClients;

   @BeforeEach
   void setUp() {
      user = mock(Principal.class);
      when(user.getName()).thenReturn("plain~;~host-org");
      repository = mock(AnalyticRepository.class);

      // single tenant with security on: the case where @DeniedMultiTenancyOrgUser lets it through
      sutil = mockStatic(SUtil.class);
      sutil.when(SUtil::isMultiTenant).thenReturn(false);
      sutil.when(SUtil::getRepletRepository).thenReturn(repository);
      sreeEnv = mockStatic(SreeEnv.class);
      // getServerClusterNodes() creates its own client, which would reach Cluster.getInstance()
      clusterClients = mockConstruction(ServerClusterClient.class, (client, context) ->
         when(client.getConfiguredServers()).thenReturn(Collections.emptySet()));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      ScheduleClient scheduleClient = mock(ScheduleClient.class);
      when(scheduleClient.getScheduleServers()).thenReturn(new String[0]);
      Cluster cluster = mock(Cluster.class);
      when(cluster.getLocalMember()).thenReturn("node1:5701");
      viewsheetService = mock(ViewsheetService.class);

      ServerMonitoringController controller = new ServerMonitoringController(
         null, mock(MonitoringDataService.class), null, viewsheetService, mock(QueryService.class),
         mock(SchedulerMonitoringService.class), null, null, null, cluster, null, scheduleClient,
         null, null, securityEngine);

      ComponentAuthorizationService components = mock(ComponentAuthorizationService.class);
      when(components.getComponent("monitoring/summary"))
         .thenReturn(ViewComponent.builder().name("summary").label("Summary").build());

      AspectJProxyFactory factory = new AspectJProxyFactory(controller);
      factory.setProxyTargetClass(true);
      factory.addAspect(new SecuredAspect(mock(DataSourceRegistry.class), components));
      factory.addAspect(new DeniedMultiTenancyOrgUserAspect(securityEngine));

      mvc = MockMvcBuilders.standaloneSetup((Object) factory.getProxy())
         .setCustomArgumentResolvers(new HttpServletRequestWrapperArgumentResolver())
         .setControllerAdvice(new AdminExceptionHandler(mock(LogManager.class)))
         .build();
   }

   @AfterEach
   void tearDown() {
      clusterClients.close();
      sreeEnv.close();
      sutil.close();
   }

   @Test
   void userWithoutMonitoringSummaryGrantIsForbidden() throws Exception {
      when(repository.checkPermission(any(Principal.class), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(false);

      mvc.perform(get("/em/monitoring/server/summary")
                     .principal(user)
                     .accept(MediaType.APPLICATION_JSON))
         .andExpect(status().isForbidden())
         .andExpect(jsonPath("$.jvmModel").doesNotExist())
         .andExpect(jsonPath("$.message", not(containsString("plain"))));

      verify(repository).checkPermission(user, ResourceType.EM_COMPONENT, "monitoring/summary",
                                         ResourceAction.ACCESS);
      verifyNoInteractions(viewsheetService);
   }

   @Test
   void userWithMonitoringSummaryGrantGetsSummary() throws Exception {
      when(repository.checkPermission(user, ResourceType.EM_COMPONENT, "monitoring/summary",
                                      ResourceAction.ACCESS)).thenReturn(true);

      mvc.perform(get("/em/monitoring/server/summary")
                     .principal(user)
                     .accept(MediaType.APPLICATION_JSON))
         .andExpect(status().isOk())
         .andExpect(jsonPath("$.jvmModel.javaHome").exists());

      verify(viewsheetService).getHistory(null);
   }
}
