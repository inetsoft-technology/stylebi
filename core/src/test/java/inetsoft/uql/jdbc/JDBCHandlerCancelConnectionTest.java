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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.util.*;
import inetsoft.util.CancelledException;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82529. A query cancelled after {@link JDBCHandler#execute} handed its connection to a
 * table node must close that node, so the pooled connection is returned (and the pool rolls
 * back the cancelled transaction) at once instead of when the node is finalized.
 * The connections come from a counting proxy over embedded Derby. Derby doesn't fail a
 * cancelled statement, so the proxy does what PostgreSQL does: the statement is cancelled
 * through the query's {@link QueryManager} and fails with SQLState 57014.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCHandlerCancelConnectionTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCHandlerCancelConnectionTest {
   private static final String DB = "memory:bug82529";
   // the statement is cancelled while it executes
   private static final String CANCEL_IN_EXECUTE = "select T.A from T where 1 = 1";
   // the cancel lands after the result set is returned, while the node reads its metadata
   private static final String CANCEL_IN_NODE = "select T.A from T where 2 = 2";

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

      @Bean
      public ConnectionPoolFactory connectionPoolFactory() {
         DataSource ds = counting(derby());
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

   @BeforeEach
   void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table T");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table T (A INT)");
         stmt.executeUpdate("insert into T values (1), (2)");
      }

      open.set(0);
   }

   // the cancel of a statement still in execute (PostgreSQL pg_sleep cancelled by Stop or
   // close) leaves no connection borrowed
   @Test
   void cancelDuringExecuteReturnsTheConnection() throws Exception {
      for(int i = 0; i < 3; i++) {
         assertThrows(CancelledException.class, () -> execute(CANCEL_IN_EXECUTE));
         assertEquals(0, open.get(), "borrowed connections after cancel " + (i + 1));
      }
   }

   // a cancel after the node took the connection (with a result set) also returns it
   @Test
   void cancelAfterTheNodeTookTheConnectionReturnsIt() throws Exception {
      assertThrows(CancelledException.class, () -> execute(CANCEL_IN_NODE));
      assertEquals(0, open.get(), "borrowed connections after cancel");
   }

   // control: a query that is not cancelled keeps its connection open in the returned node
   // until the node is closed
   @Test
   void uncancelledQueryKeepsTheConnectionUntilTheNodeIsClosed() throws Exception {
      XNode node = execute("select T.A from T");
      assertInstanceOf(JDBCTableNode.class, node);
      assertEquals(1, open.get(), "the returned node holds the connection");

      ((JDBCTableNode) node).close();
      assertEquals(0, open.get(), "closing the node returns the connection");
   }

   private static XNode execute(String sql) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource());
      usql.setParseSQL(false);
      usql.setSQLString(sql, false);
      JDBCQuery query = new JDBCQuery();
      query.setName("bug82529");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      QueryManager qmgr = new QueryManager();
      query.setProperty("queryManager", qmgr);
      VariableTable vars = new VariableTable();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      queryManager.set(qmgr);

      try {
         return handler.execute(query, vars, null, null);
      }
      finally {
         queryManager.remove();
      }
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug82529");
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

   /**
    * A data source that counts the connections it lent and that are not closed yet.
    */
   private static DataSource counting(DataSource ds) {
      return proxy(DataSource.class, ds, (method, args, target) -> {
         Object result = invoke(method, target, args);

         if(result instanceof Connection) {
            open.incrementAndGet();
            return connection((Connection) result);
         }

         return result;
      });
   }

   private static Connection connection(Connection conn) {
      boolean[] closed = { false };

      return proxy(Connection.class, conn, (method, args, target) -> {
         if("close".equals(method.getName()) && !closed[0]) {
            closed[0] = true;
            open.decrementAndGet();
         }

         Object result = invoke(method, target, args);

         if(result instanceof Statement && !(result instanceof PreparedStatement)) {
            return statement((Statement) result);
         }

         return result;
      });
   }

   private static Statement statement(Statement stmt) {
      return proxy(Statement.class, stmt, new Handler() {
         @Override
         public Object handle(Method method, Object[] args, Object target) throws Throwable {
            String name = method.getName();

            if("execute".equals(name) && args != null && CANCEL_IN_EXECUTE.equals(args[0])) {
               // the statement is cancelled while it runs and fails like PostgreSQL's
               queryManager.get().cancel();
               cancelled = true;
               throw new SQLException("canceling statement due to user request", "57014");
            }

            if(cancelled && "getUpdateCount".equals(name)) {
               return -1;
            }

            if(cancelled && "getResultSet".equals(name)) {
               return null;
            }

            Object result = invoke(method, target, args);

            if(result instanceof ResultSet && "getResultSet".equals(name) &&
               CANCEL_IN_NODE.equals(sql))
            {
               return resultSet((ResultSet) result);
            }

            if("execute".equals(name) && args != null && args[0] instanceof String) {
               sql = (String) args[0];
            }

            return result;
         }

         private boolean cancelled;
         private String sql;
      });
   }

   // a result set whose statement is cancelled when the node reads its metadata
   private static ResultSet resultSet(ResultSet rs) {
      return proxy(ResultSet.class, rs, (method, args, target) -> {
         if("getMetaData".equals(method.getName())) {
            queryManager.get().cancel();
         }

         return invoke(method, target, args);
      });
   }

   private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
      try {
         return method.invoke(target, args);
      }
      catch(InvocationTargetException e) {
         throw e.getCause();
      }
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(Class<T> type, T target, Handler handler) {
      return (T) Proxy.newProxyInstance(
         JDBCHandlerCancelConnectionTest.class.getClassLoader(), new Class<?>[] { type },
         (p, method, args) -> handler.handle(method, args, target));
   }

   private interface Handler {
      Object handle(Method method, Object[] args, Object target) throws Throwable;
   }

   private static final AtomicInteger open = new AtomicInteger();
   private static final ThreadLocal<QueryManager> queryManager = new ThreadLocal<>();
}
