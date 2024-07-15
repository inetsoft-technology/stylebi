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
package inetsoft.report.filter;

import inetsoft.report.Comparer;
import inetsoft.uql.AbstractCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;

import java.text.DateFormat;
import java.util.Date;

/**
 * Date comparison. This implements date object comparison.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DateComparer implements Comparer {
   /**
    * Create a default date comparer. The objects being compared must
    * be Date objects.
    */
   public DateComparer() {
   }

   /**
    * Create a date comparer that parses strings according to the date
    * format specified by the pattern.
    * @param pattern date format.
    */
   public DateComparer(String pattern) {
      fmt = Tool.createDateFormat(pattern);
   }

   /**
    * Create a date comparer that parses strings using the date format
    * object.
    * @param fmt date format.
    */
   public DateComparer(DateFormat fmt) {
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

      Date d1 = null;
      Date d2 = null;

      if(v1 instanceof Date) {
         d1 = (Date) v1;
      }
      else {
         try {
            d1 = fmt.parse(v1.toString());
         }
         catch(Exception e) {
            d1 = (Date) AbstractCondition.getObject(XSchema.DATE, v1.toString());
         }
      }

      if(v2 instanceof Date) {
         d2 = (Date) v2;
      }
      else {
         try {
            d2 = fmt.parse(v2.toString());
         }
         catch(Exception e) {
            d2 = (Date) AbstractCondition.getObject(XSchema.DATE, v2.toString());
         }
      }

      if(d1 instanceof Date && d2 instanceof Date) {
         if(d1 instanceof java.sql.Time && d2 instanceof java.sql.Time) {
            int rc = d1.getHours() - d2.getHours();

            if(rc == 0) {
               rc = d1.getMinutes() - d2.getMinutes();

               if(rc == 0) {
                  rc = d1.getSeconds() - d2.getSeconds();
               }
            }

            return rc;
         }

         if(d1 instanceof java.sql.Time && d2 instanceof java.sql.Time) {
            int rc = d1.getHours() - d2.getHours();

            if(rc == 0) {
               rc = d1.getMinutes() - d2.getMinutes();

               if(rc == 0) {
                  rc = d1.getSeconds() - d2.getSeconds();
               }
            }

            return rc;
         }
         else if((d1 instanceof java.sql.Date || d2 instanceof java.sql.Date) &&
                 // calling getYear() throws exception in Time
                 !(d1 instanceof java.sql.Time || d2 instanceof java.sql.Time))
         {

            int rc = d1.getYear() - d2.getYear();

            if(rc == 0) {
               rc = d1.getMonth() - d2.getMonth();

               if(rc == 0) {
                  rc = d1.getDate() - d2.getDate();
               }
            }

            return rc;
         }

         // ignore ms
         long t1 = d1.getTime();
         long t2 = d2.getTime();
         t1 = t1 - (t1 % 1000);
         t2 = t2 - (t2 % 1000);
         long rc = t1 - t2;

         return (rc == 0) ? 0 : (int) (rc / Math.abs(rc));
      }

      return v1.toString().compareTo(v2.toString());
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

   private DateFormat fmt = Tool.createDateFormat("yyyy-MM-dd");
}
