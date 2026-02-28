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
   void allNullArrayElementsPreservesDesignTimeDefault() throws Exception {
      // VariableTable.put() converts an Object[]{null, null, ...} to a stored null value
      // (the key is present but the value is null). Verify the null-preservation path
      // is triggered the same way as an explicit scalar null.
      variableTable.put("TextInput1", new Object[]{null});
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
}
