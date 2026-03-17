/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ViewsheetSandbox.applyParameterToInput().
 *
 * The method is private, so it is exercised via reflection. A minimal
 * AssetQuerySandbox carrying a controlled VariableTable is injected into the
 * sandbox via reflection to avoid the full viewsheet init cycle.
 */
@SreeHome
class ApplyParameterToInputTest {

   private ViewsheetSandbox sandbox;
   private Method applyMethod;
   private VariableTable variableTable;

   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();
      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);

      // Build a lightweight AssetQuerySandbox backed by our test VariableTable
      // and replace the field so applyParameterToInput() uses it.
      // Note: production uses CachedVariableTable (a trivial no-override subclass),
      // so substituting a plain VariableTable here produces identical behaviour for
      // all get/contains/put operations exercised by these tests.
      variableTable = new VariableTable();
      AssetQuerySandbox wbox = new AssetQuerySandbox(new Worksheet(), null, variableTable);

      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(sandbox, wbox);

      applyMethod = ViewsheetSandbox.class.getDeclaredMethod(
         "applyParameterToInput", InputVSAssembly.class);
      applyMethod.setAccessible(true);
   }

   private void invoke(InputVSAssembly assembly) throws Exception {
      applyMethod.invoke(sandbox, assembly);
   }

   // ── SingleInputVSAssembly ──────────────────────────────────────────────────

   @Test
   void singleInputGetsValueFromMatchingParameter() throws Exception {
      variableTable.put("TextInput1", "hello");
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("TextInput1");

      invoke(assembly);

      assertEquals("hello", assembly.getSelectedObject());
   }

   @Test
   void noActionWhenParameterNameDoesNotMatch() throws Exception {
      variableTable.put("OtherParam", "hello");
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("TextInput1");

      invoke(assembly);

      assertNull(assembly.getSelectedObject());
   }

   @Test
   void typeCoercionConvertsStringToInteger() throws Exception {
      variableTable.put("NumInput", "42");
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("NumInput");
      assembly.setDataType(XSchema.INTEGER);

      invoke(assembly);

      assertEquals(42, assembly.getSelectedObject());
   }

   @Test
   void arrayValueUnwrappedToFirstElementForSingleInput() throws Exception {
      variableTable.put("TextInput1", new Object[]{"first", "second"});
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("TextInput1");

      invoke(assembly);

      assertEquals("first", assembly.getSelectedObject());
   }

   @Test
   void nullParameterValuePreservesDesignTimeDefault() throws Exception {
      // A null value in the variable table must not clear the existing default.
      variableTable.put("TextInput1", null);
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("TextInput1");
      assembly.setSelectedObject("designDefault");

      invoke(assembly);

      assertEquals("designDefault", assembly.getSelectedObject());
   }

   @Test
   void emptyArrayPreservesDesignTimeDefaultForSingleInput() throws Exception {
      // VariableTable.put() normalises an empty Object[] to null before storage,
      // so vt.get() returns null here and the val == null early-return fires.
      // The design-time default must therefore be retained.
      variableTable.put("TextInput1", new Object[]{});
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("TextInput1");
      assembly.setSelectedObject("designDefault");

      invoke(assembly);

      assertEquals("designDefault", assembly.getSelectedObject());
   }

   @Test
   void secondInvocationForSameAssemblyIsDeduplicatedAndIgnored() throws Exception {
      // The first call should apply the parameter value; a subsequent call for the
      // same assembly (same absolute name) must be suppressed by the dedup set so
      // a later VT update does not overwrite the already-applied value.
      variableTable.put("TextInput1", "first");
      TextInputVSAssembly assembly = new TextInputVSAssembly();
      assembly.getVSAssemblyInfo().setName("TextInput1");

      invoke(assembly);
      assertEquals("first", assembly.getSelectedObject());

      variableTable.put("TextInput1", "second");
      invoke(assembly);

      assertEquals("first", assembly.getSelectedObject());
   }

   // ── CompositeInputVSAssembly ───────────────────────────────────────────────

   @Test
   void compositeInputGetsArrayValuesFromMatchingParameter() throws Exception {
      variableTable.put("CheckBox1", new Object[]{"A", "B"});
      CheckBoxVSAssembly assembly = new CheckBoxVSAssembly();
      assembly.getVSAssemblyInfo().setName("CheckBox1");

      invoke(assembly);

      assertArrayEquals(new Object[]{"A", "B"}, assembly.getSelectedObjects());
   }

   @Test
   void compositeInputWrapsScalarValueIntoArray() throws Exception {
      variableTable.put("CheckBox1", "solo");
      CheckBoxVSAssembly assembly = new CheckBoxVSAssembly();
      assembly.getVSAssemblyInfo().setName("CheckBox1");

      invoke(assembly);

      assertArrayEquals(new Object[]{"solo"}, assembly.getSelectedObjects());
   }

   // ── Variable-bound assembly (Bug #74184) ──────────────────────────────────
   // When an assembly is bound to a worksheet variable (tableName = "$(varName)"),
   // passParams sends the parameter under the variable name, not the assembly name.
   // applyParameterToInput must fall back to the variable name so the combobox is
   // seeded correctly when drilling down.

   @Test
   void variableBoundSingleInputGetsValueFromVariableName() throws Exception {
      variableTable.put("var1", "drillValue");
      ComboBoxVSAssembly assembly = new ComboBoxVSAssembly();
      assembly.getVSAssemblyInfo().setName("ComboBox1");
      assembly.setVariable(true);
      assembly.setTableName("$(var1)");

      invoke(assembly);

      assertEquals("drillValue", assembly.getSelectedObject());
   }

   @Test
   void variableBoundSingleInputAssemblyNameTakesPrecedenceOverVariableName() throws Exception {
      // If the parameter is also present under the assembly name, the assembly-name
      // match takes priority (existing behaviour for non-variable-bound assemblies).
      variableTable.put("ComboBox1", "byAssemblyName");
      variableTable.put("var1", "byVariableName");
      ComboBoxVSAssembly assembly = new ComboBoxVSAssembly();
      assembly.getVSAssemblyInfo().setName("ComboBox1");
      assembly.setVariable(true);
      assembly.setTableName("$(var1)");

      invoke(assembly);

      assertEquals("byAssemblyName", assembly.getSelectedObject());
   }

   @Test
   void emptyArrayPreservesDesignTimeDefaultForCompositeInput() throws Exception {
      // VariableTable.put() normalises empty Object[] to null → val == null guard fires.
      variableTable.put("CheckBox1", new Object[]{});
      CheckBoxVSAssembly assembly = new CheckBoxVSAssembly();
      assembly.getVSAssemblyInfo().setName("CheckBox1");
      assembly.setSelectedObjects(new Object[]{"designDefault"});

      invoke(assembly);

      assertArrayEquals(new Object[]{"designDefault"}, assembly.getSelectedObjects());
   }
}
