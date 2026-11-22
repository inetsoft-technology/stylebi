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
import org.junit.jupiter.api.Disabled;
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

      for(Shape shape : vo.getShapes()) {
         assertTrue(bounds.contains(shape.getBounds2D()), "size " + size);
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
   @Disabled("Bug #82051: LineVO.getUnscaledSize(BOTTOM) falls through into the LEFT case")
   @DisplayName("unscaled size at the bottom is the radius of the lowest point")
   void unscaledSizeBottom() {
      assertEquals(5.5, twoSizedPoints().getUnscaledSize(GraphConstants.BOTTOM, null), EPS);
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

   @Test
   @Disabled("Not filed (latent: only VGraph.layout transforms the points, clones only get a screen transform): expected a clone to own its points, actual LineVO.clone keeps the same points array, so transform(coord) on the clone moves the points of the original")
   @DisplayName("transforming the points of a clone leaves the original points alone")
   void cloneHasOwnPoints() {
      LineVO vo = createVO(3, row(1, 10.0, 3), row(2, 50.0, 3));
      Point2D[] before = vo.getTransformedPoints();
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
         assertEquals(before[i], after[i], "point " + i);
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
   @Disabled("Bug #82052: Step or jump line with ignoreNull draws nothing after a null point")
   @DisplayName("a step line connects over an ignored null point")
   void stepLineConnectsOverNull() {
      BufferedImage img = paint(new Info(p(10, 20), p(40, Double.NaN), p(70, 60))
                                   .type(LineElement.Type.STEP).ignoreNull(true));

      assertTrue(painted(img, 40, 20));
      assertTrue(painted(img, 70, 40));
   }

   @Test
   @DisplayName("control: a solid line with a null second point is painted")
   void solidLineWithNullSecondPointIsPainted() {
      BufferedImage img = paint(new Info(p(10, 50), p(40, Double.NaN), p(70, 50), p(90, 50))
                                   .ignoreNull(true));

      assertTrue(paintedInColumns(img, 20, 60));
   }

   @Test
   @Disabled("Bug #82053: Dashed line with ignoreNull loses the segment next to a null point")
   @DisplayName("a dashed line with a null second point is painted")
   void dashedLineWithNullSecondPointIsPainted() {
      BufferedImage img = paint(new Info(p(10, 50), p(40, Double.NaN), p(70, 50), p(90, 50))
                                   .ignoreNull(true).line(GLine.DASH_LINE));

      assertTrue(paintedInColumns(img, 20, 60));
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
}
