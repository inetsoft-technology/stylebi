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
import inetsoft.uql.schema.XSchema;
import inetsoft.util.Tool;
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
   private static final Set<String> VALUELESS = Set.of("null", "is_null");

   /** Operators that take exactly two. */
   private static final Set<String> PAIRED = Set.of("between");

   /** Operations that take exactly one value (Condition.evaluate reads only the first). */
   private static final Set<Integer> SINGLE_VALUED = Set.of(
      XCondition.EQUAL_TO, XCondition.LESS_THAN, XCondition.GREATER_THAN,
      XCondition.STARTING_WITH, XCondition.CONTAINS, XCondition.LIKE, XCondition.DATE_IN);

   private ConditionVocabulary() {
   }

   /**
    * One condition in the flat vocabulary. {@code junction} points at the *next* condition.
    *
    * <p>{@code junctionLevel} is the level of the junction to the next condition, not of this
    * condition itself; it is nullable, and when absent defaults to
    * {@code Math.min(this clause's level, the next clause's level)}. It only needs to be set
    * explicitly to express two independent, side-by-side groups joined at a shallower level than
    * either group's own conditions (e.g. {@code (A OR B) AND (C OR D)}), a shape the default
    * cannot infer because both flanking conditions sit at the same level.
    */
   public record Clause(String field, String operator, List<Object> values, String junction,
                        boolean negated, boolean equal, int level, Integer junctionLevel) {
      public Clause(String field, String operator, List<Object> values, String junction,
                    boolean negated, boolean equal, int level) {
         this(field, operator, values, junction, negated, equal, level, null);
      }
   }

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
            out.add(junction(clause, clauses.get(i + 1), i));
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
            clause.put("equal", condition.isEqual());
            clause.put("level", condition.getLevel());
            clause.put("junction", junctionAfter(conditionList, i));
            clause.put("junctionLevel", junctionLevelAfter(conditionList, i));
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
            "cannot filter on. Available fields: " + fieldList(fields) +
            ". A condition on an unknown column is the recorded cause of a downstream cast " +
            "failure, so it is refused here.");
      }

      String operator = requireOperator(clause.operator(), index);
      int operation = OPERATORS.get(operator);
      List<Object> values = clause.values() == null ? List.of() : clause.values();
      requireValueArity(operator, operation, values, index);

      ConditionModel condition = new ConditionModel();
      condition.setField(field);
      condition.setOperation(operation);
      condition.setNegated(clause.negated());
      condition.setEqual(clause.equal());
      condition.setLevel(clause.level());

      if(operation == XCondition.TOP_N || operation == XCondition.BOTTOM_N) {
         requireExactlyOneValue(values, index);
         condition.setValues(new ConditionValueModel[] { rankingValue(values.get(0), index, fields) });
      }
      else {
         condition.setValues(values.stream().map(raw -> value(raw, index, fields, field))
                                .toArray(ConditionValueModel[]::new));
         requireSessionDataOperator(condition.getValues(), operator, operation, index);
      }

      return condition;
   }

   /**
    * _ROLES_/_GROUPS_ expand to a list of names, which only ONE_OF matches against every entry --
    * under EQUAL_TO the condition silently compares a single one. _USER_ is a single name. This
    * mirrors the pairing the Composer's condition dialog offers (vs-condition-item-pane-provider).
    */
   private static void requireSessionDataOperator(ConditionValueModel[] values, String operator,
                                                  int operation, int index)
   {
      for(ConditionValueModel value : values) {
         if(!ConditionValueModel.SESSION_DATA.equals(value.getType())) {
            continue;
         }

         String name = variableName(value.getValue());
         boolean multi = "_ROLES_".equals(name) || "_GROUPS_".equals(name);

         if(multi && operation != XCondition.ONE_OF) {
            throw new IllegalArgumentException(
               "Condition " + index + " compares against session_data '" + name + "' with '" +
               operator + "', but " + name + " is a list of names and only one_of matches " +
               "against all of them. Use operator one_of.");
         }

         if(!multi && operation != XCondition.EQUAL_TO && operation != XCondition.ONE_OF) {
            throw new IllegalArgumentException(
               "Condition " + index + " compares against session_data '" + name + "' with '" +
               operator + "'; session_data values support only equals (or one_of).");
         }
      }
   }

   /**
    * A plain scalar (string/number/boolean/null) means {@code VALUE}, exactly as before. An
    * object carrying a recognized {@code type} discriminator selects one of the four non-VALUE
    * shapes ConditionUtil actually knows how to read: comparing a column to another column
    * ({@code field}), to a prompted variable ({@code variable}), to a built-in session value
    * ({@code session_data}), or to a computed expression ({@code expression}). SUBQUERY is
    * deliberately not supported here -- its payload is a full sub-query definition with no
    * tractable minimal-field summary.
    */
   private static ConditionValueModel value(Object raw, int index, Map<String, DataRefModel> fields,
                                            DataRefModel targetField)
   {
      if(raw instanceof Map<?, ?> map && map.get("type") instanceof String typeToken) {
         return typedValue(typeToken, map, index, fields);
      }

      // ConditionUtil later force-coerces a literal VALUE against the target field's declared
      // type (Tool.getData(dataType, value)) and silently keeps null on failure -- checking here,
      // one hop earlier, catches a bare string that can't become the field's type (e.g. a column
      // name meant for a {type:"field",...} operand) with a named error instead of a silently
      // null-valued condition that matches zero rows.
      if(raw instanceof String str && !str.isBlank()) {
         String dataType = targetField.getDataType();

         // Tool.getData never returns null for a boolean -- anything but true/false (e.g. "yes")
         // quietly becomes Boolean.FALSE -- so the null check below can't catch it. The
         // Composer's boolean value editor only produces true/false.
         if(XSchema.BOOLEAN.equals(dataType) &&
            !"true".equalsIgnoreCase(str.trim()) && !"false".equalsIgnoreCase(str.trim()))
         {
            throw new IllegalArgumentException(
               "Condition " + index + "'s value '" + str + "' is not a boolean for field '" +
               targetField.getName() + "'. Use true or false.");
         }

         if(!XSchema.STRING.equals(dataType) && Tool.getData(dataType, str) == null) {
            throw new IllegalArgumentException(
               "Condition " + index + "'s value '" + str + "' cannot be interpreted as a " +
               dataType + " for field '" + targetField.getName() + "'. If you meant to compare " +
               "against another column, use {type: \"field\", field: \"" + str + "\"} instead of " +
               "a plain string.");
         }
      }

      ConditionValueModel value = new ConditionValueModel();
      value.setValue(raw);
      value.setType(ConditionValueModel.VALUE);
      return value;
   }

   private static ConditionValueModel typedValue(String typeToken, Map<?, ?> map, int index,
                                                  Map<String, DataRefModel> fields)
   {
      String type = typeToken.trim().toLowerCase();
      ConditionValueModel value = new ConditionValueModel();

      switch(type) {
         case "field" -> {
            String name = requireString(map.get("field"), index, "field");
            DataRefModel resolved = fields.get(name.toLowerCase());

            if(resolved == null) {
               throw new IllegalArgumentException(
                  "Condition " + index + "'s field-typed value names '" + name + "', which this " +
                  "assembly cannot filter on. Available fields: " + fieldList(fields) + ".");
            }

            value.setValue(resolved);
            value.setType(ConditionValueModel.FIELD);
         }
         case "variable" -> {
            String name = requireString(map.get("name"), index, "name");
            value.setValue("$(" + name + ")");
            value.setType(ConditionValueModel.VARIABLE);
            Object choiceQuery = map.get("choiceQuery");

            if(choiceQuery instanceof String query && !query.isBlank()) {
               value.setChoiceQuery(query);
            }
         }
         case "session_data" -> {
            String name = requireString(map.get("name"), index, "name").trim().toUpperCase();

            if(!SESSION_DATA_NAMES.contains(name)) {
               throw new IllegalArgumentException(
                  "Condition " + index + "'s session_data value must be one of " +
                  SESSION_DATA_NAMES + ", got '" + map.get("name") + "'.");
            }

            value.setValue("$(" + name + ")");
            value.setType(ConditionValueModel.SESSION_DATA);
         }
         case "expression" -> {
            String expression = requireString(map.get("expression"), index, "expression");
            Object languageRaw = map.get("language");
            String language = languageRaw == null ? "js" :
               String.valueOf(languageRaw).trim().toLowerCase();

            // Anything other than "sql" used to be stored as JS, so a typo silently changed the
            // expression's language.
            if(!"js".equals(language) && !"javascript".equals(language) &&
               !"sql".equals(language))
            {
               throw new IllegalArgumentException(
                  "Condition " + index + "'s expression value has language '" + languageRaw +
                  "'; language must be js or sql.");
            }

            ExpressionValueModel exprModel = new ExpressionValueModel();
            exprModel.setExpression(expression);
            exprModel.setType("sql".equals(language) ?
               ExpressionValueModel.SQL : ExpressionValueModel.JS);
            value.setValue(exprModel);
            value.setType(ConditionValueModel.EXPRESSION);
         }
         default -> throw new IllegalArgumentException(
            "Condition " + index + " has a value of unknown type '" + typeToken + "'. Supported " +
            "typed values: field, variable, session_data, expression.");
      }

      return value;
   }

   private static String requireString(Object raw, int index, String fieldName) {
      if(!(raw instanceof String str) || str.isBlank()) {
         throw new IllegalArgumentException(
            "Condition " + index + "'s value needs a non-empty '" + fieldName + "'.");
      }

      return str.trim();
   }

   /**
    * {@code TOP_N}/{@code BOTTOM_N} carry a ranking value -- {@code n} and the group-by field --
    * rather than a plain scalar. ConditionUtil unconditionally casts {@code getValue()} to
    * {@code RankingValueModel} for these two operations, so this is the one branch that MUST
    * produce that shape.
    */
   private static ConditionValueModel rankingValue(Object raw, int index,
                                                    Map<String, DataRefModel> fields)
   {
      if(!(raw instanceof Map<?, ?> map)) {
         throw new IllegalArgumentException(
            "Condition " + index + " uses a ranking operator (top_n/bottom_n) and needs a value " +
            "shaped {n, groupField}, not a plain value.");
      }

      Object nRaw = map.get("n");

      if(!(nRaw instanceof Number number) || number.intValue() <= 0 ||
         number.doubleValue() != number.intValue())
      {
         throw new IllegalArgumentException(
            "Condition " + index + "'s ranking value needs a positive integer 'n', got " + nRaw + ".");
      }

      String groupFieldName = requireString(map.get("groupField"), index, "groupField");
      DataRefModel groupField = fields.get(groupFieldName.toLowerCase());

      if(groupField == null) {
         throw new IllegalArgumentException(
            "Condition " + index + "'s ranking groupField '" + groupFieldName + "' is not a " +
            "field this assembly can filter on. Available fields: " + fieldList(fields) + ".");
      }

      RankingValueModel ranking = new RankingValueModel();
      ranking.setN(number.intValue());
      ranking.setDataRef(groupField);

      ConditionValueModel value = new ConditionValueModel();
      value.setValue(ranking);
      value.setType(ConditionValueModel.VALUE);
      return value;
   }

   private static void requireExactlyOneValue(List<Object> values, int index) {
      if(values.size() != 1) {
         throw new IllegalArgumentException(
            "Condition " + index + " uses a ranking operator (top_n/bottom_n), which needs " +
            "exactly one value shaped {n, groupField}, got " + values.size() + ".");
      }
   }

   private static String fieldList(Map<String, DataRefModel> fields) {
      return fields.isEmpty() ? "(none)" : String.join(", ", new TreeSet<>(names(fields)));
   }

   private static JunctionOperatorModel junction(Clause clause, Clause nextClause, int index) {
      String token = clause.junction();
      String name = token.trim().toLowerCase();
      int type = switch(name) {
         case "and", "&&" -> JunctionOperator.AND;
         case "or", "||" -> JunctionOperator.OR;
         default -> throw new IllegalArgumentException(
            "Condition " + index + " has junction '" + token + "'; valid junctions are and, or.");
      };

      JunctionOperatorModel junction = new JunctionOperatorModel();
      junction.setType(type);
      junction.setLevel(clause.junctionLevel() != null
         ? clause.junctionLevel()
         : Math.min(clause.level(), nextClause.level()));
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
   private static void requireValueArity(String operator, int operation, List<Object> values,
                                         int index)
   {
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

      // Condition.evaluate reads only the first value for these, so extra values would be
      // silently ignored.
      if(SINGLE_VALUED.contains(operation) && values.size() != 1) {
         throw new IllegalArgumentException(
            "Condition " + index + " uses '" + operator + "', which takes exactly one value, " +
            "got " + values.size() + "." + (operation == XCondition.EQUAL_TO ?
            " To match any of several values, use operator one_of." : ""));
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

   /**
    * The joining junction's own level, so a caller reading {@code get_condition}'s output and
    * replaying it unchanged into {@code set_condition} doesn't silently lose an explicit
    * {@code junctionLevel} back to the default-inference formula.
    */
   private static Integer junctionLevelAfter(Object[] conditionList, int index) {
      if(index + 1 < conditionList.length &&
         conditionList[index + 1] instanceof JunctionOperatorModel junction)
      {
         return junction.getLevel();
      }

      return null;
   }

   private static List<Object> values(ConditionValueModel[] values) {
      List<Object> out = new ArrayList<>();

      if(values != null) {
         for(ConditionValueModel value : values) {
            out.add(describeValue(value));
         }
      }

      return out;
   }

   /**
    * The reverse of {@link #typedValue} / {@link #rankingValue}: a non-VALUE value reads back as
    * the same typed-object shape it would be written as, rather than leaking the raw Java model
    * (a {@code DataRefModel}, {@code ExpressionValueModel}, ...) into the agent-facing JSON.
    */
   private static Object describeValue(ConditionValueModel value) {
      if(value == null) {
         return null;
      }

      String type = value.getType();

      if(ConditionValueModel.FIELD.equals(type) && value.getValue() instanceof DataRefModel field) {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("type", "field");
         out.put("field", field.getName());
         return out;
      }

      if(ConditionValueModel.VARIABLE.equals(type)) {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("type", "variable");
         out.put("name", variableName(value.getValue()));

         if(value.getChoiceQuery() != null) {
            out.put("choiceQuery", value.getChoiceQuery());
         }

         return out;
      }

      if(ConditionValueModel.SESSION_DATA.equals(type)) {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("type", "session_data");
         out.put("name", variableName(value.getValue()));
         return out;
      }

      if(ConditionValueModel.EXPRESSION.equals(type) &&
         value.getValue() instanceof ExpressionValueModel expr)
      {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("type", "expression");
         out.put("expression", expr.getExpression());
         out.put("language", expr.getType() == ExpressionValueModel.SQL ? "sql" : "js");
         return out;
      }

      if(value.getValue() instanceof RankingValueModel ranking) {
         Map<String, Object> out = new LinkedHashMap<>();
         out.put("n", ranking.getN());
         out.put("groupField", ranking.getDataRef() == null ? null : ranking.getDataRef().getName());
         return out;
      }

      // SUBQUERY and anything else read back as the raw value, unchanged from before -- SUBQUERY's
      // payload has no tractable minimal-field summary (see the write-side note on typedValue).
      return value.getValue();
   }

   private static String variableName(Object value) {
      if(value instanceof String str && str.startsWith("$(") && str.endsWith(")")) {
         return str.substring(2, str.length() - 1);
      }

      return String.valueOf(value);
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
      "null", "top_n", "bottom_n", "date_in", "like");

   /** The only names StyleBI recognizes as built-in session values (see Condition.isSessionVariable). */
   private static final Set<String> SESSION_DATA_NAMES = Set.of("_USER_", "_ROLES_", "_GROUPS_");

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
      map.put("bottom_n", XCondition.BOTTOM_N);
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
