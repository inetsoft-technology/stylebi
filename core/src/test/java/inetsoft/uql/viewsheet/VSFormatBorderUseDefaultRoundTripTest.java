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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.Insets;
import java.io.*;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Bug #79096: a table cell border side stored as -1 ("use the table default" to
 * FormatTableLens.mergeLineStyle) must survive the XML and binary writers unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSFormatBorderUseDefaultRoundTripTest {
   @Test
   void aUseDefaultBorderSideSurvivesAnXmlSaveAndLoad() throws Exception {
      VSFormat fmt = new VSFormat();
      fmt.setBordersValue(new Insets(4099, -1, -1, -1));

      String first = write(fmt);
      VSFormat loaded = new VSFormat();
      loaded.parseXML(element(first));

      Insets insets = loaded.getBordersValue();
      assertEquals(4099, insets.top);
      assertEquals(-1, insets.left);
      assertEquals(-1, insets.bottom);
      assertEquals(-1, insets.right);
      assertEquals(first, write(loaded), "second save differs from the first");
   }

   @Test
   void aUseDefaultBorderSideIsAcceptedByTheBinaryWriter() {
      VSFormat fmt = new VSFormat();
      fmt.setBordersValue(new Insets(4099, -1, -1, -1));

      assertDoesNotThrow(() -> fmt.writeData(new DataOutputStream(new ByteArrayOutputStream())));
   }

   private static String write(VSFormat fmt) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      fmt.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static Element element(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }
}
