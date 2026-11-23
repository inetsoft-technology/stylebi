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
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.AreaElement;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for Bug #82051 (getUnscaledSize BOTTOM fell through into LEFT), Bug #82052
 * (step/jump line with ignoreNull lost the step across a null point) and Bug #82053 (dashed or
 * jump line with ignoreNull lost the segment next to a null point).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class LineVOIgnoreNullTest {
   private static final int W = 700;
   private static final int H = 400;

   private static LineVO createVO(Object[][] rows, int x, int y, Consumer<LineElement> setup) {
      EGraph graph = new EGraph();
      LineElement elem = new LineElement("x", "y");

      if(setup != null) {
         setup.accept(elem);
      }

      graph.addElement(elem);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), x, y, W, H);

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof LineVO vo) {
            return vo;
         }
      }

      fail("no LineVO");
      return null;
   }

   private static AreaVO createAreaVO(Object[][] rows, Consumer<AreaElement> setup) {
      EGraph graph = new EGraph();
      AreaElement elem = new AreaElement("x", "y");
      setup.accept(elem);
      graph.addElement(elem);
      VGraph vg = Plotter.getPlotter(graph).plotAndLayout(new DefaultDataSet(rows), 0, 0, W, H);

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof AreaVO vo) {
            return vo;
         }
      }

      fail("no AreaVO");
      return null;
   }

   private static BufferedImage paint(LineVO vo) {
      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      vo.paint(g);
      g.dispose();
      return img;
   }

   private static boolean painted(BufferedImage img, double x, double y) {
      int ix = (int) Math.floor(x);
      int iy = (int) Math.floor(y);
      // a 3x3 neighbourhood so the check does not depend on sub-pixel antialiasing
      for(int dx = -1; dx <= 1; dx++) {
         for(int dy = -1; dy <= 1; dy++) {
            if((img.getRGB(ix + dx, iy + dy) >>> 24) != 0) {
               return true;
            }
         }
      }

      return false;
   }

   /**
    * Count the integer-x sample points on the straight centre line from p1 to p2 (skipping
    * {@code margin} pixels at each end, where the end dots are filled) that are painted.
    */
   private static int paintedOnSegment(BufferedImage img, Point2D p1, Point2D p2, int margin) {
      int n = 0;
      int x1 = (int) Math.ceil(p1.getX()) + margin;
      int x2 = (int) Math.floor(p2.getX()) - margin;

      for(int x = x1; x <= x2; x++) {
         double t = (x - p1.getX()) / (p2.getX() - p1.getX());
         double y = p1.getY() + t * (p2.getY() - p1.getY());

         if(painted(img, x, y)) {
            n++;
         }
      }

      return n;
   }

   private static void assertNaNAt(Point2D[] pts, int... idxs) {
      for(int i = 0; i < pts.length; i++) {
         boolean nan = LineVO.isNaN(pts[i]);
         final int fi = i;
         boolean expected = Arrays.stream(idxs).anyMatch(k -> k == fi);
         assertEquals(expected, nan, "precondition: point " + i + " NaN state, pts=" +
            Arrays.toString(pts));
      }
   }

   // ---------------------------------------------------------------------------------------
   // Bug #82051: getUnscaledSize(BOTTOM) falls through into the LEFT case
   // ---------------------------------------------------------------------------------------

   /**
    * Three points: left (x=0,y=10,small), bottom (x=10,y=0,big), right (x=20,y=10,small).
    * The size frame maps s=0 to 3 and s=10 to 19; getLineSize(v) = 1 + (v - 1) * 0.5, so the big
    * point has radius getLineSize(19) / 2 = 10 / 2 = 5 and the small points radius
    * getLineSize(3) / 2 = 2 / 2 = 1. The lowest edge (y - r) belongs to the big bottom point, so
    * BOTTOM must return 5 + 0.5 = 5.5. The graph is laid out at y = 300 so every point's y is far
    * greater than the left point's x: the fall-through into LEFT then lets the left (small) point
    * win and BOTTOM returns 1 + 0.5 = 1.5.
    */
   @Test
   void unscaledSizeBottom() {
      Object[][] rows = {
         { "x", "y", "s" },
         { 0, 10, 0 },
         { 10, 0, 10 },
         { 20, 10, 0 },
      };
      LineVO vo = createVO(rows, 0, 300, e -> {
         LinearSizeFrame frame = new LinearSizeFrame("s");
         frame.setScale(new LinearScale(0, 10));
         frame.setSmallest(3);
         frame.setLargest(19);
         e.setSizeFrame(frame);
      });

      LineGeometry gobj = (LineGeometry) vo.getGeometry();
      int[] idxs = gobj.getTupleIndexes();
      Point2D[] pts = vo.getTransformedPoints();
      StringBuilder diag = new StringBuilder();

      for(int i = 0; i < pts.length; i++) {
         diag.append(pts[i]).append(" size=").append(gobj.getSize(idxs[i % idxs.length]))
            .append("; ");
      }

      // preconditions: the thick point is the lowest and the thin left point is left-most
      assertEquals(3, pts.length, diag.toString());
      assertEquals(3.0, gobj.getSize(idxs[0]), 1e-9, diag.toString());
      assertEquals(19.0, gobj.getSize(idxs[1]), 1e-9, diag.toString());
      assertTrue(pts[1].getY() < pts[0].getY() && pts[1].getY() < pts[2].getY(), diag.toString());
      assertTrue(pts[0].getX() < pts[1].getX(), diag.toString());
      assertTrue(pts[0].getX() < pts[1].getY(), "left x below bottom y: " + diag);

      assertEquals(5.5, vo.getUnscaledSize(GraphConstants.BOTTOM, null), 1e-9, diag.toString());
   }

   /** Control: TOP / LEFT / RIGHT have their own break and are correct. */
   @Test
   void unscaledSizeOtherSidesControl() {
      Object[][] rows = {
         { "x", "y", "s" },
         { 0, 10, 10 },
         { 10, 0, 0 },
         { 20, 10, 0 },
      };
      LineVO vo = createVO(rows, 0, 300, e -> {
         LinearSizeFrame frame = new LinearSizeFrame("s");
         frame.setScale(new LinearScale(0, 10));
         frame.setSmallest(3);
         frame.setLargest(19);
         e.setSizeFrame(frame);
      });

      // left point is big (r = 5) and also top-most together with the right point (r = 1);
      // TOP uses strict <, so the first top-most (left, big) wins
      assertEquals(5.5, vo.getUnscaledSize(GraphConstants.LEFT, null), 1e-9);
      assertEquals(1.5, vo.getUnscaledSize(GraphConstants.RIGHT, null), 1e-9);
   }

   // ---------------------------------------------------------------------------------------
   // Bug #82052: step / jump line with ignoreNull draws nothing across a null point
   // ---------------------------------------------------------------------------------------

   private static Object[][] stepRows() {
      return new Object[][] {
         { "x", "y" },
         { 0, 10 },
         { 1, null },
         { 2, 30 },
         { 3, 20 },
      };
   }

   /**
    * STEP with ignoreNull over [p0, null, p2, p3]: the step must go horizontally from p0 to
    * x2 at y0 (then vertically to p2). The midpoint ((x0 + x2) / 2, y0) is on that segment.
    */
   @Test
   void stepLineConnectsOverNull() {
      LineVO vo = createVO(stepRows(), 0, 0, e -> {
         e.setType(LineElement.Type.STEP);
         e.setIgnoreNull(true);
         e.setFillLineStyle(0); // as GraphGenerator does unless "fill gap with dash" is on
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 1);
      BufferedImage img = paint(vo);

      double mx = (pts[0].getX() + pts[2].getX()) / 2;
      assertTrue(painted(img, mx, pts[0].getY()),
                 "step segment from p0 to x2 at y0 not painted at (" + mx + ", " +
                    pts[0].getY() + "), pts=" + Arrays.toString(pts));
      // the step after the null (p2 -> p3, horizontal at y2) must be painted too
      double mx2 = (pts[2].getX() + pts[3].getX()) / 2;
      assertTrue(painted(img, mx2, pts[2].getY()),
                 "step segment from p2 to x3 at y2 not painted, pts=" + Arrays.toString(pts));
   }

   /** Control: the same step without the null row paints the same horizontal segment. */
   @Test
   void stepLineWithoutNullControl() {
      Object[][] rows = { { "x", "y" }, { 0, 10 }, { 2, 30 }, { 3, 20 } };
      LineVO vo = createVO(rows, 0, 0, e -> {
         e.setType(LineElement.Type.STEP);
         e.setIgnoreNull(true);
         e.setFillLineStyle(0); // as GraphGenerator does unless "fill gap with dash" is on
      });
      Point2D[] pts = vo.getTransformedPoints();
      BufferedImage img = paint(vo);
      double mx = (pts[0].getX() + pts[1].getX()) / 2;
      assertTrue(painted(img, mx, pts[0].getY()), "control step not painted");
   }

   /**
    * JUMP with ignoreNull over [p0, p1, null, p3, p4]: the jump must draw the horizontal from p1
    * to x3 at y1. The null is not next to the first or last point, so only the step-over-null
    * path is exercised (not the dotend angle paths).
    */
   @Test
   void jumpLineConnectsOverNull() {
      Object[][] rows = {
         { "x", "y" },
         { 0, 10 },
         { 1, 20 },
         { 2, null },
         { 3, 30 },
         { 4, 15 },
      };
      LineVO vo = createVO(rows, 0, 0, e -> {
         e.setType(LineElement.Type.JUMP);
         e.setIgnoreNull(true);
         e.setFillLineStyle(0); // as GraphGenerator does unless "fill gap with dash" is on
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 2);
      BufferedImage img = paint(vo);

      double mx = (pts[1].getX() + pts[3].getX()) / 2;
      assertTrue(painted(img, mx, pts[1].getY()),
                 "jump segment from p1 to x3 at y1 not painted at (" + mx + ", " +
                    pts[1].getY() + "), pts=" + Arrays.toString(pts));
   }

   // ---------------------------------------------------------------------------------------
   // Bug #82053: dashed line with ignoreNull loses the segment next to a null point
   // ---------------------------------------------------------------------------------------

   private static Object[][] nullSecondRows() {
      return new Object[][] {
         { "x", "y" },
         { 0, 10 },
         { 1, null },
         { 2, 30 },
         { 3, 20 },
      };
   }

   /** Control: a solid line over [p0, null, p2, p3] with ignoreNull connects p0 to p2. */
   @Test
   void solidLineWithNullSecondPointIsPainted() {
      LineVO vo = createVO(nullSecondRows(), 0, 0, e -> {
         e.setIgnoreNull(true);
         e.setFillLineStyle(0);
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 1);
      BufferedImage img = paint(vo);
      int n = paintedOnSegment(img, pts[0], pts[2], 6);
      assertTrue(n > 0, "solid p0->p2 not painted, pts=" + Arrays.toString(pts));
   }

   /**
    * Dashed line over [p0, null, p2, p3] with ignoreNull: the first-point dot-end offset is
    * computed toward pts[1], which is NaN. The p0 -> p2 segment (about 230 px, many dash
    * periods) must have painted pixels on its centre line.
    */
   @Test
   void dashedLineWithNullSecondPointIsPainted() {
      LineVO vo = createVO(nullSecondRows(), 0, 0, e -> {
         e.setIgnoreNull(true);
         e.setFillLineStyle(0); // as GraphGenerator does unless "fill gap with dash" is on
         e.setLineFrame(new StaticLineFrame(GLine.DASH_LINE));
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 1);
      BufferedImage img = paint(vo);
      int n = paintedOnSegment(img, pts[0], pts[2], 6);
      int n2 = paintedOnSegment(img, pts[2], pts[3], 6);
      assertTrue(n > 0, "dashed p0->p2 not painted (p2->p3 painted samples: " + n2 +
         "), pts=" + Arrays.toString(pts));
   }

   /**
    * Dashed line over [p0, p1, null, p3] with ignoreNull: the last-valid dot-end offset is
    * computed toward pts[i - 1] = pts[2], which is NaN. The p1 -> p3 segment must be painted.
    */
   @Test
   void dashedLineWithNullBeforeLastPointIsPainted() {
      Object[][] rows = { { "x", "y" }, { 0, 10 }, { 1, 30 }, { 2, null }, { 3, 20 } };
      LineVO vo = createVO(rows, 0, 0, e -> {
         e.setIgnoreNull(true);
         e.setFillLineStyle(0); // as GraphGenerator does unless "fill gap with dash" is on
         e.setLineFrame(new StaticLineFrame(GLine.DASH_LINE));
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 2);
      BufferedImage img = paint(vo);
      int n0 = paintedOnSegment(img, pts[0], pts[1], 6);
      int n = paintedOnSegment(img, pts[1], pts[3], 6);
      assertTrue(n > 0, "dashed p1->p3 not painted (p0->p1 painted samples: " + n0 +
         "), pts=" + Arrays.toString(pts));
   }

   // ---------------------------------------------------------------------------------------
   // Bug #82052 / #82053 together, and the step area outline
   // ---------------------------------------------------------------------------------------

   /**
    * Bug #82052: a STEP area chart paints its top outline through LineVO.paintLine and
    * AreaElement always uses fill line style 0, so the outline reaches paintLineSimple without
    * any "fill gap with dash" setting. The step from p0 to x2 at y0 must be painted.
    */
   @Test
   void stepAreaOutlineConnectsOverNull() {
      AreaVO vo = createAreaVO(stepRows(), e -> {
         e.setType(LineElement.Type.STEP);
         e.setIgnoreNull(true);
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 1);

      BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      vo.paintLine(g, 1.0); // outline only, so the fill cannot cover the sampled pixel
      g.dispose();

      double mx = (pts[0].getX() + pts[2].getX()) / 2;
      assertTrue(painted(img, mx, pts[0].getY()),
                 "step area outline from p0 to x2 at y0 not painted at (" + mx + ", " +
                    pts[0].getY() + "), pts=" + Arrays.toString(pts));
   }

   /**
    * Bug #82052 + #82053: JUMP sets the dot-end offset too, so with the null at index 1 the
    * first point was offset toward the NaN point and became a NaN start for the jump. Both
    * fixes are needed for the jump from p0 to x2 at y0 to be painted.
    */
   @Test
   void jumpLineWithNullSecondPointConnectsOverNull() {
      LineVO vo = createVO(nullSecondRows(), 0, 0, e -> {
         e.setType(LineElement.Type.JUMP);
         e.setIgnoreNull(true);
         e.setFillLineStyle(0); // as GraphGenerator does when "fill gap with dash" is off
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 1);
      BufferedImage img = paint(vo);

      double mx = (pts[0].getX() + pts[2].getX()) / 2;
      assertTrue(painted(img, mx, pts[0].getY()),
                 "jump p0->x2 at y0 not painted at (" + mx + ", " + pts[0].getY() +
                    "), pts=" + Arrays.toString(pts));
   }

   /**
    * Bug #82053: a dashed line whose only other point is null has no valid neighbour for the
    * dot-end direction. The offset is skipped and the end dot is still painted.
    */
   @Test
   void dashedLineWithoutValidNeighborPaintsDot() {
      Object[][] rows = { { "x", "y" }, { 0, 10 }, { 1, null } };
      LineVO vo = createVO(rows, 0, 0, e -> {
         e.setIgnoreNull(true);
         e.setFillLineStyle(0);
         e.setLineFrame(new StaticLineFrame(GLine.DASH_LINE));
      });
      Point2D[] pts = vo.getTransformedPoints();
      assertNaNAt(pts, 1);
      BufferedImage img = paint(vo);
      assertTrue(painted(img, pts[0].getX(), pts[0].getY()),
                 "end dot at p0 not painted, pts=" + Arrays.toString(pts));
   }
}
