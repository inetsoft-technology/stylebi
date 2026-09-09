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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #76519 P6 review round 1, Finding 2 (BLOCKER) — {@code getViewsheetStructure} must check the
 * caller's READ permission on the viewsheet's RESOLVED BASE ENTRY (the worksheet, logical model,
 * or physical table it is actually bound to), not just on the viewsheet's own entry.
 *
 * <p>{@code assetRepository.getSheet(entry, principal, true, AssetContent.ALL)} only checks READ
 * on the viewsheet's own entry. For a real-worksheet base specifically, {@code Viewsheet.update()}
 * (the method that load reaches internally to populate {@code getBaseWorksheet()}) loads that
 * worksheet with {@code principal=null, permission=false} — its own READ is never checked. A
 * principal with READ on the viewsheet but explicitly denied READ on its underlying worksheet (a
 * legitimate, existing StyleBI configuration) could otherwise read the full worksheet structure
 * through this endpoint. This suite drives {@code getViewsheetStructure} with a mocked
 * {@link AssetRepository} to assert the explicit {@code checkAssetPermission} call this fix adds.
 */
@Tag("core")
class MetadataApiServiceViewsheetStructureSecurityTest {

   // AssetEntry.createAssetEntry(String) returns null for an identifier it can't parse (no '^'
   // and no "ws:" prefix — confirmed by reading AssetEntry.java's else-branch) — a plain "vs-1"
   // silently produces a null entry, which then makes assetRepository.getSheet(...) return the
   // mock's default (null) regardless of stubbing, since the ACTUAL argument passed is null. Use
   // the documented "ws:SCOPE:path" shorthand so the entry this test drives through is a real,
   // non-null AssetEntry, same as production always receives.
   private static final String VS_ID = "ws:global:vs-1";

   @Test
   void getViewsheetStructure_throwsAndSkipsBaseWorksheetReadWhenBasePermissionDenied() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      AssetTreeService assetTreeService = mock(AssetTreeService.class);
      ObjectMapper objectMapper = new ObjectMapper();
      XPrincipal principal = mock(XPrincipal.class);

      AssetEntry baseEntry = mock(AssetEntry.class); // the base worksheet's own entry
      Viewsheet viewsheet = mock(Viewsheet.class);
      Worksheet baseWorksheet = mock(Worksheet.class);

      doReturn(viewsheet).when(assetRepository)
         .getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class));
      when(viewsheet.getBaseEntry()).thenReturn(baseEntry);
      when(viewsheet.getBaseWorksheet()).thenReturn(baseWorksheet);

      // Denied: READ on the base entry (the worksheet/model/table the viewsheet is actually
      // bound to) throws, exactly what a real denial does (see AbstractAssetEngine.checkAssetPermission).
      doThrow(new MessageException("Access denied"))
         .when(assetRepository).checkAssetPermission(eq(principal), eq(baseEntry), eq(ResourceAction.READ));

      MetadataApiService service = new MetadataApiService(
         xrepository, dataSourceService, assetRepository, assetTreeService, objectMapper);

      MessageException ex = assertThrows(MessageException.class,
         () -> service.getViewsheetStructure(VS_ID, null, principal));

      assertEquals("Access denied", ex.getMessage());

      // The denial must fire BEFORE the base worksheet's structure is ever walked — a caller
      // denied READ on the base must never see any of its table/column/join/condition data.
      verify(assetRepository).checkAssetPermission(principal, baseEntry, ResourceAction.READ);
      verify(viewsheet, never()).getBaseWorksheet();
   }

   @Test
   void getViewsheetStructure_checksBasePermissionThenProceedsWhenGranted() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      AssetTreeService assetTreeService = mock(AssetTreeService.class);
      ObjectMapper objectMapper = new ObjectMapper();
      XPrincipal principal = mock(XPrincipal.class);

      AssetEntry baseEntry = mock(AssetEntry.class);
      Viewsheet viewsheet = mock(Viewsheet.class);

      doReturn(viewsheet).when(assetRepository)
         .getSheet(any(AssetEntry.class), any(), anyBoolean(), any(AssetContent.class));
      when(viewsheet.getBaseEntry()).thenReturn(baseEntry);
      // checkAssetPermission granted (default mock behavior: returns normally, no throw).

      // getBaseWorksheet() returns null past the permission check, so the method throws its own
      // "failed to load" exception — a distinctive marker proving execution reached PAST the
      // permission gate rather than being denied by it.
      when(viewsheet.getBaseWorksheet()).thenReturn(null);
      when(baseEntry.toView()).thenReturn("Sample Queries/accounts");

      MetadataApiService service = new MetadataApiService(
         xrepository, dataSourceService, assetRepository, assetTreeService, objectMapper);

      Exception ex = assertThrows(Exception.class,
         () -> service.getViewsheetStructure(VS_ID, null, principal));

      assertEquals("Viewsheet " + VS_ID + " has a base (\"Sample Queries/accounts\") that failed to load.",
         ex.getMessage());

      verify(assetRepository).checkAssetPermission(principal, baseEntry, ResourceAction.READ);
   }
}
