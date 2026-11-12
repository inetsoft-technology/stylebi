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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.LegendsDescriptor;
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
 * The stored legend placement state: the crowded verdict, the pending count and the author flag.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendPlacementStateTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theVerdictRoundTrips() throws Exception {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.setLegendCrowded(true);

      String written = writeXml(info);

      assertTrue(written.contains(" legendCrowded=\"true\""), written);
      assertTrue(parseInfo(written).isLegendCrowded());
   }

   @Test
   void aClassicChartWritesWhatItAlwaysWrote() {
      String written = writeXml(newChart());

      assertFalse(written.contains("legendCrowded"), written);
      assertFalse(written.contains("userLayout"), written);
   }

   @Test
   void aRevertedChartWritesNoVerdict() {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      info.setLegendCrowded(true);
      assertTrue(writeXml(info).contains(" legendCrowded=\"true\""), "premise: marked");

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));

      String written = writeXml(info);

      assertTrue(info.isLegendCrowded(), "premise: the verdict is kept in memory");
      assertFalse(written.contains("legendCrowded"), written);
   }

   @Test
   void theVerdictIsNotInABookmark() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      chart.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      ((ChartVSAssemblyInfo) chart.getVSAssemblyInfo()).setLegendCrowded(true);

      StringWriter sw = new StringWriter();
      chart.writeState(new PrintWriter(sw), true);

      assertFalse(sw.toString().contains("legendCrowded"), sw.toString());
   }

   @Test
   void copyViewInfoCarriesTheVerdictAndThePendingCount() {
      ChartVSAssemblyInfo from = newChart();
      from.setLegendCrowded(true);
      from.requestLegendCount();
      ChartVSAssemblyInfo to = newChart();

      to.copyViewInfo(from, true);

      assertTrue(to.isLegendCrowded());
      assertTrue(to.isLegendCountPending());

      // a copy never loses a request, in either direction
      ChartVSAssemblyInfo pending = newChart();
      pending.requestLegendCount();
      pending.copyViewInfo(newChart(), true);
      assertTrue(pending.isLegendCountPending());

      ChartVSAssemblyInfo idle = newChart();
      idle.copyViewInfo(pending, true);
      assertTrue(idle.isLegendCountPending());

      idle.clearLegendCountPending();
      assertFalse(idle.isLegendCountPending());
      idle.copyViewInfo(newChart(), true);
      assertFalse(idle.isLegendCountPending());
   }

   @Test
   void needsLegendCountOnlyOnAMarkedUnflaggedChart() {
      ChartVSAssemblyInfo info = newChart();
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.requestLegendCount();
      assertTrue(info.needsLegendCount(), "marked, unflagged and pending");

      ChartVSAssemblyInfo unmarked = newChart();
      unmarked.requestLegendCount();
      assertFalse(unmarked.needsLegendCount(), "unmarked");

      ChartVSAssemblyInfo flagged = newChart();
      flagged.setVizMark(VizMark.MODERN_LIGHT);
      flagged.requestLegendCount();
      flagged.getChartDescriptor().getLegendsDescriptor().setAuthorLayout(LegendsDescriptor.TOP);
      assertFalse(flagged.needsLegendCount(), "the author placed the legend");

      ChartVSAssemblyInfo idle = newChart();
      idle.setVizMark(VizMark.MODERN_LIGHT);
      assertFalse(idle.needsLegendCount(), "not pending");
   }

   // the gate off, so a chart is unmarked until a test marks it
   private static ChartVSAssemblyInfo newChart() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
      // creation asks for a count; these tests start with none pending
      info.clearLegendCountPending();
      return info;
   }

   private static String writeXml(ChartVSAssemblyInfo info) {
      StringWriter buf = new StringWriter();
      PrintWriter writer = new PrintWriter(buf);
      info.writeXML(writer);
      writer.flush();
      return buf.toString();
   }

   private static ChartVSAssemblyInfo parseInfo(String xml) throws Exception {
      ChartVSAssemblyInfo restored = new ChartVSAssemblyInfo();
      restored.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return restored;
   }
}
