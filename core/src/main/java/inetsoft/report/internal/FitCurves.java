/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal;

import java.util.ArrayList;
import java.util.List;

/*
 An Algorithm for Automatically Fitting Digitized Curves
 by Philip J. Schneider
 from "Graphics Gems", Academic Press, 1990
 */

/* Piecewise cubic fitting code	*/

/**
 *
 * @version 5.1, 9/20/2003
 * @author Inetsoft Technology
 */
public class FitCurves {
   public static class Point2 {
      public Point2(double x, double y) {
         this.x = x;
         this.y = y;
      }

      public Point2 dup() {
         return new Point2(x, y);
      }

      public String toString() {
         return "(" + x + "," + y + ")";
      }

      public double x, y;
   }

   /*
    *  FitCurve :
    *  	Fit a Bezier curve to a set of digitized points
    * @param d Array of digitized points
    * @param nPts Number of digitized points
    * @param error User-defined error squared
    */
   public FitCurves(Point2[] d, double error) {
      Point2 tHat1, tHat2;	/* Unit tangent vectors at endpoints */
      int nPts = d.length;

      tHat1 = ComputeLeftTangent(d, 0);
      tHat2 = ComputeRightTangent(d, nPts - 1);
      FitCubic(d, 0, nPts - 1, tHat1, tHat2, error);
   }

   /**
    * Return the curves. Each curve is a Point2 array with 4 elements.
    */
   public List getCurves() {
      return curves;
   }

   /*
    *  FitCubic :
    *  	Fit a Bezier curve to a (sub)set of digitized points
    * @param d Array of digitized points
    * @param first Indices of first and last pts in region
    * @param last Indices of first and last pts in region
    * @param tHat1 Unit tangent vectors at endpoints
    * @param tHat2 Unit tangent vectors at endpoints
    * @param error User-defined error squared
    */
   void FitCubic(Point2[] d, int first, int last, Point2 tHat1,
      Point2 tHat2, double error) {
      Point2[] bezCurve; /* Control points of fitted Bezier curve*/
      double[] u;		/* Parameter values for point  */
      double[] uPrime;	/* Improved parameter values */
      double maxError;	/* Maximum fitting error	 */
      int[] splitPoint = {0};	/* Point to split point set at	 */
      int nPts;		/* Number of points in subset  */
      double iterationError; /* Error below which you try iterating  */
      int maxIterations = 4; /* Max times to try iterating  */
      Point2 tHatCenter;   	/* Unit tangent vector at splitPoint */
      int i;

      iterationError = error * error;
      nPts = last - first + 1;

      /* Use heuristic if region only has two points in it */
      if(nPts == 2) {
         double dist = V2DistanceBetween2Points(d[last], d[first]) / 3.0;

         bezCurve = new Point2[4];
         alloc(bezCurve);
         bezCurve[0] = d[first];
         bezCurve[3] = d[last];
         V2Add(bezCurve[0], V2Scale(tHat1, dist), bezCurve[1]);
         V2Add(bezCurve[3], V2Scale(tHat2, dist), bezCurve[2]);

         DrawBezierCurve(bezCurve);
         return;
      }

      /* Parameterize points, and attempt to fit curve */
      u = ChordLengthParameterize(d, first, last);

      /*
        for(int k = 0; k < u.length; k++) {
        }
      */

      bezCurve = GenerateBezier(d, first, last, u, tHat1, tHat2);

      /*
        bezCurve[0] + bezCurve[1] + bezCurve[2] + bezCurve[3]);
      */

      /* Find max deviation of points to fitted curve */
      maxError = ComputeMaxError(d, first, last, bezCurve, u, splitPoint);

      if(maxError < error) {
         DrawBezierCurve(bezCurve);
         return;
      }

      /* If error not too large, try some reparameterization  */

      /* and iteration */
      if(maxError < iterationError) {
         for(i = 0; i < maxIterations; i++) {
            uPrime = Reparameterize(d, first, last, u, bezCurve);
            bezCurve = GenerateBezier(d, first, last, uPrime, tHat1, tHat2);
            maxError = ComputeMaxError(d, first, last, bezCurve, uPrime,
               splitPoint);

            if(maxError < error) {
               DrawBezierCurve(bezCurve);
               return;
            }

            u = uPrime;
         }
      }

      /* Fitting failed -- split at max error point and fit recursively */
      tHatCenter = ComputeCenterTangent(d, splitPoint[0]);
      FitCubic(d, first, splitPoint[0], tHat1, tHatCenter, error);
      V2Negate(tHatCenter);
      FitCubic(d, splitPoint[0], last, tHatCenter, tHat2, error);
   }

