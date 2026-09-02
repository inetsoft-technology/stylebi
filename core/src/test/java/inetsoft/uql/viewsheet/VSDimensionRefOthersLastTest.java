/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

package inetsoft.uql.viewsheet;

import inetsoft.report.filter.OthersComparator;
import inetsoft.test.*;
import inetsoft.uql.XConstants;
import inetsoft.uql.XCondition;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Bug #76413: a chart legend/color dimension with Ranking (Top N/Bottom N) +
 * "Group Others" enabled must always sort the "Others" bucket last, even when
 * the dimension's own sort order is None/Original -- "sort Others last" is a
 * separate, always-on-by-default option independent of the primary sort
 * order (VSDimensionRef#sortOthersLast defaults to true).
 *
 * Before the fix, VSDimensionRef#createComparator() returned a null base
 * comparator for SORT_NONE/SORT_ORIGINAL *before* the sortOthersLast check
 * ran, so the "comparator != null" guard short-circuited and no
 * OthersComparator was ever applied -- CategoricalScale#getValues() (and
 * other callers) then skip sorting entirely on a null comparator, leaving
 * "Others" wherever it first appears in the underlying data instead of last.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class VSDimensionRefOthersLastTest {
   // Bug #76413: SORT_NONE + ranking Top N + Group Others must still force
   // "Others" last, sorting the reporter's exact repro values (true, Others,
   // false) into (true, false, Others) -- non-Others values keep their
   // original relative order (no primary sort requested), only "Others"
   // moves to the end.
   @Test
   public void testOthersSortedLastWhenSortOrderIsNone() {
      VSDimensionRef ref = rankedGroupOthersRef(XConstants.SORT_NONE);

      Comparator comparator = ref.createComparator(null);
      Assertions.assertNotNull(comparator,
         "Bug #76413: createComparator() must still return a comparator for SORT_NONE " +
         "when sortOthersLast + ranking-group-others applies, so Others is forced last");

      Object[] values = { "true", "Others", "false" };
      Arrays.sort(values, comparator);
      Assertions.assertArrayEquals(new Object[]{ "true", "false", "Others" }, values,
         "Others must sort last while the relative order of non-Others values is preserved");
   }

   // Same scenario, SORT_ORIGINAL -- the other sort order that fell into the
   // same null-comparator branch as SORT_NONE.
   @Test
   public void testOthersSortedLastWhenSortOrderIsOriginal() {
      VSDimensionRef ref = rankedGroupOthersRef(XConstants.SORT_ORIGINAL);

      Comparator comparator = ref.createComparator(null);
      Assertions.assertNotNull(comparator,
         "Bug #76413: createComparator() must still return a comparator for SORT_ORIGINAL " +
         "when sortOthersLast + ranking-group-others applies, so Others is forced last");

      Object[] values = { "true", "Others", "false" };
      Arrays.sort(values, comparator);
      Assertions.assertArrayEquals(new Object[]{ "true", "false", "Others" }, values);
   }

   // The returned comparator must be a standalone OthersComparator, not a
   // CombinedDataSetComparator wrapping a null base -- wrapping a null base
   // (the reporter's own suggested fix) throws NPE the first time two
   // non-Others values are compared, since CombinedDataSetComparator is not
   // null-safe for its base comparator.
   @Test
   public void testNullBaseComparatorIsNotWrapped() {
      VSDimensionRef ref = rankedGroupOthersRef(XConstants.SORT_NONE);

      Comparator comparator = ref.createComparator(null);
      Assertions.assertInstanceOf(OthersComparator.class, comparator,
         "the null-base case must return a standalone OthersComparator, not a comparator " +
         "wrapping a null base (which would NPE comparing two non-Others values)");
   }

   // Control: SORT_ASC already took the non-null-base branch before the fix
   // and correctly forced Others last -- confirms the fix doesn't disturb the
   // already-working combined-comparator path.
   @Test
   public void testOthersSortedLastWhenSortOrderIsAscending() {
      VSDimensionRef ref = rankedGroupOthersRef(XConstants.SORT_ASC);

      Comparator comparator = ref.createComparator(null);
      Assertions.assertNotNull(comparator);

      Object[] values = { "true", "Others", "false" };
      Arrays.sort(values, comparator);
      Assertions.assertArrayEquals(new Object[]{ "false", "true", "Others" }, values);
   }

   // Without ranking + Group Others, SORT_NONE/SORT_ORIGINAL must continue to
   // return null (no sorting at all) -- the fix only adds Others-last
   // ordering on top of the existing null-means-"no sort" semantics, it must
   // not force a comparator to exist in the (far more common) unranked case.
   @Test
   public void testNullPreservedWhenNotRankingGroupOthers() {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new AttributeRef("RESELLER"));
      ref.setOrder(XConstants.SORT_NONE);
      // rankingOption defaults to XCondition.NONE and groupOthers defaults to
      // false, so isRankingGroupOthers() is false.

      Assertions.assertNull(ref.createComparator(null),
         "without ranking Top N/Bottom N + Group Others, SORT_NONE must still mean " +
         "\"no comparator at all\", preserving natural/query order");
   }

   private static VSDimensionRef rankedGroupOthersRef(int order) {
      VSDimensionRef ref = new VSDimensionRef();
      ref.setDataRef(new AttributeRef("RESELLER"));
      ref.setOrder(order);
      ref.setRankingOptionValue(XCondition.TOP_N + "");
      ref.setRankingNValue("1");
      ref.setGroupOthersValue("true");
      // sortOthersLast defaults to true; assert that explicitly since the
      // whole point of the bug is that this default is meant to apply
      // independently of the chosen sort order.
      Assertions.assertTrue(ref.isSortOthersLast());
      Assertions.assertTrue(ref.isRankingGroupOthers());
      return ref;
   }
}
