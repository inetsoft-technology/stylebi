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
import inetsoft.test.TestSerializeUtils;
import inetsoft.test.SreeHome;
import inetsoft.test.XTableUtil;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
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
}
