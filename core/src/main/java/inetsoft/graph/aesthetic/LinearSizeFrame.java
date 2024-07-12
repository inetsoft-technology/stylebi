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

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;

import java.lang.reflect.Method;

/**
 * This class defines a size frame for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=LinearSizeFrame")
public class LinearSizeFrame extends SizeFrame {
   /**
    * Create a size frame.
    */
   public LinearSizeFrame() {
   }

   /**
    * Create a size frame.
    * @param field field to get value to map to sizes.
    */
   @TernConstructor
   public LinearSizeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get a size for the specified value.
    */
   @Override
   @TernMethod
   public double getSize(Object val) {
      Scale scale = getScale();

      if(scale == null) {
         return getSize(1);
      }

      double v = scale.map(val);

      if(Double.isNaN(v)) {
         return getSmallest();
      }

      double min = scale.getMin();
      double max = scale.getMax();
      return getSize(Math.min(1, (v - min) / (max - min)));
   }

   /**
    * Get the size for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public double getSize(DataSet data, String col, int row) {
      Object obj = data.getData(getField(), row);
      return getSize(obj);
   }

   /**
    * Get a size at the relative scale.
    * @param ratio a value from 0 to 1.
    */
   private double getSize(double ratio) {
      return getSmallest() + (getLargest() - getSmallest()) * ratio;
   }

   /**
    * Legend is always visible.
    */
   @Override
   boolean isMultiItem(Method getter) throws Exception {
      return true;
   }

   private static final long serialVersionUID = 1L;
}
