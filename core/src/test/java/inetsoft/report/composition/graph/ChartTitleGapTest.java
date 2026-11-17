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
package inetsoft.report.composition.graph;

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The gap a marked chart keeps between its title lane and its plot.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartTitleGapTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedTitledChartGapsByItsTopPadding() {
      assertEquals(12, VGraphPair.titleGap(info(bar(), VizMark.MODERN_LIGHT, 12, true)));
   }

   @Test
   void anAuthorsPaddingIsTheGap() {
      assertEquals(20, VGraphPair.titleGap(info(bar(), VizMark.MODERN_LIGHT, 20, true)));
      assertEquals(0, VGraphPair.titleGap(info(bar(), VizMark.MODERN_LIGHT, 0, true)));
   }

   @Test
   void aHiddenTitleAddsNoGap() {
      assertEquals(0, VGraphPair.titleGap(info(bar(), VizMark.MODERN_LIGHT, 12, false)));
   }

   @Test
   void aContourMapHasNoGap() {
      VSMapInfo contour = new VSMapInfo();
      contour.setRTChartType(GraphTypes.CHART_MAP_CONTOUR);
      VSMapInfo map = new VSMapInfo();
      map.setRTChartType(GraphTypes.CHART_MAP);

      assertEquals(0, VGraphPair.titleGap(info(contour, VizMark.MODERN_LIGHT, 12, true)));
      assertEquals(12, VGraphPair.titleGap(info(map, VizMark.MODERN_LIGHT, 12, true)));
   }

   @Test
   void classicChartsHaveNoGap() {
      assertEquals(0, VGraphPair.titleGap(info(bar(), null, 12, true)));
   }

   private static VSChartInfo bar() {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      return cinfo;
   }

   private static ChartVSAssemblyInfo info(VSChartInfo cinfo, VizMark mark, int top,
                                           boolean titleVisible)
   {
      ChartVSAssemblyInfo ainfo = new ChartVSAssemblyInfo();
      ainfo.setVSChartInfo(cinfo);
      ainfo.setVizMark(mark);
      ainfo.setPadding(new Insets(top, 12, 12, 12));
      ainfo.setTitleVisible(titleVisible);
      return ainfo;
   }
}
