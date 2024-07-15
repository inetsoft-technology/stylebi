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
package inetsoft.graph.internal;

import inetsoft.report.filter.DefaultComparer;
import inetsoft.report.filter.SortOrder;
import inetsoft.util.Tool;

import java.util.Comparator;

/**
 * This Comparator class supports custom first day of the week when
 * handling the "DayOfWeek" group sorting.
 *
 * @author InetSoft Technology Corp
 * @version 12.1
 */
public class FirstDayComparator extends DefaultComparer
   implements Comparator {
   private FirstDayComparator() {
      this.firstDay = Tool.getFirstDayOfWeek();
   }

   /**
    * Constructor for Crosstabs.
    *
    * @param comp SortOrder object passed in when handling
    *             Crosstabs.
    */
   public FirstDayComparator(SortOrder comp) {
      this();
      this.comp = comp;
   }

   /**
    * Constructor for Charts.
    *
    * @param comp2 Comparator object passed in when handling
    *              Charts.
    */
   public FirstDayComparator(Comparator comp2) {
      this();
      this.comp2 = comp2;
   }

   /**
    * This method should be called when doing an comparison on the
    * Integer value which represents the DayOfWeek and when the user
    * wants Monday to represent the first day of the week. To accomplish
    * this, the Method treats Sunday's Integer value greater then any other
    * day of the week.
    *
    * @param v1 comparison value.
    * @param v2 comparison value.
    *
    * @return < 0, 0, or > 0 for v1<v2, v1==v2, or v1>v2.
    */
   @Override
   public int compare(Object v1, Object v2) {
      if(v1 != null && v2 != null &&
         v1 instanceof Number && v2 instanceof Number)
      {
         double d1 = ((Number) v1).doubleValue();
         double d2 = ((Number) v2).doubleValue();

         if(d1 < firstDay) {
            d1 = 7 + d1;
         }
         if(d2 < firstDay) {
            d2 = 7 + d2;
         }

         if(comp != null) {
            return ((SortOrder) comp).isDesc() ?
               ((d1 > d2) ? -1 : (d1 == d2 ? 0 : 1)) :
               ((d1 > d2) ? 1 : (d1 == d2 ? 0 : -1));
         }
         else if(comp2 != null) {
            return ((DefaultComparer) comp2).isNegate() ?
               (d1 > d2) ? -1 : (d1 == d2 ? 0 : 1) :
               (d1 > d2) ? 1 : (d1 == d2 ? 0 : -1);
         }
      }

      return 0;
   }

   private SortOrder comp;
   private Comparator comp2;
   private int firstDay;
}

