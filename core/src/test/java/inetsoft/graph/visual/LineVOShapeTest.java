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
import inetsoft.graph.aesthetic.GLine;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.LineElement;
import inetsoft.graph.geometry.LineGeometry;
import inetsoft.graph.scale.LinearScale;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.element.AreaElement;
import inetsoft.test.*;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Shapes, bounds and strokes of LineVO. Points and bounds are checked on the logical positions of
 * a visual object created without layout (identity screen transform: x on 0..10 maps to
 * x * 100, y on 0..100 maps to y * 10); strokes are checked by sampling pixels of the static
 * LineVO.paintLine with a hand made LineInfo.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class LineVOShapeTest {
   private static final double EPS = 1e-6;

   private static LinearScale lin(double min, double max, String... fields) {
      LinearScale scale = new LinearScale(fields);
      scale.setMin(min);
      scale.setMax(max);
      return scale;
   }

   private static Object[][] rows(Object[]... rows) {
      Object[][] result = new Object[rows.length + 1][];
      result[0] = new Object[] { "x", "y", "s" };
      System.arraycopy(rows, 0, result, 1, rows.length);
      return result;
   }

   private static Object[] row(double x, Double y, double s) {
      return new Object[] { x, y, s };
   }

   /** Line visual object without layout: positions are logical (x * 100, y * 10). */
   private static LineVO createVO(double size, Object[]... rows) {
      return createVO(new StaticSizeFrame((int) size), rows);
   }

   private static LineVO createVO(StaticSizeFrame sizes, Object[]... rows) {
      DefaultDataSet data = new DefaultDataSet(rows(rows));
      EGraph egraph = new EGraph();
      LineElement elem = new LineElement("x", "y");
      elem.setSizeFrame(sizes);
      egraph.addElement(elem);
      RectCoord coord = new RectCoord(lin(0, 10, "x"), lin(0, 100, "y"));
      egraph.setCoordinate(coord);
      GGraph ggraph = egraph.createGGraph(coord, data);
      assertEquals(1, ggraph.getGeometryCount());
      return (LineVO) ggraph.getGeometry(0).createVisual(coord);
   }

   /** Line visual object after layout of a 700 x 400 plot. */
   private static LineVO plot(Object[]... rows) {
      EGraph egraph = new EGraph();
      egraph.addElement(new LineElement("x", "y"));
      VGraph vg = Plotter.getPlotter(egraph).plotAndLayout(new DefaultDataSet(rows(rows)), 0, 0,
                                                           700, 400);

      for(int i = 0; i < vg.getVisualCount(); i++) {
         if(vg.getVisual(i) instanceof LineVO vo) {
            return vo;
         }
      }

      fail("no LineVO");
      return null;
   }

   // ---- points

   @Test
   @DisplayName("points are ordered left to right although the rows are not")
   void pointsAreOrderedLeftToRight() {
      LineVO vo = plot(row(3, 30.0, 1), row(1, 10.0, 1), row(2, 20.0, 1));
      Point2D[] pts = vo.getTransformedPoints();

      assertEquals(3, pts.length);
      assertTrue(pts[0].getX() < pts[1].getX());
      assertTrue(pts[1].getX() < pts[2].getX());
      // y is affine in the value: the middle value is half way
      assertEquals(0.5, (pts[1].getY() - pts[0].getY()) / (pts[2].getY() - pts[0].getY()), 1e-9);
   }

   @Test
   @DisplayName("positions are an affine map of the values with equal steps for equal x steps")
   void positionsAreAffine() {
      LineVO vo = plot(row(1, 20.0, 1), row(2, 60.0, 1), row(3, 100.0, 1));
      Point2D[] pts = vo.getTransformedPoints();

      assertEquals(pts[1].getX() - pts[0].getX(), pts[2].getX() - pts[1].getX(), 1e-6);
      assertEquals(0.5, (pts[1].getY() - pts[0].getY()) / (pts[2].getY() - pts[0].getY()), 1e-9);
   }

   @Test
   @DisplayName("one point per row")
   void onePointPerRow() {
      LineVO vo = plot(row(1, 10.0, 1), row(2, 20.0, 1), row(3, 15.0, 1), row(4, 40.0, 1),
                       row(5, 30.0, 1));
      LineGeometry geom = (LineGeometry) vo.getGeometry();

      assertEquals(5, vo.getTransformedPoints().length);
      assertEquals(geom.getTupleCount(), vo.getTransformedPoints().length);
   }

   @Test
   @DisplayName("a null value stays in the line as a NaN point")
   void nullLeavesGap() {
      LineVO vo = createVO(3, row(1, 1.0, 3), row(2, null, 3), row(3, 3.0, 3), row(4, 4.0, 3));
      Point2D[] pts = vo.getTransformedPoints();

      assertEquals(4, pts.length);

      for(int i = 0; i < 4; i++) {
         boolean nan = Double.isNaN(pts[i].getX()) || Double.isNaN(pts[i].getY());
         assertEquals(i == 1, nan, "point " + i);
      }
   }

   @Test
   @DisplayName("rows with the same x keep their order")
   void duplicateXKeepsOrder() {
      LineVO vo = plot(row(1, 10.0, 1), row(1, 30.0, 1), row(2, 50.0, 1));
      Point2D[] pts = vo.getTransformedPoints();

      assertEquals(3, pts.length);
      assertEquals(pts[0].getX(), pts[1].getX(), EPS);
      assertEquals(0.5, (pts[1].getY() - pts[0].getY()) / (pts[2].getY() - pts[0].getY()), 1e-9);
   }

   // ---- bounds and shapes

   @Test
   @DisplayName("single point bounds are 3 x 3 for size 3, centered on the point")
   void singlePointBounds() {
      LineVO vo = createVO(3, row(5, 50.0, 3));
      Rectangle2D bounds = vo.getBounds();

      // position (500, 500); line size of 3 is 2, half side = 2 / 2 + 0.5
      assertEquals(3, bounds.getWidth(), EPS);
      assertEquals(3, bounds.getHeight(), EPS);
      assertEquals(500, bounds.getCenterX(), EPS);
      assertEquals(500, bounds.getCenterY(), EPS);
   }

   @Test
   @DisplayName("line bounds are the box of the points widened by 1.5 on each side")
   void lineBoundsAreBoxOfPointsPlusHalfSize() {
      LineVO vo = createVO(3, row(1, 10.0, 3), row(2, 50.0, 3), row(3, 30.0, 3));
      Rectangle2D bounds = vo.getBounds();

      // points (100,100) (200,500) (300,300)
      assertEquals(98.5, bounds.getMinX(), EPS);
      assertEquals(301.5, bounds.getMaxX(), EPS);
      assertEquals(98.5, bounds.getMinY(), EPS);
      assertEquals(501.5, bounds.getMaxY(), EPS);
   }

   @Test
   @DisplayName("bounds ignore a null point")
   void boundsIgnoreNullPoints() {
      LineVO withNull = createVO(3, row(1, 10.0, 3), row(2, null, 3), row(3, 30.0, 3));
      LineVO without = createVO(3, row(1, 10.0, 3), row(3, 30.0, 3));

      Rectangle2D bounds = withNull.getBounds();

      assertEquals(without.getBounds(), bounds);
      // points (100,100) and (300,300)
      assertEquals(98.5, bounds.getMinX(), EPS);
      assertEquals(301.5, bounds.getMaxX(), EPS);
      assertEquals(98.5, bounds.getMinY(), EPS);
      assertEquals(301.5, bounds.getMaxY(), EPS);
   }

   @Test
   @DisplayName("a line without any valid point has no bounds (pinned behavior)")
   void allNullLineHasNoBounds() {
      LineVO vo = createVO(3, row(1, null, 3), row(2, null, 3), row(3, null, 3));

      assertNull(vo.getBounds());
   }

   @Test
   @DisplayName("link shapes are circles of diameter 2 around each point of a size 3 line")
   void linkShapes() {
      LineVO vo = createVO(3, row(1, 10.0, 3), row(2, 50.0, 3), row(3, 30.0, 3));
      Shape[] shapes = vo.getShapes();
      double[][] expected = { { 100, 100 }, { 200, 500 }, { 300, 300 } };

      assertEquals(3, shapes.length);

      for(int i = 0; i < 3; i++) {
         Rectangle2D box = shapes[i].getBounds2D();

         assertEquals(expected[i][0], box.getCenterX(), EPS, "x " + i);
         assertEquals(expected[i][1], box.getCenterY(), EPS, "y " + i);
         assertEquals(2, box.getWidth(), EPS, "width " + i);
         assertEquals(2, box.getHeight(), EPS, "height " + i);
      }
   }

   @ParameterizedTest(name = "size {0}")
   @ValueSource(ints = { 1, 3, 11, 19 })
   @DisplayName("bounds contain every link shape")
   void boundsContainShapes(int size) {
      LineVO vo = createVO(size, row(1, 10.0, size), row(2, 50.0, size), row(3, 30.0, size));
      Rectangle2D bounds = vo.getBounds();

      // Rectangle2D.contains is half-open, so compare against the bounds grown by EPS
      Rectangle2D grown = new Rectangle2D.Double(bounds.getX() - EPS, bounds.getY() - EPS,
                                                 bounds.getWidth() + 2 * EPS,
                                                 bounds.getHeight() + 2 * EPS);

      for(Shape shape : vo.getShapes()) {
         assertTrue(grown.contains(shape.getBounds2D()), "size " + size);
      }
   }

   // ---- unscaled size: P0 at (50, 500) with size 3 (radius 1), P1 at (900, 100) with size 19
   // (radius 5), identity screen transform

   private LineVO twoSizedPoints() {
      return createVO(new StaticSizeFrame("s"), row(0.5, 50.0, 3), row(9, 10.0, 19));
   }

   @ParameterizedTest(name = "side {0}")
   @CsvSource({ "TOP,1.5", "RIGHT,5.5", "LEFT,1.5" })
   @DisplayName("unscaled size is the radius of the point at that side plus half a pixel")
   void unscaledSize(String side, double expected) {
      LineVO vo = twoSizedPoints();
      int pos = switch(side) {
         case "TOP" -> GraphConstants.TOP;
         case "RIGHT" -> GraphConstants.RIGHT;
         default -> GraphConstants.LEFT;
      };

      assertEquals(expected, vo.getUnscaledSize(pos, null), EPS);
   }

   @Test
   @DisplayName("with two top-most points of different size, the first one decides the TOP size")
   void unscaledSizeTopTie() {
      // P0 (x 0.5, radius 5) and P1 (x 9, radius 1) are both at y 50, P2 is lower; TOP uses a
      // strict comparison, so the first top-most point (the big one) wins
      LineVO vo = createVO(new StaticSizeFrame("s"), row(0.5, 50.0, 19), row(9, 50.0, 3),
                           row(5, 10.0, 3));

      assertEquals(5.5, vo.getUnscaledSize(GraphConstants.TOP, null), EPS);
   }

   // ---- clone

   @Test
   @DisplayName("concatenating a transform on a clone moves the clone and leaves the original alone")
   void cloneHasOwnScreenTransform() {
      LineVO vo = createVO(3, row(1, 10.0, 3), row(2, 50.0, 3));
      AffineTransform before = new AffineTransform(vo.getScreenTransform());
      Point2D[] pointsBefore = vo.getTransformedPoints();
      LineVO clone = (LineVO) vo.clone();
      AffineTransform moved = new AffineTransform(clone.getScreenTransform());
      moved.concatenate(AffineTransform.getTranslateInstance(40, 50));

      clone.setScreenTransform(moved);

      assertEquals(before, vo.getScreenTransform());
      Point2D[] pointsAfter = vo.getTransformedPoints();
      Point2D[] clonePoints = clone.getTransformedPoints();

      for(int i = 0; i < pointsBefore.length; i++) {
         assertEquals(pointsBefore[i], pointsAfter[i], "original point " + i);
         assertEquals(pointsBefore[i].getX() + 40, clonePoints[i].getX(), EPS, "clone x " + i);
         assertEquals(pointsBefore[i].getY() + 50, clonePoints[i].getY(), EPS, "clone y " + i);
      }
   }

   /*
    * Pins the current, intended behavior: clone() shares the points array with the original,
    * so clone.transform(coord) also moves the original. No code path does that: clones only
    * come from Coordinate.getScaledVGraph() (element bounds during layout) and only get a
    * screen transform; only VGraph.layout() calls transform(coord), on the originals. The
    * scaled clone relies on sharing the already transformed points, so copying the array in
    * clone() would give wrong element bounds. If this test fails, clone() changed: re-check
    * facet, GeoCoord and RelationCoord layouts before adjusting it.
    */
   @Test
   @DisplayName("a clone shares the points of the original (pinned behavior)")
   void cloneSharesPoints() {
      LineVO vo = createVO(3, row(1, 10.0, 3), row(2, 50.0, 3));
      Point2D[] before = Arrays.stream(vo.getTransformedPoints())
         .map(p -> new Point2D.Double(p.getX(), p.getY())).toArray(Point2D[]::new);
      LineVO clone = (LineVO) vo.clone();
      RectCoord shifting = new RectCoord(lin(0, 10, "x"), lin(0, 100, "y")) {
         @Override
         public Object transformShape(Object geom) {
            Point2D p = (Point2D) geom;
            return new Point2D.Double(p.getX() + 25, p.getY());
         }
      };

      clone.transform(shifting);

      Point2D[] after = vo.getTransformedPoints();

      for(int i = 0; i < before.length; i++) {
         assertEquals(before[i].getX() + 25, after[i].getX(), EPS, "point " + i);
      }
   }

   // ---- strokes, by pixel

   private static final int IMG = 100;

   /** A hand made line description painted without a coordinate system. */
   private static class Info implements LineVO.LineInfo {
      Info(Point2D.Double... points) {
         this.points = points;
      }

      @Override
      public Point2D[] getPoints() {
         return points;
      }

      @Override
      public Color getColor(int idx) {
         return Color.BLACK;
      }

      @Override
      public double getSize(int idx) {
         return 11; // stroke of 6 px
      }

      @Override
      public GLine getLine(int idx) {
         return line;
      }

      @Override
      public AffineTransform getScreenTransform() {
         return new AffineTransform();
      }

      @Override
      public LineElement.Type getType() {
         return type;
      }

      @Override
      public boolean isIgnoreNull() {
         return ignoreNull;
      }

      @Override
      public int getFillLineStyle() {
         return fillStyle;
      }

      @Override
      public double getFillLineAlpha() {
         return 0.3;
      }

      Info type(LineElement.Type type) {
         this.type = type;
         return this;
      }

      Info ignoreNull(boolean ignoreNull) {
         this.ignoreNull = ignoreNull;
         return this;
      }

      Info line(GLine line) {
         this.line = line;
         return this;
      }

      Info fill(int fillStyle) {
         this.fillStyle = fillStyle;
         return this;
      }

      private final Point2D.Double[] points;
      private LineElement.Type type = LineElement.Type.STRAIGHT;
      private boolean ignoreNull = true;
      private GLine line = null;
      private int fillStyle = 0;
   }

   private static BufferedImage paint(Info info) {
      BufferedImage img = new BufferedImage(IMG, IMG, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, IMG, IMG);
      LineVO.paintLine(g, 1, info);
      g.dispose();
      return img;
   }

   private static boolean painted(BufferedImage img, int x, int y) {
      return img.getRGB(x, y) != 0xFFFFFFFF;
   }

   private static boolean paintedInColumns(BufferedImage img, int x1, int x2) {
      for(int x = x1; x <= x2; x++) {
         for(int y = 0; y < IMG; y++) {
            if(painted(img, x, y)) {
               return true;
            }
         }
      }

      return false;
   }

   private static Point2D.Double p(double x, double y) {
      return new Point2D.Double(x, y);
   }

   @Test
   @DisplayName("straight line is painted on the segment and not 30 px away")
   void straightLinePixels() {
      BufferedImage img = paint(new Info(p(10, 10), p(90, 90)));

      assertTrue(painted(img, 50, 50));
      assertFalse(painted(img, 50, 20));
   }

   @Test
   @DisplayName("step line goes horizontally first, then vertically, not along the diagonal")
   void stepLinePixels() {
      BufferedImage img = paint(new Info(p(10, 20), p(60, 70)).type(LineElement.Type.STEP));

      assertTrue(painted(img, 35, 20));
      assertTrue(painted(img, 60, 45));
      assertFalse(painted(img, 35, 45));
   }

   @Test
   @DisplayName("jump line has the horizontal part but no vertical connection")
   void jumpLinePixels() {
      BufferedImage img = paint(new Info(p(10, 20), p(60, 70)).type(LineElement.Type.JUMP));

      assertTrue(painted(img, 35, 20));
      assertFalse(painted(img, 60, 45));
      assertFalse(painted(img, 35, 45));
   }

   private static Point2D.Double[] gapPoints() {
      return new Point2D.Double[] { p(10, 50), p(30, 50), p(50, Double.NaN), p(70, 50),
                                    p(90, 50) };
   }

   @Test
   @DisplayName("a null point breaks the line when null is not ignored")
   void gapIsNotConnected() {
      BufferedImage img = paint(new Info(gapPoints()).ignoreNull(false));

      assertTrue(painted(img, 20, 50));
      assertTrue(painted(img, 80, 50));
      assertFalse(painted(img, 50, 50));
   }

   @Test
   @DisplayName("a null point is bridged when null is ignored")
   void gapIsConnectedWhenIgnoringNull() {
      BufferedImage img = paint(new Info(gapPoints()).ignoreNull(true));

      assertTrue(painted(img, 50, 50));
   }

   @Test
   @DisplayName("the gap of a null point is filled with a dash when a fill line style is set")
   void gapFilledWithDash() {
      BufferedImage img = paint(new Info(gapPoints()).ignoreNull(false)
                                   .fill(GraphConstants.DASH_LINE));

      assertTrue(paintedInColumns(img, 36, 64));
   }

   @Test
   @DisplayName("the gap of a null point stays empty without a fill line style")
   void gapEmptyWithoutFillStyle() {
      BufferedImage img = paint(new Info(gapPoints()).ignoreNull(false).fill(0));

      assertFalse(paintedInColumns(img, 36, 64));
   }
   // ---- ignoreNull handling in step, jump and dashed lines, and the BOTTOM unscaled size (Bugs #82051 to #82053)

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
