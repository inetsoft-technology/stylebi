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
import inetsoft.util.CoreTool;

import java.text.Format;
import java.text.MessageFormat;
import java.util.Arrays;

/**
 * This text frame extracts text value from multiple columns of a dataset, such as
 * candle or boxplot graphs.
 *
 * @version 11.2
 * @author InetSoft Technology
 */
@TernClass(url = "#cshid=MultiTextFrame")
public class MultiTextFrame extends TextFrame implements MultiFieldFrame {
   public MultiTextFrame() {
   }

   @TernConstructor
   public MultiTextFrame(String ...fields) {
      this.fields = fields;
   }

   /**
    * Set the column associated with this frame.
    */
   @Override
   @TernMethod
   public void setField(String field) {
      if(fields != null && fields.length > 0) {
         fields[0] = field;
      }
      else {
         fields = new String[] {field};
      }
   }

   /**
    * Get the column associated with this frame.
    */
   @Override
   @TernMethod
   public String getField() {
      return (fields != null && fields.length > 0) ? fields[0] : null;
   }

   /**
    * Set the fields for the stems.
    */
   @TernMethod
   public void setFields(String... fields) {
      this.fields = fields;
   }

   /**
    * Get the fields for getting the stems.
    */
   @TernMethod
   public String[] getFields() {
      return fields;
   }

   /**
    * Set the message for formatting multiple values into a text.
    */
   @TernMethod
   public void setMessageFormat(MessageFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * Get the message for formatting multiple values into a text.
    */
   @TernMethod
   public MessageFormat getMessageFormat() {
      return fmt;
   }

   /**
    * Get the format used to format individual values to create a concatenated string.
    */
   public Format getValueFormat() {
      return valueFormat;
   }

   /**
    * Set the format used to format individual values to create a concatenated string.
    */
   public void setValueFormat(Format valueFormat) {
      this.valueFormat = valueFormat;
   }

   /**
    * Set the delimiter used to join multiple values.
    */
   @TernMethod
   public void setDelimiter(String delim) {
      this.delim = delim;
   }

   /**
    * Get the delimiter used to join multiple values.
    */
   @TernMethod
   public String getDelimiter() {
      return delim;
   }

   /**
    * Get the fields for extracting text values.
    */
   @TernMethod
   protected String[] getTextFields() {
      return fields;
   }

   /**
    * Get the text for the specified cell.
    * @param data the specified dataset.
    * @param col the specified column name.
    * @param row the specified row index.
    */
   @Override
   public Object getText(DataSet data, String col, int row) {
      if(!isTextVisible(data, row)) {
         return null;
      }

      String[] fields = getTextFields();

      if(fields == null) {
         return super.getText(data, col, row);
      }

      Object[] vals = new Object[fields.length];

      for(int i = 0; i < vals.length; i++) {
         vals[i] = data.getData(fields[i], row);
      }

      return getText(vals);
   }

   public boolean equals(Object obj) {
      if(!super.equals(obj)) {
         return false;
      }

      MultiTextFrame frame = (MultiTextFrame) obj;

      return CoreTool.equals(fmt, frame.fmt) &&
         CoreTool.equals(fields, frame.fields);
   }

   /**
    * Get the text for the specified values.
    */
   @TernMethod
   protected Object getText(Object[] vals) {
      if(vals == null) {
         return null;
      }

      if(fmt != null) {
         try {
            return fmt.format(null2Strs(vals));
         }
         catch(IllegalArgumentException ex) {
            // value could be missing (null) and cause error if the sub-format is number. (61915)
         }
      }
      else if(vals.length == 1) {
         return formatValue2(getText(vals[0]));
      }

      StringBuilder buf = new StringBuilder();

      for(int i = 0; i < vals.length; i++) {
         if(i > 0) {
            buf.append(delim);
         }

         buf.append(null2Str(vals[i]));
      }

      return buf.toString();
   }

   private Object formatValue2(Object v) {
      if(valueFormat != null && v != null) {
         try {
            return valueFormat.format(v);
         }
         catch(Exception ex) {
            // ignore
         }
      }

      return v;
   }

   // don't pass null to MessageFormat. display empty string instead of 'null'.
   private Object[] null2Strs(Object[] vals) {
      return Arrays.stream(vals).map(a -> null2Str(a)).toArray();
   }

   private Object null2Str(Object val) {
      return val == null ? "" : formatValue2(val);
   }

   private MessageFormat fmt;
   private Format valueFormat;
   private String[] fields; // columns for getting text fields
   private String delim = ",";

   private static final long serialVersionUID = 1L;
}
