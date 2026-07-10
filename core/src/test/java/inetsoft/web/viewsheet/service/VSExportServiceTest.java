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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.FileFormatInfo;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core")
class VSExportServiceTest {
   @ParameterizedTest
   @ValueSource(strings = {"xlsx", "xls", "pptx", "ppt", "pdf", "vso", "html", "png", "csv",
                           "PDF", "Xlsx"})
   void supportedTypesAreAccepted(String ext) {
      assertTrue(VSExportService.isSupportedExportType(ext));
   }

   @ParameterizedTest
   @ValueSource(strings = {"excel", "data", "xml", "json", "svg", "txt", "powerpoint", ""})
   void unsupportedTypesAreRejected(String ext) {
      assertFalse(VSExportService.isSupportedExportType(ext));
   }

   @Test
   void nullTypeIsRejected() {
      assertFalse(VSExportService.isSupportedExportType(null));
   }

   @Test
   void getFormatNumberRejectsUnsupportedTypeWithoutNpe() {
      assertThrows(RuntimeException.class, () -> VSExportService.getFormatNumberFromExtension("excel"));
      assertThrows(RuntimeException.class, () -> VSExportService.getFormatNumberFromExtension(null));
   }

   // ── handleAttemptExportGloballyVisibleAsset(): host-org shared-dashboard export rules ──────
   // See permission-matrix-special.md "host-org 全局共享 Viewsheet 详细场景" -- 关联操作限制.
   // A non-host-org viewer's export request arrives with a placeholder AssetEntry (own org,
   // path under the "<host-org> Global Repository" marker folder) that doesn't literally exist;
   // the method resolves it to the real host-org entry. Normal formats redirect and proceed;
   // VSO snapshot format is explicitly denied even though the entry resolves successfully.

   private static final String VIEWER_ORG = "acme_id";

   @AfterEach
   void clearOrgContext() {
      OrganizationContextHolder.clear();
   }

   @Test
   void handleAttemptExportGloballyVisibleAsset_vsoSnapshot_deniedDespiteEntryResolving() throws Exception {
      VSExportService service = newServiceWithResolvableSharedEntries();
      AssetEntry placeholder = placeholderEntry();

      MessageException ex = assertThrows(MessageException.class, () ->
         service.handleAttemptExportGloballyVisibleAsset(placeholder,
                                                          FileFormatInfo.EXPORT_TYPE_SNAPSHOT),
         "VSO snapshot export of a host-org shared dashboard must be denied even though the " +
         "underlying real entry resolves successfully");
      assertNotNull(ex.getMessage());
   }

   @Test
   void handleAttemptExportGloballyVisibleAsset_pdfFormat_allowedAndRedirectsToRealHostOrgEntry()
      throws Exception
   {
      VSExportService service = newServiceWithResolvableSharedEntries();
      AssetEntry placeholder = placeholderEntry();

      AssetEntry result = service.handleAttemptExportGloballyVisibleAsset(placeholder,
                                                                          FileFormatInfo.EXPORT_TYPE_PDF);

      assertEquals(realHostOrgEntry(), result,
         "non-VSO export formats must be redirected to the real host-org entry, not denied");
   }

   private VSExportService newServiceWithResolvableSharedEntries() throws Exception {
      OrganizationContextHolder.setCurrentOrgId(VIEWER_ORG);

      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository repository = mock(AssetRepository.class);
      when(viewsheetService.getAssetRepository()).thenReturn(repository);

      // placeholder (viewer's own org) doesn't exist; the real host-org entry does.
      when(repository.containsEntry(placeholderEntry())).thenReturn(false);
      when(repository.containsEntry(realHostOrgEntry())).thenReturn(true);

      return new VSExportService(viewsheetService, mock(CoreLifecycleService.class),
                                 mock(ParameterService.class), mock(SecurityEngine.class),
                                 mock(XSessionService.class), mock(FileSystemService.class));
   }

   private AssetEntry placeholderEntry() {
      String defOrgFolder = OrganizationManager.getGlobalDefOrgFolderName();
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            defOrgFolder + "/Sales", null, VIEWER_ORG);
   }

   private AssetEntry realHostOrgEntry() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "Sales", null, Organization.getDefaultOrganizationID());
   }
}
