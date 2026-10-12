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
package inetsoft.report.composition.execution;

import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.SQLBoundTableAssemblyInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.jdbc.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
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
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77606, the reported path. A SQL bound worksheet table whose FROM is a derived table
 * with a parameter in its HAVING is run with EMPTY_STRING, then 'n1', then EMPTY_STRING, each
 * run with a new AssetQuerySandbox and SQLBoundQuery. The SQLBoundQuery merges into the
 * table's own query (which clears its sql string) and JDBCHandler executes a clone of it, so
 * the first sentinel rewrite of the derived table used to stay in the table's query.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  SQLBoundQueryDerivedSentinelTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLBoundQueryDerivedSentinelTest {
   private static final String DB = "memory:bug77606sqlbound";
   private static final String EMPTY_STRING = XConstants.CONDITION_EMPTY_STRING;
   // min(a.name) is '' in every group
   private static final String SQL =
      "select t.k from (select a.k, min(a.name) m from a group by a.k " +
      "having min(a.name) = $(p)) t";

   @Configuration
   static class JdbcConfig {
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

      @Bean
      public XRepository xRepository() {
         return mock(XRepository.class);
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table a");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table a (id int, k int, name varchar(20))");
         stmt.executeUpdate("insert into a values (1, 1, 'n1'), (null, 1, ''), (3, 2, null), " +
                               "(null, 2, 'null'), (5, 1, 'null'), (6, 1, null), " +
                               "(null, 3, ''), (null, 3, ''), (9, 2, '')");
      }
   }

   @Test
   void sentinelThenValueOnSqlBoundTable() throws Exception {
      Worksheet ws = new Worksheet();
      SQLBoundTableAssembly table = newTable(ws);
      JDBCQuery own = ((SQLBoundTableAssemblyInfo) table.getTableInfo()).getQuery();
      String[] values = { EMPTY_STRING, "n1", EMPTY_STRING };
      List<List<Integer>> expected = List.of(List.of(1, 2, 3), List.of(), List.of(1, 2, 3));

      for(int i = 0; i < values.length; i++) {
         VariableTable vars = new VariableTable();
         vars.put("p", values[i]);
         SQLBoundQuery query = new SQLBoundQuery(
            AssetQuerySandbox.RUNTIME_MODE, new AssetQuerySandbox(ws), table, false, false);
         query.merge(vars);

         // the query that runs is the table's own query, with its sql string cleared
         assertSame(own, query.getQuery());
         assertFalse(((UniformSQL) own.getSQLDefinition()).hasSQLString(),
                     "merge did not clear the sql string, validation would be skipped");

         assertEquals(expected.get(i), execute(query.getQuery(), vars), "p=" + values[i]);
      }

      String derived = ((UniformSQL) own.getSQLDefinition()).getSelectTable()[0].getName()
         .toString();
      assertTrue(derived.contains("$(p)"), derived);
   }

   private static SQLBoundTableAssembly newTable(Worksheet ws) throws Exception {
      UniformSQL usql = new UniformSQL();
      Method parse = UniformSQL.class.getDeclaredMethod("parse", String.class, int.class,
                                                        long.class);
      parse.setAccessible(true);
      // parse(String, int, long) is package private, PARSE_ALL is 0
      parse.invoke(usql, SQL, 0, 4000L);
      usql.setSQLString(SQL, false);
      assertEquals(UniformSQL.PARSE_SUCCESS, usql.getParseResult(), SQL);
      assertFalse(usql.isLossy(), SQL);

      JDBCDataSource ds = dataSource();
      usql.setDataSource(ds);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77606");
      query.setUserQuery(true);
      query.setDataSource(ds);
      query.setSQLDefinition(usql);

      SQLBoundTableAssembly table = new SQLBoundTableAssembly(ws, "T1");
      SQLBoundTableAssemblyInfo info = (SQLBoundTableAssemblyInfo) table.getTableInfo();
      info.setQuery(query);
      info.setSourceInfo(new SourceInfo(SourceInfo.DATASOURCE, ds.getName(), ds.getName()));
      table.setSQLEdited(true);

      ColumnSelection columns = new ColumnSelection();
      ColumnRef column = new ColumnRef(new AttributeRef(null, "k"));
      column.setDataType(XSchema.INTEGER);
      columns.addAttribute(column);
      table.setColumnSelection(columns, false);
      table.setColumnSelection(columns, true);
      ws.addAssembly(table);
      return table;
   }

   // what XSessionManager.getXNodeTableLens does with the merged query
   private static List<Integer> execute(JDBCQuery query, VariableTable vars) throws Exception {
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      XNode node = handler.execute(query, vars, null, null);
      XNodeTableLens lens = new XNodeTableLens(node);
      lens.moreRows(Integer.MAX_VALUE);
      List<Integer> keys = new ArrayList<>();

      for(int r = 1; r < lens.getRowCount(); r++) {
         keys.add(((Number) lens.getObject(r, 0)).intValue());
      }

      Collections.sort(keys);
      return keys;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77606sqlbound");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   private static DataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
