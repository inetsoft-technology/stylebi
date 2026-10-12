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
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77810: the Text assembly's autoSize, scaleVertical and url design
 * values must be saved as stored, not collapsed to the boolean the property
 * dialog shows. A $(var) or =expr binding, or a truthy literal such as yes,
 * TRUE or 0, was rewritten to "false" on every save.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TextDesignValueRoundTripTest {
   private static final String[] VALUES = {
      "true", "false", "TRUE", "0", "yes", "$(textFlag)", "=true",
      "=a \"x\" & <y>", null
   };

   /**
    * attribute, value a default info writes (also the parse default for a
    * missing attribute), runtime getter or null when the runtime value is not
    * compared (isUrl() reads the raw runtime value, so a literal has no effect).
    */
   private static final Object[][] ATTRS = {
      { "autoSizeValue", "false", (Predicate<TextVSAssemblyInfo>) TextVSAssemblyInfo::isAutoSize },
      { "scaleVerticalValue", "true", (Predicate<TextVSAssemblyInfo>) TextVSAssemblyInfo::isScaleVertical },
      { "urlValue", "false", null },
   };

   static Stream<Arguments> cases() {
      return Stream.of(ATTRS).flatMap(attr -> Stream.of(VALUES)
         .map(value -> Arguments.of(attr[0], attr[1], attr[2], value)));
   }

   static Stream<Arguments> bindings() {
      return Stream.of(ATTRS).flatMap(attr -> Stream.of("$(textFlag)", "=true")
         .map(value -> Arguments.of(attr[0], attr[1], attr[2], value)));
   }

   @ParameterizedTest(name = "{0}={3}")
   @MethodSource("cases")
   void designValueSurvivesASave(String attr, String defaultValue,
                                 Predicate<TextVSAssemblyInfo> runtime, String value)
      throws Exception
   {
      TextVSAssemblyInfo loaded = load(attr, defaultValue, value);
      String saved = write(loaded);
      String expected = value == null ? defaultValue : value;

      assertEquals(expected, Tool.getAttribute(parse(saved), attr), saved);

      if("true".equals(value) || "false".equals(value)) {
         // GUI-authored values are written exactly as before
         assertTrue(saved.contains(" " + attr + "=\"" + value + "\""), saved);
      }

      TextVSAssemblyInfo reloaded = new TextVSAssemblyInfo();
      reloaded.parseXML(parse(saved));

      if(runtime != null) {
         assertEquals(runtime.test(loaded), runtime.test(reloaded),
                      "the runtime value must not change across a save");
      }

      assertEquals(saved, write(reloaded), "a second save must be stable");
   }

   @ParameterizedTest(name = "{0}={3}")
   @MethodSource("bindings")
   void evaluatedBindingIsSavedAsTheBinding(String attr, String defaultValue,
                                            Predicate<TextVSAssemblyInfo> runtime,
                                            String binding)
      throws Exception
   {
      TextVSAssemblyInfo info = load(attr, defaultValue, binding);
      evaluate(info, binding);

      if(runtime != null) {
         assertTrue(runtime.test(info), "the evaluated binding is true");
      }

      Element saved = parse(write(info));
      assertEquals(binding, Tool.getAttribute(saved, attr),
                   "the binding, not the runtime value, must be saved");

      TextVSAssemblyInfo reloaded = new TextVSAssemblyInfo();
      reloaded.parseXML(saved);
      evaluate(reloaded, binding);

      if(runtime != null) {
         assertTrue(runtime.test(reloaded),
                    "the reloaded binding must still evaluate to true");
      }
   }

   /**
    * Evaluate the binding the way ViewsheetSandbox.executeDynamicValues does.
    */
   private static void evaluate(TextVSAssemblyInfo info, String binding) {
      int evaluated = 0;

      for(DynamicValue value : info.getDynamicValues()) {
         if(binding.equals(value.getDValue())) {
            value.setRValue(Boolean.TRUE);
            evaluated++;
         }
      }

      assertEquals(1, evaluated, "the reloaded assembly must still carry the binding");
   }

   /**
    * Load a Text assembly whose saved attribute is the given value, the way an
    * imported or hand-edited asset reaches the parser.
    */
   private static TextVSAssemblyInfo load(String attr, String defaultValue, String value)
      throws Exception
   {
      String xml = write(new TextVSAssemblyInfo());
      String written = " " + attr + "=\"" + defaultValue + "\"";
      assertEquals(xml.indexOf(written), xml.lastIndexOf(written), xml);
      assertTrue(xml.contains(written), xml);
      xml = xml.replace(written, value == null ? "" :
         " " + attr + "=\"" + Tool.escape(value) + "\"");

      TextVSAssemblyInfo info = new TextVSAssemblyInfo();
      info.parseXML(parse(xml));

      return info;
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
