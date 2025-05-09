/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class MVQueryTest {
   @Test
   public void testSerializeSwappableAggregateTable() throws Exception {
      String[] headers = new String[]{ "col1", "col2", "col3" };
      String[] mdates = new String[3];
      boolean[] tscols = new boolean[3];
      MVQuery.SwappableAggregateTable originalTable = new MVQuery.SwappableAggregateTable(
         3, XTableUtil.getDefaultTableLens(), headers, mdates, tscols);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(MVQuery.SwappableAggregateTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeAggregateTable() throws Exception {
      String[] headers = new String[]{ "col1", "col2", "col3" };
      String[] mdates = new String[3];
      boolean[] tscols = new boolean[3];
      MVQuery.AggregateTable originalTable = new MVQuery.AggregateTable(
         3, XTableUtil.getDefaultData(), headers, mdates, tscols);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(MVQuery.AggregateTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializePagedTableLens0() throws Exception {
      Object[][] data = XTableUtil.getDefaultData();

      int nrow = data.length;
      int ncol = data[0].length;
      MVQuery.PagedTableLens0 originalTable = new MVQuery.PagedTableLens0();
      originalTable.setColCount(ncol);

      for(int r = 0; r < nrow; r++) {
         originalTable.addRow(data[r]);
      }

      originalTable.complete();

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(MVQuery.PagedTableLens0.class, deserializedTable.getClass());
   }
}
