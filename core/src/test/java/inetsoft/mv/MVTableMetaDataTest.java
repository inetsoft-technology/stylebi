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
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.XTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.ColumnNotFoundException;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

      // the manager is a shared mock: drop the association MV a test gave it
      reset(MVManager.getManager());
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

      // the set keeps numbers normalized to double
      assertEquals(Set.of(10.0, 30.0), singles(values));
   }

   // the selection of a tree comes as paths: a value with a "/" of its own (Desk/Pro) is kept
   // whole, the parent of a partial selection ends with /CHILD_SELECTION_EXISTS (Bug #52677)
   @Test
   void treeSelectionValueWithASlashIsKeptWhole() throws Exception {
      String key = SelectionVSAssembly.SELECTION_PATH + "Region^/^Product";
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of(key, List.of("North/CHILD_SELECTION_EXISTS", "North/Desk/Pro")),
         refs("Amount"), null, aggregation());

      assertEquals(Set.of(7.0), singles(values));
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

   // Bug #82492: a selection on a column the table doesn't have (e.g. removed from the
   // worksheet after the value was selected) is ignored, as RealtimeTableMetaData does
   @Test
   void selectionOnAColumnMissingFromTheTableIsIgnored() throws Exception {
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of("Region", List.of("East"), "CUSTOMER_ID", List.of("1")),
         refs("Product"), null, aggregation());

      assertEquals(Set.of("Chair", "Desk"), singles(values));
   }

   // a list bound with the entity set (a logical model attribute, AttributeRef("E", "Region"))
   // is keyed by its name "E.Region", which is no column name of the table: the key still
   // resolves to the Region column and narrows the other list (Bug #82493)
   @Test
   void entitySetListKeyNarrowsTheOtherList() throws Exception {
      SelectionSet values = metaData().getAssociatedValues(
         "List2", Map.of("E.Region", List.of("East")), erefs("Product"), null, aggregation());

      assertEquals(Set.of("Chair", "Desk"), singles(values));
   }

   // Bug #82492: a tree with a level the table doesn't have is ignored as a whole, as
   // RealtimeTableMetaData does, including a fully selected parent (no level-2 value)
   @Test
   void treeSelectionWithALevelMissingFromTheTableIsIgnored() throws Exception {
      String key = SelectionVSAssembly.SELECTION_PATH + "Region^/^CUSTOMER_ID";
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of(key, List.of("West")), refs("Product"), null, aggregation());

      assertEquals(Set.of("Chair", "Desk", "Lamp", "Desk/Pro"), singles(values));
   }

   // Bug #82492: a selection column that is in the table but not in the table the conditions
   // are built for (a stale association MV) is not ignored: ColumnNotFoundException makes
   // getAssociatedValues try the full MV
   @Test
   void selectionOnAColumnMissingOnlyFromTheMVTableIsNotIgnored() throws Exception {
      MVTableMetaData meta = metaData();
      Worksheet ws = sandbox.getWorksheet();
      TableAssembly mirror = (TableAssembly) ((TableAssembly) ws.getAssembly(
         Assembly.SELECTION + "T")).copyAssembly("ASSOCIATION_MIRROR");
      ColumnSelection columns = (ColumnSelection) mirror.getColumnSelection().clone();
      columns.removeAttribute(columns.getAttribute("Product"));
      mirror.setColumnSelection(columns);
      Method method = MVTableMetaData.class.getDeclaredMethod(
         "getConditionList", Map.class, DataRef[].class, TableAssembly.class);
      method.setAccessible(true);

      InvocationTargetException ex = assertThrows(InvocationTargetException.class, () ->
         method.invoke(meta, Map.of("Product", List.of("Chair")), refs("Region"), mirror));
      assertInstanceOf(ColumnNotFoundException.class, ex.getCause());

      // a column that is not in the table at all is still ignored on that table
      Object conds = method.invoke(meta, Map.of("Region", List.of("East"),
                                                "CUSTOMER_ID", List.of("1")),
                                   refs("Product"), mirror);
      String text = String.valueOf(conds);
      assertTrue(text.contains("Region") && !text.contains("CUSTOMER_ID"), text);
   }

   // the path key of a tree bound with the entity set resolves each level (Bug #82493)
   @Test
   void entitySetTreePathKeyNarrowsTheOtherList() throws Exception {
      String key = SelectionVSAssembly.SELECTION_PATH + "E.Region^/^E.Product";
      SelectionSet values = metaData().getAssociatedValues(
         "List3", Map.of(key, List.of("East/CHILD_SELECTION_EXISTS", "East/Chair")),
         erefs("Amount"), null, aggregation());

      assertEquals(Set.of(10.0), singles(values));
   }

   // the values of a list bound with the entity set come from the MV (Bug #82493)
   @Test
   void entitySetColumnTableHasTheDistinctValues() throws Exception {
      XTable table = metaData().getColumnTable("List2", new String[] { "E.Product" });

      assertNotNull(table);
      assertEquals(Set.of("Chair", "Desk", "Desk/Pro", "Lamp"), new HashSet<>(column(table, 0)));
   }

   // a measure of a list bound with the entity set is aggregated per associated value
   @Test
   void entitySetMeasureIsAggregatedPerAssociatedValue() throws Exception {
      SelectionMeasureAggregation aggregation = aggregation();
      SelectionSet values = metaData().getAssociatedValues(
         "List1", Map.of("E.Product", List.of("Desk")), erefs("Region"), "Sum(E.Amount)",
         aggregation);

      assertEquals(Set.of("East", "North"), singles(values));
      assertEquals(20.0, sum(aggregation, "East"));
      assertEquals(4.0, sum(aggregation, "North"));
   }

   // with an association MV, a column of the list that the association MV has is not reported
   // missing when the list is bound with the entity set. A missing column marks the MV for a
   // rebuild (mv_column_missing) and leaves the association MV (Bug #82493)
   @Test
   void entitySetColumnIsFoundInTheAssociationMV() throws Exception {
      AssetEntry entry = associationMV("Region", "Product");

      metaData().getColumnTable("List2", new String[] { "E.Product" });

      assertNull(entry.getProperty("mv_column_missing"));
   }

   // a column the association MV doesn't have is still reported missing
   @Test
   void columnMissingFromTheAssociationMVIsReported() throws Exception {
      AssetEntry entry = associationMV("Region", "Product");

      metaData().getColumnTable("List2", new String[] { "Amount" });

      assertEquals("true", entry.getProperty("mv_column_missing"));
   }

   // a key that is the exact name of a column with a dot in it is that column, not the column
   // named after the dot
   @Test
   void exactColumnNameWinsOverTheEntityForm() throws Exception {
      Object[][] data = {
         { "Region", "Sub.Region", "Amount" },
         { "X", "East", 1 },
         { "East", "X", 2 },
         { "West", "X", 3 },
      };
      SelectionSet values = metaData(data).getAssociatedValues(
         "List1", Map.of("Sub.Region", List.of("X")), refs("Amount"), null, aggregation());

      assertEquals(Set.of(2.0, 3.0), singles(values));
   }

   // Bug #82492 with Bug #82493: an entity.attribute key resolves to its column and is applied,
   // and only the key whose column the table doesn't have is ignored
   @Test
   void entitySetKeyOnAColumnMissingFromTheTableIsIgnored() throws Exception {
      SelectionSet values = metaData().getAssociatedValues(
         "List2", Map.of("E.Region", List.of("East"), "E.CUSTOMER_ID", List.of("1")),
         erefs("Product"), null, aggregation());

      assertEquals(Set.of("Chair", "Desk"), singles(values));
   }

   // Bug #82492 with Bug #82493: a tree bound with the entity set with a level the table
   // doesn't have is ignored as a whole
   @Test
   void entitySetTreeWithALevelMissingFromTheTableIsIgnored() throws Exception {
      String key = SelectionVSAssembly.SELECTION_PATH + "E.Region^/^E.CUSTOMER_ID";
      SelectionSet values = metaData().getAssociatedValues(
         "List3", Map.of(key, List.of("West")), erefs("Product"), null, aggregation());

      assertEquals(Set.of("Chair", "Desk", "Lamp", "Desk/Pro"), singles(values));
   }

   // Bug #82492 with Bug #82493: an entity.attribute key whose column is in the selection table
   // but not in the table the conditions are built for (a stale association MV) is not ignored
   @Test
   void entitySetKeyMissingOnlyFromTheMVTableIsNotIgnored() throws Exception {
      MVTableMetaData meta = metaData();
      Worksheet ws = sandbox.getWorksheet();
      TableAssembly mirror = (TableAssembly) ((TableAssembly) ws.getAssembly(
         Assembly.SELECTION + "T")).copyAssembly("ASSOCIATION_MIRROR");
      ColumnSelection columns = (ColumnSelection) mirror.getColumnSelection().clone();
      columns.removeAttribute(columns.getAttribute("Product"));
      mirror.setColumnSelection(columns);
      Method method = MVTableMetaData.class.getDeclaredMethod(
         "getConditionList", Map.class, DataRef[].class, TableAssembly.class);
      method.setAccessible(true);

      InvocationTargetException ex = assertThrows(InvocationTargetException.class, () ->
         method.invoke(meta, Map.of("E.Product", List.of("Chair")), erefs("Region"), mirror));
      assertInstanceOf(ColumnNotFoundException.class, ex.getCause());

      Object conds = method.invoke(meta, Map.of("E.Region", List.of("East")), erefs("Product"),
                                   mirror);
      assertTrue(String.valueOf(conds).contains("Region"), String.valueOf(conds));
   }

   // ---------------------------------------------------------------- fixture

   // the association MV of the selection table T has the given columns
   private static AssetEntry associationMV(String... columns) {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                                        "MVTableMetaDataTest", null);
      RuntimeMV rmv = new RuntimeMV(entry, null, null, "T", "ASSOCIATION_T", false, 0L, null);
      MVDef def = mock(MVDef.class);
      List<MVColumn> mvcols = new ArrayList<>();

      for(String col : columns) {
         mvcols.add(new MVColumn(new ColumnRef(new AttributeRef(null, col)), true));
      }

      when(def.getColumns()).thenReturn(mvcols);
      MVManager mgr = MVManager.getManager();
      when(mgr.findRuntimeMV(any(), any(), any(), any(), any(), any(), anyBoolean()))
         .thenReturn(rmv);
      when(mgr.get("ASSOCIATION_T")).thenReturn(def);
      return entry;
   }

   private MVTableMetaData metaData() throws Exception {
      return metaData(DATA);
   }

   private MVTableMetaData metaData(Object[][] data) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, Assembly.SELECTION + "T");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.STRING, XSchema.INTEGER }, data));
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

   // the columns of lists bound with the entity set, as a logical model binding gives them:
   // AttributeRef("E", "Region") is named "E.Region"
   private static DataRef[] erefs(String... names) {
      return Arrays.stream(names).map(n -> new ColumnRef(new AttributeRef("E", n)))
         .toArray(DataRef[]::new);
   }

   // the associated values of a single list: the set holds a tuple per prefix of the list
   // columns, and SelectionSet.normalize stores a tuple of one value as the value itself, so
   // the values of the first column are the elements that are not tuples
   private static Set<Object> singles(SelectionSet values) {
      Set<Object> result = new HashSet<>();

      for(Object obj : values) {
         if(!(obj instanceof SelectionSet.Tuple)) {
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

   private AssetQuerySandbox sandbox;
}
