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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The days a Date Comparison keeps: the whole condition list from
 * {@link DateComparisonInfo#getDateComparisonConditions}, evaluated with the real
 * {@link ConditionGroup} on every day of a window.
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

      LocalDate lo = null;
      LocalDate hi = null;

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
            lo = lo == null || day.isBefore(lo) ? day : lo;
            hi = hi == null || day.isAfter(hi) ? day : hi;
         }
      }

      List<LocalDate> days = new ArrayList<>();

      if(lo == null) {
         lo = from;
         hi = to;
      }

      ConditionGroup group = new ConditionGroup(conditions);

      for(LocalDate day = lo; !day.isAfter(hi); day = day.plusDays(1)) {
         if(group.evaluate(new Object[] { DcPeriods.toDate(day) })) {
            days.add(day);
         }
      }

      return days;
   }
}
