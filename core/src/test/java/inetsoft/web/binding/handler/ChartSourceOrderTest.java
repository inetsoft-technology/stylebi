/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
package inetsoft.web.binding.handler;

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Reading a chart category in the order its values first appear in the worksheet table. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartSourceOrderTest {
   private static final String[] ITEMS = {
      "Revenue", "Returns", "Cost of goods", "Gross profit adj", "Salaries", "Marketing",
      "Other income", "Taxes"
   };

   @Test
   void readsTheValuesInTheOrderTheyFirstAppear() throws Exception {
      List<Object[]> rows = new ArrayList<>();
      double[] amounts = { 520, -35, -210, 40, -120, -45, 25, -38 };

      for(int i = 0; i < ITEMS.length; i++) {
         rows.add(new Object[] { ITEMS[i], amounts[i] });
      }

      rows.add(new Object[] { "Revenue", 10.0 });
      ChartSourceOrder order = new ChartSourceOrder(box(table(rows)), source());
      assertEquals(Arrays.asList((Object[]) ITEMS), order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void stopsAtTheRowLimit() throws Exception {
      List<Object[]> rows = new ArrayList<>();

      for(int i = 0; i < ChartSourceOrder.MAX_ROWS; i++) {
         rows.add(new Object[] { i % 2 == 0 ? "a" : "b", 1.0 });
      }

      rows.add(new Object[] { "late", 1.0 });
      ChartSourceOrder order = new ChartSourceOrder(box(table(rows)), source());
      assertEquals(Arrays.asList((Object) "a", "b"), order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void aTooLongColumnReadsNothing() throws Exception {
      List<Object[]> rows = new ArrayList<>();

      for(int i = 0; i < 101; i++) {
         rows.add(new Object[] { "v" + i, 1.0 });
      }

      ChartSourceOrder order = new ChartSourceOrder(box(table(rows)), source());
      assertNull(order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void aMissingColumnReadsNothing() throws Exception {
      ChartSourceOrder order = new ChartSourceOrder(box(table(rows())), source());
      assertNull(order.valuesInSourceOrder(dim("Nope")));
   }

   @Test
   void aCubeFieldReadsNothing() throws Exception {
      VSChartDimensionRef dim = dim("Item");
      dim.setRefType(DataRef.CUBE);
      ChartSourceOrder order = new ChartSourceOrder(box(table(rows())), source());
      assertNull(order.valuesInSourceOrder(dim));
   }

   @Test
   void aNamedGroupReadsNothing() throws Exception {
      VSChartDimensionRef dim = dim("Item");
      SNamedGroupInfo named = new SNamedGroupInfo();
      named.setGroupValue("g", new ArrayList<>(Arrays.asList("a", "b")));
      dim.setNamedGroupInfo(named);
      ChartSourceOrder order = new ChartSourceOrder(box(table(rows())), source());
      assertNull(order.valuesInSourceOrder(dim));
   }

   @Test
   void noSandboxReadsNothing() {
      assertNull(new ChartSourceOrder(null, source()).valuesInSourceOrder(dim("Item")));
      assertNull(new ChartSourceOrder(mock(ViewsheetSandbox.class), null)
                    .valuesInSourceOrder(dim("Item")));
      assertNull(new ChartSourceOrder(mock(ViewsheetSandbox.class),
                                      new SourceInfo(XSourceInfo.ASSET, null, null))
                    .valuesInSourceOrder(dim("Item")));
   }

   @Test
   void aFailingQueryReadsNothing() throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      AssetQuerySandbox aq = mock(AssetQuerySandbox.class);
      when(box.getAssetQuerySandbox()).thenReturn(aq);
      when(aq.getTableLens(anyString(), anyInt())).thenThrow(new RuntimeException("boom"));
      assertNull(new ChartSourceOrder(box, source()).valuesInSourceOrder(dim("Item")));
   }

   private static List<Object[]> rows() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "a", 1.0 });
      return rows;
   }

   private static DefaultTableLens table(List<Object[]> rows) {
      Object[][] data = new Object[rows.size() + 1][];
      data[0] = new Object[] { "Item", "Amount" };

      for(int i = 0; i < rows.size(); i++) {
         data[i + 1] = rows.get(i);
      }

      return new DefaultTableLens(data);
   }

   private static ViewsheetSandbox box(DefaultTableLens lens) throws Exception {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      AssetQuerySandbox aq = mock(AssetQuerySandbox.class);
      when(box.getAssetQuerySandbox()).thenReturn(aq);
      when(aq.getTableLens("Orders", AssetQuerySandbox.RUNTIME_MODE)).thenReturn(lens);
      return box;
   }

   private static SourceInfo source() {
      return new SourceInfo(XSourceInfo.ASSET, null, "Orders");
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef ref = new VSChartDimensionRef(new AttributeRef(null, name));
      ref.setDataType(XSchema.STRING);
      ref.setGroupColumnValue(name);
      return ref;
   }
}
