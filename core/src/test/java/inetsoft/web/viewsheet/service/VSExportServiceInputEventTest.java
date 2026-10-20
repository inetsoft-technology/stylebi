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
package inetsoft.web.viewsheet.service;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadContext;
import inetsoft.util.XPortalHelper;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.security.Principal;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78877 through the entries the wiz tools use: the value is set by the real
 * {@link VSInputService#singleApplySelection} that {@code set_input_value} calls (so onLoad sees
 * the input {@code event}), then the viewsheet is exported through the public
 * {@link VSExportService#exportViewsheet} with the arguments {@code get_viewsheet_image} and
 * {@code export_viewsheet} pass. The live Text must keep the event-set value, and the export must
 * write it. The real exporters need batik/POI from inetsoft-xml-formats, which core's tests don't
 * have, so the exporter factory returns a recording {@link AbstractVSExporter}; everything before
 * and after it (beginExport, the pre-export refresh, the clone swap, prepareForExport) is real.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  VSExportServiceInputEventTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSExportServiceInputEventTest {
   @Configuration
   static class TestConfig {
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }

      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   private static final String EXPECTED = "Last changed: MeasurePicker (checkbox)";

   private Principal savedPrincipal;
   private ViewsheetSandbox sandbox;

   @BeforeEach
   void setUp() throws Exception {
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      Worksheet ws = new Worksheet();
      Viewsheet vs = new Viewsheet();
      set(vs, Viewsheet.class, "ws", ws);
      vs.addAssembly(new CheckBoxVSAssembly(vs, "MeasurePicker"));
      TextVSAssembly last = new TextVSAssembly(vs, "LastChangeLine");
      ((TextVSAssemblyInfo) last.getVSAssemblyInfo()).setTextValue("none yet");
      vs.addAssembly(last);
      vs.getViewsheetInfo().setOnLoad(
         "if(event != null && event.name != null) { LastChangeLine.text = 'Last changed: ' + " +
         "event.name + ' (' + event.type + ')'; }");

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug78877Input", null,
         OrganizationManager.getInstance().getCurrentOrgID());
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
      set(sandbox, ViewsheetSandbox.class, "wbox",
          new AssetQuerySandbox(ws, null, new VariableTable()));
      rvs = new RuntimeViewsheet();
      set(rvs, RuntimeViewsheet.class, "box", sandbox);
      set(rvs, RuntimeViewsheet.class, "vs", vs);
      set(rvs, RuntimeSheet.class, "entry", entry);
      // what opening the viewsheet runs, without an event. onInit must run here: the first
      // processOnInit() saves the scope as initScope, which the export's clone swap restores
      // (resetRuntime), so an event attached at that time would come back in the export.
      sandbox.processOnInit();
      sandbox.processOnLoadIf();

      VSObjectService objects = mock(VSObjectService.class);
      when(objects.getRuntimeViewsheet(eq(VS_ID), any())).thenReturn(rvs);
      when(objects.createList(anyBoolean(), any(CommandDispatcher.class), any(), any()))
         .thenAnswer(inv -> new ChangedAssemblyList());
      inputs = new VSInputService(objects, lifecycle, mock(ViewsheetService.class), null, null,
                                  null, null, null, null, null);
      exports = new VSExportService(
         mock(ViewsheetService.class), lifecycle, mock(ParameterService.class),
         mock(SecurityEngine.class), mock(XSessionService.class), mock(FileSystemService.class));
   }

   @AfterEach
   void tearDown() {
      sandbox.dispose();
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   /** set_input_value, then get_viewsheet_image's export call, twice. */
   @Test
   void viewsheetImageExportKeepsEventSetText() throws Exception {
      setInput();
      assertEquals(EXPECTED, live());

      for(int i = 1; i <= 2; i++) {
         // ScriptImageService.exportViewsheetToPng
         List<String> texts = export(() -> exports.exportViewsheet(
            rvs, FileFormatInfo.EXPORT_TYPE_PNG, true, false, true, false, false, null, false,
            new ExportResponse(new ByteArrayOutputStream()), principal));
         assertTrue(texts.contains("LastChangeLine=" + EXPECTED), "image #" + i + " wrote " + texts);
         assertEquals(EXPECTED, live(), "live value after get_viewsheet_image #" + i);
      }
   }

   /** set_input_value, then export_viewsheet(PDF) with no target, twice. */
   @Test
   void viewsheetExportKeepsEventSetText() throws Exception {
      setInput();

      for(int i = 1; i <= 2; i++) {
         // ViewsheetAssemblyAgentController.export with only format given
         List<String> texts = export(() -> exports.exportViewsheet(
            rvs, FileFormatInfo.EXPORT_TYPE_PDF, true, false, true, false, false, new String[0],
            false, false, null, false, new ExportResponse(new ByteArrayOutputStream()),
            principal));
         assertTrue(texts.contains("LastChangeLine=" + EXPECTED),
                    "export #" + i + " wrote " + texts);
         assertEquals(EXPECTED, live(), "live value after export_viewsheet #" + i);
      }
   }

   private void setInput() throws Exception {
      CommandDispatcher.withDummyDispatcher(principal, d -> {
         inputs.singleApplySelection(VS_ID, "MeasurePicker", new Object[] { "Sales" },
                                     principal, d, null);
         return null;
      });
   }

   private List<String> export(ExportCall call) throws Exception {
      RecordingExporter exporter = new RecordingExporter();

      try(MockedStatic<AbstractVSExporter> factory =
             mockStatic(AbstractVSExporter.class, CALLS_REAL_METHODS))
      {
         factory.when(() -> AbstractVSExporter.getVSExporter(
            anyInt(), any(), any(), anyBoolean(), any())).thenReturn(exporter);
         call.run();
      }

      return exporter.texts;
   }

   private String live() {
      return ((TextVSAssembly) rvs.getViewsheet().getAssembly("LastChangeLine")).getText();
   }

   private static void set(Object target, Class<?> owner, String name, Object value)
      throws Exception
   {
      Field f = owner.getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
   }

   private interface ExportCall {
      void run() throws Exception;
   }

   private static final String VS_ID = "Bug78877Input";
   private final Principal principal = TestPrincipals.user("alice", "host-org");
   private final CoreLifecycleService lifecycle = new CoreLifecycleService(
      mock(VSObjectModelFactoryService.class), mock(ViewsheetService.class),
      mock(VSLayoutService.class), mock(ParameterService.class),
      mock(VSCompositionService.class), mock(DataRefModelFactoryService.class),
      null, e -> {}, mock(LicenseManager.class), mock(SecurityEngine.class),
      mock(Cluster.class), mock(DataSourceRegistry.class));
   private RuntimeViewsheet rvs;
   private VSInputService inputs;
   private VSExportService exports;

   /** Records the text the export of the current view writes. */
   private static class RecordingExporter extends AbstractVSExporter {
      final List<String> texts = new ArrayList<>();

      @Override
      public void write() {}
      @Override
      protected void writeText(VSAssembly assembly, String txt) {
         texts.add(assembly.getName() + "=" + txt);
      }
      @Override
      protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {}
      @Override
      protected void writeChart(ChartVSAssembly originalAsm, ChartVSAssembly asm, VGraph graph,
                                DataSet data, BufferedImage img, boolean firstTime,
                                boolean imgOnly) {}
      @Override
      protected void writeWarningText(Assembly[] assemblies, String warning,
                                      VSCompositeFormat format) {}
      @Override
      protected void writeImageAssembly(ImageVSAssembly assembly, XPortalHelper helper) {}
      @Override
      protected void writeTextInput(TextInputVSAssembly assembly) {}
      @Override
      protected void writeText(String text, Point pos, Dimension size,
                               VSCompositeFormat format) {}
      @Override
      protected void writeTimeSlider(TimeSliderVSAssembly assm) {}
      @Override
      protected void writeCalendar(CalendarVSAssembly assm) {}
      @Override
      protected void writeGauge(GaugeVSAssembly assembly) {}
      @Override
      protected void writeThermometer(ThermometerVSAssembly assembly) {}
      @Override
      protected void writeCylinder(CylinderVSAssembly assembly) {}
      @Override
      protected void writeSlidingScale(SlidingScaleVSAssembly assembly) {}
      @Override
      protected void writeRadioButton(RadioButtonVSAssembly assembly) {}
      @Override
      protected void writeCheckBox(CheckBoxVSAssembly assembly) {}
      @Override
      protected void writeSlider(SliderVSAssembly assembly) {}
      @Override
      protected void writeSpinner(SpinnerVSAssembly assembly) {}
      @Override
      protected void writeComboBox(ComboBoxVSAssembly assembly) {}
      @Override
      protected void writeSelectionList(SelectionListVSAssembly assembly) {}
      @Override
      protected void writeSelectionTree(SelectionTreeVSAssembly assembly) {}
      @Override
      protected void writeCrosstab(CrosstabVSAssembly assembly, VSTableLens lens) {}
      @Override
      protected void writeCalcTable(CalcTableVSAssembly assembly, VSTableLens lens) {}
      @Override
      protected void writeChart(ChartVSAssembly chartAsm, VGraph vgraph, DataSet data,
                                boolean imgOnly) {}
      @Override
      protected void writeVSTab(TabVSAssembly assembly) {}
      @Override
      protected void writeGroupContainer(GroupContainerVSAssembly assembly,
                                         XPortalHelper helper) {}
      @Override
      protected void writeShape(ShapeVSAssembly assembly) {}
      @Override
      protected void writeCurrentSelection(CurrentSelectionVSAssembly assembly) {}
      @Override
      protected void writeSubmit(SubmitVSAssembly assembly) {}
   }
}
