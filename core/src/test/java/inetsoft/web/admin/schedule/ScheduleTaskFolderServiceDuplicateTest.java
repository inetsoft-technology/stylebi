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
import inetsoft.util.*;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77454, a folder rename, add or move must not replace an existing folder. The duplicate
 * endpoints the UI calls first are only hints, the write itself refuses the duplicate. The
 * caller has rights on the root and on A (and its subfolders) only, none on B.
 */
@Tag("core")
class ScheduleTaskFolderServiceDuplicateTest {
   private final Map<String, XMLSerializable> store = new HashMap<>();
   private final Map<String, Permission> perms = new HashMap<>();
   private IndexedStorage indexedStorage;
   private ScheduleTaskFolderService service;
   private Principal principal;
   private MockedStatic<Audit> auditStatic;
   private MockedConstruction<ActionRecord> actionRecords;
   private Permission permA;
   private Permission permB;

   @BeforeEach
   void setUp() throws Exception {
      indexedStorage = mock(IndexedStorage.class);
      when(indexedStorage.getXMLSerializable(anyString(), any()))
         .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
      when(indexedStorage.contains(anyString()))
         .thenAnswer(inv -> store.containsKey(inv.<String>getArgument(0)));
      doAnswer(inv -> store.put(inv.getArgument(0), inv.getArgument(1)))
         .when(indexedStorage).putXMLSerializable(anyString(), any());
      doAnswer(inv -> store.remove(inv.<String>getArgument(0)) != null)
         .when(indexedStorage).remove(anyString());

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK_FOLDER),
                                          anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> {
            String path = inv.getArgument(2);
            return "A".equals(path) || "/".equals(path) || path.startsWith("A/");
         });
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      when(securityEngine.getPermission(any(ResourceType.class), anyString()))
         .thenAnswer(inv -> perms.get(inv.<String>getArgument(1)));
      doAnswer(inv -> perms.remove(inv.<String>getArgument(1)))
         .when(securityEngine).removePermission(any(ResourceType.class), anyString());
      doAnswer(inv -> perms.put(inv.getArgument(1), inv.getArgument(2)))
         .when(securityEngine).setPermission(any(ResourceType.class), anyString(),
                                             any(Permission.class));

      service = new ScheduleTaskFolderService(
         mock(ScheduleManager.class), securityEngine, mock(SecurityProvider.class),
         indexedStorage, mock(RenameTransformHandler.class));
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(new IdentityID("x", "orgx").convertToKey());

      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      // the audit record reads the server environment
      actionRecords = mockConstruction(ActionRecord.class);

      store.put(folder("/").toIdentifier(), new AssetFolder());
      addFolder("A");
      addTask("A", "taskA");
      addFolder("B");
      addTask("B", "taskB");
      addFolder("B/subB");
      permA = new Permission();
      permB = new Permission();
      perms.put("A", permA);
      perms.put("B", permB);
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      actionRecords.close();
   }

   // the UI would have asked first and been told B is a duplicate
   @Test
   void duplicateEndpointReportsSibling() throws Exception {
      assertTrue(service.checkRenameDuplicate(renameModel("A", "B")).isDuplicate());
   }

   // a request straight to rename-folder that skips the duplicate endpoint
   @Test
   void renameOntoExistingSibling_isRefusedAndSiblingKept() {
      assertThrows(MessageException.class,
                   () -> service.renameFolder(renameModel("A", "B"), principal));

      assertSiblingBUnchanged();
      assertTrue(store.containsKey(folder("A").toIdentifier()), "A was removed");
      assertSame(permA, perms.get("A"));
   }

   // a request straight to folder/add that skips the duplicate endpoint
   @Test
   void addOntoExistingSibling_isRefusedAndSiblingKept() {
      assertThrows(MessageException.class, () -> service.addFolder(
         folder("/"), "B", "/", AssetRepository.GLOBAL_SCOPE, principal));

      assertSiblingBUnchanged();
   }

   @Test
   void addNewFolder_isCreated() throws Exception {
      service.addFolder(folder("/"), "N", "/", AssetRepository.GLOBAL_SCOPE, principal);

      assertTrue(store.containsKey(folder("N").toIdentifier()));
      assertTrue(root().containsEntry(folder("N")));
   }

   @Test
   void renameToNewName_isRenamed() throws Exception {
      AssetEntry renamed = service.renameFolder(renameModel("A", "N"), principal);

      assertEquals("N", renamed.getPath());
      assertTrue(store.containsKey(folder("N").toIdentifier()));
      assertFalse(store.containsKey(folder("A").toIdentifier()));
      assertSame(permA, perms.get("N"));
   }

   // R1, a multi-folder move where the duplicate isn't the first folder: the UI hints only look
   // at folders[0]. Nothing is moved, not even the first folder
   @Test
   void moveWhereSecondFolderIsDuplicate_isRefusedAndNothingMoved() {
      addFolder("A/X");
      addFolder("A/B");
      addTask("A/B", "taskAB");

      assertThrows(MessageException.class, () -> service.moveScheduleItems(
         null, new String[] { "A/X", "A/B" }, folder("/"), principal));

      assertSiblingBUnchanged();
      assertTrue(store.containsKey(folder("A/X").toIdentifier()), "A/X was moved");
      assertFalse(store.containsKey(folder("X").toIdentifier()), "A/X was moved");
      AssetFolder a = (AssetFolder) store.get(folder("A").toIdentifier());
      assertTrue(a.containsEntry(folder("A/X")) && a.containsEntry(folder("A/B")));
   }

   // the portal move hint checks every folder of the request
   @Test
   void portalMoveHint_checksEveryFolder() throws Exception {
      addFolder("A/X");
      addFolder("A/B");

      assertTrue(service.checkItemsDuplicate(new String[] { "A/X", "A/B" }, folder("/"))
                    .isDuplicate());
      assertFalse(service.checkItemsDuplicate(new String[] { "A/X" }, folder("/"))
                     .isDuplicate());
   }

   @Test
   void moveOfDistinctFolders_isMoved() throws Exception {
      addFolder("A/X");

      service.moveScheduleItems(null, new String[] { "A/X" }, folder("/"), principal);

      assertTrue(store.containsKey(folder("X").toIdentifier()));
      assertFalse(store.containsKey(folder("A/X").toIdentifier()));
   }

   // R4, a folder whose parent name has one character was treated as a root folder by the
   // rename duplicate check, so the UI renamed it onto its sibling
   @Test
   void renameUnderOneCharacterParent_isDetectedAndRefused() throws Exception {
      addFolder("A/x");
      addFolder("A/y");
      addTask("A/y", "taskY");
      AssetFolder y = (AssetFolder) store.get(folder("A/y").toIdentifier());

      assertTrue(service.checkRenameDuplicate(renameModel("A/x", "y")).isDuplicate());
      assertFalse(service.checkRenameDuplicate(renameModel("A/x", "z")).isDuplicate());
      assertThrows(MessageException.class,
                   () -> service.renameFolder(renameModel("A/x", "y"), principal));

      assertSame(y, store.get(folder("A/y").toIdentifier()));
      assertTrue(y.containsEntry(task("taskY")));
      assertTrue(store.containsKey(folder("A/x").toIdentifier()));
   }

   // R2, a name with a separator would move the folder into C, where the caller has no right
   @Test
   void renameWithSeparator_isRefused() {
      addFolder("C");

      assertThrows(MessageException.class,
                   () -> service.renameFolder(renameModel("A", "C/Z"), principal));

      assertFalse(store.containsKey(folder("C/Z").toIdentifier()));
      assertTrue(store.containsKey(folder("A").toIdentifier()));
   }

   @Test
   void addWithSeparator_isRefused() {
      assertThrows(MessageException.class, () -> service.addFolder(
         folder("/"), "B/Z", "/", AssetRepository.GLOBAL_SCOPE, principal));

      assertFalse(store.containsKey(folder("B/Z").toIdentifier()));
      assertSiblingBUnchanged();
   }

   // R3, a rename to the same name is a no-op, the folder isn't rewritten
   @Test
   void renameToSameName_changesNothing() throws Exception {
      AssetFolder a = (AssetFolder) store.get(folder("A").toIdentifier());
      clearInvocations(indexedStorage);

      AssetEntry renamed = service.renameFolder(renameModel("A", "A"), principal);

      assertEquals("A", renamed.getPath());
      assertSame(a, store.get(folder("A").toIdentifier()));
      assertTrue(a.containsEntry(task("taskA")));
      assertSame(permA, perms.get("A"));
      verify(indexedStorage, never()).putXMLSerializable(anyString(), any());
   }

   // R3, a rename that only changes the case isn't a duplicate
   @Test
   void caseOnlyRename_isRenamed() throws Exception {
      service.renameFolder(renameModel("A", "a"), principal);

      assertTrue(store.containsKey(folder("a").toIdentifier()));
      assertFalse(store.containsKey(folder("A").toIdentifier()));
      assertTrue(root().containsEntry(folder("a")));
   }

   private void assertSiblingBUnchanged() {
      AssetFolder b = (AssetFolder) store.get(folder("B").toIdentifier());
      List<String> entries = Arrays.stream(b.getEntries()).map(AssetEntry::getPath).sorted()
         .toList();
      assertTrue(b.containsEntry(task("taskB")), "B lost its task entry: " + entries);
      assertTrue(b.containsEntry(folder("B/subB")), "B lost its subfolder: " + entries);
      assertSame(permB, perms.get("B"), "B's permission was replaced");
   }

   private AssetFolder root() {
      return (AssetFolder) store.get(folder("/").toIdentifier());
   }

   private void addFolder(String path) {
      AssetEntry entry = folder(path);
      AssetFolder parent = (AssetFolder) store.get(entry.getParent().toIdentifier());
      parent.addEntry(entry);
      store.put(entry.toIdentifier(), new AssetFolder());
   }

   private void addTask(String folderPath, String taskId) {
      ((AssetFolder) store.get(folder(folderPath).toIdentifier())).addEntry(task(taskId));
   }

   private static AssetEntry task(String taskId) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                            "/" + taskId, null);
   }

   private static EditTaskFolderDialogModel renameModel(String oldPath, String name) {
      return EditTaskFolderDialogModel.builder()
         .oldPath(oldPath)
         .folderName(name)
         .securityEnabled(true)
         .build();
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }
}
