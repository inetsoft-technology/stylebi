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

import inetsoft.report.TableDataPath;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.util.Map;

@SreeHome
public class FormatTableLens2Test {
   @Test
   public void testSerialize() throws Exception {
      FormatTableLens2 originalTable = new FormatTableLens2(XTableUtil.getDefaultTableLens());
      Map<TableDataPath, TableFormat> formatMap = originalTable.getFormatMap();
      TableDataPath col1Path = new TableDataPath("col1");
      TableFormat format = new TableFormat();
      format.background = Color.BLUE;
      format.foreground = Color.RED;
      formatMap.put(col1Path, format);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(FormatTableLens2.class, deserializedTable.getClass());

      FormatTableLens2 deserializedTable2 = (FormatTableLens2) deserializedTable;
      Map<TableDataPath, TableFormat> deserializedFormatMap = deserializedTable2.getFormatMap();
      Assertions.assertEquals(formatMap, deserializedFormatMap);
   }
}
