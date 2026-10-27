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
import inetsoft.analytic.composition.event.InputScriptEvent;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.*;
import inetsoft.report.composition.execution.*;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XValueNode;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.TextVSAssemblyInfo;
import inetsoft.util.FileSystemService;
import inetsoft.util.ThreadContext;
import inetsoft.util.XPortalHelper;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.composer.vs.controller.VSLayoutService;
import inetsoft.web.viewsheet.model.VSObjectModelFactoryService;
import inetsoft.web.viewsheet.controller.VSRefreshService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.script.ScriptStateRefreshService;
import inetsoft.web.wiz.script.model.ScriptExecResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78877: the refresh {@code VSExportService.doExportViewsheet} and
 * {@code ExportControllerService} run on the live runtime viewsheet before an export of the
 * current view (with {@link ViewsheetSandbox#exportRefresh} set) reset the script-set runtime
 * values and reran onLoad without the input {@code event}, so a value an event-driven onLoad had
 * set was lost from the export and from the live sheet. Each test runs the real
 * {@link CoreLifecycleService#refreshViewsheet} with the flag set, as both callers do, and then
 * the real {@code VSExportService.writeViewsheetExport}, and checks the live sheet afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  CoreLifecycleServiceExportRefreshTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CoreLifecycleServiceExportRefreshTest {
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

   private static final String BUG =
      "if(event != null && event.name != null) { LastChangeLine.text = 'Last changed: ' + " +
      "event.name + ' (' + event.type + ')'; }\n" +
      "ControlText.text = 'ctl:' + (new Date()).getTime();";

   private Principal savedPrincipal;
   private final List<ViewsheetSandbox> boxes = new ArrayList<>();

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void cleanup() {
      boxes.forEach(ViewsheetSandbox::dispose);
      boxes.clear();
      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   /** The reported script: the value set only when the input event is present. */
   @Test
   void eventGuardedValueSurvivesExport() throws Exception {
      Fixture f = new Fixture(BUG, false);
      f.input();
      String expected = "Last changed: MeasurePicker (checkbox)";
      assertEquals(expected, f.live());

      for(int i = 1; i <= 2; i++) {
         List<String> exported = f.export();
         assertTrue(exported.contains("LastChangeLine=" + expected),
                    "export #" + i + " wrote " + exported);
         assertEquals(expected, f.live(), "live value after export #" + i);
      }
   }

   /** An onLoad that also writes when there is no event must not overwrite the live value. */
   @Test
   void elseBranchOnLoadDoesNotOverwriteLiveValue() throws Exception {
      Fixture f = new Fixture(
         "if(event != null && event.name != null) { LastChangeLine.text = 'Last changed: ' + " +
         "event.name; } else { LastChangeLine.text = 'no event'; }", false);
      f.input();
      assertEquals("Last changed: MeasurePicker", f.live());

      for(int i = 1; i <= 2; i++) {
         f.export();
         assertEquals("Last changed: MeasurePicker", f.live(), "live value after export #" + i);
      }
   }

   /** A non-idempotent onLoad must not advance by one step on every export. */
   @Test
   void nonIdempotentOnLoadDoesNotDriftOnExport() throws Exception {
      Fixture f = new Fixture("LastChangeLine.text = LastChangeLine.text + '+';", false);
      f.input();
      // once on open, once on the input
      assertEquals("none yet++", f.live());

      for(int i = 1; i <= 2; i++) {
         f.export();
         assertEquals("none yet++", f.live(), "live value after export #" + i);
      }
   }

   /** Other runtime values a script sets on the event (visibility) are kept too. */
   @Test
   void eventGuardedVisibilitySurvivesExport() throws Exception {
      Fixture f = new Fixture(
         "if(event != null) { LastChangeLine.visible = false; " +
         "LastChangeLine.text = 'hidden-by-event'; }", false);
      f.input();
      assertFalse(f.liveVisible());

      for(int i = 1; i <= 2; i++) {
         f.export();
         assertFalse(f.liveVisible(), "live visibility after export #" + i);
         assertEquals("hidden-by-event", f.live(), "live value after export #" + i);
      }
   }

   /**
    * With the live onLoad no longer rerun by the pre-export refresh, the onLoad that
    * {@code prepareForExport} runs on the exported clone is the only one in the export thread.
    * A query parameter it sets must still filter the exported table (#76780, #45263). The
    * parameter is removed from the live sandbox first, so only that onLoad can supply it.
    */
   @Test
   void onLoadParameterStillFiltersExportedTable() throws Exception {
      Fixture f = new Fixture("parameter.minId = 5;", true);
      assertEquals(List.of(6, 7, 8, 9, 10), ids(f.sandbox.getData("TableV")));
      f.sandbox.getVariableTable().remove("minId");

      f.export();
      assertEquals(List.of(6, 7, 8, 9, 10), f.exportedIds, "exported rows");
   }

   /** A refresh without the export flag (a user refresh) still resets and reruns onLoad. */
   @Test
   void userRefreshStillResetsRuntimeValues() throws Exception {
      Fixture f = new Fixture(BUG, false);
      f.input();
      f.export();
      assertEquals("Last changed: MeasurePicker (checkbox)", f.live());
      assertFalse(ViewsheetSandbox.exportRefresh.get(), "flag left set");

      f.refresh(false);
      assertEquals("none yet", f.live());
   }

   /**
    * Bug #79395: the refresh a wiz execute_script / run_script_live run triggers reset the
    * runtime values and reran onLoad without the event, wiping the value the event had set.
    */
   @Test
   void eventGuardedValueSurvivesScriptRefresh() throws Exception {
      Fixture f = new Fixture(BUG, false);
      f.input();
      String expected = "Last changed: MeasurePicker (checkbox)";

      for(int i = 1; i <= 2; i++) {
         f.scriptRefresh(() -> {});
         assertEquals(expected, f.live(), "live value after script refresh #" + i);
      }

      assertFalse(ViewsheetSandbox.keepRuntimeValuesRefresh.get(), "flag left set");
      assertEquals(expected, f.export().stream().filter(t -> t.startsWith("LastChangeLine="))
                      .findFirst().orElseThrow().substring("LastChangeLine=".length()));
   }

   /** A runtime value the executed script itself wrote is not in the design and must stay. */
   @Test
   void scriptOwnWriteSurvivesScriptRefresh() throws Exception {
      Fixture f = new Fixture(BUG, false);
      f.scriptRefresh(() -> ((TextVSAssemblyInfo) f.vs.getAssembly("LastChangeLine")
         .getVSAssemblyInfo()).setText("written by script"));
      assertEquals("written by script", f.live());
   }

   /** onLoad still reruns (without the event) so selection-dependent output can update. */
   @Test
   void onLoadStillRerunsOnScriptRefresh() throws Exception {
      Fixture f = new Fixture("LastChangeLine.text = LastChangeLine.text + '+';", false);
      f.input();
      assertEquals("none yet++", f.live());

      f.scriptRefresh(() -> {});
      assertEquals("none yet+++", f.live());
   }

   /** The pre-existing user refresh behaviour is unchanged when the keep flag is not set. */
   @Test
   void scriptRefreshFlagDoesNotLeakIntoUserRefresh() throws Exception {
      Fixture f = new Fixture(BUG, false);
      f.input();
      f.scriptRefresh(() -> {});
      f.refresh(false);
      assertEquals("none yet", f.live());
   }

   private final class Fixture {
      Fixture(String onLoad, boolean withTable) throws Exception {
         Worksheet ws = withTable ? worksheet() : new Worksheet();
         vs = new Viewsheet();
         set(vs, Viewsheet.class, "ws", ws);
         picker = new CheckBoxVSAssembly(vs, "MeasurePicker");
         vs.addAssembly(picker);
         TextVSAssembly last = new TextVSAssembly(vs, "LastChangeLine");
         ((TextVSAssemblyInfo) last.getVSAssemblyInfo()).setTextValue("none yet");
         vs.addAssembly(last);
         vs.addAssembly(new TextVSAssembly(vs, "ControlText"));

         if(withTable) {
            TableVSAssembly table = new TableVSAssembly(vs, "TableV");
            table.setSourceInfo(new SourceInfo(XSourceInfo.ASSET, null, "S"));
            ColumnSelection cols = new ColumnSelection();
            cols.addAttribute(new ColumnRef(new AttributeRef(null, "id")));
            table.setColumnSelection(cols);
            vs.addAssembly(table);
         }

         vs.getViewsheetInfo().setOnLoad(onLoad);
         AssetEntry entry = new AssetEntry(
            AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET, "test/Bug78877", null,
            OrganizationManager.getInstance().getCurrentOrgID());
         sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
         boxes.add(sandbox);
         set(sandbox, ViewsheetSandbox.class, "wbox",
             new AssetQuerySandbox(ws, null, new VariableTable()));
         rvs = new RuntimeViewsheet();
         set(rvs, RuntimeViewsheet.class, "box", sandbox);
         set(rvs, RuntimeViewsheet.class, "vs", vs);
         set(rvs, RuntimeSheet.class, "entry", entry);
         // the open-time onLoad, without an event
         sandbox.processOnLoadIf();
      }

      /** What set_input_value does: onLoad with the input event attached. */
      void input() throws Exception {
         sandbox.attachScriptEvent(new InputScriptEvent("MeasurePicker", picker));

         try {
            Method m = ViewsheetSandbox.class.getDeclaredMethod(
               "processOnLoad", ChangedAssemblyList.class, boolean.class);
            m.setAccessible(true);
            m.invoke(sandbox, new ChangedAssemblyList(), true);
         }
         finally {
            sandbox.detachScriptEvent();
         }
      }

      void refresh(boolean export) throws Exception {
         CommandDispatcher.withDummyDispatcher(principal, d -> {
            ViewsheetSandbox.exportRefresh.set(export);

            try {
               lifecycle.refreshViewsheet(rvs, rvs.getID(), null, d, false, true, true,
                                          new ChangedAssemblyList());
            }
            finally {
               ViewsheetSandbox.exportRefresh.set(false);
            }

            return null;
         });
      }

      /** What execute_script does: run, then the real post-script refresh. */
      void scriptRefresh(Runnable run) throws Exception {
         VSRefreshService refreshService = mock(VSRefreshService.class);
         doAnswer(inv -> {
            lifecycle.refreshViewsheet(rvs, rvs.getID(), null, inv.getArgument(3), false, true,
                                       true, new ChangedAssemblyList());
            return null;
         }).when(refreshService).refreshViewsheet(any(), any(), any(), any(), any());
         new ScriptStateRefreshService(refreshService).runAndRefresh(rvs, principal, "", () -> {
            run.run();
            return new ScriptExecResult(true, null, null, null, false, null, null, null);
         });
      }

      /** The pre-export refresh followed by the export of the current view. */
      List<String> export() throws Exception {
         refresh(true);
         VSExportService service = new VSExportService(
            mock(ViewsheetService.class), lifecycle, mock(ParameterService.class),
            mock(SecurityEngine.class), mock(XSessionService.class),
            mock(FileSystemService.class));
         Method w = VSExportService.class.getDeclaredMethod(
            "writeViewsheetExport", RuntimeViewsheet.class, VSExporter.class, Principal.class,
            boolean.class, boolean.class, boolean.class, String[].class, boolean.class,
            boolean.class);
         w.setAccessible(true);
         RecordingExporter exporter = new RecordingExporter();
         w.invoke(service, rvs, exporter, principal, true, false, true, new String[0], false,
                  false);
         exportedIds = exporter.ids;
         return exporter.texts;
      }

      String live() {
         return ((TextVSAssembly) rvs.getViewsheet().getAssembly("LastChangeLine")).getText();
      }

      boolean liveVisible() {
         return rvs.getViewsheet().getAssembly("LastChangeLine").getVSAssemblyInfo().isVisible();
      }

      final Viewsheet vs;
      final CheckBoxVSAssembly picker;
      final ViewsheetSandbox sandbox;
      final RuntimeViewsheet rvs;
      final Principal principal = TestPrincipals.user("alice", "host-org");
      final CoreLifecycleService lifecycle = new CoreLifecycleService(
         mock(VSObjectModelFactoryService.class), mock(ViewsheetService.class),
         mock(VSLayoutService.class), mock(ParameterService.class),
         mock(VSCompositionService.class), mock(DataRefModelFactoryService.class),
         null, e -> {}, mock(LicenseManager.class), mock(SecurityEngine.class),
         mock(Cluster.class), mock(DataSourceRegistry.class));
      List<Object> exportedIds;
   }

   /** S (id 1..10) with the condition id > $(minId), minId defaulting to 0. */
   private static Worksheet worksheet() {
      Worksheet ws = new Worksheet();
      Object[][] data = new Object[11][];
      data[0] = new Object[] { "id" };

      for(int i = 1; i <= 10; i++) {
         data[i] = new Object[] { i };
      }

      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(new String[] { XSchema.INTEGER }, data));
      ws.addAssembly(s);

      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(new UserVariable("minId"));
      ConditionList conds = new ConditionList();
      conds.append(new ConditionItem(s.getColumnSelection(false).getAttribute("id"), cond, 0));
      s.setPreConditionList(conds);

      DefaultVariableAssembly variable = new DefaultVariableAssembly(ws, "minId");
      AssetVariable var = new AssetVariable("minId");
      var.setValueNode(XValueNode.createValueNode(0, "minId"));
      variable.setVariable(var);
      ws.addAssembly(variable);
      return ws;
   }

   private static List<Object> ids(Object data) {
      assertInstanceOf(TableLens.class, data, "no table");
      TableLens t = (TableLens) data;
      List<Object> vals = new ArrayList<>();
      t.moreRows(0);

      for(int r = t.getHeaderRowCount(); t.moreRows(r); r++) {
         Object v = t.getObject(r, 0);
         vals.add(v instanceof Number n ? n.intValue() : v);
      }

      return vals;
   }

   private static void set(Object target, Class<?> owner, String name, Object value)
      throws Exception
   {
      Field f = owner.getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
   }

   /** Records the text and table rows the real export of the current view writes. */
   private static class RecordingExporter extends AbstractVSExporter {
      final List<String> texts = new ArrayList<>();
      List<Object> ids;

      @Override
      public void write() {}
      @Override
      protected void writeText(VSAssembly assembly, String txt) {
         texts.add(assembly.getName() + "=" + txt);
      }
      @Override
      protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {
         ids = ids(lens);
      }
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
