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

package inetsoft.uql.table;

import inetsoft.test.SreeHome;
import inetsoft.test.TestSerializeUtils;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class XSwappableTableTest {
   @Test
   void testSerialize() throws Exception {
      XSwappableTable originalTable = new XSwappableTable(3, true);
      originalTable.addRow(new Object[]{ "col1", "col2", "col3" });
      originalTable.addRow(new Object[]{ 1, "str1", 1.2 });
      originalTable.addRow(new Object[]{ 2, "str2", 2.2 });
      originalTable.addRow(new Object[]{ 3, "str3", 3.2 });
      originalTable.complete();

      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);

      Assertions.assertEquals(XSwappableTable.class, deserializedTable.getClass());
   }
}
