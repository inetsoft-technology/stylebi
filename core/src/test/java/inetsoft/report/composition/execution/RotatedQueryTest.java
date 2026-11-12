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
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.RotatedTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AssetCondition;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
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
      box(ws);

      assertEquals(250, columnNames(ws).size());
      assertEquals("item", columnNames(ws).get(0));
      assertEquals("r249", columnNames(ws).get(249));
   }
}
