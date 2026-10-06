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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77646, on Oracle a column the user wrote unquoted as the only argument of an aggregate
 * (or any single column function) of a physical table ({@code max(a.v)}) was regenerated quoted
 * in the written case ({@code max(a."v")}). Oracle folds the unquoted name to V, so the quoted
 * lower case name is another identifier (ORA-00904). The column of a physical table is quoted
 * only when needed, and then in upper case, as Oracle stores an unquoted name. The reference to
 * the column of a derived table keeps the quoting of the alias the subquery defines it with, and
 * no other helper changes.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLOracleAggregateColumnQuoteTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLOracleAggregateColumnQuoteTest {
   private static final String[] ORACLE = { "oracle", "oracle-ansi" };
   private static final String[] UPPER = { "V", "ID" };
   private static final String[] LOWER = { "v", "id" };
   // a column with a twin that differs only in case, the lower case one first
   private static final String[] TWIN = { "v", "V", "ID" };

   @Test
   void reportedShapeIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         UniformSQL sql = parse("select max(a.v) from a", source(key));
         assertEquals(OracleSQLHelper.class, SQLHelper.getSQLHelper(sql).getClass(), key);

         // was max(a."v"), a lower case column on Oracle
         assertEquals("select max(a.v) from a", aggregate(key, "select max(a.v) from a"), key);
         // was max(a."V")
         assertEquals("select max(a.V) from A a", aggregate(key, "select max(a.v) from a", UPPER), key);
         // was max(a."v"), the lower case twin, the user's unquoted v is V on Oracle
         assertEquals("select max(a.v) from A a", aggregate(key, "select max(a.v) from a", LOWER), key);
         assertEquals("select max(a.v) from A a", aggregate(key, "select max(a.v) from a", TWIN), key);
         // was max(a."MixedCase")
         assertEquals("select max(a.MixedCase) from a", aggregate(key, "select max(a.MixedCase) from a"), key);
         // was max(a."v") as "mx", the alias is still quoted
         assertEquals("select max(a.v) as \"mx\" from a", aggregate(key, "select max(a.v) mx from a"), key);
         // was max(a."id")
         assertEquals("select max(a.id) from a", aggregate(key, "select max(a.id) from a"), key);
      }
   }

   /**
    * Every single column function shape that reaches SQLHelper.getValidAggregate, also of an
    * aliased table, in the select list and in the order by.
    */
   @Test
   void everySingleColumnFunctionIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         // was max(t."v") / max(t."V")
         assertEquals("select max(t.v) from a t", aggregate(key, "select max(t.v) from a t"), key);
         assertEquals("select max(t.V) from A t", aggregate(key, "select max(t.v) from a t", UPPER), key);
         // was upper(t."v"), not an aggregate
         assertEquals("select upper(t.v) from a t", aggregate(key, "select upper(t.v) from a t"), key);
         // was count(distinct t."v")
         assertEquals("select count(distinct t.v) from a t",
                      aggregate(key, "select count(distinct t.v) from a t"), key);
         // was sum(t."v") in the select list and in the order by
         assertEquals("select T.ID, sum(t.v) from a t group by t.id order by sum(t.v) asc",
                      aggregate(key, "select t.id, sum(t.v) from a t group by t.id order by sum(t.v)"), key);
         assertEquals("select A.ID, sum(a.v) from a group by a.id order by sum(a.v) asc",
                      aggregate(key, "select a.id, sum(a.v) from a group by a.id order by sum(a.v)"), key);
         assertEquals("select a.ID, sum(a.V) from A a group by a.ID order by sum(a.V) asc",
                      aggregate(key, "select a.id, sum(a.v) from a group by a.id order by sum(a.v)", UPPER), key);
         // was sum(t."v") in the select list, the having and group by are as written
         assertEquals("select T.ID, sum(t.v) from a t group by t.id having sum(t.v) > 0",
                      aggregate(key, "select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0"), key);
         assertEquals("select count(*), upper(t.v) from a t group by upper(t.v)",
                      aggregate(key, "select upper(t.v), count(*) from a t group by upper(t.v)"), key);
      }
   }

   /**
    * The #73735 shape, a column of a schema qualified table: the table part is unchanged, the
    * column is not quoted (it was "CUSTOMER_ID", the same column).
    */
   @Test
   void schemaQualifiedTableColumnIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         assertEquals("select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1",
                      aggregate(key, "select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1"), key);
         assertEquals("select count(o.CUSTOMER_ID) from SA.ORDERS1 o",
                      aggregate(key, "select count(o.CUSTOMER_ID) from SA.ORDERS1 o"), key);
      }
   }

   /**
    * A column that must be quoted (an Oracle keyword) is quoted in upper case, the name Oracle
    * stores for the unquoted one. ACCOUNT, VALUE and YEAR are not reserved, a table can have
    * such a column, which "account" doesn't name. A column created quoted in lower case can't be
    * named by the unquoted name of the sql, the metadata case isn't used.
    */
   @Test
   void keywordColumnIsQuotedInUpperCase() throws Exception {
      for(String key : ORACLE) {
         // was max(a."account")
         assertEquals("select max(a.\"ACCOUNT\") from a", aggregate(key, "select max(a.account) from a"), key);
         assertEquals("select max(a.\"VALUE\") from a", aggregate(key, "select max(a.value) from a"), key);
         assertEquals("select max(a.\"YEAR\") from a", aggregate(key, "select max(a.year) from a"), key);
         assertEquals("select max(a.\"ACCOUNT\") from A a",
                      aggregate(key, "select max(a.account) from a", "ACCOUNT", "ID"), key);
         // was max(a."account"), the lower case metadata
         assertEquals("select max(a.\"ACCOUNT\") from A a",
                      aggregate(key, "select max(a.account) from a", "account", "id"), key);
         // reserved, the sql fails either way. Was max(a."level") and max(t."date")
         assertEquals("select max(a.\"LEVEL\") from a", aggregate(key, "select max(a.level) from a"), key);
         assertEquals("select max(t.\"DATE\") from a t", aggregate(key, "select max(t.date) from a t"), key);
         assertEquals("select max(t.\"DATE\") from A t",
                      aggregate(key, "select max(t.date) from a t", "LEVEL", "DATE", "ID"), key);
      }
   }

   /**
    * The column of a physical table in a subquery is not quoted, the reference to the alias of
    * the subquery keeps its quotes.
    */
   @Test
   void physicalColumnInASubqueryIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         // was max(a."v")
         assertEquals("select max(s.\"x\") from ( select max(a.v) as \"x\" from a) s",
                      aggregate(key, "select max(s.x) from (select max(a.v) x from a) s"), key);
         // was sum(t."amt")
         assertEquals("select max(s.\"total\") from ( select sum(t.amt) as \"total\", t.id from a t group by t.id) s",
                      aggregate(key, "select max(s.total) from (select t.id, sum(t.amt) total from a t group by t.id) s"),
                      key);
      }
   }

   /**
    * The alias of a derived table is quoted by the subquery, the reference to it is quoted the
    * same way, also through select * (the output before this change).
    */
   @Test
   void derivedTableAliasIsUnchanged() throws Exception {
      for(String key : ORACLE) {
         assertEquals("select max(s.\"x\") from ( select a.v as \"x\" from a) s",
                      aggregate(key, "select max(s.x) from (select a.v x from a) s"), key);
         assertEquals("select max(s.\"x\") from ( select a.V as \"x\" from A a) s",
                      aggregate(key, "select max(s.x) from (select a.v x from a) s", UPPER), key);
         assertEquals("select max(s.\"x\") from ( select * from ( select a.v as \"x\" from a) i) s",
                      aggregate(key, "select max(s.x) from (select * from (select a.v x from a) i) s"), key);
         assertEquals("select max(s.\"x\") from ( select i.x from ( select a.V as \"x\" from A a) i) s",
                      aggregate(key, "select max(s.x) from (select * from (select a.v x from a) i) s", UPPER), key);
         assertEquals("select S.ID, max(s.\"x\") from ( select a.id, a.v as \"x\" from a) s group by s.id " +
                      "order by max(s.\"x\") asc",
                      aggregate(key, "select s.id, max(s.x) from (select a.id, a.v as x from a) s group by s.id " +
                                "order by max(s.x)"), key);
         // a keyword alias isn't folded
         assertEquals("select max(s.\"account\") from ( select a.v as \"account\" from a) s",
                      aggregate(key, "select max(s.account) from (select a.v account from a) s"), key);
         assertEquals("select max(s2.\"x\") from ( select s.x as x from ( select a.v as \"x\" from a) s) s2",
                      aggregate(key, "select max(s2.x) from (select s.x x from (select a.v x from a) s) s2"), key);
      }
   }

   /**
    * A quoted column keeps its quotes and its case (#77558, #77578), the output before this change.
    */
   @Test
   void quotedColumnIsUnchanged() throws Exception {
      String[] columns = { "\"v\"", "\"my col\"", "\"My Col\"", "\"account\"" };

      for(String key : ORACLE) {
         for(String column : columns) {
            String query = "select max(a." + column + ") from a";
            assertEquals("select max(a." + column + ") from a", aggregate(key, query), key);

            for(String[] metadata : new String[][] { UPPER, LOWER, TWIN }) {
               assertEquals("select max(a." + column + ") from A a", aggregate(key, query, metadata),
                            key + " " + Arrays.toString(metadata));
            }
         }
      }
   }

   /**
    * The having, group by, a plain column and an unqualified aggregate don't go through
    * getValidAggregate, the output before this change.
    */
   @Test
   void otherClausesAreUnchanged() throws Exception {
      for(String key : ORACLE) {
         assertTrue(aggregate(key, "select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0")
                       .endsWith(" from a t group by t.id having sum(t.v) > 0"), key);
         assertTrue(aggregate(key, "select a.id, sum(a.v) from a group by a.id having sum(a.v) > 0", UPPER)
                       .endsWith(" from A a group by a.ID having sum(a.v) > 0"), key);
         assertTrue(aggregate(key, "select upper(t.v), count(*) from a t group by upper(t.v)")
                       .endsWith(" from a t group by upper(t.v)"), key);
         assertEquals("select max(v) from a", aggregate(key, "select max(v) from a"), key);
         assertEquals("select A.V from a", aggregate(key, "select a.v from a"), key);
         assertEquals("select a.V from A a", aggregate(key, "select a.v from a", UPPER), key);
         // a keyword is quoted as written by the parser, not here
         assertEquals("select A.\"account\" from a", aggregate(key, "select a.account from a"), key);
      }
   }

   /**
    * The column found in the metadata isn't saved, a reloaded sql has the written case again.
    * Was max(a."V") before and max(a."v") after the reload.
    */
   @Test
   void reloadedSqlIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         UniformSQL sql = fixed(key, "select max(a.v) from a", UPPER);
         String generated = regenerate(sql);
         assertEquals("select max(a.v) from A a", regenerate(reload(sql)), key);
         assertEquals("select max(a.V) from A a", generated, key);
      }
   }

   /**
    * Parsed by another helper, generated by Oracle. Was max(a."v"). PostgreSQL and Snowflake
    * store the name quoted, the output before this change.
    */
   @Test
   void otherParserOracleGenerator() throws Exception {
      for(String from : new String[] { "default", "h2" }) {
         UniformSQL sql = parse("select max(a.v) from a", source(from));
         sql.setDataSource(source("oracle"));
         assertEquals(OracleSQLHelper.class, SQLHelper.getSQLHelper(sql).getClass(), from);
         assertEquals("select max(a.v) from a", regenerate(sql), from);
         assertEquals("select max(a.v) from a", regenerate(load(toXML(parse("select max(a.v) from a", source(from))),
                                                                source("oracle"))), from);
      }
   }

   @Test
   void quotingParserOracleGeneratorIsUnchanged() throws Exception {
      for(String from : new String[] { "postgresql", "snowflake" }) {
         UniformSQL sql = parse("select max(a.v) from a", source(from));
         sql.setDataSource(source("oracle"));
         assertEquals("select max(\"a\".\"v\") from \"a\"", regenerate(sql), from);
      }
   }

   /**
    * Surefire pins en_US. The upper case of a keyword column has no dotted capital I.
    */
   @Test
   void turkishLocale() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));
         // was max(a."id"), max(a."admin")
         assertEquals("select max(a.id) from a", aggregate("oracle", "select max(a.id) from a"));
         assertEquals("select max(a.\"ADMIN\") from a", aggregate("oracle", "select max(a.admin) from a"));
         assertEquals("select max(a.\"ACCOUNT\") from a", aggregate("oracle", "select max(a.account) from a"));
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   /**
    * An upper case keyword column with an I is still quoted in a turkish locale, where the
    * default locale lower case of SIZE is sıze, not the keyword size. Always quoted before this
    * change.
    */
   @Test
   void turkishLocaleUpperCaseKeyword() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         Locale.setDefault(Locale.forLanguageTag("tr-TR"));

         for(String key : ORACLE) {
            assertEquals("select sum(T.\"SIZE\") from T", aggregate(key, "select sum(T.SIZE) from T"), key);
            assertEquals("select sum(T.\"FILE\") from T", aggregate(key, "select sum(T.FILE) from T"), key);
            assertEquals("select sum(T.\"INDEX\") from T", aggregate(key, "select sum(T.INDEX) from T"), key);
            assertEquals("select max(a.\"FILE\") from A a",
                         aggregate(key, "select max(a.file) from a", "FILE", "SIZE", "ID"), key);
            assertEquals("select max(a.\"SIZE\") from A a",
                         aggregate(key, "select max(a.SIZE) from a", "FILE", "SIZE", "ID"), key);
            assertEquals("select max(a.\"FILE\") from a", aggregate(key, "select max(a.file) from a"), key);
         }
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   /**
    * An Oracle reserved word missing from the keywords (MODE, START, UID) is still quoted, in
    * upper case, in any locale. Always quoted before this change.
    */
   @Test
   void reservedWordIsQuotedInUpperCase() throws Exception {
      Locale locale = Locale.getDefault();

      try {
         for(String tag : new String[] { "en-US", "tr-TR" }) {
            Locale.setDefault(Locale.forLanguageTag(tag));

            for(String key : ORACLE) {
               String label = key + " " + tag;

               for(String word : new String[] { "MODE", "START", "UID", "INITIAL" }) {
                  assertEquals("select sum(T.\"" + word + "\") from T",
                               aggregate(key, "select sum(T." + word + ") from T"), label);
               }

               // was sum(T."Index"), max(t."mode")
               assertEquals("select sum(T.\"INDEX\") from T", aggregate(key, "select sum(T.Index) from T"), label);
               assertEquals("select max(t.\"MODE\") from a t", aggregate(key, "select max(t.mode) from a t"), label);
            }
         }
      }
      finally {
         Locale.setDefault(locale);
      }
   }

   /**
    * Bug #77821, a name with a non-ascii character is not quoted, as an ascii one. Oracle folds
    * it unquoted too (T.a＿b is valid unquoted on Oracle 23 and stored as A＿B), so the name
    * quoted as written ("a＿b") is another column, ORA-00904. Was quoted as written (#77686).
    */
   @Test
   void nonAsciiColumnIsNotQuoted() throws Exception {
      for(String key : ORACLE) {
         for(String name : PARSED_NON_ASCII) {
            assertEquals("select sum(T." + name + ") from T",
                         aggregate(key, "select sum(T." + name + ") from T"), key + " " + name);
         }

         assertEquals("select max(t.a＿b) from a t", aggregate(key, "select max(t.a＿b) from a t"), key);
         assertEquals("select count(distinct t.データ) from a t",
                      aggregate(key, "select count(distinct t.データ) from a t"), key);
         assertEquals("select T.ID, sum(T.名前) from T group by T.ID order by sum(T.名前) asc",
                      aggregate(key, "select T.ID, sum(T.名前) from T group by T.ID order by sum(T.名前)"), key);
         // an ascii name is still not quoted
         assertEquals("select sum(T.a_b) from T", aggregate(key, "select sum(T.a_b) from T"), key);
      }
   }

   /**
    * Bug #77821, the worksheet push-down path, an aggregate added to the selection without
    * parsing, is not quoted either (was quoted as written, #77686). This pins a known limit: the
    * column there is a catalog name, and one created quoted in lower case or with a character
    * oracle doesn't allow unquoted (a·b, a‿b) is not found, as an ascii "lv" since #77646.
    * Nothing records whether a name came from the catalog, db.caseSensitive=true quotes it.
    */
   @Test
   void nonAsciiColumnIsNotQuotedUnparsed() throws Exception {
      for(String key : ORACLE) {
         for(String name : NON_ASCII) {
            UniformSQL sql = new UniformSQL();
            sql.setDataSource(source(key));
            sql.addTable("T");
            sql.getSelection().addColumn("sum(T." + name + ")");
            assertEquals("select sum(T." + name + ") from T", regenerate(sql), key + " " + name);
         }

         UniformSQL sql = new UniformSQL();
         sql.setDataSource(source(key));
         sql.addTable("T");
         sql.getSelection().addColumn("sum(T.a_b)");
         assertEquals("select sum(T.a_b) from T", regenerate(sql), key);
      }
   }

   private static final String[] NON_ASCII = {
      "データ", "名前", "a＿b", "ａｂ", "a‿b", "a·b", "über", "Größe"
   };
   // names the parser takes unquoted, the names above U+00FF
   private static final String[] PARSED_NON_ASCII = { "データ", "名前", "a＿b", "ａｂ", "a‿b" };

   /**
    * A function of a column in the where clause doesn't go through getValidAggregate, the output
    * before this change.
    */
   @Test
   void whereClauseIsUnchanged() throws Exception {
      for(String key : ORACLE) {
         assertEquals("select A.ID from a where upper(a.v) = 'X'", aggregate(key, "select a.id from a where upper(a.v) = 'X'"), key);
         assertTrue(aggregate(key, "select max(a.v) from a where a.v > 0 and length(a.v) > 1")
                       .endsWith(" from a where a.v > 0 and length(a.v) > 1"), key);
      }
   }

   /**
    * The sql generated by the Oracle helper returns the rows of the sql as written on Derby,
    * which folds an unquoted name to upper case as Oracle does. Was "Column 'v' is either not in
    * any table in the FROM list".
    */
   @Test
   void rowsMatchOnDerby() throws Exception {
      String[] queries = {
         "select max(a.v) from a",
         "select max(t.v) from a t",
         "select count(distinct t.v) from a t",
         "select a.id, sum(a.v) from a group by a.id order by sum(a.v)",
         "select max(a.account) from a",
         "select max(a.id) from a",
         "select max(s.x) from (select max(a.v) x from a) s",
      };

      try(Connection conn = DriverManager.getConnection("jdbc:derby:memory:bug77646;create=true");
          Statement stmt = conn.createStatement())
      {
         stmt.execute("create table a (id int, v int, account int)");
         stmt.execute("insert into a values (1, 10, 100), (1, 20, 200), (2, 30, 300)");

         for(String key : ORACLE) {
            for(String query : queries) {
               List<String> expected = rows(stmt, query);

               for(String[] metadata : new String[][] { {}, { "ID", "V", "ACCOUNT" } }) {
                  String generated = aggregate(key, query, metadata);
                  assertEquals(expected, rows(stmt, generated),
                               key + " " + Arrays.toString(metadata) + ": " + query + " -> " + generated);
               }
            }
         }
      }
      finally {
         try {
            DriverManager.getConnection("jdbc:derby:memory:bug77646;drop=true");
         }
         catch(SQLException ignore) {
            // a successful drop is reported as an exception
         }
      }
   }

   /**
    * Every other helper, the output before this change. Each line is the helpers, the metadata
    * (- for none), the query and the generated sql.
    */
   @Test
   void otherHelpersAreUnchanged() throws Exception {
      int count = 0;

      for(String line : OTHER_HELPERS.strip().split("\n")) {
         String[] parts = line.strip().split(" \\| ", 3);
         String[] metadata = "-".equals(parts[1]) ? new String[0] : parts[1].split(",");
         String query = parts[2].substring(0, parts[2].indexOf(" => "));
         String expected = parts[2].substring(parts[2].indexOf(" => ") + 4);

         for(String key : parts[0].split(",")) {
            assertTrue(helpers().containsKey(key), line);
            assertEquals(expected, aggregate(key, query, metadata), key + " " + parts[1] + ": " + query);
            count++;
         }
      }

      assertEquals(403, count);
   }

   // measured on origin/main 80bfdca75. These pin main, including outputs known to be wrong (the
   // snowflake/exasol/postgresql max("a"."v") from "a", the mysql/access a."`date`"), update the
   // rows when fixing those follow-ups
   private static final String OTHER_HELPERS = """
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select max(a.v) from a => select max(a.v) from a
         postgresql,snowflake,exasol | - | select max(a.v) from a => select max("a"."v") from "a"
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select max(a.v) from a => select max(a.V) from a
         postgresql,snowflake,exasol | V,ID | select max(a.v) from a => select max("a"."v") from "a"
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select max(a.v) from a => select max(a.v) from a
         postgresql,snowflake,exasol | v,id | select max(a.v) from a => select max("a"."v") from "a"
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select max(t.v) from a t => select max(t.v) from a t
         postgresql | - | select max(t.v) from a t => select max(t."v") from "a" t
         snowflake,exasol | - | select max(t.v) from a t => select max(t.v) from "a" t
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select max(t.v) from a t => select max(t.V) from a t
         postgresql | V,ID | select max(t.v) from a t => select max(t."V") from "a" t
         snowflake,exasol | V,ID | select max(t.v) from a t => select max(t.V) from "a" t
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select max(t.v) from a t => select max(t.v) from a t
         postgresql | v,id | select max(t.v) from a t => select max(t."v") from "a" t
         snowflake,exasol | v,id | select max(t.v) from a t => select max(t.v) from "a" t
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select upper(t.v) from a t => select upper(t.v) from a t
         postgresql | - | select upper(t.v) from a t => select upper(t."v") from "a" t
         snowflake,exasol | - | select upper(t.v) from a t => select upper(t.v) from "a" t
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select upper(t.v) from a t => select upper(t.V) from a t
         postgresql | V,ID | select upper(t.v) from a t => select upper(t."V") from "a" t
         snowflake,exasol | V,ID | select upper(t.v) from a t => select upper(t.V) from "a" t
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select upper(t.v) from a t => select upper(t.v) from a t
         postgresql | v,id | select upper(t.v) from a t => select upper(t."v") from "a" t
         snowflake,exasol | v,id | select upper(t.v) from a t => select upper(t.v) from "a" t
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select count(distinct t.v) from a t => select count(distinct t.v) from a t
         postgresql | - | select count(distinct t.v) from a t => select count(distinct t."v") from "a" t
         snowflake,exasol | - | select count(distinct t.v) from a t => select count(distinct t.v) from "a" t
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select count(distinct t.v) from a t => select count(distinct t.V) from a t
         postgresql | V,ID | select count(distinct t.v) from a t => select count(distinct t."V") from "a" t
         snowflake,exasol | V,ID | select count(distinct t.v) from a t => select count(distinct t.V) from "a" t
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select count(distinct t.v) from a t => select count(distinct t.v) from a t
         postgresql | v,id | select count(distinct t.v) from a t => select count(distinct t."v") from "a" t
         snowflake,exasol | v,id | select count(distinct t.v) from a t => select count(distinct t.v) from "a" t
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select sum(t.v), t.id from a t group by t.id order by sum(t.v) asc
         postgresql | - | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select "t"."id", sum(t."v") from "a" t group by "t"."id" order by sum(t."v") asc
         snowflake,exasol | - | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select "t"."id", sum(t.v) from "a" t group by "t"."id" order by sum(t.v) asc
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select sum(t.V), t.ID from a t group by t.ID order by sum(t.V) asc
         postgresql | V,ID | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select "t"."id", sum(t."V") from "a" t group by "t"."id" order by sum(t."V") asc
         snowflake,exasol | V,ID | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select "t"."id", sum(t.V) from "a" t group by "t"."id" order by sum(t.V) asc
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select sum(t.v), t.id from a t group by t.id order by sum(t.v) asc
         postgresql | v,id | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select "t"."id", sum(t."v") from "a" t group by "t"."id" order by sum(t."v") asc
         snowflake,exasol | v,id | select t.id, sum(t.v) from a t group by t.id order by sum(t.v) => select "t"."id", sum(t.v) from "a" t group by "t"."id" order by sum(t.v) asc
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select sum(t.v), t.id from a t group by t.id having sum(t.v) > 0
         postgresql | - | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select "t"."id", sum(t."v") from "a" t group by "t"."id" having sum("t"."v") > 0
         snowflake,exasol | - | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select "t"."id", sum(t.v) from "a" t group by "t"."id" having sum("t"."v") > 0
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select sum(t.V), t.ID from a t group by t.ID having sum(t.v) > 0
         postgresql | V,ID | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select "t"."id", sum(t."V") from "a" t group by "t"."id" having sum("t"."v") > 0
         snowflake,exasol | V,ID | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select "t"."id", sum(t.V) from "a" t group by "t"."id" having sum("t"."v") > 0
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select sum(t.v), t.id from a t group by t.id having sum(t.v) > 0
         postgresql | v,id | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select "t"."id", sum(t."v") from "a" t group by "t"."id" having sum("t"."v") > 0
         snowflake,exasol | v,id | select t.id, sum(t.v) from a t group by t.id having sum(t.v) > 0 => select "t"."id", sum(t.v) from "a" t group by "t"."id" having sum("t"."v") > 0
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select max(s.x) from (select a.v x from a) s => select max(s.x) from ( select a.v as x from a) s
         postgresql | - | select max(s.x) from (select a.v x from a) s => select max(s."x") from ( select "a"."v" as "x" from "a") s
         snowflake,exasol | - | select max(s.x) from (select a.v x from a) s => select max(s.x) from ( select "a"."v" as x from "a") s
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select max(s.x) from (select a.v x from a) s => select max(s.x) from ( select a.V as x from a) s
         postgresql | V,ID | select max(s.x) from (select a.v x from a) s => select max(s."x") from ( select "a"."V" as "x" from "a") s
         snowflake,exasol | V,ID | select max(s.x) from (select a.v x from a) s => select max(s.x) from ( select "a".V as x from "a") s
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select max(s.x) from (select a.v x from a) s => select max(s.x) from ( select a.v as x from a) s
         postgresql | v,id | select max(s.x) from (select a.v x from a) s => select max(s."x") from ( select "a"."v" as "x" from "a") s
         snowflake,exasol | v,id | select max(s.x) from (select a.v x from a) s => select max(s.x) from ( select "a".v as x from "a") s
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s.x) from ( select * from ( select a.v as x from a) i) s
         postgresql | - | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s."x") from ( select * from ( select "a"."v" as "x" from "a") i) s
         snowflake,exasol | - | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s.x) from ( select * from ( select "a"."v" as x from "a") i) s
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s.x) from ( select i.x from ( select a.V as x from a) i) s
         postgresql | V,ID | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s."x") from ( select i."x" from ( select "a"."V" as "x" from "a") i) s
         snowflake,exasol | V,ID | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s.x) from ( select i.x from ( select "a".V as x from "a") i) s
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s.x) from ( select i.x from ( select a.v as x from a) i) s
         postgresql | v,id | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s."x") from ( select i."x" from ( select "a"."v" as "x" from "a") i) s
         snowflake,exasol | v,id | select max(s.x) from (select * from (select a.v x from a) i) s => select max(s.x) from ( select i.x from ( select "a".v as x from "a") i) s
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1 => select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1
         postgresql,snowflake,exasol | - | select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1 => select count("SA"."ORDERS1"."CUSTOMER_ID") from "SA"."ORDERS1"
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1 => select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1
         postgresql,snowflake,exasol | V,ID | select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1 => select count("SA"."ORDERS1"."CUSTOMER_ID") from "SA"."ORDERS1"
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1 => select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1
         postgresql,snowflake,exasol | v,id | select count(SA.ORDERS1.CUSTOMER_ID) from SA.ORDERS1 => select count("SA"."ORDERS1"."CUSTOMER_ID") from "SA"."ORDERS1"
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select max(a.account) from a => select max(a.account) from a
         postgresql,snowflake,exasol | - | select max(a.account) from a => select max("a"."account") from "a"
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select max(a.account) from a => select max(a.account) from a
         postgresql,snowflake,exasol | V,ID | select max(a.account) from a => select max("a"."account") from "a"
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select max(a.account) from a => select max(a.account) from a
         postgresql,snowflake,exasol | v,id | select max(a.account) from a => select max("a"."account") from "a"
         default,h2,h2-ansi,db2,sql server | - | select max(a.date) from a => select max(a."date") from a
         postgresql,snowflake,exasol | - | select max(a.date) from a => select max("a"."date") from "a"
         informix | - | select max(a.date) from a => select max(a.date) from a
         access,mysql | - | select max(a.date) from a => select max(a."`date`") from a
         h2,h2-ansi,db2,sql server | V,ID | select max(a.date) from a => select max(a."date") from a
         postgresql,snowflake,exasol | V,ID | select max(a.date) from a => select max("a"."date") from "a"
         informix | V,ID | select max(a.date) from a => select max(a.date) from a
         access,mysql | V,ID | select max(a.date) from a => select max(a."`date`") from a
         h2,h2-ansi,db2,sql server | v,id | select max(a.date) from a => select max(a."date") from a
         postgresql,snowflake,exasol | v,id | select max(a.date) from a => select max("a"."date") from "a"
         informix | v,id | select max(a.date) from a => select max(a.date) from a
         access,mysql | v,id | select max(a.date) from a => select max(a."`date`") from a
         default,h2,h2-ansi,informix,db2,sql server | - | select max(a."v") from a => select max(a."v") from a
         postgresql,snowflake,exasol | - | select max(a."v") from a => select max("a"."v") from "a"
         access,mysql | - | select max(a."v") from a => select max(a.`v`) from a
         h2,h2-ansi,informix,db2,sql server | V,ID | select max(a."v") from a => select max(a."v") from a
         postgresql,snowflake,exasol | V,ID | select max(a."v") from a => select max("a"."v") from "a"
         access,mysql | V,ID | select max(a."v") from a => select max(a.`v`) from a
         h2,h2-ansi,informix,db2,sql server | v,id | select max(a."v") from a => select max(a."v") from a
         postgresql,snowflake,exasol | v,id | select max(a."v") from a => select max("a"."v") from "a"
         access,mysql | v,id | select max(a."v") from a => select max(a.`v`) from a
         default,h2,h2-ansi,informix,access,db2,sql server,mysql | - | select max(s.v) from (select a.v from a) s => select max(s.v) from ( select a.v from a) s
         postgresql | - | select max(s.v) from (select a.v from a) s => select max(s."v") from ( select "a"."v" from "a") s
         snowflake,exasol | - | select max(s.v) from (select a.v from a) s => select max(s.v) from ( select "a"."v" from "a") s
         h2,h2-ansi,informix,access,db2,sql server,mysql | V,ID | select max(s.v) from (select a.v from a) s => select max(s.V) from ( select a.V from a) s
         postgresql | V,ID | select max(s.v) from (select a.v from a) s => select max(s."V") from ( select "a"."V" from "a") s
         snowflake,exasol | V,ID | select max(s.v) from (select a.v from a) s => select max(s.V) from ( select "a".V from "a") s
         h2,h2-ansi,informix,access,db2,sql server,mysql | v,id | select max(s.v) from (select a.v from a) s => select max(s.v) from ( select a.v from a) s
         postgresql | v,id | select max(s.v) from (select a.v from a) s => select max(s."v") from ( select "a"."v" from "a") s
         snowflake,exasol | v,id | select max(s.v) from (select a.v from a) s => select max(s.v) from ( select "a".v from "a") s
      """;

   // the rows, with the values of each row sorted
   private static List<String> rows(Statement stmt, String query) throws SQLException {
      List<String> rows = new ArrayList<>();

      try(ResultSet rs = executeQuery(stmt, query)) {
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

      return rows;
   }

   private static ResultSet executeQuery(Statement stmt, String query) throws SQLException {
      try {
         return stmt.executeQuery(query);
      }
      catch(SQLException ex) {
         throw new SQLException(query, ex);
      }
   }

   // ---- harness (the same as UniformSQLQuotedAggregateTest, #77578) ----

   // the sql regenerated after the metadata step with the columns of every table, or after
   // the parse if there are no columns
   private static String aggregate(String key, String query, String... columns) throws Exception {
      return regenerate(fixed(key, query, columns));
   }

   private static UniformSQL fixed(String key, String query, String... columns) throws Exception {
      JDBCDataSource ds = source(key);
      UniformSQL sql = parse(query, ds);

      if(columns.length > 0 && ds != null) {
         JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
      }

      return sql;
   }

   // the table metadata is cached by data source name, also in the sree home of earlier test
   // runs, use a new name each time
   private static JDBCDataSource source(String key) {
      JDBCDataSource ds = helpers().get(key);

      if(ds == null) {
         return null;
      }

      ds = (JDBCDataSource) ds.clone();
      ds.setName(ds.getName() + "Aggregate" + RUN + "_" + (++sources));
      return ds;
   }

   private static int sources;
   private static final String RUN = Long.toString(System.nanoTime(), 36);

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("default", null);
      helpers.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", false));
      helpers.put("h2-ansi", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2", true));
      helpers.put("oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x", "oracle",
                                       false));
      helpers.put("oracle-ansi", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x",
                                            "oracle", true));
      helpers.put("postgresql", dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql",
                                           false));
      helpers.put("snowflake", dataSource("net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://x",
                                          "snowflake", true));
      helpers.put("exasol", dataSource("com.exasol.jdbc.EXADriver", "jdbc:exa:x", "exasol", false));
      helpers.put("informix", dataSource("com.informix.jdbc.IfxDriver", "jdbc:informix-sqli://x:1/db", "informix",
                                         false));
      helpers.put("access", dataSource("net.ucanaccess.jdbc.UcanaccessDriver", "jdbc:ucanaccess://x", "access",
                                       false));
      helpers.put("db2", dataSource("com.ibm.db2.jcc.DB2Driver", "jdbc:db2://x:1/db", "db2", false));
      helpers.put("sql server", dataSource("com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver://x",
                                           "sql server", false));
      helpers.put("mysql", dataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://x/db", "mysql", false));
      return helpers;
   }

   // the column metadata comes from the repository. SQLTypes.getQualifiedName logs an error
   // that it can't get the root meta-data from XRepository.getRepository(), then goes on
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
      ds.setName("ds77646" + product.replace(' ', '_') + ansiJoin);
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

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return normalize(sql.getSQLString());
   }

   // the generated sql is pretty-printed
   private static String normalize(String sql) {
      return sql.replaceAll("\\s+", " ").trim();
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
