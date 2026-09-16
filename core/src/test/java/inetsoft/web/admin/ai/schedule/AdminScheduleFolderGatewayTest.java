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

import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
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
   @Mock private Principal user;
   private AdminScheduleFolderGateway gateway;

   @BeforeEach void setUp() {
      gateway = new AdminScheduleFolderGateway(taskFolderService, scheduleService);
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

   private static AssetEntry entry(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER, path, null);
   }
}
