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
package inetsoft.report.internal.binding;

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * Formula Field for date grouping.
 *
 * @version 10.2
 * @author InetSoft Technology Corp
 */
public class DateRangeField extends FormulaField {
   /**
    * Constructor.
    */
   public DateRangeField() {
      super();
   }

   /**
    * Constructor.
    * @param name the specified name.
    */
   public DateRangeField(String name, int option) {
      super(name);
      this.option = option;
   }

   /**
    * Get the date option.
    * @return the date option.
    */
   public int getDateOption() {
      return option;
   }

   /**
    * Set the date option, one of the option constants defined in this class.
    * @param option the specified date option.
    */
   public void setDateOption(int option) {
      this.option = option;
   }

   /**
    * Check if this expression is sql expression.
    * @return true if is, false otherwise.
    */
   @Override
   public boolean isSQL() {
      return true;
   }

   /**
    * Get the SQL expression of this reference.
    * @return a SQL expression.
    */
   @Override
   public String getExpression() {
      String type = getDBType();

      if(type == null || type.length() == 0) {
         return getScriptExpression();
      }

      DataRef ref = getOriginalField();

      while(ref instanceof FormulaField) {
         ref = ((FormulaField) ref).getOriginalField();
      }

      if(ref != null) {
         String otype = ref.getDataType();

         if(XSchema.TIME.equals(otype)) {
            return DateRangeRef.getExpression(type, getOriginalField(), option , otype);
         }
      }

      return DateRangeRef.getExpression(type, getOriginalField(), option);
   }

   /**
    * Get the script expression of this reference.
    * @return a script expression.
    */
   @Override
   public String getScriptExpression() {
      return DateRangeRef.getScriptExpression(getOriginalField(), option);
   }

   /**
    * Check if expression is editable.
    * @return <tt>true</tt> if editable, <tt>false</tt> otherwise.
    */
   @Override
   public boolean isExpressionEditable() {
      return false;
   }

   /**
    * Write the attributes of this object.
    * @param writer the output stream to which to write the XML data.
    */
   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);
      writer.print(" dateOption=\"" + option + "\"");
   }

   /**
    * Read in the attribute of this object from an XML tag.
    * @param tag the XML element representing this object.
    */
   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);
      option = Integer.parseInt(Tool.getAttribute(tag, "dateOption"));
   }

   private int option;
}
