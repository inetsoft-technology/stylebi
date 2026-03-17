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
package inetsoft.uql.asset;

import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RankingCondition}.
 */
public class RankingConditionTest {

   // ---- default constructor ----------------------------------------------------

   @Test
   void defaultConstructor_topNOperation() {
      RankingCondition cond = new RankingCondition();
      assertEquals(XCondition.TOP_N, cond.getOperation());
   }

   @Test
   void defaultConstructor_nIsZero() {
      RankingCondition cond = new RankingCondition();
      assertEquals(0, cond.getN());
   }

   @Test
   void defaultConstructor_groupOthersFalse() {
      RankingCondition cond = new RankingCondition();
      assertFalse(cond.isGroupOthers());
   }

   // ---- setOperation -----------------------------------------------------------

   @Test
   void setOperation_topN_succeeds() {
      RankingCondition cond = new RankingCondition();
      cond.setOperation(XCondition.TOP_N);
      assertEquals(XCondition.TOP_N, cond.getOperation());
   }

   @Test
   void setOperation_bottomN_succeeds() {
      RankingCondition cond = new RankingCondition();
      cond.setOperation(XCondition.BOTTOM_N);
      assertEquals(XCondition.BOTTOM_N, cond.getOperation());
   }

   @Test
   void setOperation_illegalValue_throwsRuntimeException() {
      RankingCondition cond = new RankingCondition();
      assertThrows(RuntimeException.class, () -> cond.setOperation(XCondition.EQUAL_TO));
   }

   @Test
   void setOperation_equalsOperation_throwsRuntimeException() {
      RankingCondition cond = new RankingCondition();
      assertThrows(RuntimeException.class, () -> cond.setOperation(XCondition.LESS_THAN));
   }

   // ---- setN -------------------------------------------------------------------

