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
import inetsoft.uql.viewsheet.Viewsheet;
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

import java.util.Arrays;
import java.util.Date;

import static inetsoft.test.XTableUtil.date;

/**
 * Bug #77010: {@code DateComparisonInfo.getTempDateGroupRef()}'s early-return gate assumed
 * that whenever the interval's {@code contextLevel} equals the granularity's own date-group
 * level (e.g. WEEK context under WEEK granularity), no disambiguating leading component is
 * ever needed. That is false whenever the comparison *period* level is coarser than the
 * granularity/context level -- e.g. a QUARTER period with a WEEK-to-date interval at WEEK
 * granularity/context ("week of quarter"): the WEEK value genuinely resets at the start of
 * every quarter instance, so a bare week-of-quarter integer cannot tell "week 7 of Q2" apart
 * from "week 7 of Q4". With the gate wrongly firing, {@code getTempDateGroupRef()} returned an
 * empty array, so {@code DateComparisonUtil.getMergePartTableLens()}'s chart path
 * (line ~1664: {@code if(!isCrosstab && (tempRefs == null || tempRefs.length <= 0)) return base;})
 * never wrapped the table in a {@code DCMergeDatePartFilter}, the "week of quarter" column
 * reaching {@code computeValidParts()} stayed a bare period-relative {@code Integer}, and every
 * older, already fully-elapsed quarter's weeks past the current (in-progress) quarter's own
 * reach were wrongly excluded (reported as "some weeks are missing").
 *
 * <p>The fix adds a check on {@link DateComparisonInfo#getGranularityParentLevel()} to the
 * gate: it now only skips producing a temp ref when the granularity's true parent level (the
 * level the value actually resets against) is the context level itself -- i.e. no coarser
 * period wraps around and resets it. When the period is coarser (as in this bug's QUARTER
 * period / WEEK granularity+context shape), the existing
 * {@code periodLevel == XConstants.QUARTER_DATE_GROUP} / {@code contextLevel ==
 * XConstants.WEEK_DATE_GROUP} branch (already written to cover a MONTH-context sibling
 * shape) produces a "WeekOfQuarter(...)" disambiguating ref, exactly analogous to the
 * "MonthOfQuarter"/"WeekOfMonth" temp refs Bug #76945's fix already relies on.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonInfoTempDateGroupRefTest {
   /**
    * Reproduces the exact {@code DateComparisonInfo}/{@code StandardPeriods}/
    * {@code DateComparisonInterval} shape from the reported {@code Quarter_WeekToDate (1).vso}
    * (per {@code docs/.../bug-77010/01-diagnosis.md}): QUARTER period, preCount=2,
    * toDate=false, WEEK_TO_DATE interval, granularity=WEEK, contextLevel=WEEK,
    * comparisonOption=CHANGE_VALUE. Before the fix, {@code getTempDateGroupRef()} returned an
    * empty array for this shape; it must now return a non-empty "week of quarter"
    * disambiguating ref.
    */
   @Test
   void quarterPeriodWeekToDateWeekContextProducesNonEmptyTempRef() {
      DateComparisonInfo dcInfo = bugShapeDcInfo();
      Viewsheet vs = new Viewsheet();
      VSDimensionRef dateRef = new VSDimensionRef(new AttributeRef("date"));

      XDimensionRef[] tempRefs = dcInfo.getTempDateGroupRef("ds", vs, dateRef);

      Assertions.assertNotNull(tempRefs);
      Assertions.assertTrue(tempRefs.length > 0,
         "getTempDateGroupRef() must produce a disambiguating temp ref for a QUARTER "
         + "period whose WEEK granularity/context resets every quarter instance -- an "
         + "empty array leaves computeValidParts() with bare, period-relative integers "
         + "it cannot tell apart across quarters (Bug #77010)");
      Assertions.assertEquals("WeekOfQuarter(date)", tempRefs[0].getFullName(),
         "expected the same 'week of quarter' calc ref the WEEK-granularity branch of "
         + "updateDateDimensionLevel() manufactures for the chart's own X-axis dimension "
         + "under a QUARTER period, mirroring the existing MonthOfQuarter/WeekOfMonth "
         + "temp-ref shapes");
   }

   /**
    * End-to-end confirmation, mirroring {@code DateComparisonUtilResetLeadingFamilyTest}'s
    * style, and deliberately built to fail for the same reason production failed rather than
    * for an unrelated one: the "part" column shape fed to {@code applyDateRange()} is derived
    * from the *real* {@code dcInfo.getTempDateGroupRef()} /
    * {@code dcInfo.updateDateDimensionLevel()} outputs, exactly mirroring
    * {@code DateComparisonUtil.getMergePartTableLens()}'s own decision (line ~1664:
    * {@code if(!isCrosstab && (tempRefs == null || tempRefs.length <= 0)) return base;}) --
    * bare period-relative {@code Integer}s when {@code getTempDateGroupRef()} returns empty
    * (the pre-fix bug), or real {@code MergePartCell}s built via
    * {@code DCMergeDatePartFilter} when it returns the "WeekOfQuarter" temp ref (the fix).
    * Before the fix, this reproduces the reported symptom directly: Q2/Q3 2021's real weeks
    * 7-13 get wrongly excluded because Q4 (the most recent, in-progress quarter) has itself
    * only reached week 6 of its own cycle -- and bare integers give
    * {@code computeValidParts()} no way to tell "week 7 of Q2" apart from "week 7 of Q4".
    */
   @Test
   void quarterPeriodWeekToDateWeekContextKeepsOlderQuartersLaterWeeks() {
      int[] weekOfQuarter =    { 1,  6,  7,  13, 1,  7,  13, 1,  6 };
      Date[] periodDates = {
         date("2021-04-01"), date("2021-04-01"), date("2021-04-01"), date("2021-04-01"),
         date("2021-07-01"), date("2021-07-01"), date("2021-07-01"),
         date("2021-10-01"), date("2021-10-01"),
      };
      double[] values = { 100.0, 101.0, 102.0, 103.0, 200.0, 201.0, 202.0, 300.0, 301.0 };

      DateComparisonInfo dcInfo = bugShapeDcInfo();
      Viewsheet vs = new Viewsheet();
      VSDimensionRef comparisonRef = new VSDimensionRef(new AttributeRef("date"));
      XDimensionRef[] tempRefs = dcInfo.getTempDateGroupRef("ds", vs, comparisonRef);

      Object[] parts = buildPartColumn(dcInfo, "ds", vs, tempRefs, weekOfQuarter);

      Object[][] rows = new Object[weekOfQuarter.length + 1][];
      rows[0] = new Object[] { PERIOD_COL, PART_COL, VALUE_COL };

      for(int i = 0; i < weekOfQuarter.length; i++) {
         rows[i + 1] = new Object[] { periodDates[i], parts[i], values[i] };
      }

      DataSet data = new DefaultDataSet(rows);
      Scale partScale = applyAndGetPartScale(dcInfo, data);

      // Q2/Q3 2021's real weeks 7-13 (indices 2,3,5,6) must survive even though Q4 (the most
      // recent, still in-progress quarter) has itself only reached week 6.
      assertAccepted(partScale, data, true, parts[2], parts[3], parts[5], parts[6]);
      // Q4's own rows (weeks 1 and 6, indices 7,8) must always survive.
      assertAccepted(partScale, data, true, parts[7], parts[8]);
   }

   // -- fixture plumbing --------------------------------------------------------------------

   private static final String PERIOD_COL = "period";
   private static final String PART_COL = "part";
   private static final String VALUE_COL = "value";

   /**
    * The bug's exact config: QUARTER period, preCount=2, toDate=false, WEEK_TO_DATE interval,
    * granularity=WEEK, contextLevel=WEEK, comparisonOption=CHANGE_VALUE -- matching the
    * {@code <dateComparisonPeriods>}/{@code <dateComparisonInterval>} block extracted from the
    * reported {@code Quarter_WeekToDate (1).vso} in {@code 01-diagnosis.md}.
    */
   private static DateComparisonInfo bugShapeDcInfo() {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(XConstants.QUARTER_DATE_GROUP);
      periods.setPreCount(2);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-11-10");
      periods.setToDate(false);
      periods.setInclusive(true);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.WEEK);
      interval.setLevel(DateComparisonInfo.WEEK_TO_DATE);
      interval.setContextLevel(XConstants.WEEK_DATE_GROUP);
      dcInfo.setDateComparisonInterval(interval);

      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      return dcInfo;
   }

   /**
    * Builds the "part" column values exactly the way
    * {@code DateComparisonUtil.getMergePartTableLens()} decides to for the chart path, driven
    * by the real {@code tempRefs} the test already obtained from
    * {@code dcInfo.getTempDateGroupRef()}:
    * <ul>
    *   <li>{@code tempRefs} empty (pre-fix): returns bare period-relative {@code Integer}s --
    *       {@code getMergePartTableLens()}'s {@code if(!isCrosstab && tempRefs.length <= 0)
    *       return base;} early-out means the table is never wrapped in a
    *       {@code DCMergeDatePartFilter} at all.</li>
    *   <li>{@code tempRefs} non-empty (post-fix): builds real
    *       {@code DCMergeDatePartFilter.MergePartCell} values, using {@code tempRefs} as the
    *       filter's leading/disambiguating refs and {@code dcInfo.updateDateDimensionLevel()}'s
    *       own result (the same "WeekOfQuarter" calc ref, since {@code contextLevel ==
    *       granularity} here) as the granularity's own part ref -- precisely the two refs
    *       {@code getMergePartTableLens()} passes to {@code new DCMergeDatePartFilter(base,
    *       tempRefs, partLevelRef, ...)}.</li>
    * </ul>
    */
   private static Object[] buildPartColumn(DateComparisonInfo dcInfo, String source, Viewsheet vs,
                                           XDimensionRef[] tempRefs, int[] weekOfQuarter)
   {
      if(tempRefs == null || tempRefs.length == 0) {
         Object[] parts = new Object[weekOfQuarter.length];

         for(int i = 0; i < weekOfQuarter.length; i++) {
            parts[i] = weekOfQuarter[i];
         }

         return parts;
      }

      VSDimensionRef axisDimRef = new VSDimensionRef(new AttributeRef("date"));
      XDimensionRef partLevelRef = dcInfo.updateDateDimensionLevel(axisDimRef, source, vs, true);

      Object[][] withHeader = new Object[weekOfQuarter.length + 1][];
      withHeader[0] = new Object[] { partLevelRef.getFullName() };

      for(int i = 0; i < weekOfQuarter.length; i++) {
         withHeader[i + 1] = new Object[] { weekOfQuarter[i] };
      }

      DataSet rawDataSet = new DefaultDataSet(withHeader);
      DataSetTable base = new DataSetTable(rawDataSet);
      DCMergeDatePartFilter filter =
         new DCMergeDatePartFilter(base, Arrays.asList(tempRefs), partLevelRef, null, null);

      int partColIndex = 0;
      int firstDataRow = base.getHeaderRowCount();
      Object[] parts = new Object[weekOfQuarter.length];

      for(int i = 0; i < weekOfQuarter.length; i++) {
         parts[i] = filter.getObject(firstDataRow + i, partColIndex);
         Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, parts[i],
            "test must exercise the real MergePartCell type, not a plain Integer");
      }

      return parts;
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
