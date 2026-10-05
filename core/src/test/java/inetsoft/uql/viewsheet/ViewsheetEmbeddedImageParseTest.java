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
import inetsoft.util.Tool;
import inetsoft.util.TransformerManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77601: an embedded image whose value is empty or missing (a zero-byte image is saved
 * with an empty value) must load as an empty image instead of failing the whole viewsheet.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetEmbeddedImageParseTest {
   @ParameterizedTest(name = "[{index}] value element: {0}")
   @ValueSource(strings = {
      "<value><![CDATA[]]></value>",
      "<value></value>",
      "",
      "<value><![CDATA[A]]></value>"
   })
   void emptyImageLoadsAndRoundTrips(String value) throws Exception {
      Viewsheet vs = parse(viewsheetXml(value));
      assertArrayEquals(new String[] { "img1" }, vs.getUploadedImageNames());
      assertArrayEquals(new byte[0], vs.getUploadedImageBytes("img1"));

      String saved = write(vs);
      Viewsheet reloaded = parse(saved);
      assertArrayEquals(new byte[0], reloaded.getUploadedImageBytes("img1"));
      assertEquals(saved, write(reloaded));
   }

   @Test
   void nonEmptyImageIsUnchanged() throws Exception {
      Viewsheet vs = parse(viewsheetXml("<value><![CDATA[0aFF]]></value>"));
      assertArrayEquals(new byte[] { 0x0a, (byte) 0xff }, vs.getUploadedImageBytes("img1"));
   }

   private static String viewsheetXml(String value) {
      return "<viewsheet><assembly><assemblies></assemblies>" +
         "<embeddedImage><name><![CDATA[img1]]></name>" + value + "</embeddedImage>" +
         "</assembly></viewsheet>";
   }

   private static Viewsheet parse(String xml) throws Exception {
      Document doc = Tool.parseXML(
         new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
      TransformerManager.getManager(TransformerManager.VIEWSHEET).transform(doc);
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
