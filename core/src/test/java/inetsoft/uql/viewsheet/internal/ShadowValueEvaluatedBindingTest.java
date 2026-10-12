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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.DynamicValue;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77602: a viewsheet is saved while it is open, after the sandbox has
 * evaluated the shadow binding. The saved design value must still be the
 * binding, not the evaluated runtime value or the dialog's boolean view.
 * Covers every concrete Output/Shape info type, including the ones that
 * override writeAttributes (Submit, Thermometer, Cylinder, SlidingScale).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ShadowValueEvaluatedBindingTest {
   private static final String BINDING = "$(showShadow)";

   static Stream<Arguments> infos() {
      return Stream.of(
         Arguments.of("Text", (Supplier<VSAssemblyInfo>) TextVSAssemblyInfo::new),
         Arguments.of("Image", (Supplier<VSAssemblyInfo>) ImageVSAssemblyInfo::new),
         Arguments.of("Submit", (Supplier<VSAssemblyInfo>) SubmitVSAssemblyInfo::new),
         Arguments.of("Gauge", (Supplier<VSAssemblyInfo>) GaugeVSAssemblyInfo::new),
         Arguments.of("Thermometer", (Supplier<VSAssemblyInfo>) ThermometerVSAssemblyInfo::new),
         Arguments.of("Cylinder", (Supplier<VSAssemblyInfo>) CylinderVSAssemblyInfo::new),
         Arguments.of("SlidingScale", (Supplier<VSAssemblyInfo>) SlidingScaleVSAssemblyInfo::new),
         Arguments.of("Rectangle", (Supplier<VSAssemblyInfo>) RectangleVSAssemblyInfo::new),
         Arguments.of("Line", (Supplier<VSAssemblyInfo>) LineVSAssemblyInfo::new),
         Arguments.of("Oval", (Supplier<VSAssemblyInfo>) OvalVSAssemblyInfo::new));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("infos")
   void evaluatedBindingIsSavedAsTheBinding(String name, Supplier<VSAssemblyInfo> factory)
      throws Exception
   {
      String xml = write(factory.get());
      String attr = " shadowValue=\"false\"";
      assertTrue(xml.contains(attr), xml);

      VSAssemblyInfo info = factory.get();
      info.parseXML(parse(xml.replace(attr, " shadowValue=\"" + BINDING + "\"")));

      // evaluate the binding the way ViewsheetSandbox.executeDynamicValues does
      int evaluated = 0;

      for(DynamicValue value : info.getDynamicValues()) {
         if(BINDING.equals(value.getDValue())) {
            value.setRValue(Boolean.TRUE);
            evaluated++;
         }
      }

      assertEquals(1, evaluated);
      assertTrue(isShadow(info), "the evaluated binding shows a shadow");

      Element saved = parse(write(info));
      assertEquals("true", Tool.getAttribute(saved, "shadow"));
      assertEquals(BINDING, Tool.getAttribute(saved, "shadowValue"),
                   "the binding, not the runtime value, must be saved");

      VSAssemblyInfo reloaded = factory.get();
      reloaded.parseXML(saved);
      assertTrue(reloaded.getDynamicValues().stream()
                    .anyMatch(value -> BINDING.equals(value.getDValue())),
                 "the reloaded assembly must still carry the binding");
   }

   private static boolean isShadow(VSAssemblyInfo info) {
      return info instanceof OutputVSAssemblyInfo output ? output.isShadow() :
         ((ShapeVSAssemblyInfo) info).isShadow();
   }

   private static String write(VSAssemblyInfo info) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         info.writeXML(writer);
      }

      return buffer.toString();
   }

   private static Element parse(String xml) throws Exception {
      return Tool.parseXML(new StringReader(xml)).getDocumentElement();
   }
}
