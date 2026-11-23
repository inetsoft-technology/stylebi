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

import inetsoft.graph.*;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.coord.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.scale.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82056: Bar3DVO paints its right and top faces outside getBounds() (the front face), so a
 * chart tile whose clip missed the front face did not paint the faces. getPaintBounds() covers
 * the faces for the clip culling; getBounds() stays the front face (plot fit, 3D bar order).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Bar3DVOTest {
   @Test
   void boundsCoverFaces() {
      for(boolean horizontal : new boolean[] { false, true }) {
         int w = horizontal ? 600 : 2000;
         int h = horizontal ? 2000 : 600;
         VGraph vg = bar(40, horizontal, false, false, w, h);
         List<Bar3DVO> bars = Chart3DTestUtil.collect(vg, Bar3DVO.class);
         assertEquals(40, bars.size());

         for(Bar3DVO vo : bars) {
            Rectangle2D bounds = vo.getBounds();
            Rectangle2D paintBounds = vo.getPaintBounds();
            Rectangle2D front = vo.getShapes()[0].getBounds2D();

            // getBounds() stays the front face (plus the half border)
            assertTrue(grow(front, 0.5 + 1e-6).contains(bounds),
                       "bounds " + bounds + " larger than the front face " + front);
            assertTrue(paintBounds.contains(bounds));

            // every painted pixel (front, right and top faces) is in the paint bounds
            Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, w, h);
            Rectangle2D img = Chart3DTestUtil.toImage(vg, paintBounds);
            assertTrue(grow(img, 1).contains(painted),
                       (horizontal ? "horizontal" : "vertical") + " bar painted " + painted +
                       " outside " + img);
         }
      }
   }

   @Test
   void tileCoveringOnlySideFacePaintsBar() {
      // few wide bars, so the right face of a bar is in the gap before the next bar
      VGraph vg = bar(6, false, false, false, 2000, 600);
      Bar3DVO vo = Chart3DTestUtil.collect(vg, Bar3DVO.class).get(2);
      Rectangle2D front = Chart3DTestUtil.toImage(vg, vo.getBounds());
      Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, 2000, 600);
      int x0 = (int) Math.ceil(front.getMaxX()) + 2;
      assertTrue(painted.getMaxX() > x0 + 5, "no right face painted");

      Rectangle tile = new Rectangle(x0, 0, (int) painted.getMaxX() - x0, 600);
      assertEquals(0, Chart3DTestUtil.missingPixels(vg, 2000, 600, tile),
                   "tile holding only the side face lost its pixels");
   }

   @Test
   void tilesPaintVerticalBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(40, false, false, false, 2000, 600),
                                                 2000, 600));
   }

   @Test
   void tilesPaintHorizontalBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(40, true, false, false, 600, 2000),
                                                 600, 2000));
   }

   @Test
   void tilesPaintStackedBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(30, false, true, false, 2000, 600),
                                                 2000, 600));
   }

   @Test
   void tilesPaintFacetedBars() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(bar(20, false, false, true, 2400, 600),
                                                 2400, 600));
   }

   private static VGraph bar(int n, boolean horizontal, boolean stack, boolean facet,
                             int w, int h)
   {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "x", "y", "c", "g" });
      int groups = facet ? 3 : 1;

      for(int gi = 0; gi < groups; gi++) {
         for(int i = 0; i < n; i++) {
            double v = 20 + (i * 37 + gi * 11) % 80;
            rows.add(new Object[] { "C" + i, v, "s1", "G" + gi });

            if(stack) {
               rows.add(new Object[] { "C" + i, v / 2, "s2", "G" + gi });
            }
         }
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("x", "y");

      if(stack) {
         elem.setCollisionModifier(GraphElement.MOVE_STACK);
         CategoricalColorFrame colors = new CategoricalColorFrame("c");
         colors.getLegendSpec().setVisible(false);
         elem.setColorFrame(colors);
      }

      graph.addElement(elem);
      // no axes or legend: the tiles only need the bars, and their text needs the SVG support
      CategoricalScale xscale = new CategoricalScale("x");
      xscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      LinearScale yscale = new LinearScale("y");
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);

      if(stack) {
         yscale.setScaleRange(new StackRange());
      }

      Rect25Coord coord = new Rect25Coord(xscale, yscale);

      if(horizontal) {
         coord.transpose();
      }

      if(facet) {
         CategoricalScale g = new CategoricalScale("g");
         g.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
         RectCoord outer = horizontal ? new RectCoord(null, g) : new RectCoord(g, null);
         graph.setCoordinate(new FacetCoord(outer, coord));
      }
      else {
         graph.setCoordinate(coord);
      }

      return Plotter.getPlotter(graph).plotAndLayout(
         new DefaultDataSet(rows.toArray(new Object[0][])), 0, 0, w, h);
   }

   private static Rectangle2D grow(Rectangle2D r, double d) {
      return new Rectangle2D.Double(r.getX() - d, r.getY() - d, r.getWidth() + 2 * d,
                                    r.getHeight() + 2 * d);
   }
}
