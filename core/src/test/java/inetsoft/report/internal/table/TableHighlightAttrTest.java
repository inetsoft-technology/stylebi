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
import inetsoft.report.filter.ColumnHighlight;
import inetsoft.report.filter.HighlightGroup;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.*;

@SreeHome
public class TableHighlightAttrTest {
   @Test
   public void testSerializeHighlightTableLens() throws Exception {
      ColumnRef ref = new ColumnRef(new AttributeRef("col1"));
      Condition cond = new Condition();
      cond.addValue("b");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionItem item = new ConditionItem(ref, cond, 0);
      ConditionList conditionList = new ConditionList();
      conditionList.append(item);

      HighlightGroup highlightGroup = new HighlightGroup();
      ColumnHighlight columnHighlight = new ColumnHighlight();
      columnHighlight.setConditionGroup(conditionList);
      columnHighlight.setBackground(Color.BLUE);
      highlightGroup.addHighlight("h1", columnHighlight);

      TableDataPath colPath = new TableDataPath("col1");
      colPath.setType(TableDataPath.DETAIL);
      colPath.setColIndex(0);
      TableHighlightAttr attr = new TableHighlightAttr();
      attr.setHighlight(colPath, highlightGroup);

      TableLens originalTable = attr.createFilter(XTableUtil.getDefaultTableLens());

      Assertions.assertEquals(Color.BLUE, originalTable.getBackground(2, 0));

      TableLens deserializedTable = (TableLens) TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals("inetsoft.report.internal.table.TableHighlightAttr$HighlightTableLens",
                              deserializedTable.getClass().getName());
      Assertions.assertEquals(Color.BLUE, deserializedTable.getBackground(2, 0));
   }
}
