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

import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

class LabelInfoTest {
   private LabelInfo labelInfo;

   @BeforeEach
   void setUp() {
      labelInfo = new LabelInfo("Test Label");
   }

   @Test
   void scriptOverridePropagatesAtRuntime() {
      labelInfo.setLabelPositionValue(LabelInfo.LEFT);
      labelInfo.setLabelPosition(LabelInfo.TOP);

      assertEquals(LabelInfo.TOP, labelInfo.getLabelPosition(),
         "runtime getter should return script override");
      assertEquals(LabelInfo.LEFT, labelInfo.getLabelPositionValue(),
         "design-time value should be unchanged");
   }

   @Test
   void resetRuntimeValuesFallsBackToDesignTime() {
      labelInfo.setLabelPositionValue(LabelInfo.BOTTOM);
      labelInfo.setLabelPosition(LabelInfo.RIGHT);
      assertEquals(LabelInfo.RIGHT, labelInfo.getLabelPosition());

      labelInfo.resetRuntimeValues();

      assertEquals(LabelInfo.BOTTOM, labelInfo.getLabelPosition(),
         "after reset, runtime getter should fall back to design-time value");
   }

   @Test
   void invalidPositionDefaultsToLeft() {
      labelInfo.setLabelPosition("center");
      assertEquals(LabelInfo.LEFT, labelInfo.getLabelPosition(),
         "invalid runtime position should default to LEFT");
   }

   @Test
   void invalidDesignTimePositionDefaultsToLeft() {
      labelInfo.setLabelPositionValue("center");
      assertEquals(LabelInfo.LEFT, labelInfo.getLabelPositionValue(),
         "invalid design-time position should default to LEFT");
   }

   @Test
   void backwardCompatibleXmlParsing() throws Exception {
      // simulate old XML that only has labelPosition, not labelPositionValue
      String xml = "<root><labelInfo class=\"inetsoft.uql.viewsheet.internal.LabelInfo\" " +
         "labelVisible=\"true\" labelVisibleValue=\"true\" " +
         "labelPosition=\"right\" " +
         "labelGap=\"5\" labelGapValue=\"5\">" +
         "<labelTextValue><![CDATA[Old Label]]></labelTextValue>" +
         "</labelInfo></root>";

      LabelInfo parsed = new LabelInfo();
      Element elem = parseXmlString(xml);
      parsed.parseXML(elem);

      assertEquals(LabelInfo.RIGHT, parsed.getLabelPositionValue(),
         "should read legacy labelPosition when labelPositionValue is absent");
      assertEquals("Old Label", parsed.getLabelTextValue());
   }

   @Test
   void xmlRoundTripPreservesDesignTimeValues() throws Exception {
      labelInfo.setLabelPositionValue(LabelInfo.BOTTOM);
      labelInfo.setLabelGapValue(10);
      labelInfo.setLabelVisibleValue("true");

      // set runtime overrides that should NOT leak into XML
      labelInfo.setLabelPosition(LabelInfo.TOP);
      labelInfo.setLabelGap(20);
      labelInfo.setLabelVisible(false);

      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      pw.print("<root>");
      labelInfo.writeXML(pw);
      pw.print("</root>");
      pw.flush();

      LabelInfo restored = new LabelInfo();
      Element elem = parseXmlString(sw.toString());
      restored.parseXML(elem);

      assertEquals(LabelInfo.BOTTOM, restored.getLabelPositionValue(),
         "XML should persist design-time position, not runtime override");
      assertEquals(10, restored.getLabelGapValue(),
         "XML should persist design-time gap, not runtime override");
      assertTrue(restored.getLabelVisibleValue(),
         "XML should persist design-time visibility, not runtime override");
   }

   @Test
   void clonePreservesDesignAndRuntimeValues() {
      labelInfo.setLabelPositionValue(LabelInfo.BOTTOM);
      labelInfo.setLabelPosition(LabelInfo.TOP);
      labelInfo.setLabelGapValue(10);
      labelInfo.setLabelGap(20);

      LabelInfo cloned = (LabelInfo) labelInfo.clone();

      assertEquals(LabelInfo.TOP, cloned.getLabelPosition(),
         "clone should preserve runtime position");
      assertEquals(LabelInfo.BOTTOM, cloned.getLabelPositionValue(),
         "clone should preserve design-time position");
      assertEquals(20, cloned.getLabelGap(),
         "clone should preserve runtime gap");
      assertEquals(10, cloned.getLabelGapValue(),
         "clone should preserve design-time gap");

      // modifying clone should not affect original
      cloned.setLabelPosition(LabelInfo.RIGHT);
      assertEquals(LabelInfo.TOP, labelInfo.getLabelPosition(),
         "modifying clone should not affect original");
   }

   private static Element parseXmlString(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      Document doc = factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes()));
      return doc.getDocumentElement();
   }
}
