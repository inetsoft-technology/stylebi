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
package inetsoft.web.admin.schedule;

/*
 * Test strategy
 *
 * EMScheduleTaskFolderController has real in-controller logic in three methods:
 *   addFolder     — path construction: prepends parent path unless parent is root or empty
 *   getFolder     — org validity guard before recursive tree build
 *   moveFolder    — per-task permission loop; silently returns without moving on first denial
 *
 * Coverage scope:
 *   [addFolder: nested parent]          non-root path prepended → "Monthly/Q1"
 *   [addFolder: root parent]            "/" or "" parent → folder name used verbatim → "Q1"
 *   [getFolder: invalid org]            org null → InvalidOrgException; service never called
 *   [moveFolder: task permission denied] first task fails → moveScheduleItems never called
 *   [moveFolder: task permission granted] all tasks pass → moveScheduleItems called
 *   [moveFolder: null task models]       no task models → permission loop skipped → moveScheduleItems called
 *
 * Static singletons (OrganizationManager, Catalog) are intercepted with
 * Mockito.mockStatic() using lenient() to suppress UnnecessaryStubbingException.
 */

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
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
class EMScheduleTaskFolderControllerTest {

   @Mock private ScheduleTaskFolderService scheduleTaskFolderService;
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleManager scheduleManager;
   @Mock private SecurityProvider securityProvider;
   @Mock private Organization organization;
   @Mock private ScheduleTask scheduleTask;
   @Mock private Catalog catalog;
   @Mock private Principal principal;
   @Mock private OrganizationManager orgManager;

   private EMScheduleTaskFolderController controller;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Catalog> catalogStatic;

   @BeforeEach
   void setUp() {
      controller = new EMScheduleTaskFolderController(
         scheduleTaskFolderService, scheduleService, scheduleTaskService,
         securityEngine, scheduleManager);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID(principal)).thenReturn("host-org");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(securityProvider.getOrganization("host-org")).thenReturn(organization);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      lenient().when(catalog.getString(anyString())).thenReturn("msg");
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      catalogStatic.close();
   }

   // -------------------------------------------------------------------------
   // addFolder()
   // -------------------------------------------------------------------------

   // [nested parent] parent path "Monthly" → folderName becomes "Monthly/Q1"
   @Test
   void addFolder_withNestedParentPath_constructsFullFolderName() throws Exception {
      ContentRepositoryTreeNode parentNode = ContentRepositoryTreeNode.builder()
         .label("Monthly").path("Monthly").type(0).build();
      NewTaskFolderRequest req = mock(NewTaskFolderRequest.class);
      when(req.getParent()).thenReturn(parentNode);
      when(req.getFolderName()).thenReturn("Q1");

      controller.addFolder(req, principal);

      verify(scheduleTaskFolderService).addFolder(
         any(), eq("Monthly/Q1"), eq("Monthly"), anyInt(), eq(principal));
   }

   // [root parent] parent path "/" → folderName is "Q1" with no prefix
   @Test
   void addFolder_withRootParentPath_usesFolderNameOnly() throws Exception {
      ContentRepositoryTreeNode parentNode = ContentRepositoryTreeNode.builder()
         .label("Root").path("/").type(0).build();
      NewTaskFolderRequest req = mock(NewTaskFolderRequest.class);
      when(req.getParent()).thenReturn(parentNode);
      when(req.getFolderName()).thenReturn("Q1");

      controller.addFolder(req, principal);

      verify(scheduleTaskFolderService).addFolder(
         any(), eq("Q1"), eq("/"), anyInt(), eq(principal));
   }

   // -------------------------------------------------------------------------
   // getFolder()
   // -------------------------------------------------------------------------

   // [invalid org] org lookup returns null → InvalidOrgException; no tree building
   @Test
   void getFolder_invalidOrg_throwsInvalidOrgException() {
      when(securityProvider.getOrganization("host-org")).thenReturn(null);

      assertThrows(InvalidOrgException.class,
         () -> controller.getFolder(principal));

      verify(scheduleTaskFolderService, never()).getRootEntry();
   }

   // -------------------------------------------------------------------------
   // moveFolder()
   // -------------------------------------------------------------------------

   // [task permission denied] first task fails both checks → moveScheduleItems never called
   @Test
   void moveFolder_taskPermissionDenied_skipsMove() throws Exception {
      ScheduleTaskModel taskModel = mock(ScheduleTaskModel.class);
      when(taskModel.name()).thenReturn("myTask");

      MoveTaskFolderRequest request = mock(MoveTaskFolderRequest.class);
      when(request.getTasks()).thenReturn(new ScheduleTaskModel[]{ taskModel });
      // getTarget() not stubbed: controller returns early before reaching it

      when(scheduleManager.getScheduleTask("myTask")).thenReturn(scheduleTask);
      when(securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK, "myTask", ResourceAction.WRITE))
         .thenReturn(false);
      // canDeleteTask not stubbed: default false return satisfies the denial condition

      controller.moveFolder(request, principal);

      verify(scheduleTaskFolderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   // [task permission granted] SCHEDULE_TASK WRITE granted → moveScheduleItems called
   @Test
   void moveFolder_taskPermissionGranted_movesItems() throws Exception {
      ScheduleTaskModel taskModel = mock(ScheduleTaskModel.class);
      when(taskModel.name()).thenReturn("myTask");

      ContentRepositoryTreeNode target = ContentRepositoryTreeNode.builder()
         .label("Target").path("Target").type(0).build();
      MoveTaskFolderRequest request = mock(MoveTaskFolderRequest.class);
      when(request.getTasks()).thenReturn(new ScheduleTaskModel[]{ taskModel });
      when(request.getFolders()).thenReturn(new String[]{"Monthly"});
      when(request.getTarget()).thenReturn(target);

      when(scheduleManager.getScheduleTask("myTask")).thenReturn(scheduleTask);
      when(securityEngine.checkPermission(
         principal, ResourceType.SCHEDULE_TASK, "myTask", ResourceAction.WRITE))
         .thenReturn(true);

      controller.moveFolder(request, principal);

      verify(scheduleTaskFolderService).moveScheduleItems(
         any(), eq(new String[]{"Monthly"}), argThat(e -> "Target".equals(e.getPath())), eq(principal));
   }

   // [null task models] no task models → permission loop skipped → moveScheduleItems called
   @Test
   void moveFolder_nullTaskModels_movesDirectly() throws Exception {
      ContentRepositoryTreeNode target = ContentRepositoryTreeNode.builder()
         .label("Target").path("Target").type(0).build();
      MoveTaskFolderRequest request = mock(MoveTaskFolderRequest.class);
      when(request.getTasks()).thenReturn(null);
      when(request.getFolders()).thenReturn(new String[]{"Monthly"});
      when(request.getTarget()).thenReturn(target);

      controller.moveFolder(request, principal);

      verify(scheduleTaskFolderService).moveScheduleItems(
         isNull(), eq(new String[]{"Monthly"}), argThat(e -> "Target".equals(e.getPath())), eq(principal));
   }
}
