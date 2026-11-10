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
import inetsoft.graph.TitleSpec;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.guide.legend.Legend;
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
 * can hold must give way so the labels stay inside the axis, and a legend, title or axis label
 * gap must not leave the plot (or the legend/title text) with no room.
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

   /**
    * A large legend gap gives way to the legend content so the plot is kept, in all four
    * legend positions.
    */
   @Test
   void largeLegendGapKeepsPlotAndLegend() {
      int[] layouts = { GraphConstants.RIGHT, GraphConstants.LEFT, GraphConstants.TOP,
                        GraphConstants.BOTTOM };

      for(int layout : layouts) {
         for(int gap : new int[] { 200, 1000 }) {
            VGraph vgraph = legendChart(layout, gap);
            Rectangle2D plot = vgraph.getPlotBounds();
            LegendGroup group = vgraph.getLegendGroup();
            Legend legend = group.getLegend(0);
            String msg = "layout " + layout + " gap " + gap;

            assertTrue(plot.getWidth() > 0, msg + " plot width " + plot.getWidth());
            assertTrue(plot.getHeight() > 0, msg + " plot height " + plot.getHeight());
            assertTrue(legend.getBounds().getWidth() > 0, msg + " legend width");
            assertTrue(legend.getBounds().getHeight() > 0, msg + " legend height");
            assertTrue(group.getBounds().getWidth() >= group.getMinWidth() - 0.5,
                       msg + " legend group narrower than min");
            assertTrue(group.getBounds().getHeight() >= group.getMinHeight() - 0.5,
                       msg + " legend group shorter than min");
         }
      }
   }

   /**
    * A legend gap that fits is not changed.
    */
   @Test
   void legendGapKeptWhenItFits() {
      for(int gap : new int[] { 0, 50 }) {
         LegendGroup right = legendChart(GraphConstants.RIGHT, gap).getLegendGroup();
         assertEquals(gap, right.getLegend(0).getBounds().getX() - right.getBounds().getX(), 0.5,
                      "RIGHT gap " + gap);

         LegendGroup left = legendChart(GraphConstants.LEFT, gap).getLegendGroup();
         assertEquals(gap, left.getBounds().getWidth() - left.getLegend(0).getBounds().getWidth(),
                      0.5, "LEFT gap " + gap);

         LegendGroup top = legendChart(GraphConstants.TOP, gap).getLegendGroup();
         assertEquals(gap, top.getLegend(0).getBounds().getY() - top.getBounds().getY(), 0.5,
                      "TOP gap " + gap);

         LegendGroup bottom = legendChart(GraphConstants.BOTTOM, gap).getLegendGroup();
         assertEquals(gap, bottom.getBounds().getHeight() - bottom.getLegend(0).getBounds().getHeight(),
                      0.5, "BOTTOM gap " + gap);
      }
   }

   /**
    * A large title gap gives way to the title text, so the text is not truncated and stays in
    * its tile, for both the vertical (Y) and horizontal (X) title and both rotations.
    */
   @Test
   void largeTitleGapKeepsTextInTile() {
      for(int rotation : new int[] { 0, 90, -90 }) {
         for(int gap : new int[] { 50, 200, 300 }) {
            for(boolean ytitle : new boolean[] { true, false }) {
               VLabel title = title(ytitle, rotation, gap);
               String msg = (ytitle ? "Y" : "X") + " title rotation " + rotation + " gap " + gap;
               Rectangle2D tile = title.getBounds();
               Rectangle2D text = title.getTransformedBounds().getBounds2D();

               assertArrayEquals(new String[] { "Sales" }, title.getDisplayLabel(), msg);
               assertTrue(text.getX() >= tile.getX() - 0.5 && text.getMaxX() <= tile.getMaxX() + 0.5 &&
                          text.getY() >= tile.getY() - 0.5 && text.getMaxY() <= tile.getMaxY() + 0.5,
                          msg + " text " + text + " outside tile " + tile);
               assertTrue(tile.getX() >= 0 && tile.getMaxX() <= 400 &&
                          tile.getY() >= 0 && tile.getMaxY() <= 240,
                          msg + " tile " + tile + " outside the chart");
            }
         }
      }
   }

   /**
    * A title gap that fits is not changed.
    */
   @Test
   void titleGapKeptWhenItFits() {
      double base = title(true, 90, 0).getBounds().getWidth();
      assertEquals(base + 50, title(true, 90, 50).getBounds().getWidth(), 0.5);

      // the default rotation of the Y title for CJK locales
      double cjkBase = title(true, -90, 0).getBounds().getWidth();
      assertEquals(cjkBase + 50, title(true, -90, 50).getBounds().getWidth(), 0.5);

      double xbase = title(false, 0, 0).getBounds().getHeight();
      assertEquals(xbase + 20, title(false, 0, 20).getBounds().getHeight(), 0.5);
   }

   /**
    * An auto sized axis with a huge label gap must not take the whole chart, and the layout
    * must not recurse when it limits the gap (the gap is part of the auto size).
    */
   @Test
   void hugeLabelGapOnAutoSizedAxisKeepsPlot() {
      Rectangle2D yplot = plotWithLabelGap(false, 1000);
      assertTrue(yplot.getWidth() > 0, "plot width " + yplot.getWidth());

      Rectangle2D xplot = plotWithLabelGap(true, 1000);
      assertTrue(xplot.getHeight() > 0, "plot height " + xplot.getHeight());

      assertLabelsInside(linearY(1000, 0));
   }

   /**
    * A label gap above what the chart can spare for the labels is reduced even when the
    * plot did not collapse with it, and a gap below it is kept.
    */
   @Test
   void labelGapAboveCeilingIsReduced() {
      double plot50 = plotWithLabelGap(false, 50).getWidth();
      double plot400 = plotWithLabelGap(false, 400).getWidth();
      double plot1000 = plotWithLabelGap(false, 1000).getWidth();

      // 400 is more than the chart allows, so it takes no more space than 1000
      assertEquals(plot1000, plot400, 0.5);
      // but a gap that fits (50) is not reduced
      assertTrue(plot50 > plot400 + 1, "plot " + plot50 + " vs " + plot400);
      assertTrue(plot1000 > 0);
   }

   /**
    * The modern default label gaps (12/8/6) are kept on a chart with long rotated labels, which
    * take most of the room the labels are allowed.
    */
   @Test
   void defaultLabelGapKeptWithLongRotatedLabels() {
      for(int rotation : new int[] { 90, -90 }) {
         double h2 = plotWithLongLabels(false, rotation, 2).getHeight();

         assertEquals(h2 - 6, plotWithLongLabels(false, rotation, 8).getHeight(), 0.5,
                      "gap 8 rotation " + rotation);
         assertEquals(h2 - 10, plotWithLongLabels(false, rotation, 12).getHeight(), 0.5,
                      "gap 12 rotation " + rotation);
      }
   }

   /**
    * A huge gap on long rotated labels can't push the labels and the gap over the plot.
    */
   @Test
   void hugeLabelGapWithLongRotatedLabelsKeepsPlot() {
      for(int rotation : new int[] { 90, -90 }) {
         for(int gap : new int[] { 120, 400, 1000 }) {
            Rectangle2D plot = plotWithLongLabels(false, rotation, gap);
            assertTrue(plot.getHeight() > 0,
                       "rotation " + rotation + " gap " + gap + " plot height " + plot.getHeight());
         }
      }
   }

   /**
    * The same for long category labels on the vertical axis (horizontal bars): the default gaps
    * are kept and a huge gap keeps the plot.
    */
   @Test
   void longLabelsOnVerticalAxisKeepDefaultGapAndPlot() {
      double w2 = plotWithLongLabels(true, 0, 2).getWidth();

      assertEquals(w2 - 6, plotWithLongLabels(true, 0, 8).getWidth(), 0.5, "gap 8");
      assertEquals(w2 - 10, plotWithLongLabels(true, 0, 12).getWidth(), 0.5, "gap 12");

      for(int gap : new int[] { 400, 1000 }) {
         Rectangle2D plot = plotWithLongLabels(true, 0, gap);
         assertTrue(plot.getWidth() > 0, "gap " + gap + " plot width " + plot.getWidth());
      }
   }

   /**
    * @param vertical true to put the long categorical labels on the vertical axis.
    */
   private static Rectangle2D plotWithLongLabels(boolean vertical, int rotation, int gap) {
      Object[][] rows = new Object[7][];
      rows[0] = new Object[] { "State", "Sales" };

      for(int i = 1; i <= 6; i++) {
         rows[i] = new Object[] { "A very long category name number " + i, i * 1000 };
      }

      CategoricalScale cscale = new CategoricalScale("State");
      cscale.getAxisSpec().setLabelGap(gap);
      cscale.getAxisSpec().getTextSpec().setRotation(rotation);

      EGraph graph = new EGraph();

      if(vertical) {
         graph.addElement(new PointElement("Sales", "State"));
         graph.setCoordinate(new RectCoord(new LinearScale("Sales"), cscale));
      }
      else {
         graph.addElement(new IntervalElement("State", "Sales"));
         graph.setCoordinate(new RectCoord(cscale, new LinearScale("Sales")));
      }

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240)
         .getPlotBounds();
   }

   private static VGraph legendChart(int layout, int gap) {
      Object[][] rows = { { "State", "Sales" }, { "NJ", 200 }, { "NY", 3000 }, { "CA", 12000 } };
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("State", "Sales");
      CategoricalColorFrame color = new CategoricalColorFrame("State");
      LegendSpec spec = new LegendSpec();
      spec.setGap(gap);
      color.setLegendSpec(spec);
      elem.setColorFrame(color);
      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      graph.setLegendLayout(layout);
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
   }

   private static VLabel title(boolean ytitle, int rotation, int gap) {
      Object[][] rows = { { "State", "Sales" }, { "NJ", 200 }, { "NY", 3000 } };
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Sales"));
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      TitleSpec spec = new TitleSpec();
      spec.setLabel("Sales");
      spec.setLabelGap(gap);
      spec.getTextSpec().setRotation(rotation);

      if(ytitle) {
         graph.setYTitleSpec(spec);
      }
      else {
         graph.setXTitleSpec(spec);
      }

      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
      return ytitle ? vgraph.getYTitle() : vgraph.getXTitle();
   }

   private static Rectangle2D plotWithLabelGap(boolean xaxis, int gap) {
      Object[][] rows = { { "State", "Sales" }, { "NJ", 200 }, { "NY", 3000 }, { "CA", 12000 } };
      CategoricalScale xscale = new CategoricalScale("State");
      LinearScale yscale = new LinearScale("Sales");
      (xaxis ? xscale : yscale).getAxisSpec().setLabelGap(gap);
      yscale.getAxisSpec().setTickVisible(false);

      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Sales"));
      graph.setCoordinate(new RectCoord(xscale, yscale));
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240)
         .getPlotBounds();
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
