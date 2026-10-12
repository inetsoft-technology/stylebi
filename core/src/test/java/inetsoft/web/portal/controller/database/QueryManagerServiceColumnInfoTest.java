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

import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.controller.database.QueryManagerService.DatabaseQueryTabs;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77496 (a): "Get Column Info" must not re-parse or clear the selection of a runtime
 * query that is still parse-on on the server. The "Parse SQL" checkbox is client-only until a
 * parse/save call, so the user can uncheck it, get column info and re-check it while the
 * server query stays parse-on.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, QueryManagerServiceColumnInfoTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceColumnInfoTest {
   private static final String RID = "rq-77496-column-info";
   private static final String PLAIN_SQL = "select a.x, a.y from a where a.x > 1 order by a.x";
   private static final String PLAIN2_SQL = "select a.x, a.y from a where a.x > 2 order by a.x";
   private static final String TOP_SQL = "select top 10 a.x, a.y from a where a.x > 1 order by a.x";
   private static final Principal USER = () -> "admin";

   /**
    * JDBCQuery.getOutputTypeForNonParseableSQL reads the metadata through the static
    * XRepository.getRepository(); answer with two columns, as a database would.
    */
   @Configuration
   static class Config {
      @Bean
      XRepository xRepository() throws Exception {
         XRepository repo = mock(XRepository.class);
         when(repo.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
            XTypeNode node = new XTypeNode("table");
            node.addChild(XSchema.createPrimitiveType("x", Integer.class));
            node.addChild(XSchema.createPrimitiveType("y", Integer.class));
            return node;
         });
         return repo;
      }
   }

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   /**
    * Uncheck Parse SQL, Get Column Info on the unchanged text, re-check, open Fields (no
    * request, the text equals oldSqlString) and leave Fields: the stale client model is posted.
    * The server selection must still match it, so the query keeps its fields.
    */
   @Test
   void parseOnNoEditGetColumnInfoThenFieldsLeaveKeepsQuery() throws Exception {
      UniformSQL sql = install(PLAIN_SQL, true);
      AdvancedSQLQueryModel clientModel = qms.getAdvancedQueryModel(runtimeQuery, null);

      GetColumnInfoResult result = qms.refreshColumnInfo(event(PLAIN_SQL), USER);

      assertTrue(result.isHasColumnInfo());
      assertTrue(sql.isParseSQL());
      assertSql("a.x > 1", sql);
      assertEquals(2, sql.getSelection().getColumnCount());
      assertNull(sql.getColumnInfo());

      qms.updateQuery(RID, clientModel, DatabaseQueryTabs.FIELDS.getTab(), false);

      AdvancedSQLQueryModel refreshed = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertEquals(2, refreshed.getFieldPaneModel().getFields().size());
      String text = refreshed.getFreeFormSQLPaneModel().getSqlString();
      assertNotNull(text);
      assertTrue(text.contains("a.x") && text.contains("a.y") && text.contains("> 1"), text);
   }

   /** Direct path: edit, Get Column Info, then Fields posts save/freeSQLModel with parse on. */
   @Test
   void parseOnEditGetColumnInfoThenParseShowsFields() throws Exception {
      UniformSQL sql = install(PLAIN_SQL, true);

      qms.refreshColumnInfo(event(PLAIN2_SQL), USER);
      // the runtime query is untouched until the next parse/save call
      assertSql("a.x > 1", sql);

      qms.parseSqlString(RID, PLAIN2_SQL, false, true, USER);

      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertEquals(2, model.getFieldPaneModel().getFields().size());
      assertSql("a.x > 2", sql);
   }

   /**
    * Edit to TOP, Get Column Info, re-check, edit back, tick distinct on Fields, leave Fields.
    * The server must not have become lossy/sql-only behind the client, so the edit applies.
    */
   @Test
   void parseOnTopEditGetColumnInfoKeepsFieldsEditable() throws Exception {
      UniformSQL sql = install(PLAIN_SQL, true);
      AdvancedSQLQueryModel clientModel = qms.getAdvancedQueryModel(runtimeQuery, null);

      qms.refreshColumnInfo(event(TOP_SQL), USER);

      assertFalse(sql.isLossy());
      assertFalse(QueryManagerService.isSqlOnly(sql));
      assertSql("a.x > 1", sql);

      clientModel.getFieldPaneModel().setDistinct(true);
      qms.updateQuery(RID, clientModel, DatabaseQueryTabs.FIELDS.getTab(), false);

      assertTrue(sql.isDistinct());
      assertEquals(2, sql.getSelection().getColumnCount());
      assertFalse(sql.getSQLString().toLowerCase().contains("top"), sql.getSQLString());
   }

   /**
    * Client unchecked Parse SQL, got column info and saves with parse off: the column info
    * fetched by Get Column Info is applied by parseSqlString from the runtime metadata.
    */
   @Test
   void parseOnGetColumnInfoThenParseOffSaveAppliesColumnInfo() throws Exception {
      UniformSQL sql = install(PLAIN_SQL, true);

      qms.refreshColumnInfo(event(PLAIN2_SQL), USER);
      qms.parseSqlString(RID, PLAIN2_SQL, false, false, USER);

      assertFalse(sql.isParseSQL());
      assertEquals(PLAIN2_SQL, sql.getSQLString());
      assertColumns(runtimeQuery.getQuery());
   }

   /** Control: on a parse-off runtime query, Get Column Info still stores fresh column info. */
   @Test
   void parseOffGetColumnInfoStoresColumnInfo() throws Exception {
      UniformSQL sql = install(PLAIN_SQL, false);

      GetColumnInfoResult result = qms.refreshColumnInfo(event(PLAIN2_SQL), USER);

      assertTrue(result.isHasColumnInfo());
      assertFalse(sql.isParseSQL());
      assertEquals(PLAIN2_SQL, sql.getSQLString());
      assertNotNull(sql.getColumnInfo());
      assertEquals(2, sql.getColumnInfo().length);
      assertColumns(runtimeQuery.getQuery());
      assertTrue(qms.getAdvancedQueryModel(runtimeQuery, null).getFreeFormSQLPaneModel().isHasColumnInfo());
   }

   /** The parsed sql string may be reformatted, so compare a fragment. */
   private static void assertSql(String fragment, UniformSQL sql) {
      String text = sql.getSQLString();
      assertNotNull(text);
      assertTrue(text.replaceAll("\\s+", " ").contains(fragment), text);
   }

   private static void assertColumns(JDBCQuery query) {
      XTypeNode output = query.getOutputType(null, false);
      assertEquals(2, output.getChildCount());
      assertEquals("x", output.getChild(0).getName());
      assertEquals("y", output.getChild(1).getName());
   }

   private static GetColumnInfoEvent event(String text) {
      GetColumnInfoEvent event = new GetColumnInfoEvent();
      event.setRuntimeId(RID);
      event.setSqlString(text);
      return event;
   }

   private UniformSQL install(String text, boolean parse) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(parse);

      if(parse) {
         synchronized(sql) {
            sql.setSQLString(text);
            sql.wait();
         }
      }
      else {
         sql.setSQLString(text);
      }

      JDBCQuery query = new JDBCQuery();
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return sql;
   }
}
