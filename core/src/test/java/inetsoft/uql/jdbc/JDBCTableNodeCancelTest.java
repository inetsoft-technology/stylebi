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
import inetsoft.uql.XTable;
import inetsoft.uql.util.QueryManager;
import inetsoft.uql.util.XNodeTable;
import inetsoft.util.credential.CredentialService;
import org.apache.derby.jdbc.EmbeddedDataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78200: a statement-level cancel of a JDBC query (QueryManager.cancel() of the statement,
 * e.g. the Enterprise Manager query monitor kill, or a driver that fails the read in progress)
 * ended the rows as at the end of the result, without marking the table cancelled. The rows
 * read so far were then cached and handed to later readers as the whole result. Both cancel
 * exits of JDBCTableNode.next() must leave the XNodeTable cancelled, and a cancel after the
 * last row must not.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCTableNodeCancelTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCTableNodeCancelTest {
   private static final String DB = "memory:jdbctablenodecancel";
   private static final int ROWS = 500;

   @Configuration
   static class Config {
      // JDBCDataSource reads its credential from it
      @Bean
      public CredentialService credentialService() throws Exception {
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   @BeforeAll
   static void createTable() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         try {
            stmt.executeUpdate("drop table nc");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table nc (id int, g int)");

         for(int i = 1; i <= ROWS; i++) {
            stmt.executeUpdate("insert into nc values (" + i + ", " + (i % 2) + ")");
         }
      }
   }

   @BeforeEach
   void loadInCaller() {
      // load the rows in the calling thread, so the table is complete when it is created
      SreeEnv.setProperty("replet.streaming", "false");
   }

   @AfterEach
   void reset() {
      SreeEnv.remove("replet.streaming");
   }

   /**
    * A driver that implements the cancel (Oracle ORA-01013, PostgreSQL, SQL Server) fails the
    * read in progress, and JDBCTableNode.next() ends the rows in its SQLException exit.
    */
   @Test
   void driverCancelOfTheReadMarksTheTableCancelled() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         ResultSet rs = failingAfter(stmt.executeQuery("select id, g from nc"), 30, null);
         JDBCTableNode node = node(rs, conn, stmt);
         XNodeTable table = new XNodeTable(node);
         table.moreRows(XTable.EOT);

         assertEquals(30, table.getRowCount() - table.getHeaderRowCount());
         assertTrue(node.isCanceled(), "the cancel exit was not recorded");
         assertTrue(table.isCancelled(), "the rows read before the cancel look complete");
      }
   }

   /**
    * QueryManager.cancel() of the statement (as the query monitor kill, which holds only the
    * statement) while the rows are read: next() finds the statement cancelled and stops.
    */
   @Test
   void queryManagerCancelOfTheStatementMarksTheTableCancelled() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         QueryManager qmgr = new QueryManager();
         qmgr.addPending(stmt);
         // the cancel arrives after 100 rows, Derby's own Statement.cancel() is a no-op
         ResultSet rs = failingAfter(stmt.executeQuery("select id, g from nc"), -1,
                                     () -> qmgr.cancel());
         JDBCTableNode node = node(rs, conn, stmt);
         XNodeTable table = new XNodeTable(node);
         table.moreRows(XTable.EOT);
         int rows = table.getRowCount() - table.getHeaderRowCount();

         assertTrue(rows < ROWS, "the cancel did not stop the rows: " + rows);
         assertTrue(node.isCanceled(), "the cancel was not recorded");
         assertTrue(table.isCancelled(), rows + " of " + ROWS + " rows look complete");
      }
   }

   /** The rows read to the end, then cancelled: the table is complete and not cancelled. */
   @Test
   void cancelAfterTheLastRowLeavesTheTableComplete() throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         QueryManager qmgr = new QueryManager();
         qmgr.addPending(stmt);
         JDBCTableNode node = node(stmt.executeQuery("select id, g from nc"), conn, stmt);
         XNodeTable table = new XNodeTable(node);
         table.moreRows(XTable.EOT);

         assertEquals(ROWS, table.getRowCount() - table.getHeaderRowCount());
         assertFalse(node.isCanceled());
         assertFalse(table.isCancelled());

         qmgr.cancel();
         node.cancel();
         table.cancel();

         assertFalse(node.isCanceled(), "a cancel after the last row is no cancel");
         assertFalse(table.isCancelled(), "a cancel after the last row is no cancel");
      }
   }

   private static JDBCTableNode node(ResultSet rs, Connection conn, Statement stmt)
      throws SQLException
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("nodecancel");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("ID");
      selection.addColumn("G");
      return new JDBCTableNode(rs, conn, stmt, selection, ds);
   }

   /**
    * Wrap a result set. If {@code failAt >= 0}, the read of row {@code failAt + 1} fails as a
    * driver fails a cancelled read. If {@code cancel} is set, it runs after 100 rows.
    */
   private static ResultSet failingAfter(ResultSet rs, int failAt, Runnable cancel) {
      int[] count = { 0 };
      InvocationHandler handler = (proxy, method, args) -> {
         if("next".equals(method.getName())) {
            if(failAt >= 0 && count[0] == failAt) {
               throw new SQLException("ORA-01013: user requested cancel of current operation");
            }

            if(cancel != null && count[0] == 100) {
               cancel.run();
            }

            count[0]++;
         }

         try {
            return method.invoke(rs, args);
         }
         catch(InvocationTargetException ex) {
            throw ex.getCause();
         }
      };

      return (ResultSet) Proxy.newProxyInstance(
         JDBCTableNodeCancelTest.class.getClassLoader(), new Class[] { ResultSet.class }, handler);
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
