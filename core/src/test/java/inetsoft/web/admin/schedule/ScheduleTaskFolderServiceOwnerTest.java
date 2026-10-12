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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.IndexedStorage;
import inetsoft.util.XMLSerializable;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77406, the owner of a schedule task folder is kept on a rename or move, whatever owner the
 * client sends, and the owner doesn't decide whether a folder of the same name already exists (the
 * folder is stored by its path only, so a rename onto a folder of another owner overwrites it).
 */
@Tag("core")
class ScheduleTaskFolderServiceOwnerTest {
   private static final IdentityID X = new IdentityID("x", "orgx");
   private static final IdentityID FOREIGN = new IdentityID("sadm", Organization.getDefaultOrganizationID());

   private final Map<String, XMLSerializable> store = new HashMap<>();
   private ScheduleTaskFolderService service;
   private Principal principal;
   private MockedStatic<Audit> auditStatic;
   private MockedConstruction<ActionRecord> actionRecords;

   @BeforeEach
   void setUp() throws Exception {
      IndexedStorage indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getXMLSerializable(anyString(), any()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      when(indexedStorage.contains(anyString()))
         .thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
      doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
         .when(indexedStorage).putXMLSerializable(anyString(), any());
      doAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null)
         .when(indexedStorage).remove(anyString());

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      service = new ScheduleTaskFolderService(
         mock(ScheduleManager.class), securityEngine, mock(SecurityProvider.class),
         indexedStorage, mock(RenameTransformHandler.class));
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(X.convertToKey());

      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      // the audit record reads the server environment
      actionRecords = mockConstruction(ActionRecord.class);

      // the root of UI-created folders has no owner
      store.put(folder("/").toIdentifier(), new AssetFolder());
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      actionRecords.close();
   }

   @Test
   void rename_withForeignOwner_keepsStoredOwner() throws Exception {
      addFolder("A", null);

      service.renameFolder(renameModel("A", "A2", FOREIGN), principal);

      assertNull(owner("A2"));
   }

   @Test
   void rename_withNullOwner_keepsStoredOwner() throws Exception {
      addFolder("B", X);

      service.renameFolder(renameModel("B", "B2", null), principal);

      assertEquals(X, owner("B2"));
   }

   @Test
   void renameParent_keepsChildOwner() throws Exception {
      addFolder("B", X);
      addFolder("B/sub", X);

      service.renameFolder(renameModel("B", "B2", X), principal);

      assertEquals(X, owner("B2"));
      assertEquals(X, owner("B2/sub"));
   }

   @Test
   void move_keepsOwners() throws Exception {
      addFolder("T", null);
      addFolder("C", X);
      addFolder("C/sub", X);

      service.moveScheduleItems(null, new String[] { "C" }, folder("T"), principal);

      assertEquals(X, owner("T/C"));
      assertEquals(X, owner("T/C/sub"));
   }

   // the stock UI echoes the stored owner of the renamed folder, which differs from the imported
   // owner of the sibling
   @Test
   void renameOntoSiblingOfOtherOwner_isDuplicate() throws Exception {
      addFolder("A", null);
      addFolder("B", FOREIGN);

      assertTrue(service.checkRenameDuplicate(renameModel("A", "B", null)).isDuplicate());
   }

   @Test
   void addNextToSiblingOfOtherOwner_isDuplicate() throws Exception {
      addFolder("B", FOREIGN);

      assertTrue(service.checkAddDuplicate(folder("/"), "B", AssetRepository.GLOBAL_SCOPE,
                                           principal).isDuplicate());
   }

   @Test
   void renameToFreeName_isNotDuplicate() throws Exception {
      addFolder("A", null);
      addFolder("B", FOREIGN);

      assertFalse(service.checkRenameDuplicate(renameModel("A", "C", null)).isDuplicate());
   }

   private void addFolder(String path, IdentityID owner) {
      AssetEntry entry = folder(path);
      AssetFolder parent = (AssetFolder) store.get(entry.getParent().toIdentifier());
      parent.addEntry(entry);
      AssetFolder folder = new AssetFolder();
      folder.setOwner(owner);
      store.put(entry.toIdentifier(), folder);
   }

   private IdentityID owner(String path) {
      AssetFolder folder = (AssetFolder) store.get(folder(path).toIdentifier());
      assertNotNull(folder, path);
      return folder.getOwner();
   }

   private static EditTaskFolderDialogModel renameModel(String oldPath, String name,
                                                        IdentityID owner)
   {
      return EditTaskFolderDialogModel.builder()
         .oldPath(oldPath)
         .folderName(name)
         .securityEnabled(true)
         .owner(owner)
         .build();
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }
}
