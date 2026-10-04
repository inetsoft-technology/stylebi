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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77643. On PostgreSQL, Snowflake and Exasol, which fold an unquoted name to one case,
 * the metadata step (JDBCUtil.getFullPathOf) renamed an unquoted column to the first column
 * of the table that matched it ignoring case. On a table with two columns that differ only
 * in case ("MixedCase" and mixedcase) the column then depended on the order of the metadata,
 * also for a name written in the folded case (t.mixedcase). The stored name is now matched in
 * its exact case first, and a name written unquoted in the sql parsed last in the case the
 * database folds it to.
 *
 * The expected sql was checked against PostgreSQL 16 rows on t(id, "MixedCase", mixedcase).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, UniformSQLUnquotedTwin77643Test.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLUnquotedTwin77643Test {
   /**
    * A qualified name written in the folded case is that column in both metadata orders.
    */
   @Test
   void foldedNameIsItsColumnInBothOrders() throws Exception {
      for(String[] columns : new String[][] { PG_TWIN_SECOND, PG_TWIN_FIRST }) {
         String label = columns[1];
         assertEquals("select \"t\".\"mixedcase\" from \"t\"",
                      fixed("postgresql", "select t.mixedcase from t", columns), label);
         assertEquals("select \"t\".\"id\", \"t\".\"mixedcase\" from \"t\" order by \"t\".\"mixedcase\" desc",
                      fixed("postgresql", "select t.id, t.mixedcase from t order by t.mixedcase desc",
                            columns), label);
         assertEquals("select \"t\".\"mixedcase\", count(*) from \"t\" group by \"t\".\"mixedcase\"",
                      fixed("postgresql", "select t.mixedcase, count(*) from t group by t.mixedcase",
                            columns), label);
         // the quoted twin keeps its case
         assertEquals("select \"t\".\"MixedCase\" from \"t\"",
                      fixed("postgresql", "select t.\"MixedCase\" from t", columns), label);
      }
   }

   /**
    * The twins written in one query, the unquoted one in another case than the folded one.
    */
   @Test
   void qualifiedTwinsInBothOrders() throws Exception {
      for(String[] columns : new String[][] { PG_TWIN_SECOND, PG_TWIN_FIRST }) {
         String label = columns[1];
         assertEquals("select \"t\".\"MixedCase\", \"t\".\"mixedcase\" as \"b\" from \"t\"",
                      fixed("postgresql", "select t.\"MixedCase\", t.MixedCase as b from t", columns),
                      label);
         assertEquals("select \"t\".\"MixedCase\", \"t\".\"mixedcase\" as \"b\" from \"t\" " +
                      "order by \"t\".\"mixedcase\" desc",
                      fixed("postgresql", "select t.\"MixedCase\", t.MixedCase as b from t " +
                            "order by t.MixedCase desc", columns), label);
         assertEquals("select \"id\", \"t\".\"mixedcase\" from \"t\" order by \"t\".\"mixedcase\" desc",
                      fixed("postgresql", "select id, t.MixedCase from t order by t.MixedCase desc",
                            columns), label);
      }
   }

   /**
    * The twins grouped by in one query. The group by named the first twin of the metadata
    * for both of them.
    */
   @Test
   void qualifiedTwinsInGroupByInBothOrders() throws Exception {
      for(String[] columns : new String[][] { PG_TWIN_SECOND, PG_TWIN_FIRST }) {
         assertEquals("select \"t\".\"MixedCase\", \"t\".\"mixedcase\" as \"b\", count(*) as \"n\" from \"t\" " +
                      "group by \"t\".\"MixedCase\", \"t\".\"mixedcase\"",
                      fixed("postgresql", "select t.\"MixedCase\", t.MixedCase as b, count(*) as n from t " +
                            "group by t.\"MixedCase\", t.MixedCase", columns), columns[1]);
      }

      for(String helper : new String[] { "snowflake", "exasol" }) {
         for(String[] columns : new String[][] { TWIN_SECOND, TWIN_FIRST }) {
            String generated = fixed(helper, "select t.\"MixedCase\", t.MixedCase as b, count(*) as n from t " +
                                     "group by t.\"MixedCase\", t.MixedCase", columns);
            assertTrue(generated.endsWith(" group by \"t\".\"MixedCase\", \"t\".MIXEDCASE"),
                       helper + " " + columns[1] + ": " + generated);
         }
      }
   }

   /**
    * Snowflake and exasol fold to upper case. The order by of a select column is generated
    * quoted, so it named the first twin in the metadata.
    */
   @Test
   void snowflakeAndExasolOrderByTheFoldedTwin() throws Exception {
      for(String helper : new String[] { "snowflake", "exasol" }) {
         for(String[] columns : new String[][] { TWIN_SECOND, TWIN_FIRST }) {
            String label = helper + " " + columns[1];
            String generated = fixed(helper, "select t.id, t.MIXEDCASE from t order by t.MIXEDCASE desc",
                                     columns);
            assertTrue(generated.endsWith(" order by \"t\".\"MIXEDCASE\" desc"), label + ": " + generated);

            generated = fixed(helper, "select t.id, t.MixedCase from t order by t.MixedCase desc", columns);
            assertTrue(generated.endsWith(" order by \"t\".\"MIXEDCASE\" desc"), label + ": " + generated);
         }
      }
   }

   /**
    * A name with quotes in the case of its column (as the query editor writes the default
    * text of an expression) wasn't written unquoted in the sql parsed last, so it's that
    * column, not the one of the folded case.
    */
   @Test
   void editorBuiltQuotedNameIsTheColumnOfItsCase() throws Exception {
      for(String[] columns : new String[][] { PG_TWIN_SECOND, PG_TWIN_FIRST }) {
         UniformSQL sql = parse("select t.id from t", helpers("postgresql"));
         fix(sql, columns);
         sql.getSelection().addColumn("\"t\".\"MixedCase\"");
         fix(sql, columns);
         // the select columns may be reordered
         String generated = regenerate(sql);
         assertTrue(generated.contains("\"t\".\"MixedCase\""), columns[1] + ": " + generated);
         assertFalse(generated.contains("mixedcase"), columns[1] + ": " + generated);
      }
   }

   /**
    * The metadata step also runs on a loaded query, which doesn't keep which names were
    * written unquoted. A name is the column of its stored case, whatever the order of the
    * metadata. A query saved before this change kept an unquoted name in its written case.
    */
   @Test
   void savedQueryIsTheColumnOfItsStoredCase() throws Exception {
      String xml = toXML(parse("select t.MixedCase from t", helpers("postgresql")));
      assertEquals("\"t\".\"MixedCase\"",
                   load(xml, helpers("postgresql")).getSelection().getColumn(0));

      for(String[] columns : new String[][] { PG_TWIN_SECOND, PG_TWIN_FIRST }) {
         UniformSQL sql = load(xml, helpers("postgresql"));
         assertEquals("select \"t\".\"MixedCase\" from \"t\"", fixed(sql, columns), columns[1]);
      }

      // written in the folded case
      xml = toXML(parse("select t.mixedcase from t", helpers("postgresql")));

      for(String[] columns : new String[][] { PG_TWIN_SECOND, PG_TWIN_FIRST }) {
         UniformSQL sql = load(xml, helpers("postgresql"));
         assertEquals("select \"t\".\"mixedcase\" from \"t\"", fixed(sql, columns), columns[1]);
      }
   }

   /**
    * The record of the names written unquoted is for the metadata step that follows the
    * parse. A clone has its own copy, and the metadata step empties it, also when it fails.
    */
   @Test
   void recordIsCopiedByACloneAndEmptiedByTheMetadataStep() throws Exception {
      UniformSQL sql = parse("select t.MixedCase from t", helpers("postgresql"));
      String segment = ((JDBCSelection) sql.getSelection()).getColumn(0);
      segment = segment.substring(segment.indexOf('.') + 1);
      assertTrue(sql.isParsedUnquotedSegment(segment), segment);

      UniformSQL clone = sql.clone();
      fix(clone, PG_TWIN_SECOND);
      assertFalse(clone.isParsedUnquotedSegment(segment));
      assertTrue(sql.isParsedUnquotedSegment(segment));

      XRepository failing = mock(XRepository.class);
      when(failing.getMetaData(any(), any(), any(), anyBoolean(), any()))
         .thenThrow(new RuntimeException("no connection"));
      assertThrows(RuntimeException.class,
                   () -> JDBCUtil.fixUniformSQLInfo(sql, failing, null, sql.getDataSource()));
      assertFalse(sql.isParsedUnquotedSegment(segment));

      // a new parse starts a new record, the xml doesn't keep it
      UniformSQL reparsed = parse("select t.MixedCase from t", helpers("postgresql"));
      reparsed.parse("select t.id from t", UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertFalse(reparsed.isParsedUnquotedSegment(segment));
      assertFalse(load(toXML(parse("select t.MixedCase from t", helpers("postgresql"))),
                       helpers("postgresql")).isParsedUnquotedSegment(segment));
   }

   /**
    * Only postgresql (lower case), snowflake and exasol (upper case) are known to fold an
    * unquoted name, a helper made case-sensitive by db.caseSensitive keeps the rule before.
    */
   @Test
   void identifierCaseOfTheHelpers() throws Exception {
      assertEquals(SQLHelper.IdentifierCase.LOWER, helper("postgresql").getIdentifierCase());
      assertEquals(SQLHelper.IdentifierCase.UPPER, helper("snowflake").getIdentifierCase());
      assertEquals(SQLHelper.IdentifierCase.UPPER, helper("exasol").getIdentifierCase());

      withProperty("db.caseSensitive", "true", () -> {
         assertTrue(helper("h2").isCaseSensitive());
         assertEquals(SQLHelper.IdentifierCase.UNKNOWN, helper("h2").getIdentifierCase());
         assertEquals(SQLHelper.IdentifierCase.UNKNOWN, new SQLHelper().getIdentifierCase());
         // the first column ignoring case, as before
         assertEquals("select \"t\".MixedCase from \"t\"",
                      fixed("h2", "select t.mixedcase from t", PG_TWIN_SECOND));
         assertEquals("select \"t\".mixedcase from \"t\"",
                      fixed("h2", "select t.mixedcase from t", PG_TWIN_FIRST));
         return null;
      });
   }

   /**
    * Only a plain ascii name is folded, postgresql folds ascii letters only, and the fold
    * doesn't use the default locale.
    */
   @Test
   void foldIsAsciiOnly() {
      assertEquals("MIXEDCASE", SQLHelper.IdentifierCase.UPPER.fold("MixedCase"));
      assertEquals("mixedcase", SQLHelper.IdentifierCase.LOWER.fold("MixedCase"));
      assertEquals("Ät", SQLHelper.IdentifierCase.LOWER.fold("Ät"));
      assertEquals("My A", SQLHelper.IdentifierCase.LOWER.fold("My A"));
      assertEquals("MixedCase", SQLHelper.IdentifierCase.UNKNOWN.fold("MixedCase"));

      java.util.Locale old = java.util.Locale.getDefault();
      java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));

      try {
         assertEquals("ID", SQLHelper.IdentifierCase.UPPER.fold("id"));
         assertEquals("id", SQLHelper.IdentifierCase.LOWER.fold("ID"));
      }
      finally {
         java.util.Locale.setDefault(old);
      }
   }

   private static SQLHelper helper(String key) {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(helpers(key));
      return SQLHelper.getSQLHelper(sql);
   }

   // the regenerated sql after the metadata step, with the columns of every table
   static String fixed(String helper, String query, String... columns) throws Exception {
      return fixed(parse(query, helpers(helper)), columns);
   }

   static String fixed(UniformSQL sql, String... columns) throws Exception {
      fix(sql, columns);
      return regenerate(sql);
   }

   static void fix(UniformSQL sql, String... columns) throws Exception {
      // the table metadata is cached by data source, use another one
      JDBCDataSource ds = (JDBCDataSource) sql.getDataSource().clone();
      ds.setName(ds.getName() + "_" + (++sources));
      sql.setDataSource(ds);
      JDBCUtil.fixUniformSQLInfo(sql, repository(columns), null, ds);
   }

   static JDBCDataSource helpers(String helper) {
      switch(helper) {
      case "postgresql":
         return dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db", "postgresql");
      case "snowflake":
         return dataSource("net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://x", "snowflake");
      case "exasol":
         return dataSource("com.exasol.jdbc.EXADriver", "jdbc:exa:x", "exasol");
      default:
         return dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2");
      }
   }

   // the column order of PostgreSQL getColumns: ordinal, in the stored case
   static final String[] PG_TWIN_SECOND = { "id", "MixedCase", "mixedcase" };
   static final String[] PG_TWIN_FIRST = { "id", "mixedcase", "MixedCase" };
   static final String[] TWIN_SECOND = { "id", "MixedCase", "MIXEDCASE" };
   static final String[] TWIN_FIRST = { "id", "MIXEDCASE", "MixedCase" };

   static XRepository repository(String[] columns) throws Exception {
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

   interface Action<T> {
      T run() throws Exception;
   }

   static <T> T withProperty(String name, String value, Action<T> action) throws Exception {
      String old = SreeEnv.getProperty(name);
      SreeEnv.setProperty(name, value);

      try {
         return action.run();
      }
      finally {
         if(old == null) {
            SreeEnv.remove(name);
         }
         else {
            SreeEnv.setProperty(name, old);
         }
      }
   }

   // a JDBCDataSource creates its credential when it is constructed, and
   // JDBCUtil.fixUniformSQLInfo looks up the driver type
   @Configuration
   static class Beans {
      @Bean
      Config config() {
         Config config = mock(Config.class);
         when(config.getJDBCType(any())).thenReturn("H2");
         return config;
      }

      @Bean
      CredentialService credentialService() {
         CredentialService service = mock(CredentialService.class);
         when(service.createCredential(any(), anyBoolean())).thenAnswer(inv -> new LocalPasswordCredential());
         return service;
      }
   }

   static JDBCDataSource dataSource(String driver, String url, String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77643" + product + RUN);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      ds.setProductVersion("10.0");
      return ds;
   }

   static UniformSQL load(String xml, JDBCDataSource ds) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      loaded.setDataSource(ds);
      return loaded;
   }

   static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return sql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   static UniformSQL parse(String text, JDBCDataSource ds) throws Exception {
      UniformSQL sql = new UniformSQL();
      sql.setDataSource(ds);
      sql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, sql.getParseResult(), text);
      assertFalse(sql.isLossy(), text);
      return sql;
   }

   private static final long RUN = System.nanoTime();
   private static int sources;
}
