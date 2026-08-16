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
package inetsoft.web.wiz.binding;

import inetsoft.uql.XCondition;
import inetsoft.uql.XConstants;
import inetsoft.web.binding.model.BDimensionRefModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class DimensionSortRankingTest {
   private static DimensionSortRanking.Sort sort(String direction, String by,
                                                 List<String> manual)
   {
      return new DimensionSortRanking.Sort(direction, by, manual);
   }

   private static DimensionSortRanking.Ranking ranking(String mode, Integer n, String measure) {
      return new DimensionSortRanking.Ranking(mode, n, measure, null);
   }

   // ── sorting ───────────────────────────────────────────────────────────────

   @Test
   void sortsAscendingAndDescending() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      DimensionSortRanking.applySort(dimension, sort("asc", null, null));
      assertEquals(XConstants.SORT_ASC, dimension.getOrder());

      DimensionSortRanking.applySort(dimension, sort("descending", null, null));
      assertEquals(XConstants.SORT_DESC, dimension.getOrder());
   }

   @Test
   void sortsByAnAggregateUsingTheNameForm() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      DimensionSortRanking.applySort(dimension, sort("value_desc", "Sales", null));

      assertEquals(XConstants.SORT_VALUE_DESC, dimension.getOrder());
      assertEquals("Sales", dimension.getSortByCol());
   }

   /** A by-value sort with nothing to sort by falls back to the label and looks honoured. */
   @Test
   void refusesAByValueSortWithNoField() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DimensionSortRanking.applySort(new BDimensionRefModel(),
                                              sort("value_asc", null, null)));

      assertTrue(thrown.getMessage().contains("sortByField"));
      assertTrue(thrown.getMessage().contains("looks like it worked"));
   }

   @Test
   void appliesAManualOrder() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      DimensionSortRanking.applySort(dimension,
                                     sort("manual", null, List.of("East", "West", "North")));

      assertEquals(XConstants.SORT_SPECIFIC, dimension.getOrder());
      assertEquals(3, dimension.getManualOrder().size());
   }

   @Test
   void refusesManualWithNoOrder() {
      assertThrows(IllegalArgumentException.class,
                   () -> DimensionSortRanking.applySort(new BDimensionRefModel(),
                                                        sort("manual", null, List.of())));
   }

   @Test
   void refusesAnUnknownDirectionListingTheValid() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DimensionSortRanking.applySort(new BDimensionRefModel(),
                                              sort("sideways", null, null)));

      assertTrue(thrown.getMessage().contains("sideways"));
      assertTrue(thrown.getMessage().contains("value_desc"));
   }

   // ── the index refusal, the deliberate narrowing ───────────────────────────

   /**
    * An index is stable only until the shelf is reordered, and then it sorts by the wrong column
    * without failing. There is no raw escape hatch for this on purpose.
    */
   @Test
   void refusesAColumnIndexWhereASortFieldNameBelongs() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DimensionSortRanking.applySort(new BDimensionRefModel(),
                                              sort("value_asc", "2", null)));

      assertTrue(thrown.getMessage().contains("index"));
      assertTrue(thrown.getMessage().contains("reordered"),
                 "the refusal has to say why the index is unsafe");
   }

   @Test
   void refusesAColumnIndexWhereARankingMeasureBelongs() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DimensionSortRanking.applyRanking(new BDimensionRefModel(),
                                                 ranking("top", 5, "0")));

      assertTrue(thrown.getMessage().contains("index"));
   }

   @Test
   void refusesANegativeIndexToo() {
      assertThrows(IllegalArgumentException.class,
                   () -> DimensionSortRanking.applyRanking(new BDimensionRefModel(),
                                                           ranking("top", 5, "-1")));
   }

   @Test
   void acceptsAColumnNameThatMerelyContainsDigits() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      assertDoesNotThrow(() -> DimensionSortRanking.applyRanking(
         dimension, ranking("top", 5, "Q1 Sales")));
      assertEquals("Q1 Sales", dimension.getRankingCol());
   }

   // ── ranking ───────────────────────────────────────────────────────────────

   @Test
   void appliesTopNByMeasureName() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      DimensionSortRanking.applyRanking(dimension, ranking("top", 10, "Sales"));

      assertEquals(String.valueOf(XCondition.TOP_N), dimension.getRankingOption());
      assertEquals("10", dimension.getRankingN());
      assertEquals("Sales", dimension.getRankingCol());
   }

   @Test
   void appliesBottomN() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      DimensionSortRanking.applyRanking(dimension, ranking("bottom_n", 3, "Sales"));

      assertEquals(String.valueOf(XCondition.BOTTOM_N), dimension.getRankingOption());
   }

   @Test
   void clearingRankingNeedsNothingElse() {
      BDimensionRefModel dimension = new BDimensionRefModel();
      DimensionSortRanking.applyRanking(dimension, ranking("top", 5, "Sales"));

      DimensionSortRanking.applyRanking(dimension, ranking("none", null, null));

      assertNull(dimension.getRankingN());
      assertNull(dimension.getRankingCol());
   }

   @Test
   void refusesRankingWithNoN() {
      assertThrows(IllegalArgumentException.class,
                   () -> DimensionSortRanking.applyRanking(new BDimensionRefModel(),
                                                           ranking("top", null, "Sales")));
      assertThrows(IllegalArgumentException.class,
                   () -> DimensionSortRanking.applyRanking(new BDimensionRefModel(),
                                                           ranking("top", 0, "Sales")));
   }

   /** Ranking by nothing produces an arbitrary result rather than an error. */
   @Test
   void refusesRankingWithNoMeasure() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> DimensionSortRanking.applyRanking(new BDimensionRefModel(),
                                                 ranking("top", 5, null)));

      assertTrue(thrown.getMessage().contains("arbitrary"));
   }

   @Test
   void carriesTheOthersFlag() {
      BDimensionRefModel dimension = new BDimensionRefModel();

      DimensionSortRanking.applyRanking(
         dimension, new DimensionSortRanking.Ranking("top", 5, "Sales", true));

      assertTrue(dimension.isOthers());
   }

   // ── read back ─────────────────────────────────────────────────────────────

   @Test
   void readsBackCanonicalTokensNeverAliases() {
      BDimensionRefModel dimension = new BDimensionRefModel();
      DimensionSortRanking.applySort(dimension, sort("descending", null, null));
      DimensionSortRanking.applyRanking(dimension, ranking("top_n", 5, "Sales"));

      Map<String, Object> described = DimensionSortRanking.describe(dimension);

      assertEquals("desc", described.get("direction"), "an alias must not come back out");
      assertEquals("top", described.get("ranking"));
      assertEquals("Sales", described.get("rankingMeasure"));
   }

   @Test
   void describesAnUnsortedDimension() {
      Map<String, Object> described =
         DimensionSortRanking.describe(new BDimensionRefModel());

      assertEquals("none", described.get("ranking"));
   }

   @Test
   void listsItsTokens() {
      assertTrue(DimensionSortRanking.sortTokens().contains("value_asc"));
      assertTrue(DimensionSortRanking.rankingTokens().contains("bottom"));
   }
}
