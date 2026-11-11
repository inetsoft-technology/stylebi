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
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.EmbeddedTableAssembly;
import inetsoft.uql.asset.RankingCondition;
import inetsoft.uql.asset.Worksheet;
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
 * The top / bottom N of a worksheet table when values are tied at the Nth place. The worksheet
 * ranking keeps exactly N rows (the ties after the Nth are cut), and rows with the same value
 * keep the order of the source.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  LibManagerTestConfiguration.class,
                                  RankingTiesTest.TestConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class RankingTiesTest {
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

   /** scores 10, 20, 20, 20, 5, 10: three rows tied at the top, two at 10 */
   private static final Object[][] SCORES = {
      { "name", "score" },
      { "a", 10 },
      { "b", 20 },
      { "c", 20 },
      { "d", 20 },
      { "e", 5 },
      { "f", 10 }
   };

   /** The names of the rows of the ranking, in the order of the result. */
   private static List<String> rank(int operation, int n, boolean equal) throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly table = new EmbeddedTableAssembly(ws, "S");
      table.setEmbeddedData(new XEmbeddedTable(
         new String[] { XSchema.STRING, XSchema.INTEGER }, SCORES));
      ws.addAssembly(table);

      RankingCondition ranking = new RankingCondition();
      ranking.setOperation(operation);
      ranking.setN(n);
      ranking.setEqual(equal);
      ConditionList list = new ConditionList();
      list.append(new ConditionItem(table.getColumnSelection(false).getAttribute("score"),
                                    ranking, 0));
      table.setRankingConditionList(list);

      AssetQuerySandbox box = new AssetQuerySandbox(ws);
      TableLens lens = box.getTableLens("S", AssetQuerySandbox.RUNTIME_MODE, new VariableTable());
      lens.moreRows(TableLens.EOT);
      List<String> names = new ArrayList<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         names.add((String) lens.getObject(r, 0));
      }

      return names;
   }

   // the three rows with 20 are tied: the first two of them in the order of the source
   @Test
   void topTwoCutsTheTieAfterTheSecondRow() throws Exception {
      assertEquals(List.of("b", "c"), rank(XCondition.TOP_N, 2, false));
   }

   @Test
   void topThreeKeepsAllTheTiedRows() throws Exception {
      assertEquals(List.of("b", "c", "d"), rank(XCondition.TOP_N, 3, false));
   }

   // the row with 10 that comes first in the source wins the fourth place
   @Test
   void topFourCutsTheTieOfTheLowerValue() throws Exception {
      assertEquals(List.of("b", "c", "d", "a"), rank(XCondition.TOP_N, 4, false));
   }

   // bottom: 5, then the 10s in the order of the source
   @Test
   void bottomTwoTakesTheFirstOfTheTiedRows() throws Exception {
      assertEquals(List.of("e", "a"), rank(XCondition.BOTTOM_N, 2, false));
      assertEquals(List.of("e", "a", "f"), rank(XCondition.BOTTOM_N, 3, false));
   }

   @Test
   void nLargerThanTheTableKeepsEveryRow() throws Exception {
      assertEquals(List.of("b", "c", "d", "a", "f", "e"), rank(XCondition.TOP_N, 20, false));
   }

   // the "equal" option of the ranking condition is not used by a worksheet table: the viewsheet
   // top N passes it on (SummaryFilter.setTopN), the worksheet one sets equality kept to false.
   // Pins the current behavior.
   @Test
   void equalOptionIsIgnoredByAWorksheetTable() throws Exception {
      assertEquals(List.of("b", "c"), rank(XCondition.TOP_N, 2, true));
   }
}
