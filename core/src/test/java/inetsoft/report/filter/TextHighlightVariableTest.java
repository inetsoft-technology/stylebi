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
package inetsoft.report.filter;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.*;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #80843: a highlight condition whose variable operand has no value must stay ignored
 * (it matches every row, like an ignored filter condition) for every operator, negated or not.
 * TextHighlight.replaceVariables used to clear the ignored flag right after setting it, so the
 * placeholder was compared as a literal and the outcome depended on the operator and negation.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TextHighlightVariableTest {
   private static final int[] ROWS = { 100, 29999, 30000, 50000 };

   /** An unresolved variable ignores the condition: every row matches. */
   @ParameterizedTest
   @CsvSource({
      "LESS_THAN,false,true", "LESS_THAN,true,true",
      "GREATER_THAN,false,true", "GREATER_THAN,true,true",
      "EQUAL_TO,false,true", "EQUAL_TO,true,true",
      "LESS_THAN,false,false", "LESS_THAN,true,false",
      "GREATER_THAN,false,false", "GREATER_THAN,true,false"
   })
   void unresolvedVariableMatchesEveryRow(String op, boolean negated, boolean userVariable) {
      ConditionList list = new ConditionList();
      list.append(item(condition(op, negated, variable(userVariable)), 0));

      Highlight highlight = highlight(list);
      highlight.replaceVariables(new VariableTable());

      ConditionGroup group = new ConditionGroup(0, highlight.getConditionGroup(), null);

      for(int row : ROWS) {
         assertTrue(group.evaluate(new Object[] { row }),
                    op + " negated=" + negated + " row=" + row);
      }
   }

   /** A resolved variable evaluates normally, including the negation. */
   @Test
   void resolvedVariableEvaluatesNormally() throws Exception {
      VariableTable vars = new VariableTable();
      vars.put("dfltVar", 30000);

      assertArrayEquals(new boolean[] { true, true, false, false },
                        matches("LESS_THAN", false, vars));
      assertArrayEquals(new boolean[] { false, false, true, true },
                        matches("LESS_THAN", true, vars));
      assertArrayEquals(new boolean[] { false, false, false, true },
                        matches("GREATER_THAN", false, vars));
      assertArrayEquals(new boolean[] { true, true, true, false },
                        matches("GREATER_THAN", true, vars));
   }

   /** In an AND list only the unresolved item is dropped, the other item still filters. */
   @Test
   void unresolvedItemInAndListIsDropped() {
      ConditionList list = new ConditionList();
      list.append(item(condition("LESS_THAN", true, variable(true)), 0));
      list.append(new JunctionOperator(JunctionOperator.AND, 0));
      list.append(item(condition("GREATER_THAN", false, 29999), 0));

      Highlight highlight = highlight(list);
      highlight.replaceVariables(new VariableTable());
      ConditionGroup group = new ConditionGroup(0, highlight.getConditionGroup(), null);

      assertFalse(group.evaluate(new Object[] { 100 }));
      assertFalse(group.evaluate(new Object[] { 29999 }));
      assertTrue(group.evaluate(new Object[] { 30000 }));
   }

   /** In an OR list the unresolved item is dropped too and the remaining item decides. */
   @Test
   void unresolvedItemInOrListIsDropped() {
      ConditionList list = new ConditionList();
      list.append(item(condition("GREATER_THAN", true, variable(true)), 0));
      list.append(new JunctionOperator(JunctionOperator.OR, 0));
      list.append(item(condition("EQUAL_TO", false, 100), 0));

      Highlight highlight = highlight(list);
      highlight.replaceVariables(new VariableTable());
      ConditionGroup group = new ConditionGroup(0, highlight.getConditionGroup(), null);

      assertTrue(group.evaluate(new Object[] { 100 }));
      assertFalse(group.evaluate(new Object[] { 50000 }));
   }

   private static boolean[] matches(String op, boolean negated, VariableTable vars) {
      ConditionList list = new ConditionList();
      list.append(item(condition(op, negated, variable(true)), 0));
      Highlight highlight = highlight(list);
      highlight.replaceVariables(vars);
      ConditionGroup group = new ConditionGroup(0, highlight.getConditionGroup(), null);
      boolean[] result = new boolean[ROWS.length];

      for(int i = 0; i < ROWS.length; i++) {
         result[i] = group.evaluate(new Object[] { ROWS[i] });
      }

      return result;
   }

   private static Object variable(boolean userVariable) {
      return userVariable ? new UserVariable("dfltVar") : "$(dfltVar)";
   }

   private static AssetCondition condition(String op, boolean negated, Object value) {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(operation(op));
      // a "$(name)" string is only recognised as a variable when the condition type keeps it a
      // string; a numeric type converts it before replaceVariable sees it (unrelated to #80843)
      cond.setType(value instanceof String ? XSchema.STRING : XSchema.INTEGER);
      cond.setNegated(negated);
      cond.addValue(value);
      return cond;
   }

   private static int operation(String op) {
      return switch(op) {
         case "LESS_THAN" -> XCondition.LESS_THAN;
         case "GREATER_THAN" -> XCondition.GREATER_THAN;
         default -> XCondition.EQUAL_TO;
      };
   }

   private static ConditionItem item(XCondition cond, int level) {
      return new ConditionItem(new AttributeRef(null, "REVENUE"), cond, level);
   }

   private static Highlight highlight(ConditionList list) {
      TextHighlight highlight = new TextHighlight();
      highlight.setName("h");
      highlight.setConditionGroup(list);
      return highlight;
   }
}
