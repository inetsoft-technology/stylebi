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
package inetsoft.graph;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.internal.GTool;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * The top inset keeps the content of a graph below the top of its box.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VGraphTopInsetTest {
   @Test
   void theInsetLowersThePlotTop() {
      VGraph plain = layout(bar(0, false));
      VGraph inset = layout(bar(12, false));

      assertEquals(plain.getPlotBounds().getMaxY() - 12, inset.getPlotBounds().getMaxY(), 0.01);
      assertEquals(plain.getPlotBounds().getMinY(), inset.getPlotBounds().getMinY(), 0.01);
   }

   @Test
   void noInsetIsTodaysLayout() {
      EGraph never = bar(0, false);
      EGraph zero = bar(0, false);
      zero.setTopInset(0);

      assertEquals(layout(never).getPlotBounds(), layout(zero).getPlotBounds());
   }

   @Test
   void aTopLegendMovesDownWithTheContent() {
      VGraph plain = layout(bar(0, true));
      VGraph inset = layout(bar(12, true));
      double plainTop = plain.getLegendGroup().getLegend(0).getBounds().getMaxY();
      double insetTop = inset.getLegendGroup().getLegend(0).getBounds().getMaxY();

      assertEquals(plainTop - 12, insetTop, 0.01);
   }

   @Test
   void facetCellsIgnoreTheInset() {
      double plain = cellHeight(0);
      double inset = cellHeight(12);

      assertEquals(plain - 6, inset, 1.5);
      assertNotEquals(plain - 12, inset, 0.5);
   }

   @Test
   void theInsetIsClearedWithTheGraph() {
      EGraph graph = bar(12, false);
      graph.clear();

      assertEquals(0, graph.getTopInset(), 0.0);
   }

   private static double cellHeight(double inset) {
      Object[][] rows = { { "Region", "State", "Sales" }, { "E", "A", 10.0 }, { "E", "B", 20.0 },
                          { "W", "A", 30.0 }, { "W", "B", 40.0 } };
      RectCoord outer = new RectCoord(GTool.createFakeScale(null), new CategoricalScale("Region"));
      FacetCoord coord = new FacetCoord(outer, new RectCoord(new CategoricalScale("State"),
                                                             new LinearScale("Sales")));
      EGraph graph = new EGraph();
      graph.addElement(new IntervalElement("State", "Sales"));
      graph.setCoordinate(coord);
      graph.setTopInset(inset);
      Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 200);
      Coordinate[][] cells = coord.getExpandedInnerCoords();
      assertEquals(2, cells.length);

      return cells[0][0].getVGraph().getPlotBounds().getHeight();
   }

   private static VGraph layout(EGraph graph) {
      return Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows()), 0, 0, 400, 200);
   }

   private static Object[][] rows() {
      return new Object[][] { { "State", "Sales" }, { "A", 10.0 }, { "B", 20.0 }, { "C", 30.0 } };
   }

   private static EGraph bar(double inset, boolean topLegend) {
      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("State", "Sales");

      if(topLegend) {
         CategoricalColorFrame color = new CategoricalColorFrame();
         color.setField("State");
         color.init(new DefaultDataSet(rows()));
         elem.setColorFrame(color);
         graph.setLegendLayout(GraphConstants.TOP);
      }

      graph.addElement(elem);
      graph.setCoordinate(new RectCoord(new CategoricalScale("State"), new LinearScale("Sales")));
      graph.setTopInset(inset);
      return graph;
   }
}
