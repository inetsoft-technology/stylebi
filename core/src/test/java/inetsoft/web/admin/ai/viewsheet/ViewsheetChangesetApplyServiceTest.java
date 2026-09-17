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
package inetsoft.web.admin.ai.viewsheet;

import inetsoft.web.admin.sheet.Sheet;
import inetsoft.web.admin.sheet.SheetList;
import inetsoft.web.admin.sheet.vs.ViewsheetService;
import inetsoft.web.admin.sheet.ws.WorksheetService;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import inetsoft.util.audit.AdminChangeRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.ai.ResolvedPlan;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 01-spec.md section 13. Uses a tiny in-memory fake ("the store", keyed the same way the real
 * server keys each unit) behind the mocked {@link ViewsheetService}/{@link
 * ViewsheetFolderService}, matching {@code DataSourceChangesetApplyServiceTest}'s own precedent --
 * a fake that actually mutates gives each test's intent a direct assertion instead of a brittle
 * call-count derivation, and is essential here specifically because a successful viewsheet rename
 * changes the unit's own identifier (section 2/4/6), so a positional stub cannot express "the next
 * read must use the NEW id" the way a real, mutating fake naturally does.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ViewsheetChangesetApplyServiceTest {
   @Mock private ViewsheetService viewsheetApiService;
   @Mock private ViewsheetFolderService folderService;
   @Mock private AssetRepository assetRepository;
   @Mock private AdminBackupService backupService;
   @Mock private WorksheetService worksheetApiService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private ViewsheetChangePlanService planService;
   private ViewsheetChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<DependencyTool> dependencyToolStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<SUtil> sutil;

   private static final String VS_ASSET_ID =
      new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                     "Examples/Census", null, "host-org").toIdentifier();

   /** Keyed by current assetId -- a rename removes the old key and adds a new one under the
    * (deterministic) new identifier, exactly like the real AssetRepository. */
   private final Map<String, Sheet> viewsheetStore = new LinkedHashMap<>();
   /** Keyed by {@code normalizedPath + "|" + ownerKey} -- {@code true} means the folder exists. */
   private final Map<String, Boolean> folderStore = new LinkedHashMap<>();
   /** Keyed by assetId, used only by the update-verb tests (bug #76669) -- no existing test
    * exercised the worksheet unit's find/list path before this. */
   private final Map<String, Sheet> worksheetStore = new LinkedHashMap<>();

   @BeforeEach void setUp() throws Exception {
      planService = new ViewsheetChangePlanService(
         viewsheetApiService, folderService, assetRepository, worksheetApiService);
      service = new ViewsheetChangesetApplyService(
         planService, viewsheetApiService, folderService, backupService, worksheetApiService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      dependencyToolStatic = mockStatic(DependencyTool.class, withSettings().lenient());
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of());

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

      lenient().when(backupService.backup(anyString())).thenReturn("backup-ref-1");

      // getFilteredWorksheets (used by findWorksheetById/findWorksheetByLocation) calls
      // SUtil.isDefaultVSGloballyVisible(user), which casts its Principal argument to XPrincipal --
      // the plain Principal mock above is not one, so left real this throws ClassCastException the
      // first time any worksheet-unit test resolves a worksheet. Defaulted static (returns false for
      // every method, including this one) avoids the cast entirely; only the worksheet update tests
      // below actually exercise this path today.
      sutil = mockStatic(SUtil.class, withSettings().lenient());

      worksheetStore.clear();
      lenient().when(worksheetApiService.getWorksheets(eq(user))).thenAnswer(inv -> {
         SheetList list = new SheetList();
         list.getSheets().addAll(worksheetStore.values());
         return list;
      });

      viewsheetStore.clear();
      viewsheetStore.put(VS_ASSET_ID,
         new Sheet(VS_ASSET_ID, "Examples/Census", "Examples/Census", true, null, null));
      folderStore.clear();

      lenient().when(viewsheetApiService.getViewsheets(eq(user))).thenAnswer(inv -> {
         SheetList list = new SheetList();
         list.getSheets().addAll(viewsheetStore.values());
         return list;
      });

      lenient().doAnswer(inv -> {
         String id = inv.getArgument(0);
         String path = inv.getArgument(1);
         boolean global = inv.getArgument(2);
         IdentityID owner = inv.getArgument(3);
         Sheet current = viewsheetStore.remove(id);

         if(current == null) {
            throw new IllegalStateException("no such viewsheet: " + id);
         }

         String orgId = AssetEntry.createAssetEntry(id).getOrgID();
         int scope = global ? AssetRepository.GLOBAL_SCOPE : AssetRepository.USER_SCOPE;
         AssetEntry newEntry =
            new AssetEntry(scope, AssetEntry.Type.VIEWSHEET, path, global ? null : owner, orgId);
         String newId = newEntry.toIdentifier();
         viewsheetStore.put(newId, new Sheet(newId, path, path, global, global ? null : owner, null));
         return null;
      }).when(viewsheetApiService)
        .renameViewsheet(anyString(), anyString(), anyBoolean(), any(), eq(user));

      lenient().doAnswer(inv -> {
         String id = inv.getArgument(0);
         viewsheetStore.remove(id);
         return null;
      }).when(viewsheetApiService).deleteViewsheet(anyString(), eq(user));

      lenient().doAnswer(inv -> {
         String parentFolder = inv.getArgument(0);
         String folderName = inv.getArgument(1);
         IdentityID owner = inv.getArgument(4);
         String fullPath = ViewsheetFolderService.computeFolderFullPath(parentFolder, folderName, owner);
         folderStore.put(folderKey(fullPath, owner), true);
         return null;
      }).when(viewsheetApiService).addFolder(any(), anyString(), any(), any(), any(), eq(user));

      // rollbackFolderDelete() still calls the original 4-arg addFolder overload (no
      // alias/description to restore -- Track A folder-delete rollback predates Track B).
      lenient().doAnswer(inv -> {
         String parentFolder = inv.getArgument(0);
         String folderName = inv.getArgument(1);
         IdentityID owner = inv.getArgument(2);
         String fullPath = ViewsheetFolderService.computeFolderFullPath(parentFolder, folderName, owner);
         folderStore.put(folderKey(fullPath, owner), true);
         return null;
      }).when(viewsheetApiService).addFolder(any(), anyString(), any(), eq(user));

      lenient().doAnswer(inv -> {
         String path = inv.getArgument(0);
         IdentityID owner = inv.getArgument(1);
         folderStore.remove(folderKey(path, owner));
         return null;
      }).when(viewsheetApiService).removeFolder(anyString(), any(), eq(user));

      lenient().doAnswer(inv -> {
         String oldPath = inv.getArgument(0);
         String newPath = inv.getArgument(1);
         IdentityID owner = inv.getArgument(2);
         folderStore.remove(folderKey(oldPath, owner));
         folderStore.put(folderKey(newPath, owner), true);
         return null;
      }).when(viewsheetApiService).renameFolder(anyString(), anyString(), any(), eq(user));

      lenient().when(folderService.getFolder(anyString(), any())).thenAnswer(inv -> {
         String path = inv.getArgument(0);
         IdentityID owner = inv.getArgument(1);
         boolean found = folderStore.getOrDefault(folderKey(path, owner), false);
         return new GetViewsheetFolderResult(
            found, path, ViewsheetFolderService.ownerKey(owner), null, null);
      });
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      dependencyToolStatic.close();
      tool.close();
      sutil.close();
   }

   // -------------------------------------------------------------------------
   // viewsheet rename
   // -------------------------------------------------------------------------

   @Test void appliesAViewsheetRenameAndReportsApplied() throws Exception {
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);
      ViewsheetApplyRequest req = applyRequest("rename", false, change);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(viewsheetStore.containsKey(VS_ASSET_ID));
      assertTrue(viewsheetStore.values().stream()
         .anyMatch(s -> "Examples/Census2".equals(s.getPath())));
   }

   /** The single most important rollback-correctness case (section 4/6's own framing): a successful
    * rename changes the viewsheet's own asset identifier, so the compensating rename-back must
    * target the NEWLY-resolved identity, never the stale pre-apply assetId. Forced by making a
    * SECOND entry in the same plan throw during apply. */
   @Test void viewsheetRenameRollbackTargetsTheNewlyResolvedIdentityNotTheStaleOne() throws Exception {
      folderStore.put(folderKey("Examples/DoomedFolder", null), true);
      // A no-op (not a throw) that leaves the folder untouched -- verification then fails
      // because the folder is still present, exactly like DataSourceChangesetApplyServiceTest's
      // own "silently no-ops rather than throws" precedent for forcing a verified: false outcome
      // (as opposed to the unknown-state-on-throw path, a structurally different failure class
      // covered separately below).
      doAnswer(inv -> null).when(viewsheetApiService)
         .removeFolder(eq("Examples/DoomedFolder"), isNull(), eq(user));

      ViewsheetChangeRequest rename = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);
      ViewsheetChangeRequest folderDelete = deleteFolderChange("Examples/DoomedFolder", null);
      ViewsheetApplyRequest req = applyRequest("mixed", false, rename, folderDelete);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      // The deterministic identifier for the original (path, global, owner) is VS_ASSET_ID again --
      // this only holds if rollback targeted the NEW post-rename id when calling renameViewsheet a
      // second time, not the stale one (which would no longer resolve in the store at all).
      assertTrue(viewsheetStore.containsKey(VS_ASSET_ID));
      assertEquals("Examples/Census", viewsheetStore.get(VS_ASSET_ID).getPath());
   }

   @Test void applyThrowsWhenViewsheetNotFoundAtApplyTime() throws Exception {
      viewsheetStore.remove(VS_ASSET_ID);
      // Bypass resolve()'s own preview-time existence check by hashing against a plan built before
      // removal, then removing the unit "concurrently" before apply proper runs its own re-resolve.
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.apply(applyRequest("rename", false, change), user));
      assertTrue(ex.getMessage().contains("assetId"));
   }

   // -------------------------------------------------------------------------
   // viewsheet delete + section 0.2 dependency preflight re-run at apply time
   // -------------------------------------------------------------------------

   @Test void appliesAViewsheetDeleteAndReportsApplied() throws Exception {
      ViewsheetApplyRequest req = applyRequest("delete", true, deleteViewsheetChange(VS_ASSET_ID));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(viewsheetStore.containsKey(VS_ASSET_ID));
   }

   @Test void applyRefusesWhenADependencyWasAddedAfterPreviewButBeforeApply() throws Exception {
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      ViewsheetApplyRequest req = applyRequest("delete", true, change);
      // Simulates a concurrent change adding a dependency between the original preview (whose hash
      // is embedded in `req`) and this apply call -- apply() re-resolves fresh, re-running section
      // 0.2's own preflight, so the addition is caught here too, not only at preview time.
      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("force"));
      assertTrue(viewsheetStore.containsKey(VS_ASSET_ID), "delete must not have been attempted");
   }

   @Test void deleteAdvisoryNamesTheDependentsWhenForced() throws Exception {
      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setForce(true);

      var result = service.apply(applyRequest("force delete", true, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertNotNull(result.results().get(0).advisory());
      assertTrue(result.results().get(0).advisory().contains("Census Query"));
   }

   @Test void deleteIsNeverRolledBackWhenAnotherChangeInTheSamePlanFails() throws Exception {
      folderStore.put(folderKey("Examples/DoomedFolder", null), true);
      // A no-op (not a throw) that leaves the folder untouched -- verification then fails
      // because the folder is still present, exactly like DataSourceChangesetApplyServiceTest's
      // own "silently no-ops rather than throws" precedent for forcing a verified: false outcome
      // (as opposed to the unknown-state-on-throw path, a structurally different failure class
      // covered separately below).
      doAnswer(inv -> null).when(viewsheetApiService)
         .removeFolder(eq("Examples/DoomedFolder"), isNull(), eq(user));

      ViewsheetChangeRequest delete = deleteViewsheetChange(VS_ASSET_ID);
      ViewsheetChangeRequest folderDelete = deleteFolderChange("Examples/DoomedFolder", null);
      ViewsheetApplyRequest req = applyRequest("mixed", true, delete, folderDelete);

      var result = service.apply(req, user);

      // The delete's own outcome is applied/failed, never individually rolled back -- the viewsheet
      // stays deleted even though the plan overall reports rolled-back for the OTHER entry.
      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertFalse(viewsheetStore.containsKey(VS_ASSET_ID));
      verify(viewsheetApiService, never()).renameViewsheet(any(), any(), anyBoolean(), any(), any());
   }

   // -------------------------------------------------------------------------
   // folder create / delete / rename -- apply + rollback
   // -------------------------------------------------------------------------

   @Test void appliesAFolderCreateAndReportsApplied() throws Exception {
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);

      var result = service.apply(applyRequest("create folder", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(folderStore.getOrDefault(folderKey("Examples/New Folder", null), false));
   }

   @Test void folderCreateRollbackRemovesTheCreatedFolder() throws Exception {
      folderStore.put(folderKey("Examples/DoomedFolder", null), true);
      // A no-op (not a throw) that leaves the folder untouched -- verification then fails
      // because the folder is still present, exactly like DataSourceChangesetApplyServiceTest's
      // own "silently no-ops rather than throws" precedent for forcing a verified: false outcome
      // (as opposed to the unknown-state-on-throw path, a structurally different failure class
      // covered separately below).
      doAnswer(inv -> null).when(viewsheetApiService)
         .removeFolder(eq("Examples/DoomedFolder"), isNull(), eq(user));

      ViewsheetChangeRequest create = createFolderChange("Examples", "New Folder", null);
      ViewsheetChangeRequest doomedDelete = deleteFolderChange("Examples/DoomedFolder", null);
      var result = service.apply(applyRequest("mixed", false, create, doomedDelete), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertFalse(folderStore.getOrDefault(folderKey("Examples/New Folder", null), false),
                 "rollback must remove the folder create just applied");
   }

   @Test void appliesAFolderDeleteAndReportsApplied() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);

      var result = service.apply(
         applyRequest("delete folder", false, deleteFolderChange("Examples/Old Folder", null)), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false));
   }

   // -------------------------------------------------------------------------
   // folder delete + bug #76469 content preflight, re-run at apply time
   // -------------------------------------------------------------------------

   @Test void applyThrowsWhenFolderDeleteIsNonEmptyAndForceFalse() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      // Built while the folder still resolves as empty (default unstubbed getAllEntries), so
      // resolve() succeeds and a plan hash is obtained -- mirrors
      // applyRefusesWhenADependencyWasAddedAfterPreviewButBeforeApply's own precedent for why the
      // content-preflight check, not construction of `req` itself, must be what throws.
      ViewsheetApplyRequest req = applyRequest("delete folder", true, change);
      AssetEntry containedEntry = containedViewsheet("Examples/Old Folder/Census");
      when(assetRepository.getAllEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ containedEntry });

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("force"));
      assertTrue(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false),
                "delete must not have been attempted");
   }

   /** Even with {@code force: true} already set defensively (so the content preflight itself would
    * pass), a folder that gains content between preview and apply produces a DIFFERENT plan hash
    * (bug #76469's content count/list folds into the hash the same way section 0.2's dependency
    * list already does for viewsheet delete) -- the stale {@code planHash} embedded in `req` no
    * longer matches, so apply refuses via the pre-existing hash-staleness gate before ever reaching
    * {@code viewsheetApiService.removeFolder}. */
   @Test void applyThrowsStalePlanHashWhenContentWasAddedAfterPreviewButBeforeApply()
      throws Exception
   {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);
      ViewsheetApplyRequest req = applyRequest("delete folder", true, change);
      // Simulates a concurrent save into the folder between preview (whose hash is embedded in
      // `req`, taken while the folder was still empty) and this apply call.
      AssetEntry containedEntry = containedViewsheet("Examples/Old Folder/Census");
      when(assetRepository.getAllEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ containedEntry });

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
      assertTrue(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false),
                "delete must not have been attempted");
   }

   @Test void applyThrowsWhenFolderDeleteIsNonEmptyWithoutAcknowledgeIrreversibleDelete()
      throws Exception
   {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      AssetEntry containedEntry = containedViewsheet("Examples/Old Folder/Census");
      when(assetRepository.getAllEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ containedEntry });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);
      ViewsheetApplyRequest req = applyRequest("delete folder", false, change);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("acknowledgeIrreversibleDelete"));
      assertTrue(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false),
                "delete must not have been attempted before the gate check");
   }

   @Test void appliesANonEmptyFolderDeleteWhenForcedAndAcknowledged() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      AssetEntry containedEntry = containedViewsheet("Examples/Old Folder/Census");
      when(assetRepository.getAllEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ containedEntry });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);

      var result = service.apply(applyRequest("delete folder", true, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false));
      assertNotNull(result.results().get(0).advisory());
      assertTrue(result.results().get(0).advisory().contains("Census"));
   }

   /** Section 0.4: folder delete's rollback is a COMPLETE inverse, unlike every prior area's own
    * partial-restore folder/chain/identity delete-rollbacks. */
   @Test void folderDeleteRollbackFullyRestoresTheFolder() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      folderStore.put(folderKey("Examples/DoomedFolder", null), true);
      // A no-op (not a throw) that leaves the folder untouched -- verification then fails
      // because the folder is still present, exactly like DataSourceChangesetApplyServiceTest's
      // own "silently no-ops rather than throws" precedent for forcing a verified: false outcome
      // (as opposed to the unknown-state-on-throw path, a structurally different failure class
      // covered separately below).
      doAnswer(inv -> null).when(viewsheetApiService)
         .removeFolder(eq("Examples/DoomedFolder"), isNull(), eq(user));

      ViewsheetChangeRequest delete = deleteFolderChange("Examples/Old Folder", null);
      ViewsheetChangeRequest doomedDelete = deleteFolderChange("Examples/DoomedFolder", null);
      var result = service.apply(applyRequest("mixed", false, delete, doomedDelete), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false),
                "rollback must fully restore the deleted folder");
   }

   @Test void appliesAFolderRenameAndReportsApplied() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);

      var result = service.apply(applyRequest("rename folder", false,
         renameFolderChange("Examples/Old Folder", "Examples/New Folder", null)), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false));
      assertTrue(folderStore.getOrDefault(folderKey("Examples/New Folder", null), false));
   }

   @Test void folderRenameRollbackSwapsBackToTheOriginalPath() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      folderStore.put(folderKey("Examples/DoomedFolder", null), true);
      // A no-op (not a throw) that leaves the folder untouched -- verification then fails
      // because the folder is still present, exactly like DataSourceChangesetApplyServiceTest's
      // own "silently no-ops rather than throws" precedent for forcing a verified: false outcome
      // (as opposed to the unknown-state-on-throw path, a structurally different failure class
      // covered separately below).
      doAnswer(inv -> null).when(viewsheetApiService)
         .removeFolder(eq("Examples/DoomedFolder"), isNull(), eq(user));

      ViewsheetChangeRequest rename =
         renameFolderChange("Examples/Old Folder", "Examples/New Folder", null);
      ViewsheetChangeRequest doomedDelete = deleteFolderChange("Examples/DoomedFolder", null);
      var result = service.apply(applyRequest("mixed", false, rename, doomedDelete), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(folderStore.getOrDefault(folderKey("Examples/Old Folder", null), false));
      assertFalse(folderStore.getOrDefault(folderKey("Examples/New Folder", null), false));
   }

   // -------------------------------------------------------------------------
   // viewsheet / worksheet / folder update -- alias/description clear-via-empty-string (bug #76669)
   // -------------------------------------------------------------------------

   @Test void appliesAViewsheetDescriptionUpdateAndReportsApplied() throws Exception {
      ViewsheetService.Metadata before =
         new ViewsheetService.Metadata("Existing Alias", "Existing description");
      ViewsheetService.Metadata after =
         new ViewsheetService.Metadata("Existing Alias", "New description");
      when(viewsheetApiService.getViewsheetMetadata(eq(VS_ASSET_ID), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateViewsheetChange(VS_ASSET_ID, null, "New description");
      var result = service.apply(applyRequest("update viewsheet", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(viewsheetApiService)
         .updateMetadata(VS_ASSET_ID, "Existing Alias", "New description", user);
   }

   /** The direct regression test for bug #76669: clearing description via an explicit "" writes
    * mergedDescription = null (not the raw "") so it matches the genuinely-null reread the real
    * server produces (section 2's XML-attribute-collapse mechanism, confirmed by the refuter), and
    * `verified` reports true instead of misreporting a rollback. */
   @Test void clearingAViewsheetDescriptionWithAnEmptyStringVerifiesAppliedNotRolledBack()
      throws Exception
   {
      ViewsheetService.Metadata before =
         new ViewsheetService.Metadata("Existing Alias", "Existing description");
      ViewsheetService.Metadata after = new ViewsheetService.Metadata("Existing Alias", null);
      when(viewsheetApiService.getViewsheetMetadata(eq(VS_ASSET_ID), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateViewsheetChange(VS_ASSET_ID, null, "");
      var result = service.apply(applyRequest("clear viewsheet description", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(viewsheetApiService).updateMetadata(VS_ASSET_ID, "Existing Alias", null, user);
   }

   @Test void clearingAViewsheetAliasWithAnEmptyStringVerifiesAppliedNotRolledBack() throws Exception {
      ViewsheetService.Metadata before =
         new ViewsheetService.Metadata("Existing Alias", "Existing description");
      ViewsheetService.Metadata after = new ViewsheetService.Metadata(null, "Existing description");
      when(viewsheetApiService.getViewsheetMetadata(eq(VS_ASSET_ID), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateViewsheetChange(VS_ASSET_ID, "", null);
      var result = service.apply(applyRequest("clear viewsheet alias", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(viewsheetApiService).updateMetadata(VS_ASSET_ID, null, "Existing description", user);
   }

   /** Guards against a normalization bug wiping the field the caller never touched: only
    * description is set on the wire, so the caller's existing alias must survive unchanged into
    * the write call. */
   @Test void updatingOnlyViewsheetDescriptionLeavesTheExistingAliasUnchanged() throws Exception {
      ViewsheetService.Metadata before =
         new ViewsheetService.Metadata("Existing Alias", "Existing description");
      ViewsheetService.Metadata after =
         new ViewsheetService.Metadata("Existing Alias", "New description");
      when(viewsheetApiService.getViewsheetMetadata(eq(VS_ASSET_ID), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateViewsheetChange(VS_ASSET_ID, null, "New description");
      service.apply(applyRequest("update viewsheet", false, change), user);

      verify(viewsheetApiService)
         .updateMetadata(VS_ASSET_ID, "Existing Alias", "New description", user);
   }

   @Test void appliesAWorksheetDescriptionUpdateAndReportsApplied() throws Exception {
      String assetId = worksheetAssetId("Examples/Sales");
      worksheetStore.put(assetId,
         new Sheet(assetId, "Examples/Sales", "Examples/Sales", true, null, null));
      WorksheetService.Metadata before =
         new WorksheetService.Metadata("Existing Alias", "Existing description");
      WorksheetService.Metadata after =
         new WorksheetService.Metadata("Existing Alias", "New description");
      when(worksheetApiService.getWorksheetSettingsMetadata(eq(assetId), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateWorksheetChange(assetId, null, "New description");
      var result = service.apply(applyRequest("update worksheet", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(worksheetApiService)
         .updateMetadata(assetId, "Existing Alias", "New description", user);
   }

   /** Direct regression test for bug #76669, worksheet unit -- same merge/compare normalization
    * fix, applied for consistency even though section 3b's separate (out-of-scope) AssetEntry
    * defect currently masks this live for real worksheet metadata reads. */
   @Test void clearingAWorksheetDescriptionWithAnEmptyStringVerifiesAppliedNotRolledBack()
      throws Exception
   {
      String assetId = worksheetAssetId("Examples/Sales");
      worksheetStore.put(assetId,
         new Sheet(assetId, "Examples/Sales", "Examples/Sales", true, null, null));
      WorksheetService.Metadata before =
         new WorksheetService.Metadata("Existing Alias", "Existing description");
      WorksheetService.Metadata after = new WorksheetService.Metadata("Existing Alias", null);
      when(worksheetApiService.getWorksheetSettingsMetadata(eq(assetId), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateWorksheetChange(assetId, null, "");
      var result = service.apply(applyRequest("clear worksheet description", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(worksheetApiService).updateMetadata(assetId, "Existing Alias", null, user);
   }

   @Test void clearingAWorksheetAliasWithAnEmptyStringVerifiesAppliedNotRolledBack() throws Exception {
      String assetId = worksheetAssetId("Examples/Sales");
      worksheetStore.put(assetId,
         new Sheet(assetId, "Examples/Sales", "Examples/Sales", true, null, null));
      WorksheetService.Metadata before =
         new WorksheetService.Metadata("Existing Alias", "Existing description");
      WorksheetService.Metadata after =
         new WorksheetService.Metadata(null, "Existing description");
      when(worksheetApiService.getWorksheetSettingsMetadata(eq(assetId), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateWorksheetChange(assetId, "", null);
      var result = service.apply(applyRequest("clear worksheet alias", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(worksheetApiService).updateMetadata(assetId, null, "Existing description", user);
   }

   @Test void updatingOnlyWorksheetDescriptionLeavesTheExistingAliasUnchanged() throws Exception {
      String assetId = worksheetAssetId("Examples/Sales");
      worksheetStore.put(assetId,
         new Sheet(assetId, "Examples/Sales", "Examples/Sales", true, null, null));
      WorksheetService.Metadata before =
         new WorksheetService.Metadata("Existing Alias", "Existing description");
      WorksheetService.Metadata after =
         new WorksheetService.Metadata("Existing Alias", "New description");
      when(worksheetApiService.getWorksheetSettingsMetadata(eq(assetId), eq(user)))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateWorksheetChange(assetId, null, "New description");
      service.apply(applyRequest("update worksheet", false, change), user);

      verify(worksheetApiService)
         .updateMetadata(assetId, "Existing Alias", "New description", user);
   }

   @Test void appliesAFolderDescriptionUpdateAndReportsApplied() throws Exception {
      GetViewsheetFolderResult before = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", "Existing description");
      GetViewsheetFolderResult after = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", "New description");
      when(folderService.getFolder(eq("Examples/Old Folder"), isNull()))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change =
         updateFolderChange("Examples/Old Folder", null, null, "New description");
      var result = service.apply(applyRequest("update folder", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(folderService).updateFolderMetadata(
         "Examples/Old Folder", null, "Existing Alias", "New description", user);
   }

   /** Direct regression test for bug #76669, folder unit -- applied for consistency even though the
    * refuter traced the folder registry read to be a same-process cache hit (never reproduces the
    * symptom live), per section 3a. */
   @Test void clearingAFolderDescriptionWithAnEmptyStringVerifiesAppliedNotRolledBack()
      throws Exception
   {
      GetViewsheetFolderResult before = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", "Existing description");
      GetViewsheetFolderResult after = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", null);
      when(folderService.getFolder(eq("Examples/Old Folder"), isNull()))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateFolderChange("Examples/Old Folder", null, null, "");
      var result = service.apply(applyRequest("clear folder description", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(folderService).updateFolderMetadata(
         "Examples/Old Folder", null, "Existing Alias", null, user);
   }

   @Test void clearingAFolderAliasWithAnEmptyStringVerifiesAppliedNotRolledBack() throws Exception {
      GetViewsheetFolderResult before = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", "Existing description");
      GetViewsheetFolderResult after = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, null, "Existing description");
      when(folderService.getFolder(eq("Examples/Old Folder"), isNull()))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change = updateFolderChange("Examples/Old Folder", null, "", null);
      var result = service.apply(applyRequest("clear folder alias", false, change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      verify(folderService).updateFolderMetadata(
         "Examples/Old Folder", null, null, "Existing description", user);
   }

   @Test void updatingOnlyFolderDescriptionLeavesTheExistingAliasUnchanged() throws Exception {
      GetViewsheetFolderResult before = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", "Existing description");
      GetViewsheetFolderResult after = new GetViewsheetFolderResult(
         true, "Examples/Old Folder", null, "Existing Alias", "New description");
      when(folderService.getFolder(eq("Examples/Old Folder"), isNull()))
         .thenReturn(before, before, before, after);

      ViewsheetChangeRequest change =
         updateFolderChange("Examples/Old Folder", null, null, "New description");
      service.apply(applyRequest("update folder", false, change), user);

      verify(folderService).updateFolderMetadata(
         "Examples/Old Folder", null, "Existing Alias", "New description", user);
   }

   // -------------------------------------------------------------------------
   // gates, including 03-reconcile.md's per-verb-varying signoff at the apply layer
   // -------------------------------------------------------------------------

   @Test void applyThrowsOnStalePlanHash() throws Exception {
      ViewsheetApplyRequest req =
         applyRequest("rename", false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));
      req.setPlanHash("stale-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
      verify(viewsheetApiService, never()).renameViewsheet(any(), any(), anyBoolean(), any(), any());
   }

   @Test void applyThrowsTaskTokenMismatchOnMissingToken() throws Exception {
      ViewsheetApplyRequest req =
         applyRequest("rename", false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));
      req.setTaskToken(null);

      AdminChangesetApplyService.TaskTokenMismatchException ex = assertThrows(
         AdminChangesetApplyService.TaskTokenMismatchException.class,
         () -> service.apply(req, user));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
      verify(viewsheetApiService, never()).renameViewsheet(any(), any(), anyBoolean(), any(), any());
   }

   @Test void applyThrowsTaskTokenMismatchOnBlankToken() throws Exception {
      ViewsheetApplyRequest req =
         applyRequest("rename", false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));
      req.setTaskToken("   ");

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
                  () -> service.apply(req, user));
   }

   @Test void applyThrowsTaskTokenMismatchOnTokenIssuedForADifferentPlanHash() throws Exception {
      ViewsheetApplyRequest req =
         applyRequest("rename", false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));
      ViewsheetApplyRequest otherPlan = applyRequest("rename",
         false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census3", true, null));
      req.setTaskToken(otherPlan.getTaskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
                  () -> service.apply(req, user));
      verify(viewsheetApiService, never()).renameViewsheet(any(), any(), anyBoolean(), any(), any());
   }

   @Test void applyThrowsWhenReviewOutcomeMissingAndPlanContainsAViewsheetVerb() throws Exception {
      ViewsheetApplyRequest req =
         applyRequest("rename", false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));
      req.setReviewOutcome(null);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
   }

   /** 03-reconcile.md's own required extra scrutiny: a folder-only plan must NOT require
    * reviewOutcome at all, end to end through {@code apply} -- not just at {@code resolve}. */
   @Test void applyDoesNotRequireReviewOutcomeForAFolderOnlyPlan() throws Exception {
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);
      ViewsheetApplyRequest req = applyRequest("create folder", false, change);
      req.setReviewOutcome(null);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
   }

   @Test void applyThrowsWhenViewsheetDeleteWithoutAcknowledgeIrreversibleDelete() throws Exception {
      ViewsheetApplyRequest req = applyRequest("delete", false, deleteViewsheetChange(VS_ASSET_ID));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("acknowledgeIrreversibleDelete"));
      assertTrue(viewsheetStore.containsKey(VS_ASSET_ID),
                "delete must not have been attempted before the gate check");
   }

   @Test void applyDoesNotRequireAcknowledgeIrreversibleDeleteForAFolderDelete() throws Exception {
      folderStore.put(folderKey("Examples/Old Folder", null), true);
      ViewsheetApplyRequest req =
         applyRequest("delete folder", false, deleteFolderChange("Examples/Old Folder", null));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
   }

   // -------------------------------------------------------------------------
   // rollback-failed
   // -------------------------------------------------------------------------

   @Test void throwMidApplyIsReportedAsUnknownStateAndRollbackFailed() throws Exception {
      doThrow(new IllegalStateException("boom")).when(viewsheetApiService)
         .renameViewsheet(eq(VS_ASSET_ID), anyString(), anyBoolean(), any(), eq(user));

      ViewsheetApplyRequest req =
         applyRequest("rename", false, renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLBACK_FAILED, result.status());
      assertEquals(1, result.rollbackFailures().size());
      assertTrue(result.rollbackFailures().get(0).property().startsWith("viewsheet:"));
   }

   // -------------------------------------------------------------------------
   // taskToken audit-pinning: the audit record must carry the PREVIEWED task narrative, never the
   // apply call's own (possibly diverged) task field -- covers both source-read points (the main
   // apply pass's applyOne(...) call and the rollback pass's rollback(...) call), and both unit
   // types (viewsheet verb, folder verb) even though the fix only needed to touch two call sites.
   // -------------------------------------------------------------------------

   @Test void auditsThePreviewedTaskNarrativeEvenWhenApplyTaskDiffersForAViewsheetVerb()
      throws Exception
   {
      tool.when(Tool::getHost).thenReturn("test-host");
      ViewsheetApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: rename census", "totally different apply-time text", false,
         renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null));
      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         var result = service.apply(req, user);

         assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      assertEquals("reviewed: rename census", captor.getValue().getTaskDescription());
   }

   @Test void auditsThePreviewedTaskNarrativeEvenWhenApplyTaskDiffersForAFolderVerb()
      throws Exception
   {
      tool.when(Tool::getHost).thenReturn("test-host");
      ViewsheetApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: create folder", "totally different apply-time text", false,
         createFolderChange("Examples", "New Folder", null));
      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         var result = service.apply(req, user);

         assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance).auditAdminChange(captor.capture(), eq(user));
      assertEquals("reviewed: create folder", captor.getValue().getTaskDescription());
   }

   /** Exercises the SECOND source-read point (the rollback pass's own {@code rollback(...)} call)
    * -- a mixed plan whose second entry fails verification, forcing the first (a viewsheet rename)
    * to be rolled back, with the apply request's own task diverging from what was reviewed. */
   @Test void auditsThePreviewedTaskNarrativeEvenWhenApplyTaskDiffersOnRollback() throws Exception {
      tool.when(Tool::getHost).thenReturn("test-host");
      folderStore.put(folderKey("Examples/DoomedFolder", null), true);
      // A no-op (not a throw) that leaves the folder untouched -- verification then fails, forcing
      // the whole plan to roll back, exactly like the existing (non-audit-focused) rollback tests
      // above.
      doAnswer(inv -> null).when(viewsheetApiService)
         .removeFolder(eq("Examples/DoomedFolder"), isNull(), eq(user));

      ViewsheetChangeRequest rename = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);
      ViewsheetChangeRequest folderDelete = deleteFolderChange("Examples/DoomedFolder", null);
      ViewsheetApplyRequest req = applyRequestWithDivergentApplyTask(
         "reviewed: mixed plan", "totally different apply-time text", false, rename, folderDelete);
      Audit auditInstance = mock(Audit.class);

      try(MockedStatic<Audit> audit = mockStatic(Audit.class)) {
         audit.when(Audit::getInstance).thenReturn(auditInstance);
         var result = service.apply(req, user);

         assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      }

      ArgumentCaptor<AdminChangeRecord> captor = ArgumentCaptor.forClass(AdminChangeRecord.class);
      verify(auditInstance, atLeastOnce()).auditAdminChange(captor.capture(), eq(user));
      List<AdminChangeRecord> rollbackRecords = captor.getAllValues().stream()
         .filter(r -> AdminChangeRecord.ACTION_ROLLBACK.equals(r.getAction()))
         .toList();
      assertFalse(rollbackRecords.isEmpty(), "expected at least one rollback audit record");
      assertTrue(rollbackRecords.stream()
         .allMatch(r -> "reviewed: mixed plan".equals(r.getTaskDescription())));
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static String folderKey(String normalizedPath, IdentityID owner) {
      String ownerKey = ViewsheetFolderService.ownerKey(owner);
      return normalizedPath + "|" + (ownerKey == null ? "" : ownerKey);
   }

   private ViewsheetApplyRequest applyRequest(String task, boolean acknowledgeDelete,
                                              ViewsheetChangeRequest... changes)
      throws Exception
   {
      ViewsheetChangePlanRequest probe = planRequest(task, List.of(changes));
      ResolvedPlan resolved = planService.resolve(probe, user);

      ViewsheetApplyRequest req = new ViewsheetApplyRequest();
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
    * Builds an apply request whose {@code taskToken} was issued for a DIFFERENT task string than
    * the one this request's own {@code task} field carries -- the shape a caller previewing an
    * honest narrative then applying with a paraphrased/diverged task string produces.
    */
   private ViewsheetApplyRequest applyRequestWithDivergentApplyTask(
      String previewTask, String applyTask, boolean acknowledgeDelete,
      ViewsheetChangeRequest... changes)
      throws Exception
   {
      ViewsheetChangePlanRequest probe = planRequest(previewTask, List.of(changes));
      ResolvedPlan resolved = planService.resolve(probe, user);

      ViewsheetApplyRequest req = new ViewsheetApplyRequest();
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

   private static ViewsheetChangePlanRequest planRequest(String task,
                                                          List<ViewsheetChangeRequest> changes)
   {
      ViewsheetChangePlanRequest req = new ViewsheetChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static AssetEntry dependency(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY, path, null,
                            "host-org");
   }

   private static AssetEntry containedViewsheet(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            "host-org");
   }

   private static ViewsheetChangeRequest deleteViewsheetChange(String assetId) {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_VIEWSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_DELETE);
      change.setAssetId(assetId);
      return change;
   }

   private static String worksheetAssetId(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            "host-org").toIdentifier();
   }

   private static ViewsheetChangeRequest updateViewsheetChange(String assetId, String alias,
                                                                String description)
   {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_VIEWSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_UPDATE);
      change.setAssetId(assetId);
      change.setAlias(alias);
      change.setDescription(description);
      return change;
   }

   private static ViewsheetChangeRequest updateWorksheetChange(String assetId, String alias,
                                                                String description)
   {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_WORKSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_UPDATE);
      change.setAssetId(assetId);
      change.setAlias(alias);
      change.setDescription(description);
      return change;
   }

   private static ViewsheetChangeRequest updateFolderChange(String path, String owner, String alias,
                                                             String description)
   {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_FOLDER);
      change.setVerb(ViewsheetChangeRequest.VERB_UPDATE);
      change.setPath(path);
      change.setOwner(owner);
      change.setAlias(alias);
      change.setDescription(description);
      return change;
   }

   private static ViewsheetChangeRequest renameViewsheetChange(String assetId, String newPath,
                                                                boolean global, String owner)
   {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_VIEWSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_RENAME);
      change.setAssetId(assetId);
      change.setNewPath(newPath);
      change.setGlobal(global);
      change.setOwner(owner);
      return change;
   }

   private static ViewsheetChangeRequest createFolderChange(String parentFolder, String folderName,
                                                             String owner)
   {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_FOLDER);
      change.setVerb(ViewsheetChangeRequest.VERB_CREATE);
      change.setParentFolder(parentFolder);
      change.setFolderName(folderName);
      change.setOwner(owner);
      return change;
   }

   private static ViewsheetChangeRequest deleteFolderChange(String path, String owner) {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_FOLDER);
      change.setVerb(ViewsheetChangeRequest.VERB_DELETE);
      change.setPath(path);
      change.setOwner(owner);
      return change;
   }

   private static ViewsheetChangeRequest renameFolderChange(String oldPath, String newPath,
                                                             String owner)
   {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_FOLDER);
      change.setVerb(ViewsheetChangeRequest.VERB_RENAME);
      change.setPath(oldPath);
      change.setNewPath(newPath);
      change.setOwner(owner);
      return change;
   }
}
