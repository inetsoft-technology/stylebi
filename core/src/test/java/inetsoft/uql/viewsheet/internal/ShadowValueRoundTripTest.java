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
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77602: the shadow DynamicValue's design value must be saved as stored,
 * not collapsed to the boolean the property dialog shows. A $(var) or =expr
 * binding, or a truthy literal such as yes, TRUE or 0, was rewritten to
 * shadowValue="false" on every save.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ShadowValueRoundTripTest {
   private static final String[] VALUES = {
      "true", "false", "TRUE", "0", "yes", "$(showShadow)", "=true",
      "=a \"x\" & <y>", null
   };

   static Stream<Arguments> cases() {
      Object[][] infos = {
         { "Text", (Supplier<VSAssemblyInfo>) TextVSAssemblyInfo::new },
         { "Image", (Supplier<VSAssemblyInfo>) ImageVSAssemblyInfo::new },
         { "Gauge", (Supplier<VSAssemblyInfo>) GaugeVSAssemblyInfo::new },
         { "Rectangle", (Supplier<VSAssemblyInfo>) RectangleVSAssemblyInfo::new },
         { "Line", (Supplier<VSAssemblyInfo>) LineVSAssemblyInfo::new },
         { "Oval", (Supplier<VSAssemblyInfo>) OvalVSAssemblyInfo::new },
      };

      return Stream.of(infos).flatMap(info -> Stream.of(VALUES)
         .map(value -> Arguments.of(info[0], info[1], value)));
   }

   @ParameterizedTest(name = "{0} shadowValue={2}")
   @MethodSource("cases")
   void shadowValueSurvivesASave(String name, Supplier<VSAssemblyInfo> factory,
                                 String value)
      throws Exception
   {
      VSAssemblyInfo loaded = load(factory, value);
      boolean shadowBefore = isShadow(loaded);

      String saved = write(loaded);
      String expected = value == null ? "false" : value;

      assertEquals(expected, Tool.getAttribute(parse(saved), "shadowValue"), saved);

      if("true".equals(value) || "false".equals(value)) {
         // GUI-authored values are written exactly as before
         assertTrue(saved.contains(" shadowValue=\"" + value + "\""), saved);
      }

      VSAssemblyInfo reloaded = factory.get();
      reloaded.parseXML(parse(saved));

      assertEquals(shadowBefore, isShadow(reloaded),
                   "the runtime shadow must not change across a save");
      assertEquals(saved, write(reloaded), "a second save must be stable");
   }

   /**
    * Load an assembly whose saved shadowValue is the given value, the way an
    * imported or hand-edited asset reaches the parser.
    */
   private static VSAssemblyInfo load(Supplier<VSAssemblyInfo> factory, String value)
      throws Exception
   {
      String xml = write(factory.get());
      String attr = " shadowValue=\"false\"";
      assertTrue(xml.contains(attr), xml);
      xml = xml.replace(attr, value == null ? "" :
         " shadowValue=\"" + Tool.escape(value) + "\"");

      VSAssemblyInfo info = factory.get();
      info.parseXML(parse(xml));

      return info;
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
