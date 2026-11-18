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

import inetsoft.report.StyleConstants;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MedianFormula} over object values, including dates.
 */
@Tag("core")
class MedianFormulaTest {
   private static Object medianOf(Object... values) {
      MedianFormula formula = new MedianFormula();

      for(Object v : values) {
         formula.addValue(v);
      }

      return formula.getResult();
   }

   @Test
   void getResult_oddDateCount_returnsMiddleDateByTime() {
      java.sql.Date jan = java.sql.Date.valueOf("2024-01-01");
      java.sql.Date feb = java.sql.Date.valueOf("2024-02-01");
      java.sql.Date mar = java.sql.Date.valueOf("2024-03-01");

      assertEquals(feb, medianOf(mar, jan, feb));
      assertEquals(feb, medianOf(feb, mar, jan));
   }

   @Test
   void getResult_oddTimestampCount_returnsMiddleTimestampByTime() {
      Timestamp t1 = Timestamp.valueOf("2024-01-01 08:00:00");
      Timestamp t2 = Timestamp.valueOf("2024-01-01 09:00:00");
      Timestamp t3 = Timestamp.valueOf("2024-01-01 10:00:00");

      assertEquals(t2, medianOf(t3, t1, t2));
   }

   @Test
   void getResult_evenDateCount_returnsNull() {
      // there is no midpoint of two dates, so an even count stays null
      assertNull(medianOf(java.sql.Date.valueOf("2024-03-01"),
                          java.sql.Date.valueOf("2024-01-01"),
                          java.sql.Date.valueOf("2024-02-01"),
                          java.sql.Date.valueOf("2024-04-01")));
   }

   @Test
   void getResult_numbers_unchanged() {
      assertEquals(2.0, medianOf(3, 1, 2));
      assertEquals(2.5, medianOf(4, 1, 3, 2));
   }

   @Test
   void getResult_blankStrings_areSkipped() {
      assertEquals(2.0, medianOf("1", "", "3"));
      assertEquals(2.0, medianOf("1", "  ", "3"));
      assertEquals(2.5, medianOf("1", "", "2", "3", "4"));
   }

   private static final double DELTA = 1e-9;

   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   private static void asPercentage(PercentageFormula formula, Object total) {
      formula.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      formula.setTotal(total);
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }

