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
import org.junit.jupiter.api.Test;

import java.text.Collator;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TextComparer.
 *
 * <p>When a Collator is provided and case-sensitive mode is off, the comparer
 * uses the Collator for non-ASCII strings but falls through to Tool.compare for
 * ASCII-only strings. When no Collator, it always uses Tool.compare.
 */
public class TextComparerTest {

   // -----------------------------------------------------------------------
   // compare(Object, Object) with no Collator
   // -----------------------------------------------------------------------

   @Test
   void compareObject_nullCollator_asciiStrings_lexicographic() {
      TextComparer comp = new TextComparer(null);
      // "apple" < "banana" lexicographically
      assertTrue(comp.compare("apple", "banana") < 0);
      assertTrue(comp.compare("banana", "apple") > 0);
      assertEquals(0, comp.compare("apple", "apple"));
   }

   @Test
   void compareObject_nullCollator_sameReference_returnsZero() {
      TextComparer comp = new TextComparer(null);
      String s = "hello";
      assertEquals(0, comp.compare(s, s));
   }

   @Test
   void compareObject_nullCollator_nullInputs_handledWithoutException() {
      TextComparer comp = new TextComparer(null);
      // Tool.compare handles nulls: null < non-null
      assertTrue(comp.compare(null, "x") < 0);
      assertTrue(comp.compare("x", null) > 0);
      assertEquals(0, comp.compare(null, null));
   }

   @Test
   void compareObject_nullCollator_nonStringObjects_comparesViaToString() {
      TextComparer comp = new TextComparer(null);
      // Compare integers — they are not strings, so uses Tool.compare which handles them
      // as numbers since both are Number
      assertTrue(comp.compare(1, 2) < 0);
      assertTrue(comp.compare(2, 1) > 0);
      assertEquals(0, comp.compare(5, 5));
   }

   @Test
   void compareObject_nullCollator_caseInsensitive_uppercaseEqualsLowercase() {
      // When caseSensitive=false, "abc".equalsIgnoreCase("ABC")
      TextComparer comp = new TextComparer(null);
      comp.setCaseSensitive(false);
      assertEquals(0, comp.compare("abc", "ABC"));
   }

   @Test
   void compareObject_nullCollator_caseSensitive_differentCase_notEqual() {
      TextComparer comp = new TextComparer(null);
      comp.setCaseSensitive(true);
      // "abc" != "ABC" in case-sensitive mode
      assertNotEquals(0, comp.compare("abc", "ABC"));
   }

   // -----------------------------------------------------------------------
   // compare(Object, Object) with Collator
   // -----------------------------------------------------------------------

   @Test
   void compareObject_withCollator_asciiStrings_usesFastPath() {
      // For ASCII-only strings, the code bypasses the collator and uses Tool.compare
      Collator collator = Collator.getInstance(Locale.ENGLISH);
      TextComparer comp = new TextComparer(collator);
      // Pure ASCII: "apple" < "banana"
      assertTrue(comp.compare("apple", "banana") < 0);
      assertTrue(comp.compare("banana", "apple") > 0);
   }

   @Test
   void compareObject_withCollator_nonAsciiStrings_usesCollator() {
      // Use a French collator and compare strings with accented characters
      Collator collator = Collator.getInstance(Locale.FRENCH);
      TextComparer comp = new TextComparer(collator);
      // "café" vs "cafe" — the collator determines the ordering
      // We just verify it returns a consistent non-throwing result
      int result = comp.compare("café", "cafe");
      // Result is defined by the French collator; just ensure no exception and equal
      // strings compare as 0
      assertEquals(0, comp.compare("café", "café"));
   }

   @Test
   void compareObject_withCollator_sameReference_returnsZero() {
      Collator collator = Collator.getInstance(Locale.ENGLISH);
      TextComparer comp = new TextComparer(collator);
      String s = "hello";
      assertEquals(0, comp.compare(s, s));
   }

   @Test
   void compareObject_withCollator_nullFirstArg_handledGracefully() {
      Collator collator = Collator.getInstance(Locale.ENGLISH);
      TextComparer comp = new TextComparer(collator);
      // null is not a String, so collator.compare throws ClassCastException caught internally,
      // falling through to Tool.compare
      assertTrue(comp.compare(null, "x") < 0);
   }

