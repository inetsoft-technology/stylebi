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

import com.inetsoft.build.tern.*;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.scale.Scale;

import java.lang.reflect.Method;

/**
 * This class defines a line frame for numeric values.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=LinearLineFrame")
public class LinearLineFrame extends LineFrame {
   /**
    * Create a line frame.
    */
   public LinearLineFrame() {
   }

   /**
    * Create a line frame.
    * @param field field to get value to map to line styles.
    */
   @TernConstructor
   public LinearLineFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get a line at the relative scale.
    * @param ratio a value from 0 to 1.
    */
   protected GLine getLine(double ratio) {
      double percent = MIN_RATIO + (MAX_RATIO - MIN_RATIO) * ratio;
      return new GLine((int) (percent * 15), 1);
   }

   /**
    * Get a line for the specified value.
    */
   @Override
   @TernMethod
   public GLine getLine(Object val) {
      Scale scale = getScale();

      if(scale == null) {
         return getLine(1);
      }

      double v = scale.map(val);

      if(Double.isNaN(v)) {
         return null;
      }

      double min = scale.getMin();
      double max = scale.getMax();

      return getLine(Math.min(1, (v - min) / (max - min)));
   }

   /**
    * Get the line for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public GLine getLine(DataSet data, String col, int row) {
      Object obj = data.getData(getField(), row);
      return getLine(obj);
   }

   /**
    * Legend is always visible.
    */
   @Override
   boolean isMultiItem(Method getter) throws Exception {
      return true;
   }

   private static final double MIN_RATIO = 0.2;
   private static final double MAX_RATIO = 0.9;
   private static final long serialVersionUID = 1L;
}
