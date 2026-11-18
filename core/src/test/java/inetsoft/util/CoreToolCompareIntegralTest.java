/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.util;

import inetsoft.uql.Condition;
import inetsoft.uql.XCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #81118: integral values above 2^53 must compare exactly, not through double. The axis is
 * the value types of both operands: both integral (Byte/Short/Integer/Long) compares as long,
 * every other combination keeps the double comparison.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CoreToolCompareIntegralTest {
   private static final long P = 9007199254740992L; // 2^53
   private static final long P1 = P + 1;

   private static int cmp(Object a, Object b) {
      return CoreTool.compare(a, b, true, true);
   }

   private static Condition condition(String type, int op, Object... values) {
      Condition cond = new Condition(type);
      cond.setOperation(op);

      for(Object value : values) {
         cond.addValue(value);
      }

      return cond;
   }

   @Test
   void longsAbove2Pow53AreDistinct() {
      assertTrue(cmp(P, P1) < 0);
      assertTrue(cmp(P1, P) > 0);
      assertEquals(0, cmp(P1, P1));
      assertTrue(cmp(Long.MAX_VALUE - 1, Long.MAX_VALUE) < 0);
      assertTrue(cmp(Long.MIN_VALUE, Long.MIN_VALUE + 1) < 0);
   }

   @Test
   void mixedIntegralClassesCompareAsLong() {
      assertTrue(cmp((Integer) 5, P1) < 0);
      assertTrue(cmp(P1, (Short) (short) 5) > 0);
      assertEquals(0, cmp((Byte) (byte) 7, 7L));
      assertEquals(0, cmp((Integer) 7, 7L));
   }

   @Test
   void belowTwoPow53KeepsOrdering() {
      assertTrue(cmp(1, 2) < 0);
      assertTrue(cmp(2L, 1L) > 0);
      assertEquals(0, cmp(42, 42L));
      assertTrue(cmp(-3L, 3) < 0);
   }

   @Test
   void floatingAndMixedKeepDoubleBehavior() {
      assertEquals(0, cmp(42, 42.0));
      assertEquals(0, cmp(42L, 42.0f));
      assertTrue(cmp(42, 42.5) < 0);
      assertTrue(cmp(1.5, 1.25) > 0);
      // mixed integral/floating still goes through double: 2^53+1 rounds to 2^53
      assertEquals(0, cmp(P1, (double) P));
      assertEquals(0, cmp((double) P, P1));
      assertEquals(0, cmp(new java.math.BigDecimal("5"), 5));
   }

   @Test
   void sortOrderOfLongsAround2Pow53() {
      List<Long> list = new ArrayList<>(Arrays.asList(P1 + 1, P, P1, P - 1));
      list.sort(new DefaultComparator());
      assertEquals(Arrays.asList(P - 1, P, P1, P1 + 1), list);
   }

   @Test
   void equalToCondition() {
      for(String type : new String[] { XSchema.LONG, XSchema.INTEGER }) {
         Condition cond = condition(type, XCondition.EQUAL_TO, P1);
         assertFalse(cond.evaluate(P), type + " different long");
         assertTrue(cond.evaluate(P1), type + " same long");
      }
   }

   @Test
   void lessThanCondition() {
      Condition cond = condition(XSchema.LONG, XCondition.LESS_THAN, P1);
      assertTrue(cond.evaluate(P));
      assertFalse(cond.evaluate(P1));
      assertFalse(cond.evaluate(P1 + 1));

      Condition cond2 = condition(XSchema.LONG, XCondition.GREATER_THAN, P);
      assertTrue(cond2.evaluate(P1));
      assertFalse(cond2.evaluate(P));
   }

   @Test
   void oneOfCondition() {
      for(String type : new String[] { XSchema.LONG, XSchema.INTEGER }) {
         Condition cond = condition(type, XCondition.ONE_OF, P1, P1 + 2);
         assertFalse(cond.evaluate(P), type);
         assertTrue(cond.evaluate(P1), type);
         assertFalse(cond.evaluate(P1 + 1), type);
         assertTrue(cond.evaluate(P1 + 2), type);
      }
   }

   @Test
   void betweenCondition() {
      Condition cond = condition(XSchema.LONG, XCondition.BETWEEN, P1, P1 + 2);
      assertFalse(cond.evaluate(P));
      assertTrue(cond.evaluate(P1));
      assertTrue(cond.evaluate(P1 + 1));
      assertTrue(cond.evaluate(P1 + 2));
      assertFalse(cond.evaluate(P1 + 3));
   }

   @Test
   void conditionMixedLongAndDoubleUnchanged() {
      Condition cond = condition(XSchema.DOUBLE, XCondition.EQUAL_TO, 42.0);
      assertTrue(cond.evaluate(42L));
      assertFalse(cond.evaluate(43L));

      Condition lng = condition(XSchema.LONG, XCondition.EQUAL_TO, 42);
      assertTrue(lng.evaluate(42.0));
      assertFalse(lng.evaluate(42.5));
   }
}
