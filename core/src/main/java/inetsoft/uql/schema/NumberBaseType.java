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

import java.text.DecimalFormat;

/**
 * Base type for all numeric types.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NumberBaseType extends XTypeNode {
   /**
    * Create a number type node.
    */
   public NumberBaseType() {
   }

   /**
    * Create a number type node.
    */
   public NumberBaseType(String name) {
      super(name);
   }

   /**
    * Return true if this is a primitive type.
    */
   @Override
   public boolean isPrimitive() {
      return true;
   }

   /**
    * Check if this type is a numeric type.
    */
   @Override
   public boolean isNumber() {
      return true;
   }

   /**
    * Check if two types are compatible. If two types are compatible, the
    * values of one value node can be assigned to the value of another
    * value node.
    */
   @Override
   public boolean isCompatible(XTypeNode type) {
      return type.isNumber();
   }

   /**
    * Apply format to a value object.
    * by this type.
    */
   protected void applyFormat(XValueNode node) {
      if(fmt != null) {
         if(fmtObj == null) {
            fmtObj = new DecimalFormat(fmt);
         }

         node.setFormat(fmtObj);
      }
   }

   /**
    * Set the format string for the type. The meaning of the format
    * depends on the data type. For example, for date related formats,
    * the format string is used to construct a SimpleDateFormat
    * object.
    */
   @Override
   public void setFormat(String fmt) {
      // @by jamshedd to reinstantiate the date format object
      // with the new format
      fmtObj = fmt == null ? null : new DecimalFormat(fmt);
      this.fmt = fmt;
   }

   /**
    * Get the format string of this data type.
    */
   @Override
   public String getFormat() {
      return fmt;
   }

   String fmt = null;
   transient DecimalFormat fmtObj; // cached
}

