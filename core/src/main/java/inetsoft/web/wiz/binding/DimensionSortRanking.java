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

import java.util.*;

/**
 * Per-dimension sorting and ranking, in the agent vocabulary.
 *
 * <p><b>Columns are addressed by name, never by index.</b> {@code OrderModel.sortByCol} and
 * {@code TopNModel.sumCol} are integer indices with name-valued twins, and an index is brittle
 * under any shelf reorder: a stable index today is the wrong column tomorrow, and the failure is
 * silent because the sort still "works", just on the wrong column. So this writes the name form
 * only, and an integer supplied where a column name belongs is refused.
 *
 * <p>That is a deliberate narrowing — the raw escape hatch offered elsewhere in this plugin is
 * withheld here, because there is no case where a caller legitimately knows the index but not the
 * name.
 *
 * <p>Ranking spellings match what viz-chat already established, so an agent that has driven
 * that surface does not have to relearn them here.
 */
public final class DimensionSortRanking {
   /** Sort directions. {@code value_asc}/{@code value_desc} sort by an aggregate, not the label. */
   private static final Map<String, Integer> SORTS = Map.of(
      "none", XConstants.SORT_NONE,
      "asc", XConstants.SORT_ASC,
      "ascending", XConstants.SORT_ASC,
      "desc", XConstants.SORT_DESC,
      "descending", XConstants.SORT_DESC,
      "value_asc", XConstants.SORT_VALUE_ASC,
      "value_desc", XConstants.SORT_VALUE_DESC,
      "manual", XConstants.SORT_SPECIFIC);

   /** Ranking modes, spelled as viz-chat spells them. */
   private static final Map<String, Integer> RANKINGS = Map.of(
      "none", XCondition.NONE,
      "top", XCondition.TOP_N,
      "top_n", XCondition.TOP_N,
      "bottom", XCondition.BOTTOM_N,
      "bottom_n", XCondition.BOTTOM_N);

   private DimensionSortRanking() {
   }

   /**
    * @param direction   a token from {@link #sortTokens()}
    * @param sortByField the measure to sort by, for {@code value_asc}/{@code value_desc}
    * @param manualOrder the explicit value order, for {@code manual}
    */
   public record Sort(String direction, String sortByField, List<String> manualOrder) {}

   /**
    * @param mode    {@code top}, {@code bottom} or {@code none}
    * @param n       how many
    * @param measure the measure to rank by, by name
    * @param others  group the remainder into an "Others" row
    */
   public record Ranking(String mode, Integer n, String measure, Boolean others) {}

   public static void applySort(BDimensionRefModel dimension, Sort sort) {
      String direction = require(SORTS, sort == null ? null : sort.direction(), "direction",
                                 sortTokens());
      int order = SORTS.get(direction);

      // A by-value sort with nothing to sort by silently falls back to sorting by label, which
      // looks like the request was honoured.
      if((order == XConstants.SORT_VALUE_ASC || order == XConstants.SORT_VALUE_DESC) &&
         blank(sort.sortByField()))
      {
         throw new IllegalArgumentException(
            "'" + direction + "' sorts by an aggregate, so it needs 'sortByField' — the measure " +
            "to sort on. Without it the sort falls back to the label, which looks like it worked.");
      }

      if(order == XConstants.SORT_SPECIFIC &&
         (sort.manualOrder() == null || sort.manualOrder().isEmpty()))
      {
         throw new IllegalArgumentException(
            "'manual' needs 'manualOrder' — the values in the order you want them.");
      }

      requireName(sort.sortByField(), "sortByField");
      dimension.setOrder(order);

      if(!blank(sort.sortByField())) {
         dimension.setSortByCol(sort.sortByField());
      }

      if(sort.manualOrder() != null && !sort.manualOrder().isEmpty()) {
         dimension.setManualOrder(new ArrayList<>(sort.manualOrder()));
      }
   }

