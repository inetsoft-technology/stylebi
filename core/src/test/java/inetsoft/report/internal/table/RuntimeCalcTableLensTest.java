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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
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

   @Test
   public void indexMapIsTheIdentityWithinItsSizeAndBeyondIt() {
      RuntimeCalcTableLens.IndexMap map = new RuntimeCalcTableLens.IndexMap(3);

      for(int i = 0; i < 3; i++) {
         Assertions.assertEquals(i, map.get(i));
      }

      Assertions.assertEquals(3, map.size());
      // an index past the size maps to itself
      Assertions.assertEquals(3, map.get(3));
      Assertions.assertEquals(5, map.get(5));
      Assertions.assertEquals(1, map.getReverse(1));
      // a value that no index maps to
      Assertions.assertEquals(-1, map.getReverse(9));
   }

   @Test
   public void rowAndColumnOfTheRuntimeTableAreTheOriginalOnesWithoutMapsAndWithIdentityMaps() {
      RuntimeCalcTableLens table = new RuntimeCalcTableLens(
         new CalcTableLens(XTableUtil.getDefaultTableLens()));

      for(int i = 0; i < 3; i++) {
         Assertions.assertEquals(i, table.getRow(i));
         Assertions.assertEquals(i, table.getCol(i));
      }

      table.setRowMap(new RuntimeCalcTableLens.IndexMap(3));
      table.setColMap(new RuntimeCalcTableLens.IndexMap(3));

      for(int i = 0; i < 3; i++) {
         Assertions.assertEquals(i, table.getRow(i));
         Assertions.assertEquals(i, table.getCol(i));
      }
   }
}
