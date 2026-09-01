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
import inetsoft.test.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

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
}
