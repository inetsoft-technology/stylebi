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

import inetsoft.uql.XConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ConcatFormula.
 *
 * <p>getResult() returns null when cnt==0 and def==true (default), empty string when
 * def==false and cnt==0. Otherwise returns the comma-separated string.
 */
@Tag("core")
public class ConcatFormulaTest {

   private ConcatFormula formula;

   @BeforeEach
   void setUp() {
      formula = new ConcatFormula();
   }

   // -----------------------------------------------------------------------
   // isNull()
   // -----------------------------------------------------------------------

   @Test
   void isNull_noValuesAdded_returnsTrue() {
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddValue_returnsFalse() {
      formula.addValue("hello");
      assertFalse(formula.isNull());
   }

   @Test
   void isNull_afterAddNullValue_remainsTrue() {
      formula.addValue((Object) null);
      assertTrue(formula.isNull());
   }

   @Test
   void isNull_afterAddNullDouble_remainsTrue() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // getResult() — empty cases
   // -----------------------------------------------------------------------

   @Test
   void getResult_noValues_defaultResultFalse_returnsEmpty() {
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   @Test
   void getResult_noValues_defaultResultTrue_returnsNull() {
      formula.setDefaultResult(true);
      assertNull(formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(Object) — string values
   // -----------------------------------------------------------------------

   @Test
   void addValue_singleString_getResultReturnsThatString() {
      formula.addValue("alpha");
      assertEquals("alpha", formula.getResult());
   }

   @Test
   void addValue_multipleStrings_getResultReturnsCommaSeparated() {
      formula.addValue("a");
      formula.addValue("b");
      formula.addValue("c");
      assertEquals("a,b,c", formula.getResult());
   }

   @Test
   void addValue_nullObject_skipped_resultUnchanged() {
      formula.addValue("a");
      formula.addValue((Object) null);
      formula.addValue("b");
      assertEquals("a,b", formula.getResult());
   }

   @Test
   void addValue_nullOnly_resultIsEmpty() {
      formula.addValue((Object) null);
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   @Test
   void addValue_numericObject_convertedToString() {
      formula.addValue(Integer.valueOf(42));
      assertEquals("42", formula.getResult());
   }

   @Test
   void addValue_doubleObject_convertedToString() {
      formula.addValue(Double.valueOf(3.14));
      assertEquals("3.14", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(double)
   // -----------------------------------------------------------------------

   @Test
   void addValueDouble_nullDouble_skipped() {
      formula.addValue(Tool.NULL_DOUBLE);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueDouble_normalValue_appended() {
      formula.addValue(1.5d);
      assertEquals("1.5", formula.getResult());
   }

   @Test
   void addValueDouble_multipleNormalValues_commaSeparated() {
      formula.addValue(1.0d);
      formula.addValue(2.0d);
      assertEquals("1.0,2.0", formula.getResult());
   }

   @Test
   void addValueDouble_nullSentinelMixedWithReal_onlyRealIncluded() {
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(5.0d);
      formula.addValue(Tool.NULL_DOUBLE);
      assertEquals("5.0", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(float)
   // -----------------------------------------------------------------------

   @Test
   void addValueFloat_nullFloat_skipped() {
      formula.addValue(Tool.NULL_FLOAT);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueFloat_normalValue_appended() {
      formula.addValue(2.5f);
      assertEquals("2.5", formula.getResult());
   }

   @Test
   void addValueFloat_nullMixedWithReal_onlyRealIncluded() {
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(3.0f);
      assertEquals("3.0", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(long)
   // -----------------------------------------------------------------------

   @Test
   void addValueLong_nullLong_skipped() {
      formula.addValue(Tool.NULL_LONG);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueLong_normalValue_appended() {
      formula.addValue(100L);
      assertEquals("100", formula.getResult());
   }

   @Test
   void addValueLong_multiple_commaSeparated() {
      formula.addValue(1L);
      formula.addValue(2L);
      assertEquals("1,2", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(int)
   // -----------------------------------------------------------------------

   @Test
   void addValueInt_nullInteger_skipped() {
      formula.addValue(Tool.NULL_INTEGER);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueInt_normalValue_appended() {
      formula.addValue(7);
      assertEquals("7", formula.getResult());
   }

   @Test
   void addValueInt_multiple_commaSeparated() {
      formula.addValue(10);
      formula.addValue(20);
      formula.addValue(30);
      assertEquals("10,20,30", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // addValue(short)
   // -----------------------------------------------------------------------

   @Test
   void addValueShort_nullShort_skipped() {
      formula.addValue(Tool.NULL_SHORT);
      assertTrue(formula.isNull());
   }

   @Test
   void addValueShort_normalValue_appended() {
      formula.addValue((short) 5);
      assertEquals("5", formula.getResult());
   }

   @Test
   void addValueShort_multiple_commaSeparated() {
      formula.addValue((short) 1);
      formula.addValue((short) 2);
      assertEquals("1,2", formula.getResult());
   }

   // -----------------------------------------------------------------------
   // reset()
   // -----------------------------------------------------------------------

   @Test
   void reset_clearsBufferAndCounter() {
      formula.addValue("x");
      formula.addValue("y");
      assertFalse(formula.isNull());
      formula.reset();
      assertTrue(formula.isNull());
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   @Test
   void reset_allowsReuseForNewValues() {
      formula.addValue("first");
      formula.reset();
      formula.addValue("second");
      assertEquals("second", formula.getResult());
   }

   @Test
   void reset_doubleReset_stillIsNull() {
      formula.addValue("a");
      formula.reset();
      formula.reset();
      assertTrue(formula.isNull());
   }

   // -----------------------------------------------------------------------
   // clone()
   // -----------------------------------------------------------------------

   @Test
   void clone_preservesDefaultResult() {
      formula.setDefaultResult(true);
      ConcatFormula cloned = (ConcatFormula) formula.clone();
      assertNotNull(cloned);
      assertTrue(cloned.isDefaultResult());
   }

   @Test
   void clone_doesNotCopyAccumulatedValues() {
      formula.addValue("a");
      formula.addValue("b");
      ConcatFormula cloned = (ConcatFormula) formula.clone();
      // clone starts fresh
      assertTrue(cloned.isNull());
   }

   // -----------------------------------------------------------------------
   // isDefaultResult / setDefaultResult
   // -----------------------------------------------------------------------

   @Test
   void setDefaultResult_trueAndFalse_roundTrip() {
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());
      formula.setDefaultResult(false);
      assertFalse(formula.isDefaultResult());
   }

   // -----------------------------------------------------------------------
   // getResultType()
   // -----------------------------------------------------------------------

   @Test
   void getResultType_returnsStringClass() {
      assertEquals(String.class, formula.getResultType());
   }

   // -----------------------------------------------------------------------
   // getDoubleResult() — should throw
   // -----------------------------------------------------------------------

   @Test
   void getDoubleResult_throwsRuntimeException() {
      assertThrows(RuntimeException.class, () -> formula.getDoubleResult());
   }

   // -----------------------------------------------------------------------
   // Bug #80737: an empty string is a value and gets a separator like any other
   // -----------------------------------------------------------------------

   @Test
   void addValue_leadingEmptyString_keepsSeparator() {
      formula.addValue("");
      formula.addValue("a");
      assertEquals(",a", formula.getResult());
   }

   @Test
   void addValue_leadingEmptyStringThenSeveral_keepsEverySeparator() {
      formula.addValue("");
      formula.addValue("a");
      formula.addValue("b");
      assertEquals(",a,b", formula.getResult());
   }

   @Test
   void addValue_twoLeadingEmptyStrings_keepsBothSeparators() {
      formula.addValue("");
      formula.addValue("");
      formula.addValue("a");
      assertEquals(",,a", formula.getResult());
   }

   @Test
   void addValue_emptyStringInMiddle_keepsSeparators() {
      formula.addValue("a");
      formula.addValue("");
      formula.addValue("b");
      assertEquals("a,,b", formula.getResult());
   }

   @Test
   void addValue_trailingEmptyString_keepsSeparator() {
      formula.addValue("a");
      formula.addValue("");
      assertEquals("a,", formula.getResult());
   }

   @Test
   void addValue_onlyEmptyString_resultIsEmptyButNotNull() {
      formula.setDefaultResult(true);
      formula.addValue("");
      assertFalse(formula.isNull());
      assertEquals("", formula.getResult());
   }

   @Test
   void addValue_nullBeforeLeadingEmptyString_nullStillSkipped() {
      formula.addValue((Object) null);
      formula.addValue("");
      formula.addValue((Object) null);
      formula.addValue("a");
      assertEquals(",a", formula.getResult());
   }

   @Test
   void addValue_leadingEmptyStringThenPrimitiveOverloads_keepSeparators() {
      formula.addValue("");
      formula.addValue(1.5d);
      formula.addValue(new double[] { 2.5d });
      formula.addValue(3.5f);
      formula.addValue(4L);
      formula.addValue(5);
      formula.addValue((short) 6);
      assertEquals(",1.5,2.5,3.5,4,5,6", formula.getResult());
   }

   @Test
   void addValue_leadingEmptyStringThenNullSentinels_sentinelsSkipped() {
      formula.addValue("");
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(new double[] { Tool.NULL_DOUBLE });
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(Tool.NULL_LONG);
      formula.addValue(Tool.NULL_INTEGER);
      formula.addValue(Tool.NULL_SHORT);
      formula.addValue("a");
      assertEquals(",a", formula.getResult());
   }

   @Test
   void reset_afterLeadingEmptyString_startsWithoutSeparator() {
      formula.addValue("");
      formula.addValue("a");
      formula.reset();
      formula.addValue("b");
      assertEquals("b", formula.getResult());
   }

   // Bug #80621: only blank strings give one empty entry each, and the formula has a result
   @Test
   void addValue_onlyEmptyStrings_oneEmptyEntryEach() {
      formula.addValue("");
      formula.addValue("");
      assertEquals(",", formula.getResult());
      assertFalse(formula.isNull());
   }

   // Bug #80621: sequences that did not start with a blank string are unchanged
   @Test
   void addValue_randomSequences_changedOnlyByALeadingBlank() {
      Object[] pool = { null, "", " ", "a", "b,c", 7, 2.5, 3L, new Timestamp(0L) };
      Random random = new Random(80621L);
      int compared = 0;
      int changed = 0;

      for(int i = 0; i < 3000; i++) {
         Object[] values = new Object[random.nextInt(7)];

         for(int j = 0; j < values.length; j++) {
            values[j] = pool[random.nextInt(pool.length)];
         }

         ConcatFormula concat = new ConcatFormula();
         concat.setDefaultResult(random.nextBoolean());

         for(Object value : values) {
            concat.addValue(value);
         }

         Object actual = concat.getResult();

         // the result of the formula before the fix, copied here
         Object before = baseConcat(values, concat.isDefaultResult());

         // one entry per non-null value
         StringBuilder entries = new StringBuilder();
         int count = 0;

         for(Object value : values) {
            if(value != null) {
               entries.append(count++ > 0 ? "," : "").append(value);
            }
         }

         assertEquals(count == 0 ? (concat.isDefaultResult() ? null : "") : entries.toString(),
                      actual, Arrays.toString(values));

         if(Objects.equals(before, actual)) {
            compared++;
         }
         else {
            changed++;
            // the result differs only when a blank string was written before any other text
            assertTrue(startsWithBlank(values), Arrays.toString(values));
         }
      }

      assertTrue(compared > 1000);
      assertTrue(changed > 100);
   }

   // -----------------------------------------------------------------------
   // name, arrival order, mixed values, the default result
   // -----------------------------------------------------------------------

   // gap:report.summary-formulas.text#concat-comma-separated
   @Test
   void getName_returnsConcatFormula() {
      assertEquals(XConstants.CONCAT_FORMULA, formula.getName());
   }

   // gap:report.summary-formulas.text#concat-comma-separated
   @Test
   void addValue_mixedValues_joinedInArrivalOrder() {
      // arrival order, not sorted
      formula.addValue("c");
      formula.addValue("a");
      formula.addValue("b");
      assertEquals("c,a,b", formula.getResult());

      // numbers and a date use their toString
      formula.reset();
      Timestamp when = Timestamp.valueOf("2024-06-12 10:30:00");
      formula.addValue(Integer.valueOf(3));
      formula.addValue(Double.valueOf(4.5));
      formula.addValue(when);
      assertEquals("3,4.5,2024-06-12 10:30:00.0", formula.getResult());

      // the primitive forms append the same way
      formula.reset();
      formula.addValue(7);
      formula.addValue(8L);
      formula.addValue((short) 9);
      formula.addValue(1.5f);
      formula.addValue(2.5);
      formula.addValue(new double[] { 6.5, 99 });
      assertEquals("7,8,9,1.5,2.5,6.5", formula.getResult());

      // a primitive null marker is skipped
      formula.reset();
      formula.addValue(1);
      formula.addValue(Tool.NULL_INTEGER);
      formula.addValue(Tool.NULL_DOUBLE);
      formula.addValue(Tool.NULL_LONG);
      formula.addValue(Tool.NULL_FLOAT);
      formula.addValue(Tool.NULL_SHORT);
      formula.addValue(new double[] { Tool.NULL_DOUBLE });
      formula.addValue(2);
      assertEquals("1,2", formula.getResult());

      // a clone starts empty but keeps the default-result option
      formula.setDefaultResult(true);
      ConcatFormula copy = (ConcatFormula) formula.clone();
      assertTrue(copy.isDefaultResult());
      assertTrue(copy.isNull());
      assertNull(copy.getResult());
      assertEquals("1,2", formula.getResult());
   }

   // gap:report.summary-formulas.text#concat-skips-null
   @Test
   void addValue_leadingNullsAndBlankStrings_noLeadingCommaAndBlanksKept() {
      // a leading null leaves no leading comma
      formula.addValue((Object) null);
      formula.addValue((Object) null);
      formula.addValue("x");
      assertEquals("x", formula.getResult());

      // blank strings are kept as entries
      formula.reset();
      formula.addValue("a");
      formula.addValue("");
      formula.addValue(" ");
      formula.addValue((Object) null);
      formula.addValue("b");
      assertEquals("a,, ,b", formula.getResult());
   }

   // gap:report.summary-formulas.text#concat-default-result-null
   @Test
   void getResult_defaultResultTrue_nullUntilAValueAndAfterReset() {
      assertFalse(formula.isDefaultResult());
      formula.setDefaultResult(true);
      assertTrue(formula.isDefaultResult());

      assertNull(formula.getResult());
      assertTrue(formula.isNull());

      // a value turns the result back into the joined string
      formula.addValue("a");
      assertEquals("a", formula.getResult());

      // reset returns to no result, null again, the option is kept
      formula.reset();
      assertNull(formula.getResult());

      // only nulls added is still no result
      formula.addValue((Object) null);
      assertNull(formula.getResult());

      // without the option the same empty formula gives the empty string
      formula.setDefaultResult(false);
      assertEquals("", formula.getResult());
   }

   // gap:report.summary-formulas.text#concat-no-double-result
   @Test
   void getDoubleResult_withOrWithoutValues_throwsUnsupportedMethod() {
      RuntimeException empty = assertThrows(RuntimeException.class, formula::getDoubleResult);
      assertEquals("Unsupported method called!", empty.getMessage());

      // still unsupported with values, even numeric ones
      formula.addValue(Integer.valueOf(1));
      formula.addValue(Integer.valueOf(2));
      RuntimeException full = assertThrows(RuntimeException.class, formula::getDoubleResult);
      assertEquals("Unsupported method called!", full.getMessage());
      assertEquals("1,2", formula.getResult());
   }

   private static boolean startsWithBlank(Object[] values) {
      for(Object value : values) {
         if(value != null) {
            return value.toString().isEmpty();
         }
      }

      return false;
   }

   // ConcatFormula.addValue(Object) and getResult before Bug #80621
   private static Object baseConcat(Object[] values, boolean def) {
      StringBuilder buf = new StringBuilder();
      int cnt = 0;

      for(Object value : values) {
         if(value != null) {
            if(buf.length() > 0) {
               buf.append(",");
            }

            cnt++;
            buf.append(value);
         }
      }

      return cnt == 0 ? (def ? null : "") : buf.toString();
   }
}
