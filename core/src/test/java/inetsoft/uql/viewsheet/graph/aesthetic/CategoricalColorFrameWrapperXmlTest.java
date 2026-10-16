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
package inetsoft.uql.viewsheet.graph.aesthetic;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.util.CoreTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78460: a color frame static value (a data value) holding a character XML 1.0 can't
 * carry must be written so the sheet can be parsed back, and read back exactly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CategoricalColorFrameWrapperXmlTest {
   @Test
   void controlCharDimensionValueRoundTrips() throws Exception {
      CategoricalColorFrame frame = new CategoricalColorFrame();
      frame.setColor("\u0001Aachen", new Color(0x518DB9));
      frame.setColor("Boston", new Color(0x123456));
      frame.setColor("a\\u0001\u0002b", new Color(0x654321));

      String xml = write(frame);
      assertFalse(xml.contains("\u0001"), xml);
      assertFalse(xml.contains("\u0002"), xml);
      assertTrue(xml.contains("<color dim=\"Boston\" value="), "plain value is unchanged: " + xml);

      CategoricalColorFrame read = read(xml);
      assertEquals(new Color(0x518DB9), read.getColor("\u0001Aachen"));
      assertEquals(new Color(0x123456), read.getColor("Boston"));
      assertEquals(new Color(0x654321), read.getColor("a\\u0001\u0002b"));
   }

   @Test
   void unmarkedLegacyValueIsReadUnchanged() throws Exception {
      // written before the fix: no marker, so a backslash sequence is not decoded
      String xml = "<legendFrame classname=\"inetsoft.graph.aesthetic.CategoricalColorFrame\"" +
         " changed=\"false\"><dimensionColors>" +
         "<color dim=\"C:\\u0041 &amp; B\" value=\"5344697\" />" +
         "</dimensionColors></legendFrame>";

      CategoricalColorFrame read = read(xml);
      assertEquals(new Color(5344697), read.getColor("C:\\u0041 & B"));
      assertNull(read.getColor("C:A & B"));
   }

   private static String write(CategoricalColorFrame frame) throws Exception {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      VisualFrameWrapper.wrap(frame).writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static CategoricalColorFrame read(String xml) throws Exception {
      Document doc = CoreTool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      VisualFrameWrapper wrapper = VisualFrameWrapper.createVisualFrame(doc.getDocumentElement());
      assertInstanceOf(CategoricalColorFrameWrapper.class, wrapper);
      return (CategoricalColorFrame) wrapper.getVisualFrame();
   }
}
