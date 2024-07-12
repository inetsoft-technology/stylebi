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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.graph.*;

import java.util.Comparator;
import java.util.Map;

public class ChartRefComparator implements Comparator<ChartRef> {
   public ChartRefComparator(Map<String, Integer> cardinalities) {
      this.cardinalities = cardinalities;
   }

   // dimension before measure
   // date before no-date
   // date sort according to level
   // string dim according to cardinality
   @Override
   public int compare(ChartRef a, ChartRef b) {
      boolean am = a instanceof VSChartAggregateRef;
      boolean bm = b instanceof VSChartAggregateRef;
      boolean datea = XSchema.isDateType(a.getDataType());
      boolean dateb = XSchema.isDateType(b.getDataType());

      if(am && bm) {
         return 0;
      }
      else if(am && !bm) {
         return 1;
      }
      else if(!am && bm) {
         return -1;
      }
      else if(!am && !bm) {
         if(datea && dateb) {
            return compareLevel(a, b);
         }

         if(datea && !dateb) {
            return -1;
         }

         if(!datea && dateb) {
            return 1;
         }

         return compareCardinality(a, b);
      }

      return 1;
   }

   private int compareCardinality(ChartRef a, ChartRef b) {
      int c1 = getCardinality(a);
      int c2 = getCardinality(b);

      if(c1 < c2) {
         return -1;
      }
      else if(c1 == c2) {
         return 0;
      }

      return 1;
   }

   private Integer getCardinality(ChartRef ref) {
      if(cardinalities == null) {
         return 0;
      }

      Integer val = cardinalities.get(ref.getFullName());
      return val != null ? val : 0;
   }

   private int compareLevel(ChartRef a, ChartRef b) {
      int c1 = getLevelValue(a);
      int c2 = getLevelValue(b);

      if(c1 < c2) {
         return -1;
      }
      else if(c1 == c2) {
         return 0;
      }

      return 1;
   }

   // Sort date col by year/quarter/month/day/hour/minute/second
   private int getLevelValue(ChartRef ref) {
      int level;

      try {
         level = Integer.parseInt(((VSChartDimensionRef) ref).getDateLevelValue());
      }
      catch(NumberFormatException e) {
         level = 0;
      }

      if(level == DateRangeRef.YEAR_INTERVAL) {
         return 1;
      }
      else if(level == DateRangeRef.QUARTER_INTERVAL ||
              level == DateRangeRef.QUARTER_OF_YEAR_PART)
      {
         return 2;
      }
      else if(level == DateRangeRef.MONTH_INTERVAL || level == DateRangeRef.MONTH_OF_YEAR_PART) {
         return 3;
      }
      else if(level == DateRangeRef.DAY_INTERVAL) {
         return 4;
      }
      else if(level == DateRangeRef.HOUR_INTERVAL) {
         return 5;
      }
      else if(level == DateRangeRef.MINUTE_INTERVAL) {
         return 6;
      }

      return level;
   }

   private Map<String, Integer> cardinalities;
}
