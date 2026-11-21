/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.wiz.controller;

import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XPartition;
import inetsoft.uql.jdbc.XFilterNode;
import inetsoft.web.composer.model.condition.ConditionUtil;
import inetsoft.web.portal.model.database.Operation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit test of the VPM definition validator: every shape, arity and operand rule is named by
 * field and code, nothing reaches {@code createVPM} (which would NPE on an unknown operator, F9).
 */
@Tag("core")
class WizVpmValidatorTest {
   private final XDataModel dataModel = dataModel();
   private final WizVpmValidator validator = new WizVpmValidator(null);

   private static XDataModel dataModel() {
      XDataModel model = mock(XDataModel.class);
      when(model.getPartition("pv")).thenReturn(new XPartition("pv"));
      return model;
   }

   // -- the valid definition ----------------------------------------------------------------

   @Test
   void aFullValidDefinitionHasNoErrors() {
      Map<String, Object> hidden = new LinkedHashMap<>();
      hidden.put("roles", List.of("Analyst"));
      hidden.put("script", "hide();");
      hidden.put("columns", List.of(column("ORDERS", "COST")));
      Map<String, Object> definition = definition(condition("c1", tableTarget(),
         group("and", clause("field", "ORDERS.REGION", "=", "value", "East"),
               group("or", unary("ORDERS.X", "IS NULL"),
                     between("ORDERS.AMT", "1", "9")))));
      definition.put("description", "d");
      definition.put("lookup", "var a = 1;");
      definition.put("hidden", hidden);

      WizVpmValidator.Result result = validate(definition);

      assertEquals(List.of(), result.errors());
      assertEquals(List.of(
         "TARGET_UNVERIFIED: conditions[c1].target.name: table \"ORDERS\" cannot be verified " +
            "without reading the database metadata"), result.warnings());
   }

   @Test
   void aScriptOnlyConditionAndAScriptWithATreeBothPass() {
      Map<String, Object> scriptOnly = condition("s", tableTarget(), null);
      scriptOnly.put("script", "result = true;");
      Map<String, Object> both = condition("b", tableTarget(),
         clause("field", "T.A", "=", "value", "1"));
      both.put("script", "// kept");

      assertEquals(List.of(), validate(definition(scriptOnly, both)).errors());
   }

   // -- operators and arity -----------------------------------------------------------------

   @Test
   void everyServerOperatorIsAcceptedWithItsArity() {
      List<String> symbols = new ArrayList<>();

      for(String name : XFilterNode.getAllOperators()) {
         String symbol = XFilterNode.getOpSymbol(name);

         if(!symbols.contains(symbol)) {
            symbols.add(symbol);
         }
      }

      assertTrue(symbols.containsAll(List.of("=", ">", "<", ">=", "<=", "<>", "LIKE", "MATCH", "IN",
                                             "BETWEEN", "IS NULL", "EXISTS", "UNIQUE")), "" + symbols);

      for(String symbol : symbols) {
         int arity = WizVpmValidator.arity(symbol);
         assertTrue(arity >= 1, symbol + " has an arity");
         Map<String, Object> clause = new LinkedHashMap<>();
         clause.put("left", "EXISTS".equals(symbol) || "UNIQUE".equals(symbol) ?
            operand("subquery", null) : operand("field", "T.A"));
         clause.put("op", symbol);

         if(arity >= 2) {
            clause.put("right", operand("value", "1"));
         }

         if(arity == 3) {
            clause.put("right2", operand("value", "2"));
         }

         if("EXISTS".equals(symbol) || "UNIQUE".equals(symbol)) {
            ((Map<String, Object>) clause.get("left")).put("sql", "select 1 from T");
         }

         assertEquals(List.of(), validate(definition(condition("c", tableTarget(), clause)))
                         .errors(), symbol);
      }
   }

   @Test
   void thePortalOperationListIsASubsetOfWhatTheValidatorAccepts() {
      for(Operation op : ConditionUtil.getOperationList()) {
         assertTrue(WizVpmValidator.arity(WizVpmMapper.canonicalOp(op.getSymbol())) >= 1,
                    op.getSymbol());
      }
   }