   /*
    *  GenerateBezier :
    *  Use least-squares method to find Bezier control points for region.
    *
    * @param d Array of digitized points
    * @param first Indices defining region
    * @param last Indices defining region
    * @param uPrime Parameter values for region
    * @param tHat1 Unit tangents at endpoints
    * @param tHat2 Unit tangents at endpoints
    */
   static Point2[] GenerateBezier(Point2[] d, int first, int last,
      double[] uPrime, Point2 tHat1, Point2 tHat2) {
      int i;
      int nPts = last - first + 1; /* Number of pts in sub-curve */
      Point2[][] A = new Point2[nPts][2];/* Precomputed rhs for eqn*/
      double[][] C = new double[2][2];	/* Matrix C		*/
      double[] X = new double[2];	/* Matrix X			*/
      double det_C0_C1,		/* Determinants of matrices	*/
         det_C0_X,
         det_X_C1;
      double alpha_l,		/* Alpha values, left and right	*/
         alpha_r;
      Point2 tmp;			/* Utility variable		*/
      Point2[] bezCurve;	/* RETURN bezier curve ctl pts	*/

      bezCurve = new Point2[4];
      alloc(bezCurve);
      nPts = last - first + 1;

      /* Compute the A's	*/
      for(i = 0; i < nPts; i++) {
         Point2 v1, v2;

         v1 = tHat1.dup();
         v2 = tHat2.dup();
         V2Scale(v1, B1(uPrime[i]));
         V2Scale(v2, B2(uPrime[i]));
         A[i][0] = v1;
         A[i][1] = v2;
      }

      /* Create the C and X matrices	*/
      C[0][0] = 0.0;
      C[0][1] = 0.0;
      C[1][0] = 0.0;
      C[1][1] = 0.0;
      X[0] = 0.0;
      X[1] = 0.0;

      for(i = 0; i < nPts; i++) {
         C[0][0] += V2Dot(A[i][0], A[i][0]);
         C[0][1] += V2Dot(A[i][0], A[i][1]);
         C[1][0] = C[0][1];
         C[1][1] += V2Dot(A[i][1], A[i][1]);

         tmp = V2SubII(d[first + i],
            V2AddII(V2ScaleIII(d[first], B0(uPrime[i])),
            V2AddII(V2ScaleIII(d[first], B1(uPrime[i])),
            V2AddII(V2ScaleIII(d[last], B2(uPrime[i])),
            V2ScaleIII(d[last], B3(uPrime[i]))))));

         X[0] += V2Dot(A[i][0], tmp);
         X[1] += V2Dot(A[i][1], tmp);
      }

      /* Compute the determinants of C and X	*/
      det_C0_C1 = C[0][0] * C[1][1] - C[1][0] * C[0][1];
      det_C0_X = C[0][0] * X[1] - C[0][1] * X[0];
      det_X_C1 = X[0] * C[1][1] - X[1] * C[0][1];

      /* Finally, derive alpha values	*/
      if(det_C0_C1 == 0.0) {
         det_C0_C1 = (C[0][0] * C[1][1]) * 10e-12;
      }

      alpha_l = det_X_C1 / det_C0_C1;
      alpha_r = det_C0_X / det_C0_C1;

      /* If alpha negative, use the Wu/Barsky heuristic (see text) */

      /* (if alpha is 0, you get coincident control points that lead to
       * divide by zero in any subsequent NewtonRaphsonRootFind() call. */
      if(Double.isNaN(alpha_l) || Double.isNaN(alpha_r) ||
         Double.isInfinite(alpha_l) || Double.isInfinite(alpha_r) ||
         alpha_l < 1.0e-6 || alpha_r < 1.0e-6) {
         double dist = V2DistanceBetween2Points(d[last], d[first]) / 3.0;

         bezCurve[0] = d[first];
         bezCurve[3] = d[last];
         V2Add(bezCurve[0], V2Scale(tHat1, dist), bezCurve[1]);
         V2Add(bezCurve[3], V2Scale(tHat2, dist), bezCurve[2]);

         /*
           bezCurve[2]);
         */
         return (bezCurve);
      }

      /* First and last control points of the Bezier curve are */

      /* positioned exactly at the first and last data points */

      /* Control points 1 and 2 are positioned an alpha distance out */

      /* on the tangent vectors, left and right, respectively */
      /*
      */

      bezCurve[0] = d[first];
      bezCurve[3] = d[last];
      V2Add(bezCurve[0], V2Scale(tHat1, alpha_l), bezCurve[1]);
      V2Add(bezCurve[3], V2Scale(tHat2, alpha_r), bezCurve[2]);

      return (bezCurve);
   }

