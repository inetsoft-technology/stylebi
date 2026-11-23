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
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base points, fill shapes and bounds of AreaVO, on a laid out 700 x 400 plot. Positions are read
 * from the visual object (points and base points after the screen transform), the fill is checked
 * by sampling pixels of AreaVO.paint.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AreaVOShapeTest {
   private static final int W = 700;
   private static final int H = 400;
   private static final double EPS = 1e-6;

   private static LinearScale lin(double min, double max, String field) {
      LinearScale scale = new LinearScale(field);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   /** Area at x = 1, 2, ... with the given values (null is a missing value). */
   private static AreaVO area(double ymin, double ymax, LineElement.Type type, Double... ys) {
      Object[][] rows = new Object[ys.length + 1][];
      rows[0] = new Object[] { "x", "y" };

      for(int i = 0; i < ys.length; i++) {
         rows[i + 1] = new Object[] { (double) (i + 1), ys[i] };
      }

      EGraph egraph = new EGraph();
      AreaElement elem = new AreaElement("x", "y");

      if(type != null) {
         elem.setType(type);
      }

      egraph.addElement(elem);
      egraph.setCoordinate(new RectCoord(new LinearScale("x"), lin(ymin, ymax, "y")));
      VGraph vg = Plotter.getPlotter(egraph).plotAndLayout(new DefaultDataSet(rows), 0, 0, W, H);

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof AreaVO vo) {
            return vo;
         }
      }

      fail("no AreaVO");
      return null;
   }

   private static AreaVO area(Double... ys) {
      return area(0, 100, null, ys);
   }

   private static Point2D[] basePoints(AreaVO vo) throws Exception {
      Method m = AreaVO.class.getDeclaredMethod("getTransformedBasePoints");
      m.setAccessible(true);
      return (Point2D[]) m.invoke(vo);
   }

   private static BufferedImage paint(AreaVO vo) {
      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      vo.paint(g);
      g.dispose();
      return img;
   }

   private static boolean filled(BufferedImage img, double x, double y) {
      int px = (int) Math.round(x);
      int py = (int) Math.round(y);

      if(px < 0 || py < 0 || px >= img.getWidth() || py >= img.getHeight()) {
         return false;
      }

      return (img.getRGB(px, py) >>> 24) > 0;
   }

   // ---- base points

   @Test
   @DisplayName("base point x equals the x of its point")
   void basePointXEqualsPointX() throws Exception {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0);
      Point2D[] pts = vo.getTransformedPoints();
      Point2D[] bases = basePoints(vo);

      assertEquals(4, bases.length);

      for(int i = 0; i < pts.length; i++) {
         assertEquals(pts[i].getX(), bases[i].getX(), EPS, "base " + i);
      }
   }

   @Test
   @DisplayName("base line is at the zero value of the axis")
   void baseAtZeroLine() throws Exception {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0, 0.0);
      Point2D[] pts = vo.getTransformedPoints();
      Point2D[] bases = basePoints(vo);

      for(int i = 0; i < bases.length; i++) {
         assertEquals(pts[4].getY(), bases[i].getY(), EPS, "base " + i);
      }
   }

   @Test
   @DisplayName("base line is at the axis minimum when the axis does not include zero")
   void baseAtAxisMinimum() throws Exception {
      AreaVO vo = area(20, 100, null, 40.0, 80.0, 20.0, 60.0);
      Point2D[] pts = vo.getTransformedPoints();
      Point2D[] bases = basePoints(vo);

      for(int i = 0; i < bases.length; i++) {
         // the third point has the value 20, the minimum of the axis
         assertEquals(pts[2].getY(), bases[i].getY(), EPS, "base " + i);
      }
   }

   @Test
   @DisplayName("negative values are filled down to the zero line")
   void negativeValuesFillToZero() throws Exception {
      AreaVO vo = area(-50, 100, null, -20.0, 0.0, 40.0);
      Point2D[] pts = vo.getTransformedPoints();
      Point2D[] bases = basePoints(vo);
      BufferedImage img = paint(vo);

      for(int i = 0; i < bases.length; i++) {
         assertEquals(pts[1].getY(), bases[i].getY(), EPS, "base " + i);
      }

      // a little inside the first segment (below the zero line) and the second (above it)
      double x1 = pts[0].getX() + 4;
      double x2 = pts[2].getX() - 4;

      assertTrue(filled(img, x1, (pts[0].getY() + bases[0].getY()) / 2));
      assertTrue(filled(img, x2, (pts[2].getY() + bases[2].getY()) / 2));
   }

   // ---- fill

   @Test
   @DisplayName("straight area is filled between the line and the base, not above or left of it")
   void straightFill() throws Exception {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0);
      Point2D[] pts = vo.getTransformedPoints();
      Point2D[] bases = basePoints(vo);
      BufferedImage img = paint(vo);
      double dir = Math.signum(pts[1].getY() - bases[1].getY());

      assertTrue(filled(img, pts[1].getX(), (pts[1].getY() + bases[1].getY()) / 2));
      assertFalse(filled(img, pts[1].getX(), pts[1].getY() + dir * 15));
      assertFalse(filled(img, pts[0].getX() - 10, (pts[0].getY() + bases[0].getY()) / 2));
   }

   @Test
   @DisplayName("step area is a rectangle with the height of the left point of each step")
   void stepFill() throws Exception {
      AreaVO step = area(0, 100, LineElement.Type.STEP, 20.0, 80.0);
      AreaVO straight = area(0, 100, null, 20.0, 80.0);
      Point2D[] pts = step.getTransformedPoints();
      Point2D[] bases = basePoints(step);
      BufferedImage img = paint(step);
      BufferedImage straightImg = paint(straight);
      Point2D[] spts = straight.getTransformedPoints();
      Point2D[] sbases = basePoints(straight);
      double x = pts[1].getX() - 10;
      // screen y of the value v: the base is the value 0, the first point the value 20
      double y10 = bases[0].getY() + (pts[0].getY() - bases[0].getY()) * 10 / 20;
      double y70 = bases[0].getY() + (pts[0].getY() - bases[0].getY()) * 70 / 20;
      double sx = spts[1].getX() - 10;
      double sy70 = sbases[0].getY() + (spts[0].getY() - sbases[0].getY()) * 70 / 20;

      assertTrue(filled(img, x, y10));
      assertFalse(filled(img, x, y70));
      // the straight area at the same place does reach the value 70
      assertTrue(filled(straightImg, sx, sy70));
   }

   @Test
   @DisplayName("a missing value leaves its segments unfilled")
   void gapNotFilled() throws Exception {
      AreaVO vo = area(40.0, null, 40.0, 40.0);
      Point2D[] pts = vo.getTransformedPoints();
      Point2D[] bases = basePoints(vo);
      BufferedImage img = paint(vo);
      double mid = (pts[0].getY() + bases[0].getY()) / 2;

      assertFalse(filled(img, (pts[0].getX() + pts[2].getX()) / 2, mid));
      assertTrue(filled(img, (pts[2].getX() + pts[3].getX()) / 2, mid));
   }

   // ---- bounds

   @Test
   @DisplayName("bounds contain every point of the line")
   void boundsContainPoints() {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0);
      Rectangle2D bounds = vo.getBounds();

      // Rectangle2D.contains is half-open, so compare against the bounds grown by EPS
      Rectangle2D grown = new Rectangle2D.Double(bounds.getX() - EPS, bounds.getY() - EPS,
                                                 bounds.getWidth() + 2 * EPS,
                                                 bounds.getHeight() + 2 * EPS);

      for(Point2D pt : vo.getTransformedPoints()) {
         assertTrue(grown.contains(pt), pt.toString());
      }
   }

   @Test
   @Disabled("Bug #75419: Bottom of area chart missing in tiled plots: the fix 2d67f5576d is on other branches, not on main; it is ported in PR #8637, which must remove this @Disabled")
   @DisplayName("bounds contain the filled area down to the base line")
   void boundsContainFilledArea() throws Exception {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0);
      Rectangle2D bounds = vo.getBounds();

      for(Point2D base : basePoints(vo)) {
         assertTrue(bounds.contains(base), base.toString());
      }
   }

   // ---- clone

   @Test
   @DisplayName("concatenating a transform on a clone moves the clone and leaves the original base points alone")
   void cloneHasOwnScreenTransform() throws Exception {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0);
      Point2D[] before = basePoints(vo);
      AreaVO clone = (AreaVO) vo.clone();
      java.awt.geom.AffineTransform moved =
         new java.awt.geom.AffineTransform(clone.getScreenTransform());
      moved.concatenate(java.awt.geom.AffineTransform.getTranslateInstance(40, 50));

      clone.setScreenTransform(moved);

      Point2D[] after = basePoints(vo);
      Point2D[] cloneBases = basePoints(clone);

      // T * translate(40, 50) maps a point to T(p) + linear part of T applied to (40, 50)
      Point2D delta = vo.getScreenTransform().deltaTransform(new Point2D.Double(40, 50), null);

      for(int i = 0; i < before.length; i++) {
         assertEquals(before[i], after[i], "original base " + i);
         assertEquals(before[i].getX() + delta.getX(), cloneBases[i].getX(), EPS, "clone x " + i);
         assertEquals(before[i].getY() + delta.getY(), cloneBases[i].getY(), EPS, "clone y " + i);
      }
   }

   /*
    * Pins the current, intended behavior: clone() shares the base points array with the
    * original, so clone.transform(coord) also moves the original. No code path does that:
    * clones only come from Coordinate.getScaledVGraph() (element bounds during layout) and
    * only get a screen transform; only VGraph.layout() calls transform(coord), on the
    * originals. The scaled clone relies on sharing the already transformed points, so
    * copying the array in clone() would give wrong element bounds. If this test fails,
    * clone() changed: re-check facet, GeoCoord and RelationCoord layouts first.
    */
   @Test
   @DisplayName("a clone shares the base points of the original (pinned behavior)")
   void cloneSharesBasePoints() throws Exception {
      AreaVO vo = area(40.0, 80.0, 20.0, 60.0);
      Point2D[] before = Arrays.stream(basePoints(vo))
         .map(p -> new Point2D.Double(p.getX(), p.getY())).toArray(Point2D[]::new);
      AreaVO clone = (AreaVO) vo.clone();
      RectCoord shifting = new RectCoord(new LinearScale("x"), lin(0, 100, "y")) {
         @Override
         public Object transformShape(Object geom) {
            Point2D p = (Point2D) geom;
            return new Point2D.Double(p.getX() + 25, p.getY());
         }
      };

      clone.transform(shifting);

      Point2D[] after = basePoints(vo);

      Point2D[] cloneBases = basePoints(clone);

      for(int i = 0; i < before.length; i++) {
         // the base points are read through the screen transform, so the shift is scaled
         assertTrue(after[i].getX() > before[i].getX() + EPS, "original base moved " + i);
         assertEquals(cloneBases[i].getX(), after[i].getX(), EPS, "same base " + i);
      }
   }
}
