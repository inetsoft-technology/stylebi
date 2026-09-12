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
package inetsoft.report.filter;

import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultComparer.
 *
 * <p>Tool.NULL_DOUBLE = -Double.MAX_VALUE, Tool.NULL_FLOAT = -Float.MAX_VALUE,
 * Tool.NULL_LONG = Long.MIN_VALUE, Tool.NULL_INTEGER = Integer.MIN_VALUE,
 * Tool.NULL_SHORT = Short.MIN_VALUE.
 *
 * <p>In ascending (sign=+1), NULL sorts last (comes after real values), so:
 *   compare(NULL, real) returns negative (NULL &lt; real, i.e. NULL is "less"), but
 * actually looking at the code: v1==NULL_DOUBLE → return -getSign() = -1 (ascending),
 * meaning NULL is "less" → sorts first in ascending? Let's re-read.
 *
 * <p>Re-reading: v1==NULL_DOUBLE → return -getSign(). In ascending getSign()=1, so -1.
 * That means NULL (v1) &lt; real (v2), i.e. NULL sorts before real in ascending sort.
 * So NULL_DOUBLE sorts FIRST in ascending, LAST in descending.
 */
public class DefaultComparerTest {

   private DefaultComparer ascending;
   private DefaultComparer descending;

   @BeforeEach
   void setUp() {
      ascending = new DefaultComparer();
      // descending: setNegate(true) flips sign to -1
      descending = new DefaultComparer();
      descending.setNegate(true);
   }

   // -----------------------------------------------------------------------
   // compare(double, double)
   // -----------------------------------------------------------------------

   @Test
   void compareDouble_bothNullDouble_returnsZero() {
      assertEquals(0, ascending.compare(Tool.NULL_DOUBLE, Tool.NULL_DOUBLE));
   }

   @Test
   void compareDouble_firstIsNull_returnsNegativeInAscending() {
      // v1=NULL → -getSign() = -1 in ascending
      assertTrue(ascending.compare(Tool.NULL_DOUBLE, 5.0) < 0);
   }

   @Test
   void compareDouble_secondIsNull_returnsPositiveInAscending() {
      // v2=NULL → +getSign() = +1 in ascending
      assertTrue(ascending.compare(5.0, Tool.NULL_DOUBLE) > 0);
   }

   @Test
   void compareDouble_firstIsNull_ascendingVsDescendingSignFlipped() {
      // ascending: compare(NULL, 5.0) < 0 ; descending: compare(NULL, 5.0) > 0
      int asc = ascending.compare(Tool.NULL_DOUBLE, 5.0);
      int desc = descending.compare(Tool.NULL_DOUBLE, 5.0);
      assertTrue(asc < 0);
      assertTrue(desc > 0);
   }

   @Test
   void compareDouble_withinEpsilonTolerance_returnsZero() {
      // epsilon is POSITIVE_DOUBLE_ERROR = 0.0000001; difference below that → 0
      double base = 1.0;
      double close = base + 0.00000005; // within tolerance
      assertEquals(0, ascending.compare(base, close));
   }

   @Test
   void compareDouble_v1GreaterThanV2_returnsPositiveInAscending() {
      assertTrue(ascending.compare(10.0, 5.0) > 0);
   }

   @Test
   void compareDouble_v1LessThanV2_returnsNegativeInAscending() {
      assertTrue(ascending.compare(5.0, 10.0) < 0);
   }

   @Test
   void compareDouble_equalValues_returnsZero() {
      assertEquals(0, ascending.compare(7.5, 7.5));
   }

   @Test
   void compareDouble_descendingV1Greater_returnsNegative() {
      // descending inverts sign: v1 > v2 → normally positive → inverted negative
      assertTrue(descending.compare(10.0, 5.0) < 0);
   }

   @Test
   void compareDouble_descendingV1Less_returnsPositive() {
      assertTrue(descending.compare(5.0, 10.0) > 0);
   }

   // -----------------------------------------------------------------------
   // compare(float, float)
   // -----------------------------------------------------------------------

   @Test
   void compareFloat_bothNullFloat_returnsZero() {
      assertEquals(0, ascending.compare(Tool.NULL_FLOAT, Tool.NULL_FLOAT));
   }

   @Test
   void compareFloat_firstIsNull_returnsNegativeInAscending() {
      assertTrue(ascending.compare(Tool.NULL_FLOAT, 5.0f) < 0);
   }

