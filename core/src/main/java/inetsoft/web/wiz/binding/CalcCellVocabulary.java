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

import inetsoft.report.CellBinding;
import inetsoft.report.GroupableCellBinding;
import inetsoft.report.TableCellBinding;
import inetsoft.uql.XConstants;
import inetsoft.uql.XCondition;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.model.table.OrderModel;
import inetsoft.web.binding.model.table.TopNModel;

import java.util.*;

/**
 * The agent-facing calc-table cell vocabulary.
 *
 * <p>Across this binding surface {@code type} now means five unrelated things:
 * the object type on {@code BindingModel}; dimension-or-measure on a field ref;
 * {@code BIND_TEXT}/{@code BIND_COLUMN}/{@code BIND_FORMULA} on a cell; sort direction on
 * {@code OrderModel}; ranking mode on {@code TopNModel}. Three of those are reachable in one
 * {@code set_cell_binding} call.
 *
 * <p>That is the {@code fieldType}-vs-{@code role} defect class at its worst — not one
 * ambiguous key but a family of them, where a plausible wrong guess lands in a valid-looking
 * field and produces a silently wrong table. <b>So this vocabulary does not expose
 * {@code type} or {@code btype} at all</b>: the keys are {@code content}, {@code grouping} and
 * {@code expand}, and supplying any of the ambiguous spellings fails loud naming the right one.
 *
 * <p>{@code role} is refused for the same reason rather than adopted as an alias: it is the
 * wrong key in the recorded {@code fieldConfigs} defect, and giving it a new meaning inside the
 * same plugin family would be its own trap.
 *
 * <p>Integer constants never appear in either direction.
 */
public final class CalcCellVocabulary {
   private static final Map<String, Integer> CONTENT = Map.of(
      "text", CellBinding.BIND_TEXT,
      "column", CellBinding.BIND_COLUMN,
      "formula", CellBinding.BIND_FORMULA);

   private static final Map<String, Integer> GROUPING = Map.of(
      "group", CellBinding.GROUP,
      "detail", CellBinding.DETAIL,
      "summary", CellBinding.SUMMARY);

   private static final Map<String, Integer> EXPAND = Map.of(
      "none", GroupableCellBinding.EXPAND_NONE,
      "vertical", GroupableCellBinding.EXPAND_V,
      "v", GroupableCellBinding.EXPAND_V,
      "horizontal", GroupableCellBinding.EXPAND_H,
      "h", GroupableCellBinding.EXPAND_H);

   /**
    * A group cell's sort direction ({@code OrderModel.type}). {@code "manual"} requires
    * {@code manualOrder} alongside it. Sorting by a specific aggregate's value
    * ({@code XConstants.SORT_VALUE_ASC}/{@code SORT_VALUE_DESC}) is not exposed here yet -- it
    * needs the same in-scope-aggregate resolution {@link #TOPN_MODE} deliberately does not
    * expose either; see the note on {@code topn.mode}.
    */
   private static final Map<String, Integer> SORT_DIRECTION = Map.of(
      "none", XConstants.SORT_NONE,
      "asc", XConstants.SORT_ASC,
      "desc", XConstants.SORT_DESC,
      "manual", XConstants.SORT_SPECIFIC);

   /**
    * A group cell's ranking mode ({@code TopNModel.type}). Values match {@code StyleConstants}
    * exactly (confirmed identical to {@code XCondition.NONE}/{@code TOP_N}/{@code BOTTOM_N}: 0,
    * 9, 10) -- the UI's own dropdown and this vocabulary resolve to the same wire values.
    */
   private static final Map<String, Integer> TOPN_MODE = Map.of(
      "none", XCondition.NONE,
      "top", XCondition.TOP_N,
      "bottom", XCondition.BOTTOM_N);

   /** Keys that mean something else here, mapped to the key the caller meant. */
   private static final Map<String, String> REJECTED = Map.of(
      "type", "content",
      "btype", "grouping",
      "expansion", "expand",
      "role", "content' or 'grouping");

   private CalcCellVocabulary() {
   }

   public static int content(String token) {
      return resolve(CONTENT, token, "content");
   }

   public static int grouping(String token) {
      return resolve(GROUPING, token, "grouping");
   }

   public static int expand(String token) {
      return resolve(EXPAND, token, "expand");
   }

   public static int sortDirection(String token) {
      return resolve(SORT_DIRECTION, token, "sort.direction");
   }

   public static int topnMode(String token) {
      return resolve(TOPN_MODE, token, "topn.mode");
   }

   /** Whether a resolved grouping constant is the aggregating one. */
   public static boolean isSummary(int grouping) {
      return grouping == CellBinding.SUMMARY;
   }

