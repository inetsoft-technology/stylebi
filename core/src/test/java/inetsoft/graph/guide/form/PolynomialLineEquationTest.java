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

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PolynomialLineEquation} and its concrete subclasses
 * {@link PolynomialLineEquation.Linear}, {@link PolynomialLineEquation.Quadratic},
 * and {@link PolynomialLineEquation.Cubic}.
 *
 * <p>Each subclass uses the degree-n polynomial OLS fit.  The {@code Linear}
 * (degree 1) subclass returns exactly two points (endpoints); {@code Quadratic}
 * (degree 2) and {@code Cubic} (degree 3) return ~101 points.</p>
 */
class PolynomialLineEquationTest {

   // ==========================================================================
   // Linear (degree 1)
   // ==========================================================================

   /** Linear: calculate with exact collinear data returns 2 endpoints. */
   @Test
   void linear_calculate_returnsExactlyTwoPointsForCollinearData() {
      PolynomialLineEquation.Linear eq = new PolynomialLineEquation.Linear();
      // y = 2x + 1
      Point2D[] pts = {
         new Point2D.Double(1, 3),
         new Point2D.Double(2, 5),
         new Point2D.Double(3, 7),
         new Point2D.Double(4, 9)
      };

      Point2D[] result = eq.calculate(pts);
      assertEquals(2, result.length, "Linear should produce exactly 2 points");
   }

   /** Linear: endpoints x-coordinates match xmin and xmax. */
   @Test
   void linear_calculate_endpointXMatchesInputRange() {
      PolynomialLineEquation.Linear eq = new PolynomialLineEquation.Linear();
      Point2D[] pts = {
         new Point2D.Double(0, 1),
         new Point2D.Double(5, 11)
      };

      Point2D[] result = eq.calculate(pts);
      assertEquals(0.0, result[0].getX(), 1e-9);
      assertEquals(5.0, result[1].getX(), 1e-9);
   }

   /** Linear: y = 3x - 1 should be recovered exactly from collinear points. */
   @Test
   void linear_calculate_fitsLinearEquationExactly() {
      PolynomialLineEquation.Linear eq = new PolynomialLineEquation.Linear();
      // y = 3x - 1
      Point2D[] pts = {
         new Point2D.Double(0, -1),
         new Point2D.Double(1,  2),
         new Point2D.Double(2,  5),
         new Point2D.Double(3,  8)
      };

      Point2D[] result = eq.calculate(pts);
      // result[0] should be at x=0 => y=-1; result[1] at x=3 => y=8
      assertEquals(-1.0, result[0].getY(), 1e-6, "y at x=0 should be -1");
      assertEquals(8.0,  result[1].getY(), 1e-6, "y at x=3 should be 8");
   }

   /** Linear: fewer points than needed (degree+1=2) returns empty array. */
   @Test
   void linear_calculate_insufficientPointsReturnsEmpty() {
      PolynomialLineEquation.Linear eq = new PolynomialLineEquation.Linear();
      Point2D[] result = eq.calculate(new Point2D.Double(1, 2));
      assertEquals(0, result.length);
   }

   /** Linear: all points with same x — fewer than degree+1 distinct x => empty. */
   @Test
   void linear_calculate_sameXValuesReturnsEmpty() {
      PolynomialLineEquation.Linear eq = new PolynomialLineEquation.Linear();
      Point2D[] pts = {
         new Point2D.Double(3, 5),
         new Point2D.Double(3, 10),
         new Point2D.Double(3, 15)
      };

      Point2D[] result = eq.calculate(pts);
      assertEquals(0, result.length, "All same x should produce empty result");
   }

   // ==========================================================================
   // Quadratic (degree 2)
   // ==========================================================================

