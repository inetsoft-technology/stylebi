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
package inetsoft.web.portal.controller;

import inetsoft.sree.DefaultFolderEntry;
import inetsoft.sree.FolderEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import inetsoft.web.portal.model.NewTaskFolderEvent;

import inetsoft.test.SreeHome;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import static org.junit.jupiter.api.Assertions.*;

@SreeHome
class ScheduleTaskFolderControllerTest {
   static SecurityEngine securityEngine;
   static ScheduleTaskFolderController scheduleTaskFolderController;
   static ScheduleTaskFolderService scheduleTaskFolderService;

   static SRPrincipal admin;

   @BeforeAll
   static void before() throws Exception {
      securityEngine = SecurityEngine.getSecurity();
      securityEngine.enableSecurity();
      SUtil.setMultiTenant(true);

      scheduleTaskFolderService = new ScheduleTaskFolderService(
         ScheduleManager.getScheduleManager(), securityEngine, securityEngine.getSecurityProvider());
      ScheduleService scheduleService = Mockito.mock(ScheduleService.class);
      scheduleTaskFolderController = new ScheduleTaskFolderController(scheduleTaskFolderService, scheduleService);

      admin = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()), new IdentityID[] { new IdentityID("Administrator", null)}, new String[0], "host_org",
                              Tool.getSecureRandom().nextLong());
   }

   @AfterAll
   static void cleanup() throws Exception {

      SecurityEngine.clear();
      securityEngine.disableSecurity();
   }

   /**
    * Check add,rename,remove task folder actions
    */
   @Test
   void checkAddTaskFolderAction() throws Exception {
      NewTaskFolderEvent newTaskFolderEvent = new NewTaskFolderEvent();
      newTaskFolderEvent.setParent(new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                                  AssetEntry.Type.SCHEDULE_TASK_FOLDER, "/", new IdentityID("admin", Organization.getDefaultOrganizationID())));
      newTaskFolderEvent.setFolderName("f1");
      boolean isDuplacate = scheduleTaskFolderController.checkAddItemDuplicate(newTaskFolderEvent, admin).isDuplicate();

      if(!isDuplacate) {
         scheduleTaskFolderController.addFolder(newTaskFolderEvent, admin);
         String fname = scheduleTaskFolderController.getFolderEditModel("f1", admin).folderName();
         assertEquals("f1", fname);
      }

      EditTaskFolderDialogModel editTaskFolderDialogModel = new EditTaskFolderDialogModel.Builder()
         .folderName("f1_new")
         .oldPath("f1")
         .adminName("admin")
         .owner(new IdentityID("admin", Organization.getDefaultOrganizationID()))
         .securityEnabled(true)
         .build();
      scheduleTaskFolderController.renameFolder(editTaskFolderDialogModel, admin);
      String nfolderName =  scheduleTaskFolderController.getFolderEditModel("f1_new", admin).folderName();
      assertEquals("f1_new", nfolderName);

      AssetEntry folderEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                                              "f1_new", null, Organization.getDefaultOrganizationID());
      scheduleTaskFolderService.removeFolder(folderEntry);
   }
}