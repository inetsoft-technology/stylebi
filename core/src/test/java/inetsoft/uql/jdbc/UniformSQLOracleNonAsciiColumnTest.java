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
 * Bug #77821, on Oracle a non-ascii column written unquoted as the only argument of a function
 * of a physical table ({@code sum(a.имя)}) was regenerated quoted as written
 * ({@code sum(a."имя")}). Oracle folds the unquoted name to ИМЯ, so the quoted name is another
 * identifier (ORA-00904). It is now emitted as an ascii name is, unquoted unless it must be
 * quoted. A name that must be quoted, and a plain column of the select list, are upper cased as
 * Oracle folds an unquoted name: the simple per-code-point mapping, not String.toUpperCase,
 * which maps ß to SS and ﬀ to FF (select a.ﬀx was A.FFX, ORA-00904).
 * <p>
 * Derby folds with the full mapping, it can't check ß or a ligature. Oracle 23 Free (AL32UTF8)
 * is the reference, measured for the bug: unquoted имя łza ωx a＿b ａｂ ﬀx ŉx ǰx ᾀx ᾳx ςx ıx 𐐨x
 * are stored as ИМЯ ŁZA ΩX A＿B ＡＢ ﬀX ŉX ǰX ᾈX ᾼX ΣX IX 𐐀X, in any session language, and every
 * sql generated here returned the rows of the sql as written there. There is no Oracle in CI.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLOracleNonAsciiColumnTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOracleNonAsciiColumnTest {
   private static final String[] ORACLE = { "oracle", "oracle-ansi" };
   // written unquoted, and the name oracle stores for it
   private static final String[] NAMES = { "имя", "łza", "ωx", "a＿b", "ａｂ", "ﬀx", "ŉx", "ᾀx", "ςx", "ıx" };
   private static final String[] STORED = { "ИМЯ", "ŁZA", "ΩX", "A＿B", "ＡＢ", "ﬀX", "ŉX", "ᾈX", "ΣX", "IX" };

   @Test
   void upperCaseIdentifierIsPerCodePoint() {
      // as oracle stores it, String.toUpperCase(Locale.ROOT) gives SS, FF, ʼN, Ϊ́, ΑΙ
      assertEquals("ß", SQLHelper.upperCaseIdentifier("ß"));
      assertEquals("STRAßE", SQLHelper.upperCaseIdentifier("straße"));
      assertEquals("ﬀX", SQLHelper.upperCaseIdentifier("ﬀx"));
      assertEquals("ŉX", SQLHelper.upperCaseIdentifier("ŉx"));
      assertEquals("ΐX", SQLHelper.upperCaseIdentifier("ΐx"));
      assertEquals("ᾈX", SQLHelper.upperCaseIdentifier("ᾀx"));
      assertEquals("ᾼX", SQLHelper.upperCaseIdentifier("ᾳx"));
      // the same as String.toUpperCase
      assertEquals("ΜX", SQLHelper.upperCaseIdentifier("µx"));
      assertEquals("ŸX", SQLHelper.upperCaseIdentifier("ÿx"));
      assertEquals("ǄX", SQLHelper.upperCaseIdentifier("ǅx"));
      assertEquals("ИМЯ", SQLHelper.upperCaseIdentifier("имя"));
      // a supplementary character, upper cased as one code point, not as two chars
      assertEquals("𐐀X", SQLHelper.upperCaseIdentifier("𐐨x"));
      assertEquals("", SQLHelper.upperCaseIdentifier(""));

      for(String name : new String[] { "id", "a_b$#1", "MixedCase", "file" }) {
         assertEquals(name.toUpperCase(Locale.ROOT), SQLHelper.upperCaseIdentifier(name), name);
      }
   }

   /**
    * Surefire pins en_US. The upper case doesn't depend on the locale, oracle stores ix as IX in
    * a turkish session too.
    */
   @Test
   void upperCaseIdentifierIgnoresTheLocale() {
      Locale locale = Locale.getDefault();

      try {
         for(String tag : new String[] { "tr-TR", "az-AZ" }) {
            Locale.setDefault(Locale.forLanguageTag(tag));
            assertEquals("ID", SQLHelper.upperCaseIdentifier("id"), tag);
            assertEquals("IX", SQLHelper.upperCaseIdentifier("ıx"), tag);
            assertEquals("STRAßE", SQLHelper.upperCaseIdentifier("straße"), tag);
         }
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   /**
    * The reported shape, with no metadata. Was sum(a."имя") etc. With the catalog names as
    * metadata the column is the catalog name, unquoted (was quoted).
    */
   @Test
   void aggregateIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         for(int i = 0; i < NAMES.length; i++) {
            String name = NAMES[i];
            String label = key + " " + name;
            assertEquals("select sum(a." + name + ") from a",
                         aggregate(key, "select sum(a." + name + ") from a"), label);
            assertEquals("select sum(a." + STORED[i] + ") from A a",
                         aggregate(key, "select sum(a." + name + ") from a", "ID", STORED[i]), label);
         }
      }
   }

   /**
    * The other shapes that reach SQLHelper.getValidAggregate: order by, count(distinct) and the
    * inner level of a derived table.
    */
   @Test
   void everyAggregateShapeIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         for(int i = 0; i < NAMES.length; i++) {
            String name = NAMES[i];
            String label = key + " " + name;
            assertEquals("select T.ID, sum(t." + name + ") from a t group by t.id order by sum(t." + name + ") asc",
                         aggregate(key, "select t.id, sum(t." + name + ") from a t group by t.id order by sum(t." +
                                   name + ")"), label);
            // the select list is reordered with the metadata, as for an ascii name
            assertEquals("select sum(t." + STORED[i] + "), t.ID from A t group by t.ID order by sum(t." +
                         STORED[i] + ") asc",
                         aggregate(key, "select t.id, sum(t." + name + ") from a t group by t.id order by sum(t." +
                                   name + ")", "ID", STORED[i]), label);
            assertEquals("select count(distinct t." + name + ") from a t",
                         aggregate(key, "select count(distinct t." + name + ") from a t"), label);
            assertEquals("select max(s.\"x\") from ( select max(t." + name + ") as \"x\" from a t) s",
                         aggregate(key, "select max(s.x) from (select max(t." + name + ") x from a t) s"), label);
         }
      }
   }

   /**
    * A plain column of the select list is upper cased as oracle stores it. Was A.FFX, A.ʼNX,
    * A.J̌X, A.ἈΙX, A.ΑΙX (ORA-00904), also with the metadata, which keeps the folded name.
    */
   @Test
   void selectColumnIsFoldedPerCodePoint() throws Exception {
      String[][] cases = {
         { "ﬀx", "ﬀX" }, { "ŉx", "ŉX" }, { "ǰx", "ǰX" }, { "ᾀx", "ᾈX" }, { "ᾳx", "ᾼX" },
         { "𐐨x", "𐐀X" }, { "имя", "ИМЯ" }, { "ςx", "ΣX" }, { "v", "V" }
      };

      for(String key : ORACLE) {
         for(String[] c : cases) {
            String label = key + " " + c[0];
            assertEquals("select A." + c[1] + " from a", aggregate(key, "select a." + c[0] + " from a"), label);
            assertEquals("select A.ID, A." + c[1] + " from a order by a." + c[0] + " asc",
                         aggregate(key, "select a.id, a." + c[0] + " from a order by a." + c[0]), label);
            assertEquals("select a." + c[1] + " from A a",
                         aggregate(key, "select a." + c[0] + " from a", "ID", c[1]), label);
         }
      }
   }

   /**
    * The reserved word check takes the name as oracle folds it: ﬁle is ﬁLE, not the reserved
    * word FILE, so it isn't quoted ("FILE" names another column). An ascii reserved word is
    * still quoted in upper case.
    */
   @Test
   void reservedWordCheckIsPerCodePoint() throws Exception {
      for(String key : ORACLE) {
         assertEquals("select sum(t.ﬁle) from a t", aggregate(key, "select sum(t.ﬁle) from a t"), key);
         assertEquals("select sum(t.\"FILE\") from a t", aggregate(key, "select sum(t.FILE) from a t"), key);
         assertEquals("select sum(t.\"MODE\") from a t", aggregate(key, "select sum(t.mode) from a t"), key);
      }
   }

   /**
    * Surefire pins en_US, the output is the same in a turkish locale.
    */
   @Test
   void turkishLocale() throws Exception {
      String[] queries = new String[NAMES.length * 2 + 2];

      for(int i = 0; i < NAMES.length; i++) {
         queries[i * 2] = "select sum(a." + NAMES[i] + "), max(a.id) from a";
         queries[i * 2 + 1] = "select a." + NAMES[i] + " from a";
      }

      queries[queries.length - 2] = "select sum(t.ﬁle), sum(t.mode), sum(t.SIZE) from a t";
      queries[queries.length - 1] = "select a.ix, a.ǰx from a order by a.ix";
      List<String> expected = generateAll(queries);
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         assertEquals(expected, generateAll(queries));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   private static List<String> generateAll(String[] queries) throws Exception {
      List<String> list = new ArrayList<>();

      for(String key : ORACLE) {
         for(String query : queries) {
            list.add(aggregate(key, query));
         }
      }

      return list;
   }

   /**
    * The reporter's setup: the sql generated by the oracle helper returns the rows of the sql as
    * written on Derby, which folds an unquoted name to upper case. Was "Column 'A.имя' is not in
    * any table".
    */
   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select sum(a.имя) from a",
         "select sum(a.łza) from a",
         "select sum(a.ωx) from a",
         "select sum(a.v) from a",
         "select a.id, sum(a.имя) from a group by a.id order by sum(a.имя)",
         "select count(distinct t.łza) from a t",
         "select max(s.x) from (select max(a.ωx) x from a) s",
         "select a.имя, a.łza, a.ωx from a order by a.ωx",
      };

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77821;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table a (id int, имя int, łza int, ωx int, v int)");
         stmt.execute("insert into a values (1, 10, 100, 1000, 1), (1, 20, 200, 2000, 2), (2, 30, 300, 3000, 3)");

         for(String key : ORACLE) {
            for(String query : queries) {
               List<String> expected = rows(stmt, query);

               for(String[] metadata : new String[][] { {}, { "ID", "ИМЯ", "ŁZA", "ΩX", "V" } }) {
                  String generated = aggregate(key, query, metadata);
                  assertEquals(expected, rows(stmt, generated),
                               key + " " + Arrays.toString(metadata) + ": " + query + " -> " + generated);
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77821;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   // the rows, with the values of each row sorted (oracle reorders the select list)
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

   // ---- harness (the same as UniformSQLOracleAggregateColumnQuoteTest, #77646) ----

   // the sql regenerated after the metadata step with the columns of every table, or after
   // the parse if there are no columns
   private static String aggregate(String key, String query, String... columns) throws Exception {
      JDBCDataSource ds = source(key);
      UniformSQL sql = parse(query, ds);

      if(columns.length > 0) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
      }

      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   // the table metadata is cached by data source name, also in the sree home of earlier test
   // runs, use a new name each time
   private static JDBCDataSource source(String key) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77821" + key + RUN + "_" + (++sources));
      ds.setDriver("oracle.jdbc.OracleDriver");
      ds.setURL("jdbc:oracle:thin:@localhost:1521:x");
      ds.setRuntimeProductName("oracle");
      // otherwise the oracle helper asks the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(key.endsWith("-ansi"));
      return ds;
   }

   private static int sources;
   private static final String RUN = Long.toString(System.nanoTime(), 36);

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

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      assertEquals(OracleSQLHelper.class, SQLHelper.getSQLHelper(sql).getClass(), text);
      return sql;
   }
}
