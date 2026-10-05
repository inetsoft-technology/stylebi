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
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
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
import static org.mockito.Mockito.mock;

/**
 * Bug #77548, a parsed query saved before the parser recorded where each join came from (Bug
 * #77475) loads with every join at UNKNOWN_CLAUSE. SQLHelper regenerates such joins in the
 * outer-last order (or flattens a parenthesized operand), not in text order, which returned
 * different rows for some nestings, and the sql string was cleared before the regeneration
 * (query cache, merge, vpm). The join clauses of a parse of the saved sql string are now
 * recorded on the saved joins, by position at each query level, when every join has the same
 * columns and op. Otherwise the query is refused (PARSE_FAILED) where the structure is still the
 * one saved, and left unchanged where the sql string is cleared.
 * <p>
 * The saved queries are real 1.1.0 XML (UniformSQLLegacyJoinClauseGraftTest-1.1.0.txt). The
 * fuzz test strips the joinClause attribute from the XML of the current parser instead.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLLegacyJoinClauseGraftTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLLegacyJoinClauseGraftTest {
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

   private static final String DB = "jdbc:derby:memory:bug77548";
   private static final String[] TABLES = {
      "a", "b", "c", "d", "e", "g", "p", "q", "r", "x", "t0", "t1", "t2", "t3", "t4", "t5"
   };

   // the issue's example, a join cycle that legacy regeneration wrote as
   // (((c J d) J e) RIGHT JOIN a) LEFT JOIN b
   private static final String ISSUE = "select a.id, b.id, c.id, d.id, e.id from a left join b " +
      "on a.id = b.id left join c on a.id = c.id join d on c.id = d.id join e on c.id = e.id " +
      "and d.k = e.k";
   // a parenthesized operand that legacy regeneration wrote with the inner join of p on the
   // null-supplying side of the RIGHT join
   private static final String NESTED = "select b.id, e.id, g.id, p.id from b left join " +
      "(g join e on g.id = e.id) on b.id = e.id join p on e.id = p.id";
   // the old grammar recorded the outer join of a parenthesized operand from b (Bug #77516)
   private static final String ORIENTATION = "select a.id, b.id, c.id from a left join " +
      "(b join c on b.id = c.id) on b.id = a.id";
   // the old grammar recorded the where clause comparison a.k = b.k of the outer joined pair
   // as a join, the current one as a condition
   private static final String WHERE_PAIR = "select a.id, b.id, c.id from a left join b on " +
      "a.id = b.id join c on a.id = c.id where a.k = b.k";
   // the same join in an ON and in the where clause
   private static final String SAME_JOIN = "select a.id, b.id, c.id from a left join b on " +
      "a.id = b.id join c on a.id = c.id where a.id = c.id";
   private static final String IN_SUBQUERY = "select x.id from x where x.id in (select b.id " +
      "from b left join (g join e on g.id = e.id) on b.id = e.id join p on e.id = p.id)";
   private static final String EXISTS_SUBQUERY = "select x.id from x where exists (select 1 " +
      "from b left join c on b.id = c.id join d on c.id = d.id where b.k = x.k)";
   private static final String INNER_CYCLE = "select a.id, b.id, c.id from a join b on " +
      "a.id = b.id join c on b.id = c.id and a.k = c.k";
   // Bug #77674 (V0), an ON that names no new table
   private static final String NO_NEW_TABLE = "select c.id, d.id, e.id, p.id, q.id, r.id from " +
      "d left join c on d.id = c.id join e on e.id = c.id, p join q on p.id = q.id join r on " +
      "p.k = q.k";
   // Bug #77674 (V19), refused by the current parser
   private static final String NO_NEW_TABLE_REFUSED = "select c.id, d.id, e.id, p.id, q.id, " +
      "q2.id, r.id from d left join c on d.id = c.id join e on e.id = c.id, (p join q on " +
      "p.id = q.id) join (r join q q2 on r.id = q2.id) on p.k = q.k and r.k = q2.k";
   private static final String PG_QUOTED = "select \"a\".\"id\", \"b\".\"id\" from \"a\" left " +
      "join \"b\" on \"a\".\"id\" = \"b\".\"id\"";

   // the saved queries that are recorded, and their rows are compared on derby
   static Stream<String> recordedQueries() {
      return Stream.of(
         "select a.id, b.id from a left join b on a.id = b.id",
         "select c.id, d.id, e.id from d left join c on d.id = c.id join e on e.id = c.id",
         ISSUE, NESTED,
         "select a.id, b.id, c.id from a left join (b join c on b.id = c.id) on a.id = b.id",
         SAME_JOIN,
         "select a.id, b.id, c.id, d.id from a left join b on a.id = b.id join c on " +
            "b.id = c.id left join d on a.id = d.id where b.id = c.id",
         IN_SUBQUERY, EXISTS_SUBQUERY, NO_NEW_TABLE);
   }

   // the saved queries that can't be recorded
   static Stream<String> refusedQueries() {
      return Stream.of(ORIENTATION, WHERE_PAIR, NO_NEW_TABLE_REFUSED);
   }

   @ParameterizedTest
   @MethodSource("recordedQueries")
   void savedJoinsAreRecordedAsTheTextParses(String text) throws Exception {
      JDBCDataSource ds = GenericJDBCDataSource.create();
      UniformSQL legacy = saved(text);
      assertTrue(isLegacy(legacy), text);
      legacy.setDataSource(ds);

      assertEquals(UniformSQL.PARSE_SUCCESS, legacy.getParseResult(), text);
      assertFalse(isLegacy(legacy), text);
      assertFalse(legacy.isLossy(), text);
      assertTrue(XUtil.isParsedSQL(legacy), text);
      assertEquals(text, legacy.getSQLString());

      // byte-identical to the regeneration of a fresh parse
      String generated = regenerate(legacy);
      assertEquals(regenerate(parse(text, ds)), generated, text);
      assertSameRows(text, generated);
      assertRoundTrip(generated, ds);
   }

   @Test
   void legacyRegenerationWasWrong() throws Exception {
      // fails before the fix: the clauses weren't recorded and the sql was regenerated in the
      // outer-last order, which returns different rows
      for(String text : new String[] { ISSUE, NESTED, IN_SUBQUERY }) {
         UniformSQL legacy = saved(text);
         String generated = regenerate(legacy, GenericJDBCDataSource.create());
         assertNotNull(rowMismatch(text, generated), generated);

         legacy.setDataSource(GenericJDBCDataSource.create());
         assertNull(rowMismatch(text, regenerate(legacy)), text);
      }
   }

   @Test
   void issueCycleIsNoLongerLossy() throws Exception {
      // the cycle made it lossy (Bug #77489), its recorded joins regenerate in text order
      UniformSQL legacy = saved(ISSUE);
      legacy.setDataSource(GenericJDBCDataSource.create());
      assertFalse(legacy.isLossy());
      assertTrue(regenerate(legacy).endsWith("from (((a LEFT OUTER JOIN b ON a.id = b.id ) " +
         "LEFT OUTER JOIN c ON a.id = c.id ) INNER JOIN d ON c.id = d.id ) INNER JOIN e ON " +
         "c.id = e.id AND d.k = e.k"), regenerate(legacy));
   }

   @Test
   void sameJoinInAnOnAndTheWhereClauseIsRecordedByPosition() throws Exception {
      UniformSQL legacy = saved(SAME_JOIN);
      legacy.setDataSource(GenericJDBCDataSource.create());
      List<String> clauses = new ArrayList<>();

      for(XJoin join : legacy.getJoins()) {
         clauses.add(join.getExpression1().getValue() + " " + join.getOp() + " " +
                     join.getExpression2().getValue() + " " + join.getJoinClause());
      }

      assertEquals(List.of("a.id *= b.id 1", "a.id = c.id 2", "a.id = c.id -1"), clauses);
      assertTrue(regenerate(legacy).endsWith("INNER JOIN c ON a.id = c.id where a.id = c.id"),
                 regenerate(legacy));
   }

   @Test
   void subqueryJoinsAreRecorded() throws Exception {
      for(String text : new String[] { IN_SUBQUERY, EXISTS_SUBQUERY }) {
         UniformSQL legacy = saved(text);
         legacy.setDataSource(GenericJDBCDataSource.create());
         UniformSQL subquery = subquery(legacy);
         assertNotNull(subquery, text);

         for(XJoin join : subquery.getJoins()) {
            assertNotEquals(XJoin.UNKNOWN_CLAUSE, join.getJoinClause(), text + ": " + join);
         }
      }
   }

   @ParameterizedTest
   @MethodSource("refusedQueries")
   void unmatchedJoinsAreRefused(String text) throws Exception {
      // set a data source
      UniformSQL legacy = saved(text);
      legacy.setDataSource(GenericJDBCDataSource.create());
      assertRefused(text, legacy);

      // loaded by a query of a data source
      JDBCQuery query = loadQuery(text, GenericJDBCDataSource.create());
      UniformSQL loaded = (UniformSQL) query.getSQLDefinition();
      assertNull(loaded.getDataSource());
      assertRefused(text, loaded);
      assertFalse(XUtil.isQueryMergeable(query), text);

      // in isLossy(), before a caller (the query cache) clears the sql string
      UniformSQL lossy = saved(text, GenericJDBCDataSource.create());
      assertTrue(lossy.isLossy(), text);
      assertRefused(text, lossy);
   }

   @Test
   void queryLoadRecordsTheJoinsWithTheQueryDataSource() throws Exception {
      JDBCQuery query = loadQuery(NESTED, GenericJDBCDataSource.create());
      UniformSQL sql = (UniformSQL) query.getSQLDefinition();
      // the sql isn't given the data source when it's loaded
      assertNull(sql.getDataSource());
      assertFalse(isLegacy(sql));
      assertEquals(NESTED, sql.getSQLString());
      assertTrue(XUtil.isQueryMergeable(query));
   }

   @Test
   void queryCacheRegeneratesTheRecordedJoins() throws Exception {
      // JDBCQueryCacheNormalizer clears the sql string field itself after isLossy()
      JDBCDataSource ds = GenericJDBCDataSource.create();
      UniformSQL sql = saved(NESTED, ds);
      assertTrue(isLegacy(sql));
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      assertTrue(isLegacy(sql));

      new JDBCQueryCacheNormalizer(query);
      assertNull(sql.sqlstring);
      String generated = normalize(sql.getSQLString());
      assertEquals(regenerate(parse(NESTED, ds)), generated);
      assertSameRows(NESTED, generated);
   }

   @Test
   void clearedSqlStringRecordsMatchedJoins() throws Exception {
      // the query editor or a merge clears the sql string of a query it changed
      JDBCDataSource ds = GenericJDBCDataSource.create();
      UniformSQL sql = saved(NESTED, ds);
      sql.clearSQLString();
      assertFalse(isLegacy(sql));
      assertEquals(regenerate(parse(NESTED, ds)), normalize(sql.getSQLString()));

      sql = saved(NESTED, ds);
      sql.setSQLString(null);
      assertFalse(isLegacy(sql));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @ParameterizedTest
   @MethodSource("refusedQueries")
   void clearedSqlStringKeepsUnmatchedJoins(String text) throws Exception {
      // the structure may have been changed before the sql string is cleared, so an unmatched
      // query is regenerated as before, not refused
      JDBCDataSource ds = GenericJDBCDataSource.create();
      String before = regenerate(saved(text), ds);

      UniformSQL sql = saved(text, ds);
      sql.clearSQLString();
      assertTrue(isLegacy(sql), text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertEquals(before, normalize(sql.getSQLString()), text);

      sql = saved(text, ds);
      sql.setSQLString(null);
      assertTrue(isLegacy(sql), text);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertEquals(before, normalize(sql.getSQLString()), text);
   }

   @Test
   void clearedSqlStringOfAnEditedQueryIsNotRecorded() throws Exception {
      // a join added in the query editor before the sql string is cleared
      JDBCDataSource ds = GenericJDBCDataSource.create();
      UniformSQL sql = saved(NESTED, ds);
      XJoin join = new XJoin(new XExpression("b.k", XExpression.FIELD), new XExpression("p.k",
         XExpression.FIELD), "=");
      sql.addJoin(join);
      String before = regenerate(sql, ds);
      sql.clearSQLString();
      assertTrue(isLegacy(sql));
      assertEquals(before, normalize(sql.getSQLString()));
   }

   @Test
   void withoutDataSourceNothingIsRecorded() throws Exception {
      UniformSQL sql = saved(NESTED);
      assertFalse(sql.isLossy());
      assertTrue(isLegacy(sql));
      sql.clearSQLString();
      assertTrue(isLegacy(sql));
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
   }

   @Test
   void innerJoinsAreNotRecorded() throws Exception {
      // without an outer join the clauses don't change the regenerated sql, so the inner cycle
      // stays lossy (Bug #77489)
      UniformSQL sql = saved(INNER_CYCLE);
      sql.setDataSource(GenericJDBCDataSource.create());
      assertTrue(isLegacy(sql));
      assertTrue(sql.isLossy());
   }

   @Test
   void editorJoinsAreUnchanged() throws Exception {
      // joins of the query editor (no sql string) and a saved query whose sql string was
      // cleared before it was saved can't be told apart, and keep their regenerated sql
      JDBCDataSource ds = GenericJDBCDataSource.create();

      for(String text : new String[] { NESTED, ISSUE, ORIENTATION }) {
         UniformSQL editor = parse(text, ds);
         editor.clearSQLString();

         for(XJoin join : editor.getJoins()) {
            join.setJoinClause(XJoin.UNKNOWN_CLAUSE);
         }

         SQLHelper helper = SQLHelper.getSQLHelper(editor);
         helper.setUniformSql(editor.clone());
         String before = normalize(helper.generateSentence());

         UniformSQL copy = editor.clone();
         copy.setDataSource(null);
         copy.setDataSource(ds);
         assertFalse(copy.isLossy());
         copy.clearSQLString();
         copy.setSQLString(null);
         assertTrue(isLegacy(copy), text);
         assertEquals(before, normalize(copy.getSQLString()), text);

         UniformSQL cleared = saved(text);
         cleared.clearSQLString();
         cleared.setDataSource(ds);
         assertTrue(isLegacy(cleared), text);
      }
   }

   @Test
   void postgresqlQuotedQueriesAreRecorded() throws Exception {
      // the old parse of a quoting data source saved "a"."id", the current one "a".id when the
      // column was written quoted (Bug #77558). The saved columns are kept
      JDBCDataSource ds = dataSource("postgresql");

      for(Fixture fixture : fixtures()) {
         if(!"postgresql".equals(fixture.type)) {
            continue;
         }

         UniformSQL legacy = load(fixture.xml);
         assertTrue(isLegacy(legacy), fixture.text);
         legacy.setDataSource(ds);
         assertEquals(UniformSQL.PARSE_SUCCESS, legacy.getParseResult(), fixture.text);
         assertFalse(isLegacy(legacy), fixture.text);

         // the select list of the quoted text is regenerated as saved, see below
         if(!PG_QUOTED.equals(fixture.text)) {
            assertEquals(regenerate(parse(fixture.text, ds)), regenerate(legacy), fixture.text);
         }
      }

      UniformSQL quoted = savedFixture(PG_QUOTED, "postgresql");
      quoted.setDataSource(ds);
      assertEquals("select \"a\".\"id\", \"b\".\"id\" from \"a\" LEFT OUTER JOIN \"b\" ON " +
                      "\"a\".\"id\" = \"b\".\"id\"", regenerate(quoted));
   }

   @Test
   void oracleWhereClauseOuterJoinIsRecorded() throws Exception {
      // Oracle without ansi join writes the (+) joins back in the where clause
      JDBCDataSource ds = dataSource("oracle");
      UniformSQL legacy = savedFixture("select a.id, b.id, c.id from a, b, c where " +
                                       "a.id = b.id(+) and b.id = c.id", "oracle");
      legacy.setDataSource(ds);
      assertEquals(UniformSQL.PARSE_SUCCESS, legacy.getParseResult());
      assertFalse(isLegacy(legacy));
      assertEquals("select a.id, b.id, c.id from a, b, c where a.id = b.id(+) and b.id = c.id",
                   regenerate(legacy));
   }

   @ParameterizedTest
   @ValueSource(strings = {
      "select a.id, b.id, c.id from a, b, c where a.id = b.id(+) and b.id = c.id",
      "select a.id, b.id, c.id from a, b, c where a.id *= b.id and b.id = c.id",
      "select a.id, b.id from a, b where a.id = b.id(+)",
      "select a.id, b.id, c.id from a left join b on a.id = b.id, c where a.id = c.id(+)"
   })
   void whereClauseOuterJoinIsRefusedWithAnsiJoins(String text) throws Exception {
      // the reparse is refused with a data source that writes ANSI joins (Bug #77548)
      for(String type : new String[] { "generic", "h2", "oracle-ansi", "postgresql" }) {
         UniformSQL legacy = saved(text);
         legacy.setDataSource(dataSource(type));
         assertRefused(text, legacy);
      }

      UniformSQL legacy = saved(text);
      legacy.setDataSource(dataSource("oracle"));
      assertEquals(UniformSQL.PARSE_SUCCESS, legacy.getParseResult(), text);
      assertFalse(isLegacy(legacy), text);
   }

   /**
    * Random saved queries, the joinClause attribute stripped from the xml of the current
    * parser: the recorded joins regenerate the sql of a fresh parse, and return the rows of the
    * text.
    */
   @Test
   void randomSavedQueriesRegenerateAsTheTextParses() throws Exception {
      Random random = new Random(77548);
      JDBCDataSource ds = GenericJDBCDataSource.create();
      int recorded = 0;

      for(int i = 0; i < 150; i++) {
         String text = randomQuery(random);
         UniformSQL fresh;

         try {
            fresh = parse(text, ds);
         }
         catch(Exception ex) {
            continue;
         }

         if(Arrays.stream(fresh.getJoins()).noneMatch(XJoin::isOuterJoin)) {
            continue;
         }

         fresh.setSQLString(text, false);
         StringWriter buf = new StringWriter();
         fresh.writeXML(new PrintWriter(buf));
         String xml = buf.toString().replaceAll(" joinClause=\"-?\\d+\"", "");
         UniformSQL legacy = load(xml);
         assertTrue(isLegacy(legacy), text);
         legacy.setDataSource(ds);
         assertFalse(isLegacy(legacy), text);
         assertEquals(UniformSQL.PARSE_SUCCESS, legacy.getParseResult(), text);

         String generated = regenerate(legacy);
         assertEquals(regenerate(fresh), generated, text);
         assertSameRows(text, generated);
         recorded++;
      }

      assertTrue(recorded > 50, "recorded " + recorded);
   }

   // a random query of 3 to 6 tables joined by LEFT (50%), inner (40%) and RIGHT (10%) joins,
   // with parenthesized operands, swapped ON operands, extra conditions, repeated joins and
   // where clause joins
   private static String randomQuery(Random random) {
      int count = 3 + random.nextInt(4);
      List<String> used = new ArrayList<>();
      List<String[]> pairs = new ArrayList<>();
      StringBuilder from = new StringBuilder("t0");
      used.add("t0");

      for(int i = 1; i < count; ) {
         String type = joinType(random);

         if(i + 1 < count && random.nextInt(4) == 0) {
            String t1 = "t" + i;
            String t2 = "t" + (i + 1);
            String inner = "(" + t1 + " join " + t2 + " on " + condition(random, t1, t2) + ")";
            String left = used.get(random.nextInt(used.size()));
            from.append(" ").append(type).append(" ").append(inner).append(" on ")
               .append(condition(random, left, t1));
            pairs.add(new String[] { left, t1 });
            used.add(t1);
            used.add(t2);
            i += 2;
         }
         else {
            String t = "t" + i;
            String left = used.get(random.nextInt(used.size()));

            // a later ON repeats an earlier join
            if(!pairs.isEmpty() && random.nextInt(8) == 0) {
               String[] pair = pairs.get(random.nextInt(pairs.size()));
               from.append(" ").append(type).append(" ").append(t).append(" on ")
                  .append(condition(random, left, t)).append(" and ")
                  .append(condition(random, pair[0], pair[1]));
            }
            else {
               from.append(" ").append(type).append(" ").append(t).append(" on ")
                  .append(condition(random, left, t));
            }

            pairs.add(new String[] { left, t });
            used.add(t);
            i++;
         }
      }

      StringBuilder text = new StringBuilder("select ");

      for(int i = 0; i < count; i++) {
         text.append(i > 0 ? ", " : "").append("t").append(i).append(".id");
      }

      text.append(" from ").append(from);

      // a where clause join that is also in an ON
      if(random.nextInt(6) == 0) {
         String[] pair = pairs.get(random.nextInt(pairs.size()));
         text.append(" where ").append(pair[0]).append(".id = ").append(pair[1]).append(".id");
      }

      return text.toString();
   }

   private static String joinType(Random random) {
      int n = random.nextInt(10);
      return n < 5 ? "left join" : n < 9 ? "join" : "right join";
   }

   private static String condition(Random random, String t1, String t2) {
      String cond = random.nextInt(10) < 3 ? t2 + ".id = " + t1 + ".id" : t1 + ".id = " + t2 + ".id";
      return random.nextInt(10) < 3 ? cond + " and " + t1 + ".k = " + t2 + ".k" : cond;
   }

   private static void assertRefused(String text, UniformSQL sql) {
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());
      assertTrue(sql.isLossy(), text);
      // vpm treats it as sql it can't parse
      assertFalse(XUtil.isParsedSQL(sql), text);
   }

   // every join of the query and its subqueries has no recorded clause
   private static boolean isLegacy(UniformSQL sql) {
      boolean joins = false;

      for(UniformSQL level : new UniformSQL[] { sql, subquery(sql) }) {
         for(int i = 0; level != null && i < level.getJoins().length; i++) {
            joins = true;

            if(level.getJoins()[i].getJoinClause() != XJoin.UNKNOWN_CLAUSE) {
               return false;
            }
         }
      }

      return joins;
   }

   // the subquery of the first where condition with one
   private static UniformSQL subquery(UniformSQL sql) {
      return findSubquery(sql.getWhere());
   }

   private static UniformSQL findSubquery(inetsoft.uql.XNode node) {
      if(node instanceof XBinaryCondition) {
         Object value = ((XBinaryCondition) node).getExpression2().getValue();

         if(value instanceof UniformSQL) {
            return (UniformSQL) value;
         }
      }
      else if(node instanceof XUnaryCondition) {
         Object value = ((XUnaryCondition) node).getExpression1().getValue();

         if(value instanceof UniformSQL) {
            return (UniformSQL) value;
         }
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         UniformSQL sql = findSubquery(node.getChild(i));

         if(sql != null) {
            return sql;
         }
      }

      return null;
   }

   private record Fixture(String type, String text, String xml) {
   }

   private static List<Fixture> fixtures() throws IOException {
      List<Fixture> list = new ArrayList<>();

      try(InputStream in = UniformSQLLegacyJoinClauseGraftTest.class.getResourceAsStream(
         "UniformSQLLegacyJoinClauseGraftTest-1.1.0.txt");
          BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
      {
         String line;

         while((line = reader.readLine()) != null) {
            if(!line.isEmpty() && !line.startsWith("#")) {
               String[] parts = line.split("\\|", 3);
               list.add(new Fixture(parts[0], parts[1], parts[2]));
            }
         }
      }

      return list;
   }

   // a query saved by 1.1.0, parsed without a data source
   private static UniformSQL saved(String text) throws Exception {
      return savedFixture(text, "none");
   }

   // a query saved by 1.1.0 that already has a data source when it's loaded, so it's only
   // checked by isLossy() or where its sql string is cleared
   private static UniformSQL saved(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parseXML(element(savedXml(text, "none")));
      return sql;
   }

   private static UniformSQL savedFixture(String text, String type) throws Exception {
      return load(savedXml(text, type));
   }

   private static String savedXml(String text, String type) throws IOException {
      for(Fixture fixture : fixtures()) {
         if(fixture.text.equals(text) && fixture.type.equals(type)) {
            return fixture.xml;
         }
      }

      throw new IllegalArgumentException("no saved xml: " + type + " " + text);
   }

   // a query of a data source, loaded with the saved sql
   private static JDBCQuery loadQuery(String text, JDBCDataSource ds) throws Exception {
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.parseXML(element("<query_jdbc>" + savedXml(text, "none") + "</query_jdbc>"));
      return query;
   }

   private static UniformSQL load(String xml) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parseXML(element(xml));
      return sql;
   }

   private static Element element(String xml) throws Exception {
      return DocumentBuilderFactory.newInstance().newDocumentBuilder()
         .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }

   // the sql a merge or the query cache runs, generated from the structure
   private static String regenerate(UniformSQL sql) {
      UniformSQL copy = sql.clone();
      copy.clearSQLString();
      return normalize(copy.getSQLString());
   }

   // generated as before the fix: the sql string is cleared without a data source, which is
   // set afterwards
   private static String regenerate(UniformSQL sql, JDBCDataSource ds) {
      UniformSQL copy = sql.clone();
      copy.setDataSource(null);
      copy.clearSQLString();
      copy.setDataSource(ds);
      return normalize(copy.getSQLString());
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }

   // the regenerated sql parses and regenerates to a fixed point. A RIGHT OUTER JOIN written
   // for a nested left join swaps its ON operands once (#77475)
   private static void assertRoundTrip(String generated, JDBCDataSource ds) throws Exception {
      String regenerated = regenerate(parse(generated, ds));
      assertEquals(regenerated, regenerate(parse(regenerated, ds)), generated);
   }

   private static JDBCDataSource dataSource(String type) {
      if("generic".equals(type)) {
         return GenericJDBCDataSource.create();
      }

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77548_" + type);
      ds.setProductVersion("19");
      ds.setAnsiJoin(type.endsWith("-ansi"));

      switch(type.replace("-ansi", "")) {
      case "h2" -> {
         ds.setDriver("org.h2.Driver");
         ds.setURL("jdbc:h2:mem:test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/db");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:orcl");
         ds.setRuntimeProductName("oracle");
      }
      default -> throw new IllegalArgumentException(type);
      }

      assertEquals(type.replace("-ansi", ""), SQLHelper.getSQLHelper(ds).getSQLHelperType());
      return ds;
   }

   @BeforeAll
   static void createTables() throws SQLException {
      try(Connection conn = DriverManager.getConnection(DB + ";create=true");
          Statement stmt = conn.createStatement())
      {
         for(String table : TABLES) {
            stmt.executeUpdate("create table " + table + " (id int, k int)");
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

   private static void assertSameRows(String text, String generated) throws SQLException {
      assertNull(rowMismatch(text, generated), generated);
   }

   /**
    * Run both queries on random data with nulls, and return the first dataset whose rows
    * differ, null if none does.
    */
   private static String rowMismatch(String text, String generated) throws SQLException {
      Random random = new Random(77548);

      try(Connection conn = DriverManager.getConnection(DB)) {
         for(int i = 0; i < 60; i++) {
            fillTables(conn, random);
            List<String> rows1 = rows(conn, text);
            List<String> rows2 = rows(conn, generated);

            if(!rows1.equals(rows2)) {
               return "dataset " + i + ": " + rows1 + " vs " + rows2;
            }
         }
      }

      return null;
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
            int count = random.nextInt(4);

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
