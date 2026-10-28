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
package inetsoft.graph.guide.axis;

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.legend.LegendGroup;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
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
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79381: a label gap ("Label Distance from Axis") larger than a fixed (user-resized) axis
 * can hold must give way so the labels stay inside the axis, and a legend gap must not leave the
 * legend with a non-positive width.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class AxisLabelGapShrinkTest {
   /**
    * Linear Y of a bar chart as GraphGenerator builds it by default: AxisDescriptor ticks are
    * hidden by default, so the labels are placed by the label gap.
    */
   @Test
   void linearYLabelsStayInsideNarrowedAxis() {
      DefaultAxis axis = linearY(100, 60);
      assertLabelsInside(axis);
   }

   /**
    * The reporter's chart: a modern bar chart built by GraphGenerator, with the measure's
    * "Label Distance from Axis" set to 100 and the Y axis dragged to 60px.
    */
   @Test
   void modernBarChartMeasureLabelsStayInsideNarrowedAxis() {
      Object[][] rows = { { "State", "Total" }, { "CA", 200.0 }, { "NY", 3000.0 },
                          { "TX", 12000.0 } };
      VSChartDimensionRef state = new VSChartDimensionRef(new BaseField("State"));
      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_BAR);
      cinfo.setRTChartType(GraphTypes.CHART_BAR);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.updateChartType(false);

      for(AxisDescriptor ydesc : new AxisDescriptor[] {
         total.getAxisDescriptor(), cinfo.getAxisDescriptor() })
      {
         ydesc.setLabelGap(100, CompositeValue.Type.USER);
         ydesc.setAxisWidth(60);
      }

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(VizMark.MODERN_LIGHT);
      Viewsheet vs = new Viewsheet();
      vs.getViewsheetInfo().setVizDensity("compact");
      assemblyInfo.setViewsheet(vs);

      DefaultDataSet data = new DefaultDataSet(rows);
      EGraph graph = GraphGenerator.getGenerator(
         assemblyInfo, null, data, new VariableTable(), null, XSourceInfo.NONE, null)
         .createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(data, 0, 0, 500, 400);
      DefaultAxis yaxis = ((RectCoord) vgraph.getCoordinate()).getYAxis1();

      AxisSpec yspec = yaxis.getScale().getAxisSpec();
      assertEquals(100, yspec.getLabelGap(), "author gap reaches the axis");
      // ticks are hidden by default, so the linear labels are placed by the gap
      assertFalse(yspec.isTickVisible(), "default measure axis ticks");
      assertEquals(60, yaxis.getBounds().getWidth(), 0.5, "user axis width");
      assertLabelsInside(yaxis);
   }

   @Test
   void linearYLabelsStayInsideVeryNarrowAxis() {
      assertLabelsInside(linearY(50, 40));
   }

   @Test
   void categoricalYLabelsKeepLegacyRoomInNarrowedAxis() {
      DefaultAxis gap100 = categoricalY(100, 60);
      DefaultAxis gap2 = categoricalY(2, 60);
      assertLabelsInside(gap100);
      VLabel[] l100 = visibleLabels(gap100);
      VLabel[] l2 = visibleLabels(gap2);
      assertEquals(l2.length, l100.length);

      // the gap gives way, so the labels get the same (truncated) room as with the legacy gap
      for(int i = 0; i < l100.length; i++) {
         assertEquals(l2[i].getBounds().getWidth(), l100[i].getBounds().getWidth(), 0.5,
                      "label " + i + " width");
      }
   }

   @Test
   void authorGapHonouredWhenAxisIsWideEnough() {
      // 140 >= 100 + label (~28) + line, so the full author gap is kept
      DefaultAxis axis = linearY(100, 140);
      assertLabelsInside(axis);
      double axisRight = axis.getBounds().getMaxX();

      for(VLabel label : visibleLabels(axis)) {
         assertEquals(100, axisRight - label.getBounds().getMaxX(), 0.5,
                      "gap between label and axis line");
      }

      // auto size on a roomy chart reserves the gap too
      DefaultAxis auto = linearY(100, 0);
      double autoRight = auto.getBounds().getMaxX();

      for(VLabel label : visibleLabels(auto)) {
         assertEquals(100, autoRight - label.getBounds().getMaxX(), 0.5,
                      "gap between label and axis line (auto size)");
      }
   }

   @Test
   void legendKeepsPositiveWidthWhenGapExceedsSize() {
      Object[][] rows = { { "State", "Sales" }, { "NJ", 200 }, { "NY", 3000 }, { "CA", 12000 } };
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("State", "Sales");
      CategoricalColorFrame color = new CategoricalColorFrame("State");
      LegendSpec spec = new LegendSpec();
      spec.setGap(50);
      color.setLegendSpec(spec);
      elem.setColorFrame(color);
      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      graph.setLegendLayout(GraphConstants.RIGHT);
      graph.setLegendPreferredSize(40);

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 500, 400);
      LegendGroup group = vgraph.getLegendGroup();
      double legendWidth = group.getLegend(0).getBounds().getWidth();

      assertTrue(legendWidth > 0, "legend width " + legendWidth);
      assertTrue(group.getBounds().getWidth() >= group.getMinWidth() - 0.5,
                 "legend group " + group.getBounds().getWidth() + " < min " + group.getMinWidth());
   }

   private static DefaultAxis linearY(int gap, double size) {
      Object[][] rows = { { "State", "Sales" }, { "NJ", 200 }, { "NY", 3000 }, { "CA", 12000 } };
      LinearScale yscale = new LinearScale("Sales");
      AxisSpec spec = yscale.getAxisSpec();
      spec.setTickVisible(false); // AxisDescriptor.ticksVisible defaults to false
      spec.setLabelGap(gap);
      spec.setAxisSize(size);

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Sales"));
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), yscale));
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 500, 400);
      return ((RectCoord) vgraph.getCoordinate()).getYAxis1();
   }

   private static DefaultAxis categoricalY(int gap, double size) {
      Object[][] rows = { { "State", "Sales" }, { "New Jersey Region", 200 },
                          { "New York Metro Area", 3000 }, { "California", 12000 } };
      CategoricalScale yscale = new CategoricalScale("State");
      yscale.getAxisSpec().setLabelGap(gap);
      yscale.getAxisSpec().setAxisSize(size);

      EGraph graph = new EGraph();
      graph.addElement(new PointElement("Sales", "State"));
      graph.setCoordinate(new RectCoord(new LinearScale("Sales"), yscale));
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 500, 400);
      return ((RectCoord) vgraph.getCoordinate()).getYAxis1();
   }

   private static VLabel[] visibleLabels(DefaultAxis axis) {
      return java.util.Arrays.stream(axis.getLabels())
         .filter(l -> l != null && l.getZIndex() >= 0)
         .toArray(VLabel[]::new);
   }

   private static void assertLabelsInside(DefaultAxis axis) {
      Rectangle2D ab = axis.getBounds();
      VLabel[] labels = visibleLabels(axis);
      assertTrue(labels.length > 0, "axis has no visible labels");

      for(VLabel label : labels) {
         Rectangle2D lb = label.getBounds();
         assertTrue(lb.getWidth() > 0, "label '" + label.getText() + "' width " + lb.getWidth());
         assertTrue(lb.getX() >= ab.getX() - 0.5 && lb.getMaxX() <= ab.getMaxX() + 0.5,
                    "label '" + label.getText() + "' x [" + lb.getX() + ", " + lb.getMaxX() +
                    "] outside axis [" + ab.getX() + ", " + ab.getMaxX() + "]");
      }
   }
}
