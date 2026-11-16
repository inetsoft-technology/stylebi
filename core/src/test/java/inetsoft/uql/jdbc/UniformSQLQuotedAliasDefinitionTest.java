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
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #80212, snowflake and exasol fold an unquoted identifier to upper case, but a reference
 * to a derived table column is always quoted in the case it is stored ({@code "s"."TransDateTime"}).
 * A select alias written unquoted ({@code as TransDateTime}) was folded to TRANSDATETIME, and
 * the reference was an invalid identifier. Every select alias is now quoted in its case, and so
 * is every reference to it. A column of a physical table, the argument of an aggregate, is not
 * an alias and is generated as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLQuotedAliasDefinitionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLQuotedAliasDefinitionTest {
   private static final String[] FOLDING = { "snowflake", "exasol" };
   private static final String DERIVED =
      "select s.TransDateTime from (select t.a as TransDateTime from t) s";

   @Test
   void derivedTableAliasMatchesItsReference() throws Exception {
      for(String key : FOLDING) {
         String generated = regenerate(parse(DERIVED, source(key)));
         assertTrue(generated.contains("as \"TransDateTime\""), key + ": " + generated);
         assertTrue(generated.contains("\"s\".\"TransDateTime\""), key + ": " + generated);
         assertFalse(generated.contains("as TransDateTime"), key + ": " + generated);

         // the quotes the user wrote are kept
         generated = regenerate(parse("select s.\"TransDateTime\" from " +
                                      "(select t.a as \"TransDateTime\" from t) s", source(key)));
         assertTrue(generated.contains("as \"TransDateTime\""), key + ": " + generated);
         assertTrue(generated.contains("\"s\".\"TransDateTime\""), key + ": " + generated);
      }
   }

   @Test
   void everyAliasKindIsQuotedInItsCase() throws Exception {
      String query = "select t.a as TransDateTime, t.b as \"My Col\", t.c as order_total, " +
         "t.d as select1, t.e as lower, t.f as UPPER from t";

      for(String key : FOLDING) {
         String generated = regenerate(parse(query, source(key)));

         for(String alias : new String[] { "TransDateTime", "My Col", "order_total", "select1",
                                           "lower", "UPPER" })
         {
            assertTrue(generated.contains(" as \"" + alias + "\""), key + ": " + generated);
         }

         assertFalse(generated.matches(".* as [^\"].*"), key + ": " + generated);
      }
   }

   @Test
   void orderByAndGroupByNameTheAlias() throws Exception {
      for(String key : FOLDING) {
         String generated = regenerate(
            parse("select t.a as TransDateTime from t order by TransDateTime", source(key)));
         assertTrue(generated.contains("as \"TransDateTime\""), key + ": " + generated);
         assertTrue(generated.endsWith("order by \"TransDateTime\" asc"), key + ": " + generated);

         // an alias of an expression, and a reference from the outer query
         generated = regenerate(parse(
            "select s.g, sum(s.Total) from (select t.g as g, t.a + 1 as Total from t) s " +
            "group by s.g order by s.g", source(key)));
         assertTrue(generated.contains("as \"Total\""), key + ": " + generated);
         assertTrue(generated.contains("sum(s.\"Total\")"), key + ": " + generated);
         assertTrue(generated.contains("as \"g\""), key + ": " + generated);
         assertTrue(generated.contains("group by \"s\".\"g\""), key + ": " + generated);
      }
   }

   /**
    * The sql generated is parsed back as it is, not lossy, and generated again the same.
    */
   @Test
   void generatedSqlParsesBack() throws Exception {
      String[] queries = {
         DERIVED,
         "select t.a as TransDateTime, t.b as \"My Col\" from t order by TransDateTime",
         "select s.g, sum(s.Total) from (select t.g as g, t.a + 1 as Total from t) s group by s.g",
      };

      for(String key : FOLDING) {
         for(String query : queries) {
            String generated = regenerate(parse(query, source(key)));
            // parse asserts the result is a success and not lossy. A quoted order by name that
            // is an alias is read as the column it names, which is the same order
            // (the table alias "s" written quoted and unquoted is refused by the parser, it is
            // not the alias this change is about, so it is written as it is in from)
            String again = regenerate(parse(generated.replace("\"s\".", "s."), source(key)));
            assertEquals(beforeOrderBy(generated), beforeOrderBy(again), key + ": " + query);
         }
      }
   }

   /**
    * The column of a physical table in an aggregate is not an alias, it is not quoted by the
    * alias rule: the column found ignoring case may be another one (MIXEDCASE for MixedCase).
    */
   @Test
   void aggregateArgumentsAreUnchanged() throws Exception {
      String query = "select q.id, sum(q.MixedCase) from t q group by q.id";

      for(String key : FOLDING) {
         assertEquals("select \"q\".\"id\", sum(q.MIXEDCASE) from \"t\" q group by \"q\".\"id\"",
                      regenerate(fixed(key, query, "MIXEDCASE", "MixedCase", "id")), key);
         assertEquals("select \"q\".\"id\", sum(q.MixedCase) from \"t\" q group by \"q\".\"id\"",
                      regenerate(fixed(key, query, "MixedCase", "MIXEDCASE", "id")), key);
         assertEquals("select \"q\".\"id\", sum(q.MixedCase) from \"t\" q group by \"q\".\"id\"",
                      regenerate(fixed(key, query)), key);
         assertEquals("select \"q\".\"id\", sum(q.\"MixedCase\") from \"t\" q group by \"q\".\"id\"",
                      regenerate(fixed(key, "select q.id, sum(q.\"MixedCase\") from t q group by q.id")),
                      key);
      }
   }

   /**
    * The helpers that didn't write the alias unquoted are unchanged.
    */
   @Test
   void otherHelpersAreUnchanged() throws Exception {
      assertEquals("select \"t\".\"a\" as \"TransDateTime\" from \"t\"",
                   regenerate(parse("select t.a as TransDateTime from t", source("postgresql"))));
      assertEquals("select t.a as TransDateTime from t",
                   regenerate(parse("select t.a as TransDateTime from t", source("h2"))));
      assertEquals("select T.A as \"TransDateTime\" from t",
                   regenerate(parse("select t.a as TransDateTime from t", source("oracle"))));
   }

   /**
    * The rows of the sql generated are the rows of the query written, on derby, which folds an
    * unquoted name to upper case as snowflake and exasol do.
    */
   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         DERIVED,
         "select t.a as TransDateTime from t order by TransDateTime",
         "select s.g, sum(s.Total) from (select t.g as g, t.a + 1 as Total from t) s group by s.g",
         "select s.g, count(*) from (select t.g as g, t.a as Total from t) s where s.Total > 1 " +
            "group by s.g",
         "select q.id, sum(q.MixedCase) from t q group by q.id",
      };

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug80212;create=true");
          Statement stmt = conn.createStatement())
      {
         // t is read by the queries as written, "t" by the generated ones
         stmt.execute("create table t (id int, a int, g int, MIXEDCASE int, \"MixedCase\" int)");
         stmt.execute("create table \"t\" (id int, \"a\" int, \"g\" int, MIXEDCASE int, " +
                      "\"MixedCase\" int)");

         for(String table : new String[] { "t", "\"t\"" }) {
            stmt.execute("insert into " + table + " values (1, 1, 1, 10, 100), " +
                         "(2, 2, 1, 20, 200), (3, 3, 2, 30, 300)");
         }

         for(String key : FOLDING) {
            for(String query : queries) {
               List<String> expected = rows(stmt, query);
               String[][] metadata = query.contains("MixedCase") ?
                  new String[][] { {}, { "MIXEDCASE", "MixedCase", "id" },
                                   { "MixedCase", "MIXEDCASE", "id" } } : new String[][] { {} };

               for(String[] columns : metadata) {
                  // the table alias is quoted as the helper writes it, which derby folds
                  // differently from the unquoted alias in from
                  String generated = regenerate(fixed(key, query, columns))
                     .replace("\"q\".\"id\"", "q.id").replace("\"s\".", "s.");
                  assertEquals(expected, rows(stmt, generated),
                               key + " " + Arrays.toString(columns) + ": " + query + " -> " + generated);
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug80212;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   private static String beforeOrderBy(String sql) {
      int index = sql.indexOf(" order by ");
      return index < 0 ? sql : sql.substring(0, index);
   }

   // the rows, with the values of each row sorted, since the generated sql may reorder columns
   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = executeQuery(stmt, query)) {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(row);
            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }

   private static ResultSet executeQuery(Statement stmt, String query) throws SQLException {
      try {
         return stmt.executeQuery(query);
      }
      catch(SQLException ex) {
         throw new SQLException(query, ex);
      }
   }

   // ---- harness (as UniformSQLQuotedAggregateTest) ----

   // the sql regenerated after the metadata step with the columns of every table, or after
   // the parse if there are no columns
   private static UniformSQL fixed(String key, String query, String... columns) throws Exception {
      JDBCDataSource ds = source(key);
      UniformSQL sql = parse(query, ds);

      if(columns.length > 0) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
      }

      return sql;
   }

   // the table metadata is cached by data source name, use a new name each time
   private static JDBCDataSource source(String key) {
      JDBCDataSource ds = (JDBCDataSource) helpers().get(key).clone();
      ds.setName(ds.getName() + "Alias" + RUN + "_" + (++sources));
      return ds;
   }

   private static int sources;
   private static final String RUN = Long.toString(System.nanoTime(), 36);

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false));
      helpers.put("oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x",
                                       "oracle", false));
      helpers.put("postgresql", dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db",
                                           "postgresql", false));
      helpers.put("snowflake", dataSource("net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://x",
                                          "snowflake", true));
      helpers.put("exasol", dataSource("com.exasol.jdbc.EXADriver", "jdbc:exa:x", "exasol", false));
      return helpers;
   }

   private static JDBCDataSource dataSource(String driver, String url, String product, boolean ansiJoin) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds80212" + product + ansiJoin);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(ansiJoin);
      return ds;
   }

   // the column metadata comes from the repository
   private static XRepository repository(String[] columns) throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");

         for(String column : columns) {
            result.addChild(XSchema.createPrimitiveType(column, Integer.class));
         }

         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenAnswer(
            inv -> String.valueOf((Object) inv.getArgument(0)).contains("oracle") ? "oracle" : "H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }
}
