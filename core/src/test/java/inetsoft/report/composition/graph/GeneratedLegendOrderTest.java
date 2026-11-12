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

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.legend.Legend;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.guide.legend.LegendItem;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The order of a modern top legend on bar charts built by the generator: a bar whose color field
 * is also on X reads as a cluster, a real stack lists the top segment first.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class GeneratedLegendOrderTest {
   @Test
   void aBarWithItsColorFieldOnXReadsLeftToRight() {
      assertEquals("A", leftmost(items(true)));
   }

   @Test
   void aRealStackListsTheTopSegmentFirst() {
      assertEquals("C", leftmost(items(false)));
   }

   private static List<LegendItem> items(boolean regionOnX) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Year", "Region", "Total" });

      for(String year : new String[] { "2020", "2021" }) {
         for(String region : new String[] { "A", "B", "C" }) {
            rows.add(new Object[] { year, region, 10.0 * (region.charAt(0) - 'A' + 1) });
         }
      }

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setMultiStyles(false);
      cinfo.setChartType(GraphTypes.CHART_BAR_STACK);
      cinfo.setRTChartType(GraphTypes.CHART_BAR_STACK);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("Year")));

      if(regionOnX) {
         cinfo.addXField(new VSChartDimensionRef(new BaseField("Region")));
      }

      cinfo.addYField(total);
      VSAestheticRef color = new VSAestheticRef();
      color.setALLDataRef(new VSChartDimensionRef(new BaseField("Region")));
      color.setVisualFrameWrapper(new CategoricalColorFrameWrapper());
      cinfo.setColorField(color);
      cinfo.updateChartType(false);

      ChartDescriptor chartDesc = new ChartDescriptor();
      chartDesc.getLegendsDescriptor().setAuthorLayout(GraphConstants.TOP);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(chartDesc);
      assemblyInfo.setVizMark(VizMark.MODERN_LIGHT);
      assemblyInfo.setViewsheet(new Viewsheet());

      GraphGenerator generator = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows.toArray(new Object[0][])),
         new VariableTable(), null, XSourceInfo.NONE, null);
      EGraph graph = generator.createEGraph();
      assertTrue(graph.getElement(0).isStack(), "premise: the generator stacks the bar");
      assertTrue(graph.isLegendInMarkOrder(), "premise: a modern chart");
      assertEquals(GraphConstants.TOP, graph.getLegendLayout(), "premise: a top legend");
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(generator.getData(), 0, 0, 800, 400);
      List<LegendItem> result = new ArrayList<>();
      LegendGroup group = vg.getLegendGroup();

      for(int i = 0; i < group.getLegendCount(); i++) {
         Legend legend = group.getLegend(i);

         for(int j = 0; j < legend.getVisualCount(); j++) {
            if(legend.getVisual(j) instanceof LegendItem) {
               result.add((LegendItem) legend.getVisual(j));
            }
         }
      }

      assertEquals(3, result.size());
      return result;
   }

   private static String leftmost(List<LegendItem> items) {
      return String.valueOf(items.stream()
         .min(Comparator.comparingDouble(i -> i.getBounds().getX())).get().getValue());
   }
}