   @Test
   void operatorsAreMatchedIgnoringCaseAndLocalizedNamesAreRefused() {
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(),
         unary("T.A", "is null")))).errors());
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "like", "value", "x")))).errors());
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "!=", "value", "x")))).errors());

      WizDataModelWire.Err bad = first(validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "equal to", "value", "x")))));
      assertEquals("OPERATION_INVALID", bad.code());
      assertEquals("conditions[c].tree.op", bad.field());
      assertTrue(bad.message().contains("valid operators: "), bad.message());
      assertTrue(bad.message().contains("BETWEEN"), bad.message());

      assertEquals("OPERATION_INVALID", first(validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "", "value", "x"))))).code());
   }

   @Test
   void arityIsEnforcedAndExtraOperandsAreRefusedNotDropped() {
      Map<String, Object> unaryWithRight = unary("T.A", "IS NULL");
      unaryWithRight.put("right", operand("value", "x"));
      assertEquals("OPERAND_ARITY", first(validate(definition(
         condition("c", tableTarget(), unaryWithRight)))).code());

      Map<String, Object> binaryNoRight = new LinkedHashMap<>();
      binaryNoRight.put("left", operand("field", "T.A"));
      binaryNoRight.put("op", "=");
      assertEquals("conditions[c].tree.right", first(validate(definition(
         condition("c", tableTarget(), binaryNoRight)))).field());

      Map<String, Object> binaryWithRight2 = clause("field", "T.A", "=", "value", "1");
      binaryWithRight2.put("right2", operand("value", "2"));
      assertEquals("conditions[c].tree.right2", first(validate(definition(
         condition("c", tableTarget(), binaryWithRight2)))).field());

      Map<String, Object> trinaryNoRight2 = clause("field", "T.A", "BETWEEN", "value", "1");
      assertEquals("conditions[c].tree.right2", first(validate(definition(
         condition("c", tableTarget(), trinaryNoRight2)))).field());
   }

   @Test
   void existsAndUniqueNeedASubqueryOperand() {
      Map<String, Object> exists = new LinkedHashMap<>();
      exists.put("left", operand("field", "T.A"));
      exists.put("op", "EXISTS");

      WizDataModelWire.Err err = first(validate(definition(condition("c", tableTarget(), exists))));
      assertEquals("OPERAND_TYPE_INVALID", err.code());
      assertEquals("conditions[c].tree.left.type", err.field());
   }

   // -- operands ----------------------------------------------------------------------------

   @Test
   void operandRulesAreNamedByField() {
      assertOperandError("OPERAND_TYPE_INVALID", "frobnicate", "x");
      assertOperandError("OPERAND_TYPE_INVALID", "variable", "$(region)");
      assertOperandError("OPERAND_TYPE_INVALID", "variable", "re)gion");
      assertOperandError("OPERAND_TYPE_INVALID", "variable", "_USER_");
      assertOperandError("OPERAND_TYPE_INVALID", "session", "region");
      assertOperandError("OPERAND_TYPE_INVALID", "value", "$(region)");
      assertOperandError("OPERAND_TYPE_INVALID", "expression", "$(region)");
      assertOperandError("REQUIRED", "field", " ");
      assertOperandError("REQUIRED", "expression", "");
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "=", "session", "_ROLES_")))).errors());
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "=", "VARIABLE", "region")))).errors());
      // an empty value is a value
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "=", "value", "")))).errors());
   }

   private void assertOperandError(String code, String type, String expression) {
      WizVpmValidator.Result result = validate(definition(condition("c", tableTarget(),
         clause("field", "T.A", "=", type, expression))));
      assertEquals(code, first(result).code(), type + " " + expression);
      assertTrue(first(result).field().startsWith("conditions[c].tree.right"), first(result).field());
   }

   @Test
   void subqueryOperandRules() {
      Map<String, Object> clause = new LinkedHashMap<>();
      clause.put("left", subquery("select 1 from T"));
      clause.put("op", "EXISTS");
      assertEquals(List.of(), validate(definition(condition("c", tableTarget(), clause))).errors());

      Map<String, Object> noSql = new LinkedHashMap<>();
      noSql.put("left", operand("subquery", null));
      noSql.put("op", "EXISTS");
      assertEquals("REQUIRED", first(validate(definition(
         condition("c", tableTarget(), noSql)))).code());

      Map<String, Object> withExpression = subquery("select 1 from T");
      withExpression.put("expression", "x");
      Map<String, Object> both = new LinkedHashMap<>();
      both.put("left", withExpression);
      both.put("op", "EXISTS");
      assertEquals("conditions[c].tree.left.expression", first(validate(definition(
         condition("c", tableTarget(), both)))).field());

      // sql belongs to subquery only
      Map<String, Object> field = operand("field", "T.A");
      field.put("sql", "select 1");
      Map<String, Object> sqlOnField = new LinkedHashMap<>();
      sqlOnField.put("left", field);
      sqlOnField.put("op", "IS NULL");
      assertEquals("UNKNOWN_KEY", first(validate(definition(
         condition("c", tableTarget(), sqlOnField)))).code());

      // the check, when the controller supplies one, speaks for unusable SQL
      WizVpmValidator checking = new WizVpmValidator(sql -> "the SQL cannot be parsed");
      WizVpmValidator.Result result = checking.validate(
         dataModel, definition(condition("c", tableTarget(), clause)),
         WizVpmValidator.Mode.CREATE, null, null);
      assertEquals("OPERAND_TYPE_INVALID", first(result).code());
      assertEquals("conditions[c].tree.left.sql", first(result).field());
   }

   // -- tree shape --------------------------------------------------------------------------

   @Test
   void groupShapeIsChecked() {
      assertEquals("TREE_SHAPE_INVALID", first(validate(definition(condition("c", tableTarget(),
         group("and", clause("field", "T.A", "=", "value", "1")))))).code());
      assertEquals("OPERATION_INVALID", first(validate(definition(condition("c", tableTarget(),
         group("xor", clause("field", "T.A", "=", "value", "1"),
               clause("field", "T.B", "=", "value", "2")))))).code());

      Map<String, Object> notBoolean = group("and", clause("field", "T.A", "=", "value", "1"),
                                             clause("field", "T.B", "=", "value", "2"));
      notBoolean.put("not", "yes");
      assertEquals("TYPE_INVALID", first(validate(definition(
         condition("c", tableTarget(), notBoolean)))).code());

      Map<String, Object> deep = clause("field", "T.A", "=", "value", "1");

      for(int i = 0; i < 25; i++) {
         deep = group("and", deep, clause("field", "T.B", "=", "value", "2"));
      }

      assertTrue(validate(definition(condition("c", tableTarget(), deep))).errors().stream()
                    .anyMatch(e -> e.message().contains("nested deeper than")));
   }

   @Test
   void conditionRules() {
      assertEquals("CONDITION_DUPLICATE", first(validate(definition(
         condition("c", tableTarget(), clause("field", "T.A", "=", "value", "1")),
         condition("c", tableTarget(), clause("field", "T.A", "=", "value", "1"))))).code());

      Map<String, Object> noTarget = condition("c", null, clause("field", "T.A", "=", "value", "1"));
      noTarget.remove("target");
      assertEquals("conditions[c].target", first(validate(definition(noTarget))).field());

      assertEquals("TREE_SHAPE_INVALID", first(validate(definition(
         condition("c", tableTarget(), null)))).code());

      Map<String, Object> badKind = new LinkedHashMap<>();
      badKind.put("kind", "column");
      badKind.put("name", "T");
      assertEquals("TARGET_INVALID", first(validate(definition(condition(
         "c", badKind, clause("field", "T.A", "=", "value", "1"))))).code());

      // a physical view target must exist, a table target is only warned about
      Map<String, Object> missingView = new LinkedHashMap<>();
      missingView.put("kind", "physicalView");
      missingView.put("name", "nope");
      WizDataModelWire.Err err = first(validate(definition(condition(
         "c", missingView, clause("field", "T.A", "=", "value", "1")))));
      assertEquals("TARGET_NOT_FOUND", err.code());
      assertEquals("conditions[c].target.name", err.field());
      Map<String, Object> view = new LinkedHashMap<>();
      view.put("kind", "physicalView");
      view.put("name", "pv");
      assertEquals(List.of(), validate(definition(condition(
         "c", view, clause("field", "T.A", "=", "value", "1")))).errors());
   }

   @Test
   void unknownKeysAreNamedAtEveryLevel() {
      Map<String, Object> definition = definition(condition("c", tableTarget(),
         clause("field", "T.A", "=", "value", "1")));
      definition.put("colunm", 1);
      assertEquals("definition.colunm", first(validate(definition)).field());

      Map<String, Object> clause = clause("field", "T.A", "=", "value", "1");
      clause.put("operator", "=");
      assertEquals("conditions[c].tree.operator", first(validate(definition(
         condition("c", tableTarget(), clause)))).field());

      Map<String, Object> target = tableTarget();
      target.put("type", "table");
      assertEquals("conditions[c].target.type", first(validate(definition(
         condition("c", target, clauseOf())))).field());
   }

   @Test
   void nameIsRequiredExceptForADraft() {
      Map<String, Object> definition = definition();
      definition.remove("name");
      assertEquals("name", first(validate(definition)).field());
      assertEquals(List.of(), validator.validate(dataModel, definition, WizVpmValidator.Mode.DRAFT,
                                                 null, null).errors());
      assertEquals("definition", first(validator.validate(
         dataModel, null, WizVpmValidator.Mode.CREATE, null, null)).field());
   }

   // -- hidden columns ----------------------------------------------------------------------

   @Test
   void hiddenColumnRules() {
      Map<String, Object> hidden = new LinkedHashMap<>();
      hidden.put("roles", List.of("Analyst"));
      hidden.put("columns", List.of(column("T", "A"), column("T", "A")));
      Map<String, Object> definition = definition();
      definition.put("hidden", hidden);
      assertEquals("hidden.columns[1]", first(validate(definition)).field());

      hidden.put("columns", List.of(column("T", " ")));
      assertEquals("hidden.columns[0].column", first(validate(definition)).field());

      hidden.put("columns", List.of(Map.of("table", 5, "column", "A")));
      assertEquals("hidden.columns[0].table", first(validate(definition)).field());

      hidden.put("roles", "Analyst");
      hidden.put("columns", List.of());
      assertEquals("hidden.roles", first(validate(definition)).field());

      hidden.put("roles", List.of("known", "ghost"));
      WizVpmValidator.Result result = validator.validate(
         dataModel, definition, WizVpmValidator.Mode.CREATE, null, role -> role.equals("known"));
      assertEquals(List.of(), result.errors());
      assertEquals(List.of("ROLE_UNKNOWN: hidden.roles[1]: no role \"ghost\" exists yet"),
                   result.warnings());
   }

   // -- bounds ------------------------------------------------------------------------------

   @Test
   void errorsAreCollectedUpToFifty() {
      List<Object> conditions = new ArrayList<>();

      for(int i = 0; i < 80; i++) {
         conditions.add(condition("c" + i, tableTarget(), clause("field", "T.A", "nope", "value", "1")));
      }

      Map<String, Object> definition = definition();
      definition.put("conditions", conditions);
      assertEquals(WizVpmValidator.MAX_ERRORS, validate(definition).errors().size());
   }

   // -- builders ----------------------------------------------------------------------------

   private WizVpmValidator.Result validate(Map<String, Object> definition) {
      return validator.validate(dataModel, definition, WizVpmValidator.Mode.CREATE, null, null);
   }

   private static WizDataModelWire.Err first(WizVpmValidator.Result result) {
      assertFalse(result.errors().isEmpty(), "expected an error");
      return result.errors().get(0);
   }

   private static Map<String, Object> definition(Map<String, Object>... conditions) {
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("name", "Rule");
      definition.put("conditions", new ArrayList<>(Arrays.asList(conditions)));
      return definition;
   }

   private static Map<String, Object> condition(String name, Map<String, Object> target,
                                                Object tree)
   {
      Map<String, Object> condition = new LinkedHashMap<>();
      condition.put("name", name);
      condition.put("target", target);

      if(tree != null) {
         condition.put("tree", tree);
      }

      return condition;
   }

   private static Map<String, Object> clauseOf() {
      return clause("field", "T.A", "=", "value", "1");
   }

   private static Map<String, Object> tableTarget() {
      Map<String, Object> target = new LinkedHashMap<>();
      target.put("kind", "table");
      target.put("name", "ORDERS");
      return target;
   }

   private static Map<String, Object> operand(String type, String expression) {
      Map<String, Object> operand = new LinkedHashMap<>();
      operand.put("type", type);

      if(expression != null) {
         operand.put("expression", expression);
      }

      return operand;
   }

   private static Map<String, Object> subquery(String sql) {
      Map<String, Object> operand = new LinkedHashMap<>();
      operand.put("type", "subquery");
      operand.put("sql", sql);
      return operand;
   }

   private static Map<String, Object> clause(String leftType, String left, String op,
                                             String rightType, String right)
   {
      Map<String, Object> clause = new LinkedHashMap<>();
      clause.put("left", operand(leftType, left));
      clause.put("op", op);
      clause.put("right", operand(rightType, right));
      return clause;
   }

   private static Map<String, Object> unary(String left, String op) {
      Map<String, Object> clause = new LinkedHashMap<>();
      clause.put("left", operand("field", left));
      clause.put("op", op);
      return clause;
   }

   private static Map<String, Object> between(String left, String low, String high) {
      Map<String, Object> clause = clause("field", left, "BETWEEN", "value", low);
      clause.put("right2", operand("value", high));
      return clause;
   }

   private static Map<String, Object> group(String op, Object... items) {
      Map<String, Object> group = new LinkedHashMap<>();
      group.put("op", op);
      group.put("items", new ArrayList<>(Arrays.asList(items)));
      return group;
   }

   private static Map<String, Object> column(String table, String column) {
      Map<String, Object> map = new LinkedHashMap<>();
      map.put("table", table);
      map.put("column", column);
      return map;
   }
}
