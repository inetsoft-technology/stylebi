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
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AssetContent;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.SelectionListVSAssembly;
import inetsoft.uql.viewsheet.TextVSAssembly;
import inetsoft.uql.viewsheet.TimeSliderVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.MessageException;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.controller.AssemblyImageService;
import inetsoft.web.viewsheet.service.VSExportService;
import inetsoft.web.wiz.model.WizFolderSaveResult;
import inetsoft.web.wiz.model.WizVisualizationRenameResult;
import inetsoft.web.wiz.model.WizVisualizationRenderEvent;
import inetsoft.web.wiz.model.WizVisualizationSaveEvent;
import inetsoft.web.wiz.model.WizVisualizationSaveResult;
import inetsoft.web.wiz.request.WizFolderCreateRequest;
import inetsoft.web.wiz.request.WizVisualizationRenameRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.awt.Point;
import java.security.Principal;
import java.util.List;

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
   void rejectsSourcePathOutsideManagedFolders() throws Exception {
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

   // ── saveVisualization carries forward filter-control assemblies (07-fix-r3.md) ───────────────
   //
   // WizVisualizationService.saveVisualization historically cloned ONLY the one named chart
   // assembly into the new single-assembly ViewSheet, so any sibling SelectionList/TimeSlider/
   // Calendar controls add_visualization_filters had placed below it on the source runtime were
   // silently dropped -- a saved visualization reopened with none of its controls. These three
   // tests pin: (a) the pre-existing single-chart save path is unchanged when there are no
   // controls; (b) 1-3 controls all survive, each at its original (unreset) position; (c) a
   // control's table binding survives clone().

   @Test
   void savingAChartWithNoFilterControlsCarriesOnlyTheChart() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(viewsheetService, assetRepository);

      AssetEntry insideEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      Viewsheet sourceVs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(sourceVs, "Chart1");
      chart.setPixelOffset(new Point(0, 0));
      chart.setPixelSize(new Dimension(400, 240));
      sourceVs.addAssembly(chart);

      WizVisualizationSaveEvent event = new WizVisualizationSaveEvent();
      event.setSourceViewsheetIdentifier(insideEntry.toIdentifier());
      event.setAssemblyName("Chart1");

      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin" + IdentityID.KEY_DELIMITER + "host-org");
      when(assetRepository.getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(sourceVs);

      service.saveVisualization(event, principal);

      var captor = ArgumentCaptor.forClass(Viewsheet.class);
      verify(viewsheetService).setViewsheet(
         captor.capture(), any(AssetEntry.class), eq(principal), eq(true), eq(true));
      Viewsheet newVs = captor.getValue();

      assertEquals(1, newVs.getAssemblies().length, "no filter controls on the source -> only the chart clone");
   }

   @Test
   void savingAChartWithThreeFilterControlsCarriesAllOfThemAtTheirOriginalPositions() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(viewsheetService, assetRepository);

      AssetEntry insideEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      Viewsheet sourceVs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(sourceVs, "Chart1");
      chart.setPixelOffset(new Point(0, 0));
      chart.setPixelSize(new Dimension(400, 240));
      sourceVs.addAssembly(chart);

      SelectionListVSAssembly selectionList = new SelectionListVSAssembly(sourceVs, "SelectionList1");
      selectionList.setPixelOffset(new Point(0, 250));
      selectionList.setPixelSize(new Dimension(100, 120));
      selectionList.setTableNames(List.of("SALES_FULL"));
      sourceVs.addAssembly(selectionList);

      TimeSliderVSAssembly timeSlider = new TimeSliderVSAssembly(sourceVs, "RangeSlider1");
      timeSlider.setPixelOffset(new Point(100, 250));
      timeSlider.setPixelSize(new Dimension(100, 120));
      timeSlider.setTableNames(List.of("SALES_FULL"));
      sourceVs.addAssembly(timeSlider);

      CalendarVSAssembly calendar = new CalendarVSAssembly(sourceVs, "Calendar1");
      calendar.setPixelOffset(new Point(200, 250));
      calendar.setPixelSize(new Dimension(100, 120));
      calendar.setTableNames(List.of("SALES_FULL"));
      sourceVs.addAssembly(calendar);

      WizVisualizationSaveEvent event = new WizVisualizationSaveEvent();
      event.setSourceViewsheetIdentifier(insideEntry.toIdentifier());
      event.setAssemblyName("Chart1");

      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin" + IdentityID.KEY_DELIMITER + "host-org");
      when(assetRepository.getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(sourceVs);

      service.saveVisualization(event, principal);

      var captor = ArgumentCaptor.forClass(Viewsheet.class);
      verify(viewsheetService).setViewsheet(
         captor.capture(), any(AssetEntry.class), eq(principal), eq(true), eq(true));
      Viewsheet newVs = captor.getValue();

      assertEquals(4, newVs.getAssemblies().length,
                   "chart + all 3 controls must be carried into the saved viewsheet");

      VSAssembly clonedSelectionList = (VSAssembly) newVs.getAssembly("SelectionList1");
      VSAssembly clonedTimeSlider = (VSAssembly) newVs.getAssembly("RangeSlider1");
      VSAssembly clonedCalendar = (VSAssembly) newVs.getAssembly("Calendar1");
      assertNotNull(clonedSelectionList, "SelectionList control must be present on the saved viewsheet");
      assertNotNull(clonedTimeSlider, "TimeSlider control must be present on the saved viewsheet");
      assertNotNull(clonedCalendar, "Calendar control must be present on the saved viewsheet");

      // Unlike the primary chart's clone (reset to (24,24)), a control's pixelOffset/pixelSize must
      // be preserved exactly -- it stays packed relative to the chart, never repositioned by save.
      assertEquals(new Point(0, 250), clonedSelectionList.getPixelOffset());
      assertEquals(new Dimension(100, 120), clonedSelectionList.getPixelSize());
      assertEquals(new Point(100, 250), clonedTimeSlider.getPixelOffset());
      assertEquals(new Point(200, 250), clonedCalendar.getPixelOffset());

      VSAssembly clonedChart = (VSAssembly) newVs.getAssembly("Chart1");
      assertEquals(new Point(24, 24), clonedChart.getPixelOffset(),
                   "the primary chart clone is still reset to the viewsheet's top-left, unlike its controls");
   }

   @Test
   void aCarriedOverFilterControlsTableBindingSurvivesClone() throws Exception {
      ViewsheetService viewsheetService = mock(ViewsheetService.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(viewsheetService, assetRepository);

      AssetEntry insideEntry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      Viewsheet sourceVs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(sourceVs, "Chart1");
      chart.setPixelOffset(new Point(0, 0));
      chart.setPixelSize(new Dimension(400, 240));
      sourceVs.addAssembly(chart);

      SelectionListVSAssembly selectionList = new SelectionListVSAssembly(sourceVs, "SelectionList1");
      selectionList.setPixelOffset(new Point(0, 250));
      selectionList.setPixelSize(new Dimension(100, 120));
      selectionList.setTableNames(List.of("SALES_FULL"));
      sourceVs.addAssembly(selectionList);

      WizVisualizationSaveEvent event = new WizVisualizationSaveEvent();
      event.setSourceViewsheetIdentifier(insideEntry.toIdentifier());
      event.setAssemblyName("Chart1");

      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin" + IdentityID.KEY_DELIMITER + "host-org");
      when(assetRepository.getSheet(
         any(AssetEntry.class), eq(principal), eq(true), any(AssetContent.class)))
         .thenReturn(sourceVs);

      service.saveVisualization(event, principal);

      var captor = ArgumentCaptor.forClass(Viewsheet.class);
      verify(viewsheetService).setViewsheet(
         captor.capture(), any(AssetEntry.class), eq(principal), eq(true), eq(true));
      Viewsheet newVs = captor.getValue();

      SelectionListVSAssembly clonedSelectionList =
         (SelectionListVSAssembly) newVs.getAssembly("SelectionList1");
      assertNotNull(clonedSelectionList);
      assertEquals(List.of("SALES_FULL"), clonedSelectionList.getTableNames(),
                   "the control's table binding must survive clone(), not just its geometry");
   }

   // ── createVisualizationFolder ─────────────────────────────────────────────────

   @Test
   void createFolderRejectsBlankName() throws Exception {
      WizVisualizationService service = createService(mock(ViewsheetService.class), mock(AssetRepository.class));

      assertThrows(IllegalArgumentException.class,
                   () -> service.createVisualizationFolder(
                      new WizFolderCreateRequest(null, "  "), mock(Principal.class)));
   }

   @Test
   void createFolderRejectsNameWithPathSeparator() throws Exception {
      WizVisualizationService service = createService(mock(ViewsheetService.class), mock(AssetRepository.class));

      assertThrows(IllegalArgumentException.class,
                   () -> service.createVisualizationFolder(
                      new WizFolderCreateRequest(null, "a/b"), mock(Principal.class)));
   }

   @Test
   void createFolderRejectsParentPathOutsideManagedFolder() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      assertThrows(IllegalArgumentException.class,
                   () -> service.createVisualizationFolder(
                      new WizFolderCreateRequest("some/unmanaged/folder", "Sales"), mock(Principal.class)));

      // The folder-scope check must happen before any asset is touched.
      verifyNoInteractions(assetRepository);
   }

   @Test
   void createFolderDefaultsBlankParentPathToTheManagedRoot() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(false);

      WizFolderSaveResult result = service.createVisualizationFolder(
         new WizFolderCreateRequest(null, "Sales"), mock(Principal.class));

      String expectedPath = WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/Sales";
      assertTrue(result.ok());
      assertEquals(expectedPath, result.path());
      verify(assetRepository).addFolder(
         argThat(e -> expectedPath.equals(e.getPath())), any(Principal.class));
   }

   @Test
   void createFolderChecksWritePermissionOnTheParent() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(false);

      String parentPath = WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/Region";
      Principal principal = mock(Principal.class);
      service.createVisualizationFolder(new WizFolderCreateRequest(parentPath, "Sales"), principal);

      verify(assetRepository).checkAssetPermission(
         eq(principal), argThat(e -> parentPath.equals(e.getPath())), eq(ResourceAction.WRITE));
   }

   @Test
   void createFolderConvertsPermissionDenialToSecurityException() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      // checkAssetPermission signals a WRITE denial via MessageException (see
      // AbstractAssetEngine#checkAssetPermission0) — the service must convert this to
      // SecurityException so it reaches WizVisualizationController#createFolder's
      // catch(SecurityException) branch (a real 403) instead of falling into catch(Exception)
      // as a misleading "unexpected error" 500.
      doThrow(new MessageException("Write access denied"))
         .when(assetRepository).checkAssetPermission(
            any(Principal.class), any(AssetEntry.class), eq(ResourceAction.WRITE));

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> service.createVisualizationFolder(
                      new WizFolderCreateRequest(null, "Sales"), mock(Principal.class)));

      verify(assetRepository, never()).addFolder(any(AssetEntry.class), any(Principal.class));
   }

   @Test
   void createFolderTreatsSlashParentPathAsRoot() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(false);

      // WizFolderCreateRequest.parentPath's javadoc documents "/" as meaning root, same as
      // null/empty — matching WizDatabaseController#normalizePath's handling of the same input.
      WizFolderSaveResult result = service.createVisualizationFolder(
         new WizFolderCreateRequest("/", "Sales"), mock(Principal.class));

      String expectedPath = WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/Sales";
      assertTrue(result.ok());
      assertEquals(expectedPath, result.path());
   }

   @Test
   void createFolderRejectsASiblingPathSharingOnlyAPrefix() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      // A path that merely starts with the same characters as the managed root (but is not
      // actually under it) must not pass the "under the visualization components folder" guard.
      String siblingPath = WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "-evil";

      assertThrows(IllegalArgumentException.class,
                   () -> service.createVisualizationFolder(
                      new WizFolderCreateRequest(siblingPath, "Sales"), mock(Principal.class)));

      verifyNoInteractions(assetRepository);
   }

   @Test
   void createFolderReturnsDuplicateNameWithoutCallingAddFolder() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(true);

      WizFolderSaveResult result = service.createVisualizationFolder(
         new WizFolderCreateRequest(null, "Sales"), mock(Principal.class));

      assertFalse(result.ok());
      assertEquals(WizFolderSaveResult.DUPLICATE_NAME, result.reason());
      verify(assetRepository, never()).addFolder(any(AssetEntry.class), any(Principal.class));
   }

   // ── renderVisualization: guard + permission branches ─────────────────────────
   //
   // The happy-path render (open → resolve assembly → renderAssemblyToImage → build result)
   // cannot be unit-tested here: it requires a live rendering engine (real RuntimeViewsheet
   // with a real VGraphPair) that this mocked-dependency harness does not provide. That path
   // is verified end-to-end after the image rebuild (Task A5). This test class covers only
   // the guard and permission-check branches, which are the logic this task owns.

   @Test
   void renderRejectsIdentifierOutsideManagedFolders() throws Exception {
      WizVisualizationService service = createService(mock(ViewsheetService.class), mock(AssetRepository.class));

      AssetEntry outside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "some/unmanaged/vs1", null);

      WizVisualizationRenderEvent ev = new WizVisualizationRenderEvent();
      ev.setIdentifier(outside.toIdentifier());

      assertThrows(IllegalArgumentException.class,
                   () -> service.renderVisualization(ev, mock(Principal.class)));
   }

   @Test
   void renderThrowsSecurityExceptionWhenPermissionDenied() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.VIEWSHEET), anyString(), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      WizVisualizationService service = createService(
         mock(ViewsheetService.class), mock(AssetRepository.class), securityEngine);

      // Build an identifier UNDER VISUALIZATION_COMPONENTS_FOLDER_PATH so the folder guard
      // passes and the permission check is actually reached.
      AssetEntry inside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      WizVisualizationRenderEvent ev = new WizVisualizationRenderEvent();
      ev.setIdentifier(inside.toIdentifier());

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> service.renderVisualization(ev, mock(Principal.class)));
   }

   // ── renameVisualization ─────────────────────────────────────────────────────

   @Test
   void renameRejectsBlankIdentifier() throws Exception {
      WizVisualizationService service = createService(mock(ViewsheetService.class), mock(AssetRepository.class));

      assertThrows(IllegalArgumentException.class,
                   () -> service.renameVisualization(
                      new WizVisualizationRenameRequest("", "New Name"), mock(Principal.class)));
   }

   @Test
   void renameRejectsBlankDisplayName() throws Exception {
      WizVisualizationService service = createService(mock(ViewsheetService.class), mock(AssetRepository.class));

      AssetEntry inside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      assertThrows(IllegalArgumentException.class,
                   () -> service.renameVisualization(
                      new WizVisualizationRenameRequest(inside.toIdentifier(), "  "), mock(Principal.class)));
   }

   @Test
   void renameRejectsIdentifierOutsideManagedFolder() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      AssetEntry outside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "some/unmanaged/vs1", null);

      assertThrows(IllegalArgumentException.class,
                   () -> service.renameVisualization(
                      new WizVisualizationRenameRequest(outside.toIdentifier(), "New Name"),
                      mock(Principal.class)));

      // The folder-scope check must happen before any asset is touched.
      verifyNoInteractions(assetRepository);
   }

   @Test
   void renameConvertsPermissionDenialToSecurityException() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      AssetEntry inside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);

      // checkAssetPermission signals a WRITE denial via MessageException — the service must
      // convert this to SecurityException so it reaches
      // WizVisualizationController#renameVisualization's catch(SecurityException) branch (a real
      // 403) instead of falling into catch(Exception) as a misleading "unexpected error" 500.
      doThrow(new MessageException("Write access denied"))
         .when(assetRepository).checkAssetPermission(
            any(Principal.class), any(AssetEntry.class), eq(ResourceAction.WRITE));

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> service.renameVisualization(
                      new WizVisualizationRenameRequest(inside.toIdentifier(), "New Name"),
                      mock(Principal.class)));

      verify(assetRepository, never()).changeSheet(
         any(AssetEntry.class), any(AssetEntry.class), any(Principal.class), anyBoolean());
   }

   @Test
   void renameSetsAliasAndPersistsAnAliasOnlyChangeSheet() throws Exception {
      AssetRepository assetRepository = mock(AssetRepository.class);
      WizVisualizationService service = createService(mock(ViewsheetService.class), assetRepository);

      AssetEntry inside = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         WizVisualizationService.VISUALIZATION_COMPONENTS_FOLDER_PATH + "/vs1", null);
      String identifier = inside.toIdentifier();
      Principal principal = mock(Principal.class);

      WizVisualizationRenameResult result = service.renameVisualization(
         new WizVisualizationRenameRequest(identifier, "  New Name  "), principal);

      // The identifier is unchanged by an alias-only rename — toIdentifier() is derived from
      // scope/type/user/path/orgID only, none of which this call touches.
      assertEquals(identifier, result.identifier());
      assertEquals("New Name", result.displayName());

      verify(assetRepository).checkAssetPermission(
         eq(principal), argThat(e -> identifier.equals(e.toIdentifier())), eq(ResourceAction.WRITE));

      // oentry/nentry compare equal (AssetEntry#equals ignores alias) regardless of whether
      // changeSheet is handed the same object or a copy — asserting on alias/path is what
      // actually matters here.
      verify(assetRepository).changeSheet(
         argThat(e -> "New Name".equals(e.getAlias()) && identifier.equals(e.toIdentifier())),
         argThat(e -> "New Name".equals(e.getAlias()) && identifier.equals(e.toIdentifier())),
         eq(principal), eq(true));
   }

   private static WizVisualizationService createService(ViewsheetService viewsheetService,
                                                          AssetRepository assetRepository)
      throws Exception
   {
      SecurityEngine defaultSecurityEngine = mock(SecurityEngine.class);
      when(defaultSecurityEngine.checkPermission(
         any(Principal.class), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(true);

      return createService(viewsheetService, assetRepository, defaultSecurityEngine);
   }

   private static WizVisualizationService createService(ViewsheetService viewsheetService,
                                                          AssetRepository assetRepository,
                                                          SecurityEngine securityEngine)
   {
      return new WizVisualizationService(
         viewsheetService, assetRepository,
         mock(AssemblyImageService.class),
         mock(BinaryTransferService.class),
         mock(VSExportService.class),
         securityEngine);
   }
}
