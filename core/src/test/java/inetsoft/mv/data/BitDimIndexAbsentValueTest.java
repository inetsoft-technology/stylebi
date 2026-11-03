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
 * Bug #79870: a value that is not in the dictionary reaches the dimension index as a negative
 * insertion code. 'not in' / 'not equal' of such values is true for every non-null row (SQL
 * drops null), but the range api took the code as a position and returned the null rows too.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class BitDimIndexAbsentValueTest {
   // dictionary keys per row, 0 is null: rows 0 and 3 are null
   private static final int[] KEYS = { 0, 1, 2, 0, 3, 1 };

   @Test
   void notInAllAbsent() {
      for(XDimIndex dim : dims()) {
         assertEquals(rows(1, 2, 4, 5), dim.getRows("IN", new long[] { -5, -6 }, true, true),
                      dim.getClass().getSimpleName());
         assertEquals(rows(1, 2, 4, 5), dim.getRows("IN", new long[] { -5 }, true, true),
                      dim.getClass().getSimpleName());
         assertEquals(rows(1, 2, 4, 5), dim.getRows("IN", new long[] { -5, -5, -1 }, true, true),
                      dim.getClass().getSimpleName());
      }
   }

   @Test
   void notInPresentAndAbsent() {
      for(XDimIndex dim : dims()) {
         assertEquals(rows(1, 4, 5), dim.getRows("IN", new long[] { 2, -5 }, true, true),
                      dim.getClass().getSimpleName());
         assertEquals(rows(4), dim.getRows("IN", new long[] { 1, 2, -9 }, true, true),
                      dim.getClass().getSimpleName());
      }
   }

   @Test
   void notEqualAbsent() {
      for(XDimIndex dim : dims()) {
         assertEquals(rows(1, 2, 4, 5), dim.getRows("=", -9, true, true),
                      dim.getClass().getSimpleName());
      }
   }

   @Test
   void absentWithoutNullInDictionary() {
      // no null slot in the dictionary: every row matches
      BitDimIndex idx = new BitDimIndex();
      int[] keys = { 0, 1, 2 };

      for(int row = 0; row < keys.length; row++) {
         idx.addKey(keys[row], row);
      }

      idx.complete();
      assertEquals(rows(0, 1, 2), idx.getRows("IN", new long[] { -5, -6 }, true, false));
      assertEquals(rows(0, 1, 2), idx.getRows("=", -5, true, false));
   }

   @Test
   void inOfAbsentMatchesNothing() {
      for(XDimIndex dim : dims()) {
         assertEquals(rows(), dim.getRows("IN", new long[] { -5, -6 }, false, true),
                      dim.getClass().getSimpleName());
      }
   }

   @Test
   void presentValuesUnchanged() {
      for(XDimIndex dim : dims()) {
         assertEquals(rows(1, 4, 5), dim.getRows("IN", new long[] { 2, 2 }, true, true),
                      dim.getClass().getSimpleName());
         assertEquals(rows(4), dim.getRows("IN", new long[] { 1, 2 }, true, true),
                      dim.getClass().getSimpleName());
         assertEquals(rows(1, 4, 5), dim.getRows("=", 2, true, true),
                      dim.getClass().getSimpleName());
      }
   }

   @Test
   void negativeMeasureValueIsNotAbsent() {
      // a measure column compares raw double bits, a negative long is a real value there
      double[] vals = { Tool.NULL_DOUBLE, -5, 0, 7 };
      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, vals.length, true);

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      assertEquals(rows(2, 3), col.getRows("=", Double.doubleToLongBits(-5), true, true));
   }

   private static XDimIndex[] dims() {
      // 3 bits (PackedIntBuf) and 8 bits (ByteIntBuf)
      XDimIndex[] dims = {
         new BitDimIndex(), new MVDimColumn(4, KEYS.length, true),
         new MVDimColumn(100, KEYS.length, true)
      };

      for(XDimIndex dim : dims) {
         for(int row = 0; row < KEYS.length; row++) {
            if(dim instanceof BitDimIndex) {
               ((BitDimIndex) dim).addKey(KEYS[row], row);
            }
            else {
               ((MVDimColumn) dim).setValue(row, KEYS[row]);
            }
         }

         if(dim instanceof BitDimIndex) {
            ((BitDimIndex) dim).complete();
         }
         else {
            ((DictDimIndex) dim).complete();
         }
      }

      return dims;
   }

   /**
    * An int measure column compares double bit patterns, negative for every negative number.
    * It is a DictDimIndex but must never treat them as absent dictionary keys.
    */
   @Test
   void negativeIntMeasureKeepsOldBehavior() {
      // null, -5, 0, 7, -5
      Double[] vals = { null, -5.0, 0.0, 7.0, -5.0 };
      MVIntColumn col = new MVIntColumn(vals.length, -5, 7);

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i] == null ? Tool.NULL_DOUBLE : vals[i]);
      }

      long m5 = Double.doubleToLongBits(-5);
      long m9 = Double.doubleToLongBits(-9);
      assertEquals(rows(1, 4), col.getRows("=", m5, false, true));
      assertEquals(rows(2, 3), col.getRows("=", m5, true, true));
      assertEquals(rows(2, 3), col.getRows("IN", new long[] { m5, m9 }, true, true));
      assertEquals(rows(1, 4), col.getRows("IN", new long[] { m5 }, false, true));
      assertEquals(rows(2, 3), col.getRows(">", m5, false, true));
      assertEquals(rows(1, 2, 4), col.getRows("<=", 0, false, true));
   }

   private static BitSet rows(int... rows) {
      BitSet set = new BitSet();

      for(int row : rows) {
         set.set(row);
      }

      return set;
   }
}
