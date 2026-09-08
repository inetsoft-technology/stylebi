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

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.filter.DCMergeDatePartFilter;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.XDimensionRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static inetsoft.test.XTableUtil.date;

/**
 * Bug #76391: DateComparisonUtil.computeValidParts() used "does the most-recent comparison
 * period's own data have a row for this part" as a proxy for "has the most-recent period
 * chronologically reached this part yet". Those conditions only coincide when the most
 * recent period's data is dense. When it is merely sparse -- a real bucket with zero
 * matching rows -- the old algorithm dropped that bucket's data for *every* period,
 * including older periods that have real, legitimate rows for it.
 *
 * The fix (see DateComparisonUtil.computeValidParts()) keeps a part valid whenever it sorts
 * at or before the last part the most recent period's own rows actually reach, instead of
 * requiring an exact row match. A part that sorts strictly beyond that point is still
 * treated as an unreached "future" bucket and stays excluded, preserving the behavior
 * Bug #75152/#76389 rely on.
 *
 * Part values below use the same "month * 10 + weekOfMonth" encoding
 * DCMergeDatePartFilter.MergePartCell uses for a merged WeekOfMonth-of-Month part (see
 * MergePartCell.getEquivalenceCell()), so plain boxed Integers already sort exactly the way
 * the real MergePartCell values would via Tool.compare(). E.g. 7-1 -> 71, 9-2 -> 92,
 * 12-2 -> 122.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateComparisonUtilValidPartsTest {
   /**
    * Reproduces Bug #76391: the most recent period (2020) is missing rows for 7-1 (71),
    * 9-2 (92) and 12-2 (122) purely because those specific weeks happen to have no matching
    * records -- 2020 otherwise has data all the way out to 12-3 (123), well past all three.
    * Older periods (2018/2019) have real rows for those same buckets. None of them should be
    * orphaned.
    */
   @Test
   void sparseRecentPeriodDoesNotOrphanOlderPeriodsRealData() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "period", "part" },
         { date("2018-01-01"), 71 },    // 2018: 7-1 (204 in the bug report)
         { date("2019-01-01"), 71 },    // 2019: 7-1 (54)
         { date("2019-01-01"), 92 },    // 2019: 9-2 (36)
         { date("2019-01-01"), 112 },   // 2019: 11-2 (27) -- already survives today
         { date("2019-01-01"), 122 },   // 2019: 12-2 (18)
         { date("2020-01-01"), 32 },    // 2020: 3-2 -- already survives today
         { date("2020-01-01"), 112 },   // 2020: 11-2 -- already survives today
         { date("2020-01-01"), 123 },   // 2020: 12-3 -- 2020 reaches past December week 2
         });

      Set<Object> validParts = DateComparisonUtil.computeValidParts(data, "period", "part", null);

      Assertions.assertEquals(Set.of(32, 71, 92, 112, 122, 123), validParts,
                               "sparse gaps in the most recent period's own data must not " +
                               "orphan older periods' real buckets");
   }

   /**
    * Guards the legitimate use case computeValidParts exists for (Bug #75152/#76389): when
    * the most recent period is genuinely in-progress and its own rows stop at May (part 51),
    * a bucket in August (81) that only older, completed periods have data for is a real
    * future bucket relative to the current period and must stay excluded.
    */
   @Test
   void futureBucketsBeyondRecentPeriodsReachStayOrphaned() {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "period", "part" },
         { date("2018-01-01"), 51 },
         { date("2018-01-01"), 81 },
         { date("2019-01-01"), 51 },
         { date("2019-01-01"), 81 },
         { date("2020-01-01"), 51 },    // most recent period only reaches May
         });

      Set<Object> validParts = DateComparisonUtil.computeValidParts(data, "period", "part", null);

      Assertions.assertEquals(Set.of(51), validParts,
                               "a bucket the most recent period hasn't chronologically " +
                               "reached yet must stay orphaned even though older periods " +
                               "have real data for it");
   }

   /**
    * Reproduces the residual gap in Bug #76391's original fix, found live against the actual
    * reported Year_MonthToDate Chart4 fixture: WeekOfMonth "12-2" (2019: 18) still didn't
    * render even after that fix, because the real "12-2"/"12-1" values are not plain
    * month*10+week integers -- they are DCMergeDatePartFilter.MergePartCell instances whose
    * compareTo() does a two-element tuple compare (month component, then week-of-month
    * component; see MergePartCell.getMergedRefs()/compareTo()). The most recent period (2020)
    * reaches month 12 (has a "12-1" row) but its calendar never produces a "12-2" split week,
    * so maxPart is "12-1" and the plain "sorts at or before maxPart" rule from the first fix
    * excludes "12-2" (its own tuple sorts after "12-1") even though 2020 plainly did reach
    * month 12 -- the missing sub-bucket is calendar variation, not an unreached future month.
    *
    * The fix compares only the leading (non-tie-breaking) components against maxPart's own
    * leading components: sharing "12" is enough to rescue "12-2", regardless of its trailing
    * week-of-month value.
    */
   @Test
   void mergePartCellSharingMaxPartsLeadingComponentsIsNotOrphaned() {
      VSDimensionRef monthRef = new VSDimensionRef();
      monthRef.setDataRef(new AttributeRef("MonthOfWeekN(date)"));
      VSDimensionRef weekOfMonthRef = new VSDimensionRef();
      weekOfMonthRef.setDataRef(new AttributeRef("WeekOfMonth(date)"));
      VSDimensionRef dateGroupRef = new VSDimensionRef();
      dateGroupRef.setDataRef(new AttributeRef("date"));

      DataSet rawDataSet = new DefaultDataSet(new Object[][] {
         { "MonthOfWeekN(date)", "WeekOfMonth(date)", "date" },
         { 12, 1, date("2019-12-02") },   // 2019: 12-1
         { 12, 2, date("2019-12-09") },   // 2019: 12-2 -- must not be orphaned
         { 12, 1, date("2020-12-07") },   // 2020 (most recent): reaches month 12 via 12-1 only
         });
      DataSetTable base = new DataSetTable(rawDataSet);
      List<XDimensionRef> extraRefs = Collections.singletonList(monthRef);
      DCMergeDatePartFilter filter =
         new DCMergeDatePartFilter(base, extraRefs, weekOfMonthRef, dateGroupRef, null);

      // Column order matches the rawDataSet header above: MonthOfWeekN=0, WeekOfMonth=1, date=2.
      int weekColIndex = 1;
      int firstDataRow = base.getHeaderRowCount();

      Object part2019_12_1 = filter.getObject(firstDataRow, weekColIndex);
      Object part2019_12_2 = filter.getObject(firstDataRow + 1, weekColIndex);
      Object part2020_12_1 = filter.getObject(firstDataRow + 2, weekColIndex);

      Assertions.assertInstanceOf(DCMergeDatePartFilter.MergePartCell.class, part2019_12_2,
                                  "test must exercise the real MergePartCell type, not a plain Integer");
      Assertions.assertEquals("12-1", part2019_12_1.toString());
      Assertions.assertEquals("12-2", part2019_12_2.toString());
      Assertions.assertEquals("12-1", part2020_12_1.toString());

      DataSet data = new DefaultDataSet(new Object[][] {
         { "period", "part" },
         { date("2019-01-01"), part2019_12_1 },
         { date("2019-01-01"), part2019_12_2 },
         { date("2020-01-01"), part2020_12_1 },
         });

      Set<Object> validParts = DateComparisonUtil.computeValidParts(data, "period", "part", null);

      Assertions.assertTrue(validParts.contains(part2019_12_2),
         "12-2 shares the most recent period's own reached month (12) and must not be " +
         "orphaned merely because 2020's calendar never produces a 12-2 split week");
   }
}
