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

import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.schema.*;
import inetsoft.graph.schema.SchemaTestSupport.Mapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static inetsoft.graph.schema.SchemaTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Schema visual object (candle, stock and box charts): bounds from the painter shapes padded for
 * the line width, the screen transform applied on a copy, centring of single scale charts, and
 * dodging of visual objects that fall on the same column.
 */
@Tag("core")
class SchemaVOTest {
   /** A visual object with its own element, geometry, coordinate and painter. */
   private static SchemaVO vo(SchemaPainter painter, double x, boolean hasX, int collision,
                              Mapping mapping, double... vars)
   {
      SchemaElement element = element(painter);
      element.setCollisionModifier(collision);
      double[] tuple = tuple(vars);
      tuple[0] = x;
      String[] names = new String[vars.length];

      for(int i = 0; i < names.length; i++) {
         names[i] = "v" + i;
      }

      SchemaGeometry geometry = geometry(element, tuple, names);
      RectCoord coord = coord(hasX, mapping, 100);
      painter.init(geometry, coord);
      return new SchemaVO(geometry, coord, painter);
   }

   private static SchemaVO vo(SchemaPainter painter, double... vars) {
      return vo(painter, 5, true, GraphElement.DODGE_SYMMETRIC, Mapping.SCALED, vars);
   }

   private static SchemaVO candle(double... vars) {
      return vo(new CandlePainter(), vars);
   }

   private static double wickX(SchemaVO vo) {
      return ((Line2D) vo.getShapes()[0]).getX1();
   }

   // ----------------------------------------------------------------------------- bounds

   @Test
   @DisplayName("candle bounds: wick and body padded by 1")
   void candleBounds() {
      assertBounds(candle(80, 60, 40, 20).getBounds(), 34, 199, 32, 602);
   }

   @Test
   @DisplayName("stock bounds: ticks and wick padded by 1")
   void stockBounds() {
      assertBounds(vo(new StockPainter(), 80, 60, 40, 20).getBounds(), 39, 199, 22, 602);
   }

   @Test
   @DisplayName("box bounds: wick, caps, box and median padded by 1")
   void boxBounds() {
      assertBounds(vo(new BoxPainter(), 90, 70, 50, 30, 10).getBounds(), 34, 99, 32, 802);
   }

   @Test
   @DisplayName("a zero-size wick is skipped when computing the bounds")
   void zeroSizeShapeSkipped() {
      assertBounds(vo(new StockPainter(), 50, 50, 50, 50).getBounds(), 39, 499, 22, 2);
   }

   @Test
   @DisplayName("shapes with NaN positions give an empty rectangle, never NaN")
   void nanBounds() {
      double nan = Double.NaN;
      Rectangle2D bounds = candle(nan, nan, nan, nan).getBounds();

      assertBounds(bounds, 0, 0, 0, 0);
   }

   @Test
   @DisplayName("the screen transform scales the shapes but not the padding")
   void scaledBounds() {
      SchemaVO vo = candle(80, 60, 40, 20);
      vo.setScreenTransform(AffineTransform.getScaleInstance(2, 2));

      assertBounds(vo.getBounds(), 69, 399, 62, 1202);
   }

   // ---------------------------------------------------------------------------- getShapes

   @ParameterizedTest(name = "{0} shapes")
   @CsvSource({ "2, candle", "3, stock", "5, box" })
   @DisplayName("shapes follow the screen transform on every call and the raw shapes come back")
   void shapesFollowScreenTransform(int count, String kind) {
      SchemaPainter painter = kind.equals("candle") ? new CandlePainter()
         : kind.equals("stock") ? new StockPainter() : new BoxPainter();
      double[] vars = kind.equals("box") ? new double[] { 90, 70, 50, 30, 10 }
         : new double[] { 80, 60, 40, 20 };
      SchemaVO vo = vo(painter, vars);
      Rectangle2D[] raw = new Rectangle2D[count];
      Shape[] first = vo.getShapes();

      assertEquals(count, first.length);

      for(int i = 0; i < count; i++) {
         raw[i] = first[i].getBounds2D();
      }

      vo.setScreenTransform(AffineTransform.getTranslateInstance(100, 0));

      for(int call = 0; call < 2; call++) {
         Shape[] shapes = vo.getShapes();

         for(int i = 0; i < count; i++) {
            assertBounds(shapes[i], raw[i].getX() + 100, raw[i].getY(), raw[i].getWidth(),
                         raw[i].getHeight());
         }
      }

      vo.setScreenTransform(new AffineTransform());
      Shape[] again = vo.getShapes();

      for(int i = 0; i < count; i++) {
         assertBounds(again[i], raw[i].getX(), raw[i].getY(), raw[i].getWidth(), raw[i].getHeight());
      }
   }

   // ------------------------------------------------------------------------------- paint

   @Test
   @DisplayName("painting twice with a screen transform does not accumulate the transform")
   void paintIsRepeatable() {
      SchemaPainter painter = new CandlePainter();
      SchemaElement element = element(painter);
      double[] tuple = tuple(80, 40, 60, 20);
      SchemaGeometry geometry = geometry(element, tuple, "a", "b", "c", "d");
      RectCoord coord = coord(true, Mapping.PIXEL, 100);
      painter.init(geometry, coord);
      SchemaVO vo = new SchemaVO(geometry, coord, painter);
      vo.setScreenTransform(AffineTransform.getTranslateInstance(10, 0));
      Rectangle2D before = vo.getBounds();

      BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      Color background = new Color(200, 200, 200);
      g.setColor(background);
      g.fillRect(0, 0, 100, 100);
      vo.paint(g);
      vo.paint(g);
      g.dispose();

      assertEquals(Color.GREEN.getRGB(), img.getRGB(70, 50), "body moved by the transform");
      assertEquals(background.getRGB(), img.getRGB(80, 50), "nothing painted beyond the body");
      assertEquals(before, vo.getBounds());
   }

