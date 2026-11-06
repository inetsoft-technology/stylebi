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

import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.AbstractSheet;
import inetsoft.uql.asset.DefaultVariableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80215: an input first refreshed while unbound leaves a variable-table entry under
 * its own name; once the input targets a worksheet variable $(v), that stale entry must not
 * shadow the value written under the variable name on the next runtime reset.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VariableTargetStaleKeyTest {
   private ViewsheetSandbox sandbox;
   private VariableTable vt;
   private Method refresh;

   @BeforeEach
   void setUp() throws Exception {
      Viewsheet vs = new Viewsheet();
      Worksheet ws = new Worksheet();
      ws.addAssembly(new DefaultVariableAssembly(ws, "v"));
      Field wsField = Viewsheet.class.getDeclaredField("ws");
      wsField.setAccessible(true);
      wsField.set(vs, ws);

      sandbox = new ViewsheetSandbox(vs, AbstractSheet.SHEET_RUNTIME_MODE, null, false, null);
      vt = new VariableTable();
      Field wboxField = ViewsheetSandbox.class.getDeclaredField("wbox");
      wboxField.setAccessible(true);
      wboxField.set(sandbox, new AssetQuerySandbox(ws, null, vt));

      refresh = ViewsheetSandbox.class.getDeclaredMethod(
         "refreshVariable", InputVSAssembly.class, boolean.class);
      refresh.setAccessible(true);
   }

   private void refresh(InputVSAssembly a, boolean initing) throws Exception {
      refresh.invoke(sandbox, a, initing);
   }

   // mirrors what ViewsheetSandbox.resetRuntime() does to the apply-once state
   private void resetRuntime() throws Exception {
      Field applied = ViewsheetSandbox.class.getDeclaredField("parametersApplied");
      applied.setAccessible(true);
      applied.setBoolean(sandbox, false);
      Field set = ViewsheetSandbox.class.getDeclaredField("parametersAppliedAssemblies");
      set.setAccessible(true);
      ((java.util.Set<?>) set.get(sandbox)).clear();
   }

   private void bind(InputVSAssembly a) {
      a.setVariable(true);
      a.setTableName("$(v)");
   }

   @Test
   void sliderSetWhileUnboundThenBoundKeepsNewValueAcrossReset() throws Exception {
      SliderVSAssembly a = new SliderVSAssembly();
      a.getVSAssemblyInfo().setName("Slider1");
      a.setDataType(XSchema.INTEGER);
      a.setSelectedObject(10);
      refresh(a, false);

      bind(a);
      a.setSelectedObject(33);
      refresh(a, false);
      resetRuntime();
      refresh(a, true);

      assertEquals(33, a.getSelectedObject());
      assertEquals(33, vt.get("v"));
   }

   @Test
   void spinnerSetWhileUnboundThenBoundKeepsNewValueAcrossReset() throws Exception {
      SpinnerVSAssembly a = new SpinnerVSAssembly();
      a.getVSAssemblyInfo().setName("Spinner1");
      a.setDataType(XSchema.INTEGER);
      a.setSelectedObject(10);
      refresh(a, false);

      bind(a);
      a.setSelectedObject(33);
      refresh(a, false);
      resetRuntime();
      refresh(a, true);

      assertEquals(33, a.getSelectedObject());
      assertEquals(33, vt.get("v"));
   }

   @Test
   void checkBoxSetWhileUnboundThenBoundKeepsNewValuesAcrossReset() throws Exception {
      CheckBoxVSAssembly a = new CheckBoxVSAssembly();
      a.getVSAssemblyInfo().setName("CheckBox1");
      a.setSelectedObjects(new Object[]{"A"});
      refresh(a, false);

      bind(a);
      a.setSelectedObjects(new Object[]{"B", "C"});
      refresh(a, false);
      resetRuntime();
      refresh(a, true);

      assertArrayEquals(new Object[]{"B", "C"}, a.getSelectedObjects());
   }

   @Test
   void boundFromStartKeepsValueAcrossReset() throws Exception {
      SliderVSAssembly a = new SliderVSAssembly();
      a.getVSAssemblyInfo().setName("Slider1");
      a.setDataType(XSchema.INTEGER);
      bind(a);
      refresh(a, false);

      a.setSelectedObject(33);
      refresh(a, false);
      resetRuntime();
      refresh(a, true);

      assertEquals(33, a.getSelectedObject());
   }

   @Test
   void ownNameParameterStillSeedsVariableBoundInputOnOpen() throws Exception {
      // hyperlink/URL parameter named after the input, no value under the variable name
      vt.put("Slider1", "20");
      SliderVSAssembly a = new SliderVSAssembly();
      a.getVSAssemblyInfo().setName("Slider1");
      a.setDataType(XSchema.INTEGER);
      bind(a);

      refresh(a, true);

      assertEquals(20, a.getSelectedObject());
      assertEquals(20, vt.get("v"));
   }

   @Test
   void unboundInputKeepsOwnNameEntry() throws Exception {
      SliderVSAssembly a = new SliderVSAssembly();
      a.getVSAssemblyInfo().setName("Slider1");
      a.setDataType(XSchema.INTEGER);
      a.setSelectedObject(10);

      refresh(a, false);

      assertEquals(10, vt.get("Slider1"));
   }
}
