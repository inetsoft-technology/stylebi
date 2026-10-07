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
package inetsoft.web.admin.schedule;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.content.repository.ContentRepositoryTreeNode;
import inetsoft.web.admin.schedule.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77811, #77812, the EM folder duplicate hints need the folder permission of the operation
 * they come before, checked before any storage lookup: add/checkDuplicate needs WRITE on the
 * parent and is refused without it, check-folder needs WRITE on the target and
 * rename/checkDuplicate needs DELETE and WRITE on the old path, and both answer "not duplicate"
 * without it. Without the permission an existing folder and a missing one get the same answer.
 *
 * The storage is a real BlobIndexedStorage, spied on to show that a denied hint reads nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class EMScheduleTaskFolderControllerHintPermissionTest {
   private static final String ORG = "org77811";

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private EMScheduleTaskFolderController controller;
   private SRPrincipal user;
   private Set<ResourceAction> granted;

   @BeforeEach
   void setUp() throws Exception {
      storage = spy(new BlobIndexedStorage(blobStorageManager));
      user = new SRPrincipal(new IdentityID("user77811", ORG), new IdentityID[0],
                             new String[0], ORG, 0L);
      ThreadContext.setContextPrincipal(user);
      granted = EnumSet.allOf(ResourceAction.class);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> granted.contains(inv.getArgument(3, ResourceAction.class)));

      ScheduleTaskFolderService service = new ScheduleTaskFolderService(
         mock(ScheduleManager.class), securityEngine, mock(SecurityProvider.class),
         storage, mock(RenameTransformHandler.class));
      controller = new EMScheduleTaskFolderController(
         service, mock(ScheduleService.class), mock(ScheduleTaskService.class), securityEngine,
         mock(ScheduleManager.class));

      // / -> Secret -> Secret/Payroll
      AssetFolder root = new AssetFolder();
      root.addEntry(folder("Secret"));
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
      AssetFolder secret = new AssetFolder();
      secret.addEntry(folder("Secret/Payroll"));
      storage.putXMLSerializable(folder("Secret").toIdentifier(), secret);
      storage.putXMLSerializable(folder("Secret/Payroll").toIdentifier(), new AssetFolder());
      clearInvocations(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   // #77811, without WRITE on the parent, existing and missing folders and parents are all refused
   @Test
   void addCheckDuplicate_withoutWrite_isRefusedForExistingAndMissing() {
      granted.remove(ResourceAction.WRITE);

      for(String[] probe : new String[][] {
         { "Secret", "Payroll" }, { "Secret", "Nope" }, { "NoSuch", "X" }, { "/", "Secret" } })
      {
         assertThrows(SecurityException.class,
                      () -> controller.checkAddFolderDuplicate(addRequest(probe[0], probe[1]), user),
                      probe[0] + "/" + probe[1]);
      }

      assertStorageNotRead();
   }

   // positive control: with WRITE the answers are unchanged
   @Test
   void addCheckDuplicate_withWrite_answersByExistence() throws Exception {
      granted = EnumSet.of(ResourceAction.WRITE);

      assertTrue(controller.checkAddFolderDuplicate(addRequest("Secret", "Payroll"), user));
      assertFalse(controller.checkAddFolderDuplicate(addRequest("Secret", "Nope"), user));
      assertTrue(controller.checkAddFolderDuplicate(addRequest("/", "Secret"), user));
      assertThrows(FileNotFoundException.class,
                   () -> controller.checkAddFolderDuplicate(addRequest("NoSuch", "X"), user));
   }

   // #77812, without WRITE on the target, check-folder answers false whatever exists
   @Test
   void checkFolder_withoutWrite_answersFalseForExistingAndMissing() throws Exception {
      granted.remove(ResourceAction.WRITE);

      assertFalse(controller.checkDuplicateFolderPath(moveRequest("Secret", "zz/Payroll"), user));
      assertFalse(controller.checkDuplicateFolderPath(moveRequest("Secret", "zz/Nope"), user));
      assertFalse(controller.checkDuplicateFolderPath(moveRequest("NoSuch", "zz/X"), user));
      assertFalse(controller.checkDuplicateFolderPath(moveRequest("/", "zz/Secret"), user));
      assertStorageNotRead();
   }

   @Test
   void checkFolder_withWrite_answersByExistence() throws Exception {
      granted = EnumSet.of(ResourceAction.WRITE);

      assertTrue(controller.checkDuplicateFolderPath(moveRequest("Secret", "zz/Payroll"), user));
      assertFalse(controller.checkDuplicateFolderPath(moveRequest("Secret", "zz/Nope"), user));
      assertTrue(controller.checkDuplicateFolderPath(moveRequest("/", "zz/Secret"), user));
   }

   // #77812, without DELETE and WRITE on the old path, rename/checkDuplicate answers false
   // whatever exists, a missing parent included
   @Test
   void renameCheckDuplicate_withoutPermission_answersFalseForExistingAndMissing()
      throws Exception
   {
      granted = EnumSet.noneOf(ResourceAction.class);

      assertFalse(controller.checkRenameItemDuplicate(renameModel("Secret/x", "Payroll"), user)
                     .isDuplicate());
      assertFalse(controller.checkRenameItemDuplicate(renameModel("Secret/x", "Nope"), user)
                     .isDuplicate());
      assertFalse(controller.checkRenameItemDuplicate(renameModel("NoSuch/x", "A"), user)
                     .isDuplicate());
      assertFalse(controller.checkRenameItemDuplicate(renameModel("//x", "Secret"), user)
                     .isDuplicate());
      assertStorageNotRead();
   }

   // New Folder asks the rename hint with a made-up path in the parent first, so a user who can
   // add to the folder but not delete in it gets "not duplicate", not an error
   @Test
   void renameCheckDuplicate_writeWithoutDelete_answersFalseWithoutRefusing() throws Exception {
      granted = EnumSet.of(ResourceAction.WRITE);

      assertFalse(controller.checkRenameItemDuplicate(renameModel("Secret/x", "Payroll"), user)
                     .isDuplicate());
      assertFalse(controller.checkRenameItemDuplicate(renameModel("NoSuch/x", "A"), user)
                     .isDuplicate());
      assertStorageNotRead();
   }

   @Test
   void renameCheckDuplicate_withPermission_answersByExistence() throws Exception {
      granted = EnumSet.of(ResourceAction.DELETE, ResourceAction.WRITE);

      assertTrue(controller.checkRenameItemDuplicate(renameModel("Secret/x", "Payroll"), user)
                    .isDuplicate());
      assertFalse(controller.checkRenameItemDuplicate(renameModel("Secret/x", "Nope"), user)
                     .isDuplicate());
      assertThrows(FileNotFoundException.class,
                   () -> controller.checkRenameItemDuplicate(renameModel("NoSuch/x", "A"), user));
      // no model is still a duplicate, as before
      assertTrue(controller.checkRenameItemDuplicate(null, user).isDuplicate());
   }

   private void assertStorageNotRead() {
      assertTrue(mockingDetails(storage).getInvocations().isEmpty(),
                 () -> "storage was read: " + mockingDetails(storage).getInvocations());
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }

   private static ContentRepositoryTreeNode node(String path) {
      return ContentRepositoryTreeNode.builder()
         .label(path)
         .path(path)
         .type(RepositoryEntry.FOLDER)
         .build();
   }

   private static NewTaskFolderRequest addRequest(String parent, String name) {
      NewTaskFolderRequest req = new NewTaskFolderRequest();
      req.setParent(node(parent));
      req.setFolderName(name);
      return req;
   }

   private static MoveTaskFolderRequest moveRequest(String target, String folder) {
      MoveTaskFolderRequest req = new MoveTaskFolderRequest();
      req.setTarget(node(target));
      req.setTasks(new ScheduleTaskModel[0]);
      req.setFolders(new String[] { folder });
      return req;
   }

   private static EditTaskFolderDialogModel renameModel(String oldPath, String name) {
      return EditTaskFolderDialogModel.builder()
         .oldPath(oldPath)
         .folderName(name)
         .securityEnabled(true)
         .build();
   }
}
