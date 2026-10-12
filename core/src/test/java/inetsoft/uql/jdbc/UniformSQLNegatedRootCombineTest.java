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
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.*;
import inetsoft.uql.util.HierarchyListModel;
import inetsoft.uql.util.XUtil;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.web.portal.model.database.*;
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
 * Bug #77739, UniformSQL.combineWhere treated a negated AND set as an AND junction and added the
 * other condition inside the NOT. The condition pane rebuilds a lone "not (x and y)" group as the
 * where root, so the query's joins (addJoin after the pane save, or a join added on the Links
 * tab) and a VPM row condition (combineWhereByAnd) were negated with it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLNegatedRootCombineTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLNegatedRootCombineTest {
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

   private static final String SEL = "select a.id ai, a.k ak, b.id bi, b.k bk ";
   private static final String[] HELPERS = { "h2", "h2-ansi", "derby-ansi" };
   private static final String[] MODES = { "raw", "saved", "fixed" };
   // the join moved inside the NOT differed in 27 of 40 random datasets
   private static final int DATASETS = 40;

   // a condition pane save keeps the join outside the NOT
   @ParameterizedTest
   @ValueSource(strings = {
      "a.id = b.id and not (a.k = 1 and b.k = 2)",
      "a.id = b.id and not (a.k = b.k and a.k = 1)",
   })
   void paneSaveKeepsTheJoinOutsideTheNot(String where) throws Exception {
      String text = SEL + "from a, b where " + where;

      for(String mode : MODES) {
         for(String type : HELPERS) {
            JDBCDataSource ds = dataSource(type);
            String msg = type + " " + mode;
            UniformSQL sql = paneRoundTrip(parse(text, mode), ds);

            assertFalse(((XSet) sql.getWhere()).isIsNot(), msg + ": " + sql.getWhere());
            assertEquals(1, sql.getJoins().length, msg + ": " + sql.getWhere());
            assertSameRows(text, generate(sql, ds), ds, msg);
         }
      }
   }

   // a join added on the Links tab to a query whose pane-saved where is a lone NOT group,
   // before and after the query is saved
   @Test
   void linksTabJoinOnANegatedRootIsNotNegated() throws Exception {
      String text = SEL + "from a, b where not (a.k = 1 and b.k = 2)";

      for(boolean saved : new boolean[] { false, true }) {
         for(String type : HELPERS) {
            JDBCDataSource ds = dataSource(type);
            String msg = type + " saved=" + saved;
            UniformSQL sql = negatedRoot(text, ds, saved);

            sql.addJoin(new XJoin(field("a.id"), field("b.id"), "="));

            assertEquals(1, sql.getJoins().length, msg + ": " + sql.getWhere());
            assertSameRows(SEL + "from a, b where a.id = b.id and not (a.k = 1 and b.k = 2)",
                           generate(sql, ds), ds, msg);
         }
      }
   }

   // VpmUtil.applyConditions ANDs the VPM row condition to the where with combineWhereByAnd
   @Test
   void andConditionOnANegatedRootIsNotNegated() throws Exception {
      String text = SEL + "from a, b where not (a.k = 1 and b.k = 2)";

      for(boolean saved : new boolean[] { false, true }) {
         for(String type : HELPERS) {
            JDBCDataSource ds = dataSource(type);
            String msg = type + " saved=" + saved;
            UniformSQL sql = negatedRoot(text, ds, saved);

            sql.combineWhereByAnd(
               new XExpressionCondition(new XExpression("b.k = 9", XExpression.EXPRESSION)));

            assertSameRows(text + " and b.k = 9", generate(sql, ds), ds, msg);
         }
      }

      // with the query's join, which the pane save added first
      String joined = SEL + "from a, b where a.id = b.id and not (a.k = 1 and b.k = 2)";

      for(String type : HELPERS) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = paneRoundTrip(parse(joined, "fixed"), ds);

         sql.combineWhereByAnd(
            new XExpressionCondition(new XExpression("b.k < 3", XExpression.EXPRESSION)));

         assertSameRows(joined + " and b.k < 3", generate(sql, ds), ds, type + " joined");
      }
   }

   // a negated AND condition is not a junction the where can be added to either. A parsed
   // where is an AND set, so these set the where the condition pane can save.
   @ParameterizedTest
   @ValueSource(strings = { "bare", "or", "not" })
   void negatedAndConditionDoesNotAbsorbTheWhere(String root) throws Exception {
      for(String type : HELPERS) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(SEL + "from a, b", "fixed");
         sql.setWhere(where(root));
         String text = SEL + "from a, b where (" + sql.getWhere() + ")";
         XSet condition = new XSet(XSet.AND);
         condition.setIsNot(true);
         condition.addChild(binary("b.k", "1"));
         condition.addChild(binary("b.id", "2"));

         sql.combineWhereByAnd(condition);

         assertNotSame(condition, sql.getWhere(), type);
         assertSameRows(text + " and not (b.k = 1 and b.id = 2)", generate(sql, ds), ds,
                        type + " " + root);
      }
   }

   // the negated root a pane save makes is kept by the query's XML, so a saved query reaches
   // the combine with it whenever it's edited or a VPM applies
   @Test
   void savedNegatedRootIsKeptAndCombinedOutsideTheNot() throws Exception {
      String text = SEL + "from a, b where not (a.k = 1 and b.k = 2)";
      JDBCDataSource ds = dataSource("derby-ansi");
      UniformSQL sql = negatedRoot(text, ds, true);

      assertTrue(((XSet) sql.getWhere()).isIsNot(), sql.getWhere().toString());

      sql.addJoin(new XJoin(field("a.id"), field("b.id"), "="));
      sql.combineWhereByAnd(
         new XExpressionCondition(new XExpression("b.k = 9", XExpression.EXPRESSION)));

      assertSameRows(SEL + "from a, b where a.id = b.id and not (a.k = 1 and b.k = 2) and " +
                        "b.k = 9", generate(sql, ds), ds, "saved");
   }

   // controls: a non-negated root, or a negated set that is not the root, is combined as before.
   // The parameter is the where clause, then the generated where after a pane save.
   @ParameterizedTest
   @ValueSource(strings = {
      "a.id = b.id and a.k = 3 and not (a.k = 1 and b.k = 2)" +
         "|where a.k = 3 and (not (a.k = 1 and b.k = 2)) and a.id = b.id",
      "a.id = b.id and not (a.k = 1 or b.k = 2)|where a.id = b.id and (not (a.k = 1 or b.k = 2))",
      "a.id = b.id and not (a.k = 1)|where not (a.k = 1) and a.id = b.id",
      "a.id = b.id and b.k = 3 or not (a.k = 1 and b.k = 2)" +
         "|where (a.id = b.id and b.k = 3) or (not (a.k = 1 and b.k = 2))",
      "a.id = b.id and a.k = b.k|where (a.id = b.id and a.k = b.k)",
   })
   void nonNegatedRootIsCombinedAsBefore(String param) throws Exception {
      String[] parts = param.split("\\|");
      String text = SEL + "from a, b where " + parts[0];
      JDBCDataSource ds = dataSource("h2");
      String generated = generate(paneRoundTrip(parse(text, "fixed"), ds), ds);

      assertTrue(generated.endsWith(" from a, b " + parts[1]), generated);
   }

   @Test
   void nonNegatedAndIsCombinedAsBefore() throws Exception {
      JDBCDataSource ds = dataSource("h2");
      UniformSQL sql = parse(SEL + "from a, b where a.k = 1 and b.k = 2", "fixed");
      sql.combineWhereByAnd(binary("b.id", "9"));

      assertTrue(generate(sql, ds).endsWith(" from a, b where a.k = 1 and b.k = 2 and b.id = 9"),
                 generate(sql, ds));

      // a non-negated AND condition still takes the where in
      for(String root : new String[] { "bare|a.k = 1", "or|(a.k = 1 or b.k = 2)" }) {
         String[] parts = root.split("\\|");
         sql = parse(SEL + "from a, b", "fixed");
         sql.setWhere(where(parts[0]));
         XSet condition = new XSet(XSet.AND);
         condition.addChild(binary("b.k", "1"));
         condition.addChild(binary("b.id", "2"));
         sql.combineWhereByAnd(condition);

         assertSame(condition, sql.getWhere(), root);
         assertTrue(generate(sql, ds).endsWith(" from a, b where b.k = 1 and b.id = 2 and " +
                                                  parts[1]), generate(sql, ds));
      }
   }

   // a non-negated AND condition takes in a negated where, not the other way round
   @Test
   void andSetOnANegatedRootIsNotNegated() throws Exception {
      for(String type : HELPERS) {
         JDBCDataSource ds = dataSource(type);
         UniformSQL sql = parse(SEL + "from a, b", "fixed");
         sql.setWhere(where("not"));
         XSet condition = new XSet(XSet.AND);
         condition.addChild(binary("b.k", "1"));
         condition.addChild(binary("b.id", "2"));

         sql.combineWhereByAnd(condition);

         assertSameRows(SEL + "from a, b where not (a.k = 1 and b.id = 2) and b.k = 1 and " +
                           "b.id = 2", generate(sql, ds), ds, type);
      }
   }

   // a where root that is not an AND set: a.k = 1, a.k = 1 or b.k = 2, not (a.k = 1 and b.id = 2)
   private static XFilterNode where(String root) {
      if("bare".equals(root)) {
         return binary("a.k", "1");
      }

      XSet set = new XSet("or".equals(root) ? XSet.OR : XSet.AND);
      set.setIsNot("not".equals(root));
      set.addChild(binary("a.k", "1"));
      set.addChild(binary("or".equals(root) ? "b.k" : "b.id", "2"));
      return set;
   }

   // the where of a pane save of a lone "not (x and y)" group, which is that negated group
   private static UniformSQL negatedRoot(String text, JDBCDataSource ds, boolean saved)
      throws Exception
   {
      UniformSQL sql = paneRoundTrip(parse(text, "fixed"), ds);
      assertTrue(((XSet) sql.getWhere()).isIsNot(), sql.getWhere().toString());
      return saved ? xmlRoundTrip(sql) : sql;
   }

   private static void assertSameRows(String original, String generated, JDBCDataSource ds,
                                      String msg)
      throws Exception
   {
      assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
         original, generated, DATASETS), msg + ": " + generated);
      // the regenerated SQL may gain parentheses once, then it's stable
      String again = SQLHelperWhereOrOuterJoinTest.generate(generated, ds);
      assertEquals(again, SQLHelperWhereOrOuterJoinTest.generate(again, ds), msg + " round trip");
   }

   /**
    * Parses the text. A raw tree is the parse tree, a saved tree is the raw tree XML
    * round-tripped, and a fixed tree is the parse tree after fixWhereInfo, which is what the
    * editor normally works on.
    */
   private static UniformSQL parse(String text, String mode) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);

      if("fixed".equals(mode)) {
         JDBCUtil.fixWhereInfo(sql);
      }

      return "saved".equals(mode) ? xmlRoundTrip(sql) : sql;
   }

   private static UniformSQL xmlRoundTrip(UniformSQL sql) throws Exception {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(buffer.toString())).getDocumentElement());
      return loaded;
   }

   // QueryManagerService.getQueryCondition(CONDITION_WHERE)
   private static XFilterNode getQueryCondition(UniformSQL sql) {
      if(sql.getWhere() == null) {
         return null;
      }

      XFilterNode condition = (XFilterNode) sql.getWhere().clone();
      condition.removeAllJoins();
      return condition instanceof XJoin ? null : condition;
   }

   // QueryManagerService.setQueryCondition(CONDITION_WHERE)
   private static UniformSQL setQueryCondition(UniformSQL sql, XFilterNode condition) {
      XJoin[] joins = sql.getJoins();
      sql.setWhere(condition);

      for(int i = 0; joins != null && i < joins.length; i++) {
         sql.addJoin(joins[i]);
      }

      return sql;
   }

   // QueryManagerService.createConditions, then createConditionXFilterNode and
   // setQueryCondition, as the condition pane does
   private static UniformSQL paneRoundTrip(UniformSQL sql, JDBCDataSource ds) throws Exception {
      List<DataConditionItem> conditions = new ArrayList<>();
      HierarchyList list = new FilterList();

      for(HierarchyItem item : XUtil.constructConditionList(getQueryCondition(sql))) {
         list.append(item);
      }

      list.validate(false);

      for(int i = 0; i < list.getSize(); i++) {
         HierarchyItem item = list.getItem(i);

         if(item instanceof XSetItem) {
            XSet set = ((XSetItem) item).getXSet();
            Conjunction conjunction = new Conjunction();
            conjunction.setLevel(item.getLevel());
            conjunction.setValue(set.toString());
            conjunction.setIsNot(set.isIsNot());
            conjunction.setConjunction(set.getRelation());
            conjunction.setJunc(true);
            conditions.add(conjunction);
         }
         else {
            Clause clause = JDBCUtil.createCondition(((XFilterNodeItem) item).getNode(), ds);
            clause.setLevel(item.getLevel());
            conditions.add(clause);
         }
      }

      HierarchyListModel model = new HierarchyListModel(new FilterList());

      for(DataConditionItem condition : conditions) {
         if(condition instanceof Clause) {
            model.append(new XFilterNodeItem(JDBCUtil.createXFilterNode((Clause) condition),
                                             condition.getLevel()));
         }
         else if(condition instanceof Conjunction) {
            XSet set = new XSet(((Conjunction) condition).getConjunction());
            set.setIsNot(((Conjunction) condition).isIsNot());
            model.append(new XSetItem(set, condition.getLevel()));
         }
      }

      model.fixConditions();
      XFilterNode node = conditions.isEmpty() ? null
         : new ConditionListHandler().createXFilterNode(model.getHierarchyList());
      return setQueryCondition(sql, node);
   }

   private static String generate(UniformSQL sql, JDBCDataSource ds) {
      sql.setDataSource(ds);
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static JDBCDataSource dataSource(String type) {
      return SQLHelperWhereOrOuterJoinTest.RowCompare.dataSource(type);
   }

   private static XBinaryCondition binary(String column, String value) {
      return new XBinaryCondition(field(column), new XExpression(value, XExpression.VALUE), "=");
   }

   private static XExpression field(String name) {
      return new XExpression(name, XExpression.FIELD);
   }
}
