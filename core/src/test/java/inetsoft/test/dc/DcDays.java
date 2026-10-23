/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.test.dc;

import inetsoft.report.filter.ConditionGroup;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The days a Date Comparison keeps: the whole condition list from
 * {@link DateComparisonInfo#getDateComparisonConditions}, evaluated with the real
 * {@link ConditionGroup} on one day of each stretch where the answer cannot change.
 */
public final class DcDays {
   private DcDays() {
   }

   /**
    * The days the comparison keeps. The condition list has to be BETWEEN ranges on the date
    * only, so the bounds of the ranges are the exact window to scan; anything else fails here
    * instead of being missed by the scan.
    */
   public static List<LocalDate> kept(DateComparisonInfo dc) {
      return kept(dc, LocalDate.of(2015, 1, 1), LocalDate.of(2023, 12, 31));
   }

   /**
    * @param from the first day to scan when the condition list has no range, which keeps every
    *             day of the data.
    * @param to   the last day to scan then.
    */
   public static List<LocalDate> kept(DateComparisonInfo dc, LocalDate from, LocalDate to) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Order Date"));
      ref.setDataType(XSchema.DATE);
      ConditionList conditions = dc.getDateComparisonConditions(ref);
      assertNotNull(conditions);

      // The days are midnights and every item is a BETWEEN on the date, so whether a day is kept
      // can only change at a bound of a range (or the day after it). Between two such points the
      // answer is the same for every day: evaluate the real condition list once per stretch.
      TreeSet<LocalDate> points = new TreeSet<>();

      for(int i = 0; i < conditions.getSize(); i++) {
         if(conditions.getConditionItem(i) == null) {
            continue; // a junction
         }

         assertTrue(conditions.getXCondition(i) instanceof AssetCondition,
                    "not a range condition: " + conditions.getXCondition(i));
         AssetCondition condition = (AssetCondition) conditions.getXCondition(i);
         assertEquals(XCondition.BETWEEN, condition.getOperation(), "not a BETWEEN condition");

         for(Object value : condition.getValues()) {
            assertTrue(value instanceof Date, "range bound " + value);
            LocalDate day = DcPeriods.toLocal((Date) value);
            points.add(day);
            points.add(day.plusDays(1));
         }
      }

      if(points.isEmpty()) {
         points.add(from);
         points.add(to.plusDays(1));
      }

      ConditionGroup group = new ConditionGroup(conditions);
      List<LocalDate> days = new ArrayList<>();
      LocalDate stretch = null;

      for(LocalDate point : points) {
         if(stretch != null && group.evaluate(new Object[] { DcPeriods.toDate(stretch) })) {
            for(LocalDate day = stretch; day.isBefore(point); day = day.plusDays(1)) {
               days.add(day);
            }
         }

         stretch = point;
      }

      return days;
   }

   /**
    * Whether the comparison keeps a value of the date column, evaluated on the real condition
    * list. Unlike {@link #kept} the value can be any instant, as a date-time column holds.
    */
   public static Predicate<Date> keeps(DateComparisonInfo dc) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Order Date"));
      ref.setDataType(XSchema.DATE);
      ConditionGroup group = new ConditionGroup(dc.getDateComparisonConditions(ref));

      return value -> group.evaluate(new Object[] { value });
   }

   /** The same days found by evaluating the condition list on every day, to check {@link #kept}. */
   public static List<LocalDate> keptDayByDay(DateComparisonInfo dc, LocalDate from, LocalDate to) {
      Predicate<Date> keeps = keeps(dc);
      List<LocalDate> days = new ArrayList<>();

      for(LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
         if(keeps.test(DcPeriods.toDate(day))) {
            days.add(day);
         }
      }

      return days;
   }
}
