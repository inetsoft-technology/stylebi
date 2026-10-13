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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78408: drives the real {@code ChartDcProcessor}/{@code CrosstabDcProcessor} and then
 * {@code DateComparisonService.describeRetargetedDimension}, so the disclosure is checked against
 * the runtime shelf the processors actually build. When the comparison adds a period dimension
 * under the bound dimension's name, the disclosure used to report that period dimension's level
 * as the bound dimension's new grouping, and printed DC-only level codes ("2055 (was 0)").
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DateComparisonServiceRetargetProcessorTest {
   /** The reported shape: period month, interval all, granularity week, bound at none. */
   @Test
   void chartReportsTheBoundDimensionNotTheFullWeekPeriodDimension() throws Exception {
      Map<String, Object> out = chart(XConstants.NONE_DATE_GROUP, standard(
         XConstants.MONTH_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.WEEK,
         XConstants.MONTH_DATE_GROUP);

      assertRetarget(out, "none", "week_of_month");
   }

   @Test
   void crosstabReportsTheBoundDimensionNotTheFullWeekPeriodDimension() throws Exception {
      Map<String, Object> out = crosstab(XConstants.NONE_DATE_GROUP, standard(
         XConstants.MONTH_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.WEEK,
         XConstants.MONTH_DATE_GROUP);

      assertRetarget(out, "none", "week_of_month");
   }

   /** Period year, granularity month: the bound dimension becomes month_of_year, not year. */
   @Test
   void chartReportsMonthOfYearRatherThanThePeriodLevel() throws Exception {
      Map<String, Object> out = chart(XConstants.MONTH_DATE_GROUP, standard(
         XConstants.YEAR_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.MONTH,
         XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "month", "month_of_year");
   }

   @Test
   void crosstabReportsMonthOfYearRatherThanThePeriodLevel() throws Exception {
      Map<String, Object> out = crosstab(XConstants.MONTH_DATE_GROUP, standard(
         XConstants.YEAR_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.MONTH,
         XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "month", "month_of_year");
   }

   /** Was a false negative: the period level equals the design level, so nothing was reported. */
   @Test
   void chartReportsARetargetWhenThePeriodLevelEqualsTheDesignLevel() throws Exception {
      Map<String, Object> out = chart(XConstants.YEAR_DATE_GROUP, standard(
         XConstants.YEAR_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.QUARTER,
         XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "year", "quarter_of_year");
   }

   /** A DC calc part keeps getDateLevel() at year, so it must be named from the calc. */
   @Test
   void chartNamesADcCalcPartFromTheCalc() throws Exception {
      Map<String, Object> out = chart(XConstants.MONTH_DATE_GROUP, standard(
         XConstants.QUARTER_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.DAY,
         XConstants.QUARTER_DATE_GROUP);

      assertRetarget(out, "month", "day_of_quarter");
   }

   /** Custom periods: the period dimension is a named-group clone at the design level. */
   @Test
   void chartReportsACustomPeriodsRetarget() throws Exception {
      Map<String, Object> out = chart(XConstants.YEAR_DATE_GROUP, custom(),
         DateComparisonInfo.ALL, DateComparisonInfo.MONTH, XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "year", "month");
   }

   @Test
   void crosstabReportsACustomPeriodsRetarget() throws Exception {
      Map<String, Object> out = crosstab(XConstants.NONE_DATE_GROUP, custom(),
         DateComparisonInfo.ALL, DateComparisonInfo.WEEK, XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "none", "week");
   }

   /**
    * Custom periods on a bound dimension that already has its own named group: both the bound
    * and the period dimension carry a named group, and the bound one must still be reported.
    */
   @Test
   void chartReportsACustomPeriodsRetargetOnANamedGroupDimension() throws Exception {
      VSChartDimensionRef designDim = dateDim(VSChartDimensionRef::new,
                                              XConstants.YEAR_DATE_GROUP);
      designDim.setNamedGroupInfo(namedGroup());
      VSChartInfo info = chartInfo(designDim, designDim.clone());
      new ChartDcProcessor(info, dcInfo(custom(), DateComparisonInfo.ALL,
                                        DateComparisonInfo.WEEK, XConstants.YEAR_DATE_GROUP))
         .process("ds", new Viewsheet());

      assertRetarget(describe(chartAssembly(info)), "year", "week");
   }

   @Test
   void crosstabReportsACustomPeriodsRetargetOnANamedGroupDimension() throws Exception {
      VSDimensionRef bound = dateDim(VSDimensionRef::new, XConstants.YEAR_DATE_GROUP);
      bound.setNamedGroupInfo(namedGroup());
      Map<String, Object> out = describe(crosstabAssembly(crosstabInfo(bound, dcInfo(
         custom(), DateComparisonInfo.ALL, DateComparisonInfo.WEEK, XConstants.YEAR_DATE_GROUP))));

      assertRetarget(out, "year", "week");
   }

   /** The date dimension on Y: the bound dimension is looked up on the Y shelves. */
   @Test
   void chartReportsARetargetWithTheDateDimensionOnY() throws Exception {
      VSChartDimensionRef designDim = dateDim(VSChartDimensionRef::new,
                                              XConstants.MONTH_DATE_GROUP);
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef("SALES"));
      measure.setColumnValue("SALES");
      VSChartInfo info = new VSChartInfo();
      info.addYField(designDim);
      info.addXField(measure);
      info.setRTYFields(new ChartRef[] { designDim.clone() });
      info.setRTXFields(new ChartRef[] { measure });
      new ChartDcProcessor(info, dcInfo(standard(XConstants.YEAR_DATE_GROUP),
         DateComparisonInfo.ALL, DateComparisonInfo.MONTH, XConstants.YEAR_DATE_GROUP))
         .process("ds", new Viewsheet());

      assertRetarget(describe(chartAssembly(info)), "month", "month_of_year");
   }

   /** A timestamp bound at a time level: the design level is named, not printed as a code. */
   @Test
   void chartNamesATimeLevelDesignDimension() throws Exception {
      AttributeRef attr = new AttributeRef("ORDER_DATE");
      attr.setDataType(XSchema.TIME_INSTANT);
      VSChartDimensionRef designDim = new VSChartDimensionRef(attr);
      designDim.setDateLevel(XConstants.HOUR_DATE_GROUP);
      designDim.setDataType(XSchema.TIME_INSTANT);
      VSChartInfo info = chartInfo(designDim, designDim.clone());
      new ChartDcProcessor(info, dcInfo(standard(XConstants.MONTH_DATE_GROUP),
         DateComparisonInfo.ALL, DateComparisonInfo.WEEK, XConstants.MONTH_DATE_GROUP))
         .process("ds", new Viewsheet());

      assertRetarget(describe(chartAssembly(info)), "hour", "week_of_month");
   }

   /**
    * Granularity all: the period dimension replaces the bound one, so it is what that slot now
    * renders, and only the "0" needed a word.
    */
   @Test
   void chartReportsTheReplacingPeriodDimensionForGranularityAll() throws Exception {
      Map<String, Object> out = chart(XConstants.NONE_DATE_GROUP, standard(
         XConstants.YEAR_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.ALL,
         XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "none", "year");
   }

   @Test
   void crosstabReportsTheReplacingPeriodDimensionForGranularityAll() throws Exception {
      Map<String, Object> out = crosstab(XConstants.NONE_DATE_GROUP, standard(
         XConstants.YEAR_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.ALL,
         XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "none", "year");
   }

   /** Control: no period dimension; this shape was already reported correctly. */
   @Test
   void chartStillReportsARetargetWithoutAPeriodDimension() throws Exception {
      Map<String, Object> out = chart(XConstants.YEAR_DATE_GROUP, standard(
         XConstants.MONTH_DATE_GROUP), DateComparisonInfo.ALL, DateComparisonInfo.MONTH,
         XConstants.YEAR_DATE_GROUP);

      assertRetarget(out, "year", "month");
   }

   /**
    * Sweeps the standard-period shapes through both processors. Whatever is disclosed never holds a
    * bare number, names the design level, and names the level of the bound runtime instance the
    * processor mutated (or of the period dimension that replaced it). A bound dimension whose word
    * changed is always disclosed.
    */
   @Test
   void sweepNeverPrintsACodeAndAlwaysDescribesTheBoundDimension() throws Exception {
      int[] designs = { XConstants.NONE_DATE_GROUP, XConstants.DAY_DATE_GROUP,
                        XConstants.MONTH_DATE_GROUP, XConstants.YEAR_DATE_GROUP };
      int[] periodLevels = { XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP,
                             XConstants.MONTH_DATE_GROUP, XConstants.WEEK_DATE_GROUP,
                             XConstants.DAY_DATE_GROUP };
      int[] intervals = { DateComparisonInfo.ALL, DateComparisonInfo.YEAR_TO_DATE,
                          DateComparisonInfo.QUARTER_TO_DATE, DateComparisonInfo.MONTH_TO_DATE,
                          DateComparisonInfo.WEEK_TO_DATE, DateComparisonInfo.SAME_QUARTER,
                          DateComparisonInfo.SAME_MONTH, DateComparisonInfo.SAME_WEEK,
                          DateComparisonInfo.SAME_DAY };
      int[] granularities = { DateComparisonInfo.ALL, DateComparisonInfo.YEAR,
                              DateComparisonInfo.QUARTER, DateComparisonInfo.MONTH,
                              DateComparisonInfo.WEEK, DateComparisonInfo.DAY };
      int[] contexts = { XConstants.YEAR_DATE_GROUP, XConstants.QUARTER_DATE_GROUP,
                         XConstants.MONTH_DATE_GROUP, XConstants.WEEK_DATE_GROUP };
      int checked = 0;

      for(int design : designs) {
         for(int period : periodLevels) {
            for(int interval : intervals) {
               for(int granularity : granularities) {
                  for(int context : contexts) {
                     DateComparisonInfo probe =
                        dcInfo(standard(period), interval, granularity, context);

                     if(probe.invalid()) {
                        continue;
                     }

                     String shape = "design=" + design + " period=" + period + " interval=" +
                        interval + " granularity=" + granularity + " context=" + context;
                     checkChart(design, period, interval, granularity, context, shape);
                     checkCrosstab(design, period, interval, granularity, context, shape);
                     checked++;
                  }
               }
            }
         }
      }

      assertTrue(checked > 100, "only " + checked + " valid shapes");
   }

   /** The level-word mapping is total over the codes that reach the disclosure. */
   @Test
   void levelWordsNeverFallBackToANumber() throws Exception {
      Object[][] cases = {
         { XConstants.NONE_DATE_GROUP, "none" },
         { XConstants.YEAR_DATE_GROUP, "year" },
         { XConstants.WEEK_DATE_GROUP, "week" },
         { XConstants.HOUR_DATE_GROUP, "hour" },
         { XConstants.QUARTER_OF_YEAR_DATE_GROUP, "quarter_of_year" },
         { XConstants.MONTH_OF_YEAR_DATE_GROUP, "month_of_year" },
         { XConstants.DAY_OF_MONTH_DATE_GROUP, "day_of_month" },
         { XConstants.DAY_OF_WEEK_DATE_GROUP, "day_of_week" },
         { DateRangeRef.MONTH_OF_FULL_WEEK, "month (full weeks)" },
         { DateRangeRef.QUARTER_OF_FULL_WEEK, "quarter (full weeks)" },
         { DateRangeRef.YEAR_OF_FULL_WEEK, "year (full weeks)" },
         { DateRangeRef.MONTH_OF_FULL_WEEK_PART, null },
      };

      for(Object[] c : cases) {
         VSDimensionRef dim = dateDim(VSDimensionRef::new, (Integer) c[0]);
         assertEquals(c[1], levelWord(dim), "level " + c[0]);
      }
   }

   private void checkChart(int design, int period, int interval, int granularity, int context,
                           String shape) throws Exception
   {
      VSChartDimensionRef designDim = dateDim(VSChartDimensionRef::new, design);
      VSChartDimensionRef bound = designDim.clone();
      VSChartInfo info = chartInfo(designDim, bound);
      new ChartDcProcessor(info, dcInfo(standard(period), interval, granularity, context))
         .process("ds", new Viewsheet());
      Map<String, Object> out = describe(chartAssembly(info));
      ChartRef[] shelf = info.isDcBaseDateOnX() ? info.getRTXFields() : info.getRTYFields();
      checkDisclosure(out, designDim, bound, shelf, "chart " + shape);
   }

   private void checkCrosstab(int design, int period, int interval, int granularity, int context,
                              String shape) throws Exception
   {
      VSDimensionRef bound = dateDim(VSDimensionRef::new, design);
      VSDimensionRef designDim = bound.clone();
      VSCrosstabInfo info = crosstabInfo(bound, dcInfo(standard(period), interval, granularity,
                                                       context));
      Map<String, Object> out = describe(crosstabAssembly(info));
      DataRef[] shelf = info.isDateComparisonOnRow() ?
         info.getRuntimeRowHeaders() : info.getRuntimeColHeaders();
      checkDisclosure(out, designDim, bound, shelf, "crosstab " + shape);
   }

   private void checkDisclosure(Map<String, Object> out, VSDimensionRef designDim,
                                VSDimensionRef bound, DataRef[] shelf, String shape)
      throws Exception
   {
      for(Object value : out.values()) {
         assertFalse(String.valueOf(value).matches(".*\\d.*"), shape + " printed " + out);
      }

      VSDimensionRef rendered = null;

      // by identity: VSDimensionRef.equals() also matches the same-named period dimension
      if(Arrays.stream(shelf).anyMatch(ref -> ref == bound)) {
         rendered = bound;
      }
      else {
         // granularity all: the period dimension replaced the bound one
         for(DataRef ref : shelf) {
            if(ref instanceof VSDimensionRef dim && "ORDER_DATE".equals(dim.getName())) {
               rendered = dim;
            }
         }
      }

      if(rendered == null) {
         return;
      }

      String from = levelWord(designDim);
      String to = levelWord(rendered);

      if(from == null || to == null || from.equals(to)) {
         assertFalse(out.containsKey("retargetedDimension"), shape + " reported " + out);
      }
      else {
         assertEquals(Map.of("retargetedDimension", "ORDER_DATE", "retargetedFromLevel", from,
                             "retargetedToLevel", to), out, shape + " shelf " + describe(shelf));
      }
   }

   private static String describe(DataRef[] shelf) {
      StringBuilder text = new StringBuilder();

      for(DataRef ref : shelf) {
         text.append(ref instanceof VSDimensionRef dim ?
            dim.getFullName() + "/" + dim.getDateLevel() : String.valueOf(ref)).append(' ');
      }

      return text.toString();
   }

   private static void assertRetarget(Map<String, Object> out, String from, String to) {
      assertEquals("ORDER_DATE", out.get("retargetedDimension"), String.valueOf(out));
      assertEquals(from, out.get("retargetedFromLevel"), String.valueOf(out));
      assertEquals(to, out.get("retargetedToLevel"), String.valueOf(out));
   }

   private static Map<String, Object> chart(int design, DateComparisonPeriods periods,
                                            int interval, int granularity, int context)
      throws Exception
   {
      VSChartDimensionRef designDim = dateDim(VSChartDimensionRef::new, design);
      VSChartInfo info = chartInfo(designDim, designDim.clone());
      new ChartDcProcessor(info, dcInfo(periods, interval, granularity, context))
         .process("ds", new Viewsheet());
      return describe(chartAssembly(info));
   }

   private static Map<String, Object> crosstab(int design, DateComparisonPeriods periods,
                                               int interval, int granularity, int context)
      throws Exception
   {
      VSDimensionRef bound = dateDim(VSDimensionRef::new, design);
      return describe(crosstabAssembly(
         crosstabInfo(bound, dcInfo(periods, interval, granularity, context))));
   }

   private static VSChartInfo chartInfo(VSChartDimensionRef designDim, VSChartDimensionRef bound) {
      VSChartAggregateRef measure = new VSChartAggregateRef();
      measure.setDataRef(new AttributeRef("SALES"));
      measure.setColumnValue("SALES");

      VSChartInfo info = new VSChartInfo();
      info.addXField(designDim);
      info.addYField(measure);
      info.setRTXFields(new ChartRef[] { bound });
      info.setRTYFields(new ChartRef[] { measure });
      return info;
   }

   private static VSCrosstabInfo crosstabInfo(VSDimensionRef bound, DateComparisonInfo dcInfo) {
      VSAggregateRef measure = new VSAggregateRef();
      measure.setDataRef(new AttributeRef("SALES"));
      measure.setColumnValue("SALES");

      VSCrosstabInfo info = new VSCrosstabInfo();
      new CrosstabDcProcessor(info, dcInfo, new DataRef[] { bound }, new DataRef[0],
                              new DataRef[] { measure }).process("ds", new Viewsheet());
      return info;
   }

   private static <T extends VSDimensionRef> T dateDim(
      java.util.function.Function<DataRef, T> factory, int level)
   {
      AttributeRef attr = new AttributeRef("ORDER_DATE");
      attr.setDataType(XSchema.DATE);
      T dim = factory.apply(attr);
      dim.setDateLevel(level);
      dim.setDataType(XSchema.DATE);
      return dim;
   }

   private static StandardPeriods standard(int level) {
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(level);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      return periods;
   }

   private static CustomPeriods custom() throws Exception {
      SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
      CustomPeriods periods = new CustomPeriods();
      periods.setDatePeriods(new ArrayList<>(List.of(
         new DatePeriod(fmt.parse("2020-01-01"), fmt.parse("2020-06-30")),
         new DatePeriod(fmt.parse("2021-01-01"), fmt.parse("2021-06-30")))));
      return periods;
   }

   private static SNamedGroupInfo namedGroup() {
      SNamedGroupInfo group = new SNamedGroupInfo();
      group.setGroupValue("Early", new ArrayList<>(List.of("2020-01-01")));
      return group;
   }

   private static DateComparisonInfo dcInfo(DateComparisonPeriods periods, int intervalLevel,
                                            int granularity, int context)
   {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      dcInfo.setDateComparisonPeriods(periods);
      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);
      interval.setContextLevel(context);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);
      return dcInfo;
   }

   private static ChartVSAssembly chartAssembly(VSChartInfo info) {
      ChartVSAssembly assembly = mock(ChartVSAssembly.class);
      when(assembly.getVSChartInfo()).thenReturn(info);
      return assembly;
   }

   private static CrosstabVSAssembly crosstabAssembly(VSCrosstabInfo info) {
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSCrosstabInfo()).thenReturn(info);
      return assembly;
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> describe(VSAssembly assembly) throws Exception {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly("A1")).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);

      Method m = DateComparisonService.class.getDeclaredMethod(
         "describeRetargetedDimension", RuntimeViewsheet.class, String.class);
      m.setAccessible(true);
      return (Map<String, Object>) m.invoke(null, rvs, "A1");
   }

   private static String levelWord(VSDimensionRef dim) throws Exception {
      Method m = DateComparisonService.class.getDeclaredMethod(
         "dimensionLevelWord", VSDimensionRef.class);
      m.setAccessible(true);
      return (String) m.invoke(null, dim);
   }
}
