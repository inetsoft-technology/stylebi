/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.scale;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79661: the y-axis major increment of an all-negative bar graph must be honoured
 * through the EGraph/RectCoord path, not only on a bare LinearScale.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LinearScaleIncrementGraphTest {
   private static double[] yTicks(double v1, double v2, double increment) {
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "Cat", "m" }, { "A", v1 }, { "B", v2 }
      });
      CategoricalScale xScale = new CategoricalScale("Cat");
      xScale.init(data);
      LinearScale yScale = new LinearScale("m");
      yScale.setIncrement(increment);
      yScale.setScaleOption(Scale.TICKS | Scale.ZERO);
      yScale.init(data);

      IntervalElement element = new IntervalElement();
      element.addDim("Cat");
      element.addVar("m");

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RectCoord coord = new RectCoord(xScale, yScale);
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);
      assertEquals(2, ggraph.getGeometryCount());
      return coord.getYScale().getTicks();
   }

   @Test
   void allNegativeBarHonoursIncrement() {
      double[] t = yTicks(-80, -10, 25);
      assertArrayEquals(new double[] { -100, -75, -50, -25, 0 }, t, 1E-6,
                        java.util.Arrays.toString(t));
   }

   @Test
   void positiveBarHonoursIncrement() {
      double[] t = yTicks(10, 80, 25);
      assertEquals(25, t[1] - t[0], 1E-6, java.util.Arrays.toString(t));
   }
}
