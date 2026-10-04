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
import inetsoft.sree.security.SecurityEngine;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.path.XSelection;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.ColumnCache;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.credential.LocalPasswordCredential;
import inetsoft.web.portal.controller.database.*;
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

      // twins resolve to one path. Ordinals that name the column of another item stay
      // ordinals (#77570), so the sql runs as written
      sql = parse("select MixedCase, \"MixedCase\" as b from t order by 2 desc, 1", h2);
      fix(sql, h2, TWIN_SECOND);
      assertEquals("select t.MixedCase, t.\"MixedCase\" as b from t order by 2 desc, 1 asc",
                   regenerate(sql));

      // one twin ordinal is converted, with the quoting of its own column
      sql = parse("select MixedCase, \"MixedCase\" as b from t order by 2 desc", h2);
      fix(sql, h2, TWIN_SECOND);
      assertEquals("select t.MixedCase, t.\"MixedCase\" as b from t order by t.\"MixedCase\" desc",
                   regenerate(sql));
      sql = parse("select MixedCase, \"MixedCase\" as b from t order by 1 desc", h2);
      fix(sql, h2, TWIN_SECOND);
      assertEquals("select t.MixedCase, t.\"MixedCase\" as b from t order by t.MixedCase desc",
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
    * one keeps its case. The unquoted one is generated in the case postgresql reads it,
    * "mixedcase" (Bug #77643).
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
      assertEquals("select \"mixedcase\" as \"b\", \"t\".\"MixedCase\" from \"t\" " +
                   "group by \"t\".\"MixedCase\", \"mixedcase\" order by \"mixedcase\" desc, \"t\".\"MixedCase\" asc",
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

      // the unquoted twin of a quoted text says so, it would read as quoted by its text
      assertEquals(2, count(xml, " quoted=\"false\"><![CDATA[MixedCase]]></field>"), xml);
      String unquoted = xml.replace("<quoted><![CDATA[true]]></quoted>", "").replace(" quoted=\"true\"", "")
         .replace(" quoted=\"false\"", "");
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
    * An order by or group by element saved before the quoting was kept per element has no
    * attribute, and is quoted by its text as before. Bug #77573 verification: saved by a
    * build with #6105, order by "1" was an ordinal there and only the group by "1" carried
    * the flag, so it must reload as the column "1", not as ordinal 1.
    */
   @Test
   void elementSavedWithoutItsQuotingIsQuotedByItsText() throws Exception {
      String query = "select id, \"1\" from n group by \"1\", id order by \"1\"";
      String xml = toXML(parse(query));
      assertTrue(xml.contains("<field quoted=\"true\"><![CDATA[1]]></field>"), xml);

      // as written by that build: no attribute on the order by element
      String old = xml.replace("<field order=\"asc\" quoted=\"true\">", "<field order=\"asc\">");
      assertNotEquals(xml, old);
      assertEquals("select id, \"1\" from n group by \"1\", id order by \"1\" asc", regenerate(load(old, null)));

      // none on the group by element, but the order by element of that text is quoted
      String oldGroup = xml.replace("<field quoted=\"true\"><![CDATA[1]]></field>", "<field><![CDATA[1]]></field>");
      assertNotEquals(xml, oldGroup);
      assertEquals("select id, \"1\" from n group by \"1\", id order by \"1\" asc",
                   regenerate(load(oldGroup, null)));

      // round trip: the reloaded asset writes the attributes and reloads the same
      UniformSQL reloaded = load(old, null);
      assertEquals(regenerate(load(old, null)), regenerate(load(toXML(reloaded), null)));

      // an unquoted twin written now says so, and doesn't read as quoted by its text
      UniformSQL twins = parse("select \"MixedCase\", MixedCase as b from t order by MixedCase desc, \"MixedCase\"");
      String twinsXml = toXML(twins);
      assertTrue(twinsXml.contains("<field order=\"desc\" quoted=\"false\">"), twinsXml);
      assertEquals(regenerate(twins), regenerate(load(twinsXml, null)));
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
    * the same text and quoting doesn't clear or change the record of the first item. It was
    * dropped, it's another column (mixedcase) on postgresql, so it's kept and generated as
    * that column (Bug #77643).
    */
   @Test
   void droppedOrderByItemKeepsTheAggregateRecord() throws Exception {
      UniformSQL sql = parse("select sum(t.\"MixedCase\") from t " +
                             "order by sum(t.\"MixedCase\") desc, sum(t.MixedCase)", helpers().get("postgresql"));
      OrderByItem[] items = sql.getOrderByItems();
      assertEquals(2, items.length, Arrays.toString(items));
      assertEquals("desc", items[0].getOrder());
      assertEquals("MixedCase", sql.getQuotedAggregate(items[0].getField()));
      assertTrue(regenerate(sql).endsWith(" order by sum(\"t\".\"MixedCase\") desc, sum(\"t\".\"mixedcase\") asc"),
                 regenerate(sql));
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

   /**
    * #77616: the parser records whether each select alias was written quoted (as "A") or
    * not (as A). A variable or placeholder alias is not known.
    */
   @Test
   void parserRecordsTheQuotingOfEachAlias() throws Exception {
      JDBCSelection select = (JDBCSelection) parse(
         "select id as A, id as \"B\", id as [C], id as 'D', id E, id as \"F G\", id from t").getSelection();
      assertEquals(Boolean.FALSE, select.isAliasQuoted(0));
      assertEquals(Boolean.TRUE, select.isAliasQuoted(1));
      assertEquals(Boolean.TRUE, select.isAliasQuoted(2));
      assertEquals(Boolean.TRUE, select.isAliasQuoted(3));
      assertEquals(Boolean.FALSE, select.isAliasQuoted(4));
      assertEquals(Boolean.TRUE, select.isAliasQuoted(5));
      assertNull(select.isAliasQuoted(6));

      // sybase select A = expression, the alias quoting is read before the expression
      select = (JDBCSelection) parse("select A = t.\"x\", \"B\" = t.y from t").getSelection();
      assertEquals(Boolean.FALSE, select.isAliasQuoted(0));
      assertEquals(Boolean.TRUE, select.isAliasQuoted(1));

      // the flag doesn't describe an alias changed after parsing
      select.setAlias(0, "Z");
      assertNull(select.isAliasQuoted(0));
      select.removeColumn(0);
      assertEquals(Boolean.TRUE, select.isAliasQuoted(0));
      assertEquals(select, select.clone());
   }

   /**
    * #77616 shapes on a database that folds unquoted names: an unquoted reference in another
    * case matches an unquoted alias, and not a quoted one. A match on a table column alias
    * references the column, since a reference to the alias could also name a selected
    * column. The expected sql returns the rows of the original on PostgreSQL 16.
    */
   @Test
   void aliasQuotingDecidesTheReferenceOnPostgresql() throws Exception {
      // F2, order by "A" would be ambiguous with w."A"
      assertEquals("select \"w\".\"A\", \"w\".\"id\" as \"A\" from \"w\" order by \"w\".\"id\" desc",
                   fixed("postgresql", "select w.id as A, w.\"A\" from w order by a desc", "id", "A"));
      assertEquals("select \"id\" as \"A\" from \"u\" order by \"id\" desc",
                   fixed("postgresql", "select id as A from u order by a desc", "id"));
      // a quoted alias is another name, a is the column
      assertEquals("select \"k\" as \"A\" from \"t\" order by \"t\".a desc",
                   fixed("postgresql", "select k as \"A\" from t order by a desc", "id", "k", "a"));
      // group by resolves the column first, order by the alias
      assertEquals("select \"k\" as \"ID\", count(*) from \"t\" group by \"t\".id, \"k\" " +
                   "order by \"k\" asc, count(*) asc",
                   fixed("postgresql", "select k as ID, count(*) from t group by id, k order by id, 2",
                         "id", "k", "a"));
      // A1: an unquoted A is a, not the quoted alias "A"
      assertEquals("select \"id\" as \"A\" from \"t\" order by \"t\".a desc",
                   fixed("postgresql", "select id as \"A\" from t order by A desc", "id", "k", "a"));
      // a quoted "A" is the column "A", not the unquoted alias A (which is a)
      assertEquals("select \"w\".\"A\", \"w\".\"id\" as \"A\" from \"w\" order by \"w\".\"A\" desc",
                   fixed("postgresql", "select w.id as A, w.\"A\" from w order by \"A\" desc", "id", "A"));
      // X5, an unquoted alias in another case without an ordinal
      assertEquals("select \"u\".\"id\", \"u\".\"k\" as \"A\" from \"u\" order by \"u\".\"k\" desc",
                   fixed("postgresql", "select u.k as A, u.id from u order by a desc", "id", "k"));
      // the common shape, the reference is the alias as generated
      assertEquals("select \"id\" as \"A\" from \"u\" order by \"A\" desc",
                   fixed("postgresql", "select id as A from u order by A desc", "id"));
      // A1 where no column a exists: the original fails on postgresql (column a doesn't exist),
      // the reference names nothing and is dropped, as on #6190
      assertEquals("select \"id\" as \"A\" from \"u\"",
                   fixed("postgresql", "select id as \"A\" from u order by A desc", "id"));
      // an ordinal next to them is converted, the items are decided
      assertEquals("select \"u\".\"id\", \"u\".\"k\" as \"A\" from \"u\" order by \"u\".\"k\" asc, \"u\".\"id\" desc",
                   fixed("postgresql", "select u.k as A, u.id from u order by a, 2 desc", "id", "k"));
      // an alias in the folded case is the same name either way
      assertEquals("select \"id\" as \"a\" from \"t\" order by \"a\" desc",
                   fixed("postgresql", "select id as a from t order by a desc", "id", "k"));
      assertEquals("select \"id\" as \"a\" from \"t\" order by \"id\" desc",
                   fixed("postgresql", "select id as \"a\" from t order by A desc", "id", "k"));
   }

   /**
    * Finding Z (#77616 verification): an unquoted reference is folded by the database, so it
    * names only a column spelled in the folded case. With only a quoted column "A" on
    * postgresql, group by a is the alias. A quoted reference names the column of that exact
    * spelling, not an unquoted alias A, which is a (rule 6.3.2). The expected sql returns the
    * rows of the original on PostgreSQL 16.
    */
   @Test
   void unquotedReferenceNamesOnlyAColumnInTheFoldedCase() throws Exception {
      assertEquals("select \"k\" as \"a\", count(*) from \"z\" group by \"a\"",
                   fixed("postgresql", "select k as a, count(*) from z group by a", "id", "k", "A"));
      assertEquals("select \"k\" as \"a\", count(*) from \"z\" group by \"k\"",
                   fixed("postgresql", "select k as a, count(*) from z group by A", "id", "k", "A"));
      // no column a and no alias: the original fails, the item names nothing (as before)
      assertEquals("select \"id\" from \"z\"",
                   fixed("postgresql", "select id from z order by a desc", "id", "k", "A"));
      assertEquals("select \"id\" from \"z\" order by \"z\".\"A\" desc",
                   fixed("postgresql", "select id from z order by \"A\" desc", "id", "k", "A"));
      // L01, L02: the column "A", not the alias A
      assertEquals("select \"id\" as \"A\" from \"z\" order by \"z\".\"A\" asc",
                   fixed("postgresql", "select id as A from z order by \"A\"", "id", "k", "A"));
      assertEquals("select \"k\" as \"A\" from \"w\" order by \"w\".\"A\" asc",
                   fixed("postgresql", "select k as A from w order by \"A\"", "id", "k", "A"));
   }

   /**
    * Review B1 and #6204 verification: the query editor's "edit expression" replaces a select
    * column in place (QueryManagerService.editExpression). The new text is generated as it
    * is written, per segment: the quoted flag of the column it replaced doesn't apply to it,
    * even when it names a column of the same spelling (q.k for "k", u.MixedCase for
    * q."MixedCase"). A table renamed in the query keeps the quotes.
    */
   @Test
   void editedColumnIsGeneratedAsWritten() throws Exception {
      String[][] cases = {
         // query, column, expression, expected select item on h2, on postgresql
         { "select \"My Col\" as e, id from t", "My Col", "id * 2", "id * 2 as e", "id * 2 as \"e\"" },
         { "select t.\"MixedCase\" as e, t.id from t", "MixedCase", "t.\"MixedCase\" * 2",
           "t.\"MixedCase\" * 2 as e", "t.\"MixedCase\" * 2 as \"e\"" },
         { "select \"MixedCase\" as e, id from t", "MixedCase", "\"MixedCase\" + id",
           "\"MixedCase\" + id as e", "\"MixedCase\" + id as \"e\"" },
         // an unquoted column edited into a quoted one keeps the quotes as written
         { "select id as e, k from t", "id", "\"MixedCase\"", "\"MixedCase\" as e", "\"MixedCase\" as \"e\"" },
         // a quoted column edited into the unquoted name of the same spelling
         { "select \"k\" as e, id from q", "k", "q.k", "q.k as e", "q.k as \"e\"" },
         { "select \"K\" as e, id from q", "K", "q.K", "q.K as e", "q.K as \"e\"" },
         { "select \"MixedCase\" as e, id from q", "MixedCase", "q.MixedCase", "q.MixedCase as e",
           "q.MixedCase as \"e\"" },
         // another table's column of the same name
         { "select q.\"MixedCase\" as e, u.id from q, u", "MixedCase", "u.MixedCase",
           "u.MixedCase as e", "u.MixedCase as \"e\"" },
      };
      QueryManagerService service = new QueryManagerService(
         mock(RuntimeQueryService.class), mock(XRepository.class), mock(DataSourceService.class),
         mock(SecurityEngine.class), mock(ColumnCache.class));

      for(String helper : new String[] { "h2", "postgresql" }) {
         for(String[] c : cases) {
            UniformSQL sql = edit(service, c[0], c[2], helpers().get(helper), c[1]);
            String generated = regenerate(sql);
            assertTrue(generated.contains(" " + ("h2".equals(helper) ? c[3] : c[4])),
                       helper + " " + c[0] + " -> " + generated);
            assertEquals(generated, regenerate(reload(sql)), helper + " xml " + c[0]);
         }
      }

      // a table renamed in the query keeps the quotes of its columns. postgresql stores the
      // table alias with in-band quotes, which this rename doesn't handle
      for(String helper : new String[] { "h2", "oracle" }) {
         UniformSQL sql = parse("select q.\"MixedCase\" as e, q.\"k\" from q group by q.\"MixedCase\", q.\"k\" " +
                                "order by q.\"MixedCase\" desc", helpers().get(helper));
         fixed(sql, COLUMNS);
         renameAlias(sql, "q", "x");
         String generated = regenerate(sql);
         String x = "x";
         assertEquals(3, count(generated, x + ".\"MixedCase\""), helper + " " + generated);
         assertEquals(2, count(generated, x + ".\"k\""), helper + " " + generated);
      }

      // the rows of the edited query on Derby (h2 helper)
      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77573d;create=true");
          Statement stmt = conn.createStatement())
      {
         // "k" and K, "MixedCase" and MIXEDCASE are different columns
         for(String table : new String[] { "t", "q", "u" }) {
            stmt.execute("create table " + table + " (\"My Col\" int, \"MixedCase\" int, MIXEDCASE int, " +
                         "id int, \"k\" int, K int)");
         }

         stmt.execute("insert into t values (1, 2, 30, 10, 5, 50), (4, 5, 60, 20, 6, 70)");
         stmt.execute("insert into q values (1, 2, 30, 10, 5, 50), (4, 5, 60, 20, 6, 70)");
         stmt.execute("insert into u values (7, 8, 90, 10, 1, 2)");

         for(String[] c : cases) {
            UniformSQL sql = edit(service, c[0], c[2], helpers().get("h2"), c[1]);
            String edited = c[0].replace(c[0].substring(7, c[0].indexOf(',')), c[2] + " as e");
            assertTrue(edited.startsWith("select " + c[2] + " as e,"), edited);
            assertEquals(rows(stmt, edited, false), rows(stmt, regenerate(sql), false),
                         c[0] + " -> " + regenerate(sql));
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77573d;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   /**
    * #6204 verification: the expression editor starts from the column's quoted spelling
    * (QueryFieldModel.quotedName), so pressing OK without a change keeps the quoted column,
    * and an edit from it keeps the quotes as written. Any other text, also the unquoted name
    * typed in its place (q.k for "k", stored as q.k), is generated as written
    * (editedColumnIsGeneratedAsWritten).
    */
   @Test
   void unchangedEditKeepsTheQuotedColumn() throws Exception {
      String[][] cases = {
         // query, quoted spelling the editor starts from (h2/oracle after the metadata step)
         { "select q.\"MixedCase\" as e, q.id from q", "q.\"MixedCase\"" },
         { "select \"MixedCase\" as e, id from q", "q.\"MixedCase\"" },
         { "select \"My Col\" as e, id from q", "q.\"My Col\"" },
         { "select \"k\" as e, id from q", "q.\"k\"" },
      };
      QueryManagerService service = new QueryManagerService(
         mock(RuntimeQueryService.class), mock(XRepository.class), mock(DataSourceService.class),
         mock(SecurityEngine.class), mock(ColumnCache.class));

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77573e;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table q (\"My Col\" int, \"MixedCase\" int, MIXEDCASE int, id int, " +
                      "\"k\" int, K int)");
         stmt.execute("insert into q values (1, 2, 30, 10, 5, 50), (4, 5, 60, 20, 6, 70)");

         for(String helper : new String[] { "h2", "oracle", "postgresql" }) {
            for(String[] c : cases) {
               List<String> expected = rows(stmt, c[0], false);

               // OK without a change, from the quoted spelling
               UniformSQL sql = parse(c[0], helpers().get(helper));
               fixed(sql, COLUMNS);
               JDBCSelection select = (JDBCSelection) sql.getSelection();
               String quoted = QueryManagerService.getQuotedName(sql, select, 0);
               assertNotNull(quoted, helper + " " + c[0]);

               if(!"postgresql".equals(helper)) {
                  assertEquals(c[1], quoted, helper + " " + c[0]);
               }

               service.editExpression(sql, select, quoted, select.getColumn(0), "e");
               assertTrue(select.isQuoted(0), helper + " " + c[0]);
               String generated = regenerate(sql);
               assertTrue(generated.contains(quoted + " as "), helper + " " + c[0] + " -> " + generated);

               if(!"postgresql".equals(helper)) {
                  assertEquals(expected, rows(stmt, generated, false), helper + " " + generated);
               }

               // an edit from it keeps the quotes as written
               sql = parse(c[0], helpers().get(helper));
               fixed(sql, COLUMNS);
               select = (JDBCSelection) sql.getSelection();
               quoted = QueryManagerService.getQuotedName(sql, select, 0);
               service.editExpression(sql, select, quoted + " * 2", select.getColumn(0), "e");
               generated = regenerate(sql);
               assertTrue(generated.contains(quoted + " * 2 as "), helper + " " + c[0] + " -> " + generated);

               if(!"postgresql".equals(helper)) {
                  assertEquals(rows(stmt, c[0].replace(c[0].substring(7, c[0].indexOf(" as e")),
                                                       c[1] + " * 2"), false),
                               rows(stmt, generated, false), helper + " " + generated);
               }

            }
         }

         // an unquoted column has no quoted spelling
         UniformSQL sql = parse("select id as e from q", helpers().get("h2"));
         fixed(sql, COLUMNS);
         assertNull(QueryManagerService.getQuotedName(sql, (JDBCSelection) sql.getSelection(), 0));
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77573e;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   // the query through the metadata step, its first column edited into an expression
   private static UniformSQL edit(QueryManagerService service, String query, String expression,
                                  JDBCDataSource ds, String column) throws Exception
   {
      UniformSQL sql = parse(query, ds);
      fixed(sql, COLUMNS);
      JDBCSelection select = (JDBCSelection) sql.getSelection();
      // postgresql stores an unquoted name with in-band quotes
      assertTrue(select.getColumn(0).replace("\"", "").endsWith(column), query + " " + select);
      assertNotNull(service.editExpression(sql, select, expression, select.getColumn(0), "e"), query);
      return sql;
   }

   private static final String[] COLUMNS = { "My Col", "MixedCase", "MIXEDCASE", "id", "k", "K" };

   /**
    * The flags only describe the text they were set for, in every store. An element whose
    * text is replaced in place is generated as the new text is written: a select column
    * unquoted, an alias of unknown quoting, a group by field or order by item quoted by its
    * text. Code that qualifies a name or renames its table keeps the flags explicitly.
    */
   @Test
   void replacedTextLosesItsQuotedFlag() throws Exception {
      UniformSQL sql = parse("select t.\"MixedCase\" as a, t.\"My Col\" as b from t " +
                             "group by t.\"MixedCase\", t.\"My Col\" order by t.\"MixedCase\" desc, t.\"My Col\"");
      JDBCSelection select = (JDBCSelection) sql.getSelection();

      // renamed with its table: kept in every store
      renameAlias(sql, "t", "x");
      assertEquals("x.MixedCase", select.getColumn(0));
      assertEquals("MixedCase", select.getQuotedColumn(0));
      assertEquals("My Col", select.getQuotedColumn(1));
      assertEquals("x.MixedCase", sql.getGroupBy()[0]);
      assertEquals("MixedCase", sql.getQuotedGroupByColumn(0));
      assertEquals("x.MixedCase", sql.getOrderByItems()[0].getField());
      assertEquals("MixedCase", sql.getQuotedOrderByColumn(0));
      select.renameColumn(0, "y.MixedCase");
      assertEquals("MixedCase", select.getQuotedColumn(0));

      // the same column of another table, another name or an expression: as written
      select.setColumn(0, "u.MixedCase");
      select.setColumn(1, "x.Other");
      assertFalse(select.isQuoted(0));
      assertFalse(select.isQuoted(1));
      select.setColumn(1, "id + 1");
      assertFalse(select.isQuoted(1));

      // the alias flag follows the alias text
      assertEquals(Boolean.FALSE, select.isAliasQuoted(0));
      select.setAlias(0, "a");
      assertEquals(Boolean.FALSE, select.isAliasQuoted(0));
      select.setAlias(0, "c");
      assertNull(select.isAliasQuoted(0));

      // group by entries and order by items written in place fall back to their text
      Object[] groups = sql.getGroupBy();
      groups[0] = "u.MixedCase";
      groups[1] = "id + 1";
      assertFalse(sql.isQuotedGroupBy(0));
      assertFalse(sql.isQuotedGroupBy(1));

      OrderByItem[] items = sql.getOrderByItems();
      items[0].setField("u.MixedCase");
      items[1].setField("id + 1");
      assertFalse(sql.isQuotedOrderBy(0));
      assertFalse(sql.isQuotedOrderBy(1));
   }

   /**
    * Review m1: a helper made case-sensitive by db.caseSensitive quotes every name, but its
    * database may not fold names. An unquoted reference to a column that isn't in upper case
    * keeps matching it in any case there, as on #6190. Only postgresql, snowflake and exasol
    * match through the folded spelling (Finding Z).
    */
   @Test
   void dbCaseSensitiveHelpersMatchAColumnInAnyCase() throws Exception {
      String old = SreeEnv.getProperty("db.caseSensitive");
      SreeEnv.setProperty("db.caseSensitive", "true");

      try {
         Map<String, JDBCDataSource> sources = new LinkedHashMap<>();
         sources.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false));
         sources.put("sql server", dataSource("com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                              "jdbc:sqlserver://localhost;databaseName=x", "sql server",
                                              false));

         for(Map.Entry<String, JDBCDataSource> source : sources.entrySet()) {
            String key = source.getKey();
            String generated = fixed(parse("select id from t order by k desc", source.getValue()),
                                     "id", "k");
            assertTrue(generated.endsWith(" order by \"t\".k desc"), key + " " + generated);

            generated = fixed(parse("select id, count(*) from t group by id, k", source.getValue()),
                              "id", "k");
            assertTrue(generated.endsWith(" group by \"id\", \"t\".k"), key + " " + generated);
         }
      }
      finally {
         if(old == null) {
            SreeEnv.remove("db.caseSensitive");
         }
         else {
            SreeEnv.setProperty("db.caseSensitive", old);
         }
      }
   }

   /**
    * Snowflake and exasol fold an unquoted name to upper case, and write a plain alias
    * unquoted. An expression alias is referenced as the helper writes it (A2).
    */
   @Test
   void aliasQuotingDecidesTheReferenceOnSnowflakeAndExasol() throws Exception {
      for(String helper : new String[] { "snowflake", "exasol" }) {
         // a reference written unquoted is generated as the alias is, unquoted (Bug #77643)
         assertEquals("order by A desc",
                      orderBy(fixed(helper, "select id as a from u order by A desc", "id")), helper);
         // t written unquoted is T (Bug #77643)
         assertEquals("order by \"T\".A desc",
                      orderBy(fixed(helper, "select k as \"a\" from t order by a desc", "id", "k", "A")), helper);
         assertEquals("order by \"T\".A desc",
                      orderBy(fixed(helper, "select id as \"a\" from t order by a desc", "id", "k", "A")), helper);
         // an expression alias is written unquoted, so the database folds it
         assertEquals("order by \"A\" desc",
                      orderBy(fixed(helper, "select id + 1 as a from t order by a desc", "id")), helper);
         assertEquals("order by \"A\" desc",
                      orderBy(fixed(helper, "select id + 1 as A from t order by a desc", "id")), helper);
      }

      assertEquals("order by \"a\" desc",
                   orderBy(fixed("postgresql", "select id + 1 as a from t order by a desc", "id")));
      // postgresql writes the alias quoted as stored
      assertEquals("order by \"A\" desc",
                   orderBy(fixed("postgresql", "select id + 1 as A from t order by a desc", "id")));
   }

   /**
    * The alias quoting is saved on the alias element. Without it (a query saved before it
    * was recorded) an unquoted reference in another case isn't guessed, as before.
    */
   @Test
   void aliasQuotingSurvivesXml() throws Exception {
      JDBCDataSource pg = helpers().get("postgresql");
      UniformSQL sql = parse("select id as A, id as \"B\", id from u", pg);
      String xml = toXML(sql);
      assertTrue(xml.contains("<alias quoted=\"false\"><![CDATA[A]]></alias>"), xml);
      assertTrue(xml.contains("<alias quoted=\"true\"><![CDATA[B]]></alias>"), xml);
      JDBCSelection loaded = (JDBCSelection) load(xml, pg).getSelection();
      assertEquals(Boolean.FALSE, loaded.isAliasQuoted(0));
      assertEquals(Boolean.TRUE, loaded.isAliasQuoted(1));
      assertNull(loaded.isAliasQuoted(2));

      String query = "select id as A from u order by a desc";
      UniformSQL parsed = parse(query, pg);
      String saved = toXML(parsed);
      assertEquals("select \"id\" as \"A\" from \"u\" order by \"id\" desc", fixed(load(saved, pg), "id"));
      // saved before the flag, not known
      String old = saved.replace("<alias quoted=\"false\">", "<alias>");
      assertEquals("select \"id\" as \"A\" from \"u\"", fixed(load(old, pg), "id"));
   }

   /**
    * Helpers that don't quote every name ignore the alias quoting.
    */
   @Test
   void aliasQuotingIsIgnoredByOtherHelpers() throws Exception {
      for(String helper : new String[] { "h2", "oracle" }) {
         for(String query : new String[] {
            "select id as A from u order by a desc",
            "select k as \"A\" from t order by a desc",
            "select id as \"A\" from t order by A desc" })
         {
            String withFlag = fixed(helper, query, "id", "k", "a");
            UniformSQL sql = parse(query, helpers().get(helper));
            JDBCSelection select = (JDBCSelection) sql.getSelection();

            for(int i = 0; i < select.getColumnCount(); i++) {
               select.setAliasQuoted(i, null);
            }

            assertEquals(withFlag, fixed(sql, "id", "k", "a"), helper + ": " + query);
         }
      }
   }

   /**
    * #77616 shapes, rows of the original and the regenerated sql on Derby (h2 helper, which
    * ignores the alias quoting).
    */
   @Test
   void aliasShapesRowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select w.id as A, w.k from w order by a desc",
         "select id as A from w order by a desc",
         "select k as \"B\" from w order by b desc",
         // derby rejects order by id there (an output and an input column)
         "select k as KK, count(*) from w group by id, k order by kk, 2",
         "select id as \"A\" from w order by A desc",
      };

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77573c;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table w (id int, k int)");
         stmt.execute("insert into w values (2, 40), (3, 20), (1, 30)");

         for(String query : queries) {
            List<String> expected = rows(stmt, query, true);

            for(String helper : new String[] { "default", "h2", "h2-ansi" }) {
               for(Map.Entry<String, String> stage :
                  stages(query, helpers().get(helper), "ID", "K").entrySet())
               {
                  // the metadata has the names of the derby columns
                  assertEquals(expected, rows(stmt, stage.getValue(), true),
                               helper + " " + stage.getKey() + ": " + query + " -> " + stage.getValue());
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77573c;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   // the regenerated sql after the metadata step, with the columns of every table
   private static String fixed(String helper, String query, String... columns) throws Exception {
      return fixed(parse(query, helpers().get(helper)), columns);
   }

   private static String fixed(UniformSQL sql, String... columns) throws Exception {
      // the table metadata is cached by data source, use another one
      JDBCDataSource ds = (JDBCDataSource) sql.getDataSource().clone();
      ds.setName(ds.getName() + "_" + (++sources));
      sql.setDataSource(ds);
      fix(sql, ds, columns);
      return regenerate(sql);
   }

   private static String orderBy(String sql) {
      return sql.substring(sql.indexOf("order by"));
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
      helpers.put("snowflake", dataSource("net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://x",
                                          "snowflake", true));
      helpers.put("exasol", dataSource("com.exasol.jdbc.EXADriver", "jdbc:exa:x", "exasol", false));
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
