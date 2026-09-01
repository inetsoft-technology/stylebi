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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.XDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regression test for Bug #76388: a chart with Date Comparison = Standard Periods
 * (dateLevel = MONTH), Interval level = SAME_WEEK, granularity = DAY, contextLevel = MONTH
 * (the "Month_SameWeek" reported configuration) produced a spurious calculated
 * {@code WeekOfMonth(Date)} temp grouping field via
 * {@link DateComparisonInfo#getTempDateGroupRef(String, Viewsheet, VSDataRef)}. That field
 * was folded into the chart's real GROUP BY columns (see {@code ChartVSAQuery}), even though
 * the interval's granularity is DAY and the legitimate axis field is already
 * {@code DayOfWeek(Date)} -- a per-weekday axis has no use for a month-boundary week
 * disambiguator.
 *
 * <p>Root cause: {@code DateComparisonInfo.getIntervalTempDateGroupRef()}'s
 * {@code contextLevel == MONTH_DATE_GROUP} branch gated purely on
 * {@code (intervalLevel & WEEK) == WEEK}, which is true for both {@code SAME_WEEK} and
 * {@code WEEK_TO_DATE} regardless of the chosen granularity, never consulting
 * {@code granularity} itself.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonInfoTempDateGroupRefTest {
   /**
    * Bug #76388's exact reported configuration: Standard Periods (dateLevel = MONTH),
    * Interval level = SAME_WEEK, granularity = DAY, contextLevel = MONTH. The temp group
    * refs used to expand/group the query must not include a WeekOfMonth(...) calculated
    * field -- day-of-week granularity has no use for one, and its presence is what
    * manufactures a spurious GROUP BY column that drops facet groups from the result.
    */
   @Test
   void sameWeekIntervalWithDayGranularityDoesNotProduceWeekOfMonthTempGroup() {
      XDimensionRef[] tempRefs = getTempDateGroupRefs(
         XConstants.MONTH_DATE_GROUP, DateComparisonInfo.SAME_WEEK, DateComparisonInfo.DAY,
         XConstants.MONTH_DATE_GROUP);

      assertNoWeekOfMonthTempGroup(tempRefs);
   }

   /**
    * Same defect, other affected interval level: WEEK_TO_DATE with DAY granularity in a
    * MONTH context must not manufacture a WeekOfMonth(...) temp group either.
    */
   @Test
   void weekToDateIntervalWithDayGranularityDoesNotProduceWeekOfMonthTempGroup() {
      XDimensionRef[] tempRefs = getTempDateGroupRefs(
         XConstants.MONTH_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.DAY,
         XConstants.MONTH_DATE_GROUP);

      assertNoWeekOfMonthTempGroup(tempRefs);
   }

   /**
    * Legitimate config check: when the granularity really is WEEK, the top-level guard in
    * {@code getIntervalTempDateGroupRef()} (```if((intervalLevel & granularity) == granularity)
    * return null;```) already short-circuits before ever reaching the MONTH_DATE_GROUP branch
    * this fix touches, for both SAME_WEEK and WEEK_TO_DATE (their interval level bitmask always
    * contains the WEEK bit). So granularity == WEEK configs must remain unaffected -- no temp
    * group is produced before or after this fix.
    */
   @Test
   void sameWeekIntervalWithWeekGranularityProducesNoTempGroupEitherWay() {
      XDimensionRef[] tempRefs = getTempDateGroupRefs(
         XConstants.MONTH_DATE_GROUP, DateComparisonInfo.SAME_WEEK, DateComparisonInfo.WEEK,
         XConstants.MONTH_DATE_GROUP);

      assertEquals(0, tempRefs.length,
         "granularity == WEEK should already be filtered out by the method's own early guard");
   }

   private static void assertNoWeekOfMonthTempGroup(XDimensionRef[] tempRefs) {
      boolean hasWeekOfMonth = Arrays.stream(tempRefs)
         .anyMatch(ref -> ref.getFullName() != null && ref.getFullName().startsWith("WeekOfMonth("));

      assertFalse(hasWeekOfMonth,
         "expected no WeekOfMonth(...) temp date group ref, got: " + Arrays.toString(tempRefs));
   }

   private static XDimensionRef[] getTempDateGroupRefs(int contextLevel, int intervalLevel,
                                                         int granularity, int periodDateLevel)
   {
      Viewsheet vs = new Viewsheet();

      DateComparisonInfo dc = new DateComparisonInfo();

      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevelValue(String.valueOf(periodDateLevel));
      periods.setPreCountValue("2");
      dc.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevelValue(String.valueOf(intervalLevel));
      interval.setGranularityValue(String.valueOf(granularity));
      interval.setContextLevelValue(String.valueOf(contextLevel));
      dc.setDateComparisonInterval(interval);

      VSDimensionRef ref = new VSDimensionRef(new AttributeRef(null, "Date"));
      ref.setDataType(XSchema.DATE);

      return dc.getTempDateGroupRef("Orders", vs, ref);
   }
}
