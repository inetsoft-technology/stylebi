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
package inetsoft.graph.internal;

/**
 * Axis size strategy access the size of an axis.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AxisSizeStrategy {
   public AxisSizeStrategy(int axis) {
      this.axis = axis;
   }

   /**
    * Get the min size.
    */
   public double getMinSize(ICoordinate coord) {
      return coord.getAxisMinSize(axis);
   }

   /**
    * Get the preferred size.
    */
   public double getPreferredSize(ICoordinate coord) {
      return coord.getAxisPreferredSize(axis);
   }

   /**
    * Get the size.
    */
   public double getSize(ICoordinate coord) {
      return coord.getAxisSize(axis);
   }

   /**
    * Set the size.
    */
   public void setSize(ICoordinate coord, double val) {
      coord.setAxisSize(axis, val);
   }

   /**
    * Check if is visible.
    */
   public boolean isVisible(ICoordinate coord) {
      return coord.isAxisLabelVisible(axis);
   }

   public int getAxis() {
      return axis;
   }

   private int axis;
}
