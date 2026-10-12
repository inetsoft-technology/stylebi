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
package inetsoft.uql.jdbc;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.ColumnCache;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.portal.controller.database.*;
import inetsoft.web.portal.model.database.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77739, the condition pane edit of a query whose where is its joins and a "not (...)"
 * group, through the real QueryManagerService.updateQuery with the model the client posts back
 * as json. The pane saves the lone NOT group as the where root and adds the joins back to it,
 * which put them inside the NOT.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  UniformSQLNegatedRootPaneEditTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNegatedRootPaneEditTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String RID = "rq-77739";
   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk from a, b where ";
   private static final String[] HELPERS = { "h2", "h2-ansi", "derby", "derby-ansi" };
   private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

   @BeforeEach
   void setUp() {
      rqs = mock(RuntimeQueryService.class);
      qms = new QueryManagerService(rqs, mock(XRepository.class), mock(DataSourceService.class),
                                    mock(SecurityEngine.class), mock(ColumnCache.class));
   }

   // The parameter is the where, then the where after the first clause in the pane is negated.
   // On main the first four differed in 24 to 29 of 40 datasets; the last two (an OR inside
   // the NOT, a NOT under a plain AND) are controls that were already right.
   @ParameterizedTest
   @ValueSource(strings = {
      "a.id = b.id and not (a.k = 1 and b.k = 2)|" +
         "a.id = b.id and not (not a.k = 1 and b.k = 2)",
      "a.id = b.id and not (a.k = b.k and a.k = 1)|" +
         "a.id = b.id and not (not a.k = b.k and a.k = 1)",
      "a.id = b.id and a.k = b.k and not (a.k = 1 and b.id = 2)|" +
         "a.id = b.id and a.k = b.k and not (not a.k = 1 and b.id = 2)",
      "a.id = b.id and not (not (a.k = 1 and b.k = 2) and a.id < 3)|" +
         "a.id = b.id and not (not (not a.k = 1 and b.k = 2) and a.id < 3)",
      "a.id = b.id and not (a.k = 1 or a.id = 2)|" +
         "a.id = b.id and not (not a.k = 1 or a.id = 2)",
      "a.id = b.id and b.k >= 0 and not (a.k = 1 and b.k = 2)|" +
         "a.id = b.id and not b.k >= 0 and not (a.k = 1 and b.k = 2)",
   })
   void paneEditKeepsTheJoinsOutsideTheNot(String param) throws Exception {
      String[] parts = param.split("\\|");
      String original = SEL + parts[0];
      int joins = parts[0].contains("a.k = b.k and not") ? 2 : 1;

      for(String type : HELPERS) {
         JDBCDataSource ds = SQLHelperWhereOrOuterJoinTest.RowCompare.dataSource(type);
         UniformSQL sql = install(original, ds);
         AdvancedSQLQueryModel model = clientEcho();
         Clause clause = (Clause) model.getConditionPaneModel().getConditions().stream()
            .filter(c -> c instanceof Clause).findFirst().orElseThrow();
         clause.setNegated(!clause.isNegated());

         qms.updateQuery(RID, model, null, true);

         String generated = sql.getSQLString().replaceAll("\\s+", " ").trim();
         assertEquals(joins, sql.getJoins().length, type + ": " + sql.getWhere());
         assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
            SEL + parts[1], generated, 40), type + ": " + generated);
      }
   }

   // the model as the client posts it back: fetched, sent as json and read back
   private AdvancedSQLQueryModel clientEcho() throws Exception {
      AdvancedSQLQueryModel model = qms.getAdvancedQueryModel(runtimeQuery, null);
      String json = MAPPER.writeValueAsString(model);
      return MAPPER.readValue(json, AdvancedSQLQueryModel.class);
   }

   // same as QueryManagerService.parseSqlString: the parse is asynchronous
   private UniformSQL install(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setParseSQL(true);

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait();
      }

      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      sql.setDataSource(ds);
      runtimeQuery = new RuntimeQueryService.RuntimeXQuery(query, RID, ds.getName());
      runtimeQuery.setVariables(new VariableTable());
      when(rqs.getRuntimeQuery(RID)).thenReturn(runtimeQuery);
      return sql;
   }

   private RuntimeQueryService rqs;
   private QueryManagerService qms;
   private RuntimeQueryService.RuntimeXQuery runtimeQuery;
}
