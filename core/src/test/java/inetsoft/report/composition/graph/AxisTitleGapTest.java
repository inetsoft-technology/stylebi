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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The card's axis-title gap on a marked chart: 4px when the axis draws its labels, and the
 * plot-adjacent 8px when it does not, so a hidden label band hands its gap to the title rather than
 * leaving an empty stub. A separated chart keeps its axis visibility on the bound field's own
 * descriptor - which is also where the UI writes it - so resolving the gap from the chart-level
 * descriptor would answer with the untouched default whatever the author did.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AxisTitleGapTest {
   @Test
   void separatedChartTakesTheTitleGapWhenItsAxisLabelsAreDrawn() {
      assertEquals(4, xTitleGap(true, VizMark.MODERN_LIGHT));
   }

   @Test
   void separatedChartInheritsTheLabelGapWhenItsAxisLabelsAreHidden() {
      assertEquals(8, xTitleGap(false, VizMark.MODERN_LIGHT));
   }

   @Test
   void unmarkedChartKeepsItsUnsetTitleGapEitherWay() {
      assertEquals(0, xTitleGap(true, null));
      assertEquals(0, xTitleGap(false, null));
   }

   @Test
   void markedChartWithACustomTitleResolvesTheCardGap() {
      // a custom title had never taken the descriptor's gap; a marked chart must still get it
      assertEquals(4, xTitleGap(true, VizMark.MODERN_LIGHT, "Custom X Title", 0));
   }

   @Test
   void unmarkedChartWithACustomTitleAndADescriptorGapStaysAtZero() {
      // the regression guard: an unmarked chart's custom title must not start applying a descriptor
      // gap it never applied before. The gap is written here on the user tier, but the guard is
      // tier-agnostic - what reaches this in the field is a CSS label_gap
      assertEquals(0, xTitleGap(true, null, "Custom X Title", 99));
   }

   /**
    * Build a separated bar chart of one x dimension by one y measure, hide or show the x axis labels
    * on the dimension's own descriptor, and report the gap the x axis title ends up with.
    */
   private static int xTitleGap(boolean labelVisible, VizMark mark) {
      return xTitleGap(labelVisible, mark, null, 0);
   }

   /**
    * Same as {@link #xTitleGap(boolean, VizMark)}, but also allows setting a custom axis title and
    * a descriptor-level label gap, to exercise the custom-title branch of getTitleSpec.
    */
   private static int xTitleGap(boolean labelVisible, VizMark mark, String customTitle,
                                int titleGap)
   {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[]{ "State", "Total" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[]{ i % 3 == 0 ? "CA" : (i % 3 == 1 ? "NY" : "TX"),
                                 (double) (i * 100) };
      }

      VSChartDimensionRef state = new VSChartDimensionRef(new BaseField("State"));
      state.getAxisDescriptor().setLabelVisible(labelVisible);

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setSeparatedGraph(true);
      cinfo.setMultiStyles(false);
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.updateChartType(false);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      ChartDescriptor chartDesc = new ChartDescriptor();

      if(customTitle != null) {
         TitleDescriptor xTitleDesc = chartDesc.getTitlesDescriptor().getXTitleDescriptor();
         xTitleDesc.setTitle(customTitle);
         xTitleDesc.setLabelGap(titleGap);
      }

      assemblyInfo.setChartDescriptor(chartDesc);
      assemblyInfo.setVizMark(mark);

      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();

      return graph.getXTitleSpec().getLabelGap();
   }
}
