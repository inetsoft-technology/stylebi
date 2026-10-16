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
import inetsoft.test.*;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.RankingSpec;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78719 item 2: a ranking's {@code of} binds to its aggregate by the aggregate's output
 * name, so re-aliasing that aggregate with set_group_aggregate made the query drop the ranking
 * (every group came back, with only a log WARN), and giving the old alias to another column
 * silently re-targeted it. {@link WorksheetMutationSupport#applyAggregateInfo} now re-points the
 * {@code of} the way the Composer's rename does, or refuses when it can't tell which aggregate
 * the ranking should follow.
 *
 * <p>Executed for real with {@link AssetQuerySandbox} on mirror M of embedded S. S has G/Qty/P
 * rows (a, 10, 1) x3, (b, 25, 9), (c, 5, 20), so the top group is a by Sum(Qty) (30), b by
 * Max(Qty) (25) and c by Sum(P) (20). Most cases put a {@code ws.clone()} between the ranking
 * and the edit: live, the ranking holds its own copy of the aggregate's column, while in one
 * in-memory worksheet it shares the selection's column and follows a first re-alias by accident.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  RankingOfReAliasTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RankingOfReAliasTest {
   @Configuration
   static class Config {
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

   /** The control: TOP 1 of Total ranks by Sum(Qty). */
   @Test
   void theRankingAppliesBeforeAnyEdit() throws Exception {
      Worksheet ws = rankedBySumOfQty();

      assertEquals(List.of("G", "Total"), header(ws));
      assertEquals(List.of(row("a", 30)), rows(ws));
   }

   /** WBS-135: the re-alias used to return every group. */
   @Test
   void aReAliasAfterACloneKeepsTheRanking() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      aggregate(ws, sum("Qty", "T2"));

      assertEquals(List.of("G", "T2"), header(ws));
      assertEquals(List.of(row("a", 30)), rows(ws));
      assertEquals("T2", ofName(ws));
   }

   /** Without a clone the first re-alias followed by sharing the column; the second did not. */
   @Test
   void twoReAliasesWithoutACloneKeepTheRanking() throws Exception {
      Worksheet ws = rankedBySumOfQty();

      aggregate(ws, sum("Qty", "T2"));
      assertEquals(List.of(row("a", 30)), rows(ws));

      aggregate(ws, sum("Qty", "T3"));
      assertEquals(List.of("G", "T3"), header(ws));
      assertEquals(List.of(row("a", 30)), rows(ws));
      assertEquals("T3", ofName(ws));
   }

   /** Two aggregates on Qty: the ranking's formula, Sum, says which one it follows. */
   @Test
   void sumAndMaxOnOneColumnFollowTheRankingsFormula() throws Exception {
      Worksheet ws = new Worksheet();
      source(ws);
      aggregate(ws, sum("Qty", "Total"), new AggregateSpec("Qty", "Max", "MaxQ"));
      WorksheetMutationSupport.setRanking(mirror(ws), top1("Total"));
      assertEquals(List.of(row("a", 30, 10)), rows(ws));
      ws = cloned(ws);

      aggregate(ws, sum("Qty", "T2"), new AggregateSpec("Qty", "Max", "MaxQ"));

      assertEquals(List.of("G", "T2", "MaxQ"), header(ws));
      assertEquals(List.of(row("a", 30, 10)), rows(ws));
      assertEquals("T2", ofName(ws));
   }

   /** The same, ranked by the second aggregate, which lives in a generated expression column. */
   @Test
   void sumAndMaxOnOneColumnRankedByTheMax() throws Exception {
      Worksheet ws = new Worksheet();
      source(ws);
      aggregate(ws, sum("Qty", "Total"), new AggregateSpec("Qty", "Max", "MaxQ"));
      WorksheetMutationSupport.setRanking(mirror(ws), top1("MaxQ"));
      assertEquals(List.of(row("b", 25, 25)), rows(ws));
      ws = cloned(ws);

      aggregate(ws, sum("Qty", "Total"), new AggregateSpec("Qty", "Max", "M2"));

      assertEquals(List.of(row("b", 25, 25)), rows(ws));
      assertEquals("M2", ofName(ws));
   }

   /** A formula change that keeps the alias follows the name, as it did before. */
   @Test
   void aFormulaChangeKeepingTheAliasRanksByTheNewFormula() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      aggregate(ws, new AggregateSpec("Qty", "Max", "Total"));

      assertEquals(List.of(row("b", 25)), rows(ws));
   }

   /** A formula change with a new alias: the one aggregate left on Qty is the one to follow. */
   @Test
   void aFormulaChangeWithANewAliasFollowsTheOnlyAggregateOnTheColumn() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      aggregate(ws, new AggregateSpec("Qty", "Max", "T2"));

      assertEquals(List.of(row("b", 25)), rows(ws));
      assertEquals("T2", ofName(ws));
   }

   /** An 'of' set on the unaliased aggregate keeps applying once an alias is added. */
   @Test
   void anOfOnTheBareColumnFollowsAnAliasAddedLater() throws Exception {
      Worksheet ws = new Worksheet();
      source(ws);
      aggregate(ws, sum("Qty", null));
      WorksheetMutationSupport.setRanking(mirror(ws), top1("Qty"));
      assertEquals(List.of(row("a", 30)), rows(ws));
      ws = cloned(ws);

      aggregate(ws, sum("Qty", "T2"));

      assertEquals(List.of("G", "T2"), header(ws));
      assertEquals(List.of(row("a", 30)), rows(ws));
   }

   /** J2: the old alias moved to another column, and Qty kept under a new one, is followed. */
   @Test
   void theAliasMovedElsewhereWithQtyStillAggregatedFollowsQty() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      aggregate(ws, sum("P", "Total"), sum("Qty", "T2"));

      assertEquals(List.of(row("a", 30, 3)), rows(ws));
      assertEquals("T2", ofName(ws));
   }

   /** J2: the old alias moved to another column, and Qty no longer aggregated, is refused. */
   @Test
   void theAliasMovedToAnotherColumnIsRefusedBeforeAnythingChanges() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      PairingException thrown = assertThrows(PairingException.class,
         () -> aggregate(ws, sum("P", "Total")));

      String message = thrown.getMessage();
      assertTrue(message.contains("ranking on 'G'"), message);
      assertTrue(message.contains("'Total'"), message);
      assertTrue(message.contains("of P"), message);
      assertEquals(List.of("G", "Total"), header(ws));
      assertEquals(List.of(row("a", 30)), rows(ws));
   }

   /** Neither the name nor the formula picks one of the aggregates left on Qty. */
   @Test
   void anAmbiguousRebindIsRefusedBeforeAnythingChanges() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      PairingException thrown = assertThrows(PairingException.class,
         () -> aggregate(ws, new AggregateSpec("Qty", "Max", "A"),
                         new AggregateSpec("Qty", "Min", "B")));

      String message = thrown.getMessage();
      assertTrue(message.contains("ranking on 'G'"), message);
      assertTrue(message.contains("'A'") && message.contains("'B'"), message);
      assertEquals(List.of("G", "Total"), header(ws));
      assertEquals(List.of(row("a", 30)), rows(ws));
   }

   /** The ranking's aggregate removed: left as it is, as before (the plugin warns). */
   @Test
   void removingTheRankedAggregateIsNotRefused() throws Exception {
      Worksheet ws = cloned(rankedBySumOfQty());

      assertDoesNotThrow(() -> aggregate(ws, sum("P", "SumP")));
   }

   // -- fixtures ------------------------------------------------------------------------------

   private static Worksheet rankedBySumOfQty() throws Exception {
      Worksheet ws = new Worksheet();
      source(ws);
      aggregate(ws, sum("Qty", "Total"));
      WorksheetMutationSupport.setRanking(mirror(ws), top1("Total"));
      return ws;
   }

   private static RankingSpec top1(String of) {
      return new RankingSpec("G", 1, "TOP_N", false, of);
   }

   private static AggregateSpec sum(String field, String alias) {
      return new AggregateSpec(field, "Sum", alias);
   }

   private static void aggregate(Worksheet ws, AggregateSpec... aggregates) throws Exception {
      WorksheetMutationSupport.applyAggregateInfo(
         mirror(ws), List.of(new GroupSpec("G")), List.of(aggregates));
   }

   private static void source(Worksheet ws) {
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      Object[][] data = {
         { "G", "Qty", "P" },
         { "a", 10, 1 }, { "a", 10, 1 }, { "a", 10, 1 },
         { "b", 25, 9 },
         { "c", 5, 20 } };
      String[] types = { XSchema.STRING, XSchema.INTEGER, XSchema.INTEGER };
      s.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(s);

      MirrorTableAssembly m = new MirrorTableAssembly(ws, "M", s);
      m.setProperty("no_cache", "true");
      ws.addAssembly(m);
      m.update();
   }

   private static MirrorTableAssembly mirror(Worksheet ws) {
      return (MirrorTableAssembly) ws.getAssembly("M");
   }

   private static Worksheet cloned(Worksheet ws) {
      return (Worksheet) ws.clone();
   }

   private static String ofName(Worksheet ws) {
      ConditionListWrapper rankings = mirror(ws).getRankingConditionList();
      RankingCondition ranking = (RankingCondition) rankings.getConditionItem(0).getXCondition();
      return ranking.getDataRef().getName();
   }

   private static List<Object> row(Object... values) {
      return Arrays.asList(values);
   }

   private static List<Object> header(Worksheet ws) throws Exception {
      return read(ws, true).get(0);
   }

   /** The data rows of M as the worksheet reads it, sorted by G. */
   private static List<List<Object>> rows(Worksheet ws) throws Exception {
      return read(ws, false);
   }

   private static List<List<Object>> read(Worksheet ws, boolean header) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      try {
         TableLens lens = box.getTableLens("M", AssetQuerySandbox.RUNTIME_MODE);
         assertNotNull(lens);
         List<List<Object>> rows = new ArrayList<>();

         for(int r = header ? 0 : 1; lens.moreRows(r) && (!header || r == 0); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               Object value = lens.getObject(r, c);
               row.add(value instanceof Number ? ((Number) value).intValue() : value);
            }

            rows.add(row);
         }

         rows.sort(Comparator.comparing(row -> String.valueOf(row.get(0))));
         return rows;
      }
      finally {
         box.dispose();
      }
   }
}
