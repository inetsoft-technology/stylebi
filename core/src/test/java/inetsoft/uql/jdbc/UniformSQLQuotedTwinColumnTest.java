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
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77573. The quoted and the unquoted spelling of one name ({@code "MixedCase"} and
 * {@code MixedCase}, or {@code t."MixedCase"} and {@code t.MixedCase}) are stored as the same
 * text, and the quoted flag was kept by that text. In the select list, GROUP BY and ORDER BY of
 * one query both spellings were generated quoted, so on a case-folding database the unquoted
 * one read the quoted column. ORDER BY items of the same text also merged into one item with
 * the last direction. The flag is now kept per select column, per GROUP BY field and per
 * ORDER BY item.
 *
 * The rows of the regenerated sql are compared with the rows of the original sql on Derby,
 * where "MixedCase" and MIXEDCASE are different columns and an unquoted MixedCase is
 * MIXEDCASE.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLQuotedTwinColumnTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLQuotedTwinColumnTest {
   @Test
   void twinsKeepTheirOwnQuotes() throws Exception {
      assertEquals("select \"MixedCase\", MixedCase as b from t",
                   regenerate("select \"MixedCase\", MixedCase as b from t"));
      assertEquals("select MixedCase, \"MixedCase\" as b from t",
                   regenerate("select MixedCase, \"MixedCase\" as b from t"));
      assertEquals("select t.\"MixedCase\", t.MixedCase as b from t",
                   regenerate("select t.\"MixedCase\", t.MixedCase as b from t"));
      assertEquals("select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase",
                   regenerate("select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase"));
      assertEquals("select t.MixedCase, t.\"MixedCase\" as b from t group by t.MixedCase, t.\"MixedCase\"",
                   regenerate("select t.MixedCase, t.\"MixedCase\" as b from t " +
                              "group by t.MixedCase, t.\"MixedCase\""));
      // an alias is sorted by its own column, with its quoting
      assertEquals("select \"MixedCase\" as a, MixedCase as b from t order by MixedCase asc, \"MixedCase\" asc",
                   regenerate("select \"MixedCase\" as a, MixedCase as b from t order by b, a"));
   }

   /**
    * Two ORDER BY items of the same text with other quoting are two sort keys, each with its
    * own direction.
    */
   @Test
   void orderByTwinsAreTwoItems() throws Exception {
      assertEquals("select \"MixedCase\", MixedCase as b from t order by MixedCase desc, \"MixedCase\" asc",
                   regenerate("select \"MixedCase\", MixedCase as b from t " +
                              "order by MixedCase desc, \"MixedCase\""));
      assertEquals("select \"MixedCase\", MixedCase as b from t order by \"MixedCase\" desc, MixedCase asc",
                   regenerate("select \"MixedCase\", MixedCase as b from t " +
                              "order by \"MixedCase\" desc, MixedCase"));
      assertEquals("select t.id from t order by t.MixedCase desc, t.\"MixedCase\" asc",
                   regenerate("select t.id from t order by t.MixedCase desc, t.\"MixedCase\""));
   }

   /**
    * A later ORDER BY item of the same text and quoting sorts on the same key, so the first
    * decides the direction, as in sql. It used to replace the first one's direction.
    */
   @Test
   void sameKeyKeepsTheFirstDirection() throws Exception {
      assertEquals("select t.A, t.B from t order by t.B desc",
                   regenerate("select t.A, t.B from t order by t.B desc, t.B"));
      assertEquals("select t.A, t.B from t order by t.B desc",
                   regenerate("select t.A, t.B from t order by t.B desc, t.B asc"));
      assertEquals("select t.A, t.B from t order by t.B asc, t.A desc",
                   regenerate("select t.A, t.B from t order by t.B, t.A desc, t.B desc"));
      assertEquals("select t.id from t order by t.\"MixedCase\" desc",
                   regenerate("select t.id from t order by t.\"MixedCase\" desc, t.\"MixedCase\""));
      // setOrderBy still replaces the direction of the item of a field
      UniformSQL sql = parse("select t.A, t.B from t order by t.B desc");
      sql.setOrderBy("t.B", "asc");
      assertEquals("select t.A, t.B from t order by t.B asc", regenerate(sql));
   }

   /**
    * A quoted ORDER BY "1" is a column named 1, not the first select column.
    */
   @Test
   void quotedDigitsAreAColumnNotAnOrdinal() throws Exception {
      UniformSQL sql = parse("select w.id, w.\"1\" from w order by \"1\" desc");
      assertEquals("1", sql.getOrderByItems()[0].getField());
      assertTrue(sql.isQuotedOrderBy(0));
      assertTrue(regenerate(sql).endsWith("order by \"1\" desc"), regenerate(sql));

      sql = parse("select w.id, w.\"1\" from w order by 1 desc");
      assertEquals(1, sql.getOrderByItems()[0].getField());
      assertTrue(regenerate(sql).endsWith("order by 1 desc"), regenerate(sql));
   }

   /**
    * An ordinal converted to its select column by the metadata step takes the quoting of
    * that column, also when another column has the same path.
    */
   @Test
   void ordinalTakesTheQuotesOfItsColumn() throws Exception {
      JDBCDataSource h2 = helpers().get("h2");
      UniformSQL sql = parse("select \"MixedCase\" from t order by 1", h2);
      fix(sql, h2, TWIN_SECOND);
      assertEquals("select t.\"MixedCase\" from t order by t.\"MixedCase\" asc", regenerate(sql));

      sql = parse("select MixedCase, \"MixedCase\" as b from t order by 2 desc, 1", h2);
      fix(sql, h2, TWIN_SECOND);
      assertEquals("select t.MixedCase, t.\"MixedCase\" as b from t order by t.\"MixedCase\" desc, t.MixedCase asc",
                   regenerate(sql));
   }

   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select \"MixedCase\", MixedCase as b from t",
         "select MixedCase, \"MixedCase\" as b from t",
         "select \"MixedCase\", MixedCase as b from t order by b",
         "select \"MixedCase\" as a, MixedCase as b from t order by a",
         "select \"MixedCase\" as a, MixedCase as b from t order by b, a",
         "select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase",
         "select MixedCase, \"MixedCase\" as b from t group by MixedCase, \"MixedCase\"",
         "select \"MixedCase\", MixedCase as b from t order by MixedCase",
         "select \"MixedCase\", MixedCase as b from t order by \"MixedCase\"",
         "select \"MixedCase\", MixedCase as b from t order by MixedCase desc, \"MixedCase\"",
         "select \"MixedCase\", MixedCase as b from t order by \"MixedCase\" desc, MixedCase",
         "select \"MixedCase\" as a, MixedCase as b from t group by \"MixedCase\", MixedCase order by a, b",
         "select \"MixedCase\", MixedCase as b from t order by 2 desc, 1",
         "select t.\"MixedCase\", t.MixedCase as b from t",
         "select t.MixedCase, t.\"MixedCase\" as b from t",
         "select t.\"MixedCase\", t.MixedCase as b from t order by b",
         "select t.\"MixedCase\", t.MixedCase as b from t group by t.\"MixedCase\", t.MixedCase",
         "select t.\"MixedCase\", t.MixedCase as b from t order by t.MixedCase desc, t.\"MixedCase\"",
         "select t.id, t.MixedCase from t order by t.MixedCase desc, t.MixedCase",
         "select t.id, t.\"MixedCase\" from t order by t.\"MixedCase\" desc, t.\"MixedCase\"",
         "select t.MixedCase, t.\"MixedCase\" as b from t order by 2 desc, 1",
         // one spelling (#77501, #77558)
         "select \"MixedCase\" from t order by 1",
         "select t.\"MixedCase\" from t order by 1 desc",
         "select \"MixedCase\" from t group by \"MixedCase\" order by \"MixedCase\"",
         "select t.MixedCase from t order by t.MixedCase desc",
         // where and subqueries keep their own flags
         "select \"MixedCase\", MixedCase as b from t where \"MixedCase\" = 1 or MixedCase = 1",
         "select \"MixedCase\" from t where \"MixedCase\" in (select MixedCase from u)",
      };
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("default", null);
      helpers.put("h2", helpers().get("h2"));
      helpers.put("h2-ansi", helpers().get("h2-ansi"));

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77573;create=true");
          Statement stmt = conn.createStatement())
      {
         // the twins vary independently, so a wrong column gives other rows
         for(String table : new String[] { "t", "u" }) {
            stmt.execute("create table " + table + " (\"MixedCase\" int, MIXEDCASE int, id int)");
         }

         stmt.execute("insert into t values (1, 3, 10), (2, 1, 20), (3, 2, 30), (null, 4, 40)");
         stmt.execute("insert into u values (2, 1, 1), (5, 3, 2)");

         for(String query : queries) {
            boolean ordered = query.contains("order by");
            List<String> expected = rows(stmt, query, ordered);

            for(Map.Entry<String, JDBCDataSource> helper : helpers.entrySet()) {
               for(Map.Entry<String, String> stage : stages(query, helper.getValue()).entrySet()) {
                  String generated = stage.getValue();
                  assertEquals(expected, rows(stmt, generated, ordered),
                               helper.getKey() + " " + stage.getKey() + ": " + query + " -> " + generated);
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77573;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   /**
    * Bug #77573 refute: order by T.B desc, T.B regenerated as order by t.b asc and reversed
    * the rows. The data has no ties on B, so the order of the rows is fixed.
    */
   @Test
   void sameKeyRowsMatchOnDerby() throws Exception {
      String query = "select T.A, T.B from T order by T.B desc, T.B";

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77573b;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table T (A int, B int)");
         stmt.execute("insert into T values (1, 10), (2, 30), (3, 20)");
         List<String> expected = rows(stmt, query, true);
         assertEquals(List.of("[2, 30]", "[20, 3]", "[1, 10]"), expected);

         for(String helper : new String[] { "default", "h2", "h2-ansi" }) {
            for(Map.Entry<String, String> stage : stages(query, helpers().get(helper), "A", "B").entrySet()) {
               assertEquals(expected, rows(stmt, stage.getValue(), true),
                            helper + " " + stage.getKey() + ": " + stage.getValue());
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77573b;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   /**
    * Oracle stores an unquoted name in upper case, so the two spellings have other text when
    * parsed. The metadata step resolves the unquoted one ignoring case, to the first column
    * of the metadata, which can give both the same path. Each keeps its own flag, and the
    * unquoted path is folded to upper case by the database.
    */
   @Test
   void oracleTwinsBeforeAndAfterTheMetadataStep() throws Exception {
      JDBCDataSource oracle = helpers().get("oracle");
      Map<String, String> stages = stages(
         "select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase " +
         "order by MixedCase desc, \"MixedCase\"", oracle);
      assertEquals("select MIXEDCASE as \"b\", \"MixedCase\" from t group by \"MixedCase\", MixedCase " +
                   "order by MixedCase desc, \"MixedCase\" asc", stages.get("parse"));
      assertEquals("select t.\"MixedCase\", t.MixedCase as \"b\" from T t group by t.\"MixedCase\", t.MixedCase " +
                   "order by t.MixedCase desc, t.\"MixedCase\" asc", stages.get("fixed"));
      assertEquals(stages.get("fixed"), stages.get("fixed-xml"));
      assertEquals("select t.MIXEDCASE as \"b\", t.\"MixedCase\" from T t group by t.\"MixedCase\", t.MIXEDCASE " +
                   "order by t.MIXEDCASE desc, t.\"MixedCase\" asc", stages.get("fixed-twin-first"));

      stages = stages("select t.\"MixedCase\", t.MixedCase as b from t", oracle);
      assertEquals("select T.MIXEDCASE as \"b\", t.\"MixedCase\" from t", stages.get("parse"));
      assertEquals("select t.\"MixedCase\", t.MixedCase as \"b\" from T t", stages.get("fixed"));
      assertEquals("select t.MIXEDCASE as \"b\", t.\"MixedCase\" from T t", stages.get("fixed-twin-first"));

      assertEquals("select t.\"MixedCase\" from T t order by t.\"MixedCase\" asc",
                   stages("select \"MixedCase\" from t order by 1", oracle).get("fixed"));
   }

   /**
    * PostgreSQL stores an unquoted name with in-band quotes ("MixedCase") and a quoted one
    * without them, flagged, so the two spellings have other text and other flags. The quoted
    * one keeps its case. The unquoted one is generated quoted, as before this change: that is
    * the in-band quoting of a case-sensitive helper, not the flag.
    */
   @Test
   void postgresqlTwinsBeforeAndAfterTheMetadataStep() throws Exception {
      JDBCDataSource pg = helpers().get("postgresql");
      UniformSQL sql = parse("select \"MixedCase\", MixedCase as b from t", pg);
      JDBCSelection select = (JDBCSelection) sql.getSelection();
      assertEquals("MixedCase", select.getColumn(0));
      assertTrue(select.isQuoted(0));
      assertEquals("\"MixedCase\"", select.getColumn(1));
      assertFalse(select.isQuoted(1));

      Map<String, String> stages = stages(
         "select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase " +
         "order by MixedCase desc, \"MixedCase\"", pg);
      assertEquals("select \"MixedCase\" as \"b\", \"t\".\"MixedCase\" from \"t\" " +
                   "group by \"t\".\"MixedCase\", \"MixedCase\" order by \"MixedCase\" desc, \"t\".\"MixedCase\" asc",
                   stages.get("fixed"));
      assertEquals(stages.get("fixed"), stages.get("fixed-xml"));
      assertEquals("select \"t\".\"MIXEDCASE\" as \"b\", \"t\".\"MixedCase\" from \"t\"",
                   stages("select t.\"MixedCase\", t.MixedCase as b from t", pg).get("fixed-twin-first"));
      assertEquals("select \"t\".\"MixedCase\" from \"t\" order by \"t\".\"MixedCase\" asc",
                   stages("select \"MixedCase\" from t order by 1", pg).get("fixed"));
   }

   /**
    * The query editor's sort and group panes rebuild ORDER BY and GROUP BY from the text of
    * the items, without their flags. A name written quoted keeps its quotes there, through
    * the text (#77501, #77558). Two spellings of one name can't be told apart there.
    */
   @Test
   void editorRebuildKeepsTheQuotesOfOneSpelling() throws Exception {
      for(String query : new String[] {
         "select \"MixedCase\" from t group by \"MixedCase\" order by \"MixedCase\" desc",
         "select t.\"MixedCase\" from t group by t.\"MixedCase\" order by t.\"MixedCase\" desc" })
      {
         String expected = regenerate(query);
         UniformSQL sql = parse(query);
         OrderByItem[] items = sql.getOrderByItems();
         sql.removeAllOrderByFields();

         for(OrderByItem item : items) {
            sql.setOrderBy(item.getField(), item.getOrder());
         }

         sql.setGroupBy(sql.getGroupBy().clone());
         assertEquals(expected, regenerate(sql), query);
         assertEquals(expected, regenerate(reload(sql)), query);
      }
   }

   /**
    * The xml has one flag per element. Old assets load as they were saved: without flags
    * unquoted, and with the flag that both spellings shared on every element.
    */
   @Test
   void xmlKeepsOneFlagPerElement() throws Exception {
      String query = "select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase " +
         "order by MixedCase desc, \"MixedCase\"";
      String xml = toXML(parse(query));
      assertEquals(1, count(xml, "<quoted><![CDATA[true]]></quoted>"), xml);
      assertEquals(2, count(xml, " quoted=\"true\""), xml);
      assertEquals("select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase " +
                   "order by MixedCase desc, \"MixedCase\" asc", regenerate(load(xml, null)));

      String unquoted = xml.replace("<quoted><![CDATA[true]]></quoted>", "").replace(" quoted=\"true\"", "");
      assertEquals("select MixedCase, MixedCase as b from t group by MixedCase, MixedCase " +
                   "order by MixedCase desc", regenerate(load(unquoted, null)));

      // saved before this change, every element of the name has the flag
      String shared = unquoted.replace("<isExp><![CDATA[false]]></isExp>",
                                       "<isExp><![CDATA[false]]></isExp><quoted><![CDATA[true]]></quoted>")
         .replace("<field>", "<field quoted=\"true\">").replace("<field order=\"", "<field quoted=\"true\" order=\"");
      assertEquals("select \"MixedCase\", \"MixedCase\" as b from t group by \"MixedCase\", \"MixedCase\" " +
                   "order by \"MixedCase\" desc", regenerate(load(shared, null)));

      String qualified = toXML(parse("select t.\"MixedCase\", t.MixedCase as b from t"));
      assertEquals(1, count(qualified, "<quotedColumn column=\"MixedCase\"/>"), qualified);
   }

   /**
    * The flag stays with its column when a column is removed, copied or compared.
    */
   @Test
   void selectionKeepsTheFlagByPosition() {
      JDBCSelection select = new JDBCSelection();
      select.addColumn("t.MixedCase");
      select.addColumn("t.MixedCase");
      select.addColumn("t.id");
      select.addColumn("t.MixedCase");
      select.setQuoted(1, "MixedCase");
      select.setQuoted(3, true);

      assertFalse(select.isQuoted("t.MixedCase"));
      assertTrue(select.isQuoted(1));
      assertEquals("MixedCase", select.getQuotedColumn(1));

      JDBCSelection copy = select.clone();
      assertEquals(select, copy);
      assertEquals(select.hashCode(), copy.hashCode());
      copy.setQuoted(1, false);
      assertNotEquals(select, copy);
      assertTrue(select.isQuoted(1));

      JDBCSelection converted = new JDBCSelection((XSelection) select);
      assertTrue(converted.isQuoted(1) && converted.isQuoted(3) && !converted.isQuoted(0));

      select.removeColumn(0);
      assertTrue(select.isQuoted(0));
      assertEquals("MixedCase", select.getQuotedColumn(0));
      assertFalse(select.isQuoted(1));
      assertTrue(select.isQuoted(2));

      // removes the first column of the path, the others keep their flags
      select.removeColumn("t.MixedCase");
      assertFalse(select.isQuoted(0));
      assertTrue(select.isQuoted(1));
      assertEquals(2, select.getColumnCount());
   }

   /**
    * #77578 records the quoted column of an ORDER BY aggregate by its text. A later item of
    * the same text and quoting is dropped, and doesn't clear or change the record of the item
    * kept.
    */
   @Test
   void droppedOrderByItemKeepsTheAggregateRecord() throws Exception {
      UniformSQL sql = parse("select sum(t.\"MixedCase\") from t " +
                             "order by sum(t.\"MixedCase\") desc, sum(t.MixedCase)", helpers().get("postgresql"));
      OrderByItem[] items = sql.getOrderByItems();
      assertEquals(1, items.length, Arrays.toString(items));
      assertEquals("desc", items[0].getOrder());
      assertEquals("MixedCase", sql.getQuotedAggregate(items[0].getField()));
   }

   /**
    * The regenerated sql parses and regenerates to itself.
    */
   @Test
   void regeneratedSqlRegeneratesToItself() throws Exception {
      for(String query : new String[] {
         "select \"MixedCase\", MixedCase as b from t group by \"MixedCase\", MixedCase " +
            "order by MixedCase desc, \"MixedCase\"",
         "select t.MixedCase, t.\"MixedCase\" as b from t group by t.MixedCase, t.\"MixedCase\" " +
            "order by t.\"MixedCase\" desc, t.MixedCase",
         "select \"MixedCase\" as a, MixedCase as b from t order by b, a" })
      {
         for(String helper : new String[] { "default", "h2", "oracle" }) {
            String once = regenerate(parse(query, helpers().get(helper)));
            assertEquals(once, regenerate(parse(once, helpers().get(helper))), helper + ": " + query);
         }
      }
   }

   // the regenerated sql after each stage of the pipeline
   private static Map<String, String> stages(String query, JDBCDataSource ds) throws Exception {
      return stages(query, ds, TWIN_SECOND);
   }

   private static Map<String, String> stages(String query, JDBCDataSource ds, String... columns)
      throws Exception
   {
      Map<String, String> stages = new LinkedHashMap<>();
      UniformSQL sql = parse(query, ds);
      stages.put("parse", regenerate(sql));
      stages.put("xml", regenerate(reload(parse(query, ds))));
      stages.put("clone", regenerate((UniformSQL) parse(query, ds).clone()));
      stages.put("read", regenerate(copy(parse(query, ds))));

      // a table alias rename only applies to qualified names
      if(query.contains("t.") || query.contains("T.")) {
         UniformSQL renamed = parse(query, ds);
         renameAlias(renamed, query.contains("T.") ? "T" : "t", "x");
         stages.put("rename", regenerate(renamed));
      }

      if(ds != null) {
         // the table metadata is cached by data source, use another one per order
         JDBCDataSource fds = (JDBCDataSource) ds.clone();
         fds.setName(ds.getName() + "_" + (++sources));
         UniformSQL fixed = parse(query, fds);
         fix(fixed, fds, columns);
         stages.put("fixed", regenerate(fixed));
         stages.put("fixed-xml", regenerate(reload(fixed)));
         stages.put("fixed-clone", regenerate((UniformSQL) fixed.clone()));

         if(columns == TWIN_SECOND) {
            JDBCDataSource tds = (JDBCDataSource) ds.clone();
            tds.setName(ds.getName() + "_" + (++sources));
            UniformSQL twin = parse(query, tds);
            fix(twin, tds, TWIN_FIRST);
            stages.put("fixed-twin-first", regenerate(twin));
            stages.put("fixed-twin-first-xml", regenerate(reload(twin)));
         }
      }

      return stages;
   }

   // the metadata step, with the columns of every table
   private static void fix(UniformSQL sql, JDBCDataSource ds, String[] columns) throws Exception {
      JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
   }

   // renames a table alias as QueryGraphModelService does
   private static void renameAlias(UniformSQL sql, String from, String to) {
      Hashtable<String, String> aliasMap = new Hashtable<>();

      for(int i = 0; i < sql.getTableCount(); i++) {
         SelectTable table = sql.getSelectTable(i);
         aliasMap.put(table.getAlias(), table.getAlias());

         if(from.equals(table.getAlias())) {
            aliasMap.put(from, to);
            table.setAlias(to);
         }
      }

      sql.syncTableAlias(aliasMap);
      sql.clearSQLString();
   }

   private static int count(String str, String part) {
      return str.split(Pattern.quote(part), -1).length - 1;
   }

   private static UniformSQL copy(UniformSQL sql) {
      UniformSQL copy = new UniformSQL();
      copy.read(sql);
      return copy;
   }

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("default", null);
      helpers.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false));
      helpers.put("h2-ansi", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", true));
      helpers.put("oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x", "oracle",
                                       false));
      helpers.put("postgresql", dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql",
                                           false));
      return helpers;
   }

   // the metadata of every table, the quoted name has a twin that differs only in case
   private static final String[] TWIN_SECOND = { "MixedCase", "MIXEDCASE", "id" };
   private static final String[] TWIN_FIRST = { "MIXEDCASE", "MixedCase", "id" };

   // SQLTypes.getQualifiedName logs an error that it can't get the root meta-data from
   // XRepository.getRepository(), then goes on
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

   // the rows with the values of each row sorted, since the generated sql may reorder
   // columns. In order if the query sorts, else sorted.
   private static List<String> rows(Statement stmt, String query, boolean ordered) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = stmt.executeQuery(query)) {
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

      if(!ordered) {
         Collections.sort(rows);
      }

      return rows;
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

   private static JDBCDataSource dataSource(String driver, String url, String product, boolean ansiJoin) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77573" + product + ansiJoin + RUN);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin(ansiJoin);
      return ds;
   }

   private static UniformSQL reload(UniformSQL sql) throws Exception {
      return load(toXML(sql), sql.getDataSource());
   }

   private static UniformSQL load(String xml, JDBCDataSource ds) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      if(ds != null) {
         loaded.setDataSource(ds);
      }

      return loaded;
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static String regenerate(String text) throws Exception {
      return regenerate(parse(text));
   }

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(String text) throws Exception {
      return parse(text, null);
   }

   private static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();

      if(ds != null) {
         sql.setDataSource(ds);
      }

      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static final long RUN = System.nanoTime();
   private static int sources;
}