   @Test
   void setN_integer_succeeds() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.setN(5));
      assertEquals(5, cond.getN());
   }

   @Test
   void setN_zero_succeeds() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.setN(0));
      assertEquals(0, cond.getN());
   }

   @Test
   void setN_validIntegerString_succeeds() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.setN("10"));
      assertEquals(10, cond.getN());
   }

   @Test
   void setN_invalidString_returnsFalse() {
      RankingCondition cond = new RankingCondition();
      assertFalse(cond.setN("notANumber"));
   }

   @Test
   void setN_variableString_succeeds() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.setN("$(myVar)"));
      assertEquals("$(myVar)", cond.getN());
   }

   @Test
   void setN_null_returnsFalse() {
      RankingCondition cond = new RankingCondition();
      assertFalse(cond.setN(null));
   }

   @Test
   void setN_doubleValue_returnsFalse() {
      RankingCondition cond = new RankingCondition();
      // Double is not Integer, String, or UserVariable — returns false
      assertFalse(cond.setN(3.14));
   }

   // ---- groupOthers ------------------------------------------------------------

   @Test
   void setGroupOthers_true() {
      RankingCondition cond = new RankingCondition();
      cond.setGroupOthers(true);
      assertTrue(cond.isGroupOthers());
   }

   @Test
   void setGroupOthers_false() {
      RankingCondition cond = new RankingCondition();
      cond.setGroupOthers(true);
      cond.setGroupOthers(false);
      assertFalse(cond.isGroupOthers());
   }

   // ---- evaluate ---------------------------------------------------------------

   @Test
   void evaluate_alwaysReturnsTrue() {
      RankingCondition cond = new RankingCondition();
      cond.setN(3);
      cond.setOperation(XCondition.TOP_N);
      // evaluate() always returns true; actual ranking is handled by table filters
      assertTrue(cond.evaluate("anything"));
      assertTrue(cond.evaluate(null));
      assertTrue(cond.evaluate(42));
   }

   // ---- isValid ----------------------------------------------------------------

   @Test
   void isValid_noRef_returnsTrue() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.isValid());
   }

   @Test
   void isValid_withRef_noColumns_returnsTrue() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.isValid(null));
   }

   // ---- isTypeChangeable / isOperationChangeable / isEqualChangeable / isNegatedChangeable ----

   @Test
   void isTypeChangeable_returnsFalse() {
      assertFalse(new RankingCondition().isTypeChangeable());
   }

   @Test
   void isOperationChangeable_returnsTrue() {
      assertTrue(new RankingCondition().isOperationChangeable());
   }

   @Test
   void isEqualChangeable_returnsFalse() {
      assertFalse(new RankingCondition().isEqualChangeable());
   }

   @Test
   void isNegatedChangeable_returnsFalse() {
      assertFalse(new RankingCondition().isNegatedChangeable());
   }

   // ---- equals / hashCode ------------------------------------------------------

   @Test
   void equals_sameNAndOp_areEqual() {
      RankingCondition c1 = new RankingCondition();
      c1.setN(5);
      c1.setOperation(XCondition.TOP_N);

      RankingCondition c2 = new RankingCondition();
      c2.setN(5);
      c2.setOperation(XCondition.TOP_N);

      assertEquals(c1, c2);
   }

   @Test
   void equals_differentN_notEqual() {
      RankingCondition c1 = new RankingCondition();
      c1.setN(5);

      RankingCondition c2 = new RankingCondition();
      c2.setN(10);

      assertNotEquals(c1, c2);
   }

   @Test
   void equals_differentOp_notEqual() {
      RankingCondition c1 = new RankingCondition();
      c1.setOperation(XCondition.TOP_N);

      RankingCondition c2 = new RankingCondition();
      c2.setOperation(XCondition.BOTTOM_N);

      assertNotEquals(c1, c2);
   }

   @Test
   void equals_notRankingCondition_returnsFalse() {
      RankingCondition c1 = new RankingCondition();
      assertNotEquals(c1, "a string");
      assertNotEquals(c1, null);
   }

   @Test
   void hashCode_equalObjects_sameHashCode() {
      RankingCondition c1 = new RankingCondition();
      c1.setN(5);

      RankingCondition c2 = new RankingCondition();
      c2.setN(5);

      assertEquals(c1.hashCode(), c2.hashCode());
   }

   // ---- clone ------------------------------------------------------------------

   @Test
   void clone_producesEqualObject() {
      RankingCondition original = new RankingCondition();
      original.setN(7);
      original.setOperation(XCondition.BOTTOM_N);
      original.setGroupOthers(true);

      RankingCondition clone = original.clone();
      assertNotNull(clone);
      assertNotSame(original, clone);
      assertEquals(7, clone.getN());
      assertEquals(XCondition.BOTTOM_N, clone.getOperation());
      assertTrue(clone.isGroupOthers());
   }

   // ---- toString ---------------------------------------------------------------

   @Test
   void toString_topN_containsN() {
      RankingCondition cond = new RankingCondition();
      cond.setN(5);
      cond.setOperation(XCondition.TOP_N);
      String s = cond.toString();
      assertNotNull(s);
      assertTrue(s.contains("5"), "toString should contain n value '5'");
   }

   @Test
   void toString_bottomN_containsN() {
      RankingCondition cond = new RankingCondition();
      cond.setN(3);
      cond.setOperation(XCondition.BOTTOM_N);
      String s = cond.toString();
      assertNotNull(s);
      assertTrue(s.contains("3"), "toString should contain n value '3'");
   }

   // ---- getAllVariables when no variable set ------------------------------------

   @Test
   void getAllVariables_noVariable_returnsEmptyArray() {
      RankingCondition cond = new RankingCondition();
      cond.setN(5);
      assertEquals(0, cond.getAllVariables().length);
   }

   // ---- replaceVariable --------------------------------------------------------

   @Test
   void replaceVariable_withVariableN_replacesValue() throws Exception {
      RankingCondition cond = new RankingCondition();
      cond.setN("$(topN)");

      VariableTable vars = new VariableTable();
      vars.put("topN", 10);
      cond.replaceVariable(vars);

      assertEquals(10, cond.getN());
   }

   @Test
   void replaceVariable_withStringVariableN_replacesValue() throws Exception {
      RankingCondition cond = new RankingCondition();
      cond.setN("$(topN)");

      VariableTable vars = new VariableTable();
      vars.put("topN", "7");
      cond.replaceVariable(vars);

      assertEquals(7, cond.getN());
   }

   // ---- parameterized: n values -----------------------------------------------

   @ParameterizedTest
   @ValueSource(ints = {0, 1, 5, 10, 100})
   void setN_variousIntegerValues_succeeds(int n) {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.setN(n));
      assertEquals(n, cond.getN());
   }
}
