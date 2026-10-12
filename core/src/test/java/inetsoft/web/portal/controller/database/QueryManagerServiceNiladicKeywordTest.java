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
import inetsoft.uql.util.Config;
import inetsoft.util.Plugins;
import inetsoft.util.credential.*;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77763, a niladic keyword-function written unquoted in the select list (current_date) is
 * stored as an expression column. In the advanced query editor it's listed as before, a save
 * of the unchanged panes keeps the typed sql, and a field pane edit keeps the record, so the
 * regenerated sql doesn't quote it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, PluginsTestConfiguration.class,
   QueryManagerServiceNiladicKeywordTest.TestConfig.class
}, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceNiladicKeywordTest {
   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   // the fields are listed by the word as written, as a plain field of no table
   @Test
   void niladicItemIsListedAsWritten() throws Exception {
      typeSql("select current_user, current_timestamp as ts, a.x from a where a.d < current_date");
      AdvancedSQLQueryModel model = clientEcho();
      List<QueryFieldModel> fields = model.getFieldPaneModel().getFields();

      assertEquals("current_user", fields.get(0).getName());
      assertNull(fields.get(0).getQuotedName());
      assertEquals("current_timestamp", fields.get(1).getName());
      assertEquals("ts", fields.get(1).getAlias());
      assertTrue(qms.isExpression(RID, "ts"));

      Column having = model.getGroupingPaneModel().getHavingConditions().getFields().get(0);
      assertEquals("current_user", having.getColumnName());
      assertNull(having.getTableName());
   }

   // a save of the unchanged panes, at once or per tab, keeps the typed sql
   @ParameterizedTest
   @ValueSource(strings = {
      "select current_date, a.x from a",
      "select current_user, current_timestamp as ts, a.x from a where a.d < current_date"
   })
   void unchangedSaveKeepsTypedSql(String text) throws Exception {
      typeSql(text);
      qms.updateQuery(RID, clientEcho(), null, true);
      assertEquals(text, currentSql().getSQLString());

      for(String tab : new String[] { "fields", "conditions", "sort", "grouping" }) {
         qms.updateQuery(RID, clientEcho(), tab, false);
      }

      qms.updateQuery(RID, clientEcho(), null, true);
      assertEquals(text, currentSql().getSQLString());
   }

   // a field pane edit rebuilds the selection, the niladic item stays an expression and isn't
   // quoted when the sql is generated
   @Test
   void fieldPaneEditKeepsKeywordUnquoted() throws Exception {
      typeSql("select current_user, current_timestamp as ts, a.x from a where a.d < current_date");
      AdvancedSQLQueryModel model = clientEcho();
      model.getFieldPaneModel().setDistinct(true);
      qms.updateQuery(RID, model, null, true);

      UniformSQL sql = currentSql();
      assertFalse(sql.hasSQLString());
      JDBCSelection selection = (JDBCSelection) sql.getSelection();
      assertTrue(selection.isExpression(0));
      assertTrue(selection.isExpression(1));
      assertFalse(selection.isExpression(2));
      assertEquals("select distinct current_user, current_timestamp as ts, a.x from a " +
                      "where a.d < current_date", generated(sql));
   }

   // type the sql and leave the sql tab, as in QueryManagerServiceStructureEditTest
   private void typeSql(String text) throws Exception {
      install();
      FreeFormSQLPaneModel pane = new FreeFormSQLPaneModel();
      pane.setSqlString(text);
      pane.setParseSql(true);
      UpdateFreeFormSQLPaneEvent event = new UpdateFreeFormSQLPaneEvent();
      event.setRuntimeId(RID);
      event.setFreeFormSqlPaneModel(pane);
      qms.setFreeFormSQLPaneModel(event, principal());

      UniformSQL sql = currentSql();
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      clientEcho();
      assertEquals(text, currentSql().getSQLString());
   }

   private AdvancedSQLQueryModel clientEcho() throws Exception {
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      return MAPPER.readValue(MAPPER.writeValueAsString(model), AdvancedSQLQueryModel.class);
   }

   private UniformSQL currentSql() {
      return (UniformSQL) runtimeQuery.getQuery().getSQLDefinition();
   }

   // the generated sql on one line, without the sorted select list of the cache normalizer
   private static String generated(UniformSQL sql) {
      sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, true);

      try {
         return sql.getSQLString().replaceAll("\\s+", " ").trim();
      }
      finally {
         sql.setHint(UniformSQL.HINT_WITHOUT_SORTED_SQL, false);
      }
   }

   private static Principal principal() {
      Principal principal = mock(Principal.class);
      when(principal.getName()).thenReturn("admin");
      return principal;
   }

   private void install() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77763");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:memory:test;create=true");
      ds.setProductVersion("10.17");
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds77763");
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
   }

   @Configuration
   static class TestConfig {
      // a real JDBCDataSource creates its credential through the CredentialService bean
      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean()))
            .thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }

      @Bean
      Config config(Plugins plugins) {
         return new Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String RID = "rq-77763";
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
}
