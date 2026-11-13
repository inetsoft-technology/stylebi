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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A bar corner radius saved by a build that had no flag is the author's; a stored flag is read as
 * is; a modern chart seeds the modern corner.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarCornerFlagTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aReleasedFilesRadiusIsTheAuthors() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml("0.25"));
      assertTrue(info.isUserBarCornerRadius());
   }

   @Test
   void aReleasedFilesSquareBarsAreNoOpinion() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml("0.0"));
      assertFalse(info.isUserBarCornerRadius());
   }

   @Test
   void aStoredFlagIsReadAsIs() throws Exception {
      String xml = releasedXml("0.08").replaceFirst("<assemblyInfo ",
                                                    "$0userBarCornerRadius=\"false\" ");
      assertTrue(xml.contains("userBarCornerRadius=\"false\""), "premise: the flag is stored");
      ChartVSAssemblyInfo info = parseInfo(xml);
      assertFalse(info.isUserBarCornerRadius());
   }

   @Test
   void keepAndRevertKeepAReleasedAuthorsRadius() throws Exception {
      ChartVSAssemblyInfo info = parseInfo(releasedXml("0.25"));

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(0.25, radius(info), 1e-9);

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(0.25, radius(info), 1e-9);
   }

   @Test
   void aNewModernChartSeedsTheModernCorner() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      assertNotNull(info.getVizMark(), "premise: a marked chart");
      assertEquals(VSChartChromeDefaults.BAR_CORNER_RADIUS, radius(info), 1e-9);

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(0, radius(info), 1e-9);
   }

   @Test
   void aClassicChartWritesWhatItAlwaysWrote() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      ChartVSAssemblyInfo info = newChart();
      assertNull(info.getVizMark(), "premise: a classic chart");

      String first = writeXml(info);
      String second = writeXml(parseInfo(first));
      String third = writeXml(parseInfo(second));

      assertEquals(second, third);
      assertTrue(first.contains("userBarCornerRadius=\"false\""));
      assertTrue(second.contains("userBarCornerRadius=\"false\""));
      assertTrue(second.contains("barCornerRadius=\"0.0\""));
   }

   // a classic chart written, then edited into what a build without the flag wrote
   private static String releasedXml(String radius) {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      String xml = writeXml(newChart());
      assertTrue(xml.contains("userBarCornerRadius=\"false\""), "premise: the flag is written");
      assertTrue(xml.contains("barCornerRadius=\"0.0\""), "premise: the radius is written");
      return xml.replace(" userBarCornerRadius=\"false\"", "")
         .replace("barCornerRadius=\"0.0\"", "barCornerRadius=\"" + radius + "\"");
   }

   private static double radius(ChartVSAssemblyInfo info) {
      return info.getChartDescriptor().getPlotDescriptor().getBarCornerRadius();
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static ChartVSAssemblyInfo parseInfo(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }
}
