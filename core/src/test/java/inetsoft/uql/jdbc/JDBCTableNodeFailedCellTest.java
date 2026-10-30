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
 * Bug #79484: when the driver failed to read one cell (e.g. MySQL zero date), JDBCTableNode
 * logged it and moved on, but its row buffer is reused across rows, so the cell kept the value of
 * the previous row. A failed cell must be null, whatever the column type and whichever row it is.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  JDBCTableNodeFailedCellTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class JDBCTableNodeFailedCellTest {
   private static final String DB = "memory:jdbctablenodefailedcell";

   @Configuration
   static class Config {
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
            stmt.executeUpdate("drop table fc");
         }
         catch(Exception ignore) {
            // first run
         }

         stmt.executeUpdate("create table fc (id int, d timestamp, amt decimal(10,2), " +
                            "b varchar(10) for bit data)");

         for(int i = 1; i <= 4; i++) {
            stmt.executeUpdate("insert into fc values (" + i + ", '202" + i + "-01-01 00:00:00', " +
                               i * 10 + ".50, X'0" + i + "')");
         }
      }
   }

   @BeforeEach
   void loadInCaller() {
      SreeEnv.setProperty("replet.streaming", "false");
   }

   @AfterEach
   void reset() {
      SreeEnv.remove("replet.streaming");
   }

   @Test
   void noFailureReadsEveryCell() throws Exception {
      Object[][] rows = read(0, 0);

      for(int r = 0; r < 4; r++) {
         assertNotNull(rows[r][1], "row " + (r + 1));
         assertNotNull(rows[r][2], "row " + (r + 1));
         assertNotNull(rows[r][3], "row " + (r + 1));
      }

      assertNotEquals(rows[1][1], rows[2][1]);
   }

   @Test
   void failedCellOfALaterRowIsNullNotThePreviousRowsValue() throws Exception {
      Object[][] rows = read(2, 3);

      assertNotNull(rows[1][2]);
      assertNull(rows[2][2], "row 3 amount shows row 2's value " + rows[2][2]);
      // the other cells of the row and the next row are intact
      assertNotNull(rows[2][1]);
      assertNotNull(rows[3][2]);
      assertNotEquals(rows[1][2], rows[3][2]);
   }

   @Test
   void failedDateCellOfALaterRowIsNull() throws Exception {
      Object[][] rows = read(1, 3);

      assertNotNull(rows[1][1]);
      assertNull(rows[2][1], "row 3 date shows row 2's value " + rows[2][1]);
      assertNotNull(rows[2][2]);
   }

   @Test
   void failedBinaryCellOfALaterRowIsNull() throws Exception {
      Object[][] rows = read(3, 3);

      assertNotNull(rows[1][3]);
      assertNull(rows[2][3], "row 3 binary shows row 2's value");
      assertNotNull(rows[3][3]);
   }

   @Test
   void failedCellOfTheFirstRowIsNull() throws Exception {
      Object[][] rows = read(1, 1);

      assertNull(rows[0][1]);
      assertNotNull(rows[1][1]);
   }

   /** Read all rows through XNodeTable; the driver fails column {@code col} on row {@code row}. */
   private static Object[][] read(int col, int row) throws Exception {
      try(Connection conn = derby().getConnection(); Statement stmt = conn.createStatement()) {
         ResultSet rs = failing(stmt.executeQuery("select id, d, amt, b from fc order by id"),
                                col, row);
         XNodeTable table = new XNodeTable(node(rs, conn, stmt));
         table.moreRows(XTable.EOT);
         int header = table.getHeaderRowCount();
         assertEquals(4, table.getRowCount() - header);
         Object[][] rows = new Object[4][4];

         for(int r = 0; r < 4; r++) {
            for(int c = 0; c < 4; c++) {
               rows[r][c] = table.getObject(r + header, c);
            }
         }

         return rows;
      }
   }

   private static JDBCTableNode node(ResultSet rs, Connection conn, Statement stmt)
      throws SQLException
   {
      JDBCDataSource ds = new JDBCDataSource();
      ds.setName("nodefailedcell");
      ds.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      ds.setURL("jdbc:derby:" + DB);
      ds.setRequireLogin(false);
      JDBCSelection selection = new JDBCSelection();
      selection.addColumn("ID");
      selection.addColumn("D");
      selection.addColumn("AMT");
      selection.addColumn("B");
      return new JDBCTableNode(rs, conn, stmt, selection, ds);
   }

   /**
    * Wrap a result set so that every getter of 1-based column {@code col + 1} throws on the
    * {@code row}-th row (1-based); {@code col == 0} or {@code row == 0} means never.
    */
   private static ResultSet failing(ResultSet rs, int col, int row) {
      int[] count = { 0 };
      InvocationHandler handler = (proxy, method, args) -> {
         String name = method.getName();

         if("next".equals(name)) {
            count[0]++;
         }
         else if(name.startsWith("get") && args != null && args.length > 0 &&
                 args[0] instanceof Integer && col > 0 && count[0] == row &&
                 (Integer) args[0] == col + 1)
         {
            throw new SQLException("Value '0000-00-00' can not be represented");
         }

         try {
            return method.invoke(rs, args);
         }
         catch(InvocationTargetException ex) {
            throw ex.getCause();
         }
      };

      return (ResultSet) Proxy.newProxyInstance(
         JDBCTableNodeFailedCellTest.class.getClassLoader(), new Class[] { ResultSet.class },
         handler);
   }

   private static EmbeddedDataSource derby() {
      EmbeddedDataSource ds = new EmbeddedDataSource();
      ds.setDatabaseName(DB);
      ds.setCreateDatabase("create");
      return ds;
   }
}
