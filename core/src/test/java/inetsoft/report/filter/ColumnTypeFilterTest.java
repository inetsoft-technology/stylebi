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
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class ColumnTypeFilterTest {
   @Test
   public void testSerialize() throws Exception {
      TableLens defTable = XTableUtil.getDefaultTableLens();
      String[] types = new String[defTable.getColCount()];
      types[0] = XSchema.CHARACTER;
      types[2] = XSchema.INTEGER;
      ColumnTypeFilter originalTable = new ColumnTypeFilter(defTable, types);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(ColumnTypeFilter.class, deserializedTable.getClass());
   }
}
