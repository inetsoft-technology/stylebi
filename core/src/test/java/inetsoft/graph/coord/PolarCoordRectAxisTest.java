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

import inetsoft.graph.AxisSpec;
import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.graph.guide.axis.PolarAxis;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A {@link PolarCoord} wrapping a {@link RectCoord} whose scales keep the default axis style
 * (the script API, {@code new PolarCoord(new RectCoord(xs, ys))}) still gets its angular axis.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PolarCoordRectAxisTest {
   @Test
   @DisplayName("a polar coord over a rect coord with default axis specs gets one polar axis, labelled per category, and a radius axis")
   void defaultAxisSpecGetsAngularAxis() {
      AxisSpec spec = new CategoricalScale("Cat").getAxisSpec();
      VGraph vgraph = wedges(null);
      String axes = describe(vgraph);

      assertEquals(1, count(vgraph, PolarAxis.class), "one PolarAxis expected, axes " + axes);
      assertNotEquals(AxisSpec.AXIS_NONE, spec.getAxisStyle(), "premise: default is not none");

      PolarAxis polar = (PolarAxis) axisOf(vgraph, PolarAxis.class).get(0);

      assertEquals(3, polar.getLabels().length, axes);
      assertTrue(count(vgraph, DefaultAxis.class) >= 1, "radius axis, axes " + axes);
   }

   @Test
   @DisplayName("an explicit AXIS_NONE on the angular scale gets no polar axis")
   void explicitNoneGetsNoAngularAxis() {
      VGraph vgraph = wedges(AxisSpec.AXIS_NONE);

      assertEquals(0, count(vgraph, PolarAxis.class), describe(vgraph));
   }

   @Test
   @DisplayName("a radar over a parallel coord builds its spokes and one polar ring axis, as before")
   void radarIsUnchanged() {
      int n = 3;
      EGraph graph = new EGraph();
      AreaElement area = new AreaElement();
      Scale[] scales = new Scale[n];

      for(int i = 0; i < n; i++) {
         LinearScale scale = new LinearScale("m" + i);
         scale.setMin(0);
         scale.setMax(100);
         scales[i] = scale;
         area.addDim("m" + i);
      }

      graph.addElement(area);
      graph.setCoordinate(new PolarCoord(new ParallelCoord(scales)));

      Object[][] rows = { { "m0", "m1", "m2" }, { 10.0, 20.0, 30.0 }, { 40.0, 50.0, 60.0 } };
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 400);

      assertEquals(1, count(vgraph, PolarAxis.class), describe(vgraph));
      assertEquals(n, count(vgraph, DefaultAxis.class), describe(vgraph));
   }

   private static VGraph wedges(Integer angularStyle) {
      Object[][] rows = { { "Cat", "m" }, { "A", 40.0 }, { "B", 80.0 }, { "C", 60.0 } };
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("Cat", "m"));
      CategoricalScale xs = new CategoricalScale("Cat");
      LinearScale ys = new LinearScale("m");
      ys.setMin(0);
      ys.setMax(100);
      ys.setIncrement(25);

      if(angularStyle != null) {
         xs.getAxisSpec().setAxisStyle(angularStyle);
      }

      graph.setScale("Cat", xs);
      graph.setScale("m", ys);
      graph.setCoordinate(new PolarCoord(new RectCoord(xs, ys)));

      return Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 600, 400);
   }

   private static List<Axis> axisOf(VGraph vgraph, Class<? extends Axis> type) {
      List<Axis> list = new ArrayList<>();

      for(int i = 0; i < vgraph.getAxisCount(); i++) {
         if(type.isInstance(vgraph.getAxis(i)) &&
            (type != DefaultAxis.class || !(vgraph.getAxis(i) instanceof PolarAxis)))
         {
            list.add(vgraph.getAxis(i));
         }
      }

      return list;
   }

   private static int count(VGraph vgraph, Class<? extends Axis> type) {
      return axisOf(vgraph, type).size();
   }

   private static String describe(VGraph vgraph) {
      List<String> names = new ArrayList<>();

      for(int i = 0; i < vgraph.getAxisCount(); i++) {
         names.add(vgraph.getAxis(i).getClass().getSimpleName());
      }

      return names.toString();
   }
}
