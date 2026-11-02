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

import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.content.repository.ImportAssetServiceProxy;
import inetsoft.web.admin.content.repository.StagedImportNotFoundException;
import inetsoft.web.admin.content.repository.model.RequiredAssetModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
