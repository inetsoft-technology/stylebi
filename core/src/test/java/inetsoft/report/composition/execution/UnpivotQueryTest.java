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
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.UnpivotTableAssembly;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.schema.XSchema;
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
 * An un-pivot table assembly executed in a worksheet, on top of an embedded table: the rows the
 * engine gives and the columns it names.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  UnpivotQueryTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class UnpivotQueryTest {
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

   /** The worksheet: the embedded table S of a crosstab and its un-pivot U. */
   private static Worksheet worksheet(int headerColumns) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      s.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER, XSchema.INTEGER },
         new Object[][] {
            { "region", "Q1", "Q2", "Q3" },
            { "East", 1, 2, 3 },
            { "West", 4, 5, 6 }
         }));
      ws.addAssembly(s);

      UnpivotTableAssembly u = new UnpivotTableAssembly(ws, "U", s);
      u.setHeaderColumns(headerColumns);
      ws.addAssembly(u);
      return ws;
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

   @Test
   void runtimeGivesARowPerCellWithDimensionAndMeasureColumns() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(worksheet(1));
      box.setActive(true);
      box.refreshColumnSelection("U", true);
      TableLens lens = box.getTableLens("U", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());

      assertEquals(List.of(
         List.of("region", "Dimension", "Measure"),
         List.of("East", "Q1", 1), List.of("East", "Q2", 2), List.of("East", "Q3", 3),
         List.of("West", "Q1", 4), List.of("West", "Q2", 5), List.of("West", "Q3", 6)),
                   read(lens));
   }

   @Test
   void twoHeaderColumnsLeaveTwoMeasureColumns() throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(worksheet(2));
      box.setActive(true);
      box.refreshColumnSelection("U", true);
      TableLens lens = box.getTableLens("U", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());

      // region and Q1 are copied to every row, the values of Q1 keep their type (an integer)
      assertEquals(List.of(
         List.of("region", "Q1", "Dimension", "Measure"),
         List.of("East", 1, "Q2", 2), List.of("East", 1, "Q3", 3),
         List.of("West", 4, "Q2", 5), List.of("West", 4, "Q3", 6)),
                   read(lens));
   }

   // the user changed the type of the Measure column to text: its cells are text
   @Test
   void changedColumnTypeConvertsTheMeasureColumn() throws Exception {
      Worksheet ws = worksheet(1);
      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      box.setActive(true);
      box.refreshColumnSelection("U", true);
      UnpivotTableAssembly u = (UnpivotTableAssembly) ws.getAssembly("U");
      u.changeColumnType(u.getColumnSelection(false).getAttribute("Measure"), XSchema.STRING,
                         null, false);
      box.resetTableLens("U");
      TableLens lens = box.getTableLens("U", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());

      assertEquals(List.of(
         List.of("region", "Dimension", "Measure"),
         List.of("East", "Q1", "1"), List.of("East", "Q2", "2"), List.of("East", "Q3", "3"),
         List.of("West", "Q1", "4"), List.of("West", "Q2", "5"), List.of("West", "Q3", "6")),
                   read(lens));
   }
}
