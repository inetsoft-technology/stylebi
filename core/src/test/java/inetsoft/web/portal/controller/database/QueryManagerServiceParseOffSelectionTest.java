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
import inetsoft.uql.XQuery;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.Tool;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;

import java.io.*;
import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77483, a parse-off query whose selection has columns must keep its sql string when the
 * query editor reads its model and when it is saved, instead of being regenerated from the
 * selection as "select x, y" with no from clause.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceParseOffSelectionTest {
   private static final String RID = "rq-77483";
   private static final String TOP_SQL = "select top 10 a.x, a.y from a where a.x > 1 order by a.x";
   private static final String PLAIN_SQL = "select a.x, a.y from a where a.x > 1 order by a.x";
   private static final String FAILED_SQL = "select a.x, a.y from a where a.x ~ 'q'";

   private RuntimeQueryService rqs;
   private XRepository repository;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      repository = mock(XRepository.class);
      qms = new QueryManagerService(rqs, repository, mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   @Test
   void readingTheModelKeepsTheSqlString() throws Exception {
      UniformSQL sql = installParseOffWithSelection(TOP_SQL);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);

      assertTrue(sql.hasSQLString(), "getAdvancedQueryModel must not drop the sql string");
      assertNotEquals(Boolean.TRUE, sql.getHint(UniformSQL.HINT_CLEARED_SQL_STRING, true));
      assertEquals(TOP_SQL, model.getFreeFormSQLPaneModel().getSqlString());
   }

   @Test
   void saveKeepsTopSql() throws Exception {
      UniformSQL sql = installParseOffWithSelection(TOP_SQL);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      qms.updateQuery(RID, model, null, true);

      assertEquals(TOP_SQL, sql.getSQLString());
   }

   @Test
   void saveKeepsPlainSql() throws Exception {
      UniformSQL sql = installParseOffWithSelection(PLAIN_SQL);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      qms.updateQuery(RID, model, null, true);

      assertEquals(PLAIN_SQL, sql.getSQLString());
   }

   @Test
   void saveWithoutSelectionKeepsSql() throws Exception {
      // control: no selection columns while the model is read, as before the fix
      UniformSQL sql = installParseOffWithSelection(TOP_SQL);
      sql.getSelection().clear();
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      addXY(sql);
      qms.updateQuery(RID, model, null, true);

      assertEquals(TOP_SQL, sql.getSQLString());
   }

   @Test
   void reopenedSavedQueryKeepsSql() throws Exception {
      // a saved parse-off query persists its selection columns and sql string
      UniformSQL orig = installParseOffWithSelection(TOP_SQL);
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      orig.writeXML(pw);
      pw.flush();
      Document doc = Tool.parseXML(new StringReader(sw.toString()));
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(doc.getDocumentElement());

      assertFalse(loaded.isParseSQL());
      assertEquals(2, loaded.getSelection().getColumnCount());
      assertEquals(TOP_SQL, loaded.getSQLString());

      // open the dialog and press OK without editing
      runtimeQuery.getQuery().setSQLDefinition(loaded);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      qms.updateQuery(RID, model, null, true);

      assertEquals(TOP_SQL, loaded.getSQLString());
   }

   @Test
   void failedParseThenParseOffKeepsSql() throws Exception {
      // parse fails -> the query runs to get metadata, which fills the selection -> the user
      // unchecks Parse and clicks Parse Now -> OK with sqlEdited=false
      UniformSQL sql = install(FAILED_SQL, true);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      JDBCTableNode node = mock(JDBCTableNode.class);
      when(node.getColCount()).thenReturn(2);
      when(node.getName(0)).thenReturn("x");
      when(node.getName(1)).thenReturn("y");
      when(repository.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenReturn(node);
      Principal p = principal();
      qms.parseSqlString(RID, FAILED_SQL, false, true, p);
      qms.parseSqlString(RID, FAILED_SQL, true, true, p);
      qms.getAdvancedQueryModel(runtimeQuery, p);
      qms.parseSqlString(RID, FAILED_SQL, false, false, p);

      assertFalse(sql.isParseSQL());
      assertEquals(2, sql.getSelection().getColumnCount(), "parse-off query carries selection");
      assertTrue(sql.hasSQLString());

      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, p);
      assertTrue(sql.hasSQLString());
      qms.updateQuery(RID, model, null, true);

      assertEquals(FAILED_SQL, sql.getSQLString());
   }

   private static Principal principal() {
      Principal p = mock(Principal.class);
      when(p.getName()).thenReturn("admin");
      return p;
   }

   private UniformSQL installParseOffWithSelection(String text) throws Exception {
      UniformSQL sql = install(text, false);
      addXY(sql);
      return sql;
   }

   private static void addXY(UniformSQL sql) {
      sql.getSelection().addColumn("x");
      sql.getSelection().addColumn("y");
   }

   private UniformSQL install(String text, boolean parseIt) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(parseIt);

      synchronized(sql) {
         sql.setSQLString(text);

         if(sql.isParseSQL()) {
            sql.wait();
         }
      }

      JDBCQuery query = new JDBCQuery();
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return sql;
   }
}
