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
package inetsoft.web.viewsheet.controller.chart;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An author's plot resize stores each axis's ratio as a percent of that axis's initial
 * ratio, and Reset Size clears it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartPlotResizeServiceTest {
   private static final double INITIAL_W = 2;
   private static final double INITIAL_H = 4;

   @Test
   void aWidthResizeSetsTheWidthPercent() {
      VSChartInfo info = chart(GraphTypes.CHART_BAR);

      VSChartPlotResizeService.resizePlot(info, false, 3, false);

      assertTrue(info.isWidthResized());
      assertFalse(info.isHeightResized());
      assertEquals(3, info.getUnitWidthRatio(), 1e-9);
      assertEquals(1.5, info.getUnitWidthRatioPercent(), 1e-9);
      assertEquals(0, info.getUnitHeightRatioPercent(), 0);
   }

   @Test
   void aHeightResizeSetsTheHeightPercent() {
      VSChartInfo info = chart(GraphTypes.CHART_BAR);

      VSChartPlotResizeService.resizePlot(info, false, 6, true);

      assertTrue(info.isHeightResized());
      assertFalse(info.isWidthResized());
      assertEquals(6, info.getUnitHeightRatio(), 1e-9);
      assertEquals(1.5, info.getUnitHeightRatioPercent(), 1e-9, "the height percent");
      assertEquals(0, info.getUnitWidthRatioPercent(), 0, "the width percent");
   }

   @Test
   void aCircleResizeSetsBothPercents() {
      VSChartInfo info = chart(GraphTypes.CHART_CIRCULAR);

      VSChartPlotResizeService.resizePlot(info, false, 6, false);

      assertTrue(info.isWidthResized() && info.isHeightResized());
      assertEquals(6 / INITIAL_W, info.getUnitWidthRatioPercent(), 1e-9, "width");
      assertEquals(6 / INITIAL_H, info.getUnitHeightRatioPercent(), 1e-9, "height");
   }

   @Test
   void resetSizeClearsBothPercents() {
      for(int type : new int[] { GraphTypes.CHART_BAR, GraphTypes.CHART_NETWORK,
                                 GraphTypes.CHART_CIRCULAR })
      {
         VSChartInfo info = chart(type);
         VSChartPlotResizeService.resizePlot(info, false, 3, false);
         VSChartPlotResizeService.resizePlot(info, false, 6, true);
         assertTrue(info.getUnitWidthRatioPercent() > 0, "premise: a width percent, " + type);

         VSChartPlotResizeService.resizePlot(info, true, 0, false);

         assertFalse(info.isWidthResized() || info.isHeightResized(), "flags, " + type);
         assertEquals(1, info.getUnitWidthRatio(), 0, "width ratio, " + type);
         assertEquals(1, info.getUnitHeightRatio(), 0, "height ratio, " + type);
         assertEquals(0, info.getUnitWidthRatioPercent(), 0, "width percent, " + type);
         assertEquals(0, info.getUnitHeightRatioPercent(), 0, "height percent, " + type);
      }
   }

   private static VSChartInfo chart(int type) {
      VSChartInfo info = type == GraphTypes.CHART_BAR ? new DefaultVSChartInfo()
         : new RelationVSChartInfo();
      info.setChartType(type);
      info.setRTChartType(type);
      info.setInitialWidthRatio(INITIAL_W);
      info.setInitialHeightRatio(INITIAL_H);
      return info;
   }
}
