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

import inetsoft.report.TableLens;
import inetsoft.report.XSessionManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.Drivers;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Plugins;
import inetsoft.util.Tool;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77569. The parser stored a quoted table, schema or qualifier segment ("a", `a`, [a])
 * without its quotes, so the regenerated sql read another table on a database that folds
 * unquoted names (select * from "a" became select * from a, which reads "A" on Derby). The
 * quoted segments are now recorded on the {@link SelectTable} and quoted again when the sql
 * is generated.
 *
 * The plain execution path (normalizer, regeneration) runs through the real
 * {@link XSessionManager#getXNodeTableLens} and {@link JDBCHandler#execute} on embedded Derby,
 * and the rows are compared with the original sql run directly on Derby.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  UniformSQLQuotedTableNameTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLQuotedTableNameTest {
   private static final String DB = "memory:bug77569";

   @Configuration
   static class JdbcConfig {
      // JDBCDataSource's constructor needs CredentialService, whose constructor is
      // package-private.
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }

      @Bean
      public Plugins plugins(BlobStorageManager blobStorageManager, Cluster cluster,
                             ApplicationEventPublisher eventPublisher)
      {
         return new Plugins(blobStorageManager.getStorage("plugins", true), cluster,
                            eventPublisher);
      }

      // the pool returns a Derby data source directly
      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = recording(derby());
         ConnectionPoolFactory factory = mock(ConnectionPoolFactory.class);
         when(factory.getConnectionPool(any(), any())).thenReturn(ds);
         return factory;
      }

      @Bean
      public Drivers drivers(Plugins plugins, ConnectionPoolFactory connectionPoolFactory) {
         return new Drivers(plugins, connectionPoolFactory);
      }

      @Bean
      public Config config(Plugins plugins) {
         return new Config(plugins);
      }

      // DerbyHelper asks the repository for the product version
      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @AfterEach
   void tearDownSessions() {
      for(XSessionManager session : sessions) {
         session.tearDown();
      }

      sessions.clear();
   }

   @BeforeEach
   void createTables() throws Exception {
      if(created) {
         return;
      }

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         // tables whose names differ only in case, so a dropped quote reads another table
         stmt.executeUpdate("create table \"A\" (ID INT, V INT, \"MixedCase\" INT)");
         stmt.executeUpdate("create table \"a\" (ID INT, V INT, \"MixedCase\" INT)");
         stmt.executeUpdate("create table \"b\" (ID INT, W INT)");
         stmt.executeUpdate("create table \"c\" (ID INT)");
         stmt.executeUpdate("create table C (ID INT, V INT)");
         stmt.executeUpdate("create schema \"S\"");
         stmt.executeUpdate("create table \"S\".\"a\" (ID INT, V INT)");
         stmt.executeUpdate("create table \"S\".\"A\" (ID INT, V INT)");
         stmt.executeUpdate("insert into \"A\" values (1, 100, 1000), (2, 200, 2000)");
         stmt.executeUpdate("insert into \"a\" values (1, 1, 10)");
         stmt.executeUpdate("insert into \"b\" values (1, 7), (3, 9)");
         stmt.executeUpdate("insert into \"c\" values (1), (3)");
         stmt.executeUpdate("insert into C values (1, 5), (2, 6)");
         stmt.executeUpdate("insert into \"S\".\"a\" values (1, 42)");
         stmt.executeUpdate("insert into \"S\".\"A\" values (1, 43), (2, 44)");
      }

      created = true;
   }

   // plain execution regenerates the sql of a parsed query, which must read the same tables
   @Test
   void plainExecutionReadsTheQuotedTable() throws Exception {
      // the select lists are explicit, the plain path can't expand * without the metadata
      String[] queries = {
         "select \"a\".V from \"a\"",
         "select x.V from \"a\" x",
         "select \"a\".ID from \"a\"",
         "select \"a\".V, \"a\".ID from \"a\"",
         "select \"a\".V from \"a\" where \"a\".ID = 1",
         "select x.ID, y.V from \"A\" x left join \"a\" y on x.ID = y.ID",
         "select \"b\".W, \"a\".V from \"b\" left join \"a\" on \"b\".ID = \"a\".ID",
         "select \"b\".ID, \"a\".V from \"b\", \"a\" where \"b\".ID = \"a\".ID",
         // both tables of one name in one from
         "select \"a\".V, \"A\".V from \"a\", \"A\" where \"a\".ID = \"A\".ID",
         "select \"A\".V, \"a\".V from \"A\" left join \"a\" on \"A\".ID = \"a\".ID",
         // a subquery
         "select \"A\".V from \"A\" where \"A\".ID in (select \"a\".ID from \"a\")",
         "select \"A\".V from \"A\" where exists (select 1 from \"a\" where \"a\".ID = \"A\".ID)",
         // group by, order by, having and an aggregate qualifier
         "select \"a\".ID, count(*) from \"a\" group by \"a\".ID having count(*) > 0 " +
            "order by \"a\".ID",
         "select sum(\"a\".\"MixedCase\") from \"a\"",
         "select max(\"a\".V), \"a\".ID from \"a\" group by \"a\".ID",
         // quoted schemas
         "select y.V from \"S\".\"a\" y",
         "select \"S\".\"a\".V from \"S\".\"a\"",
         "select x.V from S.\"a\" x",
         // a correlated column of a quoted outer table, the subquery has the table in
         // another case
         "select \"c\".ID from \"c\" where exists (select 1 from C where C.ID = \"c\".ID)",
         "select \"c\".ID from \"c\" where not exists (select 1 from C where C.ID = \"c\".ID)",
         "select \"c\".ID from \"c\" where \"c\".ID in (select C.ID from C where C.ID = \"c\".ID)",
         // a correlated scalar subquery in the select list, in an expression, in a case and
         // in having
         "select \"c\".ID, (select max(C.V) from C where C.ID = \"c\".ID) from \"c\"",
         "select \"c\".ID, coalesce((select max(C.V) from C where C.ID = \"c\".ID), 0) from \"c\"",
         "select \"c\".ID, (select count(*) from C where C.ID = \"c\".ID) + 1 from \"c\"",
         "select \"c\".ID, case when (select count(*) from C where C.ID = \"c\".ID) > 0 " +
            "then 1 else 0 end from \"c\"",
         "select \"c\".ID from \"c\" group by \"c\".ID " +
            "having (select count(*) from C where C.ID = \"c\".ID) > 0",
         "select \"c\".ID from \"c\" where \"c\".ID = " +
            "(select max(C.ID) from C where C.ID = \"c\".ID)",
         "select C.ID, (select max(a.V) from a where a.ID = C.ID) from C",
         // quoted qualifiers in expressions
         "select \"a\".ID, max(\"a\".V) from \"a\" group by \"a\".ID having max(\"a\".V) > 0",
         "select \"a\".ID, count(*) from \"a\" where \"a\".V > 0 group by \"a\".ID " +
            "having max(\"a\".V) > 0 order by \"a\".ID",
         "select case when \"a\".V > 0 then \"a\".ID else 0 end from \"a\"",
         "select \"a\".ID + 1 from \"a\"",
         "select coalesce(\"a\".V, 0) from \"a\"",
         "select \"a\".ID from \"a\" where \"a\".V + 1 > 1",
         "select (\"a\".V + 1) * 2 from \"a\"",
         "select \"S\".\"a\".V * 2 from \"S\".\"a\"",
         // control: an unquoted name reads the folded table, as before
         "select a.V from a",
         "select C.ID from C where exists (select 1 from a where a.ID = C.ID)",
         "select x.V from a x",
      };

      for(boolean ansi : new boolean[] { false, true }) {
         for(String query : queries) {
            Run run;

            try {
               run = run(newSession(), parsed(query), ansi, 0);
            }
            catch(Exception ex) {
               throw new AssertionError(query + " (ansi " + ansi + ")", ex);
            }

            String label = query + " (ansi " + ansi + ") ran " + run.executedSql;
            assertEquals(direct(query), rows(run.table), label);
         }
      }

      // the select list is sorted, so the sql that ran was regenerated
      Run run = run(newSession(), parsed("select \"a\".V, \"a\".ID from \"a\""), false, 0);
      assertEquals("select \"a\".ID, \"a\".V from \"a\"", norm(run.executedSql));
   }

   @Test
   void plainExecutionWithMaxRows() throws Exception {
      for(String query : new String[] { "select \"a\".V from \"a\"", "select x.V from \"a\" x",
                                        "select y.V from \"S\".\"a\" y" })
      {
         Run run = run(newSession(), parsed(query), false, 1);
         assertEquals(direct(query), rows(run.table), query + " ran " + run.executedSql);
      }
   }

   // a saved query, and a query whose sql was regenerated, read the same tables
   @Test
   void savedQueryReadsTheQuotedTable() throws Exception {
      String query = "select \"a\".V, \"A\".V from \"a\", \"A\" where \"a\".ID = \"A\".ID";
      UniformSQL loaded = load(toXML(parsed(query)), null);
      Run run = run(newSession(), loaded, false, 0);
      assertEquals(direct(query), rows(run.table), run.executedSql);

      UniformSQL sql = parsed("select \"S\".\"a\".V from \"S\".\"a\" where \"S\".\"a\".ID = 1");
      sql.clearSQLString();
      String regenerated = sql.getSQLString();
      assertEquals(direct("select \"S\".\"a\".V from \"S\".\"a\" where \"S\".\"a\".ID = 1"),
                   rows(run(newSession(), parsed(regenerated), false, 0).table), regenerated);
   }

   @Test
   void quotedNamesRegenerateQuoted() throws Exception {
      String[][] cases = {
         { "select * from \"a\"", "select * from \"a\"" },
         { "select * from \"a\" x", "select * from \"a\" x" },
         { "select \"a\".id from \"a\"", "select \"a\".id from \"a\"" },
         { "select * from \"a\" where \"a\".id = 1", "select * from \"a\" where \"a\".id = 1" },
         { "select x.id, y.v from \"A\" x left join \"a\" y on x.id = y.id",
           "select x.id, y.v from \"A\" x LEFT OUTER JOIN \"a\" y ON x.id = y.id" },
         { "select * from \"b\" left join \"a\" on \"b\".id = \"a\".id",
           "select * from \"b\" LEFT OUTER JOIN \"a\" ON \"b\".id = \"a\".id" },
         { "select * from \"S\".\"a\"", "select * from \"S\".\"a\"" },
         { "select * from S.\"a\"", "select * from S.\"a\"" },
         { "select * from \"dbo\".\"orders\"", "select * from \"dbo\".\"orders\"" },
         { "select * from \"MixedCase\"", "select * from \"MixedCase\"" },
         { "select * from a where a.id in (select \"a\".id from \"a\")",
           "select * from a where a.id IN ( select \"a\".id from \"a\")" },
         { "select \"a\".id, count(*) from \"a\" group by \"a\".id order by \"a\".id",
           "select \"a\".id, count(*) from \"a\" group by \"a\".id order by \"a\".id asc" },
         { "select sum(\"a\".\"MixedCase\") from \"a\"", "select sum(\"a\".\"MixedCase\") from \"a\"" },
         // expressions keep a quoted qualifier as written
         { "select \"a\".id + 1 from \"a\"", "select \"a\".id+1 from \"a\"" },
         { "select case when \"a\".v > 0 then \"a\".id else 0 end from \"a\"",
           "select case when \"a\".v > 0 then \"a\".id else 0 END from \"a\"" },
         { "select \"a\".id from \"a\" group by \"a\".id having max(\"a\".v) > 0",
           "select \"a\".id from \"a\" group by \"a\".id having max(\"a\".v) > 0" },
         // a correlated column of a quoted outer table
         { "select \"c\".id from \"c\" where exists (select 1 from C where C.id = \"c\".id)",
           "select \"c\".id from \"c\" where EXISTS ( select 1 from C where C.id = \"c\".id)" },
         { "select \"c\".id, (select max(C.v) from C where C.id = \"c\".id) from \"c\"",
           "select (select max(C.v) from C where C.id = \"c\".id ), \"c\".id from \"c\"" },
         // brackets and backticks are quoted with the helper quote
         { "select * from [a]", "select * from \"a\"" },
         { "select `a`.id from `a`", "select \"a\".id from \"a\"" },
         // names the parser quotes again are not quoted twice
         { "select * from \"My A\"", "select * from \"My A\"" },
         { "select * from \"select\"", "select * from \"select\"" },
         { "select * from \"S\".\"My A\"", "select * from \"S\".\"My A\"" },
         // unquoted names are unchanged
         { "select * from a", "select * from a" },
         { "select a.id from a where a.id = 1", "select a.id from a where a.id = 1" },
         { "select * from S.a", "select * from S.a" },
      };

      for(String[] c : cases) {
         UniformSQL sql = parse(c[0], null);
         String generated = regenerate(sql);
         assertEquals(c[1], generated, c[0]);
         // round trip: the regenerated sql regenerates to itself
         assertEquals(generated, regenerate(parse(generated, null)), c[0]);
         // xml round trip
         assertEquals(generated, regenerate(load(toXML(parse(c[0], null)), null)), c[0]);
      }
   }

   @Test
   void helpersQuoteWithTheirQuote() throws Exception {
      String[][] cases = {
         // helper, sql, generated
         { "h2", "select * from \"a\"", "select * from \"a\"" },
         { "h2", "select * from \"dbo\".\"orders\"", "select * from \"dbo\".\"orders\"" },
         { "derby", "select \"a\".id from \"a\"", "select \"a\".id from \"a\"" },
         { "mysql", "select * from `a`", "select * from `a`" },
         { "mysql", "select `a`.id from `a` where `a`.id = 1", "select `a`.id from `a` where `a`.id = 1" },
         { "mysql", "select * from \"dbo\".\"orders\"", "select * from `dbo`.`orders`" },
         { "sqlserver", "select * from [a]", "select * from \"a\"" },
         { "sqlserver", "select [a].id from [dbo].[a]", "select \"a\".id from \"dbo\".\"a\"" },
         // a qualifier written as the end of a schema table name
         { "h2", "select \"a\".id from \"S\".\"a\" where \"a\".id = 1",
           "select \"a\".id from \"S\".\"a\" where \"a\".id = 1" },
         { "h2", "select \"a\".\"MixedCase\" from \"S\".\"a\" where \"a\".\"MixedCase\" = 1",
           "select \"a\".\"MixedCase\" from \"S\".\"a\" where \"a\".\"MixedCase\" = 1" },
         // case-sensitive helpers keep every name quoted, never twice
         { "postgresql", "select * from \"a\"", "select * from \"a\"" },
         { "postgresql", "select \"a\".id from \"S\".\"a\"", "select \"a\".\"id\" from \"S\".\"a\"" },
         { "snowflake", "select * from \"a\"", "select * from \"a\"" },
      };

      for(String[] c : cases) {
         JDBCDataSource ds = helpers().get(c[0]);
         String generated = regenerate(parse(c[1], ds));
         assertEquals(c[2], generated, c[0] + ": " + c[1]);
         assertEquals(generated, regenerate(parse(generated, ds)), c[0] + " round trip: " + c[1]);
         assertEquals(generated, regenerate(load(toXML(parse(c[1], ds)), ds)), c[0] + " xml: " + c[1]);
      }
   }

   // oracle uppercases the select list at parse time, a quoted qualifier keeps its case
   @Test
   void oracleKeepsQuotedQualifierCase() throws Exception {
      JDBCDataSource oracle = helpers().get("oracle");
      UniformSQL sql = parse("select \"a\".id from \"a\"", oracle);
      assertEquals("a.ID", sql.getSelection().getColumn(0));
      assertEquals("select \"a\".ID from \"a\"", regenerate(sql));

      sql = parse("select \"a\".id, \"A\".id from \"a\", \"A\"", oracle);
      assertEquals("a.ID", sql.getSelection().getColumn(0));
      assertEquals("A.ID", sql.getSelection().getColumn(1));
      // the select list is sorted by JDBCQueryCacheNormalizer
      assertEquals("select \"A\".ID, \"a\".ID from \"a\", \"A\"", regenerate(sql));

      assertEquals("select \"S\".\"a\".ID from \"S\".\"a\"",
                   regenerate(parse("select \"S\".\"a\".id from \"S\".\"a\"", oracle)));
      // a qualifier with a quoted segment keeps its case, the database folds the other ones
      assertEquals("select \"s\".a.ID from \"s\".a", regenerate(parse("select \"s\".a.id from \"s\".a", oracle)));
      assertEquals("select \"s\".a.ID from \"s\".a where \"s\".a.id = 1 order by \"s\".a.id asc",
                   regenerate(parse("select \"s\".a.id from \"s\".a where \"s\".a.id = 1 order by \"s\".a.id",
                                    oracle)));
      // and after the metadata fix of the query editor and the worksheet
      sql = parse("select \"s\".a.id from \"s\".a", oracle);
      JDBCUtil.fixUniformSQLInfo(sql, repository(), null, oracle);
      String fixed = regenerate(sql);
      assertTrue(fixed.endsWith("from \"s\".a"), fixed);
      assertTrue(fixed.startsWith("select \"s\".a."), fixed);
      // unquoted names are uppercased, as before
      assertEquals("select A.ID from a", regenerate(parse("select a.id from a", oracle)));

      // non-ansi outer joins
      String generated = regenerate(parse("select * from \"b\" left join \"a\" on \"b\".id = \"a\".id",
                                          oracle));
      assertEquals("select * from \"b\", \"a\" where \"b\".id = \"a\".id(+)", generated);
   }

   @Test
   void persistence() throws Exception {
      UniformSQL sql = parse("select * from \"S\".x, \"a\" y, b", null);
      String xml = toXML(sql);
      assertTrue(xml.contains("<name quotedSegments=\"0\"><![CDATA[S.x]]></name>"), xml);
      assertTrue(xml.contains("<name quotedSegments=\"0\"><![CDATA[a]]></name>"), xml);
      assertTrue(xml.contains("<name><![CDATA[b]]></name>"), xml);

      UniformSQL loaded = load(xml, null);
      assertArrayEquals(new int[] { 0 }, loaded.getSelectTable(0).getQuotedSegments());
      assertArrayEquals(new int[] { 0 }, loaded.getSelectTable(1).getQuotedSegments());
      assertNull(loaded.getSelectTable(2).getQuotedSegments());
      assertTrue(loaded.equalsStructure(load(xml, null)));

      // an asset saved before the change, or by an older build, generates as before
      String old = xml.replace(" quotedSegments=\"0\"", "");
      assertEquals("select * from S.x, a y, b", regenerate(load(old, null)));
      // a malformed value is ignored
      String malformed = xml.replace("quotedSegments=\"0\"", "quotedSegments=\"x,0\"");
      assertEquals("select * from S.x, a y, b", regenerate(load(malformed, null)));
      // an index without a segment is ignored
      String extra = xml.replace("quotedSegments=\"0\"", "quotedSegments=\"0,5\"");
      assertEquals("select * from \"S\".x, \"a\" y, b", regenerate(load(extra, null)));
      assertFalse(loaded.equalsStructure(load(old, null)));
   }

   @Test
   void copiesAndRenames() throws Exception {
      UniformSQL sql = parse("select \"a\".id from \"a\"", null);
      assertEquals("select \"a\".id from \"a\"", regenerate((UniformSQL) sql.clone()));

      UniformSQL read = new UniformSQL();
      read.read(sql);
      assertEquals("select \"a\".id from \"a\"", regenerate(read));

      // the same name keeps its quotes
      sql.setTable("a", "a");
      assertEquals("select \"a\".id from \"a\"", regenerate(sql));

      // another name drops them
      SelectTable table = sql.getSelectTable(0);
      table.setName("b");
      assertNull(table.getQuotedSegments());

      sql = parse("select \"a\".id from \"a\"", null);
      sql.setTable("a", "c");
      assertNull(sql.getSelectTable(0).getQuotedSegments());
   }

   // the query editor and the worksheet fix the parsed sql with the metadata
   @Test
   void fixUniformSQLInfoKeepsQuotes() throws Exception {
      for(String key : new String[] { "h2", "oracle" }) {
         JDBCDataSource ds = helpers().get(key);
         UniformSQL sql = parse("select \"a\".id from \"a\" where \"a\".id = 1", ds);
         JDBCUtil.fixUniformSQLInfo(sql, repository(), null, ds);
         String generated = regenerate(sql);
         assertTrue(generated.contains("from \"a\""), key + ": " + generated);
         assertFalse(generated.matches(".*[^\"]a\\.(id|ID).*"), key + ": " + generated);
      }
   }


   /**
    * Run the query through XSessionManager, as a plain query would run.
    */
   private Run run(XSessionManager session, UniformSQL usql, boolean ansi, int maxRows)
      throws Exception
   {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77569");
      query.setDataSource(derbySource(ansi));
      query.setSQLDefinition(usql);
      query.setMaxRows(maxRows);
      Run run = new Run();
      currentRun.set(run);

      try {
         run.table = session.getXNodeTableLens(query, new VariableTable(), null, null, null, -1);
      }
      finally {
         currentRun.remove();
      }

      assertNotNull(run.table, "query failed, see log");
      return run;
   }

   // the rows of the original sql, with the values of each row and the rows sorted, since the
   // generated sql may reorder columns
   private static List<String> direct(String sql) throws Exception {
      List<String> rows = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
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

      assertFalse(rows.isEmpty(), sql);
      Collections.sort(rows);
      return rows;
   }

   private static List<String> rows(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      List<String> rows = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         List<String> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(String.valueOf(table.getObject(r, c)));
         }

         Collections.sort(row);
         rows.add(row.toString());
      }

      Collections.sort(rows);
      return rows;
   }

   private static String norm(String sql) {
      return sql == null ? null : sql.replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = parse(sql, null);
      usql.setSQLString(sql, false);
      return usql;
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

   private static String regenerate(UniformSQL sql) {
      sql.clearSQLString();
      return norm(sql.getSQLString());
   }

   private static String toXML(UniformSQL sql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         sql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml, JDBCDataSource ds) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());

      if(ds != null) {
         loaded.setDataSource(ds);
      }

      return loaded;
   }

   private static Map<String, JDBCDataSource> helpers() {
      Map<String, JDBCDataSource> helpers = new LinkedHashMap<>();
      helpers.put("h2", dataSource("org.h2.Driver", "jdbc:h2:mem:x", "h2"));
      helpers.put("derby", dataSource("org.apache.derby.jdbc.EmbeddedDriver", "jdbc:derby:x", "derby"));
      helpers.put("oracle", dataSource("oracle.jdbc.OracleDriver", "jdbc:oracle:thin:@localhost:1521:x",
                                       "oracle"));
      helpers.put("mysql", dataSource("com.mysql.jdbc.Driver", "jdbc:mysql://localhost/db", "mysql"));
      helpers.put("sqlserver", dataSource("com.microsoft.sqlserver.jdbc.SQLServerDriver",
                                          "jdbc:sqlserver://localhost", "sql server"));
      helpers.put("postgresql", dataSource("org.postgresql.Driver", "jdbc:postgresql://localhost/db",
                                           "postgresql"));
      helpers.put("snowflake", dataSource("net.snowflake.client.jdbc.SnowflakeDriver", "jdbc:snowflake://x",
                                          "snowflake"));
      return helpers;
   }

   private static JDBCDataSource dataSource(String driver, String url, String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77569" + product);
      ds.setDriver(driver);
      ds.setURL(url);
      ds.setRuntimeProductName(product);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      return ds;
   }

   private static JDBCDataSource derbySource(boolean ansi) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77569" + ansi);
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      ds.setAnsiJoin(ansi);
      return ds;
   }

   // the metadata of every table
   private static XRepository repository() throws Exception {
      XRepository repository = mock(XRepository.class);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenAnswer(inv -> {
         XNode mtype = inv.getArgument(2);

         if("DBPROPERTIES".equals(mtype.getAttribute("type"))) {
            return new XNode("properties");
         }

         XTypeNode result = new XTypeNode("Result");
         result.addChild(XSchema.createPrimitiveType("id", Integer.class));
         XTypeNode meta = new XTypeNode("meta");
         meta.addChild(result);
         return meta;
      });

      return repository;
   }

   private static XSessionManager newSession() throws Exception {
      XDataService dataService = mock(XDataService.class);
      when(dataService.execute(any(), any(XQuery.class), any(), any(), anyBoolean(), any()))
         .thenAnswer(inv -> execute(inv.getArgument(1), inv.getArgument(2),
                                    inv.getArgument(3), inv.getArgument(5)));
      XSessionManager session = new XSessionManager(
         dataService, ConfigurationContext.getContext().getSpringBean(XSessionService.class),
         null);
      session.setCacheData(false);
      sessions.add(session);
      return session;
   }

   private static XNode execute(XQuery query, VariableTable vars, java.security.Principal user,
                                inetsoft.util.DataCacheVisitor visitor) throws Exception
   {
      Run run = currentRun.get();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      XNode node = handler.execute(query, vars, user, visitor);
      run.executedSql = executed.get();
      return node;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }

   /**
    * JDBCHandler generates the sql on a clone of the query, so record the sql that reaches
    * the connection instead.
    */
   private static DataSource recording(DataSource ds) {
      return proxy(DataSource.class, ds);
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target) {
      return (T) Proxy.newProxyInstance(
         UniformSQLQuotedTableNameTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> {
            String name = method.getName();

            if(args != null && args.length > 0 && args[0] instanceof String &&
               (name.startsWith("prepare") || name.startsWith("execute")) &&
               ((String) args[0]).trim().toLowerCase().startsWith("select"))
            {
               executed.set((String) args[0]);
            }

            Object result;

            try {
               result = method.invoke(target, args);
            }
            catch(InvocationTargetException e) {
               throw e.getCause();
            }

            if(result instanceof Connection && type != Connection.class) {
               return proxy(Connection.class, (Connection) result);
            }

            if(result instanceof Statement && !(result instanceof PreparedStatement)) {
               return proxy(Statement.class, (Statement) result);
            }

            return result;
         });
   }

   private static final class Run {
      TableLens table;
      String executedSql;
   }

   private static boolean created;
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<Run> currentRun = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
}
