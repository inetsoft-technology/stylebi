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
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Verifies the quarter ranges generated for a "quarter to date" date comparison interval
 * over yearly standard periods.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonIntervalConditionTest {
   /**
    * When the last period is a completed year, every quarter of that year must get an
    * interval condition, even when the end day itself falls in an early quarter. Otherwise
    * the quarters after the end day are silently dropped from the chart and crosstab.
    */
   @Test
   void completedLastPeriodCoversAllItsQuarters() {
      List<String> starts = intervalRangeStarts("2023-04-07", false);

      Assertions.assertEquals(
         List.of("2021-01-01", "2021-04-01", "2021-07-01", "2021-10-01",
                 "2022-01-01", "2022-04-01", "2022-07-01", "2022-10-01"),
         starts);
   }

   /**
    * When the last period is the in-progress year, the iteration must stop at the quarter
    * containing the end day rather than running out to the end of the year, which would
    * add empty future quarters to the axis.
    */
   @Test
   void inProgressLastPeriodStopsAtEndDay() {
      List<String> starts = intervalRangeStarts("2023-04-07", true);

      Assertions.assertEquals(
         List.of("2021-01-01", "2021-04-01", "2021-07-01", "2021-10-01",
                 "2022-01-01", "2022-04-01", "2022-07-01", "2022-10-01",
                 "2023-01-01", "2023-04-01"),
         starts);
   }

   /**
    * Collect, in ascending order, the start date of every interval condition produced for a
    * "previous 2 years / quarter to date / by quarter" comparison ending on the given day.
    */
   private List<String> intervalRangeStarts(String endDay, boolean inclusive) {
      DateComparisonInfo dc = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setPreCountValue("2");
      periods.setDateLevelValue(String.valueOf(XConstants.YEAR_DATE_GROUP));
      periods.setToDate(false);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(endDay);
      periods.setInclusive(inclusive);
      dc.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setLevelValue(String.valueOf(DateComparisonInfo.QUARTER_TO_DATE));
      interval.setGranularityValue(String.valueOf(DateComparisonInfo.QUARTER));
      interval.setContextLevelValue(String.valueOf(XConstants.QUARTER_DATE_GROUP));
      interval.setEndDayAsToDate(true);
      interval.setIntervalEndDateValue(endDay);
      dc.setDateComparisonInterval(interval);
      dc.setComparisonOption(DateComparisonInfo.VALUE);

      ColumnRef ref = new ColumnRef(new AttributeRef(null, "Order Date"));
      ref.setDataType(XSchema.DATE);

      ConditionList conditions = dc.getDateComparisonConditions(ref);
      Assertions.assertNotNull(conditions);

      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
      List<String> starts = new ArrayList<>();

      for(int i = 0; i < conditions.getSize(); i++) {
         Object item = conditions.getItem(i);

         // level 0 is the overall period range; the per-quarter intervals are at level 1
         if(item instanceof ConditionItem && ((ConditionItem) item).getLevel() == 1) {
            List<?> values = ((AssetCondition) ((ConditionItem) item).getXCondition()).getValues();
            starts.add(format.format((Date) values.get(0)));
         }
      }

      Collections.sort(starts);

      return starts;
   }
}
