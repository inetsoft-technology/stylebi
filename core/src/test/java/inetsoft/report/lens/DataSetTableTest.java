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

package inetsoft.report.lens;

import inetsoft.graph.data.*;
import inetsoft.report.composition.graph.*;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XTableDataSet;
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

@SreeHome
public class DataSetTableTest {
   @Test
   public void testSerialize() throws Exception {
      DataSetTable originalTable = new DataSetTable(XTableUtil.getDefaultDataSet());
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());
   }

   @Test
   public void testSerializeAliasedColumnDataSet() throws Exception {
      Map<String, String> aliasMap = new HashMap<>();
      aliasMap.put("myAliasedColumn", "col1");
      AliasedColumnDataSet dataSet = new AliasedColumnDataSet(XTableUtil.getDefaultDataSet(),
                                                              aliasMap);
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(AliasedColumnDataSet.class, deserializedDataSetTable.getDataSet().getClass());
      Assertions.assertEquals(originalTable.getDataSet().getData("myAliasedColumn", 0),
                              deserializedDataSetTable.getDataSet().getData("myAliasedColumn", 0));
   }

   @Test
   public void testSerializeBoxDataSet() throws Exception {
      String[] dims = new String[]{ "col1" };
      String[] measures = new String[]{ "col2", "col3" };
      BoxDataSet dataSet = new BoxDataSet(XTableUtil.getDefaultDataSet(), dims, measures);
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(BoxDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeSubDataSet() throws Exception {
      SubDataSet subDataSet = new SubDataSet(XTableUtil.getDefaultDataSet(), 0, 2);
      DataSetTable originalTable = new DataSetTable(subDataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(SubDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeBrushDataSet() throws Exception {
      DataSet fullDataSet = XTableUtil.getDefaultDataSet();
      SubDataSet subDataSet = new SubDataSet(fullDataSet, 0, 2);
      BrushDataSet dataSet = new BrushDataSet(fullDataSet, subDataSet);
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(BrushDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeExpandableDataSet() throws Exception {
      ExpandableDataSet dataSet = new ExpandableDataSet(XTableUtil.getDefaultDataSet());
      dataSet.addMeasure("value", 123);
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(ExpandableDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeFullProjectedDataSet() throws Exception {
      FullProjectedDataSet dataSet = new FullProjectedDataSet(XTableUtil.getDefaultDataSet());
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(FullProjectedDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeIntervalDataSet() throws Exception {
      IntervalDataSet dataSet = new IntervalDataSet(XTableUtil.getDefaultDataSet());
      dataSet.addInterval("col2", "col3");
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(IntervalDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializePairsDataSet() throws Exception {
      PairsDataSet dataSet = new PairsDataSet(XTableUtil.getDefaultDataSet(), "col2", "col3");
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(PairsDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeSortedDataSet() throws Exception {
      SortedDataSet dataSet = new SortedDataSet(XTableUtil.getDefaultDataSet(), "col2", "col3");
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(SortedDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeSumDataSet() throws Exception {
      SumDataSet dataSet = new SumDataSet(XTableUtil.getDefaultDataSet(),
                                          new String[]{ "col2", "col3" }, "col1");
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(SumDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeUnionDataSet() throws Exception {
      UnionDataSet dataSet = new UnionDataSet(XTableUtil.getDefaultDataSet(),
                                              XTableUtil.getDefaultDataSet());
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(UnionDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeXTableDataSet() throws Exception {
      XTableDataSet dataSet = new XTableDataSet(XTableUtil.getDefaultTableLens());
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(XTableDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }

   @Test
   public void testSerializeVSDataSet() throws Exception {
      VSDimensionRef col1 = new VSDimensionRef(new AttributeRef("col1"));
      VSAggregateRef col2 = new VSAggregateRef();
      col2.setDataRef(new AttributeRef("col2"));
      VSAggregateRef col3 = new VSAggregateRef();
      col3.setDataRef(new AttributeRef("col3"));
      VSDataSet dataSet = new VSDataSet(XTableUtil.getDefaultTableLens(),
                                        new VSDataRef[]{ col1, col2, col3 });
      DataSetTable originalTable = new DataSetTable(dataSet);
      XTable deserializedTable = TestSerializeUtils.serializeAndDeserialize(originalTable);
      Assertions.assertEquals(DataSetTable.class, deserializedTable.getClass());

      DataSetTable deserializedDataSetTable = (DataSetTable) deserializedTable;
      Assertions.assertEquals(VSDataSet.class, deserializedDataSetTable.getDataSet().getClass());
   }


   // TODO add geo, mapped data set tests
}
