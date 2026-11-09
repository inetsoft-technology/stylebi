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

import inetsoft.report.TableLens;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.report.lens.DistinctTableLens;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class ColumnMapFilterTest {
   @Test
   public void testSerialize() throws Exception {
      TableLens defTable = XTableUtil.getDefaultTableLens();

      int[] map = new int[defTable.getColCount()];

      for(int i = 0; i < map.length; i++) {
         map[i] = map.length - 1 - i;
      }

      ColumnMapFilter originalTable = new ColumnMapFilter(XTableUtil.getDefaultTableLens(),
                                                          map);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);

      Assertions.assertEquals(ColumnMapFilter.class, deserializedTable.getClass());
   }

   /**
    * A hidden sort column in the middle ends the sorted prefix: the rows are not ordered on
    * the following sort columns, which must not be reported with a stale base index
    * (bug #80695).
    */
   @Test
   public void sortColsStopAtHiddenMiddleColumn() {
      ColumnMapFilter filter = new ColumnMapFilter(sortedOn(0, 1, 2), new int[] { 0, 2 });
      Assertions.assertArrayEquals(new int[] { 0 }, filter.getSortCols());
   }

   @Test
   public void sortColsEmptyWhenFirstSortColumnHidden() {
      ColumnMapFilter filter = new ColumnMapFilter(sortedOn(0, 1, 2), new int[] { 1, 2 });
      Assertions.assertArrayEquals(new int[0], filter.getSortCols());
   }

   @Test
   public void sortColsUnchangedWhenNoSortColumnHidden() {
      ColumnMapFilter filter = new ColumnMapFilter(sortedOn(0, 1, 2), new int[] { 0, 1, 2 });
      Assertions.assertArrayEquals(new int[] { 0, 1, 2 }, filter.getSortCols());
   }

   @Test
   public void sortColsMappedThroughReorderedMap() {
      ColumnMapFilter filter = new ColumnMapFilter(sortedOn(0, 2), new int[] { 2, 0 });
      Assertions.assertArrayEquals(new int[] { 1, 0 }, filter.getSortCols());

      // the hidden column 1 ends the prefix after the mapped column 2
      filter = new ColumnMapFilter(sortedOn(2, 1, 0), new int[] { 2, 0 });
      Assertions.assertArrayEquals(new int[] { 0 }, filter.getSortCols());
   }

   @Test
   public void sortColsUseFirstMatchOfDuplicatedMapping() {
      ColumnMapFilter filter = new ColumnMapFilter(sortedOn(0, 2), new int[] { 0, 0, 2 });
      Assertions.assertArrayEquals(new int[] { 0, 2 }, filter.getSortCols());
   }

   /**
    * Distinct on the visible columns of a table sorted on (a,b,c) with b hidden returns each
    * (a,c) value once (bug #80695).
    */
   @Test
   public void distinctOverHiddenSortColumnRemovesDuplicates() {
      ColumnMapFilter filter = new ColumnMapFilter(sortedOn(0, 1, 2), new int[] { 0, 2 });
      DistinctTableLens distinct = new DistinctTableLens(filter, new int[] { 0, 1 }, false);

      Assertions.assertEquals(List.of("p,x", "p,y", "q,x", "q,y"), rows(distinct));
   }

   private static SortFilter sortedOn(int... cols) {
      return new SortFilter(new DefaultTableLens(DATA), cols);
   }

   private static List<String> rows(TableLens lens) {
      List<String> rows = new ArrayList<>();
      lens.moreRows(XTable.EOT);

      for(int r = lens.getHeaderRowCount(); lens.moreRows(r); r++) {
         rows.add(lens.getObject(r, 0) + "," + lens.getObject(r, 1));
      }

      return rows;
   }

   private static final Object[][] DATA = {
      { "a", "b", "c" },
      { "q", 2, "x" },
      { "p", 3, "x" },
      { "q", 1, "y" },
      { "p", 1, "x" },
      { "q", 3, "y" },
      { "p", 2, "y" }
   };
}
