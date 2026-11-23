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
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82054: Pie3DVO.getBounds() dropped the createUnion result (only the first slice was in
 * the bounds) and left out the 3D depth painted below the pie, so a chart tile whose clip
 * missed that box did not paint the pie (VGraph.paintVisualizables -> insideClip).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class Pie3DVOTest {
   @Test
   void boundsContainEverySlice() {
      List<Pie3DVO> single = Chart3DTestUtil.collect(pie(6, false, 2000, 700), Pie3DVO.class);
      List<Pie3DVO> facet = Chart3DTestUtil.collect(pie(6, true, 2400, 500), Pie3DVO.class);
      assertEquals(1, single.size());
      assertEquals(4, facet.size());

      for(Pie3DVO vo : concat(single, facet)) {
         Rectangle2D bounds = vo.getBounds();
         assertEquals(6, vo.getShapes().length);

         for(Shape slice : vo.getShapes()) {
            Rectangle2D sb = slice.getBounds2D();
            assertTrue(grow(bounds, 1e-6).contains(sb), "bounds " + bounds + " miss slice " + sb);
         }
      }
   }

   @Test
   void boundsEqualUnion() {
      VGraph vg = pie(6, false, 2000, 700);
      Pie3DVO vo = Chart3DTestUtil.collect(vg, Pie3DVO.class).get(0);
      Rectangle2D bounds = vo.getBounds();
      Rectangle2D union = null;

      for(Shape slice : vo.getShapes()) {
         union = union == null ? slice.getBounds2D() : union.createUnion(slice.getBounds2D());
      }

      // the top of the pie is the union of the slices; the depth is painted below it
      assertEquals(union.getMinX(), bounds.getMinX(), 1e-6);
      assertEquals(union.getMaxX(), bounds.getMaxX(), 1e-6);
      assertEquals(union.getMaxY(), bounds.getMaxY(), 1e-6);
      assertTrue(bounds.getMinY() < union.getMinY(), "depth below the pie is not in the bounds");

      // every pixel the pie paints (top and depth) is inside the bounds
      Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, 2000, 700);
      Rectangle2D img = Chart3DTestUtil.toImage(vg, bounds);
      assertTrue(grow(img, 1).contains(painted), "painted " + painted + " outside " + img);
   }

   @Test
   void tileMissingFirstSliceStillPaintsPie() {
      VGraph vg = pie(6, false, 2000, 700);
      Pie3DVO vo = Chart3DTestUtil.collect(vg, Pie3DVO.class).get(0);
      Rectangle2D slice0 = Chart3DTestUtil.toImage(vg, vo.getShapes()[0].getBounds2D());
      Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, 2000, 700);
      int right = (int) Math.floor(slice0.getMinX()) - 2;
      assertTrue(right > painted.x + 50, "slice 0 must not be the left-most slice");

      Rectangle tile = new Rectangle(0, 0, right, 700);
      assertEquals(0, Chart3DTestUtil.missingPixels(vg, 2000, 700, tile),
                   "tile left of the first slice lost pie pixels");
   }

   @Test
   void tileCoveringOnlyDepthPaintsPie() {
      VGraph vg = pie(6, false, 2000, 700);
      Pie3DVO vo = Chart3DTestUtil.collect(vg, Pie3DVO.class).get(0);
      Rectangle2D union = null;

      for(Shape slice : vo.getShapes()) {
         union = union == null ? slice.getBounds2D() : union.createUnion(slice.getBounds2D());
      }

      // the strip below the top arcs only holds the 3d depth (image y grows downwards)
      Rectangle2D top = Chart3DTestUtil.toImage(vg, union);
      Rectangle painted = Chart3DTestUtil.paintedBox(vg, vo, 2000, 700);
      int y0 = (int) Math.ceil(top.getMaxY()) + 2;
      assertTrue(painted.getMaxY() > y0 + 5, "no depth painted below the pie");

      Rectangle tile = new Rectangle(0, y0, 2000, 700 - y0);
      assertEquals(0, Chart3DTestUtil.missingPixels(vg, 2000, 700, tile),
                   "tile below the pie top lost the depth pixels");
   }

   @Test
   void tilesPaintSinglePie() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(pie(6, false, 2000, 700), 2000, 700));
   }

   @Test
   void tilesPaintFacetedPies() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(pie(6, true, 2400, 500), 2400, 500));
   }

   @Test
   void tilesPaintTallPie() {
      assertEquals(0, Chart3DTestUtil.sweepTiles(pie(20, false, 400, 1800), 400, 1800));
   }

   private static VGraph pie(int n, boolean facet, int w, int h) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "x", "y", "g" });
      int groups = facet ? 4 : 1;

      for(int gi = 0; gi < groups; gi++) {
         for(int i = 0; i < n; i++) {
            rows.add(new Object[] { "C" + i, 10 + 7 * i + gi * 3, "G" + gi });
         }
      }

      EGraph graph = new EGraph();
      IntervalElement elem = new IntervalElement("y");
      elem.setCollisionModifier(GraphElement.MOVE_STACK);
      CategoricalColorFrame colors = new CategoricalColorFrame("x");
      // no legend: the tiles only need the pies, and legend text needs the SVG support
      colors.getLegendSpec().setVisible(false);
      elem.setColorFrame(colors);
      graph.addElement(elem);
      LinearScale yscale = new LinearScale("y");
      yscale.setScaleRange(new StackRange());
      yscale.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
      PolarCoord coord = new PolarCoord(new Rect25Coord(null, yscale));
      coord.setType(PolarCoord.THETA);

      if(facet) {
         CategoricalScale g = new CategoricalScale("g");
         g.getAxisSpec().setAxisStyle(AxisSpec.AXIS_NONE);
         graph.setCoordinate(new FacetCoord(new RectCoord(g, null), coord));
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

   private static <T> List<T> concat(List<T> a, List<T> b) {
      List<T> list = new ArrayList<>(a);
      list.addAll(b);
      return list;
   }
}
