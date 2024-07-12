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
import inetsoft.uql.CompositeValue;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Static size frame defines a static size for visual objects.
 * If a column is bound to this frame, and the value of the column is a number,
 * the value is used as the size for the row instead of the static size.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=StaticSizeFrame")
public class StaticSizeFrame extends SizeFrame {
   /**
    * Create a size frame.
    */
   public StaticSizeFrame() {
      super();
   }

   /**
    * Create a static size frame with the specified size.
    */
   public StaticSizeFrame(int size) {
      this.size.setValue((double) size, CompositeValue.Type.DEFAULT);
   }

   /**
    * Create a line frame.
    * @param field field to get value to map to line styles.
    */
   @TernConstructor
   public StaticSizeFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the size of the static size frame.
    */
   @TernMethod
   public double getSize() {
      return this.size.get();
   }

   /**
    * Set the size of the static size frame.
    * @param size size
    */
   @TernMethod
   public void setSize(double size) {
      setSize(size, CompositeValue.Type.USER);
   }

   /**
    * Set the size of the static size frame.
    * @param size size
    * @param type the type of value to set: DEFAULT, CSS, USER
    */
   public void setSize(double size, CompositeValue.Type type) {
      this.size.setValue(size, type);
   }

   /**
    * Get the size for negative values.
    */
   @TernMethod
   public double getNegativeSize() {
      return negsize;
   }

   /**
    * Set the size for negative values. If this size is not set, the regular
    * size is used for all values.
    */
   @TernMethod
   public void setNegativeSize(double negsize) {
      this.negsize = negsize;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      return Tool.equals(((StaticSizeFrame) obj).size, size);
   }

   /**
    * Get the size for the specified cell.
    * @param data the specified dataset.
    * @param col the name of the specified column.
    * @param row the specified row index.
    */
   @Override
   public double getSize(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getSize(val);
   }

   /**
    * Get the size for the specified value.
    */
   @Override
   @TernMethod
   public double getSize(Object val) {
      if(!Double.isNaN(negsize) && val instanceof Number) {
         if(((Number) val).doubleValue() < 0) {
            return negsize;
         }
      }
      else {
         String field = getField();

         if(field != null) {
            if(val instanceof Number) {
               return ((Number) val).doubleValue();
            }
         }
      }

      return size.get();
   }

   /**
    * Get the values mapped by this frame.
    */
   @Override
   @TernMethod
   public Object[] getValues() {
      return null;
   }

   /**
    * Get the title to show on the legend.
    */
   @Override
   @TernMethod
   public String getTitle() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Static frame never shows legend.
    * @return false
    */
   @Override
   @TernMethod
   public boolean isVisible() {
      return false;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      return super.toString() + "[size:" + size +",from:" + getSmallest() +
         ",to:" + getLargest() + ",max:" + getMax() + "]";
   }

   public CompositeValue<Double> getSizeCompositeValue() {
      return size;
   }

   public void resetCompositeValues(CompositeValue.Type type) {
      size.resetValue(type);
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return "StaticSizeFrame:" + getSize();
   }

   @Override
   public Object clone() {
      try {
         StaticSizeFrame frame = (StaticSizeFrame) super.clone();
         frame.size = (CompositeValue<Double>) size.clone();
         return frame;
      }
      catch(Exception ex) {
         LOG.error("Failed to clone static size frame", ex);
         return null;
      }
   }

   private CompositeValue<Double> size = new CompositeValue<>(Double.class, 1d, true);
   private double negsize = Double.NaN;
   private static final long serialVersionUID = 1L;
   private static final Logger LOG = LoggerFactory.getLogger(StaticSizeFrame.class);
}
