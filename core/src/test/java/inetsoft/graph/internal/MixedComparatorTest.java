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

import inetsoft.util.DefaultComparator;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MixedComparatorTest {

   private MixedComparator mixedOf(Comparator<?> delegate) {
      return new MixedComparator(delegate);
   }

   // ---- Number normalization ----

   @Test
   void integerAndDoubleAreComparedNumerically() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      // Integer vs Double: both converted to double before delegating
      // 1 (Integer) vs 2.0 (Double): after normalizing both to double → 1.0 < 2.0
      assertTrue(comp.compare(Integer.valueOf(1), Double.valueOf(2.0)) < 0);
      assertTrue(comp.compare(Double.valueOf(2.0), Integer.valueOf(1)) > 0);
      assertEquals(0, comp.compare(Integer.valueOf(3), Double.valueOf(3.0)));
   }

   @Test
   void integerAndLongAreComparedNumerically() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      assertTrue(comp.compare(Integer.valueOf(5), Long.valueOf(10L)) < 0);
      assertTrue(comp.compare(Long.valueOf(10L), Integer.valueOf(5)) > 0);
   }

   @Test
   void sameNumberTypeComparedDirectly() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      assertTrue(comp.compare(Integer.valueOf(3), Integer.valueOf(7)) < 0);
      assertTrue(comp.compare(Double.valueOf(5.0), Double.valueOf(2.0)) > 0);
   }

   // ---- Date comparison ----

   @Test
   void datesAreComparedByTimestamp() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      Date earlier = new Date(1000L);
      Date later   = new Date(2000L);
      assertTrue(comp.compare(earlier, later) < 0);
      assertTrue(comp.compare(later, earlier) > 0);
      assertEquals(0, comp.compare(earlier, new Date(1000L)));
   }

   // ---- String toString() fallback for mismatched non-number, non-date types ----

   @Test
   void differentNonNumericNonDateTypesUseToStringFallback() {
      // When two non-null objects of different types (neither Number nor Date)
      // are compared, MixedComparator converts both to String via toString().
      MixedComparator comp = mixedOf(new DefaultComparator());

      Object objA = new Object() {
         @Override public String toString() { return "alpha"; }
      };
      Object objB = new Object() {
         @Override public String toString() { return "beta"; }
      };

      // "alpha" < "beta" alphabetically
      assertTrue(comp.compare(objA, objB) < 0);
      assertTrue(comp.compare(objB, objA) > 0);
   }

   @Test
   void sameTypeStringsDelegateDirectly() {
      MixedComparator comp = mixedOf((Comparator<String>) String::compareTo);
      assertTrue(comp.compare("abc", "def") < 0);
      assertTrue(comp.compare("def", "abc") > 0);
      assertEquals(0, comp.compare("xyz", "xyz"));
   }

   // ---- Null handling ----

   @Test
   void nullValuesAreHandledByDelegate() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      // DefaultComparator treats null as less than non-null
      assertTrue(comp.compare(null, "anything") < 0);
      assertTrue(comp.compare("anything", null) > 0);
      assertEquals(0, comp.compare(null, null));
   }

   @Test
   void oneNullAndOneNumberHandledByDelegate() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      // null vs Integer: no type-check branch fires because v1 is null
      assertTrue(comp.compare(null, Integer.valueOf(5)) < 0);
   }

   // ---- Exception fall-through and check flag ----

   @Test
   void delegateThatThrowsOnMismatchedTypesTriggersMixedChecking() {
      // Create a comparator that throws ClassCastException when types differ
      Comparator<Object> strictStringComp = (a, b) -> ((String) a).compareTo((String) b);
      MixedComparator comp = new MixedComparator(strictStringComp);

      // First call: Integer vs String → delegate throws → check is set → retry as strings
      // Integer(42).toString() = "42", "abc" stays "abc"
      // "42" < "abc" alphabetically
      int result = comp.compare(Integer.valueOf(42), "abc");
      assertTrue(result < 0, "\"42\" should be less than \"abc\" alphabetically");
   }

   @Test
   void delegateThatAlwaysThrowsAfterCheckRethrows() {
      Comparator<Object> alwaysThrows = (a, b) -> { throw new ClassCastException("always"); };
      MixedComparator comp = new MixedComparator(alwaysThrows);

      assertThrows(RuntimeException.class, () -> comp.compare("x", "y"));
   }

   // ---- clone() ----

   @Test
   void cloneReturnsNonNullObject() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      Object cloned = comp.clone();
      assertNotNull(cloned);
      assertInstanceOf(MixedComparator.class, cloned);
   }

   @Test
   void clonedComparatorBehavesLikeOriginal() {
      MixedComparator comp = mixedOf(new DefaultComparator());
      MixedComparator cloned = (MixedComparator) comp.clone();
      assertTrue(cloned.compare(Integer.valueOf(1), Double.valueOf(2.0)) < 0);
      assertTrue(cloned.compare(Double.valueOf(5.0), Integer.valueOf(3)) > 0);
   }
}
