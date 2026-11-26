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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.script.AbstractChartArray;
import inetsoft.report.script.ChartArray;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.ChartInfo;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.util.script.graal.ScriptValueConverter;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression for #81786: reading an indexed element of a chart binding array
 * (bindingInfo.colors[0], ...) from a script must give JS {@code undefined} (as in
 * 13.8 / Rhino), not {@code null}. The Java contract of getArrayElement stays null, and
 * the named members and length are unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartArrayOutOfRangeScriptTest {
   @BeforeEach
   void setup() {
      ctx = Context.create("js");

      Viewsheet viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");
      ChartVSAssembly assembly = new ChartVSAssembly();
      assembly.getVSAssemblyInfo().setName("chart1");
      viewsheet.addAssembly(assembly);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);

      ChartVSAScriptable chart = new ChartVSAScriptable(box);
      chart.setAssembly("chart1");
      binding = new VSChartBindingScriptable(chart);
   }

   @AfterEach
   void teardown() {
      ctx.close();
   }

   private Value eval(Object array, String expr) {
      ctx.getBindings("js").putMember("a", ScriptValueConverter.toGuest(array));
      return ctx.eval("js", expr);
   }

   private void check(Object array) {
      assertTrue(eval(array, "a[0] === undefined").asBoolean(), "index 0");
      assertTrue(eval(array, "a[3] === undefined").asBoolean(), "index 3");
      assertTrue(eval(array, "a[-1] === undefined").asBoolean(), "negative index");
      assertTrue(eval(array, "a['nope'] === undefined").asBoolean(),
                 "unknown name stays undefined");
      assertEquals(0, eval(array, "a.length").asInt(), "length unchanged");
   }

   @Test
   void vsChartArrayIndexIsUndefined() {
      for(String property : new String[] { "colors", "lines", "sizes", "shapes", "textures" }) {
         Object array = binding.getMember(property);
         assertTrue(array instanceof AbstractChartArray, property);
         check(array);
      }
   }

   @Test
   void chartArrayIndexIsUndefined() {
      ChartArray array = new ChartArray("Frame", Object.class) {
         @Override
         public ChartInfo getInfo() {
            return mock(ChartInfo.class);
         }
      };
      check(array);
   }

   @Test
   void javaContractStillReturnsNull() {
      AbstractChartArray array = (AbstractChartArray) binding.getMember("colors");
      assertNull(array.getArrayElement(0));
      assertEquals(0, array.getArraySize());
   }

   private Context ctx;
   private VSChartBindingScriptable binding;
}
