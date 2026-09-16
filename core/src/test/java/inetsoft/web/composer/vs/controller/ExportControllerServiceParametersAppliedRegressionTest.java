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
package inetsoft.web.composer.vs.controller;

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DataSet;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.VSTableLens;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.AbstractVSExporter;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.util.XPortalHelper;
import inetsoft.web.service.BinaryTransferService;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;

/**
 * Redmine #76699 / VFO-017, mechanism 2 -- the same bug found (live, unpatched) in a second
 * production call site during PR #5299's review (see
 * {@code docs/teams/2026-09-16-bug-76699-vfo017-checkbox-clear-lag/06-review-mechanism2-r1.md}
 * finding 1). {@code ExportControllerService.writeViewsheetExport}'s "current view" branch
 * ({@code ExportControllerService.java:214-275} -- the Composer toolbar's manual "Export" action)
 * is a near-verbatim copy of {@code VSExportService.writeViewsheetExport}'s identical branch: the
 * same {@code synchronized(rvs) { rvs.setViewsheet(cviewsheet); try { ... } finally {
 * rvs.setViewsheet(originalViewsheet); } }} swap-restore shape, sharing the exact same defect --
 * {@code RuntimeViewsheet.setViewsheet(Viewsheet)} unconditionally forces
 * {@code ViewsheetSandbox.resetRuntime()}, clearing {@code ViewsheetSandbox.parametersApplied} and
 * re-arming {@code applyParameterToInput()} to reapply a stale {@code VariableTable} snapshot on
 * the very next {@code VSInputService.refreshVS0 -> reset(clist)} cascade. See
 * {@code docs/teams/2026-09-16-bug-76699-vfo017-checkbox-clear-lag/07-mechanism2-root-cause.md}
 * for the full root-cause trace (against {@code VSExportService}; the mechanism here is identical).
 *
 * <p>This test drives the real, private
 * {@code ExportControllerService.writeViewsheetExport(RuntimeViewsheet, VSExporter, ...)} method
 * (via reflection) with {@code current=true} -- the Composer toolbar's manual export of the
 * current view -- against the same fix pattern applied here:
 * {@code rbox.get().markParametersApplied()} added to the {@code finally} block, immediately
 * after the swap-restore. The {@code exporter} is a real, minimal {@code AbstractVSExporter}
 * subclass ({@link NoOpVSExporter}), not a bare mock, so that {@code AbstractVSExporter.export()}'s
 * own internal {@code rvs.setViewsheet(...)} swap-restore (a second, independent
 * {@code resetRuntime()} trigger) genuinely runs too -- this test exercises both triggers the fix
 * has to survive, not just the direct one.
 */
@WizAgentTestSupport
class ExportControllerServiceParametersAppliedRegressionTest {
   private static final String CHECKBOX = "CheckBox1";

   private Viewsheet vs;
   private CheckBoxVSAssembly checkBox;
   private CheckBoxVSAssemblyInfo info;
   private ViewsheetSandbox sandbox;
   private RuntimeViewsheet rvs;
   private VariableTable variableTable;
   private Method writeViewsheetExport;
   private Method refreshVariableMethod;

   @BeforeEach
   void setUp() throws Exception {
      vs = new Viewsheet();
      // Real base worksheet: ViewsheetSandbox.resetRuntime() discards wbox entirely when
      // getBaseWorksheet() is null (ViewsheetSandbox.java:406-407), which would defeat this test
      // regardless of the fix. Every real viewsheet has one.
      setPrivateField(vs, Viewsheet.class, "ws", new Worksheet());
      checkBox = new CheckBoxVSAssembly(vs, CHECKBOX);
      info = (CheckBoxVSAssemblyInfo) checkBox.getVSAssemblyInfo();
      vs.addAssembly(checkBox);

      AssetEntry entry = new AssetEntry(
         AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
         "test/ExportControllerServiceParametersAppliedRegressionTest", null,
         OrganizationManager.getInstance().getCurrentOrgID());

      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);

