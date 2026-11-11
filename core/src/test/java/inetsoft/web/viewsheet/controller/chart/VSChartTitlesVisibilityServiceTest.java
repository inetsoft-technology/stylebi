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

import inetsoft.sree.SreeEnv;
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
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Show All Titles": in max mode a modern chart's titles follow their normal visibility, so the
 * show writes it too; a classic chart keeps writing the max-mode value only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSChartTitlesVisibilityServiceTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void showAllTitlesInMaxModeShowsThemOnAModernChart() {
      ChartVSAssemblyInfo info = bar(true);
      VizContext ctx = VizContext.of(info);
      TitlesDescriptor titles = info.getChartDescriptor().getTitlesDescriptor();
      assertFalse(titles.getXTitleDescriptor().isMaxModeVisible(ctx), "premise: the seed hid x");
      assertFalse(titles.getYTitleDescriptor().isMaxModeVisible(ctx), "premise: the seed hid y");

      VSChartTitlesVisibilityService.showTitles(info, true);

      assertTrue(titles.getXTitleDescriptor().isMaxModeVisible(ctx), "x");
      assertTrue(titles.getYTitleDescriptor().isMaxModeVisible(ctx), "y");
   }

   @Test
   void showAllTitlesInMaxModeOnAModernChartWritesTheRuntimeCopyToo() {
      ChartVSAssemblyInfo info = bar(true);
      VizContext ctx = VizContext.of(info);
      ChartDescriptor rdesc = (ChartDescriptor) info.getChartDescriptor().clone();
      info.setRTChartDescriptor(rdesc);

      VSChartTitlesVisibilityService.showTitles(info, true);

      assertTrue(rdesc.getTitlesDescriptor().getXTitleDescriptor().isMaxModeVisible(ctx));
      assertTrue(rdesc.getTitlesDescriptor().getYTitleDescriptor().isMaxModeVisible(ctx));
   }

   @Test
   void showAllTitlesInModernMaxModeShowsAMeasuresOwnTitle() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      RadarVSChartInfo radar = new RadarVSChartInfo();
      info.setVSChartInfo(radar);
      VSChartAggregateRef v = agg("v");
      radar.setRTYFields(new ChartRef[] { v });
      TitleDescriptor title = v.getTitleDescriptor();
      title.setVisible(false);
      VizContext ctx = VizContext.of(info);
      assertFalse(title.isMaxModeVisible(ctx), "premise: max mode follows the hidden title");

      VSChartTitlesVisibilityService.showTitles(info, true);

      assertTrue(title.isMaxModeVisible(ctx));
   }

   @Test
   void showAllTitlesInMaxModeOnAClassicChartWritesMaxModeOnly() {
      ChartVSAssemblyInfo info = bar(false);
      TitlesDescriptor titles = info.getChartDescriptor().getTitlesDescriptor();
      TitleDescriptor[] all = { titles.getXTitleDescriptor(), titles.getX2TitleDescriptor(),
                                titles.getYTitleDescriptor(), titles.getY2TitleDescriptor() };

      for(TitleDescriptor title : all) {
         // as a hide in max mode leaves it
         title.setMaxModeVisible(false);
      }

      VSChartTitlesVisibilityService.showTitles(info, true);

      for(int i = 0; i < all.length; i++) {
         assertTrue(all[i].isMaxModeVisible(), "title " + i);
         assertFalse(all[i].hasVisibleUserValue(), "title " + i);
      }
   }

   // ---- harness -----------------------------------------------------------------------------

   // a seeded bar of state by Sum(v), marked when the gate is on at creation
   private static ChartVSAssemblyInfo bar(boolean marked) {
      SreeEnv.setProperty("viewsheet.modernVisualization", marked ? "true" : "false");
      ChartVSAssemblyInfo info = newChart();
      VSChartInfo cinfo = info.getVSChartInfo();
      cinfo.addXField(dim("state"));
      cinfo.addYField(agg("v"));
      cinfo.setChartType(GraphTypes.CHART_BAR);
      VizModernizeUtil.reseedAfterRestore(info);
      assertEquals(marked, VizContext.of(info).modern, "premise: the chart's mark");
      return info;
   }

   // marked when the gate is on
   private static ChartVSAssemblyInfo newChart() {
      ChartVSAssembly chart = new ChartVSAssembly(new Viewsheet(), "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new AttributeRef(null, name));
      dim.setDataType(XSchema.STRING);
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new AttributeRef(null, name));
      agg.setColumnValue(name);
      agg.setFormula(AggregateFormula.SUM);
      return agg;
   }
}
