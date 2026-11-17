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
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.element.SchemaElement;
import inetsoft.graph.geometry.SchemaGeometry;
import inetsoft.graph.visual.ElementVO;
import inetsoft.graph.visual.SchemaVO;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * A marked candle body is at least 3 screen pixels wide and a marked stock tick at least 3
 * long, or their share of a slot too narrow for that, so dodged marks never overlap.
 */
// GTool, reached by the painters, initializes the report renderer on first use
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CandleStockModernTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aThinCandleBodyFloorsAt3() {
      AffineTransform screen = screen(20);
      CandlePainter painter = fitted(candle(true), screen);
      Rectangle2D body = painter.getShape(1).getBounds2D();

      assertTrue(bodyPx(candle(true), screen) < CandlePainter.MIN_BODY_PX,
                 "premise: the natural body is thinner");
      assertEquals(3, body.getWidth(), 1e-6, "width");
      assertEquals(slotCenter(screen), body.getCenterX(), 1e-6, "centred on the slot");
      assertEquals(body.getCenterX(), painter.getShape(0).getBounds2D().getCenterX(), 1e-6,
                   "centred on the wick");
   }

   @Test
   void aWideCandleBodyIsUntouched() {
      AffineTransform screen = screen(200);
      CandlePainter unfitted = unfitted(candle(true), screen);
      CandlePainter fit = fitted(candle(true), screen);

      assertTrue(bodyPx(candle(true), screen) > CandlePainter.MIN_BODY_PX,
                 "premise: the natural body is wider");

      for(int i = 0; i < fit.getShapeCount(); i++) {
         assertEquals(unfitted.getShape(i).getBounds2D(), fit.getShape(i).getBounds2D(),
                      "shape " + i);
      }
   }

   @Test
   void aCrowdedSlotFloorsCandlesAtTheirShare() {
      // a lone candle in a slot narrower than the floor
      AffineTransform alone = screen(1.4);
      CandlePainter single = fitted(candle(true), alone);
      Rectangle2D body = single.getShape(1).getBounds2D();

      assertEquals(1.4, body.getWidth(), 1e-6, "a lone candle's share is its slot");
      assertEquals(single.getShape(0).getBounds2D().getCenterX(), body.getCenterX(), 1e-6,
                   "centred on the wick");

      CandlePainter left = candle(true);
      CandlePainter right = candle(true);
      AffineTransform screen = screen(2);
      dodge(left, right);

      // the pair's natural extent on screen and the dodge gap between the bodies
      Rectangle2D a0 = unfitted(left, screen).getShape(1).getBounds2D();
      Rectangle2D b0 = unfitted(right, screen).getShape(1).getBounds2D();
      double extentPx = b0.getMaxX() - a0.getX();
      double gapPx = b0.getX() - a0.getMaxX();
      CandlePainter leftFit = fitted(left, screen);
      CandlePainter rightFit = fitted(right, screen);
      Rectangle2D a = leftFit.getShape(1).getBounds2D();
      Rectangle2D b = rightFit.getShape(1).getBounds2D();
      double inSlot = b.getX() - a.getMaxX();
      double expected = Math.min(CandlePainter.MIN_BODY_PX, a0.getWidth() * 2 / (extentPx + gapPx));

      assertTrue(gapPx > 0, "premise: dodge leaves a gap");
      assertTrue(a0.getWidth() < expected, "premise: the floor applies");
      assertEquals(expected, a.getWidth(), 1e-6, "the body's share of the slot");
      assertEquals(a.getWidth(), b.getWidth(), 1e-9, "equal bodies");
      assertTrue(inSlot >= 0, "no overlap: " + a + " " + b);
      assertTrue(b.getMaxX() - a.getX() <= 2 + 1e-6, "the pair fits its slot: " + a + " " + b);
      assertTrue(a.getX() - (slotCenter(screen) - 1) >= inSlot / 2 - 1e-6, "left slot edge");
      assertTrue(slotCenter(screen) + 1 - b.getMaxX() >= inSlot / 2 - 1e-6, "right slot edge");
      assertEquals(a.getCenterX(), leftFit.getShape(0).getBounds2D().getCenterX(), 1e-6,
                   "left wick");
      assertEquals(b.getCenterX(), rightFit.getShape(0).getBounds2D().getCenterX(), 1e-6,
                   "right wick");
   }

   @Test
   void theWickStays1px() {
      CandlePainter painter = fitted(candle(true), screen(20));
      Rectangle2D body = painter.getShape(1).getBounds2D();
      // a Graphics2D starts with a 1px stroke
      Stroke[] stroke = { new BasicStroke() };
      List<Stroke> wickStrokes = new ArrayList<>();
      List<Rectangle2D> wicks = new ArrayList<>();
      Graphics2D g = mock(Graphics2D.class);
      when(g.create()).thenReturn(g);
      when(g.getStroke()).thenAnswer(inv -> stroke[0]);
      doAnswer(inv -> {
         stroke[0] = inv.getArgument(0);
         return null;
      }).when(g).setStroke(any());
      doAnswer(inv -> {
         Rectangle2D drawn = ((Shape) inv.getArgument(0)).getBounds2D();

         if(drawn.getWidth() == 0) {
            wicks.add(drawn);
            wickStrokes.add(stroke[0]);
         }

         return null;
      }).when(g).draw(any());

      painter.paint(g);

      assertEquals(3, body.getWidth(), 1e-6, "premise: the body is floored");
      assertEquals(1, wicks.size(), "the wick is drawn as a line");
      assertEquals(1f, ((BasicStroke) wickStrokes.get(0)).getLineWidth(), 1e-9, "stroke");
      assertEquals(body.getCenterX(), wicks.get(0).getX(), 1e-6, "on the body's centre");
   }

   @Test
   void aShortStockTickFloorsAt3() {
      AffineTransform screen = screen(50);
      StockPainter painter = fitted(stock(true), screen);
      Rectangle2D line = painter.getShape(0).getBounds2D();
      Rectangle2D close = painter.getShape(1).getBounds2D();
      Rectangle2D open = painter.getShape(2).getBounds2D();

      assertTrue(tickPx(stock(true), screen) < StockPainter.MIN_TICK_PX,
                 "premise: the natural tick is shorter");
      assertEquals(3, close.getWidth(), 1e-6, "close tick");
      assertEquals(3, open.getWidth(), 1e-6, "open tick");
      assertEquals(slotCenter(screen), line.getCenterX(), 1e-6, "the line stays on the slot");
      assertEquals(line.getCenterX(), close.getX(), 1e-6, "close tick leaves the line");
      assertEquals(line.getCenterX(), open.getMaxX(), 1e-6, "open tick leaves the line");
   }

   @Test
   void aCrowdedSlotFloorsTicksAtHalfTheirShare() {
      AffineTransform alone = screen(4);
      StockPainter single = fitted(stock(true), alone);

      assertEquals(2, single.getShape(1).getBounds2D().getWidth(), 1e-6,
                   "a lone stock's tick is half its slot");
      assertEquals(2, single.getShape(2).getBounds2D().getWidth(), 1e-6,
                   "both ticks fit the slot");

      StockPainter left = stock(true);
      StockPainter right = stock(true);
      AffineTransform screen = screen(4);
      dodge(left, right);

      // the pair's natural extent on screen and the dodge gap between the stocks
      Rectangle2D a0 = extent(unfitted(left, screen));
      Rectangle2D b0 = extent(unfitted(right, screen));
      double extentPx = b0.getMaxX() - a0.getX();
      double gapPx = b0.getX() - a0.getMaxX();
      double tickPx = unfitted(left, screen).getShape(1).getBounds2D().getWidth();
      StockPainter leftFit = fitted(left, screen);
      StockPainter rightFit = fitted(right, screen);
      Rectangle2D a = extent(leftFit);
      Rectangle2D b = extent(rightFit);
      double inSlot = b.getX() - a.getMaxX();
      double expected = Math.min(StockPainter.MIN_TICK_PX, tickPx * 4 / (extentPx + gapPx));

      assertTrue(gapPx > 0, "premise: dodge leaves a gap");
      assertTrue(tickPx < expected, "premise: the floor applies");

      for(StockPainter painter : List.of(leftFit, rightFit)) {
         assertEquals(expected, painter.getShape(1).getBounds2D().getWidth(), 1e-6,
                      "close tick, the tick's share of the slot");
         assertEquals(expected, painter.getShape(2).getBounds2D().getWidth(), 1e-6,
                      "open tick, the tick's share of the slot");
      }

      assertTrue(inSlot >= 0, "no overlap: " + a + " " + b);
      assertTrue(b.getMaxX() - a.getX() <= 4 + 1e-6, "the pair fits its slot: " + a + " " + b);
      assertTrue(a.getX() - (slotCenter(screen) - 2) >= inSlot / 2 - 1e-6, "left slot edge");
      assertTrue(slotCenter(screen) + 2 - b.getMaxX() >= inSlot / 2 - 1e-6, "right slot edge");
   }

   @Test
   void classicCandleAndStockUnchanged() {
      AffineTransform thin = screen(20);
      AffineTransform shortTick = screen(50);

      assertTrue(bodyPx(candle(false), thin) < CandlePainter.MIN_BODY_PX,
                 "premise: a floor would apply");
      assertTrue(tickPx(stock(false), shortTick) < StockPainter.MIN_TICK_PX,
                 "premise: a floor would apply");
      assertUnfitted(candle(false), thin);
      assertUnfitted(stock(false), shortTick);
   }

   // the middle of three slots across the 1000-unit chart width
   private static final double SLOT = 1000.0 / 3;
   private static final double CENTER = SLOT * 1.5;

   // a rising candle in the middle slot: high, close, open, low
   private static CandlePainter candle(boolean floor) {
      return init(new CandlePainter(), floor, 700, 400, 250, 100);
   }

   // a rising stock with an open tick in the middle slot: high, close, open, low
   private static StockPainter stock(boolean floor) {
      return init(new StockPainter(), floor, 700, 400, 250, 100);
   }

   // a painter initialized in the middle slot from its values
   private static <T extends SchemaPainter> T init(T painter, boolean floor, double... values) {
      SchemaElement elem = new SchemaElement(painter);
      elem.setWidthFloor(floor);
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

   // the chart-to-screen transform for a slot this many pixels wide, y flipped
   private static AffineTransform screen(double slotPx) {
      return new AffineTransform(slotPx / SLOT, 0, 0, -0.2, 20, 220);
   }

   private static double slotCenter(AffineTransform screen) {
      return screen.transform(new Point2D.Double(CENTER, 0), null).getX();
   }

   private static double bodyPx(CandlePainter painter, AffineTransform screen) {
      return unfitted(painter, screen).getShape(1).getBounds2D().getWidth();
   }

   private static double tickPx(StockPainter painter, AffineTransform screen) {
      return unfitted(painter, screen).getShape(1).getBounds2D().getWidth();
   }

   // SchemaVO's screen painter
   @SuppressWarnings("unchecked")
   private static <T extends SchemaPainter> T fitted(T painter, AffineTransform screen) {
      T copy = (T) painter.clone();
      copy.transformScreen(screen);
      copy.fitScreen(screen);
      return copy;
   }

   // the painter on screen before any pixel rule
   @SuppressWarnings("unchecked")
   private static <T extends SchemaPainter> T unfitted(T painter, AffineTransform screen) {
      T copy = (T) painter.clone();
      copy.transformScreen(screen);
      return copy;
   }

   private static void assertUnfitted(SchemaPainter painter, AffineTransform screen) {
      SchemaPainter unfitted = unfitted(painter, screen);
      SchemaPainter fit = fitted(painter, screen);

      for(int i = 0; i < painter.getShapeCount(); i++) {
         assertEquals(unfitted.getShape(i).getBounds2D(), fit.getShape(i).getBounds2D(),
                      painter.getClass().getSimpleName() + " shape " + i);
      }
   }

   // the union of a painter's shapes
   private static Rectangle2D extent(SchemaPainter painter) {
      Rectangle2D box = null;

      for(int i = 0; i < painter.getShapeCount(); i++) {
         Rectangle2D b = painter.getShape(i).getBounds2D();
         box = box == null ? b : box.createUnion(b);
      }

      return box;
   }

   // SchemaVO.dodge on two marks in one slot, in chart units before layout
   private static void dodge(SchemaPainter left, SchemaPainter right) {
      Coordinate coord = mock(Coordinate.class);
      SchemaVO first = new SchemaVO(left.geometry, coord, left);
      SchemaVO second = new SchemaVO(right.geometry, coord, right);

      first.dodge(Map.<ElementVO, List<ElementVO>>of(first, List.of(second)), coord);
   }
}
