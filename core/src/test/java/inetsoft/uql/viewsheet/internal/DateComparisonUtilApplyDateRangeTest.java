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
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphtDataSelector;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.graph.scale.Scale;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static inetsoft.test.XTableUtil.date;

/**
 * DateComparisonUtil.applyDateRange() must not drop any real row the query returned for the
 * comparison window. The part scale's selector only hides rows before
 * {@link DateComparisonInfo#getStartDate()} -- the extra prior period
 * {@code getStandardPeriodsCondition()} fetches so the first shown period has a Change value.
 *
 * <p>applyDateRange() used to also clip older periods to the parts the newest period's own rows
 * reached (computeValidParts()/ValidPartsSelector, Bug #75152), with a growing list of skips
 * (Compare-All #76389, part-is-facet #76388/#76518, toDate off #77236) and rescues (#76391,
 * #76945). Every row it could drop was real in-window data: the query layer already cuts each
 * period at the End Date's relative position when toDate is on and returns whole periods when
 * it is off. It only ran in the "In Separate Sub-Graphs" layout, so the two layouts showed
 * different data. Bug #77236 removed it; these tests pin down that every layout and toDate
 * setting keeps every in-window row, via the real applyDateRange() entry point and a hand-built
 * EGraph/VSChartInfo.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonUtilApplyDateRangeTest {
   /**
    * Bug #77236: Month / Week to Date / Week (context WEEK), toDate=false, Change, "In Separate
    * Sub-Graphs" -- the period is faceted and the part (WeekOfMonth) is a bare Integer.
    * November (the most recent month) only reaches week 1, but September/October are complete
    * older months, so their weeks 2-5 are real history and must render. August is the extra
    * period fetched for Change and stays hidden.
    */
   @Test
   void toDateFalseKeepsOlderMonthsWeeksPastTheCurrentMonthsReach() {
      DataSet data = rows(
         "2021-08-01", 1, 5,   // extra prior period for Change
         "2021-09-01", 1, 4,
         "2021-10-01", 1, 5,
         "2021-11-01", 1, 1);  // November (most recent) only reaches week 1
      DateComparisonInfo dcInfo = standardPeriods(XConstants.MONTH_DATE_GROUP, "2021-11-10",
         false, DateComparisonInfo.WEEK, DateComparisonInfo.WEEK_TO_DATE,
         XConstants.WEEK_DATE_GROUP);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.PERIOD_IS_FACET);

      assertOnlyRowsBeforeStartHidden(partScale, data, "2021-09-01");
   }

   /**
    * Year / Month to Date / Month, previous 2 years, toDate=false, period faceted, bare
    * month-of-year part. 2019 and 2020 have months 1-12 while 2021 has only reached April --
    * 2019's and 2020's May-December are complete history and must render.
    */
   @Test
   void toDateFalseKeepsOlderYearsMonthsPastTheCurrentYearsReach() {
      DataSet data = rows(
         "2018-01-01", 1, 12,   // extra prior period for Change
         "2019-01-01", 1, 12,
         "2020-01-01", 1, 12,
         "2021-01-01", 1, 4);
      DateComparisonInfo dcInfo = standardPeriods(XConstants.YEAR_DATE_GROUP, "2021-04-26",
         false, DateComparisonInfo.MONTH, DateComparisonInfo.MONTH_TO_DATE,
         XConstants.MONTH_DATE_GROUP);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.PERIOD_IS_FACET);

      assertOnlyRowsBeforeStartHidden(partScale, data, "2019-01-01");
   }

   /**
    * toDate=true with a sparse newest-period tail: the End Date is in September, so the query
    * returns 2020's January-September, but 2021's own data stops at March (e.g. a data load
    * lag). 2020's April-September are inside the query window and must render; the newest
    * period's observed reach is not a cutoff.
    */
   @Test
   void toDateTrueKeepsOlderPeriodsRowsPastASparseNewestPeriodTail() {
      DataSet data = rows(
         "2019-01-01", 1, 9,   // extra prior period for Change
         "2020-01-01", 1, 9,
         "2021-01-01", 1, 3);  // 2021's own data stops at March
      DateComparisonInfo dcInfo = standardPeriods(XConstants.YEAR_DATE_GROUP, "2021-09-10",
         true, DateComparisonInfo.MONTH, DateComparisonInfo.MONTH_TO_DATE,
         XConstants.MONTH_DATE_GROUP, 1);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.PERIOD_IS_FACET);

      assertOnlyRowsBeforeStartHidden(partScale, data, "2020-01-01");
   }

   /**
    * Bug #76389: "Compare Data Of: All" must render every part of each comparison period, e.g.
    * 2019/2020's December weeks while 2021 has only reached week 17.
    */
   @Test
   void compareAllKeepsPriorPeriodsPartsPastTheCurrentPeriodsReach() {
      DataSet data = buildYearWeekRows();
      DateComparisonInfo dcInfo = standardPeriods(XConstants.YEAR_DATE_GROUP, "2021-04-26",
         true, DateComparisonInfo.WEEK, DateComparisonInfo.ALL, 0);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.NO_FACET);

      assertOnlyRowsBeforeStartHidden(partScale, data, "2019-01-01");
   }

   /**
    * Bug #76388: when the part is itself the faceted dimension (e.g. DayOfWeek), a facet group
    * the most recent period has no row for must still show the older periods' rows.
    */
   @Test
   void partFacetKeepsGroupsTheMostRecentPeriodLacks() {
      DataSet data = buildYearWeekRows();
      DateComparisonInfo dcInfo = standardPeriods(XConstants.YEAR_DATE_GROUP, "2021-04-26",
         true, DateComparisonInfo.WEEK, DateComparisonInfo.SAME_WEEK, 0);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.PART_IS_FACET);

      assertOnlyRowsBeforeStartHidden(partScale, data, "2019-01-01");
   }

   /**
    * Former Bug #76518 fixtures (a plain chart, and a chart faceted on an unrelated dimension)
    * used to assert that 2019/2020's weeks 51/52 were hidden because 2021 had only reached
    * week 17. That expectation came from a synthetic fixture and contradicts the query-layer
    * model: every row here is inside the window the query returned, so it is real data and
    * must render in every layout.
    */
   @Test
   void plainAndUnrelatedFacetLayoutsKeepPriorPeriodsPartsPastTheCurrentPeriodsReach() {
      for(CoordShape shape : new CoordShape[] { CoordShape.NO_FACET, CoordShape.UNRELATED_FACET }) {
         DataSet data = buildYearWeekRows();
         DateComparisonInfo dcInfo = standardPeriods(XConstants.YEAR_DATE_GROUP, "2021-04-26",
            true, DateComparisonInfo.WEEK, DateComparisonInfo.SAME_WEEK, 0);

         Scale partScale = applyAndGetPartScale(dcInfo, data, shape);

         assertOnlyRowsBeforeStartHidden(partScale, data, "2019-01-01");
      }
   }

   // -- fixture plumbing --------------------------------------------------------------------

   private static final String PERIOD_COL = "period";
   private static final String PART_COL = "part";
   private static final String VALUE_COL = "value";

   /**
    * Three comparison periods (2019/2020/2021) where 2021 only reaches part 17 while 2019/2020
    * also have parts 51/52, plus a 2018 row for the extra prior period fetched for Change.
    * Each period's rows share one repeated period-marker date, the "year bucket" shape
    * applyDateRange() sees at runtime.
    */
   private static DataSet buildYearWeekRows() {
      return new DefaultDataSet(new Object[][] {
         { PERIOD_COL, PART_COL, VALUE_COL },
         { date("2018-01-01"), 52, 10.0 },   // extra prior period for Change
         { date("2019-01-01"), 1, 100.0 },
         { date("2019-01-01"), 51, 204.0 },
         { date("2019-01-01"), 52, 18.0 },
         { date("2020-01-01"), 1, 90.0 },
         { date("2020-01-01"), 51, 54.0 },
         { date("2021-01-01"), 1, 80.0 },
         { date("2021-01-01"), 17, 70.0 },   // 2021 (most recent) only reaches part 17
      });
   }

   /**
    * Builds period/part/value rows from {@code periodDate, firstPart, lastPart} triples: one
    * row per part in [firstPart, lastPart] for each period.
    */
   private static DataSet rows(Object... spec) {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { PERIOD_COL, PART_COL, VALUE_COL });

      for(int i = 0; i < spec.length; i += 3) {
         Date period = date((String) spec[i]);

         for(int part = (Integer) spec[i + 1]; part <= (Integer) spec[i + 2]; part++) {
            rows.add(new Object[] { period, part, 10.0 * part });
         }
      }

      return new DefaultDataSet(rows.toArray(new Object[0][]));
   }

   private static DateComparisonInfo standardPeriods(int periodLevel, String endDate,
                                                     boolean toDate, int granularity,
                                                     int intervalLevel, int contextLevel)
   {
      return standardPeriods(periodLevel, endDate, toDate, granularity, intervalLevel,
                             contextLevel, 2);
   }

   /**
    * Standard Periods ending at {@code endDate} (anchored instead of "today" so getStartDate()
    * lands on the fixture's own periods), Change &amp; Value. A {@code contextLevel} of 0 leaves
    * the interval's context level unset. The interval "level" is the DC interval type
    * (ALL / *_TO_DATE / SAME_*), not the granularity.
    */
   private static DateComparisonInfo standardPeriods(int periodLevel, String endDate,
                                                     boolean toDate, int granularity,
                                                     int intervalLevel, int contextLevel,
                                                     int preCount)
   {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(periodLevel);
      periods.setPreCount(preCount);
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue(endDate);
      periods.setToDate(toDate);
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(granularity);
      interval.setLevel(intervalLevel);

      if(contextLevel != 0) {
         interval.setContextLevel(contextLevel);
      }

      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE_VALUE);

      return dcInfo;
   }

   /**
    * The coordinate shapes GraphGenerator.createCoord() produces for a DC chart: the innermost
    * dimension of an axis becomes the plot axis of the inner coordinate, and every remaining
    * outer dimension is wrapped in a FacetCoord.
    */
   private enum CoordShape {
      /** Plain single-coordinate chart. */
      NO_FACET,
      /** Bug #76388: the DC part column is the faceted dimension (part scale in the outer coord). */
      PART_IS_FACET,
      /** An unrelated dimension is faceted; part stays a plain inner axis. */
      UNRELATED_FACET,
      /** "In Separate Sub-Graphs": the period is faceted on Y, part is the inner X axis. */
      PERIOD_IS_FACET
   }

   private static final String OTHER_DIM_COL = "region";
   private static final String INNER_DIM_COL = "innerPeriod";

   /**
    * Builds a minimal VSChartInfo bound to PERIOD_COL/PART_COL and a matching hand-built EGraph
    * (bypassing the full chart-generation pipeline), calls the real
    * DateComparisonUtil.applyDateRange() entry point, and returns the part-column Scale so the
    * test can inspect the GraphtDataSelector it ends up with.
    */
   private static Scale applyAndGetPartScale(DateComparisonInfo dcInfo, DataSet data,
                                             CoordShape shape)
   {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setFacet(shape != CoordShape.NO_FACET);
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
      egraph.setCoordinate(buildCoord(shape, partScale, valueScale));

      DateComparisonUtil.applyDateRange(dcInfo, egraph, info, data);

      return partScale;
   }

   private static Coordinate buildCoord(CoordShape shape, Scale partScale, Scale valueScale) {
      switch(shape) {
      case PART_IS_FACET: {
         // X = [part, innerPeriod]: createCoord() consumes the innermost dim (innerPeriod) as
         // the plot axis and wraps part as the facet level.
         Scale innerScale = new CategoricalScale();
         innerScale.setFields(INNER_DIM_COL);
         RectCoord outer = new RectCoord(partScale, GTool.createFakeScale(null));

         return new FacetCoord(outer, new RectCoord(innerScale, valueScale));
      }
      case UNRELATED_FACET: {
         // X = [region, part]: part is still the innermost axis; the facet level is an
         // unrelated dimension.
         Scale otherScale = new CategoricalScale();
         otherScale.setFields(OTHER_DIM_COL);
         RectCoord outer = new RectCoord(otherScale, GTool.createFakeScale(null));

         return new FacetCoord(outer, new RectCoord(partScale, valueScale));
      }
      case PERIOD_IS_FACET: {
         // Y = [period, value]: ChartDcProcessor's useFacet branch puts the period on the axis
         // opposite the part, so the period is the facet level and part the inner X axis.
         Scale periodScale = new CategoricalScale();
         periodScale.setFields(PERIOD_COL);
         RectCoord outer = new RectCoord(GTool.createFakeScale(null), periodScale);

         return new FacetCoord(outer, new RectCoord(partScale, valueScale));
      }
      default:
         return new RectCoord(partScale, valueScale);
      }
   }

   /**
    * Every row whose period is on or after {@code startDate} must be accepted by the part
    * scale's selector, and every row before it (the extra prior period) rejected.
    */
   private static void assertOnlyRowsBeforeStartHidden(Scale partScale, DataSet data,
                                                       String startDate)
   {
      GraphtDataSelector selector = partScale.getGraphDataSelector();
      Assertions.assertNotNull(selector, "applyDateRange() must install a GraphtDataSelector "
         + "on the part-column scale");
      Date start = date(startDate);
      boolean sawHidden = false;

      for(int row = 0; row < data.getRowCount(); row++) {
         Date period = (Date) data.getData(PERIOD_COL, row);
         boolean expected = !period.before(start);
         sawHidden |= !expected;
         boolean accepted = selector.accept(data, row, new String[] { PART_COL });
         Assertions.assertEquals(expected, accepted,
            "row for period=" + period + ", part=" + data.getData(PART_COL, row)
            + " expected accepted=" + expected + " but was " + accepted);
      }

      Assertions.assertTrue(sawHidden, "fixture must include a row before startDate");
   }
}
