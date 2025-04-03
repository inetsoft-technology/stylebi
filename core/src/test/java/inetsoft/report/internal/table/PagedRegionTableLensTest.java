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

import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;

@SreeHome
public class PagedRegionTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      Object[][] data = XTableUtil.getDefaultData();

      int nrow = data.length;
      int ncol = data[0].length;
      int hrow = 1;
      int hcol = 0;
      PagedRegionTableLens originalTable = new PagedRegionTableLens(nrow, ncol, hrow, hcol,
                                                                    new Rectangle(hcol, hrow,
                                                                                  ncol - hcol,
                                                                                  nrow - hrow));

      for(int i = 0; i < nrow; i++) {
         originalTable.setRowHeight(i, 20);
      }

      for(int i = 0; i < ncol; i++) {
         originalTable.setColWidth(i, 20);
      }

      for(Object[] row : data) {
         originalTable.addRow(row);
      }

      originalTable.complete();

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(PagedRegionTableLens.class, deserializedTable.getClass());
   }
}
