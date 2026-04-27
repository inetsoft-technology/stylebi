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
package inetsoft.uql;

import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link XConditionGroup}.
 */
public class XConditionGroupTest {

   // ---- empty group -----------------------------------------------------------

   @Test
   void emptyGroup_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      assertTrue(group.evaluate(new Object[]{"anything"}));
   }

   @Test
   void emptyGroup_returnsTrue_noArgs() {
      XConditionGroup group = new XConditionGroup();
      // evaluate(Object) dispatches to evaluate0(Object[]) — passes single-element array
      assertTrue(group.evaluate("anything"));
   }

   // ---- single condition -------------------------------------------------------

   @Test
   void singleCondition_matchingValue_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      Condition cond = equalStringCondition("hello");
      group.addCondition(0, cond, 0);

      assertTrue(group.evaluate(new Object[]{"hello"}));
   }

   @Test
   void singleCondition_nonMatchingValue_returnsFalse() {
      XConditionGroup group = new XConditionGroup();
      Condition cond = equalStringCondition("hello");
      group.addCondition(0, cond, 0);

      assertFalse(group.evaluate(new Object[]{"world"}));
   }

   @Test
   void singleConditionEvaluateWithSingleValue_matchingValue_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      Condition cond = equalStringCondition("hello");
      group.addCondition(0, cond, 0);

      // When values.length == 1, it uses values[0] regardless of col index
      assertTrue(group.evaluate(new Object[]{"hello"}));
   }

   // ---- AND combination --------------------------------------------------------

   @Test
   void andCombination_bothTrue_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.AND, 0);
      group.addCondition(1, cond2, 0);

      assertTrue(group.evaluate(new Object[]{"hello", "world"}));
   }

   @Test
   void andCombination_firstFalse_returnsFalse() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.AND, 0);
      group.addCondition(1, cond2, 0);

      assertFalse(group.evaluate(new Object[]{"WRONG", "world"}));
   }

   @Test
   void andCombination_secondFalse_returnsFalse() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.AND, 0);
      group.addCondition(1, cond2, 0);

      assertFalse(group.evaluate(new Object[]{"hello", "WRONG"}));
   }

   @Test
   void andCombination_bothFalse_returnsFalse() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.AND, 0);
      group.addCondition(1, cond2, 0);

      assertFalse(group.evaluate(new Object[]{"WRONG1", "WRONG2"}));
   }

   // ---- OR combination ---------------------------------------------------------

   @Test
   void orCombination_firstTrue_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.OR, 0);
      group.addCondition(1, cond2, 0);

      assertTrue(group.evaluate(new Object[]{"hello", "WRONG"}));
   }

   @Test
   void orCombination_secondTrue_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.OR, 0);
      group.addCondition(1, cond2, 0);

      assertTrue(group.evaluate(new Object[]{"WRONG", "world"}));
   }

   @Test
   void orCombination_bothTrue_returnsTrue() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.OR, 0);
      group.addCondition(1, cond2, 0);

      assertTrue(group.evaluate(new Object[]{"hello", "world"}));
   }

   @Test
   void orCombination_bothFalse_returnsFalse() {
      XConditionGroup group = new XConditionGroup();
      Condition cond1 = equalStringCondition("hello");
      Condition cond2 = equalStringCondition("world");
      group.addCondition(0, cond1, 0);
      group.addOperator(XConditionGroup.OR, 0);
      group.addCondition(1, cond2, 0);

      assertFalse(group.evaluate(new Object[]{"WRONG1", "WRONG2"}));
   }

   // ---- negated conditions -----------------------------------------------------

   @Test
   void negatedCondition_evaluatesCorrectly() {
      XConditionGroup group = new XConditionGroup();
      Condition cond = equalStringCondition("hello");
      cond.setNegated(true);
      group.addCondition(0, cond, 0);

      assertFalse(group.evaluate(new Object[]{"hello"}));
      assertTrue(group.evaluate(new Object[]{"world"}));
   }

   // ---- three conditions with mixed AND/OR ------------------------------------

   @Test
   void threeConditions_andAndOr() {
      // (col0=="a" AND col1=="b") OR col2=="c"
      // Level 0 items: groups at level 1 get AND, OR at level 0
      XConditionGroup group = new XConditionGroup();
      Condition c1 = equalStringCondition("a");
      Condition c2 = equalStringCondition("b");
      Condition c3 = equalStringCondition("c");

      group.addCondition(0, c1, 1);
      group.addOperator(XConditionGroup.AND, 1);
      group.addCondition(1, c2, 1);
      group.addOperator(XConditionGroup.OR, 0);
      group.addCondition(2, c3, 0);

      // c1 and c2 both match — AND true, OR with c3 doesn't matter
      assertTrue(group.evaluate(new Object[]{"a", "b", "WRONG"}));
      // c1 fails, but c3 matches — OR true
      assertTrue(group.evaluate(new Object[]{"WRONG", "b", "c"}));
      // none match
      assertFalse(group.evaluate(new Object[]{"WRONG", "WRONG", "WRONG"}));
   }

   // ---- notFoundResult ---------------------------------------------------------

   @Test
   void notFoundResult_colOutOfBounds_defaultTrue() {
      XConditionGroup group = new XConditionGroup();
      group.setNotFoundResult(true);
      Condition cond = equalStringCondition("hello");
      group.addCondition(5, cond, 0); // col 5 doesn't exist in a 2-element array

      assertTrue(group.evaluate(new Object[]{"a", "b"}));
   }

   @Test
   void notFoundResult_colOutOfBounds_setFalse() {
      XConditionGroup group = new XConditionGroup();
      group.setNotFoundResult(false);
      Condition cond = equalStringCondition("hello");
      group.addCondition(5, cond, 0); // col 5 doesn't exist in a 2-element array

      assertFalse(group.evaluate(new Object[]{"a", "b"}));
   }

   // ---- size / clear / getItem -------------------------------------------------

   @Test
   void size_afterAddingItems() {
      XConditionGroup group = new XConditionGroup();
      assertEquals(0, group.size());
      group.addCondition(0, equalStringCondition("a"), 0);
      assertEquals(1, group.size());
      group.addOperator(XConditionGroup.AND, 0);
      assertEquals(2, group.size());
      group.addCondition(1, equalStringCondition("b"), 0);
      assertEquals(3, group.size());
   }

   @Test
   void clear_removesAllItems() {
      XConditionGroup group = new XConditionGroup();
      group.addCondition(0, equalStringCondition("a"), 0);
      group.addOperator(XConditionGroup.AND, 0);
      group.addCondition(1, equalStringCondition("b"), 0);
      group.clear();
      assertEquals(0, group.size());
      // cleared group evaluates as empty — returns true
      assertTrue(group.evaluate(new Object[]{"a", "b"}));
   }

   @Test
   void getItem_returnsCorrectType() {
      XConditionGroup group = new XConditionGroup();
      Condition cond = equalStringCondition("hello");
      group.addCondition(0, cond, 0);
      group.addOperator(XConditionGroup.AND, 0);

      assertInstanceOf(XConditionGroup.CondItem.class, group.getItem(0));
      assertInstanceOf(XConditionGroup.Operator.class, group.getItem(1));
   }

   // ---- addOperator: no-op if list is empty or last item is Operator -----------

   @Test
   void addOperator_noopWhenListEmpty() {
      XConditionGroup group = new XConditionGroup();
      group.addOperator(XConditionGroup.AND, 0);
      assertEquals(0, group.size());
   }

   @Test
   void addOperator_noopWhenLastItemIsAlreadyOperator() {
      XConditionGroup group = new XConditionGroup();
      group.addCondition(0, equalStringCondition("a"), 0);
      group.addOperator(XConditionGroup.AND, 0);
      // Second operator should be ignored since last item is already Operator
      group.addOperator(XConditionGroup.OR, 0);
      assertEquals(2, group.size());
   }

   // ---- clone ------------------------------------------------------------------

   @Test
   void clone_producesIndependentCopy() {
      XConditionGroup group = new XConditionGroup();
      group.addCondition(0, equalStringCondition("hello"), 0);

      XConditionGroup clone = group.clone();
      assertNotNull(clone);
      assertEquals(group.size(), clone.size());

      // Modifying the clone should not affect the original
      clone.clear();
      assertEquals(1, group.size());
      assertEquals(0, clone.size());
   }

   // ---- single-element array dispatch ------------------------------------------

   @Test
   void evaluate_scalarObject_treatedAsSingleElementArray() {
      XConditionGroup group = new XConditionGroup();
      Condition cond = equalStringCondition("hello");
      group.addCondition(0, cond, 0);

      // passing a plain String, not an array
      assertTrue(group.evaluate("hello"));
      assertFalse(group.evaluate("world"));
   }

   // ---- helper -----------------------------------------------------------------

   private Condition equalStringCondition(String value) {
      Condition cond = new Condition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue(value);
      return cond;
   }
}
