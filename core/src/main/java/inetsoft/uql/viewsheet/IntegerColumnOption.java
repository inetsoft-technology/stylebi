/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.uql.viewsheet;

import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import org.w3c.dom.Element;

import java.io.PrintWriter;

/**
 * IntegerColumnOption stores column options for FormRef.
 *
 * @version 11.2
 * @author InetSoft Technology Corp
 */
public class IntegerColumnOption extends ColumnOption {
   /**
    * Constructor.
    */
   public IntegerColumnOption() {
   }

   /**
    * Constructor.
    */
   public IntegerColumnOption(Integer maximum, Integer minimum,
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
      return ColumnOption.INTEGER;
   }

   /**
    * Get maximum.
    */
   public int getMax() {
      return maximum == null ? Integer.MAX_VALUE : maximum;
   }

   /**
    * Get minimum.
    */
   public int getMin() {
      return minimum == null ? Integer.MIN_VALUE : minimum;
   }

   /**
    * Check whether the value is invalid by the range setting.
    */
   @Override
   public boolean validate(Object val) {
      if(val == null || "".equals(val)) {
         return true;
      }

      Integer intValue = Tool.getIntegerData(val);

      if(intValue == null) {
         return false;
      }

      if((maximum != null && intValue > maximum) ||
         (minimum != null && intValue < minimum))
      {
         return false;
      }
      else {
         return true;
      }
   }

   @Override
   protected void writeAttributes(PrintWriter writer) {
      super.writeAttributes(writer);

      if(maximum != null) {
         writer.print(" maximum=\"" + maximum + "\"");
      }

      if(minimum != null) {
         writer.print(" minimum=\"" + minimum + "\"");
      }
   }

   @Override
   protected void parseAttributes(Element tag) throws Exception {
      super.parseAttributes(tag);

      String str = Tool.getAttribute(tag, "maximum");
      maximum = str != null ? Integer.valueOf(str) : null;
      str = Tool.getAttribute(tag, "minimum");
      minimum = str != null ? Integer.valueOf(str) : null;
   }

   @Override
   public boolean equals(Object obj) {
      if(!(obj instanceof IntegerColumnOption) || !super.equals(obj)) {
         return false;
      }

      IntegerColumnOption opt = (IntegerColumnOption) obj;
      return Tool.equals(maximum, opt.maximum) &&
             Tool.equals(minimum, opt.minimum);
   }

   private Integer maximum = null;
   private Integer minimum = null;
}
