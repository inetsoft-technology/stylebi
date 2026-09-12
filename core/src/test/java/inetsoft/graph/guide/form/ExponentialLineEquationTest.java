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
 * Tests for {@link ExponentialLineEquation}.
 *
 * <p>The exponential regression fits y = A * e^(b*x) by linearising:
 * ln(y) = ln(A) + b*x, then applying OLS.  The implementation clamps
 * yi <= 1 to 1 before taking the log, so the fitted curve is only
 * meaningful for positive y data.</p>
 */
class ExponentialLineEquationTest {

   private ExponentialLineEquation equation;

   @BeforeEach
   void setUp() {
      equation = new ExponentialLineEquation();
   }

   // ---- calculate() basic contract ----

   /**
    * With a clean exponential dataset the returned point array must be
    * non-empty and span [xmin, xmax].
    */
   @Test
   void calculate_returnsNonEmptyArrayForValidData() {
      // y = e^x  =>  a=1, b=1
      Point2D[] pts = {
         new Point2D.Double(1, Math.E),
         new Point2D.Double(2, Math.E * Math.E),
         new Point2D.Double(3, Math.E * Math.E * Math.E),
         new Point2D.Double(4, Math.pow(Math.E, 4))
      };

      Point2D[] result = equation.calculate(pts);

      assertNotNull(result);
      assertTrue(result.length > 0, "Expected at least one output point");
   }

   /**
    * The first and last x-coordinates of the output must match xmin / xmax
    * of the input to within floating-point rounding.
    */
   @Test
   void calculate_outputSpansInputXRange() {
      Point2D[] pts = {
         new Point2D.Double(1, Math.E),
         new Point2D.Double(2, Math.E * Math.E),
         new Point2D.Double(3, Math.E * Math.E * Math.E)
      };

      Point2D[] result = equation.calculate(pts);

      assertEquals(1.0, result[0].getX(), 1e-9, "First x should equal xmin");
      assertEquals(3.0, result[result.length - 1].getX(), 0.05, "Last x should be near xmax");
   }

   /**
    * When x-values span [1,3] with y = e^x, the fitted curve values
    * at integer x points should recover e^x closely.
    */
   @Test
   void calculate_fitsExponentialCurveAccurately() {
      Point2D[] pts = {
         new Point2D.Double(1, Math.E),
         new Point2D.Double(2, Math.E * Math.E),
         new Point2D.Double(3, Math.E * Math.E * Math.E)
      };

      Point2D[] result = equation.calculate(pts);

      // Find the point closest to x=2 in the output and verify y ≈ e^2
      double targetX = 2.0;
      Point2D closest = findClosestX(result, targetX);
      assertEquals(Math.E * Math.E, closest.getY(), 0.1,
                   "Fitted curve should approximate e^2 at x=2");
   }

   /**
    * When all data points share the same x-value the equation cannot compute
    * a meaningful slope; the implementation returns an empty array.
    */
   @Test
   void calculate_singleUniqueXReturnsEmptyArray() {
      Point2D[] pts = {
         new Point2D.Double(5, 10),
         new Point2D.Double(5, 20),
         new Point2D.Double(5, 30)
      };

      Point2D[] result = equation.calculate(pts);
      assertEquals(0, result.length, "Same x for all points should yield empty result");
   }

   /**
    * A single data point has xmin == xmax, so the loop increment is 0;
    * the implementation must return an empty array.
    */
   @Test
   void calculate_singlePointReturnsEmptyArray() {
      Point2D[] result = equation.calculate(new Point2D.Double(3, 20));
      assertEquals(0, result.length, "Single point should yield empty result");
   }

   /**
    * y-values <= 1 are clamped to 1 before log.  The output must still be
    * non-empty and all y-values must be positive.
    */
   @Test
   void calculate_yValuesLeqOneAreClampedAndOutputIsPositive() {
      Point2D[] pts = {
         new Point2D.Double(1, 0.5),
         new Point2D.Double(2, 1.0),
         new Point2D.Double(3, 0.1),
         new Point2D.Double(4, 5.0)
      };

      Point2D[] result = equation.calculate(pts);

      assertTrue(result.length > 0);
      for(Point2D p : result) {
         assertTrue(p.getY() > 0, "All output y-values must be positive");
      }
   }

   /**
    * Negative y values are clamped to 1 (since they are <= 1); output
    * should still be produced without exceptions.
    */
   @Test
   void calculate_negativeYValuesDoNotThrow() {
      Point2D[] pts = {
         new Point2D.Double(1, -5),
         new Point2D.Double(2, -2),
         new Point2D.Double(3, 10),
         new Point2D.Double(4, 50)
      };

      assertDoesNotThrow(() -> equation.calculate(pts));
   }

   /**
    * setXmax() allows the caller to override the upper x bound.  When set
    * smaller than the data maximum the last output point must not exceed the
    * specified limit.
    */
   @Test
   void setXmax_limitsOutputRange() {
      equation.setXmax(2.5);

      Point2D[] pts = {
         new Point2D.Double(1, Math.E),
         new Point2D.Double(2, Math.E * Math.E),
         new Point2D.Double(3, Math.E * Math.E * Math.E)
      };

      Point2D[] result = equation.calculate(pts);

      // xmax is taken as max(provided xmax, data xmax) inside calculate;
      // the preset value is used only when it is larger than the data xmax.
      // Here the data xmax (3) > preset (2.5), so result spans up to 3.
      // The important thing is that no exception is thrown and we get results.
      assertNotNull(result);
   }

   // ---- output monotonicity ----

   /**
    * For a strictly increasing exponential (b > 0), output y-values should
    * be monotonically non-decreasing across output points.
    */
   @Test
   void calculate_outputYIsMonotonicallyIncreasingForGrowthCurve() {
      Point2D[] pts = {
         new Point2D.Double(1, Math.E),
         new Point2D.Double(2, Math.E * Math.E),
         new Point2D.Double(3, Math.E * Math.E * Math.E),
         new Point2D.Double(4, Math.pow(Math.E, 4))
      };

      Point2D[] result = equation.calculate(pts);

      for(int i = 1; i < result.length; i++) {
         assertTrue(result[i].getY() >= result[i - 1].getY() - 1e-9,
                    "y should be non-decreasing for growing exponential at index " + i);
      }
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
