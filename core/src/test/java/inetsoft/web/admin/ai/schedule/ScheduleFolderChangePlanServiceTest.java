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

import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.PlanChange;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Covers what is NEW for the folder area relative to {@code ScheduleChangePlanServiceTest}: four
 * verbs instead of two (design §6 restores {@code move}), the root-path guard (§6.2), the
 * segment-boundary-aware self/descendant move guard (§6.1's own load-bearing finding), and the
 * non-empty-delete {@code force} gate (§0.2/§3 decision 3) -- the per-entry {@code risk} varying
 * (LOW for create/rename/move, LOW/HIGH for delete by emptiness) independent of {@code
 * requiresAgentSignoff} being unconditionally {@code true} for the whole plan (§4).
 *
 * <p>Non-root path literals below deliberately carry no leading slash, matching {@code
 * AdminScheduleFolderGateway#normalizePath}'s own canonical, no-leading-slash convention (only
 * {@code "/"} itself means root) -- the same convention {@code ScheduleTaskFolderService}/{@code
 * AssetEntry} already use throughout.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleFolderChangePlanServiceTest {
   @Mock private AdminScheduleFolderGateway folderGateway;
   @Mock private Principal user;
   private ScheduleFolderChangePlanService service;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new ScheduleFolderChangePlanService(folderGateway);
      // TaskAuditToken.issue calls Tool.encryptPassword, which needs a live Spring context for its
      // key outside a unit test -- mocked the same way ScheduleChangePlanServiceTest does.
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ScheduleFolderChangePlanRequest req = request("   ", List.of(deleteChange("A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      ScheduleFolderChangePlanRequest req = request("do something", List.of());

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb("update");
      change.setPath("A");
      ScheduleFolderChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnDuplicateEntry() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());

      ScheduleFolderChangePlanRequest req =
         request("task", List.of(deleteChangeForce("A"), deleteChangeForce("A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // A caller-supplied leading slash is an unambiguous alias for the same canonical path.
   @Test void resolveNormalizesALeadingSlashAwayForDedup() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());

      ScheduleFolderChangePlanRequest req =
         request("task", List.of(deleteChangeForce("A"), deleteChangeForce("/A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // create
   // -------------------------------------------------------------------------

   @Test void resolveCreateThrowsWhenFolderNameMissing() {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_CREATE);
      ScheduleFolderChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("folderName"));
   }

   @Test void resolveCreateThrowsWhenFolderNameIsAPath() {
      ScheduleFolderChangeRequest change = createChange(null, "A/B");
      ScheduleFolderChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("folderName"));
   }

   @Test void resolveCreateThrowsWhenFolderAlreadyExists() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(true);
      ScheduleFolderChangePlanRequest req = request("task", List.of(createChange(null, "NewFolder")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveCreateThrowsWhenUnusedFieldPresent() {
      ScheduleFolderChangeRequest change = createChange(null, "NewFolder");
      change.setNewPath("Elsewhere");
      ScheduleFolderChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("newPath"));
   }

   @Test void resolveCreateSucceedsAtRootWithNullCurrentAndNonNullProposed() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(false);
      when(folderGateway.resolveInheritedOwner("/")).thenReturn(null);
      ScheduleFolderChangePlanRequest req =
         request("create a folder", List.of(createChange(null, "NewFolder")));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
      PlanChange change = plan.changes().get(0);
      assertEquals("NewFolder", change.property());
      assertNull(change.currentValue());
      assertNotNull(change.proposedValue());
      assertEquals("low", change.risk());
      assertEquals("storage", change.snapshotScope());
      assertTrue(plan.requiresStorageBackup());
      // Unconditional for the whole area (design §4), independent of this entry's own low risk.
      assertTrue(plan.requiresAgentSignoff());
      assertNotNull(plan.planHash());
   }

   @Test void resolveCreateUnderParentSucceeds() throws Exception {
      when(folderGateway.folderExists("Parent/NewFolder")).thenReturn(false);
      when(folderGateway.resolveInheritedOwner("Parent")).thenReturn(null);
      ScheduleFolderChangePlanRequest req =
         request("create a folder", List.of(createChange("Parent", "NewFolder")));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals("Parent/NewFolder", plan.changes().get(0).property());
   }

   // -------------------------------------------------------------------------
   // rename
   // -------------------------------------------------------------------------

   @Test void resolveRenameThrowsWhenNewPathMissing() {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_RENAME);
      change.setPath("A");
      ScheduleFolderChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("newPath"));
   }

   @Test void resolveRenameThrowsOnRootPath() {
      ScheduleFolderChangePlanRequest req = request("task", List.of(renameChange("/", "B")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("root"));
   }

   @Test void resolveRenameThrowsWhenNewPathResolvesToRoot() {
      ScheduleFolderChangePlanRequest req = request("task", List.of(renameChange("A", "/")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("root"));
   }

   @Test void resolveRenameThrowsWhenSourceDoesNotExist() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(null);
      ScheduleFolderChangePlanRequest req = request("task", List.of(renameChange("A", "B")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no schedule-task folder exists"));
   }

   @Test void resolveRenameThrowsWhenNewPathAlreadyExists() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("B")).thenReturn(true);
      ScheduleFolderChangePlanRequest req = request("task", List.of(renameChange("A", "B")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveRenameSucceeds() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("B")).thenReturn(false);
      ScheduleFolderChangePlanRequest req = request("rename a folder", List.of(renameChange("A", "B")));

      ResolvedPlan plan = service.resolve(req, user);

      PlanChange change = plan.changes().get(0);
      assertEquals("A", change.property());
      assertNotNull(change.currentValue());
      assertNotNull(change.proposedValue());
      assertEquals("low", change.risk());
   }

   // -------------------------------------------------------------------------
   // move
   // -------------------------------------------------------------------------

   @Test void resolveMoveThrowsWhenTargetPathMissing() {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_MOVE);
      change.setPath("A");
      ScheduleFolderChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("targetPath"));
   }

   @Test void resolveMoveThrowsOnRootSourcePath() {
      ScheduleFolderChangePlanRequest req = request("task", List.of(moveChange("/", "Target")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("root"));
   }

   // §6.2: moving something TO the top level (targetPath="/") is legitimate and must remain
   // allowed -- the root guard applies only to `path`, never to a move's `targetPath`.
   @Test void resolveMoveToRootTargetSucceeds() throws Exception {
      when(folderGateway.findFolder("A/B")).thenReturn(new AssetFolder());
      ScheduleFolderChangePlanRequest req = request("move to root", List.of(moveChange("A/B", "/")));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
   }

   // §6.1's own load-bearing finding: a folder cannot be moved into itself or a descendant.
   @Test void resolveMoveThrowsWhenTargetIsSelf() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("A")).thenReturn(true);
      ScheduleFolderChangePlanRequest req = request("task", List.of(moveChange("A", "A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("itself"));
   }

   @Test void resolveMoveThrowsWhenTargetIsDescendant() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("A/B")).thenReturn(true);
      ScheduleFolderChangePlanRequest req = request("task", List.of(moveChange("A", "A/B")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("descendant"));
   }

   // §6.1's own load-bearing finding: the underlying Java primitive's raw string-prefix guard
   // ALSO false-positives on a true sibling ("A" vs "AB/X") -- this plan service's own guard must
   // NOT reproduce that bug; moving "A" into "AB" (an unrelated sibling) must succeed.
   @Test void resolveMoveToUnrelatedSiblingWithSharedPrefixSucceeds() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("AB")).thenReturn(true);
      ScheduleFolderChangePlanRequest req = request("move", List.of(moveChange("A", "AB")));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
   }

   @Test void resolveMoveThrowsWhenTargetDoesNotExist() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("Target")).thenReturn(false);
      ScheduleFolderChangePlanRequest req = request("task", List.of(moveChange("A", "Target")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("targetPath"));
   }

   @Test void resolveMoveSucceeds() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.folderExists("Target")).thenReturn(true);
      ScheduleFolderChangePlanRequest req = request("move a folder", List.of(moveChange("A", "Target")));

      ResolvedPlan plan = service.resolve(req, user);

      PlanChange change = plan.changes().get(0);
      assertEquals("A", change.property());
      assertEquals("low", change.risk());
   }

   // -------------------------------------------------------------------------
   // delete
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsOnRootPath() {
      ScheduleFolderChangePlanRequest req = request("task", List.of(deleteChange("/")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("root"));
   }

   @Test void resolveDeleteThrowsWhenFolderDoesNotExist() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(null);
      ScheduleFolderChangePlanRequest req = request("task", List.of(deleteChange("A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no schedule-task folder exists"));
   }

   @Test void resolveDeleteThrowsWhenNonEmptyWithoutForce() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.countContainedTasks("A")).thenReturn(3);
      ScheduleFolderChangePlanRequest req = request("task", List.of(deleteChange("A")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("force"));
      assertTrue(ex.getMessage().contains("3"));
   }

   @Test void resolveDeleteSucceedsWhenNonEmptyWithForceAndReportsHighRisk() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.countContainedTasks("A")).thenReturn(3);
      ScheduleFolderChangePlanRequest req = request("delete", List.of(deleteChangeForce("A")));

      ResolvedPlan plan = service.resolve(req, user);

      PlanChange change = plan.changes().get(0);
      assertEquals("high", change.risk());
      assertNull(change.proposedValue());
   }

   @Test void resolveDeleteSucceedsWhenEmptyWithoutForceAndReportsLowRisk() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.countContainedTasks("A")).thenReturn(0);
      ScheduleFolderChangePlanRequest req = request("delete", List.of(deleteChange("A")));

      ResolvedPlan plan = service.resolve(req, user);

      assertEquals("low", plan.changes().get(0).risk());
   }

   // -------------------------------------------------------------------------
   // hash stability
   // -------------------------------------------------------------------------

   @Test void hashIsStableForIdenticalRequests() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.countContainedTasks("A")).thenReturn(0);

      ResolvedPlan first = service.resolve(request("delete", List.of(deleteChange("A"))), user);
      ResolvedPlan second = service.resolve(request("delete", List.of(deleteChange("A"))), user);

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      when(folderGateway.findFolder("A")).thenReturn(new AssetFolder());
      when(folderGateway.countContainedTasks("A")).thenReturn(0);

      ResolvedPlan first = service.resolve(request("delete folder A", List.of(deleteChange("A"))), user);
      ResolvedPlan second = service.resolve(request("remove folder A", List.of(deleteChange("A"))), user);

      assertEquals(first.planHash(), second.planHash());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static ScheduleFolderChangePlanRequest request(
      String task, List<ScheduleFolderChangeRequest> changes)
   {
      ScheduleFolderChangePlanRequest req = new ScheduleFolderChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static ScheduleFolderChangeRequest createChange(String parentPath, String folderName) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_CREATE);
      change.setParentPath(parentPath);
      change.setFolderName(folderName);
      return change;
   }

   private static ScheduleFolderChangeRequest renameChange(String path, String newPath) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_RENAME);
      change.setPath(path);
      change.setNewPath(newPath);
      return change;
   }

   private static ScheduleFolderChangeRequest moveChange(String path, String targetPath) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_MOVE);
      change.setPath(path);
      change.setTargetPath(targetPath);
      return change;
   }

   private static ScheduleFolderChangeRequest deleteChange(String path) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_DELETE);
      change.setPath(path);
      return change;
   }

   private static ScheduleFolderChangeRequest deleteChangeForce(String path) {
      ScheduleFolderChangeRequest change = deleteChange(path);
      change.setForce(true);
      return change;
   }
}