   /*
    *  Reparameterize:
    *	Given set of points and their parameterization, try to find
    *   a better parameterization.
    *
    * @param d Array of digitized points
    * @param first Indices defining region
    * @param last Indices defining region
    * @param u Current parameter values
    * @param bezCurve Current fitted curve
    */
   static double[] Reparameterize(Point2[] d, int first, int last, double[] u,
      Point2[] bezCurve) {
      int nPts = last - first + 1;
      int i;
      double[] uPrime;		/* New parameter values	*/

      uPrime = new double[nPts];
      for(i = first; i <= last; i++) {
         uPrime[i - first] = NewtonRaphsonRootFind(bezCurve, d[i],
            u[i - first]);
      }

      return (uPrime);
   }

   /*
    *  NewtonRaphsonRootFind :
    *	Use Newton-Raphson iteration to find better root.
    * @param Q Current fitted curve
    * @param P Digitized point
    * @param u Parameter value for "P"
    */
   static double NewtonRaphsonRootFind(Point2[] Q, Point2 P, double u) {
      double numerator, denominator;
      /* Q' and Q''			*/
      Point2[] Q1 = new Point2[3];
      Point2[] Q2 = new Point2[2];
      Point2 Q_u, Q1_u, Q2_u; /* u evaluated at Q, Q', & Q''	*/
      double uPrime;		/* Improved u			*/
      int i;

      /* Compute Q(u)	*/
      Q_u = BezierII(3, Q, u);

      /* Generate control vertices for Q'	*/
      for(i = 0; i <= 2; i++) {
         Q1[i] = new Point2((Q[i + 1].x - Q[i].x) * 3.0,
            (Q[i + 1].y - Q[i].y) * 3.0);
      }

      /* Generate control vertices for Q'' */
      for(i = 0; i <= 1; i++) {
         Q2[i] = new Point2((Q1[i + 1].x - Q1[i].x) * 2.0,
            (Q1[i + 1].y - Q1[i].y) * 2.0);
      }

      /* Compute Q'(u) and Q''(u)	*/
      Q1_u = BezierII(2, Q1, u);
      Q2_u = BezierII(1, Q2, u);

      /* Compute f(u)/f'(u) */
      numerator = (Q_u.x - P.x) * (Q1_u.x) + (Q_u.y - P.y) * (Q1_u.y);
      denominator = (Q1_u.x) * (Q1_u.x) + (Q1_u.y) * (Q1_u.y) +
         (Q_u.x - P.x) * (Q2_u.x) + (Q_u.y - P.y) * (Q2_u.y);

      /* u = u - f(u)/f'(u) */
      uPrime = u - (numerator / denominator);
      return (uPrime);
   }

