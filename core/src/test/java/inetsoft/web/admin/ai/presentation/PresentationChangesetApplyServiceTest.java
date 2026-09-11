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
package inetsoft.web.admin.ai.presentation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.presentation.model.LookAndFeelSettingsModel;
import inetsoft.web.admin.presentation.model.PresentationDashboardSettingsModel;
import inetsoft.web.admin.presentation.model.PresentationFormatsSettingsModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 6 (apply/rollback per verb, echoed-hash gate), section 7 (unconditional Tier-2
 * backup), section 11 ({@code reviewOutcome} always required, {@code acknowledgeIrreversibleUpdate}
 * required exactly when a storage-scope sub-model is touched). {@code PresentationSettingsAccess} is
 * mocked with an in-memory map so write-then-read-back verification and rollback assertions reflect
 * real state transitions, matching {@code LicenseChangesetApplyServiceTest}'s own in-memory-fake
 * pattern.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PresentationChangesetApplyServiceTest {
   @Mock
   private PresentationSettingsAccess access;
   @Mock
   private AdminBackupService backupService;
   @Mock
   private XPrincipal user;

   private final Map<String, Object> state = new HashMap<>();
   private PresentationChangePlanService planService;
   private PresentationChangesetApplyService service;
   private MockedStatic<Tool> tool;

   private static final ObjectMapper MAPPER = new ObjectMapper();

   @BeforeEach
   void setUp() throws Exception {
      planService = new PresentationChangePlanService(access);
      service = new PresentationChangesetApplyService(planService, access, backupService);

      lenient().when(backupService.backup(anyString())).thenReturn("admin-snapshot/ref");
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

      lenient().when(access.read(any(), any(), anyBoolean())).thenAnswer(inv -> {
         PresentationSubModel subModel = inv.getArgument(0);
         boolean global = inv.getArgument(2);
         return state.get(stateKey(subModel, global));
      });

      lenient().doAnswer(inv -> {
         PresentationSubModel subModel = inv.getArgument(0);
         Object model = inv.getArgument(1);
         boolean global = inv.getArgument(3);
         state.put(stateKey(subModel, global), model);
         return null;
      }).when(access).write(any(), any(), any(), anyBoolean());
   }

   @AfterEach
   void tearDown() {
      tool.close();
   }

   private static String stateKey(PresentationSubModel subModel, boolean global) {
      return subModel.key() + ":" + global;
   }

   private void seed(PresentationSubModel subModel, boolean global, Object current) {
      state.put(stateKey(subModel, global), current);
   }

   private static ObjectNode obj() {
      return MAPPER.createObjectNode();
   }

   private static PresentationChangeRequest change(String subModel, String scope, ObjectNode spec) {
      PresentationChangeRequest r = new PresentationChangeRequest();
      r.setVerb("update");
      r.setSubModel(subModel);
      r.setScope(scope);
      r.setSpec(spec);
      return r;
   }

   private PresentationApplyRequest applyRequest(String task, String reviewOutcome,
                                                 Boolean acknowledgeIrreversibleUpdate,
                                                 PresentationChangeRequest... changes)
      throws Exception
   {
      PresentationChangePlanRequest preview = new PresentationChangePlanRequest();
      preview.setTask(task);
      preview.setChanges(List.of(changes));
      var plan = planService.resolve(preview, user);

      PresentationApplyRequest apply = new PresentationApplyRequest();
      apply.setTask(task);
      apply.setChanges(List.of(changes));
      apply.setPlanHash(plan.planHash());
      apply.setTaskToken(plan.taskToken());
      apply.setReviewOutcome(reviewOutcome);
      apply.setAcknowledgeIrreversibleUpdate(acknowledgeIrreversibleUpdate);
      return apply;
   }

   private static MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      Audit instance = mock(Audit.class);
      audit.when(Audit::getInstance).thenReturn(instance);
      return audit;
   }

   private static PresentationFormatsSettingsModel formats(String dateFormat) {
      return PresentationFormatsSettingsModel.builder()
         .dateFormat(dateFormat).timeFormat("HH:mm").dateTimeFormat("MM/dd/yyyy HH:mm").build();
   }

   private static PresentationDashboardSettingsModel dashboard(boolean enabled) {
      return PresentationDashboardSettingsModel.builder()
         .enabled(enabled).tabsTop(false).drillTabsTop(false).build();
   }

   private static LookAndFeelSettingsModel lookAndFeel(boolean expand) {
      return LookAndFeelSettingsModel.builder()
         .ascending(true).repositoryTree(true).expand(expand)
         .defaultLogo(true).defaultFavicon(true).defaultViewsheet(true).defaultFont(true)
         .viewsheetCSSEntries(List.of()).vsEnabled(true).build();
   }

   // ---------------------------------------------------------------- applied

   @Test
   void applySucceedsForAValueScopeChange() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationApplyRequest req = applyRequest("t", "looks good", null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(1, result.results().size());
      assertEquals("verified", result.results().get(0).status());
      assertNull(result.rollbackFailures());
      verify(backupService).backup(anyString());
   }

   @Test
   void applyRefusesWithoutReviewOutcome() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationApplyRequest req = applyRequest("t", null, null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));

      assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
   }

   @Test
   void applySucceedsWhenTaskIsParaphrasedBetweenPreviewAndApply() throws Exception {
      // hash() recomputes locally at this call site (PresentationChangesetApplyService.apply), a
      // second place from PresentationChangePlanService.resolve() where the same "task must not
      // affect the hash" mistake could be reintroduced independently -- this test guards that
      // second call site specifically, not just the plan-service-level hash contract.
      //
      // Post-fix behavior: the apply request's own (paraphrased) task field is purely
      // informational and must not block apply -- see applyAuditsThePreviewedTaskEvenWhenApply-
      // TaskDiffers below for the companion assertion that the AUDIT RECORD still carries the
      // originally-previewed narrative, not this paraphrase.
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationChangeRequest changeReq =
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd"));

      PresentationChangePlanRequest preview = new PresentationChangePlanRequest();
      preview.setTask("Update date format to ISO");
      preview.setChanges(List.of(changeReq));
      var plan = planService.resolve(preview, user);

      PresentationApplyRequest apply = new PresentationApplyRequest();
      apply.setTask("Change the date format to ISO 8601");
      apply.setChanges(List.of(changeReq));
      apply.setPlanHash(plan.planHash());
      apply.setTaskToken(plan.taskToken());
      apply.setReviewOutcome("looks good");

      var result = service.apply(apply, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
   }

   @Test
   void applyAuditsThePreviewedTaskEvenWhenApplyTaskDiffers() throws Exception {
      // scope=global (not organization), so writeAudit's setOrganizationId branch never calls the
      // real, unmocked OrganizationManager.getInstance() -- the point of this test is the
      // taskToken/audit-narrative wiring, not the org-scope branch.
      seed(PresentationSubModel.FORMATS, true, formats("MM/dd/yyyy"));
      PresentationChangeRequest changeReq =
         change("formats", "global", obj().put("dateFormat", "yyyy-MM-dd"));

      PresentationChangePlanRequest preview = new PresentationChangePlanRequest();
      preview.setTask("Update date format to ISO");
      preview.setChanges(List.of(changeReq));
      var plan = planService.resolve(preview, user);

      PresentationApplyRequest apply = new PresentationApplyRequest();
      apply.setTask("Change the date format to ISO 8601");
      apply.setChanges(List.of(changeReq));
      apply.setPlanHash(plan.planHash());
      apply.setTaskToken(plan.taskToken());
      apply.setReviewOutcome("looks good");

      Audit auditInstance = mock(Audit.class);
      tool.when(Tool::getHost).thenReturn("test-host");

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         service.apply(apply, user);
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      // The core regression proof: the audit record carries the PREVIEWED narrative embedded in
      // the taskToken, not the apply request's own (paraphrased) task field.
      assertEquals("Update date format to ISO", captor.getValue().getTaskDescription());
   }

   @Test
   void applyRefusesAStaleHash() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));
      req.setPlanHash("not-the-real-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
   }

   @Test
   void conflictPathNeverHandsBackAUsableTaskToken() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));
      req.setPlanHash("not-the-real-hash");

      AdminChangesetApplyService.PlanHashMismatchException ex = assertThrows(
         AdminChangesetApplyService.PlanHashMismatchException.class,
         () -> service.apply(req, user));

      assertNull(ex.current().taskToken(),
         "a 409 conflict must never hand back a usable taskToken");
   }

   @Test
   void applyRefusesWithoutATaskToken() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));
      req.setTaskToken(null);

      AdminChangesetApplyService.TaskTokenMismatchException ex = assertThrows(
         AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
   }

   @Test
   void applyRefusesATaskTokenIssuedForADifferentPlan() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));
      PresentationApplyRequest otherPlanReq = applyRequest("t", "ok", null,
         change("formats", "organization", obj().put("dateFormat", "MM-dd-yyyy")));
      req.setTaskToken(otherPlanReq.getTaskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
   }

   // ---------------------------------------------------------------- storage-scope acknowledgement gate

   @Test
   void applyRefusesStorageScopeChangeWithoutAcknowledgement() throws Exception {
      seed(PresentationSubModel.LOOK_AND_FEEL, true, lookAndFeel(false));
      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("lookAndFeel", "global", obj().put("expand", true)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("acknowledgeIrreversibleUpdate"));
   }

   @Test
   void applyAllowsStorageScopeChangeWithAcknowledgement() throws Exception {
      seed(PresentationSubModel.LOOK_AND_FEEL, true, lookAndFeel(false));
      PresentationApplyRequest req = applyRequest("t", "ok", true,
         change("lookAndFeel", "global", obj().put("expand", true)));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
   }

   // ---------------------------------------------------------------- rollback: value-scope

   @Test
   void applyRollsBackAnEarlierValueScopeChangeWhenALaterChangeFailsVerification() throws Exception {
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));
      seed(PresentationSubModel.DASHBOARD, false, dashboard(true));

      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")),
         change("dashboard", "organization", obj().put("enabled", false)));

      // Simulate a mid-apply failure on the SECOND change only, via a clean (non-throwing)
      // read-back mismatch rather than a thrown exception: a throw leaves that property's own
      // state genuinely unknown and must always force rollback-failed (see the dedicated
      // storage-scope/non-compensable test below for that case) -- a verified-false outcome is
      // the only failure shape that can still legitimately end in rolled-back.
      doAnswer(inv -> null)
         .when(access).write(eq(PresentationSubModel.DASHBOARD), any(), any(), anyBoolean());

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // formats was written, then rolled back to its original value.
      Object restored = state.get(stateKey(PresentationSubModel.FORMATS, false));
      assertEquals("MM/dd/yyyy", ((PresentationFormatsSettingsModel) restored).dateFormat());
   }

   // ---------------------------------------------------------------- rollback: storage-scope non-compensable

   @Test
   void applyReportsRollbackFailedWhenAStorageScopeChangeAlreadySucceededBeforeALaterFailure()
      throws Exception
   {
      seed(PresentationSubModel.LOOK_AND_FEEL, true, lookAndFeel(false));
      seed(PresentationSubModel.FORMATS, true, formats("MM/dd/yyyy"));

      PresentationApplyRequest req = applyRequest("t", "ok", true,
         change("lookAndFeel", "global", obj().put("expand", true)),
         change("formats", "global", obj().put("dateFormat", "yyyy-MM-dd")));

      doThrow(new RuntimeException("boom"))
         .when(access).write(eq(PresentationSubModel.FORMATS), any(), any(), anyBoolean());

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
                    .anyMatch(f -> f.property().startsWith("lookAndFeel:")));
      // The storage-scope write itself is never undone -- still the new value.
      Object stillApplied = state.get(stateKey(PresentationSubModel.LOOK_AND_FEEL, true));
      assertTrue(((LookAndFeelSettingsModel) stillApplied).expand());
   }
}
