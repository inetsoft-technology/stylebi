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
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77548, a parsed query saved before the parser recorded the join clauses (#77475) loads
 * with no join clause on any join, but with its sql string. JDBCQueryCacheNormalizer clears
 * the sql string of a query that is not lossy, and the joins were then regenerated in the
 * outer-last order of joins built in the query editor, e.g. a left join followed by an inner
 * join on its null-supplying table became (c INNER JOIN d) RIGHT OUTER JOIN a, which keeps the
 * a rows the inner join removes. UniformSQL.isLossy() now copies the join clauses of the
 * parse of the sql string to the loaded joins. A query level that can't be matched keeps its
 * sql string (lossy), and a legacy cycle stays lossy as before (#77489).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLegacyJoinClauseTest {
   private static final String DB = "jdbc:derby:memory:bug77548";
   private static final String[] TABLES = { "a", "b", "c", "d", "e", "f", "g", "p", "q", "r" };

   // cycle-free legacy shapes that regenerated with the outer-last order and returned
   // different rows. The select lists are sorted, as the normalizer sorts them
   static Stream<String> legacyQueries() {
      return Stream.of(
         // the star of the diagnosis
         "select a.id, b.id, c.id, d.id from a left join b on a.id = b.id " +
            "left join c on a.id = c.id join d on c.id = d.id",
         // two left joins from d, then an inner join on the null-supplying c
         "select c.id, d.id, e.id, f.id from d left join c on d.id = c.id " +
            "left join f on d.id = f.id join e on e.id = c.id",
         // a second inner join leaving the null-supplying table
         "select a.id, b.id, c.id, d.id from a left join b on a.id = b.id " +
            "join c on b.id = c.id join d on b.k = d.k",
         // right joins reordered against each other
         "select a.id, b.id, c.id, d.id from a join b on a.id = b.id " +
            "right join c on a.id = c.id right join d on b.id = d.id",
         // an unrelated extra join group
         "select a.id, b.id, c.id, d.id, p.id, q.id from a left join b on a.id = b.id " +
            "left join c on a.id = c.id join d on c.id = d.id, p join q on p.id = q.id",
         // a where clause join and condition next to the on clause joins
         "select a.id, b.id, c.id, d.id, e.id from a left join b on a.id = b.id " +
            "left join c on a.id = c.id join d on c.id = d.id, e where e.id = a.id and e.k > 1");
   }

   @ParameterizedTest
   @MethodSource("legacyQueries")
   void legacyQueryRunsWithTheRowsOfItsText(String text) throws Exception {
      UniformSQL loaded = loadLegacy(text, true);
      assertLegacy(loaded);

      String executed = normalize(loaded.clone());
      assertNotNull(executed, "the normalizer clears the sql string of a non-lossy query");
      assertNotEquals(text, executed);
      assertSameRows(text, executed);

      // the structure is the one of a fresh parse of the text
      assertEquals(normalize(loadLegacy(text, false).clone()), executed);
   }

   @ParameterizedTest
   @MethodSource("legacyQueries")
   void legacyQueryGetsTheJoinClausesOfItsText(String text) throws Exception {
      UniformSQL loaded = loadLegacy(text, true);
      UniformSQL fresh = parse(text);
      assertFalse(loaded.isLossy(), text);
      assertEquals(clauses(fresh), clauses(loaded));
      assertEquals(text, loaded.getSQLString());

      // ansi join on and off, the regenerated sql is the one of the fresh parse
      for(boolean ansiJoin : new boolean[] { false, true }) {
         assertEquals(generate(fresh, ansiJoin), generate(loaded, ansiJoin));
      }

      // saved again, the clauses are in the xml
      assertTrue(toXML(loaded).contains(" joinClause=\""));
   }

   @Test
   void legacyJoinsInAnExistsSubqueryGetTheirClauses() throws Exception {
      // the top level has no join, the legacy joins are in the subquery, which has no sql
      // string of its own and is regenerated from its structure
      String text = "select g.id, g.k from g where exists (select 1 from d " +
         "left join c on d.id = c.id left join f on d.id = f.id join e on e.id = c.id " +
         "where d.k = g.k)";
      UniformSQL loaded = loadLegacy(text, true);
      UniformSQL subquery = subquery(loaded);
      assertTrue(Arrays.stream(subquery.getJoins())
                    .allMatch(join -> join.getJoinClause() == XJoin.UNKNOWN_CLAUSE));

      String executed = normalize(loaded.clone());
      assertNotNull(executed);
      assertSameRows(text, executed);
      assertFalse(loaded.isLossy());
      assertEquals(clauses(subquery(parse(text))), clauses(subquery(loaded)));
   }

   @Test
   void legacyCycleWithAStepOfNoNewTableStaysLossy() throws Exception {
      // a legacy cycle is lossy (#77489). With its clauses copied, this one would regenerate
      // through the outer-last order unless the text order handles the "join r on p.k = g.k"
      // step (#77674), so the clauses are not copied and the text is kept
      String text = "select c.id, d.id, e.id, g.id, p.id, q.id from d left join c on d.id = c.id " +
         "join e on e.id = c.id, p join q on p.id = q.id join g on g.id = q.id join r on p.k = g.k";
      UniformSQL loaded = loadLegacy(text, true);
      assertTrue(loaded.isLossy());
      assertLegacy(loaded);
      assertNull(normalize(loaded.clone()), "a lossy query keeps its sql string");
   }

   @Test
   void legacyCycleOfTheReportStaysLossy() throws Exception {
      String text = "select a.id, b.id, c.id, d.id, e.id from a left join b on a.id = b.id " +
         "left join c on a.id = c.id join d on c.id = d.id join e on c.id = e.id and d.k = e.k";
      UniformSQL loaded = loadLegacy(text, true);
      assertTrue(loaded.isLossy());
      assertLegacy(loaded);
   }

   @Test
   void legacyCycleInASubqueryIsLossy() throws Exception {
      String text = "select g.id from g where exists (select 1 from a left join b on a.id = b.id " +
         "join c on b.id = c.id and a.k = c.k where a.id = g.id)";
      UniformSQL loaded = loadLegacy(text, true);
      assertTrue(loaded.isLossy());
      assertNull(normalize(loadLegacy(text, true)));
   }

   @Test
   void unmatchedLegacyJoinsKeepTheSqlString() throws Exception {
      // the structure was changed after the parse (e.g. by fixUniformSQLInfo), so its joins
      // don't match the parse of the text: nothing is copied and the text is kept
      String text = legacyQueries().findFirst().get();
      UniformSQL loaded = loadLegacy(text, true);
      XJoin join = Arrays.stream(loaded.getJoins())
         .filter(j -> "c.id = d.id".equals(j.toString())).findFirst().get();
      join.setExpression2(new XExpression("d.k", XExpression.FIELD));

      assertTrue(loaded.isLossy());
      assertLegacy(loaded);
      assertEquals(text, loaded.getSQLString());
   }

   @Test
   void unmatchedTablesKeepTheSqlString() throws Exception {
      // e.g. a table the parser splits differently since the save
      String text = legacyQueries().findFirst().get();
      UniformSQL loaded = loadLegacy(text, true);
      loaded.getSelectTable()[3].setAlias("d2");

      assertTrue(loaded.isLossy());
      assertLegacy(loaded);
   }

   @Test
   void unmatchedInnerJoinsAreNotLossy() throws Exception {
      // inner joins can be reordered, so an unmatched legacy level without an outer join is
      // generated as before
      String text = "select a.id, b.id, c.id from a join b on a.id = b.id join c on b.id = c.id";
      UniformSQL loaded = loadLegacy(text, true);
      XJoin join = loaded.getJoins()[1];
      join.setExpression2(new XExpression("c.k", XExpression.FIELD));

      assertFalse(loaded.isLossy());
      assertLegacy(loaded);
   }

   @Test
   void cloneGetsTheClausesOnItsOwnCheck() throws Exception {
      String text = legacyQueries().findFirst().get();
      UniformSQL loaded = loadLegacy(text, true);
      UniformSQL early = loaded.clone();

      assertFalse(loaded.isLossy());
      assertLegacy(early);
      // a clone after the check has the clauses
      assertEquals(clauses(loaded), clauses(loaded.clone()));

      // a clone made before it gets them on its own check, while it has the sql string
      assertFalse(early.isLossy());
      assertEquals(clauses(loaded), clauses(early));
   }

   @Test
   void clearedLegacyQueryIsNotChanged() throws Exception {
      // without the sql string there's nothing to parse, the joins are generated as before
      String text = legacyQueries().findFirst().get();
      UniformSQL loaded = loadLegacy(text, true);
      loaded.clearSQLString();

      assertFalse(loaded.isLossy());
      assertLegacy(loaded);
      assertTrue(generate(loaded, false).endsWith(OUTER_LAST), generate(loaded, false));
   }

   @Test
   void editorJoinsAreNotChanged() throws Exception {
      UniformSQL editor = new UniformSQL();
      JDBCSelection selection = new JDBCSelection();

      for(String table : new String[] { "a", "b", "c", "d" }) {
         selection.addColumn(table + ".id");
         editor.addTable(table);
      }

      editor.setSelection(selection);
      editor.addJoin(join("a.id", "*=", "b.id"));
      editor.addJoin(join("a.id", "*=", "c.id"));
      editor.addJoin(join("c.id", "=", "d.id"));
      editor.setDataSource(GenericJDBCDataSource.create());

      assertFalse(editor.isLossy());
      assertLegacy(editor);
      assertTrue(generate(editor, false).endsWith(OUTER_LAST), generate(editor, false));
   }

   @Test
   void uncheckedJoinOrderWithoutDataSourceIsNotCopied() throws Exception {
      // a right join mixed with an inner join needs the join order check, which needs the data
      // source. Without it, nothing is copied and the result isn't cached
      String text = "select a.id, b.id, c.id, d.id from a join b on a.id = b.id " +
         "right join c on a.id = c.id right join d on b.id = d.id";
      UniformSQL loaded = loadLegacy(text, true, true);
      assertNull(loaded.getDataSource());
      assertTrue(loaded.isLossy());
      assertLegacy(loaded);

      loaded.setDataSource(GenericJDBCDataSource.create());
      assertFalse(loaded.isLossy());
      assertEquals(clauses(parse(text)), clauses(loaded));
   }

   @Test
   void checkedLevelWithoutDataSourceIsCopied() throws Exception {
      String text = legacyQueries().findFirst().get();
      UniformSQL loaded = loadLegacy(text, true, true);
      assertNull(loaded.getDataSource());
      assertFalse(loaded.isLossy());
      assertEquals(clauses(parse(text)), clauses(loaded));
   }

   @Test
   void recordedQueryIsNotChanged() throws Exception {
      String text = legacyQueries().findFirst().get();
      UniformSQL loaded = loadLegacy(text, false);
      List<Integer> before = clauses(loaded);
      String xml = toXML(loaded);

      assertFalse(loaded.isLossy());
      assertEquals(before, clauses(loaded));
      assertEquals(xml, toXML(loaded).replace(" lossy=\"false\"", ""));
   }

   private static final String OUTER_LAST = "from ((c INNER JOIN d ON c.id = d.id ) " +
      "RIGHT OUTER JOIN a ON a.id = c.id ) LEFT OUTER JOIN b ON a.id = b.id";

   @BeforeAll
   static void createTables() throws SQLException {
      try(Connection conn = DriverManager.getConnection(DB + ";create=true");
          Statement stmt = conn.createStatement())
      {
         for(String table : TABLES) {
            stmt.execute("create table " + table + " (id int, k int)");
         }
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection(DB + ";drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static void assertLegacy(UniformSQL sql) {
      for(XJoin join : sql.getJoins()) {
         assertEquals(XJoin.UNKNOWN_CLAUSE, join.getJoinClause(), join.toString());
      }
   }

   private static List<Integer> clauses(UniformSQL sql) {
      return Arrays.stream(sql.getJoins()).map(XJoin::getJoinClause).toList();
   }

   private static XJoin join(String column1, String op, String column2) {
      return new XJoin(new XExpression(column1, XExpression.FIELD),
                       new XExpression(column2, XExpression.FIELD), op);
   }

   // the first where subquery
   private static UniformSQL subquery(UniformSQL sql) {
      return subquery(sql.getWhere());
   }

   private static UniformSQL subquery(XFilterNode node) {
      if(node instanceof XUnaryCondition &&
         ((XUnaryCondition) node).getExpression1().getValue() instanceof UniformSQL)
      {
         return (UniformSQL) ((XUnaryCondition) node).getExpression1().getValue();
      }

      for(int i = 0; i < node.getChildCount(); i++) {
         UniformSQL subquery = subquery((XFilterNode) node.getChild(i));

         if(subquery != null) {
            return subquery;
         }
      }

      return null;
   }

   /**
    * Run the cache normalizer on the query, as plain execution does.
    * @return the sql it executes, or null if the sql string is kept.
    */
   private static String normalize(UniformSQL sql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("q77548");
      // the query sets its data source on the sql
      query.setDataSource(sql.getDataSource());
      query.setSQLDefinition(sql);
      JDBCQueryCacheNormalizer normalizer = new JDBCQueryCacheNormalizer(query);
      return normalizer.isClearedSqlString() ? flatten(sql.getSQLString()) : null;
   }

   private static String generate(UniformSQL sql, boolean ansiJoin) {
      SQLHelper helper = new SQLHelper();
      helper.setUniformSql(sql);
      helper.setAnsiJoin(ansiJoin);
      return flatten(helper.generateSentence());
   }

   // the generated sql is pretty-printed
   private static String flatten(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      // a RIGHT join mixed with an inner join is refused without a data source
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      sql.setSQLString(text, false);
      return sql;
   }

   private static UniformSQL loadLegacy(String text, boolean legacy) throws Exception {
      return loadLegacy(text, legacy, false);
   }

   /**
    * Save a parsed query with its sql string and load it, as a stored query is loaded.
    * @param legacy true to remove the joinClause attributes, as in the xml saved before the
    *               join clauses were recorded.
    */
   private static UniformSQL loadLegacy(String text, boolean legacy, boolean noDataSource)
      throws Exception
   {
      String xml = toXML(parse(text));

      if(legacy) {
         assertTrue(xml.contains(" joinClause=\""), xml);
         xml = xml.replaceAll(" joinClause=\"-?\\d+\"", "");
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      if(!noDataSource) {
         loaded.setDataSource(GenericJDBCDataSource.create());
      }

      assertTrue(loaded.hasSQLString());
      assertEquals(UniformSQL.PARSE_SUCCESS, loaded.getParseResult());
      return loaded;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   /**
    * Runs both queries on random data with nulls, and requires the same rows from both.
    */
   private static void assertSameRows(String expected, String generated) throws SQLException {
      Random random = new Random(77548);

      try(Connection conn = DriverManager.getConnection(DB)) {
         for(int i = 0; i < 200; i++) {
            fillTables(conn, random);
            List<String> rows1 = rows(conn, expected);
            List<String> rows2 = rows(conn, generated);
            assertEquals(rows1, rows2, "dataset " + i + "\nexpected: " + expected +
               "\ngenerated: " + generated);
         }
      }
   }

   private static void fillTables(Connection conn, Random random) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            stmt.executeUpdate("delete from " + table);
         }
      }

      for(String table : TABLES) {
         try(PreparedStatement insert =
                conn.prepareStatement("insert into " + table + " values (?, ?)"))
         {
            int count = random.nextInt(5);

            for(int i = 0; i < count; i++) {
               for(int column = 1; column <= 2; column++) {
                  int value = random.nextInt(4);

                  if(value == 0) {
                     insert.setNull(column, Types.INTEGER);
                  }
                  else {
                     insert.setInt(column, value);
                  }
               }

               insert.addBatch();
            }

            if(count > 0) {
               insert.executeBatch();
            }
         }
      }
   }

   // the result rows as a sorted multiset
   private static List<String> rows(Connection conn, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(Statement stmt = conn.createStatement(); ResultSet result = stmt.executeQuery(query)) {
         int columns = result.getMetaData().getColumnCount();

         while(result.next()) {
            StringBuilder row = new StringBuilder();

            for(int i = 1; i <= columns; i++) {
               row.append(result.getObject(i)).append('|');
            }

            rows.add(row.toString());
         }
      }

      Collections.sort(rows);
      return rows;
   }
}
