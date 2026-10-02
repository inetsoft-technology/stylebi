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
package inetsoft.uql.viewsheet.graph;

import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77176: drives the real ChartDcProcessor.process() for a date comparison chart and
 * checks the calculator installed on the measure. For the equal-level shapes the info's temp
 * list must still hold the part dimension (#77010 merge-part wrap), while the change
 * calculator's lookup ignore list must not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartDcProcessorProcessCalcTempGroupsTest {
   @Test
   void quarterWeekToDateWeekKeepsPartDimInLookup() {
      Result r = process(XConstants.QUARTER_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                         DateComparisonInfo.WEEK, XConstants.WEEK_DATE_GROUP);

      assertTrue(r.partName.startsWith("WeekOfQuarter("), r.partName);
      assertTrue(r.infoTemps.contains(r.partName), "info temps " + r.infoTemps);
      assertFalse(r.calcTemps.contains(r.partName), "calc temps " + r.calcTemps);
   }

   @Test
   void yearSameQuarterQuarterKeepsPartDimInLookup() {
      Result r = process(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.SAME_QUARTER,
                         DateComparisonInfo.QUARTER, XConstants.QUARTER_DATE_GROUP);

      assertTrue(r.partName.startsWith("QuarterOfYear("), r.partName);
      assertTrue(r.infoTemps.contains(r.partName), "info temps " + r.infoTemps);
      assertFalse(r.calcTemps.contains(r.partName), "calc temps " + r.calcTemps);
   }

   /**
    * Bug #77318: no auxiliary QuarterOfYear temp group, so a week spanning two quarters is
    * not split into two rows and the lookup has nothing left to ignore.
    */
   @Test
   void yearWeekToDateWeekHasNoQuarterOfYearTemp() {
      Result r = process(XConstants.YEAR_DATE_GROUP, DateComparisonInfo.WEEK_TO_DATE,
                         DateComparisonInfo.WEEK, XConstants.WEEK_DATE_GROUP);

      assertEquals(List.of(r.partName), r.infoTemps);
      assertEquals(List.of(), r.calcTemps);
   }

   private record Result(String partName, List<String> infoTemps, List<String> calcTemps) {
   }

   private static Result process(int periodLevel, int intervalLevel, int granularity,
                                 int context)
   {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(periodLevel);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);
      interval.setContextLevel(context);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);

      AttributeRef dateAttr = new AttributeRef("date");
      dateAttr.setDataType(XSchema.DATE);
      VSChartDimensionRef dateDim = new VSChartDimensionRef(dateAttr);
      dateDim.setDateLevel(XConstants.YEAR_DATE_GROUP);
      dateDim.setDataType(XSchema.DATE);

      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef("v"));
      measure.setColumnValue("v");

      VSChartInfo info = new VSChartInfo();
      info.setRTXFields(new ChartRef[] { dateDim });
      info.setRTYFields(new ChartRef[] { measure });

      new ChartDcProcessor(info, dcInfo).process("ds", new Viewsheet());

      ValueOfCalc calc = null;

      for(ChartRef[] fields : new ChartRef[][] { info.getRTXFields(), info.getRTYFields() }) {
         for(ChartRef ref : fields) {
            if(ref instanceof VSChartAggregateRef aref &&
               aref.getCalculator() instanceof ValueOfCalc vcalc)
            {
               calc = vcalc;
            }
         }
      }

      assertNotNull(calc, "process() must install a ValueOfCalc on the measure");
      assertNotNull(info.getDateComparisonRef());

      return new Result(dateDim.getFullName(), names(Arrays.asList(info.getDcTempGroups())),
                        names(calc.getDcTempGroups()));
   }

   private static List<String> names(List<XDimensionRef> refs) {
      List<String> names = new ArrayList<>();

      for(XDimensionRef ref : refs) {
         names.add(ref == null ? null : ref.getFullName());
      }

      return names;
   }
}
