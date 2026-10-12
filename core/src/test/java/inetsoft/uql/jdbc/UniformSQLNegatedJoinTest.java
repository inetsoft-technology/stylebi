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

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #77553, UniformSQL.getJoins() returned the joins under a negated AND set, e.g. the
 * a.id = b.k of "not (a.id = b.k and a.k = 1)", as joins of the query. The query editor rebuilds
 * the where tree from getJoins() (removeAllJoins, then addJoin for each join), so the comparison
 * was pulled out of the NOT, and XFilterNode.removeJoinsRecursive dropped the NOT of a set it
 * collapsed to one operand.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLNegatedJoinTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("slow")
class UniformSQLNegatedJoinTest {
   // a data source with a real driver and URL needs the credential service, and the JDBC
   // driver types of Config to pick its SQL helper
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

   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk, b.j bj ";
   private static final String SEL3 = "select a.id ai, a.k ak, b.k bk, c.k ck, c.j cj ";
   private static final String[] HELPERS = { "h2", "h2-ansi", "derby-ansi" };
   // the broken rebuilds differed in about 35-70 of 100 random datasets
   private static final int DATASETS = 40;

   @Test
   void joinsUnderNotAreNotJoinsOfTheQuery() throws Exception {
      assertJoins("[]", SEL + "from a, b where not (a.id = b.k and a.k = 1)");
      assertJoins("[]", SEL + "from a, b where not (a.k = 1 or (a.id = b.k and b.j = 1))");
      assertJoins("[]", SEL + "from a, b where not ((a.id = b.k and a.k = 1) and b.j = 1)");
      assertJoins("[a.id = b.id]",
                  SEL + "from a, b where a.id = b.id and not (a.k = b.k and b.j = 1)");
      // a single negated join is still a join, written as <> in an ANSI ON
      assertJoins("[not (a.id = b.k)]", SEL + "from a, b where not (a.id = b.k)");
      assertJoins("[a.id = b.id, not (a.id = b.k)]",
                  SEL + "from a, b where a.id = b.id and not (a.id = b.k)");
   }