   /*
    *  Bezier :
    *  	Evaluate a Bezier curve at a particular parameter value
    * @param degree The degree of the bezier curve
    * @param V Array of control points
    * @param t Parametric value to find point for
    */
   static Point2 BezierII(int degree, Point2[] V, double t) {
      int i, j;
      Point2 Q;	        /* Point on curve at parameter t	*/
      Point2[] Vtemp;		/* Local copy of control points		*/

      /* Copy array	*/
      Vtemp = new Point2[degree + 1];
      for(i = 0; i <= degree; i++) {
         Vtemp[i] = V[i].dup();
      }

      /* Triangle computation	*/
      for(i = 1; i <= degree; i++) {
         for(j = 0; j <= degree - i; j++) {
            Vtemp[j].x = (1.0 - t) * Vtemp[j].x + t * Vtemp[j + 1].x;
            Vtemp[j].y = (1.0 - t) * Vtemp[j].y + t * Vtemp[j + 1].y;
         }
      }

      Q = Vtemp[0];
      return Q;
   }

   /*
    *  B0, B1, B2, B3 :
    *	Bezier multipliers
    */
   static double B0(double u) {
      double tmp = 1.0 - u;

      return (tmp * tmp * tmp);
   }

   static double B1(double u) {
      double tmp = 1.0 - u;

      return (3 * u * (tmp * tmp));
   }

   static double B2(double u) {
      double tmp = 1.0 - u;

      return (3 * u * u * tmp);
   }

   static double B3(double u) {
      return (u * u * u);
   }

   /*
    * ComputeLeftTangent, ComputeRightTangent, ComputeCenterTangent :
    *Approximate unit tangents at endpoints and "center" of digitized curve
    * @param d Digitized points
    * @param end Index to "left" end of region
    */
   static Point2 ComputeLeftTangent(Point2[] d, int end) {
      Point2 tHat1;

      tHat1 = V2SubII(d[end + 1], d[end]);
      tHat1 = V2Normalize(tHat1);
      return tHat1;
   }

   /**
    * @param d Digitized points
    * @param end Index to "right" end of region
    */
   static Point2 ComputeRightTangent(Point2[] d, int end) {
      Point2 tHat2;

      tHat2 = V2SubII(d[end - 1], d[end]);
      tHat2 = V2Normalize(tHat2);
      return tHat2;
   }

   /**
    * @param d Digitized points
    * @param center Index to point inside region
    */
   static Point2 ComputeCenterTangent(Point2[] d, int center) {
      Point2 V1, V2, tHatCenter;

      V1 = V2SubII(d[center - 1], d[center]);
      V2 = V2SubII(d[center], d[center + 1]);
      tHatCenter = new Point2((V1.x + V2.x) / 2.0, (V1.y + V2.y) / 2.0);
      tHatCenter = V2Normalize(tHatCenter);
      return tHatCenter;
   }

   /*
    *  ChordLengthParameterize :
    *	Assign parameter values to digitized points
    *	using relative distances between points.
    * @param d Array of digitized points
    * @param first Indices defining region
    * @param last Indices defining region
    */
   static double[] ChordLengthParameterize(Point2[] d, int first, int last) {
      int i;
      double[] u;			/* Parameterization		*/

      u = new double[last - first + 1];

      u[0] = 0.0;
      for(i = first + 1; i <= last; i++) {
         u[i - first] = u[i - first - 1] +
            V2DistanceBetween2Points(d[i], d[i - 1]);
      }

      for(i = first + 1; i <= last; i++) {
         u[i - first] = u[i - first] / u[last - first];
      }

      return (u);
   }

