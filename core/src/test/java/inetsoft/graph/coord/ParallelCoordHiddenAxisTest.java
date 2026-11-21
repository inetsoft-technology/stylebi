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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.guide.axis.Axis;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Point2D;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82035: hiding one axis of a parallel coordinate with AxisSpec.AXIS_NONE (chart script)
 * must not throw a NullPointerException during plot and layout, and the visible axes must keep
 * their slots.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ParallelCoordHiddenAxisTest {
   private static final String[] FIELDS = { "a", "b", "c", "d" };
   private static final Object[][] ROWS = {
      FIELDS, { 10, 200, 3000, 5 }, { 20, 100, 1000, 7 }, { 30, 150, 2000, 9 }
   };

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, 3 })
   void hiddenAxisKeepsSlotsOfVisibleAxes(int hidden) {
      Map<String, Double> control = axisPositions(plot(false, -1, false));
      ParallelCoord parallel = plot(false, hidden, false);
      Axis[] axes = parallel.getAxes(false);

      assertEquals(FIELDS.length - 1, axes.length);
      Map<String, Double> positions = axisPositions(parallel);
      assertFalse(positions.containsKey(FIELDS[hidden]));

      for(Map.Entry<String, Double> e : positions.entrySet()) {
         assertEquals(control.get(e.getKey()), e.getValue(), 0.01,
                      "x position of axis " + e.getKey());
      }
   }

   @Test
   void gridLinesBeforeHiddenAxis() {
      ParallelCoord parallel = plot(false, 1, true);
      Axis[] axes = parallel.getAxes(false);

      assertEquals(3, axes.length);
      assertTrue(Arrays.stream(axes).noneMatch(Objects::isNull));
   }

   @ParameterizedTest
   @ValueSource(ints = { 0, 1, 3 })
   void radarGridLinesWithHiddenAxis(int hidden) {
      ParallelCoord parallel = plot(true, hidden, true);
      Axis[] axes = parallel.getAxes(false);

      assertEquals(FIELDS.length - 1, axes.length);
      assertTrue(Arrays.stream(axes).noneMatch(Objects::isNull));
   }

   /**
    * Plot and lay out a line element on a parallel coord (wrapped in a PolarCoord for a
    * radar), the way the radar graph generator builds it.
    */
   private static ParallelCoord plot(boolean radar, int hidden, boolean grid) {
      Scale[] scales = Arrays.stream(FIELDS).map(LinearScale::new).toArray(Scale[]::new);

      if(hidden >= 0) {
         scales[hidden].getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      }

      if(grid) {
         for(Scale scale : scales) {
            scale.getAxisSpec().setGridStyle(GraphConstants.THIN_LINE);
         }
      }

      ParallelCoord parallel = new ParallelCoord(scales);
      LineElement elem = new LineElement();

      for(String field : FIELDS) {
         elem.addDim(field);
      }

      elem.setClosed(radar);
      EGraph graph = new EGraph();
      graph.addElement(elem);
      graph.setCoordinate(radar ? new PolarCoord(parallel) : parallel);

      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(ROWS), 0, 0, 600, 400);
      assertNotNull(vgraph);

      return parallel;
   }

   private static Map<String, Double> axisPositions(ParallelCoord parallel) {
      Map<String, Double> positions = new HashMap<>();

      for(Axis axis : parallel.getAxes(false)) {
         Point2D p = axis.getScreenTransform().transform(new Point2D.Double(0, 0), null);
         positions.put(axis.getScale().getFields()[0], p.getX());
      }

      return positions;
   }
}
