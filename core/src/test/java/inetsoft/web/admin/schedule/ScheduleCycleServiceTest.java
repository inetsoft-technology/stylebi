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
package inetsoft.web.admin.schedule;

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.web.admin.content.repository.ResourcePermissionService;
import inetsoft.web.admin.schedule.model.DataCycleListModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleCycleServiceTest {
   @Mock private DataCycleManager dataCycleManager;
   @Mock private ScheduleConditionService scheduleConditionService;
   @Mock private SchedulerMonitoringService schedulerMonitoringService;
   @Mock private ResourcePermissionService permissionService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Principal principal;
   @Mock private OrganizationManager organizationManager;

   private ScheduleCycleService service;
   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<OrganizationManager> organizationManagerStatic;

   @BeforeEach
   void setUp() {
      service = new ScheduleCycleService(
         dataCycleManager, scheduleConditionService, schedulerMonitoringService,
         permissionService, securityEngine);

      sutilStatic = mockStatic(SUtil.class, withSettings().lenient());
      organizationManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      lenient().when(organizationManager.getCurrentOrgID(principal)).thenReturn("host-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
   }

   @AfterEach
   void tearDown() {
      organizationManagerStatic.close();
      sutilStatic.close();
   }

   @Test
   void getCycleInfos_filtersWithSecurityProviderWithoutActiveLoginGuard() throws Exception {
      DataCycleInfo allowed = new DataCycleInfo("AllowedCycle");
      DataCycleInfo denied = new DataCycleInfo("DeniedCycle");
      when(schedulerMonitoringService.getDataCycleInfos())
         .thenReturn(new DataCycleInfo[] { allowed, denied });
      when(securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE,
         ScheduleCycleService.getCyclePermissionID("AllowedCycle", "host-org"),
         ResourceAction.ACCESS))
         .thenReturn(true);
      when(securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE,
         ScheduleCycleService.getCyclePermissionID("DeniedCycle", "host-org"),
         ResourceAction.ACCESS))
         .thenReturn(false);

      DataCycleListModel result = service.getCycleInfos(principal);

      assertEquals(List.of(allowed), result.cycles());
      assertFalse(result.cycles().contains(denied));
      verify(securityProvider).checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE,
         ScheduleCycleService.getCyclePermissionID("AllowedCycle", "host-org"),
         ResourceAction.ACCESS);
      verify(securityProvider).checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE,
         ScheduleCycleService.getCyclePermissionID("DeniedCycle", "host-org"),
         ResourceAction.ACCESS);
      verify(securityEngine, never()).checkPermission(
         any(), any(ResourceType.class), anyString(), any(ResourceAction.class));
   }

   @Test
   void getCycleInfos_includesCycleWhenAdminPermissionIsGranted() throws Exception {
      DataCycleInfo adminOnly = new DataCycleInfo("AdminOnlyCycle");
      String permissionId = ScheduleCycleService.getCyclePermissionID("AdminOnlyCycle", "host-org");
      when(schedulerMonitoringService.getDataCycleInfos()).thenReturn(new DataCycleInfo[] { adminOnly });
      when(securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE, permissionId, ResourceAction.ACCESS))
         .thenReturn(false);
      when(securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE, permissionId, ResourceAction.ADMIN))
         .thenReturn(true);

      DataCycleListModel result = service.getCycleInfos(principal);

      assertEquals(List.of(adminOnly), result.cycles());
   }

   @Test
   void getCycleInfos_excludesCycleWhenAccessAndAdminPermissionsAreDenied() throws Exception {
      DataCycleInfo denied = new DataCycleInfo("DeniedCycle");
      String permissionId = ScheduleCycleService.getCyclePermissionID("DeniedCycle", "host-org");
      when(schedulerMonitoringService.getDataCycleInfos()).thenReturn(new DataCycleInfo[] { denied });
      when(securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE, permissionId, ResourceAction.ACCESS))
         .thenReturn(false);
      when(securityProvider.checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE, permissionId, ResourceAction.ADMIN))
         .thenReturn(false);

      DataCycleListModel result = service.getCycleInfos(principal);

      assertEquals(List.of(), result.cycles());
      verify(securityProvider).checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE, permissionId, ResourceAction.ACCESS);
      verify(securityProvider).checkPermission(
         principal, ResourceType.SCHEDULE_CYCLE, permissionId, ResourceAction.ADMIN);
   }

   @Test
   void getCycleInfos_noSecurityProvider_returnsAllCycles() throws Exception {
      DataCycleInfo cycle = new DataCycleInfo("Cycle1");
      when(securityEngine.getSecurityProvider()).thenReturn(null);
      when(schedulerMonitoringService.getDataCycleInfos()).thenReturn(new DataCycleInfo[] { cycle });

      DataCycleListModel result = service.getCycleInfos(principal);

      assertEquals(List.of(cycle), result.cycles());
      verifyNoInteractions(securityProvider);
   }
}
