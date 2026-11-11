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
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Dimension2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Moving, resizing and restoring a legend: each edit of the author's stamps its flag, and an
 * edit that changes nothing stamps nothing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendAuthorPathsTest {
   @Test
   void movingToAnotherEdgeStampsAndReseedsTheTitles() {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_BAR);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor color = legends.getColorLegendDescriptor();
      assertEquals(LegendsDescriptor.TOP, legends.getLayout(), "premise: a seeded top legend");
      assertFalse(color.isTitleVisible(), "premise: a hidden title");

      VSChartMoveLegendService.stampLayout(legends, info, LegendsDescriptor.RIGHT);

      assertEquals(LegendsDescriptor.RIGHT, legends.getLayout());
      assertTrue(legends.isUserLayout());
      assertTrue(color.isTitleVisible());
      assertFalse(color.isUserTitleVisible(), "the title follows the position, not the author");
   }

   @Test
   void droppingInPlaceOnTheSameEdgeStampsNothing() {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_BAR);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      LegendDescriptor color = legends.getColorLegendDescriptor();

      VSChartMoveLegendService.stampLayout(legends, info, LegendsDescriptor.TOP);

      assertEquals(LegendsDescriptor.TOP, legends.getLayout());
      assertFalse(legends.isUserLayout());
      assertFalse(color.isTitleVisible());
   }

   @Test
   void resizingStampsTheFlag() {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_BAR);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      assertFalse(legends.isUserLayout());

      VSChartResizeLegendService.setDockedSize(legends, 0.2, 0.1, 0);

      assertTrue(legends.isUserLayout());
      Dimension2D size = legends.getPreferredSize();
      assertNotNull(size);
      assertEquals(0.1, size.getHeight(), 0.0001);
   }

   @Test
   void showAllOnAModernChartUsesTheTypeDefault() {
      ChartVSAssemblyInfo pie = marked(GraphTypes.CHART_PIE);
      LegendsDescriptor pieLegends = pie.getChartDescriptor().getLegendsDescriptor();
      pieLegends.setAuthorLayout(LegendsDescriptor.NO_LEGEND);
      pie.clearLegendCountPending();

      VSChartLegendsVisibilityService.restoreLegendLayout(pie, pieLegends);

      assertEquals(LegendsDescriptor.RIGHT, pieLegends.getLayout());
      assertFalse(pieLegends.isUserLayout());
      assertTrue(pie.isLegendCountPending());

      ChartVSAssemblyInfo bar = marked(GraphTypes.CHART_BAR);
      LegendsDescriptor barLegends = bar.getChartDescriptor().getLegendsDescriptor();
      barLegends.setAuthorLayout(LegendsDescriptor.NO_LEGEND);

      VSChartLegendsVisibilityService.restoreLegendLayout(bar, barLegends);

      assertEquals(LegendsDescriptor.TOP, barLegends.getLayout());
      assertFalse(barLegends.isUserLayout());
   }

   @Test
   void showAllOnAClassicChartIsRight() {
      ChartVSAssemblyInfo info = chart(GraphTypes.CHART_BAR);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      legends.setLayout(LegendsDescriptor.NO_LEGEND);
      info.setVizMark(null);
      info.clearLegendCountPending();

      VSChartLegendsVisibilityService.restoreLegendLayout(info, legends);

      assertEquals(LegendsDescriptor.RIGHT, legends.getLayout());
      assertFalse(info.isLegendCountPending());
   }

   @Test
   void showAllLeavesAnExistingLayoutAlone() {
      ChartVSAssemblyInfo info = marked(GraphTypes.CHART_BAR);
      LegendsDescriptor legends = info.getChartDescriptor().getLegendsDescriptor();
      legends.setAuthorLayout(LegendsDescriptor.LEFT);

      VSChartLegendsVisibilityService.restoreLegendLayout(info, legends);

      assertEquals(LegendsDescriptor.LEFT, legends.getLayout());
      assertTrue(legends.isUserLayout());
   }

   private static ChartVSAssemblyInfo marked(int type) {
      ChartVSAssemblyInfo info = chart(type);
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.reseedLegendPlacement();
      info.reseedLegendTitles();
      return info;
   }

   private static ChartVSAssemblyInfo chart(int type) {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      vs.addAssembly(chart);
      chart.getVSAssemblyInfo().initDefaultFormat();
      ChartVSAssemblyInfo info = chart.getChartInfo();
      VSChartInfo cinfo = info.getVSChartInfo();
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, "state"));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue("state");
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, "v"));
      agg.setColumnValue("v");
      agg.setFormula(AggregateFormula.SUM);
      cinfo.addXField(dim);
      cinfo.addYField(agg);
      cinfo.setChartType(type);
      return info;
   }
}
