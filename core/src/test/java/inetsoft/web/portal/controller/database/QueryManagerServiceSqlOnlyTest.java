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
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.composer.model.ws.*;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import inetsoft.uql.XRepository;
import inetsoft.web.portal.controller.database.QueryManagerService.DatabaseQueryTabs;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77437, the advanced query editor must keep a sql string that its structure cannot
 * represent (a lossy parse such as TOP, a failed or partial parse, or parsing turned off)
 * instead of regenerating it on tab-leave, save, the switch to simple mode or preview.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceSqlOnlyTest {
   private static final String RID = "rq-77437";
   private static final String TOP_SQL = "select top 10 a.x, a.y from a where a.x > 1 order by a.x";
   private static final String PLAIN_SQL = "select a.x, a.y from a where a.x > 1 order by a.x";
   private static final String FAILED_SQL = "select a.x from a where a.x ~ 'q'";

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   @ParameterizedTest
   @EnumSource(value = DatabaseQueryTabs.class, names = { "FIELDS", "CONDITIONS", "SORT", "GROUPING" })
   void tabLeaveKeepsTop(DatabaseQueryTabs tab) throws Exception {
      UniformSQL sql = install(TOP_SQL, true);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertTrue(model.getFreeFormSQLPaneModel().isLossy());

      qms.updateQuery(RID, model, tab.getTab(), false);

      assertTrue(sql.hasSQLString());
      assertEquals(TOP_SQL, sql.getSQLString());
      verify(rqs).saveRuntimeQuery(runtimeQuery);
   }

   @ParameterizedTest
   @ValueSource(strings = { TOP_SQL, FAILED_SQL })
   void saveKeepsUnrepresentableSql(String text) throws Exception {
      UniformSQL sql = install(text, true);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);

      qms.updateQuery(RID, model, null, true);

      assertTrue(sql.hasSQLString());
      assertEquals(text, sql.getSQLString());
   }

   @Test
   void saveKeepsParseOffSql() throws Exception {
      UniformSQL sql = install(TOP_SQL, false);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);

      qms.updateQuery(RID, model, null, true);

      assertTrue(sql.hasSQLString());
      assertEquals(TOP_SQL, sql.getSQLString());
   }

   @Test
   void switchToSimpleKeepsTop() throws Exception {
      install(TOP_SQL, true);
      SQLQueryDialogModel dialog = new SQLQueryDialogModel();
      dialog.setAdvancedModel(qms.getAdvancedQueryModel(runtimeQuery, null));

      BasicSQLQueryModel simple = qms.convertToSimpleQueryModel(dialog, RID);

      assertEquals(TOP_SQL, simple.getSqlString());
      assertTrue(simple.isSqlEdited());
   }

   @Test
   void representableQueryStillRegenerates() throws Exception {
      // control: a full, non-lossy parse is still updated from the graphical panes
      UniformSQL sql = install(PLAIN_SQL, true);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      assertFalse(model.getFreeFormSQLPaneModel().isLossy());
      model.getFieldPaneModel().setDistinct(true);

      qms.updateQuery(RID, model, DatabaseQueryTabs.FIELDS.getTab(), false);

      assertFalse(sql.hasSQLString());
      String generated = sql.getSQLString().replaceAll("\\s+", " ").trim();
      assertTrue(generated.startsWith("select distinct a.x, a.y from a where a.x > 1"), generated);
   }

   @Test
   void sqlOnlyPredicate() throws Exception {
      // also the preview (loadQueryData) gate: only a full non-lossy parse is regenerated
      assertTrue(QueryManagerService.isSqlOnly(parse(TOP_SQL, true)));
      assertTrue(QueryManagerService.isSqlOnly(parse(FAILED_SQL, true)));
      assertTrue(QueryManagerService.isSqlOnly(parse(PLAIN_SQL, false)));
      assertFalse(QueryManagerService.isSqlOnly(parse(PLAIN_SQL, true)));

      UniformSQL partial = parse(PLAIN_SQL, true);
      partial.setParseResult(UniformSQL.PARSE_PARTIALLY);
      assertTrue(QueryManagerService.isSqlOnly(partial));

      UniformSQL structural = parse(PLAIN_SQL, true);
      structural.clearSQLString();
      assertFalse(QueryManagerService.isSqlOnly(structural));
   }

   @Test
   void reparseResetsStaleLossy() throws Exception {
      UniformSQL sql = parse(PLAIN_SQL, true);
      assertFalse(sql.isLossy());
      setSQL(sql, FAILED_SQL);
      assertTrue(sql.isLossy(), "stale false must be re-derived");
      setSQL(sql, TOP_SQL);
      assertTrue(sql.isLossy());
      setSQL(sql, PLAIN_SQL);
      assertFalse(sql.isLossy(), "stale true must be re-derived");
   }

   @Test
   void clearSQLStringResetsLossy() throws Exception {
      UniformSQL sql = parse(TOP_SQL, true);
      assertTrue(sql.isLossy());
      sql.clearSQLString();
      assertFalse(sql.isLossy(), "a cleared structural query must not stay lossy (unmergeable)");
   }

   private UniformSQL install(String text, boolean parseIt) throws Exception {
      UniformSQL sql = parse(text, parseIt);
      JDBCQuery query = new JDBCQuery();
      // a known product name keeps SQLHelper.getSQLHelper() from looking it up through Config
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return sql;
   }

   private static UniformSQL parse(String text, boolean parseIt) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(parseIt);
      setSQL(sql, text);
      return sql;
   }

   // same as QueryManagerService.parseSqlString: the parse is asynchronous
   private static void setSQL(UniformSQL sql, String text) throws Exception {
      synchronized(sql) {
         sql.setSQLString(text);

         if(sql.isParseSQL()) {
            sql.wait();
         }
      }
   }
}
