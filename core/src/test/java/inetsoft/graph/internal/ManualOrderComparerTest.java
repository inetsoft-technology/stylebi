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
package inetsoft.graph.internal;

import inetsoft.uql.xmla.MemberObject;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ManualOrderComparerTest {

   // ---- Object[] constructor tests ----

   @Test
   void itemsInListAreOrderedByListPosition() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"B", "A", "C"});

      // B is at index 0, A is at index 1 → B < A
      assertTrue(comparer.compare("B", "A") < 0);
      // A is at index 1, C is at index 2 → A < C
      assertTrue(comparer.compare("A", "C") < 0);
      // C is at index 2, B is at index 0 → C > B
      assertTrue(comparer.compare("C", "B") > 0);
   }

   @Test
   void itemInListComesBeforeItemNotInList() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"known"});

      assertTrue(comparer.compare("known", "unknown") < 0);
      assertTrue(comparer.compare("unknown", "known") > 0);
   }

   @Test
   void itemsNotInListFallBackToDefaultComparator() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"X"});

      // Both not in list — default comparator applies (alphabetic)
      assertTrue(comparer.compare("alpha", "beta") < 0);
      assertTrue(comparer.compare("beta", "alpha") > 0);
      assertEquals(0, comparer.compare("same", "same"));
   }

   @Test
   void sameItemEqualsItself() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"A", "B"});

      assertEquals(0, comparer.compare("A", "A"));
      assertEquals(0, comparer.compare("B", "B"));
   }

   @Test
   void emptyStringIsTreatedAsNull() {
      // empty string "" is converted to null in compare()
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"A"});

      // null is not in the list, so "A" (in list) comes before null (not in list)
      assertTrue(comparer.compare("A", "") < 0);
      assertTrue(comparer.compare("", "A") > 0);
   }

   @Test
   void nullHandlingBothNotInList() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"A"});

      // Both null-equivalent – delegate to DefaultComparator which handles nulls
      assertEquals(0, comparer.compare(null, null));
   }

   // ---- String dtype constructor tests ----

   @Test
   void stringDtypeOrdersStringValues() {
      List<String> list = Arrays.asList("cat", "dog", "bird");
      ManualOrderComparer comparer = new ManualOrderComparer("string", list);

      assertTrue(comparer.compare("cat", "dog") < 0);
      assertTrue(comparer.compare("bird", "cat") > 0);
      assertEquals(0, comparer.compare("dog", "dog"));
   }

   @Test
   void stringDtypeItemNotInListFallsBack() {
      List<String> list = Arrays.asList("alpha");
      ManualOrderComparer comparer = new ManualOrderComparer("string", list);

      assertTrue(comparer.compare("alpha", "zzz") < 0);
      assertTrue(comparer.compare("zzz", "alpha") > 0);
   }

   @Test
   void customDefaultComparatorIsUsed() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"X"});
      // Set a comparator that always returns 42
      comparer.setDefaultComparator((a, b) -> 42);

      // Both not in list → use custom comparator
      assertEquals(42, comparer.compare("foo", "bar"));
   }

   @Test
   void setDefaultComparatorIgnoresNull() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      Comparator<?> original = comparer.getDefaultComparator();
      comparer.setDefaultComparator(null);
      // Should still be the original because null is rejected
      assertSame(original, comparer.getDefaultComparator());
   }

   @Test
   void getDefaultComparatorReturnsSetValue() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      Comparator<Object> custom = (a, b) -> 0;
      comparer.setDefaultComparator(custom);
      assertSame(custom, comparer.getDefaultComparator());
   }

   // ---- MemberObject unwrapping ----

   @Test
   void memberObjectsAreComparedByCaption() throws Exception {
      MemberObject m1 = new MemberObject("[Year].[2020]");
      MemberObject m2 = new MemberObject("[Year].[2021]");

      // Set caption via reflection since the field is package-private
      java.lang.reflect.Field captionField = MemberObject.class.getDeclaredField("caption");
      captionField.setAccessible(true);
      captionField.set(m1, "2020");
      captionField.set(m2, "2021");

      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});

      // compare via MemberObject path: captions "2020" vs "2021"
      int result = comparer.compare(m1, m2);
      // Both captions not in list (empty list) → delegate to DefaultComparator
      // "2020" < "2021" alphabetically
      assertTrue(result < 0);
   }

   @Test
   void memberObjectsWithSameCaptionAreEqual() throws Exception {
      MemberObject m1 = new MemberObject("[Year].[2020]");
      MemberObject m2 = new MemberObject("[Year].[2020b]");

      java.lang.reflect.Field captionField = MemberObject.class.getDeclaredField("caption");
      captionField.setAccessible(true);
      captionField.set(m1, "same");
      captionField.set(m2, "same");

      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      assertEquals(0, comparer.compare(m1, m2));
   }

   @Test
   void memberObjectsWithNullCaptionsAreHandled() {
      // When captions are null, both null → delegates to DefaultComparator(null, null) = 0
      MemberObject m1 = new MemberObject("[A]");
      MemberObject m2 = new MemberObject("[B]");
      // captions are null by default
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      // compare(null, null) → both treated as null, not in list → DefaultComparator
      assertEquals(0, comparer.compare(m1, m2));
   }

   // ---- Numeric type compare() methods ----

   @Test
   void compareDoubleReturnsNegativeWhenFirstIsSmaller() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      assertTrue(comparer.compare(1.0, 2.0) < 0);
      assertTrue(comparer.compare(2.0, 1.0) > 0);
      assertEquals(0, comparer.compare(5.0, 5.0));
   }

   @Test
   void compareDoubleEpsilonTreatsVeryCloseValuesAsEqual() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      // Within epsilon of 0.000001
      assertEquals(0, comparer.compare(1.0, 1.0000005));
      // Outside epsilon
      assertTrue(comparer.compare(1.0, 1.0001) < 0);
   }

   @Test
   void compareDoubleWithNullDoubleConstant() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      double nd = Tool.NULL_DOUBLE;
      // null_double vs null_double → 0
      assertEquals(0, comparer.compare(nd, nd));
      // null_double vs normal → null_double is "less"
      assertTrue(comparer.compare(nd, 5.0) < 0);
      // normal vs null_double → normal is "greater"
      assertTrue(comparer.compare(5.0, nd) > 0);
   }

   @Test
   void compareFloatReturnsCorrectOrder() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      assertTrue(comparer.compare(1.0f, 2.0f) < 0);
      assertTrue(comparer.compare(2.0f, 1.0f) > 0);
      assertEquals(0, comparer.compare(3.0f, 3.0f));
   }

   @Test
   void compareFloatWithNullFloatConstant() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      float nf = Tool.NULL_FLOAT;
      assertEquals(0, comparer.compare(nf, nf));
      assertTrue(comparer.compare(nf, 5.0f) < 0);
      assertTrue(comparer.compare(5.0f, nf) > 0);
   }

   @Test
   void compareFloatEpsilonTreatsVeryCloseValuesAsEqual() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      assertEquals(0, comparer.compare(1.0f, 1.0000005f));
   }

   @Test
   void compareLongReturnsCorrectOrder() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      assertTrue(comparer.compare(10L, 20L) < 0);
      assertTrue(comparer.compare(20L, 10L) > 0);
      assertEquals(0, comparer.compare(5L, 5L));
   }

   @Test
   void compareIntReturnsCorrectOrder() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      assertTrue(comparer.compare(3, 7) < 0);
      assertTrue(comparer.compare(7, 3) > 0);
      assertEquals(0, comparer.compare(4, 4));
   }

   @Test
   void compareShortReturnsCorrectOrder() {
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{});
      short s1 = 10, s2 = 20;
      assertTrue(comparer.compare(s1, s2) < 0);
      assertTrue(comparer.compare(s2, s1) > 0);
      assertEquals(0, comparer.compare(s1, s1));
   }

   @Test
   void listOrderTakesPrecedenceOverAlphabeticOrder() {
      // "Z" before "A" in manual list — should contradict alphabetic
      ManualOrderComparer comparer = new ManualOrderComparer(new Object[]{"Z", "A"});
      assertTrue(comparer.compare("Z", "A") < 0);
   }

   @Test
   void nullListInStringDtypeConstructor() {
      // Passing null list should not throw
      ManualOrderComparer comparer = new ManualOrderComparer("string", null);
      // Both not in empty list → delegates to default comparator
      assertTrue(comparer.compare("a", "b") < 0);
   }
}