   public static void applyRanking(BDimensionRefModel dimension, Ranking ranking) {
      String mode = require(RANKINGS, ranking == null ? null : ranking.mode(), "mode",
                            rankingTokens());
      int option = RANKINGS.get(mode);

      if(option == XCondition.NONE) {
         dimension.setRankingOption(String.valueOf(XCondition.NONE));
         dimension.setRankingN(null);
         dimension.setRankingCol(null);
         return;
      }

      if(ranking.n() == null || ranking.n() < 1) {
         throw new IllegalArgumentException(
            "'" + mode + "' ranking needs an 'n' of at least 1, got " + ranking.n() + ".");
      }

      // Ranking by an unbound or unnamed measure produces an empty or arbitrary result rather
      // than an error, so the measure is required by name.
      if(blank(ranking.measure())) {
         throw new IllegalArgumentException(
            "'" + mode + "' ranking needs a 'measure' — the aggregate to rank by, by name. " +
            "Ranking by nothing produces an arbitrary result rather than an error.");
      }

      requireName(ranking.measure(), "measure");
      dimension.setRankingOption(String.valueOf(option));
      dimension.setRankingN(String.valueOf(ranking.n()));
      dimension.setRankingCol(ranking.measure());

      if(ranking.others() != null) {
         dimension.setOthers(ranking.others());
      }
   }

   /** What a dimension is currently sorted and ranked by, in the agent vocabulary. */
   public static Map<String, Object> describe(BDimensionRefModel dimension) {
      Map<String, Object> out = new LinkedHashMap<>();

      if(dimension == null) {
         return out;
      }

      out.put("direction", sortToken(dimension.getOrder()));
      out.put("sortByField", dimension.getSortByCol());
      out.put("manualOrder", dimension.getManualOrder());
      out.put("ranking", rankingToken(dimension.getRankingOption()));
      out.put("rankingN", dimension.getRankingN());
      out.put("rankingMeasure", dimension.getRankingCol());
      out.put("others", dimension.isOthers());
      return out;
   }

   public static List<String> sortTokens() {
      return sorted(SORTS);
   }

   public static List<String> rankingTokens() {
      return sorted(RANKINGS);
   }

   // ── the index refusal ─────────────────────────────────────────────────────

   /**
    * The deliberate narrowing. An all-digit value where a column name belongs is almost
    * certainly an index, and an index silently sorts or ranks by the wrong column after any
    * shelf reorder.
    */
   private static void requireName(String value, String key) {
      if(blank(value)) {
         return;
      }

      if(value.trim().matches("-?\\d+")) {
         throw new IllegalArgumentException(
            "'" + key + "' is '" + value + "', which looks like a column index. Columns are " +
            "addressed by name here: an index is stable only until the shelf is reordered, and " +
            "then it sorts or ranks by the wrong column without failing. Use the column's name.");
      }
   }

   private static String require(Map<String, Integer> table, String token, String key,
                                 List<String> valid)
   {
      String name = token == null ? "" : token.trim().toLowerCase();

      if(!table.containsKey(name)) {
         throw new IllegalArgumentException(
            "'" + key + "' must be one of " + valid + ", got '" + token + "'.");
      }

      return name;
   }

   /** Canonical spellings, so a read never hands back an alias. */
   private static String sortToken(int order) {
      if(order == XConstants.SORT_VALUE_ASC) {
         return "value_asc";
      }

      if(order == XConstants.SORT_VALUE_DESC) {
         return "value_desc";
      }

      if(order == XConstants.SORT_ASC) {
         return "asc";
      }

      if(order == XConstants.SORT_DESC) {
         return "desc";
      }

      if(order == XConstants.SORT_SPECIFIC) {
         return "manual";
      }

      if(order == XConstants.SORT_NONE) {
         return "none";
      }

      return "unknown(" + order + ")";
   }

   private static String rankingToken(String option) {
      if(blank(option)) {
         return "none";
      }

      try {
         int value = Integer.parseInt(option.trim());

         if(value == XCondition.TOP_N) {
            return "top";
         }

         if(value == XCondition.BOTTOM_N) {
            return "bottom";
         }

         return value == XCondition.NONE ? "none" : "unknown(" + value + ")";
      }
      catch(NumberFormatException e) {
         return "unknown(" + option + ")";
      }
   }

   private static List<String> sorted(Map<String, Integer> table) {
      List<String> names = new ArrayList<>(table.keySet());
      Collections.sort(names);
      return names;
   }

   private static boolean blank(String value) {
      return value == null || value.isBlank();
   }
}
