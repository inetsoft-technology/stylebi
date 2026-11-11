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
import inetsoft.uql.XTable;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81102, a rotated table keeps every row of its source when the first-column values
 * (the rotated header names) repeat, including names that differ only in case.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  RotatedQueryDuplicateHeaderTest.CacheConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RotatedQueryDuplicateHeaderTest {
   @Configuration
   static class CacheConfig {
      @Bean
      @SuppressWarnings("unchecked")
      public AssetDataCache assetDataCache() {
         ObjectProvider<DistributedTableCacheStore> provider = mock(ObjectProvider.class);
         when(provider.getObject()).thenReturn(mock(DistributedTableCacheStore.class));
         return new AssetDataCache(mock(DataSourceRegistry.class), provider);
      }
   }

   @Test
   void repeatedNameGetsSuffixAndKeepsItsData() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "A", 2, 20 }, { "B", 3, 30 } },
                    "item,A,A_1,B", "Q1,1,2,3", "Q2,10,20,30");
   }

   @Test
   void threeEqualNames() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "A", 2, 20 }, { "A", 3, 30 } },
                    "item,A,A_1,A_2", "Q1,1,2,3", "Q2,10,20,30");
   }

   @Test
   void nonAdjacentRepeat() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "B", 2, 20 }, { "A", 3, 30 } },
                    "item,A,B,A_1", "Q1,1,2,3", "Q2,10,20,30");
   }

   @Test
   void namesDifferingOnlyInCaseStayDistinctColumns() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "a", 2, 20 } },
                    "item,A,a_1", "Q1,1,2", "Q2,10,20");
   }

   @Test
   void caseDifferentThenEqual() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "a", 2, 20 }, { "A", 3, 30 } },
                    "item,A,a_1,A_2", "Q1,1,2,3", "Q2,10,20,30");
   }

   @Test
   void literalSuffixedNameKeepsItsName() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "A", 2, 20 }, { "A_1", 3, 30 } },
                    "item,A,A_1_1,A_1", "Q1,1,2,3", "Q2,10,20,30");
   }

   @Test
   void uniqueNamesAreUnchanged() throws Exception {
      assertRotated(new Object[][] { { "A", 1, 10 }, { "B", 2, 20 }, { "C", 3, 30 } },
                    "item,A,B,C", "Q1,1,2,3", "Q2,10,20,30");
   }

   private static void assertRotated(Object[][] rows, String... expected) throws Exception {
      Worksheet ws = new Worksheet();
      Object[][] data = new Object[rows.length + 1][];
      data[0] = new Object[] { "item", "Q1", "Q2" };
      System.arraycopy(rows, 0, data, 1, rows.length);
      EmbeddedTableAssembly source = new EmbeddedTableAssembly(ws, "S");
      source.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER }, data));
      ws.addAssembly(source);
      ws.addAssembly(new RotatedTableAssembly(ws, "R", source));

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      TableLens lens = box.getTableLens("R", AssetQuerySandbox.RUNTIME_MODE,
                                        new VariableTable());
      assertNotNull(lens, "the query failed, see the log");
      lens.moreRows(XTable.EOT);
      List<String> actual = new ArrayList<>();

      for(int r = 0; r < lens.getRowCount(); r++) {
         StringBuilder line = new StringBuilder();

         for(int c = 0; c < lens.getColCount(); c++) {
            line.append(c == 0 ? "" : ",").append(lens.getObject(r, c));
         }

         actual.add(line.toString());
      }

      assertEquals(List.of(expected), actual);
   }
}
