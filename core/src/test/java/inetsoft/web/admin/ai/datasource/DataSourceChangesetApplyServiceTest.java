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
package inetsoft.web.admin.ai.datasource;

import inetsoft.web.admin.datasource.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.security.auth.MissingResourceException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 13. Uses a tiny in-memory fake ("the store", keyed by current name) behind
 * the mocked {@link DataSourceService} rather than positional sequential stubbing, since this
 * area's apply path re-reads/re-resolves far more times per change (id-by-name re-resolution, the
 * password merge's own extra read, the post-update re-read) than any prior area's -- a fake that
 * actually mutates gives each test's intent a direct assertion instead of a brittle call-count
 * derivation.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class DataSourceChangesetApplyServiceTest {
   private static final String REAL_PASSWORD = "super-secret-live-password";

   @Mock private DataSourceService dataSourceService;
   @Mock private DatabaseSettingsService databaseSettingsService;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   @Mock private Audit audit;
   private DataSourceChangePlanService planService;
   private DataSourceChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<DependencyTool> dependencyToolStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Audit> auditStatic;
   /** The fake "live" data sources, keyed by current name -- a delete/rename removes/renames the
    * key. */
   private final Map<String, JdbcDataSourceProperties> store = new LinkedHashMap<>();
   /** The fake "live" data source folders (bug 76599, Gap 2a). */
   private final Set<String> existingFolders = new HashSet<>();

   @BeforeEach void setUp() throws Exception {
      planService = new DataSourceChangePlanService(dataSourceService, databaseSettingsService);
      service = new DataSourceChangesetApplyService(planService, dataSourceService, backupService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      dependencyToolStatic = mockStatic(DependencyTool.class, withSettings().lenient());
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of());

      // Unstubbed and LENIENT: Tool.getHost() (called by writeAudit) falls through to
      // SreeEnv.getProperty("local.host.name"), which without this mock throws ShutdownException
      // (no Spring context in this plain Mockito unit test) -- writeAudit's own catch-and-log-only
      // behavior would then silently swallow that, and every audit-content assertion below would
      // see zero interactions. Mirrors AdminChangesetApplyServiceTest's own setUp() for this.
      sreeEnv = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      // Real shape, copied verbatim from AdminChangesetApplyServiceTest -- throws on a non-"TKN:"
      // string rather than silently passing it through, since TaskAuditToken.verify() now calls
      // this for real once a taskToken is actually supplied.
      tool.when(() -> Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });

      auditStatic = mockStatic(Audit.class, withSettings().lenient());
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      lenient().when(backupService.backup(anyString())).thenReturn("backup-ref-1");

      store.clear();
      store.put("Orders", jdbc("id-orders", "Orders", false, null));

      lenient().when(dataSourceService.getDataSources(anyString(), eq(user)))
         .thenAnswer(inv -> {
            String name = inv.getArgument(0);
            DataSourceList list = new DataSourceList();
            JdbcDataSourceProperties current = store.get(name);

            if(current != null) {
               DataSourceDescription d = new DataSourceDescription();
               d.setId(current.getId());
               d.setName(name);
               d.setType("jdbc");
               list.setDataSources(List.of(d));
            }
            else {
               list.setDataSources(List.of());
            }

            return list;
         });

      lenient().when(dataSourceService.getDataSource(anyString(), eq(user)))
         .thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return store.values().stream().filter(d -> d.getId().equals(id)).findFirst()
               .map(DataSourceChangesetApplyServiceTest::copyMasked)
               .orElseThrow(() -> new MissingResourceException(id));
         });

      lenient().when(dataSourceService.getCurrentPassword(anyString(), eq(user)))
         .thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return store.values().stream().filter(d -> d.getId().equals(id)).findFirst()
               .filter(JdbcDataSourceProperties::isRequireLogin)
               .map(d -> REAL_PASSWORD).orElse(null);
         });

      lenient().doAnswer(inv -> {
         String id = inv.getArgument(0);
         JdbcDataSourceProperties proposed = copy((JdbcDataSourceProperties) inv.getArgument(1));
         store.entrySet().removeIf(e -> e.getValue().getId().equals(id));
         store.put(proposed.getName(), proposed);
         return null;
      }).when(dataSourceService).updateDataSource(anyString(), any(), eq(user));

      lenient().doAnswer(inv -> {
         String id = inv.getArgument(0);
         store.entrySet().removeIf(e -> e.getValue().getId().equals(id));
         return null;
      }).when(dataSourceService).deleteDataSource(anyString(), anyBoolean(), eq(user));

      existingFolders.clear();
      lenient().when(dataSourceService.dataSourceFolderExists(anyString()))
         .thenAnswer(inv -> existingFolders.contains((String) inv.getArgument(0)));
      lenient().doAnswer(inv -> {
         existingFolders.add(inv.getArgument(0));
         return null;
      }).when(dataSourceService).createDataSourceFolder(anyString(), eq(user));
      lenient().doAnswer(inv -> {
         existingFolders.remove(inv.getArgument(0));
         return null;
      }).when(dataSourceService).removeDataSourceFolder(anyString());
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      dependencyToolStatic.close();
      tool.close();
      sreeEnv.close();
      auditStatic.close();
   }

   // -------------------------------------------------------------------------
   // success
   // -------------------------------------------------------------------------

   @Test void appliesAnUpdateAndReportsApplied() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("backup-ref-1", result.backupRef());
      assertNull(result.rollbackFailures());
      assertEquals("jdbc:h2:mem:test2", store.get("Orders").getUrl());
      verify(dataSourceService).updateDataSource(eq("id-orders"), any(), eq(user));
   }

   // Bug 76457: an update with no spec.name must never silently change the name on the DTO it
   // sends to updateDataSource, even though this fixture (keyed purely by bare getName(), no
   // folder concept) cannot represent the folder-qualified/bare split that actually triggers the
   // bug in production -- see DataSourceChangePlanServiceTest for that half of the coverage.
   @Test void appliesAnUpdateWithNoSpecNameWithoutChangingTheProposedName() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));

      service.apply(req, user);

      ArgumentCaptor<DataSourceProperties> captor = ArgumentCaptor.forClass(DataSourceProperties.class);
      verify(dataSourceService).updateDataSource(eq("id-orders"), captor.capture(), eq(user));
      assertEquals("Orders", captor.getValue().getName());
   }

   @Test void appliesAnUpdateThatOmitsPasswordAndPreservesTheRealValue() throws Exception {
      store.put("Orders", jdbc("id-orders", "Orders", true, "******"));

      DataSourceApplyRequest req = applyRequest("rotate url only",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test3")));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(REAL_PASSWORD, store.get("Orders").getPassword());
      // Neither the before nor after projection in the outcome ever carries the real password.
      assertFalse(result.results().get(0).before().contains(REAL_PASSWORD));
      assertFalse(result.results().get(0).after().contains(REAL_PASSWORD));
   }

   @Test void appliesARenameUpdateWithConfirmRenameAndReResolvesTheNewName() throws Exception {
      DataSourceChangeRequest change = updateChange("Orders", Map.of("name", "Orders2"));
      change.setConfirmRename(true);
      DataSourceApplyRequest req = applyRequest("rename", false, change);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(store.get("Orders"));
      assertNotNull(store.get("Orders2"));
   }

   @Test void appliesADeleteAndReportsApplied() throws Exception {
      DataSourceApplyRequest req = applyRequest("delete it", true, deleteChange("Orders", null));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNull(store.get("Orders"));
      verify(dataSourceService).deleteDataSource(eq("id-orders"), eq(false), eq(user));
   }

   // -------------------------------------------------------------------------
   // bug 76599, Gap 2a: bare folder create
   // -------------------------------------------------------------------------

   @Test void appliesAFolderCreateAndReportsApplied() throws Exception {
      DataSourceApplyRequest req = applyRequest("create folder",
         false, folderCreateChange("Examples/NewFolder"));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(existingFolders.contains("Examples/NewFolder"));
      verify(dataSourceService).createDataSourceFolder("Examples/NewFolder", user);
   }

   /** Two-entry plan: the folder create succeeds; the update fails verification, forcing the
    * whole apply into rollback -- which must remove the folder it just created, not merely
    * report success for the create. */
   @Test void folderCreateRollsBackWhenALaterChangeFails() throws Exception {
      store.put("Reports", jdbc("id-reports", "Reports", false, null));
      doAnswer(inv -> null).when(dataSourceService)
         .updateDataSource(eq("id-reports"), any(), eq(user));

      DataSourceChangeRequest folderCreate = folderCreateChange("Examples/NewFolder");
      DataSourceChangeRequest reportsUpdate =
         updateChange("Reports", Map.of("url", "jdbc:h2:mem:new2"));
      ResolvedPlan resolved = planService.resolve(
         planRequest("folder create then a failing update",
                     List.of(folderCreate, reportsUpdate)), user);

      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask("folder create then a failing update");
      req.setChanges(List.of(folderCreate, reportsUpdate));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertFalse(existingFolders.contains("Examples/NewFolder"));
      verify(dataSourceService).removeDataSourceFolder("Examples/NewFolder");
   }

   @Test void deleteAdvisoryNamesTheDependentsWhenForced() throws Exception {
      var dep = dependency("Examples/Orders Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      DataSourceApplyRequest req = applyRequest("force delete", true, deleteChange("Orders", true));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNotNull(result.results().get(0).advisory());
      assertTrue(result.results().get(0).advisory().contains("Orders Query"));
   }

   // The core regression proof for this bug: the audit record's task narrative comes from the
   // taskToken issued at preview time, not from this apply call's own (possibly diverged) task
   // field.
   @Test void auditsThePreviewedTaskForAnUpdateEvenWhenApplyTaskDiffers() throws Exception {
      DataSourceApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: widen the url", "totally different apply-time text",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(captor.capture(), eq(user));
      assertEquals("reviewed: widen the url", captor.getValue().getTaskDescription());
   }

   @Test void auditsThePreviewedTaskForADeleteEvenWhenApplyTaskDiffers() throws Exception {
      DataSourceApplyRequest req = requestWithDivergentApplyTask(
         "reviewed: delete Orders", "totally different apply-time text",
         true, deleteChange("Orders", null));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(audit).auditAdminChange(captor.capture(), eq(user));
      assertEquals("reviewed: delete Orders", captor.getValue().getTaskDescription());
   }

   // -------------------------------------------------------------------------
   // gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsTaskTokenMismatchOnMissingToken() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));
      req.setTaskToken(null);

      AdminChangesetApplyService.TaskTokenMismatchException ex = assertThrows(
         AdminChangesetApplyService.TaskTokenMismatchException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
      verify(dataSourceService, never()).updateDataSource(any(), any(), any());
   }

   @Test void applyThrowsTaskTokenMismatchOnBlankToken() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));
      req.setTaskToken("   ");

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
                  () -> service.apply(req, user));
      verify(dataSourceService, never()).updateDataSource(any(), any(), any());
   }

   @Test void applyThrowsTaskTokenMismatchOnTokenIssuedForDifferentPlanHash() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));
      DataSourceChangePlanRequest otherProbe =
         planRequest("a different task entirely", List.of(deleteChange("Orders", null)));
      req.setTaskToken(planService.resolve(otherProbe, user).taskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
                  () -> service.apply(req, user));
      verify(dataSourceService, never()).updateDataSource(any(), any(), any());
   }

   @Test void applyThrowsOnStalePlanHash() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));
      req.setPlanHash("stale-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
      verify(dataSourceService, never()).updateDataSource(any(), any(), any());
   }

   @Test void applyThrowsWhenReviewOutcomeMissing() throws Exception {
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));
      req.setReviewOutcome(null);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   @Test void applyThrowsWhenDeleteWithoutAcknowledgeIrreversibleDelete() throws Exception {
      DataSourceApplyRequest req = applyRequest("delete it", false, deleteChange("Orders", null));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("acknowledgeIrreversibleDelete"));
      assertNotNull(store.get("Orders"), "delete must not have been attempted before the gate check");
   }

   // -------------------------------------------------------------------------
   // failure / rollback
   // -------------------------------------------------------------------------

   @Test void throwMidApplyIsReportedAsUnknownStateAndRollbackFailed() throws Exception {
      doThrow(new IllegalStateException("boom")).when(dataSourceService)
         .updateDataSource(eq("id-orders"), any(), eq(user));

      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals("Orders", result.rollbackFailures().get(0).property());
   }

   // Bug 76808: a throw strictly BEFORE the mutating call must not by itself force
   // ROLLBACK_FAILED -- the item was never touched, so a fully-verified rollback (of everything
   // that WAS touched, i.e. nothing here) must still report ROLLED_BACK. Mirrors bug 76567's fix
   // for LicenseChangesetApplyService.
   @Test void throwBeforeMutatingCallMustNotForceRollbackFailed() throws Exception {
      // Build the request FIRST (this itself calls getDataSource once, during preview/plan
      // resolution) -- only THEN wire the fault so it fires solely inside apply()'s own re-resolve,
      // strictly before the mutating call.
      DataSourceApplyRequest req = applyRequest("update url",
         false, updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2")));

      // apply() itself re-resolves the plan (1st getDataSource call, before the per-item loop even
      // starts) before applyUpdate makes its OWN re-resolve (2nd call) immediately before the
      // mutating updateDataSource call -- let the 1st succeed normally and only fault the 2nd, so
      // the throw lands strictly inside the per-item loop's try/catch, before any mutation.
      when(dataSourceService.getDataSource(eq("id-orders"), eq(user)))
         .thenAnswer(inv -> copyMasked(store.get("Orders")))
         .thenThrow(new IllegalStateException("boom before any mutation"));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(dataSourceService, never()).updateDataSource(any(), any(), any());
      assertEquals("jdbc:h2:mem:test", store.get("Orders").getUrl(),
                  "nothing was ever mutated -- this is a genuine zero-net-change no-op");
   }

   // Bug 76808: same "pre-mutation throw must not force rollback failed" proof, for the delete
   // verb -- fault the SECOND getDataSource read (inside applyDelete itself, before the dependency
   // preflight and the mutating deleteDataSource call), leaving the 1st (apply()'s own re-resolve)
   // to succeed normally.
   @Test void throwBeforeDeleteMutatingCallMustNotForceRollbackFailed() throws Exception {
      DataSourceApplyRequest req = applyRequest("delete it", true, deleteChange("Orders", null));

      when(dataSourceService.getDataSource(eq("id-orders"), eq(user)))
         .thenAnswer(inv -> copyMasked(store.get("Orders")))
         .thenThrow(new IllegalStateException("boom before any mutation"));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(result.rollbackFailures());
      verify(dataSourceService, never()).deleteDataSource(any(), anyBoolean(), any());
      assertNotNull(store.get("Orders"), "nothing was ever mutated -- this is a genuine no-op");
   }

   // Bug 76808 per-verb coverage: applyFolderCreate has no pre-mutation service call of its own to
   // fault (unlike update/delete) -- its only mutating-adjacent call is createDataSourceFolder
   // itself, immediately after mutationEntered is set true. This proves the OTHER half of the same
   // gate for this verb: a throw that DOES reach the mutating call must still force
   // ROLLBACK_FAILED, exactly like throwMidApplyIsReportedAsUnknownStateAndRollbackFailed does for
   // update.
   @Test void throwDuringFolderCreateMutatingCallIsReportedAsRollbackFailed() throws Exception {
      doThrow(new IllegalStateException("boom")).when(dataSourceService)
         .createDataSourceFolder(eq("Examples/NewFolder"), eq(user));

      DataSourceApplyRequest req = applyRequest("create folder",
         false, folderCreateChange("Examples/NewFolder"));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals(1, result.rollbackFailures().size());
      assertEquals("folder:Examples/NewFolder", result.rollbackFailures().get(0).property());
   }

   /** Two-entry plan: the first (Orders) update succeeds; the second (Reports) fails verification
    * because its {@code updateDataSource} call is stubbed to silently no-op (the URL never
    * actually changes) -- forcing the whole apply into rollback, which must restore Orders' ORIGINAL
    * url, not merely report success. */
   @Test void rollbackOfUpdateRestoresTheBeforeValueWhenALaterChangeFails() throws Exception {
      store.put("Reports", jdbc("id-reports", "Reports", false, null));
      doAnswer(inv -> null).when(dataSourceService)
         .updateDataSource(eq("id-reports"), any(), eq(user));

      DataSourceChangeRequest ordersUpdate = updateChange("Orders", Map.of("url", "jdbc:h2:mem:new"));
      DataSourceChangeRequest reportsUpdate =
         updateChange("Reports", Map.of("url", "jdbc:h2:mem:new2"));
      ResolvedPlan resolved =
         planService.resolve(planRequest("two changes", List.of(ordersUpdate, reportsUpdate)), user);
      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask("two changes");
      req.setChanges(List.of(ordersUpdate, reportsUpdate));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals("jdbc:h2:mem:test", store.get("Orders").getUrl(),
                  "rollback must restore the original url");
   }

   @Test void applyIgnoresAStaleIdOnTheRequestAndResolvesByNameInstead() throws Exception {
      DataSourceChangeRequest change = updateChange("Orders", Map.of("url", "jdbc:h2:mem:test2"));
      change.setId("some-stale-cached-id-from-preview");
      DataSourceApplyRequest req = applyRequest("update url", false, change);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("jdbc:h2:mem:test2", store.get("Orders").getUrl());
   }

   @Test void deleteIsNeverRolledBackWhenAnotherChangeInTheSamePlanFails() throws Exception {
      store.put("Reports", jdbc("id-reports", "Reports", false, null));
      doAnswer(inv -> null).when(dataSourceService)
         .updateDataSource(eq("id-reports"), any(), eq(user));

      DataSourceChangeRequest ordersDelete = deleteChange("Orders", null);
      DataSourceChangeRequest reportsUpdate =
         updateChange("Reports", Map.of("url", "jdbc:h2:mem:new2"));
      ResolvedPlan resolved = planService.resolve(
         planRequest("delete then a failing update", List.of(ordersDelete, reportsUpdate)), user);
      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask("delete then a failing update");
      req.setChanges(List.of(ordersDelete, reportsUpdate));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");
      req.setAcknowledgeIrreversibleDelete(true);

      var result = service.apply(req, user);

      // The delete's own outcome is applied/failed, never individually rolled back -- Orders stays
      // deleted even though the plan overall reports rolled-back for the OTHER entry.
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertNull(store.get("Orders"));
      verify(dataSourceService, never()).updateDataSource(eq("id-orders"), any(), any());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private DataSourceApplyRequest applyRequest(String task, boolean acknowledgeDelete,
                                               DataSourceChangeRequest... changes)
      throws Exception
   {
      DataSourceChangePlanRequest probe = planRequest(task, List.of(changes));
      ResolvedPlan resolved = planService.resolve(probe, user);

      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");

      if(acknowledgeDelete) {
         req.setAcknowledgeIrreversibleDelete(true);
      }

      return req;
   }

   /**
    * Builds an apply request whose {@code taskToken}/{@code planHash} were issued for {@code
    * previewTask} (the reviewed narrative), but whose own {@code task} field carries a DIFFERENT
    * string ({@code applyTask}) -- the shape a caller previewing an honest description then
    * applying with paraphrased/divergent text produces. {@code planHash} is unaffected by the task
    * text either way (task is deliberately excluded from the hash), so this still passes the
    * planHash gate.
    */
   private DataSourceApplyRequest requestWithDivergentApplyTask(
      String previewTask, String applyTask, boolean acknowledgeDelete,
      DataSourceChangeRequest... changes) throws Exception
   {
      DataSourceChangePlanRequest preview = planRequest(previewTask, List.of(changes));
      ResolvedPlan resolved = planService.resolve(preview, user);

      DataSourceApplyRequest req = new DataSourceApplyRequest();
      req.setTask(applyTask);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");

      if(acknowledgeDelete) {
         req.setAcknowledgeIrreversibleDelete(true);
      }

      return req;
   }

   private static DataSourceChangePlanRequest planRequest(String task,
                                                           List<DataSourceChangeRequest> changes)
   {
      DataSourceChangePlanRequest req = new DataSourceChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static DataSourceChangeRequest updateChange(String name, Map<String, Object> spec) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_UPDATE);
      change.setName(name);
      change.setSpec(spec);
      return change;
   }

   private static DataSourceChangeRequest deleteChange(String name, Boolean force) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_DELETE);
      change.setName(name);
      change.setForce(force);
      return change;
   }

   private static DataSourceChangeRequest folderCreateChange(String folderPath) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_CREATE);
      change.setFolderPath(folderPath);
      return change;
   }

   private static inetsoft.uql.asset.AssetObject dependency(String path) {
      return new inetsoft.uql.asset.AssetEntry(inetsoft.uql.asset.AssetRepository.QUERY_SCOPE,
         inetsoft.uql.asset.AssetEntry.Type.QUERY, path, null, "host-org");
   }

   private static JdbcDataSourceProperties jdbc(String id, String name, boolean requireLogin,
                                                String password)
   {
      JdbcDataSourceProperties p = new JdbcDataSourceProperties();
      p.setId(id);
      p.setName(name);
      p.setUrl("jdbc:h2:mem:test");
      p.setDriver("org.h2.Driver");
      p.setDefaultDatabase("APP");
      p.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      p.setIsolation(JdbcDataSourceProperties.IsolationLevel.DEFAULT);
      p.setAnsiJoin(false);
      p.setRequireLogin(requireLogin);
      p.setUser(requireLogin ? "sa" : null);
      p.setPassword(password);
      p.setUseCredentialId(false);
      return p;
   }

   /** Raw copy -- what the fake "storage" actually holds, real password included. Used for the
    * store the test asserts against and for {@code updateDataSource}'s own state mutation (a write
    * must persist exactly what was sent, never re-masking it). */
   private static JdbcDataSourceProperties copy(JdbcDataSourceProperties src) {
      JdbcDataSourceProperties c = new JdbcDataSourceProperties();
      c.setId(src.getId());
      c.setName(src.getName());
      c.setUrl(src.getUrl());
      c.setDriver(src.getDriver());
      c.setDefaultDatabase(src.getDefaultDatabase());
      c.setTableName(src.getTableName());
      c.setIsolation(src.getIsolation());
      c.setAnsiJoin(src.isAnsiJoin());
      c.setRequireLogin(src.isRequireLogin());
      c.setUser(src.getUser());
      c.setPassword(src.getPassword());
      c.setUseCredentialId(src.isUseCredentialId());
      c.setCredentialID(src.getCredentialID());
      return c;
   }

   /** Masked copy -- what a {@code getDataSource} READ actually returns, mirroring the real
    * {@code DataSourceService}/{@code JdbcDataSourceProperties} constructor's own unconditional
    * {@code setPassword("******")} (section 0.1) -- this is exactly the masking
    * {@code buildProposedJdbc}/{@code buildBeforeJdbc} must never trust as a write input. */
   private static JdbcDataSourceProperties copyMasked(JdbcDataSourceProperties src) {
      JdbcDataSourceProperties c = copy(src);
      c.setPassword(src.isRequireLogin() ? "******" : null);
      return c;
   }
}
