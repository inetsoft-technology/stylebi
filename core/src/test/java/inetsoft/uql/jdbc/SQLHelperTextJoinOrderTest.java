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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77475, a parsed query whose joins are not a simple chain (e.g. LEFT JOIN followed by an
 * inner join on the null-supplying table and another LEFT JOIN) must be regenerated with the joins
 * nested in text order, not reordered with the inner join moved inside a RIGHT OUTER JOIN. The
 * parser records which ON clause, or the where clause, each join came from: where clause joins are
 * regenerated as where conditions, and each ON clause is one join step in text order.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperTextJoinOrderTest {
   private static final String COLS3 = "select a.id, b.id, c.id ";
   private static final String COLS = "select a.id, b.id, c.id, d.id ";
   private static final String COLS5 = "select a.id, b.id, c.id, d.id, e.id ";
   private static final String S05 =
      COLS + "from a left join b on a.id = b.id join c on b.id = c.id left join d on a.id = d.id";

   static Stream<String> parsedQueries() {
      return Stream.of(
         // S05, S21 and L8 from the report
         S05,
         COLS + "from a left join b on a.id = b.id left join c on a.id = c.id join d on b.id = d.id",
         COLS + "from a left join b on a.id = b.id left join d on a.id = d.id join c on b.id = c.id",
         // T12, a parenthesized outer join and a join from the where clause
         COLS + "from a left join (b left join c on b.id = c.id) on a.id = b.id, d where c.id = d.id",
         // an inner join from the where clause on the null-supplying side
         COLS + "from a left join b on a.id = b.id, c, d where b.id = c.id and c.id = d.id",
         // comma-separated groups linked by a where join, on either side of an outer join
         COLS + "from a join b on a.id = b.id, c left join d on c.id = d.id where b.id = c.id",
         COLS + "from a join b on a.id = b.id, c left join d on c.id = d.id where b.id = d.id",
         // the join matrix walk would emit c-e before a-d
         COLS5 + "from a left join b on a.id = b.id join c on b.id = c.id " +
            "left join d on a.id = d.id join e on d.id = e.id",
         // a parenthesized join on the null side in the middle of the text
         COLS + "from a join b on a.id = b.id left join (c join d on c.id = d.id) on b.id = c.id",
         // ... with an ON that also names a table joined before
         COLS5 + "from a join b on a.id = b.id left join (c join d on c.id = d.id " +
            "join e on d.id = e.id and c.id = e.id) on b.id = c.id",
         // an ON or where condition between tables that are already joined
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and a.id = c.id " +
            "left join d on a.id = d.id",
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id, d " +
            "where a.id = c.id and c.id = d.id",
         COLS + "from a join b on a.id = b.id join c on b.id = c.id and a.id = c.id " +
            "right join d on a.id = d.id",
         COLS5 + "from a join b on a.id = b.id join c on b.id = c.id and a.id = c.id " +
            "right join d on a.id = d.id left join e on d.id = e.id where a.id = e.id",
         // a where join between the tables of an outer join (#77478)
         COLS3 + "from a left join b on a.id = b.id join c on a.id = c.id where a.id = b.id",
         COLS3 + "from a left join b on a.id = b.id left join c on a.id = c.id where b.id = c.id",
         // a where join under not and or
         COLS3 + "from a left join b on a.id = b.id left join c on a.id = c.id " +
            "where not (c.id = 1 and b.id = c.id)",
         COLS + "from a left join b on a.id = b.id left join c on a.id = c.id, d " +
            "where b.id = c.id or c.id = d.id",
         // an outer join to a parenthesized join whose ON names the nested table first
         COLS3 + "from a left join (b join c on b.id = c.id) on b.id = a.id",
         COLS3 + "from a right join (b join c on b.id = c.id) on b.id = a.id",
         // Bug #77440, the ON names the earlier table first, and an outer join nested in
         // the right operand. Only the table range of the right operand orients them
         COLS3 + "from a left join (b join c on b.id = c.id) on a.id = b.id",
         COLS3 + "from a left join (b left join c on c.id = b.id) on b.id = a.id",
         COLS3 + "from a right join (b join c on b.id = c.id) on a.id = c.id",
         // a condition on one table in an ON
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id and c.id = c.id " +
            "left join d on a.id = d.id",
         // chains, which were already correct
         COLS + "from a left join b on a.id = b.id join c on b.id = c.id join d on c.id = d.id",
         COLS + "from b join c on b.id = c.id right join a on a.id = b.id left join d on a.id = d.id"
      );
   }

   @Test
   void leftInnerLeftKeepsTextNesting() throws Exception {
      String generated = normalize(parse(S05).getSQLString());

      assertFalse(generated.contains("RIGHT"), generated);
      assertTrue(generated.contains("((a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON " +
                                    "b.id = c.id ) LEFT OUTER JOIN d ON a.id = d.id"), generated);
   }

   @Test
   void parenthesizedOuterJoinStaysOnPreservedSide() throws Exception {
      UniformSQL sql = parse(COLS + "from a left join (b left join c on b.id = c.id) on a.id = b.id, d " +
                             "where c.id = d.id");
      String generated = normalize(sql.getSQLString());

      // a left join (b left join c) is (b left join c) right join a
      assertTrue(generated.endsWith("from (b LEFT OUTER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON " +
                                    "a.id = b.id , d where c.id = d.id"), generated);
   }

   @Test
   void whereJoinsStayInWhere() throws Exception {
      String generated = normalize(
         parse(COLS + "from a join b on a.id = b.id, c left join d on c.id = d.id where b.id = d.id")
            .getSQLString());
      assertTrue(generated.endsWith("from a INNER JOIN b ON a.id = b.id , c LEFT OUTER JOIN d ON " +
                                    "c.id = d.id where b.id = d.id"), generated);

      // not folded into the outer join's ON (#77478)
      generated = normalize(parse(COLS3 + "from a left join b on a.id = b.id join c on a.id = c.id " +
                                  "where a.id = b.id").getSQLString());
      assertTrue(generated.endsWith("where a.id = b.id"), generated);

      // the not around a where join is kept
      generated = normalize(parse(COLS3 + "from a left join b on a.id = b.id left join c on a.id = c.id " +
                                  "where not (c.id = 1 and b.id = c.id)").getSQLString());
      assertTrue(generated.toLowerCase().contains("where not (c.id = 1 and b.id = c.id)"), generated);
   }

   @Test
   void outerJoinToParenthesizedGroupNestsTheGroups() throws Exception {
      UniformSQL sql = parse(COLS + "from a join b on a.id = b.id left join (c join d on c.id = d.id) " +
                             "on b.id = c.id");
      String generated = normalize(sql.getSQLString());

      assertTrue(generated.contains("(a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN " +
                                    "(c INNER JOIN d ON c.id = d.id ) ON b.id = c.id"), generated);
   }

   @Test
   void joinedTableOnTheRightIsOneChain() throws Exception {
      UniformSQL sql = parse(COLS5 + "from a join b on a.id = b.id left join (c join d on c.id = d.id " +
                             "join e on d.id = e.id and c.id = e.id) on b.id = c.id");
      String generated = normalize(sql.getSQLString());

      assertTrue(generated.contains("(a INNER JOIN b ON a.id = b.id ) LEFT OUTER JOIN (c INNER JOIN d ON " +
                                    "c.id = d.id INNER JOIN e ON d.id = e.id AND c.id = e.id ) ON " +
                                    "b.id = c.id"), generated);
   }

   @Test
   void outerJoinToParenthesizedJoinKeepsItsDirection() throws Exception {
      // the parser recorded no join type for b, so this was a left join from b
      String generated = normalize(parse(COLS3 + "from a left join (b join c on b.id = c.id) " +
                                         "on b.id = a.id").getSQLString());
      assertTrue(generated.contains("(b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a"), generated);
   }

   @Test
   void oneTableConditionInAnOnKeepsTheOrder() throws Exception {
      // c.id = c.id is not a join, so like any such inner ON condition it is a where condition
      String generated = normalize(parse(COLS + "from a left join b on a.id = b.id join c on b.id = c.id " +
                                         "and c.id = c.id left join d on a.id = d.id").getSQLString());
      assertFalse(generated.contains("RIGHT"), generated);
   }

   @Test
   void onWithOrIsNotFlattened() throws Exception {
      // the ON conditions can't be written as one AND list, so the old order is used
      String generated = normalize(parse(COLS3 + "from a join b on a.id = b.id join c on b.id = c.id " +
                                         "and (a.id = c.id or b.id = c.id)").getSQLString());
      assertFalse(generated.contains("ON b.id = c.id AND a.id = c.id OR b.id = c.id"), generated);
   }

   @Test
   void whereClauseOuterJoinKeepsTheOldOrder() throws Exception {
      // a *= written in the where clause keeps the (B = C) =* A meaning. The sql is refused
      // with a data source that writes ANSI joins (Bug #77548), so it's parsed without one
      UniformSQL sql = new UniformSQL();
      sql.parse(COLS3 + "from a, b, c where a.id *= b.id and b.id = c.id",
                UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      sql.setDataSource(GenericJDBCDataSource.create());
      String generated = normalize(sql.getSQLString());

      for(XJoin join : sql.getJoins()) {
         join.setJoinClause(XJoin.UNKNOWN_CLAUSE);
      }

      sql.clearSQLString();
      assertEquals(normalize(sql.getSQLString()), generated);
   }

   @Test
   void innerJoinsKeepTheOldOrder() throws Exception {
      // inner joins can be reordered freely, so a query without an outer join
      // is generated as before
      assertSameAsUnrecorded(parse(COLS3 + "from a join b on a.id = b.id, c where b.id = c.id"));
      assertSameAsUnrecorded(parse(COLS3 + "from a, b, c where a.id = b.id and b.id = c.id"));
   }

   @Test
   void joinTypeChangedInTheEditorKeepsTheOldOrder() throws Exception {
      // the query editor changes a join's type in place; a.id = c.id is now an
      // outer join in the inner ON of c, which one join can't write
      UniformSQL sql = parse(COLS + "from a left join b on a.id = b.id join c on b.id = c.id " +
                             "and a.id = c.id left join d on a.id = d.id");

      for(XJoin join : sql.getJoins()) {
         if("a.id".equals(join.getExpression1().getValue()) &&
            "c.id".equals(join.getExpression2().getValue()))
         {
            join.setOp("*=");
         }
      }

      sql.clearSQLString();
      assertSameAsUnrecorded(sql);
   }

   // the sql is generated the same as with the join clauses not recorded
   private static void assertSameAsUnrecorded(UniformSQL sql) {
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      UniformSQL unrecorded = sql.clone();

      for(XJoin join : unrecorded.getJoins()) {
         join.setJoinClause(XJoin.UNKNOWN_CLAUSE);
      }

      unrecorded.clearSQLString();
      assertEquals(normalize(unrecorded.getSQLString()), generated);
   }

   @Test
   void onConditionsStayInTheirOn() throws Exception {
      UniformSQL sql = parse(COLS + "from a join b on a.id = b.id join c on b.id = c.id and a.id = c.id " +
                             "right join d on a.id = d.id");
      String generated = normalize(sql.getSQLString());

      assertFalse(generated.contains(" where "), generated);
      assertTrue(generated.contains("INNER JOIN c ON b.id = c.id AND a.id = c.id"), generated);
   }

   @ParameterizedTest
   @MethodSource("parsedQueries")
   void regeneratedSqlRoundTrips(String text) throws Exception {
      // a reparsed outer join can swap its ON operands once, so require a
      // fixed point after the first regeneration
      String generated = normalize(parse(text).getSQLString());
      String regenerated = normalize(parse(generated).getSQLString());
      assertEquals(regenerated, normalize(parse(regenerated).getSQLString()));
   }

   @Test
   void subqueryJoinsKeepTextOrder() throws Exception {
      UniformSQL sql = parse("select t.x from (select a.id x from a left join b on a.id = b.id " +
                             "join c on b.id = c.id left join d on a.id = d.id) t");
      String generated = normalize(sql.getSQLString());
      assertFalse(generated.contains("RIGHT"), generated);
   }

   @Test
   void parserRecordsJoinClauses() throws Exception {
      UniformSQL sql = parse(COLS + "from a left join b on a.id = b.id join c on b.id = c.id " +
                             "and a.id = c.id, d where c.id = d.id");
      int[] clauses = Arrays.stream(sql.getJoins()).mapToInt(XJoin::getJoinClause).toArray();
      assertArrayEquals(new int[] { 1, 2, 2, XJoin.WHERE_CLAUSE }, clauses);

      UniformSQL using = parse("select a.id from a join b using (id)");
      clauses = Arrays.stream(using.getJoins()).mapToInt(XJoin::getJoinClause).toArray();
      assertArrayEquals(new int[] { 1 }, clauses);
   }

   @Test
   void malformedJoinClauseIsUnknown() throws Exception {
      UniformSQL merged = parse(S05).clone();
      merged.clearSQLString();
      String xml = toXml(merged).replaceAll(" joinClause=\"-?\\d+\"", " joinClause=\"x\"");
      String generated = normalize(fromXml(xml).getSQLString());
      assertTrue(generated.contains("RIGHT OUTER JOIN a"), generated);
   }

   @Test
   void joinClausesSurviveCopyAndXml() throws Exception {
      UniformSQL sql = parse(S05);
      String expected = normalize(sql.getSQLString());

      UniformSQL copy = new UniformSQL();
      copy.read(sql);
      copy.clearSQLString();
      assertEquals(expected, normalize(copy.getSQLString()));

      // merge and VPM clear the sql string and regenerate from the structure
      UniformSQL merged = sql.clone();
      merged.clearSQLString();
      assertEquals(expected, normalize(merged.getSQLString()));

      String xml = toXml(merged);
      assertTrue(xml.contains("joinClause=\"3\""), xml);
      UniformSQL restored = fromXml(xml);
      assertEquals(expected, normalize(restored.getSQLString()));
   }

   @Test
   void unrecordedJoinsKeepTheOldOrder() throws Exception {
      // a query saved before the join clauses were recorded is generated as before
      UniformSQL merged = parse(S05).clone();
      merged.clearSQLString();
      String legacyXml = toXml(merged).replaceAll(" joinClause=\"-?\\d+\"", "");
      String generated = normalize(fromXml(legacyXml).getSQLString());
      assertTrue(generated.contains("RIGHT OUTER JOIN a"), generated);
   }

   @Test
   void editorJoinEditsKeepTextOrder() throws Exception {
      UniformSQL sql = parse(S05 + ", e");
      String expected = normalize(sql.getSQLString());

      // QueryGraphModelService.processEditJoin, processDeleteJoins and
      // processRenameJoins remove all joins and add the remaining ones back
      UniformSQL edited = sql.clone();
      edited.removeAllJoins();

      for(XJoin join : sql.getJoins()) {
         edited.addJoin(join);
      }

      edited.clearSQLString();
      assertEquals(expected, normalize(edited.getSQLString()));

      // a join added in the editor comes last, as if appended to the text
      edited.addJoin(join("d.id", "=", "e.id"));
      edited.clearSQLString();
      String generated = normalize(edited.getSQLString());
      assertTrue(generated.contains("((a LEFT OUTER JOIN b ON a.id = b.id ) INNER JOIN c ON " +
                                    "b.id = c.id ) LEFT OUTER JOIN d ON a.id = d.id ) INNER JOIN e ON " +
                                    "d.id = e.id"), generated);
   }

   @Test
   void editorBuiltJoinsKeepOuterJoinsLast() {
      // an editor or data model join set is not text ordered and keeps the
      // A *= B, B = C means (B = C) =* A behavior
      UniformSQL sql = new UniformSQL();
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("a.id");
      sql.setSelection(selection);

      for(String table : new String[] { "a", "b", "c", "d" }) {
         sql.addTable(table);
      }

      sql.addJoin(join("a.id", "*=", "b.id"));
      sql.addJoin(join("b.id", "=", "c.id"));
      sql.addJoin(join("a.id", "*=", "d.id"));

      String generated = normalize(sql.getSQLString());
      assertTrue(generated.contains("(b INNER JOIN c ON b.id = c.id ) RIGHT OUTER JOIN a ON a.id = b.id"),
                 generated);
   }

   /**
    * Runs the original and regenerated sql on random data with nulls, and requires the same
    * rows from both.
    */
   @ParameterizedTest
   @MethodSource("parsedQueries")
   void regeneratedSqlReturnsSameRowsOnDerby(String text) throws Exception {
      String generated = parse(text).getSQLString();
      Random random = new Random(77475);

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77475;create=true")) {
         createTables(conn);

         for(int i = 0; i < 100; i++) {
            fillTables(conn, random);
            assertEquals(rows(conn, text), rows(conn, generated),
                         "dataset " + i + "\noriginal: " + text + "\ngenerated: " + generated);
         }
      }
   }

   @AfterAll
   static void dropDatabase() {
      try {
         DriverManager.getConnection("jdbc:derby:memory:bug77475;drop=true").close();
      }
      catch(SQLException ignore) {
         // derby reports a dropped database with an exception
      }
   }

   private static final String[] TABLES = { "a", "b", "c", "d", "e" };

   private static void createTables(Connection conn) throws SQLException {
      try(Statement stmt = conn.createStatement()) {
         for(String table : TABLES) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(SQLException ignore) {
               // first run
            }

            stmt.executeUpdate("create table " + table + " (id int)");
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
         try(PreparedStatement insert = conn.prepareStatement("insert into " + table + " values (?)")) {
            int count = random.nextInt(5);

            for(int i = 0; i < count; i++) {
               int value = random.nextInt(4);

               if(value == 0) {
                  insert.setNull(1, Types.INTEGER);
               }
               else {
                  insert.setInt(1, value);
               }

               insert.addBatch();
            }

            insert.executeBatch();
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

   private static XJoin join(String column1, String op, String column2) {
      return new XJoin(new XExpression(column1, XExpression.FIELD),
                       new XExpression(column2, XExpression.FIELD), op);
   }

   private static String toXml(UniformSQL sql) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      sql.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   private static UniformSQL fromXml(String xml) throws Exception {
      Element root = DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
      UniformSQL sql = new UniformSQL();
      sql.parseXML(root);
      return sql;
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      // Bug #77434 refuses a RIGHT or FULL join mixed with an inner join, and a nested
      // join on the right of an outer join, without a data source
      sql.setDataSource(GenericJDBCDataSource.create());
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
