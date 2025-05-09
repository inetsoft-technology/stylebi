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
import inetsoft.report.TableLens;
import inetsoft.test.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;

@SreeHome
public class TableFormatAttrTest {
   @Test
   public void testSerialize() throws Exception {
      TableDataPath col1Path = new TableDataPath("col1");
      TableFormat format = new TableFormat();
      format.background = Color.BLUE;
      format.foreground = Color.RED;

      TableFormatAttr attr = new TableFormatAttr();
      attr.setFormat(col1Path, format);

      TableLens originalTable = attr.createFilter(XTableUtil.getDefaultTableLens());
      TableLens deserializedTable = (TableLens) TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(TableFormatAttr.FormatTableLens2.class, deserializedTable.getClass());
      Assertions.assertEquals(Color.BLUE, deserializedTable.getBackground(2, 0));
      Assertions.assertEquals(Color.RED, deserializedTable.getForeground(2, 0));
   }
}
