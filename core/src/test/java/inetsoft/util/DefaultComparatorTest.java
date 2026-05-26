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
package inetsoft.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DefaultComparatorTest {

   @Test
   void stringVsStringCaseSensitiveLess() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare("apple", "banana") < 0);
   }

   @Test
   void stringVsStringCaseSensitiveGreater() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare("banana", "apple") > 0);
   }

   @Test
   void stringVsStringCaseSensitiveEqual() {
      DefaultComparator cmp = new DefaultComparator();
      assertEquals(0, cmp.compare("apple", "apple"));
   }

   @Test
   void stringVsStringCaseSensitiveDiffersByCase() {
      DefaultComparator cmp = new DefaultComparator();
      // case-sensitive: "Apple" != "apple" (uppercase A < lowercase a in Unicode)
      assertNotEquals(0, cmp.compare("Apple", "apple"));
   }

   @Test
   void stringVsStringCaseInsensitiveEqual() {
      DefaultComparator cmp = new DefaultComparator();
      cmp.setCaseSensitive(false);
      assertEquals(0, cmp.compare("Apple", "apple"));
   }

   @Test
   void stringVsStringCaseInsensitiveOrdering() {
      DefaultComparator cmp = new DefaultComparator();
      cmp.setCaseSensitive(false);
      assertTrue(cmp.compare("APPLE", "BANANA") < 0);
      assertTrue(cmp.compare("BANANA", "APPLE") > 0);
   }

   @Test
   void integerVsIntegerComparison() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare(1, 2) < 0);
      assertTrue(cmp.compare(5, 3) > 0);
      assertEquals(0, cmp.compare(7, 7));
   }

   @Test
   void doubleVsDoubleComparison() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare(1.5, 2.5) < 0);
      assertTrue(cmp.compare(3.14, 2.71) > 0);
      assertEquals(0, cmp.compare(1.0, 1.0));
   }

   @Test
   void longVsLongComparison() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare(100L, 200L) < 0);
      assertTrue(cmp.compare(200L, 100L) > 0);
      assertEquals(0, cmp.compare(999L, 999L));
   }

   @Test
   void mixedNumberAndStringDefaultBehavior() {
      DefaultComparator cmp = new DefaultComparator();
      // with parseNumber=true (default), "5" should compare to 3 as numbers
      // CoreTool.compare will parse "5" → 5.0, compare numerically with 3
      assertTrue(cmp.compare("5", 3) > 0);
      assertTrue(cmp.compare("2", 10) < 0);
   }

   @Test
   void nullBothSideReturnsZero() {
      DefaultComparator cmp = new DefaultComparator();
      assertEquals(0, cmp.compare(null, null));
   }

   @Test
   void nullFirstIsLess() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare(null, "anything") < 0);
   }

   @Test
   void nullSecondIsGreater() {
      DefaultComparator cmp = new DefaultComparator();
      assertTrue(cmp.compare("anything", null) > 0);
   }

   @Test
   void negateReversesResult() {
      DefaultComparator cmp = new DefaultComparator();
      cmp.setNegate(true);

      // "apple" < "banana" normally, negated → positive
      assertTrue(cmp.compare("apple", "banana") > 0);
      // "banana" > "apple" normally, negated → negative
      assertTrue(cmp.compare("banana", "apple") < 0);
      // equal remains zero after negation (0 * -1 = 0)
      assertEquals(0, cmp.compare("same", "same"));
   }

   @Test
   void negateIsReflectedInIsNegate() {
      DefaultComparator cmp = new DefaultComparator();
      assertFalse(cmp.isNegate());

      cmp.setNegate(true);
      assertTrue(cmp.isNegate());

      cmp.setNegate(false);
      assertFalse(cmp.isNegate());
   }

   @Test
   void clonePreservesSettings() {
      DefaultComparator cmp = new DefaultComparator();
      cmp.setCaseSensitive(false);
      cmp.setNegate(true);

      DefaultComparator cloned = (DefaultComparator) cmp.clone();

      assertNotNull(cloned);
      assertFalse(cloned.isCaseSensitive());
      assertTrue(cloned.isNegate());
      // should produce same comparison result as original
      assertEquals(cmp.compare("Apple", "banana"), cloned.compare("Apple", "banana"));
   }

   @Test
   void cloneIsIndependent() {
      DefaultComparator original = new DefaultComparator();
      DefaultComparator cloned = (DefaultComparator) original.clone();

      cloned.setNegate(true);
      // original should remain unchanged
      assertFalse(original.isNegate());
   }
}
