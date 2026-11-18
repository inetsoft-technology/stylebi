/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.test.SwapperTestConfiguration;
import inetsoft.uql.VariableTable;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AggregateInfo;
import inetsoft.uql.asset.AggregateRef;
import inetsoft.uql.asset.GroupRef;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.RotatedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.util.Catalog;
import inetsoft.util.Tool;
import inetsoft.util.UserMessage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A rotated table assembly executed in a worksheet on top of an embedded table: the first column
 * of the source gives the names of the columns, the other columns give the rows.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RotatedQueryTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RotatedQueryTest {
   @Configuration
   static class TestConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }

      // a cache hit checks the materialized view of the cached data (no view here)
      @Bean
      public MVManager mvManager() {
         return mock(MVManager.class);
      }
   }

   /** The worksheet: the embedded table S of the data and its rotation R. */
   private static Worksheet worksheet(Object[][] data, String... types) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(s);
      ws.addAssembly(new RotatedTableAssembly(ws, "R", s));
      return ws;
   }

   private static final Object[][] ITEMS = {
      { "item", "Q1", "Q2" },
      { "A", 1, 2 },
      { "B", 3, 4 },
      { "C", 5, 6 }
   };

   private static final String[] ITEM_TYPES =
      { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER };

   private static AssetQuerySandbox box(Worksheet ws) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setActive(true);
      box.refreshColumnSelection("R", true);
      return box;
   }

   private static List<List<Object>> read(TableLens lens) {
      lens.moreRows(TableLens.EOT);
      List<List<Object>> rows = new ArrayList<>();

      for(int r = 0; r < lens.getRowCount(); r++) {
         List<Object> row = new ArrayList<>();

         for(int c = 0; c < lens.getColCount(); c++) {
            row.add(lens.getObject(r, c));
         }

         rows.add(row);
      }

      return rows;
   }

   private static List<String> columnNames(Worksheet ws) {
      ColumnSelection columns = ((RotatedTableAssembly) ws.getAssembly("R"))
         .getColumnSelection(false);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         names.add(columns.getAttribute(i).getName());
      }

      return names;
   }

   private static TableLens run(AssetQuerySandbox box) throws Exception {
      return box.getTableLens("R", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
   }

   @Test
   void firstColumnGivesTheColumnNamesAndTheOtherColumnsTheRows() throws Exception {
      Worksheet ws = worksheet(ITEMS, ITEM_TYPES);
      AssetQuerySandbox box = box(ws);

      assertEquals(List.of("item", "A", "B", "C"), columnNames(ws));
      assertEquals(List.of(
         List.of("item", "A", "B", "C"),
         List.of("Q1", 1, 3, 5),
         List.of("Q2", 2, 4, 6)), read(run(box)));
   }

   // the rows that do not satisfy a condition on a column of the rotated table are left out
   @Test
   void conditionOnARotatedColumnFiltersTheRotatedRows() throws Exception {
      Worksheet ws = worksheet(ITEMS, ITEM_TYPES);
      AssetQuerySandbox box = box(ws);
      RotatedTableAssembly rotated = (RotatedTableAssembly) ws.getAssembly("R");
      AssetCondition cond = new AssetCondition();
      cond.setOperation(XCondition.GREATER_THAN);
      cond.setType(XSchema.INTEGER);
      cond.addValue(3);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(rotated.getColumnSelection(false).getAttribute("B"), cond, 0));
      rotated.setPreConditionList(list);
      box.resetTableLens("R");

      // B is 3 for Q1 and 4 for Q2: only Q2 is greater than 3
      assertEquals(List.of(
         List.of("item", "A", "B", "C"),
         List.of("Q2", 2, 4, 6)), read(run(box)));
   }

   // Bug #81102: the first column holds A twice, AssetUtil.fixAlias gives the second one an alias
   // (A_1) and no column or data is lost
   @Test
   void sameNamesInTheFirstColumnAreMadeDifferent() throws Exception {
      Object[][] data = {
         { "item", "Q1", "Q2" },
         { "A", 1, 10 },
         { "A", 2, 20 },
         { "B", 3, 30 }
      };
      Worksheet ws = worksheet(data, XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER);
      List<List<Object>> rows = read(run(box(ws)));

      assertEquals(List.of("item", "A", "A_1", "B"), rows.get(0));
      assertEquals(List.of("Q1", 1, 2, 3), rows.get(1));
      assertEquals(List.of("Q2", 10, 20, 30), rows.get(2));
   }

   // Bug #81102: names that differ in case only are kept as separate columns, the second one gets
   // an alias
   @Test
   void namesDifferingInCaseKeepAllColumns() throws Exception {
      Object[][] data = {
         { "item", "Q1" },
         { "A", 1 },
         { "a", 2 }
      };
      Worksheet ws = worksheet(data, XSchema.STRING, XSchema.INTEGER);
      List<List<Object>> rows = read(run(box(ws)));

      assertEquals(List.of("item", "A", "a_1"), rows.get(0));
      assertEquals(List.of("Q1", 1, 2), rows.get(1));
   }

   // a column selection has at most 250 columns, however many rows the source has
   @Test
   void columnSelectionHasAtMost250Columns() throws Exception {
      Object[][] data = new Object[301][];
      data[0] = new Object[] { "item", "Q1" };

      for(int i = 1; i <= 300; i++) {
         data[i] = new Object[] { "r" + i, i };
      }

      Worksheet ws = worksheet(data, XSchema.STRING, XSchema.INTEGER);
      Tool.clearUserMessage();
      box(ws);

      assertEquals(250, columnNames(ws).size());
      assertEquals("item", columnNames(ws).get(0));
      assertEquals("r249", columnNames(ws).get(249));
      assertEquals(LIMITED_MESSAGE, message());
   }

   // ---- the column limit (Bug #81193) ----

   private static final String LIMITED_MESSAGE =
      Catalog.getCatalog().getString("common.limited.column", 250);

   /** The pending user message, cleared by this call, null if there is none. */
   private static String message() {
      UserMessage message = Tool.getUserMessage();
      return message == null ? null : message.getMessage();
   }

   /**
    * 300 rows: the keys 1 to 10 repeat in the rows 1 to 270, the key 11 first appears in the
    * row 271 and repeats up to the row 300.
    */
   private static Worksheet lateNameWorksheet() {
      Object[][] data = new Object[301][];
      data[0] = new Object[] { "key", "value" };

      for(int i = 1; i <= 300; i++) {
         data[i] = new Object[] { i <= 270 ? String.valueOf((i - 1) % 10 + 1) : "11", i };
      }

      return worksheet(data, XSchema.STRING, XSchema.INTEGER);
   }

   private static final List<String> FIRST_NAMES =
      List.of("key", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11");

   // the rotated table has more than 250 columns: the first occurrences of every name come
   // first in the order of the source rows, then the repeated names, so 11 keeps its column
   @Test
   void firstOccurrencesComeFirstWhenColumnsAreLimited() throws Exception {
      Worksheet ws = lateNameWorksheet();
      Tool.clearUserMessage();
      AssetQuerySandbox box = box(ws);
      List<String> names = columnNames(ws);

      assertEquals(250, names.size());
      assertEquals(FIRST_NAMES, names.subList(0, 12));
      assertEquals(List.of("1_1", "2_1"), names.subList(12, 14));
      assertFalse(names.contains("11_1"));
      assertEquals(LIMITED_MESSAGE, message());

      // the value of the column 11 is the value of its first row, 271
      List<List<Object>> rows = read(run(box));

      assertEquals(FIRST_NAMES, rows.get(0).subList(0, 12));
      assertEquals(271, rows.get(1).get(11));
   }

   // a column selection saved with the columns key, 1 to 11 keeps the column 11
   @Test
   void savedSelectionKeepsANameThatFirstAppearsLate() throws Exception {
      Worksheet ws = lateNameWorksheet();
      RotatedTableAssembly rotated = (RotatedTableAssembly) ws.getAssembly("R");
      ColumnSelection saved = new ColumnSelection();

      for(String name : FIRST_NAMES) {
         ColumnRef column = new ColumnRef(new AttributeRef(name));
         column.setDataType(name.equals("key") ? XSchema.STRING : XSchema.INTEGER);
         saved.addAttribute(column);
      }

      rotated.setColumnSelection(saved, false);
      rotated.setColumnSelection(saved.clone(), true);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setActive(true);
      Tool.clearUserMessage();
      List<List<Object>> rows = read(run(box));

      assertEquals(FIRST_NAMES, columnNames(ws).subList(0, 12));
      assertEquals(250, columnNames(ws).size());
      assertEquals(FIRST_NAMES, rows.get(0).subList(0, 12));
      assertEquals(271, rows.get(1).get(11));
      assertEquals(LIMITED_MESSAGE, message());
   }

   // a rotated table that is not limited keeps its columns in the order of the source rows and
   // shows no message
   @Test
   void noMessageWhenTheColumnsAreNotLimited() throws Exception {
      Object[][] data = {
         { "item", "Q1" },
         { "B", 1 },
         { "A", 2 },
         { "B", 3 },
         { "C", 4 }
      };
      Worksheet ws = worksheet(data, XSchema.STRING, XSchema.INTEGER);
      Tool.clearUserMessage();
      AssetQuerySandbox box = box(ws);
      run(box);

      assertEquals(List.of("item", "B", "A", "B_1", "C"), columnNames(ws));
      assertNull(message());
   }

   // a cache hit only sees the limited result, the cut is kept with the column selection so the
   // message is shown again
   @Test
   void cacheHitShowsTheMessageAgain() throws Exception {
      Worksheet ws = lateNameWorksheet();
      AssetQuerySandbox box = box(ws);
      run(box);

      box.resetTableLens("R");
      Tool.clearUserMessage();
      run(box);

      assertEquals(LIMITED_MESSAGE, message());

      // without the mark of the cut a cache hit has nothing to show, which shows that the
      // run above was a cache hit
      RotatedTableAssembly rotated = (RotatedTableAssembly) ws.getAssembly("R");
      rotated.getColumnSelection(false).setProperty("rotatedColumnLimited", null);
      rotated.getColumnSelection(true).setProperty("rotatedColumnLimited", null);
      box.resetTableLens("R");
      Tool.clearUserMessage();
      run(box);

      assertNull(message());
   }

   // ---- the names of the columns ----

   // a first column value that is empty is named by the place of its column in the rotated table,
   // counted from the column of the names (0)
   @Test
   void emptyAndBlankNamesAreNamedByTheirColumn() throws Exception {
      Object[][] data = {
         { "item", "Q1" },
         { "A", 1 },
         { null, 2 },
         { "  ", 3 },
         { "D", 4 }
      };
      Worksheet ws = worksheet(data, XSchema.STRING, XSchema.INTEGER);
      List<List<Object>> rows = read(run(box(ws)));

      assertEquals(List.of("item", "A", "Column [2]", "Column [3]", "D"), columnNames(ws));
      assertEquals(List.of("Q1", 1, 2, 3, 4), rows.get(1));
   }

   // a number is named by its text, with the number format of the worksheet (two decimals at
   // least); a date by the date format
   @Test
   void numbersAndDatesAreNamedByTheirText() throws Exception {
      Object[][] numbers = {
         { "item", "Q1" },
         { 1.5, 1 },
         { 2.0, 2 },
         { 1234567.891, 3 }
      };
      Worksheet ws = worksheet(numbers, XSchema.DOUBLE, XSchema.INTEGER);
      box(ws);

      assertEquals(List.of("item", "1.50", "2.00", "1234567.891"), columnNames(ws));

      Object[][] dates = {
         { "item", "Q1" },
         { java.sql.Date.valueOf("2024-02-29"), 1 }
      };
      ws = worksheet(dates, XSchema.DATE, XSchema.INTEGER);
      box(ws);

      assertEquals(List.of("item", "2024-02-29"), columnNames(ws));
   }

   // ---- the types of the columns ----

   private static List<String> columnTypes(Worksheet ws) {
      ColumnSelection columns = ((RotatedTableAssembly) ws.getAssembly("R"))
         .getColumnSelection(false);
      List<String> types = new ArrayList<>();

      for(int i = 0; i < columns.getAttributeCount(); i++) {
         types.add(((ColumnRef) columns.getAttribute(i)).getDataType());
      }

      return types;
   }

   // the type of a rotated column is the type of its values: the class of the first value, and
   // text when another class comes, in whichever row (the check that leaves a different class in
   // the last row out is for the grand total of a table, a rotated table has none)
   @Test
   void typeOfAColumnIsTheTypeOfItsValues() throws Exception {
      // the rotated columns A, B, C hold the cells of A, B, C in the columns Q1, Q2, Q3
      Object[][] data = {
         { "item", "Q1", "Q2", "Q3" },
         { "A", 1, 2, 3 },
         { "B", 1, "x", 3 },
         { "C", 1, 2, "x" }
      };
      Worksheet ws = worksheet(data, XSchema.STRING, XSchema.STRING, XSchema.STRING,
                               XSchema.STRING);
      box(ws);

      assertEquals(List.of(XSchema.STRING, XSchema.INTEGER, XSchema.STRING, XSchema.STRING),
                   columnTypes(ws));
   }

   // ---- on top of a rotated table ----

   // the rows of the rotated table can be grouped: the sum of each column of the original table
   @Test
   void rotatedTableCanBeGroupedAndSummed() throws Exception {
      Worksheet ws = worksheet(ITEMS, ITEM_TYPES);
      AssetQuerySandbox box = box(ws);
      RotatedTableAssembly rotated = (RotatedTableAssembly) ws.getAssembly("R");
      ColumnSelection columns = rotated.getColumnSelection(false);
      AggregateInfo info = new AggregateInfo();
      info.addGroup(new GroupRef(columns.getAttribute("item")));
      info.addAggregate(new AggregateRef(columns.getAttribute("A"), AggregateFormula.SUM));
      info.addAggregate(new AggregateRef(columns.getAttribute("B"), AggregateFormula.SUM));
      rotated.setAggregateInfo(info);
      box.resetTableLens("R");
      List<List<Object>> rows = read(run(box));

      // Q1 is 1, 3, 5 and Q2 is 2, 4, 6 for A, B, C
      assertEquals(3, rows.size());
      assertEquals(List.of("Q1", 1.0, 3.0), rows.get(1));
      assertEquals(List.of("Q2", 2.0, 4.0), rows.get(2));
   }
}
