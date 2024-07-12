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

import inetsoft.report.internal.RectangleRegion;
import inetsoft.report.internal.Region;

import java.awt.geom.Rectangle2D;

/**
 * An area on a chart.
 *
 * @version 11.5
 * @author InetSoft Technology Corp
 */
public class FixedArea extends DefaultArea {
   /**
    * Constructor.
    */
   public FixedArea(Rectangle2D region) {
      super(null, null);
      this.region = region;
   }

   /**
    * Get regions.
    */
   @Override
   public Region[] getRegions() {
      return new Region[] {new RectangleRegion(region)};
   }

   private Rectangle2D region;
}
