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

import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.DateCondition;
import inetsoft.web.binding.drm.DataRefModel;
import inetsoft.web.composer.model.condition.ConditionModel;
import inetsoft.web.composer.model.condition.ConditionValueModel;

import java.util.*;

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
    * @param vars        the variables in scope ({@code ViewsheetSandbox.getAllVariables()}); null
    *                    means unknown and nothing is judged
    * @param refuseDateIn throw for a {@code date_in} variable that holds a definite non-range;
    *                    otherwise that case is a warning too
    * @param warnings    receives the findings
    * @throws IllegalArgumentException when {@code refuseDateIn} and a variable cannot be a range
    */
   static void check(Object[] conditionList, VariableTable vars, boolean refuseDateIn,
                     List<String> warnings)
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
            if(value == null || !ConditionValueModel.VARIABLE.equals(value.getType())) {
               continue;
            }

            checkOperand(index, condition, ConditionVocabulary.variableName(value.getValue()),
                         vars, refuseDateIn, warnings);
         }
      }
   }

   private static void checkOperand(int index, ConditionModel condition, String name,
                                    VariableTable vars, boolean refuseDateIn,
                                    List<String> warnings)
   {
      DataRefModel field = condition.getField();
      int operation = condition.getOperation();
      String use = "condition " + index + " (" + field.getName() + " " +
         ConditionVocabulary.operatorToken(operation) + ")";

      if(!resolves(vars, name)) {
         warnings.add("Variable '" + name + "' used by " + use + " is not defined: this " +
                      "viewsheet has no input assembly or variable with that name (yet), so " +
                      "the condition has nothing to compare against until it exists.");
         return;
      }

      Object current = valueOf(vars, name);
      List<Object> elements = elements(current);

      if(elements.isEmpty()) {
         warnings.add("Variable '" + name + "' used by " + use + " has no value yet, so the " +
                      "condition has nothing to compare against until it is set.");
         return;
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
