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
package inetsoft.mv;

import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.*;
import inetsoft.test.*;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.SelectionVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link MVTableMetaData} gives the selection lists of a viewsheet bound to a materialized view
 * their values: the distinct values of a column, and the values associated with the current
 * selections (and a measure per value). Without an association MV it queries the selection table
 * of the viewsheet ({@code S_<table>}) with the selections as conditions.
 * <p>
 * Here the selection table is an embedded table of a real worksheet, run by a real
 * {@link AssetQuerySandbox}, so the conditions, the grouping and the measure are computed by the
 * query engine. The manager finds no association MV, so the full MV path is taken.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class, PluginsTestConfiguration.class,
                                  MVTableMetaDataTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MVTableMetaDataTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // findRuntimeMV gives no association MV
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   private static final Object[][] DATA = {
      { "Region", "Product", "Amount" },
      { "East", "Chair", 10 },
      { "East", "Desk", 20 },
      { "West", "Chair", 30 },
      { "West", "Lamp", 5 },
      { "North", "Desk/Pro", 7 },
      { "North", "Desk", 4 },
   };

   @AfterEach
   void tearDown() {
      if(sandbox != null) {
         sandbox.dispose();
      }
   }

   // the column table has the distinct values of the column, and the type of the column is
   // known afterwards
   @Test
   void columnTableHasTheDistinctValuesAndGivesTheColumnType() throws Exception {
      MVTableMetaData meta = metaData();

      XTable table = meta.getColumnTable("List1", new String[] { "Region" });

      assertEquals(Set.of("East", "West", "North"), new HashSet<>(column(table, 0)));
      assertEquals(XSchema.STRING, meta.getType("Region"));

      meta.getColumnTable("List2", new String[] { "Amount" });
      assertEquals(XSchema.INTEGER, meta.getType("Amount"));
   }

   // the values of a list associated with the selection of another list
   @Test
   void associatedValuesFollowTheSelection() throws Exception {
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of("Region", List.of("East")), refs("Product"), null, aggregation());

      assertEquals(Set.of("Chair", "Desk"), singles(values));
   }

   // selections of two lists are both applied
   @Test
   void selectionsOfTwoListsAreBothApplied() throws Exception {
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of("Region", List.of("West", "East"), "Product", List.of("Chair")),
         refs("Amount"), null, aggregation());

      assertEquals(Set.of(10, 30), singles(values));
   }

   // the selection of a tree comes as paths: a value with a "/" of its own (Desk/Pro) is kept
   // whole, the parent of a partial selection ends with /CHILD_SELECTION_EXISTS (Bug #52677)
   @Test
   void treeSelectionValueWithASlashIsKeptWhole() throws Exception {
      String key = SelectionVSAssembly.SELECTION_PATH + "Region^/^Product";
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of(key, List.of("North/CHILD_SELECTION_EXISTS", "North/Desk/Pro")),
         refs("Amount"), null, aggregation());

      assertEquals(Set.of(7), singles(values));
   }

   // a measure is aggregated per associated value: the bounds over all values and a sum per value
   @Test
   void measureIsAggregatedPerAssociatedValue() throws Exception {
      SelectionMeasureAggregation aggregation = aggregation();
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of("Product", List.of("Chair", "Desk")), refs("Region"), "Sum(Amount)",
         aggregation);

      assertEquals(Set.of("East", "West", "North"), singles(values));
      // East 10 + 20, West 30, North 4
      assertEquals(30.0, aggregation.getMax());
      assertEquals(4.0, aggregation.getMin());
      assertEquals(30.0, sum(aggregation, "East"));
      assertEquals(30.0, sum(aggregation, "West"));
      assertEquals(4.0, sum(aggregation, "North"));
   }

   // ---------------------------------------------------------------- fixture

   private MVTableMetaData metaData() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, Assembly.SELECTION + "T");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.INTEGER }, DATA));
      ws.addAssembly(table);

      // the data keys are content based: without this a run could read another's rows
      AssetDataCache.getCache().clearCache();
      sandbox = new AssetQuerySandbox(ws);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      when(box.getWorksheet()).thenReturn(ws);
      when(box.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(box.isRuntime()).thenReturn(true);
      when(box.getAssetQuerySandbox()).thenReturn(sandbox);
      when(box.getID()).thenReturn("MVTableMetaDataTest");
      // the bound table of a selection is a copy of the selection table under the given name
      when(box.getBoundTable(any(), anyString(), anyBoolean()))
         .thenAnswer(inv -> ((TableAssembly) inv.getArgument(0)).copyAssembly(inv.getArgument(1)));
      return new MVTableMetaData("T", null, box);
   }

   private static SelectionMeasureAggregation aggregation() {
      return new SelectionMeasureAggregation(false);
   }

   // the columns of the selection lists, as the lists give them
   private static DataRef[] refs(String... names) {
      return Arrays.stream(names).map(n -> new ColumnRef(new AttributeRef(null, n)))
         .toArray(DataRef[]::new);
   }

   // the associated values of a single list
   private static Set<Object> singles(SelectionSet values) {
      Set<Object> result = new HashSet<>();

      for(Object obj : DATA_VALUES) {
         if(values.contains(new SelectionSet.Tuple(new Object[] { obj }, 1))) {
            result.add(obj);
         }
      }

      return result;
   }

   private static double sum(SelectionMeasureAggregation aggregation, Object value) {
      return ((Number) aggregation.getFormulas()
         .get(new SelectionSet.Tuple(new Object[] { value }, 1)).getResult()).doubleValue();
   }

   private static List<Object> column(XTable table, int col) {
      table.moreRows(TableLens.EOT);
      List<Object> values = new ArrayList<>();

      for(int r = table.getHeaderRowCount(); table.moreRows(r); r++) {
         values.add(table.getObject(r, col));
      }

      return values;
   }

   // every value of the data, to look up which ones are associated
   private static final Set<Object> DATA_VALUES = new HashSet<>();

   static {
      for(int r = 1; r < DATA.length; r++) {
         DATA_VALUES.addAll(Arrays.asList(DATA[r]));
      }
   }

   private AssetQuerySandbox sandbox;
}
