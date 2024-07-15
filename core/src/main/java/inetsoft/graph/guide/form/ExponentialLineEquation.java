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

import com.inetsoft.build.tern.TernClass;
import com.inetsoft.build.tern.TernMethod;

import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Renders a exponential trendline.
 *
 * @hidden
 * @version 10.0.
 * @author InetSoft Technology Corp.
 */
@TernClass(url = "#cshid=ExponentialLineEquation")
public class ExponentialLineEquation extends AbstractLineEquation {
   /**
    * Calculate the points on the line.
    * @param points the points to fit the line.
    * @return the points on the equation.
    */
   @Override
   @TernMethod
   public Point2D[] calculate(Point2D... points) {
      double logy = 0.0;
      double xsq = 0.0;
      double x = 0.0;
      double xlogy = 0.0;
      double xmin = Double.POSITIVE_INFINITY;

      for(Point2D point : points) {
         double xi = point.getX();
         double yi = point.getY();

         xmin =  Math.min(xmin, xi);
         xmax =  Math.max(xmax, xi);

         yi = yi <= 1 ? 1 : yi;

         logy += Math.log(yi);
         xsq += Math.pow(xi, 2.0);
         x += xi;
         xlogy += xi * Math.log(yi);
      }

      double n = (double) points.length;
      double a = (logy * xsq - x * xlogy) / (n * xsq - Math.pow(x, 2.0));
      double b = (n * xlogy - x * logy) / (n * xsq - Math.pow(x, 2.0));
      double expa = Math.exp(a);
      double inc = (xmax - xmin) / 100.0;

      if(xmin == xmax || inc == 0) {
         return new Point2D[0];
      }

      ArrayList<Point2D.Double> vec = new ArrayList<>();

      for(double x1 = xmin; x1 <= xmax; x1 += inc) {
         double y1 = expa * Math.exp(x1 * b);

         vec.add(new Point2D.Double(x1, y1));
      }

      return vec.toArray(new Point2D[0]);
   }

   private static final long serialVersionUID = 1L;
}
