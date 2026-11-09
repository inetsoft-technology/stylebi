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
import inetsoft.uql.XNode;
import inetsoft.uql.jdbc.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80570: an MV incremental update deletes the rows of a 'not equal to' delete condition
 * together with the null rows, since the MV creation keeps only the rows where the negated
 * condition ('equal to') is true, which never includes null. A query keeps dropping null for
 * 'not equal to' (bug #79870, {@link SubMVNotEqualNullTest}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SubMVDeleteNullRowsTest {
   // dictionary keys per row, 0 is null: rows 0 and 3 are null
   private static final int[] KEYS = { 0, 1, 2, 0, 3, 1 };

   @Test
   void bitDimNotEqualDeletesNullRows() throws Exception {
      assertEquals(rows(0, 1, 3, 4, 5), delete(bitDim(), "=", "2", true));
   }

   @Test
   void dictDimNotEqualDeletesNullRows() throws Exception {
      assertEquals(rows(0, 1, 3, 4, 5), delete(dictDim(), "=", "2", true));
   }

   @Test
   void measureNotEqualDeletesNullRows() throws Exception {
      // null, 5, 0, null, 7, 0
      assertEquals(rows(0, 1, 3, 4), delete(measure(), "=", "0", true));
   }

   @Test
   void queryNotEqualStillDropsNull() throws Exception {
      // bug #79870 is unchanged for queries
      assertEquals(rows(1, 4, 5), sub(bitDim()).getRows(cond("=", "2", true, true)));
      assertEquals(rows(1, 4, 5), sub(dictDim()).getRows(cond("=", "2", true, true)));
      assertEquals(rows(1, 4), sub(measure()).getRows(cond("=", "0", true, true)));
   }

   @Test
   void notEqualToNullDoesNotAddNullRows() throws Exception {
      // not equal to null deletes the non-null rows only, as a query returns them
      assertEquals(rows(1, 2, 4, 5), delete(measure(), "=", null, true));
      assertEquals(query(measure(), "=", null, true), delete(measure(), "=", null, true));
   }

   @Test
   void notEqualToNullKeyDoesNotAddNullRows() throws Exception {
      // MV.fixFilter maps a null or '' value to the null key 0 of a column containing null:
      // the delete must not take the null rows too (creation keeps them for '= null')
      assertEquals(rows(1, 2, 4, 5), delete(bitDim(), "=", "0", true));
      assertEquals(rows(1, 2, 4, 5), delete(dictDim(), "=", "0", true));
      assertEquals(query(bitDim(), "=", "0", true), delete(bitDim(), "=", "0", true));
      assertEquals(query(dictDim(), "=", "0", true), delete(dictDim(), "=", "0", true));
   }

   @Test
   void dictionaryFixedNullOrEmptyValueDeletesNoMoreThanAQuery() throws Exception {
      String[] col = { null, "a", "b", "", "c", "a" };

      for(String val : new String[] { "", null }) {
         assertEquals(fixedRows(col, val, false), fixedRows(col, val, true), "value " + val);
         assertEquals(rows(1, 2, 4, 5), fixedRows(col, val, true), "value " + val);
      }

      // a real value still deletes the null (and '') rows
      assertEquals(rows(0, 1, 3, 4, 5), fixedRows(col, "b", true));
      assertEquals(rows(1, 4, 5), fixedRows(col, "b", false));
   }

   @Test
   void columnWithoutNullUnchanged() throws Exception {
      BitDimIndex idx = new BitDimIndex();
      int[] keys = { 0, 1, 2, 0 };

      for(int row = 0; row < keys.length; row++) {
         idx.addKey(keys[row], row);
      }

      idx.complete();
      XBinaryCondition cond = cond("=", "1", true, false);
      assertEquals(rows(0, 2, 3), sub(idx).getRows(cond, true));
   }

   @Test
   void otherOperatorsUnchanged() throws Exception {
      // plain equal, is (not) null, not in and ranges delete what a query returns
      assertEquals(rows(2), delete(bitDim(), "=", "2", false));
      assertEquals(query(bitDim(), "null", null, true), delete(bitDim(), "null", null, true));
      assertEquals(query(bitDim(), "null", null, false), delete(bitDim(), "null", null, false));
      assertEquals(rows(1, 4, 5), delete(bitDim(), "IN", new Object[] { "2" }, true));
      assertEquals(rows(1, 5), delete(bitDim(), "<", "2", false));
      assertEquals(rows(1, 5), delete(bitDim(), ">=", "2", true));
   }

   @Test
   void notEqualInsideOrSetDeletesNullRows() throws Exception {
      XSet set = new XSet(XSet.OR);
      set.addChild(cond("=", "2", true, true));
      set.addChild(cond("=", "3", false, true));
      assertEquals(rows(0, 1, 3, 4, 5), sub(bitDim()).getRows(set, true));
      assertEquals(rows(1, 4, 5), sub(bitDim()).getRows(set));
   }

   @Test
   void notEqualUnderNegatedSetDoesNotAddNullRows() throws Exception {
      XSet set = new XSet(XSet.OR);
      set.setIsNot(true);
      set.addChild(cond("=", "1", false, true));
      set.addChild(cond("=", "2", true, true));
      assertEquals(sub(bitDim()).getRows(set), sub(bitDim()).getRows(set, true));
   }

   @Test
   void deleteRecordUsesTheDeleteRows() throws Exception {
      SubMV sub = spy(sub(bitDim()));
      XNode cond = cond("=", "2", true, true);
      doReturn(new BitSet()).when(sub).getRows(cond, true);

      assertFalse(sub.deleteRecord(cond));
      verify(sub).getRows(cond, true);
      verify(sub, never()).getRows(cond);
   }

   private static BitSet delete(XDimIndex dim, String op, Object val, boolean not)
      throws Exception
   {
      return sub(dim).getRows(cond(op, val, not, true), true);
   }

   private static BitSet query(XDimIndex dim, String op, Object val, boolean not)
      throws Exception
   {
      return sub(dim).getRows(cond(op, val, not, true));
   }

   /**
    * Rows of 'col != val' on an index built through a dictionary, with the condition fixed by
    * the dictionary as MV.fixFilter does on the delete and query paths.
    */
   private static BitSet fixedRows(String[] col, String val, boolean delete) throws Exception {
      XDimDictionary dict = new XDimDictionary();
      dict.setDataType(String.class);

      for(String v : Arrays.stream(col).filter(v -> v != null && !v.isEmpty()).distinct().sorted()
         .toList())
      {
         dict.addValue(v);
      }

      dict.addValue(null);
      dict.complete();
      BitDimIndex idx = new BitDimIndex();

      for(int row = 0; row < col.length; row++) {
         idx.addKey(dict.indexOf(col[row], row), row);
      }

      idx.complete();
      XBinaryCondition cond =
         new XBinaryCondition(new XExpression("col", XExpression.FIELD),
                              new XExpression(val, XExpression.VALUE), "=");
      cond.setIsNot(true);
      dict.fixFilter(cond);
      return sub(idx).getRows(cond, delete);
   }

   private static SubMV sub(XDimIndex dim) throws Exception {
      DefaultTableBlock table = mock(DefaultTableBlock.class);
      when(table.indexOfHeader("col")).thenReturn(0);
      SubMV sub = new SubMV(new XDimIndex[] { dim }, table);
      Field f = SubMV.class.getDeclaredField("rtdims");
      f.setAccessible(true);
      f.set(sub, new XDimIndex[] { dim });
      return sub;
   }

   private static XBinaryCondition cond(String op, Object val, boolean not, boolean cnull) {
      XBinaryCondition cond =
         new XBinaryCondition(new XExpression("col", XExpression.FIELD),
                              new XExpression(val, XExpression.VALUE), op);
      cond.setIsNot(not);
      cond.setAttribute("containsNull", cnull + "");
      return cond;
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
