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
import inetsoft.uql.jdbc.XBinaryCondition;
import inetsoft.uql.jdbc.XExpression;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79505: a 'not equal to' condition on an MV column dropped the rows containing null (or
 * empty), because it is evaluated as the two index ranges around the value and ranges never
 * contain null. Non-MV evaluation ({@code Condition}) keeps them. 'not in' must keep dropping
 * null, as non-MV does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubMVNotEqualNullTest {
   // dictionary keys per row, 0 is null: rows 0 and 3 are null
   private static final int[] KEYS = { 0, 1, 2, 0, 3, 1 };

   @Test
   void bitDimNotEqualKeepsNull() throws Exception {
      assertEquals(rows(0, 1, 3, 4, 5), eval(bitDim(), "=", "2", true));
   }

   @Test
   void dictDimNotEqualKeepsNull() throws Exception {
      assertEquals(rows(0, 1, 3, 4, 5), eval(dictDim(), "=", "2", true));
   }

   @Test
   void measureNotEqualKeepsNull() throws Exception {
      // null, 5, 0, null, 7, 0
      assertEquals(rows(0, 1, 3, 4), eval(measure(), "=", "0", true));
   }

   @Test
   void notEqualToNullDoesNotAddNull() throws Exception {
      // null != null is false
      assertEquals(rows(1, 2, 4, 5), eval(measure(), "=", null, true));
   }

   @Test
   void columnWithoutNullUnchanged() throws Exception {
      BitDimIndex idx = new BitDimIndex();
      int[] keys = { 0, 1, 2, 0 };

      for(int row = 0; row < keys.length; row++) {
         idx.addKey(keys[row], row);
      }

      idx.complete();
      assertEquals(rows(0, 2, 3), eval(idx, "=", "1", true, false));
   }

   @Test
   void notInStillDropsNull() throws Exception {
      assertEquals(rows(1, 4, 5), eval(bitDim(), "IN", new Object[] { "2" }, true));
   }

   @Test
   void equalAndRangeUnchanged() throws Exception {
      assertEquals(rows(2), eval(bitDim(), "=", "2", false));
      assertEquals(rows(1, 5), eval(bitDim(), "<", "2", false));
      assertEquals(rows(1, 5), eval(bitDim(), ">=", "2", true));
   }

   private static BitSet eval(XDimIndex dim, String op, Object val, boolean not) throws Exception {
      return eval(dim, op, val, not, true);
   }

   private static BitSet eval(XDimIndex dim, String op, Object val, boolean not, boolean cnull)
      throws Exception
   {
      DefaultTableBlock table = mock(DefaultTableBlock.class);
      when(table.indexOfHeader("col")).thenReturn(0);
      SubMV sub = new SubMV(new XDimIndex[] { dim }, table);
      Field f = SubMV.class.getDeclaredField("rtdims");
      f.setAccessible(true);
      f.set(sub, new XDimIndex[] { dim });

      XBinaryCondition cond =
         new XBinaryCondition(new XExpression("col", XExpression.FIELD),
                              new XExpression(val, XExpression.VALUE), op);
      cond.setIsNot(not);
      cond.setAttribute("containsNull", cnull + "");
      return sub.getRows(cond);
   }

   private static BitDimIndex bitDim() {
      BitDimIndex idx = new BitDimIndex();

      for(int row = 0; row < KEYS.length; row++) {
         idx.addKey(KEYS[row], row);
      }

      idx.complete();
      return idx;
   }

   private static DictDimIndex dictDim() {
      DictDimIndex idx = new DictDimIndex(4);

      for(int row = 0; row < KEYS.length; row++) {
         idx.addKey(KEYS[row], row);
      }

      idx.complete();
      return idx;
   }

   private static MVDoubleColumn measure() {
      double[] vals = { Tool.NULL_DOUBLE, 5, 0, Tool.NULL_DOUBLE, 7, 0 };
      MVDoubleColumn col = new MVDoubleColumn(null, 0, null, vals.length, true);

      for(int i = 0; i < vals.length; i++) {
         col.setValue(i, vals[i]);
      }

      return col;
   }

   private static BitSet rows(int... rows) {
      BitSet set = new BitSet();

      for(int row : rows) {
         set.set(row);
      }

      return set;
   }
}
