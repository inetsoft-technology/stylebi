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
package inetsoft.web.portal.controller;

import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;
import inetsoft.web.portal.model.PortalMoveTaskFolderRequest;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77379, the portal move of schedule tasks must only move stored tasks the user can move in
 * the portal (the user's own or group shared tasks the user can delete, in a folder the user can
 * read), and must move them into a folder of the user's organization.
 */
@Tag("core")
class ScheduleTaskFolderControllerMoveTest {
   private static final String ORG = "orga";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final IdentityID BOB = new IdentityID("bob", ORG);

   private ScheduleTaskFolderService folderService;
   private ScheduleService scheduleService;
   private ScheduleTaskFolderController controller;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      folderService = mock(ScheduleTaskFolderService.class);
      scheduleService = mock(ScheduleService.class);
      controller = new ScheduleTaskFolderController(folderService, scheduleService);
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(ALICE.convertToKey());
      when(scheduleService.canDeleteTask(any(ScheduleTask.class), any())).thenReturn(true);
      when(folderService.checkFolderPermission(anyString(), any(), any())).thenReturn(true);
   }

   @Test
   void ownTask_isMoved() throws Exception {
      ScheduleTaskModel model = stored(ALICE, "t1", "/");

      controller.moveFolder(request(target("Mine", null), model), principal);

      verify(folderService).moveScheduleItems(any(), any(), any(), eq(principal));
   }

   @Test
   void taskOfOtherUser_isRefused() throws Exception {
      ScheduleTaskModel model = stored(BOB, "nightly", "/");

      assertThrows(MessageException.class,
                   () -> controller.moveFolder(request(target("Mine", null), model), principal));

      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   @Test
   void groupSharedTask_isMoved() throws Exception {
      ScheduleTaskModel model = stored(BOB, "shared", "/");
      when(scheduleService.isGroupShareTask(any(ScheduleTask.class), eq(principal)))
         .thenReturn(true);

      controller.moveFolder(request(target("Mine", null), model), principal);

      verify(folderService).moveScheduleItems(any(), any(), any(), eq(principal));
   }

   @Test
   void groupSharedTaskUserCantDelete_isRefused() throws Exception {
      ScheduleTaskModel model = stored(BOB, "shared", "/");
      when(scheduleService.isGroupShareTask(any(ScheduleTask.class), eq(principal)))
         .thenReturn(true);
      when(scheduleService.canDeleteTask(any(ScheduleTask.class), eq(principal)))
         .thenReturn(false);

      assertThrows(MessageException.class,
                   () -> controller.moveFolder(request(target("Mine", null), model), principal));

      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   // the folder the task is stored in is checked, not the one the client names
   @Test
   void taskInFolderUserCantRead_isRefused() throws Exception {
      ScheduleTaskModel model = stored(ALICE, "t1", "Hidden");
      when(folderService.checkFolderPermission("Hidden", principal, ResourceAction.READ))
         .thenReturn(false);

      assertThrows(MessageException.class,
                   () -> controller.moveFolder(request(target("Mine", null), model), principal));

      verify(folderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   // a task that can't be moved (e.g. an internal task with a forged removable flag) is left to
   // the move, which skips it
   @Test
   void unmovableTask_isNotChecked() throws Exception {
      ScheduleTaskModel model = mock(ScheduleTaskModel.class);
      when(folderService.getMovableTask(model)).thenReturn(null);

      controller.moveFolder(request(target("Mine", null), model), principal);

      verify(scheduleService, never()).canDeleteTask(any(ScheduleTask.class), any());
      verify(folderService).moveScheduleItems(any(), any(), any(), eq(principal));
   }

   @Test
   void targetOfOtherOrg_isMovedToFolderOfUserOrg() throws Exception {
      ScheduleTaskModel model = stored(ALICE, "t1", "/");
      AssetEntry target = target("Mine", "orgb");
      assertEquals("orgb", target.getOrgID());

      controller.moveFolder(request(target, model), principal);

      ArgumentCaptor<AssetEntry> entry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(folderService).moveScheduleItems(any(), any(), entry.capture(), eq(principal));
      assertEquals("Mine", entry.getValue().getPath());
      assertEquals(AssetEntry.Type.SCHEDULE_TASK_FOLDER, entry.getValue().getType());
      assertNotEquals("orgb", entry.getValue().getOrgID());
      assertEquals(target("Mine", null).getOrgID(), entry.getValue().getOrgID());
   }

   private ScheduleTaskModel stored(IdentityID owner, String name, String path) {
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(owner.convertToKey() + ":" + name);
      when(task.getName()).thenReturn(name);
      when(task.getOwner()).thenReturn(owner);
      when(task.getPath()).thenReturn(path);
      ScheduleTaskModel model = mock(ScheduleTaskModel.class);
      when(folderService.getMovableTask(model)).thenReturn(task);
      return model;
   }

   private static PortalMoveTaskFolderRequest request(AssetEntry target,
                                                      ScheduleTaskModel... models)
   {
      PortalMoveTaskFolderRequest request = new PortalMoveTaskFolderRequest();
      request.setTasks(models);
      request.setFolders(new String[0]);
      request.setTarget(target);
      return request;
   }

   private static AssetEntry target(String path, String orgID) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, orgID);
   }
}