   /*
    *  ComputeMaxError :
    *	Find the maximum squared distance of digitized points
    *	to fitted curve.
    * @param d Array of digitized points
    * @param first Indices defining region
    * @param last Indices defining region
    * @param bezCurve Fitted Bezier curve
    * @param u Parameterization of points
    * @param splitPoint Point of maximum error
    */
   static double ComputeMaxError(Point2[] d, int first, int last,
      Point2[] bezCurve, double[] u,
      int[] splitPoint) {
      int i;
      double maxDist;		/* Maximum error		*/
      double dist;		/* Current error		*/
      Point2 P;			/* Point on curve		*/
      Point2 v;			/* Vector from point to curve	*/

      splitPoint[0] = (last - first + 1) / 2;
      maxDist = 0.0;
      for(i = first + 1; i < last; i++) {
         P = BezierII(3, bezCurve, u[i - first]);
         v = V2SubII(P, d[i]);
         dist = V2SquaredLength(v);
         if(dist >= maxDist) {
            maxDist = dist;
            splitPoint[0] = i;
         }
      }

      return (maxDist);
   }

   static Point2 V2AddII(Point2 a, Point2 b) {
      return new Point2(a.x + b.x, a.y + b.y);
   }

   static Point2 V2ScaleIII(Point2 v, double s) {
      return new Point2(v.x * s, v.y * s);
   }

   static Point2 V2SubII(Point2 a, Point2 b) {
      return new Point2(a.x - b.x, a.y - b.y);
   }

   /* scales the input vector to the new length and returns it */
   static Point2 V2Scale(Point2 v, double newlen) {
      double len = V2Length(v);

      if(len != 0.0) {
         v.x *= newlen / len;
         v.y *= newlen / len;
      }

      return (v);
   }

   /* return vector sum c = a+b */
   static Point2 V2Add(Point2 a, Point2 b, Point2 c) {
      c.x = a.x + b.x;
      c.y = a.y + b.y;
      return c;
   }

   /* negates the input vector and returns it */
   static Point2 V2Negate(Point2 v) {
      v.x = -v.x;
      v.y = -v.y;
      return (v);
   }

   /* return the dot product of vectors a and b */
   static double V2Dot(Point2 a, Point2 b) {
      return ((a.x * b.x) + (a.y * b.y));
   }

   /* normalizes the input vector and returns it */
   static Point2 V2Normalize(Point2 v) {
      double len = V2Length(v);

      if(len != 0.0) {
         v.x /= len;
         v.y /= len;
      }

      return (v);
   }

   /* return the distance between two points */
   static double V2DistanceBetween2Points(Point2 a, Point2 b) {
      double dx = a.x - b.x;
      double dy = a.y - b.y;

      return (Math.sqrt((dx * dx) + (dy * dy)));
   }

   /* returns squared length of input vector */
   static double V2SquaredLength(Point2 a) {
      return ((a.x * a.x) + (a.y * a.y));
   }

   /* returns length of input vector */
   static double V2Length(Point2 a) {
      return (Math.sqrt(V2SquaredLength(a)));
   }

   static void alloc(Point2[] arr) {
      for(int i = 0; i < arr.length; i++) {
         arr[i] = new Point2(0, 0);
      }
   }

   void DrawBezierCurve(Point2[] curve) {
      /*
        for(int i = 0; i < curve.length; i++) {
        }
      */
      curves.add(curve);
   }

   // @by louis, pass the security scanning
   //public static void main(String args[]) {
   //   Point2 d[] = {	/* Digitized points */ new Point2(0.0, 0.0),
   //     new Point2(0.0, 0.5), new Point2(1.1, 1.4), new Point2(2.1, 1.6),
   //      new Point2(3.2, 1.1), new Point2(4.0, 0.2), new Point2(4.0, 0.0), };
   //   double error = 4.0;		/* Squared error */

   //   new FitCurves(d, error);		/* Fit the Bezier curves */
   //}

   List curves = new ArrayList(); // Point2[]
}

