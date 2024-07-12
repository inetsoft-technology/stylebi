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
package inetsoft.web.vswizard.recommender.chart;

import it.unimi.dsi.fastutil.ints.IntList;

public class ChartRefCombination {
   public ChartRefCombination() {
   }

   public ChartRefCombination(IntList x, IntList y, IntList inside) {
      this.x = x;
      this.y = y;
      this.inside = inside;
   }

   public IntList getX() {
      return this.x;
   }

   public IntList getY() {
      return this.y;
   }

   public IntList getInside() {
      return this.inside;
   }

   public void setX(IntList xIndex) {
      this.x = xIndex;
   }

   public void setY(IntList yIndex) {
      this.y = yIndex;
   }

   public void setInside(IntList insideIndex) {
      this.inside = insideIndex;
   }

   public int getXCount() {
      return x == null ? 0 : x.size();
   }

   public int getYCount() {
      return y == null ? 0 : y.size();
   }

   public int getInsideCount() {
      return inside == null ? 0 : inside.size();
   }

   @Override
   public String toString() {
      return super.toString() + "[" + x + "," + y + "," + inside + "]";
   }

   private IntList x;
   private IntList y;
   private IntList inside;
   // optimization
   Boolean xDimension, yDimension, insideDimension, xMeasure, yMeasure, insideMeasure;
   Boolean xDate, yDate, insideDate;
}
