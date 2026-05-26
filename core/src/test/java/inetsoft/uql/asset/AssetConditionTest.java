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

import inetsoft.uql.XCondition;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AssetCondition}.
 */
public class AssetConditionTest {

   // ---- constructors -----------------------------------------------------------

   @Test
   void defaultConstructor_typeIsString() {
      AssetCondition cond = new AssetCondition();
      assertEquals(XSchema.STRING, cond.getType());
   }

   @Test
   void typeConstructor_setsType() {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      assertEquals(XSchema.INTEGER, cond.getType());
   }

   // ---- evaluate — inherits from Condition ------------------------------------

   @Test
   void evaluate_equalToString_matches() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      assertTrue(cond.evaluate("hello"));
   }

   @Test
   void evaluate_equalToString_doesNotMatch() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void evaluate_equalToNull_valueIsNull() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      assertFalse(cond.evaluate(null));
   }

   @Test
   void evaluate_lessThan_integer() {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      cond.setOperation(XCondition.LESS_THAN);
      cond.addValue(10);
      assertTrue(cond.evaluate(5));
      assertFalse(cond.evaluate(15));
   }

   @Test
   void evaluate_greaterThan_integer() {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      cond.setOperation(XCondition.GREATER_THAN);
      cond.addValue(10);
      assertTrue(cond.evaluate(15));
      assertFalse(cond.evaluate(5));
   }

   @Test
   void evaluate_between_integer() {
      AssetCondition cond = new AssetCondition(XSchema.INTEGER);
      cond.setOperation(XCondition.BETWEEN);
      cond.addValue(1);
      cond.addValue(10);
      assertTrue(cond.evaluate(5));
      assertFalse(cond.evaluate(0));
      assertFalse(cond.evaluate(11));
   }

   @Test
   void evaluate_oneOf_string() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");
      cond.addValue("b");
      cond.addValue("c");
      assertTrue(cond.evaluate("b"));
      assertFalse(cond.evaluate("d"));
   }

   @Test
   void evaluate_nullOperator_matchesNull() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.NULL);
      assertTrue(cond.evaluate(null));
      assertTrue(cond.evaluate(""));
      assertFalse(cond.evaluate("value"));
   }

   @Test
   void evaluate_startingWith_matches() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.STARTING_WITH);
      cond.addValue("hel");
      assertTrue(cond.evaluate("hello"));
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void evaluate_contains_matches() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.CONTAINS);
      cond.addValue("ell");
      assertTrue(cond.evaluate("hello"));
      assertFalse(cond.evaluate("world"));
   }

   @Test
   void evaluate_negated_reversesResult() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setNegated(true);
      cond.addValue("hello");
      assertFalse(cond.evaluate("hello"));
      assertTrue(cond.evaluate("world"));
   }

   // ---- caching: lvalue optimization -------------------------------------------

   @Test
   void evaluate_sameValueTwice_consistentResult() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");
      cond.addValue("b");

      // Evaluate same value twice — should use cached result
      assertTrue(cond.evaluate("a"));
      assertTrue(cond.evaluate("a")); // should hit the lvalue cache
   }

   @Test
   void evaluate_differentValue_doesNotUseCachedResult() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");

      assertTrue(cond.evaluate("a"));
      assertFalse(cond.evaluate("b")); // different value must re-evaluate
   }

   // ---- reset ------------------------------------------------------------------

   @Test
   void reset_allowsReevaluation() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.ONE_OF);
      cond.addValue("a");

      assertTrue(cond.evaluate("a"));
      cond.reset();
      // After reset lvalue is Tool.NULL, next call re-evaluates
      assertTrue(cond.evaluate("a"));
   }

   // ---- getSubQueryValue with no sub query -------------------------------------

   @Test
   void getSubQueryValue_noValues_returnsNull() {
      AssetCondition cond = new AssetCondition();
      assertNull(cond.getSubQueryValue());
   }

   @Test
   void getSubQueryValue_nonSubQueryValue_returnsNull() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("notASubQuery");
      assertNull(cond.getSubQueryValue());
   }

   @Test
   void getSubQueryValue_multipleValues_returnsNull() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("a");
      cond.addValue("b");
      assertNull(cond.getSubQueryValue());
   }

   // ---- getValues falls through to super when no sub -------------------------

   @Test
   void getValues_returnsCurrentValues() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("x");
      cond.addValue("y");
      assertEquals(2, cond.getValues().size());
   }

   // ---- setIgnored -------------------------------------------------------------

   @Test
   void ignored_alwaysReturnsTrue() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setOperation(XCondition.EQUAL_TO);
      cond.addValue("hello");
      cond.setIgnored(true);
      assertTrue(cond.evaluate("completely different"));
   }

   // ---- replaceVariable clears ignored -----------------------------------------

   @Test
   void replaceVariable_clearsIgnoredFlag() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.setIgnored(true);
      cond.replaceVariable(null);
      // replaceVariable with null vart resets ignored to false
      assertFalse(cond.isIgnored());
   }

   // ---- getDependeds with no sub query values ----------------------------------

   @Test
   void getDependeds_noSubQuery_emptySet() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("plainValue");
      java.util.Set<AssemblyRef> set = new java.util.HashSet<>();
      cond.getDependeds(set);
      assertTrue(set.isEmpty());
   }

   // ---- getAllVariables ---------------------------------------------------------

   @Test
   void getAllVariables_noVariables_returnsEmptyArray() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("plainValue");
      assertEquals(0, cond.getAllVariables().length);
   }

   @Test
   void getAllVariables_withVariableString_returnsVariable() {
      AssetCondition cond = new AssetCondition(XSchema.STRING);
      cond.addValue("$(myParam)");
      inetsoft.uql.schema.UserVariable[] vars = cond.getAllVariables();
      assertEquals(1, vars.length);
      assertEquals("myParam", vars[0].getName());
   }
}
