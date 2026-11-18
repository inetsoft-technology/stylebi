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

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Reading a chart category in the order its values first appear in the worksheet table. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  ChartSourceOrderTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartSourceOrderTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   private static final String[] ITEMS = {
      "Revenue", "Returns", "Cost of goods", "Gross profit adj", "Salaries", "Marketing",
      "Other income", "Taxes"
   };

   @AfterEach
   void tearDown() {
      for(AssetQuerySandbox sandbox : sandboxes) {
         sandbox.dispose();
      }

      sandboxes.clear();
   }

   @Test
   void readsTheValuesInTheOrderTheyFirstAppear() throws Exception {
      List<Object[]> rows = itemRows();
      rows.add(new Object[] { "Revenue", 10.0 });
      ChartSourceOrder order = new ChartSourceOrder(box(worksheet(rows)), source());
      assertEquals(Arrays.asList((Object[]) ITEMS), order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void stopsAtTheRowLimit() throws Exception {
      List<Object[]> rows = new ArrayList<>();

      for(int i = 0; i < ChartSourceOrder.MAX_ROWS; i++) {
         rows.add(new Object[] { i % 2 == 0 ? "a" : "b", 1.0 });
      }

      rows.add(new Object[] { "late", 1.0 });
      ChartSourceOrder order = new ChartSourceOrder(box(worksheet(rows)), source());
      assertEquals(Arrays.asList((Object) "a", "b"), order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void aTooLongColumnReadsNothing() throws Exception {
      List<Object[]> rows = new ArrayList<>();

      for(int i = 0; i < 101; i++) {
         rows.add(new Object[] { "v" + i, 1.0 });
      }

      ChartSourceOrder order = new ChartSourceOrder(box(worksheet(rows)), source());
      assertNull(order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void aMissingColumnReadsNothing() throws Exception {
      ChartSourceOrder order = new ChartSourceOrder(box(worksheet(rows())), source());
      assertNull(order.valuesInSourceOrder(dim("Nope")));
   }

   @Test
   void aCubeFieldReadsNothing() throws Exception {
      VSChartDimensionRef dim = dim("Item");
      dim.setRefType(DataRef.CUBE);
      ChartSourceOrder order = new ChartSourceOrder(box(worksheet(rows())), source());
      assertNull(order.valuesInSourceOrder(dim));
   }

   @Test
   void aNamedGroupReadsNothing() throws Exception {
      VSChartDimensionRef dim = dim("Item");
      SNamedGroupInfo named = new SNamedGroupInfo();
      named.setGroupValue("g", new ArrayList<>(Arrays.asList("a", "b")));
      dim.setNamedGroupInfo(named);
      ChartSourceOrder order = new ChartSourceOrder(box(worksheet(rows())), source());
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
      ViewsheetSandbox box = box(worksheet(rows()));

      try(MockedStatic<AssetQuery> query = mockStatic(AssetQuery.class, CALLS_REAL_METHODS)) {
         query.when(() -> AssetQuery.createAssetQuery(any(), anyInt(), any(), anyBoolean(),
                                                      anyLong(), anyBoolean(), anyBoolean()))
            .thenThrow(new RuntimeException("boom"));
         assertNull(new ChartSourceOrder(box, source()).valuesInSourceOrder(dim("Item")));
      }
   }

   @Test
   void theQueryCarriesTheRowLimit() throws Exception {
      ViewsheetSandbox box = box(worksheet(itemRows()));
      List<TableAssembly> queried = new ArrayList<>();

      try(MockedStatic<AssetQuery> query = mockStatic(AssetQuery.class, CALLS_REAL_METHODS)) {
         query.when(() -> AssetQuery.createAssetQuery(any(), anyInt(), any(), anyBoolean(),
                                                      anyLong(), anyBoolean(), anyBoolean()))
            .thenAnswer(i -> {
               // the top-level query of the read
               if(i.getArgument(5, Boolean.class)) {
                  queried.add(i.getArgument(0, TableAssembly.class));
               }

               return i.callRealMethod();
            });

         assertEquals(Arrays.asList((Object[]) ITEMS),
                      new ChartSourceOrder(box, source()).valuesInSourceOrder(dim("Item")));
      }

      assertEquals(1, queried.size(), "one query runs");
      assertEquals(ChartSourceOrder.MAX_ROWS, queried.get(0).getMaxRows(),
                   "the row limit is in the query");
   }

   @Test
   void aSmallerWorksheetRowLimitStays() throws Exception {
      Worksheet ws = worksheet(itemRows());
      table(ws).setMaxRows(3);
      ChartSourceOrder order = new ChartSourceOrder(box(ws), source());
      assertEquals(Arrays.asList((Object) "Revenue", "Returns", "Cost of goods"),
                   order.valuesInSourceOrder(dim("Item")));
   }

   @Test
   void aSelectionOnTheTableDoesNotFilterTheRead() throws Exception {
      Worksheet ws = worksheet(itemRows());
      TableAssembly table = table(ws);
      // as a dashboard selection of Revenue sets it
      table.setPreRuntimeConditionList(itemCondition(table, "Revenue", false));
      table.setPostRuntimeConditionList(itemCondition(table, "Revenue", false));
      ChartSourceOrder order = new ChartSourceOrder(box(ws), source());

      assertEquals(Arrays.asList((Object[]) ITEMS), order.valuesInSourceOrder(dim("Item")));
      assertFalse(table.getPreRuntimeConditionList().isEmpty(), "the selection stays");
      assertFalse(table.getPostRuntimeConditionList().isEmpty(), "the selection stays");
   }

   @Test
   void aWorksheetConditionStillApplies() throws Exception {
      Worksheet ws = worksheet(itemRows());
      TableAssembly table = table(ws);
      table.setPreConditionList(itemCondition(table, "Taxes", true));
      List<Object> expected = new ArrayList<>(Arrays.asList((Object[]) ITEMS));
      expected.remove("Taxes");

      assertEquals(expected, new ChartSourceOrder(box(ws), source())
         .valuesInSourceOrder(dim("Item")));
   }

   @Test
   void theReadLeavesTheWorksheetAndTheSandboxAsTheyWere() throws Exception {
      Worksheet ws = worksheet(itemRows());
      AssetQuerySandbox sandbox = sandbox(ws);
      ViewsheetSandbox box = box(sandbox);
      List<String> names = names(ws);

      assertEquals(Arrays.asList((Object[]) ITEMS),
                   new ChartSourceOrder(box, source()).valuesInSourceOrder(dim("Item")));
      assertEquals(names, names(ws), "no assembly is left in the worksheet");
      assertTrue(cached(sandbox).isEmpty(), "the read fills no cached result");

      TableLens result = sandbox.getTableLens("Orders", AssetQuerySandbox.RUNTIME_MODE);
      new ChartSourceOrder(box, source()).valuesInSourceOrder(dim("Item"));
      assertSame(result, sandbox.getTableLens("Orders", AssetQuerySandbox.RUNTIME_MODE),
                 "the table's cached result is not replaced");
      assertEquals(1, cached(sandbox).size(), "the read adds no cached result");
   }

   @Test
   void aComposedTableIsReadWithoutChangingTheWorksheet() throws Exception {
      Worksheet ws = worksheet("Base", itemRows());
      TableAssembly base = (TableAssembly) ws.getAssembly("Base");
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "Orders", base);
      ws.addAssembly(mirror);
      mirror.update();
      List<String> names = names(ws);

      assertEquals(Arrays.asList((Object[]) ITEMS), new ChartSourceOrder(box(ws), source())
         .valuesInSourceOrder(dim("Item")));
      assertEquals(names, names(ws), "no assembly is left in the worksheet");
   }

   private static List<Object[]> itemRows() {
      List<Object[]> rows = new ArrayList<>();
      double[] amounts = { 520, -35, -210, 40, -120, -45, 25, -38 };

      for(int i = 0; i < ITEMS.length; i++) {
         rows.add(new Object[] { ITEMS[i], amounts[i] });
      }

      return rows;
   }

   private static List<Object[]> rows() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "a", 1.0 });
      return rows;
   }

   // the worksheet table Orders: Item, Amount
   private static Worksheet worksheet(List<Object[]> rows) {
      return worksheet("Orders", rows);
   }

   private static Worksheet worksheet(String name, List<Object[]> rows) {
      Object[][] data = new Object[rows.size() + 1][];
      data[0] = new Object[] { "Item", "Amount" };

      for(int i = 0; i < rows.size(); i++) {
         data[i + 1] = rows.get(i);
      }

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, name);
      table.setEmbeddedData(
         new XEmbeddedTable(new String[] { XSchema.STRING, XSchema.DOUBLE }, data));
      ws.addAssembly(table);
      return ws;
   }

   private static TableAssembly table(Worksheet ws) {
      return (TableAssembly) ws.getAssembly("Orders");
   }

   // Item = value, or Item != value when negated
   private static ConditionList itemCondition(TableAssembly table, String value,
                                              boolean negated)
   {
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.EQUAL_TO);
      cond.setType(XSchema.STRING);
      cond.addValue(value);
      cond.setNegated(negated);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute("Item"),
                                    cond, 0));
      return list;
   }

   private AssetQuerySandbox sandbox(Worksheet ws) {
      // the data keys are content based, so a run could read another's rows
      AssetDataCache.getCache().clearCache();
      AssetQuerySandbox sandbox = new AssetQuerySandbox(ws);
      sandboxes.add(sandbox);
      return sandbox;
   }

   private ViewsheetSandbox box(Worksheet ws) {
      return box(sandbox(ws));
   }

   private static ViewsheetSandbox box(AssetQuerySandbox sandbox) {
      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getAssetQuerySandbox()).thenReturn(sandbox);
      return box;
   }

   private static List<String> names(Worksheet ws) {
      List<String> names = new ArrayList<>();

      for(Assembly assembly : ws.getAssemblies()) {
         names.add(assembly.getName());
      }

      return names;
   }

   private static Map<?, ?> cached(AssetQuerySandbox sandbox) throws Exception {
      Field field = AssetQuerySandbox.class.getDeclaredField("tmap");
      field.setAccessible(true);
      return (Map<?, ?>) field.get(sandbox);
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

   private final List<AssetQuerySandbox> sandboxes = new ArrayList<>();
}
