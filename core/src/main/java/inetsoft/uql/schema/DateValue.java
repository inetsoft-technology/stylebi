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
package inetsoft.uql.schema;

import inetsoft.uql.asset.ExpressionValue;
import inetsoft.util.Tool;

import java.text.*;
import java.util.Date;

/**
 * Date value node.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DateValue extends XValueNode {
   /**
    * Create a date value node.
    */
   public DateValue() {
   }

   /**
    * Create a date value node.
    */
   public DateValue(String name) {
      super(name);
   }

   /**
    * Create a date value node.
    */
   public DateValue(String name, Object val) {
      this(name);
      setValue(val);
   }

   /**
    * Get the type of this node. The types are defined in XSchema class.
    */
   @Override
   public String getType() {
      return XSchema.DATE;
   }

   /**
    * Convert the value to a string.
    */
   @Override
   public String format() {
      return isExpression() ?
         ((ExpressionValue) getValue()).getExpression() : format.format((Date) getValue());
   }

   /**
    * Parse the string to the value.
    */
   @Override
   public void parse0(String str) throws ParseException {
      setValue((str.length() == 0) ?
         null :
         (new java.sql.Date(format.parse(str).getTime())));
   }

   /**
    * Set the format for formatting and parsing string values.
    */
   @Override
   public void setFormat(Format fmt) {
      this.format = (DateFormat) fmt;
   }

   public static final DateFormat DEFAULT =
      Tool.createDateFormat(DateType.DEFAULT);
   DateFormat format = DEFAULT;
}

