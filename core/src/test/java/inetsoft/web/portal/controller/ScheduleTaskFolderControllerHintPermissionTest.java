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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import inetsoft.web.portal.data.CheckTaskDuplicateRequest;
import inetsoft.web.portal.model.NewTaskFolderEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77812, the portal move and rename duplicate hints need the folder permission of the
 * operation they come before, checked before any storage lookup: move/checkDuplicate needs WRITE
 * on the target and rename/checkDuplicate needs DELETE and WRITE on the old path. Without it
 * both answer "not duplicate", so an existing folder and a missing one get the same answer.
 *
 * Bug #78137, a hint answers "not duplicate" without a storage lookup for a name the real add or
 * rename refuses, a name with a separator in particular. Such a name would look up a folder
 * outside the one the permission was checked on, which a path-dependent permission shows.
 *
 * The storage is a real BlobIndexedStorage, spied on to show that a denied hint reads nothing.
 * The requests are deserialized from JSON, as a real request is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskFolderControllerHintPermissionTest {
   private static final String ORG = "org77812";

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private ScheduleTaskFolderController controller;
   private SRPrincipal user;
   private Set<ResourceAction> granted;
   private BiPredicate<String, ResourceAction> permission;
   private ScheduleTaskFolderService service;

   @BeforeEach
   void setUp() throws Exception {
      storage = spy(new BlobIndexedStorage(blobStorageManager));
      user = new SRPrincipal(new IdentityID("user77812", ORG), new IdentityID[0],
                             new String[0], ORG, 0L);
      ThreadContext.setContextPrincipal(user);
      granted = EnumSet.allOf(ResourceAction.class);
      permission = (path, action) -> granted.contains(action);

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> permission.test(inv.getArgument(2, String.class),
                                            inv.getArgument(3, ResourceAction.class)));

      service = new ScheduleTaskFolderService(
         mock(ScheduleManager.class), securityEngine, mock(SecurityProvider.class),
         storage, mock(RenameTransformHandler.class));
      controller = new ScheduleTaskFolderController(service, mock(ScheduleService.class));

      // / -> Secret -> Secret/Payroll, / -> Mine -> Mine/Sub, Mine/Hidden -> Mine/Hidden/Deep
      AssetFolder root = new AssetFolder();
      root.addEntry(folder("Secret"));
      root.addEntry(folder("Mine"));
      storage.putXMLSerializable(folder("/").toIdentifier(), root);
      AssetFolder secret = new AssetFolder();
      secret.addEntry(folder("Secret/Payroll"));
      storage.putXMLSerializable(folder("Secret").toIdentifier(), secret);
      storage.putXMLSerializable(folder("Secret/Payroll").toIdentifier(), new AssetFolder());
      AssetFolder mine = new AssetFolder();
      mine.addEntry(folder("Mine/Sub"));
      mine.addEntry(folder("Mine/Hidden"));
      storage.putXMLSerializable(folder("Mine").toIdentifier(), mine);
      storage.putXMLSerializable(folder("Mine/Sub").toIdentifier(), new AssetFolder());
      AssetFolder hidden = new AssetFolder();
      hidden.addEntry(folder("Mine/Hidden/Deep"));
      storage.putXMLSerializable(folder("Mine/Hidden").toIdentifier(), hidden);
      storage.putXMLSerializable(folder("Mine/Hidden/Deep").toIdentifier(), new AssetFolder());
      clearInvocations(storage);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   // without WRITE on the target, move/checkDuplicate answers false whatever exists
   @Test
   void moveCheckDuplicate_withoutWrite_answersFalseForExistingAndMissing() throws Exception {
      granted.remove(ResourceAction.WRITE);

      assertFalse(controller.checkItemsDuplicate(moveRequest("Secret", "zz/Payroll"), user)
                     .isDuplicate());
      assertFalse(controller.checkItemsDuplicate(moveRequest("Secret", "zz/Nope"), user)
                     .isDuplicate());
      assertFalse(controller.checkItemsDuplicate(moveRequest("NoSuch", "zz/X"), user)
                     .isDuplicate());
      assertFalse(controller.checkItemsDuplicate(moveRequest("/", "zz/Secret"), user)
                     .isDuplicate());
      assertStorageNotRead();
   }

   // positive control: with WRITE the answers are unchanged
   @Test
   void moveCheckDuplicate_withWrite_answersByExistence() throws Exception {
      granted = EnumSet.of(ResourceAction.WRITE);

      assertTrue(controller.checkItemsDuplicate(moveRequest("Secret", "zz/Payroll"), user)
                    .isDuplicate());
      assertFalse(controller.checkItemsDuplicate(moveRequest("Secret", "zz/Nope"), user)
                     .isDuplicate());
      assertTrue(controller.checkItemsDuplicate(moveRequest("/", "zz/Secret"), user)
                    .isDuplicate());
   }

   // without DELETE and WRITE on the old path, rename/checkDuplicate answers false whatever
   // exists, a missing parent included
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

   // #78137, rename from a top-level, a nested and a missing old path with a separator in the
   // name: the hint looked up Secret/Payroll or Mine/Hidden/Deep, which the caller can't see
   @Test
   void renameCheckDuplicate_nameWithSeparator_answersFalseWithoutLookup() throws Exception {
      usePathPermission(false);

      List<String> duplicates = new ArrayList<>();

      for(String[] probe : new String[][] {
         { "Mine", "Secret/Payroll" }, { "Mine", "Secret/Nope" },
         { "Mine/Sub", "Hidden/Deep" }, { "Mine/Sub", "Hidden/Nope" },
         { "Mine/ghost", "Hidden/Deep" }, { "Mine/ghost", "Hidden/Nope" } })
      {
         if(controller.checkRenameItemDuplicate(renameModel(probe[0], probe[1]), user)
               .isDuplicate())
         {
            duplicates.add(probe[0] + " -> " + probe[1]);
         }
      }

      assertEquals(List.of(), duplicates);
      assertStorageNotRead();
      // the real rename refuses the same name
      assertThrows(MessageException.class,
                   () -> service.renameFolder(renameModel("Mine", "Secret/Payroll"), user));
   }

   // positive control: a valid name still answers by existence with the same permissions
   @Test
   void renameCheckDuplicate_validName_answersByExistence() throws Exception {
      usePathPermission(false);

      assertTrue(controller.checkRenameItemDuplicate(renameModel("Mine/Sub", "Hidden"), user)
                    .isDuplicate());
      assertFalse(controller.checkRenameItemDuplicate(renameModel("Mine/Sub", "Nope"), user)
                     .isDuplicate());
      assertTrue(controller.checkRenameItemDuplicate(renameModel("Mine", "Secret"), user)
                    .isDuplicate());
   }

   // #78137, add under the root or a granted folder with a separator in the name: the hint
   // looked up Secret/Payroll or Mine/Hidden/Deep, which the caller can't see
   @Test
   void addCheckDuplicate_nameWithSeparator_answersFalseWithoutLookup() throws Exception {
      usePathPermission(true);

      List<String> duplicates = new ArrayList<>();

      for(String[] probe : new String[][] {
         { "/", "Secret/Payroll" }, { "/", "Secret/Nope" },
         { "Mine", "Hidden/Deep" }, { "Mine", "Hidden/Nope" } })
      {
         if(controller.checkAddItemDuplicate(addRequest(probe[0], probe[1]), user)
               .isDuplicate())
         {
            duplicates.add(probe[0] + " + " + probe[1]);
         }
      }

      assertEquals(List.of(), duplicates);
      assertStorageNotRead();
      // the real add refuses the same name
      assertThrows(MessageException.class,
                   () -> controller.addFolder(addRequest("/", "Secret/Payroll"), user));
   }

   // positive control: a valid name still answers by existence with the same permissions
   @Test
   void addCheckDuplicate_validName_answersByExistence() throws Exception {
      usePathPermission(true);

      assertTrue(controller.checkAddItemDuplicate(addRequest("/", "Secret"), user)
                    .isDuplicate());
      assertFalse(controller.checkAddItemDuplicate(addRequest("/", "Nope"), user)
                     .isDuplicate());
      assertTrue(controller.checkAddItemDuplicate(addRequest("Mine", "Hidden"), user)
                    .isDuplicate());
      assertFalse(controller.checkAddItemDuplicate(addRequest("Mine", "Nope"), user)
                     .isDuplicate());
   }

   /**
    * Everything on Mine and below, nothing on Secret or Mine/Hidden and below, READ elsewhere
    * (the root and what inherits from it), and WRITE on the root if rootWrite is set.
    */
   private void usePathPermission(boolean rootWrite) {
      permission = (path, action) -> {
         if(under(path, "Secret") || under(path, "Mine/Hidden")) {
            return false;
         }

         if(under(path, "Mine")) {
            return true;
         }

         return action == ResourceAction.READ || rootWrite && action == ResourceAction.WRITE;
      };
   }

   private static boolean under(String path, String folder) {
      return path.equals(folder) || path.startsWith(folder + "/");
   }

   private void assertStorageNotRead() {
      assertTrue(mockingDetails(storage).getInvocations().isEmpty(),
                 () -> "storage was read: " + mockingDetails(storage).getInvocations());
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, ORG);
   }

   private static CheckTaskDuplicateRequest moveRequest(String target, String folder)
      throws Exception
   {
      String json = "{\"path\":\"" + target + "\",\"items\":[],\"folders\":[\"" + folder + "\"]}";
      return new ObjectMapper().readValue(json, CheckTaskDuplicateRequest.class);
   }

   private static NewTaskFolderEvent addRequest(String parent, String name) {
      NewTaskFolderEvent req = new NewTaskFolderEvent();
      req.setParent(folder(parent));
      req.setFolderName(name);
      return req;
   }

   private static EditTaskFolderDialogModel renameModel(String oldPath, String name)
      throws Exception
   {
      String json = "{\"oldPath\":\"" + oldPath + "\",\"folderName\":\"" + name + "\"," +
         "\"securityEnabled\":true,\"users\":[]}";
      return new ObjectMapper().readValue(json, EditTaskFolderDialogModel.class);
   }
}
