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
package inetsoft.graph.visual;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.geometry.IntervalGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The 2px corner floor of a modern bar: the drawn path, a stacked segment and the fraction the
 * region sends all use the same effective corner.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BarVOCornerFloorTest {
   @Test
   void flooredFraction() {
      assertEquals(2.0, BarVO.flooredCornerFraction(0.08, 21) * 21, 1e-9);
      assertEquals(2.56, BarVO.flooredCornerFraction(0.08, 32) * 32, 1e-9);
      assertEquals(1.5, BarVO.flooredCornerFraction(0.08, 6) * 6, 1e-9);
      assertEquals(0.75, BarVO.flooredCornerFraction(0.08, 3) * 3, 1e-9);
      // the author's larger corner wins
      assertEquals(6.3, BarVO.flooredCornerFraction(0.3, 21) * 21, 1e-9);
      assertEquals(0, BarVO.flooredCornerFraction(0, 21), 1e-9);
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void aModernBarDrawsTheFlooredCorner(boolean transposed) {
      List<BarVO> bars = bars(true, transposed);
      assertFalse(bars.isEmpty(), "premise: bars were drawn");

      for(BarVO bar : bars) {
         Rectangle2D bounds = bar.getPreRoundingBounds();
         double f = bar.getEffectiveCornerRadius();
         assertTrue(f > 0.08, "premise: the bar is thin enough for the floor, thickness " +
                    thickness(bounds, transposed));

         Area expected = new Area(BarVO.buildRoundedBarShape(
            bounds, f, openDir(transposed), false));
         assertTrue(expected.equals(new Area(bar.getPath())), "drawn corner " + bounds);
      }
   }

   @Test
   void aStackedBarUsesTheSameFraction() {
      int outer = 0;

      for(BarVO bar : stackedBars()) {
         IntervalGeometry geom = (IntervalGeometry) bar.getGeometry();

         if(!geom.isStackOutermost() || geom.isStackInnermost()) {
            continue;
         }

         Rectangle2D seg = bar.getPreRoundingBounds();
         double f = bar.getEffectiveCornerRadius();
         assertTrue(f > 0.08, "premise: the bar is thin enough for the floor");

         Rectangle2D full = BarVO.computeFullBarBounds(
            seg, true, 1, geom.getInterval(), Math.abs(geom.getCumulativeStackInterval()),
            Math.abs(geom.getTotalStackInterval()));
         Area expected = new Area(seg);
         expected.intersect(new Area(BarVO.buildRoundedBarShape(full, f, 1, false)));

         assertTrue(expected.equals(new Area(bar.getPath())), "outer segment " + seg);
         outer++;
      }

      assertTrue(outer > 0, "premise: outer segments were drawn");
   }

   @ParameterizedTest
   @ValueSource(booleans = { false, true })
   void theRegionFractionDrawsTheSameCorner(boolean transposed) {
      for(BarVO bar : bars(true, transposed)) {
         double t = thickness(bar.getPreRoundingBounds(), transposed);
         double arc = Math.min(Math.max(0.08 * t, Math.min(2, t / 4)), t / 2);

         assertEquals(arc, bar.getEffectiveCornerRadius() * t, 1e-9, "thickness " + t);
      }
   }

   @Test
   void classicCornersUnchanged() {
      List<BarVO> bars = bars(false, false);
      assertFalse(bars.isEmpty(), "premise: bars were drawn");

      for(BarVO bar : bars) {
         Rectangle2D bounds = bar.getPreRoundingBounds();
         assertEquals(0.08, bar.getEffectiveCornerRadius());

         Area expected = new Area(BarVO.buildRoundedBarShape(bounds, 0.08, 1, false));
         assertTrue(expected.equals(new Area(bar.getPath())), "classic corner " + bounds);
      }
   }

   // twelve thin bars, one per category
   private static List<BarVO> bars(boolean floor, boolean transposed) {
      Object[][] rows = new Object[13][];
      rows[0] = new Object[] { "State", "Sales" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "S" + i, 100.0 * i };
      }

      IntervalElement elem = new IntervalElement("State", "Sales");
      elem.setCornerRadius(0.08);
      elem.setCornerFloor(floor);
      RectCoord coord = new RectCoord(new CategoricalScale("State"), new LinearScale("Sales"));

      if(transposed) {
         coord.transpose();
      }

      return render(elem, coord, rows);
   }

   private static List<BarVO> stackedBars() {
      Object[][] rows = new Object[13][];
      rows[0] = new Object[] { "State", "m1", "m2" };

      for(int i = 1; i < rows.length; i++) {
         rows[i] = new Object[] { "S" + i, 100.0 * i, 50.0 * i };
      }

      IntervalElement elem = new IntervalElement("State", "m1");
      elem.addVar("m2");
      elem.setCollisionModifier(GraphElement.STACK_SYMMETRIC);
      elem.setCornerRadius(0.08);
      elem.setCornerFloor(true);
      RectCoord coord = new RectCoord(new CategoricalScale("State"), new LinearScale("m1", "m2"));

      return render(elem, coord, rows);
   }

   private static List<BarVO> render(IntervalElement elem, RectCoord coord, Object[][] rows) {
      EGraph graph = new EGraph();
      graph.addElement(elem);
      graph.setCoordinate(coord);
      VGraph vgraph = Plotter.getPlotter(graph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 400, 240);
      List<BarVO> bars = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof BarVO bar) {
            bars.add(bar);
         }
      }

      return bars;
   }

   // a transposed coord draws horizontal bars, whose thickness is their height
   private static double thickness(Rectangle2D bounds, boolean transposed) {
      return transposed ? bounds.getHeight() : bounds.getWidth();
   }

   private static int openDir(boolean transposed) {
      return transposed ? 2 : 1;
   }
}
