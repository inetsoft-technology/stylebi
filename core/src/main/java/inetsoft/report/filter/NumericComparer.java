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
package inetsoft.report.filter;

import inetsoft.report.Comparer;
import inetsoft.util.Tool;
import org.jnumbers.NumberParser;

import java.util.Date;

/**
 * Numeric comparison. The compared values must be in valid numeric format.
 * <p>
 * Values that are neither numbers nor parsable as numbers sort first and are
 * equal to each other. {@link Date} values (including {@code java.sql.Date},
 * {@code java.sql.Time} and {@code java.sql.Timestamp}) sort after those values
 * and before all numeric values, and are ordered among themselves by
 * {@link Date#getTime()}. Keeping dates in their own rank makes this a total
 * order, so it is safe to use with {@code Arrays.sort} on mixed input.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class NumericComparer implements Comparer {
   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(Object v1, Object v2) {
      boolean date1 = v1 instanceof Date;
      boolean date2 = v2 instanceof Date;

      if(date1 || date2) {
         if(date1 && date2) {
            return Long.compare(((Date) v1).getTime(), ((Date) v2).getTime());
         }

         return date1 ? -compareToDate(v2) : compareToDate(v1);
      }

      double val1 = Double.NaN;
      double val2 = Double.NaN;

      try {
         val1 = (v1 instanceof Number) ? ((Number) v1).doubleValue() :
            NumberParser.getDouble(v1.toString());
      }
      catch(Exception ex) {
         v1 = null;
      }

      try {
         val2 = (v2 instanceof Number) ? ((Number) v2).doubleValue() :
            NumberParser.getDouble(v2.toString());
      }
      catch(Exception ex) {
         v2 = null;
      }

      if(v1 == null) {
         return (v2 == null) ? 0 : -1;
      }
      else if(v2 == null) {
         return (v1 == null) ? 0 : 1;
      }

      return Double.compare(val1, val2);
   }

   /**
    * Compare a value that is not a date to a date. Numeric values sort after
    * dates, and any other value (including null) sorts before dates.
    */
   private static int compareToDate(Object v) {
      if(v instanceof Number) {
         return 1;
      }

      try {
         NumberParser.getDouble(v.toString());
         return 1;
      }
      catch(Exception ex) {
         return -1;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(double v1, double v2) {
      if(v1 == Tool.NULL_DOUBLE || v2 == Tool.NULL_DOUBLE) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_DOUBLE ? -1 : 1);
      }

      double val = v1 - v2;

      if(val < NEGATIVE_DOUBLE_ERROR) {
         return -1;
      }
      else if(val > POSITIVE_DOUBLE_ERROR) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(float v1, float v2) {
      if(v1 == Tool.NULL_FLOAT || v2 == Tool.NULL_FLOAT) {
         return v1 == v2 ? 0 : (v1 == Tool.NULL_FLOAT ? -1 : 1);
      }

      float val = v1 - v2;

      if(val < NEGATIVE_FLOAT_ERROR) {
         return -1;
      }
      else if(val > POSITIVE_FLOAT_ERROR) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(long v1, long v2) {
      if(v1 < v2) {
         return -1;
      }
      else if(v1 > v2) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(int v1, int v2) {
      if(v1 < v2) {
         return -1;
      }
      else if(v1 > v2) {
         return 1;
      }
      else {
         return 0;
      }
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(short v1, short v2) {
      if(v1 < v2) {
         return -1;
      }
      else if(v1 > v2) {
         return 1;
      }
      else {
         return 0;
      }
   }
}
