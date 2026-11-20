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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.mv.MVManager;
import inetsoft.report.TableLens;
import inetsoft.report.composition.execution.AssetDataCache;
import inetsoft.report.composition.execution.DistributedTableCacheStore;
import inetsoft.uql.ColumnSelection;
import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.test.*;
import inetsoft.uql.ConditionItem;
import inetsoft.uql.ConditionList;
import inetsoft.uql.ConditionListWrapper;
import inetsoft.uql.asset.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XEmbeddedTable;
import inetsoft.web.wiz.model.WorksheetTable;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.AggregateSpec;
import inetsoft.web.wiz.worksheet.WorksheetMutationSupport.GroupSpec;
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
 * Bug #80089: {@code POST /api/wiz/ws/table}'s {@code rankingCondition} leaf had one {@code field}
 * serving as both the ranked column and the ranked-by aggregate, so the nested per-group-column
 * ranking {@code set_rankings} supports (STATE top 2 AND CITY top 1, each of an aggregate) could
 * not be expressed. A leaf may now carry {@code of}; leaves with {@code of} are built through
 * {@link WorksheetMutationSupport#setRankings}, and the shapes the engine would not apply are
 * refused naming {@code rankingCondition}. Leaves without {@code of} keep the old path.
 *
 * <p>Fixture: mirror M of embedded S, grouped by (G1, G2) with Sum(Q) as Total. S has nine rows,
 * x/p 90, x/q 80, x/r 10, y/p 70, y/q 20, y/r 60, z/p 5, z/q 4, z/r 3. G1 top 2 of Total keeps
 * x and y (six rows); adding G2 top 1 keeps the best G2 per parent (two rows).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  WorksheetTableServiceNestedRankingTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WorksheetTableServiceNestedRankingTest {
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

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private static WorksheetTableService service() {
      return new WorksheetTableService(null, null, null, null, null, null, null, null, null);
   }

   private static final String G1_TOP2 =
      "{\"field\":\"G1\",\"operation\":\"TOP_N\",\"of\":\"Total\"," +
      "\"values\":[{\"type\":\"VALUE\",\"value\":2}]}";
   private static final String G2_TOP1 =
      "{\"junction\":\"and\",\"field\":\"G2\",\"operation\":\"TOP_N\",\"of\":\"Total\"," +
      "\"values\":[{\"type\":\"VALUE\",\"value\":1}]}";

   // -- accepted: nested shape -------------------------------------------------------------------

   @Test
   void nestedLeavesBuildTheSameListSetRankingsBuilds() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly viaService = table(ws, "M");
      service().applyNestedRankings(viaService, leaves(G1_TOP2, G2_TOP1));

      TableAssembly viaSupport = table(worksheet(), "M");
      WorksheetMutationSupport.setRankings(viaSupport, List.of(
         new WorksheetMutationSupport.RankingSpec("G1", 2, "TOP_N", false, "Total"),
         new WorksheetMutationSupport.RankingSpec("G2", 1, "TOP_N", false, "Total")));

      ConditionList actual = viaService.getRankingConditionList().getConditionList();
      ConditionList expected = viaSupport.getRankingConditionList().getConditionList();
      assertEquals(3, actual.getSize());
      assertEquals(expected.getSize(), actual.getSize());

      for(int i = 0; i < 2; i++) {
         ConditionItem a = actual.getConditionItem(i * 2);
         ConditionItem e = expected.getConditionItem(i * 2);
         assertInstanceOf(GroupRef.class, a.getAttribute());
         assertEquals(e.getAttribute().getName(), a.getAttribute().getName());
         assertInstanceOf(AggregateRef.class,
                          ((RankingCondition) a.getXCondition()).getDataRef());
      }
   }

   @Test
   void nestedLeavesExecuteAsNestedTopN() throws Exception {
      Worksheet ws = worksheet();
      service().applyNestedRankings(table(ws, "M"), leaves(G1_TOP2, G2_TOP1));

      assertEquals(2, rows(ws, "M").size());
   }

   @Test
   void aLoneLeafWithOfRanksTheGroupColumnByTheAggregate() throws Exception {
      Worksheet ws = worksheet();
      service().applyNestedRankings(table(ws, "M"), leaves(G1_TOP2));

      assertEquals(6, rows(ws, "M").size());
   }

   // -- unchanged: leaf without of ---------------------------------------------------------------

   @Test
   void aLeafWithoutOfStillGoesThroughTheOldPath() throws Exception {
      WorksheetTable req = request("{\"rankingCondition\":[" +
         "{\"field\":\"Q\",\"operation\":\"TOP_N\",\"values\":[{\"type\":\"VALUE\",\"value\":3}]}]}");
      ColumnSelection cs = new ColumnSelection();
      cs.addAttribute(new ColumnRef(new inetsoft.uql.erm.AttributeRef(null, "Q")));

      ConditionList list = service().buildRankingConditionList(
         cs, req.getRankingCondition(), cs);

      assertEquals(1, list.getSize());
      RankingCondition rc = (RankingCondition) list.getConditionItem(0).getXCondition();
      assertEquals(3, rc.getN());
      assertEquals("Q", rc.getDataRef().getName());
      assertNull(req.getRankingCondition().get(0).getOf());
   }

   @Test
   void ofIsReadFromJson() throws Exception {
      assertEquals("Total", leaves(G1_TOP2).get(0).getOf());
   }

   // -- refused ----------------------------------------------------------------------------------

   @Test
   void anUngroupedTableIsRefused() throws Exception {
      Worksheet ws = worksheet();
      assertRefused(table(ws, "P"), leaves(G1_TOP2, G2_TOP1));
      assertRefused(table(ws, "P"), leaves(G1_TOP2));
   }

   @Test
   void theSameGroupColumnTwiceIsRefused() throws Exception {
      Worksheet ws = worksheet();
      assertRefused(table(ws, "M"), leaves(G1_TOP2, G2_TOP1.replace("\"G2\"", "\"G1\"")));
   }

   @Test
   void anOfThatIsNotAnAggregateIsRefused() throws Exception {
      Worksheet ws = worksheet();
      assertRefused(table(ws, "M"), leaves(G1_TOP2, G2_TOP1.replace("\"Total\"", "\"G1\"")));
   }

   @Test
   void anUnknownColumnIsRefused() throws Exception {
      Worksheet ws = worksheet();
      assertRefused(table(ws, "M"), leaves(G1_TOP2.replace("\"G1\"", "\"NoSuch\""), G2_TOP1));
      assertRefused(table(ws, "M"), leaves(G1_TOP2, G2_TOP1.replace("\"Total\"", "\"NoSuch\"")));
   }

   @Test
   void aMixedListWhereALeafHasNoOfIsRefused() throws Exception {
      Worksheet ws = worksheet();
      assertRefused(table(ws, "M"), leaves(G1_TOP2,
         "{\"junction\":\"and\",\"field\":\"G2\",\"operation\":\"TOP_N\"," +
         "\"values\":[{\"type\":\"VALUE\",\"value\":1}]}"));
   }

   @Test
   void twoLeavesWithoutOfAreRefusedOnTheRequestPath() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");
      String g1 = "{\"field\":\"G1\",\"operation\":\"TOP_N\",\"values\":[{\"type\":\"VALUE\",\"value\":2}]}";
      String g2 = "{\"junction\":\"and\",\"field\":\"G2\",\"operation\":\"TOP_N\"," +
         "\"values\":[{\"type\":\"VALUE\",\"value\":1}]}";
      List<WorksheetTable.ConditionItem> items = leaves(g1, g2);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyRankingCondition(m, items));
      assertTrue(ex.getMessage().contains("rankingCondition"), ex.getMessage());
   }

   @Test
   void unsupportedLeafOptionsAreRefusedNotDropped() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");
      assertRefused(m, leaves(G1_TOP2.replace("TOP_N", "MIDDLE_N")));
      assertRefused(m, leaves(G1_TOP2.replace("\"of\"", "\"dateGroupLevel\":\"year\",\"of\"")));
      assertRefused(m, leaves(G1_TOP2.replace("\"field\"", "\"conditionLevel\":1,\"field\"")));
      assertRefused(m, leaves(G1_TOP2.replace("\"values\":[{\"type\":\"VALUE\",\"value\":2}]",
                                              "\"values\":[]")));
      assertRefused(m, leaves(G1_TOP2, G2_TOP1.replace("and", "or")));
   }

   @Test
   void aRefusalLeavesTheStoredRankingUntouched() throws Exception {
      Worksheet ws = worksheet();
      TableAssembly m = table(ws, "M");
      service().applyNestedRankings(m, leaves(G1_TOP2));
      ConditionListWrapper stored = m.getRankingConditionList();

      assertRefused(m, leaves(G1_TOP2, G2_TOP1.replace("\"G2\"", "\"G1\"")));
      assertSame(stored, m.getRankingConditionList());
      assertEquals(6, rows(ws, "M").size());
   }

   // -- fixtures ---------------------------------------------------------------------------------

   private static void assertRefused(TableAssembly t, List<WorksheetTable.ConditionItem> items) {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service().applyNestedRankings(t, items));
      assertTrue(ex.getMessage().contains("rankingCondition"), ex.getMessage());
   }

   private static List<WorksheetTable.ConditionItem> leaves(String... leafJson) throws Exception {
      return request("{\"rankingCondition\":[" + String.join(",", leafJson) + "]}")
         .getRankingCondition();
   }

   private static WorksheetTable request(String json) throws Exception {
      return MAPPER.readValue(json, WorksheetTable.class);
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
         List.of(new AggregateSpec("Q", "Sum", "Total")));

      // a mirror of the grouped table has its own, empty AggregateInfo
      mirror(ws, "P", m);
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

   private static List<List<Object>> rows(Worksheet ws, String name) throws Exception {
      AssetQuerySandbox box = new AssetQuerySandbox(ws);

      try {
         TableLens lens = box.getTableLens(name, AssetQuerySandbox.RUNTIME_MODE);
         assertNotNull(lens);
         List<List<Object>> rows = new ArrayList<>();

         for(int r = 1; lens.moreRows(r); r++) {
            List<Object> row = new ArrayList<>();

            for(int c = 0; c < lens.getColCount(); c++) {
               row.add(lens.getObject(r, c));
            }

            rows.add(row);
         }

         return rows;
      }
      finally {
         box.dispose();
      }
   }
}
