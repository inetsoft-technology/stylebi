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
package inetsoft.graph.coord;

import inetsoft.graph.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.guide.VLabel;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.graph.visual.LineVO;
import inetsoft.graph.visual.VisualObject;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82034: a one-measure, one-group radar (OneVarParallelCoord) with "Ignore Null"
 * (Scale.NO_NULL) must keep one parallel scale per label value, including a null label.
 * The generator path reaches ConfigurationContext, so the class sets up a Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class OneVarParallelCoordNullLabelTest {
   private static Object[][] rows(Object... states) {
      Object[][] r = new Object[states.length + 1][];
      r[0] = new Object[] { "State", "Total" };

      for(int i = 0; i < states.length; i++) {
         r[i + 1] = new Object[] { states[i], (double) (i + 1) * 10 };
      }

      return r;
   }

   private static OneVarParallelCoord coord(DataSet data, boolean noNull) {
      OneVarParallelCoord c = new OneVarParallelCoord("Total", "State", new LinearScale("Total"));

      if(noNull) {
         Scale s = c.getAxisLabelScale();
         s.setScaleOption(s.getScaleOption() | Scale.NO_NULL);
      }

      c.init(data);
      return c;
   }

   private static String desc(OneVarParallelCoord c) {
      return "labels=" + Arrays.toString(c.getAxisLabelScale().getValues()) +
         " parallelScales=" + c.getParallelScales().length;
   }

   private static void assertOneScalePerLabel(OneVarParallelCoord c) {
      assertEquals(c.getAxisLabelScale().getValues().length, c.getParallelScales().length,
                   desc(c));
   }

   @Test
   void ignoreNullWithOnlyNullGroupsKeepsOneScalePerLabel() {
      OneVarParallelCoord c = coord(new DefaultDataSet(rows(null, null)), true);

      assertArrayEquals(new Object[] { null }, c.getAxisLabelScale().getValues());
      assertOneScalePerLabel(c);

      double idx = c.getAxisLabelScale().map(null);
      Point2D pos = assertDoesNotThrow(() -> c.getPosition(new double[] { idx, 10 }));
      assertNotNull(pos);
   }

   @Test
   void ignoreNullWithMixedGroupsDropsNullLabel() {
      OneVarParallelCoord c = coord(new DefaultDataSet(rows(null, "CA", "NY")), true);

      assertArrayEquals(new Object[] { "CA", "NY" }, c.getAxisLabelScale().getValues());
      assertOneScalePerLabel(c);
   }

   @Test
   void onlyNullGroupsWithoutIgnoreNullKeepsOneScalePerLabel() {
      OneVarParallelCoord c = coord(new DefaultDataSet(rows(null, null)), false);

      assertOneScalePerLabel(c);
   }

   @Test
   void scriptLabelValuesWithNullAndIgnoreNullKeepOneScalePerLabel() {
      // values set explicitly (as a script can through getAxisLabelScale().setValues) keep a
      // null among non-null values even with NO_NULL
      OneVarParallelCoord c = new OneVarParallelCoord("Total", "State", new LinearScale("Total"));
      Scale s = c.getAxisLabelScale();
      s.setScaleOption(s.getScaleOption() | Scale.NO_NULL);
      ((CategoricalScale) s).setValues(new Object[] { null, "CA", "NY" });
      c.init(new DefaultDataSet(rows(null, "CA", "NY")));

      assertEquals(3, s.getValues().length, desc(c));
      assertOneScalePerLabel(c);
   }

   private static VGraph radar(Object[][] rows, boolean noNull) {
      RadarVSChartInfo info = new RadarVSChartInfo();
      info.setChartType(GraphTypes.CHART_RADAR);
      info.setRTChartType(GraphTypes.CHART_RADAR);
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField("State"));
      dim.getAxisDescriptor().setNoNull(noNull);
      info.addGroupField(dim);
      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      info.addYField(total);
      info.updateChartType(false);
      DefaultDataSet data = new DefaultDataSet(rows);
      GraphGenerator gen = GraphGenerator.getGenerator(
         info, new ChartDescriptor(), null, null, data, new VariableTable(),
         XSourceInfo.NONE, null);
      EGraph eg = gen.createEGraph();
      Coordinate coord = eg.getCoordinate();

      assertInstanceOf(PolarCoord.class, coord);
      assertInstanceOf(OneVarParallelCoord.class, ((PolarCoord) coord).getCoordinate());

      return Plotter.getPlotter(eg).plotAndLayout(data, 0, 0, 400, 400);
   }

   @Test
   void radarWithIgnoreNullAndOnlyNullGroupsPlots() {
      VGraph vg = assertDoesNotThrow(() -> radar(rows(null, null), true));

      assertTrue(vg.getVisualCount() > 0, "radar should have a visual");
   }

   @Test
   void radarWithIgnoreNullAndOnlyNullGroupsRendersAsWithoutIgnoreNull() {
      VGraph on = radar(rows(null, null), true);
      VGraph off = radar(rows(null, null), false);
      OneVarParallelCoord onCoord = innerCoord(on);

      // one parallel axis and one (null) axis label, as without Ignore Null
      assertEquals(1, onCoord.getAxes(false).length);
      assertEquals(innerCoord(off).getAxes(false).length, onCoord.getAxes(false).length);
      assertEquals(Arrays.asList((Object) null), labelValues(on, onCoord));
      assertEquals(labelValues(off, innerCoord(off)), labelValues(on, onCoord));

      // the same finite vertices inside the plot as without Ignore Null
      List<Point2D> onPts = vertices(on);
      Rectangle2D plot = on.getPlotBounds();
      assertEquals(2, onPts.size());
      assertEquals(vertices(off), onPts);

      for(Point2D pt : onPts) {
         assertTrue(Double.isFinite(pt.getX()) && Double.isFinite(pt.getY()), pt.toString());
         assertTrue(plot.getX() - 1 <= pt.getX() && pt.getX() <= plot.getMaxX() + 1 &&
                    plot.getY() - 1 <= pt.getY() && pt.getY() <= plot.getMaxY() + 1,
                    pt + " outside " + plot);
      }
   }

   private static OneVarParallelCoord innerCoord(VGraph vg) {
      return (OneVarParallelCoord) ((PolarCoord) vg.getCoordinate()).getCoordinate();
   }

   private static List<Object> labelValues(VGraph vg, OneVarParallelCoord coord) {
      Scale label = coord.getAxisLabelScale();

      for(int i = 0; i < vg.getAxisCount(); i++) {
         if(vg.getAxis(i).getScale() == label) {
            List<Object> list = new ArrayList<>();

            for(VLabel l : vg.getAxis(i).getLabels()) {
               list.add(l.getValue());
            }

            return list;
         }
      }

      return fail("label axis not found");
   }

   private static List<Point2D> vertices(VGraph vg) {
      List<Point2D> list = new ArrayList<>();

      for(VisualObject vo : GTool.getVOs(vg)) {
         if(vo instanceof LineVO line) {
            for(Point2D pt : line.getTransformedPoints()) {
               if(pt != null) {
                  list.add(pt);
               }
            }
         }
      }

      return list;
   }

   @Test
   void radarWithIgnoreNullAndMixedGroupsPlots() {
      VGraph vg = radar(rows(null, "CA", "NY"), true);

      assertTrue(vg.getVisualCount() > 0, "radar should have a visual");
   }
}
