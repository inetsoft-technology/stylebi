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
package inetsoft.graph.aesthetic;

import com.inetsoft.build.tern.TernMethod;
import inetsoft.graph.data.DataSet;

/**
 * Static size frame defines a static size for visual objects.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
public abstract class SizeFrame extends VisualFrame {
   /**
    * Get the size for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   public abstract double getSize(DataSet data, String col, int row);

   /**
    * Get the size for the specified value. Size values are interpreted by each
    * graph element. It may not correspond directly to the point size of the
    * visual object. Each element has a base size. The size from the size frame
    * is used to proportionally increase the base size.
    */
   public abstract double getSize(Object val);

   /**
    * Set the largest value of the range, smallest &lt;= largest.
    */
   @TernMethod
   public void setLargest(double val) {
      this.largest = val;
   }

   /**
    * Get the largest value of the range.
    */
   @TernMethod
   public double getLargest() {
      return largest;
   }

   /**
    * Set the smallest value of the range, smallest &lt;= largest.
    */
   @TernMethod
   public void setSmallest(double val) {
      this.smallest = val;
   }

   /**
    * Get the smallest value of the range.
    */
   @TernMethod
   public double getSmallest() {
      return smallest;
   }

   /**
    * Check if equals another object.
    */
   /* @by larryl, when size frame is rendered in legend, the smallest and
      largest values don't make the rendering any different. since equals()
      is used in EGraph.getVisualFrames() to see if two frames will result
      in identical legend, we should ignore the comparison for smallest and
      largest. If we do need to distinguish it for other purpose, we need
      to change how getVisualFrames() compare the frames
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      SizeFrame frame2 = (SizeFrame) obj;
      return frame2.largest == largest && frame2.smallest == smallest && frame2.max == max;
   }
   */

   /**
    * Check if content equals.
    * @hidden
    */
   @Override
   public boolean equalsContent(Object obj) {
      if(!super.equalsContent(obj)) {
         return false;
      }

      SizeFrame frame2 = (SizeFrame) obj;
      return frame2.largest == largest && frame2.smallest == smallest && frame2.max == max;
   }

   /**
    * Set the max value. The largest value should be less than or equal to
    * max value.
    */
   @TernMethod
   public void setMax(double max) {
      this.max = max;
   }

   /**
    * Get the max value.The largest value should be less than or equal to
    * max value.
    */
   @TernMethod
   public double getMax() {
      return max;
   }

   private double largest = 30;
   private double smallest = 1;
   private double max = 30;
}