   // ------------------------------------------------------------------------- 1D centering

   @Test
   @DisplayName("on a single scale chart the painter is centred in the slot")
   void centeredOnSingleScale() {
      SchemaVO vo = vo(new CandlePainter(), 5, false, GraphElement.DODGE_SYMMETRIC, Mapping.SCALED,
                       80, 60, 40, 20);

      assertLine(vo.getShapes()[0], 50, 800, 50, 200);
      assertBounds(vo.getShapes()[1], 35, 400, 30, 200);
   }

   @Test
   @DisplayName("without the center option the painter is not moved")
   void notCenteredWithoutCenterOption() {
      SchemaVO vo = vo(new CandlePainter(), 5, false, GraphElement.MOVE_DODGE, Mapping.SCALED,
                       80, 60, 40, 20);

      assertLine(vo.getShapes()[0], 0, 800, 0, 200);
      assertBounds(vo.getShapes()[1], -15, 400, 30, 200);
   }

   // ---------------------------------------------------------------------- requiresDodge

   @ParameterizedTest(name = "collision {0}")
   @CsvSource({ "0, false", "1, true", "2, true", "4, false", "3, true" })
   @DisplayName("only the dodge and center options require dodging")
   void requiresDodgeByOption(int collision, boolean expected) {
      SchemaVO vo = vo(new CandlePainter(), 5, true, collision, Mapping.SCALED, 80, 60, 40, 20);

      assertEquals(expected, vo.requiresDodge());
   }

   @ParameterizedTest(name = "x {0} vs 5.0")
   @CsvSource({ "5.19, true", "5.21, false" })
   @DisplayName("objects whose centres are closer than 2 pixels on the same y range dodge")
   void requiresDodgeByDistance(double x2, boolean expected) {
      SchemaVO a = candle(80, 60, 40, 20);
      SchemaVO b = vo(new CandlePainter(), x2, true, GraphElement.DODGE_SYMMETRIC, Mapping.SCALED,
                      80, 60, 40, 20);

      assertEquals(expected, a.requiresDodge(b));
      assertFalse(a.requiresDodge(a));
   }

   @Test
   @DisplayName("different overlay hint, another visual object class or disjoint y ranges do not dodge (pinned)")
   void requiresDodgeGuards() {
      SchemaVO a = candle(80, 60, 40, 20);

      SchemaVO overlay = candle(80, 60, 40, 20);
      elementOf(overlay).setHint("overlay", "x");
      assertFalse(a.requiresDodge(overlay));

      ElementVO other = mock(ElementVO.class);
      when(other.getGeometry()).thenReturn(a.getGeometry());
      assertFalse(a.requiresDodge(other));

      SchemaVO far = candle(380, 360, 340, 320);
      assertFalse(a.requiresDodge(far));
   }

   private static SchemaElement elementOf(SchemaVO vo) {
      return (SchemaElement) ((SchemaGeometry) vo.getGeometry()).getElement();
   }

   // ---------------------------------------------------------------------------- dodge

   private static Map<ElementVO, List<ElementVO>> overlaps(SchemaVO first, SchemaVO... others) {
      Map<ElementVO, List<ElementVO>> map = new HashMap<>();
      map.put(first, new ArrayList<>(Arrays.asList(others)));
      return map;
   }

   @Test
   @DisplayName("two overlapping candles are placed side by side, centred on the column")
   void dodgeTwo() {
      SchemaVO a = candle(80, 60, 40, 20);
      SchemaVO b = candle(80, 60, 40, 20);

      a.dodge(overlaps(a, b), mock(RectCoord.class));

      assertEquals(33.5, wickX(a), EPS);
      assertEquals(66.5, wickX(b), EPS);
   }

   @Test
   @DisplayName("three overlapping candles are placed symmetrically about the column")
   void dodgeThree() {
      SchemaVO a = candle(80, 60, 40, 20);
      SchemaVO b = candle(80, 60, 40, 20);
      SchemaVO c = candle(80, 60, 40, 20);

      a.dodge(overlaps(a, b, c), mock(RectCoord.class));

      assertEquals(17, wickX(a), EPS);
      assertEquals(50, wickX(b), EPS);
      assertEquals(83, wickX(c), EPS);
   }

   @Test
   @DisplayName("dodge without the center option moves only the others")
   void dodgeWithoutCenter() {
      SchemaVO a = vo(new CandlePainter(), 5, true, GraphElement.MOVE_DODGE, Mapping.SCALED,
                      80, 60, 40, 20);
      SchemaVO b = vo(new CandlePainter(), 5, true, GraphElement.MOVE_DODGE, Mapping.SCALED,
                      80, 60, 40, 20);

      a.dodge(overlaps(a, b), mock(RectCoord.class));

      assertEquals(50, wickX(a), EPS);
      assertEquals(83, wickX(b), EPS);
   }

   @Test
   @DisplayName("dodge with no overlapping objects changes nothing")
   void dodgeNoOverlaps() {
      SchemaVO a = candle(80, 60, 40, 20);
      Rectangle2D before = a.getBounds();

      a.dodge(overlaps(a), mock(RectCoord.class));

      assertEquals(before, a.getBounds());
   }

   // ---------------------------------------------------------------------------- clone
}
