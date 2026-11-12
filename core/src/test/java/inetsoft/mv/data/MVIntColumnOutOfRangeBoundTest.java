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
package inetsoft.mv.data;

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #80155: a condition bound outside the int range (e.g. a Long variable default 3000000000)
 * reached MVIntColumn as double bits and was narrowed with an (int) cast, which wraps. The bound
 * landed below the column min, so '&gt;' returned every row. The result must depend only on where
 * the bound lies relative to the data, never on the wrap.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVIntColumnOutOfRangeBoundTest {
   // row 0 is null, rows 1..25 hold the values 1..25, row 26 is null
   private static final int N = 27;

   @Test
   void boundAboveMax() {
      MVIntColumn col = column();

      for(double b : new double[] { 3e9, 4294967297.0, 5e18, 30 }) {
         String m = "bound " + b;
         assertEquals(rows(), col.getRows(">", bits(b), false, true), m + " >");
         assertEquals(rows(), col.getRows(">=", bits(b), false, true), m + " >=");
         assertEquals(nonNull(), col.getRows("<", bits(b), false, true), m + " <");
         assertEquals(nonNull(), col.getRows("<=", bits(b), false, true), m + " <=");
         assertEquals(nonNull(), col.getRows("=", bits(b), true, true), m + " not =");
         // NOT is folded into the range call, so the negations mirror the plain operators
         assertEquals(nonNull(), col.getRows(">", bits(b), true, true), m + " not >");
         assertEquals(nonNull(), col.getRows(">=", bits(b), true, true), m + " not >=");
         assertEquals(rows(), col.getRows("<", bits(b), true, true), m + " not <");
         assertEquals(rows(), col.getRows("<=", bits(b), true, true), m + " not <=");
      }
   }

   @Test
   void boundBelowMin() {
      MVIntColumn col = column();

      for(double b : new double[] { -3e9, -4294967297.0, -5e18, -2147483649.0, -1, 0 }) {
         String m = "bound " + b;
         assertEquals(nonNull(), col.getRows(">", bits(b), false, true), m + " >");
         assertEquals(nonNull(), col.getRows(">=", bits(b), false, true), m + " >=");
         assertEquals(rows(), col.getRows("<", bits(b), false, true), m + " <");
         assertEquals(rows(), col.getRows("<=", bits(b), false, true), m + " <=");
         assertEquals(nonNull(), col.getRows("=", bits(b), true, true), m + " not =");
         assertEquals(rows(), col.getRows(">", bits(b), true, true), m + " not >");
         assertEquals(nonNull(), col.getRows("<", bits(b), true, true), m + " not <");
      }
   }

   @Test
   void wrapDoesNotFalseMatch() {
      MVIntColumn col = column();
      // 4294967297 = 2^32 + 1 used to wrap to 1 and match the row holding 1
      assertEquals(rows(), col.getRows("=", bits(4294967297.0), false, true));
      assertEquals(rows(), col.getRows("IN", new long[] { bits(4294967297.0) }, false, true));
      assertEquals(rows(), col.getRows("=", bits(3e9), false, true));
      assertEquals(rows(), col.getRows("=", bits(-3e9), false, true));
   }

   @Test
   void inDropsOutOfRangeMembers() {
      MVIntColumn col = column();
      long[] mixed = { bits(4294967297.0), bits(3), bits(-3e9), bits(5e18) };
      assertEquals(rows(3), col.getRows("IN", mixed, false, true));
      assertEquals(rows(), col.getRows("IN", new long[] { bits(3e9), bits(-3e9) }, false, true));
      // not in: only the out of range members are dropped, null rows never match
      BitSet expect = new BitSet();

      for(int i = 1; i <= 25; i++) {
         if(i != 3) {
            expect.set(i);
         }
      }

      assertEquals(expect, col.getRows("IN", mixed, true, true));
      assertEquals(nonNull(), col.getRows("IN", new long[] { bits(3e9), bits(-3e9) }, true, true));
      assertEquals(nonNull(), col.getRows("IN", new long[] { bits(3e9) }, true, true));
   }

   @Test
   void betweenWithOutOfRangeBound() {
      MVIntColumn col = column();
      assertEquals(nonNull(), col.getRows("BETWEEN", new long[] { bits(-3e9), bits(3e9) },
                                          false, true));
      assertEquals(range(20, 25), col.getRows("BETWEEN", new long[] { bits(20), bits(3e9) },
                                               false, true));
      assertEquals(range(1, 5), col.getRows("BETWEEN", new long[] { bits(-3e9), bits(5) },
                                             false, true));
      assertEquals(rows(), col.getRows("BETWEEN", new long[] { bits(3e9), bits(4e9) },
                                       false, true));
      assertEquals(nonNull(), col.getRows("BETWEEN", new long[] { bits(3e9), bits(4e9) },
                                          true, true));
   }

   @Test
   void inRangeUnchanged() {
      MVIntColumn col = column();
      assertEquals(range(21, 25), col.getRows(">", bits(20), false, true));
      assertEquals(range(20, 25), col.getRows(">=", bits(20), false, true));
      assertEquals(range(1, 19), col.getRows("<", bits(20), false, true));
      assertEquals(range(1, 20), col.getRows("<=", bits(20), false, true));
      assertEquals(rows(20), col.getRows("=", bits(20), false, true));
      assertEquals(rows(), col.getRows(">", bits(2147483647.0), false, true));
      assertEquals(rows(), col.getRows(">", bits(25), false, true));
      assertEquals(rows(25), col.getRows(">=", bits(25), false, true));
      assertEquals(rows(1), col.getRows("<=", bits(1), false, true));
      assertEquals(rows(), col.getRows("<", bits(1), false, true));
      assertEquals(range(2, 25), col.getRows("=", bits(1), true, true));
      assertEquals(rows(1, 2, 3), col.getRows("IN", new long[] { bits(1), bits(2), bits(3) },
                                              false, true));
   }

   @Test
   void nullHandling() {
      MVIntColumn col = column();
      long nul = Double.doubleToLongBits(Tool.NULL_DOUBLE);
      assertEquals(rows(0, 26), col.getRows("null", nul, false, true));
      assertEquals(nonNull(), col.getRows("null", nul, true, true));
      // an ordinary bound never returns the null rows
      assertEquals(nonNull(), col.getRows(">=", bits(-3e9), false, true));
      assertEquals(nonNull(), col.getRows("<=", bits(3e9), false, true));
   }

   @Test
   void negativeRangeColumn() {
      MVIntColumn col = new MVIntColumn(5, -5, 7);
      double[] vals = { Tool.NULL_DOUBLE, -5, 0, 7, -5 };

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      assertEquals(rows(1, 2, 3, 4), col.getRows(">", bits(-3e9), false, true));
      assertEquals(rows(), col.getRows(">", bits(3e9), false, true));
      assertEquals(rows(1, 2, 3, 4), col.getRows("<", bits(3e9), false, true));
      assertEquals(rows(), col.getRows("<", bits(-3e9), false, true));
      assertEquals(rows(2, 3), col.getRows(">", bits(-5), false, true));
      assertEquals(rows(1, 2, 4), col.getRows("<=", bits(0), false, true));
   }

   @Test
   void columnWithoutMin() {
      // created without min/max: 32 bit keys, null is stored as Integer.MIN_VALUE
      MVIntColumn col = new MVIntColumn(4, null, null);
      double[] vals = { Tool.NULL_DOUBLE, 5, 100000, -7 };

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      assertEquals(rows(), col.getRows(">", bits(3e9), false, true));
      assertEquals(rows(), col.getRows("=", bits(4294967301.0), false, true));
      assertEquals(rows(1, 2, 3), col.getRows(">", bits(-3e9), false, true));
      // not asserted: "<" with an unbounded lower side starts at key 0 for a column without min
      // (existing behavior, negative values are skipped), unrelated to the bound narrowing
      assertEquals(rows(), col.getRows("<", bits(-3e9), false, true));
      assertEquals(rows(1), col.getRows("=", bits(5), false, true));
   }

   private static MVIntColumn column() {
      MVIntColumn col = new MVIntColumn(N, 1, 25);

      for(int i = 0; i < N; i++) {
         col.setValue(i, (i >= 1 && i <= 25) ? i : Tool.NULL_DOUBLE);
      }

      return col;
   }

   private static long bits(double v) {
      return Double.doubleToLongBits(v);
   }

   private static BitSet nonNull() {
      return range(1, 25);
   }

   private static BitSet range(int from, int to) {
      BitSet set = new BitSet();

      for(int i = from; i <= to; i++) {
         set.set(i);
      }

      return set;
   }

   private static BitSet rows(int... rows) {
      BitSet set = new BitSet();

      for(int row : rows) {
         set.set(row);
      }

      return set;
   }
}