   // Bug #80539: gap:report.summary-formulas.statistical#median-dates
   @Test
   void getResult_oddDateCountWithRepeats_returnsMiddleDate() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      MedianFormula median = new MedianFormula();
      addAll(median, middle, late, early, middle, late);
      assertEquals(middle, median.getResult());
   }

   // Median of dates that are already in order is a Date
   @Test
   void getResult_datesInOrder_returnsDate() {
      Date early = new Date(1_000_000L);
      Date middle = new Date(2_000_000L);
      Date late = new Date(3_000_000L);

      MedianFormula median = new MedianFormula();
      addAll(median, early, early, middle);
      assertEquals(early, median.getResult());
      assertFalse(median.isNull());

      // the value is returned as a Date, not converted to a number
      median.reset();
      addAll(median, early, middle, late);
      assertEquals(middle, median.getResult());
      assertInstanceOf(Date.class, median.getResult());
      assertEquals(0.0, median.getDoubleResult());

      median.reset();
      addAll(median, middle, null);
      assertEquals(middle, median.getResult());
   }

   // gap:report.summary-formulas.statistical#median-odd-even
   @Test
   void getResult_oddAndEvenCounts() {
      MedianFormula median = new MedianFormula();
      assertTrue(median.isNull());
      assertNull(median.getResult());
      assertEquals(0.0, median.getDoubleResult());

      // an odd count gives the middle value of the sorted values
      addAll(median, 12, -2.01, 10, 2, 10.01);
      assertEquals(10.0, median.getResult());
      assertEquals(10.0, median.getDoubleResult());
      assertFalse(median.isNull());

      // an even count gives the mean of the two middle values
      median.reset();
      addAll(median, 13, -2.01, 12, 10.6, 2, 10);
      assertEquals(10.3, asDouble(median.getResult()), DELTA);
      assertEquals(10.3, median.getDoubleResult(), DELTA);

      median.reset();
      addAll(median, 7, 3);
      assertEquals(5.0, median.getResult());

      // one value, and two equal values
      median.reset();
      addAll(median, 8);
      assertEquals(8.0, median.getResult());
      median.addValue(8);
      assertEquals(8.0, median.getResult());

      // the primitive overloads add to the same list, and their null markers are ignored
      median.reset();
      median.addValue(9.5);
      median.addValue(1.5f);
      median.addValue(5L);
      median.addValue((short) 7);
      median.addValue(Tool.NULL_DOUBLE);
      median.addValue(Tool.NULL_INTEGER);
      median.addValue(3);
      assertEquals(5.0, median.getResult());

      median.reset();
      median.addValue(new double[] { 4.0 });
      median.addValue(new double[] { Tool.NULL_DOUBLE });
      median.addValue(new double[] { 6.0 });
      assertEquals(5.0, median.getResult());

      // numeric strings count as numbers
      median.reset();
      addAll(median, "1", "30", "20");
      assertEquals(20.0, median.getResult());
      median.addValue("4");
      assertEquals(12.0, median.getResult());
   }

   // gap:report.summary-formulas.statistical#median-percentage-of-total
   @Test
   void getResult_percentageOfTotal() {
      MedianFormula median = new MedianFormula();
      addAll(median, -2.01, 2, 10, 10.01);
      asPercentage(median, 100);
      assertEquals(0.06, asDouble(median.getResult()), DELTA);
      assertEquals(6.0, asDouble(median.getOriginalResult()), DELTA);
      // the percentage option is restored after the original result is read
      assertEquals(StyleConstants.PERCENTAGE_OF_GROUP, median.getPercentageType());
      assertEquals(0.06, asDouble(median.getResult()), DELTA);

      // an odd count and a numeric string total
      median.reset();
      addAll(median, 1, 9, 5);
      asPercentage(median, "20");
      assertEquals(0.25, asDouble(median.getResult()), DELTA);

      // a total of 0 gives null, or 0 with the default-result flag, for both counts
      median.setTotal(0);
      assertNull(median.getResult());
      assertEquals(5.0, median.getOriginalResult());
      median.setDefaultResult(true);
      assertEquals(0.0, median.getResult());
      median.addValue(7);
      assertEquals(0.0, median.getResult());
      median.setDefaultResult(false);
      assertNull(median.getResult());

      // the sign follows a negative total
      median.setTotal(-4);
      assertEquals(-1.5, asDouble(median.getResult()), DELTA);

      // the percentage option has no effect without a total
      median.setTotal(null);
      assertEquals(6.0, asDouble(median.getResult()), DELTA);

      // the text value does not stop the median of the numeric values
      MedianFormula withText = new MedianFormula();
      addAll(withText, "-3", "2", "10", "a");
      asPercentage(withText, 10);
      assertEquals(-0.05, asDouble(withText.getResult()), DELTA);
      assertEquals(-0.5, asDouble(withText.getOriginalResult()), DELTA);
   }

   // gap:report.summary-formulas.statistical#median-null-and-text
   @Test
   void getResult_nullAndText() {
      MedianFormula median = new MedianFormula();
      addAll(median, -3, 2, 10, null);
      asPercentage(median, 100);
      // the null is not counted: the median of three values is 2
      assertEquals(0.02, asDouble(median.getResult()), DELTA);
      assertEquals(2.0, median.getOriginalResult());

      // an even count of non-numeric text has no result
      MedianFormula text = new MedianFormula();
      addAll(text, "KING", "HELLO", "12RTT", "1234");
      assertNull(text.getResult());
      assertEquals(0.0, text.getDoubleResult());
      asPercentage(text, 100);
      assertNull(text.getResult());
      assertNull(text.getOriginalResult());

      // an odd count gives the middle text
      text.addValue("12");
      assertEquals("12RTT", text.getResult());
      assertEquals("12RTT", text.getOriginalResult());
      assertEquals(0.0, text.getDoubleResult());

      // only nulls leave nothing to calculate
      MedianFormula nulls = new MedianFormula();
      addAll(nulls, null, null, null, null);
      assertTrue(nulls.isNull());
      assertNull(nulls.getResult());
      nulls.setDefaultResult(true);
      assertEquals(0.0, nulls.getResult());
      assertTrue(nulls.isDefaultResult());

      // a null in the middle of numeric strings is skipped as well
      MedianFormula strings = new MedianFormula();
      addAll(strings, "1", null, "3", null, "100");
      assertEquals(3.0, strings.getResult());
   }

   // Bug #80539: Median of numeric text is unchanged, ordered by value and not by text
   @Test
   void getResult_numericStrings_orderedByValue() {
      assertEquals(10.0, medianOf("10", "9", "100"));
   }

   // Bug #80697: Median skips a blank string like Sum
   @Test
   void getResult_blankStrings_skippedWherever() {
      MedianFormula median = new MedianFormula();
      addAll(median, "1", "", "3");
      assertEquals(2.0, median.getDoubleResult(), DELTA);
      assertFalse(median.isNull());

      // whitespace is blank too, wherever it is in the list
      median.reset();
      addAll(median, "", "5", " ", "1", "  ", "3", "");
      assertEquals(3.0, median.getResult());

      // numbers mixed with blank strings
      median.reset();
      addAll(median, 10, "", 20, "30", "");
      assertEquals(20.0, median.getResult());

      // the percentage option works on the values that are left
      median.reset();
      addAll(median, "2", "", "4");
      asPercentage(median, 10);
      assertEquals(0.3, asDouble(median.getResult()), DELTA);
      assertEquals(3.0, median.getOriginalResult());

      // a clone keeps the values that were kept
      MedianFormula copy = (MedianFormula) median.clone();
      assertEquals(3.0, copy.getOriginalResult());

      // only blank strings leave nothing to calculate, like only nulls
      MedianFormula blanks = new MedianFormula();
      addAll(blanks, "", " ");
      assertTrue(blanks.isNull());
      assertNull(blanks.getResult());
      blanks.setDefaultResult(true);
      assertEquals(0.0, blanks.getResult());
   }

   // Bug #80697: blank strings change nothing, and other values are unchanged
   @Test
   void getResult_blankStrings_changeNothingInRandomLists() {
      Object[] pool = { null, "", " ", "1", "2.5", "-4", "abc", 3, 7.5, 2L };
      Random random = new Random(80697L);

      for(int i = 0; i < 3000; i++) {
         List<Object> values = new ArrayList<>();
         List<Object> noBlanks = new ArrayList<>();
         int size = random.nextInt(8);

         for(int j = 0; j < size; j++) {
            Object value = pool[random.nextInt(pool.length)];
            values.add(value);

            if(!(value instanceof String) || !((String) value).trim().isEmpty()) {
               noBlanks.add(value);
            }
         }

         // the percentile the ported test drew for Pth Percentile, kept so the lists stay the same
         random.nextInt(101);
         MedianFormula median = new MedianFormula();
         MedianFormula medianNoBlanks = new MedianFormula();
         values.forEach(median::addValue);
         noBlanks.forEach(medianNoBlanks::addValue);
         assertEquals(medianNoBlanks.getResult(), median.getResult(), String.valueOf(values));
      }
   }
}
