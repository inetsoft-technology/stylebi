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
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A chart bound to a new source drops the author's plot resize, its stored percents included.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartVSAssemblyInfoResizeResetTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aNewSourceClearsTheResizeAndItsPercents() {
      ChartVSAssemblyInfo info = chart("Orders");

      info.copyInputDataInfo(chart("Sales"), 0);

      VSChartInfo cinfo = info.getVSChartInfo();
      assertFalse(cinfo.isWidthResized() || cinfo.isHeightResized(), "flags");
      assertEquals(1, cinfo.getUnitWidthRatio(), 0, "width ratio");
      assertEquals(1, cinfo.getUnitHeightRatio(), 0, "height ratio");
      assertEquals(0, cinfo.getUnitWidthRatioPercent(), 0, "width percent");
      assertEquals(0, cinfo.getUnitHeightRatioPercent(), 0, "height percent");
   }

   @Test
   void theSameSourceKeepsTheResize() {
      ChartVSAssemblyInfo info = chart("Orders");

      info.copyInputDataInfo(chart("Orders"), 0);

      VSChartInfo cinfo = info.getVSChartInfo();
      assertTrue(cinfo.isWidthResized() && cinfo.isHeightResized(), "flags");
      assertEquals(3, cinfo.getUnitWidthRatio(), 0, "width ratio");
      assertEquals(1.5, cinfo.getUnitWidthRatioPercent(), 0, "width percent");
      assertEquals(1.25, cinfo.getUnitHeightRatioPercent(), 0, "height percent");
   }

   // a bar chart on the source, its plot resized on both axes
   private static ChartVSAssemblyInfo chart(String source) {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setWidthResized(true);
      cinfo.setHeightResized(true);
      cinfo.setUnitWidthRatio(3);
      cinfo.setUnitHeightRatio(2);
      cinfo.setUnitWidthRatioPercent(1.5);
      cinfo.setUnitHeightRatioPercent(1.25);

      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(cinfo);
      info.setSourceInfo(new SourceInfo(SourceInfo.ASSET, null, source));
      return info;
   }
}
