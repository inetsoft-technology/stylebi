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

import inetsoft.report.composition.execution.AssetQuerySandbox;
import inetsoft.report.composition.execution.SQLBoundQuery;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XUtil;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

/**
 * Bug #78382, a SQL Server table hint ({@code from t with (nolock)}) failed the parse since
 * Bug #77492, so a VPM-restricted user got "Query rejected by VPM". The hint is now kept on the
 * {@link SelectTable} and written back after the alias by the SQL Server and Sybase helpers, at
 * every query level. A non-T-SQL data source, or no data source, still refuses it.
 * <p>
 * No SQL Server is reachable from the tests. The rows are compared on Derby and HSQLDB with
 * the hint removed from the original and the regenerated sql, and the regenerated sql is
 * checked to hold every hint in its place. With H2 on the test classpath
 * ({@code -Dmaven.test.additionalClasspath}), the hinted sql itself also runs on H2 in its
 * MSSQLServer mode, which accepts and ignores table hints.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLTableHintRepresentTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLTableHintRepresentTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
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

   private static final String DERBY_URL = "jdbc:derby:memory:bug78382";
   private static final String HSQLDB_URL = "jdbc:hsqldb:mem:bug78382";
   private static final String H2_URL = "jdbc:h2:mem:bug78382;MODE=MSSQLServer;DB_CLOSE_DELAY=-1";

   // the dialects that keep the hint
   private static final String[] TSQL = { "sql server", "sql server-ansi", "sybase" };
   // the dialects that refuse it
   private static final String[] NON_TSQL = { "postgresql", "mysql", "oracle", "derby" };

   // with (nolock) at every query level and in every from clause position
   private static final String[] POSITIONS = {
      // the reported shape and qualified columns, which the alias "with(nolock)" broke
      "select x from t with (nolock)",
      "select a.id from a with (nolock)",
      "select id, k from a with (nolock)",
      "select a.id from a WITH (NOLOCK) where a.k > 100 order by a.id",
      "select a.id from a WiTh(NoLock)",
      "select a.id from a with ( nolock )",
      "select a.id from a with/*c*/(nolock)",
      "select a.id from a with -- c\n (nolock)",
      "select distinct a.k from a with (nolock)",
      // quoted and qualified table names
      "select id from [a] with (nolock)",
      "select id from \"a\" with (nolock)",
      "select a.id from dbo.a with (nolock)",
      "select id from dbo.[a] with (nolock)",
      "select id from db.dbo.a with (nolock)",
      // aliased tables
      "select x.id from a x with (nolock)",
      "select x.id from a as x with (nolock)",
      "select x.id, y.k from a x with (nolock) join b y with (nolock) on x.id = y.id",
      // comma lists
      "select a.id, b.k from a with (nolock), b where a.id = b.id",
      "select a.id, b.k from a, b with (nolock) where a.id = b.id",
      "select a.id, b.k from a with (nolock), b with (nolock) where a.id = b.id",
      // joins, the hint on either operand
      "select a.id, b.k from a with (nolock) join b on a.id = b.id",
      "select a.id, b.k from a join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a with (nolock) inner join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a with (nolock) left join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a left outer join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a right join b with (nolock) on a.id = b.id",
      "select a.id, b.k from a with (nolock) full outer join b on a.id = b.id",
      "select a.id, b.k from a with (nolock) cross join b",
      "select a.id, b.k from a cross join b with (nolock)",
      // nested joins
      "select a.id, b.k from (a join b with (nolock) on a.id = b.id)",
      "select a.id, b.k from (a with (nolock) join b on a.id = b.id)",
      "select a.id, c.k from ((a join b with (nolock) on a.id = b.id) join c on b.id = c.id)",
      "select a.id, c.k from a join (b join c with (nolock) on b.id = c.id) on a.id = b.id",
      // subqueries in IN, EXISTS, the select list, HAVING, ORDER BY and ON
      "select id from a where id in (select id from b with (nolock))",
      "select a.id from a where a.id in (select b.id from b with (nolock))",
      "select b.id from b where b.k in (select b.k from b with (nolock) where b.id > 1)",
      "select a.id from a where exists (select 1 from b with (nolock) where b.id = a.id)",
      "select a.id, (select max(b.k) from b with (nolock) where b.id = a.id) m from a",
      "select a.k from a where a.k = (select max(a.k) from a with (nolock))",
      "select a.k, count(*) from a group by a.k having count(*) > " +
         "(select count(*) from c with (nolock))",
      "select a.id, b.k from a join b on a.id = b.id and b.k in " +
         "(select c.k from c with (nolock))",
      // derived tables: the body, a nested body and a join operand
      "select t.id from (select b.id from b with (nolock)) t",
      "select x.id from (select t.id from (select b.id from b with (nolock)) t) x",
      "select a.id, t.k from a join (select id, k from b with (nolock)) t on a.id = t.id",
   };

   static String[] positions() {
      return POSITIONS;
   }

   // every position parses on the T-SQL dialects, keeps each hint in its place, and
   // regenerates to itself
   @ParameterizedTest
   @MethodSource("positions")
   void hintIsKeptAndWrittenBack(String text) throws Exception {
      for(String type : TSQL) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), type + ": " + text);

         String regenerated = regenerate(sql);
         assertEquals(count(text), count(regenerated, "WITH (nolock)", "WITH (NOLOCK)",
                                         "WITH (NoLock)"), type + ": " + regenerated);
         assertFalse(regenerated.contains("\"with"), type + ": " + regenerated);
         assertFalse(regenerated.contains("--") || regenerated.contains("/*"),
                     type + ": " + regenerated);
         assertRoundTrip(regenerated, ds, type);

         // the hint adds nothing else: without it the sql is what the same query without the
         // hint regenerates (the join order, aliases and conditions are not changed)
         assertEquals(regenerate(parse(stripHints(text), ds)), stripHints(regenerated),
                      type + ": " + text);

         // mergeable as the same query without the hint
         assertEquals(XUtil.isQueryMergeable(query(parse(stripHints(text), ds), ds)),
                      XUtil.isQueryMergeable(query(sql, ds)), type + ": " + text);

         // the xml save of the query and its derived tables keeps the hints
         UniformSQL loaded = load(toXML(sql), ds);
         assertEquals(regenerated, regenerate(loaded), type + ": " + text);
      }

      // the production (asynchronous) parse with the data source of the query
      UniformSQL async = parseAsync(text, dataSource("sql server"));
      assertEquals(UniformSQL.PARSE_SUCCESS, async.getParseResult(), text);
      assertTrue(XUtil.isParsedSQL(async), text);
   }

   // the regenerated sql returns the rows of the original. Derby and HSQLDB have no table
   // hints, so both run without them; the full join runs on HSQLDB only
   @ParameterizedTest
   @MethodSource("positions")
   void regeneratedReturnsOriginalRows(String text) throws Exception {
      String original = stripHints(text);

      for(String type : TSQL) {
         String regenerated = stripHints(regenerate(parse(text, dataSource(type))));

         for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
            try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
               List<String> expected;

               try {
                  expected = rows(stmt, original);
               }
               catch(SQLException ex) {
                  // a shape this database doesn't run ([a], dbo.a, full join on Derby)
                  continue;
               }

               assertEquals(expected, rows(stmt, regenerated),
                            type + " on " + url + ": " + regenerated);
            }
         }
      }
   }

   // random statements, hints on random tables of joins, comma lists, nested joins, derived
   // tables and subqueries. Each regenerates as the statement without the hints does, plus
   // its hints, and returns the original rows (with the hints on H2 when it's available)
   @Test
   void fuzzedStatements() throws Exception {
      Random random = new Random(78382);
      List<String> statements = new ArrayList<>();

      for(int i = 0; i < 400; i++) {
         statements.add(randomStatement(random));
      }

      List<String> urls = new ArrayList<>(List.of(DERBY_URL, HSQLDB_URL));

      if(isH2Available()) {
         urls.add(H2_URL);
      }

      Map<String, Connection> conns = new HashMap<>();

      try {
         for(String url : urls) {
            conns.put(url, connect(url));
         }

         int compared = 0;

         for(String text : statements) {
            if(count(text) == 0) {
               continue;
            }

            for(String type : TSQL) {
               JDBCDataSource ds = dataSource(type);

               // a statement refused without the hints (e.g. Bug #77495) is refused with them
               try {
                  parse(stripHints(text), ds);
               }
               catch(Exception ex) {
                  assertThrows(Exception.class, () -> parse(text, ds), type + ": " + text);
                  continue;
               }

               UniformSQL sql = parse(text, ds);
               assertFalse(sql.isLossy(), type + ": " + text);
               String regenerated = regenerate(sql);
               assertEquals(count(text), count(regenerated, "WITH (nolock)"),
                            type + ": " + regenerated);
               assertEquals(regenerate(parse(stripHints(text), ds)), stripHints(regenerated),
                            type + ": " + text);
               assertRoundTrip(regenerated, ds, type);

               for(String url : urls) {
                  boolean hints = url.equals(H2_URL);

                  try(Statement stmt = conns.get(url).createStatement()) {
                     List<String> expected;

                     try {
                        expected = rows(stmt, hints ? text : stripHints(text));
                     }
                     catch(SQLException ex) {
                        // a shape this database doesn't run (e.g. a full join on Derby)
                        continue;
                     }

                     assertEquals(expected, rows(stmt, hints ? regenerated :
                                                    stripHints(regenerated)),
                                  type + " on " + url + ": " + text + " => " + regenerated);
                     compared++;
                  }
               }
            }
         }

         assertTrue(compared > 1000, "compared " + compared);
      }
      finally {
         for(Connection conn : conns.values()) {
            conn.close();
         }
      }
   }

   private static String randomStatement(Random random) {
      String[] tables = { "a", "b", "c" };
      String[] joins = { "join", "inner join", "left join", "left outer join", "right join",
                         "full outer join" };
      int n = 1 + random.nextInt(3);
      List<String> aliases = new ArrayList<>();
      StringBuilder from = new StringBuilder();
      boolean comma = n > 1 && random.nextInt(4) == 0;
      List<String> where = new ArrayList<>();

      for(int i = 0; i < n; i++) {
         String alias = "t" + i;
         String item = randomTable(random, tables[random.nextInt(tables.length)], alias, 0);
         aliases.add(alias);

         if(i == 0) {
            from.append(item);
         }
         else if(comma) {
            from.append(", ").append(item);
            where.add("t" + (i - 1) + ".id = " + alias + ".id");
         }
         else {
            from.append(" ").append(joins[random.nextInt(joins.length)]).append(" ")
               .append(item).append(" on t").append(random.nextInt(i)).append(".id = ")
               .append(alias).append(".id");

            // a nested (parenthesized) left operand
            if(random.nextInt(5) == 0) {
               from.insert(0, "(").append(")");
            }
         }
      }

      if(random.nextInt(3) == 0) {
         String inner = randomTable(random, tables[random.nextInt(tables.length)], "s", 0);
         where.add(random.nextBoolean() ?
            "t0.k in (select s.k from " + inner + " where s.id > 1)" :
            "exists (select 1 from " + inner + " where s.id = t0.id)");
      }

      if(random.nextInt(4) == 0) {
         where.add("t0.k > 100");
      }

      return "select t0.id, " + aliases.get(aliases.size() - 1) + ".k from " + from +
         (where.isEmpty() ? "" : " where " + String.join(" and ", where));
   }

   // a table with an alias and maybe a hint, or a derived table with a hinted body
   private static String randomTable(Random random, String table, String alias, int depth) {
      if(depth == 0 && random.nextInt(5) == 0) {
         return "(select d.id, d.k from " + randomTable(random, table, "d", 1) + ") " + alias;
      }

      return table + " " + (random.nextBoolean() ? "as " : "") + alias +
         (random.nextInt(3) > 0 ? " with (nolock)" : "");
   }

   // the hinted sql itself runs on H2 in its MSSQLServer mode, which ignores table hints
   @ParameterizedTest
   @MethodSource("positions")
   void regeneratedReturnsOriginalRowsWithHints(String text) throws Exception {
      assumeTrue(isH2Available(), "H2 is not on the test classpath");

      try(Connection conn = connect(H2_URL); Statement stmt = conn.createStatement()) {
         List<String> expected;

         try {
            expected = rows(stmt, text);
         }
         catch(SQLException ex) {
            assumeTrue(false, "H2 doesn't run " + text + ": " + ex.getMessage());
            return;
         }

         for(String type : TSQL) {
            String regenerated = regenerate(parse(text, dataSource(type)));
            assertEquals(expected, rows(stmt, regenerated), type + ": " + regenerated);
         }
      }
   }

   // the forms of a hint, each stored as rebuilt from its tokens and written back as stored
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select a.id from a with (nolock)|nolock",
      "select a.id from a WITH (NOLOCK)|NOLOCK",
      "select a.id from a with (nolock, readpast)|nolock, readpast",
      "select a.id from a with (nolock,readpast)|nolock, readpast",
      "select a.id from a with (updlock holdlock)|updlock holdlock",
      "select a.id from a with (updlock, holdlock rowlock)|updlock, holdlock rowlock",
      "select a.id from a with (index(ix))|index(ix)",
      "select a.id from a with (index (ix1, ix2))|index(ix1, ix2)",
      "select a.id from a with (index(0))|index(0)",
      "select a.id from a with (index = ix)|index = ix",
      "select a.id from a with (index=ix, nolock)|index = ix, nolock",
      "select a.id from a with (forceseek)|forceseek",
      "select a.id from a with (forceseek(ix(c1, c2)))|forceseek(ix(c1, c2))",
      "select a.id from a with (spatial_window_max_cells = 512)|spatial_window_max_cells = 512",
      "select a.id from a with (nolock, index([ix a]))|nolock, index([ix a])",
      "select a.id from a with (index(\"ix a\"))|index(\"ix a\")",
      // quoted names keep their escapes, so the bracket isn't closed early
      "select a.id from a with (index([x]]y]))|index([x]]y])",
      "select a.id from a with (index(\"x\"\"y\"))|index(\"x\"\"y\")",
   })
   void hintForms(String text, String hint) throws Exception {
      for(String type : TSQL) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(text, ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertEquals(hint, sql.getSelectTable(0).getHint(), type + ": " + text);
         assertEquals("a", sql.getSelectTable(0).getName(), type + ": " + text);

         String regenerated = regenerate(sql);
         assertTrue(regenerated.endsWith("from a WITH (" + hint + ")"),
                    type + ": " + regenerated);
         assertFalse(regenerated.contains("where"), type + ": " + regenerated);
         assertRoundTrip(regenerated, ds, type);

         UniformSQL loaded = load(toXML(sql), ds);
         assertEquals(hint, loaded.getSelectTable(0).getHint(), type + ": " + text);
      }
   }

   // comments are dropped by the lexer, they can't hide a parenthesis or text in the hint
   @Test
   void commentsInHint() throws Exception {
      hintForms("select a.id from a with (nolock -- c\n)", "nolock");
      hintForms("select a.id from a with (nolock /* ) where 1=1 */)", "nolock");
      hintForms("select a.id from a with (/* c */ nolock, -- c\n readpast)", "nolock, readpast");
      hintForms("select a.id from a with (nolock -- ) where 1=1\n)", "nolock");
   }

   // a hint that isn't one of the token forms above is a syntax error, so the sql runs as
   // written
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.id from a with ('x')",
      "select a.id from a with (select 1)",
      "select a.id from a with (nolock) where 1 = 1) --",
      "select a.id from a with (index(ix(c1(d))))",
      "select a.id from a with ()",
      "select a.id from a with (nolock",
      "select a.id from a with (index = 'x')",
      "select a.id from a with (a.b)",
      "select a.id from a with (nolock) option (maxdop 1)",
      // the hint before the alias, and the old form without with
      "select x.id from a with (nolock) x",
      "select x.id from a x (nolock)",
   })
   void malformedHintFailsParse(String text) {
      for(String type : TSQL) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(dataSource(type));
         assertThrows(Exception.class,
            () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), type + ": " + text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
      }
   }

   // a hint on a derived table (not valid T-SQL) and the alias with with a column list keep
   // the refusal of Bug #77492
   @ParameterizedTest
   @ValueSource(strings = {
      "select id from (select id from b) with (nolock)",
      "select id from (select id from b) as with (nolock)",
      "select a.id from a join (select id from b) with (nolock) on a.id = 1",
      "select id, k from a as with (nolock)",
      "select id from a as with (nolock, readpast)",
   })
   void derivedTableHintIsRefused(String text) {
      for(String type : TSQL) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(dataSource(type));
         Exception ex = assertThrows(Exception.class,
            () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), type + ": " + text);
         assertTrue(ex.getMessage().contains("Unsupported table hint: "),
                    type + ": " + ex.getMessage());
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
      }
   }

   // a dialect without table hints, or an unknown dialect, refuses the hint at every level,
   // so the sql runs as written
   @ParameterizedTest
   @MethodSource("positions")
   void otherDialectRefusesHint(String text) {
      List<String> types = new ArrayList<>(Arrays.asList(NON_TSQL));
      types.add(null);

      for(String type : types) {
         UniformSQL sql = new UniformSQL();
         sql.setDataSource(type == null ? null : dataSource(type));
         Exception ex = assertThrows(Exception.class,
            () -> sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD), type + ": " + text);
         assertTrue(ex.getMessage().contains("Unsupported table hint: "),
                    type + ": " + ex.getMessage());
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type + ": " + text);
      }
   }

   // the hint is never written by a helper of a dialect without table hints, e.g. when the
   // data source of a saved query is changed
   @Test
   void otherDialectDropsHint() throws Exception {
      String text = "select a.id from a with (nolock) where a.id in " +
         "(select b.id from b with (nolock)) and exists (select 1 from " +
         "(select c.id from c with (nolock)) t where t.id = a.id)";
      UniformSQL sql = parse(text, dataSource("sql server"));
      assertEquals(3, count(regenerate(sql), "WITH (nolock)"), regenerate(sql));

      for(String type : NON_TSQL) {
         UniformSQL copy = sql.clone();
         copy.setDataSource(dataSource(type));
         String regenerated = regenerate(copy);
         assertFalse(regenerated.toLowerCase(Locale.ROOT).contains("with"),
                     type + ": " + regenerated);
      }

      // with no data source (the plan text), the hint is written
      UniformSQL copy = sql.clone();
      copy.setDataSource(null);
      assertEquals(3, count(regenerate(copy), "WITH (nolock)"), regenerate(copy));
   }

   // the hint of an aliased table follows its alias, and a qualified column uses the alias
   @Test
   void aliasedTable() throws Exception {
      for(String type : TSQL) {
         UniformSQL sql = parse("select x.id from dbo.a as x with (nolock) where x.k > 1",
                                dataSource(type));
         assertEquals("x", sql.getSelectTable(0).getAlias());
         assertEquals("dbo.a", sql.getSelectTable(0).getName());
         assertEquals("nolock", sql.getSelectTable(0).getHint());
         assertTrue(regenerate(sql).contains("from dbo.a x WITH (nolock) where"),
                    type + ": " + regenerate(sql));
      }
   }

   // the input max rows wraps a table as (select top n * from t), the hint goes inside
   @ParameterizedTest
   @ValueSource(strings = { "sql server", "sql server-ansi" })
   void maxRowsKeepsHintInsideWrapper(String type) throws Exception {
      JDBCDataSource ds = dataSource(type);
      String[][] cases = {
         { "select a.id from a with (nolock)",
           "from (select top 10 * from a WITH (nolock)) a" },
         { "select x.id from a x with (nolock)",
           "from (select top 10 * from a WITH (nolock)) x" },
         { "select a.id, b.k from a with (nolock) join b with (nolock) on a.id = b.id",
           "(select top 10 * from b WITH (nolock)) b" },
         { "select a.id from a where a.id in (select b.id from b with (nolock))",
           "(select top 10 * from b WITH (nolock)) b" },
      };

      for(String[] c : cases) {
         UniformSQL sql = parse(c[0], ds);
         sql.clearSQLString();
         sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "10");
         String regenerated = normalize(sql.getSQLString()).replace("( ", "(");
         assertTrue(regenerated.contains(c[1]), type + ": " + regenerated);
         assertFalse(Pattern.compile("\\) \\w+ WITH \\(").matcher(regenerated).find(),
                     type + ": " + regenerated);
         assertEquals(count(c[0]), count(regenerated, "WITH (nolock)"), regenerated);
      }
   }

   // Sybase has no input max rows wrapper, the hint follows the table
   @Test
   void maxRowsOnSybase() throws Exception {
      UniformSQL sql = parse("select a.id from a with (nolock)", dataSource("sybase"));
      sql.clearSQLString();
      sql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "10");
      String regenerated = normalize(sql.getSQLString());
      assertTrue(regenerated.contains("from a WITH (nolock)"), regenerated);
      assertFalse(regenerated.contains("top 10 * from"), regenerated);
   }

   // the hint is saved as the tableHint attribute of the table name. Xml without it (saved
   // before) has no hint, and the structure comparison sees the hint
   @Test
   void xmlRoundTripAndEquality() throws Exception {
      JDBCDataSource ds = dataSource("sql server");
      UniformSQL sql = parse("select a.id from a with (index([x]]y]), nolock) " +
                             "where a.id in (select b.id from b with (nolock))", ds);
      String xml = toXML(sql);
      assertTrue(xml.contains(" tableHint=\"index([x]]y]), nolock\""), xml);

      UniformSQL loaded = load(xml, ds);
      assertEquals("index([x]]y]), nolock", loaded.getSelectTable(0).getHint());
      assertEquals(regenerate(sql), regenerate(loaded));

      UniformSQL other = loaded.clone();
      assertEquals("index([x]]y]), nolock", other.getSelectTable(0).getHint());
      assertTrue(other.equalsStructure(loaded));
      other.getSelectTable(0).setHint("nolock");
      assertFalse(other.equalsStructure(loaded));
      assertNotEquals(other, loaded);

      // xml saved before the hint was kept
      UniformSQL old = load(xml.replaceAll(" tableHint=\"[^\"]*\"", ""), ds);
      assertNull(old.getSelectTable(0).getHint());

      // a hint with xml characters is escaped
      SelectTable table = sql.getSelectTable(0);
      table.setHint("index(\"a<&>b\")");
      xml = toXML(sql);
      assertEquals("index(\"a<&>b\")", load(xml, ds).getSelectTable(0).getHint(), xml);
   }

   // a table renamed in place (setTable) keeps its hint only for the same name, as its quoted
   // segments
   @Test
   void setTableKeepsHintForSameName() throws Exception {
      UniformSQL sql = parse("select x.id from a x with (nolock)", dataSource("sql server"));
      sql.setTable("x", "a");
      assertEquals("nolock", sql.getSelectTable(0).getHint());
      sql.setTable("x", "b");
      assertNull(sql.getSelectTable(0).getHint());
   }

   // the parse that checks the syntax only (no model, ANTLR guess mode), as in the join
   // lookahead, accepts the hint, and so do the validity checks without a statement
   @ParameterizedTest
   @ValueSource(strings = {
      "select max(a.id) from a with (nolock)",
      "select max(a.id) from a with (nolock) join b on a.id = b.id",
      "select max(a.id) from (a with (nolock) join b on a.id = b.id)",
      "select max(a.id) from a join b with (index(ix), nolock) on a.id = b.id",
      "select max(x.id) from a x with (nolock) left join b y with (nolock) on x.id = y.id",
      "select max(id) from a where id in (select id from b with (nolock))",
      "select max(t.id) from (select b.id from b with (nolock)) t",
   })
   void scalarSubqueryWithHintIsValid(String subquery) throws Exception {
      String exp = "(" + subquery + ")";
      new SQLParser(new SQLLexer(new StringReader(exp))).value_exp();
      assertTrue(XUtil.isSQLExpressionValid(exp), exp);
      assertNotNull(XUtil.parseSQLExpressionSyntax(exp), exp);

      // the same text as the subquery of a statement keeps the hint
      UniformSQL sql = parse("select a.id, " + exp + " m from a", dataSource("sql server"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), exp);
      assertEquals(count(subquery), count(regenerate(sql), "WITH ("), regenerate(sql));
   }

   // the self-referencing IN subquery of Bug #77492 binds the inner columns to the inner b
   @Test
   void inSubqueryReturnsOriginalRows() throws Exception {
      String text = "select b.id from b where b.k in " +
         "(select b.k from b with (nolock) where b.id > 1)";

      for(String type : TSQL) {
         String regenerated = regenerate(parse(text, dataSource(type)));
         assertTrue(regenerated.contains("from b WITH (nolock) where b.id > 1"), regenerated);

         for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
            try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
               assertEquals(List.of("1", "2", "3"), ids(stmt, stripHints(regenerated)),
                            url + ": " + regenerated);
            }
         }
      }
   }

   // the data cache path (JDBCQueryCacheNormalizer) clears the sql string of a parsed query
   // for every user, so the regenerated sql is sent: it must keep the hint
   @ParameterizedTest
   @MethodSource("positions")
   void normalizerSendsHint(String text) throws Exception {
      JDBCDataSource ds = dataSource("sql server");
      UniformSQL sql = parseAsync(text, ds);
      JDBCQuery query = query(sql, ds);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      String sent = query.getSQLAsString();

      // DISTINCT without ORDER BY runs as written on this path
      if(text.contains("distinct")) {
         assertFalse(normalizer.isClearedSqlString(), text);
         assertEquals(text, sent);
         return;
      }

      assertTrue(normalizer.isClearedSqlString(), text);
      assertEquals(count(text), count(sent, "WITH (nolock)", "WITH (NOLOCK)", "WITH (NoLock)"),
                   sent);
   }

   // the worksheet merge writes the worksheet's condition, sort and aggregate into the query
   // and regenerates it, the hint stays on the table
   @ParameterizedTest
   @ValueSource(strings = { "none", "cond", "sort", "group" })
   void worksheetMergeKeepsHint(String op) throws Exception {
      for(String type : new String[] { "sql server", "sql server-ansi" }) {
         String text = "select x.id, x.k from a x with (nolock) join b y with (nolock) " +
            "on x.id = y.id";
         JDBCDataSource ds = dataSource(type);
         Worksheet ws = new Worksheet();
         SQLBoundTableAssembly table = newTable(ws, text, ds);
         ColumnSelection cols = table.getColumnSelection(false);

         if("cond".equals(op)) {
            AssetCondition cond = new AssetCondition();
            cond.setOperation(XCondition.GREATER_THAN);
            cond.setType(XSchema.INTEGER);
            cond.addValue(1);
            ConditionList list = new ConditionList();
            list.append(new ConditionItem(cols.getAttribute("id"), cond, 0));
            table.setPreConditionList(list);
         }
         else if("sort".equals(op)) {
            SortRef sort = new SortRef(cols.getAttribute("k"));
            sort.setOrder(XConstants.SORT_DESC);
            SortInfo info = new SortInfo();
            info.addSort(sort);
            table.setSortInfo(info);
         }
         else if("group".equals(op)) {
            AggregateInfo info = new AggregateInfo();
            info.addGroup(new GroupRef(cols.getAttribute("k")));
            info.addAggregate(new AggregateRef(cols.getAttribute("id"), AggregateFormula.SUM));
            table.setAggregateInfo(info);
            table.setAggregate(true);
         }

         SQLBoundQuery query = new SQLBoundQuery(
            AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
         query.merge(new VariableTable());
         UniformSQL merged = (UniformSQL) query.getQuery().getSQLDefinition().clone();
         merged.clearSQLString();
         String sent = normalize(merged.getSQLString());
         assertEquals(2, count(sent, "WITH (nolock)"), type + " " + op + ": " + sent);
         assertTrue(sent.contains(" x WITH (nolock)") && sent.contains(" y WITH (nolock)"),
                    type + " " + op + ": " + sent);
      }
   }

   // a query saved when the parser kept the hint as the alias with(nolock) (1.1.x) has that
   // structure and parseResult SUCCESS. Its sql string now parses with the hint, but the
   // stale structure must not be regenerated for an unrestricted user: it drops the hint and,
   // in the IN subquery, binds b.k and b.id to the outer b (Bug #77492). It is lossy, so its
   // sql string is sent
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
      // the original, the structure 1.1.x saved, the rows of the original
      "select b.id from b with (nolock)|select b.id from b \"with(nolock)\"|1,2,3,5",
      "select b.id from b where b.k in (select b.k from b with (nolock) where b.id > 1)|" +
         "select b.id from b where b.k in (select b.k from b \"with(nolock)\" where b.id > 1)|" +
         "1,2,3",
      "select a.id from a where exists (select 1 from b with (nolock) where b.id = a.id)|" +
         "select a.id from a where exists (select 1 from b \"with(nolock)\" where b.id = a.id)|" +
         "1,2,3",
      "select t.id from (select b.id from b with (nolock)) t|" +
         "select t.id from (select b.id from b \"with(nolock)\") t|1,2,3,5",
      "select a.id, (select max(b.k) from b with (nolock) where b.id = a.id) m from a|" +
         "select a.id, (select max(b.k) from b \"with(nolock)\" where b.id = a.id) m from a|" +
         "1,2,3,4",
      "select a.id from a where a.id in (select c.id from c where c.k in " +
         "(select b.k from b with (nolock)))|select a.id from a where a.id in (select c.id " +
         "from c where c.k in (select b.k from b \"WITH(NOLOCK)\"))|2,3",
   })
   void savedAliasModelStaysLossy(String text, String legacy, String ids) throws Exception {
      JDBCDataSource ds = dataSource("sql server");
      UniformSQL old = parse(legacy, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, old.getParseResult(), legacy);
      old.setSQLString(text, false);
      old.setParseResult(UniformSQL.PARSE_SUCCESS);
      String xml = toXML(old);
      assertFalse(xml.contains("tableHint"), xml);

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      loaded.setDataSource(ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, loaded.getParseResult());
      assertTrue(loaded.isLossy(), text);

      JDBCQuery query = query(loaded, ds);
      assertFalse(XUtil.isQueryMergeable(query), text);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      assertFalse(normalizer.isClearedSqlString(), text);
      assertEquals(text, query.getSQLAsString());

      List<String> expected = Arrays.asList(ids.split(","));

      for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
         try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            assertEquals(expected, ids(stmt, stripHints(text)), url + ": " + text);
         }
      }

      // the same query saved with the hint kept is not lossy, and its regenerated sql keeps
      // the hint
      UniformSQL current = parse(text, ds);
      current.setSQLString(text, false);
      UniformSQL saved = new UniformSQL();
      saved.parseXML(Tool.parseXML(new StringReader(toXML(current))).getDocumentElement());
      saved.setDataSource(ds);
      assertFalse(saved.isLossy(), text);
      query = query(saved, ds);
      assertTrue(new JDBCQueryCacheNormalizer(query).isClearedSqlString(), text);
      String sent = query.getSQLAsString();
      assertEquals(1, count(sent, "WITH (nolock)", "WITH (NOLOCK)"), sent);

      for(String url : new String[] { DERBY_URL, HSQLDB_URL }) {
         try(Connection conn = connect(url); Statement stmt = conn.createStatement()) {
            assertEquals(rows(stmt, stripHints(text)), rows(stmt, stripHints(sent)),
                         url + ": " + sent);
         }
      }
   }

   // a lone with alias, a quoted alias with(nolock) written by the user and ordinary
   // aliases are not hints
   @ParameterizedTest
   @ValueSource(strings = {
      "select id from a with",
      "select with.id from a as with",
      "select id from a with where id > 1",
      "select x.id from a x",
      "select a.id, b.k from a join b on a.id = b.id",
   })
   void withoutHintUnchanged(String text) throws Exception {
      for(String type : TSQL) {
         UniformSQL sql = parse(text, dataSource(type));
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), type + ": " + text);
         assertFalse(sql.isLossy(), text);

         for(SelectTable table : sql.getSelectTable()) {
            assertNull(table.getHint(), text);
         }

         assertFalse(regenerate(sql).contains("WITH ("), regenerate(sql));
      }
   }

   // the hint is matched and kept whatever the default locale (dotted capital I in Turkish)
   @Test
   void upperCaseHintInTurkish() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         UniformSQL sql = parse("SELECT A.ID FROM A WITH (NOLOCK, INDEX(IX))",
                                dataSource("sql server"));
         assertEquals("NOLOCK, INDEX(IX)", sql.getSelectTable(0).getHint());
         assertTrue(regenerate(sql).endsWith("WITH (NOLOCK, INDEX(IX))"), regenerate(sql));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // a hinted table in a JOIN USING (not T-SQL, but parsed) keeps its hint, the USING join
   // stays lossy as before
   @Test
   void usingJoinKeepsHint() throws Exception {
      UniformSQL sql = parse("select a.id from a with (nolock) join b using (id)",
                             dataSource("sql server"));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      assertTrue(sql.isLossy());
      assertEquals("nolock", sql.getSelectTable(0).getHint());
      assertTrue(regenerate(sql).contains("from a WITH (nolock)"), regenerate(sql));
   }

   @AfterAll
   static void dropDatabases() throws SQLException {
      try(Connection conn = DriverManager.getConnection(HSQLDB_URL);
          Statement stmt = conn.createStatement())
      {
         stmt.execute("shutdown");
      }

      try {
         DriverManager.getConnection(DERBY_URL + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }

      if(isH2Available()) {
         try(Connection conn = DriverManager.getConnection(H2_URL);
             Statement stmt = conn.createStatement())
         {
            stmt.execute("shutdown");
         }
      }
   }

   private static void assertRoundTrip(String regenerated, JDBCDataSource ds, String type)
      throws Exception
   {
      UniformSQL again = parse(regenerated, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, again.getParseResult(), type + ": " + regenerated);
      assertEquals(regenerated, regenerate(again), type + ": round trip");
   }

   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   // the number of hints in the original text
   private static int count(String text) {
      Matcher matcher = Pattern.compile("(?i)\\bwith\\s*(/\\*[^*]*\\*/|--[^\n]*\n|\\s)*\\(")
         .matcher(text);
      int n = 0;

      while(matcher.find()) {
         n++;
      }

      return n;
   }

   private static int count(String text, String... parts) {
      int n = 0;

      for(String part : parts) {
         for(int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            n++;
         }
      }

      return n;
   }

   // the hints and comments removed, for the databases without table hints
   private static String stripHints(String text) {
      return text.replaceAll("/\\*.*?\\*/", " ").replaceAll("--[^\n]*\n", " ")
         .replaceAll("(?i)\\s*\\bwith\\s*\\(\\s*nolock\\s*\\)", "");
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml, JDBCDataSource ds) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      loaded.setDataSource(ds);
      return loaded;
   }

   private static boolean isH2Available() {
      try {
         Class.forName("org.h2.Driver");
         return true;
      }
      catch(ClassNotFoundException ex) {
         return false;
      }
   }

   // a = (1,100),(2,200),(3,null),(4,100); b = (1,100),(2,200),(3,100),(5,null);
   // c = (2,200),(3,100),(6,300). t is the table of the reported shape
   private static Connection connect(String url) throws SQLException {
      Connection conn = DriverManager.getConnection(
         url + (url.equals(DERBY_URL) ? ";create=true" : ""));

      try(Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "a", "b", "c", "t" }) {
            try {
               stmt.execute("drop table " + table);
            }
            catch(SQLException ignore) {
               // not created yet
            }
         }

         stmt.execute("create table a (id int, k int)");
         stmt.execute("insert into a values (1, 100), (2, 200), (3, null), (4, 100)");
         stmt.execute("create table b (id int, k int)");
         stmt.execute("insert into b values (1, 100), (2, 200), (3, 100), (5, null)");
         stmt.execute("create table c (id int, k int)");
         stmt.execute("insert into c values (2, 200), (3, 100), (6, 300)");
         stmt.execute("create table t (x int, k varchar(10))");
         stmt.execute("insert into t values (1, 'keep'), (2, 'drop'), (3, 'keep')");

         // the quoted ([a], "a") and schema qualified (dbo.a) names of a, on H2
         if(url.equals(H2_URL)) {
            stmt.execute("create schema if not exists dbo");

            for(String table : new String[] { "\"a\"", "dbo.a", "dbo.\"a\"" }) {
               stmt.execute("drop table if exists " + table);
               stmt.execute("create table " + table + " as select * from a");
            }
         }
      }

      return conn;
   }

   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         int n = rs.getMetaData().getColumnCount();

         // the cells of a row are sorted, the regeneration sorts the select list
         while(rs.next()) {
            List<String> cells = new ArrayList<>();

            for(int i = 1; i <= n; i++) {
               cells.add(String.valueOf(rs.getObject(i)));
            }

            Collections.sort(cells);
            rows.add(String.join("|", cells));
         }
      }

      Collections.sort(rows);
      return rows;
   }

   // the first column of a query
   private static List<String> ids(Statement stmt, String query) throws SQLException {
      List<String> ids = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
         while(rs.next()) {
            ids.add(String.valueOf(rs.getObject(1)));
         }
      }

      Collections.sort(ids);
      return ids;
   }

   private static JDBCQuery query(UniformSQL sql, JDBCDataSource ds) {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static JDBCDataSource dataSource(String type) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds_78382_" + type);

      switch(type.replace("-ansi", "")) {
      case "sql server" -> {
         ds.setProductVersion("19.0");
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "sybase" -> {
         ds.setDriver("net.sourceforge.jtds.jdbc.Driver");
         ds.setURL("jdbc:jtds:sybase://localhost/test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost/test");
         ds.setProductVersion("8.0");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:x");
      }
      default -> throw new IllegalArgumentException(type);
      }

      ds.setAnsiJoin(type.endsWith("-ansi"));
      return ds;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   private static UniformSQL parseAsync(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      return sql;
   }

   // a sql bound worksheet table of the query, its columns named by the select list
   private static SQLBoundTableAssembly newTable(Worksheet ws, String text, JDBCDataSource ds)
      throws Exception
   {
      UniformSQL usql = parse(text, ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);
      usql.setSQLString(text, false);

      JDBCQuery query = query(usql, ds);
      query.setName("bug78382");
      query.setUserQuery(true);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();
      XSelection selection = usql.getSelection();

      for(int i = 0; i < selection.getColumnCount(); i++) {
         String path = selection.getColumn(i);
         String name = path.substring(path.lastIndexOf('.') + 1);
         ColumnRef column = new ColumnRef(new AttributeRef(null, name));
         column.setDataType(XSchema.INTEGER);
         columns.addAttribute(column);
      }

      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      return table;
   }
}
