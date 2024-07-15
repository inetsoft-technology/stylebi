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
package inetsoft.graph.aesthetic;

import inetsoft.graph.scale.Scale;

/**
 * This class defines a size frame where only positive values are accepted. If there are
 * negative values, the values are mapped to (max - min + smallest). Otherwise the
 * values are used as size without mapping.
 *
 * @version 13.2
 * @author InetSoft Technology
 */
public class TreemapSizeFrame extends LinearSizeFrame {
   /**
    * Create a size frame.
    */
   public TreemapSizeFrame() {
   }

   /**
    * Create a size frame.
    * @param field field to get value to map to sizes.
    */
   public TreemapSizeFrame(String field) {
      super(field);
   }

   @Override
   public double getSmallest() {
      Scale scale = getScale();

      if(scale == null) {
         return super.getSmallest();
      }

      double min = scale.getMin();

      // treemap requires >= 1 to show up
      if(min < 1) {
         return 1;
      }

      return scale.getMin();
   }

   @Override
   public double getLargest() {
      Scale scale = getScale();

      if(scale == null) {
         return super.getLargest();
      }

      double min = scale.getMin();

      if(min < 0) {
         return scale.getMax() - min + 1;
      }
      // min forced to 1 if it's 0 (or fraction), return a small value would make
      // the max (e.g. 1.1) have almost the same size as min (0). scale the max to be
      // proportional.
      else if(min < 1) {
         return Math.max(scale.getMax(), Math.min(1000, scale.getMax() / (Math.max(min, 0.0001))));
      }

      return scale.getMax();
   }

   private static final long serialVersionUID = 1L;
}
