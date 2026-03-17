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
 * Tests for {@link PowerLineEquation}.
 *
 * <p>The power regression fits y = A * x^b by log-linearising:
 * ln(y) = ln(A) + b*ln(x).  Points where x=0 or y=0 are skipped in the
 * accumulation sums (the log would be undefined).  The generation loop
 * uses an increment of max(inc, 1) to avoid an excessive number of points.</p>
 */
class PowerLineEquationTest {

   private PowerLineEquation equation;

   @BeforeEach
   void setUp() {
      equation = new PowerLineEquation();
   }

   // ---- calculate() basic contract ----

   /**
    * A clean power-law dataset should produce a non-empty result.
    */
   @Test
   void calculate_returnsNonEmptyArrayForValidData() {
      // y = x^2
      Point2D[] pts = {
         new Point2D.Double(1,  1),
         new Point2D.Double(2,  4),
         new Point2D.Double(3,  9),
         new Point2D.Double(4, 16)
      };

      Point2D[] result = equation.calculate(pts);
      assertNotNull(result);
      assertTrue(result.length > 0);
   }

   /**
    * Output x-values must span from xmin to (near) xmax.
    */
   @Test
   void calculate_outputSpansInputXRange() {
      Point2D[] pts = {
         new Point2D.Double(1,  1),
         new Point2D.Double(2,  4),
         new Point2D.Double(3,  9),
         new Point2D.Double(10, 100)
      };

      Point2D[] result = equation.calculate(pts);
      assertEquals(1.0,  result[0].getX(), 1e-9, "First x should equal xmin");
      assertEquals(10.0, result[result.length - 1].getX(), 1.0,
                   "Last x should be near xmax");
   }

   /**
    * For y = x^2, at x=3 the expected y is 9.  The fitted curve should
    * recover this closely.
    */
   @Test
   void calculate_fitsSquareLawAtMidpoint() {
      Point2D[] pts = {
         new Point2D.Double(1,  1),
         new Point2D.Double(2,  4),
         new Point2D.Double(3,  9),
         new Point2D.Double(4, 16),
         new Point2D.Double(5, 25)
      };

      Point2D[] result = equation.calculate(pts);
      Point2D closest = findClosestX(result, 3.0);
      assertEquals(9.0, closest.getY(), 0.5, "Power fit should give y≈9 at x=3");
   }

   /**
    * For y = 2 * x^0.5 (square-root law), the fit should recover the
    * relationship at an interior point.
    */
   @Test
   void calculate_fitsSqrtLaw() {
      double a = 2.0;
      double b = 0.5;
      Point2D[] pts = {
         new Point2D.Double(1,  a * Math.pow(1,  b)),
         new Point2D.Double(4,  a * Math.pow(4,  b)),
         new Point2D.Double(9,  a * Math.pow(9,  b)),
         new Point2D.Double(16, a * Math.pow(16, b)),
         new Point2D.Double(25, a * Math.pow(25, b))
      };

      Point2D[] result = equation.calculate(pts);
      assertNotNull(result);
      assertTrue(result.length > 0);

      // At x=4, y = 2 * sqrt(4) = 4.0
      Point2D closest = findClosestX(result, 4.0);
      assertEquals(4.0, closest.getY(), 0.3, "Power fit y ≈ 4 at x=4");
   }

   /**
    * When all data points have x=0 (skipped in log accumulation) and a
    * range of y, xmin == xmax so the output should be empty.
    */
   @Test
   void calculate_singleUniqueXReturnsEmptyArray() {
      Point2D[] pts = {
         new Point2D.Double(5, 10),
         new Point2D.Double(5, 20),
         new Point2D.Double(5, 30)
      };

      Point2D[] result = equation.calculate(pts);
      assertEquals(0, result.length);
   }

   /**
    * A single data point has xmin == xmax; must return empty array.
    */
   @Test
   void calculate_singlePointReturnsEmptyArray() {
      Point2D[] result = equation.calculate(new Point2D.Double(5, 25));
      assertEquals(0, result.length);
   }

   /**
    * Points with x=0 or y=0 are skipped in the log accumulation.  As long
    * as there are other points that span a range, the calculation should not
    * throw and should return non-empty output.
    */
   @Test
   void calculate_xOrYZeroPointsAreSkippedGracefully() {
      Point2D[] pts = {
         new Point2D.Double(0, 0),   // skipped (x=0, y=0)
         new Point2D.Double(1, 1),
         new Point2D.Double(4, 16),
         new Point2D.Double(9, 81)
      };

      assertDoesNotThrow(() -> {
         Point2D[] result = equation.calculate(pts);
         assertTrue(result.length > 0, "Should produce output ignoring (0,0) point");
      });
   }

   /**
    * All y-values in the output must be positive for a positive power-law
    * with positive x-values.
    */
   @Test
   void calculate_allOutputYValuesArePositive() {
      Point2D[] pts = {
         new Point2D.Double(1,  1),
         new Point2D.Double(2,  4),
         new Point2D.Double(3,  9),
         new Point2D.Double(4, 16)
      };

      Point2D[] result = equation.calculate(pts);

      for(Point2D p : result) {
         assertTrue(p.getY() > 0, "All output y must be positive, got " + p.getY());
      }
   }

   /**
    * The generation loop uses inc = max((xmax-xmin)/100, 1), so for large
    * ranges the number of output points should not exceed ~xRange+1.
    */
   @Test
   void calculate_incIsAtLeastOneForLargeRange() {
      Point2D[] pts = {
         new Point2D.Double(1,    1),
         new Point2D.Double(100,  10000),
         new Point2D.Double(200,  40000),
         new Point2D.Double(1000, 1000000)
      };

      Point2D[] result = equation.calculate(pts);
      // range = 999, inc = max(999/100, 1) = 9.99, so ~101 steps
      assertTrue(result.length <= 200, "Should not produce excessive output points");
   }

   /**
    * setXmax() should not cause an exception when called before calculate().
    */
   @Test
   void setXmax_doesNotThrow() {
      assertDoesNotThrow(() -> equation.setXmax(100.0));
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
