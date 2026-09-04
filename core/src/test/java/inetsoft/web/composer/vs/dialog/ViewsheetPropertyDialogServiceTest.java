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
package inetsoft.web.composer.vs.dialog;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.internal.DimensionD;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.ViewsheetInfo;
import inetsoft.uql.viewsheet.vslayout.*;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.composer.model.vs.*;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.service.CommandDispatcher;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.security.Principal;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@ExtendWith({MockitoExtension.class})
@Tag("core")
public class ViewsheetPropertyDialogServiceTest {
   @BeforeEach
   public void setup() throws Exception {
      service = new ViewsheetPropertyDialogService(coreLifecycleService, viewsheetService,
                                                   layoutService, viewsheetSettingsService,
                                                   vsAssemblyInfoHandler, null,
                                                   null, null);
   }

   // Bug #16756 Update layout info if it has same id as incoming layout
   @Test
   public void layoutIsUpdated() throws Exception {
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(viewsheetSandbox));
      ViewsheetInfo viewsheetInfo = new ViewsheetInfo();
      when(viewsheet.getViewsheetInfo()).thenReturn(viewsheetInfo);
      LayoutInfo layoutInfo = new LayoutInfo();
      List<ViewsheetLayout> viewsheetLayoutList = new ArrayList<>();
      ViewsheetLayout viewsheetLayout = new ViewsheetLayout();
      viewsheetLayout.setID("VSLayout001");
      List<VSAssemblyLayout> assemblyLayouts = new ArrayList<>();
      assemblyLayouts.add(new VSAssemblyLayout(
         "Bar001", new Point(0, 0), new Dimension(0, 0)));
      viewsheetLayout.setVSAssemblyLayouts(assemblyLayouts);
      viewsheetLayoutList.add(viewsheetLayout);
      layoutInfo.setViewsheetLayouts(viewsheetLayoutList);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      ScreensPaneModel screensPaneModel = model.screensPane();
      FiltersPaneModel filtersPaneModel = model.filtersPane();
      List<VSDeviceLayoutDialogModel> deviceLayouts = screensPaneModel.getDeviceLayouts();
      VSDeviceLayoutDialogModel deviceLayout = new VSDeviceLayoutDialogModel();
      deviceLayout.setId("VSLayout001");
      deviceLayout.setName("Foo001");
      deviceLayout.setSelectedDevices(new ArrayList<>());
      deviceLayouts.add(deviceLayout);
      model.vsOptionsPane().getViewsheetParametersDialogModel().setDisabledParameters(new String[0]);
      filtersPaneModel.setSharedFilters(new ArrayList<>());
      filtersPaneModel.setFilters(new ArrayList<>());
      screensPaneModel.setDevices(new ArrayList<>());

      if(model.localizationPane() != null) {
         model.localizationPane().setLocalized(new ArrayList<>());
      }

