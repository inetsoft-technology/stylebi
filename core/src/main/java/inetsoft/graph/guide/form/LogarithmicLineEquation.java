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
package inetsoft.graph.guide.form;

import com.inetsoft.build.tern.TernClass;

import java.awt.geom.Point2D;
import java.util.ArrayList;

/**
 * Renders a logarithmic trendline.
 *
 * @hidden
 * @version 10.0.
 * @author InetSoft Technology Corp.
 */
@TernClass(url = "#cshid=LogarithmicLineEquation")
public class LogarithmicLineEquation extends AbstractLineEquation {
   /**
    * Calculate the points on the line.
    * @param points the points to fit the line.
    * @return the points on the equation.
    */
   @Override
   public Point2D[] calculate(Point2D... points) {
      double ylogx = 0.0;
      double y = 0.0;
      double logx = 0.0;
      double logxsq = 0.0;
      double xmin = Double.POSITIVE_INFINITY;

      for(Point2D point : points) {
         final double xi = point.getX();
         final double yi = point.getY();
         double logxi = Math.log(xi);

         if(xi == 0) {
            logxi = 0; // log(0) is infinity
         }

         xmin = Math.min(xmin, xi);
         xmax = Math.max(xmax, xi);

         ylogx += yi * logxi;
         y += yi;

         logx += logxi;
         logxsq += Math.pow(logxi, 2.0);
      }

      double n = points.length;
      double b = (n * ylogx - y * logx) / (n * logxsq - Math.pow(logx, 2.0));
      double a = (y - b * logx) / n;
      double inc = (xmax - xmin) / 100.0;

      if(xmin == xmax || inc == 0) {
         return new Point2D[0];
      }

      ArrayList<Point2D.Double> vec = new ArrayList<>();

      for(double x1 = xmin; x1 <= xmax; x1 += inc) {
         if(x1 <= 0) {
            continue;
         }

         double y1 = a + b * Math.log(x1);

         vec.add(new Point2D.Double(x1, y1));
      }

      return vec.toArray(new Point2D[0]);
   }

   private static final long serialVersionUID = 1L;
}
