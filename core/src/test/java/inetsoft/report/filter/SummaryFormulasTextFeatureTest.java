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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.XConstants;
import inetsoft.util.Tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Concat (comma separated) summary formula, driven directly through the formula API and
 * through a real table summary.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SummaryFormulasTextFeatureTest {
   private static void addAll(Formula formula, Object... values) {
      for(Object value : values) {
         formula.addValue(value);
      }
   }

   @Test
   @DisplayName("gap:report.summary-formulas.text#concat-comma-separated")
   void concatCommaSeparated() {
      ConcatFormula concat = new ConcatFormula();
      assertEquals(XConstants.CONCAT_FORMULA, concat.getName());
      assertEquals(String.class, concat.getResultType());

      addAll(concat, "alpha", "beta", "gamma");
      assertEquals("alpha,beta,gamma", concat.getResult());
      assertFalse(concat.isNull());

      // arrival order, not sorted
      concat.reset();
      addAll(concat, "c", "a", "b");
      assertEquals("c,a,b", concat.getResult());

      // numbers and a date use their toString
      concat.reset();
      Timestamp when = Timestamp.valueOf("2024-06-12 10:30:00");
      addAll(concat, 3, 4.5, when);
      assertEquals("3,4.5,2024-06-12 10:30:00.0", concat.getResult());

      // the primitive forms append the same way
      concat.reset();
      concat.addValue(7);
      concat.addValue(8L);
      concat.addValue((short) 9);
      concat.addValue(1.5f);
      concat.addValue(2.5);
      concat.addValue(new double[] { 6.5, 99 });
      assertEquals("7,8,9,1.5,2.5,6.5", concat.getResult());

      // a primitive null marker is skipped
      concat.reset();
      concat.addValue(1);
      concat.addValue(Tool.NULL_INTEGER);
      concat.addValue(Tool.NULL_DOUBLE);
      concat.addValue(Tool.NULL_LONG);
      concat.addValue(Tool.NULL_FLOAT);
      concat.addValue(Tool.NULL_SHORT);
      concat.addValue(new double[] { Tool.NULL_DOUBLE });
      concat.addValue(2);
      assertEquals("1,2", concat.getResult());

      // a clone starts empty but keeps the default-result option
      concat.setDefaultResult(true);
      ConcatFormula copy = (ConcatFormula) concat.clone();
      assertTrue(copy.isDefaultResult());
      assertTrue(copy.isNull());
      assertNull(copy.getResult());
      assertEquals("1,2", concat.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.text#concat-comma-separated")
   void concatInATableSummaryJoinsEachGroup() {
      TableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" },
         { "a", "x" }, { "a", "y" }, { "a", "z" }, { "b", "p" }, { "b", "q" }
      });
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, new ConcatFormula(), new ConcatFormula());
      summary.moreRows(Integer.MAX_VALUE);

      // one row for each group, then the grand total
      assertEquals(4, summary.getRowCount());
      assertEquals("a", summary.getObject(1, 0));
      assertEquals("x,y,z", summary.getObject(1, 1));
      assertEquals("b", summary.getObject(2, 0));
      assertEquals("p,q", summary.getObject(2, 1));
      assertEquals("x,y,z,p,q", summary.getObject(3, 1));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.text#concat-default-result-null")
   void concatDefaultResultNull() {
      ConcatFormula concat = new ConcatFormula();
      assertFalse(concat.isDefaultResult());
      concat.setDefaultResult(true);
      assertTrue(concat.isDefaultResult());

      assertNull(concat.getResult());
      assertTrue(concat.isNull());

      // a value turns the result back into the joined string
      concat.addValue("a");
      assertEquals("a", concat.getResult());

      // reset returns to no result, null again, the option is kept
      concat.reset();
      assertNull(concat.getResult());

      // only nulls added is still no result
      concat.addValue((Object) null);
      assertNull(concat.getResult());

      // without the option the same empty formula gives the empty string
      concat.setDefaultResult(false);
      assertEquals("", concat.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.text#concat-no-double-result")
   void concatNoDoubleResult() {
      ConcatFormula concat = new ConcatFormula();
      RuntimeException empty = assertThrows(RuntimeException.class, concat::getDoubleResult);
      assertEquals("Unsupported method called!", empty.getMessage());

      // still unsupported with values, even numeric ones
      addAll(concat, 1, 2);
      RuntimeException full = assertThrows(RuntimeException.class, concat::getDoubleResult);
      assertEquals("Unsupported method called!", full.getMessage());
      assertEquals("1,2", concat.getResult());
   }

   @Test
   @DisplayName("gap:report.summary-formulas.text#concat-no-values-empty-string")
   void concatNoValuesEmptyString() {
      ConcatFormula concat = new ConcatFormula();
      assertEquals("", concat.getResult());
      assertTrue(concat.isNull());

      // after use, reset gives back the empty string and null
      addAll(concat, "a", "b");
      assertEquals("a,b", concat.getResult());
      assertFalse(concat.isNull());
      concat.reset();
      assertEquals("", concat.getResult());
      assertTrue(concat.isNull());

      // only nulls added: still no values
      addAll(concat, null, null);
      assertEquals("", concat.getResult());
      assertTrue(concat.isNull());

      // a table summary of a group whose values are all null shows the empty string
      TableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", null }, { "a", null }, { "b", "k" }
      });
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, new ConcatFormula(), null);
      summary.moreRows(Integer.MAX_VALUE);
      assertEquals(3, summary.getRowCount());
      assertEquals("a", summary.getObject(1, 0));
      assertEquals("", summary.getObject(1, 1));
      assertEquals("k", summary.getObject(2, 1));
   }

   @Test
   @DisplayName("gap:report.summary-formulas.text#concat-skips-null")
   void concatSkipsNull() {
      ConcatFormula concat = new ConcatFormula();
      addAll(concat, null, "a", null, "b", null);
      assertEquals("a,b", concat.getResult());

      // a leading null leaves no leading comma
      concat.reset();
      addAll(concat, null, null, "x");
      assertEquals("x", concat.getResult());

      // all nulls: nothing
      concat.reset();
      addAll(concat, null, null);
      assertEquals("", concat.getResult());
      assertTrue(concat.isNull());

      // blank strings are kept as entries
      concat.reset();
      addAll(concat, "a", "", " ", null, "b");
      assertEquals("a,, ,b", concat.getResult());
   }

   @Test
   @DisplayName("Bug #80621: gap:report.summary-formulas.text#concat-skips-null")
   void concatKeepsALeadingBlankString() {
      ConcatFormula concat = new ConcatFormula();
      addAll(concat, "", "a");
      assertEquals(",a", concat.getResult());

      concat.reset();
      addAll(concat, "", "", "a");
      assertEquals(",,a", concat.getResult());

      // one entry per value, the same as when the blank string comes last
      concat.reset();
      addAll(concat, "a", "");
      assertEquals("a,", concat.getResult());

      // nulls before a leading blank string are still skipped
      concat.reset();
      addAll(concat, null, "", null, "a");
      assertEquals(",a", concat.getResult());

      // only blank strings: one empty entry each, and the formula has a result
      concat.reset();
      addAll(concat, "", "");
      assertEquals(",", concat.getResult());
      assertFalse(concat.isNull());

      concat.reset();
      addAll(concat, "");
      assertEquals("", concat.getResult());
      assertFalse(concat.isNull());

      // the typed overloads take the separator from the count too
      concat.reset();
      concat.addValue("");
      concat.addValue(1);
      concat.addValue(2L);
      concat.addValue((short) 3);
      concat.addValue(4.5f);
      concat.addValue(5.5);
      concat.addValue(new double[] { 6.5 });
      assertEquals(",1,2,3,4.5,5.5,6.5", concat.getResult());
   }

   @Test
   @DisplayName("Bug #80621: a table summary keeps a group's leading blank string")
   void concatInATableSummaryKeepsALeadingBlankString() {
      TableLens data = new DefaultTableLens(new Object[][] {
         { "g", "v" }, { "a", "" }, { "a", "x" }, { "b", "p" }, { "b", "" }, { "c", "" }
      });
      SummaryFilter summary = new SummaryFilter(
         data, new int[] { 0 }, new int[] { 1 }, new ConcatFormula(), new ConcatFormula());
      summary.moreRows(Integer.MAX_VALUE);

      assertEquals(5, summary.getRowCount());
      assertEquals(",x", summary.getObject(1, 1));
      assertEquals("p,", summary.getObject(2, 1));
      assertEquals("", summary.getObject(3, 1));
      assertEquals(",x,p,,", summary.getObject(4, 1));
   }

   @Test
   @DisplayName("Bug #80621: sequences that did not start with a blank string are unchanged")
   void concatSequencesWithoutALeadingBlankAreUnchanged() {
      Object[] pool = { null, "", " ", "a", "b,c", 7, 2.5, 3L, new Timestamp(0L) };
      java.util.Random random = new java.util.Random(80621L);
      int compared = 0;
      int changed = 0;

      for(int i = 0; i < 3000; i++) {
         Object[] values = new Object[random.nextInt(7)];

         for(int j = 0; j < values.length; j++) {
            values[j] = pool[random.nextInt(pool.length)];
         }

         ConcatFormula concat = new ConcatFormula();
         concat.setDefaultResult(random.nextBoolean());
         addAll(concat, values);
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
                      actual, java.util.Arrays.toString(values));

         if(Objects.equals(before, actual)) {
            compared++;
         }
         else {
            changed++;
            // the result differs only when a blank string was written before any other text
            assertTrue(startsWithBlank(values), java.util.Arrays.toString(values));
         }
      }

      assertTrue(compared > 1000);
      assertTrue(changed > 100);
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
