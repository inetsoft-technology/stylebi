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
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77318: since #77010, Year / Week to Date / Week has a WeekOfYear temp group, so
 * getAllTempDateGroupRef() also appended an auxiliary QuarterOfYear group. The chart query
 * grouped by it, so a week spanning two quarters became two rows and the change was computed
 * against half a week. The temp groups must not contain the auxiliary QuarterOfYear.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonUtilAllTempDateGroupRefTest {
   @Test
   void yearWeekToDateWeekHasNoQuarterOfYearGroup() {
      List<String> names = tempGroupNames(XConstants.YEAR_DATE_GROUP,
         DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.WEEK, dateRef(-1));

      assertEquals(List.of("WeekOfYear(date)"), names);
   }

   @Test
   void yearWeekToDateWeekOnWeekOfYearPartDimHasNoQuarterOfYearGroup() {
      List<String> names = tempGroupNames(XConstants.YEAR_DATE_GROUP,
         DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.WEEK,
         dateRef(DateRangeRef.WEEK_OF_YEAR_PART));

      assertFalse(names.isEmpty(), "fixture: expected a temp group");
      assertNoQuarterOfYear(names);
   }

   @Test
   void yearWeekToDateDayHasNoQuarterOfYearGroup() {
      List<String> names = tempGroupNames(XConstants.YEAR_DATE_GROUP,
         DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.DAY, dateRef(-1));

      assertFalse(names.isEmpty(), "fixture: expected a temp group");
      assertNoQuarterOfYear(names);
   }

   /** Bug #77010: Quarter / Week to Date / Week keeps its WeekOfQuarter temp group. */
   @Test
   void quarterWeekToDateWeekKeepsWeekOfQuarterGroup() {
      List<String> names = tempGroupNames(XConstants.QUARTER_DATE_GROUP,
         DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.WEEK, dateRef(-1));

      assertEquals(List.of("WeekOfQuarter(date)"), names);
   }

   private static List<String> tempGroupNames(int periodLevel, int intervalLevel,
                                              int granularity, VSDimensionRef dateDim)
   {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(periodLevel);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(false);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);
      interval.setContextLevel(XConstants.WEEK_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      XDimensionRef[] refs =
         DateComparisonUtil.getAllTempDateGroupRef(dcInfo, "ds", dateDim, new Viewsheet());
      List<String> names = new ArrayList<>();

      for(XDimensionRef ref : refs) {
         names.add(ref.getFullName());
      }

      return names;
   }

   private static VSDimensionRef dateRef(int dateLevel) {
      AttributeRef attr = new AttributeRef("date");
      attr.setDataType(XSchema.DATE);
      VSDimensionRef ref = new VSDimensionRef(attr);

      if(dateLevel >= 0) {
         ref.setDateLevelValue(dateLevel + "");
      }

      return ref;
   }

   private static void assertNoQuarterOfYear(List<String> names) {
      for(String name : names) {
         assertFalse(name.startsWith("QuarterOfYear("), "unexpected group " + name);
      }
   }
}
