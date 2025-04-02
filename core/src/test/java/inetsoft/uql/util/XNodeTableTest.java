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

package inetsoft.uql.util;

import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class XNodeTableTest {
   @Test
   public void testSerializeXTableTableNode() throws Exception {
      XTableTableNode xNode = new XTableTableNode(XTableUtil.getDefaultTableLens());
      XNodeTable originalTable = new XNodeTable(xNode);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeCompositeTableNode() throws Exception {
      CompositeTableNode xNode = new CompositeTableNode(
         new XTableTableNode(XTableUtil.getDefaultTableLens()), 3);
      XNodeTable originalTable = new XNodeTable(xNode);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeXTableNode() throws Exception {
      XNodeTable originalTable = new XNodeTable(XTableUtil.getDefaultXTableNode());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(XNodeTable.class, deserializedTable.getClass());
   }
}
