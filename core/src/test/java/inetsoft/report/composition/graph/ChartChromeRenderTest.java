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
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.StyleConstants;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.CompositeValue;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The facet panel separators of a modern chart: they follow the value-axis line, but a line that
 * is off only because of the modern seed must not remove them, while an author-hidden line does.
 * And in max mode a modern chart's axis title follows its normal visibility.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartChromeRenderTest {
   @Test
   void aModernFacetKeepsItsPanelSeparators() {
      int expected = outerXGridStyle(VizMark.MODERN_LIGHT, true, false);
      assertEquals(StyleConstants.THIN_LINE, expected);
      assertEquals(expected, outerXGridStyle(VizMark.MODERN_LIGHT, false, false));
   }

   @Test
   void anAuthorHiddenValueLineStillRemovesThem() {
      assertEquals(GraphConstants.NONE, outerXGridStyle(VizMark.MODERN_LIGHT, false, true));
   }

   @Test
   void aClassicFacetIsUnchanged() {
      assertEquals(GraphConstants.NONE, outerXGridStyle(null, false, false));
      assertEquals(StyleConstants.THIN_LINE, outerXGridStyle(null, true, false));
   }

   @Test
   void aSeedHiddenTitleDrawsNoLabelInModernMaxMode() {
      assertNull(maxModeXTitleLabel(VizMark.MODERN_LIGHT));
   }

   @Test
   void aClassicChartReadsTheStoredMaxModeTitleValue() {
      assertEquals("State", maxModeXTitleLabel(null));
   }

   // a maximized bar whose x title is off at the default tier only, as the modern seed leaves it
   private static String maxModeXTitleLabel(VizMark mark) {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[]{ "State", "Sum(Total)" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[]{ i % 3 == 0 ? "CA" : (i % 3 == 1 ? "NY" : "TX"),
                                 (double) (i * 100) };
      }

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      total.setFormulaValue("Sum");

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
      cinfo.addYField(total);
      cinfo.updateChartType(false);

      ChartDescriptor desc = new ChartDescriptor();
      TitleDescriptor xtitle = desc.getTitlesDescriptor().getXTitleDescriptor();
      xtitle.setVisible(false, CompositeValue.Type.DEFAULT);
      assertTrue(xtitle.isMaxModeVisible(), "premise: the stored max-mode value is on");

      Viewsheet vs = new Viewsheet();
      vs.setMaxMode(true);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(desc);
      assemblyInfo.setVizMark(mark);
      assemblyInfo.setViewsheet(vs);

      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();
      return graph.getXTitleSpec().getLabel();
   }

   private static int outerXGridStyle(VizMark mark, boolean lineVisible, boolean author) {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[]{ "Region", "State", "Sum(Total)" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[]{ i % 2 == 0 ? "East" : "West",
                                 i % 3 == 0 ? "CA" : (i % 3 == 1 ? "NY" : "TX"),
                                 (double) (i * 100) };
      }

      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      total.setFormulaValue("Sum");

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(new VSChartDimensionRef(new BaseField("Region")));
      cinfo.addXField(new VSChartDimensionRef(new BaseField("State")));
      cinfo.addYField(total);
      cinfo.updateChartType(false);

      for(AxisDescriptor axis : new AxisDescriptor[]{ cinfo.getAxisDescriptor(),
                                                      total.getAxisDescriptor() })
      {
         if(author) {
            axis.setAuthorLineVisible(lineVisible);
         }
         else {
            axis.setLineVisible(lineVisible);
         }
      }

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);

      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null).createEGraph();
      FacetCoord facet = (FacetCoord) graph.getCoordinate();
      RectCoord outer = (RectCoord) facet.getOuterCoordinate();
      return outer.getXScale().getAxisSpec().getGridStyle();
   }
}
