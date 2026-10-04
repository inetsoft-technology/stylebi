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

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77698. The input row limit of a query with vpm conditions
 * ({@link SQLHelper}.appendLimitClause) was skipped when the generated sql contained the
 * limit keyword of the database anywhere, in a column name (credit_limit) or a literal
 * ('no limit', 'select top', 'fetch first', ' where rownum <= '). And
 * {@link JDBCQueryCacheNormalizer} didn't normalize a query with a limit keyword in a
 * literal or quoted name.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLHelperVpmInputLimitTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLHelperVpmInputLimitTest {
   private static final String DB = "memory:bug77698";

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
         DataSource ds = derby();
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

   // the generated sql with an input limit of 2 rows and a vpm condition, for each limit
   // syntax: limit (mysql, postgresql), top (sql server), fetch first (db2, derby) and rownum
   // (oracle). Each with a control, and the text that skipped the limit.
   @ParameterizedTest(name = "{0}: {1}")
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "mysql|select customers.credit, customers.id from customers where customers.region = 'east'",
      "mysql|select customers.credit_limit, customers.id from customers where customers.region = 'east'",
      "mysql|select customers.credit as rate_limit, customers.id from customers where customers.region = 'east'",
      "mysql|select customers.credit, customers.id from customers where customers.note <> 'no limit'",
      "mysql|select customers.credit, customers.id from customers where customers.note <> 'NO LIMIT'",
      "postgresql|select customers.credit, customers.id from customers where customers.region = 'east'",
      "postgresql|select customers.credit_limit, customers.id from customers where customers.region = 'east'",
      "postgresql|select customers.credit, customers.id from customers where customers.note <> 'no limit'",
      "sql server|select customers.credit, customers.id from customers where customers.region = 'east'",
      "sql server|select customers.credit, customers.id from customers where customers.note <> 'select top'",
      "sql server|select customers.credit_limit, customers.id from customers where customers.note <> 'no limit'",
      "db2|select customers.credit, customers.id from customers where customers.region = 'east'",
      "db2|select customers.credit, customers.id from customers where customers.note <> 'fetch first'",
      "derby|select customers.credit, customers.id from customers where customers.region = 'east'",
      "derby|select customers.credit, customers.id from customers where customers.note <> 'fetch first'",
      "oracle|select customers.credit, customers.id from customers where customers.region = 'east'",
      "oracle|select customers.credit, customers.id from customers where customers.note <> ' where rownum <= '",
   })
   void inputLimitAppended(String product, String text) throws Exception {
      String sql = generate(product, text, true);
      String plain = generate(product, text, false);

      switch(product) {
      case "mysql", "postgresql" -> {
         assertEquals(plain + " limit 2", sql);
         assertEquals(1, count(sql, "\\blimit 2\\b"), sql);
      }
      case "sql server" -> {
         assertEquals(plain.replaceFirst("select", "select top 2"), sql);
         assertFalse(sql.contains("limit 2"), sql);
      }
      case "db2", "derby" -> {
         assertEquals(plain + " fetch first 2 rows only", sql);
         assertEquals(1, count(sql, "fetch first 2 rows only"), sql);
      }
      case "oracle" -> {
         assertTrue(sql.endsWith(") AND rownum <= 2"), sql);
         assertEquals(1, count(sql, "AND rownum <= 2"), sql);
      }
      default -> fail(product);
      }
   }

   // a derived table limited by its own input limit doesn't limit the statement, so the
   // statement gets its own limit too (stricter than before, and valid sql)
   @Test
   void derivedTableLimitDoesNotSkipStatementLimit() throws Exception {
      String text = "select x.id from (select customers.id from customers) x " +
         "where x.id > 0";
      String sql = generate("mysql", text, true);

      assertTrue(sql.endsWith(" limit 2"), sql);
      assertFalse(sql.contains("limit 2 limit"), sql);
   }

   // a sql server subquery with order by starts with the top select option
   // (getSelectionOption()), with or without distinct/all, so no second top is added
   // (select top 2 top 999999999 is not valid sql)
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select customers.id from customers order by customers.id|select top 999999999",
      "select distinct customers.id from customers order by customers.id|select distinct top 999999999",
      "select all customers.id from customers order by customers.id|select all top 999999999",
   })
   void sqlServerSubqueryTopKept(String text, String start) throws Exception {
      UniformSQL plain = parse(dataSource("sql server"), text);
      plain.setSubQuery(true);
      UniformSQL vpm = parse(dataSource("sql server"), text);
      vpm.setSubQuery(true);
      vpm.setVPMCondition(true);
      vpm.setHint(UniformSQL.HINT_INPUT_MAXROWS, "2");
      String sql = vpm.getSQLString().replaceAll("\\s+", " ").trim();

      assertTrue(sql.startsWith(start + " "), sql);
      assertEquals(plain.getSQLString().replaceAll("\\s+", " ").trim(), sql);
      assertEquals(1, count(sql, "\\btop\\b"), sql);
   }

   // an oracle query without a where clause: the empty where was "replaced" in the whole
   // sql, which broke it
   @Test
   void oracleWithoutWhereStaysValid() throws Exception {
      String text = "select customers.credit, customers.id from customers";

      assertEquals(generate("oracle", text, false), generate("oracle", text, true));
   }

   // the input limit is applied on the database: 2 of the 6 rows
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select customers.id, customers.note from customers where customers.note <> 'x'",
      "select customers.id, customers.note from customers where customers.note <> 'fetch first'",
      "select customers.id, customers.note_limit from customers where customers.note <> 'no limit'",
   })
   void derbyRowsLimited(String text) throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table customers");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table customers (id INT, note VARCHAR(20), " +
                               "note_limit VARCHAR(20))");
         stmt.executeUpdate("insert into customers values (1, 'a', 'a'), (2, 'b', 'b'), " +
                               "(3, 'c', 'c'), (4, 'd', 'd'), (5, 'e', 'e'), (6, 'f', 'f')");
      }

      JDBCDataSource ds = dataSource("derby");
      UniformSQL usql = parse(ds, text);
      usql.setVPMCondition(true);
      usql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "2");

      JDBCQuery query = new JDBCQuery();
      query.setName("bug77698");
      query.setDataSource(ds);
      query.setSQLDefinition(usql);
      VariableTable vars = new VariableTable();

      JDBCHandler handler = new JDBCHandler();
      handler.connect(ds, vars);
      XNode node = handler.execute(query, vars, null, null);
      XNodeTableLens table = new XNodeTableLens(node);
      table.moreRows(Integer.MAX_VALUE);

      assertEquals(3, table.getRowCount(), "header plus the 2 rows of the input limit");
   }

   // a row limit keyword in a literal or quoted name is not a row limit
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select a from t where b <> 'no limit set'|false",
      "select a as \"the top one\" from t|false",
      "select a from t where b <> 'a fetch first b'|false",
      "select a from t where b <> 'x rownum < 3'|false",
      "select a, `limit` from t|false",
      // conservative: a [ is not a quote in every database, so [top] is not skipped
      "select a, [top] from t|true",
      "select credit_limit, top_n, rownum_x from t|false",
      "select a from t where b <> 'x'|false",
      "select top 10 a from t|true",
      "SELECT TOP 10 a FROM t|true",
      "select a from t limit 10|true",
      "select a from t\tlimit 10|true",
      "select a from t fetch first 10 rows only|true",
      "select a from t where rownum < 10|true",
      "select a from t where rownum <= 10|true",
      "select a from t where b <> 'it''s' limit 10|true",
   })
   void maxRowKeyword(String sql, boolean expected) {
      assertEquals(expected, JDBCQueryCacheNormalizer.hasMaxRow(sql), sql);
   }

   // a parsed query with a limit keyword in a literal is normalized, like the control
   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '~', value = {
      "select customers.note, customers.id from customers where customers.note <> 'x'",
      "select customers.note, customers.id from customers where customers.note <> 'no limit set'",
      "select customers.note, customers.id as \"the top one\" from customers",
   })
   void literalKeywordNormalized(String text) throws Exception {
      UniformSQL usql = parse(dataSource("mysql"), text);
      usql.setSQLString(text, false);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77698");
      query.setDataSource(dataSource("mysql"));
      query.setSQLDefinition(usql);

      assertArrayEquals(new int[] { 1, 0 },
                        new JDBCQueryCacheNormalizer(query).getSortedColumnMap(), text);
   }

   // a real top is still not normalized
   @Test
   void realTopNotNormalized() throws Exception {
      String text = "select top 10 customers.note, customers.id from customers";
      UniformSQL usql = parse(dataSource("mysql"), text);
      usql.setSQLString(text, false);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77698");
      query.setDataSource(dataSource("mysql"));
      query.setSQLDefinition(usql);

      assertNull(new JDBCQueryCacheNormalizer(query).getSortedColumnMap());
   }

   private static String generate(String product, String text, boolean vpm) throws Exception {
      UniformSQL usql = parse(dataSource(product), text);

      if(vpm) {
         usql.setVPMCondition(true);
         usql.setHint(UniformSQL.HINT_INPUT_MAXROWS, "2");
      }

      return usql.getSQLString().replaceAll("\\s+", " ").trim();
   }

   private static UniformSQL parse(JDBCDataSource ds, String text) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(ds);
      usql.parse(text, UniformSQL.PARSE_ALL, UniformSQL.PARSE_PERIOD);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), text);
      usql.clearSQLString();
      return usql;
   }

   private static int count(String text, String regex) {
      Matcher matcher = Pattern.compile(regex).matcher(text);
      int n = 0;

      while(matcher.find()) {
         n++;
      }

      return n;
   }

   private static JDBCDataSource dataSource(String product) {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77698-" + product);

      switch(product) {
      case "mysql" -> {
         ds.setDriver("com.mysql.cj.jdbc.Driver");
         ds.setURL("jdbc:mysql://localhost:3306/test");
      }
      case "postgresql" -> {
         ds.setDriver("org.postgresql.Driver");
         ds.setURL("jdbc:postgresql://localhost:5432/test");
      }
      case "sql server" -> {
         ds.setDriver("com.microsoft.sqlserver.jdbc.SQLServerDriver");
         ds.setURL("jdbc:sqlserver://localhost:1433;databaseName=test");
      }
      case "oracle" -> {
         ds.setDriver("oracle.jdbc.OracleDriver");
         ds.setURL("jdbc:oracle:thin:@localhost:1521:test");
      }
      case "db2" -> {
         ds.setDriver("com.ibm.db2.jcc.DB2Driver");
         ds.setURL("jdbc:db2://localhost:50000/test");
      }
      case "derby" -> {
         ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
         ds.setURL("jdbc:derby:" + DB);
         ds.setRequireLogin(false);
      }
      default -> throw new IllegalArgumentException(product);
      }

      ds.setRuntimeProductName(product);
      Class<?> helper = switch(product) {
         case "mysql" -> MySQLHelper.class;
         case "postgresql" -> PostgreSQLHelper.class;
         case "sql server" -> SQLServerHelper.class;
         case "oracle" -> OracleSQLHelper.class;
         case "db2" -> DB2SQLHelper.class;
         default -> DerbyHelper.class;
      };
      assertEquals(helper, SQLHelper.getSQLHelper(ds).getClass(), product);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
