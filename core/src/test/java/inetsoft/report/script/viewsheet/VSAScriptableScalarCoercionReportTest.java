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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #78000: the reporter's dashboard script, which passes a text assembly object (not its
 * {@code .value}) to a scaling function, scores it from the assembly's value as on 1.1; an
 * input with no selection coerces to null; explicit {@code .value}/{@code .toString()} calls
 * on an assembly still work.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome(importResources = "ViewsheetScopeTest.vso")
@Tag("core")
@Tag("integration")
class VSAScriptableScalarCoercionReportTest {
   @RegisterExtension
   RuntimeViewsheetExtension viewsheetResource =
      new RuntimeViewsheetExtension(createOpenViewsheetEvent());

   private ViewsheetScope scope;

   @BeforeEach
   void setUp() {
      RuntimeViewsheet rvs = viewsheetResource.getRuntimeViewsheet();
      ViewsheetSandbox sandbox = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = sandbox.getViewsheet();

      addText(vs, "txtRegisterIncrease1M", 86.0);
      addText(vs, "txtRegisterIncrease12M", 50.0);
      addText(vs, "txtRegisterIncrease12MStr", "50");
      vs.addAssembly(new TextInputVSAssembly(vs, "TextInputEmpty"));
      vs.addAssembly(new ComboBoxVSAssembly(vs, "ComboEmpty"));
      scope = new ViewsheetScope(sandbox, false);
   }

   /**
    * The reporter's getPercentage, called with the assembly object as v12: the object reads
    * as its value 50, which is at or above maxScale 10, so it scores 100 (2.5 → 5.0 on the
    * dashboard).
    */
   @Test
   void reporterScriptScoresTheAssemblyFromItsValue() throws Exception {
      assertEquals(List.of(100.0, 100.0),
                   toList(eval(GET_PERCENTAGE + "getPercentage(txtRegisterIncrease1M.value, " +
                               "txtRegisterIncrease12M, 0, 10)")));
      assertEquals(List.of(100.0, 100.0),
                   toList(eval(GET_PERCENTAGE + "getPercentage(txtRegisterIncrease1M.value, " +
                               "txtRegisterIncrease12MStr, 0, 10)")));
      assertEquals(5.0, eval(GET_PERCENTAGE + "var arr = getPercentage(" +
                             "txtRegisterIncrease1M.value, txtRegisterIncrease12M, 0, 10); " +
                             "(100 + arr[1]) / 2 / 20"));
   }

   /**
    * An input with no selection coerces to null: compares as 0, concatenates as "null".
    */
   @Test
   void inputWithNoSelectionCoercesToNull() throws Exception {
      for(String name : new String[] { "TextInputEmpty", "ComboEmpty" }) {
         assertNull(eval(name + ".selectedObject"), name);
         assertEquals(false, eval(name + " >= 10"), name);
         assertEquals(true, eval(name + " >= 0"), name);
         assertEquals(0.0, eval(name + " - 0"), name);
         assertEquals("null", eval("'' + " + name), name);
         assertEquals(false, eval(name + " == null"), name);
      }
   }

   @Test
   void explicitValueAndToStringCallsWork() throws Exception {
      assertEquals(50.0, eval("txtRegisterIncrease12M.value"));
      assertEquals(60.0, eval("txtRegisterIncrease12M.value + 10"));
      // an explicit toString()/valueOf() call is callable and yields the value
      assertEquals("50", eval("'' + txtRegisterIncrease12M.toString()"));
      assertEquals("50", eval("txtRegisterIncrease12MStr.toString()"));
      assertEquals(50.0, eval("txtRegisterIncrease12M.valueOf()"));
      assertEquals("null", eval("String(TextInputEmpty.toString())"));
      assertEquals("string", eval("typeof txtRegisterIncrease12M.toString()"));
      assertEquals("50", eval("txtRegisterIncrease12M.toString()"));
      assertEquals(2.0, eval("txtRegisterIncrease12M.toString().length"));
      assertEquals("string", eval("typeof TextInputEmpty.toString()"));
      assertEquals("null", eval("TextInputEmpty.toString()"));
      assertEquals("string", eval("typeof ComboEmpty.toString()"));
   }

   private Object eval(String script) throws Exception {
      return scope.execute(script, (VSAScriptable) null);
   }

   private static List<Object> toList(Object arr) {
      if(arr instanceof Object[] objs) {
         return List.of(objs);
      }

      if(arr instanceof List<?> list) {
         return List.copyOf(list);
      }

      fail("not an array: " + arr + " (" + (arr == null ? null : arr.getClass()) + ")");
      return null;
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

   // the reporter's function (Redmine #78000), with the loop the report elides
   private static final String GET_PERCENTAGE =
      "function getPercentage(v1, v12, minScale, maxScale) {\n" +
      "   var arrValues = [v1, v12];\n" +
      "   for(var i = 0; i < arrValues.length; i++) {\n" +
      "      var x;\n" +
      "      if(arrValues[i] >= maxScale) { x = 100; }\n" +
      "      else if(arrValues[i] <= minScale) { x = 0; }\n" +
      "      else { x = ((arrValues[i] - minScale) / (maxScale - minScale)) * 100; }\n" +
      "      if(x >= 0 && x <= 100) { arrValues[i] = x; } else { arrValues[i] = 0; }\n" +
      "   }\n" +
      "   return arrValues;\n" +
      "}\n";
}
