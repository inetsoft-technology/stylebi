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
package inetsoft.uql.tabular;

import inetsoft.uql.schema.XSchema;

/**
 * Data types.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public enum DataType {
   /**
    * Indicates that the values in the column are character strings.
    *
    * @see XSchema#STRING
    */
   STRING(XSchema.STRING),

   /**
    * Indicates that the values in the column are a single characters.
    *
    * @see XSchema#CHARACTER
    */
   CHAR(XSchema.CHARACTER),

   /**
    * Indicates that the values in the column are integers.
    *
    * @see XSchema#INTEGER
    */
   INTEGER(XSchema.INTEGER),

   /**
    * Indicates that the values in the column are single bytes (8-bit
    * integers).
    *
    * @see XSchema#BYTE
    */
   BYTE(XSchema.BYTE),

   /**
    * Indicates that the values in the column are short integers.
    *
    * @see XSchema#SHORT
    */
   SHORT(XSchema.SHORT),

   /**
    * Indicates that the values in the column are long integers.
    *
    * @see XSchema#LONG
    */
   LONG(XSchema.LONG),

   /**
    * Indicates that the values in the column are floating point numbers.
    *
    * @see XSchema#FLOAT
    */
   FLOAT(XSchema.FLOAT),

   /**
    * Indicates that the values in the column are double precision floating
    * point numbers.
    *
    * @see XSchema#DOUBLE
    */
   DOUBLE(XSchema.DOUBLE),

   /**
    * Indicates that the values in the column are boolean (true/false).
    *
    * @see XSchema#BOOLEAN
    */
   BOOLEAN(XSchema.BOOLEAN),

   /**
    * Indicates that the values in the column are dates.
    *
    * @see XSchema#DATE
    */
   DATE(XSchema.DATE),

   /**
    * Indicates that the values in the column are times.
    *
    * @see XSchema#TIME
    */
   TIME(XSchema.TIME),

   /**
    * Indicates the the values in the column are date-time values.
    *
    * @see XSchema#TIME_INSTANT
    */
   TIME_INSTANT(XSchema.TIME_INSTANT);

   private final String type;

   /**
    * Creates a new instance of <tt>DataType</tt>.
    *
    * @param type the XSchema type string.
    */
   DataType(String type) {
      this.type = type;
   }

   /**
    * Gets the type string for this data type.
    *
    * @return the type.
    */
   public String type() {
      return type;
   }

   /**
    * Gets the data type that corresponds to the specified type string.
    *
    * @param type the type string.
    *
    * @return the matching data type.
    */
   public static DataType fromType(String type) {
      DataType result = null;

      for(DataType dataType : values()) {
         if(dataType.type.equals(type)) {
            result = dataType;
            break;
         }
      }

      return result;
   }
}