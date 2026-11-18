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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PopulationStandardDeviationFormula, Bug #80974: a blank string is no value; the double[] pair is members 0 and 1.
 */
@Tag("core")
class PopulationStandardDeviationFormulaTest {
   @Test
   void blankString_isSkipped() {
      PopulationStandardDeviationFormula f = new PopulationStandardDeviationFormula();
      for(Object o : new Object[] { 2, "", 4, " ", 6 }) {
         f.addValue(o);
      }
      assertEquals(Math.sqrt(8.0 / 3), ((Number) f.getResult()).doubleValue(), 1e-9);
   }

   private static final double DELTA = 1e-9;

   private static void addAll(Formula target, Object... values) {
      for(Object value : values) {
         target.addValue(value);
      }
   }

   private static void asPercentage(PercentageFormula target, Object total) {
      target.setPercentageType(StyleConstants.PERCENTAGE_OF_GROUP);
      target.setTotal(total);
   }

   private static double asDouble(Object result) {
      assertInstanceOf(Double.class, result);
      return (Double) result;
   }

   // gap:report.summary-formulas.statistical#population-standard-deviation
   @Test
   void populationDeviation_dividesByN() {
      PopulationStandardDeviationFormula deviation = new PopulationStandardDeviationFormula();
      addAll(deviation, 20, 1, -10, 20, 0.01);
      assertEquals(11.905260013960216, asDouble(deviation.getResult()), DELTA);
      assertEquals(11.905260013960216, deviation.getDoubleResult(), DELTA);

      // the square root of the population variance, divided by n
      deviation.reset();
      addAll(deviation, "1", "2", "3", "4", "5.5", "a", null);
      assertEquals(1.5620499351813308, asDouble(deviation.getResult()), DELTA);

      deviation.reset();
      addAll(deviation, 1, 3);
      assertEquals(1.0, deviation.getResult());

      deviation.reset();
      addAll(deviation, 0, 1, 10, null);
      assertEquals(4.4969125210773475, asDouble(deviation.getResult()), DELTA);

      PopulationStandardDeviationFormula copy =
         (PopulationStandardDeviationFormula) deviation.clone();
      assertEquals(4.4969125210773475, asDouble(copy.getResult()), DELTA);
   }

   // gap:report.summary-formulas.statistical#variance-family-percentage-of-total
   @Test
   void percentageOfTotal() {
      PopulationStandardDeviationFormula popDeviation = new PopulationStandardDeviationFormula();
      addAll(popDeviation, 30, 1, -10, 20, 7.2, 0.01, null);
      asPercentage(popDeviation, 100);
      assertEquals(0.13325869264954788, asDouble(popDeviation.getResult()), DELTA);
      assertEquals(0.13325869264954788, popDeviation.getDoubleResult(), DELTA);
      assertEquals(13.325869264954788, asDouble(popDeviation.getOriginalResult()), DELTA);

      // a zero or negative total gives no percentage, and the original result is unchanged
      PopulationStandardDeviationFormula other = new PopulationStandardDeviationFormula();
      addAll(other, 2, 4, 6, 8);
      asPercentage(other, 0);
      assertNull(other.getResult());
      assertEquals(0.0, other.getDoubleResult());
      assertNotNull(other.getOriginalResult());
      other.setDefaultResult(true);
      assertNull(other.getResult());

      other.setTotal(-100);
      assertNull(other.getResult());
      assertNotNull(other.getOriginalResult());

      // a total that is not a number gives none either, a missing total as well
      other.setTotal("n/a");
      assertNull(other.getResult());
      other.setTotal(null);
      assertNull(other.getResult());

      // a numeric string is a total
      other.setTotal("100");
      assertNotNull(other.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-no-numeric-input-null
   @Test
   void noNumericInput_isNull() {
      PopulationStandardDeviationFormula deviation = new PopulationStandardDeviationFormula();
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());
      assertEquals(0.0, deviation.getDoubleResult());

      // dates and nulls are not numbers
      addAll(deviation, new Date(1_000_000L), new Date(2_000_000L), null);
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());

      deviation.reset();
      addAll(deviation, null, null, null, null);
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());

      deviation.addValue(Tool.NULL_DOUBLE);
      deviation.addValue(Tool.NULL_INTEGER);
      deviation.addValue(Tool.NULL_LONG);
      deviation.addValue(Tool.NULL_SHORT);
      deviation.addValue(Tool.NULL_FLOAT);
      deviation.addValue(new double[] { Tool.NULL_DOUBLE });
      addAll(deviation, "text", "x1");
      assertTrue(deviation.isNull());
      assertNull(deviation.getResult());

      // with the default-result flag nothing gives 0 instead of null
      deviation.setDefaultResult(true);
      assertEquals(0.0, deviation.getResult());
      deviation.setDefaultResult(false);

      // a number ends the empty state
      deviation.addValue(5);
      assertFalse(deviation.isNull());
      assertNotNull(deviation.getResult());
   }

   // gap:report.summary-formulas.statistical#variance-single-value-is-zero
   @Test
   void singleValueOfAnyType_isZero() {
      PopulationStandardDeviationFormula deviation = new PopulationStandardDeviationFormula();
      deviation.addValue(1);
      assertFalse(deviation.isNull());
      assertEquals(0.0, deviation.getResult());
      assertEquals(0.0, deviation.getDoubleResult());

      // the same for another type, after the nulls and the text that are skipped
      deviation.reset();
      addAll(deviation, null, "text", 42.5);
      assertEquals(0.0, deviation.getResult());

      deviation.reset();
      deviation.addValue(-7L);
      assertEquals(0.0, deviation.getResult());

      // as a percentage it is 0 of a positive total
      asPercentage(deviation, 50);
      assertEquals(0.0, asDouble(deviation.getResult()));
      assertEquals(0.0, deviation.getOriginalResult());

      // two different values have a spread
      deviation.addValue(-5L);
      assertTrue(asDouble(deviation.getOriginalResult()) > 0);
   }

   // Bug #80868: Population Standard Deviation skips a blank string next to text that is not
   // a number
   @Test
   void blankStringNextToText_isSkipped() {
      PopulationStandardDeviationFormula clean = new PopulationStandardDeviationFormula();
      addAll(clean, "2", "4", "6");

      // text that is not a number was already skipped, and a blank next to it changes nothing
      PopulationStandardDeviationFormula deviation = new PopulationStandardDeviationFormula();
      addAll(deviation, "2", "abc", "", "4", " ", "6");
      assertEquals(clean.getResult(), deviation.getResult());
   }

   // Bug #80868: blank strings change nothing in the variance family
   @Test
   void blankStrings_changeNothingInRandomLists() {
      Random random = new Random(80868L);
      Object[] pool = { 1, 2.5, -3, 10L, "4", "-7.5", " 8 ", "", "  ", "abc", "x1", null };

      for(int i = 0; i < 400; i++) {
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

         PopulationStandardDeviationFormula all = new PopulationStandardDeviationFormula();
         PopulationStandardDeviationFormula clean = new PopulationStandardDeviationFormula();
         values.forEach(all::addValue);
         noBlanks.forEach(clean::addValue);
         assertEquals(clean.getResult(), all.getResult(), String.valueOf(values));
      }
   }
}