      variableTable = new VariableTable();
      AssetQuerySandbox wbox = new AssetQuerySandbox(new Worksheet(), null, variableTable);
      setPrivateField(sandbox, ViewsheetSandbox.class, "wbox", wbox);

      rvs = new RuntimeViewsheet();
      setPrivateField(rvs, RuntimeViewsheet.class, "box", sandbox);
      setPrivateField(rvs, RuntimeViewsheet.class, "vs", vs);
      setPrivateField(rvs, RuntimeSheet.class, "entry", entry);

      // Simulate a normal running viewsheet: the initial apply pass has already completed.
      setPrivateField(sandbox, ViewsheetSandbox.class, "parametersApplied", true);

      writeViewsheetExport = ExportControllerService.class.getDeclaredMethod(
         "writeViewsheetExport", RuntimeViewsheet.class, VSExporter.class, Principal.class,
         boolean.class, boolean.class, boolean.class, String[].class, boolean.class,
         boolean.class);
      writeViewsheetExport.setAccessible(true);

      refreshVariableMethod = ViewsheetSandbox.class.getDeclaredMethod(
         "refreshVariable", InputVSAssembly.class, boolean.class, ChangedAssemblyList.class);
      refreshVariableMethod.setAccessible(true);
   }

   private static void setPrivateField(Object target, Class<?> owner, String name, Object value)
      throws Exception
   {
      Field field = owner.getDeclaredField(name);
      field.setAccessible(true);
      field.set(target, value);
   }

   /** Mirrors what VSInputService.refreshVS0's box.get().reset(clist) ultimately reaches for a
    *  changed input assembly -- ViewsheetSandbox.reset(clist)'s 1-arg overload hardcodes
    *  initing=true, and that is what makes refreshVariable's guard consult parametersApplied. */
   private void refreshVariableAsAResetClistCallWould() throws Exception {
      refreshVariableMethod.invoke(sandbox, checkBox, true, new ChangedAssemblyList());
   }

   @Test
   void clearSurvivesAComposerToolbarExportOfTheCurrentView() throws Exception {
      ExportControllerService service = new ExportControllerService(
         mock(ViewsheetService.class), mock(CoreLifecycleService.class),
         mock(BinaryTransferService.class));

      // The user checks itemA; refreshVariable's own side effect keeps the VariableTable
      // echoing the assembly's current value (production: driven by
      // VSInputService.refreshVS0 -> reset -> refreshVariable).
      info.setSelectedObjects(new Object[]{ "itemA" });
      variableTable.put(CHECKBOX, new Object[]{ "itemA" });

      Principal principal = TestPrincipals.user("alice", "host-org");
      VSExporter exporter = new NoOpVSExporter();

      // The Composer toolbar's manual "Export" action, exporting the current view.
      writeViewsheetExport.invoke(service, rvs, exporter, principal, true, false, true,
         new String[0], false, false);

      // set_input_value([]) -- applySelection0's direct, synchronous write. This part is never
      // in question; it is refreshVS0's *own* refreshVariable call, right after, that is the bug.
      info.setSelectedObjects(new Object[0]);
      refreshVariableAsAResetClistCallWould();

      assertArrayEquals(new Object[0], info.getSelectedObjects(),
         "a clear that lands correctly must not be reverted by the next refreshVS0/reset(clist) " +
         "cascade just because a manual Composer-toolbar export of the current view ran first " +
         "(Redmine #76699 VFO-017 mechanism 2, PR #5299 review finding 1)");
   }

   /** A real, minimal AbstractVSExporter subclass -- every write* method is a no-op, since this
    *  test is about the swap-restore/parametersApplied bookkeeping around export(), not about
    *  rendering output. Needed instead of a bare VSExporter mock so that export()'s own real
    *  control flow (including its internal rvs.setViewsheet swap-restore) actually executes. */
   private static class NoOpVSExporter extends AbstractVSExporter {
      @Override
      public void write() {}
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
      protected void writeText(VSAssembly assembly, String txt) {}
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
      protected void writeTable(TableVSAssembly assembly, VSTableLens lens) {}
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
