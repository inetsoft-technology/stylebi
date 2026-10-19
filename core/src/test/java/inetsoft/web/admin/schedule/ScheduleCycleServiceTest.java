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
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.Principal;
import java.util.*;

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
   private MockedStatic<Audit> auditStatic;

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
      if(auditStatic != null) {
         auditStatic.close();
      }

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

   @Test
   void editCycle_failedStoreOfRenamedCycleKeepsOldCycleAndPermissions() throws Exception {
      stubEditCycle();
      doThrow(new IOException("disk full")).when(dataCycleManager)
         .setConditions(eq("NewName"), eq("host-org"), anyList());

      IOException e = assertThrows(
         IOException.class, () -> service.editCycle(renameModel("OldName", "NewName"), principal));

      assertEquals("disk full", e.getMessage());
      verify(dataCycleManager, never()).removeDataCycle(anyString(), anyString());
      verify(securityEngine, never()).setPermission(
         eq(ResourceType.SCHEDULE_CYCLE), anyString(), any());
      verify(dataCycleManager, never()).save();
   }

   @Test
   void editCycle_renameRemovesOldCycleOnlyAfterNewCycleIsStored() throws Exception {
      stubEditCycle();
      Permission permission = mock(Permission.class);
      when(securityEngine.getPermission(
         ResourceType.SCHEDULE_CYCLE,
         ScheduleCycleService.getCyclePermissionID("OldName", "host-org")))
         .thenReturn(permission);

      service.editCycle(renameModel("OldName", "NewName"), principal);

      InOrder order = inOrder(dataCycleManager, securityEngine);
      order.verify(dataCycleManager).setCycleInfo(eq("NewName"), eq("host-org"), any());
      order.verify(securityEngine).setPermission(
         ResourceType.SCHEDULE_CYCLE,
         ScheduleCycleService.getCyclePermissionID("NewName", "host-org"), permission);
      order.verify(dataCycleManager).removeDataCycle("OldName", "host-org");
      order.verify(dataCycleManager).save();
   }

   @Test
   void editCycle_sameNameNeverRemovesTheCycle() throws Exception {
      stubEditCycle();

      service.editCycle(renameModel("Cycle1", "Cycle1"), principal);

      verify(dataCycleManager).setCycleInfo(eq("Cycle1"), eq("host-org"), any());
      verify(dataCycleManager, never()).removeDataCycle(anyString(), anyString());
      verify(dataCycleManager).save();
   }

   private void stubEditCycle() throws Exception {
      sutilStatic.when(() -> SUtil.getActionRecord(any(Principal.class), anyString(), any(), anyString()))
         .thenAnswer(inv -> new ActionRecord());
      auditStatic = mockStatic(Audit.class, withSettings().lenient());
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      lenient().when(principal.getName()).thenReturn("admin");
      lenient().when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.SCHEDULE_CYCLE), anyString(), any(ResourceAction.class)))
         .thenReturn(true);
      lenient().doReturn(Collections.emptyEnumeration()).when(dataCycleManager)
         .getDataCycles("host-org");
   }

   private ScheduleCycleDialogModel renameModel(String name, String label) {
      return ScheduleCycleDialogModel.builder()
         .name(name)
         .label(label)
         .timeZone("UTC")
         .taskDefaultTime(false)
         .conditionPaneModel(TaskConditionPaneModel.builder()
                                .twelveHourSystem(false)
                                .build())
         .cycleInfo(new DataCycleManager.CycleInfo(name, "host-org"))
         .permissionModel(mock(ResourcePermissionModel.class))
         .startTimeEnabled(false)
         .build();
   }
}
