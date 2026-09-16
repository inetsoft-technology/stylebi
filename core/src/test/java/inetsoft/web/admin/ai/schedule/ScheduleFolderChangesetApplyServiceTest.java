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

import inetsoft.sree.SreeEnv;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetFolder;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ApplyResult;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskFolderService;
import inetsoft.web.admin.schedule.model.EditTaskFolderDialogModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Same mocking shape as {@code ScheduleChangesetApplyServiceTest} (a REAL {@link
 * ScheduleFolderChangePlanService} wired to a mocked {@link AdminScheduleFolderGateway}, {@code
 * Tool}/{@code Audit} statics mocked). Covers the plan-hash/task-token gates, the unconditional
 * reviewOutcome requirement (design §4), and the create/delete rollback pairing this area's own
 * {@code Undo} shape uses (a delete's rollback only restores the folder LABEL, not any task
 * destroyed underneath -- see {@link ScheduleFolderChangesetApplyService}'s own javadoc).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ScheduleFolderChangesetApplyServiceTest {
   @Mock private AdminScheduleFolderGateway folderGateway;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   private ScheduleFolderChangePlanService planService;
   private ScheduleFolderChangesetApplyService service;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach void setUp() {
      planService = new ScheduleFolderChangePlanService(folderGateway);
      service = new ScheduleFolderChangesetApplyService(planService, folderGateway, backupService);

      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));
      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });

      Audit auditMock = mock(Audit.class);
      auditStatic = mockStatic(Audit.class, withSettings().strictness(Strictness.LENIENT));
      auditStatic.when(Audit::getInstance).thenReturn(auditMock);
   }

   @AfterEach void tearDown() {
      sreeEnv.close();
      tool.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // plan hash / task token gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsPlanHashMismatchWhenHashMissing() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(false);
      ScheduleFolderApplyRequest req = applyRequest(null, null, createChange(null, "NewFolder"));

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsTaskTokenMismatchWhenTokenMissing() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(false);
      ResolvedPlan preview = planService.resolve(planRequest(createChange(null, "NewFolder")), user);
      ScheduleFolderApplyRequest req =
         applyRequest(preview.planHash(), null, createChange(null, "NewFolder"));

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(false);
      ResolvedPlan preview = planService.resolve(planRequest(createChange(null, "NewFolder")), user);
      ScheduleFolderApplyRequest req =
         applyRequest(preview.planHash(), preview.taskToken(), createChange(null, "NewFolder"));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   // -------------------------------------------------------------------------
   // success
   // -------------------------------------------------------------------------

   @Test void appliesACreateAndReportsApplied() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(false);
      when(folderGateway.findFolder("NewFolder")).thenReturn(new AssetFolder());
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ResolvedPlan preview = planService.resolve(planRequest(createChange(null, "NewFolder")), user);
      ScheduleFolderApplyRequest req =
         applyRequest(preview.planHash(), preview.taskToken(), createChange(null, "NewFolder"));
      req.setReviewOutcome("approved");

      ApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("snap-ref", result.backupRef());
      verify(folderGateway).createFolder("NewFolder", user);
   }

   // A later entry's failure at APPLY time (not preview/resolve time) rolls an earlier, already-
   // applied create back -- its own rollback deletes the folder it created (this area's own
   // Undo.create shape). The later entry fails via a RETURNED, verified-false outcome ("the
   // folder still exists after delete") rather than a thrown exception -- a thrown failure is
   // deliberately treated more severely (STATUS_ROLLBACK_FAILED unconditionally, per {@code
   // AdminChangesetApplyService}'s own "state unknown" precedent this class's apply loop mirrors),
   // so this scenario specifically exercises the cleaner, fully-verified rollback path.
   @Test void rollsBackAnEarlierCreateWhenALaterDeleteFailsVerification() throws Exception {
      when(folderGateway.folderExists("NewFolder")).thenReturn(false);
      // Verified once after create (non-null), then null again after the rollback's own undo-delete.
      when(folderGateway.findFolder("NewFolder")).thenReturn(new AssetFolder(), (AssetFolder) null);
      // "Missing" resolves as existing at both preview and apply's internal re-resolve, and its
      // own applyDelete before-capture -- but the folder is (unrealistically, for this test's own
      // purposes) still reported present immediately after deleteFolder is called, simulating a
      // delete that did not actually take effect.
      when(folderGateway.findFolder("Missing")).thenReturn(new AssetFolder());
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleFolderChangeRequest create = createChange(null, "NewFolder");
      ScheduleFolderChangeRequest badDelete = deleteChangeForce("Missing");

      ResolvedPlan preview = planService.resolve(planRequest(create, badDelete), user);
      ScheduleFolderApplyRequest req =
         applyRequest(preview.planHash(), preview.taskToken(), create, badDelete);
      req.setReviewOutcome("approved");

      ApplyResult result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      verify(folderGateway).createFolder("NewFolder", user);
      verify(folderGateway).deleteFolder("Missing", user);
      verify(folderGateway).deleteFolder("NewFolder", user);
   }

   // -------------------------------------------------------------------------
   // owner preservation on rename (reviewer round 1: this file's own mocked folderGateway hides
   // the real AdminScheduleFolderGateway#renameFolder boundary entirely, so it cannot catch a
   // future "simplification" back to passing a null owner directly -- see that method's own
   // javadoc for why null WIPES the owner rather than preserving it. This test wires a REAL
   // AdminScheduleFolderGateway (backed by mocked ScheduleTaskFolderService/ScheduleService)
   // through the full preview/apply pipeline, so it fails if that boundary regresses.
   // -------------------------------------------------------------------------

   @Test void appliesARenameAndSendsAnOwnerPreservingModelToTheRealService() throws Exception {
      ScheduleTaskFolderService realTaskFolderService = mock(ScheduleTaskFolderService.class);
      ScheduleService realScheduleService = mock(ScheduleService.class);
      AdminScheduleFolderGateway realGateway =
         new AdminScheduleFolderGateway(realTaskFolderService, realScheduleService);
      ScheduleFolderChangePlanService realPlanService = new ScheduleFolderChangePlanService(realGateway);
      ScheduleFolderChangesetApplyService realApplyService =
         new ScheduleFolderChangesetApplyService(realPlanService, realGateway, backupService);

      lenient().when(realTaskFolderService.getFolderEntry(anyString())).thenAnswer(inv ->
         new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK_FOLDER,
                        (String) inv.getArgument(0), null));
      // Every lookup sees a folder present -- this test only cares about the argument shape
      // renameFolder is called with, not about this apply's own verified/rolled-back outcome.
      lenient().when(realTaskFolderService.getTaskFolder(anyString())).thenReturn(new AssetFolder());
      ArgumentCaptor<EditTaskFolderDialogModel> captor = ArgumentCaptor.forClass(EditTaskFolderDialogModel.class);
      when(realTaskFolderService.renameFolder(captor.capture(), eq(user))).thenReturn(null);
      when(backupService.backup(anyString())).thenReturn("snap-ref");

      ScheduleFolderChangeRequest rename = renameChange("A", "B");
      ResolvedPlan preview = realPlanService.resolve(planRequest(rename), user);
      ScheduleFolderApplyRequest req = applyRequest(preview.planHash(), preview.taskToken(), rename);
      req.setReviewOutcome("approved");

      realApplyService.apply(req, user);

      EditTaskFolderDialogModel model = captor.getValue();
      assertNotNull(model.owner(), "owner must not be null -- a null owner WIPES the folder's " +
                     "existing owner in ScheduleTaskFolderService#changeFolder, it does not preserve it");
      assertEquals("", model.owner().name);
      assertNull(model.owner().orgID);
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static ScheduleFolderChangeRequest renameChange(String path, String newPath) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_RENAME);
      change.setPath(path);
      change.setNewPath(newPath);
      return change;
   }

   private static ScheduleFolderChangePlanRequest planRequest(ScheduleFolderChangeRequest... changes) {
      ScheduleFolderChangePlanRequest req = new ScheduleFolderChangePlanRequest();
      req.setTask("a task");
      req.setChanges(List.of(changes));
      return req;
   }

   private static ScheduleFolderApplyRequest applyRequest(
      String planHash, String taskToken, ScheduleFolderChangeRequest... changes)
   {
      ScheduleFolderApplyRequest req = new ScheduleFolderApplyRequest();
      req.setTask("a task");
      req.setChanges(List.of(changes));
      req.setPlanHash(planHash);
      req.setTaskToken(taskToken);
      return req;
   }

   private static ScheduleFolderChangeRequest createChange(String parentPath, String folderName) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_CREATE);
      change.setParentPath(parentPath);
      change.setFolderName(folderName);
      return change;
   }

   private static ScheduleFolderChangeRequest deleteChangeForce(String path) {
      ScheduleFolderChangeRequest change = new ScheduleFolderChangeRequest();
      change.setVerb(ScheduleFolderChangeRequest.VERB_DELETE);
      change.setPath(path);
      change.setForce(true);
      return change;
   }
}
