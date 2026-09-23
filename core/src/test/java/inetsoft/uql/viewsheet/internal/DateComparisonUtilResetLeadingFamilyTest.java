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

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Collections;
import java.util.List;

import static inetsoft.test.XTableUtil.date;

/**
 * Bug #76945: DateComparisonUtil.computeValidParts() derives maxPart from only the most
 * recent comparison period's own rows, then applies {@code Tool.compare(part, maxPart) <= 0}
 * (plus the Bug #76391 leading-family prefix rescue) as a cutoff across *every* period's rows.
 * That is only sound when a MergePartCell's leading component -- the disambiguating value
 * {@link DateComparisonInfo#getTempDateGroupRef} manufactures whenever the interval's context
 * level is finer than the comparison period level (e.g. "month of quarter" under a QUARTER
 * period, or "month of year" under a YEAR period) -- represents a stable position on a single
 * shared timeline. It does not: that leading component resets to its own start at the
 * beginning of every period instance, so an *older*, already fully-elapsed period's leading
 * value sorting numerically after the current, still in-progress period's own reach does not
 * mean the older period's data is "in the future" -- it belongs to a different period
 * instance's cycle entirely.
 *
 * <p>The fix (see the {@code differentLeadingFamily} branch in
 * {@code DateComparisonUtil.computeValidParts()}) treats a part whose leading family differs
 * from maxPart's own leading family as never excludable by this heuristic, regardless of how
 * it sorts against maxPart. It does not special-case any one period/context-level
 * combination: both the reported QUARTER-period/MONTH-context shape and the previously
 * untested YEAR-period/MONTH-context shape from the #76391 family are exercised here, using
 * the real {@link DCMergeDatePartFilter}/{@code MergePartCell} construction and real
 * {@link DateComparisonInfo}/{@link StandardPeriods}/{@link DateComparisonInterval} wiring
 * through the production {@code DateComparisonUtil.applyDateRange()} entry point (not just
 * {@code computeValidParts()} in isolation).</p>
 *
 * <p>Two further preconditions -- found only after the diagnosis's original round, per
 * {@code docs/.../bug-76945/01-diagnosis.md}'s "Revision (round 1)" and
 * {@code 02-refute.md}'s "Recheck (round 1)" -- are required for the vulnerable data shape to
 * be reachable at all, and both are deliberately exercised here so this test cannot pass "for
 * the wrong reason":</p>
 * <ul>
 *   <li>{@link StandardPeriods#isToDate()} must be {@code false}. Under the composer's default
 *       ({@code true}), {@code DateComparisonInfo.getStandardPeriodsCondition()} already clips
 *       every compared period's query rows to an identical relative cutoff, so the data shape
 *       below (an older, complete period's real higher-leading-family rows alongside the
 *       current, in-progress period's own lower reach) could never reach this heuristic in the
 *       first place.</li>
 *   <li>{@link DateComparisonInfo#getComparisonOption()} must not be {@code VALUE}. That is the
 *       composer's own default for a new Date Comparison config, and it disables
 *       {@code applyDateRange()}'s entire body (no selector of any kind is installed) -- a test
 *       that leaves it unset would pass trivially without ever exercising the fix.</li>
 * </ul>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonUtilResetLeadingFamilyTest {
   /**
    * Bug #76945's own reported shape: QUARTER period, Week-to-date interval (contextLevel =
    * MONTH), useFacet-equivalent placement (period is not itself the part-column's facet, so
    * the heuristic is not skipped by the existing partIsFacetDim/isCompareAll() guards).
    * Q2/Q3 2021 are older, fully-elapsed quarters that legitimately reach month 2 and month 3;
    * Q4 2021 (the most recent, still in-progress quarter) has itself only reached month 1.
    */
   @Test
   void quarterPeriodMonthContextToDateFalseKeepsOlderQuartersRealData() {
      Object[] parts = buildMergeParts("MonthOfQuarter(date)", new Object[][] {
         { 1, 1, date("2021-04-05") },   // Q2 2021: 1-1
         { 1, 2, date("2021-04-12") },   // Q2 2021: 1-2
         { 2, 1, date("2021-05-03") },   // Q2 2021: 2-1 -- must survive
         { 2, 2, date("2021-05-10") },   // Q2 2021: 2-2 -- must survive
         { 3, 1, date("2021-06-07") },   // Q2 2021: 3-1 -- must survive
         { 3, 2, date("2021-06-14") },   // Q2 2021: 3-2 -- must survive
         { 1, 1, date("2021-07-05") },   // Q3 2021: 1-1
         { 2, 1, date("2021-08-02") },   // Q3 2021: 2-1 -- must survive
         { 3, 1, date("2021-09-06") },   // Q3 2021: 3-1 -- must survive
         { 1, 1, date("2021-10-04") },   // Q4 2021 (most recent): 1-1
         { 1, 2, date("2021-10-11") },   // Q4 2021 (most recent): 1-2 -- its own reach
      });

      DataSet data = new DefaultDataSet(new Object[][] {
         { PERIOD_COL, PART_COL, VALUE_COL },
         { date("2021-04-01"), parts[0], 100.0 },
         { date("2021-04-01"), parts[1], 101.0 },
         { date("2021-04-01"), parts[2], 102.0 },
         { date("2021-04-01"), parts[3], 103.0 },
         { date("2021-04-01"), parts[4], 104.0 },
         { date("2021-04-01"), parts[5], 105.0 },
         { date("2021-07-01"), parts[6], 200.0 },
         { date("2021-07-01"), parts[7], 201.0 },
         { date("2021-07-01"), parts[8], 202.0 },
         { date("2021-10-01"), parts[9], 300.0 },
         { date("2021-10-01"), parts[10], 301.0 },
      });

      DateComparisonInfo dcInfo = standardPeriodsDcInfo(XConstants.QUARTER_DATE_GROUP,
                                                        "2021-10-15");
      Scale partScale = applyAndGetPartScale(dcInfo, data);

      // Q2/Q3 2021's real month-2/month-3 data must survive even though Q4 (the most
      // recent, still in-progress quarter) has itself only reached month 1.
      assertAccepted(partScale, data, true,
                     parts[2], parts[3], parts[4], parts[5], parts[7], parts[8]);
      // Q4's own rows (month 1) must always survive.
      assertAccepted(partScale, data, true, parts[9], parts[10]);
   }

   /**
    * The #76391 family (YEAR period, MONTH-of-year leading component), which the refuter's
    * round-1 recheck identified as untested against this exact defect class under
    * {@code isToDate()==false} -- #76391's own regression test never exercises
    * {@link DateComparisonInfo} at all, so it never had the chance to reach the query-level
    * condition that would let this shape occur. If the fix in computeValidParts() were
    * special-cased to the QUARTER-period/MONTH-context combination above, this test would
    * still fail -- it must pass too, on the same general leading-family logic.
    */
   @Test
   void yearPeriodMonthContextToDateFalseKeepsOlderYearsRealData() {
      Object[] parts = buildMergeParts("MonthOfYear(date)", new Object[][] {
         { 4, 1, date("2019-04-05") },   // 2019: 4-1
         { 9, 1, date("2019-09-02") },   // 2019: 9-1 -- must survive
         { 4, 1, date("2020-04-06") },   // 2020: 4-1
         { 9, 1, date("2020-09-07") },   // 2020: 9-1 -- must survive
         { 4, 1, date("2021-04-05") },   // 2021 (most recent): 4-1
         { 4, 2, date("2021-04-12") },   // 2021 (most recent): 4-2 -- its own reach
      });

      DataSet data = new DefaultDataSet(new Object[][] {
         { PERIOD_COL, PART_COL, VALUE_COL },
         { date("2019-01-01"), parts[0], 100.0 },
         { date("2019-01-01"), parts[1], 101.0 },
         { date("2020-01-01"), parts[2], 200.0 },
         { date("2020-01-01"), parts[3], 201.0 },
         { date("2021-01-01"), parts[4], 300.0 },
         { date("2021-01-01"), parts[5], 301.0 },
      });

      DateComparisonInfo dcInfo = standardPeriodsDcInfo(XConstants.YEAR_DATE_GROUP,
                                                        "2021-04-26");
      Scale partScale = applyAndGetPartScale(dcInfo, data);

      // 2019/2020's real September data must survive even though 2021 (the most recent
      // year) has itself only reached April.
      assertAccepted(partScale, data, true, parts[1], parts[3]);
      // 2021's own rows (April) must always survive.
      assertAccepted(partScale, data, true, parts[4], parts[5]);
   }

   // -- fixture plumbing --------------------------------------------------------------------

   private static final String PERIOD_COL = "period";
   private static final String PART_COL = "part";
   private static final String VALUE_COL = "value";

   /**
    * Builds real {@code DCMergeDatePartFilter.MergePartCell} values -- a leading
    * period-relative component (e.g. MonthOfQuarter/MonthOfYear, per {@code leadingRefName})
    * plus a trailing WeekOfMonth tie-breaker -- from raw {@code {leading, weekOfMonth, date}}
    * rows, the same construction {@link DateComparisonInfo#getTempDateGroupRef} produces at
    * runtime whenever the interval's context level is finer than the comparison period level.
    */
   private static Object[] buildMergeParts(String leadingRefName, Object[][] rawRows) {
      VSDimensionRef leadingRef = new VSDimensionRef();
      leadingRef.setDataRef(new AttributeRef(leadingRefName));
      VSDimensionRef weekOfMonthRef = new VSDimensionRef();
      weekOfMonthRef.setDataRef(new AttributeRef("WeekOfMonth(date)"));
      VSDimensionRef dateGroupRef = new VSDimensionRef();
      dateGroupRef.setDataRef(new AttributeRef("date"));

      Object[][] withHeader = new Object[rawRows.length + 1][];
      withHeader[0] = new Object[] { leadingRefName, "WeekOfMonth(date)", "date" };
      System.arraycopy(rawRows, 0, withHeader, 1, rawRows.length);

      DataSet rawDataSet = new DefaultDataSet(withHeader);
      DataSetTable base = new DataSetTable(rawDataSet);
      List<XDimensionRef> extraRefs = Collections.singletonList(leadingRef);
      DCMergeDatePartFilter filter =
         new DCMergeDatePartFilter(base, extraRefs, weekOfMonthRef, dateGroupRef, null);

      // Column order matches withHeader above: leading = 0, WeekOfMonth = 1, date = 2.
      int weekColIndex = 1;
      int firstDataRow = base.getHeaderRowCount();
      Object[] parts = new Object[rawRows.length];

      for(int i = 0; i < rawRows.length; i++) {
         parts[i] = filter.getObject(firstDataRow + i, weekColIndex);
         Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, parts[i],
            "test must exercise the real MergePartCell type, not a plain Integer");
      }

      return parts;
   }

   /**
    * A Standard Periods, Week-to-date (contextLevel = MONTH) date comparison, preCount=2,
    * with {@code isToDate()==false} and {@code comparisonOption=CHANGE} -- the two
    * independent preconditions this bug requires beyond the period/context leading-family
    * shape itself (see the class Javadoc).
    */
   private static DateComparisonInfo standardPeriodsDcInfo(int periodLevel, String endDateValue) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(periodLevel);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(endDateValue);
      periods.setToDate(false);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.WEEK);
      interval.setLevel(DateComparisonInfo.WEEK_TO_DATE);
      interval.setContextLevel(XConstants.MONTH_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);

      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);

      return dcInfo;
   }

   /**
    * Builds a minimal, non-facet VSChartInfo/EGraph bound to PERIOD_COL/PART_COL (mirroring
    * {@code DateComparisonUtilApplyDateRangeTest}'s NO_FACET shape) and calls the real
    * {@code DateComparisonUtil.applyDateRange()} entry point, returning the part-column Scale
    * so the test can inspect the GraphtDataSelector it ends up with.
    */
   private static Scale applyAndGetPartScale(DateComparisonInfo dcInfo, DataSet data) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setFacet(false);
      info.setDcBaseDateOnX(true);
      info.setDateComparisonRef(new VSDimensionRef(new AttributeRef(PERIOD_COL)));

      CalculateRef partCalc = new CalculateRef();
      partCalc.setDataRef(new AttributeRef(PART_COL));
      partCalc.setDcRuntime(true);
      info.addXField(new VSChartDimensionRef(partCalc));

      Scale partScale = new CategoricalScale();
      partScale.setFields(PART_COL);
      Scale valueScale = new LinearScale();
      valueScale.setFields(VALUE_COL);

      EGraph egraph = new EGraph();
      egraph.setCoordinate(new RectCoord(partScale, valueScale));

      DateComparisonUtil.applyDateRange(dcInfo, egraph, info, data);

      return partScale;
   }

   private static void assertAccepted(Scale partScale, DataSet data, boolean expectAccepted,
                                      Object... parts)
   {
      GraphtDataSelector selector = partScale.getGraphDataSelector();
      Assertions.assertNotNull(selector, "applyDateRange() must install a GraphtDataSelector "
         + "on the part-column scale -- comparisonOption must not be VALUE");

      for(Object part : parts) {
         int row = findRow(data, part);
         Assertions.assertTrue(row >= 0, "fixture row not found for part=" + part);
         boolean accepted = selector.accept(data, row, new String[] { PART_COL });
         Assertions.assertEquals(expectAccepted, accepted,
            "part=" + part + " expected accepted=" + expectAccepted + " but was " + accepted);
      }
   }

   private static int findRow(DataSet data, Object part) {
      for(int i = 0; i < data.getRowCount(); i++) {
         if(Tool.equals(part, data.getData(PART_COL, i))) {
            return i;
         }
      }

      return -1;
   }
}
