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

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78460: a viewsheet whose chart color binding maps a data value holding a character
 * XML 1.0 can't carry is saved the way the storage writes it and opened the way the
 * storage reads it, and the color mapping survives.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetChartColorSaveRoundTripTest {
   @Test
   void chartWithControlCharColorValueSavesAndOpens() throws Exception {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);

      VSChartInfo info = chart.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("city");
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dim);
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      color.setVisualFrameWrapper(wrapper);
      CategoricalColorFrame frame = (CategoricalColorFrame) wrapper.getVisualFrame();
      frame.setColor("\u0001Aachen", new Color(0x518DB9));
      frame.setColor("\u0001New York", new Color(0x123456));
      info.setColorField(color);
      vs.setDimensionColors("city", Map.of("\u0001Aachen", new Color(0x518DB9)));

      byte[] saved = save(vs);
      String xml = new String(saved, StandardCharsets.UTF_8);
      assertTrue(xml.contains("Aachen"), "the color frame is written with the viewsheet");

      Viewsheet read = open(saved);
      ChartVSAssembly readChart = (ChartVSAssembly) read.getAssembly("Chart1");
      assertNotNull(readChart);
      CategoricalColorFrame readFrame = (CategoricalColorFrame)
         readChart.getVSChartInfo().getColorField().getVisualFrame();
      assertEquals(new Color(0x518DB9), readFrame.getColor("\u0001Aachen"));
      assertEquals(new Color(0x123456), readFrame.getColor("\u0001New York"));
      assertEquals(Map.of("\u0001Aachen", new Color(0x518DB9)), read.getDimensionColors("city"));
   }

   // mirrors BlobIndexedStorage.putXMLSerializable
   private static byte[] save(Viewsheet vs) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      PrintWriter writer = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
      writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      vs.writeXML(writer);
      writer.flush();
      return out.toByteArray();
   }

   // mirrors BlobIndexedStorage.getXMLSerializable
   private static Viewsheet open(byte[] saved) throws Exception {
      Document doc = CoreTool.parseXML(new ByteArrayInputStream(saved));
      Viewsheet vs = new Viewsheet();
      vs.parseXML(doc.getDocumentElement());
      return vs;
   }
}
