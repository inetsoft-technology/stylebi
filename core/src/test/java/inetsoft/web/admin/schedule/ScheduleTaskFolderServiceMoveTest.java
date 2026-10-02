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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.util.IndexedStorage;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77379, a folder move of schedule tasks must not trust the client's task model: the stored
 * task decides whether it can be moved (the removable flag of the model is ignored) and which
 * folder it is moved out of, and nothing is written for a task that can't be moved.
 */
@Tag("core")
class ScheduleTaskFolderServiceMoveTest {
   private static final String ORG = "orgx";
   private static final IdentityID ALICE = new IdentityID("alice", ORG);
   private static final String BACKUP = InternalScheduledTaskService.ASSET_FILE_BACKUP;

   private ScheduleManager scheduleManager;
   private IndexedStorage indexedStorage;
   private ScheduleTaskFolderService service;
   private Principal principal;
   private AssetEntry target;
   private MockedStatic<Audit> auditStatic;
   private MockedConstruction<ActionRecord> actionRecords;

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      indexedStorage = mock(IndexedStorage.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          any(ResourceAction.class))).thenReturn(true);
      // the save and the rename transform are verified through changeTaskFolder()
      service = spy(new ScheduleTaskFolderService(
         scheduleManager, securityEngine, mock(SecurityProvider.class), indexedStorage,
         mock(RenameTransformHandler.class)));
      doNothing().when(service).changeTaskFolder(any(), any(), any());
      principal = mock(Principal.class);
      when(principal.getName()).thenReturn(ALICE.convertToKey());
      target = folder("Mine");

      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(mock(Audit.class));
      // the audit record reads the server environment
      actionRecords = mockConstruction(ActionRecord.class);
   }

   @AfterEach
   void tearDown() {
      auditStatic.close();
      actionRecords.close();
   }

   @Test
   void normalTask_isMovedOutOfItsStoredFolder() throws Exception {
      ScheduleTask task = task(ALICE, "t1", ScheduleTask.Type.NORMAL_TASK, true, "Stored");
      AssetFolder stored = new AssetFolder();
      when(indexedStorage.getXMLSerializable(folder("Stored").toIdentifier(), null))
         .thenReturn(stored);

      // the client claims the task is in another folder, which isn't changed
      move(model(task.getTaskId(), ALICE, "Other", true));

      ArgumentCaptor<AssetEntry> taskEntry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(service).changeTaskFolder(taskEntry.capture(), eq(target), eq(principal));
      assertEquals(task.getTaskId(), taskEntry.getValue().getName());
      verify(indexedStorage).putXMLSerializable(folder("Stored").toIdentifier(), stored);
      verify(indexedStorage, never()).getXMLSerializable(eq(folder("Other").toIdentifier()),
                                                          any());
   }

   @Test
   void internalTaskWithForgedRemovable_isNotMoved() throws Exception {
      task(new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()), BACKUP,
           ScheduleTask.Type.INTERNAL_TASK, false, "/");

      move(model(BACKUP, null, "/", true));

      verifyNotMoved();
   }

   // the stored flag is used even if the task type isn't internal
   @Test
   void nonRemovableTaskWithForgedRemovable_isNotMoved() throws Exception {
      ScheduleTask task = task(ALICE, "t1", ScheduleTask.Type.NORMAL_TASK, false, "/");

      move(model(task.getTaskId(), ALICE, "/", true));

      verifyNotMoved();
   }

   @Test
   void cycleTaskWithForgedRemovable_isNotMoved() throws Exception {
      ScheduleTask task = task(ALICE, "cycle", ScheduleTask.Type.CYCLE_TASK, true, "/");

      move(model(task.getTaskId(), ALICE, "/", true));

      verifyNotMoved();
      verify(task, never()).setPath(any());
   }

   // no phantom entry is added to the target folder for a task that doesn't exist
   @Test
   void nonExistentTask_isNotMoved() throws Exception {
      move(model(ALICE.convertToKey() + ":ghost", ALICE, "/", true));

      verifyNotMoved();
   }

   @Test
   void ownerlessTask_isNotMoved() throws Exception {
      task(null, "t1", ScheduleTask.Type.NORMAL_TASK, true, "/");

      move(model("t1", null, "/", true));

      verifyNotMoved();
   }

   @Test
   void nullTaskModel_isSkipped() throws Exception {
      ScheduleTask task = task(ALICE, "t1", ScheduleTask.Type.NORMAL_TASK, true, "/");

      move(null, model(task.getTaskId(), ALICE, "/", true));

      verify(service, times(1)).changeTaskFolder(any(), eq(target), eq(principal));
   }

   // the model of the EM import (#77350), an imported task still lands in the xml's folder and an
   // imported internal task still isn't moved
   @Test
   void importModel_movesImportedTaskButNotInternalTask() throws Exception {
      ScheduleTask task = task(ALICE, "t1", ScheduleTask.Type.NORMAL_TASK, true, "/");
      task(new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()), BACKUP,
           ScheduleTask.Type.INTERNAL_TASK, false, "/");

      move(model(task.getTaskId(), ALICE, "/", true));
      move(model(BACKUP, new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()),
                 "/", false));

      ArgumentCaptor<AssetEntry> taskEntry = ArgumentCaptor.forClass(AssetEntry.class);
      verify(service, times(1)).changeTaskFolder(taskEntry.capture(), eq(target), eq(principal));
      assertEquals(task.getTaskId(), taskEntry.getValue().getName());
   }

   // behavior change: a site admin moving an internal task in the EM, which already passed the
   // task permission check of the EM controller, doesn't move it either
   @Test
   void siteAdminEmMoveOfInternalTask_isNotMoved() throws Exception {
      task(new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()), BACKUP,
           ScheduleTask.Type.INTERNAL_TASK, false, "/");
      when(principal.getName()).thenReturn(
         new IdentityID("admin", Organization.getDefaultOrganizationID()).convertToKey());

      move(model(BACKUP, new IdentityID(XPrincipal.SYSTEM, Organization.getDefaultOrganizationID()),
                 "/", true));

      verifyNotMoved();
   }

   private void move(ScheduleTaskModel... models) throws Exception {
      service.moveScheduleItems(models, new String[0], target, principal);
   }

   private void verifyNotMoved() throws Exception {
      verify(service, never()).changeTaskFolder(any(), any(), any());
      verify(indexedStorage, never()).putXMLSerializable(anyString(), any());
      verify(indexedStorage, never()).remove(anyString());
   }

   private ScheduleTask task(IdentityID owner, String name, ScheduleTask.Type type,
                             boolean removable, String path)
   {
      String taskId = owner == null || XPrincipal.SYSTEM.equals(owner.name) ?
         name : owner.convertToKey() + ":" + name;
      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getTaskId()).thenReturn(taskId);
      when(task.getName()).thenReturn(name);
      when(task.getOwner()).thenReturn(owner);
      when(task.getType()).thenReturn(type);
      when(task.isRemovable()).thenReturn(removable);
      when(task.getPath()).thenReturn(path);
      when(scheduleManager.getScheduleTask(taskId)).thenReturn(task);
      return task;
   }

   // a mock, the owner of a model sent by the client may be missing
   private static ScheduleTaskModel model(String name, IdentityID owner, String path,
                                          boolean removable)
   {
      ScheduleTaskModel model = mock(ScheduleTaskModel.class);
      when(model.name()).thenReturn(name);
      when(model.owner()).thenReturn(owner);
      when(model.path()).thenReturn(path);
      when(model.removable()).thenReturn(removable);
      return model;
   }

   private static AssetEntry folder(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                            path, null);
   }
}
