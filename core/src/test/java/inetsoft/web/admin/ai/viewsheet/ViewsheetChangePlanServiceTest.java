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
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Answers;
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
 * 01-spec.md section 13. Focuses on what this area alone needs beyond the shared preview/hash
 * mechanics every prior area already covers: the two-unit-type discriminator (section 1/11), the
 * section 0.2 dependency preflight for viewsheet delete, the section 2 malformed-assetId
 * rejection, the section 0.3 FOLDER_REQUIRED-adjacent existence checks, and -- the genuine novelty
 * this area introduces (03-reconcile.md) -- per-verb-varying risk/signoff, asserted for BOTH the
 * low-risk folder classification and the high-risk viewsheet classification, plus a plan mixing
 * both verb kinds.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class ViewsheetChangePlanServiceTest {
   @Mock private ViewsheetService viewsheetApiService;
   @Mock private ViewsheetFolderService folderService;
   @Mock private AssetRepository assetRepository;
   @Mock private WorksheetService worksheetApiService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private ViewsheetChangePlanService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<inetsoft.sree.security.SecurityEngine> securityEngineStatic;
   private inetsoft.sree.security.SecurityProvider ownerProvider;
   private MockedStatic<DependencyTool> dependencyToolStatic;
   private MockedStatic<Tool> tool;
   private MockedStatic<inetsoft.sree.internal.SUtil> sutilStatic;

   private static final String VS_ASSET_ID =
      new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                     "Examples/Census", null, "host-org").toIdentifier();

   @BeforeEach void setUp() throws Exception {
      service = new ViewsheetChangePlanService(
         viewsheetApiService, folderService, assetRepository, worksheetApiService);

      // Bug #78866: owner parsing and the missing-owner warning consult the provider through
      // SecurityEngine.getSecurity(); the fixture owners and orgs exist
      ownerProvider = mock(inetsoft.sree.security.SecurityProvider.class, withSettings().lenient());
      inetsoft.sree.security.SecurityEngine engine =
         mock(inetsoft.sree.security.SecurityEngine.class, withSettings().lenient());
      when(engine.getSecurityProvider()).thenReturn(ownerProvider);
      when(ownerProvider.getUser(any())).thenReturn(mock(inetsoft.sree.security.User.class));
      when(ownerProvider.getOrganization(any()))
         .thenReturn(mock(inetsoft.sree.security.Organization.class));
      securityEngineStatic =
         mockStatic(inetsoft.sree.security.SecurityEngine.class, withSettings().lenient());
      securityEngineStatic.when(inetsoft.sree.security.SecurityEngine::getSecurity)
         .thenReturn(engine);
      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");
      lenient().when(user.getName()).thenReturn("bob~;~host-org");

      dependencyToolStatic = mockStatic(DependencyTool.class, withSettings().lenient());
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of());

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      securityEngineStatic.close();
      dependencyToolStatic.close();
      tool.close();

      if(sutilStatic != null) {
         sutilStatic.close();
      }
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      ViewsheetChangePlanRequest req = request("   ", List.of(deleteViewsheetChange(VS_ASSET_ID)));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of()), user));
   }

   @Test void resolveThrowsOnUnrecognizedUnitType() {
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setUnitType("dashboard");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("unitType"));
   }

   @Test void resolveRefusesUnitTypeAbbreviation() {
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setUnitType("vs");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("unitType"));
   }

   @Test void resolveThrowsOnDuplicateViewsheetEntry() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change1 = deleteViewsheetChange(VS_ASSET_ID);
      ViewsheetChangeRequest change2 = deleteViewsheetChange(VS_ASSET_ID);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change1, change2)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // viewsheet: verb aliasing
   // -------------------------------------------------------------------------

   @Test void resolveAcceptsMoveAsRenameAlias() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);
      change.setVerb("move");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveAcceptsRemoveAsDeleteAlias() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setVerb("remove");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveThrowsWhenFolderVerbUsedOnViewsheet() {
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setVerb("create");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   // -------------------------------------------------------------------------
   // section 2: malformed assetId
   // -------------------------------------------------------------------------

   @Test void resolveThrowsFieldNamedErrorOnAssetIdWithNoDelimiter() {
      ViewsheetChangeRequest change = deleteViewsheetChange("not-a-valid-identifier");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("assetId"));
   }

   @Test void resolveThrowsFieldNamedErrorOnAssetIdWithNonNumericScope() {
      ViewsheetChangeRequest change = deleteViewsheetChange("x^y^__NULL__^foo^host-org");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("assetId"));
   }

   @Test void resolveThrowsFieldNamedErrorOnBlankAssetId() {
      ViewsheetChangeRequest change = deleteViewsheetChange("   ");

      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
   }

   // -------------------------------------------------------------------------
   // viewsheet rename
   // -------------------------------------------------------------------------

   @Test void resolveRenameThrowsWhenGlobalMissing() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_VIEWSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_RENAME);
      change.setAssetId(VS_ASSET_ID);
      change.setNewPath("Examples/Census2");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("global"));
   }

   @Test void resolveRenameThrowsWhenGlobalFalseAndOwnerMissing() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", false, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("owner"));
   }

   @Test void resolveRenameThrowsWhenGlobalTrueAndOwnerGiven() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change =
         renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, "admin:host-org");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("owner"));
   }

   @Test void resolveRenameThrowsWhenForceGiven() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);
      change.setForce(true);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("force"));
   }

   @Test void resolveRenameThrowsWhenViewsheetNotFound() throws Exception {
      stubViewsheetList(null, null, true, null); // empty list
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Census2", true, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("assetId"));
   }

   @Test void resolveRenameSucceedsWithOwnerWhenGlobalFalse() throws Exception {
      IdentityID owner = new IdentityID("bob", "host-org");
      stubViewsheetList(VS_ASSET_ID, "My Dashboards/Census", false, owner);
      ViewsheetChangeRequest change =
         renameViewsheetChange(VS_ASSET_ID, "My Dashboards/Census2", false, "bob:host-org");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   // Bug #79898: a rename onto an occupied path would silently overwrite the destination.
   @Test void resolveRenameThrowsWhenDestinationIsOccupied() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      when(viewsheetApiService.isRenameTargetTaken(
         eq(VS_ASSET_ID), eq("Examples/Other"), eq(true), any(), eq(user))).thenReturn(true);
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Other", true, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains(".newPath"));
      assertTrue(ex.getMessage().contains("Examples/Other"));
      assertTrue(ex.getMessage().contains("overwrite"));
   }

   @Test void resolveRenameSucceedsWhenDestinationIsFree() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      when(viewsheetApiService.isRenameTargetTaken(any(), any(), anyBoolean(), any(), any()))
         .thenReturn(false);
      ViewsheetChangeRequest change = renameViewsheetChange(VS_ASSET_ID, "Examples/Free", true, null);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   // Bug #79897: a move into another user's tree cannot be listed, verified or undone afterwards

   @ParameterizedTest
   @ValueSource(strings = { "alice", "alice:host-org", "bob:other-org" })
   void resolveRenameRefusesAnOwnerThatIsNotTheCaller(String ownerRef) throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(renameViewsheetChange(
            VS_ASSET_ID, "My Dashboards/Census2", false, ownerRef))), user));
      assertTrue(ex.getMessage().contains(".owner"), ex.getMessage());
      assertTrue(ex.getMessage().contains(ownerRef.split(":")[0]), ex.getMessage());
      assertTrue(ex.getMessage().contains("not supported"), ex.getMessage());
   }

   @Test void resolveRenameAcceptsTheCallersOwnOrgQualifiedOwner() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(renameViewsheetChange(
         VS_ASSET_ID, "My Dashboards/Census2", false, "bob:host-org"))), user));
   }

   @Test void resolveWorksheetRenameRefusesAnOwnerThatIsNotTheCaller() throws Exception {
      String wsId = stubWorksheetList();
      ViewsheetChangeRequest change = renameViewsheetChange(wsId, "Examples/Sales2", false, "alice");
      change.setUnitType(ViewsheetChangeRequest.UNIT_WORKSHEET);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains(".owner"), ex.getMessage());
      assertTrue(ex.getMessage().contains("alice"), ex.getMessage());
   }

   @Test void resolveWorksheetRenameAcceptsTheCallersOwnOwner() throws Exception {
      String wsId = stubWorksheetList();
      ViewsheetChangeRequest change = renameViewsheetChange(wsId, "Examples/Sales2", false, "bob");
      change.setUnitType(ViewsheetChangeRequest.UNIT_WORKSHEET);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   private String stubWorksheetList() throws Exception {
      // getFilteredWorksheets casts the Principal to XPrincipal inside SUtil; the plain mock is not one
      sutilStatic = mockStatic(inetsoft.sree.internal.SUtil.class, withSettings().lenient());
      String wsId = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                   "Examples/Sales", null, "host-org").toIdentifier();
      SheetList list = new SheetList();
      list.getSheets().add(new Sheet(wsId, "Examples/Sales", "Examples/Sales", true, null, null));
      lenient().when(worksheetApiService.getWorksheets(any())).thenReturn(list);
      return wsId;
   }

   // -------------------------------------------------------------------------
   // viewsheet delete + section 0.2 dependency preflight
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsWhenDependenciesExistAndForceFalse() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("force"));
   }

   @Test void resolveViewsheetDeleteRefusalNamesAViewsheet() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(deleteViewsheetChange(VS_ASSET_ID))), user));

      assertTrue(ex.getMessage().contains("viewsheet \"" + VS_ASSET_ID + "\""), ex.getMessage());
      assertFalse(ex.getMessage().contains("worksheet \"" + VS_ASSET_ID + "\""), ex.getMessage());
   }

   /** Bug #79895: the dependents refusal of a worksheet delete named the asset as a viewsheet. */
   @Test void resolveWorksheetDeleteRefusalNamesAWorksheet() throws Exception {
      String wsId = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                   "Examples/Sales", null, "host-org").toIdentifier();
      SheetList list = new SheetList();
      list.getSheets().add(new Sheet(wsId, "Examples/Sales", "Examples/Sales", true, null, null));
      lenient().when(worksheetApiService.getWorksheets(eq(user))).thenReturn(list);
      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_WORKSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_DELETE);
      change.setAssetId(wsId);

      try(var sutil = mockStatic(inetsoft.sree.internal.SUtil.class, withSettings().lenient())) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("task", List.of(change)), user));

         assertTrue(ex.getMessage().contains("worksheet \"" + wsId + "\""), ex.getMessage());
         assertFalse(ex.getMessage().contains("viewsheet \"" + wsId + "\""), ex.getMessage());
         assertTrue(ex.getMessage().contains("force"), ex.getMessage());
      }
   }

   @Test void resolveDeleteSucceedsWhenDependenciesExistAndForceTrue() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setForce(true);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveDeleteForcePlanDescriptionNamesEveryDependent() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      var dep1 = dependency("Examples/Census Query");
      var dep2 = dependency("Examples/Census Summary");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep1, dep2));
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setForce(true);

      String description = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).description();
      assertTrue(description.contains("2 dependent asset(s)"), description);
      assertTrue(description.contains("Examples/Census Query"), description);
      assertTrue(description.contains("Examples/Census Summary"), description);
   }

   @Test void resolveDeleteSucceedsWhenNoDependencies() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);

      assertDoesNotThrow(
         () -> service.resolve(request("task", List.of(deleteViewsheetChange(VS_ASSET_ID))), user));
   }

   @Test void resolveDeleteThrowsWhenNewPathGiven() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setNewPath("Examples/Other");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("newPath"));
   }

   // -------------------------------------------------------------------------
   // folder: create
   // -------------------------------------------------------------------------

   @Test void resolveFolderCreateRequiresFolderName() {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_FOLDER);
      change.setVerb(ViewsheetChangeRequest.VERB_CREATE);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("folderName"));
   }

   @Test void resolveFolderCreateThrowsWhenAlreadyExists() throws Exception {
      stubFolder("Examples/New Folder", null, true);
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveFolderCreateOnlyPlanSkipsStorageBackup() throws Exception {
      stubFolder("Examples/New Folder", null, false);
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);

      var plan = service.resolve(request("task", List.of(change)), user);

      // Bug #80650: a brand-new registry folder has a live undo; the snapshot adds nothing.
      assertFalse(plan.requiresStorageBackup());
   }

   @Test void resolveFolderCreateWithAliasVerbAndUpperCaseUnitStillSkipsStorageBackup()
      throws Exception
   {
      stubFolder("Examples/New Folder", null, false);
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);
      change.setUnitType("FOLDER");
      change.setVerb("add");

      assertFalse(service.resolve(request("task", List.of(change)), user).requiresStorageBackup());
   }

   @Test void resolveFolderCreatePlusViewsheetDeleteKeepsStorageBackup() throws Exception {
      stubFolder("Examples/New Folder", null, false);
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);

      var plan = service.resolve(request("task", List.of(
         createFolderChange("Examples", "New Folder", null),
         deleteViewsheetChange(VS_ASSET_ID))), user);

      assertTrue(plan.requiresStorageBackup());
   }

   @Test void resolveFolderCreateAcceptsAddAlias() throws Exception {
      stubFolder("Examples/New Folder", null, false);
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);
      change.setVerb("add");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveFolderCreatePrependsMyDashboardsForOwnerScopedFolder() throws Exception {
      IdentityID owner = new IdentityID("bob", "host-org");
      stubFolder("My Dashboards/Personal", owner, false);
      ViewsheetChangeRequest change = createFolderChange(null, "Personal", "bob:host-org");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertTrue(plan.changes().get(0).property().contains("My Dashboards/Personal"));
      assertTrue(plan.changes().get(0).property().contains("bob"));
   }

   /** Regression test for a real bug found during this track's own testing (repo CLAUDE.md's
    * "tool-misuse is a plugin gap" class): a colon-separated {@code owner} string like {@code
    * "bob:host-org"} must resolve to name "bob"/org "host-org" -- NOT be silently misparsed as one
    * opaque name with an ambient-context-derived org, which is what calling {@code
    * IdentityID.getIdentityIDFromKey} directly on it would produce (that method's own delimiter is
    * the internal {@code "~;~"}, which never appears in caller input; see {@link
    * ViewsheetFolderService#parseOwner}'s javadoc for the full trace). */
   @Test void resolveFolderCreateParsesColonSeparatedOwnerAsNameAndOrgSeparately() throws Exception {
      IdentityID owner = new IdentityID("bob", "other-org");
      stubFolder("My Dashboards/Personal", owner, false);
      ViewsheetChangeRequest change = createFolderChange(null, "Personal", "bob:other-org");

      var plan = service.resolve(request("task", List.of(change)), user);

      // The folderService.getFolder stub only matches an IdentityID with name="bob"/org="other-org"
      // -- if the owner had been misparsed as one opaque name, this stub would never match and the
      // resolve call would NPE (as it did before the fix), not merely produce a wrong key.
      assertEquals("other-org", plan.changes().get(0).orgId());
   }

   // -------------------------------------------------------------------------
   // folder: delete -- path/oldPath aliasing (section 11)
   // -------------------------------------------------------------------------

   @Test void resolveFolderDeleteAcceptsPathField() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      ViewsheetChangeRequest change = deleteFolderChange(null, null);
      change.setPath("Examples/Old Folder");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveFolderDeleteAcceptsOldPathAliasField() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      ViewsheetChangeRequest change = deleteFolderChange(null, null);
      change.setOldPath("Examples/Old Folder");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveFolderDeleteThrowsWhenPathAndOldPathDisagree() {
      ViewsheetChangeRequest change = deleteFolderChange(null, null);
      change.setPath("Examples/A");
      change.setOldPath("Examples/B");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("disagree"));
   }

   @Test void resolveFolderDeleteThrowsWhenNeitherPathNorOldPathGiven() {
      ViewsheetChangeRequest change = deleteFolderChange(null, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("path"));
   }

   @Test void resolveFolderDeleteThrowsWhenNotFound() throws Exception {
      stubFolder("Examples/Missing", null, false);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Missing", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("does not exist"));
   }

   @Test void resolveFolderDeleteThrowsWhenParentFolderGiven() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setParentFolder("Examples");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("parentFolder"));
   }

   @Test void resolveFolderDeleteOfEmptyFolderDisclosesNoContent() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolderContents(new AssetEntry[0]);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);

      var plan = service.resolve(request("task", List.of(change)), user);

      assertTrue(plan.changes().get(0).description().contains("contains no viewsheet"));
      assertFalse(plan.changes().get(0).description().contains("does NOT delete or move"));
      assertFalse(plan.changes().get(0).description().contains("fully compensable"));
   }

   // -------------------------------------------------------------------------
   // folder: delete + bug #76469 content preflight (mirrors viewsheet delete's own
   // dependency preflight, per the fix's "resolveFolderDelete mirrors resolveViewsheetDelete" brief)
   // -------------------------------------------------------------------------

   @Test void resolveFolderDeleteThrowsWhenNonEmptyAndForceFalse() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolderContents(new AssetEntry[]{ containedViewsheet("Examples/Old Folder/Census") });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("force"));
      assertTrue(ex.getMessage().contains("Examples/Old Folder"));
   }

   @Test void resolveFolderDeleteSucceedsWhenNonEmptyAndForceTrue() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolderContents(new AssetEntry[]{ containedViewsheet("Examples/Old Folder/Census") });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveFolderDeleteOfNonEmptyFolderIsClassifiedHighRiskAndRequiresSignoff()
      throws Exception
   {
      stubFolder("Examples/Old Folder", null, true);
      stubFolderContents(new AssetEntry[]{ containedViewsheet("Examples/Old Folder/Census") });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("high", plan.changes().get(0).risk());
      assertTrue(plan.requiresAgentSignoff());
   }

   @Test void resolveFolderDeleteOfNonEmptyFolderDisclosesPermanentIrrecoverableDeletion()
      throws Exception
   {
      stubFolder("Examples/Old Folder", null, true);
      stubFolderContents(new AssetEntry[]{ containedViewsheet("Examples/Old Folder/Census") });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);

      var plan = service.resolve(request("task", List.of(change)), user);
      String description = plan.changes().get(0).description();

      assertTrue(description.contains("PERMANENTLY AND IRRECOVERABLY"));
      assertTrue(description.contains("no recycle bin and no undo"));
      assertFalse(description.contains("does NOT delete or move"));
      assertFalse(description.contains("fully compensable"));
   }

   @Test void hashChangesWhenFolderContentsChange() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);

      stubFolderContents(new AssetEntry[0]);
      var beforePlan = service.resolve(request("t", List.of(change)), user);

      AssetEntry containedEntry = containedViewsheet("Examples/Old Folder/Census");
      when(assetRepository.getAllEntries(any(), any(), any(), any()))
         .thenReturn(new AssetEntry[]{ containedEntry });
      var afterPlan = service.resolve(request("t", List.of(change)), user);

      assertNotEquals(beforePlan.planHash(), afterPlan.planHash());
   }

   /** bug #76469 follow-up (review finding 1(c)): the real delete cascade
    * ({@code AbstractAssetEngine#removeFolder0}/{@code removeSheet0}) does not check the calling
    * principal's own READ permission on individual children, so {@code findFolderContents}'s safety
    * -gate content walk must not either -- otherwise a viewsheet the caller lacks READ on, nested in
    * an otherwise-visible shared (GLOBAL_SCOPE) folder, would be silently excluded from the count,
    * letting a non-empty folder delete slip through as low-risk with no {@code force} required.
    * Asserts the actual bypass mechanism ({@link AssetRepository#IGNORE_PERM}) is set for the
    * duration of the {@code getAllEntries} call and cleared immediately after, not merely that the
    * count comes out right (which the pre-existing non-empty tests already cover for the
    * caller-can-see case). */
   @Test void findFolderContentsBypassesCallerPermissionSoCascadeVisibleContentIsCounted()
      throws Exception
   {
      stubFolder("Examples/Old Folder", null, true);
      when(assetRepository.getAllEntries(any(), any(), any(), any())).thenAnswer(invocation -> {
         assertEquals(Boolean.TRUE, AssetRepository.IGNORE_PERM.get(),
            "findFolderContents must bypass per-child READ permission while walking folder " +
            "contents, since the real delete cascade does not check it either");
         return new AssetEntry[]{ containedViewsheet("Examples/Old Folder/Census") };
      });
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));

      assertTrue(ex.getMessage().contains("force"));
      assertEquals(Boolean.FALSE, AssetRepository.IGNORE_PERM.get(),
         "IGNORE_PERM must be cleared after findFolderContents returns, not leaked to later work " +
         "on this thread");
   }

   /** bug #76469 follow-up review finding 5: the {@code try}/{@code finally} claim in {@link
    * ViewsheetChangePlanService#findFolderContents} was only exercised on the success path. Confirms
    * {@code IGNORE_PERM} is also cleared when {@code getAllEntries} itself throws. */
   @Test void findFolderContentsClearsIgnorePermWhenGetAllEntriesThrows() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      when(assetRepository.getAllEntries(any(), any(), any(), any()))
         .thenThrow(new RuntimeException("backing store failure"));
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);

      assertThrows(Exception.class, () -> service.resolve(request("task", List.of(change)), user));

      assertEquals(Boolean.FALSE, AssetRepository.IGNORE_PERM.get(),
         "IGNORE_PERM must be cleared via finally even when getAllEntries throws, not only on the " +
         "success path");
   }

   // -------------------------------------------------------------------------
   // folder: delete + bug #76469 follow-up review finding 4 -- the bypassed content walk must not
   // disclose permission-invisible-to-the-caller entries' type/path in caller-facing text
   // -------------------------------------------------------------------------

   @Test void resolveFolderDeleteExceptionMessageDoesNotDiscloseEntriesCallerCannotRead()
      throws Exception
   {
      stubFolder("Examples/Old Folder", null, true);
      AssetEntry visibleEntry = containedViewsheet("Examples/Old Folder/Visible");
      AssetEntry hiddenEntry = containedViewsheet("Examples/Old Folder/Hidden");
      stubFolderContents(new AssetEntry[]{ visibleEntry, hiddenEntry });
      doNothing().when(assetRepository)
         .checkAssetPermission(eq(user), eq(visibleEntry), eq(ResourceAction.READ));
      doThrow(new Exception("no read permission")).when(assetRepository)
         .checkAssetPermission(eq(user), eq(hiddenEntry), eq(ResourceAction.READ));
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));

      assertTrue(ex.getMessage().contains("contains 2 viewsheet(s)"));
      assertTrue(ex.getMessage().contains("Examples/Old Folder/Visible"));
      assertFalse(ex.getMessage().contains("Examples/Old Folder/Hidden"));
      assertTrue(ex.getMessage().contains("1 of which you do not have permission to view"));
   }

   @Test void resolveFolderDeleteDescriptionDoesNotDiscloseEntriesCallerCannotRead() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      AssetEntry visibleEntry = containedViewsheet("Examples/Old Folder/Visible");
      AssetEntry hiddenEntry = containedViewsheet("Examples/Old Folder/Hidden");
      stubFolderContents(new AssetEntry[]{ visibleEntry, hiddenEntry });
      doNothing().when(assetRepository)
         .checkAssetPermission(eq(user), eq(visibleEntry), eq(ResourceAction.READ));
      doThrow(new Exception("no read permission")).when(assetRepository)
         .checkAssetPermission(eq(user), eq(hiddenEntry), eq(ResourceAction.READ));
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setForce(true);

      var plan = service.resolve(request("task", List.of(change)), user);
      String description = plan.changes().get(0).description();

      assertTrue(description.contains("Examples/Old Folder/Visible"));
      assertFalse(description.contains("Examples/Old Folder/Hidden"));
      assertTrue(description.contains("1 of which you do not have permission to view"));
      // the count driving the gate/risk decision must still reflect the full, bypassed content --
      // only the human-readable enumeration is restricted to what the caller can see
      assertTrue(description.contains("deletes 2 contained viewsheet(s)"));
      assertEquals("high", plan.changes().get(0).risk());
   }

   // -------------------------------------------------------------------------
   // folder: rename
   // -------------------------------------------------------------------------

   @Test void resolveFolderRenameRequiresNewPath() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      ViewsheetChangeRequest change = deleteFolderChange("Examples/Old Folder", null);
      change.setVerb(ViewsheetChangeRequest.VERB_RENAME);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("newPath"));
   }

   @Test void resolveFolderRenameThrowsWhenOldPathNotFound() throws Exception {
      stubFolder("Examples/Missing", null, false);
      ViewsheetChangeRequest change = renameFolderChange("Examples/Missing", "Examples/New", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("does not exist"));
   }

   @Test void resolveFolderRenameSucceeds() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolder("Examples/New Folder", null, false);
      ViewsheetChangeRequest change =
         renameFolderChange("Examples/Old Folder", "Examples/New Folder", null);

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   // Bug #80190: renameFolder also refuses a destination occupied by a VIEWSHEET (not a folder);
   // preview must refuse it too, naming the taken path.
   @Test void resolveFolderRenameRefusesDestinationOccupiedByAViewsheet() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolder("Examples/New Folder", null, false);
      when(viewsheetApiService.isFolderRenameTargetTaken(eq("Examples/New Folder"), any()))
         .thenReturn(true);
      ViewsheetChangeRequest change =
         renameFolderChange("Examples/Old Folder", "Examples/New Folder", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("\"Examples/New Folder\" already exists"), ex.getMessage());
   }

   // Bug #80190: same for folder create -- addFolder refuses a viewsheet / case-variant folder.
   @Test void resolveFolderCreateRefusesPathOccupiedByAViewsheet() throws Exception {
      stubFolder("Examples/New Folder", null, false);
      when(viewsheetApiService.isFolderCreateTargetTaken(eq("Examples/New Folder"), any()))
         .thenReturn(true);
      ViewsheetChangeRequest change = createFolderChange("Examples", "New Folder", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("\"Examples/New Folder\" already exists"), ex.getMessage());
   }

   // Bug #79896: StyleBI re-points schedule-task actions on rename, so the plan text must not
   // list schedule tasks among the references that go stale.
   @Test void resolveFolderRenameDescriptionSaysScheduleTasksFollow() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolder("Examples/New Folder", null, false);
      ViewsheetChangeRequest change =
         renameFolderChange("Examples/Old Folder", "Examples/New Folder", null);

      String description = service.resolve(request("task", List.of(change)), user)
         .changes().get(0).description();

      assertTrue(description.contains("rewrites the path/asset-identifier of every viewsheet"));
      assertTrue(description.contains("rewrites the path/asset-identifier of every viewsheet currently"));
      assertFalse(description.contains("viewsheet and worksheet"));
      assertTrue(description.contains("Worksheets stored under the same folder path are not moved"));
      assertTrue(description.contains("re-points schedule-task viewsheet actions"));
      assertTrue(description.contains("scheduled tasks follow the rename"));
      assertTrue(description.contains("held outside StyleBI"));
      assertFalse(description.contains("any external reference to those viewsheets"));
   }

   // -------------------------------------------------------------------------
   // 03-reconcile.md: per-verb-varying risk/signoff -- the genuine novelty this area introduces
   // -------------------------------------------------------------------------

   @Test void resolveClassifiesViewsheetVerbAsHighRiskAndRequiresSignoff() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);

      var plan = service.resolve(
         request("task", List.of(deleteViewsheetChange(VS_ASSET_ID))), user);

      assertEquals("high", plan.changes().get(0).risk());
      assertTrue(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
   }

   @Test void resolveClassifiesFolderOnlyPlanAsLowRiskAndDoesNotRequireSignoff() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolderContents(new AssetEntry[0]);

      var plan = service.resolve(
         request("task", List.of(deleteFolderChange("Examples/Old Folder", null))), user);

      assertEquals("low", plan.changes().get(0).risk());
      // snapshotScope is independent of risk (AREA-SPEC-GUIDE.md section 2.3): still storage-scoped.
      assertTrue(plan.requiresStorageBackup());
      assertFalse(plan.requiresAgentSignoff());
   }

   @Test void resolveMixedPlanOfFolderAndViewsheetVerbsRequiresSignoff() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);

      var plan = service.resolve(request("task",
         List.of(deleteFolderChange("Examples/Old Folder", null), deleteViewsheetChange(VS_ASSET_ID))),
         user);

      assertEquals("low", plan.changes().get(0).risk());
      assertEquals("high", plan.changes().get(1).risk());
      // A single high-risk entry anywhere in the plan is enough to require signoff for the whole
      // plan -- the mixed-plan resolution 03-reconcile.md's own review flagged for extra scrutiny.
      assertTrue(plan.requiresAgentSignoff());
   }

   // -------------------------------------------------------------------------
   // hash stability
   // -------------------------------------------------------------------------

   @Test void hashIsStableForIdenticalRequests() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);

      var first = service.resolve(request("t", List.of(change)), user);
      var second = service.resolve(request("t", List.of(change)), user);

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void issuesATaskTokenBoundToThePlanHash() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);

      var plan = service.resolve(request("t", List.of(change)), user);

      assertEquals("TKN:" + plan.planHash() + "\u001ft", plan.taskToken());
   }

   @Test void hashChangesWhenDependencyListChanges() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);
      change.setForce(true);

      var beforePlan = service.resolve(request("t", List.of(change)), user);

      var dep = dependency("Examples/Census Query");
      dependencyToolStatic.when(() -> DependencyTool.getDependencies(anyString()))
         .thenReturn(List.of(dep));
      var afterPlan = service.resolve(request("t", List.of(change)), user);

      assertNotEquals(beforePlan.planHash(), afterPlan.planHash());
   }

   // task is a free-text, audit-only label (bug 76454) -- a caller that does not replay it
   // byte-for-byte between preview and apply must not see a false planHash conflict.
   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest change = deleteViewsheetChange(VS_ASSET_ID);

      var first = service.resolve(request("delete the Census viewsheet", List.of(change)), user);
      var second = service.resolve(request("remove Census vs", List.of(change)), user);

      assertEquals(first.planHash(), second.planHash());
   }

   // -------------------------------------------------------------------------
   // Bug #78866: owner reference forms on viewsheet rename (global=false)
   // -------------------------------------------------------------------------

   @ParameterizedTest
   @ValueSource(strings = { "bob", "bob~;~host-org", "bob^host-org" })
   void resolveRenameAcceptsEveryOwnerForm(String ownerRef) throws Exception {
      IdentityID owner = new IdentityID("bob", "host-org");
      inetsoft.sree.security.User bob = mock(inetsoft.sree.security.User.class);
      when(ownerProvider.getUser(any()))
         .thenAnswer(inv -> owner.equals(inv.getArgument(0)) ? bob : null);
      stubViewsheetList(VS_ASSET_ID, "My Dashboards/Census", false, owner);

      var expected = service.resolve(request("task", List.of(renameViewsheetChange(
         VS_ASSET_ID, "My Dashboards/Census2", false, "bob:host-org"))), user);
      var plan = service.resolve(request("task", List.of(renameViewsheetChange(
         VS_ASSET_ID, "My Dashboards/Census2", false, ownerRef))), user);

      assertEquals(expected.changes().get(0).proposedValue(), plan.changes().get(0).proposedValue());
      assertEquals(expected.planHash(), plan.planHash());
      assertFalse(plan.changes().get(0).description().contains("WARNING"));
   }

   @Test void resolveRenameWarnsForAnOwnerTheProviderDoesNotKnow() throws Exception {
      when(ownerProvider.getUser(any())).thenReturn(null);
      stubViewsheetList(VS_ASSET_ID, "My Dashboards/Census", false,
                        new IdentityID("bob", "host-org"));

      var plan = service.resolve(request("task", List.of(renameViewsheetChange(
         VS_ASSET_ID, "My Dashboards/Census2", false, "bob"))), user);

      assertTrue(plan.changes().get(0).description().startsWith("WARNING: owner \"bob"),
                 plan.changes().get(0).description());
   }

   @Test void resolveRenameRefusesAnOwnerInAnUnknownOrg() throws Exception {
      when(ownerProvider.getOrganization("nope-org")).thenReturn(null);
      stubViewsheetList(VS_ASSET_ID, "My Dashboards/Census", false,
                        new IdentityID("bob", "host-org"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(renameViewsheetChange(
            VS_ASSET_ID, "My Dashboards/Census2", false, "bob:nope-org"))), user));
      assertTrue(ex.getMessage().contains("organization \"nope-org\" does not exist"),
                 ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // Bug #80355: a rename's landing path is claimed within one submission
   // -------------------------------------------------------------------------

   private static final String VS_ASSET_ID_2 =
      new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                     "Examples/Other", null, "host-org").toIdentifier();
   private static final String VS_ASSET_ID_OTHER_ORG =
      new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                     "Examples/Other", null, "other-org").toIdentifier();

   private void stubTwoViewsheets(String id2) throws Exception {
      SheetList list = new SheetList();
      list.getSheets().add(new Sheet(VS_ASSET_ID, "Examples/Census", "Examples/Census", true, null, null));
      list.getSheets().add(new Sheet(id2, "Examples/Other", "Examples/Other", true, null, null));
      lenient().when(viewsheetApiService.getViewsheets(eq(user))).thenReturn(list);
   }

   private void assertLandingRefused(ViewsheetChangeRequest... changes) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(changes)), user));
      assertTrue(ex.getMessage().contains("a path may appear once per submission"), ex.getMessage());
      assertTrue(ex.getMessage().contains(".newPath"), ex.getMessage());
   }

   @Test void twoViewsheetRenamesToOnePathAreRefused() throws Exception {
      stubTwoViewsheets(VS_ASSET_ID_2);
      assertLandingRefused(
         renameViewsheetChange(VS_ASSET_ID, "Examples/Target", true, null),
         renameViewsheetChange(VS_ASSET_ID_2, "Examples/Target", true, null));
   }

   @Test void viewsheetRenamesToDifferentPathsStillResolve() throws Exception {
      stubTwoViewsheets(VS_ASSET_ID_2);
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(
         renameViewsheetChange(VS_ASSET_ID, "Examples/Target", true, null),
         renameViewsheetChange(VS_ASSET_ID_2, "Examples/Target2", true, null))), user));
   }

   @Test void viewsheetRenamesToOnePathUnderDifferentScopeOrOrgStillResolve() throws Exception {
      // global vs the caller's own user tree
      stubTwoViewsheets(VS_ASSET_ID_2);
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(
         renameViewsheetChange(VS_ASSET_ID, "Examples/Target", true, null),
         renameViewsheetChange(VS_ASSET_ID_2, "Examples/Target", false, "bob:host-org"))), user));

      // same global path in another organization
      stubTwoViewsheets(VS_ASSET_ID_OTHER_ORG);
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(
         renameViewsheetChange(VS_ASSET_ID, "Examples/Target", true, null),
         renameViewsheetChange(VS_ASSET_ID_OTHER_ORG, "Examples/Target", true, null))), user));
   }

   @Test void twoWorksheetRenamesToOnePathAreRefusedButAViewsheetAtThatPathIsNot() throws Exception {
      String wsId = stubWorksheetList();
      String wsId2 = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                    "Examples/Sales2", null, "host-org").toIdentifier();
      SheetList list = new SheetList();
      list.getSheets().add(new Sheet(wsId, "Examples/Sales", "Examples/Sales", true, null, null));
      list.getSheets().add(new Sheet(wsId2, "Examples/Sales2", "Examples/Sales2", true, null, null));
      lenient().when(worksheetApiService.getWorksheets(any())).thenReturn(list);
      ViewsheetChangeRequest ws1 = renameViewsheetChange(wsId, "Examples/Target", true, null);
      ws1.setUnitType(ViewsheetChangeRequest.UNIT_WORKSHEET);
      ViewsheetChangeRequest ws2 = renameViewsheetChange(wsId2, "Examples/Target", true, null);
      ws2.setUnitType(ViewsheetChangeRequest.UNIT_WORKSHEET);
      assertLandingRefused(ws1, ws2);

      stubViewsheetList(VS_ASSET_ID, "Examples/Census", true, null);
      ViewsheetChangeRequest vs = renameViewsheetChange(VS_ASSET_ID, "Examples/Target", true, null);
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(ws1, vs)), user));
   }

   @Test void folderRenameThenCreateAtTheRenameTargetIsRefusedInBothOrders() throws Exception {
      stubFolder("Examples/Old Folder", null, true);
      stubFolder("Examples/New Folder", null, false);
      ViewsheetChangeRequest rename =
         renameFolderChange("Examples/Old Folder", "Examples/New Folder", null);
      ViewsheetChangeRequest create = createFolderChange("Examples", "New Folder", null);

      for(List<ViewsheetChangeRequest> order : List.of(List.of(rename, create), List.of(create, rename))) {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> service.resolve(request("task", order), user));
         assertTrue(ex.getMessage().contains("duplicate entry for folder"), ex.getMessage());
      }
   }

   @Test void twoFolderRenamesToOnePathAreRefused() throws Exception {
      stubFolder("Examples/A", null, true);
      stubFolder("Examples/B", null, true);
      stubFolder("Examples/G", null, false);
      assertLandingRefused(renameFolderChange("Examples/A", "Examples/G", null),
                           renameFolderChange("Examples/B", "Examples/G", null));
   }

   @Test void folderRenameOntoAnExistingFolderIsRefused() throws Exception {
      stubFolder("Examples/A", null, true);
      stubFolder("Examples/G", null, true);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(
            renameFolderChange("Examples/A", "Examples/G", null))), user));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
      assertTrue(ex.getMessage().contains(".newPath"), ex.getMessage());
   }

   @Test void folderRenameControlsStillResolve() throws Exception {
      stubFolder("Examples/A", null, true);
      stubFolder("Examples/B", null, true);
      stubFolder("Examples/G", null, false);
      stubFolder("Examples/H", null, false);
      stubFolder("Examples/Z", null, false);
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(
         renameFolderChange("Examples/A", "Examples/G", null),
         renameFolderChange("Examples/B", "Examples/H", null),
         createFolderChange("Examples", "Z", null))), user));
      // a rename to the same path is a no-op, not a collision with itself
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(
         renameFolderChange("Examples/A", "Examples/A", null))), user));
   }

   @Test void folderDeleteThenRenameOntoTheDeletedPathIsRefused() throws Exception {
      stubFolder("Examples/A", null, true);
      stubFolder("Examples/G", null, true);
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(
            deleteFolderChange("Examples/G", null),
            renameFolderChange("Examples/A", "Examples/G", null))), user));
      assertTrue(ex.getMessage().contains("already exists"), ex.getMessage());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubViewsheetList(String assetId, String path, boolean global, IdentityID owner)
      throws Exception
   {
      SheetList list = new SheetList();

      if(assetId != null) {
         list.getSheets().add(new Sheet(assetId, path, path, global, owner, null));
      }

      lenient().when(viewsheetApiService.getViewsheets(eq(user))).thenReturn(list);
   }

   private void stubFolder(String normalizedPath, IdentityID owner, boolean found) throws Exception {
      GetViewsheetFolderResult result = new GetViewsheetFolderResult(
         found, normalizedPath, ViewsheetFolderService.ownerKey(owner), null, null);
      lenient().when(folderService.getFolder(eq(normalizedPath), eq(owner))).thenReturn(result);
   }

   private static AssetEntry dependency(String path) {
      return new AssetEntry(AssetRepository.QUERY_SCOPE, AssetEntry.Type.QUERY, path, null,
                            "host-org");
   }

   private static AssetEntry containedViewsheet(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, path, null,
                            "host-org");
   }

   /** bug #76469: {@code resolveFolderDelete} now walks {@code assetRepository.getAllEntries} the
    * same way {@code resolveViewsheetDelete} walks {@code getSheetDependencies}. Unstubbed, Mockito
    * returns {@code null} for this call, which {@code findFolderContents} treats as empty -- so
    * tests that never call this helper still exercise the "empty folder" path by default. */
   private void stubFolderContents(AssetEntry[] contents) throws Exception {
      lenient().when(assetRepository.getAllEntries(any(), any(), any(), any())).thenReturn(contents);
   }

   private static ViewsheetChangePlanRequest request(String task, List<ViewsheetChangeRequest> changes) {
      ViewsheetChangePlanRequest req = new ViewsheetChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static ViewsheetChangeRequest deleteViewsheetChange(String assetId) {
      ViewsheetChangeRequest change = new ViewsheetChangeRequest();
      change.setUnitType(ViewsheetChangeRequest.UNIT_VIEWSHEET);
      change.setVerb(ViewsheetChangeRequest.VERB_DELETE);
      change.setAssetId(assetId);
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
