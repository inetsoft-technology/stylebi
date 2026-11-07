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
package inetsoft.web.admin.ai.repository;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.Tool;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.StagedImportNotFoundException;
import inetsoft.web.admin.ai.TaskAuditToken;
import inetsoft.web.admin.content.repository.model.BookmarkConflict;
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import inetsoft.web.admin.content.repository.model.RequiredAssetModel;
import inetsoft.web.admin.content.repository.model.SelectedAssetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.withSettings;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@Tag("core")
class RepositoryImportChangePlanServiceTest {
   private static RequiredAssetModel asset(String name, int index) {
      return RequiredAssetModel.builder().name(name).index(index).lastModifiedTime(0L).build();
   }

   @Test
   void unknownIgnoreNameIsRefusedNamingItVerbatim() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(
            List.of(asset("Examples/Real", 0)), List.of("Examples/NoSuchDep")));
      assertTrue(e.getMessage().contains("Examples/NoSuchDep"), e.getMessage());
      assertTrue(e.getMessage().contains("Examples/Real"), e.getMessage());
   }

   @Test
   void unknownNameMixedWithRealNameIsRefused() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(
            List.of(asset("A", 0)), List.of("A", "Typo")));
   }

   @Test
   void blankNameDoesNotHideATypo() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(
            List.of(asset("A", 0)), List.of("")));
   }

   @Test
   void unknownNameRefusedWhenPackageHasNoDependents() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.ignoreListIndices(null, List.of("X")));
   }

   @Test
   void realNamesMapToIndicesAndDuplicatesAreHarmless() {
      assertEquals(List.of("3"), RepositoryImportChangePlanService.ignoreListIndices(
         List.of(asset("A", 1), asset("B", 3)), List.of("B", "B")));
   }

   @Test
   void emptyOrNullIgnoreListIgnoresNothing() {
      assertTrue(RepositoryImportChangePlanService.ignoreListIndices(
         List.of(asset("A", 1)), List.of()).isEmpty());
      assertTrue(RepositoryImportChangePlanService.ignoreListIndices(
         List.of(asset("A", 1)), null).isEmpty());
   }

   // ---- Bug #79937: an unknown/expired stagingToken must be a designed 400, not a 500 ----

   private static final String DESIGNED = "stagingToken: no staged import found for this token";

   private final ImportAssetServiceProxy importService = mock(ImportAssetServiceProxy.class);
   private final Principal principal = mock(Principal.class);
   private final RepositoryImportChangePlanService planService =
      new RepositoryImportChangePlanService(importService, mock(AssetRepository.class));

   private static RepositoryImportRequest request(String targetFolderPath) {
      RepositoryImportRequest req = new RepositoryImportRequest();
      req.setTask("t");
      req.setStagingToken("gone");
      req.setTargetFolderPath(targetFolderPath);
      return req;
   }

   private static StagedImportNotFoundException notFound() {
      return new StagedImportNotFoundException("Current session is missing the imported jar info.");
   }

   private void assertDesigned400(RepositoryImportRequest req) {
      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> planService.resolve(req, principal));
      assertTrue(e.getMessage().startsWith(DESIGNED), e.getMessage());
   }

   @Test
   void previewWithUnknownTokenIsADesigned400() throws Exception {
      when(importService.getJarFileInfo(eq("gone"), any())).thenThrow(notFound());
      assertDesigned400(request(null));
   }

   @Test
   void previewWithTargetFolderAndUnknownTokenIsADesigned400() throws Exception {
      when(importService.getJarFileInfo(eq("gone"), any())).thenThrow(notFound());
      assertDesigned400(request("/Some/Folder"));
   }

   @Test
   void unknownTokenWrappedByTheClusterProxyIsStillADesigned400() throws Exception {
      when(importService.getJarFileInfo(eq("gone"), any()))
         .thenThrow(new RuntimeException("proxy call failed", notFound()));
      assertDesigned400(request(null));
   }

   @Test
   void unrelatedIllegalStateIsNotMistakenForAnUnknownToken() throws Exception {
      when(importService.getJarFileInfo(eq("gone"), any()))
         .thenThrow(new IllegalStateException("boom"));
      assertThrows(IllegalStateException.class, () -> planService.resolve(request(null), principal));
   }

   @Test
   void applyWithUnknownTokenIsADesigned400BeforeAnythingIsWritten() throws Exception {
      when(importService.getJarFileInfo(eq("gone"), any())).thenThrow(notFound());
      AdminBackupService backup = mock(AdminBackupService.class);
      AdminAssetImportApplyService apply =
         new AdminAssetImportApplyService(planService, importService, backup);
      RepositoryImportApplyRequest req = new RepositoryImportApplyRequest();
      req.setTask("t");
      req.setStagingToken("gone");
      req.setPlanHash("h");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> apply.apply(req, principal));
      assertTrue(e.getMessage().startsWith(DESIGNED), e.getMessage());
      org.mockito.Mockito.verifyNoInteractions(backup);
   }

   // ---- Bug #80093: willOverwrite/willCreate follow the retargeted destination ----

   private static SelectedAssetModel worksheet(String path, String targetPath,
                                               IdentityID targetUser)
   {
      SelectedAssetModel.Builder b = SelectedAssetModel.builder().path(path).label(path)
         .type(RepositoryEntry.WORKSHEET).typeName("WORKSHEET").typeLabel("Worksheet");

      if(targetPath != null) {
         b.appliedTargetPath(targetPath).appliedTargetUser(targetUser);
      }

      return b.build();
   }

   private RepositoryImportPlan resolveWith(SelectedAssetModel model, AssetRepository repo)
      throws Exception
   {
      RepositoryImportChangePlanService svc = new RepositoryImportChangePlanService(importService, repo);
      when(importService.getJarFileInfo(eq("tok"), any())).thenReturn(
         ExportedAssetsModel.builder().selectedEntities(List.of(model))
            .dependentAssets(List.of()).build());
      RepositoryImportRequest req = request(null);
      req.setStagingToken("tok");

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, withSettings().strictness(
         Strictness.LENIENT).defaultAnswer(Answers.CALLS_REAL_METHODS)))
      {
         tool.when(() -> Tool.encryptPassword(anyString())).thenAnswer(inv -> "TKN:" + inv.getArgument(0));
         return svc.resolve(req, principal);
      }
   }

   private static AssetRepository repoHolding(int scope, String path, IdentityID user)
      throws Exception
   {
      AssetRepository repo = mock(AssetRepository.class);
      when(repo.getAssetEntry(any())).thenAnswer(inv -> {
         AssetEntry e = inv.getArgument(0);
         boolean match = e.getScope() == scope && e.getPath().equals(path) &&
            java.util.Objects.equals(e.getUser(), user);
         return match ? e : null;
      });
      return repo;
   }

   @Test
   void noRetargetUsesTheRecordedPath() throws Exception {
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", null, null),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "A/ws", null));
      assertEquals(1, plan.willOverwrite().size());
      assertTrue(plan.willCreate().isEmpty());
      assertTrue(plan.acknowledgeOverwriteRequired());
   }

   @Test
   void globalRetargetChecksTheDestinationNotTheOriginalPath() throws Exception {
      // only the ORIGINAL path exists, the import lands in Target/A/ws, so nothing is overwritten
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", "Target/A/ws", null),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "A/ws", null));
      assertTrue(plan.willOverwrite().isEmpty());
      assertEquals(1, plan.willCreate().size());
      assertFalse(plan.acknowledgeOverwriteRequired());
   }

   @Test
   void globalRetargetOntoAnExistingAssetIsAnOverwrite() throws Exception {
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", "Target/A/ws", null),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "Target/A/ws", null));
      assertEquals(1, plan.willOverwrite().size());
      assertTrue(plan.acknowledgeOverwriteRequired());
   }

   @Test
   void userScopeRetargetChecksTheTargetOwnersScope() throws Exception {
      IdentityID bob = new IdentityID("bob", "host-org");
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", "Target/A/ws", bob),
         repoHolding(AssetRepository.USER_SCOPE, "Target/A/ws", bob));
      assertEquals(1, plan.willOverwrite().size());

      RepositoryImportPlan other = resolveWith(worksheet("A/ws", "Target/A/ws", bob),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "Target/A/ws", null));
      assertEquals(1, other.willCreate().size());
   }

   // ---- Bug #80423: entries name the destination they were classified against ----

   @Test
   void noRetargetEntryNamesTheRecordedPath() throws Exception {
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", null, null),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "A/ws", null));
      assertEquals("A/ws", plan.willOverwrite().get(0).path());
      assertNull(plan.willOverwrite().get(0).user());
   }

   @Test
   void globalRetargetCreateEntryNamesTheDestination() throws Exception {
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", "Target/A/ws", null),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "A/ws", null));
      assertEquals("Target/A/ws", plan.willCreate().get(0).path());
      assertNull(plan.willCreate().get(0).user());
   }

   @Test
   void globalRetargetOverwriteEntryNamesTheDestination() throws Exception {
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", "Target/A/ws", null),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "Target/A/ws", null));
      assertEquals("Target/A/ws", plan.willOverwrite().get(0).path());
      assertNull(plan.willOverwrite().get(0).user());
   }

   @Test
   void nullMarkerUserIsReportedAsGlobal() throws Exception {
      IdentityID none = new IdentityID("__NULL__", "host-org");
      RepositoryImportPlan plan = resolveWith(worksheet("A/ws", "Target/A/ws", none),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "Target/A/ws", null));
      assertEquals("Target/A/ws", plan.willOverwrite().get(0).path());
      assertNull(plan.willOverwrite().get(0).user());
   }

   @Test
   void userRetargetEntryNamesTheDestinationPathAndUser() throws Exception {
      IdentityID bob = new IdentityID("bob", "host-org");
      RepositoryImportPlan over = resolveWith(worksheet("A/ws", "Target/A/ws", bob),
         repoHolding(AssetRepository.USER_SCOPE, "Target/A/ws", bob));
      assertEquals("Target/A/ws", over.willOverwrite().get(0).path());
      assertEquals(bob.convertToKey(), over.willOverwrite().get(0).user());

      RepositoryImportPlan created = resolveWith(worksheet("A/ws", "Target/A/ws", bob),
         repoHolding(AssetRepository.GLOBAL_SCOPE, "Target/A/ws", null));
      assertEquals("Target/A/ws", created.willCreate().get(0).path());
      assertEquals(bob.convertToKey(), created.willCreate().get(0).user());
   }

   @Test
   void planHashChangesWithTheRetargetedEntry() throws Exception {
      AssetRepository repo = repoHolding(AssetRepository.GLOBAL_SCOPE, "none", null);
      String plain = resolveWith(worksheet("A/ws", null, null), repo).planHash();
      String global = resolveWith(worksheet("A/ws", "Target/A/ws", null), repo).planHash();
      String user = resolveWith(
         worksheet("A/ws", "Target/A/ws", new IdentityID("bob", "host-org")), repo).planHash();
      assertNotEquals(plain, global);
      assertNotEquals(global, user);
   }

   private static BookmarkConflict conflict(String path, String user, String label, String name) {
      return BookmarkConflict.builder().viewsheetPath(path).user(user).userLabel(label)
         .bookmarkName(name).existingCreated(0L).existingModified(0L).importedCreated(0L)
         .importedModified(0L).build();
   }

   private static RepositoryImportRequest.BookmarkResolutionEntry res(
      String path, String user, String name, boolean keepImported)
   {
      RepositoryImportRequest.BookmarkResolutionEntry r =
         new RepositoryImportRequest.BookmarkResolutionEntry();
      r.setViewsheetPath(path);
      r.setUser(user);
      r.setBookmarkName(name);
      r.setKeepImported(keepImported);
      return r;
   }

   private static final BookmarkConflict REAL = conflict("Reports/Sales", "alice~;~host-org",
      "alice", "Q3");

   @Test
   void resolutionMatchingNoConflictIsRefusedNamingItsIndex() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("Nope/Nope", "nobody", "typo", false)), List.of(REAL)));
      assertTrue(e.getMessage().startsWith("bookmarkResolutions[0]:"), e.getMessage());
      assertTrue(e.getMessage().contains("Reports/Sales|alice~;~host-org|Q3"), e.getMessage());
   }

   @Test
   void matchingResolutionIsAcceptedForBothKeepValues() {
      RepositoryImportChangePlanService.validateBookmarkResolutions(
         List.of(res("Reports/Sales", "alice~;~host-org", "Q3", false)), List.of(REAL));
      RepositoryImportChangePlanService.validateBookmarkResolutions(
         List.of(res("Reports/Sales", "alice~;~host-org", "Q3", true)), List.of(REAL));
   }

   @Test
   void unmatchedKeepImportedTrueEntryIsRefusedToo() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("Reports/Sales", "alice~;~host-org", "Typo", true)), List.of(REAL)));
   }

   @Test
   void mixedListNamesOnlyTheBadIndex() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("Reports/Sales", "alice~;~host-org", "Q3", false),
               res("Reports/Sales", "alice~;~host-org", "Q4", false)), List.of(REAL)));
      assertTrue(e.getMessage().startsWith("bookmarkResolutions[1]:"), e.getMessage());
      assertFalse(e.getMessage().contains("bookmarkResolutions[0]"), e.getMessage());
   }

   @Test
   void anyEntryIsRefusedWhenThereAreNoConflicts() {
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("Reports/Sales", "alice~;~host-org", "Q3", false)), List.of()));
      assertTrue(e.getMessage().contains("none"), e.getMessage());
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("Reports/Sales", "alice~;~host-org", "Q3", false)), null));
   }

   @Test
   void userLabelInsteadOfKeyAndCaseDifferenceAreRefused() {
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("Reports/Sales", "alice", "Q3", false)), List.of(REAL)));
      assertThrows(IllegalArgumentException.class,
         () -> RepositoryImportChangePlanService.validateBookmarkResolutions(
            List.of(res("reports/sales", "alice~;~host-org", "q3", false)), List.of(REAL)));
   }

   @Test
   void noResolutionsNeverFails() {
      RepositoryImportChangePlanService.validateBookmarkResolutions(null, null);
      RepositoryImportChangePlanService.validateBookmarkResolutions(List.of(), List.of());
   }

   private static RepositoryImportRequest bookmarkRequest(
      RepositoryImportRequest.BookmarkResolutionEntry... entries)
   {
      RepositoryImportRequest req = new RepositoryImportRequest();
      req.setTask("t");
      req.setStagingToken("tok");
      req.setBookmarkResolutions(List.of(entries));
      return req;
   }

   private static RepositoryImportChangePlanService service(List<BookmarkConflict> conflicts)
      throws Exception
   {
      ImportAssetServiceProxy proxy = mock(ImportAssetServiceProxy.class);
      ExportedAssetsModel info = ExportedAssetsModel.builder().selectedEntities(List.of())
         .dependentAssets(List.of()).build();
      when(proxy.getJarFileInfo(anyString(), any())).thenReturn(info);
      when(proxy.getBookmarkConflicts(anyString(), isNull(), isNull(), isNull(), anyBoolean(),
         anyList(), any())).thenReturn(conflicts);
      return new RepositoryImportChangePlanService(proxy, mock(AssetRepository.class));
   }

   @Test
   void resolveRefusesUnmatchedResolutionAndKeepsHashForMatchingOnes() throws Exception {
      RepositoryImportChangePlanService svc = service(List.of(REAL));
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> svc.resolve(bookmarkRequest(res("Nope/Nope", "nobody", "typo", false)), null));
      assertTrue(e.getMessage().startsWith("bookmarkResolutions[0]:"), e.getMessage());

      try(MockedStatic<TaskAuditToken> token = mockStatic(TaskAuditToken.class)) {
         token.when(() -> TaskAuditToken.issue(anyString(), anyString())).thenReturn("tt");
         String matching = svc.resolve(
            bookmarkRequest(res("Reports/Sales", "alice~;~host-org", "Q3", false)), null).planHash();
         assertEquals(matching, svc.resolve(
            bookmarkRequest(res("Reports/Sales", "alice~;~host-org", "Q3", false)), null).planHash());
         assertNotEquals(matching, svc.resolve(bookmarkRequest(), null).planHash());
      }
   }
}
