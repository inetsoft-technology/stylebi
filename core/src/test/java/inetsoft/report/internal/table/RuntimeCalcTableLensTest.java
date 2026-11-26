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

   @Test
   public void indexMapCloneKeepsTheMappingAndIsIndependentOfTheOriginal() {
      RuntimeCalcTableLens.IndexMap map = new RuntimeCalcTableLens.IndexMap(3);
      map.insert(0, 7, new CalcCellContext());
      RuntimeCalcTableLens.IndexMap copy = (RuntimeCalcTableLens.IndexMap) map.clone();

      Assertions.assertEquals(4, copy.size());
      Assertions.assertArrayEquals(new int[] { 7, 0, 1, 2 }, copy.getMapArray());
      Assertions.assertEquals(7, copy.get(0));
      Assertions.assertEquals(2, copy.get(3));

      copy.remove(0);
      Assertions.assertEquals(4, map.size());
      Assertions.assertArrayEquals(new int[] { 7, 0, 1, 2 }, map.getMapArray());
      Assertions.assertArrayEquals(new int[] { 0, 1, 2 }, copy.getMapArray());
   }

   @Test
   public void clonedRuntimeTableMapsItsRowsLikeTheOriginal() {
      RuntimeCalcTableLens table = new RuntimeCalcTableLens(
         new CalcTableLens(XTableUtil.getDefaultTableLens()));
      RuntimeCalcTableLens.IndexMap rowMap = new RuntimeCalcTableLens.IndexMap(3);
      rowMap.insert(0, 2, new CalcCellContext());
      table.setRowMap(rowMap);

      RuntimeCalcTableLens copy = table.clone();

      for(int i = 0; i < 4; i++) {
         Assertions.assertEquals(table.getRow(i), copy.getRow(i));
      }

      Assertions.assertEquals(2, copy.getRow(0));
   }

   private static RuntimeCalcTableLens.IndexMap mapWithAnInsertedRow() {
      RuntimeCalcTableLens.IndexMap map = new RuntimeCalcTableLens.IndexMap(3);
      map.insert(1, 0, new CalcCellContext());
      return map;
   }

   @Test
   public void indexMapInsertAndRemoveShiftTheLaterIndexes() {
      RuntimeCalcTableLens.IndexMap map = mapWithAnInsertedRow();

      Assertions.assertEquals(4, map.size());
      Assertions.assertArrayEquals(new int[] { 0, 0, 1, 2 }, map.getMapArray());
      // the first runtime row that shows layout row 0, and all of them
      Assertions.assertEquals(0, map.getReverse(0));
      Assertions.assertArrayEquals(new int[] { 0, 1 }, map.getReverseIndexs(0));

      map.remove(0);

      Assertions.assertEquals(3, map.size());
      Assertions.assertArrayEquals(new int[] { 0, 1, 2 }, map.getMapArray());
   }

   @Test
   public void indexMapReverseSearchWithAStartAndAnIncrementWalksBothWays() {
      RuntimeCalcTableLens.IndexMap map = mapWithAnInsertedRow();

      // the start index itself is searched too
      Assertions.assertEquals(0, map.getReverse(0, 0, 1), "forward from 0");
      Assertions.assertEquals(1, map.getReverse(0, 1, 1), "forward from 1");
      Assertions.assertEquals(1, map.getReverse(0, 3, -1), "backward from 3 finds the last match");
      Assertions.assertEquals(-1, map.getReverse(2, 0, -1), "nothing before the start");
      Assertions.assertEquals(-1, map.getReverse(0, 4, 1), "start past the end");
      Assertions.assertEquals(-1, map.getReverse(0, -1, 1), "negative start");
   }
}
