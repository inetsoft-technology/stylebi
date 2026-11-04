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
import inetsoft.web.admin.content.repository.model.ExportedAssetsModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Bug #80194: apply re-resolves the plan, so a bookmarkResolutions entry that matches no bookmark
 * conflict must be refused before any backup or import is attempted.
 */
@Tag("core")
class AdminAssetImportApplyServiceBookmarkTest {
   @Test
   void applyRefusesUnmatchedBookmarkResolutionBeforeBackupOrImport() throws Exception {
      ImportAssetServiceProxy proxy = mock(ImportAssetServiceProxy.class);
      ExportedAssetsModel info = ExportedAssetsModel.builder().selectedEntities(List.of())
         .dependentAssets(List.of()).build();
      when(proxy.getJarFileInfo(anyString(), any())).thenReturn(info);
      when(proxy.getBookmarkConflicts(anyString(), isNull(), isNull(), isNull(), anyBoolean(),
         anyList(), any())).thenReturn(List.of());
      AdminBackupService backup = mock(AdminBackupService.class);
      RepositoryImportChangePlanService plan =
         new RepositoryImportChangePlanService(proxy, mock(AssetRepository.class));
      AdminAssetImportApplyService apply =
         new AdminAssetImportApplyService(plan, proxy, backup);

      RepositoryImportRequest.BookmarkResolutionEntry bad =
         new RepositoryImportRequest.BookmarkResolutionEntry();
      bad.setViewsheetPath("Nope/Nope");
      bad.setUser("nobody");
      bad.setBookmarkName("typo");
      bad.setKeepImported(false);

      RepositoryImportApplyRequest req = new RepositoryImportApplyRequest();
      req.setTask("t");
      req.setStagingToken("tok");
      req.setBookmarkResolutions(List.of(bad));
      req.setPlanHash("whatever");
      req.setReviewOutcome("ok");

      IllegalArgumentException e =
         assertThrows(IllegalArgumentException.class, () -> apply.apply(req, null));
      assertTrue(e.getMessage().startsWith("bookmarkResolutions[0]:"), e.getMessage());
      verifyNoInteractions(backup);
      verify(proxy, never()).importAsset(anyString(), any(), any(), any(), anyBoolean(),
         anyList(), anyBoolean(), anyBoolean(), any(), any());
   }
}
