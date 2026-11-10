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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.schema.XSchema;

import java.util.*;

/**
 * The Data pane of a Gauge, Text or Image ({@code data-output-pane.component}) shows the
 * {@code With} column only for a two-column formula, the N/P operand only for an Nth/Pth
 * formula, and lists only the formulas that fit the column's type; the services store whatever
 * the model holds, so a raw or alias write skipped those rules (Redmine #80464).
 *
 * <ul>
 *   <li>{@code with} is needed by, and only by, a formula whose
 *       {@link AggregateFormula#isTwoColumns()} is true (First, Last, Correlation, Covariance,
 *       WeightedAverage);</li>
 *   <li>{@code num} (the N or P operand) only by one whose {@link AggregateFormula#hasN()} is
 *       true. {@code DataOutputPaneModel.getNum()} turns any integer below 1 into 1, so a 0 or
 *       negative operand reads back as 1;</li>
 *   <li>the aggregate must be in the dialog's list for the column's type
 *       ({@code AssetUtil.getFormulas}): date/time columns offer Max, Min, Count, DistinctCount,
 *       First, Last, Concat and the Nth/Pth formulas, boolean columns Count, DistinctCount,
 *       First and Last, every other column the full list without Aggregate.</li>
 * </ul>
 *
 * <p>The services copy aggregate, with, num and magnitude into the stored binding only when the
 * pane has a table, so a write of any of them to an output with no table is dropped silently
 * (Redmine #80798); {@link #requireBinding} refuses it. A blank aggregate is judged like
 * {@code none}: it takes neither a second column nor an N/P operand.
 *
 * <p>A dynamic aggregate ({@code $(x)}, {@code =script}) is not judged, and every rule fires only
 * when the patch changes a field it is about, so a state saved earlier never blocks an unrelated
 * write.
 */
final class OutputFormulaGuard {
   private OutputFormulaGuard() {
   }

   static final String AGGREGATE = "dataOutputPaneModel.aggregate";
   static final String WITH = "dataOutputPaneModel.with";
   static final String NUM = "dataOutputPaneModel.num";
   static final String TABLE = "dataOutputPaneModel.table";
   static final String MAGNITUDE = "dataOutputPaneModel.magnitude";

   record Snapshot(String aggregate, String with, String num, String table, Object magnitude) {
   }

   private static final Set<String> DATE_FORMULAS = names(
      AggregateFormula.MAX, AggregateFormula.MIN, AggregateFormula.COUNT_ALL,
      AggregateFormula.COUNT_DISTINCT, AggregateFormula.FIRST, AggregateFormula.LAST,
      AggregateFormula.CONCAT, AggregateFormula.NTH_LARGEST, AggregateFormula.NTH_SMALLEST,
      AggregateFormula.NTH_MOST_FREQUENT, AggregateFormula.PTH_PERCENTILE);

   private static final Set<String> BOOLEAN_FORMULAS = names(
      AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT, AggregateFormula.FIRST,
      AggregateFormula.LAST);

   private static final Set<String> OTHER_FORMULAS = names(
      AggregateFormula.SUM, AggregateFormula.AVG, AggregateFormula.MAX, AggregateFormula.MIN,
      AggregateFormula.COUNT_ALL, AggregateFormula.COUNT_DISTINCT, AggregateFormula.FIRST,
      AggregateFormula.LAST, AggregateFormula.STANDARD_DEVIATION, AggregateFormula.VARIANCE,
      AggregateFormula.POPULATION_STANDARD_DEVIATION, AggregateFormula.POPULATION_VARIANCE,
      AggregateFormula.CORRELATION, AggregateFormula.COVARIANCE, AggregateFormula.MEDIAN,
      AggregateFormula.MODE, AggregateFormula.WEIGHTED_AVG, AggregateFormula.PRODUCT,
      AggregateFormula.CONCAT, AggregateFormula.NTH_LARGEST, AggregateFormula.NTH_SMALLEST,
      AggregateFormula.NTH_MOST_FREQUENT, AggregateFormula.PTH_PERCENTILE);

   private static Set<String> names(AggregateFormula... formulas) {
      Set<String> names = new LinkedHashSet<>();

      for(AggregateFormula formula : formulas) {
         names.add(formula.getFormulaName());
      }

      return Collections.unmodifiableSet(names);
   }