   /**
    * Checks a cell-binding spec before anything is written.
    *
    * <p>An incomplete binding is refused rather than applied: a cell bound to nothing renders
    * blank, which reads as a data problem rather than as the binding error it is.
    */
   public static void validate(Map<String, Object> spec) {
      Map<String, Object> cell = spec == null ? Map.of() : spec;

      for(Map.Entry<String, String> rejected : REJECTED.entrySet()) {
         if(cell.containsKey(rejected.getKey())) {
            throw new IllegalArgumentException(
               "'" + rejected.getKey() + "' is not a calc-table cell key — it means something " +
               "else elsewhere in this plugin, which is exactly why it is refused here. Use '" +
               rejected.getValue() + "'. Cell keys are: content, grouping, expand, field, " +
               "value, formula, name.");
         }
      }

      String contentToken = str(cell, "content");

      if(contentToken == null) {
         throw new IllegalArgumentException(
            "A cell binding needs a 'content' of " + tokens(CONTENT) + ".");
      }

      int type = content(contentToken);

      if(cell.get("grouping") != null) {
         grouping(str(cell, "grouping"));
      }

      if(cell.get("expand") != null) {
         expand(str(cell, "expand"));
      }

      if(type == CellBinding.BIND_COLUMN && cell.get("field") == null) {
         throw new IllegalArgumentException(
            "A cell with content 'column' needs a 'field' — {column, type}. A cell bound to " +
            "nothing renders blank, which reads as missing data rather than a binding error.");
      }

      if(type == CellBinding.BIND_FORMULA && str(cell, "formula") == null &&
         str(cell, "value") == null)
      {
         throw new IllegalArgumentException(
            "A cell with content 'formula' needs a 'formula' (or 'value'). A cell bound to " +
            "nothing renders blank, which reads as missing data rather than a binding error.");
      }

      if(type == CellBinding.BIND_TEXT && str(cell, "value") == null) {
         throw new IllegalArgumentException(
            "A cell with content 'text' needs a 'value' — the literal text to show.");
      }

      if(cell.get("name") != null && !(cell.get("name") instanceof String)) {
         throw new IllegalArgumentException("'name' must be a string — the cell's own name.");
      }

      validateSort(asMap(cell.get("sort")));
      validateTopn(asMap(cell.get("topn")));

      for(String key : List.of("mergeRowGroup", "mergeColGroup")) {
         if(cell.get(key) != null && !(cell.get(key) instanceof String)) {
            throw new IllegalArgumentException(
               "'" + key + "' must be a string (another group cell's name), '" +
               TableCellBinding.DEFAULT_GROUP + "' to inherit the nearest enclosing group, or " +
               "null for the grand total.");
         }
      }

      if(cell.get("timeSeries") != null && !(cell.get("timeSeries") instanceof Boolean)) {
         throw new IllegalArgumentException("'timeSeries' must be true or false.");
      }
   }

   /**
    * A group cell's sort direction. Deliberately narrower than {@code OrderModel}: sorting by a
    * specific aggregate's value ({@code SORT_VALUE_ASC}/{@code SORT_VALUE_DESC}) needs the same
    * in-scope-aggregate resolution {@code topn.mode: "top"/"bottom"} would need for
    * {@code rankBy} and neither is exposed yet — see the class-level note on {@code TOPN_MODE}.
    * {@code manual} requires {@code manualOrder} alongside it, since a manual order with nothing
    * to order is not meaningfully different from no order at all.
    */
   private static void validateSort(Map<String, Object> sort) {
      if(sort == null) {
         return;
      }

      String direction = str(sort, "direction");

      if(direction == null) {
         throw new IllegalArgumentException(
            "'sort' needs a 'direction' of " + tokens(SORT_DIRECTION) + ".");
      }

      int resolved = sortDirection(direction);

      if(resolved == XConstants.SORT_SPECIFIC) {
         Object manual = sort.get("manualOrder");

         if(!(manual instanceof List) || ((List<?>) manual).isEmpty()) {
            throw new IllegalArgumentException(
               "sort.direction 'manual' needs a non-empty 'manualOrder' array of values — " +
               "otherwise there is nothing to order by.");
         }
      }
   }

   /**
    * A group cell's ranking. {@code rankBy} (choosing a specific aggregate to rank by, when a
    * group has more than one) is not exposed yet: the UI resolves it against a server-pushed,
    * cell-selection-scoped aggregate list this seam does not have access to, and guessing an
    * index wrong would silently rank by the wrong column rather than fail loud. With exactly one
    * aggregate in scope -- the common case -- StyleBI's own default (index 0) applies, matching
    * what the Composer's own "Aggregate" dropdown defaults to when nothing else is selected.
    */
   private static void validateTopn(Map<String, Object> topn) {
      if(topn == null) {
         return;
      }

      String mode = str(topn, "mode");

      if(mode == null) {
         throw new IllegalArgumentException("'topn' needs a 'mode' of " + tokens(TOPN_MODE) + ".");
      }

      topnMode(mode);
      Object n = topn.get("n");

      if(n != null && (!(n instanceof Number) || ((Number) n).intValue() < 1)) {
         throw new IllegalArgumentException("'topn.n' must be a positive integer, got " + n + ".");
      }
   }

