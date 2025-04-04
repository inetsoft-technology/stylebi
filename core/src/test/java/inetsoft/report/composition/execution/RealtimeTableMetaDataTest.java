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

package inetsoft.report.composition.execution;

import inetsoft.report.internal.Util;
import inetsoft.test.TestSerializeUtils;
import inetsoft.test.XTableUtil;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

public class RealtimeTableMetaDataTest {
   @Test
   public void testSerializeColumnsTable() throws Exception {
      RealtimeTableMetaData metaData = new RealtimeTableMetaData("test");
      metaData.process(XTableUtil.getDefaultTableLens(), new String[]{ "col1", "col2", "col3" },
                       new ArrayList<>());
      XTable originalTable = metaData.getColumnTable("test", new String[]{ "col1", "col2", "col3" });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertNotNull(Util.getNestedTable(deserializedTable,
                                                   RealtimeTableMetaData.ColumnsTable.class));
   }
}
