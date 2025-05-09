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

import inetsoft.report.Hyperlink;
import inetsoft.report.TableDataPath;
import inetsoft.report.internal.table.TableHyperlinkAttr;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class VSHyperlinkTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      TableDataPath col1Path = new TableDataPath("col1");
      TableHyperlinkAttr attr = new TableHyperlinkAttr();
      Hyperlink hyperlink = new Hyperlink();
      hyperlink.setToolTip("tooltip");
      hyperlink.setLink("https://inetsoft.com");
      attr.setHyperlink(col1Path, hyperlink);
      VSHyperlinkTableLens originalTable =
         new VSHyperlinkTableLens(XTableUtil.getDefaultTableLens(), attr);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(VSHyperlinkTableLens.class, deserializedTable.getClass());
   }
}
