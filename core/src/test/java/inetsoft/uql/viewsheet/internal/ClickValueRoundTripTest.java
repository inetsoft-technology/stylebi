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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77413: an unset "tip view on click" / "flyover on click" value was written as the
 * literal "null", which reloads as true. A missing or "null" attribute must parse as the
 * default (false), and a save and reload must not change it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ClickValueRoundTripTest {
   static Stream<Arguments> cases() {
      Supplier<VSAssemblyInfo> chart = ChartVSAssemblyInfo::new;
      Supplier<VSAssemblyInfo> table = TableVSAssemblyInfo::new;
      Supplier<VSAssemblyInfo> crosstab = CrosstabVSAssemblyInfo::new;
      Predicate<VSAssemblyInfo> chartTip = i -> ((ChartVSAssemblyInfo) i).isTipOnClick();
      Predicate<VSAssemblyInfo> chartFly = i -> ((ChartVSAssemblyInfo) i).isFlyOnClick();
      Predicate<VSAssemblyInfo> tableTip = i -> ((TableDataVSAssemblyInfo) i).isTipOnClick();
      Predicate<VSAssemblyInfo> tableFly = i -> ((TableDataVSAssemblyInfo) i).isFlyOnClick();

      return Stream.of(
         Arguments.of("chart tip", chart, "tipClickValue", chartTip),
         Arguments.of("chart fly", chart, "flyClickValue", chartFly),
         Arguments.of("table tip", table, "tipClickValue", tableTip),
         Arguments.of("table fly", table, "flyClickValue", tableFly),
         Arguments.of("crosstab tip", crosstab, "tipClickValue", tableTip),
         Arguments.of("crosstab fly", crosstab, "flyClickValue", tableFly));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void aMissingValueStaysFalseAfterSaveAndReload(String name, Supplier<VSAssemblyInfo> factory,
                                                  String attr, Predicate<VSAssemblyInfo> onClick)
      throws Exception
   {
      String xml = withAttr(write(factory.get()), attr, null);
      assertFalse(xml.contains(attr + "="), xml);
      assertFalseAfterReloads(factory, xml, attr, onClick);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void aNullValueLoadsAsFalse(String name, Supplier<VSAssemblyInfo> factory,
                               String attr, Predicate<VSAssemblyInfo> onClick)
      throws Exception
   {
      String xml = withAttr(write(factory.get()), attr, "null");
      assertFalseAfterReloads(factory, xml, attr, onClick);
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void aTrueValueStaysTrue(String name, Supplier<VSAssemblyInfo> factory,
                            String attr, Predicate<VSAssemblyInfo> onClick)
      throws Exception
   {
      String xml = withAttr(write(factory.get()), attr, "true");

      for(int i = 0; i < 2; i++) {
         VSAssemblyInfo parsed = parse(factory, xml);
         assertTrue(onClick.test(parsed), xml);
         xml = write(parsed);
      }
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("cases")
   void aVariableOrScriptValueIsKept(String name, Supplier<VSAssemblyInfo> factory,
                                     String attr, Predicate<VSAssemblyInfo> onClick)
      throws Exception
   {
      for(String value : new String[] { "$(clickVar)", "=true" }) {
         String xml = withAttr(write(factory.get()), attr, value);

         for(int i = 0; i < 2; i++) {
            xml = write(parse(factory, xml));
            assertTrue(xml.contains(" " + attr + "=\"" + value + "\""), "pass " + i + ": " + xml);
         }
      }
   }

   private static void assertFalseAfterReloads(Supplier<VSAssemblyInfo> factory, String xml,
                                               String attr, Predicate<VSAssemblyInfo> onClick)
      throws Exception
   {
      for(int i = 0; i < 3; i++) {
         VSAssemblyInfo parsed = parse(factory, xml);
         assertFalse(onClick.test(parsed), "pass " + i + ": " + xml);
         xml = write(parsed);
         assertFalse(xml.contains(attr + "=\"null\""), xml);
      }
   }

   /**
    * Replaces the attribute value, or removes the attribute if value is null.
    */
   private static String withAttr(String xml, String attr, String value) {
      String replacement = value == null ? "" : " " + attr + "=\"" + value + "\"";
      String result = xml.replaceFirst(" " + attr + "=\"[^\"]*\"",
                                      Matcher.quoteReplacement(replacement));
      assertNotEquals(value == null, result.contains(" " + attr + "="), result);
      return result;
   }

   private static VSAssemblyInfo parse(Supplier<VSAssemblyInfo> factory, String xml)
      throws Exception
   {
      Element elem = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      VSAssemblyInfo info = factory.get();
      info.parseXML(elem);
      return info;
   }

   private static String write(VSAssemblyInfo info) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         info.writeXML(writer);
      }

      return buffer.toString();
   }
}
