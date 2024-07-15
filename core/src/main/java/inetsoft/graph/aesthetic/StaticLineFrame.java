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
import inetsoft.util.CoreTool;

/**
 * Static line frame defines a static line style for visual objects.
 * If a column is bound to this frame, and the value of the column is a GLine,
 * the value is used as the line for the row instead of the static line.
 *
 * @version 10.0
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=StaticLineFrame")
public class StaticLineFrame extends LineFrame {
   /**
    * Create a line frame.
    */
   public StaticLineFrame() {
      super();
   }

   /**
    * Create a static line frame with the specified line.
    */
   @TernConstructor
   public StaticLineFrame(GLine line) {
      setLine(line);
   }

   /**
    * Create a static line frame with the specified line style.
    * @param lineStyle a line style defined in GraphConstants.
    */
   public StaticLineFrame(int lineStyle) {
      this(new GLine(lineStyle));
   }

   /**
    * Create a line frame.
    * @param field field to get value to map to line styles.
    */
   public StaticLineFrame(String field) {
      this();
      setField(field);
   }

   /**
    * Get the line style of the line frame.
    */
   @TernMethod
   public GLine getLine() {
      return line;
   }

   /**
    * Set the line style of the line frame.
    */
   @TernMethod
   public void setLine(GLine line) {
      this.line = line;
   }

   /**
    * Get the line style for negative values.
    */
   @TernMethod
   public GLine getNegativeLine() {
      return negline;
   }

   /**
    * Set the line style for negative values. If this line is not set,
    * the regular line is used for all values.
    */
   @TernMethod
   public void setNegativeLine(GLine negline) {
      this.negline = negline;
   }

   /**
    * Check if equals another object.
    */
   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      return CoreTool.equals(((StaticLineFrame) obj).line, line) &&
         CoreTool.equals(((StaticLineFrame) obj).negline, negline);
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
   @TernMethod
   @Override
   public String getTitle() {
      throw new RuntimeException("Unsupported method called!");
   }

   /**
    * Static frame never shows legend.
    * @return false
    */
   @TernMethod
   @Override
   public boolean isVisible() {
      return false;
   }

   /**
    * Get the line for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public GLine getLine(DataSet data, String col, int row) {
      Object val = (getField() != null) ? data.getData(getField(), row) : col;
      return getLine(val);
   }

   /**
    * Get the line for the specified value.
    */
   @Override
   @TernMethod
   public GLine getLine(Object val) {
      if(negline != null && val instanceof Number) {
         if(((Number) val).doubleValue() < 0) {
            return negline;
         }
      }
      else {
         String field = getField();

         if(field != null) {
            if(val instanceof Number) {
               return new GLine(((Number) val).intValue());
            }
            else if(val instanceof GLine) {
               return (GLine) val;
            }
         }
      }

      return line;
   }

   @Override
   @TernMethod
   public String getUniqueId() {
      return line != null ? "StaticLineFrame:" + line.getStyle() : super.getUniqueId();
   }

   private GLine line = GLine.THIN_LINE;
   private GLine negline = null;
   private static final long serialVersionUID = 1L;
}
