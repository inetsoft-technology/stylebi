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
import inetsoft.sree.security.SecurityException;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.BlobIndexedStorage;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.portal.model.NewTaskFolderEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.FileNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77523, the portal folder/add and add/checkDuplicate endpoints use only the path of the
 * client's parent entry. The scope, type, user and organization of the parent written to are the
 * server's, a missing parent is refused, and add/checkDuplicate needs WRITE on the parent like
 * folder/add does.
 *
 * The storage is a real BlobIndexedStorage, so the per-organization stores are the production
 * ones. The requests are deserialized from JSON, so AssetEntry.Deserializer runs as it does for
 * a real request.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ScheduleTaskFolderControllerParentEntryTest {
   private static final String ORG_A = "orga77523";
   private static final String ORG_B = "orgb77523";
   private static final IdentityID VICTIM = new IdentityID("victim77523", ORG_A);

   @Autowired
   private BlobStorageManager blobStorageManager;

   private BlobIndexedStorage storage;
   private ScheduleTaskFolderController controller;
   private SRPrincipal orgAUser;
   private boolean grantWrite;
   private MockedStatic<Audit> auditStatic;
   private MockedConstruction<ActionRecord> actionRecords;

   @BeforeEach
   void setUp() throws Exception {
      storage = new BlobIndexedStorage(blobStorageManager);
      orgAUser = new SRPrincipal(new IdentityID("userA77523", ORG_A), new IdentityID[0],
                                 new String[0], ORG_A, 0L);
      ThreadContext.setContextPrincipal(orgAUser);
      grantWrite = true;

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> grantWrite);

      ScheduleTaskFolderService service = new ScheduleTaskFolderService(
         mock(ScheduleManager.class), securityEngine, mock(SecurityProvider.class),
         storage, mock(RenameTransformHandler.class));
      controller = new ScheduleTaskFolderController(service, mock(ScheduleService.class));

      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      actionRecords = mockConstruction(ActionRecord.class);

      for(String org : new String[] { ORG_A, ORG_B }) {
         AssetFolder root = new AssetFolder();
         root.addEntry(folder("Shared", org));
         storage.putXMLSerializable(folder("/", org).toIdentifier(), root);
         AssetFolder shared = new AssetFolder();
         shared.setOwner(new IdentityID("owner_" + org, org));
         storage.putXMLSerializable(folder("Shared", org).toIdentifier(), shared);
      }
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      actionRecords.close();
      ThreadContext.setContextPrincipal(null);
   }

   // positive control: the parent entry the UI sends for a schedule folder
   @Test
   void scheduleFolderParent_createsFolder() throws Exception {
      controller.addFolder(request(folder("Shared", ORG_A), "Own"), orgAUser);

      assertNotNull(read("Shared/Own", ORG_A));
      assertTrue(read("Shared", ORG_A).containsEntry(folder("Shared/Own", ORG_A)));
   }

   // the UI sends the server's tree root entry, whose path is "/"
   @Test
   void rootParent_createsFolderInRoot() throws Exception {
      NewTaskFolderEvent req = request(folder("/", ORG_A), "Top");

      assertFalse(controller.checkAddItemDuplicate(req, orgAUser).isDuplicate());
      controller.addFolder(req, orgAUser);

      assertNotNull(read("Top", ORG_A));
      assertTrue(read("/", ORG_A).containsEntry(folder("Top", ORG_A)));
      assertTrue(controller.checkAddItemDuplicate(req, orgAUser).isDuplicate());
   }

   // type axis: a repository folder with the same path is not written into, the new folder
   // goes into the schedule folder with that path
   @Test
   void repositoryFolderParent_writesScheduleFolderOnly() throws Exception {
      AssetEntry wsFolder = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.FOLDER,
                                           "Shared", null, ORG_A);
      storage.putXMLSerializable(wsFolder.toIdentifier(), new AssetFolder());

      controller.addFolder(request(wsFolder, "TypeInj"), orgAUser);

      AssetFolder after =
         (AssetFolder) storage.getXMLSerializable(wsFolder.toIdentifier(), null, ORG_A);
      assertEquals(0, after.getEntries().length, "repository folder was written into");
      assertTrue(read("Shared", ORG_A).containsEntry(folder("Shared/TypeInj", ORG_A)));
      assertEquals(new IdentityID("owner_" + ORG_A, ORG_A), read("Shared/TypeInj", ORG_A).getOwner());
   }

   // scope + user axis: another user's private folder is not written into, and with no schedule
   // folder of that path the request is refused without storing an orphan folder
   @Test
   void otherUsersPrivateFolderParent_isRefused() throws Exception {
      AssetEntry privateFolder = userFolder("Private");
      storage.putXMLSerializable(privateFolder.toIdentifier(), ownedFolder(VICTIM));

      assertThrows(FileNotFoundException.class,
                   () -> controller.addFolder(request(privateFolder, "Inj"), orgAUser));

      AssetFolder after =
         (AssetFolder) storage.getXMLSerializable(privateFolder.toIdentifier(), null, ORG_A);
      assertEquals(0, after.getEntries().length, "private folder was written into");
      assertNull(read("Private/Inj", ORG_A), "orphan schedule folder stored");
   }

   // org axis: the parent is always the current organization's, the other org is untouched
   @Test
   void otherOrgParent_writesCurrentOrgOnly() throws Exception {
      controller.addFolder(request(folder("Shared", ORG_B), "OrgInj"), orgAUser);

      assertEquals(0, read("Shared", ORG_B).getEntries().length);
      assertNull(read("Shared/OrgInj", ORG_B));
      assertTrue(read("Shared", ORG_A).containsEntry(folder("Shared/OrgInj", ORG_A)));
   }

   // a missing parent is refused cleanly, not with a null pointer wrapped in a RuntimeException
   @Test
   void missingParent_isRefused() throws Exception {
      assertThrows(FileNotFoundException.class,
                   () -> controller.addFolder(request(folder("NoSuch", ORG_A), "Child"), orgAUser));
      assertNull(read("NoSuch/Child", ORG_A));
   }

   // checkDuplicate: a foreign-scope parent is looked up as the schedule folder of its path, so
   // the existence of another user's private folder is not revealed
   @Test
   void checkDuplicate_otherUsersPrivateFolderParent_isNotFound() throws Exception {
      AssetEntry salary = userFolder("Salary");
      storage.putXMLSerializable(salary.toIdentifier(), ownedFolder(VICTIM));
      AssetEntry missing = userFolder("NoSuchPrivate");

      assertThrows(FileNotFoundException.class,
                   () -> controller.checkAddItemDuplicate(request(salary, "X"), orgAUser));
      assertThrows(FileNotFoundException.class,
                   () -> controller.checkAddItemDuplicate(request(missing, "X"), orgAUser));
   }

   @Test
   void checkDuplicate_scheduleFolderParent() throws Exception {
      NewTaskFolderEvent req = request(folder("Shared", ORG_A), "Dup");

      assertFalse(controller.checkAddItemDuplicate(req, orgAUser).isDuplicate());
      controller.addFolder(req, orgAUser);
      assertTrue(controller.checkAddItemDuplicate(req, orgAUser).isDuplicate());
   }

   // checkDuplicate needs the WRITE that folder/add needs
   @Test
   void checkDuplicate_withoutWrite_isRefused() throws Exception {
      grantWrite = false;
      NewTaskFolderEvent req = request(folder("Shared", ORG_A), "NoWrite");

      assertThrows(SecurityException.class, () -> controller.checkAddItemDuplicate(req, orgAUser));
      assertThrows(SecurityException.class, () -> controller.addFolder(req, orgAUser));
      assertNull(read("Shared/NoWrite", ORG_A));
   }

   private static AssetEntry folder(String path, String org) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null, org);
   }

   private static AssetEntry userFolder(String path) {
      return new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.FOLDER, path, VICTIM,
                            ORG_A);
   }

   private static AssetFolder ownedFolder(IdentityID owner) {
      AssetFolder folder = new AssetFolder();
      folder.setOwner(owner);
      return folder;
   }

   private static NewTaskFolderEvent request(AssetEntry parent, String name) throws Exception {
      String json = "{\"parent\":{\"scope\":" + parent.getScope() + ",\"type\":\"" +
         parent.getType().name() + "\",\"path\":\"" + parent.getPath() + "\"," +
         "\"identifier\":\"" + parent.toIdentifier() + "\",\"properties\":{}}," +
         "\"folderName\":\"" + name + "\"}";
      return new ObjectMapper().readValue(json, NewTaskFolderEvent.class);
   }

   private AssetFolder read(String path, String org) throws Exception {
      return (AssetFolder) storage.getXMLSerializable(folder(path, org).toIdentifier(), null, org);
   }
}
