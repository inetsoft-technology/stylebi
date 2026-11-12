/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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

import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.PluginsTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.XRepository;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Whether the binding of a worksheet may be merged into the SQL of a query
 * ({@link XUtil#isQueryMergeable}). The rules are the ones of the code: the query has to be a
 * JDBC query with a parsed, not lossy uniform sql, may not be on the push down blacklist, may not
 * embed a variable with $(@name), and may not already group or aggregate. The sql with a
 * lossy or unparsed text is covered by the UniformSQL tests; this class covers the other rules.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  XUtilQueryMergeableTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XUtilQueryMergeableTest {
   // a data source with a real driver and URL needs the credential service and the driver types
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   private static final String BLACKLIST = "jdbc.pushdown.blacklist";
   private static final String PLAIN = "select a.x, a.y from a where a.x > 1 order by a.y";

   @AfterEach
   void clearBlacklist() {
      SreeEnv.remove(BLACKLIST);
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("mergeable_ds");
      ds.setDriver("org.h2.Driver");
      ds.setURL("jdbc:h2:mem:xutilquerymergeable");
      return ds;
   }

   private static UniformSQL parse(String text) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(dataSource());
      new SQLProcessor(sql).parse(text);
      return sql;
   }

   private static JDBCQuery query(String name, String text) {
      JDBCQuery query = new JDBCQuery();
      query.setName(name);
      query.setDataSource(dataSource());
      query.setSQLDefinition(parse(text));
      return query;
   }

   @Test
   void plainSelectWithWhereAndOrderIsMergeable() {
      assertTrue(XUtil.isParsedSQL(parse(PLAIN)));
      assertTrue(XUtil.isQueryMergeable(query("q", PLAIN)));
   }

   @Test
   void noQueryOrAnotherKindOfQueryIsNotMergeable() {
      assertFalse(XUtil.isQueryMergeable(null));
      assertFalse(XUtil.isQueryMergeable(mock(inetsoft.uql.XQuery.class)));
   }

   @Test
   void queryWithoutAUniformSqlIsNotMergeable() {
      JDBCQuery query = new JDBCQuery();
      query.setName("q");
      query.setDataSource(dataSource());
      query.setSQLDefinition(mock(SQLDefinition.class));

      assertFalse(XUtil.isQueryMergeable(query));
   }

   // a query that already has a group by keeps its own grouping
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = {
      "select a.x from a group by a.x",
      "select a.x, a.y from a where a.y > 1 group by a.x, a.y"
   })
   void queryWithGroupByIsNotMergeable(String text) {
      assertTrue(XUtil.isParsedSQL(parse(text)), text);
      assertFalse(XUtil.isQueryMergeable(query("q", text)), text);
   }

   // an aggregate column, with or without group by
   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = {
      "select sum(a.x) from a",
      "select count(a.x) from a",
      "select max(a.x), min(a.y) from a",
      "select a.y, avg(a.x) from a group by a.y"
   })
   void queryWithAnAggregateColumnIsNotMergeable(String text) {
      assertTrue(XUtil.isParsedSQL(parse(text)), text);
      assertFalse(XUtil.isQueryMergeable(query("q", text)), text);
   }

   // the star on the blacklist turns the push down off for every query
   @Test
   void starOnTheBlacklistTurnsOffEveryQuery() {
      SreeEnv.setProperty(BLACKLIST, "*");

      assertFalse(XUtil.isQueryMergeable(query("q", PLAIN)));
   }

   // a name on the blacklist (a list separated by commas) turns off that query only
   @Test
   void queryNamedOnTheBlacklistIsNotMergeable() {
      SreeEnv.setProperty(BLACKLIST, "other,q,third");

      assertFalse(XUtil.isQueryMergeable(query("q", PLAIN)));
      assertFalse(XUtil.isQueryMergeable(query("third", PLAIN)));
      assertTrue(XUtil.isQueryMergeable(query("not listed", PLAIN)));
   }

   // the names are compared as a whole, not as a part
   @Test
   void blacklistNameIsNotAPrefixOrAPart() {
      SreeEnv.setProperty(BLACKLIST, "query1");

      assertTrue(XUtil.isQueryMergeable(query("query", PLAIN)));
      assertTrue(XUtil.isQueryMergeable(query("query10", PLAIN)));
      assertFalse(XUtil.isQueryMergeable(query("query1", PLAIN)));
   }

   @Test
   void emptyBlacklistTurnsOffNothing() {
      SreeEnv.setProperty(BLACKLIST, "");

      assertTrue(XUtil.isQueryMergeable(query("q", PLAIN)));
   }

   // $(@name) is replaced in the sql string only: the uniform sql would lose it when merged
   @Test
   void embeddedVariableIsNotMergeable() {
      String text = "select a.x from a where a.name = $(@p)";
      JDBCQuery query = query("q", text);
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();

      assertTrue(sql.hasSQLString(), text);
      assertFalse(XUtil.isQueryMergeable(query), text);
   }

   // a bound variable $(p) is a parameter of the statement and stays mergeable
   @Test
   void boundVariableIsMergeable() {
      JDBCQuery query = query("q", "select a.x from a where a.name = $(p)");

      assertTrue(XUtil.isQueryMergeable(query));
   }

   @Test
   void queryWithoutADataSourceIsNotMergeable() {
      JDBCQuery query = query("q", PLAIN);
      query.setDataSource(null);

      assertFalse(XUtil.isQueryMergeable(query));
   }
}