   @Test
   void compareFloat_secondIsNull_returnsPositiveInAscending() {
      assertTrue(ascending.compare(5.0f, Tool.NULL_FLOAT) > 0);
   }

   @Test
   void compareFloat_withinEpsilonTolerance_returnsZero() {
      // POSITIVE_FLOAT_ERROR = 0.000001f
      float base = 1.0f;
      float close = base + 0.0000005f;
      assertEquals(0, ascending.compare(base, close));
   }

   @Test
   void compareFloat_v1GreaterThanV2_returnsPositiveInAscending() {
      assertTrue(ascending.compare(10.0f, 5.0f) > 0);
   }

   @Test
   void compareFloat_v1LessThanV2_returnsNegativeInAscending() {
      assertTrue(ascending.compare(5.0f, 10.0f) < 0);
   }

   @Test
   void compareFloat_equalValues_returnsZero() {
      assertEquals(0, ascending.compare(3.14f, 3.14f));
   }

   @Test
   void compareFloat_descendingSignFlipped() {
      assertTrue(descending.compare(10.0f, 5.0f) < 0);
      assertTrue(descending.compare(5.0f, 10.0f) > 0);
   }

   // -----------------------------------------------------------------------
   // compare(long, long)
   // -----------------------------------------------------------------------

   @Test
   void compareLong_equal_returnsZero() {
      assertEquals(0, ascending.compare(100L, 100L));
   }

   @Test
   void compareLong_v1Less_returnsNegativeInAscending() {
      assertTrue(ascending.compare(1L, 2L) < 0);
   }

   @Test
   void compareLong_v1Greater_returnsPositiveInAscending() {
      assertTrue(ascending.compare(2L, 1L) > 0);
   }

   @Test
   void compareLong_descending_signFlipped() {
      assertTrue(descending.compare(2L, 1L) < 0);
      assertTrue(descending.compare(1L, 2L) > 0);
   }

   @Test
   void compareLong_nullLong_treatedAsRegularLong() {
      // Tool.NULL_LONG = Long.MIN_VALUE — there is no special null handling for long
      // compare(NULL_LONG, 0L) → NULL_LONG < 0L → negative
      assertTrue(ascending.compare(Tool.NULL_LONG, 0L) < 0);
   }

   // -----------------------------------------------------------------------
   // compare(int, int)
   // -----------------------------------------------------------------------

   @Test
   void compareInt_equal_returnsZero() {
      assertEquals(0, ascending.compare(5, 5));
   }

   @Test
   void compareInt_v1Less_returnsNegativeInAscending() {
      assertTrue(ascending.compare(3, 7) < 0);
   }

   @Test
   void compareInt_v1Greater_returnsPositiveInAscending() {
      assertTrue(ascending.compare(7, 3) > 0);
   }

   @Test
   void compareInt_descending_signFlipped() {
      assertTrue(descending.compare(7, 3) < 0);
      assertTrue(descending.compare(3, 7) > 0);
   }

   // -----------------------------------------------------------------------
   // compare(short, short)
   // -----------------------------------------------------------------------

   @Test
   void compareShort_equal_returnsZero() {
      assertEquals(0, ascending.compare((short) 10, (short) 10));
   }

   @Test
   void compareShort_v1Less_returnsNegativeInAscending() {
      assertTrue(ascending.compare((short) 1, (short) 2) < 0);
   }

   @Test
   void compareShort_v1Greater_returnsPositiveInAscending() {
      assertTrue(ascending.compare((short) 2, (short) 1) > 0);
   }

   @Test
   void compareShort_descending_signFlipped() {
      assertTrue(descending.compare((short) 2, (short) 1) < 0);
      assertTrue(descending.compare((short) 1, (short) 2) > 0);
   }

   // -----------------------------------------------------------------------
   // getSign / setNegate interaction
   // -----------------------------------------------------------------------

   @Test
   void setNegate_false_ascendingBehavior() {
      DefaultComparer comp = new DefaultComparer();
      comp.setNegate(false);
      assertTrue(comp.compare(10.0, 5.0) > 0);
   }

   @Test
   void setNegate_true_descendingBehavior() {
      DefaultComparer comp = new DefaultComparer();
      comp.setNegate(true);
      assertTrue(comp.compare(10.0, 5.0) < 0);
   }

   @Test
   void isNegate_defaultFalse() {
      assertFalse(ascending.isNegate());
   }

   @Test
   void isNegate_afterSetNegate_true() {
      ascending.setNegate(true);
      assertTrue(ascending.isNegate());
   }
}
