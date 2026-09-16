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
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.io.viewsheet.VSExporter;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.util.XSessionService;
import inetsoft.uql.viewsheet.CheckBoxVSAssembly;
import inetsoft.uql.viewsheet.InputVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CheckBoxVSAssemblyInfo;
import inetsoft.util.FileSystemService;
import inetsoft.web.wiz.pairing.TestPrincipals;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.Mockito.mock;

/**
 * Redmine #76699 / VFO-017, mechanism 2. {@code RuntimeViewsheet.setViewsheet(Viewsheet)} always
 * calls {@code ViewsheetSandbox.setViewsheet(vs, true)} ({@code RuntimeViewsheet.java:694}) -- it
 * has no way to request {@code resetRuntime=false}. {@code VSExportService}'s whole-viewsheet
 * "current view" export (the fallback {@code get_viewsheet_image} takes for a CheckBox, which
 * cannot be rendered in isolation) calls this method to temporarily swap the live
 * {@code Viewsheet} for a clone and back -- and so unconditionally forces
 * {@code ViewsheetSandbox.resetRuntime()} in the process, even though the export code
 * deliberately passes {@code resetRuntime=false} to the sandbox-level setter it also calls two
 * lines later. {@code resetRuntime()} clears {@code ViewsheetSandbox.parametersApplied}, the flag
 * that normally short-circuits {@code refreshVariable()}'s call to {@code applyParameterToInput()}
 * for the rest of a viewsheet's life (see {@code refreshVariable}'s own
 * {@code if(initing && wbox != null && !parametersApplied)} guard). Once cleared, the very next
 * {@code initing=true} refresh -- exactly what {@code VSInputService.refreshVS0}'s
 * {@code box.get().reset(clist)} call (the 1-arg overload, which hardcodes {@code initing=true})
 * triggers -- re-fires {@code applyParameterToInput()}, which reapplies whatever stale value is
 * still sitting in the sandbox's {@code VariableTable} for that assembly -- silently reverting a
 * selection change that had just been applied directly (as {@code set_input_value}'s
 * {@code applySelection0} does), in the same request. Confirmed live with a debugger attached to a
 * running StyleBI JVM; see
 * {@code docs/teams/2026-09-16-bug-76699-vfo017-checkbox-clear-lag/07-mechanism2-root-cause.md}
 * in the stylebi-wiz repo for the full trace.
 *
 * <p>This is a distinct, deeper-layer bug from the {@code cellValue}-snapshot mechanism fixed by
 * {@code 665836d68} (mechanism 1): that fix made {@code CheckBox.value} track the live getter
 * instead of a frozen literal, but does nothing to stop {@code applyParameterToInput} from
 * mutating the live getter's own backing field in the first place.
 *
 * <p>This test drives the real, private
 * {@code VSExportService.writeViewsheetExport(RuntimeViewsheet, VSExporter, ...)} method (via
 * reflection, since it is private) with {@code current=true} -- the exact method
 * {@code get_viewsheet_image}'s whole-viewsheet CheckBox fallback calls -- so it exercises the
 * actual production call site containing the fix (the {@code rbox.get().markParametersApplied()}
 * call added to the {@code finally} block after the clone swap-restore, the same idiom
 * {@code RuntimeViewsheet.restoreCheckpoint0()} already uses to protect the undo/redo restore path
 * for Bug #74220). The {@code VSExporter} itself is mocked -- this test is not about rendering
 * output, only about whether the swap-restore dance around it leaves the sandbox's
 * input-parameter-reapplication latch where it needs to be.
 */
@WizAgentTestSupport
class VSExportServiceParametersAppliedRegressionTest {
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
         "test/VSExportServiceParametersAppliedRegressionTest", null,
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

      writeViewsheetExport = VSExportService.class.getDeclaredMethod(
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
   void clearSurvivesAGetViewsheetImageExportOfTheCurrentView() throws Exception {
      VSExportService service = new VSExportService(
         mock(ViewsheetService.class), mock(CoreLifecycleService.class),
         mock(ParameterService.class), mock(SecurityEngine.class), mock(XSessionService.class),
         mock(FileSystemService.class));

      // The user checks itemA; refreshVariable's own side effect keeps the VariableTable
      // echoing the assembly's current value (production: driven by
      // VSInputService.refreshVS0 -> reset -> refreshVariable).
      info.setSelectedObjects(new Object[]{ "itemA" });
      variableTable.put(CHECKBOX, new Object[]{ "itemA" });

      Principal principal = TestPrincipals.user("alice", "host-org");
      VSExporter exporter = mock(VSExporter.class);

      // get_viewsheet_image's whole-viewsheet CheckBox fallback: a "current view" export.
      writeViewsheetExport.invoke(service, rvs, exporter, principal, true, false, true,
         new String[0], false, false);

      // set_input_value([]) -- applySelection0's direct, synchronous write. This part is never
      // in question; it is refreshVS0's *own* refreshVariable call, right after, that is the bug.
      info.setSelectedObjects(new Object[0]);
      refreshVariableAsAResetClistCallWould();

      assertArrayEquals(new Object[0], info.getSelectedObjects(),
         "a clear that lands correctly must not be reverted by the next refreshVS0/reset(clist) " +
         "cascade just because get_viewsheet_image's whole-viewsheet export ran first " +
         "(Redmine #76699 VFO-017 mechanism 2)");
   }
}
