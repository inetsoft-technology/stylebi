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

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.schema.SchemaTestSupport.Fixture;
import inetsoft.graph.schema.SchemaTestSupport.Mapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;

import static inetsoft.graph.schema.SchemaTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Candle painter geometry: the wick joins high and low, the body spans open and close, and the
 * fill rule (hollow when close is above open) comes from the values rather than the positions.
 * Values are given in the order high, close, open, low.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CandlePainterTest {
   private static Fixture candle(double... vars) {
      return fixture(new CandlePainter(), vars);
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource({ "up candle (close above open), 80, 60, 40, 20",
                "down candle (close below open), 80, 40, 60, 20" })
   @DisplayName("wick joins high and low; body spans open and close, centred on the slot")
   void wickAndBody(String name, double high, double close, double open, double low) {
      Fixture f = candle(high, close, open, low);

      assertEquals(2, f.painter.getShapeCount());
      assertLine(f.painter.getShape(0), 50, 800, 50, 200);
      assertBounds(f.painter.getShape(1), 35, 400, 30, 200);
   }

   @Test
   @DisplayName("up and down candles over the same span have the same shapes")
   void sameBodyForUpAndDown() {
      Fixture up = candle(80, 60, 40, 20);
      Fixture down = candle(80, 40, 60, 20);

      assertEquals(up.painter.getShape(1).getBounds2D(), down.painter.getShape(1).getBounds2D());
      assertEquals(((Line2D) up.painter.getShape(0)).getP1(),
                   ((Line2D) down.painter.getShape(0)).getP1());
      assertEquals(((Line2D) up.painter.getShape(0)).getP2(),
                   ((Line2D) down.painter.getShape(0)).getP2());
   }

   @Test
   @DisplayName("reversed scale: wick flips, body unchanged")
   void reversedScale() {
      Fixture f = fixture(new CandlePainter(), true, Mapping.REVERSED, 100, 80, 60, 40, 20);

      assertLine(f.painter.getShape(0), 50, 200, 50, 800);
      assertBounds(f.painter.getShape(1), 35, 400, 30, 200);
   }

   @Test
   @DisplayName("open equal to close gives a zero-height body")
   void openEqualsClose() {
      Fixture f = candle(80, 50, 50, 20);

      assertBounds(f.painter.getShape(1), 35, 500, 30, 0);
      assertLine(f.painter.getShape(0), 50, 800, 50, 200);
   }

   @Test
   @DisplayName("all four values equal give a point wick and a zero-height body, no NaN")
   void allEqual() {
      Fixture f = candle(50, 50, 50, 50);

      assertLine(f.painter.getShape(0), 50, 500, 50, 500);
      assertBounds(f.painter.getShape(1), 35, 500, 30, 0);
   }

   @Test
   @DisplayName("high below open is not clamped")
   void highBelowOpen() {
      Fixture f = candle(30, 60, 40, 20);

      assertLine(f.painter.getShape(0), 50, 300, 50, 200);
      assertBounds(f.painter.getShape(1), 35, 400, 30, 200);
   }

   @Test
   @DisplayName("negative values")
   void negativeValues() {
      Fixture f = candle(-20, -40, -60, -80);

      assertLine(f.painter.getShape(0), 50, -200, 50, -800);
      assertBounds(f.painter.getShape(1), 35, -600, 30, 200);
   }

   @Test
   @DisplayName("slot narrower than the preferred width limits the body width")
   void narrowSlot() {
      Fixture f = fixture(new CandlePainter(), true, Mapping.SCALED, 20, 80, 60, 40, 20);

      assertBounds(f.painter.getShape(1), 40, 400, 20, 200);
   }

   @Test
   @DisplayName("without an x scale the x position stays 0")
   void withoutXScale() {
      Fixture f = fixture(new CandlePainter(), false, Mapping.SCALED, 100, 80, 60, 40, 20);

      assertLine(f.painter.getShape(0), 0, 800, 0, 200);
      assertBounds(f.painter.getShape(1), -15, 400, 30, 200);
   }

   @Test
   @DisplayName("NaN open does not fail and leaves the wick alone")
   void nanOpen() {
      Fixture f = candle(80, 60, Double.NaN, 20);

      assertLine(f.painter.getShape(0), 50, 800, 50, 200);
   }

   // -------------------------------------------------------------------------------- paint

   private static final Color BACKGROUND = new Color(200, 200, 200);

   private static BufferedImage paint(Fixture f) {
      BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = img.createGraphics();
      g.setColor(BACKGROUND);
      g.fillRect(0, 0, 100, 100);
      f.painter.paint(g);
      g.dispose();
      return img;
   }

   @Test
   @DisplayName("up candle (close above open) is painted hollow")
   void paintUpCandleHollow() {
      Fixture f = fixture(new CandlePainter(), true, Mapping.PIXEL, 100, 80, 60, 40, 20);

      assertEquals(Color.WHITE.getRGB(), paint(f).getRGB(50, 50));
   }

   @Test
   @DisplayName("down candle (close below open) is filled with the candle color")
   void paintDownCandleFilled() {
      Fixture f = fixture(new CandlePainter(), true, Mapping.PIXEL, 100, 80, 40, 60, 20);

      assertEquals(Color.GREEN.getRGB(), paint(f).getRGB(50, 50));
   }

   @Test
   @DisplayName("a painter reused for an up candle after a down candle is hollow again")
   void reusedPainterResetsFill() {
      CandlePainter painter = new CandlePainter();
      fixture(painter, true, Mapping.PIXEL, 100, 80, 40, 60, 20);
      Fixture f = new Fixture();
      f.painter = painter;
      f.element = element(painter);
      f.geometry = geometry(f.element, tuple(80, 60, 40, 20), "v0", "v1", "v2", "v3");
      f.coord = coord(true, Mapping.PIXEL, 100);
      painter.init(f.geometry, f.coord);

      assertEquals(Color.WHITE.getRGB(), paint(f).getRGB(50, 50));
   }

   // ------------------------------------------------------------------------------- others

   @Test
   @DisplayName("every frame is supported except a shape frame")
   void supportsFrame() {
      CandlePainter painter = new CandlePainter();

      assertFalse(painter.supportsFrame(mock(ShapeFrame.class)));
      assertTrue(painter.supportsFrame(mock(TextureFrame.class)));
      assertTrue(painter.supportsFrame(mock(ColorFrame.class)));
      assertTrue(painter.supportsFrame(mock(SizeFrame.class)));
      assertTrue(painter.supportsFrame(mock(LineFrame.class)));
   }

   @Test
   @DisplayName("clone has its own shapes; cloning an uninitialised painter works")
   void cloneIsIndependent() {
      assertNotNull(new CandlePainter().clone());

      Fixture f = candle(80, 60, 40, 20);
      CandlePainter copy = (CandlePainter) f.painter.clone();
      Shape original = f.painter.getShape(1);
      copy.setShape(1, new Line2D.Double(0, 0, 1, 1));

      assertSame(original, f.painter.getShape(1));
      assertNotSame(original, copy.getShape(1));
   }

   @Test
   @DisplayName("screen transform: translate moves, scale scales the raw shapes")
   void transformScreen() {
      Fixture moved = candle(80, 60, 40, 20);
      moved.painter.transformScreen(AffineTransform.getTranslateInstance(5, 7));

      assertLine(moved.painter.getShape(0), 55, 807, 55, 207);
      assertBounds(moved.painter.getShape(1), 40, 407, 30, 200);

      Fixture scaled = candle(80, 60, 40, 20);
      scaled.painter.transformScreen(AffineTransform.getScaleInstance(2, 3));

      assertLine(scaled.painter.getShape(0), 100, 2400, 100, 600);
      assertBounds(scaled.painter.getShape(1), 70, 1200, 60, 600);
   }
}
