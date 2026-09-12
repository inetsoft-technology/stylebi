/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
package inetsoft.web.admin.content.repository;

/*
 * Test strategy
 *
 * ResourcePermissionController orchestrates two endpoints:
 *   getRepositoryEntryPermissions — validates org, checks resource permission, delegates to service
 *   setRepositoryEntryPermissions — checks resource permission, delegates to service
 *
 * Coverage scope:
 *   [getPermissions: invalid org]       getCurrentOrgID() → getOrganization() null → InvalidOrgException
 *   [getPermissions: permission denied] SecurityEngine.checkPermission() false → MessageException
 *   [getPermissions: valid]             valid org, permission granted → service model returned
 *   [setPermissions: permission denied] checkPermission false → MessageException before Util is called
 *
 * The setPermissions success path (delegation + duplicate-identity tests) cannot be unit-tested here
 * because ResourcePermissionController.setRepositoryEntryPermissions() calls
 * Util.getObjectFullPath(), whose static initializer requires a Spring context. Those scenarios are
 * covered by E2E API tests instead.
 *
 * Static singletons (OrganizationManager, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 *
 * Note: checkPermission() always calls securityEngine.checkPermission() with ResourceAction.ADMIN
 * regardless of the action passed in (scheduleTaskAction only applies to SCHEDULE_TASK resources).
 */

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class ResourcePermissionControllerTest {

   @Mock private ResourcePermissionService resourcePermissionService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private ScheduleManager scheduleManager;
   @Mock private Organization organization;
   @Mock private ResourcePermissionModel permissionModel;
   @Mock private Catalog catalog;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;

   private ResourcePermissionController controller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new ResourcePermissionController(resourcePermissionService, securityEngine, scheduleManager);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.getOrganization("host-org")).thenReturn(organization);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      catalogStatic.when(() -> Catalog.getCatalog(any(Principal.class))).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("msg");
      lenient().when(catalog.getString(anyString(), any())).thenReturn("msg");
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // getRepositoryEntryPermissions()
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException; service is never called
   @Test
   void getPermissions_invalidOrg_throwsInvalidOrgException() {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getRepositoryEntryPermissions(
            "/reports/dash1", RepositoryEntry.VIEWSHEET, principal));

      verify(resourcePermissionService, never()).getTableModel(
         anyString(), any(), any(), any(Principal.class), anyBoolean());
   }

   // [permission denied] SecurityEngine.checkPermission() returns false → MessageException thrown
   @Test
   void getPermissions_permissionDenied_throwsMessageException() throws Exception {
      Resource resource = new Resource(ResourceType.REPORT, "/reports/dash1");
      when(resourcePermissionService.getRepositoryResourceType(RepositoryEntry.VIEWSHEET, "/reports/dash1"))
         .thenReturn(resource);
      when(securityEngine.checkPermission(
         principal, ResourceType.REPORT, "/reports/dash1", ResourceAction.ADMIN))
         .thenReturn(false);

      assertThrows(MessageException.class,
         () -> controller.getRepositoryEntryPermissions(
            "/reports/dash1", RepositoryEntry.VIEWSHEET, principal));

      verify(resourcePermissionService, never()).getTableModel(
         anyString(), any(), any(), any(Principal.class), anyBoolean());
   }

   // [valid] valid org, permission granted → service model returned unchanged
   @Test
   void getPermissions_validResource_returnsPermissionModel() throws Exception {
      Resource resource = new Resource(ResourceType.REPORT, "/reports/dash1");
      when(resourcePermissionService.getRepositoryResourceType(RepositoryEntry.VIEWSHEET, "/reports/dash1"))
         .thenReturn(resource);
      when(securityEngine.checkPermission(
         principal, ResourceType.REPORT, "/reports/dash1", ResourceAction.ADMIN))
         .thenReturn(true);
      when(resourcePermissionService.getTableModel(
         "/reports/dash1", ResourceType.REPORT,
         ResourcePermissionService.ADMIN_ACTIONS, principal, false))
         .thenReturn(permissionModel);

      ResourcePermissionModel result =
         controller.getRepositoryEntryPermissions("/reports/dash1", RepositoryEntry.VIEWSHEET, principal);

      assertSame(permissionModel, result);
   }

   // -------------------------------------------------------------------------
   // setRepositoryEntryPermissions()
   // -------------------------------------------------------------------------

   // [permission denied] checkPermission returns false → MessageException thrown before Util is called
   @Test
   void setPermissions_permissionDenied_throwsMessageException() throws Exception {
      Resource resource = new Resource(ResourceType.REPORT, "/reports/dash1");
      when(resourcePermissionService.getRepositoryResourceType(RepositoryEntry.VIEWSHEET, "/reports/dash1"))
         .thenReturn(resource);
      when(securityEngine.checkPermission(
         principal, ResourceType.REPORT, "/reports/dash1", ResourceAction.ADMIN))
         .thenReturn(false);

      assertThrows(MessageException.class,
         () -> controller.setRepositoryEntryPermissions(
            "/reports/dash1", RepositoryEntry.VIEWSHEET, permissionModel, principal));

      verify(resourcePermissionService, never()).setResourcePermissions(
         anyString(), any(), anyString(), any(), any(Principal.class), anyBoolean());
   }

   // -------------------------------------------------------------------------
   // checkPermission() — SCHEDULE_TASK branch
   // -------------------------------------------------------------------------

   // [task not found] getScheduleTask returns null → allowed=false → MessageException
   @Test
   void getPermissions_scheduleTask_taskNotFound_throwsMessageException() throws Exception {
      Resource resource = new Resource(ResourceType.SCHEDULE_TASK, "myTask");
      when(resourcePermissionService.getRepositoryResourceType(RepositoryEntry.VIEWSHEET, "myTask"))
         .thenReturn(resource);
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(null);

      assertThrows(MessageException.class,
         () -> controller.getRepositoryEntryPermissions(
            "myTask", RepositoryEntry.VIEWSHEET, principal));
   }

   // [task found, no permission] hasTaskPermission returns false → MessageException
   @Test
   void getPermissions_scheduleTask_noPermission_throwsMessageException() throws Exception {
      ScheduleTask task = mock(ScheduleTask.class);
      Resource resource = new Resource(ResourceType.SCHEDULE_TASK, "myTask");
      when(resourcePermissionService.getRepositoryResourceType(RepositoryEntry.VIEWSHEET, "myTask"))
         .thenReturn(resource);
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(task);

      try(MockedStatic<ScheduleManager> smStatic = mockStatic(ScheduleManager.class)) {
         smStatic.when(() -> ScheduleManager.hasTaskPermission(any(), eq(principal), any()))
            .thenReturn(false);

         assertThrows(MessageException.class,
            () -> controller.getRepositoryEntryPermissions(
               "myTask", RepositoryEntry.VIEWSHEET, principal));
      }
   }

   // [task found, permission granted] hasTaskPermission returns true → model returned
   @Test
   void getPermissions_scheduleTask_permissionGranted_returnsModel() throws Exception {
      ScheduleTask task = mock(ScheduleTask.class);
      Resource resource = new Resource(ResourceType.SCHEDULE_TASK, "myTask");
      when(resourcePermissionService.getRepositoryResourceType(RepositoryEntry.VIEWSHEET, "myTask"))
         .thenReturn(resource);
      when(scheduleManager.getScheduleTask("myTask")).thenReturn(task);
      when(resourcePermissionService.getTableModel(
         "myTask", ResourceType.SCHEDULE_TASK,
         ResourcePermissionService.ADMIN_ACTIONS, principal, false))
         .thenReturn(permissionModel);

      try(MockedStatic<ScheduleManager> smStatic = mockStatic(ScheduleManager.class)) {
         smStatic.when(() -> ScheduleManager.hasTaskPermission(any(), eq(principal), any()))
            .thenReturn(true);

         ResourcePermissionModel result =
            controller.getRepositoryEntryPermissions("myTask", RepositoryEntry.VIEWSHEET, principal);

         assertSame(permissionModel, result);
      }
   }
}
