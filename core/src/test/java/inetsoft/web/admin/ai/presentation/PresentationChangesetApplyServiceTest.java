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
import inetsoft.web.admin.model.FileData;
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

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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
         .defaultLogo(true).logoName("")
         .defaultFavicon(true).faviconName("")
         .defaultViewsheet(true).viewsheetName("")
         .defaultFont(true)
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

   // ---------------------------------------------------------------- lookAndFeel FileData read-back (bug #76729)

   private static LookAndFeelSettingsModel.Builder cleanLookAndFeel() {
      return LookAndFeelSettingsModel.builder()
         .ascending(true).repositoryTree(true).expand(false)
         .defaultLogo(true).logoName("")
         .defaultFavicon(true).faviconName("")
         .defaultViewsheet(true).viewsheetName("")
         .defaultFont(true).viewsheetCSSEntries(List.of()).vsEnabled(true);
   }

   /** Overrides the generic identity write/read fake (set up in {@link #setUp}) for
    * {@code LOOK_AND_FEEL} only, to actually simulate what {@code LookAndFeelService.setModel}/
    * {@code getModel} really do to {@code logoFile}/{@code faviconFile}: derive a server-side
    * persisted name and never round-trip the FileData field itself. Without this, the generic
    * fake's write-then-read-is-identity assumption is exactly what bug #76729 is about, so it
    * cannot express the bug at all (see 02-refute.md's own probe, which this mirrors). */
   private void mockLookAndFeelRealWrite() throws Exception {
      doAnswer(inv -> {
         LookAndFeelSettingsModel proposed = inv.getArgument(1);
         boolean global = inv.getArgument(3);
         state.put(stateKey(PresentationSubModel.LOOK_AND_FEEL, global),
                   simulateLookAndFeelWrite(proposed));
         return null;
      }).when(access).write(eq(PresentationSubModel.LOOK_AND_FEEL), any(), any(), anyBoolean());
   }

   private static LookAndFeelSettingsModel simulateLookAndFeelWrite(LookAndFeelSettingsModel proposed) {
      String logoName = proposed.defaultLogo() ? "" :
         proposed.logoFile() != null
            ? "portal/logo" + extensionOf(proposed.logoFile().name()) : proposed.logoName();
      String faviconName = proposed.defaultFavicon() ? "" :
         proposed.faviconFile() != null
            ? "portal/favicon" + extensionOf(proposed.faviconFile().name()) : proposed.faviconName();

      return LookAndFeelSettingsModel.builder().from(proposed)
         .logoName(logoName).logoFile(null)
         .faviconName(faviconName).faviconFile(null)
         .viewsheetFile(null)
         .userformatFile(null)
         .build();
   }

   private static String extensionOf(String name) {
      int dot = name.lastIndexOf('.');
      return dot >= 0 ? name.substring(dot) : ".gif";
   }

   @Test
   void applyVerifiesALogoFileUpdate() throws Exception {
      seed(PresentationSubModel.LOOK_AND_FEEL, true, cleanLookAndFeel().build());
      mockLookAndFeelRealWrite();

      ObjectNode logoFile = obj()
         .put("name", "qa-test-logo.png")
         .put("content", Base64.getEncoder().encodeToString("fake-png-bytes".getBytes()));
      ObjectNode spec = obj().put("defaultLogo", false);
      spec.set("logoFile", logoFile);

      PresentationApplyRequest req = applyRequest("t", "ok", true,
         change("lookAndFeel", "global", spec));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.rollbackFailures());
      PresentationApplyOutcome outcome = result.results().get(0);
      assertEquals("verified", outcome.status());
      assertNull(outcome.error());

      var after = MAPPER.readTree(outcome.after());
      assertEquals("portal/logo.png", after.get("logoName").asText());
      assertTrue(after.get("logoFile").isNull());
   }

   @Test
   void applyVerifiesALogoRevertToDefault() throws Exception {
      seed(PresentationSubModel.LOOK_AND_FEEL, true, cleanLookAndFeel()
         .defaultLogo(false).logoName("portal/logo.png").build());
      mockLookAndFeelRealWrite();

      PresentationApplyRequest req = applyRequest("t", "ok", true,
         change("lookAndFeel", "global", obj().put("defaultLogo", true)));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.rollbackFailures());
      PresentationApplyOutcome outcome = result.results().get(0);
      assertEquals("verified", outcome.status());

      var after = MAPPER.readTree(outcome.after());
      assertEquals("", after.get("logoName").asText());
   }

   @Test
   void applyVerifiesAFaviconFileUpdate() throws Exception {
      seed(PresentationSubModel.LOOK_AND_FEEL, true, cleanLookAndFeel().build());
      mockLookAndFeelRealWrite();

      ObjectNode faviconFile = obj()
         .put("name", "qa-test-favicon.ico")
         .put("content", Base64.getEncoder().encodeToString("fake-ico-bytes".getBytes()));
      ObjectNode spec = obj().put("defaultFavicon", false);
      spec.set("faviconFile", faviconFile);

      PresentationApplyRequest req = applyRequest("t", "ok", true,
         change("lookAndFeel", "global", spec));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(result.rollbackFailures());
      PresentationApplyOutcome outcome = result.results().get(0);
      assertEquals("verified", outcome.status());

      var after = MAPPER.readTree(outcome.after());
      assertEquals("portal/favicon.ico", after.get("faviconName").asText());
      assertTrue(after.get("faviconFile").isNull());
   }

   @Test
   void applyReportsRollbackFailedWhenALookAndFeelEntrysOwnVerificationFails() throws Exception {
      // Defect 2, independent of Defect 1: access.write "succeeds" (no exception) but the storage
      // layer silently keeps the old value, as a genuine persistence failure would -- this must
      // surface as an unconditional RollbackFailure (rollback-failed), never rolled-back, since the
      // write attempt happened and lookAndFeel has no live rollback (01-spec.md section 4/6).
      seed(PresentationSubModel.LOOK_AND_FEEL, true, cleanLookAndFeel().build());

      doAnswer(inv -> null)
         .when(access).write(eq(PresentationSubModel.LOOK_AND_FEEL), any(), any(), anyBoolean());

      PresentationApplyRequest req = applyRequest("t", "ok", true,
         change("lookAndFeel", "global", obj().put("expand", true)));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals("failed", result.results().get(0).status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
                    .anyMatch(f -> f.property().startsWith("lookAndFeel:")));
      assertFalse(((LookAndFeelSettingsModel)
         state.get(stateKey(PresentationSubModel.LOOK_AND_FEEL, true))).expand());
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

   // ---------------------------------------------------------------- rollback: throw-before-mutation (bug 76634)

   @Test
   void applyRollsBackCleanlyWhenAValueScopeChangeThrowsBeforeAnyMutation() throws Exception {
      // The core regression proof for bug 76634: a value-scope change whose access.write throws
      // strictly before mutating anything (the real PresentationFormatsSettingsService shape for a
      // malformed dateFormat like "BAD"/"qqqq" -- checkDateFormatPattern throws before any SreeEnv
      // write) must not force rollback-failed just because the loop can't tell "threw before
      // touching state" from "threw mid-write". The rest of the batch rolls back cleanly, so the
      // correct overall status is rolled-back.
      seed(PresentationSubModel.DASHBOARD, false, dashboard(true));
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));

      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("dashboard", "organization", obj().put("enabled", false)),
         change("formats", "organization", obj().put("dateFormat", "BAD")));

      // access.write never touches `state` for this call -- mirrors checkDateFormatPattern throwing
      // before the first SreeEnv mutation in the real PresentationFormatsSettingsService#setModel.
      doThrow(new IllegalArgumentException("Illegal pattern character 'B'"))
         .when(access).write(eq(PresentationSubModel.FORMATS), any(), any(), anyBoolean());

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // dashboard was applied, then rolled back to its original value.
      Object restoredDashboard = state.get(stateKey(PresentationSubModel.DASHBOARD, false));
      assertTrue(((PresentationDashboardSettingsModel) restoredDashboard).enabled());
   }

   @Test
   void applyStillReportsRollbackFailedWhenAValueScopeChangeGenuinelyFailsMidWrite() throws Exception {
      // Regression guard against over-correcting bug 76634's fix into blindly downgrading every
      // unknown-state failure: when the re-read after a throw shows the sub-model's value is STILL
      // the proposed one (i.e. the write actually landed before throwing, e.g. a post-write
      // audit/side-effect failure), that is a genuine unknown/undo-needed state and must still be
      // able to end in rollback-failed once the rollback attempt for it also fails.
      seed(PresentationSubModel.DASHBOARD, false, dashboard(true));
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));

      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("dashboard", "organization", obj().put("enabled", false)),
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));

      // Every write to FORMATS (both the initial apply and any later rollback attempt) mutates
      // `state` and then throws -- simulating a sub-service that genuinely can't be trusted to
      // leave a clean, restorable state even on rollback.
      doAnswer(inv -> {
         PresentationSubModel subModel = inv.getArgument(0);
         Object model = inv.getArgument(1);
         boolean global = inv.getArgument(3);
         state.put(stateKey(subModel, global), model);
         throw new RuntimeException("boom");
      }).when(access).write(eq(PresentationSubModel.FORMATS), any(), any(), anyBoolean());

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertNotNull(result.rollbackFailures());
      assertTrue(result.rollbackFailures().stream()
                    .anyMatch(f -> f.property().startsWith("formats:")));
   }

   @Test
   void applyAttemptsAndCompletesRollbackForAValueScopeChangeThatMutatedBeforeThrowing()
      throws Exception
   {
      // Covers the "moved but threw" branch the refuter flagged as a design-completeness
      // requirement: no real value-scope sub-service exhibits a partial-mutation-before-throw
      // shape today (only the storage-scope PortalIntegrationViewSettingsService does), so this
      // mocks access.write directly to produce that shape rather than skipping coverage. The first
      // write (the apply attempt) mutates `state` and then throws; the item must still be added to
      // the undoable set and get a genuine rollback attempt (the second write, on rollback, which
      // this mock lets succeed) -- not be silently reported as clean without ever being restored.
      seed(PresentationSubModel.DASHBOARD, false, dashboard(true));
      seed(PresentationSubModel.FORMATS, false, formats("MM/dd/yyyy"));

      PresentationApplyRequest req = applyRequest("t", "ok", null,
         change("dashboard", "organization", obj().put("enabled", false)),
         change("formats", "organization", obj().put("dateFormat", "yyyy-MM-dd")));

      AtomicInteger formatsWrites = new AtomicInteger();
      doAnswer(inv -> {
         PresentationSubModel subModel = inv.getArgument(0);
         Object model = inv.getArgument(1);
         boolean global = inv.getArgument(3);
         state.put(stateKey(subModel, global), model);

         if(formatsWrites.getAndIncrement() == 0) {
            throw new RuntimeException("partial mutation before throw");
         }

         return null;
      }).when(access).write(eq(PresentationSubModel.FORMATS), any(), any(), anyBoolean());

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      // Proves an actual rollback attempt happened (not just a report that skipped it): one write
      // for the apply attempt, one more for the rollback attempt that restored the original value.
      verify(access, times(2))
         .write(eq(PresentationSubModel.FORMATS), any(), any(), anyBoolean());
      Object restoredFormats = state.get(stateKey(PresentationSubModel.FORMATS, false));
      assertEquals("MM/dd/yyyy", ((PresentationFormatsSettingsModel) restoredFormats).dateFormat());
   }
}
