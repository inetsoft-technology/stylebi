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
package inetsoft.report.composition.execution;

import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.lens.DefaultTableLens;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.internal.SelectionListVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.SelectionSet;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82323: a selection list on a column whose values have more than one type (a rotated
 * table) typed the whole column after its first value. Values of the other types were not
 * found in the associated values (state 0 instead of compatible with nothing selected), the
 * list was sorted by converting every value to the first value's type, and the no-MV metadata
 * converted booleans and numbers into each other. The first value differs between the no-MV
 * metadata (row order) and the MV metadata (a grouped query), so the two gave different lists.
 *
 * <p>Runs the real rotation, {@link RealtimeTableMetaData} and
 * {@link SelectionListVSAQuery#refreshSelectionValue0}; the MV metadata's grouped order is made
 * the way {@code MVTableMetaData.transform} makes it, with a group on the column.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
               LibManagerTestConfiguration.class, SelectionListMixedTypeTest.TestConfig.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SelectionListMixedTypeTest {
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

   @BeforeEach
   void setUp() {
      viewsheet = new Viewsheet();
      viewsheet.getVSAssemblyInfo().setName("vs1");

      box = mock(ViewsheetSandbox.class);
      when(box.getID()).thenReturn("vs1");
      when(box.getViewsheet()).thenReturn(viewsheet);
      when(box.getMode()).thenReturn(RuntimeViewsheet.VIEWSHEET_RUNTIME_MODE);
      when(box.isRuntime()).thenReturn(true);
   }

   @Test
   void rotatedMixedColumnIsAllCompatibleInOneOrderOnBothPaths() throws Exception {
      // the reporter's rotate2 row, rotated into column X
      Object[][] rotate2 = {
         { "k", "YEAR", "DISCOUNT", "PAID", "S1" },
         { "X", YEAR, 0.15, false, 6 }
      };
      // the same values in another column order, so another row order after the rotation
      Object[][] shuffled = {
         { "k", "PAID", "S1", "DISCOUNT", "YEAR" },
         { "X", false, 6, 0.15, YEAR }
      };
      List<String> expected = List.of("0.15", "6", YEAR_STR, "false");

      for(Object[][] data : List.of(rotate2, shuffled)) {
         Worksheet ws = rotate(data);
         SelectionList noMV = refreshNoMV(ws);
         assertEquals(expected, values(noMV), "no-MV order");
         assertAllCompatible(noMV);
         assertTrue(noMV.isMixedType());

         SelectionList mv = refreshGrouped(ws);
         assertEquals(expected, values(mv), "grouped (MV) order");
         assertAllCompatible(mv);
      }
   }

   @Test
   void rotatedBooleanAndNumberKeepBothValues() throws Exception {
      for(Object number : List.of(23, 23L, 23.0)) {
         Worksheet ws = rotate(new Object[][] { { "k", "PAID", "ID" }, { "X", false, number } });
         SelectionList noMV = refreshNoMV(ws);

         assertEquals(List.of("23", "false"), values(noMV), "no-MV values of " + number);
         assertAllCompatible(noMV);

         SelectionList mv = refreshGrouped(ws);
         assertEquals(List.of("23", "false"), values(mv), "grouped values of " + number);
         assertAllCompatible(mv);
      }
   }

   @Test
   void resortKeepsTheMixedOrder() throws Exception {
      SelectionList list = refreshNoMV(rotate(new Object[][] {
         { "k", "YEAR", "DISCOUNT", "PAID", "S1" },
         { "X", YEAR, 0.15, false, 6 } }));

      // the user toggles the sort (VSSelectionService.sortList) on the list or a clone of it
      list.sort(XConstants.SORT_DESC);
      assertEquals(List.of("false", YEAR_STR, "6", "0.15"), values(list));

      SelectionList copy = (SelectionList) list.clone();
      copy.sort(XConstants.SORT_ASC);
      assertEquals(List.of("0.15", "6", YEAR_STR, "false"), values(copy));
   }

   @Test
   void numbersOfOneKindKeepTheirNumericOrder() throws Exception {
      // 1.5 is a Double, the others Integers: still one type (number), as before
      SelectionList list = refresh(new DefaultTableLens(new Object[][] {
         { COL }, { 10 }, { 9 }, { 100 }, { 1.5 } }), null);

      assertFalse(list.isMixedType());
      assertEquals(List.of("1.5", "9", "10", "100"), values(list));
   }

   @Test
   void textValuesKeepTheirTextOrder() throws Exception {
      SelectionList list = refresh(new DefaultTableLens(new Object[][] {
         { COL }, { "9" }, { "b" }, { "10" }, { "a" } }), null);

      assertFalse(list.isMixedType());
      assertEquals(List.of("10", "9", "a", "b"), values(list));
   }

   @Test
   void selectionSetFindsValuesOfAnotherType() {
      SelectionSet set = new SelectionSet();
      set.add(YEAR);
      set.add(0.15);
      set.add(false);
      set.add(6);

      assertTrue(set.contains(YEAR));
      assertTrue(set.contains(0.15));
      assertTrue(set.contains(false));
      assertTrue(set.contains(6));
      assertFalse(set.contains(7));
   }

   @Test
   void columnMetaDataKeepsBooleanAndNumber() {
      assertValues(List.of(false, 23), false, 23);
      assertValues(List.of(23, false), 23, false);
      assertValues(List.of(false, 23L), false, 23L);
      assertValues(List.of(false, 23.0), false, 23.0);
      // 0 and 1 are still merged with booleans
      assertValues(List.of(true, false), true, 0);
      assertValues(List.of(0, 1), 0, true);

      ColumnMetaData meta = new ColumnMetaData();
      meta.addValue(false);
      meta.addValue(23);
      assertEquals(0, meta.getValueIndex(false));
      assertEquals(1, meta.getValueIndex(23));
   }

   private static void assertValues(List<Object> expected, Object... added) {
      ColumnMetaData meta = new ColumnMetaData();
      meta.setType(XSchema.STRING);

      for(Object val : added) {
         meta.addValue(val);
      }

      List<Object> result = new ArrayList<>();

      for(int i = 0; i < meta.getValueCount(); i++) {
         result.add(meta.getValue(i));
      }

      assertEquals(expected, result, Arrays.toString(added));
   }

   /** A worksheet with the embedded table S and R, the rotation of S. */
   private static Worksheet rotate(Object[][] data) {
      String[] types = new String[data[0].length];

      for(int i = 0; i < types.length; i++) {
         Object val = data[1][i];
         types[i] = val instanceof String ? XSchema.STRING : Tool.getDataType(val.getClass());
      }

      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(s);
      ws.addAssembly(new RotatedTableAssembly(ws, "R", s));
      return ws;
   }

   /** The list made from RealtimeTableMetaData, what the no-MV path uses. */
   private SelectionList refreshNoMV(Worksheet ws) throws Exception {
      AssetQuerySandbox wbox = sandbox(ws);
      TableLens rotated = wbox.getTableLens("R", AssetQuerySandbox.RUNTIME_MODE,
                                            new VariableTable());
      rotated.moreRows(TableLens.EOT);

      RealtimeTableMetaData meta = new RealtimeTableMetaData("R");
      meta.process(rotated, new String[] { COL }, new ArrayList<>());
      DataRef[] refs = { new AttributeRef(null, COL) };
      SelectionSet vset = meta.getAssociatedValues(NAME, new HashMap<>(), refs, null,
                                                   new SelectionMeasureAggregation(false));
      return refresh(meta.getColumnTable(NAME, refs), vset);
   }

   /** The list made from a group on the column, the row order of MVTableMetaData. */
   private SelectionList refreshGrouped(Worksheet ws) throws Exception {
      MirrorTableAssembly mirror = new MirrorTableAssembly(ws, "M", (WSAssembly) ws.getAssembly("R"));
      ws.addAssembly(mirror);
      AssetQuerySandbox wbox = sandbox(ws);
      wbox.refreshColumnSelection("M", true);
      ColumnRef col = (ColumnRef) mirror.getColumnSelection(false).getAttribute(COL);
      AggregateInfo ainfo = new AggregateInfo();
      ainfo.addGroup(new GroupRef(col));
      ColumnSelection columns = new ColumnSelection();
      columns.addAttribute(col);
      mirror.setColumnSelection(columns);
      mirror.setAggregateInfo(ainfo);
      mirror.resetColumnSelection();

      TableLens grouped = wbox.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE,
                                            new VariableTable());
      grouped.moreRows(TableLens.EOT);
      SelectionSet vset = new SelectionSet();

      for(int r = 1; grouped.moreRows(r); r++) {
         // as MVTableMetaData.getAssociatedValues adds them
         Object val = SelectionSet.normalize(grouped.getObject(r, 0));
         vset.add(new SelectionSet.Tuple(new Object[] { val }, 1));
      }

      return refresh(grouped, vset);
   }

   private static AssetQuerySandbox sandbox(Worksheet ws) throws Exception {
      AssetQuerySandbox wbox = new AssetQuerySandbox(ws);
      wbox.setActive(true);
      wbox.refreshColumnSelection("R", true);
      return wbox;
   }

   /** Runs SelectionListVSAQuery.refreshSelectionValue0 with nothing selected. */
   private SelectionList refresh(XTable data, Set<Object> vset) throws Exception {
      String name = NAME + (++count);
      SelectionListVSAssembly list = new SelectionListVSAssembly(viewsheet, name);
      SelectionListVSAssemblyInfo info = (SelectionListVSAssemblyInfo) list.getInfo();
      ColumnRef ref = new ColumnRef(new AttributeRef(null, COL));
      ref.setDataType(XSchema.STRING);
      info.setTableName("R");
      info.setDataRef(ref);
      info.setSortTypeValue(XConstants.SORT_ASC);
      viewsheet.addAssembly(list);

      Map<String, Set<Object>> values = new HashMap<>();

      if(vset != null) {
         values.put(COL, vset);
      }

      new SelectionListVSAQuery(box, name).refreshSelectionValue0(
         data, new HashMap<>(), new HashMap<>(), values, new SelectionMeasureAggregation(false));
      return list.getSelectionList();
   }

   private static List<String> values(SelectionList list) {
      List<String> result = new ArrayList<>();

      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         result.add(list.getSelectionValue(i).getValue());
      }

      return result;
   }

   private static void assertAllCompatible(SelectionList list) {
      for(int i = 0; i < list.getSelectionValueCount(); i++) {
         SelectionValue value = list.getSelectionValue(i);
         assertEquals(SelectionValue.STATE_COMPATIBLE, value.getState(),
                      "state of " + value.getValue());
      }
   }

   private static final Timestamp YEAR = Timestamp.valueOf("2005-01-01 00:00:00");
   private static final String YEAR_STR = "2005-01-01 00:00:00";
   private static final String NAME = "SelectionList";
   private static final String COL = "X";

   private Viewsheet viewsheet;
   private ViewsheetSandbox box;
   private int count;
}
