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

import inetsoft.mv.*;
import inetsoft.report.TableLens;
import inetsoft.report.internal.Util;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.SreeHome;
import inetsoft.test.TestSerializeUtils;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static inetsoft.test.XTableUtil.date;

@SreeHome
public class XDynamicTableTest {
   @Test
   public void testSerialize() throws Exception {
      TableLens tableLens = new DefaultTableLens(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", date("2021-01-03"), 3 },
         { "a", date("2021-01-05"), 5 },
         { "b", date("2021-01-10"), 10 },
         { "b", date("2021-01-24"), 24 },
         { "c", date("2021-01-24"), 24 },
         });

      ColumnRef dateColRef = new ColumnRef(new AttributeRef("col2"));
      dateColRef.setDataType(XSchema.DATE);
      MVColumn baseDateMVColumn = new MVColumn(dateColRef);

      ColumnRef numColRef = new ColumnRef(new AttributeRef("col3"));
      numColRef.setDataType(XSchema.INTEGER);
      MVColumn baseNumMVColumn = new MVColumn(numColRef);

      XDynamicMVColumn[] dynamicMVColumns = new XDynamicMVColumn[2];
      dynamicMVColumns[0] = new DateMVColumn(baseDateMVColumn, dateColRef, DateRangeRef.DAY_OF_MONTH_PART);
      dynamicMVColumns[1] = new RangeMVColumn(baseNumMVColumn, numColRef, false);

      int[] indexes = new int[]{ 1, 2 };

      XDynamicTable originalTable = new XDynamicTable(tableLens, dynamicMVColumns, indexes);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XDynamicTable.class, deserializedTable.getClass());
   }
}
