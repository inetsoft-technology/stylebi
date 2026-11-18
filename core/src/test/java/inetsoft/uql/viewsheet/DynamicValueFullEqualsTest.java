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
package inetsoft.uql.viewsheet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class DynamicValueFullEqualsTest {
   @Test
   void differentRuntimeValueIsNotFullEqual() {
      DynamicValue a = new DynamicValue("v");
      DynamicValue b = new DynamicValue("v");
      a.setRValue("x");
      b.setRValue("y");

      assertEquals(a, b);
      assertFalse(a.fullEquals(b));
   }

   @Test
   void sameRuntimeValueIsFullEqual() {
      DynamicValue a = new DynamicValue("v");
      DynamicValue b = new DynamicValue("v");
      a.setRValue("x");
      b.setRValue("x");

      assertTrue(a.fullEquals(b));
   }

   @Test
   void differentDesignValueIsNotFullEqual() {
      assertFalse(new DynamicValue("v").fullEquals(new DynamicValue("w")));
   }

   // FullHashObject contract: fullEquals true => fullHashCode equal.
   private static DynamicValue withRValue(Object rvalue) {
      DynamicValue val = new DynamicValue("v");
      val.setRValue(rvalue);
      return val;
   }

   private static void assertContract(Object r1, Object r2) {
      DynamicValue a = withRValue(r1);
      DynamicValue b = withRValue(r2);

      assertTrue(a.fullEquals(b));
      assertEquals(a.fullHashCode(), b.fullHashCode());
   }

   @Test
   void equalArrayRuntimeValuesHaveSameFullHashCode() {
      assertContract(new String[] { "a", "b" }, new String[] { "a", "b" });
      assertContract(new int[] { 1, 2 }, new int[] { 1, 2 });
      assertContract(new Object[] { new int[] { 1 }, "x" }, new Object[] { new int[] { 1 }, "x" });
      assertContract(new Object[] { 1, 2 }, new Object[] { 1.0, 2.0 });
   }

   @Test
   void equalNumbersOfDifferentTypesHaveSameFullHashCode() {
      assertContract(1, 1.0);
      assertContract(1L, 1.0f);
      assertContract(0.0, -0.0);
   }

   @Test
   void numbersBeyondDoublePrecisionKeepContract() {
      assertContract((1L << 53) + 1, 1L << 53);
   }

   @Test
   void differentArrayRuntimeValuesAreNotFullEqual() {
      assertFalse(withRValue(new String[] { "a" }).fullEquals(withRValue(new String[] { "b" })));
   }

   @Test
   void nullRuntimeValueHashIsStable() {
      assertEquals(withRValue(null).fullHashCode(), withRValue(null).fullHashCode());
   }

   @DisplayName("D03 fullEquals edge cases")
   @Test
   void d03_edges() {
      DynamicValue a = new DynamicValue("v");
      assertFalse(a.fullEquals(null));

      FullHashObject other = new FullHashObject() {
         @Override
         public boolean fullEquals(FullHashObject obj) {
            return false;
         }

         @Override
         public int fullHashCode() {
            return 0;
         }
      };
      assertFalse(a.fullEquals(other));

      DynamicValue b = new DynamicValue("v");
      assertTrue(a.fullEquals(b), "both runtime values null");

      b.setRValue("x");
      assertFalse(a.fullEquals(b), "null vs x");
   }

   @DisplayName("D02b pinned current behavior: small numbers of different Java types are fullEqual with equal hash codes")
   @Test
   void d02b_smallNumbers() {
      Object[] values = { 5, 5L, (short) 5 };

      for(Object x : values) {
         for(Object y : values) {
            DynamicValue a = new DynamicValue("v");
            DynamicValue b = new DynamicValue("v");
            a.setRValue(x);
            b.setRValue(y);
            assertTrue(a.fullEquals(b), x.getClass() + " vs " + y.getClass());
            assertEquals(a.fullHashCode(), b.fullHashCode());
         }
      }

      DynamicValue five = new DynamicValue("v");
      DynamicValue six = new DynamicValue("v");
      five.setRValue(5L);
      six.setRValue(6L);
      assertFalse(five.fullEquals(six));
   }
}
