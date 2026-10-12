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
import inetsoft.report.lens.xnode.XNodeTableLens;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.storage.BlobStorageManager;
import inetsoft.test.*;
import inetsoft.uql.*;
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.util.*;
import inetsoft.util.Plugins;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;
import java.lang.reflect.*;
import java.security.Principal;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #77788. A -- line that ends a slash-star comment opened on an earlier line is the text
 * of that comment to the database, so removing it (XUtil.clearComments) would let the comment
 * run on and hide the sql after it, such as a vpm condition. {@link JDBCHandler#execute} fails
 * such a query before any sql reaches the database. The query runs through the real
 * {@link JDBCHandler#execute} on embedded Derby, with a {@link VpmProcessor} that adds the row
 * condition to the sql string the way the enterprise VpmUtil does for sql that isn't parsed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCHandlerClosingCommentLineTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCHandlerClosingCommentLineTest {
   private static final String DB = "memory:bug77788";
   // the row condition the test vpm adds
   private static final String VPM_CONDITION = "T.A = 1";

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

      // the DriverManager in this JVM trips over another driver's static init, so the pool
      // returns a Derby data source directly
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

   @BeforeEach
   void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table T");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table T (A INT, B VARCHAR(10))");
         stmt.executeUpdate("insert into T values (1, 'x'), (2, 'y')");
      }
   }

   // a -- line that ends a slash-star comment: JDBCHandler.execute fails before any sql
   // reaches the database, for a user with and without a vpm
   @ParameterizedTest
   @ValueSource(strings = {
      "select T.A, T.B from T /* old:\n-- keep */\nwhere T.A > 0 /* end */",
      "select T.A, T.B from T where T.A > 0 /* old:\n-- see /* note */\n" +
         "union all select T.A, T.B from T /* end */",
      "select T.A, T.B from T /* x\n-- vpm.tables: T */\nwhere T.A > 0"
   })
   void closingCommentLineFailsBeforeSqlIsSent(String sql) throws Exception {
      for(Principal user : new Principal[] { null, USER }) {
         executed.set(null);
         SQLException ex = assertThrows(SQLException.class, () -> execute(sql, user));
         assertTrue(ex.getMessage().contains("ends a /* */ comment"), ex.getMessage());
         assertNull(executed.get(), "no sql may be sent");
      }
   }

   // a -- line inside a slash-star comment that doesn't end it is removed as before
   @Test
   void commentLineInsideCommentRuns() throws Exception {
      String sql = "select T.A, T.B from T /* old:\n-- keep\n*/\nwhere T.A > 0";
      XNodeTableLens table = execute(sql, USER);

      assertEquals("select T.A, T.B from T /* old:\n*/\nwhere T.A > 0 and " + VPM_CONDITION,
                   executed.get());
      assertEquals(2, rowCount(table), "header plus the one row the vpm allows");
   }

   private static XNodeTableLens execute(String sql, Principal user) throws Exception {
      UniformSQL usql = new UniformSQL();
      usql.setDataSource(dataSource());
      usql.setParseSQL(false);
      usql.setSQLString(sql, false);
      JDBCQuery query = newQuery(usql);
      VariableTable vars = new VariableTable();
      JDBCHandler handler = new JDBCHandler();
      handler.connect(query.getDataSource(), vars);
      executed.set(null);
      return new XNodeTableLens(handler.execute(query, vars, user, null));
   }

   private static int rowCount(TableLens table) {
      table.moreRows(Integer.MAX_VALUE);
      return table.getRowCount();
   }

   private static JDBCQuery newQuery(UniformSQL usql) {
      JDBCQuery query = new JDBCQuery();
      query.setName("bug77788");
      query.setDataSource(dataSource());
      query.setSQLDefinition(usql);
      return query;
   }

   private static JDBCDataSource dataSource() {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("bug77788");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      return ds;
   }

   /**
    * Rewrites the query like the enterprise VpmUtil for a user the vpm applies to:
    * applyHiddenColumns returns a clone, and applyConditions adds the row condition to the
    * sql string of a query that isn't parsed and saves it without parsing. A parsed query
    * gets no condition here, VpmUtil adds it to the structure.
    */
   private static final class TestVpmProcessor extends VpmProcessor {
      @Override
      public XQuery applyConditions(XQuery query, VariableTable vars, boolean checkVariable,
                                    Principal user)
      {
         if(user == null) {
            return query;
         }

         JDBCQuery clone = (JDBCQuery) query.clone();
         UniformSQL usql = (UniformSQL) clone.getSQLDefinition();

         if(!XUtil.isParsedSQL(usql)) {
            usql.setSQLString(usql.getSQLString() + " and " + VPM_CONDITION, false);
         }

         return clone;
      }

      @Override
      public XQuery applyHiddenColumns(XQuery query, VariableTable vars, Principal user) {
         return user == null ? query : (XQuery) query.clone();
      }
   }

   private static Field vpmProcessorField() throws Exception {
      Field field = VpmProcessor.class.getDeclaredField("processor");
      field.setAccessible(true);
      return field;
   }

   @BeforeAll
   static void installVpmProcessor() throws Exception {
      Field field = vpmProcessorField();
      oldVpmProcessor = field.get(null);
      field.set(null, new TestVpmProcessor());
   }

   @AfterAll
   static void restoreVpmProcessor() throws Exception {
      vpmProcessorField().set(null, oldVpmProcessor);
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
         JDBCHandlerClosingCommentLineTest.class.getClassLoader(), new Class<?>[] { type },
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

   private static final Principal USER = new XPrincipal(new IdentityID("bug77788", "host-org"));
   private static final ThreadLocal<String> executed = new ThreadLocal<>();
   private static Object oldVpmProcessor;
}
