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

import inetsoft.test.*;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class VSCubeTableLensTest {
   @Test
   public void testSerialize() throws Exception {
      ColumnRef col1Ref = new ColumnRef(new AttributeRef("col1"));
      col1Ref.setDataType(XSchema.STRING);
      ColumnRef col2Ref = new ColumnRef(new AttributeRef("col2"));
      col2Ref.setDataType(XSchema.INTEGER);
      ColumnRef col3Ref = new ColumnRef(new AttributeRef("col3"));
      col3Ref.setDataType(XSchema.DOUBLE);

      ColumnSelection columnSelection = new ColumnSelection();
      columnSelection.addAttribute(col1Ref);
      columnSelection.addAttribute(col2Ref);
      columnSelection.addAttribute(col3Ref);

      VSCubeTableLens originalTable = new VSCubeTableLens(XTableUtil.getDefaultTableLens(),
                                                          columnSelection);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(VSCubeTableLens.class, deserializedTable.getClass());
   }
}
