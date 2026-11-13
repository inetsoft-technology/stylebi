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
package inetsoft.graph.schema;

import inetsoft.graph.aesthetic.VisualModel;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A box plot under the width rule draws its box 30% of the slot, 6 to 28 screen pixels wide and
 * at least 2px tall, and a marked box takes the chart's bar corner.
 */
// GTool, reached by the painter, initializes the report renderer on first use
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BoxPainterModernTest {
   @Test
   void boxArcDiameter() {
      assertEquals(4.0, BoxPainter.boxArcDiameter(0.08, 20), 1e-9, "radius 2 at s = 20");
      assertEquals(4.48, BoxPainter.boxArcDiameter(0.08, 28), 1e-9, "f·s above the floor");
      assertEquals(0, BoxPainter.boxArcDiameter(0, 20), 1e-9, "an author's 0 is square");
   }

   @Test
   void aWideSlotCapsTheBoxAt28() {
      AffineTransform screen = screen(200);
      Rectangle2D box = fitted(box(true), screen).getShape(3).getBounds2D();

      assertTrue(BoxPainter.BOX_WIDTH_RATIO * 200 > 28, "premise: 30% of the slot is wider");
      assertEquals(28, box.getWidth(), 0.5, "width");
      assertEquals(slotCenter(screen), box.getCenterX(), 0.5, "centred on the slot");
   }

   @Test
   void aNarrowSlotFloorsTheBoxAt6() {
      AffineTransform screen = screen(10);
      Rectangle2D box = fitted(box(true), screen).getShape(3).getBounds2D();

      assertEquals(6, box.getWidth(), 0.5, "width");
      assertEquals(slotCenter(screen), box.getCenterX(), 0.5, "centred on the slot");
   }

   @Test
   void aMidSlotKeeps30Percent() {
      Rectangle2D box = fitted(box(true), screen(60)).getShape(3).getBounds2D();

      assertEquals(18, box.getWidth(), 0.5);
   }

   @Test
   void theWhiskerCapsAndMedianScaleWithTheBox() {
      BoxPainter painter = fitted(box(true), screen(200));
      double box = painter.getShape(3).getBounds2D().getWidth();

      assertEquals(box / 2, painter.getShape(1).getBounds2D().getWidth(), 0.5, "top cap");
      assertEquals(box / 2, painter.getShape(2).getBounds2D().getWidth(), 0.5, "bottom cap");
      assertEquals(box, painter.getShape(4).getBounds2D().getWidth(), 0.5, "median");
      assertEquals(painter.getShape(3).getBounds2D().getCenterX(),
                   painter.getShape(0).getBounds2D().getCenterX(), 0.5, "whisker");
   }

   @Test
   void dodgedBoxesStaySideBySide() {
      BoxPainter left = box(true);
      BoxPainter right = box(true);
      AffineTransform screen = screen(200);

      // SchemaVO.dodge, in chart units before layout
      double move = dodgeBounds(left).getWidth() + 1;
      right.transformScreen(AffineTransform.getTranslateInstance(move, 0));
      double x1 = dodgeBounds(left).getCenterX();
      double x2 = dodgeBounds(right).getCenterX();
      AffineTransform center = AffineTransform.getTranslateInstance(-Math.abs(x2 - x1) / 2, 0);
      left.transformScreen(center);
      right.transformScreen(center);

      Rectangle2D a = fitted(left, screen).getShape(3).getBounds2D();
      Rectangle2D b = fitted(right, screen).getShape(3).getBounds2D();

      assertEquals(28, a.getWidth(), 0.5, "left width");
      assertEquals(28, b.getWidth(), 0.5, "right width");
      assertTrue(b.getX() >= a.getMaxX(), "no overlap: " + a + " " + b);
      assertTrue(b.getX() - a.getMaxX() <= 1.5, "gap " + (b.getX() - a.getMaxX()));
      assertEquals(slotCenter(screen), (a.getX() + b.getMaxX()) / 2, 0.5, "pair centred");
   }

   @Test
   void aFlatBoxIsTwoPixelsTall() {
      AffineTransform screen = screen(200);
      Rectangle2D box = fitted(box(true, true, 300, 400, 400, 400, 500), screen)
         .getShape(3).getBounds2D();
      double median = screen.transform(new Point2D.Double(0, 400), null).getY();

      assertEquals(2, box.getHeight(), 1e-6, "height");
      assertEquals(median, box.getCenterY(), 1e-6, "grown about its centre");
   }

   @Test
   void anAuthorSizedFlatBoxIsTwoPixelsTallAtTodaysWidth() {
      AffineTransform screen = screen(200);
      // an author's size turns the width rule off, not the height floor
      Rectangle2D box = fitted(box(false, true, 300, 400, 400, 400, 500), screen)
         .getShape(3).getBounds2D();
      double median = screen.transform(new Point2D.Double(0, 400), null).getY();

      assertEquals(2, box.getHeight(), 1e-6, "height");
      assertEquals(median, box.getCenterY(), 1e-6, "grown about its centre");
      assertEquals(30 * 200 / SLOT, box.getWidth(), 1e-6, "today's 30-unit box");
   }

   @Test
   void aClassicFlatBoxStaysFlat() {
      Rectangle2D box = fitted(box(false, false, 300, 400, 400, 400, 500), screen(200))
         .getShape(3).getBounds2D();

      assertEquals(0, box.getHeight(), 1e-6);
   }

   @Test
   void aRotatedBoxIsClampedAlongItsWidth() {
      double scale = 200 / SLOT;
      // a horizontal box plot: category axis down the screen, value axis across
      AffineTransform screen = new AffineTransform(0, scale, 0.2, 0, 20, 20);
      BoxPainter painter = fitted(box(true, true, 300, 400, 400, 400, 500), screen);
      Rectangle2D box = painter.getShape(3).getBounds2D();
      double center = screen.transform(new Point2D.Double(CENTER, 0), null).getY();

      assertEquals(28, box.getHeight(), 0.5, "width along the category axis");
      assertEquals(center, box.getCenterY(), 0.5, "centred on the slot");
      assertEquals(2, box.getWidth(), 1e-6, "flat box along the value axis");
   }

   @Test
   void aMarkedBoxDrawsTheBarCorner() {
      BoxPainter painter = box(true);
      element(painter).setBoxCornerRadius(0.08);
      RoundRectangle2D drawn = paintedBox(fitted(painter, screen(200)));
      double shortSide = Math.min(drawn.getWidth(), drawn.getHeight());

      assertEquals(28, shortSide, 0.5, "premise: the box is the short side");
      assertEquals(BoxPainter.boxArcDiameter(0.08, shortSide), drawn.getArcWidth(), 1e-9);
      assertEquals(drawn.getArcWidth(), drawn.getArcHeight(), 1e-9);

      element(painter).setBoxCornerRadius(0);
      assertEquals(0, paintedBox(fitted(painter, screen(200))).getArcWidth(), 1e-9, "square");
   }

   @Test
   void classicBoxUnchanged() {
      BoxPainter painter = box(false);
      AffineTransform screen = screen(200);
      BoxPainter unfitted = (BoxPainter) painter.clone();
      unfitted.transformScreen(screen);
      BoxPainter fit = fitted(painter, screen);

      for(int i = 0; i < painter.getShapeCount(); i++) {
         assertEquals(unfitted.getShape(i).getBounds2D(), fit.getShape(i).getBounds2D(),
                      "shape " + i);
      }

      assertEquals(30 * 200 / SLOT, fit.getShape(3).getBounds2D().getWidth(), 1e-6,
                   "today's 30-unit box");

      RoundRectangle2D drawn = paintedBox(fit);
      double shortDim = Math.min(drawn.getWidth(), drawn.getHeight());
      assertEquals(shortDim / 3, drawn.getArcWidth(), 1e-9, "the s/3 arc");
   }

   // the middle of three slots across the 1000-unit chart width
   private static final double SLOT = 1000.0 / 3;
   private static final double CENTER = SLOT * 1.5;

   // a marked box takes both rules, a classic box neither
   private static BoxPainter box(boolean marked) {
      return box(marked, marked, 100, 250, 400, 550, 700);
   }

   // a box initialized in the middle slot from its five values
   private static BoxPainter box(boolean widthRule, boolean heightFloor, double low, double q25,
                                 double median, double q75, double high)
   {
      BoxPainter painter = new BoxPainter();
      SchemaElement elem = new SchemaElement(painter);
      elem.setBoxWidthRule(widthRule);
      elem.setBoxHeightFloor(heightFloor);
      double[] values = { high, q75, median, q25, low };
      SchemaGeometry geom = mock(SchemaGeometry.class);
      when(geom.getElement()).thenReturn(elem);
      when(geom.getTuple()).thenReturn(values);
      when(geom.getVarTuple()).thenReturn(values);
      when(geom.getVisualModel()).thenReturn(mock(VisualModel.class));
      RectCoord coord = mock(RectCoord.class);
      when(coord.getMaxWidth()).thenReturn(SLOT);
      when(coord.getPosition(any())).thenAnswer(inv -> {
         double[] tuple = inv.getArgument(0);
         return new Point2D.Double(CENTER, tuple[tuple.length - 1]);
      });

      painter.init(geom, coord);
      return painter;
   }

   private static SchemaElement element(BoxPainter painter) {
      return (SchemaElement) painter.geometry.getElement();
   }

   // the chart-to-screen transform for a slot this many pixels wide, y flipped
   private static AffineTransform screen(double slotPx) {
      return new AffineTransform(slotPx / SLOT, 0, 0, -0.2, 20, 220);
   }

   private static double slotCenter(AffineTransform screen) {
      return screen.transform(new Point2D.Double(CENTER, 0), null).getX();
   }

   // SchemaVO's screen painter
   private static BoxPainter fitted(BoxPainter painter, AffineTransform screen) {
      BoxPainter copy = (BoxPainter) painter.clone();
      copy.transformScreen(screen);
      copy.fitScreen(screen);
      return copy;
   }

   // SchemaVO's painter bounds, which dodge reads before layout
   private static Rectangle2D dodgeBounds(BoxPainter painter) {
      Rectangle2D box = null;

      for(int i = 0; i < painter.getShapeCount(); i++) {
         Rectangle2D b = painter.getShape(i).getBounds2D();
         b = new Rectangle2D.Double(b.getX() - 1, b.getY() - 1, b.getWidth() + 2,
                                    b.getHeight() + 2);
         box = box == null ? b : box.createUnion(b);
      }

      return box;
   }

   // the box shape handed to fill
   private static RoundRectangle2D paintedBox(BoxPainter painter) {
      Graphics2D g = mock(Graphics2D.class);
      when(g.create()).thenReturn(g);
      painter.paint(g);
      ArgumentCaptor<Shape> filled = ArgumentCaptor.forClass(Shape.class);
      verify(g, atLeastOnce()).fill(filled.capture());
      return (RoundRectangle2D) filled.getAllValues().stream()
         .filter(s -> s instanceof RoundRectangle2D.Double).findFirst().orElseThrow();
   }
}
