/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.graph.schema;

import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.schema.SchemaTestSupport.Fixture;
import inetsoft.graph.schema.SchemaTestSupport.Mapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.Random;

import static inetsoft.graph.schema.SchemaTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Box painter: a scripted SchemaElement has no color frame and painting with a border color must
 * not NPE; the five-number geometry (max, Q75, median, Q25, min) is derived from the positions.
 */
@Tag("core")
class BoxPainterTest {
   @Test
   void borderColorWithoutColorFrameDoesNotThrow() throws Exception {
      SchemaElement elem = new SchemaElement(new BoxPainter());
      elem.setBorderColor(Color.RED);
      assertNull(elem.getColorFrame());

      BufferedImage img = paint(elem);

      // the median line is drawn with the border color
      assertEquals(Color.RED.getRGB(), img.getRGB(10, 20));
   }

   @Test
   void borderColorWithColorFrameStillPaints() throws Exception {
      SchemaElement elem = new SchemaElement(new BoxPainter());
      elem.setBorderColor(Color.RED);
      elem.setColorFrame(new StaticColorFrame(Color.BLUE));

      BufferedImage img = paint(elem);

      assertNotEquals(0, img.getRGB(10, 20));
   }

   private BufferedImage paint(SchemaElement elem) throws Exception {
      BoxPainter painter = new BoxPainter();
      SchemaGeometry geometry = mock(SchemaGeometry.class);
      when(geometry.getElement()).thenReturn(elem);
      when(geometry.getColor(0)).thenReturn(Color.GREEN);

      Field gf = SchemaPainter.class.getDeclaredField("geometry");
      gf.setAccessible(true);
      gf.set(painter, geometry);

      Field sf = BoxPainter.class.getDeclaredField("shapes");
      sf.setAccessible(true);
      sf.set(painter, new java.awt.Shape[] {
         new Line2D.Double(10, 0, 10, 10), new Line2D.Double(5, 0, 15, 0),
         new Line2D.Double(5, 10, 15, 10), new Rectangle2D.Double(2, 2, 16, 6),
         new Line2D.Double(0, 20, 20, 20) });

      BufferedImage img = new BufferedImage(30, 30, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      painter.paint(g);
      g.dispose();
      return img;
   }

   // -------------------------------------------------------------------------- geometry

   private static Fixture box(double... vars) {
      return fixture(new BoxPainter(), vars);
   }

   @Test
   @DisplayName("five numbers give the wick, two caps, the box and the median line")
   void fiveNumberGeometry() {
      Fixture f = box(90, 70, 50, 30, 10);

      assertLine(f.painter.getShape(0), 50, 900, 50, 100);
      assertLine(f.painter.getShape(1), 42.5, 900, 57.5, 900);
      assertLine(f.painter.getShape(2), 42.5, 100, 57.5, 100);
      assertBounds(f.painter.getShape(3), 35, 300, 30, 400);
      assertLine(f.painter.getShape(4), 35, 500, 65, 500);
   }

   @ParameterizedTest(name = "seed {0}")
   @ValueSource(longs = { 1, 2, 3, 4, 5, 6, 7, 8 })
   @DisplayName("ordered numbers: caps and box inside the wick, median inside the box")
   void orderingInvariants(long seed) {
      Random random = new Random(seed);
      double[] v = new double[5];

      for(int i = 0; i < 5; i++) {
         v[i] = random.nextInt(100);
      }

      java.util.Arrays.sort(v);
      // max, q75, median, q25, min
      Fixture f = box(v[4], v[3], v[2], v[1], v[0]);
      Line2D wick = (Line2D) f.painter.getShape(0);
      double top = Math.max(wick.getY1(), wick.getY2());
      double bottom = Math.min(wick.getY1(), wick.getY2());
      Rectangle2D rect = f.painter.getShape(3).getBounds2D();
      Line2D median = (Line2D) f.painter.getShape(4);

      for(int i = 1; i <= 2; i++) {
         double y = ((Line2D) f.painter.getShape(i)).getY1();
         assertTrue(y >= bottom - EPS && y <= top + EPS, "cap " + i);
      }

      assertTrue(rect.getMinY() >= bottom - EPS && rect.getMaxY() <= top + EPS, "box in wick");
      assertTrue(median.getY1() >= rect.getMinY() - EPS && median.getY1() <= rect.getMaxY() + EPS,
                 "median in box");
      assertEquals(rect.getMinX(), Math.min(median.getX1(), median.getX2()), EPS);
      assertEquals(rect.getMaxX(), Math.max(median.getX1(), median.getX2()), EPS);
   }

   @Test
   @DisplayName("reversed scale: box height is normalised, median stays inside")
   void reversedScale() {
      Fixture f = fixture(new BoxPainter(), true, Mapping.REVERSED, 100, 90, 70, 50, 30, 10);

      assertLine(f.painter.getShape(0), 50, 100, 50, 900);
      assertBounds(f.painter.getShape(3), 35, 300, 30, 400);
      assertEquals(500, ((Line2D) f.painter.getShape(4)).getY1(), EPS);
   }

   @Test
   @DisplayName("a single value collapses the box and the wick onto one row, no NaN")
   void singleValue() {
      Fixture f = box(50, 50, 50, 50, 50);

      assertBounds(f.painter.getShape(3), 35, 500, 30, 0);
      assertLine(f.painter.getShape(0), 50, 500, 50, 500);
      assertEquals(500, ((Line2D) f.painter.getShape(1)).getY1(), EPS);
      assertEquals(500, ((Line2D) f.painter.getShape(2)).getY1(), EPS);
   }

   @Test
   @DisplayName("equal quartiles give a zero-height box with the median on it")
   void equalQuartiles() {
      Fixture f = box(90, 50, 50, 50, 10);

      assertBounds(f.painter.getShape(3), 35, 500, 30, 0);
      assertLine(f.painter.getShape(4), 35, 500, 65, 500);
      assertLine(f.painter.getShape(0), 50, 900, 50, 100);
   }

   @Test
   @DisplayName("median equal to a quartile lies on the box edge")
   void medianOnQuartile() {
      Fixture f = box(90, 70, 30, 30, 10);

      assertLine(f.painter.getShape(4), 35, 300, 65, 300);
      assertBounds(f.painter.getShape(3), 35, 300, 30, 400);
   }

   @Test
   @DisplayName("negative values")
   void negativeValues() {
      Fixture f = box(-10, -30, -50, -70, -90);

      assertLine(f.painter.getShape(0), 50, -100, 50, -900);
      assertBounds(f.painter.getShape(3), 35, -700, 30, 400);
      assertEquals(-500, ((Line2D) f.painter.getShape(4)).getY1(), EPS);
   }

   @Test
   @DisplayName("slot narrower than the preferred width limits the box and caps")
   void narrowSlot() {
      Fixture f = fixture(new BoxPainter(), true, Mapping.SCALED, 20, 90, 70, 50, 30, 10);

      assertBounds(f.painter.getShape(3), 40, 300, 20, 400);
      assertLine(f.painter.getShape(1), 45, 900, 55, 900);
   }

   @Test
   @DisplayName("shape and texture frames are not supported; color and size are")
   void supportsFrame() {
      BoxPainter painter = new BoxPainter();

      assertFalse(painter.supportsFrame(mock(ShapeFrame.class)));
      assertFalse(painter.supportsFrame(mock(TextureFrame.class)));
      assertTrue(painter.supportsFrame(mock(ColorFrame.class)));
      assertTrue(painter.supportsFrame(mock(SizeFrame.class)));
   }

   @Test
   @DisplayName("clone has its own shapes")
   void cloneIsIndependent() {
      Fixture f = box(90, 70, 50, 30, 10);
      BoxPainter copy = (BoxPainter) f.painter.clone();
      Shape original = f.painter.getShape(3);
      copy.setShape(3, new Line2D.Double(0, 0, 1, 1));

      assertSame(original, f.painter.getShape(3));
   }

   @Test
   @DisplayName("translate keeps the lines as lines and moves everything by the offset")
   void transformScreen() {
      Fixture f = box(90, 70, 50, 30, 10);
      f.painter.transformScreen(AffineTransform.getTranslateInstance(5, 7));

      assertLine(f.painter.getShape(0), 55, 907, 55, 107);
      assertLine(f.painter.getShape(1), 47.5, 907, 62.5, 907);
      assertLine(f.painter.getShape(2), 47.5, 107, 62.5, 107);
      assertBounds(f.painter.getShape(3), 40, 307, 30, 400);
      assertLine(f.painter.getShape(4), 40, 507, 70, 507);
   }

   @Test
   @DisplayName("without an x scale the x position stays 0")
   void withoutXScale() {
      Fixture f = fixture(new BoxPainter(), false, Mapping.SCALED, 100, 90, 70, 50, 30, 10);

      assertLine(f.painter.getShape(0), 0, 900, 0, 100);
      assertBounds(f.painter.getShape(3), -15, 300, 30, 400);
   }

   @Test
   @DisplayName("five shapes: four lines and the box rectangle at index 3")
   void shapeCount() {
      Fixture f = box(90, 70, 50, 30, 10);

      assertEquals(5, f.painter.getShapeCount());

      for(int i = 0; i < 5; i++) {
         if(i == 3) {
            assertInstanceOf(Rectangle2D.class, f.painter.getShape(i));
         }
         else {
            assertInstanceOf(Line2D.class, f.painter.getShape(i));
         }
      }
   }
}
