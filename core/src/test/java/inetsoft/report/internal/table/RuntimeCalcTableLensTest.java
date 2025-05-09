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

package inetsoft.report.internal.table;

import inetsoft.report.lens.CalcTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class RuntimeCalcTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      RuntimeCalcTableLens originalTable = new RuntimeCalcTableLens(
         new CalcTableLens(XTableUtil.getDefaultTableLens()));
      RuntimeCalcTableLens.IndexMap colMap = new RuntimeCalcTableLens.IndexMap(originalTable.getColCount());
      originalTable.setColMap(colMap);
      CalcCellContext calcCellContext = new CalcCellContext();
      originalTable.addCalcColumn(0, calcCellContext);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(RuntimeCalcTableLens.class, deserializedTable.getClass());
   }
}
