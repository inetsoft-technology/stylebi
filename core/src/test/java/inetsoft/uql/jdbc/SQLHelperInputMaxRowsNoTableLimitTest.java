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

import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #81649. With the input row limit (worksheet live mode), a helper that supports max rows
 * but has no table-limit syntax (mongo, hive, denodo) wrapped the table in a derived table with
 * no text: from ( ) t. The table is now read without the wrapper, the same sql as without the
 * limit; the row limit is applied by Statement.setMaxRows. Helpers with the syntax are
 * unchanged.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperInputMaxRowsNoTableLimitTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperInputMaxRowsNoTableLimitTest {
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

   static Stream<String> products() {
      return Stream.of("mongo", "hive", "denodo");
   }

   static Stream<String> queries() {
      return Stream.of(
         "select t.\"_id\" as \"_id\", t.k as k, t.v as v from t",
         "select t.\"_id\" as \"_id\", t.k as k, t.v as v from t where t.k = 1",
         "select x.k as k from t x",
         "select x.k as k from t x where x.k > 1 order by x.k",
         "select x.k as k from sch.t x",
         "select a.k as k, b.v as v from t a, u b where a.id = b.id",
         "select t.k as k from t where t.k in (select u.k from u)",
         "select t.k as k, count(*) as c from t group by t.k",
         "select \"my coll\".k as k from \"my coll\"");
   }

   static Stream<Arguments> shapes() {
      return products().flatMap(p -> queries().map(q -> Arguments.of(p, q)));
   }

   @ParameterizedTest(name = "{0}: {1}")
   @MethodSource("shapes")
   void noEmptyDerivedTable(String product, String text) throws Exception {
      String plain = generate(product, text, false);
      String limited = generate(product, text, true);

      assertFalse(limited.contains("( )"), limited);
      assertFalse(limited.contains("()"), limited);
      assertEquals(plain, limited);
   }

   // the reported query, exactly
   @ParameterizedTest
   @MethodSource("products")
   void reportedQuery(String product) throws Exception {
      String text = "select t.\"_id\" as \"_id\", t.k as k, t.v as v from t";
      String sql = generate(product, text, true);

      // the quote character depends on the dialect
      assertEquals(generate(product, text, false), sql);
      assertTrue(sql.endsWith(" from t"), sql);
   }

   // a table given as a select string (the select string path of getFromTable)
   @ParameterizedTest
   @MethodSource("products")
   void selectStringTable(String product) throws Exception {
      for(boolean limit : new boolean[] { false, true }) {
         JDBCDataSource ds = dataSource(product);
         UniformSQL usql = new UniformSQL();
         usql.setDataSource(ds);
         usql.addTable("x", "select k from t");
         usql.getSelection().addColumn("x.k");
         usql.clearSQLString();

         if(limit) {
            usql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "5001");
         }

         String sql = normalize(usql.getSQLString());
         assertFalse(sql.contains("( )"), sql);
         assertTrue(sql.contains("select k from t"), sql);
      }
   }

   // control: a helper with the syntax still wraps the table
   @Test
   void postgresStillWrapped() throws Exception {
      String sql = generate("postgresql", "select t.k as k from t", true);
      assertTrue(sql.contains("from ( select * from \"t\" limit 5001) t"), sql);
   }

   private static String generate(String product, String text, boolean limit) throws Exception {
      JDBCDataSource ds = dataSource(product);
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);
      usql.clearSQLString();

      if(limit) {
         usql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "5001");
      }

      return normalize(usql.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug81649-" + product);
      ds.setProductVersion("1.0");
      Class<?> helper;

      switch(product) {
      case "mongo" -> {
         ds.setDriver("mongodb.jdbc.MongoDriver");
         ds.setURL("jdbc:mongo://localhost:27017/test81229");
         helper = MongoHelper.class;
      }
      case "hive" -> {
         ds.setDriver("org.apache.hive.jdbc.HiveDriver");
         ds.setURL("jdbc:hive2://localhost:10000/default");
         helper = HiveHelper.class;
      }
      case "denodo" -> {
         ds.setDriver("com.denodo.vdp.jdbc.Driver");
         ds.setURL("jdbc:vdb://localhost:9999/test");
         ds.setRuntimeProductName("denodo");
         helper = DenodoSQLHelper.class;
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
         ds.setRuntimeProductName("postgresql");
         helper = PostgreSQLHelper.class;
      }
      default -> throw new IllegalArgumentException(product);
      }

      assertEquals(helper, SQLHelper.getSQLHelper(ds).getClass(), product);
      return ds;
   }
}