   // the editor operations rebuild the where tree from getJoins(); the NOT and the comparisons
   // in it must stay where they are, on a fresh parse and on a tree saved before the fix
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a, b where not (a.id = b.k and a.k = 1)",
      SEL + "from a, b where not (a.id = b.k and a.k = 1 and b.j = 2)",
      SEL + "from a, b where a.id = b.id and not (a.k = b.k and b.j = 1)",
      SEL + "from a join b on a.id = b.id where not (a.k = b.k and b.j = 1)",
      SEL + "from a, b where a.id = b.k and not (a.id = b.k and a.k = 1)",
      SEL + "from b, a where not (a.id = b.k and a.k = 1)",
      SEL + "from a, b where not ((a.id = b.k and a.k = 1) and b.j = 1)",
      SEL + "from a, b where not (a.k = 1 or (a.id = b.k and b.j = 1))",
      // a NOT made only of joins was left as an invalid "not ()"
      SEL3 + "from a, b, c where not (a.id = b.k and b.k = c.k)",
      SEL3 + "from a, b, c where a.id = b.id and not (b.k = c.k and c.j = 1)",
      SEL3 + "from a left join b on a.id = b.id, c where not (b.k = c.k and c.j = 1)",
      // controls
      SEL + "from a, b where not (a.id = b.k)",
      SEL + "from a, b where a.id = b.id and not (a.id = b.k)",
      SEL + "from a, b where not (a.id = b.k or a.k = 1)",
      SEL + "from a, b where a.k = 2 or (a.id = b.k and b.j = 1)",
   })
   void editorRebuildKeepsTheNot(String text) throws Exception {
      // a saved tree has no XJoin under an OR, fixWhereInfo has always converted those
      boolean[] modes = text.contains(" or ") ? new boolean[] { false }
         : new boolean[] { false, true };

      for(boolean saved : modes) {
         for(String type : HELPERS) {
            JDBCDataSource ds = SQLHelperWhereOrOuterJoinTest.RowCompare.dataSource(type);
            String expected = generate(parse(text, saved), ds);
            String msg = type + " saved=" + saved;

            assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
               text, expected, DATASETS), msg + ": " + expected);

            for(String op : new String[] { "rejoin", "condition" }) {
               UniformSQL sql = parse(text, saved);
               String generated = generate(
                  "rejoin".equals(op) ? rejoin(sql) : conditionRoundTrip(sql), ds);

               assertFalse(generated.contains("()"), msg + " " + op + ": " + generated);
               assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
                  text, generated, DATASETS), msg + " " + op + ": " + generated);
               // the regenerated SQL may gain parentheses once, then it's stable
               String again = SQLHelperWhereOrOuterJoinTest.generate(generated, ds);
               assertEquals(again, SQLHelperWhereOrOuterJoinTest.generate(again, ds),
                            msg + " " + op + " round trip");
            }
         }
      }
   }

   @Test
   void reporterQueryKeepsTheNotAfterAJoinEdit() throws Exception {
      String text = SEL + "from a, b where not (a.id = b.k and a.k = 1)";

      for(boolean saved : new boolean[] { false, true }) {
         assertTrue(generate(rejoin(parse(text, saved)), dataSource("h2-ansi"))
                       .endsWith("from a, b where not (a.id = b.k and a.k = 1)"),
                    "saved=" + saved);
         assertTrue(generate(conditionRoundTrip(parse(text, saved)), dataSource("h2"))
                       .endsWith("from a, b where not (a.id = b.k and a.k = 1)"),
                    "saved=" + saved);
      }
   }

   // Bug #77481, a legacy outer join under a NOT set with other conditions can't be generated:
   // "ON a.id = b.k where not (a.k = 1)" isn't "not (a.id = b.k and a.k = 1)", so the parse
   // fails and the original sql runs
   @ParameterizedTest
   @ValueSource(strings = {
      SEL + "from a, b where not (a.id *= b.k and a.k = 1)",
      SEL + "from a, b where not (a.id = b.k (+) and a.k = 1)",
   })
   void outerJoinUnderNotIsRefused(String text) {
      Exception ex = assertThrows(Exception.class, () ->
         new UniformSQL().parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD));
      assertTrue(ex.getMessage().contains("Unsupported outer join condition"), ex.getMessage());

      UniformSQL sql = new UniformSQL();
      new SQLProcessor(sql).parse(text);
      assertEquals(UniformSQL.PARSE_FAILED, sql.getParseResult());
      assertEquals(text, sql.getSQLString());
      assertTrue(sql.isLossy());
   }

   // removeAllJoins hoists the only operand left in a set and must keep the set's NOT
   @Test
   void hoistedOperandKeepsTheNot() {
      XSet not = new XSet(XSet.AND);
      not.setIsNot(true);
      not.addChild(new XJoin(field("a.id"), field("b.k"), "*="));
      not.addChild(new XBinaryCondition(field("a.k"), new XExpression("1", XExpression.VALUE),
                                        "="));
      XSet root = new XSet(XSet.AND);
      root.addChild(new XJoin(field("a.id"), field("b.id"), "="));
      root.addChild(not);

      root.removeAllJoins();

      assertEquals(1, root.getChildCount());
      XFilterNode operand = (XFilterNode) root.getChild(0);
      assertTrue(operand.isIsNot(), operand.toString());
      assertEquals("not a.k = 1", operand.toString());
   }

   // VPM adds its lookup joins with addJoin, which skips a join already in the query; a
   // comparison under a NOT is not one, so the join is added
   @Test
   void addJoinEqualToAComparisonUnderNotIsAdded() throws Exception {
      for(boolean saved : new boolean[] { false, true }) {
         UniformSQL sql = parse(SEL + "from a, b where not (a.id = b.k and a.k = 1)", saved);
         sql.addJoin(new XJoin(field("a.id"), field("b.k"), "="));
         assertEquals("[a.id = b.k]", Arrays.toString(sql.getJoins()), "saved=" + saved);
         assertTrue(generate(sql, dataSource("h2-ansi"))
                       .endsWith("from a INNER JOIN b ON a.id = b.k where not (a.id = b.k " +
                                    "and a.k = 1)"), "saved=" + saved);
      }
   }

   // remove.useless.joinTable keeps a table that is used only in a comparison under a NOT, also
   // in a tree saved before the fix, where the comparison is still an XJoin
   @Test
   void uselessJoinTableKeepsATableUsedUnderNot() throws Exception {
      String old = SreeEnv.getProperty("remove.useless.joinTable");
      SreeEnv.setProperty("remove.useless.joinTable", "true");

      try {
         for(boolean saved : new boolean[] { false, true }) {
            UniformSQL sql = parse("select a.id ai from a, b where not (a.id = b.k and a.k = 1)",
                                   saved);
            XJoin[] joins = sql.getJoins();
            sql.setOriginalJoins(joins == null ? null : Arrays.asList(joins));
            sql.clearSQLString();
            JDBCQuery query = new JDBCQuery();
            query.setDataSource(dataSource("h2"));
            query.setSQLDefinition(sql);

            XUtil.removeTable(query, sql, null);

            assertEquals(2, sql.getSelectTable().length, "saved=" + saved);
            assertTrue(generate(sql, dataSource("h2"))
                          .endsWith("from a, b where not (a.id = b.k and a.k = 1)"),
                       "saved=" + saved);
         }
      }
      finally {
         if(old == null) {
            SreeEnv.remove("remove.useless.joinTable");
         }
         else {
            SreeEnv.setProperty("remove.useless.joinTable", old);
         }
      }
   }

   private static void assertJoins(String expected, String text) throws Exception {
      for(boolean saved : new boolean[] { false, true }) {
         assertEquals(expected, Arrays.toString(parse(text, saved).getJoins()),
                      text + " saved=" + saved);
      }
   }

   /**
    * Parses the text as the query editor does (fixWhereInfo after the parse). A saved tree is
    * the raw parse tree, XML round-tripped without fixWhereInfo, which is how a tree saved
    * before the fix still holds its joins under a NOT as XJoins.
    */
   private static UniformSQL parse(String text, boolean saved) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);

      if(!saved) {
         JDBCUtil.fixWhereInfo(sql);
         return sql;
      }

      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      return loaded;
   }

   // QueryGraphModelService.processEditJoin/processDeleteJoins/processRenameJoins, when the
   // edited join is another one
   private static UniformSQL rejoin(UniformSQL sql) {
      UniformSQL clone = sql.clone();
      clone.removeAllJoins();
      XJoin[] joins = sql.getJoins();

      for(int i = 0; joins != null && i < joins.length; i++) {
         clone.addJoin(joins[i]);
      }

      return clone;
   }

   // QueryManagerService.getQueryCondition, fed back through setQueryCondition(CONDITION_WHERE)
   private static UniformSQL conditionRoundTrip(UniformSQL sql) {
      XFilterNode condition = null;

      if(sql.getWhere() != null) {
         condition = (XFilterNode) sql.getWhere().clone();
         condition.removeAllJoins();

         if(condition instanceof XJoin) {
            condition = null;
         }
      }

      XJoin[] joins = sql.getJoins();
      sql.setWhere(condition);

      for(int i = 0; joins != null && i < joins.length; i++) {
         sql.addJoin(joins[i]);
      }

      return sql;
   }

   private static String generate(UniformSQL sql, JDBCDataSource ds) {
      sql.setDataSource(ds);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperWhereOrOuterJoinTest.RowCompare.dataSource(type);
   }

   private static XExpression field(String name) {
      return new XExpression(name, XExpression.FIELD);
   }
}