   /** Quadratic: returns ~101 points for a valid dataset. */
   @Test
   void quadratic_calculate_returnsAbout101Points() {
      PolynomialLineEquation.Quadratic eq = new PolynomialLineEquation.Quadratic();
      // y = x^2
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 1),
         new Point2D.Double(2, 4),
         new Point2D.Double(3, 9),
         new Point2D.Double(4, 16)
      };

      Point2D[] result = eq.calculate(pts);
      assertTrue(result.length >= 100, "Quadratic should produce ~101 points");
   }

   /** Quadratic: fits y = x^2 accurately at x=2 (expected y=4). */
   @Test
   void quadratic_calculate_fitsParabolaAtMidpoint() {
      PolynomialLineEquation.Quadratic eq = new PolynomialLineEquation.Quadratic();
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 1),
         new Point2D.Double(2, 4),
         new Point2D.Double(3, 9),
         new Point2D.Double(4, 16)
      };

      Point2D[] result = eq.calculate(pts);
      Point2D closest = findClosestX(result, 2.0);
      assertEquals(4.0, closest.getY(), 0.05, "Quadratic fit should give y≈4 at x=2");
   }

   /** Quadratic: needs at least 3 distinct x-values (degree+1=3). */
   @Test
   void quadratic_calculate_twoDistinctXReturnsEmpty() {
      PolynomialLineEquation.Quadratic eq = new PolynomialLineEquation.Quadratic();
      Point2D[] pts = {
         new Point2D.Double(1, 1),
         new Point2D.Double(2, 4)
      };

      Point2D[] result = eq.calculate(pts);
      assertEquals(0, result.length);
   }

   /** Quadratic: fits y = -x^2 + 4 (downward parabola). */
   @Test
   void quadratic_calculate_fitsDownwardParabola() {
      PolynomialLineEquation.Quadratic eq = new PolynomialLineEquation.Quadratic();
      // y = -x^2 + 4
      Point2D[] pts = {
         new Point2D.Double(-2, 0),
         new Point2D.Double(-1, 3),
         new Point2D.Double(0,  4),
         new Point2D.Double(1,  3),
         new Point2D.Double(2,  0)
      };

      Point2D[] result = eq.calculate(pts);
      assertNotNull(result);
      assertTrue(result.length > 0);
      // At x=0 y should be ~4
      Point2D closest = findClosestX(result, 0.0);
      assertEquals(4.0, closest.getY(), 0.1, "Downward parabola y at x=0 should be ~4");
   }

   // ==========================================================================
   // Cubic (degree 3)
   // ==========================================================================

   /** Cubic: needs at least 4 distinct x-values (degree+1=4). */
   @Test
   void cubic_calculate_threeDistinctXReturnsEmpty() {
      PolynomialLineEquation.Cubic eq = new PolynomialLineEquation.Cubic();
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 1),
         new Point2D.Double(2, 8)
      };

      Point2D[] result = eq.calculate(pts);
      assertEquals(0, result.length);
   }

   /** Cubic: returns ~101 points for valid data. */
   @Test
   void cubic_calculate_returnsAbout101Points() {
      PolynomialLineEquation.Cubic eq = new PolynomialLineEquation.Cubic();
      // y = x^3
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 1),
         new Point2D.Double(2, 8),
         new Point2D.Double(3, 27),
         new Point2D.Double(4, 64)
      };

      Point2D[] result = eq.calculate(pts);
      assertTrue(result.length >= 100);
   }

   /** Cubic: fits y = x^3 accurately at x=2 (expected y=8). */
   @Test
   void cubic_calculate_fitsCubicCurveAtMidpoint() {
      PolynomialLineEquation.Cubic eq = new PolynomialLineEquation.Cubic();
      Point2D[] pts = {
         new Point2D.Double(0, 0),
         new Point2D.Double(1, 1),
         new Point2D.Double(2, 8),
         new Point2D.Double(3, 27),
         new Point2D.Double(4, 64)
      };

      Point2D[] result = eq.calculate(pts);
      Point2D closest = findClosestX(result, 2.0);
      assertEquals(8.0, closest.getY(), 0.1, "Cubic fit should give y≈8 at x=2");
   }

   /** Cubic: fits y = x^3 - x (roots at -1, 0, 1). */
   @Test
   void cubic_calculate_fitsCubicWithNegativeCoefficient() {
      PolynomialLineEquation.Cubic eq = new PolynomialLineEquation.Cubic();
      // y = x^3 - x
      Point2D[] pts = {
         new Point2D.Double(-2, -6),
         new Point2D.Double(-1, 0),
         new Point2D.Double(0,  0),
         new Point2D.Double(1,  0),
         new Point2D.Double(2,  6)
      };

      Point2D[] result = eq.calculate(pts);
      assertNotNull(result);
      assertTrue(result.length > 0);
      // At x=1 the expected y is 0
      Point2D closest = findClosestX(result, 1.0);
      assertEquals(0.0, closest.getY(), 0.1, "y at x=1 should be ~0");
   }

   // ==========================================================================
   // calculateY (protected utility via subclass behaviour)
   // ==========================================================================

   /**
    * The {@code calculateY} formula is exercised indirectly through Linear:
    * for coefficients [c0, c1] the value is c0 + c1*x.
    * Verify that the slope and intercept are correctly recovered.
    */
   @Test
   void linear_calculateY_slopeAndInterceptAreCorrect() {
      PolynomialLineEquation.Linear eq = new PolynomialLineEquation.Linear();
      // y = 5 + 2x  =>  at x=0 -> 5, at x=10 -> 25
      Point2D[] pts = {
         new Point2D.Double(0,  5),
         new Point2D.Double(5,  15),
         new Point2D.Double(10, 25)
      };

      Point2D[] result = eq.calculate(pts);
      // result[0] is (xmin, y(xmin)), result[1] is (xmax, y(xmax))
      assertEquals(5.0,  result[0].getY(), 1e-4, "y at x=0 should be 5");
      assertEquals(25.0, result[1].getY(), 1e-4, "y at x=10 should be 25");
   }

   // ==========================================================================
   // helper
   // ==========================================================================

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
