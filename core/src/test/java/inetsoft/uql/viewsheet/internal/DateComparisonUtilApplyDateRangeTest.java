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

import static inetsoft.test.XTableUtil.date;

/**
 * DateComparisonUtil.applyDateRange()'s orphaned-cell heuristic (computeValidParts()) must be
 * skipped entirely in two configurations, since neither one's "part" values represent a position
 * on a chronologically-progressing axis:
 *
 * <ul>
 *   <li>Bug #76389 -- "Compare Data Of: All" ({@link DateComparisonInfo#isCompareAll()}): every
 *       part of every comparison period must render unclipped, so a part the in-progress current
 *       period hasn't reached yet (e.g. December while the current year is only a few months in)
 *       is not an orphan -- it's real historical data for the prior periods.</li>
 *   <li>Bug #76388 -- facet mode ({@link VSChartInfo#isFacet()}): "part" identifies the facet
 *       dimension itself (e.g. DayOfWeek), not a chronological position, so a facet the most
 *       recent period happens to lack a row for is not a future bucket.</li>
 * </ul>
 *
 * Both bugs share the exact same skip condition in applyDateRange() (`info.isFacet() ||
 * dcInfo.isCompareAll()`), so both are exercised here against the real production entry point
 * (rather than only DateComparisonUtil.computeValidParts() in isolation, as
 * {@link DateComparisonUtilValidPartsTest} does) via a hand-built EGraph/VSChartInfo -- the same
 * "period"/"part" column shape {@link DateComparisonUtilValidPartsTest} uses, wired through
 * applyDateRange()'s real column-resolution path (getDcPeriodCol()/getDcPartCol()) instead of
 * being passed to computeValidParts() directly.
 *
 * Neither skip condition had any automated coverage prior to this test (verified: grep -rn
 * "isFacet()|isCompareAll()" core/src/test returned no hits against DateComparisonUtil before
 * this file was added) -- both fixes had previously been verified live only.
 *
 * <p>Bug #76518 narrowed the first of those two conditions. {@link VSChartInfo#isFacet()} is a
 * single chart-wide boolean that {@code GraphGenerator.createCoord()} sets from
 * "coordinate instanceof FacetCoord", so it is true whenever *any* axis carries 2+ dimensions,
 * regardless of whether the DC part column is one of them. On its own it cannot distinguish
 * "part IS the faceted dimension" (Bug #76388's case, where skipping is correct) from "some
 * unrelated dimension is faceted and part is still a plain chronologically-ordered axis" (where
 * skipping means a genuinely-unreached future part -- e.g. December while the current year has
 * only reached April -- renders anyway). {@code applyDateRange()} now also checks the actual
 * coordinate tree, so these tests pin down all three shapes via {@code CoordShape}:
 * {@code NO_FACET}, {@code PART_IS_FACET} (part scale in the FacetCoord's outer coordinate) and
 * {@code UNRELATED_FACET} (part scale in the inner coordinate, an unrelated dimension faceted
 * around it).</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonUtilApplyDateRangeTest {
   /**
    * Bug #76389: Year/Week Compare-All, preCount=2 -- the current (most recent) year only has 17
    * weeks of data (Jan-Apr), while 2019/2020 have real December weeks. Compare-All mode must
    * still render those December weeks; they must not be excluded as "orphaned" merely because
    * the current period hasn't reached December yet.
    */
   @Test
   void compareAllModeDoesNotOrphanPriorPeriodsUnreachedParts() {
      DataSet data = buildRows();
      DateComparisonInfo dcInfo = dcInfo(true);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.NO_FACET);

      // 2019/2020's December weeks (51-52) are past what 2021 (the most recent period) has
      // reached (only up to week 17) -- Compare-All must keep them anyway.
      assertPartRowAccepted(partScale, data, true, "2019-01-01", 51);
      assertPartRowAccepted(partScale, data, true, "2019-01-01", 52);
      assertPartRowAccepted(partScale, data, true, "2020-01-01", 51);
   }

   /**
    * Regression guard for the legitimate use case computeValidParts() exists for
    * (Bug #75152/#76391): outside Compare-All/facet mode, a part the most recent period hasn't
    * chronologically reached yet must still be excluded.
    */
   @Test
   void nonCompareAllNonFacetModeStillOrphansUnreachedParts() {
      DataSet data = buildRows();
      DateComparisonInfo dcInfo = dcInfo(false);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.NO_FACET);

      // Same rows as the Compare-All test above, but now Compare-All is off, so the heuristic
      // must run and exclude them as unreached-future parts (2021, the most recent period,
      // only reaches part 17).
      assertPartRowAccepted(partScale, data, false, "2019-01-01", 51);
      assertPartRowAccepted(partScale, data, false, "2019-01-01", 52);
      assertPartRowAccepted(partScale, data, false, "2020-01-01", 51);
   }

   /**
    * Bug #76388: Month/SAME_WEEK, DayOfWeek facet -- facet groups 5-7 have real data for every
    * period except the most recent (2021), which is exactly the shape the ticket's own expected
    * results anticipated (facets 5-7 should span only the older periods). Facet mode must keep
    * those groups' older-period rows; they must not be excluded as "orphaned" merely because the
    * most recent period has no row for that facet.
    */
   @Test
   void facetModeDoesNotOrphanFacetsTheMostRecentPeriodLacks() {
      DataSet data = buildRows();
      DateComparisonInfo dcInfo = dcInfo(false);

      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.PART_IS_FACET);

      // facet group 5-7 (encoded here as parts 51/52) never appears in 2021 (the most recent
      // period) at all -- facet mode must still keep 2019/2020's real rows for them.
      assertPartRowAccepted(partScale, data, true, "2019-01-01", 51);
      assertPartRowAccepted(partScale, data, true, "2019-01-01", 52);
      assertPartRowAccepted(partScale, data, true, "2020-01-01", 51);
   }

   /**
    * Bug #76518: same rows/DC config as
    * {@link #nonCompareAllNonFacetModeStillOrphansUnreachedParts()} -- part is a plain
    * chronologically-ordered week number, not itself a facet dimension -- except the chart is a
    * {@code FacetCoord} because some *other*, DC-unrelated dimension (e.g. Region) shares the
    * axis, so {@code info.isFacet()} is true. Unlike
    * {@link #facetModeDoesNotOrphanFacetsTheMostRecentPeriodLacks()} (where part genuinely is the
    * faceted dimension), the most-recent period's own chronological reach is still meaningful
    * here, so parts 51/52 (December) must still be orphaned.
    */
   @Test
   void facetCausedByUnrelatedDimensionStillOrphansUnreachedChronologicalPart() {
      DataSet data = buildRows();
      DateComparisonInfo dcInfo = dcInfo(false);

      // the part scale sits in the FacetCoord's *inner* coordinate here -- the facet levels are
      // the unrelated dimension's, so the orphan heuristic must still run.
      Scale partScale = applyAndGetPartScale(dcInfo, data, CoordShape.UNRELATED_FACET);

      assertPartRowAccepted(partScale, data, false, "2019-01-01", 51);
      assertPartRowAccepted(partScale, data, false, "2019-01-01", 52);
      assertPartRowAccepted(partScale, data, false, "2020-01-01", 51);
   }

   // -- fixture plumbing --------------------------------------------------------------------

   private static final String PERIOD_COL = "period";
   private static final String PART_COL = "part";
   private static final String VALUE_COL = "value";

   /**
    * period/part/value rows shaped after the Bug #76389/#76388 reports: three comparison periods
    * (2019/2020/2021), where the most recent period (2021) only reaches part 17, while 2019/2020
    * additionally have real rows for parts 51/52 (December) that 2021 hasn't reached. Each
    * period's rows share one repeated period-marker date (the same "year bucket" shape
    * {@link DateComparisonUtilValidPartsTest}'s fixtures use) -- DateComparisonUtil.
    * computeValidParts() only applies its orphan heuristic when the period column has repeated
    * values across rows; per-row-unique real dates are treated as a different chart shape
    * entirely and always skip the heuristic.
    */
   private static DataSet buildRows() {
      return new DefaultDataSet(new Object[][] {
         { PERIOD_COL, PART_COL, VALUE_COL },
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
    * Standard Periods (previous 2 years), interval level ALL (Compare-All) or SAME_WEEK
    * (an ordinary, non-Compare-All interval type) per {@code compareAll}.
    */
   private static DateComparisonInfo dcInfo(boolean compareAll) {
      DateComparisonInfo dcInfo = new DateComparisonInfo();
      StandardPeriods periods = new StandardPeriods();
      periods.setDateLevel(DateComparisonInfo.YEAR);
      periods.setPreCount(2);
      // Anchor "today" to the fixture's own most-recent row instead of the real system clock,
      // so getStartDate() (endDate - preCount years, rounded to year start) lands at
      // 2019-01-01 and doesn't reject the fixture's 2019-2021 rows outright regardless of the
      // orphaned-cell heuristic under test.
      periods.setToDayAsEndDay(false);
      periods.setEndDateValue("2021-04-26");
      dcInfo.setDateComparisonPeriods(periods);

      DateComparisonInterval interval = new DateComparisonInterval();
      interval.setGranularity(DateComparisonInfo.WEEK);
      // "level" is the DC interval *type* (ALL / *_TO_DATE / SAME_*), not the granularity --
      // DateComparisonInfo.WEEK (a granularity bit-flag) is not a valid level value and would
      // silently coerce to the DynamicValue's first allowed entry (ALL) if used here instead.
      interval.setLevel(compareAll ? DateComparisonInfo.ALL : DateComparisonInfo.SAME_WEEK);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(DateComparisonInfo.CHANGE);

      return dcInfo;
   }

   /**
    * The three coordinate shapes applyDateRange()'s facet handling has to tell apart. Mirrors
    * what GraphGenerator.createCoord() produces: the innermost dimension of an axis becomes the
    * plot axis of the inner coordinate, and every remaining outer dimension is wrapped in a
    * FacetCoord.
    */
   private enum CoordShape {
      /** Plain single-coordinate chart; {@code info.isFacet()} false. */
      NO_FACET,
      /** Bug #76388: the DC part column is the faceted dimension (part scale in the outer coord). */
      PART_IS_FACET,
      /** Bug #76518: an unrelated dimension is faceted; part stays a plain inner axis. */
      UNRELATED_FACET
   }

   private static final String OTHER_DIM_COL = "region";
   private static final String INNER_DIM_COL = "innerPeriod";

   /**
    * Builds a minimal VSChartInfo bound to PERIOD_COL/PART_COL, a matching hand-built EGraph
    * (bypassing the full chart-generation pipeline, the same "construct only what the seam under
    * test needs" approach {@link DateComparisonFormatOrphanedFacetTest} uses), calls the real
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
         // the plot axis and wraps part as the facet level -- Bug #76388's Month_SameWeek
         // DayOfWeek-facet shape, where ChartDcProcessor appends the period ref after the part.
         Scale innerScale = new CategoricalScale();
         innerScale.setFields(INNER_DIM_COL);
         RectCoord outer = new RectCoord(partScale, GTool.createFakeScale(null));

         return new FacetCoord(outer, new RectCoord(innerScale, valueScale));
      }
      case UNRELATED_FACET: {
         // X = [region, part]: part is still the innermost (plain chronological) axis; the facet
         // level is an unrelated dimension.
         Scale otherScale = new CategoricalScale();
         otherScale.setFields(OTHER_DIM_COL);
         RectCoord outer = new RectCoord(otherScale, GTool.createFakeScale(null));

         return new FacetCoord(outer, new RectCoord(partScale, valueScale));
      }
      default:
         return new RectCoord(partScale, valueScale);
      }
   }

   private static void assertPartRowAccepted(Scale partScale, DataSet data, boolean expectAccepted,
                                             String periodDate, int part)
   {
      GraphtDataSelector selector = partScale.getGraphDataSelector();
      Assertions.assertNotNull(selector, "applyDateRange() must install a GraphtDataSelector "
         + "on the part-column scale");

      int row = findRow(data, periodDate, part);
      Assertions.assertTrue(row >= 0,
         "fixture row not found for period=" + periodDate + ", part=" + part);
      boolean accepted = selector.accept(data, row, new String[] { PART_COL });
      Assertions.assertEquals(expectAccepted, accepted,
         "row for period=" + periodDate + ", part=" + part + " expected accepted="
         + expectAccepted + " but was " + accepted);
   }

   private static int findRow(DataSet data, String periodDate, int part) {
      Object targetPeriod = date(periodDate);

      for(int i = 0; i < data.getRowCount(); i++) {
         if(targetPeriod.equals(data.getData(PERIOD_COL, i)) &&
            Integer.valueOf(part).equals(data.getData(PART_COL, i)))
         {
            return i;
         }
      }

      return -1;
   }
}
