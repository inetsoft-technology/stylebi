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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.sree.security.IdentityID;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.WizVisualizationSaveEvent;
import inetsoft.web.wiz.model.WizVisualizationSaveResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the security fix applied to
 * {@link WizVisualizationService#saveVisualization}:
 * <ol>
 *   <li>{@code sourceVsEntry.getPath()} must be restricted to the managed wiz folders
 *       ({@link WizVisualizationService#VISUALIZATION_ROOT_FOLDER_PATH} or
 *       {@link WizVisualizationService#VISUALIZATION_COMPONENTS_FOLDER_PATH}) before any asset
 *       is loaded.</li>
 *   <li>{@code assetRepository.getSheet(..., permission=true, ...)} must actually run the
 *       {@code checkAssetPermission} check (instead of {@code permission=false}, which silently
 *       skips it) and any resulting exception must propagate, not be swallowed.</li>
 * </ol>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizVisualizationServiceTest {
   @Test
   void rejectsSourcePathOutsideManagedFolders() {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(viewsheetService, assetRepository);

      AssetEntry outsideEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "some/unmanaged/folder/vs1", null);

      WizVisualizationSaveEvent event = new WizVisualizationSaveEvent();
      event.setSourceViewsheetIdentifier(outsideEntry.toIdentifier());
      event.setAssemblyName("Chart1");

      Principal principal = mock(Principal.class);

      assertThrows(IllegalArgumentException.class,
                   () -> service.saveVisualization(event, principal));

      // The folder-scope check must happen before any asset is loaded, so the asset
      // repository (and therefore checkAssetPermission) is never touched.
      verifyNoInteractions(assetRepository);
   }

   @Test
   void propagatesPermissionDenialFromGetSheet() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(viewsheetService, assetRepository);

      AssetEntry insideEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_ROOT_FOLDER_PATH + "/vs1", null);

      WizVisualizationSaveEvent event = new WizVisualizationSaveEvent();
      event.setSourceViewsheetIdentifier(insideEntry.toIdentifier());
      event.setAssemblyName("Chart1");

      Principal principal = mock(Principal.class);

      when(assetRepository.getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class)))
         .thenThrow(new MessageException("Permission denied"));

      // The exception raised by checkAssetPermission (via getSheet) must propagate out of
      // saveVisualization rather than being swallowed.
      assertThrows(MessageException.class,
                   () -> service.saveVisualization(event, principal));

      verify(assetRepository).getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class));
   }

   @Test
   void savesVisualizationWhenSourceInManagedFolderAndPermissionGranted() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(viewsheetService, assetRepository);

      AssetEntry insideEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      // Real (not mocked) source viewsheet + assembly so the full method body can execute:
      // getBaseEntry() is null, so saveWorksheet() short-circuits before needing a worksheet.
      Viewsheet sourceVs = new Viewsheet();
      TextVSAssembly assembly = new TextVSAssembly(sourceVs, "Text1");
      sourceVs.addAssembly(assembly);

      WizVisualizationSaveEvent event = new WizVisualizationSaveEvent();
      event.setSourceViewsheetIdentifier(insideEntry.toIdentifier());
      event.setAssemblyName("Text1");

      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin" + IdentityID.KEY_DELIMITER + "host-org");

      when(assetRepository.getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(sourceVs);

      WizVisualizationSaveResult result = service.saveVisualization(event, principal);

      assertNotNull(result);
      assertNotNull(result.getSavedViewsheetIdentifier());

      // permission=true is required so checkAssetPermission actually runs instead of being
      // silently skipped.
      verify(assetRepository).getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class));
      verify(viewsheetService).setViewsheet(
         any(Viewsheet.class), any(AssetEntry.class), eq(principal), eq(true), eq(true));
   }

   private static WizVisualizationService createService(ViewsheetService viewsheetService,
                                                          AssetRepository assetRepository)
   {
      return new WizVisualizationService(
         viewsheetService, assetRepository,
         mock(AssemblyImageService.class),
         mock(BinaryTransferService.class),
         mock(VSExportService.class));
   }
}
