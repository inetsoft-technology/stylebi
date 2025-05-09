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

import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.xmla.XMLAQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class CubeTableFilterTest {
   @Test
   public void testSerialize() throws Exception {
      CubeTableFilter originalTable = new CubeTableFilter(XTableUtil.getDefaultTableLens(),
                                                          new XMLAQuery());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(CubeTableFilter.class, deserializedTable.getClass());
   }
}
