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
package inetsoft.graph.internal;

import java.util.ArrayList;
import java.util.List;

/**
 * Axis size list assign size to axis objects.
 *
 * @version 10.0
 * @author InetSoft Technology Corp
 */
public class AxisSizeList implements ISize {
   /**
    * Constructor.
    * @param strategy the specified axis size strategy.
    */
   public AxisSizeList(AxisSizeStrategy strategy) {
      super();
      coords = new ArrayList();
      this.strategy = strategy;
      msize = -1;
      psize = -1;
   }

   /**
    * Clear cached sizes.
    */
   public void invalidate() {
      psize = -1;
      msize = -1;
   }

   /**
    * Add one coordinate.
    */
   public void add(ICoordinate coord) {
      if(strategy.isVisible(coord)) {
         coords.add(coord);
      }
   }

   /**
    * Get the min size.
    */
   @Override
   public double getMinSize() {
      if(msize < 0) {
         double size = 0;

         for(int i = 0; i < coords.size(); i++) {
            ICoordinate coord = (ICoordinate) coords.get(i);
            size = Math.max(strategy.getMinSize(coord), size);
         }

         msize = size;
      }

      return msize;
   }

   /**
    * Get the preferred size.
    */
   @Override
   public double getPreferredSize() {
      if(psize < 0) {
         double size = 0;

         for(int i = 0; i < coords.size(); i++) {
            ICoordinate coord = (ICoordinate) coords.get(i);
            size = Math.max(strategy.getPreferredSize(coord), size);
         }

         psize = size;
      }

      return psize;
   }

   /**
    * Get the size.
    */
   @Override
   public double getSize() {
      ICoordinate coord = (ICoordinate) coords.get(0);
      return strategy.getSize(coord);
   }

   /**
    * Set the size.
    */
   @Override
   public void setSize(double size) {
      for(int i = 0; i < coords.size(); i++) {
         ICoordinate coord = (ICoordinate) coords.get(i);
         strategy.setSize(coord, size);
      }
   }

   /**
    * Get the count.
    */
   public int getCount() {
      return coords.size();
   }

   @Override
   public String toString() {
      return super.toString() + "[" + strategy.getAxis() + "]";
   }

   private List coords; // coordinate for axis
   private AxisSizeStrategy strategy; // size strategy
   private double msize; // cached min size
   private double psize; // cached preferred size
}
