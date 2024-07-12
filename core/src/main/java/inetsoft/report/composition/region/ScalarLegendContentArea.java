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

import inetsoft.graph.guide.legend.Legend;
import inetsoft.report.internal.RectangleRegion;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.util.List;

/**
 * ScalarLegendContentArea defines the method of write data to an OutputStream
 *  and parse it from an InputStream.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class ScalarLegendContentArea extends LegendContentArea implements RollOverArea {
   /**
    * Constructor.
    */
   public ScalarLegendContentArea(Legend legend, List<String> targetFields,
                                  boolean sharedColor, AffineTransform trans,
                                  IndexedSet<String> palette)
   {
      super(legend, targetFields, sharedColor, trans, palette);
   }

   /**
    * Paint area.
    * @param g the graphic of the area.
    */
   @Override
   public void paintArea(Graphics g, Color color) {
      RectangleRegion region = (RectangleRegion) getRegion();

      if(region != null) {
         region.fill(g, color);
      }
   }
}
