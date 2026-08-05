/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.graph.internal.Donut;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Shape;
import java.awt.geom.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BarVO.getAnchorFraction, the data-anchor value the client applies to a mark's
 * rendered rect to place the tooltip tail.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BarVOAnchorFractionTest {
   /** A wedge sweeping 200 degrees, the shape whose box centre falls in the donut hole. */
   private static Donut wideWedge() {
      return new Donut(0, 0, 200, 200, 100, 100, 0, 200);
   }

   private static double[] parse(String fraction) {
      String[] parts = fraction.split(",");
      return new double[] { Double.parseDouble(parts[0]), Double.parseDouble(parts[1]) };
   }

   /** Apply the fraction to the shape's device box, the way the client applies it to the rect. */
   private static Point2D resolve(String fraction, Shape path, AffineTransform at) {
      double[] f = parse(fraction);
      Rectangle2D box = at.createTransformedShape(path).getBounds2D();

      return new Point2D.Double(box.getX() + f[0] * box.getWidth(),
                                box.getY() + f[1] * box.getHeight());
   }

   @Test
   void plainBarGetsNoAnchor() {
      // A rectangle's box centre is already on the mark, so nothing is written.
      assertNull(BarVO.getAnchorFraction(new Rectangle2D.Double(10, 20, 30, 40),
                                         new AffineTransform()));
   }

   @Test
   void degenerateShapeGetsNoAnchor() {
      assertNull(BarVO.getAnchorFraction(new Donut(0, 0, 0, 0, 0, 0, 0, 90),
                                         new AffineTransform()));
   }

   @Test
   void fractionResolvesToTheWedgesPolarMidPoint() {
      Donut wedge = wideWedge();
      AffineTransform at = new AffineTransform();
      Point2D expected = wedge.getCentroid();
      Point2D actual = resolve(BarVO.getAnchorFraction(wedge, at), wedge, at);

      assertEquals(expected.getX(), actual.getX(), 0.05);
      assertEquals(expected.getY(), actual.getY(), 0.05);
   }

   @Test
   void fractionSurvivesAScaledAndFlippedTransform() {
      // The y-flip is the case that cannot be settled by reading the SVG generator: taking both
      // the point and the box in device space is what makes the fraction match the rendered rect.
      Donut wedge = wideWedge();
      AffineTransform at = new AffineTransform(2, 0, 0, -3, 17, 400);
      Point2D expected = at.transform(wedge.getCentroid(), null);
      Point2D actual = resolve(BarVO.getAnchorFraction(wedge, at), wedge, at);

      assertEquals(expected.getX(), actual.getX(), 0.05);
      assertEquals(expected.getY(), actual.getY(), 0.05);
   }

   @Test
   void wideWedgeAnchorIsNotTheBoxCentre() {
      // The regression this exists for: a box centre would land in the hole, off the wedge.
      double[] f = parse(BarVO.getAnchorFraction(wideWedge(), new AffineTransform()));

      assertTrue(f[0] >= 0 && f[0] <= 1, "fx within the box: " + f[0]);
      assertTrue(f[1] >= 0 && f[1] <= 1, "fy within the box: " + f[1]);
      assertTrue(Math.hypot(f[0] - 0.5, f[1] - 0.5) > 0.1,
                 "anchor should be off the box centre, got " + f[0] + "," + f[1]);
   }
}
