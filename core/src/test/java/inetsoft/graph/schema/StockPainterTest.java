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

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;

import static inetsoft.graph.schema.SchemaTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Stock (high-low-close) painter geometry: a wick from high to low, a close tick to the right and,
 * when an open value is bound, an open tick to the left; each tick is 10 logical units long.
 * Values are given in the order high, close, open, low.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class StockPainterTest {
   private static Fixture stock(double... vars) {
      return fixture(new StockPainter(), vars);
   }

   @Test
   @DisplayName("high, low, close and open give a wick and two ticks")
   void highLowCloseOpen() {
      Fixture f = stock(80, 60, 40, 20);

      assertEquals(3, f.painter.getShapeCount());
      assertLine(f.painter.getShape(0), 50, 800, 50, 200);
      assertLine(f.painter.getShape(1), 50, 600, 60, 600);
      assertLine(f.painter.getShape(2), 50, 400, 40, 400);
   }

   @Test
   @DisplayName("without open the low is the third value and there are two shapes")
   void withoutOpen() {
      Fixture f = stock(80, 60, 20);

      assertEquals(2, f.painter.getShapeCount());
      assertLine(f.painter.getShape(0), 50, 800, 50, 200);
      assertLine(f.painter.getShape(1), 50, 600, 60, 600);
   }

   @Test
   @DisplayName("reversed scale flips every y")
   void reversedScale() {
      Fixture f = fixture(new StockPainter(), true, Mapping.REVERSED, 100, 80, 60, 40, 20);

      assertLine(f.painter.getShape(0), 50, 200, 50, 800);
      assertLine(f.painter.getShape(1), 50, 400, 60, 400);
      assertLine(f.painter.getShape(2), 50, 600, 40, 600);
   }

   @Test
   @DisplayName("all values equal give a zero-length wick and ticks at the same y, no NaN")
   void allEqual() {
      Fixture f = stock(50, 50, 50, 50);

      assertLine(f.painter.getShape(0), 50, 500, 50, 500);
      assertLine(f.painter.getShape(1), 50, 500, 60, 500);
      assertLine(f.painter.getShape(2), 50, 500, 40, 500);
   }

   @Test
   @DisplayName("close equal to open gives collinear ticks on either side of the wick")
   void closeEqualsOpen() {
      Fixture f = stock(80, 50, 50, 20);

      assertLine(f.painter.getShape(2), 50, 500, 40, 500);
      assertLine(f.painter.getShape(1), 50, 500, 60, 500);
   }

   @Test
   @DisplayName("without an x scale the x position stays 0")
   void withoutXScale() {
      Fixture f = fixture(new StockPainter(), false, Mapping.SCALED, 100, 80, 60, 40, 20);

      assertLine(f.painter.getShape(1), 0, 600, 10, 600);
      assertLine(f.painter.getShape(2), 0, 400, -10, 400);
   }

   @Test
   @DisplayName("translate moves every shape by exactly the offset and keeps the count")
   void transformScreen() {
      Fixture f = stock(80, 60, 40, 20);
      Rectangle2D[] before = new Rectangle2D[3];

      for(int i = 0; i < 3; i++) {
         before[i] = f.painter.getShape(i).getBounds2D();
      }

      f.painter.transformScreen(AffineTransform.getTranslateInstance(5, 7));

      assertEquals(3, f.painter.getShapeCount());

      for(int i = 0; i < 3; i++) {
         Rectangle2D b = before[i];
         assertBounds(f.painter.getShape(i), b.getX() + 5, b.getY() + 7, b.getWidth(),
                      b.getHeight());
      }
   }

   @Test
   @DisplayName("shape and texture frames are not supported; color and size are")
   void supportsFrame() {
      StockPainter painter = new StockPainter();

      assertFalse(painter.supportsFrame(mock(ShapeFrame.class)));
      assertFalse(painter.supportsFrame(mock(TextureFrame.class)));
      assertTrue(painter.supportsFrame(mock(ColorFrame.class)));
      assertTrue(painter.supportsFrame(mock(SizeFrame.class)));
   }

   @Test
   @DisplayName("clone has its own shapes")
   void cloneIsIndependent() {
      Fixture f = stock(80, 60, 40, 20);
      StockPainter copy = (StockPainter) f.painter.clone();
      Shape original = f.painter.getShape(1);
      copy.setShape(1, new Line2D.Double(0, 0, 1, 1));

      assertSame(original, f.painter.getShape(1));
   }
}
