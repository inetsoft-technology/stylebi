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
 * Bug #77558, a qualified column whose column segment is a quoted identifier
 * ({@code t."MixedCase"}, {@code x."MixedCase"}, {@code "T"."MixedCase"},
 * {@code s.t."Col"}) keeps the quotes of that segment in every clause of the SQL
 * regenerated from the parsed {@link UniformSQL}, before and after
 * {@link JDBCUtil#fixUniformSQLInfo}, through an XML round trip and a clone. #77501 did the
 * same for bare quoted identifiers ({@code "MixedCase"}).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLQualifiedQuotedColumnTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLQualifiedQuotedColumnTest {
   @Test
   void reportedShapeKeepsQuotes() throws Exception {
      UniformSQL sql = parse("select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 " +
                             "group by t.\"MixedCase\" order by t.\"MixedCase\"");
      JDBCSelection selection = (JDBCSelection) sql.getSelection();

      // stored without the quotes, the segment recorded with the flag
      assertEquals("t.MixedCase", selection.getColumn(0));
      assertTrue(selection.isQuoted("t.MixedCase"));
      assertEquals("MixedCase", selection.getQuotedColumn("t.MixedCase"));
      assertEquals("MixedCase", sql.getQuotedFieldColumn("t.MixedCase"));
      assertEquals("select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 group by t.\"MixedCase\" " +
                   "order by t.\"MixedCase\" asc", regenerate(sql));
   }

   @Test
   void everyClauseKeepsQuotes() throws Exception {
      assertEquals("select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 and " +
                   "coalesce(t.\"MixedCase\",0) IN (1,2) order by t.\"MixedCase\" asc",
                   regenerate("select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 and " +
                              "coalesce(t.\"MixedCase\", 0) in (1, 2) order by t.\"MixedCase\""));
      // select and order by aggregates (getValidAggregate) and the having aggregate
      assertEquals("select sum(t.\"MixedCase\"), t.\"MixedCase\" from t group by t.\"MixedCase\" " +
                   "having sum(t.\"MixedCase\") > 0 order by sum(t.\"MixedCase\") asc",
                   regenerate("select t.\"MixedCase\", sum(t.\"MixedCase\") from t group by t.\"MixedCase\" " +
                              "having sum(t.\"MixedCase\") > 0 order by sum(t.\"MixedCase\")"));
      assertEquals("select count(distinct t.\"low\"), max(t.\"MixedCase\") from t",
                   regenerate("select max(t.\"MixedCase\"), count(distinct t.\"low\") from t"));
      // CASE is generated at parse time, before the tables are known
      assertEquals("select (t.\"MixedCase\")*2 as d, case when t.\"MixedCase\" > 0 then " +
                   "t.\"MixedCase\"+1 else 0 END as c from t",
                   regenerate("select case when t.\"MixedCase\" > 0 then t.\"MixedCase\" + 1 else 0 end as c, " +
                              "(t.\"MixedCase\") * 2 as d from t"));
      assertEquals("select t.\"MixedCase\" as m from t order by t.\"MixedCase\" asc",
                   regenerate("select t.\"MixedCase\" as m from t order by m"));
      assertEquals("select t.\"MixedCase\" as b from t", regenerate("select b = t.\"MixedCase\" from t"));
      assertEquals("select cast(t.\"MixedCase\" as varchar(10)), t.\"MixedCase\" || 'x' from t",
                   regenerate("select t.\"MixedCase\" || 'x', cast(t.\"MixedCase\" as varchar(10)) from t"));
   }

   @Test
   void qualifierShapesKeepQuotes() throws Exception {
      assertEquals("select x.\"MixedCase\" from t x where x.\"MixedCase\" = 1",
                   regenerate("select x.\"MixedCase\" from t x where x.\"MixedCase\" = 1"));
      assertEquals("select s.t.\"Col\" from s.t order by s.t.\"Col\" asc",
                   regenerate("select s.t.\"Col\" from s.t order by s.t.\"Col\""));
      // 4 parts, the column_name branch of column_ref
      assertEquals("select c.s.t.\"MixedCase\" from c.s.t", regenerate("select c.s.t.\"MixedCase\" from c.s.t"));
      // a backtick
      assertEquals("select t.\"MixedCase\" from t where t.\"MixedCase\" = 1",
                   regenerate("select t.`MixedCase` from t where t.`MixedCase` = 1"));
      // the table quotes are kept too (#77569)
      assertEquals("select \"T\".\"MixedCase\" from \"T\" where \"T\".\"MixedCase\" = 1",
                   regenerate("select \"T\".\"MixedCase\" from \"T\" where \"T\".\"MixedCase\" = 1"));
      // a qualifier that doesn't resolve to a from table
      assertEquals("select \"Schema\".\"Table\".\"Col\" from \"Schema\".\"Table\" t",
                   regenerate("select \"Schema\".\"Table\".\"Col\" from \"Schema\".\"Table\" t"));
   }

   @Test
   void joinsAndSubqueriesKeepQuotes() throws Exception {
      assertEquals("select t.id from t, u where t.\"MixedCase\" = u.\"MixedCase\"",
                   regenerate("select t.id from t inner join u on t.\"MixedCase\" = u.\"MixedCase\""));
      assertEquals("select t.id from t, u where t.\"MixedCase\" = u.\"MixedCase\"",
                   regenerate("select t.id from t, u where t.\"MixedCase\" = u.\"MixedCase\""));
      assertEquals("select t.id from t LEFT OUTER JOIN u ON t.\"MixedCase\" = u.\"MixedCase\"",
                   regenerate("select t.id from t left outer join u on t.\"MixedCase\" = u.\"MixedCase\""));
      // the outer t doesn't resolve in the subquery, the recorded segment is quoted
      assertEquals("select t.id from t where EXISTS ( select 1 from u where u.\"MixedCase\" = t.\"MixedCase\")",
                   regenerate("select t.id from t where exists (select 1 from u where " +
                              "u.\"MixedCase\" = t.\"MixedCase\")"));

      // ansi joins
      JDBCDataSource h2ansi = dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", true);
      String generated = regenerate(parse("select t.id from t inner join u on t.\"MixedCase\" = u.\"MixedCase\"",
                                          h2ansi));
      assertTrue(generated.contains("ON t.\"MixedCase\" = u.\"MixedCase\""), generated);
      UniformSQL sql = parse("select t.id from t left outer join u on t.\"MixedCase\" = u.\"MixedCase\"", h2ansi);
      JDBCUtil.fixUniformSQLInfo(sql, repository(), null, h2ansi);
      generated = regenerate(sql);
      assertTrue(generated.contains("ON t.\"MixedCase\" = u.\"MixedCase\""), generated);
   }

   @Test
   void unquotedDottedAndBareNamesUnchanged() throws Exception {
      // the same output as before the change
      assertEquals("select sum(t.MixedCase), t.MixedCase from t group by t.MixedCase having " +
                   "sum(t.MixedCase) > 0 order by sum(t.MixedCase) asc",
                   regenerate("select t.MixedCase, sum(t.MixedCase) from t group by t.MixedCase " +
                              "having sum(t.MixedCase) > 0 order by sum(t.MixedCase)"));
      assertEquals("select (t.MixedCase)*2 as d, case when t.MixedCase > 0 then t.MixedCase+1 else 0 END " +
                   "as c from t",
                   regenerate("select case when t.MixedCase > 0 then t.MixedCase + 1 else 0 end as c, " +
                              "(t.MixedCase) * 2 as d from t"));
      // a quoted segment with a dot stays on its quoted path, unflagged
      UniformSQL sql = parse("select t.\"a.b\" from t where t.\"a.b\" = 1 and coalesce(t.\"a.b\", 0) in (1, 2) " +
                             "order by t.\"a.b\"");
      assertEquals("t.\"a.b\"", sql.getSelection().getColumn(0));
      assertFalse(((JDBCSelection) sql.getSelection()).isQuoted("t.\"a.b\""));
      assertEquals("select t.\"a.b\" from t where t.\"a.b\" = 1 and coalesce(t.\"a.b\",0) IN (1,2) " +
                   "order by t.\"a.b\" asc", regenerate(sql));
      // bare quoted names (#77501)
      sql = parse("select \"MixedCase\" as m from t order by m");
      assertNull(((JDBCSelection) sql.getSelection()).getQuotedColumn("MixedCase"));
      assertEquals("select \"MixedCase\" as m from t order by \"MixedCase\" asc", regenerate(sql));
      // brackets are not flagged, as before
      sql = parse("select t.[MixedCase] from t");
      assertFalse(((JDBCSelection) sql.getSelection()).isQuoted(sql.getSelection().getColumn(0)));
   }

   @Test
   void helpersAndStagesKeepQuotes() throws Exception {
      String[] names = { "t.\"MixedCase\"", "\"T\".\"MixedCase\"", "\"Schema\".\"Table\".\"Col\"", "s.t.\"Col\"",
                         "x.\"MixedCase\"", "t.`MixedCase`" };
      String[] froms = { "t", "\"T\"", "\"Schema\".\"Table\"", "s.t", "t x", "t" };
      String[] templates = {
         "select N from F where N = 1 and coalesce(N, 0) in (1, 2) order by N",
         "select N, sum(N) from F group by N having sum(N) > 0 order by sum(N)",
         "select case when N > 0 then N + 1 else 0 end as c, (N) * 2 as d from F",
         "select N as m from F order by m",
      };
      // the column, and the same column quoted as one identifier with its qualifier
      Pattern unquoted = Pattern.compile("(?<![\"`])\\b(MixedCase|Col)\\b");
      Pattern whole = Pattern.compile("\"[^\"\\s]*\\.(MixedCase|Col)\"");
      // the case-folded twin column
      Pattern twin = Pattern.compile("\\b(MIXEDCASE|COL)\\b");
      int checked = 0;

      for(Map.Entry<String, JDBCDataSource> helper : helpers().entrySet()) {
         for(int i = 0; i < names.length; i++) {
            for(String template : templates) {
               String query = template.replace("F", "\u0000").replace("N", names[i]).replace("\u0000", froms[i]);
               JDBCDataSource ds = helper.getValue();

               for(Map.Entry<String, String> stage : stages(query, ds).entrySet()) {
                  String label = helper.getKey() + " " + stage.getKey() + ": " + query + " -> " + stage.getValue();
                  String generated = stage.getValue();

                  assertFalse(generated.contains("\"\""), label);
                  assertFalse(whole.matcher(generated).find(), label);
                  assertFalse(unquoted.matcher(generated).find(), label);
                  assertFalse(twin.matcher(generated).find(), label);
                  checked++;
               }
            }
         }
      }

      assertTrue(checked > 900, "checked " + checked);
   }

   @Test
   void caseSensitiveHelpersQuoteEverySegment() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         JDBCDataSource ds = helpers().get(key);
         String query = "select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 group by t.\"MixedCase\"";
         UniformSQL sql = parse(query, ds);

         // the column segment is stored without its quotes
         assertEquals("\"t\".MixedCase", sql.getSelection().getColumn(0), key);
         String expected = "select \"t\".\"MixedCase\" from \"t\" where \"t\".\"MixedCase\" = 1 " +
            "group by \"t\".\"MixedCase\"";
         assertEquals(expected, regenerate(sql), key);
         JDBCUtil.fixUniformSQLInfo(sql, repository(), null, ds);
         assertEquals(expected, regenerate(sql), key);
      }
   }

   @Test
   void oracleKeepsTheCaseOfAQuotedSegment() throws Exception {
      JDBCDataSource oracle = helpers().get("oracle");
      UniformSQL sql = parse("select t.\"MixedCase\", t.low from t", oracle);

      // an unquoted name is upper-cased, a quoted one is not
      assertEquals("t.MixedCase", sql.getSelection().getColumn(0));
      assertEquals("T.LOW", sql.getSelection().getColumn(1));
      assertEquals("select T.LOW, t.\"MixedCase\" from t", regenerate(sql));
   }

   @Test
   void flagsSurviveXmlRoundTripAndClone() throws Exception {
      String query = "select t.\"MixedCase\", b = t.\"low\", sum(t.\"MixedCase\") from t " +
         "where t.\"MixedCase\" = 1 group by t.\"MixedCase\", t.\"low\" " +
         "having max(t.\"low\") > 0 order by t.\"low\" desc";

      for(Map.Entry<String, JDBCDataSource> helper : helpers().entrySet()) {
         UniformSQL sql = parse(query, helper.getValue());
         String expected = regenerate(sql);
         UniformSQL loaded = reload(sql);
         UniformSQL copy = new UniformSQL();
         copy.read(sql);
         // read() doesn't copy the data source
         copy.setDataSource(sql.getDataSource());

         assertEquals(expected, regenerate(loaded), helper.getKey());
         assertEquals(expected, regenerate((UniformSQL) sql.clone()), helper.getKey());
         assertEquals(expected, regenerate(copy), helper.getKey());
         String column = sql.getSelection().getColumn(0);
         assertEquals("MixedCase", ((JDBCSelection) loaded.getSelection()).getQuotedColumn(column));
         assertEquals("MixedCase", loaded.getQuotedFieldColumn(column));
         // the expression in the where clause
         assertTrue(regenerate(loaded).contains("where " + column.replace("MixedCase", "\"MixedCase\"")),
                    expected);
      }
   }

   @Test
   void qualifiedFlagsAreWrittenInAFormOlderVersionsIgnore() throws Exception {
      UniformSQL sql = parse("select t.\"MixedCase\", \"low\" from t where t.\"MixedCase\" = 1 and \"low\" = 2 " +
                             "group by t.\"MixedCase\", \"low\" order by t.\"MixedCase\"");
      String xml = toXML(sql);

      // a qualified entry: the select column, the group by and order by fields, the where field
      assertTrue(xml.contains("<quotedColumn column=\"MixedCase\"/>"), xml);
      assertTrue(xml.contains("<field quotedColumn=\"MixedCase\"><![CDATA[t.MixedCase]]>"), xml);
      assertTrue(xml.contains("<field order=\"asc\" quotedColumn=\"MixedCase\"><![CDATA[t.MixedCase]]>"), xml);
      assertTrue(xml.contains("columnQuote=\"1\" quotedColumn=\"MixedCase\""), xml);
      // a bare entry keeps the format of #77501
      assertTrue(xml.contains("<quoted><![CDATA[true]]></quoted>"), xml);
      assertTrue(xml.contains("<field quoted=\"true\"><![CDATA[low]]>"), xml);
      assertTrue(xml.contains(" quote=\"1\""), xml);
      // the formats older versions read are used for the bare entry only
      assertEquals(1, count(xml, "<quoted>"), xml);
      assertEquals(1, count(xml, " quoted=\"true\""), xml);
      assertEquals(1, count(xml, " quote=\""), xml);
   }

   /**
    * A version before this change reads quote="1", quoted="true" and &lt;quoted&gt;true&lt;/quoted&gt;
    * exactly as this version reads them, and ignores the attributes and elements it doesn't
    * know. So removing the new ones simulates an older reader. It must regenerate the
    * qualified names unquoted, as before this change, and never quote the whole qualified
    * name ("t.MixedCase", ""t".MixedCase"), which is invalid SQL.
    */
   @Test
   void olderVersionReadsQualifiedFlagsAsUnquoted() throws Exception {
      String[] queries = {
         "select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 group by t.\"MixedCase\" order by t.\"MixedCase\"",
         "select t.id from t where exists (select 1 from u where u.\"MixedCase\" = t.\"MixedCase\")",
         "select \"Schema\".\"Table\".\"Col\" from \"Schema\".\"Table\" t",
         "select sum(t.\"MixedCase\") from t having sum(t.\"MixedCase\") > 1",
         "select x.\"MixedCase\", \"low\" from t x where \"low\" = 1",
      };
      Pattern whole = Pattern.compile("\"[^\"\\s]*\\.(MixedCase|Col)\"");

      for(Map.Entry<String, JDBCDataSource> helper : helpers().entrySet()) {
         JDBCDataSource ds = helper.getValue();

         for(String query : queries) {
            for(boolean fixed : new boolean[] { false, true }) {
               if(fixed && ds == null) {
                  continue;
               }

               UniformSQL sql = parse(query, ds);

               if(fixed) {
                  JDBCUtil.fixUniformSQLInfo(sql, repository(), null, ds);
               }

               UniformSQL old = load(asOlderVersionReads(toXML(sql)), ds);
               String generated = regenerate(old);
               String label = helper.getKey() + (fixed ? " fixed: " : " parse: ") + query + " -> " + generated;

               assertFalse(generated.contains("\"\""), label);
               assertFalse(whole.matcher(generated).find(), label);
               // the bare name keeps its quotes
               assertTrue(!query.contains("\"low\"") || generated.contains("\"low\""), label);
            }
         }
      }

      // the output before this change
      UniformSQL sql = parse(queries[0]);
      assertEquals("select t.MixedCase from t where t.MixedCase = 1 group by t.MixedCase order by t.MixedCase asc",
                   regenerate(load(asOlderVersionReads(toXML(sql)), null)));
      sql = parse(queries[0], helpers().get("postgresql"));
      assertEquals("select \"t\".MixedCase from \"t\" where \"t\".MixedCase = 1 group by \"t\".MixedCase " +
                   "order by \"t\".MixedCase asc",
                   regenerate(load(asOlderVersionReads(toXML(sql)), helpers().get("postgresql"))));
   }

   @Test
   void oldXmlWithoutSegmentsIsUnchanged() throws Exception {
      // xml of a version before this change, the qualified name is unflagged
      UniformSQL sql = parse("select t.MixedCase from t where t.MixedCase = 1 order by t.MixedCase");
      UniformSQL loaded = reload(sql);

      assertFalse(((JDBCSelection) loaded.getSelection()).isQuoted("t.MixedCase"));
      assertEquals("select t.MixedCase from t where t.MixedCase = 1 order by t.MixedCase asc", regenerate(loaded));
   }

   @Test
   void regeneratedSqlRegeneratesToItself() throws Exception {
      String[] queries = {
         "select t.\"MixedCase\", sum(t.\"low\") from t where coalesce(t.\"MixedCase\", 0) in (1, 2) " +
            "group by t.\"MixedCase\" having max(t.\"low\") > 0 order by t.\"MixedCase\"",
         "select case when x.\"MixedCase\" = 1 then x.\"low\" end as c from t x",
         "select t.id from t left outer join u on t.\"MixedCase\" = u.\"MixedCase\"",
         "select t.id from t where exists (select 1 from u where u.\"MixedCase\" = t.\"MixedCase\")",
         "select s.t.\"Col\" from s.t",
      };

      for(Map.Entry<String, JDBCDataSource> helper : helpers().entrySet()) {
         for(String query : queries) {
            String generated = regenerate(parse(query, helper.getValue()));
            assertEquals(generated, regenerate(parse(generated, helper.getValue())),
                         helper.getKey() + ": " + query);
         }
      }
   }

   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select t.\"MixedCase\" from t",
         "select t.\"MixedCase\" from t where t.\"MixedCase\" = 1 and coalesce(t.\"MixedCase\", 0) in (1, 2) " +
            "order by t.\"MixedCase\"",
         "select t.\"MixedCase\", sum(t.\"MixedCase\") from t group by t.\"MixedCase\" " +
            "having sum(t.\"MixedCase\") > 0 order by sum(t.\"MixedCase\")",
         "select case when t.\"MixedCase\" > 0 then t.\"MixedCase\" + 1 else 0 end as c, " +
            "(t.\"MixedCase\") * 2 as d from t",
         "select t.\"MixedCase\" as m from t order by m",
         "select max(t.\"MixedCase\"), count(distinct t.\"low\") from t",
         "select t.\"low\" from t where t.\"low\" = 2",
         "select x.\"MixedCase\" from t x where x.\"MixedCase\" = 2",
         "select \"T\".\"MixedCase\" from \"T\" where \"T\".\"MixedCase\" = 1",
         "select s.t.\"Col\" from s.t where s.t.\"Col\" = 1",
         "select t.id from t inner join u on t.\"MixedCase\" = u.\"MixedCase\"",
         "select t.id from t left outer join u on t.\"MixedCase\" = u.\"MixedCase\"",
         "select t.id from t, u where t.\"MixedCase\" = u.\"MixedCase\"",
         "select t.id from t where exists (select 1 from u where u.\"MixedCase\" = t.\"MixedCase\")",
         "select t.id from t where t.id = (select max(u.id) from u where u.\"low\" = t.\"low\")",
         // unquoted controls
         "select t.MixedCase from t where t.MixedCase = 1",
         "select t.MixedCase, sum(t.low) from t group by t.MixedCase",
      };
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("default", null);
      helpers.put("h2", helpers().get("h2"));
      helpers.put("h2-ansi", helpers().get("h2-ansi"));

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77558;create=true");
          Statement stmt = conn.createStatement())
      {
         // "MixedCase" and MIXEDCASE are different columns, an unquoted MixedCase is MIXEDCASE
         for(String table : new String[] { "t", "u" }) {
            stmt.execute("create table " + table + " (\"MixedCase\" int, MIXEDCASE int, \"low\" int, LOW int, id int)");
            stmt.execute("insert into " + table + " values (1, 100, 2, 200, 10), (2, 1, 5, 2, 20), " +
                         "(null, 2, null, 5, 30)");
         }

         stmt.execute("create schema s");
         stmt.execute("create table s.t (\"Col\" int, COL int)");
         stmt.execute("insert into s.t values (1, 10), (2, 1)");

         for(String query : queries) {
            List<String> expected = rows(stmt, query);

            for(Map.Entry<String, JDBCDataSource> helper : helpers.entrySet()) {
               JDBCDataSource ds = helper.getValue();

               for(Map.Entry<String, String> stage : stages(query, ds).entrySet()) {
                  String generated = stage.getValue();
                  assertEquals(expected, rows(stmt, generated),
                               helper.getKey() + " " + stage.getKey() + ": " + query + " -> " + generated);
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77558;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   /**
    * On a case-sensitive database the parser quotes every segment of an unquoted name too
    * (t.MixedCase is parsed as "t"."MixedCase"), and the metadata step rewrites it to the
    * case of the column in the database. That repair must still apply to an unquoted name,
    * and must not apply to a quoted one.
    */
   @Test
   void unquotedNamesKeepTheMetadataCaseRepair() throws Exception {
      String unquoted = "select t.MixedCase from t where t.MixedCase = 1 group by t.MixedCase " +
         "order by t.MixedCase";
      String quoted = unquoted.replace("t.MixedCase", "t.\"MixedCase\"");
      String[][] cases = {
         // helper, the column as the database stores an unquoted MixedCase
         { "postgresql", "mixedcase" }, { "snowflake", "MIXEDCASE" }, { "exasol", "MIXEDCASE" },
      };

      for(String[] c : cases) {
         JDBCDataSource ds = helpers().get(c[0]);
         String folded = "\"t\".\"" + c[1] + "\"";
         UniformSQL sql = parse(unquoted, ds);
         resolve(sql, c[1], "id");
         assertEquals("select " + folded + " from \"t\" where " + folded + " = 1 group by " + folded +
                      " order by " + folded + " asc", regenerate(sql), c[0]);

         // the quoted name keeps its case
         sql = parse(quoted, ds);
         resolve(sql, "MixedCase", "id");
         assertEquals("select \"t\".\"MixedCase\" from \"t\" where \"t\".\"MixedCase\" = 1 group by " +
                      "\"t\".\"MixedCase\" order by \"t\".\"MixedCase\" asc", regenerate(sql), c[0]);
      }
   }

   /**
    * The metadata step rewrites a name to the case of its column. A quoted name ("MixedCase"
    * or t."MixedCase") must resolve to the column of the same case, also when a column that
    * differs only in case (MIXEDCASE) comes first. An unquoted name keeps the first match
    * ignoring case, as before.
    */
   @Test
   void quotedNamesResolveToTheColumnOfTheSameCase() throws Exception {
      String qualified = "select t.\"MixedCase\", sum(t.\"low\") from t where t.\"MixedCase\" = 1 " +
         "group by t.\"MixedCase\" order by t.\"MixedCase\"";
      String bare = qualified.replace("t.\"", "\"");
      String unquoted = qualified.replace("\"", "");

      for(String key : new String[] { "default", "h2", "oracle", "postgresql", "snowflake" }) {
         JDBCDataSource ds = helpers().get(key);

         for(String[] columns : new String[][] { TWIN_FIRST, TWIN_SECOND }) {
            String label = key + " " + columns[0];

            for(String query : new String[] { qualified, bare }) {
               UniformSQL sql = parse(query, ds);
               resolve(sql, columns);
               String generated = regenerate(sql);

               assertFalse(generated.contains("MIXEDCASE"), label + ": " + generated);
               assertFalse(generated.contains("LOW"), label + ": " + generated);
               assertEquals(4, count(generated, "\"MixedCase\""), label + ": " + generated);
               assertEquals(1, count(generated, "\"low\""), label + ": " + generated);
            }

            // unquoted, the first column ignoring case. Oracle folds the select column
            if(!"oracle".equals(key)) {
               UniformSQL sql = parse(unquoted, ds);
               resolve(sql, columns);
               String generated = regenerate(sql);
               String first = columns[0];

               assertTrue(generated.contains("where " + (ds == null || key.equals("h2") ? "t." : "\"t\".") +
                                             (key.equals("postgresql") || key.equals("snowflake") ?
                                                "\"" + first + "\"" : first) + " = 1"),
                          label + ": " + generated);
            }
         }
      }
   }

   /**
    * Renaming a table alias in the query editor (QueryGraphModelService) renames the columns
    * of the select list, group by and order by. A quoted name keeps its flag and its written
    * case, also when a column that differs only in case comes first in the metadata.
    */
   @Test
   void tableAliasRenameKeepsQuotes() throws Exception {
      String qualified = "select x.\"MixedCase\", x.id from t x where x.\"MixedCase\" = 1 " +
         "group by x.\"MixedCase\", x.id order by x.\"MixedCase\"";
      String bare = "select \"MixedCase\", id from t x where \"MixedCase\" = 1 " +
         "group by \"MixedCase\", id order by \"MixedCase\"";
      String renamed = "select y.\"MixedCase\", y.id from t y where y.\"MixedCase\" = 1 " +
         "group by y.\"MixedCase\", y.id order by y.\"MixedCase\"";

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77558r;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table t (\"MixedCase\" int, MIXEDCASE int, id int)");
         stmt.execute("insert into t values (1, 100, 10), (2, 1, 20), (1, 2, 30)");
         List<String> expected = rows(stmt, renamed);

         // the query editor always has the table metadata. Without it a select column isn't
         // known as a table column and keeps the old alias, quoted or not
         for(String key : new String[] { "h2", "oracle", "postgresql", "snowflake" }) {
            for(String[] columns : new String[][] { TWIN_FIRST, TWIN_SECOND }) {
               for(String query : new String[] { qualified, bare }) {
                  JDBCDataSource ds = helpers().get(key);

                  if(ds != null) {
                     // the table metadata is cached by data source
                     ds = (JDBCDataSource) ds.clone();
                     ds.setName(ds.getName() + "Rename" + columns[0]);
                  }

                  UniformSQL sql = parse(query, ds);
                  fix(sql, ds, columns);
                  renameAlias(sql, "x", "y");
                  fix(sql, ds, columns);

                  for(String generated : new String[] { regenerate(sql), regenerate(reload(sql)) }) {
                     String label = key + " " + columns[0] + ": " + query + " -> " + generated;

                     assertFalse(generated.contains("MIXEDCASE"), label);
                     assertFalse(generated.matches(".*\\bx\\..*") || generated.contains("\"x\"."), label);
                     assertEquals(4, count(generated, "\"MixedCase\""), label);

                     if("h2".equals(key)) {
                        assertEquals(expected, rows(stmt, generated), label);
                     }
                  }
               }
            }
         }

         // an unquoted name keeps the first column ignoring case, as before
         JDBCDataSource h2 = (JDBCDataSource) helpers().get("h2").clone();
         h2.setName("ds77558RenameUnquoted");
         UniformSQL sql = parse(qualified.replace("\"", ""), h2);
         fix(sql, h2, TWIN_FIRST);
         renameAlias(sql, "x", "y");
         fix(sql, h2, TWIN_FIRST);
         assertEquals("select y.MIXEDCASE, y.id from t y where y.MIXEDCASE = 1 group by y.MIXEDCASE, y.id " +
                      "order by y.MIXEDCASE asc", regenerate(sql));
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77558r;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   // the metadata step, with the columns of every table
   private static void fix(UniformSQL sql, JDBCDataSource ds, String[] columns) throws Exception {
      if(ds != null) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
      }
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

   /**
    * t."MixedCase" and t.MixedCase are both stored as t.MixedCase. When both spellings are in
    * the same select or group list, each keeps its own quoting, since the flag is kept by
    * position (#77573). It used to be keyed by the stored name, and both were quoted.
    */
   @Test
   void bothSpellingsOfANameKeepTheirOwnFlag() throws Exception {
      assertEquals("select t.\"MixedCase\", t.MixedCase as b from t group by t.\"MixedCase\", t.MixedCase",
                   regenerate("select t.\"MixedCase\", t.MixedCase as b from t " +
                              "group by t.\"MixedCase\", t.MixedCase"));
   }

   /**
    * On a database whose parser quotes every segment (PostgreSQL, Snowflake, Exasol, where
    * SQLHelper.isCaseSensitive() is true), an unquoted aggregate argument sum(q.MixedCase) is
    * stored as sum("q"."MixedCase"), the same text as sum(q."MixedCase"). It must keep the
    * metadata case repair it had before this change, in the select list and in order by.
    * The expected strings are the output before this change.
    */
   @Test
   void unquotedAggregatesKeepTheMetadataCaseRepairOnCaseSensitiveDatabases() throws Exception {
      String sum = "select q.id, sum(q.MixedCase) from t q group by q.id";
      String pg = "select \"q\".\"id\", sum(q.\"%s\") from \"t\" q group by \"q\".\"id\"";
      String folding = "select \"q\".\"id\", sum(q.%s) from \"t\" q group by \"q\".\"id\"";

      // the column as postgresql stores an unquoted MixedCase
      assertEquals(String.format(pg, "mixedcase"), aggregate("postgresql", sum, "mixedcase", "id"));
      // the first column ignoring case
      assertEquals(String.format(pg, "MIXEDCASE"), aggregate("postgresql", sum, TWIN_FIRST));
      assertEquals(String.format(pg, "mixedcase") + " order by sum(q.\"mixedcase\") asc",
                   aggregate("postgresql", sum + " order by sum(q.MixedCase)", "mixedcase", "id"));

      for(String key : new String[] { "snowflake", "exasol" }) {
         // the column as snowflake and exasol store an unquoted MixedCase
         assertEquals(String.format(folding, "MIXEDCASE"), aggregate(key, sum, "MIXEDCASE", "id"), key);
         assertEquals(String.format(folding, "MIXEDCASE") + " order by sum(q.MIXEDCASE) asc",
                      aggregate(key, sum + " order by sum(q.MixedCase)", "MIXEDCASE", "id"), key);
         // the first column ignoring case, unquoted, so the database folds it to MIXEDCASE
         assertEquals(String.format(folding, "MixedCase"), aggregate(key, sum, TWIN_SECOND), key);
         assertEquals(String.format(folding, "MIXEDCASE"), aggregate(key, sum, TWIN_FIRST), key);
         // without the metadata step
         assertEquals(String.format(folding, "MixedCase"), aggregate(key, sum), key);
         assertEquals("select \"q\".\"id\" from \"t\" q group by \"q\".\"id\" order by sum(q.MixedCase) asc",
                      aggregate(key, "select q.id from t q group by q.id order by sum(q.MixedCase)"), key);
      }
   }

   /**
    * On a database whose parser keeps the text of a name (H2, Oracle), a quoted aggregate
    * argument keeps its case when a column that differs only in case comes first in the
    * metadata, and an unquoted one takes the first column ignoring case, as before.
    */
   @Test
   void aggregatesOnCaseFoldingDatabasesResolveByTheWrittenQuotes() throws Exception {
      String quoted = "select q.id, sum(q.\"MixedCase\") from t q group by q.id";
      String unquoted = "select q.id, sum(q.MixedCase) from t q group by q.id";

      assertEquals("select q.id, sum(q.\"MixedCase\") from t q group by q.id",
                   aggregate("h2", quoted, TWIN_FIRST));
      assertEquals("select q.id, sum(q.\"MixedCase\") from T q group by q.id",
                   aggregate("oracle", quoted, TWIN_FIRST));
      assertEquals("select q.id, sum(q.MIXEDCASE) from t q group by q.id", aggregate("h2", unquoted, TWIN_FIRST));
      assertEquals("select q.id, sum(q.\"MIXEDCASE\") from T q group by q.id",
                   aggregate("oracle", unquoted, TWIN_FIRST));
   }

   /**
    * On PostgreSQL, Snowflake and Exasol a quoted aggregate argument sum(q."MixedCase") is
    * stored with the same text as the unquoted sum(q.MixedCase) (see above). The parser records
    * the quoted column of the aggregate (#77578), so it keeps the written case, quoted, instead
    * of the metadata case repair of an unquoted name. See UniformSQLQuotedAggregateTest.
    */
   @Test
   void quotedAggregatesOnCaseSensitiveDatabasesKeepTheWrittenCase() throws Exception {
      assertEquals("select \"q\".\"id\", sum(q.\"MixedCase\") from \"t\" q group by \"q\".\"id\"",
                   aggregate("postgresql", "select q.id, sum(q.\"MixedCase\") from t q group by q.id",
                             "MIXEDCASE", "MixedCase", "id"));
      assertEquals("select \"q\".\"id\", sum(q.\"MixedCase\") from \"t\" q group by \"q\".\"id\"",
                   aggregate("snowflake", "select q.id, sum(q.\"MixedCase\") from t q group by q.id",
                             "MixedCase", "id"));
      assertEquals("select \"q\".\"id\", sum(q.\"low\") from \"t\" q group by \"q\".\"id\"",
                   aggregate("exasol", "select q.id, sum(q.\"low\") from t q group by q.id", "LOW", "low", "id"));
   }

   /**
    * PostgreSQL, Snowflake and Exasol store every segment quoted, sum("q"."MixedCase") for an
    * unquoted sum(q.MixedCase). A query parsed there and generated by another helper (a saved
    * query loaded with another data source, or the data source of a query changed) keeps the
    * metadata case repair, as before this change, because a quoted qualifier shows that the
    * quotes may not be the source's.
    */
   @Test
   void aggregatesParsedByACaseSensitiveHelperKeepTheRepairOnOtherHelpers() throws Exception {
      String select = "select q.id, sum(q.MixedCase) from t q group by q.id";
      String ordered = select + " order by sum(q.MixedCase)";

      for(String from : new String[] { "postgresql", "snowflake", "exasol" }) {
         for(String column : new String[] { "MIXEDCASE", "mixedcase" }) {
            for(boolean xml : new boolean[] { true, false }) {
               String label = from + " " + column + (xml ? " xml" : " direct");
               String h2 = "sum(q." + column + ")";
               String oracle = "sum(q.\"" + column + "\")";

               assertEquals("select \"q\".\"id\", " + h2 + " from \"t\" q group by \"q\".\"id\"",
                            crossHelper(from, "h2", select, xml, column, "id"), label);
               assertEquals("select \"q\".\"id\", " + h2 + " from \"t\" q group by \"q\".\"id\" order by " +
                            h2 + " asc", crossHelper(from, "h2", ordered, xml, column, "id"), label);
               assertEquals("select \"q\".\"id\", " + oracle + " from \"T\" q group by \"q\".\"id\"",
                            crossHelper(from, "oracle", select, xml, column, "id"), label);
               assertEquals("select \"q\".\"id\", " + oracle + " from \"T\" q group by \"q\".\"id\" order by " +
                            oracle + " asc", crossHelper(from, "oracle", ordered, xml, column, "id"), label);
            }
         }
      }
   }

   /**
    * A fully quoted aggregate argument written on H2 or Oracle keeps its quotes when the
    * qualifier is not special, the parser stores that qualifier unquoted (q."MixedCase").
    */
   @Test
   void fullyQuotedAggregatesOnCaseFoldingDatabasesKeepTheWrittenCase() throws Exception {
      assertEquals("select q.id, sum(q.\"MixedCase\") from t q group by q.id",
                   aggregate("h2", "select q.id, sum(\"q\".\"MixedCase\") from t q group by q.id", TWIN_FIRST));
      assertEquals("select sum(q.\"MixedCase\") from t q",
                   aggregate("h2", "select sum(\"q\".\"MixedCase\") from t \"q\"", TWIN_FIRST));
      assertEquals("select sum(Q.\"MixedCase\") from t Q",
                   aggregate("h2", "select sum(\"Q\".\"MixedCase\") from t \"Q\"", TWIN_FIRST));
      // a quoted table keeps its quotes (#77569)
      assertEquals("select sum(\"T\".\"MixedCase\") from \"T\"",
                   aggregate("h2", "select sum(\"T\".\"MixedCase\") from \"T\"", TWIN_FIRST));
      assertEquals("select q.id, sum(q.\"MixedCase\") from T q group by q.id",
                   aggregate("oracle", "select q.id, sum(\"q\".\"MixedCase\") from t q group by q.id", TWIN_FIRST));
      assertEquals("select sum(t.\"MixedCase\") from T t",
                   aggregate("oracle", "select sum(\"t\".\"MixedCase\") from t", TWIN_FIRST));
      assertEquals("select sum(\"T\".\"MixedCase\") from \"T\"",
                   aggregate("oracle", "select sum(\"T\".\"MixedCase\") from \"T\"", TWIN_FIRST));
   }

   /**
    * On H2 and Oracle a special qualifier (a space or a keyword) stays quoted at parse, so
    * sum("my q"."MixedCase") is stored with the same text that PostgreSQL, Snowflake and Exasol
    * store for sum("my q".MixedCase). The parser records the quoted column of the aggregate
    * (#77578), so it keeps the written case. See UniformSQLQuotedAggregateTest.
    */
   @Test
   void quotedAggregatesWithASpecialQualifierKeepTheWrittenCase() throws Exception {
      assertEquals("select sum(\"my q\".\"MixedCase\") from t \"my q\"",
                   aggregate("h2", "select sum(\"my q\".\"MixedCase\") from t \"my q\"", TWIN_FIRST));
      assertEquals("select sum(\"my q\".\"MixedCase\") from t \"my q\"",
                   aggregate("h2", "select sum(\"my q\".\"MixedCase\") from t \"my q\"", TWIN_SECOND));
      assertEquals("select sum(\"order\".\"MixedCase\") from t \"order\"",
                   aggregate("h2", "select sum(\"order\".\"MixedCase\") from t \"order\"", TWIN_FIRST));
      assertEquals("select sum(\"my q\".\"low\") from t \"my q\"",
                   aggregate("h2", "select sum(\"my q\".\"low\") from t \"my q\"", TWIN_FIRST));
      assertEquals("select sum(\"my q\".\"MixedCase\") from T \"my q\"",
                   aggregate("oracle", "select sum(\"my q\".\"MixedCase\") from t \"my q\"", TWIN_FIRST));
      assertEquals("select sum(\"order\".\"MixedCase\") from T \"order\"",
                   aggregate("oracle", "select sum(\"order\".\"MixedCase\") from t \"order\"", TWIN_FIRST));
      assertEquals("select sum(\"my q\".\"low\") from T \"my q\"",
                   aggregate("oracle", "select sum(\"my q\".\"low\") from t \"my q\"", TWIN_FIRST));
   }

   // parsed with the data source of one helper, generated with another after the metadata step,
   // from the xml loaded with the other data source or after setting it on the parsed sql
   private static String crossHelper(String from, String to, String query, boolean xml, String... columns)
      throws Exception
   {
      JDBCDataSource parsed = (JDBCDataSource) helpers().get(from).clone();
      parsed.setName(parsed.getName() + "Cross" + (++aggregateSources));
      JDBCDataSource ds = (JDBCDataSource) helpers().get(to).clone();
      ds.setName(ds.getName() + "Cross" + (++aggregateSources));
      UniformSQL sql = parse(query, parsed);

      if(xml) {
         sql = load(toXML(sql), ds);
      }
      else {
         sql.setDataSource(ds);
      }

      fix(sql, ds, columns);
      return regenerate(sql);
   }

   // the sql regenerated after the metadata step with the columns of every table, or after
   // the parse if there are no columns
   private static String aggregate(String key, String query, String... columns) throws Exception {
      // the table metadata is cached by data source
      JDBCDataSource ds = (JDBCDataSource) helpers().get(key).clone();
      ds.setName(ds.getName() + "Aggregate" + (++aggregateSources));
      UniformSQL sql = parse(query, ds);

      if(columns.length > 0) {
         fix(sql, ds, columns);
      }

      return regenerate(sql);
   }

   private static int aggregateSources;

   // the regenerated sql after each stage of the pipeline
   private static Map<String, String> stages(String query, JDBCDataSource ds) throws Exception {
      Map<String, String> stages = new LinkedHashMap<>();
      UniformSQL sql = parse(query, ds);
      String parsed = regenerate(sql);
      stages.put("parse", parsed);
      stages.put("xml", regenerate(reload(parse(query, ds))));
      stages.put("clone", regenerate((UniformSQL) parse(query, ds).clone()));

      if(ds != null) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(), null, ds);
         stages.put("fixed", regenerate(sql));
         stages.put("fixed-xml", regenerate(reload(sql)));
         stages.put("fixed-clone-xml", regenerate(reload((UniformSQL) sql.clone())));

         // the column of the same case is used when its twin comes first in the metadata
         // the table metadata is cached by data source, use another one
         JDBCDataSource tds = (JDBCDataSource) ds.clone();
         tds.setName(ds.getName() + "TwinFirst");
         UniformSQL twin = parse(query, tds);
         JDBCUtil.fixUniformSQLInfo(twin, repository(TWIN_FIRST), null, tds);
         stages.put("fixed-twin-first", regenerate(twin));
         stages.put("fixed-twin-first-xml", regenerate(reload(twin)));
      }

      return stages;
   }

   // the metadata step of JDBCUtil.fixUniformSQLInfo, with the columns of table t
   private static void resolve(UniformSQL sql, String... columns) {
      for(String column : columns) {
         sql.addField(new XField(column, column, "t", XSchema.INTEGER));
      }

      JDBCUtil.fixSelectionInfo(sql);
      JDBCUtil.expandAsterisk(sql);
      JDBCUtil.fixWhereInfo(sql);
      sql.syncTable();
   }

   // removes what a version before this change doesn't read
   private static String asOlderVersionReads(String xml) {
      return xml.replaceAll(" columnQuote=\"[^\"]*\"", "")
         .replaceAll(" quotedColumn=\"[^\"]*\"", "")
         .replaceAll("<quotedColumn column=\"[^\"]*\"/>", "");
   }

   private static int count(String str, String part) {
      return str.split(Pattern.quote(part), -1).length - 1;
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

   // the column metadata comes from the repository. SQLTypes.getQualifiedName logs an error
   // that it can't get the root meta-data from XRepository.getRepository(), then goes on
   private static XRepository repository() throws Exception {
      return repository(TWIN_SECOND);
   }

   // the metadata of every table, a quoted name has a twin that differs only in case
   private static final String[] TWIN_SECOND = { "MixedCase", "MIXEDCASE", "id", "low", "LOW", "a.b", "Col", "COL" };
   private static final String[] TWIN_FIRST = { "MIXEDCASE", "MixedCase", "id", "LOW", "low", "a.b", "COL", "Col" };

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

   // the rows, with the values of each row sorted, since the generated sql may reorder columns
   private static List<String> rows(Statement stmt, String query) throws SQLException {
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

      Collections.sort(rows);
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
      ds.setName("ds77558" + product + ansiJoin);
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
      return normalize(sql.getSQLString());
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
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
}