   static Snapshot snapshot(String type, Object model) {
      if(!PropertyAliases.isDataOutputType(type)) {
         return null;
      }

      return new Snapshot((String) PropertyPath.get(model, AGGREGATE),
                          (String) PropertyPath.get(model, WITH),
                          (String) PropertyPath.get(model, NUM),
                          (String) PropertyPath.get(model, TABLE),
                          PropertyPath.get(model, MAGNITUDE));
   }

   /**
    * A written N/P operand must be a positive integer or a dynamic value, whatever the formula:
    * {@code getNum()} silently turns 0 or a negative into 1, and the dialog marks a non-integer
    * invalid.
    */
   static void requireValidOperand(String key, String path, Object value) {
      if(!path.endsWith(NUM) || value == null) {
         return;
      }

      String text = value.toString().trim();

      if(text.startsWith("$(") || text.startsWith("=")) {
         return;
      }

      boolean valid;

      try {
         valid = Integer.parseInt(text) >= 1;
      }
      catch(NumberFormatException ex) {
         valid = false;
      }

      if(!valid) {
         throw new IllegalArgumentException(
            "'" + key + "' (" + path + "): '" + value + "' is not a valid N/P operand. The N of " +
            "NthLargest/NthSmallest/NthMostFrequent or the P of PthPercentile must be a whole " +
            "number of 1 or more (or a $(variable)/=script value); StyleBI would otherwise " +
            "silently use 1. Nothing was changed.");
      }
   }

   /**
    * The Data pane enables Aggregate only once a column is picked, and the services keep
    * aggregate, with, num and magnitude only when the pane has a table. A write of one of them
    * (to a value other than its empty default) while the post-patch pane has no table would be
    * stored nowhere, so it is refused; a same-call table write passes.
    */
   static void requireBinding(Snapshot before, Object model) {
      if(before == null || !isBlank(PropertyPath.get(model, TABLE))) {
         return;
      }

      List<String> dropped = new ArrayList<>();
      Object aggregate = PropertyPath.get(model, AGGREGATE);
      Object with = PropertyPath.get(model, WITH);
      Object num = PropertyPath.get(model, NUM);
      Object magnitude = PropertyPath.get(model, MAGNITUDE);

      if(!Objects.equals(before.aggregate(), aggregate) && !isBlank(aggregate) &&
         !"none".equalsIgnoreCase(aggregate.toString().trim()))
      {
         dropped.add("'" + AGGREGATE + "' (alias 'aggregate')");
      }

      if(!Objects.equals(before.with(), with) && !isBlank(with)) {
         dropped.add("'" + WITH + "' (alias 'with')");
      }

      if(!Objects.equals(before.num(), num) && num != null &&
         !"1".equals(num.toString().trim()))
      {
         dropped.add("'" + NUM + "' (alias 'nthOperand')");
      }

      if(!Objects.equals(before.magnitude(), magnitude) && magnitude != null &&
         !"1".equals(magnitude.toString().trim()))
      {
         dropped.add("'" + MAGNITUDE + "' (alias 'magnitude')");
      }

      if(!dropped.isEmpty()) {
         throw new IllegalArgumentException(
            String.join(", ", dropped) + ": the output is not bound to a table and column, so " +
            "the Composer's Data pane has nothing to apply it to and StyleBI would drop the " +
            "value. Set 'table' and 'column' in the same call, or bind the output first. " +
            "Nothing was changed.");
      }
   }

   private static boolean isBlank(Object value) {
      return value == null || value.toString().isBlank();
   }

