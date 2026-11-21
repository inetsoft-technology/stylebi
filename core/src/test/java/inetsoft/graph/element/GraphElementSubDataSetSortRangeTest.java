/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.graph.element;

import inetsoft.graph.data.*;
import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82041: an element whose start/end rows are set (rows of the root data set) sorts only the
 * rows of a facet cell that fall in that range. The range is mapped to the cell with
 * {@link SubDataSet#getRowFromBase(int)}, and a negative result is an insertion point.
 * {@code StackTextFrame.init} uses the range of {@code createSortedDataSet} as is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class GraphElementSubDataSetSortRangeTest {
   // header {k, v}; row i = {i, 10 * i}
   private static DefaultDataSet base(int rows) {
      Object[][] data = new Object[rows + 1][];
      data[0] = new Object[] { "k", "v" };

      for(int i = 0; i < rows; i++) {
         data[i + 1] = new Object[] { i, 10 * i };
      }

      return new DefaultDataSet(data);
   }

   // element rows [1, 5) of the root data
   private static IntervalElement element() {
      IntervalElement elem = new IntervalElement("k", "v");
      elem.setStartRow(1);
      elem.setEndRow(5);
      return elem;
   }

   // cell holding root rows 2, 3
   private static SubDataSet flatCell() {
      return new SubDataSet(base(5), new int[] { 2, 3 });
   }

   // cell holding root rows 2, 3 through a parent cell (multi-level facet)
   private static SubDataSet nestedCell() {
      return new SubDataSet(new SubDataSet(base(5), new int[] { 1, 2, 3, 4 }), new int[] { 1, 2 });
   }

   private static void assertRange(SortedDataSet sorted, int start, int end) {
      assertEquals(start, sorted.getStartRow(), "start row");
      assertEquals(end, sorted.getEndRow(), "end row");
   }

   @Test
   void createSortedDataSetOnFlatCellCoversTheCell() {
      assertRange(element().createSortedDataSet(flatCell(), "k"), 0, 2);
   }

   @Test
   void createSortedDataSetOnNestedCellCoversTheCell() {
      assertRange(element().createSortedDataSet(nestedCell(), "k"), 0, 2);
   }

   @Test
   void sortedDataSetInRangeOnFlatCellCoversTheCell() {
      SubDataSet cell = flatCell();
      IntervalElement elem = element();
      SortedDataSet sorted = (SortedDataSet)
         elem.getSortedDataSetInRange(cell, new SortedDataSet(cell, "k"));

      assertRange(sorted, 0, 2);
   }

   @Test
   void sortedDataSetInRangeOnNestedCellCoversTheCell() {
      SubDataSet cell = nestedCell();
      IntervalElement elem = element();
      SortedDataSet sorted = (SortedDataSet)
         elem.getSortedDataSetInRange(cell, new SortedDataSet(cell, "k"));

      assertRange(sorted, 0, 2);
   }

   @Test
   void createSortedDataSetOnNestedCellStartsAtTheFirstRowInRange() {
      // element rows [3, 5): only root row 3 (cell row 1) is in range
      IntervalElement elem = element();
      elem.setStartRow(3);

      assertRange(elem.createSortedDataSet(nestedCell(), "k"), 1, 2);
   }

   // element rows [2, 4): exactly the root rows 2, 3 of the nested cell. Mapping the root rows
   // without going through the parent cell gives [1, 1), an empty range.
   private static IntervalElement elementOverCellRows() {
      IntervalElement elem = element();
      elem.setStartRow(2);
      elem.setEndRow(4);
      return elem;
   }

   @Test
   void createSortedDataSetOnNestedCellMapsRootRowsThroughTheParent() {
      assertRange(elementOverCellRows().createSortedDataSet(nestedCell(), "k"), 0, 2);
   }

   @Test
   void sortedDataSetInRangeOnNestedCellMapsRootRowsThroughTheParent() {
      SubDataSet cell = nestedCell();
      IntervalElement elem = elementOverCellRows();
      SortedDataSet sorted = (SortedDataSet)
         elem.getSortedDataSetInRange(cell, new SortedDataSet(cell, "k"));

      assertRange(sorted, 0, 2);
   }
}
