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

import inetsoft.report.composition.event.AssetEventUtil;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.DateCondition;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;
import inetsoft.web.composer.model.condition.ExpressionValueModel;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Judges a condition's {@code variable} operands by the value the variable holds now (bug #79535).
 *
 * <p>A variable operand is resolved only when the table renders, so a value that cannot work
 * for the field and operator (a free-text period for {@code date_in}, a numeric Slider against a
 * date column) used to be stored without a word and surface later as an empty table, or as a raw
 * database error. The variable may legitimately change afterwards, so most findings are
 * <b>warnings</b>; only a {@code date_in} variable holding a definite non-range is refused,
 * because the runtime accepts built-in date range names only (matched case-insensitively) and
 * anything else matches no rows.
 *
 * <p>The value-versus-field rules are {@link ConditionVocabulary#literalMismatch}'s, the same
 * ones a literal value is held to.
 */
final class VariableOperandCheck {
   private VariableOperandCheck() {
   }

   /**
    * Checks every variable operand of {@code conditionList} against {@code vars}.
    *
    * @param vars        the variables that hold a value now
    *                    ({@code ViewsheetSandbox.getAllVariables()}); null means unknown and
    *                    nothing is judged
    * @param declared    names of the variables declared on the base worksheet (see
    *                    {@link #declaredNames}); one that holds no value is reported as "no
    *                    value yet" rather than "not defined" (bug #80214); null means none
    * @param refuseDateIn throw for a {@code date_in} variable that holds a definite non-range;
    *                    otherwise that case is a warning too
    * @param warnings    receives the findings
    * @throws IllegalArgumentException when {@code refuseDateIn} and a variable cannot be a range
    */
   static void check(Object[] conditionList, VariableTable vars, Set<String> declared,
                     boolean refuseDateIn, List<String> warnings)
   {
      if(conditionList == null || vars == null) {
         return;
      }

      int index = -1;

      for(Object item : conditionList) {
         if(!(item instanceof ConditionModel condition)) {
            continue;
         }

         index++;

         if(condition.getValues() == null || condition.getField() == null) {
            continue;
         }

         for(ConditionValueModel value : condition.getValues()) {
            if(value != null && value.getValue() instanceof ExpressionValueModel expression) {
               checkExpression(index, condition, expression, vars,
                               declared == null ? Set.of() : declared, warnings);
               continue;
            }

            if(value == null || !ConditionValueModel.VARIABLE.equals(value.getType())) {
               continue;
            }

            checkOperand(index, condition, ConditionVocabulary.variableName(value.getValue()),
                         vars, declared == null ? Set.of() : declared, refuseDateIn, warnings);
         }
      }
   }

   /**
    * The names of the variables declared (variable assemblies) on the viewsheet's base
    * worksheet, whether or not they hold a value, plus those a base-worksheet table references
    * only from a condition (bug #80347); the latter are what collect_parameters also lists.
    * Empty when there is no base worksheet or it cannot be read.
    */
   static Set<String> declaredNames(ViewsheetSandbox box) {
      Set<String> names = new HashSet<>();

      try {
         Worksheet ws = box == null ? null : box.getWorksheet();

         addViewsheetVariables(box, names);

         if(ws != null) {
            for(UserVariable variable : AssetEventUtil.getWSAllVariables(ws)) {
               names.add(variable.getName());
            }

            try {
               UserVariable[] used = ws.getAllVariables();

               if(used != null) {
                  for(UserVariable variable : used) {
                     names.add(variable.getName());
                  }
               }
            }
            catch(Exception e) {
               // e.g. WorksheetWrapper.getAllVariables is unsupported; keep the assembly names
            }
         }
      }
      catch(Exception e) {
         return new HashSet<>();
      }

      return names;
   }

   /**
    * Adds the variables the viewsheet's own assemblies reference (a condition, a highlight),
    * minus the disabled ones: collect_parameters lists those too (bug #80646).
    */
   private static void addViewsheetVariables(ViewsheetSandbox box, Set<String> names) {
      try {
         Viewsheet vs = box == null ? null : box.getViewsheet();

         if(vs == null) {
            return;
         }

         Set<String> disabled = vs.getDisabledVariables();
         UserVariable[] used = vs.getAllVariables();

         if(used != null) {
            for(UserVariable variable : used) {
               if(variable != null &&
                  (disabled == null || !disabled.contains(variable.getName())))
               {
                  names.add(variable.getName());
               }
            }
         }
      }
      catch(Exception e) {
         // keep whatever was collected
      }
   }

   /**
    * Vets {@code parameter.Name} / {@code parameter['Name']} reads of an expression operand the
    * way a variable operand is vetted (bug #80646). A name that resolves is left alone.
    */
   private static void checkExpression(int index, ConditionModel condition,
                                       ExpressionValueModel expression, VariableTable vars,
                                       Set<String> declared, List<String> warnings)
   {
      if(condition.getField() == null) {
         return;
      }

      String use = "condition " + index + " (" + condition.getField().getName() + " " +
         ConditionVocabulary.operatorToken(condition.getOperation()) + ")";

      for(String name : expressionParameterNames(expression.getExpression())) {
         if(!resolves(vars, name) && !EXPRESSION_BUILT_INS.contains(name)) {
            warnUnresolved(use, name, declared, warnings);
         }
      }
   }

   private static final Set<String> EXPRESSION_BUILT_INS = Set.of(
      "_BEGINNING_OF_YEAR", "_END_OF_YEAR", "_BEGINNING_OF_QUARTER", "_END_OF_QUARTER",
      "_BEGINNING_OF_MONTH", "_END_OF_MONTH", "_BEGINNING_OF_WEEK", "_END_OF_WEEK",
      "_TODAY", "_USER_", "_ROLES_", "_GROUPS_");

   private static final Set<String> OBJECT_MEMBERS = Set.of(
      "constructor", "prototype", "toString", "valueOf", "hasOwnProperty", "isPrototypeOf",
      "propertyIsEnumerable", "toLocaleString", "__proto__");

   private static final Pattern PARAMETER_READ = Pattern.compile(
      "(?<![\\w$.])parameter(?:\\s*\\.\\s*([A-Za-z_$][\\w$]*)|" +
      "\\s*\\[\\s*(?:'([^']+)'|\"([^\"]+)\")\\s*\\])");

   /** The names read off {@code parameter} in a script, ignoring string literals and comments. */
   static List<String> expressionParameterNames(String script) {
      List<String> names = new ArrayList<>();

      if(script == null) {
         return names;
      }

      List<int[]> ranges = literalRanges(script);
      Matcher matcher = PARAMETER_READ.matcher(script);

      while(matcher.find()) {
         int at = matcher.start();

         if(ranges.stream().anyMatch(r -> at >= r[0] && at <= r[1])) {
            continue;
         }

         String name = matcher.group(1) != null ? matcher.group(1) :
            matcher.group(2) != null ? matcher.group(2) : matcher.group(3);

         if(name != null && !names.contains(name) && !OBJECT_MEMBERS.contains(name)) {
            names.add(name);
         }
      }

      return names;
   }

   private static List<int[]> literalRanges(String script) {
      List<int[]> ranges = new ArrayList<>();
      int i = 0;

      while(i < script.length()) {
         char ch = script.charAt(i);
         char next = i + 1 < script.length() ? script.charAt(i + 1) : 0;

         if(ch == '\'' || ch == '"' || ch == '`') {
            int j = i + 1;

            while(j < script.length() && script.charAt(j) != ch) {
               j += script.charAt(j) == '\\' ? 2 : 1;
            }

            ranges.add(new int[]{ i, j });
            i = j + 1;
         }
         else if(ch == '/' && next == '/') {
            int end = script.indexOf('\n', i);
            int stop = end < 0 ? script.length() : end;
            ranges.add(new int[]{ i, stop });
            i = stop;
         }
         else if(ch == '/' && next == '*') {
            int end = script.indexOf("*/", i + 2);
            int stop = end < 0 ? script.length() : end + 2;
            ranges.add(new int[]{ i, stop });
            i = stop;
         }
         else {
            i++;
         }
      }

      return ranges;
   }

   private static void warnUnresolved(String use, String name, Set<String> declared,
                                      List<String> warnings)
   {
      if(declared.contains(name)) {
         warnings.add("Variable '" + name + "' used by " + use + " is declared on the base " +
                      "worksheet or used by one of the viewsheet's conditions but has no value " +
                      "yet, so StyleBI ignores this condition: it currently matches every row " +
                      "(a highlight paints all rows, a filter removes nothing) until a value is " +
                      "passed (set_parameters).");
      }
      else {
         warnings.add("Variable '" + name + "' used by " + use + " is not defined: this " +
                      "viewsheet has no input assembly or variable with that name (yet), so " +
                      "StyleBI ignores this condition: it currently matches every row (a " +
                      "highlight paints all rows, a filter removes nothing) until it exists " +
                      "and is given a value.");
      }
   }

   private static void checkOperand(int index, ConditionModel condition, String name,
                                    VariableTable vars, Set<String> declared,
                                    boolean refuseDateIn, List<String> warnings)
   {
      DataRefModel field = condition.getField();
      int operation = condition.getOperation();
      String use = "condition " + index + " (" + field.getName() + " " +
         ConditionVocabulary.operatorToken(operation) + ")";

      if(!resolves(vars, name)) {
         warnUnresolved(use, name, declared, warnings);
         return;
      }

      Object current = valueOf(vars, name);
      List<Object> elements = elements(current);

      if(elements.isEmpty()) {
         warnings.add("Variable '" + name + "' used by " + use + " has no value yet, so " +
                      "there is nothing to compare against; what the condition matches until a " +
                      "value is set depends on the operator.");
         return;
      }

      if(elements.size() > 1 &&
         (ConditionVocabulary.SINGLE_VALUED.contains(operation) || operation == XCondition.BETWEEN))
      {
         String multi = condition.isNegated() ? "not_one_of" : "one_of";
         warnings.add("Variable '" + name + "' used by " + use + " currently holds " +
                      elements.size() + " values " + display(current) + ", but " +
                      ConditionVocabulary.operatorToken(operation) + " uses only the first " +
                      "value, so the condition would silently compare against '" +
                      elements.get(0) + "' alone. Use operator " + multi + " to match every " +
                      "value, or make the variable hold a single value.");
      }

      for(Object element : elements) {
         String problem = problem(element, field, operation);

         if(problem == null) {
            continue;
         }

         if(operation == XCondition.DATE_IN && refuseDateIn) {
            throw new IllegalArgumentException(
               "Variable '" + name + "' used by " + use + " currently holds '" + element +
               "'" + problem + " Nothing was changed.");
         }

         warnings.add("Variable '" + name + "' used by " + use + " currently holds '" + element +
                      "'" + problem);
      }
   }

   /** What is wrong with {@code element} as this operator's value, or null when nothing is. */
   private static String problem(Object element, DataRefModel field, int operation) {
      if(operation == XCondition.DATE_IN) {
         return isBuiltinRange(element) || blank(element) ? null :
            ", which is not a built-in date range name, so date_in would match no rows on '" +
            field.getName() + "'. Set the variable to one of: " + builtinRangeNames() +
            " (call list_condition_date_ranges).";
      }

      if(operation == XCondition.STARTING_WITH || operation == XCondition.CONTAINS ||
         operation == XCondition.LIKE || blank(element) ||
         !(element instanceof String || element instanceof Number || element instanceof Boolean))
      {
         return null;
      }

      String mismatch = ConditionVocabulary.literalMismatch(element, field, operation);
      return mismatch == null ? null :
         ", which does not fit this condition: " + mismatch +
         " The table may fail to load or show no rows until the variable holds a fitting value.";
   }

   /**
    * The variable operands of {@code conditionList} with the value each holds now, worded for an
    * error ("QtySlider = 5 (Order:Date greater_than)"). Empty when there are none.
    */
   static String describeOperands(Object[] conditionList, VariableTable vars) {
      List<String> parts = new ArrayList<>();

      if(conditionList == null) {
         return "";
      }

      for(Object item : conditionList) {
         if(!(item instanceof ConditionModel condition) || condition.getValues() == null ||
            condition.getField() == null)
         {
            continue;
         }

         for(ConditionValueModel value : condition.getValues()) {
            if(value == null || !ConditionValueModel.VARIABLE.equals(value.getType())) {
               continue;
            }

            String name = ConditionVocabulary.variableName(value.getValue());
            Object current = vars != null && resolves(vars, name) ? valueOf(vars, name) : null;
            parts.add(name + (current == null ? " (no value)" : " = '" + display(current) + "'") +
                      " on " + condition.getField().getName() + " " +
                      ConditionVocabulary.operatorToken(condition.getOperation()));
         }
      }

      return String.join("; ", parts);
   }

   static boolean hasVariableOperand(Object[] conditionList) {
      if(conditionList == null) {
         return false;
      }

      for(Object item : conditionList) {
         if(item instanceof ConditionModel condition && condition.getValues() != null) {
            for(ConditionValueModel value : condition.getValues()) {
               if(value != null && ConditionValueModel.VARIABLE.equals(value.getType())) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   private static boolean resolves(VariableTable vars, String name) {
      return vars.contains(name) || VariableTable.isBuiltinVariable(name);
   }

   private static Object valueOf(VariableTable vars, String name) {
      try {
         return vars.get(name);
      }
      catch(Exception e) {
         return null;
      }
   }

   private static List<Object> elements(Object value) {
      List<Object> out = new ArrayList<>();

      if(value instanceof Object[] array) {
         out.addAll(Arrays.asList(array));
      }
      else if(value instanceof Collection<?> collection) {
         out.addAll(collection);
      }
      else if(value != null) {
         out.add(value);
      }

      out.removeIf(Objects::isNull);
      return out;
   }

   private static String display(Object value) {
      if(value instanceof Object[] array) {
         return Arrays.toString(array);
      }

      return String.valueOf(value);
   }

   private static boolean blank(Object element) {
      return element instanceof String str && str.isBlank();
   }

   private static boolean isBuiltinRange(Object element) {
      if(!(element instanceof String str)) {
         return false;
      }

      for(DateCondition range : DateCondition.getBuiltinDateConditions()) {
         if(str.equalsIgnoreCase(range.getName())) {
            return true;
         }
      }

      return false;
   }

   private static String builtinRangeNames() {
      List<String> names = new ArrayList<>();

      for(DateCondition range : DateCondition.getBuiltinDateConditions()) {
         names.add("'" + range.getName() + "'");
      }

      return String.join(", ", names);
   }
}
