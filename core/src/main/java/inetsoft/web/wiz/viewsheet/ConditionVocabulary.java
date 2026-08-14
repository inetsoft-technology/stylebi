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

import inetsoft.uql.JunctionOperator;
import inetsoft.uql.XCondition;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.*;

import java.util.*;

/**
 * The agent-facing condition vocabulary, and the builder for StyleBI's alternating condition
 * list.
 *
 * <p>{@code VSConditionDialogModel.conditionList} is an {@code Object[]} that alternates
 * condition items and junction operators:
 *
 * <pre>[condition, junction, condition, junction, condition]</pre>
 *
 * <p>Get the alternation wrong — two conditions adjacent, a trailing junction, a junction first
 * — and the result is an orphaned junction that either crashes downstream as a cast exception or,
 * worse, silently evaluates differently than intended. This is the most defect-prone shape on the
 * whole Composer surface, and its history proves it: orphaned-junction cast crashes,
 * flat-versus-nested shape drops, and multi-value filters normalizing to the wrong operator have
 * all been fixed here before.
 *
 * <p><b>So the agent never writes {@code conditionList}.</b> It writes a flat, homogeneous list
 * where each condition carries its junction to the <i>next</i> one, and this class builds the
 * array. There is deliberately no raw escape hatch: unlike a deep property path, there is no case
 * where a caller legitimately needs to hand-build the alternating form.
 *
 * <p>Highlights embed a full condition model, so this same vocabulary serves both — one
 * implementation, two callers.
 */
public final class ConditionVocabulary {
   /** Operator tokens, with the aliases an agent naturally reaches for. */
   private static final Map<String, Integer> OPERATORS = operators();

   /** Operators that take no values at all. */
   private static final Set<String> VALUELESS = Set.of("null");

   /** Operators that take exactly two. */
   private static final Set<String> PAIRED = Set.of("between");

   private ConditionVocabulary() {
   }

   /** One condition in the flat vocabulary. {@code junction} points at the *next* condition. */
   public record Clause(String field, String operator, List<Object> values, String junction,
                        boolean negated) {}

   /**
    * Builds the alternating array.
    *
    * @param clauses   the flat list, each carrying its junction to the next
    * @param available the model's own {@code fields[]}; a condition on a column absent from it
    *                  is the recorded cast-crash trigger, so it is refused here
    */
   public static Object[] toConditionList(List<Clause> clauses, DataRefModel[] available) {
      if(clauses == null || clauses.isEmpty()) {
         return new Object[0];
      }

      Map<String, DataRefModel> fields = index(available);
      List<Object> out = new ArrayList<>();

      for(int i = 0; i < clauses.size(); i++) {
         Clause clause = clauses.get(i);
         boolean last = i == clauses.size() - 1;

         // Arity is checked before anything is built, so a malformed list never becomes a
         // half-built array that a later cast turns into a crash.
         requireJunctionArity(clause, i, last);
         out.add(condition(clause, i, fields));

         if(!last) {
            out.add(junction(clause.junction(), i));
         }
      }

      return out.toArray();
   }

   /** Renders an alternating array back into the flat vocabulary. */
   public static List<Map<String, Object>> describe(Object[] conditionList) {
      List<Map<String, Object>> out = new ArrayList<>();

      if(conditionList == null) {
         return out;
      }

      for(int i = 0; i < conditionList.length; i++) {
         Object item = conditionList[i];

         if(item instanceof ConditionModel condition) {
            Map<String, Object> clause = new LinkedHashMap<>();
            clause.put("field", condition.getField() == null
               ? null : condition.getField().getName());
            clause.put("operator", operatorToken(condition.getOperation()));
            clause.put("values", values(condition.getValues()));
            clause.put("negated", condition.isNegated());
            clause.put("junction", junctionAfter(conditionList, i));
            out.add(clause);
         }
      }

      return out;
   }

   public static Map<String, Object> vocabulary() {
      return Map.of(
         "operators", new TreeSet<>(OPERATORS.keySet()),
         "junctions", List.of("and", "or"),
         "note", "Each condition carries the junction to the NEXT one, so the last condition " +
            "must not have a junction and every earlier one must. The alternating array " +
            "StyleBI stores is built for you and cannot be written directly.");
   }

   // ── the arity invariant ───────────────────────────────────────────────────

   /**
    * The invariant the raw shape cannot express: exactly one fewer junction than conditions.
    * Both failure directions are named by index, because "junction arity mismatch" alone leaves
    * the caller counting.
    */
   private static void requireJunctionArity(Clause clause, int index, boolean last) {
      boolean has = clause.junction() != null && !clause.junction().isBlank();

      if(last && has) {
         throw new IllegalArgumentException(
            "Condition " + index + " is the last one and must not carry a 'junction' — a " +
            "junction joins a condition to the NEXT one, and a trailing junction becomes an " +
            "orphan that fails downstream.");
      }

      if(!last && !has) {
         throw new IllegalArgumentException(
            "Condition " + index + " needs a 'junction' of and/or to join it to condition " +
            (index + 1) + ". Every condition but the last carries one.");
      }
   }

   // ── builders ──────────────────────────────────────────────────────────────

