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
package inetsoft.web.portal.controller.database;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.credential.*;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77578, the sort pane apply of the query editor removes every order by field and adds
 * the pane's fields again. A field kept in the pane keeps the quoted aggregate record the
 * parser wrote for it, so a direction change of {@code order by sum(q."MixedCase")} keeps the
 * quotes on Snowflake, where a field without the record is generated unquoted. A field
 * written unquoted has no record and is generated as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  QueryManagerServiceSortPaneQuotedAggregateTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceSortPaneQuotedAggregateTest {
   private static final String RID = "rq-77578-sort";
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   @Test
   void directionChangeKeepsTheQuotedOrderBy() throws Exception {
      typeSql("SELECT q.id, sum(q.\"MixedCase\")\n  FROM t q\n GROUP BY q.id\n ORDER BY sum(q.\"MixedCase\")");

      flipSort();

      // was order by sum(q.MixedCase) desc, which snowflake folds to MIXEDCASE
      assertTrue(generated().endsWith("order by sum(q.\"MixedCase\") desc"), generated());
   }

   @Test
   void directionChangeOfAnUnquotedOrderByIsUnchanged() throws Exception {
      typeSql("SELECT q.id, sum(q.MixedCase)\n  FROM t q\n GROUP BY q.id\n ORDER BY sum(q.MixedCase)");

      flipSort();

      assertTrue(generated().endsWith("order by sum(q.MixedCase) desc"), generated());
   }

   // a direction change in the sort pane, applied when the sort tab is left
   private void flipSort() throws Exception {
      AdvancedSQLQueryModel model = clientEcho();
      QuerySortPaneModel pane = model.getSortPaneModel();
      assertEquals(1, pane.getFields().size());
      assertEquals("asc", pane.getOrders().get(0));
      pane.setOrders(new ArrayList<>(List.of("desc")));

      qms.updateQuery(RID, model, "sort", false);
   }

   // type the sql and leave the sql tab: the client posts it and refreshes
   private void typeSql(String text) throws Exception {
      install();
      FreeFormSQLPaneModel pane = new FreeFormSQLPaneModel();
      pane.setSqlString(text);
      pane.setParseSql(true);
      UpdateFreeFormSQLPaneEvent event = new UpdateFreeFormSQLPaneEvent();
      event.setRuntimeId(RID);
      event.setFreeFormSqlPaneModel(pane);
      qms.setFreeFormSQLPaneModel(event, principal());

      assertEquals(UniformSQL.PARSE_SUCCESS, currentSql().getParseResult(), text);
      clientEcho();
   }

   // the sql regenerated from the structure, on one line
   private String generated() {
      UniformSQL sql = currentSql().clone();
      sql.clearSQLString();
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private AdvancedSQLQueryModel clientEcho() throws Exception {
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      return MAPPER.readValue(MAPPER.writeValueAsString(model), AdvancedSQLQueryModel.class);
   }

   private UniformSQL currentSql() {
      return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin");
      return principal;
   }

   // snowflake quotes every segment of a parsed column, so a quoted and an unquoted
   // aggregate have the same text and only the record tells them apart
   private void install() {
      UniformSQL sql = new UniformSQL();
      JDBCQuery query = new JDBCQuery();
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77578sort");
      ds.setDriver("net.snowflake.client.jdbc.SnowflakeDriver");
      ds.setURL("jdbc:snowflake://x");
      ds.setRuntimeProductName("snowflake");
      ds.setProductVersion("10.0");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      sql.setDataSource(ds);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds77578sort");
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
   }

   // a real JDBCDataSource creates its credential through the CredentialService bean
   @Configuration
   static class Config {
      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }
}
