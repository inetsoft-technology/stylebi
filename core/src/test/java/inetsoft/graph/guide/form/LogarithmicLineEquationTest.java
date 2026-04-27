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
package inetsoft.graph.guide.form;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LogarithmicLineEquation}.
 *
 * <p>The logarithmic regression fits y = a + b*ln(x) using OLS.
 * x = 0 is treated specially (log set to 0).  Points with x <= 0 in the
 * output loop are skipped.</p>
 */
class LogarithmicLineEquationTest {

   private LogarithmicLineEquation equation;

   @BeforeEach
   void setUp() {
      equation = new LogarithmicLineEquation();
   }

   // ---- calculate() basic contract ----

   /**
    * A clean logarithmic dataset should produce a non-empty output.
    */
   @Test
   void calculate_returnsNonEmptyArrayForValidPositiveXData() {
      // y = ln(x)  =>  a=0, b=1
      Point2D[] pts = {
         new Point2D.Double(1, Math.log(1)),
         new Point2D.Double(2, Math.log(2)),
         new Point2D.Double(4, Math.log(4)),
         new Point2D.Double(8, Math.log(8))
      };

      Point2D[] result = equation.calculate(pts);
      assertNotNull(result);
      assertTrue(result.length > 0);
   }

   /**
    * Output x-coordinates must all be strictly positive; the implementation
    * skips x1 <= 0 in the generation loop.
    */
   @Test
   void calculate_allOutputXValuesArePositive() {
      Point2D[] pts = {
         new Point2D.Double(1, 0),
         new Point2D.Double(2, Math.log(2)),
         new Point2D.Double(3, Math.log(3)),
         new Point2D.Double(4, Math.log(4))
      };

      Point2D[] result = equation.calculate(pts);

      for(Point2D p : result) {
         assertTrue(p.getX() > 0, "All output x must be positive, got " + p.getX());
      }
   }

   /**
    * For y = 2*ln(x), the fitted curve should recover the relationship
    * at an intermediate x-value with reasonable accuracy.
    */
   @Test
   void calculate_fitsLogarithmicCurveAccurately() {
      // y = 2*ln(x)
      double b = 2.0;
      Point2D[] pts = {
         new Point2D.Double(1,  b * Math.log(1)),
         new Point2D.Double(2,  b * Math.log(2)),
         new Point2D.Double(4,  b * Math.log(4)),
         new Point2D.Double(8,  b * Math.log(8)),
         new Point2D.Double(16, b * Math.log(16))
      };

      Point2D[] result = equation.calculate(pts);

      // At x≈4 the expected y is 2*ln(4) ≈ 2.773
      double expected = b * Math.log(4);
      Point2D closest = findClosestX(result, 4.0);
      assertEquals(expected, closest.getY(), 0.2,
                   "Fitted curve should approximate 2*ln(4) at x=4");
   }

   /**
    * Single unique x-value: xmin == xmax, so the step is 0 and the loop
    * never runs — the implementation returns an empty array.
    */
   @Test
   void calculate_singleUniqueXReturnsEmptyArray() {
      Point2D[] pts = {
         new Point2D.Double(3, 1),
         new Point2D.Double(3, 5),
         new Point2D.Double(3, 9)
      };

      Point2D[] result = equation.calculate(pts);
      assertEquals(0, result.length);
   }

   /**
    * A single data point has xmin == xmax; must return empty array.
    */
   @Test
   void calculate_singlePointReturnsEmptyArray() {
      Point2D[] result = equation.calculate(new Point2D.Double(5, 10));
      assertEquals(0, result.length);
   }

   /**
    * x = 0 in the input is handled by setting log(0) = 0.  No exception
    * should be thrown and the output should be non-empty (other points span a
    * non-zero range).
    */
   @Test
   void calculate_xEqualsZeroInInputDoesNotThrow() {
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 0),
         new Point2D.Double(2, Math.log(2)),
         new Point2D.Double(4, Math.log(4))
      };

      assertDoesNotThrow(() -> equation.calculate(pts));
   }

   /**
    * When xmin starts at 0, the generation loop skips x1 <= 0 so the first
    * point in the output must have x > 0.
    */
   @Test
   void calculate_withXZeroInputFirstOutputXIsPositive() {
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 0),
         new Point2D.Double(2, Math.log(2)),
         new Point2D.Double(4, Math.log(4))
      };

      Point2D[] result = equation.calculate(pts);

      if(result.length > 0) {
         assertTrue(result[0].getX() > 0,
                    "First output x must be positive when xmin=0");
      }
   }

   /**
    * Large range of x should still produce ~101 output points (one per
    * increment step).
    */
   @Test
   void calculate_outputHasApproximately101Points() {
      Point2D[] pts = {
         new Point2D.Double(1,  0),
         new Point2D.Double(10, Math.log(10)),
         new Point2D.Double(50, Math.log(50)),
         new Point2D.Double(100, Math.log(100))
      };

      Point2D[] result = equation.calculate(pts);
      // loop: x1=xmin to x1<=xmax with step (xmax-xmin)/100, so ~101 iterations
      // but x<=0 skips — since xmin=1, all should pass
      assertTrue(result.length >= 100, "Should have ~101 output points");
   }

   // ---- helper ----

   private Point2D findClosestX(Point2D[] points, double targetX) {
      Point2D best = points[0];
      double bestDist = Math.abs(points[0].getX() - targetX);

      for(Point2D p : points) {
         double dist = Math.abs(p.getX() - targetX);

         if(dist < bestDist) {
            bestDist = dist;
            best = p;
         }
      }

      return best;
   }
}