   private static ConditionModel condition(Clause clause, int index,
                                           Map<String, DataRefModel> fields)
   {
      String name = clause.field() == null ? "" : clause.field().trim();

      if(name.isEmpty()) {
         throw new IllegalArgumentException("Condition " + index + " needs a 'field'.");
      }

      DataRefModel field = fields.get(name.toLowerCase());

      if(field == null) {
         throw new IllegalArgumentException(
            "Condition " + index + " names '" + clause.field() + "', which this assembly " +
            "cannot filter on. Available fields: " +
            (fields.isEmpty() ? "(none)" : String.join(", ", new TreeSet<>(names(fields)))) +
            ". A condition on an unknown column is the recorded cause of a downstream cast " +
            "failure, so it is refused here.");
      }

      String operator = requireOperator(clause.operator(), index);
      List<Object> values = clause.values() == null ? List.of() : clause.values();
      requireValueArity(operator, values, index);

      ConditionModel condition = new ConditionModel();
      condition.setField(field);
      condition.setOperation(OPERATORS.get(operator));
      condition.setNegated(clause.negated());
      condition.setLevel(0);
      condition.setValues(values.stream().map(ConditionVocabulary::value)
                             .toArray(ConditionValueModel[]::new));
      return condition;
   }

   private static ConditionValueModel value(Object raw) {
      ConditionValueModel value = new ConditionValueModel();
      value.setValue(raw);
      value.setType("value");
      return value;
   }

   private static JunctionOperatorModel junction(String token, int index) {
      String name = token.trim().toLowerCase();
      int type = switch(name) {
         case "and", "&&" -> JunctionOperator.AND;
         case "or", "||" -> JunctionOperator.OR;
         default -> throw new IllegalArgumentException(
            "Condition " + index + " has junction '" + token + "'; valid junctions are and, or.");
      };

      JunctionOperatorModel junction = new JunctionOperatorModel();
      junction.setType(type);
      junction.setLevel(0);
      return junction;
   }

   private static String requireOperator(String operator, int index) {
      String name = operator == null ? "" : operator.trim().toLowerCase();

      if(!OPERATORS.containsKey(name)) {
         throw new IllegalArgumentException(
            "Condition " + index + " has operator '" + operator + "'. Valid operators: " +
            new TreeSet<>(OPERATORS.keySet()) + ".");
      }

      return name;
   }

   /**
    * A value-arity mismatch is the other silent case: {@code between} with one value, or
    * {@code one_of} with none, is accepted by the model and then evaluates as something the
    * caller did not ask for.
    */
   private static void requireValueArity(String operator, List<Object> values, int index) {
      if(VALUELESS.contains(operator)) {
         if(!values.isEmpty()) {
            throw new IllegalArgumentException(
               "Condition " + index + " uses '" + operator + "', which takes no values.");
         }

         return;
      }

      if(values.isEmpty()) {
         throw new IllegalArgumentException(
            "Condition " + index + " uses '" + operator + "' and needs at least one value.");
      }

      if(PAIRED.contains(operator) && values.size() != 2) {
         throw new IllegalArgumentException(
            "Condition " + index + " uses 'between', which needs exactly two values, got " +
            values.size() + ".");
      }
   }

   // ── read direction ────────────────────────────────────────────────────────

   private static String junctionAfter(Object[] conditionList, int index) {
      if(index + 1 < conditionList.length &&
         conditionList[index + 1] instanceof JunctionOperatorModel junction)
      {
         return junction.getType() == JunctionOperator.OR ? "or" : "and";
      }

      return null;
   }

   private static List<Object> values(ConditionValueModel[] values) {
      List<Object> out = new ArrayList<>();

      if(values != null) {
         for(ConditionValueModel value : values) {
            out.add(value == null ? null : value.getValue());
         }
      }

      return out;
   }

   /** An unmapped operation reads back as itself rather than as a guessed token. */
   private static String operatorToken(int operation) {
      for(Map.Entry<String, Integer> entry : OPERATORS.entrySet()) {
         if(entry.getValue() == operation && !isAlias(entry.getKey())) {
            return entry.getKey();
         }
      }

      return "unknown(" + operation + ")";
   }

   private static boolean isAlias(String token) {
      return !CANONICAL.contains(token);
   }

   // ── tables ────────────────────────────────────────────────────────────────

   /** The canonical spelling per operation, used when reading back. */
   private static final Set<String> CANONICAL = Set.of(
      "equals", "one_of", "less_than", "greater_than", "between", "starts_with", "contains",
      "null", "top_n", "date_in", "like");

   private static Map<String, Integer> operators() {
      Map<String, Integer> map = new LinkedHashMap<>();
      map.put("equals", XCondition.EQUAL_TO);
      map.put("=", XCondition.EQUAL_TO);
      map.put("==", XCondition.EQUAL_TO);
      map.put("one_of", XCondition.ONE_OF);
      map.put("oneof", XCondition.ONE_OF);
      map.put("in", XCondition.ONE_OF);
      map.put("less_than", XCondition.LESS_THAN);
      map.put("<", XCondition.LESS_THAN);
      map.put("greater_than", XCondition.GREATER_THAN);
      map.put(">", XCondition.GREATER_THAN);
      map.put("between", XCondition.BETWEEN);
      map.put("starts_with", XCondition.STARTING_WITH);
      map.put("startswith", XCondition.STARTING_WITH);
      map.put("contains", XCondition.CONTAINS);
      map.put("null", XCondition.NULL);
      map.put("is_null", XCondition.NULL);
      map.put("top_n", XCondition.TOP_N);
      map.put("date_in", XCondition.DATE_IN);
      map.put("like", XCondition.LIKE);
      return Collections.unmodifiableMap(map);
   }

   private static Map<String, DataRefModel> index(DataRefModel[] available) {
      Map<String, DataRefModel> fields = new LinkedHashMap<>();

      if(available != null) {
         for(DataRefModel field : available) {
            if(field != null && field.getName() != null) {
               fields.put(field.getName().toLowerCase(), field);
            }
         }
      }

      return fields;
   }

   private static Collection<String> names(Map<String, DataRefModel> fields) {
      List<String> names = new ArrayList<>();

      for(DataRefModel field : fields.values()) {
         names.add(field.getName());
      }

      return names;
   }
}
