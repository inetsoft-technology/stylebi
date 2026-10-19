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
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.util.XNamedGroupInfo;
import inetsoft.web.binding.model.GroupCondition;
import inetsoft.web.binding.model.NamedGroupInfoModel;
import inetsoft.web.binding.model.table.CellBindingInfo;
import inetsoft.web.binding.model.table.OrderModel;
import inetsoft.web.binding.model.table.TopNModel;
import inetsoft.web.wiz.viewsheet.ConditionVocabulary;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Pattern;

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
    * {@code manualOrder} alongside it. {@code "value_asc"}/{@code "value_desc"} sort by a
    * specific in-scope summary cell's value rather than the group's own label, and require
    * {@code rankBy} alongside them -- resolved to {@code OrderModel.sortByCol} by
    * {@code CalcTableService}, which has the runtime access this stateless vocabulary does not.
    * Token spelling matches {@code DimensionSortRanking} (the equivalent for chart/crosstab
    * dimensions), so an agent that has driven that surface does not have to relearn it here.
    */
   private static final Map<String, Integer> SORT_DIRECTION = Map.of(
      "none", XConstants.SORT_NONE,
      "asc", XConstants.SORT_ASC,
      "desc", XConstants.SORT_DESC,
      "manual", XConstants.SORT_SPECIFIC,
      "value_asc", XConstants.SORT_VALUE_ASC,
      "value_desc", XConstants.SORT_VALUE_DESC);

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

   /**
    * The keys a cell binding may write, i.e. every key {@code CalcTableService} reads from it.
    * Any other key would be dropped silently, so {@link #validate} refuses it by name.
    */
   private static final List<String> RECOGNIZED_KEYS = List.of(
      "content", "grouping", "expand", "field", "formula", "value", "mergeCells", "name", "sort",
      "topn", "rowGroup", "colGroup", "mergeRowGroup", "mergeColGroup", "timeSeries");

   /** The keys {@code CalcTableService} reads from a cell's {@code field}. */
   private static final List<String> FIELD_KEYS = List.of(
      "column", "type", "namedGroup", "dateLevel", "dateInterval");

   /** The keys {@code CalcTableService} reads from a cell's {@code sort}. */
   private static final List<String> SORT_KEYS = List.of("direction", "manualOrder", "rankBy");

   /** The keys {@code CalcTableService} reads from a cell's {@code topn}. */
   private static final List<String> TOPN_KEYS = List.of("mode", "n", "rankBy");

   /** The keys {@code CalcTableService} reads from a {@code rankBy} inside sort or topn. */
   private static final List<String> RANK_BY_KEYS = List.of("column", "formula");

   /**
    * Keys {@link #describe} emits that a read-modify-write round trip sends straight back. They
    * carry no write of their own and are ignored, so they are tolerated rather than refused.
    */
   private static final Set<String> ECHO_ONLY_KEYS = Set.of(
      "runtimeName", "dateLevel", "dateInterval", "namedGroup");

   /**
    * Formulas that take a second column, written {@code Name(Column)}, keyed by canonical
    * normalized name (see {@link #normalizeFormulaName}) to the display name used in messages.
    */
   private static final Map<String, String> TWO_COLUMN_FORMULAS = Map.of(
      "covariance", "Covariance", "correlation", "Correlation",
      "weightedaverage", "WeightedAverage", "first", "First", "last", "Last");

   /**
    * Formulas whose parenthesized argument is an integer operand (the N of NthLargest and
    * friends, the P of PthPercentile), keyed like {@link #TWO_COLUMN_FORMULAS}.
    */
   private static final Map<String, String> OPERAND_FORMULAS = Map.of(
      "nthlargest", "NthLargest", "nthsmallest", "NthSmallest",
      "nthmostfrequent", "NthMostFrequent", "pthpercentile", "PthPercentile");

   /**
    * The spelling the script path needs for each summary formula, keyed by normalized name.
    * {@code LayoutTool.parseFormula} only lower-cases the first letter and the result is emitted
    * as a call into {@code CalcTableScope}, so "SUM" would become the undefined "sUM".
    */
   private static final Map<String, String> CANONICAL_FORMULAS = Map.ofEntries(
      Map.entry("none", "None"), Map.entry("sum", "Sum"), Map.entry("average", "Average"),
      Map.entry("count", "Count"), Map.entry("distinctcount", "DistinctCount"),
      Map.entry("max", "Max"), Map.entry("min", "Min"), Map.entry("product", "Product"),
      Map.entry("concat", "Concat"), Map.entry("median", "Median"), Map.entry("mode", "Mode"),
      Map.entry("variance", "Variance"), Map.entry("standarddeviation", "StandardDeviation"),
      Map.entry("populationvariance", "PopulationVariance"),
      Map.entry("populationstandarddeviation", "PopulationStandardDeviation"),
      Map.entry("sumsq", "SumSQFormula"), Map.entry("sumsqformula", "SumSQFormula"),
      Map.entry("correlation", "Correlation"), Map.entry("covariance", "Covariance"),
      Map.entry("weightedaverage", "WeightedAverage"), Map.entry("first", "First"),
      Map.entry("last", "Last"), Map.entry("nthlargest", "NthLargest"),
      Map.entry("nthsmallest", "NthSmallest"), Map.entry("nthmostfrequent", "NthMostFrequent"),
      Map.entry("pthpercentile", "PthPercentile"));

   private static final Pattern INTEGER = Pattern.compile("-?\\d+");

   /**
    * The plugin's AGGREGATE_FORMULAS aliases (normalized key -> normalized canonical key) whose
    * key differs from the canonical formula name: "AVG", "COUNT ALL", "COUNT DISTINCT", "SUMSQ"
    * and "WEIGHTED AVG". Must stay in step with the plugin's normalizeFormulaKey rule.
    */
   private static final Map<String, String> FORMULA_ALIASES = Map.of(
      "avg", "average", "countall", "count", "countdistinct", "distinctcount",
      "sumsq", "sumsqformula", "weightedavg", "weightedaverage");

   /**
    * Formulas that cannot work on a calc-table summary cell: the weighted-sum and sum-of-squares
    * pair need a second column no calc cell can carry (StyleBI fails with a column-not-found or a
    * script ReferenceError, or silently renders blank), and Aggregate is not a runnable
    * crosstab formula. One list drives both the refusal in {@link #validate} and what
    * {@link #advertisedFormulas} leaves out of {@code list_cell_vocabulary}. SumSQFormula is
    * deliberately not here: it works on both evaluators ({@code CalcTableScope} registers
    * {@code sumSQFormula} for layouts with a formula cell).
    */
   private static final Set<String> UNSUPPORTED_CALC_FORMULAS = Set.of(
      "sumwt", "sum2", "aggregate");

   private static final Pattern PERCENT_SEGMENT = Pattern.compile("<-?\\d+>");

   private CalcCellVocabulary() {
   }

   /** {@code formulas} without the ones a calc-table summary cell cannot run. */
   public static List<String> advertisedFormulas(Collection<String> formulas) {
      List<String> out = new ArrayList<>();

      for(String formula : formulas) {
         if(!UNSUPPORTED_CALC_FORMULAS.contains(normalizeFormulaName(formula))) {
            out.add(formula);
         }
      }

      return out;
   }

   /**
    * The canonical formula-name key shared with the plugin: lower-cased, whitespace/'_'/'-'
    * removed, one trailing {@code formula} dropped when something remains, then the alias map
    * applied. SumWTFormula, sum_wt and SUMWT all become sumwt; WeightedAvg becomes
    * weightedaverage.
    */
   private static String normalizeFormulaName(String name) {
      String n = name.toLowerCase(java.util.Locale.ROOT).replaceAll("[\\s_-]", "");

      if(n.endsWith("formula") && n.length() > "formula".length()) {
         n = n.substring(0, n.length() - "formula".length());
      }

      return FORMULA_ALIASES.getOrDefault(n, n);
   }

   /**
    * A summary cell's aggregate formula string, checked before anything is written. Same rule as
    * the plugin: only percent-shaped {@code <N>} segments are stripped, the name is everything
    * before the first '(' (an unclosed '(' still ends the name, with an empty argument), the
    * argument is between that '(' and the last ')', and an empty or digits-only argument counts
    * as no second column.
    */
   private static void validateSummaryFormula(String formula) {
      String text = PERCENT_SEGMENT.matcher(formula).replaceAll("").trim();
      int open = text.indexOf('(');
      int close = text.lastIndexOf(')');
      String name = open >= 0 ? text.substring(0, open) : text;
      String arg = open >= 0 && close > open ? text.substring(open + 1, close).trim() : "";
      String normalized = normalizeFormulaName(name);

      if(!UNSUPPORTED_CALC_FORMULAS.contains(normalized) &&
         !CANONICAL_FORMULAS.containsKey(normalized))
      {
         throw new IllegalArgumentException(
            "formula '" + formula + "' is not a recognized aggregate formula: '" + name.trim() +
            "' is not a name list_cell_vocabulary advertises. Use one of: " +
            String.join(", ", new TreeSet<>(CANONICAL_FORMULAS.values())) +
            ". Nothing was changed.");
      }

      if(UNSUPPORTED_CALC_FORMULAS.contains(normalized)) {
         throw new IllegalArgumentException(
            "formula '" + formula + "' is not a recognized aggregate formula for a calc-table " +
            "summary cell: '" + name.trim() + "' is not supported (it fails when the table " +
            "renders). list_cell_vocabulary's 'aggregateFormulas' lists the accepted names.");
      }

      String display = TWO_COLUMN_FORMULAS.get(normalized);

      if(display != null && (arg.isEmpty() || arg.chars().allMatch(Character::isDigit))) {
         throw new IllegalArgumentException(
            "formula '" + formula + "' needs a second column; use " + display +
            "(<column>), e.g. " + display + "(Order:Date). Nothing was changed.");
      }

      display = OPERAND_FORMULAS.get(normalized);

      if(display != null && !arg.isEmpty() && !INTEGER.matcher(arg).matches()) {
         throw new IllegalArgumentException(
            "formula '" + formula + "': the argument '" + arg + "' of " + display +
            " must be an integer (" + ("PthPercentile".equals(display) ? "P" : "N") +
            "), e.g. " + display + "(3). Nothing was changed.");
      }

      if(display != null && !arg.isEmpty()) {
         BigInteger value = new BigInteger(arg);
         boolean percentile = "PthPercentile".equals(display);
         // The engine silently clamps P to [0, 100] and turns N <= 0 into 1; an N that does not
         // fit an int is dropped back to the default by LayoutTool.parseFormula.
         BigInteger min = percentile ? BigInteger.ZERO : BigInteger.ONE;
         BigInteger max = BigInteger.valueOf(percentile ? 100 : Integer.MAX_VALUE);

         if(value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException(
               "formula '" + formula + "': the argument '" + arg + "' of " + display +
               " must be an integer from " + min + " to " + max + " (" +
               (percentile ? "P" : "N") + "); the engine would silently clamp it. " +
               "Nothing was changed.");
         }
      }
   }

   /**
    * The second column of a two-column summary formula ({@code First(Order:Date)}), or null when
    * the formula is not one or carries none. Same parse as {@link #validateSummaryFormula}.
    */
   public static String secondaryColumn(String formula) {
      if(formula == null) {
         return null;
      }

      String text = PERCENT_SEGMENT.matcher(formula).replaceAll("").trim();
      int open = text.indexOf('(');
      int close = text.lastIndexOf(')');
      String name = open >= 0 ? text.substring(0, open) : text;
      String arg = open >= 0 && close > open ? text.substring(open + 1, close).trim() : "";

      if(!TWO_COLUMN_FORMULAS.containsKey(normalizeFormulaName(name)) || arg.isEmpty() ||
         arg.chars().allMatch(Character::isDigit))
      {
         return null;
      }

      return arg;
   }

   /**
    * The formula with its name respelled to the canonical form the script path emits as a
    * function call; the {@code (argument)} and {@code <n>} suffixes are kept as typed. An unknown
    * name is returned unchanged.
    */
   public static String canonicalFormula(String formula) {
      if(formula == null) {
         return null;
      }

      String trimmed = formula.trim();
      int end = trimmed.length();

      for(int i = 0; i < trimmed.length(); i++) {
         char c = trimmed.charAt(i);

         if(c == '(' || c == '<') {
            end = i;
            break;
         }
      }

      String canonical = CANONICAL_FORMULAS.get(normalizeFormulaName(trimmed.substring(0, end)));
      return canonical == null ? formula : canonical + trimmed.substring(end);
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
               rejected.getValue() + "'. Cell keys are: " + String.join(", ", RECOGNIZED_KEYS) +
               ".");
         }
      }

      for(String key : cell.keySet()) {
         if(!RECOGNIZED_KEYS.contains(key) && !ECHO_ONLY_KEYS.contains(key)) {
            throw new IllegalArgumentException(
               "'binding' carries '" + key + "', which this write does not recognize and would " +
               "otherwise drop silently. Recognized keys: " + String.join(", ", RECOGNIZED_KEYS) +
               ". Nothing was changed.");
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

      if(cell.get("field") instanceof Map<?, ?> fieldMap) {
         requireKnownKeys(fieldMap, "field", FIELD_KEYS);
      }

      if(type == CellBinding.BIND_COLUMN && cell.get("field") instanceof Map<?, ?> fieldMap) {
         validateInlineNamedGroup(fieldMap);
      }

      if(type == CellBinding.BIND_COLUMN && str(cell, "formula") != null &&
         "summary".equalsIgnoreCase(str(cell, "grouping")))
      {
         validateSummaryFormula(str(cell, "formula"));
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

      // The binding is an untyped map, so Jackson never coerces these: CalcTableService reads them
      // with instanceof Boolean, and a string such as "true" was silently dropped (bug #78400).
      for(String key : List.of("mergeCells", "timeSeries")) {
         if(cell.get(key) != null && !(cell.get(key) instanceof Boolean)) {
            throw new IllegalArgumentException(
               "'" + key + "' must be true or false, got '" + cell.get(key) + "'.");
         }
      }
   }

   /**
    * A group cell's sort direction. {@code manual} requires {@code manualOrder} alongside it,
    * since a manual order with nothing to order is not meaningfully different from no order at
    * all. {@code value_asc}/{@code value_desc} require {@code rankBy} -- {@code {column,
    * formula}} naming one of this table's summary cells -- since sorting "by value" with nothing
    * to sort by would otherwise silently fall back to sorting by the label, which looks like the
    * request was honoured. This only checks the shape; resolving {@code rankBy} against the
    * table's actual summary cells happens in {@code CalcTableService}, which has the runtime
    * access this stateless vocabulary does not.
    */
   private static void validateSort(Map<String, Object> sort) {
      if(sort == null) {
         return;
      }

      requireKnownKeys(sort, "sort", SORT_KEYS);
      requireKnownKeys(sort.get("rankBy"), "sort.rankBy", RANK_BY_KEYS);
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

      if(resolved == XConstants.SORT_VALUE_ASC || resolved == XConstants.SORT_VALUE_DESC) {
         requireRankBy(sort.get("rankBy"), "sort.direction '" + direction + "'");
      }
   }

   /**
    * A group cell's ranking. {@code rankBy} ({@code {column, formula}}, naming the summary cell
    * to rank by) is optional: with exactly one summary cell in scope -- the common case --
    * {@code CalcTableService} defaults to it, matching what the Composer's own Top-N pane falls
    * back to. With more than one, {@code CalcTableService} refuses rather than guessing, since a
    * wrong guess would silently rank by the wrong column instead of failing loud. This only
    * checks the shape of a given {@code rankBy}; resolving it happens in {@code CalcTableService}.
    */
   private static void validateTopn(Map<String, Object> topn) {
      if(topn == null) {
         return;
      }

      requireKnownKeys(topn, "topn", TOPN_KEYS);
      requireKnownKeys(topn.get("rankBy"), "topn.rankBy", RANK_BY_KEYS);
      String mode = str(topn, "mode");

      if(mode == null) {
         throw new IllegalArgumentException("'topn' needs a 'mode' of " + tokens(TOPN_MODE) + ".");
      }

      topnMode(mode);
      Object n = topn.get("n");

      if(n != null && (!(n instanceof Number) || ((Number) n).intValue() < 1)) {
         throw new IllegalArgumentException("'topn.n' must be a positive integer, got " + n + ".");
      }

      if(topn.get("rankBy") != null) {
         requireRankBy(topn.get("rankBy"), "'topn.rankBy'");
      }
   }

   /**
    * The shape of an inline ('Customize') {@code field.namedGroup} -- {@code {groups: [{name,
    * conditions}], others?}} -- checked here, before the runtime is touched, for the same reason
    * {@link #validateSort}/{@link #validateTopn} are: an invalid shape costs nothing to reject
    * before {@code sessions.mutate} opens a checkpoint the caller then has to undo. A string
    * {@code namedGroup} (the by-name form) is untouched -- resolving whether that name exists is
    * {@code CalcTableService}'s job, since it needs runtime session state this stateless
    * vocabulary does not have. Building the actual {@code ConditionVocabulary.Clause} list is
    * likewise left to {@code CalcTableService.applyInlineNamedGroup}; this only checks the shape
    * is well-formed enough that building it cannot fail on a null/malformed input.
    */
   @SuppressWarnings("unchecked")
   private static void validateInlineNamedGroup(Map<?, ?> field) {
      Object namedGroup = field.get("namedGroup");

      if(!(namedGroup instanceof Map)) {
         return;
      }

      Map<String, Object> spec = (Map<String, Object>) namedGroup;
      Object rawGroups = spec.get("groups");

      if(!(rawGroups instanceof List<?> groups) || groups.isEmpty()) {
         throw new IllegalArgumentException(
            "'field.namedGroup.groups' needs at least one {name, conditions} group -- a named " +
            "group with no groups in it buckets nothing.");
      }

      String column = str((Map<String, Object>) field, "column");

      for(int i = 0; i < groups.size(); i++) {
         Object g = groups.get(i);

         if(!(g instanceof Map<?, ?> groupSpec)) {
            throw new IllegalArgumentException(
               "'field.namedGroup.groups[" + i + "]' must be an object.");
         }

         Object name = groupSpec.get("name");

         if(!(name instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(
               "Every group in 'field.namedGroup.groups' needs a non-blank 'name'.");
         }

         Object rawConditions = groupSpec.get("conditions");

         if(!(rawConditions instanceof List<?> conditions) || conditions.isEmpty()) {
            throw new IllegalArgumentException(
               "Every group in 'field.namedGroup.groups' needs at least one condition.");
         }

         for(int c = 0; c < conditions.size(); c++) {
            Object condition = conditions.get(c);

            if(!(condition instanceof Map<?, ?> clause)) {
               throw new IllegalArgumentException(
                  "Every condition in 'field.namedGroup.groups[].conditions' must be an object " +
                  "such as {operator: \"one_of\", values: [...]}, got " + condition + ".");
            }

            Object conditionField = clause.get("field");

            if(conditionField != null && column != null && !column.equals(String.valueOf(conditionField)) &&
               !column.equalsIgnoreCase(String.valueOf(conditionField).trim()))
            {
               throw new IllegalArgumentException(
                  "'field.namedGroup.groups[" + i + "].conditions[" + c + "]' names field '" +
                  conditionField + "', but a named group only conditions on the column it is " +
                  "grouping ('" + column + "'). Omit 'field' or set it to '" + column + "'.");
            }

            // Read by CalcTableService.clausesOf with Boolean.TRUE.equals, so a string "true"
            // was silently read as false (bug #78400).
            for(String key : List.of("negated", "equal")) {
               Object value = clause.get(key);

               if(value != null && !(value instanceof Boolean)) {
                  throw new IllegalArgumentException(
                     "'field.namedGroup.groups[" + i + "].conditions[" + c + "]." + key +
                     "' must be true or false, got '" + value + "'.");
               }
            }
         }
      }
   }

   /**
    * Refuses a key of a nested binding object ({@code field}, {@code sort}, {@code topn},
    * {@code rankBy}) that {@code CalcTableService} does not read, which would otherwise be
    * dropped silently. A value that is not an object is left to the caller's own shape check.
    */
   private static void requireKnownKeys(Object object, String path, List<String> recognized) {
      if(!(object instanceof Map<?, ?> map)) {
         return;
      }

      for(Object key : map.keySet()) {
         if(!recognized.contains(key)) {
            throw new IllegalArgumentException(
               "'" + path + "' carries '" + key + "', which this write does not recognize and " +
               "would otherwise drop silently. Recognized keys: " + String.join(", ", recognized) +
               ". Nothing was changed.");
         }
      }
   }

   /** The {@code {column, formula}} shape both {@code sort.rankBy} and {@code topn.rankBy} use. */
   private static void requireRankBy(Object rankBy, String forWhat) {
      if(!(rankBy instanceof Map)) {
         throw new IllegalArgumentException(
            forWhat + " needs a 'rankBy' of {column, formula} -- naming the summary cell to " +
            "rank by. Without it the sort/ranking would silently fall back to a default column " +
            "instead of failing loud.");
      }

      Map<?, ?> map = (Map<?, ?>) rankBy;

      if(!(map.get("column") instanceof String text) || text.isBlank()) {
         throw new IllegalArgumentException(forWhat + "'s 'rankBy' needs a non-blank 'column'.");
      }

      if(!(map.get("formula") instanceof String formulaText) || formulaText.isBlank()) {
         throw new IllegalArgumentException(
            forWhat + "'s 'rankBy' needs a non-blank 'formula' (e.g. \"Sum\") -- " +
            "list_cell_vocabulary's 'aggregateFormulas' lists the accepted names.");
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
      return describe(info, null);
   }

   /**
    * Same as {@link #describe(CellBindingInfo)}, additionally resolving a {@code value_asc}/
    * {@code value_desc} sort's or a ranking's {@code sortByCol}/{@code sumCol} index back to the
    * {@code {column, formula}} it names. {@code aggregates} is the same in-scope summary-cell
    * list {@code CalcTableService} resolved {@code rankBy} against on write -- pass {@code null}
    * only when that context is unavailable (the index is then omitted rather than reported bare,
    * since a bare index is exactly the brittle-under-reorder shape this vocabulary avoids
    * elsewhere).
    */
   public static Map<String, Object> describe(CellBindingInfo info, AggregateRef[] aggregates) {
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
         else if(order.getType() == XConstants.SORT_VALUE_ASC ||
                 order.getType() == XConstants.SORT_VALUE_DESC)
         {
            sort.put("rankBy", rankByOf(aggregates, order.getSortCol()));
         }

         out.put("sort", sort);
         // DateLevels.name() takes the STORED numeric-string form; the write side stores "none"
         // unless the caller sets 'field.dateLevel', so a group cell whose field isn't a
         // date/time column reads "none" here -- the level is meaningful only when the bound
         // field is itself a date/time column.
         out.put("dateLevel", DateLevels.name(String.valueOf(order.getOption())));
         out.put("dateInterval", order.getInterval());

         TopNModel topn = info.getTopn();
         Map<String, Object> topnOut = new LinkedHashMap<>();
         topnOut.put("mode", tokenOf(TOPN_MODE, topn.getType()));

         if(topn.getType() != XCondition.NONE) {
            topnOut.put("n", topn.getTopn());
            topnOut.put("rankBy", rankByOf(aggregates, topn.getSumCol()));
         }

         out.put("topn", topnOut);

         Object namedGroup = namedGroupOf(order);

         if(namedGroup != null) {
            out.put("namedGroup", namedGroup);
         }
      }

      return out;
   }

   /**
    * The cell's named group in the same vocabulary {@code set_cell_binding}'s
    * {@code field.namedGroup} accepts, so a read-back can be written back: the group's name for
    * a repository-registered group, otherwise {@code {groups: [{name, conditions}], others}}.
    * A worksheet-local group is stored as an inline one (its own name is not kept), and a group
    * built in the Composer UI's simple dialog holds plain values, reported as one 'one_of'
    * condition per group. Null when the cell has no named group.
    */
   private static Object namedGroupOf(OrderModel order) {
      NamedGroupInfoModel info = order.getInfo();

      if(info == null) {
         return null;
      }

      int type = info.getType();

      if(type == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO ||
         type == XNamedGroupInfo.ASSET_NAMEDGROUP_INFO_REF)
      {
         return info.getName() == null || info.getName().isEmpty() ? null : info.getName();
      }

      List<Map<String, Object>> groups = new ArrayList<>();

      if(type == XNamedGroupInfo.EXPERT_NAMEDGROUP_INFO && info.getConditions() != null) {
         for(var expr : info.getConditions()) {
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", expr.getName());
            group.put("conditions", conditionsOf(expr.getList()));
            groups.add(group);
         }
      }
      else if(type == XNamedGroupInfo.SIMPLE_NAMEDGROUP_INFO && info.getGroups() != null) {
         for(GroupCondition simple : info.getGroups()) {
            Map<String, Object> clause = new LinkedHashMap<>();
            clause.put("operator", "one_of");
            clause.put("values", simple.getValue() == null ? List.of() : simple.getValue());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("name", simple.getName());
            group.put("conditions", List.of(clause));
            groups.add(group);
         }
      }

      if(groups.isEmpty()) {
         return null;
      }

      Map<String, Object> out = new LinkedHashMap<>();
      out.put("groups", groups);
      out.put("others", order.isOthers() ? "group" : "leave");
      return out;
   }

   /**
    * One group's conditions as {@code set_cell_binding} takes them: the field is dropped (it is
    * always the cell's own column) along with the editor-only levels, and a junction or
    * {@code equal} flag appears only when it carries a value.
    */
   private static List<Map<String, Object>> conditionsOf(Object[] conditionList) {
      List<Map<String, Object>> out = new ArrayList<>();

      for(Map<String, Object> described : ConditionVocabulary.describe(conditionList)) {
         Map<String, Object> clause = new LinkedHashMap<>();
         clause.put("operator", described.get("operator"));
         clause.put("values", described.get("values"));
         clause.put("negated", described.get("negated"));

         if(Boolean.TRUE.equals(described.get("equal"))) {
            clause.put("equal", true);
         }

         if(described.get("junction") != null) {
            clause.put("junction", described.get("junction"));
         }

         out.add(clause);
      }

      return out;
   }

   /** Resolves a {@code sortByCol}/{@code sumCol} index back to the summary cell it names. */
   private static Map<String, Object> rankByOf(AggregateRef[] aggregates, int index) {
      if(aggregates == null || index < 0 || index >= aggregates.length) {
         return null;
      }

      AggregateRef agg = aggregates[index];
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("column", agg.getDataRef() == null ? null : agg.getDataRef().getName());
      out.put("formula", agg.getFormulaName());
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
