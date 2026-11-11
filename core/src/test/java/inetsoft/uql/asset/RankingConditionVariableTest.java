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
package inetsoft.uql.asset;

import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.DisplayName;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.UserVariable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The N of a top N / bottom N condition taken from a variable. The basic cases are in
 * RankingConditionTest.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RankingConditionVariableTest {
   private static RankingCondition ranking(int op, Object n) {
      RankingCondition cond = new RankingCondition();
      cond.setOperation(op);
      assertTrue(cond.setN(n));
      return cond;
   }

   private static VariableTable vars(Object value) {
      VariableTable vars = new VariableTable();
      vars.put("n", value);
      return vars;
   }

   @Test
   void numberVariable_becomesN() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(5));

      assertEquals(5, cond.getN());
   }

   @Test
   void bottomN_isResolvedTheSameWay() {
      RankingCondition cond = ranking(XCondition.BOTTOM_N, "$(n)");
      cond.replaceVariable(vars(3));

      assertEquals(3, cond.getN());
      assertEquals(XCondition.BOTTOM_N, cond.getOperation());
   }

   @Test
   void userVariable_becomesN() {
      RankingCondition cond = ranking(XCondition.TOP_N, new UserVariable("n"));
      cond.replaceVariable(vars(7));

      assertEquals(7, cond.getN());
   }

   @Test
   void textNumber_isParsed() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars("10"));

      assertEquals(10, cond.getN());
   }

   @Test
   void decimalNumber_isTruncated() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(2.9));

      assertEquals(2, cond.getN());
   }

   @Test
   void longAboveIntRange_isClampedNotWrapped() {
      for(int op : new int[] { XCondition.TOP_N, XCondition.BOTTOM_N }) {
         RankingCondition cond = ranking(op, "$(n)");
         cond.replaceVariable(vars(3000000000L));

         assertEquals(Integer.MAX_VALUE, cond.getN());
      }
   }

   @Test
   void longBelowIntRange_isClampedToZero() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(-3000000000L));

      assertEquals(0, cond.getN());
   }

   @Test
   void negativeInRangeValues_areClampedToZero() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(-5));

      assertEquals(0, cond.getN());

      cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars("-5"));

      assertEquals(0, cond.getN());
   }

   @Test
   void everyNumberType_givesTheSameNAtTheBoundaries() {
      Object[] huge = { 3000000000L, 3000000000.0, 3e9f, new BigInteger("30000000000"),
                        new BigDecimal("3000000000.7"), "3000000000", Double.POSITIVE_INFINITY };
      Object[] hugeNegative = { -3000000000L, -3000000000.0, new BigInteger("-30000000000"),
                                new BigDecimal("-3000000000.7"), "-3000000000",
                                Double.NEGATIVE_INFINITY, Long.MIN_VALUE };
      Object[] exact = { 7, 7L, (short) 7, (byte) 7, 7.0, 7.9f, new BigInteger("7"),
                         new BigDecimal("7.99"), "7", " 7 ", new java.util.concurrent.atomic.AtomicLong(7) };

      for(Object value : huge) {
         RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
         cond.replaceVariable(vars(value));
         assertEquals(Integer.MAX_VALUE, cond.getN(), String.valueOf(value));
      }

      for(Object value : hugeNegative) {
         RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
         cond.replaceVariable(vars(value));
         assertEquals(0, cond.getN(), String.valueOf(value));
      }

      for(Object value : exact) {
         RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
         cond.replaceVariable(vars(value));
         assertEquals(7, cond.getN(), String.valueOf(value));
      }
   }

   @Test
   void intBoundaries_areKept() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars((long) Integer.MAX_VALUE));
      assertEquals(Integer.MAX_VALUE, cond.getN());

      cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(Integer.MAX_VALUE + 1L));
      assertEquals(Integer.MAX_VALUE, cond.getN());
   }

   @Test
   void nan_keepsTheReference() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(Double.NaN));

      assertEquals("$(n)", cond.getN());
   }

   @Test
   void clampedLongN_isNeverNegativeInTheRankingTable() {
      // the resolved N must be usable as the table's ranking N
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(3000000000L));

      assertTrue(cond.getN() instanceof Integer);
      assertTrue((Integer) cond.getN() >= 0);
   }

   @Test
   void multiValueVariable_usesTheFirstValue() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(new Object[] { 4, 9 }));

      assertEquals(4, cond.getN());
   }

   @Test
      // pinned current behavior, #80963 item 8
   void missingVariable_keepsTheReference() {
      // the condition is not ignored: N stays the unresolved reference
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(new VariableTable());

      assertEquals("$(n)", cond.getN());
   }

   @Test
      // pinned current behavior, #80963 item 8
   void nonNumericText_keepsTheReference() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars("abc"));

      assertEquals("$(n)", cond.getN());
   }

   @Test
      // pinned current behavior, #80963 item 8
   void emptyArray_keepsTheReference() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(new VariableTable() {
         @Override
         public Object get(String name) {
            return new Object[0];
         }
      });

      assertEquals("$(n)", cond.getN());
   }

   @Test
   void fixedN_isNotChanged() {
      RankingCondition cond = ranking(XCondition.TOP_N, 5);
      cond.replaceVariable(vars(9));

      assertEquals(5, cond.getN());
   }

   @Test
   void getAllVariables_listsTheReferencedVariable() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");

      UserVariable[] variables = cond.getAllVariables();

      assertEquals(1, variables.length);
      assertEquals("n", variables[0].getName());
   }

   @Test
   void getAllVariables_skipsBuiltinVariables() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(_USER_)");

      assertEquals(0, cond.getAllVariables().length);
   }

   @Test
   void getAllVariables_variableNameHasNoDefaultValue() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");

      UserVariable var = cond.getAllVariables()[0];

      assertEquals(XSchema.INTEGER, var.getTypeNode().getType());
      assertNotNull(var.getValueNode());
      assertNull(var.getValueNode().getValue());
      assertEquals("n", var.getValueNode().getVariable());
   }

   @Test
   void getAllVariables_userVariableHasNoDefaultValue() {
      UserVariable uvar = new UserVariable();
      uvar.setName("n");
      uvar.setTypeNode(XSchema.createPrimitiveType(XSchema.INTEGER));
      RankingCondition cond = ranking(XCondition.TOP_N, uvar);

      UserVariable var = cond.getAllVariables()[0];

      assertNull(var.getValueNode().getValue());
      assertEquals("n", var.getValueNode().getVariable());
   }

   @Test
   void replaceVariable_nullValueKeepsVariable() {
      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");

      cond.replaceVariable(vars(null));
      assertEquals("$(n)", cond.getN());

      cond.replaceVariable(vars(new Object[] {null}));
      assertEquals("$(n)", cond.getN());
   }

   @DisplayName("R02 the same logical N resolves the same way through Long / String / Double")
   @ParameterizedTest(name = "[{index}] {0} vs {1}")
   @MethodSource("equivalentPairs")
   void r02_equivalentInputsResolveTheSame(Object a, Object b) {
      assertSameResolution(a, b);
   }

   @DisplayName("R02 Long 2 and Integer 2 resolve the same")
   @Test
   void r02_smallLongAndInteger() {
      assertSameResolution(2L, 2);
   }

   private static void assertSameResolution(Object a, Object b) {
      RankingCondition ca = ranking(XCondition.TOP_N, "$(n)");
      RankingCondition cb = ranking(XCondition.TOP_N, "$(n)");
      ca.replaceVariable(vars(a));
      cb.replaceVariable(vars(b));

      assertEquals(ca.getN(), cb.getN(), a + " vs " + b);
   }

   static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> equivalentPairs() {
      return java.util.stream.Stream.of(
         org.junit.jupiter.params.provider.Arguments.of(3000000000L, "3000000000"),
         org.junit.jupiter.params.provider.Arguments.of("2.0", 2.0));
   }

   @DisplayName("R04 setN rejects non-Integer, non-reference values and keeps the old N")
   @Test
   void r04_setN() {
      RankingCondition cond = new RankingCondition();
      assertTrue(cond.setN(5));
      assertEquals(5, cond.getN());

      for(Object bad : new Object[] { 3000000000L, "abc" }) {
         assertFalse(cond.setN(bad), "rejected: " + bad);
         assertEquals(5, cond.getN(), "unchanged after " + bad);
      }

      assertTrue(cond.setN(-1));
      assertEquals(-1, cond.getN());
      assertTrue(cond.setN("$(n)"));
      assertEquals("$(n)", cond.getN());
   }

   @DisplayName("R06 a String variable that setN(text) accepts or that is not numeric resolves like setN(text)")
   @ParameterizedTest(name = "[{index}] ''{0}''")
   // lenient numeric strings ("2.0", " 7 ", "3000000000") resolve by design since #8022; see everyNumberType_givesTheSameNAtTheBoundaries
   @ValueSource(strings = { "5", "abc", "" })
   void r06_twinOfSetN(String text) {
      RankingCondition twin = new RankingCondition();
      boolean accepted = twin.setN(text);

      RankingCondition cond = ranking(XCondition.TOP_N, "$(n)");
      cond.replaceVariable(vars(text));

      if(accepted) {
         assertEquals(twin.getN(), cond.getN());
      }
      else {
         assertEquals("$(n)", cond.getN());
      }
   }
}
