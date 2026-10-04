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
 * Bug #77570, #77616. {@link UniformSQL#syncTable()}, run by
 * {@link JDBCUtil#fixUniformSQLInfo} on every query editor and worksheet path, dropped
 * ORDER BY and GROUP BY items it couldn't find in the field list by their stored text:
 * <ul>
 * <li>an ordinal greater than the field list size (a field list built without metadata),</li>
 * <li>a select alias that a case-sensitive helper (postgresql, snowflake, exasol) stores
 * with quotes ("a").</li>
 * </ul>
 * Rebuilding the ORDER BY then lost the direction of a surviving ordinal (order by 1 null
 * after an XML round trip) and merged two items on one column with the last direction. Once
 * the ordinal is gone, the query runs its regenerated sql, so the rows come back in another
 * order.
 *
 * Rows are compared, in order, with the original sql run directly on Derby, through the
 * real {@link XSessionManager#getXNodeTableLens} and {@link JDBCHandler}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  UniformSQLSyncOrdinalTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class UniformSQLSyncOrdinalTest {
   private static final String DB = "memory:bug77570";

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
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         for(String table : new String[] { "T", "Q", "W" }) {
            try {
               stmt.executeUpdate("drop table " + table);
            }
            catch(Exception ignore) {
               // first run
            }
         }

         stmt.executeUpdate("create table T (A INT, B VARCHAR(10))");
         // B sorts opposite to A, so ordering by the wrong column reverses the rows
         stmt.executeUpdate("insert into T values (1, 'z'), (2, 'y'), (3, 'x')");
         stmt.executeUpdate("create table Q (\"MixedCase\" INT, MIXEDCASE INT)");
         // MIXEDCASE sorts opposite to "MixedCase"
         stmt.executeUpdate("insert into Q values (1, 30), (2, 20), (3, 10)");
         stmt.executeUpdate("create table W (ID INT, \"a\" INT)");
         // "a" sorts differently from ID
         stmt.executeUpdate("insert into W values (1, 30), (2, 10), (3, 20)");
      }
   }

   // the reported shape: with a field list built without metadata, the duplicate column
   // leaves 2 fields and ordinal 3 was dropped, so the rows came back unsorted
   @Test
   void ordinalBeyondFieldListIsKept() throws Exception {
      String sql = "select T.B, T.B, T.A from T order by 3 desc";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals(2, usql.getFieldList().length);
      assertEquals("[T.A:desc]", orderBy(usql));
      assertSameRows(sql, usql);
   }

   @Test
   void twoOrdinalsBeyondFieldListAreKept() throws Exception {
      String sql = "select T.B, T.B, T.A, T.A from T order by 4 desc, 3";
      UniformSQL usql = fixedWithoutMeta(sql);

      // both name T.A, they stay ordinals and the sql runs as written
      assertEquals("[4:desc, 3:asc]", orderBy(usql));
      assertSameRows(sql, usql);
   }

   // a survivor kept its position but lost its direction: order by 1, and order by 1 null
   // after an xml round trip
   @Test
   void survivingOrdinalKeepsItsDirection() throws Exception {
      String sql = "select T.B, T.B, T.A from T order by 1 desc, 3";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals("[T.B:desc, T.A:asc]", orderBy(usql));
      assertSameRows(sql, usql);

      UniformSQL loaded = xmlRoundTrip(fixedWithoutMeta(sql));
      assertEquals("[T.B:desc, T.A:asc]", orderBy(loaded));
      assertTrue(norm(regenerate(loaded)).endsWith("order by t.b desc, t.a asc"),
                 regenerate(loaded));
   }

   // an item without a direction is written without it, not as order="null"
   @Test
   void missingDirectionIsNotWrittenAsNull() throws Exception {
      UniformSQL usql = parsed("select T.B, T.A from T order by 1");
      usql.removeAllOrderByFields();
      usql.setOrderBy("1", null);
      String xml = toXML(usql);

      assertFalse(xml.contains("order=\"null\""), xml);
      UniformSQL loaded = load(xml);
      assertNull(loaded.getOrderByItems()[0].getOrder());
      assertTrue(norm(regenerate(loaded)).endsWith("order by 1"), regenerate(loaded));
   }

   // an asset saved with order="null" before the fix loads without a direction
   @Test
   void savedNullDirectionLoadsAsNone() throws Exception {
      UniformSQL usql = parsed("select T.B, T.A from T order by 1");
      String xml = toXML(usql).replace("order=\"asc\"", "order=\"null\"");

      assertTrue(xml.contains("order=\"null\""), xml);
      UniformSQL loaded = load(xml);
      assertNull(loaded.getOrderByItems()[0].getOrder());
      String generated = norm(regenerate(loaded));
      assertFalse(generated.contains("null"), generated);
      assertTrue(generated.endsWith("order by 1"), generated);
   }

   // an ordinal and a named item on one column were merged with the last direction. The
   // ordinals now stay ordinals, so the sql runs as written
   @Test
   void ordinalAndNamedItemOnOneColumnRunAsWritten() throws Exception {
      String sql = "select T.B, T.A from T order by 1 desc, T.B";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals("[1:desc, T.B:asc]", orderBy(usql));
      assertSameRows(sql, usql);

      sql = "select T.B, T.B, T.A from T order by 2 desc, 1";
      usql = fixedWithoutMeta(sql);
      assertEquals("[2:desc, 1:asc]", orderBy(usql));
      assertSameRows(sql, usql);
   }

   /**
    * The ordinal 2 names w."a", which the item a also resolved to (h2 bound a to the column,
    * the database to the alias A). Converted and merged, the item took the column's quotes
    * and the sql ran as order by w."a" desc, sorting by the column. The ordinal stays an
    * ordinal, so the sql runs as written. The item a is now the alias A (Bug #77644), so
    * the ordinal of the query is converted, and an item w."a" keeps it an ordinal.
    */
   @Test
   void ordinalNamingTheColumnOfAnotherItemStaysAnOrdinal() throws Exception {
      String sql = "select w.id as A, w.\"a\" from w order by a desc, 2";

      for(String key : new String[] { "h2", "oracle" }) {
         UniformSQL usql = fixed(sql, key, "ID", "a");
         // the item a is the alias A (Bug #77644), so the ordinal names another column
         assertEquals("[A:desc, w.a:asc]", orderBy(usql), key);
         assertTrue(norm(regenerate(usql)).endsWith("order by w.id desc, w.\"a\" asc"),
                    key + " " + regenerate(usql));

         // an item that is the column of the ordinal
         usql = fixed("select w.id as A, w.\"a\" from w order by w.\"a\" desc, 2", key, "ID", "a");
         assertEquals("[w.a:desc, 2:asc]", orderBy(usql), key);
         assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), key);
      }

      assertSameRows(sql, fixed(sql, "h2", "ID", "a"));
   }

   // the ordinal is checked against the select list: an ordinal beyond it is still dropped
   @Test
   void ordinalBeyondSelectListIsDropped() throws Exception {
      UniformSQL usql = fixedWithoutMeta("select T.B, T.A from T order by 1 desc, 3");
      assertEquals("[T.B:desc]", orderBy(usql));

      usql = fixedWithoutMeta("select T.B, T.A from T group by T.B, T.A, 3");
      assertEquals("[T.B, T.A]", Arrays.toString(usql.getGroupBy()));
   }

   // with metadata, a table wider than the select list kept the ordinal before the fix too
   @Test
   void wideTableWithMetadataKeepsOrdinal() throws Exception {
      UniformSQL usql = fixed("select w.k, w.k, w.id from w order by 3 desc", "h2",
                              "ID", "K", "C1", "C2", "C3", "C4");

      assertEquals("[w.ID:desc]", orderBy(usql));
      assertTrue(norm(regenerate(usql)).endsWith("order by w.id desc"), regenerate(usql));
   }

   // group by 1, 2, 3, 4 with 3 fields became group by 1, 2, 3, which postgresql rejects
   @Test
   void groupByOrdinalsBeyondFieldListAreKept() throws Exception {
      UniformSQL usql = fixedWithoutMeta(
         "select T.B, T.B, T.B, T.A, count(*) from T group by 1, 2, 3, 4");

      assertEquals("[1, 2, 3, 4]", Arrays.toString(usql.getGroupBy()));
      assertTrue(norm(regenerate(usql)).endsWith("group by 1, 2, 3, 4"), regenerate(usql));

      UniformSQL loaded = xmlRoundTrip(fixedWithoutMeta(
         "select T.B, T.B, T.B, T.A, count(*) from T group by 1, 2, 3, 4"));
      loaded.syncTable();
      assertEquals("[1, 2, 3, 4]", Arrays.toString(loaded.getGroupBy()));
   }

   // a saved order by ordinal reloads as a String, it is checked against the select list
   @Test
   void savedStringOrdinalIsKept() throws Exception {
      String sql = "select T.B, T.B, T.A from T order by 3 desc";
      UniformSQL loaded = xmlRoundTrip(parsed(sql));
      loaded.setSQLString(sql, false);
      JDBCUtil.fixUniformSQLInfo(loaded, repository(), null, dataSource());

      assertEquals("[3:desc]", orderBy(loaded));
      assertSameRows(sql, loaded);
   }

   // "1" written as a quoted identifier is a column, not an ordinal
   @Test
   void quotedDigitsAreAColumn() throws Exception {
      UniformSQL usql = fixed("select w.id, w.\"1\" from w group by w.id, \"1\"", "h2",
                              "ID", "1");
      String generated = regenerate(usql);

      assertTrue(generated.endsWith("group by w.ID, w.\"1\""), generated);
   }

   // the converted ordinal is generated as an order by field, with the quotes of the column
   @Test
   void convertedOrdinalKeepsTheQuotes() throws Exception {
      String sql = "select \"MixedCase\" from Q order by 1";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertTrue(regenerate(fixedWithoutMeta(sql)).endsWith("order by \"MixedCase\" asc"),
                 regenerate(fixedWithoutMeta(sql)));
      assertSameRows(sql, usql);

      sql = "select Q.\"MixedCase\" from Q order by 1 desc";
      usql = fixedWithoutMeta(sql);
      assertTrue(regenerate(fixedWithoutMeta(sql)).endsWith("order by Q.\"MixedCase\" desc"),
                 regenerate(fixedWithoutMeta(sql)));
      assertSameRows(sql, usql);

      usql = fixed("select t.\"MixedCase\" from t order by 1 desc", "h2", "MixedCase", "MIXEDCASE");
      assertTrue(regenerate(usql).endsWith("order by t.\"MixedCase\" desc"), regenerate(usql));
   }

   /**
    * With a wildcard, the model's select list doesn't keep the positions of the sql (the
    * wildcard is expanded, merged with an explicit column, or deleted without metadata), so
    * an ordinal resolved against it named another column. It is kept as written.
    */
   @Test
   void ordinalWithWildcardIsKept() throws Exception {
      String[] meta = { "ID", "K", "C1", "C2" };

      for(String sql : new String[] {
         "select t.k, t.* from t order by 3 desc",
         "select t.*, t.id from t order by 3 desc",
         "select t.id, t.* from t order by 1 desc, 3"})
      {
         UniformSQL usql = fixed(sql, "h2", meta);
         String order = orderBy(usql);
         assertTrue(order.startsWith("[3:desc") || order.equals("[1:desc, 3:asc]"),
                    sql + " " + order);
      }

      UniformSQL usql = fixed("select t.*, t.id from t order by 3 desc", "h2", "ID", "K");
      // 3 is beyond the 2 columns of the model, and kept
      assertEquals("[3:desc]", orderBy(usql));

      usql = fixed("select t.*, t.id from t group by t.id, 3", "h2", "ID", "K");
      assertEquals("[t.ID, 3]", Arrays.toString(usql.getGroupBy()));
   }

   // the rows of a wildcard query: the ordinal keeps the sql as written
   @Test
   void ordinalWithWildcardRunsAsWritten() throws Exception {
      String sql = "select T.*, T.A from T order by 3 desc, 2 desc";
      UniformSQL usql = fixedWithoutMeta(sql);

      assertEquals("[3:desc, 2:desc]", orderBy(usql));
      assertSameRows(sql, usql);
   }

   // with metadata, the wildcard's expansion merged the explicit column, so the ordinal was
   // converted to another column (t.k, t.* order by 3 sorted by t.C1) and the query ran the
   // regenerated sql with that order
   @Test
   void ordinalWithWildcardAndMetadataRunsAsWritten() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table T4");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table T4 (ID INT, K VARCHAR(5), C1 INT, C2 INT)");
         // the orders of ID, K and C1 all differ
         stmt.executeUpdate(
            "insert into T4 values (1, 'a', 30, 0), (2, 'c', 10, 0), (3, 'b', 20, 0), " +
            "(4, 'd', 40, 0)");
      }

      for(String sql : new String[] {
         "select T4.K, T4.* from T4 order by 3 desc",
         "select T4.*, T4.ID from T4 order by 3 desc" })
      {
         UniformSQL usql = fixed(sql, "h2", "ID", "K", "C1", "C2");
         // run on Derby
         usql.setDataSource(dataSource());
         assertSameRows(sql, usql);
      }
   }

   // control: an ordinal in a query without a wildcard is still converted to the column
   @Test
   void ordinalWithoutWildcardIsConverted() throws Exception {
      UniformSQL usql = fixed("select t.k, t.id from t order by 2 desc", "h2", "ID", "K");
      assertEquals("[t.ID:desc]", orderBy(usql));
   }

   /**
    * #77616: postgresql, snowflake and exasol store an unquoted alias reference as "a",
    * which the filter didn't find among the aliases, so the item was dropped.
    */
   @Test
   void unquotedAliasOnCaseSensitiveHelpersIsKept() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         // the alias in the case the database folds an unquoted name to
         String a = "postgresql".equals(key) ? "a" : "A";

         assertTrue(regenerate(fixed("select id as " + a + " from t order by " + a, key, "id"))
                       .endsWith("from \"t\" order by \"" + a + "\" asc"), key);
         assertTrue(regenerate(fixed("select t.id as " + a + " from t order by " + a + " desc",
                                     key, "id"))
                       .endsWith("order by \"" + a + "\" desc"), key);
         assertTrue(regenerate(fixed("select \"MixedCase\" as " + a + " from t order by " + a,
                                     key, "MixedCase", "id"))
                       .endsWith("order by \"" + a + "\" asc"), key);

         String generated = regenerate(fixed("select id as " + a + ", count(*) from t group by " +
                                             a, key, "id"));
         assertTrue(generated.endsWith("group by \"" + a + "\""), key + " " + generated);

         generated = regenerate(fixed("select id " + a + ", count(*) from t group by id " +
                                      "order by " + a, key, "id"));
         assertTrue(generated.endsWith("order by \"" + a + "\" asc"), key + " " + generated);
      }
   }

   // controls: a quoted alias, and helpers that don't quote an unquoted alias
   @Test
   void aliasControls() throws Exception {
      String quoted = regenerate(fixed("select id as \"A\" from t order by \"A\"", "postgresql",
                                       "id"));
      // generated as its column, which sorts the same
      assertTrue(quoted.endsWith("order by \"id\" asc"), quoted);

      for(String key : new String[] { "h2", "oracle" }) {
         String generated = regenerate(fixed("select id as a from t order by a desc", key, "ID"));
         // the alias is generated as its column
         assertTrue(norm(generated).endsWith("order by t.id desc"), key + " " + generated);
      }
   }

   /**
    * #77616: an alias that shadows a column name is kept as the alias, so postgresql still
    * sorts and groups by the select item, not by the column of the same name.
    */
   @Test
   void aliasShadowingAColumnIsKept() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         // the names in the case the database folds an unquoted name to
         boolean lower = "postgresql".equals(key);
         String id = lower ? "id" : "ID";
         String k = lower ? "k" : "K";
         String a = lower ? "a" : "A";
         String c = lower ? "c" : "C";

         String generated = regenerate(fixed("select k as " + id + ", id as " + k + " from t " +
                                             "order by " + id + " desc", key, id, k));
         assertTrue(generated.endsWith("order by \"" + id + "\" desc"), key + " " + generated);

         generated = regenerate(fixed("select k as " + id + ", id as " + k + " from t " +
                                      "order by " + k + " desc, " + id, key, id, k));
         assertTrue(generated.endsWith("order by \"" + k + "\" desc, \"" + id + "\" asc"),
                    key + " " + generated);

         generated = regenerate(fixed("select id as " + a + ", count(*) as " + c + " from t " +
                                      "group by " + a + " order by " + c + " desc, " + a,
                                      key, id, k));
         assertTrue(generated.endsWith("group by \"" + a + "\" order by \"" + c + "\" desc, \"" +
                                          a + "\" asc"), key + " " + generated);
      }
   }

   /**
    * postgresql folds an unquoted name to lower case, snowflake and exasol to upper case. An
    * alias stored in the folded case is that name whether it was quoted or not, so an
    * unquoted reference in any case matches it.
    */
   @Test
   void unquotedReferenceMatchesAliasInTheFoldedCase() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         boolean lower = "postgresql".equals(key);
         String alias = lower ? "a" : "A";
         String ref = lower ? "A" : "a";

         for(String as : new String[] { alias, "\"" + alias + "\"" }) {
            // the reference is spelled in another case than the alias is generated, so it is
            // generated as the alias's column ("id"), which sorts and groups the same (#77573)
            String generated = regenerate(fixed("select id as " + as + " from t order by " +
                                                ref + " desc", key, "id", "a"));
            assertTrue(generated.endsWith(" from \"t\" order by \"id\" desc"), key + " " + generated);

            generated = regenerate(fixed("select id as " + as + ", count(*) from t group by " +
                                         ref + " order by " + ref + " desc", key, "id"));
            assertTrue(generated.endsWith(" from \"t\" group by \"id\" order by \"id\" desc"),
                       key + " " + generated);
         }
      }
   }

   /**
    * An alias stored in another case is the same name as an unquoted reference only if the
    * alias wasn't quoted, which the parsed sql doesn't record. It is neither bound to the
    * alias nor to a column of that name, the item is dropped as before #77616.
    */
   @Test
   void otherCaseAliasOfUnknownQuotingIsNotGuessed() throws Exception {
      // the parser records the alias quoting since #77573, unknown is a query saved before
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         boolean lower = "postgresql".equals(key);
         String alias = lower ? "A" : "a";
         String ref = lower ? "a" : "A";

         for(String as : new String[] { alias, "\"" + alias + "\"" }) {
            String generated = regenerate(fixedWithoutAliasQuoting(
               "select k as " + as + " from t order by " + ref + " desc", key, "id", "k", "a"));
            assertFalse(generated.contains(" order by "), key + " " + generated);
         }

         // a quoted identifier elsewhere in the sql doesn't decide it
         String generated = regenerate(fixedWithoutAliasQuoting(
            "select w.id as " + alias + ", w.\"" + alias + "\" from w order by " + ref + " desc",
            key, "id", "A", "a"));
         assertFalse(generated.contains(" order by "), key + " " + generated);
      }
   }

   /**
    * An item that isn't guessed is dropped. The ordinals next to it then stay ordinals, so
    * the query keeps running as written (Bug #77557 gate) instead of regenerated without it.
    */
   @Test
   void ordinalsNextToAnUndecidedItemStayOrdinals() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         boolean lower = "postgresql".equals(key);
         String alias = lower ? "A" : "a";
         String ref = lower ? "a" : "A";
         String select = "select t.k as " + alias + ", t.id from t order by ";

         // the alias quoting isn't known (saved before #77573 recorded it)
         UniformSQL usql = fixedWithoutAliasQuoting(select + ref + ", 1 desc, 2 desc", key, "id", "k");
         assertEquals("[1:desc, 2:desc]", orderBy(usql), key);
         assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), key);

         usql = fixedWithoutAliasQuoting(select + ref + ", 2 desc", key, "id", "k");
         assertEquals("[2:desc]", orderBy(usql), key);
         assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), key);

         usql = fixedWithoutAliasQuoting(select + ref + " desc, 1", key, "id", "k");
         assertEquals("[1:asc]", orderBy(usql), key);
         assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), key);

         // control: an alias in the folded case is decided, the ordinal is converted. The
         // alias of a table column is referenced as the column (#77573)
         usql = fixed("select t.k as " + ref + ", t.id from t order by " + alias + ", 2 desc",
                      key, "id", "k");
         assertEquals("[\"t\".k:asc, \"t\".id:desc]", orderBy(usql), key);
      }
   }

   /**
    * A1: an unquoted reference with the spelling of an alias that isn't in the folded case
    * ({@code as "A" ... order by A} on postgresql) is not that alias if the alias was quoted,
    * which isn't recorded. It is neither bound to the alias nor dropped in a way that changes
    * what runs: next to an ordinal, the ordinals stay and the sql runs as written. A group by
    * takes the table column of that name first.
    */
   @Test
   void sameSpellingAliasOutsideTheFoldedCaseIsNotGuessed() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         String alias = "postgresql".equals(key) ? "A" : "a";
         // a column created unquoted is in the folded case
         String a = "postgresql".equals(key) ? "a" : "A";

         for(String as : new String[] { alias, "\"" + alias + "\"" }) {
            // t has a column a, u doesn't
            for(String[] meta : new String[][] { { "id", "k", a }, { "id", "k" } }) {
               UniformSQL usql = fixedWithoutAliasQuoting(
                  "select id as " + as + " from t order by " + alias + " desc", key, meta);
               assertEquals("[]", orderBy(usql), key + " " + as);

               usql = fixedWithoutAliasQuoting("select id as " + as + ", k from t order by " + alias + " desc, 2",
                            key, meta);
               assertEquals("[2:asc]", orderBy(usql), key + " " + as);
               assertNull(JDBCQueryCacheNormalizer.generateSortedColumnMap(usql), key);
            }

            // the table column first
            String generated = regenerate(fixedWithoutAliasQuoting("select id as " + as + ", count(*) from t " +
                                                "group by " + alias, key, "id", "k", a));
            assertTrue(generated.endsWith("group by \"t\"." + a), key + " " + generated);

            generated = regenerate(fixedWithoutAliasQuoting("select id as " + as + ", count(*) from t " +
                                         "group by " + alias, key, "id", "k"));
            assertFalse(generated.contains(" group by "), key + " " + generated);
         }
      }
   }

   /**
    * postgresql resolves a group by name to a table column before a select alias, also when
    * the alias has the same spelling.
    */
   @Test
   void groupByColumnBeforeAlias() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         // the columns, created unquoted, are in the folded case
         boolean pg = "postgresql".equals(key);
         String id = pg ? "id" : "ID";
         String k = pg ? "k" : "K";
         String generated = regenerate(fixed("select k as ID, count(*) from t group by id",
                                             key, id, k));
         assertFalse(generated.contains("group by \"ID\""), key + " " + generated);

         generated = regenerate(fixed("select k / 100 as ID, count(*) from t " +
                                      "group by ID, k / 100", key, id, k));
         assertTrue(generated.contains("group by \"t\"." + id + ", "), key + " " + generated);

         generated = regenerate(fixed("select k / 100 as id, count(*) from t " +
                                      "group by id, k / 100", key, id, k));
         assertTrue(generated.contains("group by \"t\"." + id + ", "), key + " " + generated);

         // no column of that name: the alias, if it is in the folded case
         String alias = "postgresql".equals(key) ? "id" : "ID";
         generated = regenerate(fixed("select k / 100 as " + alias + ", count(*) from t " +
                                      "group by " + alias, key, id + "2", k));
         assertTrue(generated.endsWith("group by \"" + alias + "\""), key + " " + generated);
      }
   }

   // h2 and oracle don't quote an unquoted reference, they match the alias in any case
   @Test
   void aliasInAnotherCaseOnOtherHelpers() throws Exception {
      for(String key : new String[] { "h2", "oracle" }) {
         // the alias as if written in its case, its column (Bug #77644)
         String generated = regenerate(fixed("select id as A from t order by a desc", key, "ID"));
         assertTrue(norm(generated).endsWith("order by t.id desc"), key + " " + generated);

         generated = regenerate(fixed("select id as A, count(*) from t group by a", key, "ID"));
         assertTrue(norm(generated).contains("group by "), key + " " + generated);
      }
   }

   /**
    * The same helpers store an unquoted reference to a column that isn't selected as "k",
    * which the field list didn't find, so the item was dropped. It matches the column in
    * any case. A quoted reference only matches the exact name.
    */
   @Test
   void unquotedColumnNotSelectedIsKept() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         // the columns, created unquoted, are in the folded case
         boolean pg = "postgresql".equals(key);
         String id = pg ? "id" : "ID";
         String k = pg ? "k" : "K";
         String generated = regenerate(fixed("select id from t order by k desc", key, id, k));
         assertTrue(generated.endsWith("order by \"t\"." + k + " desc"), key + " " + generated);

         generated = regenerate(fixed("select id from t order by K desc", key, id, k));
         assertTrue(generated.endsWith("order by \"t\"." + k + " desc"), key + " " + generated);

         generated = regenerate(fixed("select id, count(*) from t group by id, k order by k",
                                      key, id, k));
         assertTrue(generated.contains(" \"t\"." + k + " order by \"t\"." + k + " asc"),
                    key + " " + generated);

         // Finding Z: a column in another case (created quoted) isn't an unquoted reference
         generated = regenerate(fixed("select id from t order by k desc", key, id, pg ? "K" : "k"));
         assertFalse(generated.contains(" order by "), key + " " + generated);
      }
   }

   /**
    * A quoted reference is stored without its quotes and doesn't go through the unquoted
    * match. JDBCUtil.getFullPathOf changes its case only when no column has the same case
    * (#77558), as before this fix.
    */
   @Test
   void quotedColumnReferenceIsUnchanged() throws Exception {
      for(String key : new String[] { "postgresql", "snowflake", "exasol" }) {
         UniformSQL usql = fixed("select id from t order by \"K\" desc, \"k\"", key, "id", "K", "k");
         assertEquals("[\"t\".K:desc, \"t\".k:asc]", orderBy(usql), key);

         // with no column K, getFullPathOf changes the case to k, before and after this fix
         String generated = regenerate(fixed("select id from t order by \"K\" desc", key, "id", "k"));
         assertEquals("select \"id\" from \"t\" order by \"t\".\"k\" desc", generated, key);
      }
   }

   // h2 finds an unquoted column in the field list, as before
   @Test
   void columnNotSelectedOnOtherHelpers() throws Exception {
      for(String key : new String[] { "h2", "oracle" }) {
         String generated = regenerate(fixed("select id from t order by k desc", key, "ID", "K"));
         assertTrue(norm(generated).endsWith("order by t.k desc"), key + " " + generated);

         generated = regenerate(fixed("select id, count(*) from t group by id, k", key, "ID", "K"));
         assertTrue(norm(generated).endsWith("group by t.id, t.k"), key + " " + generated);
      }
   }

   // #6151's quoted aggregate record of an order by item is kept when the order by is rebuilt
   @Test
   void quotedAggregateRecordIsKeptOnRebuild() throws Exception {
      UniformSQL usql = fixed("select q.id, sum(q.\"MixedCase\") from t q group by q.id " +
                              "order by sum(q.\"MixedCase\") desc, 9", "postgresql",
                              "MIXEDCASE", "MixedCase", "id");

      assertTrue(regenerate(usql).endsWith("order by sum(q.\"MixedCase\") desc"),
                 regenerate(usql));
   }

   // the order by of a query run through XSessionManager, compared with the original sql
   private void assertSameRows(String sql, UniformSQL usql) throws Exception {
      Run run = run(newSession(), usql);
      List<List<String>> expected = direct(sql);

      assertFalse(expected.isEmpty());
      assertEquals(expected, rows(run.table), sql + " executed as " + run.executedSql);
   }

   private static String orderBy(UniformSQL usql) {
      List<String> items = new ArrayList<>();

      for(OrderByItem item : usql.getOrderByItems()) {
         items.add(item.getField() + ":" + item.getOrder());
      }

      return items.toString();
   }

   // the parsed sql, through the metadata step with no table columns (the column fetch
   // failed), as the query editor and a worksheet sql table run it
   private static UniformSQL fixedWithoutMeta(String sql) throws Exception {
      UniformSQL usql = parsed(sql);
      usql.setDataSource(dataSource());
      JDBCUtil.fixUniformSQLInfo(usql, repository(), null, dataSource());
      return usql;
   }

   // the parsed sql, through the metadata step with the columns of every table
   private static UniformSQL fixed(String sql, String helper, String... columns)
      throws Exception
   {
      JDBCDataSource ds = helper(helper);
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertFalse(usql.isLossy(), sql);
      JDBCUtil.fixUniformSQLInfo(usql, repository(columns), null, ds);
      return usql;
   }

   // as fixed, for a query whose alias quoting isn't known (saved before it was recorded)
   private static UniformSQL fixedWithoutAliasQuoting(String sql, String helper, String... columns)
      throws Exception
   {
      JDBCDataSource ds = helper(helper);
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      JDBCSelection select = (JDBCSelection) usql.getSelection();

      for(int i = 0; i < select.getColumnCount(); i++) {
         select.setAliasQuoted(i, null);
      }

      JDBCUtil.fixUniformSQLInfo(usql, repository(columns), null, ds);
      return usql;
   }

   private static UniformSQL parsed(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.parse(sql, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      usql.setSQLString(sql, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), sql);
      assertFalse(usql.isLossy(), sql);
      return usql;
   }

   private static XRepository repository(String... columns) throws Exception {
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

   // the table metadata is cached by data source name, use a new name each time
   private static JDBCDataSource helper(String key) {
      String[] spec = switch(key) {
         case "h2" -> new String[] { "org.h2.Driver", "jdbc:h2:mem:x" };
         case "oracle" -> new String[] { "oracle.jdbc.OracleDriver",
                                         "jdbc:oracle:thin:@localhost:1521:x" };
         case "postgresql" -> new String[] { "org.postgresql.Driver",
                                             "jdbc:postgresql://localhost/db" };
         case "snowflake" -> new String[] { "net.snowflake.client.jdbc.SnowflakeDriver",
                                            "jdbc:snowflake://x" };
         case "exasol" -> new String[] { "com.exasol.jdbc.EXADriver", "jdbc:exa:x" };
         default -> throw new IllegalArgumentException(key);
      };

      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("ds77570" + key + "_" + (++sources) + "_" + RUN);
      ds.setDriver(spec[0]);
      ds.setURL(spec[1]);
      ds.setRuntimeProductName(key);
      // otherwise the mysql and oracle helpers ask the repository for it
      ds.setProductVersion("10.0");
      ds.setAnsiJoin("snowflake".equals(key));
      return ds;
   }

   private static String regenerate(UniformSQL usql) {
      usql.clearSQLString();
      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static String norm(String sql) {
      return sql.replaceAll("\\s+", " ").trim().toLowerCase();
   }

   private static String toXML(UniformSQL usql) {
      StringWriter buffer = new StringWriter();

      try(PrintWriter writer = new PrintWriter(buffer)) {
         usql.writeXML(writer);
      }

      return buffer.toString();
   }

   private static UniformSQL load(String xml) throws Exception {
      UniformSQL loaded = new UniformSQL();
      loaded.parseXML(Tool.parseXML(new StringReader(xml)).getDocumentElement());
      return loaded;
   }

   private static UniformSQL xmlRoundTrip(UniformSQL usql) throws Exception {
      return load(toXML(usql));
   }

   private static List<List<String>> direct(String sql) throws Exception {
      List<List<String>> rows = new ArrayList<>();

      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement();
          ResultSet rs = stmt.executeQuery(sql))
      {
         int count = rs.getMetaData().getColumnCount();

         while(rs.next()) {
            List<String> row = new ArrayList<>();

            for(int i = 1; i <= count; i++) {
               row.add(String.valueOf(rs.getObject(i)));
            }

            rows.add(row);
         }
      }

      return rows;
   }

   private static List<List<String>> rows(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      List<List<String>> rows = new ArrayList<>();

      for(int r = 1; r < table.getRowCount(); r++) {
         List<String> row = new ArrayList<>();

         for(int c = 0; c < table.getColCount(); c++) {
            row.add(String.valueOf(table.getObject(r, c)));
         }

         rows.add(row);
      }

      return rows;
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77570");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77570");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
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

   private Run run(XSessionManager session, UniformSQL usql) throws Exception {
      JDBCQuery query = newQuery(usql);
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
         UniformSQLSyncOrdinalTest.class.getClassLoader(), new Class<?>[] { type },
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

   private static final long RUN = System.nanoTime();
   private static int sources;
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static final ThreadLocal<Run> currentRun = new ThreadLocal<>();
   private static final List<XSessionManager> sessions = new ArrayList<>();
}
