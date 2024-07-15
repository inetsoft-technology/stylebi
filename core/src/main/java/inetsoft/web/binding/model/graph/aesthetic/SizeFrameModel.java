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
package inetsoft.web.binding.model.graph.aesthetic;

import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.SizeFrameWrapper;

public abstract class SizeFrameModel extends VisualFrameModel {
   public SizeFrameModel() {
   }

   public SizeFrameModel(SizeFrameWrapper wrapper) {
      super(wrapper);
      SizeFrame frame = (SizeFrame) wrapper.getVisualFrame();
      setLargest(frame.getLargest());
      setSmallest(frame.getSmallest());
      setChanged(wrapper.isChanged());
   }

   /**
    * Set the largest value of the range, smallest <= largest.
    */
   public void setLargest(double val) {
      this.largest = val;
   }

   /**
    * Get the largest value of the range.
    */
   public double getLargest() {
      return largest;
   }

   /**
    * Set the smallest value of the range, smallest <= largest.
    */
   public void setSmallest(double val) {
      this.smallest = val;
   }

   /**
    * Get the smallest value of the range.
    */
   public double getSmallest() {
      return smallest;
   }

   private double largest = 30;
   private double smallest = 1;
}
