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
package inetsoft.uql.viewsheet;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * DateColumnOption stores column options for FormRef.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class DateColumnOption extends ColumnOption {
   /**
    * Constructor.
    */
   public DateColumnOption() {
   }

   /**
    * Constructor.
    */
   public DateColumnOption(String maximum, String minimum,
                           String msg, boolean form)
   {
      this.maximum = maximum;
      this.minimum = minimum;
      this.msg = msg;
      this.form = form;
   }

   @Override
   public String getErrorMessage() {
      String msg = getMessage();

      if(msg == null || msg.isEmpty()) {
         msg = Catalog.getCatalog().getString("viewer.valueIsOutsideOfRange",
                                              minimum, maximum);
      }

      return msg;
   }

   /**
    * Get column option type.
    */
   @Override
   public String getType() {
      return ColumnOption.DATE;
   }

   /**
    * Get maximum.
    */
   public String getMax() {
      return maximum;
   }

   /**
    * Get minimum.
    */
   public String getMin() {
      return minimum;
   }

   /**
    * Check whether the value is invalid by the range setting.
    */
   @Override
   public boolean validate(Object val) {
      if(val == null || "".equals(val)) {
         return true;
      }

      try {
         Date dateValue = null;

         if(val instanceof Date) {
            dateValue = (Date) val;
         }
         else {
            dateValue = Tool.parseDate(val.toString());
         }

         if(dateValue == null) {
            return false;
         }

         long time = dateValue.getTime();

         if(maximum != null && time > Tool.parseDate(maximum).getTime() ||
            minimum != null && time < Tool.parseDate(minimum).getTime())
         {
            return false;
         }
         else {
            return true;
         }
      }
      catch(Exception ex) {
         return false;
      }
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(maximum != null) {
         writer.print(" maximum=\"{d '" + maximum + "'}\"");
      }

      if(minimum != null) {
         writer.print(" minimum=\"{d '" + minimum + "'}\"");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      String str = Tool.getAttribute(tag, "maximum");
      maximum = str != null ? dformat.format(Tool.dateFmt.get().parse(str)) : null;
      str = Tool.getAttribute(tag, "minimum");
      minimum = str != null ? dformat.format(Tool.dateFmt.get().parse(str)) : null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof DateColumnOption) || !super.equals(obj)) {
         return false;
      }

      DateColumnOption opt = (DateColumnOption) obj;
      return Tool.equals(maximum, opt.maximum) &&
             Tool.equals(minimum, opt.minimum);
   }

   private String maximum;
   private String minimum;
   private static SimpleDateFormat dformat = new SimpleDateFormat("yyyy-MM-dd");
}