   /** The with/operand rules, judged on the post-patch model. */
   static void requireArity(Snapshot before, Object model) {
      if(before == null) {
         return;
      }

      String aggregate = (String) PropertyPath.get(model, AGGREGATE);
      String with = (String) PropertyPath.get(model, WITH);
      String num = (String) PropertyPath.get(model, NUM);
      AggregateFormula formula = formulaForArity(aggregate);

      if(formula == null) {
         return;
      }

      boolean aggregateChanged = !Objects.equals(before.aggregate(), aggregate);
      boolean withChanged = !Objects.equals(before.with(), with);
      boolean numChanged = !Objects.equals(before.num(), num);
      boolean hasWith = with != null && !with.isBlank();
      String shown = aggregate == null || aggregate.isBlank() ? "no aggregate is set" :
         "the aggregate '" + aggregate + "'";

      if(formula.isTwoColumns() && !hasWith && (aggregateChanged || withChanged)) {
         throw new IllegalArgumentException(
            "'" + AGGREGATE + "' (" + aggregate + ") is a two-column formula and needs a second " +
            "column in '" + WITH + "' (alias 'with', the Data pane's \"With\" field); the " +
            "Composer fills it in as soon as such a formula is picked, and without it the " +
            "formula has nothing to pair the column with. Set 'with' in the same call. " +
            "Nothing was changed.");
      }

      if(!formula.isTwoColumns() && hasWith && withChanged) {
         throw new IllegalArgumentException(
            "'" + WITH + "' ('" + with + "'): " + shown + ", which does not take a " +
            "second column, so the Composer hides \"With\" for it and the value would be " +
            "ignored. 'with' applies to First, Last, Correlation, Covariance and " +
            "WeightedAverage. Change the aggregate in the same call, or leave 'with' out. " +
            "Nothing was changed.");
      }

      if(!formula.hasN() && numChanged) {
         throw new IllegalArgumentException(
            "'" + NUM + "' ('" + num + "'): " + shown + ", which has no N/P " +
            "operand, so the Composer hides that field and the value would be ignored. The " +
            "operand ('nthOperand') applies to NthLargest, NthSmallest, NthMostFrequent and " +
            "PthPercentile. Change the aggregate in the same call, or leave it out. Nothing " +
            "was changed.");
      }
   }

   /**
    * When the patch changed the aggregate to one that takes no second column / no N/P operand,
    * drops the value left over from the previous aggregate, as the Data pane does for
    * {@code with} ({@code updateColumn2}). The pane only hides N and keeps its value; resetting
    * {@code num} to 1 here is a deliberate difference, so that a hidden operand cannot come back
    * when an Nth/Pth aggregate is picked later. A dynamic aggregate is not judged. Runs after
    * {@link #requireArity}, so it only touches values this patch did not write.
    *
    * @return the model to keep using
    */
   static Object clearStaleOperands(Snapshot before, Object model) {
      if(before == null) {
         return model;
      }

      String aggregate = (String) PropertyPath.get(model, AGGREGATE);
      AggregateFormula formula = formulaForArity(aggregate);

      if(formula == null || Objects.equals(before.aggregate(), aggregate)) {
         return model;
      }

      if(!formula.isTwoColumns() && !isBlank(PropertyPath.get(model, WITH))) {
         model = PropertyPath.set(model, WITH, null);
      }

      String num = (String) PropertyPath.get(model, NUM);

      if(!formula.hasN() && num != null && !"1".equals(num.trim())) {
         model = PropertyPath.set(model, NUM, "1");
      }

      return model;
   }

   /** Like {@link #staticFormula}, but a blank aggregate counts as {@code none}. */
   private static AggregateFormula formulaForArity(String aggregate) {
      return aggregate == null || aggregate.isBlank() ? AggregateFormula.NONE :
         staticFormula(aggregate);
   }

   /**
    * @param columnType the real type of the bound column, or null when it could not be looked up
    *                   (the rule is then not judged)
    */
   static void requireAggregateFitsColumn(String aggregate, String columnType, String column) {
      AggregateFormula formula = staticFormula(aggregate);

      if(formula == null || columnType == null || formula == AggregateFormula.NONE) {
         return;
      }

      Set<String> allowed;

      if(XSchema.DATE.equals(columnType) || XSchema.TIME.equals(columnType) ||
         XSchema.TIME_INSTANT.equals(columnType))
      {
         allowed = DATE_FORMULAS;
      }
      else if(XSchema.BOOLEAN.equals(columnType)) {
         allowed = BOOLEAN_FORMULAS;
      }
      else {
         allowed = OTHER_FORMULAS;
      }

      if(!allowed.contains(formula.getFormulaName())) {
         throw new IllegalArgumentException(
            "'" + AGGREGATE + "' (" + formula.getFormulaName() + ") is not offered for the " +
            columnType + " column '" + column + "'; the Composer's Aggregate list for that " +
            "column type holds only: " + String.join(", ", allowed) + ", none. Pick one of " +
            "those, or a column of a type the formula fits. Nothing was changed.");
      }
   }

   /** The formula of a plain aggregate name, or null for blank, dynamic or unknown ones. */
   private static AggregateFormula staticFormula(String aggregate) {
      if(aggregate == null || aggregate.isBlank()) {
         return null;
      }

      String text = aggregate.trim();

      if(text.startsWith("$(") || text.startsWith("=")) {
         return null;
      }

      return "none".equalsIgnoreCase(text) ? AggregateFormula.NONE :
         AggregateFormula.getFormula(text);
   }
}
