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
package inetsoft.uql.viewsheet;

import inetsoft.report.StyleConstants;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.internal.LineVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.RectangleVSAssemblyInfo;
import inetsoft.util.XMLSerializable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #77583: non-numeric VSFormat, Line and Shape style values must render with the default and
 * save as the default (or the accepted integral value), and a second save must be identical.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatNonNumericSaveTest {
   private static final String DEFAULT_ALIGN =
      (StyleConstants.H_LEFT | StyleConstants.V_TOP) + "";

   @ParameterizedTest(name = "alphaValue=\"{0}\" -> {1}")
   @CsvSource(delimiter = '|', value = {
      "50|50", "$(alpha)|100", "=50|100", "50.0|50", "10(|100", "2147483648|100", "1e10|100"
   })
   void alphaSavesAndRenders(String input, int expected) throws Exception {
      VSFormat fmt = parse(VSFormat::new, "<VSFormat alphaValue=\"" + input + "\"/>");
      assertEquals(expected, fmt.getAlpha());
      assertEquals(expected, fmt.getAlphaValue());

      Element saved = saveTwice(VSFormat::new, fmt);
      assertEquals(expected + "", saved.getAttribute("alphaValue"));
      assertEquals(expected + "", saved.getAttribute("alpha"));
   }

   @Test
   void runtimeAlphaOverridesUnevaluatedDesignValue() throws Exception {
      VSFormat fmt = parse(VSFormat::new, "<VSFormat alphaValue=\"$(alpha)\"/>");
      fmt.setAlpha(40);
      assertEquals(40, fmt.getAlpha());
   }

   @Test
   void junkAlignmentUsesDefault() throws Exception {
      VSFormat fmt = parse(VSFormat::new, "<VSFormat alignValue=\"*-1\"/>");
      assertEquals(DEFAULT_ALIGN, fmt.getAlignment() + "");

      Element saved = saveTwice(VSFormat::new, fmt);
      assertEquals(DEFAULT_ALIGN, saved.getAttribute("alignValue"));
      assertEquals(DEFAULT_ALIGN, saved.getAttribute("align"));
   }

   @ParameterizedTest(name = "roundCorner=\"{0}\" -> {1}")
   @CsvSource(delimiter = '|', value = { "abc|0", "$(rc)|0", "5|5" })
   void roundCornerSavesAndRenders(String input, int expected) throws Exception {
      VSFormat fmt = parse(VSFormat::new, "<VSFormat roundCorner=\"" + input + "\"/>");
      assertEquals(expected, fmt.getRoundCorner());

      Element saved = saveTwice(VSFormat::new, fmt);
      assertEquals(expected + "", saved.getAttribute("roundCorner"));
   }

   @ParameterizedTest(name = "beginStyle=\"{0}\" -> {1}")
   @CsvSource(delimiter = '|', value = { "abc|0", "1e10|0", "2|2" })
   void lineBeginStyle(String input, int expected) throws Exception {
      String xml = "<assemblyInfo class=\"" + LineVSAssemblyInfo.class.getName() +
         "\" beginStyle=\"" + input + "\"><name><![CDATA[Line1]]></name></assemblyInfo>";
      LineVSAssemblyInfo info = parse(LineVSAssemblyInfo::new, xml);
      assertEquals(expected, info.getBeginArrowStyle());

      Element saved = saveTwice(LineVSAssemblyInfo::new, info);
      assertEquals(expected + "", saved.getAttribute("beginStyle"));
   }

   @ParameterizedTest(name = "lineStyle=\"{0}\" -> {1}")
   @CsvSource(delimiter = '|', value = { "abc|4097", "2|2" })
   void rectangleLineStyle(String input, int expected) throws Exception {
      assertEquals(StyleConstants.THIN_LINE, 4097);
      String xml = "<assemblyInfo class=\"" + RectangleVSAssemblyInfo.class.getName() +
         "\" lineStyle=\"" + input + "\"><name><![CDATA[Rect1]]></name></assemblyInfo>";
      RectangleVSAssemblyInfo info = parse(RectangleVSAssemblyInfo::new, xml);
      assertEquals(expected, info.getLineStyle());

      Element saved = saveTwice(RectangleVSAssemblyInfo::new, info);
      assertEquals(expected + "", saved.getAttribute("lineStyle"));
   }

   private static <T extends XMLSerializable> T parse(Supplier<T> factory, String xml)
      throws Exception
   {
      T obj = factory.get();
      obj.parseXML(element(xml));
      return obj;
   }

   /**
    * Writes the object, parses the output into a new object, writes that one, checks that both
    * saves are identical and returns the saved root element.
    */
   private static <T extends XMLSerializable> Element saveTwice(Supplier<T> factory, T obj)
      throws Exception
   {
      String first = write(obj);
      String second = write(parse(factory, first));
      assertEquals(first, second, "second save differs from the first");
      return element(first);
   }

   private static String write(XMLSerializable obj) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      obj.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static Element element(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