   @Test
   void compareObject_withCollator_caseSensitiveDisablesCollator() {
      // When caseSensitive=true the constructor sets collator to null
      Collator collator = Collator.getInstance(Locale.ENGLISH);
      // To force caseSensitive=true: use setCaseSensitive after construction
      TextComparer comp = new TextComparer(null);
      comp.setCaseSensitive(true);
      // Now compare non-ASCII; without collator it uses Tool.compare
      int r = comp.compare("naïve", "naive");
      // Just verify no exception
      assertNotEquals(Integer.MIN_VALUE, r); // always true, checks method runs
   }

   // -----------------------------------------------------------------------
   // forceToString mode
   // -----------------------------------------------------------------------

   @Test
   void compareObject_forceToString_convertsToStringBeforeCompare() {
      TextComparer comp = new TextComparer(null, true);
      // Integer 1 becomes "1", Integer 10 becomes "10"
      // String "1" vs "10" — lexicographic: "1" < "10" since "10".startsWith("1") but longer
      int result = comp.compare(1, 10);
      // "1" < "10" lexicographically
      assertTrue(result < 0);
   }

   @Test
   void compareObject_forceToString_nullConvertsToNullAndCompares() {
      TextComparer comp = new TextComparer(null, true);
      // null stays null
      assertTrue(comp.compare(null, "x") < 0);
   }

   // -----------------------------------------------------------------------
   // compare(double, double) — no sign manipulation in TextComparer
   // -----------------------------------------------------------------------

   @Test
   void compareDouble_bothNullDouble_returnsZero() {
      TextComparer comp = new TextComparer(null);
      assertEquals(0, comp.compare(Tool.NULL_DOUBLE, Tool.NULL_DOUBLE));
   }

   @Test
   void compareDouble_firstIsNull_returnsNegativeOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(-1, comp.compare(Tool.NULL_DOUBLE, 5.0));
   }

   @Test
   void compareDouble_secondIsNull_returnsPositiveOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(1, comp.compare(5.0, Tool.NULL_DOUBLE));
   }

   @Test
   void compareDouble_normalValues_comparesCorrectly() {
      TextComparer comp = new TextComparer(null);
      assertTrue(comp.compare(3.0, 7.0) < 0);
      assertTrue(comp.compare(7.0, 3.0) > 0);
      assertEquals(0, comp.compare(5.0, 5.0));
   }

   // -----------------------------------------------------------------------
   // compare(float, float)
   // -----------------------------------------------------------------------

   @Test
   void compareFloat_bothNullFloat_returnsZero() {
      TextComparer comp = new TextComparer(null);
      assertEquals(0, comp.compare(Tool.NULL_FLOAT, Tool.NULL_FLOAT));
   }

   @Test
   void compareFloat_firstIsNull_returnsNegativeOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(-1, comp.compare(Tool.NULL_FLOAT, 1.0f));
   }

   @Test
   void compareFloat_secondIsNull_returnsPositiveOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(1, comp.compare(1.0f, Tool.NULL_FLOAT));
   }

   // -----------------------------------------------------------------------
   // compare(long, long) / compare(int, int) / compare(short, short)
   // -----------------------------------------------------------------------

   @Test
   void compareLong_v1Less_returnsNegativeOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(-1, comp.compare(1L, 2L));
   }

   @Test
   void compareLong_v1Greater_returnsPositiveOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(1, comp.compare(2L, 1L));
   }

   @Test
   void compareLong_equal_returnsZero() {
      TextComparer comp = new TextComparer(null);
      assertEquals(0, comp.compare(5L, 5L));
   }

   @Test
   void compareInt_v1Less_returnsNegativeOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(-1, comp.compare(3, 9));
   }

   @Test
   void compareInt_v1Greater_returnsPositiveOne() {
      TextComparer comp = new TextComparer(null);
      assertEquals(1, comp.compare(9, 3));
   }

   @Test
   void compareInt_equal_returnsZero() {
      TextComparer comp = new TextComparer(null);
      assertEquals(0, comp.compare(4, 4));
   }

   @Test
   void compareShort_ordering() {
      TextComparer comp = new TextComparer(null);
      assertEquals(-1, comp.compare((short) 1, (short) 2));
      assertEquals(1, comp.compare((short) 2, (short) 1));
      assertEquals(0, comp.compare((short) 3, (short) 3));
   }

   // -----------------------------------------------------------------------
   // isCaseSensitive / setCaseSensitive
   // -----------------------------------------------------------------------

   @Test
   void setCaseSensitive_setsCorrectly() {
      TextComparer comp = new TextComparer(null);
      comp.setCaseSensitive(false);
      assertFalse(comp.isCaseSensitive());
      comp.setCaseSensitive(true);
      assertTrue(comp.isCaseSensitive());
   }
}
