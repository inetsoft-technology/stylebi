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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.util.ColumnCache;
import inetsoft.web.portal.model.database.AdvancedSQLQueryModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77736, the query editor couldn't load a query with a truth test, x IS [NOT]
 * TRUE/FALSE/UNKNOWN: the condition pane has no op for the truth value ("Unsupported
 * operation found"). A statement with a truth test fails to parse now (#77735), and the
 * editor builds its panes from the partial tree of a failed parse too, so the parse fails
 * before the truth test is in the where, on or having tree, and the editor loads the query
 * as sql only.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class QueryManagerServiceTruthTestPaneTest {
   private static final String RID = "rq-77736";

   private RuntimeQueryService rqs;
   private QueryManagerService qms;

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a where (a.k = 1) is not true",
      "select a.x from a where a.k = 1 and (a.id = $(p)) is false",
      "select a.x from a where a.k = 1 and not ((a.id = 2) is unknown)",
      "select a.k, count(*) from a where a.k > 0 group by a.k having (count(*) > 1) is true",
      "select a.k, count(*) from a group by a.k having count(*) > 0 and (max(a.id) = 1) is false",
      "select a.x from a join b on a.id = b.id and (a.k = b.k) is not true",
      "select a.x from a join b on a.id = b.id join c on b.id = c.id and (c.k = 1) is true",
      "select a.x from a left join b on a.id = b.id and (b.k = 1) is true",
      "select a.x from a where a.k in (select b.k from b where (b.id = 1) is true)",
      "select t.x from (select a.x from a where (a.k = 1) is true) t",
      "select a.x from a where a.k = 1 order by case when (a.id = 1) is true then 0 else 1 end",
      "select a.x from a where (a.k = 1) is true union select b.id from b",
      "select a.x from a where a.k = 1 union select b.id from b where (b.k = 1) is true",
   })
   void editorLoadsRefusedTruthTest(String text) throws Exception {
      UniformSQL sql = install(text);
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery(sql), null);

      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);

      assertEquals(text, model.getFreeFormSQLPaneModel().getSqlString());
      assertTrue(model.getFreeFormSQLPaneModel().isLossy(), text);
      assertNotNull(model.getConditionPaneModel(), text);
      assertNotNull(model.getGroupingPaneModel().getHavingConditions(), text);
      // the partial tree the panes are built from
      assertFalse(hasTruthTest(sql.getWhere()), text + ": " + sql.getWhere());
      assertFalse(hasTruthTest(sql.getHaving()), text + ": " + sql.getHaving());
      assertEquals(text, sql.getSQLString());
   }

   private static boolean hasTruthTest(XNode node) {
      if(node instanceof XSet set && SQLHelper.isTruthTest(set)) {
         return true;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         if(hasTruthTest(node.getChild(i))) {
            return true;
         }
      }

      return false;
   }

   private UniformSQL install(String text) throws Exception {
      UniformSQL sql = new UniformSQL();

      // same as QueryManagerService.parseSqlString: the parse is asynchronous
      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      return sql;
   }

   private RuntimeQueryService.RuntimeXQuery runtimeQuery(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      // a known product name keeps SQLHelper.getSQLHelper() from looking it up through Config
      JDBCDataSource ds = mock(JDBCDataSource.class);
      when(ds.getRuntimeProductName()).thenReturn("hsql");
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      RuntimeQueryService.RuntimeXQuery runtimeQuery =
         new RuntimeQueryService.RuntimeXQuery(query, RID, "ds");
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return runtimeQuery;
   }
}
