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
package inetsoft.report.composition.execution;

import inetsoft.analytic.composition.event.VSEventUtil;
import inetsoft.mv.MVManager;
import inetsoft.report.composition.ChangedAssemblyList;
import inetsoft.sree.security.Organization;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VSUtil;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.security.Principal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78081: an input assembly with no explicit selection shows its first sorted value
 * (the automatic value). The init cycle (a cold open, or a Composer Refresh that empties
 * the variable table) must not read the value the input wrote into the variable table
 * itself back as a passed parameter and fix it as the selection. Real passed parameters
 * must still apply.
 *
 * <p>The ComboBox lists the embedded values [Sum, Average]; sorted ascending they show as
 * [Average, Sum], so the automatic value is "Average" while the first unsorted value,
 * which the bug fixed as the selection, is "Sum".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   InputAutoValueInitTest.TestConfig.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class InputAutoValueInitTest {
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

   private Principal savedPrincipal;
   private ViewsheetSandbox box;

   @BeforeEach
   void savePrincipal() {
      savedPrincipal = ThreadContext.getContextPrincipal();
   }

   @AfterEach
   void cleanup() {
      if(box != null) {
         box.dispose();
         box = null;
      }

      ThreadContext.setContextPrincipal(savedPrincipal);
   }

   // ── Untouched input: automatic value must survive init ─────────────────────

   @Test
   void freshOpenKeepsAutomaticSortedValue() throws Exception {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/fresh");

      openReset(vs);

      assertAutomatic(combo, "Average");
      assertEquals("Average", box.getVariableTable().get("ComboBox1"));
   }

   @Test
   void composerRefreshKeepsAutomaticSortedValue() throws Exception {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/refresh");

      // Composer render, then the property-dialog OK path (processChange + executeView).
      box.executeView("ComboBox1", true);
      ChangedAssemblyList clist = new ChangedAssemblyList();
      box.processChange("ComboBox1",
         VSAssembly.INPUT_DATA_CHANGED | VSAssembly.OUTPUT_DATA_CHANGED, clist);

      for(AssemblyEntry entry : clist.getViewList()) {
         box.executeView(entry.getAbsoluteName(), false);
      }

      assertAutomatic(combo, "Average");

      composerRefresh(vs);
      assertAutomatic(combo, "Average");
      assertEquals("Average", box.getVariableTable().get("ComboBox1"));

      composerRefresh(vs);
      assertAutomatic(combo, "Average");
   }

   @Test
   void boundTextComputesAutomaticValueOnOpenAndRefresh() throws Exception {
      Viewsheet vs = new Viewsheet();
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "T");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.INTEGER },
         new Object[][] { { "x" }, { 1 }, { 2 }, { 3 }, { 10 } }));
      ws.addAssembly(table);

      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      TextVSAssembly text = new TextVSAssembly(vs, "Text1");
      ScalarBindingInfo binding = new ScalarBindingInfo();
      binding.setTableName("T");
      binding.setColumnValue("x");
      binding.setColumnType(XSchema.INTEGER);
      binding.setAggregateValue("$(ComboBox1)");
      text.setScalarBindingInfo(binding);
      vs.addAssembly(text);
      box = createSandbox(vs, ws, "test/InputAutoValueInitTest/kpi");

      openReset(vs);
      // Average of 1, 2, 3, 10; the bug computed Sum (16).
      assertEquals(4.0, ((Number) box.getData("Text1")).doubleValue(), 0.0001);
      assertAutomatic(combo, "Average");

      composerRefresh(vs);
      // Average of 1, 2, 3, 10; the bug computed Sum (16).
      assertEquals(4.0, ((Number) box.getData("Text1")).doubleValue(), 0.0001);
      assertAutomatic(combo, "Average");
   }

   @Test
   void variableBoundInputWithoutParameterKeepsAutomaticValue() throws Exception {
      Viewsheet vs = new Viewsheet();
      Worksheet ws = createVariableWorksheet("v");
      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      combo.setVariable(true);
      combo.setTableName("$(v)");
      box = createSandbox(vs, ws, "test/InputAutoValueInitTest/varNoParam");

      openReset(vs);

      assertAutomatic(combo, "Average");
      assertEquals("Average", box.getVariableTable().get("v"));
   }

   // ── Guards: genuinely passed parameters still apply ────────────────────────

   @Test
   void passedParametersByAssemblyNameStillApply() throws Exception {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      CheckBoxVSAssembly checkBox = new CheckBoxVSAssembly(vs, "CheckBox1");
      ListData data = new ListData();
      data.setValues(new Object[] { "c", "a", "b" });
      data.setLabels(new String[] { "c", "a", "b" });
      checkBox.setListData(data);
      checkBox.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
      vs.addAssembly(checkBox);
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/param");

      // Hyperlink / URL parameters are in the table before the init reset.
      box.getVariableTable().put("ComboBox1", "Sum");
      box.getVariableTable().put("CheckBox1", new Object[] { "b" });
      openReset(vs);

      assertEquals("Sum", getExplicitSelection(combo));
      assertEquals("Sum", combo.getSelectedObject());
      assertArrayEquals(new Object[] { "b" }, checkBox.getSelectedObjects());
   }

   @Test
   void passedParameterByVariableNameStillApplies() throws Exception {
      Viewsheet vs = new Viewsheet();
      Worksheet ws = createVariableWorksheet("v");
      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      combo.setVariable(true);
      combo.setTableName("$(v)");
      box = createSandbox(vs, ws, "test/InputAutoValueInitTest/varParam");

      box.getVariableTable().put("v", "Sum");
      openReset(vs);

      assertEquals("Sum", getExplicitSelection(combo));
      assertEquals("Sum", combo.getSelectedObject());
      assertEquals("Sum", box.getVariableTable().get("v"));
   }

   @Test
   void drillParametersApplyToEveryInputType() throws Exception {
      // Feature #72693: drill-down "send selections as parameters" seeds same-named inputs.
      // The parameters are put into the table and the runtime reset before the init reset,
      // as CoreLifecycleService.handleOpenedSheet does. Each value differs from both the
      // first sorted and the first unsorted value, so it can only come from the parameter.
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly section = new ComboBoxVSAssembly(vs, "section");
      setEmbeddedValues(section, "z", "x", "y");
      vs.addAssembly(section);
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "Radio1");
      setEmbeddedValues(radio, "c", "a", "b");
      vs.addAssembly(radio);
      CheckBoxVSAssembly checkBox = new CheckBoxVSAssembly(vs, "CheckBox1");
      setEmbeddedValues(checkBox, "c", "a", "b");
      vs.addAssembly(checkBox);
      SliderVSAssembly slider = new SliderVSAssembly(vs, "Slider1");
      vs.addAssembly(slider);
      SpinnerVSAssembly spinner = new SpinnerVSAssembly(vs, "Spinner1");
      vs.addAssembly(spinner);
      TextInputVSAssembly textInput = new TextInputVSAssembly(vs, "TextInput1");
      vs.addAssembly(textInput);
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/drill");

      VariableTable vt = box.getVariableTable();
      vt.put("section", "y");
      vt.put("Radio1", "b");
      vt.put("CheckBox1", new Object[] { "a", "b" });
      vt.put("Slider1", "40");
      vt.put("Spinner1", 7);
      vt.put("TextInput1", "hello");
      box.resetRuntime();
      openReset(vs);

      assertEquals("y", section.getSelectedObject());
      assertEquals("b", radio.getSelectedObject());
      assertArrayEquals(new Object[] { "a", "b" }, checkBox.getSelectedObjects());
      assertEquals(40.0, ((Number) slider.getSelectedObject()).doubleValue(), 0.0001);
      assertEquals(7.0, ((Number) spinner.getSelectedObject()).doubleValue(), 0.0001);
      assertEquals("hello", textInput.getSelectedObject());
   }

   @Test
   void explicitSelectionSurvivesComposerRefresh() throws Exception {
      Viewsheet vs = new Viewsheet();
      ComboBoxVSAssembly combo = createCombo(vs, "ComboBox1");
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/explicit");
      openReset(vs);

      // the user picks the non-automatic value
      combo.setSelectedObject("Sum");
      box.processChange("ComboBox1",
         VSAssembly.INPUT_DATA_CHANGED | VSAssembly.OUTPUT_DATA_CHANGED, new ChangedAssemblyList());
      composerRefresh(vs);

      assertEquals("Sum", getExplicitSelection(combo));
      assertEquals("Sum", combo.getSelectedObject());
      assertEquals("Sum", box.getVariableTable().get("ComboBox1"));
   }

   // ── Inputs sharing one variable, and the first value of other list inputs ──

   @Test
   void siblingInputsBoundToSameVariableStartInSync() throws Exception {
      // Two untouched inputs on the same worksheet variable must agree with the variable
      // that drives the queries, on open and after a Composer Refresh.
      Viewsheet vs = new Viewsheet();
      Worksheet ws = createVariableWorksheet("v");
      ComboBoxVSAssembly combo1 = createCombo(vs, "ComboBox1");
      combo1.setVariable(true);
      combo1.setTableName("$(v)");
      ComboBoxVSAssembly combo2 = new ComboBoxVSAssembly(vs, "ComboBox2");
      setEmbeddedValues(combo2, "Sum", "Average", "Count");
      vs.addAssembly(combo2);
      combo2.setVariable(true);
      combo2.setTableName("$(v)");
      box = createSandbox(vs, ws, "test/InputAutoValueInitTest/sibling");

      openReset(vs);
      assertEquals("Average", combo1.getSelectedObject());
      assertEquals("Average", combo2.getSelectedObject());
      assertEquals("Average", box.getVariableTable().get("v"));

      composerRefresh(vs);
      assertEquals("Average", combo1.getSelectedObject());
      assertEquals("Average", combo2.getSelectedObject());
      assertEquals("Average", box.getVariableTable().get("v"));
   }

   @Test
   void untouchedRadioButtonRenderedOnlyStartsOnFirstSortedValue() throws Exception {
      // Reference: the view path alone (executeView sorts before validate) starts an
      // untouched radio on the first sorted value.
      Viewsheet vs = new Viewsheet();
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      setEmbeddedValues(radio, "c", "a", "b");
      vs.addAssembly(radio);
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/radioView");

      box.executeView("RadioButton1", true);

      assertEquals("a", radio.getSelectedObject());
   }

   @Test
   void untouchedRadioButtonOpenStartsOnFirstSortedValue() throws Exception {
      // The init reset (inputDataChanged path) must agree with the view path above and
      // with the variable, instead of fixing the first unsorted value.
      Viewsheet vs = new Viewsheet();
      RadioButtonVSAssembly radio = new RadioButtonVSAssembly(vs, "RadioButton1");
      setEmbeddedValues(radio, "c", "a", "b");
      vs.addAssembly(radio);
      box = createSandbox(vs, new Worksheet(), "test/InputAutoValueInitTest/radioOpen");

      openReset(vs);

      assertEquals("a", radio.getSelectedObject());
      assertEquals("a", box.getVariableTable().get("RadioButton1"));
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static void setEmbeddedValues(ListInputVSAssembly assembly, String... values) {
      ListData data = new ListData();
      data.setValues(values);
      data.setLabels(values.clone());
      assembly.setListData(data);
      assembly.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
   }

   private void openReset(Viewsheet vs) throws Exception {
      VSUtil.resetRuntimeValues(vs, false);
      box.reset(null, vs.getAssemblies(), new ChangedAssemblyList(), true, true, null);
   }

   /**
    * Mirrors the Composer canvas Refresh: openReturnedViewsheet and refreshViewsheet
    * (manualRefresh) each reset the runtime, refreshParameters(reset=true) empties the
    * variable table, and the sandbox is reset with initing=true.
    */
   private void composerRefresh(Viewsheet vs) throws Exception {
      box.resetRuntime();
      box.resetRuntime();
      VSEventUtil.refreshParameters(null, box, vs, true, null, new ArrayList<>());
      openReset(vs);
   }

   private static ComboBoxVSAssembly createCombo(Viewsheet vs, String name) {
      ComboBoxVSAssembly combo = new ComboBoxVSAssembly(vs, name);
      ListData data = new ListData();
      data.setValues(new Object[] { "Sum", "Average" });
      data.setLabels(new String[] { "Sum", "Average" });
      combo.setListData(data);
      combo.setSourceType(ListInputVSAssembly.EMBEDDED_SOURCE);
      vs.addAssembly(combo);
      return combo;
   }

   private static Worksheet createVariableWorksheet(String varName) {
      Worksheet ws = new Worksheet();
      DefaultVariableAssembly assembly = new DefaultVariableAssembly(ws, varName);
      AssetVariable var = new AssetVariable();
      var.setName(varName);
      assembly.setVariable(var);
      ws.addAssembly(assembly);
      return ws;
   }

   private static ViewsheetSandbox createSandbox(Viewsheet vs, Worksheet ws, String path)
      throws Exception
   {
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE,
         AssetEntry.Type.VIEWSHEET, path, null, Organization.getDefaultOrganizationID());
      vs.setEntry(entry);
      return new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, entry);
   }

   private static void assertAutomatic(ComboBoxVSAssembly combo, String expected)
      throws Exception
   {
      assertNull(getExplicitSelection(combo),
                 "the automatic value must not be fixed as an explicit selection");
      assertEquals(expected, combo.getSelectedObject());
   }

   private static Object getExplicitSelection(ComboBoxVSAssembly combo) throws Exception {
      Field field = ComboBoxVSAssemblyInfo.class.getDeclaredField("selectedObject");
      field.setAccessible(true);
      return field.get(combo.getVSAssemblyInfo());
   }
}
