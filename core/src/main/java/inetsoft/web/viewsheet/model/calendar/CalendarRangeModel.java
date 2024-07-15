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
package inetsoft.web.viewsheet.model.calendar;


public class CalendarRangeModel {
   public CalendarRangeModel() {
   }

   public CalendarRangeModel(String[] ranges, boolean yearView) {
      if(ranges != null && ranges.length == 2) {
         if(ranges[0] != null) {
            String[] minRange = ranges[0].split("-");
            minYear = Integer.parseInt(minRange[0]);
            minMonth = Integer.parseInt(minRange[1]);

            if(!yearView && minRange.length > 2) {
               minDay = Integer.parseInt(minRange[2]);
            }
         }

         if(ranges[1] != null) {
            String[] maxRange = ranges[1].split("-");
            maxYear = Integer.parseInt(maxRange[0]);
            maxMonth = Integer.parseInt(maxRange[1]);

            if(!yearView && maxRange.length > 2) {
               maxDay = Integer.parseInt(maxRange[2]);
            }
         }
      }
   }

   public int getMinYear() {
      return minYear;
   }

   public int getMinMonth() {
      return minMonth;
   }

   public int getMinDay() {
      return minDay;
   }

   public int getMaxYear() {
      return maxYear;
   }

   public int getMaxMonth() {
      return maxMonth;
   }

   public int getMaxDay() {
      return maxDay;
   }

   private int minYear = -1;
   private int minMonth = -1;
   private int minDay = -1;
   private int maxYear = -1;
   private int maxMonth = -1;
   private int maxDay = -1;
}
