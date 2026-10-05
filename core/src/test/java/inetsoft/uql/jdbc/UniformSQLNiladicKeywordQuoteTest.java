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
import inetsoft.uql.util.XUtil;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77763 (and the rownum part of #77755). A niladic keyword-function written without
 * parens (current_timestamp, current_user, user, ...) or an Oracle pseudo-column (rownum,
 * sysdate, rowid) was regenerated as a quoted identifier, which is a column reference: the
 * query failed, or returned a column of that name instead of the function's value. It was
 * quoted at parse time by {@code quoteDot} (only current_date was exempt, #77664), and at
 * generation time by SQLHelper's bare-keyword branches (which also quoted current_date in the
 * select list and ORDER BY). Now quoteDot doesn't quote such a word written unquoted, and the
 * parser stores it in the select list as an expression (a saved flag), which the generation
 * doesn't quote. A word the user quoted, a table column, an alias named like a keyword, and a
 * select item of a query saved before quoted names were flagged (#77408) stay quoted.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNiladicKeywordQuoteTest {
   private static final String[] WORDS = {
      "current_date", "current_time", "current_timestamp", "localtime", "localtimestamp",
      "current_user", "session_user", "system_user", "user", "rownum", "sysdate", "rowid",
      "CURRENT_TIMESTAMP", "Current_User", "ROWNUM", "SYSDATE"
   };

   private static final String[] SHAPES = {
      "select %s from t",
      "select %s as today from t",
      "select t.id, %s as k from t",
      "select t.id from t where t.d < %s",
      "select t.id from t where t.d < %s - 1",
      "select t.id from t where %s = t.u",
      "select coalesce(%s, t.d) from t",
      "select max(%s) from t",
      "select %s from t order by %s",
      "select %s, count(*) from t group by %s",
      "select t.id from t where t.id in (select s.id from s where s.d < %s)",
      "select x.c from (select %s as c from t) x",
      "select x.c from (select t.id as c from t where t.d < %s) x"
   };

   private static final String[] HELPERS = {
      "generic", "derby", "h2", "oracle", "postgresql", "sql server", "mysql"
   };

   @ParameterizedTest
   @ValueSource(strings = { "generic", "derby", "h2", "oracle", "postgresql", "sql server", "mysql" })
   void niladicKeywordStaysUnquotedInEveryPosition(String type) {
      JDBCDataSource ds = dataSource(type);
      List<String> bad = new ArrayList<>();

      for(String word : WORDS) {
         for(String shape : SHAPES) {
            // see oracleOrderByMatchesByText
            if("oracle".equals(type) && shape.contains("order by") &&
               !word.equals(word.toUpperCase(Locale.ROOT)))
            {
               continue;
            }

            String text = shape.replace("%s", word);
            String generated = regenerate(text, ds);

            if(isQuoted(generated, word) || !containsWord(generated, word)) {
               bad.add(text + " => " + generated);
            }

            // round trip: the regenerated sql regenerates to itself
            String again = regenerate(generated, ds);

            if(!again.equals(generated)) {
               bad.add("round trip " + generated + " => " + again);
            }
         }
      }

      assertEquals(List.of(), bad, type);
   }

   @Test
   void currentDateInSelectListAndOrderByStaysUnquoted() {
      for(String type : HELPERS) {
         JDBCDataSource ds = dataSource(type);
         String generated = regenerate("select current_date as today from t", ds);
         assertFalse(isQuoted(generated, "current_date"), type + ": " + generated);
         assertTrue(Pattern.compile("(?i)select current_date as \"?today\"? from").matcher(generated)
                       .find(), type + ": " + generated);

         // oracle stores an unquoted select item in upper case, see oracleOrderByMatchesByText
         String word = "oracle".equals(type) ? "CURRENT_DATE" : "current_date";
         generated = regenerate("select " + word + " from t order by " + word, ds);
         assertTrue(Pattern.compile("(?i)order by current_date asc").matcher(generated).find(),
                    type + ": " + generated);
      }
   }

   @Test
   void oraclePseudoColumnsStayUnquoted() {
      JDBCDataSource ds = dataSource("oracle");
      // oracle writes an unquoted select item in upper case
      assertEquals("select ROWNUM from t", regenerate("select rownum from t", ds));
      assertEquals("select SYSDATE from dual", regenerate("select sysdate from dual", ds));
      assertEquals("select ROWID from t", regenerate("select rowid from t", ds));
      assertTrue(regenerate("select t.id from t where rownum <= 10", ds).endsWith("where rownum <= 10"));
      assertTrue(regenerate("select t.id from t where t.d < sysdate - 1", ds).endsWith("< sysdate-1"));
      assertTrue(regenerate("select t.id from t where t.d < SYSDATE", ds).endsWith("< SYSDATE"));
      assertTrue(regenerate("select ROWNUM from t order by ROWNUM", ds).endsWith("order by ROWNUM asc"));
   }

   // a word the user quoted is a column, it must stay quoted
   @ParameterizedTest
   @ValueSource(strings = { "generic", "derby", "h2", "oracle", "postgresql", "sql server", "mysql" })
   void userQuotedWordStaysQuoted(String type) {
      JDBCDataSource ds = dataSource(type);
      String q = "mysql".equals(type) ? "`" : "\"";
      String[] texts = {
         "select \"current_timestamp\" from t",
         "select t.\"current_timestamp\" from t",
         "select t.id from t where t.\"current_timestamp\" = 1",
         "select t.id from t where \"current_user\" = 1",
         "select coalesce(\"current_timestamp\", t.d) from t",
         "select \"current_date\" as x from t",
         "select t.id from t order by \"current_user\"",
         "select \"current_user\", count(*) from t group by \"current_user\"",
         "select \"user\" from t",
         "select \"rownum\" from t",
         // a table written quoted is quoted by the keyword rule, the parser doesn't flag it
         "select u.id from \"user\" u",
         "select \"user\".id from \"user\"",
         "select c.id from \"current_date\" c",
         "select s.id from \"user\".s"
      };

      for(String text : texts) {
         String generated = regenerate(text, ds);
         String word = text.replaceAll("^[^\"]*\"([^\"]+)\".*$", "$1");
         // a quoted function argument is kept as written, in double quotes on mysql too
         assertTrue(isQuoted(generated, word), type + ": " + text + " => " + generated);
      }

      String generated = regenerate("select t.id from t order by \"current_user\"", ds);
      assertTrue(generated.endsWith("order by " + q + "current_user" + q + " asc"), generated);
      generated = regenerate("select \"current_user\", count(*) from t group by \"current_user\"", ds);
      assertTrue(generated.endsWith("group by " + q + "current_user" + q), generated);
   }

   // an alias named like a keyword is still quoted, SQLHelper.isKeyword is unchanged
   @ParameterizedTest
   @ValueSource(strings = { "generic", "derby", "h2", "oracle", "postgresql", "sql server", "mysql" })
   void aliasNamedLikeKeywordStaysQuoted(String type) {
      JDBCDataSource ds = dataSource(type);
      String q = "mysql".equals(type) ? "`" : "\"";

      for(String word : new String[] { "current_date", "current_user", "user", "sysdate", "rowid" }) {
         if(!"oracle".equals(type) && (word.equals("sysdate") || word.equals("rowid"))) {
            continue;
         }

         String generated = regenerate("select t.id as " + word + " from t", ds);
         assertTrue(generated.contains(" as " + q + word + q), type + ": " + generated);
      }
   }

   // a qualified name is a column. It is quoted, or on postgresql written as the user wrote it,
   // which is a column there too (any keyword is a column name after a dot)
   @Test
   void qualifiedWordStaysColumn() {
      for(String type : HELPERS) {
         String generated = regenerate("select t.current_user from t", dataSource(type));
         assertTrue(Pattern.compile("(?i)[\"`]?t[\"`]?\\.[\"`]?current_user\\b").matcher(generated)
                       .find(), type + ": " + generated);
         assertTrue("postgresql".equals(type) || isQuoted(generated, "current_user"),
                    type + ": " + generated);
      }
   }

   // a query built by the query editor or a data model qualifies its columns by table, so a
   // column named like a keyword is still quoted
   @ParameterizedTest
   @ValueSource(strings = { "generic", "derby", "h2", "oracle", "postgresql", "sql server", "mysql" })
   void editorBuiltColumnStaysQuoted(String type) {
      JDBCDataSource ds = dataSource(type);
      String q = "mysql".equals(type) ? "`" : "\"";
      String[] words = "oracle".equals(type) ?
         new String[] { "USER", "CURRENT_USER", "ROWID", "SYSDATE", "ROWNUM" } :
         new String[] { "user", "current_user", "current_timestamp" };

      for(String word : words) {
         String generated = normalize(editorQuery(word, ds).getSQLString());
         String col = ("postgresql".equals(type) ? q + "T3" + q : "T3") + "." + q + word + q;

         assertTrue(generated.contains("select " + col + " as " + q + word + q) ||
                    generated.contains(", " + col + " as " + q + word + q), generated);
         assertTrue(generated.contains("where " + col + " = 'a'"), generated);
         assertTrue(generated.contains("group by " + col), generated);
         assertTrue(generated.endsWith("order by " + col + " asc"), generated);
      }
   }

   // a table named like a keyword and written quoted is stored quoted, as the vpm scripts'
   // tables array shows it (VpmHasTableScriptTest). Written unquoted, it is stored as written
   @Test
   void quotedTableNameIsStoredQuoted() {
      for(String type : new String[] { "generic", "derby", "oracle", "sql server" }) {
         JDBCDataSource ds = dataSource(type);
         assertEquals("\"user\"", parse("select u.id from \"user\" u", ds).getSelectTable(0).getName(),
                      type);
         assertEquals("\"current_date\"",
                      parse("select c.id from \"current_date\" c", ds).getSelectTable(0).getName(), type);
      }
   }

   // a table named like a keyword, written quoted, is still read
   @Test
   void quotedTableRunsOnDerby() throws Exception {
      try(Connection con = derby("q77763"); Statement st = con.createStatement()) {
         st.execute("create table \"user\" (id int, name varchar(20))");
         st.execute("insert into \"user\" values (1, 'a')");
         String text = "select u.id, u.name from \"user\" u where u.id = 1";
         String generated = regenerate(text, dataSource("derby"));
         assertEquals(List.of("[1,a]"), rows(con, generated), generated);
      }
   }

   @Test
   void editorBuiltColumnRunsOnDerby() throws Exception {
      try(Connection con = derby("e77763"); Statement st = con.createStatement()) {
         st.execute("create table T3 (id int, \"user\" varchar(20))");
         st.execute("insert into T3 values (1, 'a')");
         st.execute("insert into T3 values (2, 'b')");

         String generated = editorQuery("user", dataSource("derby")).getSQLString();
         assertEquals(List.of("[1,a]"), rows(con, generated), generated);
      }
   }

   @Test
   void isNiladicKeywordFunctionIgnoresCase() {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));

         for(String word : new String[] { "CURRENT_TIME", "LOCALTIME", "SYSTEM_USER", "ROWID",
                                          "current_time", "localtime" })
         {
            assertTrue(XUtil.isNiladicKeywordFunction(word), word);
         }

         assertEquals("select t.id from t where t.d < CURRENT_TIME",
                      regenerate("select t.id from t where t.d < CURRENT_TIME", dataSource("derby")));
      }
      finally {
         Locale.setDefault(locale);
      }

      for(String word : new String[] { "current_date_x", "users", "rownums", "current", "",
                                       "current_role" })
      {
         assertFalse(XUtil.isNiladicKeywordFunction(word), word);
      }

      assertFalse(XUtil.isNiladicKeywordFunction(null));
   }

   // the original and the regenerated sql return the same rows on derby
   @Test
   void regeneratedSqlReturnsSameRowsOnDerby() throws Exception {
      try(Connection con = derby("r77763"); Statement st = con.createStatement()) {
         createTables(st);
         assertSameRows(con, dataSource("derby"), new String[] {
            "select t.id from t where t.d < current_timestamp",
            "select current_timestamp as k from t",
            "select t.id, current_date as today from t",
            "select t.id, current_date as d from t order by current_date, t.id",
            "select t.id from t where t.id in (select s.id from s where s.d < current_timestamp)",
            "select x.c from (select current_date as c from t) x",
            "select coalesce(current_timestamp, t.d) from t",
            "select max(current_timestamp) from t",
            "select current_date as d, count(*) as n from t group by current_date",
            "select t.id from t where t.u = current_user",
            "select t.id from t where t.u = session_user",
            "select t.id from t where t.u = user"
         });
      }
   }

   // a lower case column with the keyword's name exists: the quoted keyword read that column
   // and returned its values instead of the function's, with no error
   @Test
   void keywordNamedColumnIsNotReadOnDerby() throws Exception {
      try(Connection con = derby("c77763"); Statement st = con.createStatement()) {
         createTables(st);
         assertSameRows(con, dataSource("derby"), new String[] {
            "select t2.id, current_timestamp as k from t2",
            "select current_date as today from t2",
            "select t2.id from t2 where current_user = 'APP'",
            "select t2.id from t2 where t2.\"current_timestamp\" < current_timestamp",
            "select t2.id from t2 where t2.\"current_date\" < current_date",
            "select t2.id, \"current_timestamp\" as k from t2"
         });
      }
   }

   // a niladic keyword-function in order by but not in the select list has no record of being
   // written unquoted, it's still quoted as before (known limit, an error, not wrong rows)
   @Test
   void standaloneOrderByIsQuotedAsBefore() {
      assertEquals("select t.id from t order by \"current_date\" asc",
                   regenerate("select t.id from t order by current_date", dataSource("derby")));
   }

   // oracle stores an unquoted select item in upper case, so a lower case order by item doesn't
   // match it and has no record, it's quoted as before (known limit, like a standalone one)
   @Test
   void oracleOrderByMatchesByText() {
      JDBCDataSource ds = dataSource("oracle");
      assertEquals("select ROWNUM from t order by ROWNUM asc",
                   regenerate("select rownum from t order by ROWNUM", ds));
      assertEquals("select ROWNUM from t order by \"rownum\" asc",
                   regenerate("select rownum from t order by rownum", ds));
   }

   // the record is the select column's expression flag, which is saved, so a saved and loaded
   // query is still generated with the keyword-function
   @Test
   void savedQueryKeepsKeywordUnquoted() throws Exception {
      String[][] cases = {
         { "derby", "select t.id, current_timestamp as k from t order by current_timestamp",
           "select current_timestamp as k, t.id from t order by current_timestamp asc" },
         { "derby", "select current_user, count(*) from t group by current_user",
           "select count(*), current_user from t group by current_user" },
         { "oracle", "select sysdate from dual", "select SYSDATE from dual" },
         { "oracle", "select rownum, t.id from t where rownum < 5",
           "select ROWNUM, T.ID from t where rownum < 5" }
      };

      for(String[] c : cases) {
         JDBCDataSource ds = dataSource(c[0]);
         UniformSQL sql = load(save(parse(c[1], ds)), ds);
         sql.clearSQLString();
         assertEquals(c[2], normalize(sql.getSQLString()), c[1]);
      }
   }

   // a query saved before quoted names were flagged (#77408, #77501) stores a quoted "user"
   // column as user with no record. It must still be generated quoted, as a column
   @ParameterizedTest
   @ValueSource(strings = {
      "select \"user\" from logins",
      "select logins.id, \"user\" from logins",
      "select \"current_user\" from logins",
      "select logins.id, \"current_user\" as c from logins",
      "select logins.id from logins order by \"user\" desc",
      "select logins.id, \"user\" from logins order by \"user\" desc",
      "select logins.id from logins order by \"current_user\" desc",
      "select \"user\" from logins where \"user\" = 'bob'",
      "select logins.id from logins where \"current_user\" = 'c2'",
      "select \"user\", count(*) from logins group by \"user\"",
      "select \"current_user\", count(*) from logins group by \"current_user\""
   })
   void legacyQuotedColumnIsNotReadAsKeyword(String text) throws Exception {
      try(Connection con = derby("l77763"); Statement st = con.createStatement()) {
         try {
            st.execute("create table logins (id int, \"user\" varchar(20), " +
                          "\"current_user\" varchar(20))");
            st.execute("insert into logins values (1, 'alice', 'c1')");
            st.execute("insert into logins values (2, 'bob', 'c2')");
         }
         catch(SQLException ignore) {
            // created by an earlier case
         }

         JDBCDataSource ds = dataSource("derby");
         String xml = toXML(parse(text, ds));
         String legacy = xml.replaceAll("<quoted><!\\[CDATA\\[[^\\]]*\\]\\]></quoted>", "")
            .replaceAll("<quotedColumn [^>]*/>", "")
            .replaceAll(" quoted=\"[^\"]*\"", "")
            .replaceAll(" quotedColumn=\"[^\"]*\"", "");
         UniformSQL sql = load(Tool.parseXML(new StringReader(legacy)).getDocumentElement(), ds);

         JDBCQuery query = new JDBCQuery();
         query.setDataSource(ds);
         query.setSQLDefinition(sql);
         assertTrue(new JDBCQueryCacheNormalizer(query).isClearedSqlString(), text);
         String generated = sql.getSQLString();
         boolean ordered = text.contains("order by");
         List<String> expected = rows(con, text, ordered);
         List<String> actual;

         try {
            actual = rows(con, generated, ordered);
         }
         catch(SQLException ex) {
            // a legacy group by is written unquoted on main too (an error, not wrong rows)
            assertTrue(text.contains("group by"), text + " => " + generated + ": " + ex);
            return;
         }

         assertEquals(expected, actual, text + " => " + generated);
      }
   }

   // ordinary execution: the data-cache normalizer clears the sql string and regenerates it
   @Test
   void normalizedQueryRunsOnDerby() throws Exception {
      try(Connection con = derby("n77763"); Statement st = con.createStatement()) {
         createTables(st);
         String[] texts = {
            "select t.id, t.u from t where t.d < current_timestamp",
            "select t.id, t.u from t where t.u = current_user",
            "select t.id, current_date as today from t"
         };

         for(String text : texts) {
            JDBCDataSource ds = dataSource("derby");
            UniformSQL sql = parse(text, ds);
            JDBCQuery query = new JDBCQuery();
            query.setDataSource(ds);
            query.setSQLDefinition(sql);
            JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
            assertTrue(normalizer.isClearedSqlString(), text);
            String generated = sql.getSQLString();
            assertEquals(rows(con, text), rows(con, generated), text + " => " + generated);
         }
      }
   }

   private static void createTables(Statement st) throws SQLException {
      st.execute("create table t (id int, d timestamp, u varchar(128))");
      st.execute("insert into t values (1, timestamp('2000-01-01 00:00:00'), 'APP')");
      st.execute("insert into t values (2, timestamp('2999-01-01 00:00:00'), 'X')");
      st.execute("create table s (id int, d timestamp)");
      st.execute("insert into s values (1, timestamp('2000-01-01 00:00:00'))");
      st.execute("insert into s values (2, timestamp('2999-01-01 00:00:00'))");
      // a table with real lower case columns named like the keywords
      st.execute("create table t2 (id int, \"current_timestamp\" timestamp, " +
                    "\"current_user\" varchar(128), \"current_date\" date)");
      st.execute("insert into t2 values (1, timestamp('1990-01-01 00:00:00'), 'bob', " +
                    "date('1990-01-01'))");
   }

   private static void assertSameRows(Connection con, JDBCDataSource ds, String[] texts)
      throws SQLException
   {
      for(String text : texts) {
         String generated = regenerate(text, ds);
         assertEquals(rows(con, text), rows(con, generated), text + " => " + generated);
      }
   }

   private static UniformSQL editorQuery(String word, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.addTable("T3", "T3");
      JDBCSelection selection = new JDBCSelection();
      String col = "T3." + word;
      selection.addColumn("T3.id");
      selection.setTable("T3.id", "T3");
      selection.setAlias(selection.addColumn(col), word);
      selection.setTable(col, "T3");
      sql.setSelection(selection);
      sql.setWhere(new XBinaryCondition(new XExpression(col, XExpression.FIELD),
                                        new XExpression("'a'", XExpression.EXPRESSION), "="));
      sql.setGroupBy(new Object[] { col, "T3.id" });
      sql.setOrderBy(col, "asc");
      return sql;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static Element save(UniformSQL sql) throws Exception {
      return Tool.parseXML(new StringReader(toXML(sql))).getDocumentElement();
   }

   private static UniformSQL load(Element xml, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parseXML(xml);
      sql.setDataSource(ds);
      return sql;
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static String regenerate(String text, JDBCDataSource ds) {
      UniformSQL sql = parse(text, ds);
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   // the word is written as a quoted identifier
   private static boolean isQuoted(String sql, String word) {
      return Pattern.compile("[\"`]" + Pattern.quote(word) + "[\"`]", Pattern.CASE_INSENSITIVE)
         .matcher(sql).find();
   }

   private static boolean containsWord(String sql, String word) {
      return Pattern.compile("(?<![\\w.\"`])" + Pattern.quote(word) + "(?![\\w\"`])",
                             Pattern.CASE_INSENSITIVE).matcher(sql).find();
   }

   private static Connection derby(String name) throws Exception {
      Driver driver = (Driver) Class.forName("org.apache.derby.iapi.jdbc.AutoloadedDriver")
         .getDeclaredConstructor().newInstance();
      return driver.connect("jdbc:derby:memory:" + name + ";create=true", new Properties());
   }

   // rows as a sorted multiset, with columns ordered by label since regeneration can reorder
   // the select list. A date or time is reduced to its year, the value of a call to now.
   private static List<String> rows(Connection con, String query) throws SQLException {
      return rows(con, query, false);
   }

   private static List<String> rows(Connection con, String query, boolean ordered)
      throws SQLException
   {
      List<String> rows = new ArrayList<>();

      try(Statement st = con.createStatement(); ResultSet rs = st.executeQuery(query)) {
         ResultSetMetaData meta = rs.getMetaData();
         Integer[] order = new Integer[meta.getColumnCount()];

         for(int i = 0; i < order.length; i++) {
            order[i] = i + 1;
         }

         Arrays.sort(order, Comparator.comparing(i -> {
            try {
               return meta.getColumnLabel(i).toLowerCase(Locale.ROOT);
            }
            catch(SQLException ex) {
               throw new RuntimeException(ex);
            }
         }));

         while(rs.next()) {
            StringBuilder row = new StringBuilder("[");

            for(int i = 0; i < order.length; i++) {
               Object value = rs.getObject(order[i]);
               String text = String.valueOf(value);

               if(value instanceof java.util.Date || value instanceof java.time.temporal.Temporal) {
                  text = "<" + text.substring(0, 4) + ">";
               }

               row.append(i > 0 ? "," : "").append(text);
            }

            rows.add(row.append("]").toString());
         }
      }

      if(!ordered) {
         Collections.sort(rows);
      }

      return rows;
   }

   private static JDBCDataSource dataSource(String type) {
      if("generic".equals(type)) {
         return null;
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77763_" + type.replace(' ', '_'));
      ds.setProductVersion("19.0");

      switch(type) {
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:memory:test;create=true");
         ds.setProductVersion("10.17");
      }
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
         ds.setProductVersion("8.0");
      }
      default -> throw new IllegalArgumentException(type);
      }

      assertEquals(type, SQLHelper.getSQLHelper(ds).getSQLHelperType(), "helper for " + type);
      return ds;
   }
}
