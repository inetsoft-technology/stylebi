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
import inetsoft.uql.XRepository;
import inetsoft.uql.util.sqlparser.SQLLexer;
import inetsoft.uql.util.sqlparser.SQLParser;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77481 / #77491 shape C, a join under "(...) IS [NOT] TRUE/FALSE/UNKNOWN" is not
 * ANDed with the where clause. An outer join there ((+), *= or =*) would be generated in
 * the from clause and lose the IS ("LEFT OUTER JOIN d ON .. where (true)"), so it fails
 * the parse and the original sql runs. An inner join there must stay in the where clause
 * with its IS, also on the ANSI generation path.
 * <p>
 * Bug #77735, a statement with a truth test fails to parse now, at the IS, before the
 * outer join is checked. A saved parse still has the inner join under IS, which must be
 * generated as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                  UniformSQLOuterJoinIsTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOuterJoinIsTest {
   @Configuration
   @Import(CredentialService.class)
   static class Config {
      @Bean
      inetsoft.uql.util.Config config(Plugins plugins) {
         return new inetsoft.uql.util.Config(plugins);
      }

      // the derby helper asks the repository for the database version
      @Bean
      XRepository repository() {
         return mock(XRepository.class);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = {
      // reporter's example and its variants
      "select e.ename from emp e, dept d where (e.deptno = d.deptno(+)) is true",
      "select e.ename from emp e, dept d where (e.deptno = d.deptno(+)) is not true",
      "select e.ename from emp e, dept d where (e.deptno = d.deptno(+)) is false",
      "select e.ename from emp e, dept d where (e.deptno = d.deptno(+)) is not false",
      "select e.ename from emp e, dept d where (e.deptno = d.deptno(+)) is unknown",
      "select a.x from a, b where (a.id (+)= b.id) is true",
      // the *= and =* forms
      "select a.x from a, b where (a.id *= b.id) is true",
      "select a.x from a, b where (a.id *= b.id) is not true",
      "select a.x from a, b where (a.id =* b.id) is false",
      // next to other conditions, and at a subquery level
      "select a.x from a, b where a.k = 1 and (a.id = b.id(+)) is true",
      "select a.x from a, b where (a.id = b.id(+) and a.k = 1) is true",
      "select a.x from a where exists (select 1 from c, d where (c.id = d.id(+)) is true)",
   })
   void outerJoinUnderIsFailsCleanly(String text) throws Exception {
      RecognitionException ex = assertThrows(RecognitionException.class, () -> parse(text));
      assertTrue(ex.getMessage().contains("Unsupported truth test"), ex.getMessage());

      for(String type : new String[] { null, "h2-ansi", "derby-ansi" }) {
         UniformSQL sql = new UniformSQL();

         if(type != null) {
            sql.setDataSource(SQLHelperWhereOrOuterJoinTest.RowCompare.dataSource(type));
         }

         new SQLProcessor(sql).parse(text);
         assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult(), type);
         assertEquals(text, sql.getSQLString(), type);
         assertTrue(sql.isLossy(), type);
      }
   }

   /**
    * An inner join under IS is a plain condition of the where clause. With the ANSI join
    * option it was taken into "INNER JOIN b ON a.id = b.id" and the IS set was left as
    * "where (true)". The generator writes "(a.id = b.id) is not true" as "(a.id = b.id is
    * not true)", which returns the same rows on H2 since = binds tighter than IS. Derby has
    * no IS TRUE, so the rows aren't compared here.
    */
   @ParameterizedTest
   @ValueSource(strings = {
      "where (a.id = b.id) is not true",
      "where (a.id = b.id) is true",
      "where (a.id = b.id) is false",
      "where (a.id = b.id) is not false",
      "where a.k = 1 and (a.id = b.id) is not true",
      "where (a.id = b.id and a.k = 1) is not true",
   })
   void innerJoinUnderIsStaysInWhere(String tail) throws Exception {
      String text = "select a.id ai, a.k ak, b.id bi, b.k bk from a, b " + tail;
      UniformSQL refused = new UniformSQL();
      new SQLProcessor(refused).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, refused.getParseResult());

      for(String type : new String[] { null, "h2", "h2-ansi", "derby-ansi" }) {
         JDBCDataSource ds = type == null ? null :
            SQLHelperWhereOrOuterJoinTest.RowCompare.dataSource(type);
         String generated = generateSaved(text, ds);
         String lower = generated.toLowerCase();

         assertFalse(lower.contains(" join "), type + ": " + generated);
         assertTrue(lower.contains(" is "), type + ": " + generated);
         assertFalse(lower.contains("(true)"), type + ": " + generated);
         // round trip: the regenerated sql parses and regenerates to itself
         assertEquals(generated, generateSaved(generated, ds), type + " round trip");
      }
   }

   // the tree of a parse saved before the refusal: the statement without its where clause,
   // and the where clause parsed as condition text, as the statement parse built it
   private static String generateSaved(String text, JDBCDataSource ds) throws Exception {
      int index = text.indexOf(" where ");
      UniformSQL sql = new UniformSQL();
      sql.parse(text.substring(0, index), UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);

      XFilterNode where = new SQLParser(new SQLLexer(new StringReader(
         text.substring(index + 7)))).search_condition();
      where.setClause(XFilterNode.WHERE);
      markJoins(where);
      sql.combineWhereByAnd(where);
      sql.setDataSource(ds);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static void markJoins(XNode node) {
      if(node instanceof XJoin join) {
         join.setJoinClause(XJoin.WHERE_CLAUSE);
      }
      else if(node instanceof XSet) {
         for(int i = 0; i < node.getChildCount(); i++) {
            markJoins(node.getChild(i));
         }
      }
   }

   private static UniformSQL parse(String text) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      return sql;
   }
}
