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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77618, XFilterNode.removeJoinsRecursive removed an XJoin under an OR or IS set, e.g. the
 * a.id = b.k of "a.id = b.k or a.k = 1", but UniformSQL.getJoins only descends AND sets and
 * doesn't return it. The query editor rebuilds the where tree from the two (removeAllJoins on a
 * clone, then addJoin for each join), so the comparison was lost. JDBCUtil.fixWhereInfo converts
 * those XJoins to conditions, so only a tree it didn't run on (raw parse, saved raw, or a failed
 * metadata lookup in fixUniformSQLInfo) is hit.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, PluginsTestConfiguration.class,
                                 UniformSQLOrJoinRebuildTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOrJoinRebuildTest {
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
   // the broken rebuild of "a.id = b.k or a.k = 1" differed in 18 of 40 random datasets
   private static final int DATASETS = 40;

   // the editor rebuilds keep a comparison under an OR, on a raw tree, a raw tree saved to XML
   // and (control) a tree fixWhereInfo ran on
   @ParameterizedTest
   @ValueSource(strings = {
      // an OR root
      "a.id = b.k or a.k = 1",
      "a.id = b.id and (a.k = b.k or a.k = 1)",
      // AND inside OR
      "a.id = b.id and ((a.k = b.k and b.id = 1) or a.k = 1)",
      // AND inside OR inside AND, with a NOT under it
      "a.id = b.id and (a.k = 1 or (a.k = b.k and not (b.id = 2 or a.id = b.k)))",
      // a single negated join under an OR
      "a.k = 1 or not (a.id = b.k)",
      // controls
      "a.id = b.id and a.k = b.k",
      "not (a.id = b.k or a.k = 1)",
      "a.id = b.id and not (a.k = b.k or a.k = 1)",
   })
   void editorRebuildKeepsTheComparisonUnderOr(String where) throws Exception {
      String text = SEL + "from a, b where " + where;

      for(String mode : MODES) {
         for(String op : new String[] { "condition", "rejoin", "pane" }) {
            assertRebuildKeepsRows(text, mode, op);
         }
      }
   }

   // the condition pane can't load this "is null" through the server-side converters alone,
   // so it's checked through the node-level condition round trip and the link edits
   @Test
   void editorRebuildKeepsTheComparisonUnderOrWithIsNull() throws Exception {
      String text = SEL + "from a, b where a.id = b.id and (a.k = b.k or b.k is null)";

      for(String mode : MODES) {
         for(String op : new String[] { "condition", "rejoin" }) {
            assertRebuildKeepsRows(text, mode, op);
         }
      }
   }

   @Test
   void reporterQueriesKeepTheComparison() throws Exception {
      for(String mode : new String[] { "raw", "saved" }) {
         assertEquals(SEL.replace(" ai", " as ai").replace(" ak", " as ak")
                         .replace(" bi", " as bi").replace(" bk", " as bk") +
                         "from a, b where (a.id = b.k or a.k = 1)",
                      generate(conditionRoundTrip(parse(SEL + "from a, b where a.id = b.k or " +
                                                           "a.k = 1", mode)), dataSource("h2")),
                      mode);
         assertTrue(generate(rejoin(parse(SEL + "from a, b where a.id = b.id and (a.k = b.k " +
                                             "or a.k = 1)", mode)), dataSource("h2-ansi"))
                       .endsWith("from a INNER JOIN b ON a.id = b.id where (a.k = b.k or " +
                                    "a.k = 1)"), mode);
      }
   }

   // a truth test is an XSet with relation "is"/"is not"; the condition pane can't load one,
   // so these use the link edits. A statement with a truth test fails to parse (#77735), so
   // the tree is the one of a parse saved before that, raw and after an XML round trip, which
   // reads the empty op of the truth value back as "" again. The parameter is the where
   // clause, then the kept truth test as generated.
   @ParameterizedTest
   @ValueSource(strings = {
      "(a.id = b.k) is not true|where ((a.id = b.k) is not true)",
      "(a.id = b.k) is true|where ((a.id = b.k) is true)",
      "a.id = b.id and (a.k = b.k) is not true|where ((a.k = b.k) is not true) and a.id = b.id",
   })
   void linkEditKeepsTheComparisonUnderATruthTest(String param) throws Exception {
      String[] parts = param.split("\\|");

      for(String mode : new String[] { "raw", "saved" }) {
         String generated = generate(rejoin(parseTruthTest(parts[0], mode)), dataSource("h2"));
         assertTrue(generated.endsWith(" from a, b " + parts[1]), mode + ": " + generated);
      }
   }

   // fixUniformSQLInfo skips fixWhereInfo when the metadata lookup throws (database down), so
   // the editor works on the raw tree
   @Test
   void conditionEditAfterAFailedMetadataLookupKeepsTheComparison() throws Exception {
      String text = SEL + "from a, b where a.id = b.k or a.k = 1";
      UniformSQL sql = new UniformSQL();
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      sql.removeAllFields();
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenThrow(new RuntimeException("Can't get connection"));
      JDBCDataSource ds = dataSource("h2");

      assertThrows(RuntimeException.class,
                   () -> JDBCUtil.fixUniformSQLInfo(sql, repository, "admin", ds, null));
      // fixWhereInfo didn't run, the comparison is still an XJoin
      assertTrue(containsJoin(sql.getWhere()), sql.getWhere().toString());
      String generated = generate(conditionRoundTrip(sql), ds);
      assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
         text, generated, DATASETS), generated);
   }

   @Test
   void removeAllJoinsKeepsTheJoinsOfANonAndRoot() {
      XSet root = new XSet(XSet.OR);
      root.addChild(new XJoin(field("a.id"), field("b.k"), "="));
      root.addChild(new XBinaryCondition(field("a.k"), new XExpression("1", XExpression.VALUE),
                                         "="));

      root.removeAllJoins();

      assertEquals("(a.id = b.k or a.k = 1)", root.toString());
   }

   private static boolean containsJoin(XNode node) {
      if(node instanceof XJoin) {
         return true;
      }

      for(int i = 0; node != null && i < node.getChildCount(); i++) {
         if(containsJoin(node.getChild(i))) {
            return true;
         }
      }

      return false;
   }

   private static void assertRebuildKeepsRows(String text, String mode, String op)
      throws Exception
   {
      for(String type : HELPERS) {
         JDBCDataSource ds = dataSource(type);
         String msg = type + " " + mode + " " + op;
         UniformSQL sql = parse(text, mode);
         String generated = generate(switch(op) {
            case "condition" -> conditionRoundTrip(sql);
            case "rejoin" -> rejoin(sql);
            default -> paneRoundTrip(sql, ds);
         }, ds);

         assertFalse(generated.contains("()"), msg + ": " + generated);
         assertEquals(0, SQLHelperWhereOrOuterJoinTest.RowCompare.diffCount(
            text, generated, DATASETS), msg + ": " + generated);
         // the regenerated SQL may gain parentheses once, then it's stable
         String again = SQLHelperWhereOrOuterJoinTest.generate(generated, ds);
         assertEquals(again, SQLHelperWhereOrOuterJoinTest.generate(again, ds),
                      msg + " round trip");
      }
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

      if(!"saved".equals(mode)) {
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

   // the statement without its where clause, with the where clause parsed as condition text,
   // as the statement parse built it before the refusal
   private static UniformSQL parseTruthTest(String where, String mode) throws Exception {
      UniformSQL refused = new UniformSQL();
      new SQLProcessor(refused).parse(SEL + "from a, b where " + where);
      assertEquals(UniformSQL.PARSE_FAILED, refused.getParseResult(), where);

      UniformSQL sql = parse(SEL + "from a, b", "raw");
      XFilterNode condition = new inetsoft.uql.util.sqlparser.SQLParser(
         new inetsoft.uql.util.sqlparser.SQLLexer(new StringReader(where))).search_condition();
      condition.setClause(XFilterNode.WHERE);
      markJoins(condition);
      sql.combineWhereByAnd(condition);

      if(!"saved".equals(mode)) {
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

   private static UniformSQL conditionRoundTrip(UniformSQL sql) {
      return setQueryCondition(sql, getQueryCondition(sql));
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

   private static XExpression field(String name) {
      return new XExpression(name, XExpression.FIELD);
   }
}