      service.setViewsheetInfo("Viewsheet1", model, null, commandDispatcher, null, null);
      viewsheetLayoutList = layoutInfo.getViewsheetLayouts();
      assertEquals(1, viewsheetLayoutList.size());
      viewsheetLayout = viewsheetLayoutList.get(0);
      assertEquals("VSLayout001", viewsheetLayout.getID());
      assertEquals("Foo001", viewsheetLayout.getName());
      assertNotNull(viewsheetLayout.getVSAssemblyLayout("Bar001"));
   }

   /**
    * Bug #76325 item 2: set_viewsheet_properties (SheetPropertyService.set) never patches the
    * print layout at all, but setViewsheetInfo unconditionally recomputes the print layout's
    * page size whenever one exists. A viewsheet whose print layout was persisted with a null
    * unit -- left over from before requireUsableUnits existed, or from any path that never runs
    * it -- must not take down an unrelated write. The persisted PrintInfo (read here via
    * oldPageSize) and the incoming dialog model are two independent objects: guarding only the
    * incoming model (as PrintDeviceLayoutPropertyService's own call-site guard does) would still
    * let this NPE through for a caller, like SheetPropertyService, that has no access to the
    * persisted LayoutInfo to repair it itself.
    */
   @Test
   public void aPersistedNullUnitsPrintLayoutIsRepairedRatherThanCrashingAnUnrelatedWrite()
      throws Exception
   {
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(viewsheetSandbox));
      ViewsheetInfo viewsheetInfo = new ViewsheetInfo();
      when(viewsheet.getViewsheetInfo()).thenReturn(viewsheetInfo);

      PrintInfo persistedInfo =
         new PrintInfo("Letter", new DimensionD(8.5, 11), 1f, 1f, 1f, 1f, null);
      PrintLayout persistedLayout = new PrintLayout();
      persistedLayout.setPrintInfo(persistedInfo);
      LayoutInfo layoutInfo = new LayoutInfo();
      layoutInfo.setPrintLayout(persistedLayout);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      ScreensPaneModel screensPaneModel = model.screensPane();
      VSPrintLayoutDialogModel vsPrintLayoutDialog = new VSPrintLayoutDialogModel();
      vsPrintLayoutDialog.setPaperSize("Letter");
      vsPrintLayoutDialog.setScaleFont(1.0f);
      screensPaneModel.setPrintLayout(vsPrintLayoutDialog);
      screensPaneModel.setDevices(new ArrayList<>());
      FiltersPaneModel filtersPaneModel = model.filtersPane();
      filtersPaneModel.setSharedFilters(new ArrayList<>());
      filtersPaneModel.setFilters(new ArrayList<>());
      model.vsOptionsPane().getViewsheetParametersDialogModel().setDisabledParameters(new String[0]);
      model.vsOptionsPane().setDesc("new description");

      if(model.localizationPane() != null) {
         model.localizationPane().setLocalized(new ArrayList<>());
      }

      // Must not throw -- this is the reported false 500.
      service.setViewsheetInfo("Viewsheet1", model, null, commandDispatcher, null, null);

      assertEquals("new description", viewsheetInfo.getDescription());
      assertEquals("inches", persistedInfo.getUnit());
   }

   /**
    * Write coordination (2026-08-17-write-coordination-design.md / -implementation.md): this
    * dialog has no defensive clone at all -- it mutates ViewsheetInfo live -- so the revision
    * check matters even more here than in dialogs that at least clone before patching. A stale
    * commit must be refused before the live ViewsheetInfo is even read.
    */
   @Test
   public void refusesAStaleCommitBeforeTouchingTheLiveInfo() throws Exception {
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getWriteRevision()).thenReturn(5);

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().revision(4).build();

      String result = service.setViewsheetInfo("Viewsheet1", model, null, commandDispatcher, null, null);

      org.junit.jupiter.api.Assertions.assertNull(result);
      org.mockito.Mockito.verify(rvs, org.mockito.Mockito.never()).getViewsheet();
      org.mockito.Mockito.verify(commandDispatcher).sendCommand(
         org.mockito.ArgumentMatchers.argThat(cmd ->
            cmd instanceof inetsoft.web.viewsheet.command.MessageCommand mc &&
            mc.getType() == inetsoft.web.viewsheet.command.MessageCommand.Type.ERROR));
   }

   @Test
   public void readSideAttachesTheCurrentWriteRevision() throws Exception {
      ViewsheetPropertyDialogService readService = new ViewsheetPropertyDialogService(
         coreLifecycleService, viewsheetService, layoutService, viewsheetSettingsService,
         vsAssemblyInfoHandler, securityEngine, null, deviceRegistry);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getWriteRevision()).thenReturn(7);
      when(viewsheet.getViewsheetInfo()).thenReturn(new ViewsheetInfo());
      when(viewsheet.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);
      when(viewsheet.getLayoutInfo()).thenReturn(new LayoutInfo());
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);
      when(deviceRegistry.getDevices()).thenReturn(new DeviceInfo[0]);

      ViewsheetPropertyDialogModel result = readService.getViewsheetInfo("Viewsheet1", null);

      assertEquals(7, result.revision());
   }

   /**
    * The reported bug: a print layout whose {@code PrintInfo} was never given a size (e.g.
    * persisted before {@link PrintInfo}'s no-arg constructor started defaulting one, or built via
    * the multi-arg constructor with a null {@code size}) must not 500 the read that every
    * {@code set_print_layout} call starts with -- it must read back with a sensible default
    * instead.
    */
   @Test
   public void readSideToleratesAPrintLayoutWithNoStoredSize() throws Exception {
      ViewsheetPropertyDialogService readService = new ViewsheetPropertyDialogService(
         coreLifecycleService, viewsheetService, layoutService, viewsheetSettingsService,
         vsAssemblyInfoHandler, securityEngine, null, deviceRegistry);

      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getViewsheetInfo()).thenReturn(new ViewsheetInfo());
      when(viewsheet.getAssemblies()).thenReturn(new inetsoft.uql.asset.Assembly[0]);

      PrintInfo persistedInfo = new PrintInfo("Letter", null, 1f, 1f, 1f, 1f, "inches");
      PrintLayout persistedLayout = new PrintLayout();
      persistedLayout.setPrintInfo(persistedInfo);
      LayoutInfo layoutInfo = new LayoutInfo();
      layoutInfo.setPrintLayout(persistedLayout);
      when(viewsheet.getLayoutInfo()).thenReturn(layoutInfo);
      when(viewsheetService.getAssetRepository()).thenReturn(assetRepository);
      when(deviceRegistry.getDevices()).thenReturn(new DeviceInfo[0]);

      // Must not throw -- this is the reported set_print_layout 500.
      ViewsheetPropertyDialogModel result = readService.getViewsheetInfo("Viewsheet1", null);

      assertEquals("Letter", result.screensPane().getPrintLayout().getPaperSize());
      assertEquals(8.5, result.screensPane().getPrintLayout().getCustomWidth(), 1e-9);
      assertEquals(11.0, result.screensPane().getPrintLayout().getCustomHeight(), 1e-9);
   }

   // ── snapGrid validation (parity audit L7) ───────────────────────────────────
   //
   // A non-positive or non-multiple-of-5 snapGrid divides into the Composer's own live canvas
   // drag math and produces NaN/Infinity assembly positions -- the one validation in this batch
   // with a confirmed corruption hazard if skipped. The accepting case is already covered by
   // layoutIsUpdated() above, which exercises the full write path at VSOptionsPaneModel's
   // default snapGrid of 20 (a legal multiple of 5) without hitting this check.

   @Test
   public void refusesAZeroSnapGrid() throws Exception {
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getViewsheetInfo()).thenReturn(new ViewsheetInfo());

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      model.vsOptionsPane().setSnapGrid(0);

      Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(
         IllegalArgumentException.class,
         () -> service.setViewsheetInfo("Viewsheet1", model, null, commandDispatcher, null, null));

      org.junit.jupiter.api.Assertions.assertTrue(thrown.getMessage().contains("snapGrid"));
   }

   @Test
   public void refusesASnapGridThatIsNotAMultipleOfFive() throws Exception {
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(viewsheet.getViewsheetInfo()).thenReturn(new ViewsheetInfo());

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      model.vsOptionsPane().setSnapGrid(7);

      Exception thrown = org.junit.jupiter.api.Assertions.assertThrows(
         IllegalArgumentException.class,
         () -> service.setViewsheetInfo("Viewsheet1", model, null, commandDispatcher, null, null));

      org.junit.jupiter.api.Assertions.assertTrue(thrown.getMessage().contains("snapGrid"));
   }

   /**
    * This service has no defensive clone (it mutates ViewsheetInfo live), so an illegal snapGrid
    * arriving alongside other legal fields must be caught before any of those other fields are
    * applied -- otherwise a refused patch would still leave a silent partial write behind.
    */
   @Test
   public void refusesASnapGridBeforeMutatingAnyOtherField() throws Exception {
      when(viewsheetService.getViewsheet(anyString(), nullable(Principal.class))).thenReturn(rvs);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      ViewsheetInfo info = new ViewsheetInfo();
      when(viewsheet.getViewsheetInfo()).thenReturn(info);

      ViewsheetPropertyDialogModel model = ViewsheetPropertyDialogModel.builder().build();
      model.vsOptionsPane().setSnapGrid(7);
      model.vsOptionsPane().setDesc("a distinctive description that must not be written");

      org.junit.jupiter.api.Assertions.assertThrows(
         IllegalArgumentException.class,
         () -> service.setViewsheetInfo("Viewsheet1", model, null, commandDispatcher, null, null));

      org.junit.jupiter.api.Assertions.assertNotEquals(
         "a distinctive description that must not be written", info.getDescription(),
         "snapGrid validation must run before other fields are mutated");
   }

   @Mock ViewsheetService viewsheetService;
   @Mock ViewsheetSettingsService viewsheetSettingsService;
   @Mock CoreLifecycleService coreLifecycleService;
   @Mock VSLayoutService layoutService;
   @Mock RuntimeViewsheet rvs;
   @Mock Viewsheet viewsheet;
   @Mock ViewsheetSandbox viewsheetSandbox;
   @Mock CommandDispatcher commandDispatcher;
   @Mock VSAssemblyInfoHandler vsAssemblyInfoHandler;
   @Mock inetsoft.sree.security.SecurityEngine securityEngine;
   @Mock DeviceRegistry deviceRegistry;
   @Mock inetsoft.uql.asset.AssetRepository assetRepository;
   private ViewsheetPropertyDialogService service;
}