   @SuppressWarnings("unchecked")
   private static Map<String, Object> asMap(Object value) {
      if(value == null) {
         return null;
      }

      if(!(value instanceof Map)) {
         throw new IllegalArgumentException("expected an object, got " + value);
      }

      return (Map<String, Object>) value;
   }

   /** Renders a cell binding back in tokens. Never emits an integer constant or an alias key. */
   public static Map<String, Object> describe(CellBindingInfo info) {
      if(info == null) {
         return null;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("content", tokenOf(CONTENT, info.getType()));
      out.put("grouping", tokenOf(GROUPING, info.getBtype()));
      out.put("expand", expandTokenOf(info.getExpansion()));
      out.put("value", info.getValue());
      out.put("formula", info.getFormula());
      out.put("mergeCells", info.getMergeCells());
      out.put("rowGroup", info.getRowGroup());
      out.put("colGroup", info.getColGroup());
      out.put("name", info.getName());
      out.put("runtimeName", info.getRuntimeName());
      out.put("mergeRowGroup", info.getMergeRowGroup());
      out.put("mergeColGroup", info.getMergeColGroup());
      out.put("timeSeries", info.isTimeSeries());

      // sort/topn/dateLevel only mean something on a group cell -- reporting OrderModel's
      // default (SORT_ASC, option YEAR_DATE_GROUP) on every detail/summary/text cell as well
      // would read as "this cell is sorted/date-grouped" when nothing was ever set on it.
      if(info.getBtype() == CellBinding.GROUP) {
         OrderModel order = info.getOrder();
         Map<String, Object> sort = new LinkedHashMap<>();
         sort.put("direction", tokenOf(SORT_DIRECTION, order.getType()));

         if(order.getType() == XConstants.SORT_SPECIFIC) {
            sort.put("manualOrder", order.getManualOrder());
         }

         out.put("sort", sort);
         // DateLevels.name() takes the STORED numeric-string form; a group cell whose field
         // isn't a date/time column simply carries the unused OrderModel default (YEAR_DATE_GROUP)
         // here, same as the write side leaves it unless the caller sets 'field.dateLevel' -- so a
         // caller should read this as meaningful only when the bound field is itself a date/time
         // column.
         out.put("dateLevel", DateLevels.name(String.valueOf(order.getOption())));
         out.put("dateInterval", order.getInterval());

         TopNModel topn = info.getTopn();
         Map<String, Object> topnOut = new LinkedHashMap<>();
         topnOut.put("mode", tokenOf(TOPN_MODE, topn.getType()));

         if(topn.getType() != XCondition.NONE) {
            topnOut.put("n", topn.getTopn());
         }

         out.put("topn", topnOut);
      }

      return out;
   }

   public static List<String> contentTokens() {
      return sorted(CONTENT);
   }

   public static List<String> groupingTokens() {
      return sorted(GROUPING);
   }

   public static List<String> expandTokens() {
      return List.of("none", "vertical", "horizontal");
   }

   public static List<String> sortDirectionTokens() {
      return sorted(SORT_DIRECTION);
   }

   public static List<String> topnModeTokens() {
      return sorted(TOPN_MODE);
   }

   // ── helpers ───────────────────────────────────────────────────────────────

   private static int resolve(Map<String, Integer> table, String token, String key) {
      String name = token == null ? "" : token.trim().toLowerCase();
      Integer value = table.get(name);

      if(value == null) {
         throw new IllegalArgumentException(
            "'" + token + "' is not a valid '" + key + "'. Valid values: " + tokens(table) +
            ". Integer constants are not accepted — the words are the vocabulary.");
      }

      return value;
   }

   /**
    * Reports an unrecognized constant as itself rather than guessing a token. A guess here
    * would read as fact to whoever consumed it.
    */
   private static String tokenOf(Map<String, Integer> table, int value) {
      for(Map.Entry<String, Integer> entry : table.entrySet()) {
         if(entry.getValue() == value) {
            return entry.getKey();
         }
      }

      return "unknown(" + value + ")";
   }

   private static String expandTokenOf(int value) {
      if(value == GroupableCellBinding.EXPAND_NONE) {
         return "none";
      }

      if(value == GroupableCellBinding.EXPAND_V) {
         return "vertical";
      }

      if(value == GroupableCellBinding.EXPAND_H) {
         return "horizontal";
      }

      return "unknown(" + value + ")";
   }

   private static String tokens(Map<String, Integer> table) {
      return String.join(", ", sorted(table));
   }

   private static List<String> sorted(Map<String, Integer> table) {
      List<String> names = new ArrayList<>(table.keySet());
      Collections.sort(names);
      return names;
   }

   private static String str(Map<String, Object> spec, String key) {
      Object value = spec == null ? null : spec.get(key);
      String text = value == null ? "" : String.valueOf(value).trim();
      return text.isEmpty() ? null : text;
   }
}
