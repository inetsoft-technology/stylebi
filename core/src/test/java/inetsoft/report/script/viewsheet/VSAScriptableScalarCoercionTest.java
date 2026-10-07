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
package inetsoft.report.script.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.*;
import inetsoft.uql.viewsheet.*;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #78000: an output or input assembly object used as a scalar in a viewsheet script
 * ({@code Text1 >= 10}, {@code Text1 - 0}, {@code '' + Text1}) coerces to the assembly's value,
 * as it did under Rhino in 1.1, instead of to its class@hash text (NaN as a number).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class VSAScriptableScalarCoercionTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetScope scope;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = sandbox.getViewsheet();

      addText(vs, "TxtNum", 50.0);
      addText(vs, "TxtStr", "50");
      GaugeVSAssembly gaugeNull = new GaugeVSAssembly(vs, "GaugeNull");
      gaugeNull.setValue(null);
      vs.addAssembly(gaugeNull);

      GaugeVSAssembly gauge = new GaugeVSAssembly(vs, "Gauge78");
      gauge.setValue(50.0);
      vs.addAssembly(gauge);

      SliderVSAssembly slider = new SliderVSAssembly(vs, "Slider78");
      slider.setMinValue("0");
      slider.setMaxValue("100");
      slider.setSelectedObject(30.0);
      vs.addAssembly(slider);

      CheckBoxVSAssembly checkBox = new CheckBoxVSAssembly(vs, "CheckBox78");
      vs.addAssembly(checkBox);

      CheckBoxVSAssembly checkNum = new CheckBoxVSAssembly(vs, "CheckNum78");
      vs.addAssembly(checkNum);

      // the scope picks up the assemblies when it is created
      scope = new ViewsheetScope(sandbox, false);

      // set the selections after the scope is created: a selection set on a new check box
      // before then is dropped (its list values are reset to empty, which filters it out)
      ((CheckBoxVSAssembly) vs.getAssembly("CheckBox78"))
         .setSelectedObjects(new Object[] { "a", "b" });
      ((CheckBoxVSAssembly) vs.getAssembly("CheckNum78"))
         .setSelectedObjects(new Object[] { 1.0, 2.5 });
   }

   @Test
   void textWithNumberValueCoercesToTheNumber() throws Exception {
      assertEquals(true, eval("TxtNum >= 10"));
      assertEquals(false, eval("TxtNum <= 0"));
      assertEquals(50.0, eval("TxtNum - 0"));
      assertEquals(true, eval("TxtNum == 50"));
      assertEquals("50", eval("'' + TxtNum"));
      assertEquals("50", eval("String(TxtNum)"));
      assertEquals("50", eval("`${TxtNum}`"));
      assertEquals(100.0, eval(
         "function sc(v) { if(v >= 10) return 100; if(v <= 0) return 0; " +
         "return (v - 0) / (10 - 0) * 100; } sc(TxtNum)"));
   }

   @Test
   void textWithStringValueCoercesToTheString() throws Exception {
      assertEquals(true, eval("TxtStr >= 10"));
      assertEquals(50.0, eval("TxtStr - 0"));
      assertEquals("50", eval("'' + TxtStr"));
      assertEquals("50", eval("String(TxtStr)"));
      assertEquals("50", eval("`${TxtStr}`"));
      assertEquals("501", eval("TxtStr + 1"));
   }

   /**
    * An output with no value (a text falls back to its static text, so a gauge is used).
    */
   @Test
   void outputWithNullValueCoercesToNull() throws Exception {
      assertNull(eval("GaugeNull.value"));
      assertEquals(false, eval("GaugeNull >= 10"));
      assertEquals(0.0, eval("GaugeNull - 0"));
      assertEquals("null", eval("'' + GaugeNull"));
      assertEquals("null", eval("String(GaugeNull)"));
      // the object itself is not null
      assertEquals(false, eval("GaugeNull == null"));
   }

   @Test
   void gaugeCoercesToItsValue() throws Exception {
      assertEquals(true, eval("Gauge78 >= 10"));
      assertEquals(50.0, eval("Gauge78 - 0"));
      assertEquals("50", eval("String(Gauge78)"));
   }

   @Test
   void sliderCoercesToItsSelectedObject() throws Exception {
      assertEquals(30.0, eval("Slider78.selectedObject"));
      assertEquals(true, eval("Slider78 >= 10"));
      assertEquals(60.0, eval("Slider78 * 2"));
      assertEquals("30", eval("'' + Slider78"));
      assertEquals("30", eval("`${Slider78}`"));
   }

   @Test
   void checkBoxCoercesToItsJoinedSelectedObjects() throws Exception {
      assertEquals("a,b", eval("'' + CheckBox78"));
      assertEquals("a,b", eval("String(CheckBox78)"));
      assertEquals("a,b", eval("`${CheckBox78}`"));
      assertEquals("1,2.5", eval("'' + CheckNum78"));
   }

   @Test
   void explicitValueReadIsUnchanged() throws Exception {
      assertEquals(50.0, eval("TxtNum.value"));
      assertEquals("50", eval("TxtStr.value"));
      assertEquals(50.0, eval("Gauge78.value"));
      assertEquals(51.0, eval("TxtNum.value + 1"));
   }

   @Test
   void coercionMembersAreNotListed() throws Exception {
      assertEquals(-1.0, eval("Object.keys(TxtNum).indexOf('valueOf')"));
      assertEquals(-1.0, eval("Object.keys(TxtNum).indexOf('toString')"));
      assertEquals(-1.0, eval("Object.keys(Slider78).indexOf('valueOf')"));
      assertFalse(scope.getVSAScriptable("TxtNum").getMemberKeys().length == 0);
   }

   /**
    * An assembly that is neither an output nor an input keeps the generic string form.
    */
   @Test
   void otherAssemblyIsUnchanged() throws Exception {
      Object str = eval("'' + TableView1");
      assertTrue(String.valueOf(str).endsWith("[TableView1]"), String.valueOf(str));
   }

   private Object eval(String script) throws Exception {
      return scope.execute(script, (VSAScriptable) null);
   }

   private static void addText(Viewsheet vs, String name, Object value) {
      TextVSAssembly text = new TextVSAssembly(vs, name);
      text.setValue(value);
      vs.addAssembly(text);
   }

   private static OpenViewsheetEvent createOpenViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId(ViewsheetScopeTest.ASSET_ID);
      event.setViewer(true);
      return event;
   }
}
