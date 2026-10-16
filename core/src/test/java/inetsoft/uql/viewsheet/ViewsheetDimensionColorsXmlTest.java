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
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78460: a shared dimension color key (a data value) holding a character XML 1.0
 * can't carry must be written so the viewsheet can be parsed back, and read back exactly.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetDimensionColorsXmlTest {
   @Test
   void controlCharDimensionValueRoundTrips() throws Exception {
      Map<String, Color> colors = new LinkedHashMap<>();
      colors.put("\u0001Aachen", new Color(0x518DB9));
      colors.put("Boston", new Color(0x123456));
      colors.put("a\\u0001\u0002]]>b", new Color(0x654321));

      Viewsheet vs = new Viewsheet();
      vs.setDimensionColors("city", colors);

      String xml = write(vs);
      assertFalse(xml.contains("\u0001"), xml);
      assertFalse(xml.contains("\u0002"), xml);
      assertTrue(xml.contains("<dimension><![CDATA[city^^Boston]]></dimension>"),
                 "plain value is unchanged: " + xml);

      Viewsheet read = parse(xml);
      assertEquals(colors, read.getDimensionColors("city"));
   }

   @Test
   void unmarkedLegacyValueIsReadUnchanged() throws Exception {
      // written before the fix: no marker, so a backslash sequence is not decoded
      String xml = "<viewsheet><assembly><assemblies></assemblies>" +
         "<dimensionColors><dimensionColor>" +
         "<dimension><![CDATA[city^^C:\\u0041]]></dimension>" +
         "<color><![CDATA[#518db9]]></color>" +
         "</dimensionColor></dimensionColors>" +
         "</assembly></viewsheet>";

      Viewsheet read = parse(xml);
      assertEquals(Map.of("C:\\u0041", new Color(0x518DB9)), read.getDimensionColors("city"));
   }

   private static Viewsheet parse(String xml) throws Exception {
      Document doc = CoreTool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      Viewsheet vs = new Viewsheet();
      vs.parseXML(doc.getDocumentElement(), false);
      return vs;
   }

   private static String write(Viewsheet vs) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      writer.println("<viewsheet>");
      vs.writeXML(writer);
      writer.println("</viewsheet>");
      writer.flush();
      return buffer.toString();
   }
}
