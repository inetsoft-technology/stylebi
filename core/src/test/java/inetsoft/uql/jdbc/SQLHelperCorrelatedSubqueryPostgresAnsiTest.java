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
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77545, with the PostgreSQL helper and the ANSI join option, the correlation of a
 * correlated subquery was dropped (EXISTS ( select 1 from "c")). It is the ANSI join option
 * shape of #77480, fixed there, with a data source that quotes identifiers.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperCorrelatedSubqueryPostgresAnsiTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperCorrelatedSubqueryPostgresAnsiTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      "select a.id ai from a where exists (select 1 from c where c.k = a.k)|" +
         "select \"a\".\"id\" as \"ai\" from \"a\" where EXISTS ( select 1 from \"c\" where " +
         "\"c\".\"k\" = \"a\".\"k\")",
      "select a.id ai from a where not exists (select 1 from c where c.k = a.k)|" +
         "select \"a\".\"id\" as \"ai\" from \"a\" where not (EXISTS ( select 1 from \"c\" " +
         "where \"c\".\"k\" = \"a\".\"k\"))",
      "select a.id ai from a where a.id in (select c.id from c where c.k = a.k)|" +
         "select \"a\".\"id\" as \"ai\" from \"a\" where \"a\".\"id\" IN ( select \"c\".\"id\" " +
         "from \"c\" where \"c\".\"k\" = \"a\".\"k\")",
      "select a.id ai from a left join b on a.id = b.id where " +
         "exists (select 1 from c where c.k = a.k)|" +
         "select \"a\".\"id\" as \"ai\" from \"a\" LEFT OUTER JOIN \"b\" ON \"a\".\"id\" = " +
         "\"b\".\"id\" where EXISTS ( select 1 from \"c\" where \"c\".\"k\" = \"a\".\"k\")",
      "select a.id ai from a, b where a.id = b.id and exists (select 1 from c where c.k = a.k)|" +
         "select \"a\".\"id\" as \"ai\" from \"a\" INNER JOIN \"b\" ON \"a\".\"id\" = " +
         "\"b\".\"id\" where EXISTS ( select 1 from \"c\" where \"c\".\"k\" = \"a\".\"k\")",
      "select x.id ai from a x where exists (select 1 from c y where y.k = x.k)|" +
         "select \"x\".\"id\" as \"ai\" from \"a\" x where EXISTS ( select 1 from \"c\" y " +
         "where \"y\".\"k\" = \"x\".\"k\")",
   })
   void correlationKept(String text, String expected) throws Exception {
      JDBCDataSource ds = dataSource();

      // parsed with the data source, as a query of the data source is
      String generated = generate(text, ds, ds);
      assertEquals(expected, generated);
      assertEquals(generated, generate(generated, ds, ds), "round trip");

      // parsed without it, as the other ANSI join tests do. The correlation's outer column
      // isn't quoted then (a.k), which is pre-existing quoting for a table outside this level
      assertEquals(expected.replace("\"", ""),
                   generate(text, GenericJDBCDataSource.create(), ds).replace("\"", ""));
   }

   private static String generate(String text, JDBCDataSource parseSource, JDBCDataSource ds)
      throws Exception
   {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(parseSource);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      sql.setDataSource(ds);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // a data source with a real driver and URL, so the PostgreSQL helper is used
   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_postgresql_ansi");
      ds.setProductVersion("16.0");
      ds.setDriver("org.postgresql.Driver");
      ds.setURL("jdbc:postgresql://localhost:5432/test");
      ds.setAnsiJoin(true);
      assertEquals("postgresql", SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }
}
