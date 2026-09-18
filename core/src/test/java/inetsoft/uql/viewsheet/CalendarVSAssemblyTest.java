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
package inetsoft.uql.viewsheet;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.Condition;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for bug #76748: a double-calendar period comparison must build each
 * period's date-cell array as one contiguous [start,end) range, not OR the boundary dates
 * together as independent single days -- while the unrelated single-calendar OR'd-days caller
 * must keep its adjacency-merge-only behavior unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CalendarVSAssemblyTest {
   private static DataRef dateRef() {
      ColumnRef ref = new ColumnRef(new AttributeRef("Date"));
      ref.setDataType(XSchema.DATE);
      return ref;
   }

   private static String asMinValue(ConditionList conds, int itemIndex) {
      Condition cond = conds.getConditionItem(itemIndex).getCondition();
      return cond.getValue(0).toString();
   }

   @Test
   void periodComparisonBuildsAContiguousRangePerPeriodInsteadOfOringTheBoundaryDates() {
      CalendarVSAssembly assembly = new CalendarVSAssembly(new Viewsheet(), "Calendar1");
      CalendarVSAssemblyInfo info = assembly.getCalendarInfo();
      info.setViewModeValue(CalendarVSAssemblyInfo.DOUBLE_CALENDAR_MODE);
      info.setPeriod(true);
      // period 1: April 1 & April 15, 2023 (non-adjacent) -- period 2: May 1 & May 16, 2023
      info.setDates(new String[] {
         "d2023-3-1", "d2023-3-15",
         "d2023-4-1", "d2023-4-16"
      });

      DataRef ref = dateRef();
      ConditionList conds = assembly.getConditionList(new DataRef[] { ref });

      // each period must collapse to exactly one (greater, AND, less) triple -- a single
      // contiguous range -- not multiple OR'd single-day conditions per period.
      assertEquals(7, conds.getSize(), "expected 2 periods x 3 items + 1 OR junction");

      assertEquals("2023-04-01", asMinValue(conds, 0), "period 1 start must be the earliest date in its cells");
      assertEquals("2023-04-16", asMinValue(conds, 2), "period 1 end must be the ceiling of the latest date in its cells, covering every day in between");

      assertEquals("2023-05-01", asMinValue(conds, 4), "period 2 start must be the earliest date in its cells");
      assertEquals("2023-05-17", asMinValue(conds, 6), "period 2 end must be the ceiling of the latest date in its cells, covering every day in between");
   }

   @Test
   void singleCalendarNonAdjacentDaysStayOredAsIndependentDays() {
      // April 1 and April 3, 2023 -- not calendar-adjacent, must stay as 2 OR'd single days.
      String[] dates = { "d2023-3-1", "d2023-3-3" };
      ConditionList conds = CalendarVSAssembly.createConditionList(dates, 0, dates.length, dateRef());

      assertEquals(7, conds.getSize(), "non-adjacent days must remain 2 OR'd (greater,AND,less) triples");
      assertEquals("2023-04-01", asMinValue(conds, 0));
      assertEquals("2023-04-02", asMinValue(conds, 2));
      assertEquals("2023-04-03", asMinValue(conds, 4));
      assertEquals("2023-04-04", asMinValue(conds, 6));
   }

   @Test
   void singleCalendarAdjacentDaysStillMergeIntoOneRange() {
      // April 1 and April 2, 2023 -- calendar-adjacent (April 1's end == April 2's start),
      // must still merge into a single contiguous range, unchanged by this fix.
      String[] dates = { "d2023-3-1", "d2023-3-2" };
      ConditionList conds = CalendarVSAssembly.createConditionList(dates, 0, dates.length, dateRef());

      assertEquals(3, conds.getSize(), "calendar-adjacent days must merge into a single (greater,AND,less) range");
      assertEquals("2023-04-01", asMinValue(conds, 0));
      assertEquals("2023-04-03", asMinValue(conds, 2));
   }
}
