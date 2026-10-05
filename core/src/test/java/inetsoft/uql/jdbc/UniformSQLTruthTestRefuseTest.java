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

import antlr.RecognitionException;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.util.XUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77735, a truth test, x IS [NOT] TRUE/FALSE/UNKNOWN, parsed into an XSet whose truth
 * value is an XUnaryCondition with an empty op. That op reads back from XML as null, the
 * condition pane has no op for it (#77736), and the removal of an unset parameter left the
 * bare truth value (#77738). A statement with a truth test at any query level fails the
 * parse now, and the original sql runs. The parse fails at the IS, so the partial tree of the
 * failed parse, which the query editor builds its condition pane from, has no truth test.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 SQLHelperNotEqualJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLTruthTestRefuseTest {
   private static final String[] TYPES = { "h2", "h2-ansi", "oracle", "postgresql" };

   @ParameterizedTest
   @ValueSource(strings = {
      // every truth value in a where clause
      "select a.x from a where (a.k = 1) is true",
      "select a.x from a where (a.k = 1) is not true",
      "select a.x from a where (a.k = 1) is false",
      "select a.x from a where (a.k = 1) is not false",
      "select a.x from a where (a.k = 1) is unknown",
      "select a.x from a where (a.k = 1) is not unknown",
      "SELECT A.X FROM A WHERE (A.K = 1) Is Not True",
      "select a.x from a where a.k = 1 and (a.id = $(p)) is false",
      "select a.x from a where not ((a.k = 1) is true)",
      "select a.x from a where ((a.k = 1) is true) is not false",
      "select a.x from a where a.k = 1 or (not (a.id = 2)) is true",
      // having, join conditions
      "select a.k, count(*) from a where a.k > 0 group by a.k having (count(*) > 1) is true",
      "select a.x from a join b on a.id = b.id and (a.k = b.k) is not true",
      "select a.x from a join b on a.id = b.id join c on b.id = c.id and (c.k = 1) is true",
      "select a.x from a left join b on a.id = b.id and (b.k = 1) is true",
      "select a.x from a, b where a.id = b.id and (a.k = b.k) is false",
      // case expressions
      "select case when (a.k = 1) is true then 1 else 0 end c from a",
      "select a.x from a where case when (a.k = 1) is not false then 1 else 0 end = 1",
      "select a.x from a where a.k = 1 order by case when (a.id = 1) is true then 0 else 1 end",
      "select count(*) from a where a.k = 1 group by case when (a.id = 1) is true then 0 end",
      // subqueries and derived tables
      "select a.x from a where a.k in (select b.k from b where (b.id = 1) is true)",
      "select a.x from a where exists (select 1 from b where b.id = a.id and (b.k = 1) is unknown)",
      "select t.x from (select a.x from a where (a.k = 1) is true) t",
      "select a.x, (select max(b.k) from b where (b.id = a.id) is true) m from a",
      "select a.x from a where a.k = (select max(b.k) from b where (b.id = 1) is not true)",
      "select a.x from a where a.k in (select case when (b.id = 1) is true then b.k end from b)",
      // union branches
      "select a.x from a where (a.k = 1) is true union select b.id from b",
      "select a.x from a union select b.id from b where (b.k = 1) is true",
   })
   void truthTestFailsParse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
      assertEquals(text, sql.getSQLString());
      // the partial tree of the failed parse
      assertNull(findTruthTest(sql.getWhere()), text + ": " + sql.getWhere());
      assertNull(findTruthTest(sql.getHaving()), text + ": " + sql.getHaving());

      UniformSQL fresh = new UniformSQL();
      fresh.setSQLString(text, false);
      assertEquals(text, fresh.getSQLString());
      assertTrue(fresh.isLossy(), text);

      for(String type : TYPES) {
         assertFalse(XUtil.isQueryMergeable(query(text, type)), type + ": " + text);
      }

      // the production (asynchronous) parse
      UniformSQL async = parseAsync(text);
      assertEquals(UniformSQL.PARSE_FAILED, async.getParseResult(), text);
      assertFalse(XUtil.isParsedSQL(async), text);
      assertEquals(text, async.getSQLString());

      JDBCQuery query = new JDBCQuery();
      query.setSQLDefinition(async);
      assertFalse(new JDBCQueryCacheNormalizer(query).isClearedSqlString(), text);
      assertEquals(text, async.getSQLString());
   }

   // the parse fails at the IS, before the other checks of the statement
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a where (a.k = 1) is not true",
      "select a.x from a where (a.id = b.id(+)) is true",
      "select a.x from a where a.k in (select b.k from b where (b.id = 1) is unknown)",
      "select case when (a.k = 1) is false then 1 end c from a",
   })
   void truthTestMessage(String text) {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> {
         UniformSQL sql = new UniformSQL();
         sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      });
      assertTrue(ex.getMessage().contains("Unsupported truth test"), ex.getMessage());
   }

   // a truth value that isn't a truth test, and IS [NOT] NULL, still parse
   @ParameterizedTest
   @ValueSource(strings = {
      "select a.x from a where a.flag = true",
      "select a.x from a where a.flag <> false and a.k = 1",
      "select a.x from a where a.k is null",
      "select a.x from a where not (a.k is not null)",
      "select a.x from a left join b on a.id = b.id where b.k is null",
      "select case when a.k is null then true else false end c from a",
   })
   void otherConditionsParse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);

      // round trip
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());
      UniformSQL reparsed = new UniformSQL();
      reparsed.parse(generated, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), generated);
      reparsed.clearSQLString();
      assertEquals(generated, normalize(reparsed.getSQLString()), "round trip");
   }

   // the sql StyleBI writes for a query built in the editor still parses
   @ParameterizedTest
   @ValueSource(strings = { "default", "h2", "h2-ansi", "oracle", "postgresql" })
   void editorSqlParses(String type) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.addTable("a");
      sql.addTable("b");
      sql.getSelection().addColumn("a.x");
      sql.addJoin(new XJoin(new XExpression("a.id", XExpression.FIELD),
                            new XExpression("b.id", XExpression.FIELD), "*="));
      XBinaryCondition flag = new XBinaryCondition(
         new XExpression("a.flag", XExpression.FIELD),
         new XExpression("true", XExpression.EXPRESSION), "=");
      XUnaryCondition isNull = new XUnaryCondition(
         new XExpression("b.k", XExpression.FIELD), "null");
      isNull.setIsNot(true);
      XSet where = new XSet(XSet.AND);
      where.addChild(flag);
      where.addChild(isNull);
      sql.combineWhereByAnd(where);
      sql.setDataSource("default".equals(type) ? null :
                           SQLHelperNotEqualJoinTest.RowCompare.dataSource(type));
      sql.clearSQLString();
      String generated = normalize(sql.getSQLString());

      UniformSQL reparsed = new UniformSQL();
      reparsed.setDataSource(sql.getDataSource());
      reparsed.parse(generated, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, reparsed.getParseResult(), type + ": " + generated);
      assertFalse(reparsed.isLossy(), generated);
   }

   // the refusal compares no keywords, but surefire pins en_US, so check the turkish locale
   // (dotless i) ourselves
   @Test
   void turkishLocale() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));

         for(String text : new String[] {
            "SELECT A.X FROM A WHERE (A.K = 1) IS TRUE",
            "select a.x from a where (a.id = 1) is not unknown",
            "SELECT A.X FROM A WHERE A.ID IN (SELECT B.ID FROM B WHERE (B.K = 1) IS FALSE)" })
         {
            UniformSQL sql = new UniformSQL();
            new SQLProcessor(sql).parse(text);
            assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), text);
            assertEquals(text, sql.getSQLString());
            assertNull(findTruthTest(sql.getWhere()), text);
         }

         UniformSQL sql = new UniformSQL();
         sql.parse("SELECT A.X FROM A WHERE A.K IS NULL AND A.FLAG = TRUE",
                   UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
         assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult());
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   // a truth test in the tree, also in a subquery of a condition
   static XSet findTruthTest(XNode node) {
      if(node instanceof XSet set && SQLHelper.isTruthTest(set)) {
         return set;
      }

      if(node instanceof XBinaryCondition condition) {
         for(XExpression exp : new XExpression[] {
            condition.getExpression1(), condition.getExpression2() })
         {
            if(exp != null && exp.getValue() instanceof UniformSQL subquery) {
               XSet test = findTruthTest(subquery.getWhere());

               if(test != null) {
                  return test;
               }
            }
         }
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         XSet test = findTruthTest(node.getChild(i));

         if(test != null) {
            return test;
         }
      }

      return null;
   }

   private static JDBCQuery query(String text, String type) {
      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      JDBCDataSource ds = SQLHelperNotEqualJoinTest.RowCompare.dataSource(type);
      sql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setDataSource(ds);
      query.setSQLDefinition(sql);
      return query;
   }

   private static UniformSQL parseAsync(String text) throws Exception {
      UniformSQL sql = new UniformSQL();

      synchronized(sql) {
         sql.setSQLString(text);
         sql.wait(20000);
      }

      return sql;
   }

   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
   }
}
