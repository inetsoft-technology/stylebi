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
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
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
 * Bug #79447: {@code set_rankings} stored two or more ranking conditions joined by AND, but the
 * query engine never intersects them and never reads the junction. On an ungrouped table, or for
 * an entry on an aggregate, only the first entry applies; for two entries on one group column
 * only the last does. The one multi-entry shape that works is a different group column per
 * entry, each with an {@code of} aggregate, applied as a nested per-parent top-N.
 * {@link WorksheetMutationSupport#setRankings} now refuses every other 2+ list before it
 * mutates anything.
 *
 * <p>Accepted lists are executed for real with {@link AssetQuerySandbox}. Mirror M of embedded S
 * is grouped by (G1, G2) with Sum(Q) as Total and Count(Q) as Cnt. S has nine rows,
 * x/p 90, x/q 80, x/r 10, y/p 70, y/q 20, y/r 60, z/p 5, z/q 4, z/r 3.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  MultipleRankingsGuardTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MultipleRankingsGuardTest {
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

   // -- accepted: distinct group columns, each with an 'of' aggregate, executed -----------------

   @Test
   void distinctGroupColumnsApplyAsNestedTopN() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");

      WorksheetMutationSupport.setRankings(m, List.of(top1("G1", "Total"), top1("G2", "Total")));

      assertEquals(3, m.getRankingConditionList().getConditionList().getSize());
      assertEquals(List.of(row("x", "p", 90, 1)), rows(ws, "M"));
   }

   @Test
   void theNestedResultFollowsTheGroupOrderNotTheEntryOrder() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");

      WorksheetMutationSupport.setRankings(m, List.of(top1("G2", "Total"), top1("G1", "Total")));

      assertEquals(List.of(row("x", "p", 90, 1)), rows(ws, "M"));
   }

   @Test
   void twoDifferentAggregatesAreAccepted() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");

      WorksheetMutationSupport.setRankings(m, List.of(
         new RankingSpec("G1", 2, "TOP_N", false, "Total"),
         new RankingSpec("G2", 1, "BOTTOM_N", false, "Cnt")));

      assertEquals(3, m.getRankingConditionList().getConditionList().getSize());
      assertEquals(List.of(row("x", "p", 90, 1), row("y", "p", 70, 1)), rows(ws, "M"));
   }

   // -- refused: nothing is stored, the earlier ranking still applies ---------------------------

   @Test
   void sameGroupColumnTwiceIsRefused() throws Exception {
      assertRefused("M", List.of(top1("G1", "Total"), top1("G1", "Cnt")));
   }

   @Test
   void aggregateDirectEntriesAreRefused() throws Exception {
      assertRefused("M", List.of(new RankingSpec("Total", 1, "TOP_N", false),
                                 new RankingSpec("Cnt", 1, "TOP_N", false)));
   }

   @Test
   void aGroupColumnPlusAnAggregateIsRefused() throws Exception {
      assertRefused("M", List.of(top1("G1", "Total"),
                                 new RankingSpec("Total", 1, "TOP_N", false)));
   }

   @Test
   void anEntryWithoutOfIsRefused() throws Exception {
      assertRefused("M", List.of(top1("G1", "Total"),
                                 new RankingSpec("G2", 1, "TOP_N", false)));
      assertRefused("M", List.of(new RankingSpec("G1", 1, "TOP_N", false),
                                 new RankingSpec("G2", 1, "TOP_N", false)));
   }

   @Test
   void anOfThatNamesAGroupColumnIsRefused() throws Exception {
      assertRefused("M", List.of(top1("G1", "Total"), top1("G2", "G1")));
      assertRefused("M", List.of(top1("G1", "G2"), top1("G2", "G1")));
   }

   @Test
   void anOfThatMatchesNothingIsRefused() throws Exception {
      assertRefused("M", List.of(top1("G1", "Total"), top1("G2", "NoSuchColumn")));
   }

   /** The view form is not an 'of' when the aggregate has an alias; the plugin sends the alias. */
   @Test
   void theViewFormOfAnAliasedAggregateIsRefused() throws Exception {
      assertRefused("M", List.of(top1("G1", "Total"), top1("G2", "Sum(Q)")));
   }

   /** A mirror of a grouped table has its own, empty AggregateInfo. */
   @Test
   void anUngroupedMirrorIsRefused() throws Exception {
      assertRefused("P", List.of(top1("G1", "Total"), top1("G2", "Total")));
   }

   @Test
   void aGrandTotalTableIsRefused() throws Exception {
      assertRefused("GT", List.of(top1("G1", "Total"), top1("G2", "Total")));
   }

   @Test
   void aNullEntryIsRefused() throws Exception {
      assertRefused("M", Arrays.asList(top1("G1", "Total"), null));
   }

   /** wbs-197 reads the message for both the ranking and the limit. */
   @Test
   void theRefusalNamesTheRankingAndTheLimit() throws Exception {
      Worksheet ws = worksheet();
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.setRankings(
            table(ws, "M"), List.of(top1("G1", "Total"), top1("G1", "Cnt"))));

      assertTrue(ex.getMessage().matches("(?is).*rank.*"), ex.getMessage());
      assertTrue(ex.getMessage().matches("(?is).*(group|more than one|only one).*"),
                 ex.getMessage());
   }

   // -- unchanged: zero or one entry ------------------------------------------------------------

   @Test
   void oneEntryWithAndWithoutOfIsUnchanged() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");

      WorksheetMutationSupport.setRankings(m, List.of(top1("G1", "Total")));
      assertEquals(1, m.getRankingConditionList().getConditionList().getSize());

      assertDoesNotThrow(() -> WorksheetMutationSupport.setRankings(
         m, List.of(new RankingSpec("Total", 1, "TOP_N", false))));
      assertDoesNotThrow(() -> WorksheetMutationSupport.setRankings(
         table(ws, "P"), List.of(top1("G1", "Total"))));
   }

   @Test
   void anEmptyListStillClears() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");
      WorksheetMutationSupport.setRankings(m, List.of(top1("G1", "Total")));

      WorksheetMutationSupport.setRankings(m, List.of());

      assertEquals(0, m.getRankingConditionList().getConditionList().getSize());
      assertEquals(9, rows(ws, "M").size());
   }

   // -- fixtures --------------------------------------------------------------------------------

   /**
    * Gives {@code name} a valid single ranking where it can take one, tries {@code specs}, and
    * checks the call is refused with the stored list and the executed rows both untouched.
    */
   private static void assertRefused(String name, List<RankingSpec> specs) throws Exception {
      Worksheet ws = worksheet();
      TableAssembly t = table(ws, name);
      List<List<Object>> before = null;

      if("M".equals(name)) {
         WorksheetMutationSupport.setRankings(t, List.of(top1("G1", "Total")));
         before = rows(ws, name);
      }

      ConditionListWrapper stored = t.getRankingConditionList();
      int size = stored == null ? 0 : stored.getConditionList().getSize();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> WorksheetMutationSupport.setRankings(t, specs));

      assertTrue(ex.getMessage().startsWith("set_rankings: more than one ranking"),
                 ex.getMessage());
      assertSame(stored, t.getRankingConditionList(), "the ranking list was replaced");
      assertEquals(size, t.getRankingConditionList().getConditionList().getSize());

      if(before != null) {
         assertEquals(before, rows(ws, name));
      }
   }

   private static RankingSpec top1(String field, String of) {
      return new RankingSpec(field, 1, "TOP_N", false, of);
   }

   private static Worksheet worksheet() throws Exception {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly s = new EmbeddedTableAssembly(ws, "S");
      Object[][] data = {
         { "G1", "G2", "Q" },
         { "x", "p", 90 }, { "x", "q", 80 }, { "x", "r", 10 },
         { "y", "p", 70 }, { "y", "q", 20 }, { "y", "r", 60 },
         { "z", "p", 5 }, { "z", "q", 4 }, { "z", "r", 3 } };
      String[] types = { XSchema.STRING, XSchema.STRING, XSchema.INTEGER };
      s.setEmbeddedData(new XEmbeddedTable(types, data));
      ws.addAssembly(s);

      MirrorTableAssembly m = mirror(ws, "M", s);
      WorksheetMutationSupport.applyAggregateInfo(m,
         List.of(new GroupSpec("G1"), new GroupSpec("G2")),
         List.of(new AggregateSpec("Q", "Sum", "Total"), new AggregateSpec("Q", "Count", "Cnt")));

      // a mirror of the grouped table carries its own, empty AggregateInfo
      mirror(ws, "P", m);

      // grand total: aggregates, no group
      MirrorTableAssembly gt = mirror(ws, "GT", s);
      WorksheetMutationSupport.applyAggregateInfo(gt, List.of(),
         List.of(new AggregateSpec("Q", "Sum", "Total")));
      return ws;
   }

   private static MirrorTableAssembly mirror(Worksheet ws, String name, TableAssembly source) {
      MirrorTableAssembly m = new MirrorTableAssembly(ws, name, source);
      m.setProperty("no_cache", "true");
      ws.addAssembly(m);
      m.update();
      return m;
   }

   private static TableAssembly table(Worksheet ws, String name) {
      return (TableAssembly) ws.getAssembly(name);
   }

   private static List<Object> row(Object... values) {
      return Arrays.asList(values);
   }

   /** The data rows of {@code name} as the worksheet reads it, sorted. */
   private static List<List<Object>> rows(Worksheet ws, String name) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      try {
         TableLens lens = box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE);
         assertNotNull(lens);
         List<List<Object>> rows = new ArrayList<>();

         for(int r = 1; lens.moreRows(r); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               Object value = lens.getObject(r, c);
               row.add(value instanceof Number ? ((Number) value).intValue() : value);
            }

            rows.add(row);
         }

         rows.sort(Comparator.comparing(String::valueOf));
         return rows;
      }
      finally {
         box.dispose();
      }
   }
}
