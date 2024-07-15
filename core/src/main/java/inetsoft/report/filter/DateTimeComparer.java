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
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.CoreTool;
import inetsoft.util.Tool;

import java.text.DateFormat;
import java.util.Date;

/**
 * Date comparison. This implements date object comparison.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 * @author AndyX
 */
public class DateTimeComparer implements Comparer {
   /**
    * Create a default date comparer. The objects being compared must
    * be Date objects.
    */
   public DateTimeComparer() {
   }

   /**
    * Create a date comparer that parses strings according to the date
    * format specified by the pattern.
    * @param pattern date format.
    */
   public DateTimeComparer(String pattern) {
      fmt = Tool.createDateFormat(pattern);
   }

   /**
    * Create a date comparer that parses strings using the date format
    * object.
    * @param fmt date format.
    */
   public DateTimeComparer(DateFormat fmt) {
      this.fmt = fmt;
   }

   /**
    * {@inheritDoc}
    */
   @Override
   public int compare(Object v1, Object v2) {
      if(v1 == v2) {
         return 0;
      }
      else if(v1 == null) {
         return -1;
      }
      else if(v2 == null) {
         return 1;
      }

      Date d1 = getDate(v1);
      Date d2 = getDate(v2);

      if(d1 != null && d2 != null) {
         if(d1.getClass() != d2.getClass()) {
            long result = d1.getTime() - d2.getTime();

            return result > 0 ? 1 : result < 0 ? -1 : 0;
         }

         return d1.compareTo(d2);
      }

      return v1.toString().compareTo(v2.toString());
   }

   private Date getDate(Object v1) {
      Date d1;

      if(v1 instanceof Date) {
         d1 = (Date) v1;
      }
      else {
         try {
            d1 = fmt.parse(v1.toString());
         }
         catch(Exception e) {
            String value = v1.toString();

            if(CoreTool.isDate(value) || value.startsWith("{ts") || value.startsWith("{d")) {
               d1 = (Date) AbstractCondition.getObject(XSchema.TIME_INSTANT, v1.toString());
            }
            else {
               d1 = null;
            }
         }
      }

      return d1;
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

   DateFormat fmt = Tool.createDateFormat("yyyy-MM-dd HH:mm:ss");
}
