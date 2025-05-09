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

import inetsoft.report.filter.*;
import inetsoft.report.internal.Util;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@SreeHome
public class AssetQueryTest {
   @Test
   public void testSerializeFormatTableLens() throws Exception {
      AssetQuery.FormatTableLens originalTable = new AssetQuery.FormatTableLens(XTableUtil.getDefaultTableLens());
      originalTable.setFormat(1, XUtil.getDefaultDateFormat(DateRangeRef.MONTH_OF_YEAR_PART, XSchema.DATE));
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.FormatTableLens.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeXArrayTable() throws Exception {
      AssetQuery.XArrayTable originalTable = new AssetQuery.XArrayTable();
      originalTable.setData(new Object[]{ "a", 1, 2.0 });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.XArrayTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeSummaryFilter2() throws Exception {
      AssetQuery.SummaryFilter2 originalTable =
         new AssetQuery.SummaryFilter2(XTableUtil.getDefaultTableLens(), new int[]{ 0 },
                                       new int[]{ 2 }, new Formula[]{ new SumFormula() }, false,
                                       null, new String[]{ "col2" });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.SummaryFilter2.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeSummaryFilter2Condition() throws Exception {
      ColumnRef ref = new ColumnRef(new AttributeRef("col1"));
      Condition cond = new Condition();
      cond.addValue("b");
      cond.setOperation(XCondition.EQUAL_TO);
      ConditionItem item = new ConditionItem(ref, cond, 0);
      ConditionList conditionList = new ConditionList();
      conditionList.append(item);
      ConditionGroup conditionGroup = new ConditionGroup(conditionList);
      AssetQuery.SummaryFilter2 originalTable =
         new AssetQuery.SummaryFilter2(XTableUtil.getDefaultTableLens(), new int[]{ 0 },
                                       new int[]{ 2 }, new Formula[]{ new SumFormula() }, false,
                                       conditionGroup, new String[]{ "col2" });
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(AssetQuery.SummaryFilter2.class, deserializedTable.getClass());
   }
}
