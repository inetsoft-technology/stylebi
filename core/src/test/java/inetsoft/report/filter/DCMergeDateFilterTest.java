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

package inetsoft.report.filter;

import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.lens.DataSetTable;
import inetsoft.test.SreeHome;
import inetsoft.test.TestSerializeUtils;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static inetsoft.test.XTableUtil.date;

@SreeHome
public class DCMergeDateFilterTest {
   @Test
   public void testSerialize() throws Exception {
      DataSet dataSet = new DefaultDataSet(new Object[][]{
         { "col1", "col2", "col3" },
         { "a", date("2021-01-03"), 3 },
         { "a", date("2021-01-05"), 5 },
         { "b", date("2021-01-10"), 10 },
         { "b", date("2021-01-24"), 24 },
         { "c", date("2021-01-24"), 24 },
         });
      Map<GroupTuple, DCMergeDatesCell> dcMergeGroup = new HashMap<>();
      GroupTuple tuple = new GroupTuple();
      tuple.addValue(date("2021-01-03"));
      DCMergeDatesCell mergeDatesCell = new DCMergeDatesCell(false, 0,
                                                             new HashMap<>(), false);
      dcMergeGroup.put(tuple, mergeDatesCell);
      DataSetTable base = new DataSetTable(dataSet);
      DCMergeDateFilter originalTable = new DCMergeDateFilter(base, 1, 1,
                                                              dcMergeGroup, false);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DCMergeDateFilter.class, deserializedTable.getClass());
   }
}
