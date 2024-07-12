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
package inetsoft.report.script.formula;

import inetsoft.report.filter.DefaultComparer;
import inetsoft.util.script.CalcDateTime;

import java.util.*;

/**
 * Compare monthname or weekday name in date order.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class DatePartComparer extends DefaultComparer {
   /**
    * Get a comparer for month name.
    */
   public static DefaultComparer getMonthNameComparer() {
      return new DatePartComparer(monthmap);
   }
   
   /**
    * Get a comparer for weekday name.
    */
   public static DefaultComparer getWeekdayNameComparer() {
      return new DatePartComparer(weekdaymap);
   }

   private DatePartComparer(Map namemap) {
      this.namemap = namemap;
   }
   
   /**
    * This method should return > 0 if v1 is greater than v2, 0 if
    * v1 is equal to v2, or < 0 if v1 is less than v2.
    * It must handle null values for the comparison values.
    * @param v1 comparison value.
    * @param v2 comparison value.
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   @Override
   public int compare(Object v1, Object v2) {
      Integer n1 = (Integer) namemap.get(v1);
      Integer n2 = (Integer) namemap.get(v2);

      return super.compare(n1, n2);
   }

   private Map namemap;

   private static Map monthmap = new Hashtable(); // name -> seq#
   private static Map weekdaymap = new Hashtable(); // name -> seq#

   private static final int[] months = {
      Calendar.JANUARY, Calendar.FEBRUARY, Calendar.MARCH, Calendar.APRIL,
      Calendar.MAY, Calendar.JUNE, Calendar.JULY, Calendar.AUGUST,
      Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER, 
      Calendar.DECEMBER
   };

   private static final int[] weekdays = {
      Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
      Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
   };

   static {
      GregorianCalendar cal = new GregorianCalendar();

      for(int i = 0; i < months.length; i++) {
         cal.set(Calendar.MONTH, months[i]);
         monthmap.put(CalcDateTime.monthname(cal.getTime()), Integer.valueOf(i));
      }

      for(int i = 0; i < weekdays.length; i++) {
         cal.set(Calendar.DAY_OF_WEEK, weekdays[i]);
         weekdaymap.put(CalcDateTime.weekdayname(cal.getTime()),Integer.valueOf(i));
      }
   }
}

