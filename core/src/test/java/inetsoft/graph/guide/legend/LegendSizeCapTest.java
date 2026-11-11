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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.GraphConstants;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Insets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A strict legend size cap holds even when the legend's minimum size is larger, and an unset
 * cap leaves the layout as it was.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LegendSizeCapTest {
   @Test
   void aStrictSideCapHoldsAtThirtyPercent() {
      assertTrue(chart(GraphConstants.RIGHT, 0, 12).getLegendGroup().getBounds().getWidth() > 120);
      VGraph vgraph = chart(GraphConstants.RIGHT, 0.3, 12);
      assertTrue(vgraph.getLegendGroup().getBounds().getWidth() <= 120 + 1e-6);
   }

   @Test
   void aStrictTopCapHoldsAtTwentyFivePercent() {
      assertTrue(chart(GraphConstants.TOP, 0, 12).getLegendGroup().getBounds().getHeight() > 60);
      VGraph vgraph = chart(GraphConstants.TOP, 0.25, 12);
      assertTrue(vgraph.getLegendGroup().getBounds().getHeight() <= 60 + 1e-6);
   }

   @Test
   void anUnsetCapIsToday() {
      for(int layout : new int[] { GraphConstants.RIGHT, GraphConstants.LEFT,
                                   GraphConstants.TOP, GraphConstants.BOTTOM })
      {
         VGraph unset = chart(layout, Double.NaN, 12);
         VGraph zero = chart(layout, 0, 12);
         assertEquals(unset.getLegendGroup().getBounds(), zero.getLegendGroup().getBounds());
         assertEquals(unset.getPlotBounds(), zero.getPlotBounds());
      }
   }

   @Test
   void aStrictCapBeatsTheMinimumSize() {
      VGraph today = chart(GraphConstants.RIGHT, 0, 12, 90);
      assertTrue(today.getLegendGroup().getMinWidth() > 120);

      VGraph capped = chart(GraphConstants.RIGHT, 0.3, 12, 90);
      assertTrue(capped.getLegendGroup().getBounds().getWidth() <= 120 + 1e-6);
   }

   // a NaN ratio leaves the property untouched
   private static VGraph chart(int layout, double ratio, int count) {
      return chart(layout, ratio, count, 0);
   }

   // a wide padding makes the legend's minimum width exceed the cap
   private static VGraph chart(int layout, double ratio, int count, int padding) {
      Object[][] rows = new Object[count + 1][];
      rows[0] = new Object[] { "State", "Sales" };

      for(int i = 1; i <= count; i++) {
         rows[i] = new Object[] { "A fairly long category label number " + i, 100 * i };
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("State", "Sales");
      CategoricalColorFrame color = new CategoricalColorFrame("State");

      if(padding > 0) {
         LegendSpec spec = new LegendSpec();
         spec.setPadding(new Insets(0, padding, 0, padding));
         color.setLegendSpec(spec);
      }

      elem.setColorFrame(color);
      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      graph.setLegendLayout(layout);

      if(!Double.isNaN(ratio)) {
         graph.setLegendMaxRatio(ratio);
      }

      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
   }
}
