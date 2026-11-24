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
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.internal.ICoordinate;
import inetsoft.graph.scale.LinearScale;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked bar chart keeps the 0 label of its value axis when the labels are thinned; a
 * classic bar thins them as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernZeroLabelTest {
   // below 80 the axis has under 5px a tick and layout skips labels before thinning
   private static final int[] HEIGHTS = { 80, 100, 120, 140, 160, 180, 200, 240 };

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedBarKeepsZeroWhereClassicLosesIt() {
      boolean classicLost = false;

      for(int h : HEIGHTS) {
         List<Double> modern = shown(VizMark.MODERN_LIGHT, h);
         List<Double> classic = shown(null, h);

         assertTrue(modern.contains(0.0), "height " + h + " " + modern);
         classicLost |= !classic.contains(0.0);
      }

      assertTrue(classicLost, "premise: a classic card that loses 0");
   }

   // the value axis labels of a bar over -300..300 by 100 at 300 x height
   private static List<Double> shown(VizMark mark, int height) {
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.setSeparatedGraph(false);
      VSChartDimensionRef state = new VSChartDimensionRef(new BaseField("State"));
      state.setGroupColumnValue("State");
      VSChartAggregateRef qty = new VSChartAggregateRef();
      BaseField field = new BaseField("Quantity");
      field.setDataType(XSchema.DOUBLE);
      qty.setDataRef(field);
      qty.setFormulaValue("Sum");
      cinfo.addXField(state);
      cinfo.addYField(qty);
      cinfo.updateChartType(false);
      Object[][] rows = { { "State", GraphUtil.getName(qty) }, { "A", -225.0 },
                          { "B", 295.0 }, { "C", 35.0 } };
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      Dimension size = new Dimension(300, height);
      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, size).createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, size.width, size.height);
      DefaultAxis axis = ((RectCoord) vgraph.getCoordinate()).getAxisAt(ICoordinate.LEFT_AXIS);
      assertInstanceOf(LinearScale.class, axis.getScale(), "premise: the value axis");
      axis.prepareLabels();
      List<Double> list = new ArrayList<>();

      for(VLabel label : axis.getLabels()) {
         if(label.getZIndex() >= 0) {
            list.add(((Number) label.getValue()).doubleValue() + 0.0);
         }
      }

      return list;
   }
}
