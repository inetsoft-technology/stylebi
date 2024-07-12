/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.TernClass;
import org.apache.commons.math3.linear.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Renders a polynomial trendline.
 *
 * @hidden
 * @version 10.0.
 * @author InetSoft Technology Corp.
 */
public abstract class PolynomialLineEquation extends AbstractLineEquation {
   /**
    * Create a new instance of PolynomialTrendline.
    * @param degree the degree of the polynomial used to fit the series.
    */
   protected PolynomialLineEquation(int degree) {
      super();
      this.degree = degree;
   }

   /**
    * Calculate the points on the line.
    * @param points the points to fit the line.
    * @return the points on the equation.
    */
   @Override
   public Point2D[] calculate(Point2D... points) {
      int size = points.length;
      int params = degree + 1;
      xmin = Double.POSITIVE_INFINITY;

      if(size < params) {
         LOG.debug("A polynomial line of degree n needs at least n+1 points: {} < {}", size, params);
         return new Point2D[0];
      }

      // check points have same x, but after filter points.length < params
      Set<Double> set = Arrays.stream(points)
         .map(Point2D::getX)
         .collect(Collectors.toSet());

      if(set.size() < params) {
         LOG.debug("A polynomial line of degree n needs at least n+1 points: {} < {}",
                   set.size(), params);
         return new Point2D[0];
      }

      /*
         given X, Y, n and d, solve AX = Y for A where:
         n is the number of data points
         d is the degree of the polynomial

         X is the matrix of x values:
         [ x0^0 x0^1 ... x0^d ]
         [ x1^0 x1^1 ... x1^d ]
         [ ... ]
         [ xn-1^0 xn-1^1 ... xn-1^d]

         Y is the matrix of y values:
         [ y0 ]
         [ y1 ]
         [ ... ]
         [ yn-1 ]
      */
      RealMatrix xMatrix = new Array2DRowRealMatrix(size, params);
      RealMatrix yMatrix = new Array2DRowRealMatrix(size, 1);

      // populate the X and Y matrices
      for(int j = 0; j < params; j++) {
         for(int i = 0; i < size; i++) {
            double xval = points[i].getX();
            xMatrix.setEntry(i, j, Math.pow(xval, j));

            xmin = Math.min(xmin, xval);
            xmax = Math.max(xmax, xval);
         }
      }

      for(int i = 0; i < size; i++) {
         yMatrix.setEntry(i, 0, points[i].getY());
      }

      // multiply both sides of the equation by the transposition of X this
      // makes a square matrix for the x values, which can be inverted
      RealMatrix xTranspose = xMatrix.transpose();
      RealMatrix xNormal = xTranspose.multiply(xMatrix);
      RealMatrix yNormal = xTranspose.multiply(yMatrix);

      // invert the x normal matrix
      xNormal = new LUDecomposition(xNormal).getSolver().getInverse();

      // multiply the inverse of the x normal matrix and the y normal matrix
      // to solve for the coefficients, A.
      RealMatrix coeffs = xNormal.multiply(yNormal);
      double[] carray = coeffs.getColumn(0);
      return getPoints(carray);
   }

   /**
    * Calculate the y-coordinate at the specified x-coordinate.
    * @param x the x-coordinate.
    * @param params the equation parameters.
    * @return the y-coordinate.
    */
   protected double calculateY(double x, double[] params) {
      double yval = 0.0;

      for(int i = 0; i < params.length; i++) {
         // add Cj * Xi term
         yval += Math.pow(x, (double) i) * params[i];
      }

      return yval;
   }

   /**
    * Generates the points for a polynomial line.
    * @param params the equation parameters.
    * @return the points
    */
   protected abstract Point2D[] getPoints(double[] params);

   private int degree = 2;
   protected double xmin = Double.POSITIVE_INFINITY;

   /**
    * Renders a linear trendline.
    */
   @TernClass(url = "#cshid=PolynomialLineEquation")
   public static class Linear extends PolynomialLineEquation {
      /**
       * Create a new instance of Linear.
       */
      public Linear() {
         super(1);
      }

      @Override
      protected Point2D[] getPoints(double[] params) {
         return new Point2D[] {
            new Point2D.Double(xmin, calculateY(xmin, params)),
            new Point2D.Double(xmax, calculateY(xmax, params))
         };
      }
   }

   /**
    * Renders a quadradic trendline.
    */
   @TernClass(url = "#cshid=PolynomialLineEquation")
   public static class Quadratic extends PolynomialLineEquation {
      /**
       * Create a new instance of Quadratic.
       */
      public Quadratic() {
         super(2);
      }

      @Override
      protected Point2D[] getPoints(double[] params) {
         double inc = (xmax - xmin) / 100.0;
         List<Point2D> vec = new ArrayList<>();

         for(double x = xmin; x <= xmax; x += inc) {
            double y = calculateY(x, params);

            vec.add(new Point2D.Double(x, y));
         }

         return vec.toArray(new Point2D[0]);
      }
   }

   /**
    * Renders a cubic trendline.
    */
   @TernClass(url = "#cshid=PolynomialLineEquation")
   public static class Cubic extends PolynomialLineEquation {
      /**
       * Create a new instance of Cubic.
       */
      public Cubic() {
         super(3);
      }

      @Override
      protected Point2D[] getPoints(double[] params) {
         double inc = (xmax - xmin) / 100.0;
         List<Point2D> vec = new ArrayList<>();

         for(double x = xmin; x <= xmax; x += inc) {
            double y = calculateY(x, params);
            vec.add(new Point2D.Double(x, y));
         }

         return vec.toArray(new Point2D[0]);
      }
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(PolynomialLineEquation.class);
}
