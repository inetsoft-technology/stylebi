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
package inetsoft.web.admin.ai.schedule;

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import inetsoft.web.admin.schedule.model.ScheduleTaskModel;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Direct unit coverage for the pure path-arithmetic helpers (design §2's own building blocks for
 * every verb) and the two pieces of logic that do not exist anywhere else in {@code
 * ScheduleTaskFolderService}: mkdir-p ({@link AdminScheduleFolderGateway#createFolder}, design §3
 * decision 4) and the recursive contained-task count ({@link
 * AdminScheduleFolderGateway#countContainedTasks}, design §0.2/§3 decision 3).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class AdminScheduleFolderGatewayTest {
   @Mock private ScheduleTaskFolderService taskFolderService;
   @Mock private ScheduleService scheduleService;
   @Mock private ScheduleManager scheduleManager;
   @Mock private SecurityEngine securityEngine;
   @Mock private ScheduleTaskService scheduleTaskService;
   @Mock private Principal user;
   private AdminScheduleFolderGateway gateway;

   @BeforeEach void setUp() {
      gateway = new AdminScheduleFolderGateway(
         taskFolderService, scheduleService, scheduleManager, securityEngine, scheduleTaskService);
      lenient().when(taskFolderService.getFolderEntry(anyString()))
         .thenAnswer(inv -> entry(inv.getArgument(0)));
   }

   // -------------------------------------------------------------------------
   // static path helpers
   // -------------------------------------------------------------------------

   @Test void normalizePathTreatsNullBlankAndSlashAsRoot() {
      assertEquals("/", AdminScheduleFolderGateway.normalizePath(null));
      assertEquals("/", AdminScheduleFolderGateway.normalizePath(""));
      assertEquals("/", AdminScheduleFolderGateway.normalizePath("  "));
      assertEquals("/", AdminScheduleFolderGateway.normalizePath("/"));
   }

   // ScheduleTaskFolderService/AssetEntry never carry a leading slash on a non-root path -- a
   // caller-supplied one (a natural, unambiguous alias) is stripped so both forms resolve to the
   // identical folder identity.
   @Test void normalizePathStripsALeadingSlashOnANonRootPath() {
      assertEquals("A/B", AdminScheduleFolderGateway.normalizePath("/A/B"));
      assertEquals("A/B", AdminScheduleFolderGateway.normalizePath("A/B"));
   }

   @Test void parentOfAndLeafOf() {
      assertEquals("/", AdminScheduleFolderGateway.parentOf("A"));
      assertEquals("A", AdminScheduleFolderGateway.leafOf("A"));
      assertEquals("A", AdminScheduleFolderGateway.parentOf("A/B"));
      assertEquals("B", AdminScheduleFolderGateway.leafOf("A/B"));
      assertEquals("/", AdminScheduleFolderGateway.parentOf("/"));
   }

   @Test void joinPathIsRootAware() {
      assertEquals("B", AdminScheduleFolderGateway.joinPath("/", "B"));
      assertEquals("A/B", AdminScheduleFolderGateway.joinPath("A", "B"));
   }

   // -------------------------------------------------------------------------
   // findFolder / folderExists
   // -------------------------------------------------------------------------

   @Test void findFolderReturnsNullWhenStorageThrows() throws Exception {
      when(taskFolderService.getTaskFolder(anyString())).thenThrow(new RuntimeException("boom"));

      assertNull(gateway.findFolder("/A"));
      assertFalse(gateway.folderExists("/A"));
   }

   @Test void findFolderReturnsNullWhenStorageReturnsNull() throws Exception {
      when(taskFolderService.getTaskFolder(anyString())).thenReturn(null);

      assertNull(gateway.findFolder("/A"));
      assertFalse(gateway.folderExists("/A"));
   }

   @Test void folderExistsTrueWhenFolderPresent() throws Exception {
      when(taskFolderService.getTaskFolder(anyString())).thenReturn(new AssetFolder());

      assertTrue(gateway.folderExists("/A"));
   }

   // -------------------------------------------------------------------------
   // resolveInheritedOwner -- mkdir-p's own owner-inheritance preview
   // -------------------------------------------------------------------------

   @Test void resolveInheritedOwnerWalksUpToNearestExistingAncestor() throws Exception {
      IdentityID rootOwner = new IdentityID("admin", "host-org");
      AssetFolder root = new AssetFolder();
      root.setOwner(rootOwner);

      when(taskFolderService.getTaskFolder(entry("/").toIdentifier())).thenReturn(root);
      when(taskFolderService.getTaskFolder(entry("Missing").toIdentifier())).thenThrow(new RuntimeException("no such folder"));
      when(taskFolderService.getTaskFolder(entry("Missing/Deeper").toIdentifier())).thenThrow(new RuntimeException("no such folder"));

      assertEquals(rootOwner, gateway.resolveInheritedOwner("Missing/Deeper"));
   }

   // -------------------------------------------------------------------------
   // countContainedTasks
   // -------------------------------------------------------------------------

   @Test void countContainedTasksIsZeroForAMissingFolder() throws Exception {
      when(taskFolderService.getTaskFolder(anyString())).thenThrow(new RuntimeException("boom"));

      assertEquals(0, gateway.countContainedTasks("/A"));
   }

   @Test void countContainedTasksIsZeroForAnEmptyFolder() throws Exception {
      AssetFolder folder = new AssetFolder();
      when(taskFolderService.getTaskFolder(entry("A").toIdentifier())).thenReturn(folder);

      assertEquals(0, gateway.countContainedTasks("/A"));
   }

   @Test void countContainedTasksCountsRecursivelyThroughNestedFolders() throws Exception {
      AssetEntry task1 = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK, "/task1", null);
      AssetEntry task2 = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK, "/task2", null);
      AssetEntry childFolderEntry = entry("/A/B");

      AssetFolder childFolder = new AssetFolder();
      childFolder.addEntry(task2);

      AssetFolder rootFolder = new AssetFolder();
      rootFolder.addEntry(task1);
      rootFolder.addEntry(childFolderEntry);

      when(taskFolderService.getTaskFolder(entry("A").toIdentifier())).thenReturn(rootFolder);
      when(taskFolderService.getTaskFolder(childFolderEntry.toIdentifier())).thenReturn(childFolder);

      assertEquals(2, gateway.countContainedTasks("/A"));
   }

   // -------------------------------------------------------------------------
   // createFolder -- mkdir-p (design §3 decision 4)
   // -------------------------------------------------------------------------

   @Test void createFolderAutoCreatesMissingAncestorsOnly() throws Exception {
      // "A" already exists; "A/B" (the requested folder's own parent) does not.
      when(taskFolderService.getTaskFolder(entry("A").toIdentifier())).thenReturn(new AssetFolder());
      when(taskFolderService.getTaskFolder(entry("A/B").toIdentifier())).thenThrow(new RuntimeException("missing"));
      when(taskFolderService.getTaskFolder(entry("A/B/C").toIdentifier())).thenThrow(new RuntimeException("missing"));

      gateway.createFolder("/A/B/C", user);

      // Only the two missing segments are created; the already-existing "A" is left untouched.
      verify(taskFolderService, never()).addFolder(any(), eq("A"), any(), anyInt(), any());
      verify(taskFolderService).addFolder(any(), eq("A/B"), eq("A"), anyInt(), eq(user));
      verify(taskFolderService).addFolder(any(), eq("A/B/C"), eq("A/B"), anyInt(), eq(user));
   }

   @Test void createFolderThrowsWhenPathAlreadyExists() throws Exception {
      when(taskFolderService.getTaskFolder(anyString())).thenReturn(new AssetFolder());

      // folderExists("A") is true for every segment here since every lookup is stubbed to
      // succeed -- nothing gets created.
      gateway.createFolder("/A", user);

      verify(taskFolderService, never()).addFolder(any(), anyString(), anyString(), anyInt(), any());
   }

   // -------------------------------------------------------------------------
   // renameFolder -- name-only owner preservation (design §3 decision 2)
   // -------------------------------------------------------------------------

   // ScheduleTaskFolderService#changeFolder's own owner-resolution rule: a NULL owner argument
   // WIPES the folder's existing owner (it does not mean "keep the current one") -- only an
   // IdentityID with a BLANK name triggers the "inherit the prior owner" branch. Passing a literal
   // null here would silently reintroduce that footgun; this test fails loud if a future
   // "simplification" of renameFolder ever does.
   @Test void renameFolderSendsAnEmptyNameIdentityIdNotANullOwner() throws Exception {
      ArgumentCaptor<EditTaskFolderDialogModel> captor = ArgumentCaptor.forClass(EditTaskFolderDialogModel.class);
      when(taskFolderService.renameFolder(captor.capture(), eq(user))).thenReturn(null);

      gateway.renameFolder("A", "B", user);

      EditTaskFolderDialogModel model = captor.getValue();
      assertEquals("A", model.oldPath());
      assertEquals("B", model.folderName());
      assertNotNull(model.owner(), "a null owner would WIPE the folder's existing owner, not preserve it");
      assertEquals("", model.owner().name);
      assertNull(model.owner().orgID);
   }

   // -------------------------------------------------------------------------
   // moveFolder / deleteFolder delegation
   // -------------------------------------------------------------------------

   @Test void moveFolderDelegatesWithSingleEntryFoldersArrayAndNoTasks() throws Exception {
      gateway.moveFolder("/A", "/Target", user);

      verify(taskFolderService).moveScheduleItems(
         isNull(), eq(new String[]{ "A" }), argThat(e -> "Target".equals(e.getPath())), eq(user));
   }

   @Test void deleteFolderDelegatesToScheduleServiceRemoveScheduleFolders() throws Exception {
      gateway.deleteFolder("/A", user);

      verify(scheduleService).removeScheduleFolders(
         argThat(model -> model.taskNames().contains("A")), eq(user));
   }

   // -------------------------------------------------------------------------
   // moveTask / taskExists / getTaskPath (bug #76841)
   // -------------------------------------------------------------------------

   @Test void taskExistsTrueWhenTaskFound() {
      when(scheduleManager.getScheduleTask("task1")).thenReturn(removableTask("task1", "Old"));

      assertTrue(gateway.taskExists("task1"));
   }

   @Test void taskExistsFalseWhenTaskMissing() {
      when(scheduleManager.getScheduleTask("missing")).thenReturn(null);

      assertFalse(gateway.taskExists("missing"));
   }

   @Test void getTaskPathReturnsNullWhenTaskMissing() {
      when(scheduleManager.getScheduleTask("missing")).thenReturn(null);

      assertNull(gateway.getTaskPath("missing"));
   }

   @Test void getTaskPathReturnsTheTasksCurrentPath() {
      when(scheduleManager.getScheduleTask("task1")).thenReturn(removableTask("task1", "Old"));

      assertEquals("Old", gateway.getTaskPath("task1"));
   }

   @Test void moveTaskThrowsMissingResourceExceptionWhenTaskNotFound() {
      when(scheduleManager.getScheduleTask("missing")).thenReturn(null);

      assertThrows(MissingResourceException.class, () -> gateway.moveTask("missing", "Target", user));
   }

   // The permission check moveScheduleItems itself does NOT perform for a task -- the native
   // EM "Move Task" dialog adds it one layer up (EMScheduleTaskFolderController#moveFolder); this
   // gateway reproduces the identical check but throws loud instead of silently no-op-ing.
   @Test void moveTaskThrowsUnauthorizedWhenNeitherWriteNorDeletePermission() throws Exception {
      ScheduleTask task = removableTask("task1", "Old");
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(securityEngine.checkPermission(
         user, ResourceType.SCHEDULE_TASK, "task1", ResourceAction.WRITE)).thenReturn(false);
      when(scheduleTaskService.canDeleteTask(task, user)).thenReturn(false);

      assertThrows(UnauthorizedAccessException.class,
         () -> gateway.moveTask("task1", "Target", user));
      verify(taskFolderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   @Test void moveTaskSucceedsWithDeletePermissionAloneWhenWriteIsDenied() throws Exception {
      ScheduleTask task = removableTask("task1", "Old");
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(securityEngine.checkPermission(
         user, ResourceType.SCHEDULE_TASK, "task1", ResourceAction.WRITE)).thenReturn(false);
      when(scheduleTaskService.canDeleteTask(task, user)).thenReturn(true);
      lenient().when(scheduleService.isSecurityEnabled()).thenReturn(true);

      moveTaskWithMockedAliasLookup("task1", "Target");

      // The model's own name() is the fully-qualified id (owner~;~org:name) ScheduleTaskModel
      // always builds from a non-null owner -- NOT the bare "task1" this method was called with.
      verify(taskFolderService).moveScheduleItems(
         argThat(models -> models.length == 1 && task.getTaskId().equals(models[0].name())),
         eq(new String[0]), argThat(e -> "Target".equals(e.getPath())), eq(user));
   }

   // Data-cycle-owned tasks are silently skipped by moveScheduleItems's own taskModels loop (no
   // exception at all) -- this gateway must refuse loud instead of forwarding a call that would
   // appear to succeed while doing nothing.
   @Test void moveTaskThrowsWhenTaskIsNotRemovable() throws Exception {
      ScheduleTask task = removableTask("task1", "Old");
      task.setRemovable(false);
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(securityEngine.checkPermission(
         user, ResourceType.SCHEDULE_TASK, "task1", ResourceAction.WRITE)).thenReturn(true);
      lenient().when(scheduleService.isSecurityEnabled()).thenReturn(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> moveTaskWithMockedAliasLookup("task1", "Target"));
      assertTrue(ex.getMessage().contains("not removable"));
      verify(taskFolderService, never()).moveScheduleItems(any(), any(), any(), any());
   }

   @Test void moveTaskDelegatesWithSingleEntryTaskModelsArrayAndNoFolders() throws Exception {
      ScheduleTask task = removableTask("task1", "Old");
      when(scheduleManager.getScheduleTask("task1")).thenReturn(task);
      when(securityEngine.checkPermission(
         user, ResourceType.SCHEDULE_TASK, "task1", ResourceAction.WRITE)).thenReturn(true);
      lenient().when(scheduleService.isSecurityEnabled()).thenReturn(true);

      moveTaskWithMockedAliasLookup("task1", "/Target");

      verify(taskFolderService).moveScheduleItems(
         argThat((ScheduleTaskModel[] models) ->
            models.length == 1 && task.getTaskId().equals(models[0].name()) && models[0].removable()),
         eq(new String[0]), argThat(e -> "Target".equals(e.getPath())), eq(user));
   }

   private static ScheduleTask removableTask(String name, String path) {
      ScheduleTask task = new ScheduleTask(name);
      task.setPath(path);
      task.setOwner(new IdentityID("admin", "host-org"));
      return task;
   }

   /** {@code ScheduleTaskModel.Builder#fromTask} resolves the task owner's alias via {@code
    * SUtil.getUserAlias}, which reaches the static {@code SecurityEngine.getSecurity()} -- a live
    * Spring context is not available in this unit test, so it is mocked here, scoped to just the
    * call under test, the same way this area's own apply-service tests mock other static
    * accessors ({@code SreeEnv}/{@code Tool}/{@code Audit}). */
   private void moveTaskWithMockedAliasLookup(String taskId, String targetPath) throws Exception {
      try(MockedStatic<SecurityEngine> securityEngineStatic = mockStatic(SecurityEngine.class)) {
         SecurityEngine security = mock(SecurityEngine.class);
         securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(security);
         lenient().when(security.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
         gateway.moveTask(taskId, targetPath, user);
      }
   }

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);
   }
}
