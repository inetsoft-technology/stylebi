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
package inetsoft.report.composition.region;

import inetsoft.graph.guide.axis.DefaultAxis;
import inetsoft.report.internal.PolygonRegion;
import inetsoft.report.internal.Region;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;

/**
 * RadarMeasureLabelsArea Class.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class RadarMeasureLabelsArea extends MeasureLabelsArea {
   /**
    * Constructor.
    */
   public RadarMeasureLabelsArea(DefaultAxis vAxis, AffineTransform trans,
                                 IndexedSet<String> palette) {
      super(vAxis, trans, palette, false);
      this.axisType = ChartArea.Y_AXIS;
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      DefaultAxis axis = (DefaultAxis) vobj;
      Point2D pos1 = new Point2D.Double(0, 0);
      Point2D pos2 = new Point2D.Double(axis.getLength(), 0);
      pos1 = axis.getScreenTransform().transform(pos1, null);
      pos2 = axis.getScreenTransform().transform(pos2, null);

      Polygon poly = new Polygon();
      poly.addPoint((int) pos1.getX(), (int) pos1.getY());

      double distance = pos1.distance(pos2);
      double xdistance = pos2.getX() - pos1.getX();
      double ydistance = pos2.getY() - pos1.getY();
      double cos = xdistance / distance;
      double sin = ydistance / distance;

      Point2D pos3 = new Point2D.Double(pos2.getX() + AXIS_HEIGHT * sin,
         pos2.getY() - AXIS_HEIGHT * cos);
      Point2D pos4 = new Point2D.Double((int) pos1.getX() + AXIS_HEIGHT * sin,
         pos1.getY() - AXIS_HEIGHT * cos);

      poly.addPoint((int) pos2.getX(), (int) pos2.getY());
      poly.addPoint((int) pos3.getX(), (int) pos3.getY());
      poly.addPoint((int) pos4.getX(), (int) pos4.getY());
      poly.addPoint((int) pos1.getX(), (int) pos1.getY());

      Point2D p = getRelPos();
      int[] xps = poly.xpoints;
      int[] yps = poly.ypoints;

      for(int i = 0; i < poly.npoints; i++) {
         Point2D pt = new Point2D.Double(xps[i], yps[i]);
         pt = trans.transform(pt, null);
         xps[i] = (int) (pt.getX() - p.getX());
         yps[i] = (int) (pt.getY() - p.getY());
      }

      return new Region[] {new PolygonRegion("", xps, yps, poly.npoints, false)};
   }

    /**
    * Method to test whether regions contain the specified point.
    * @param point the specified point.
    */
   @Override
   public boolean contains(Point point) {
      return getRegion().contains(point.x, point.y);
   }

   private static int AXIS_HEIGHT = 20;
}
