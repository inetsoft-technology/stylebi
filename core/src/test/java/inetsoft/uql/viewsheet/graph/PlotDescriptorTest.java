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
package inetsoft.uql.viewsheet.graph;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PlotDescriptor}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome()
@Tag("core")
class PlotDescriptorTest {

   @Test
   void markerShapeAndSizeSurviveXmlRoundTrip() throws Exception {
      PlotDescriptor desc = new PlotDescriptor();
      desc.setPointLine(true);
      desc.setMarkerShape("diamond");
      desc.setMarkerSize(12);

      StringWriter sw = new StringWriter();
      desc.writeXML(new PrintWriter(sw));

      Document doc = parseXmlString(sw.toString());
      PlotDescriptor parsed = new PlotDescriptor();
      parsed.parseXML(doc.getDocumentElement());

      assertTrue(parsed.isPointLine());
      assertEquals("diamond", parsed.getMarkerShape());
      assertEquals(12, parsed.getMarkerSize());
      assertTrue(parsed.equalsContent(desc));
   }

   @Test
   void markerShapeAndSizeDefaultsAreNullAndZero() {
      PlotDescriptor desc = new PlotDescriptor();
      assertNull(desc.getMarkerShape(), "default markerShape should be null");
      assertEquals(0, desc.getMarkerSize(), "default markerSize should be 0");
   }

   @Test
   void markerShapeNullNotWrittenToXml() throws Exception {
      // When markerShape is null, the attribute should not appear in the XML output
      PlotDescriptor desc = new PlotDescriptor();
      desc.setMarkerSize(0); // default

      StringWriter sw = new StringWriter();
      desc.writeXML(new PrintWriter(sw));

      assertFalse(sw.toString().contains("markerShape="),
         "null markerShape should not be written to XML");
   }

   private static Document parseXmlString(String xml) throws Exception {
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
      return factory.newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
   }
}
