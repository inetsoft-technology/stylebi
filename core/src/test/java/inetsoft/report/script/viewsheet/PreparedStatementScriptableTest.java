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
package inetsoft.report.script.viewsheet;

import inetsoft.uql.script.XTableArray;
import inetsoft.uql.script.XTableRow;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.test.*;
import inetsoft.util.credential.CredentialService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The prepared statement of a database script ({@code db.prepareStatement(sql)}): every setter binds a parameter, the
 * execute methods run the statement, and a number or date argument that is of the wrong type is refused. The
 * statements run against an in-memory Derby database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  PreparedStatementScriptableTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class PreparedStatementScriptableTest {
   /** A JDBCDataSource reads its credentials through the credential service. */
   @Configuration
   static class JdbcConfig {
      @Bean
      public CredentialService credentialService() throws Exception {
         // package-private constructor, normally reached by component scanning
         Constructor<CredentialService> ctor = CredentialService.class.getDeclaredConstructor();
         ctor.setAccessible(true);
         return ctor.newInstance();
      }
   }

   private Connection conn;
   private String url;
   private JDBCDataSource source;

   @BeforeEach
   void setUp() throws Exception {
      url = "jdbc:derby:memory:ps" + UUID.randomUUID().toString().replace("-", "");
      conn = DriverManager.getConnection(url + ";create=true");

      try(Statement st = conn.createStatement()) {
         st.execute("CREATE TABLE T(ID INT, S VARCHAR(30), D DOUBLE, B BOOLEAN, N DECIMAL(12,3), " +
                    "L BIGINT, SH SMALLINT, BT SMALLINT, F REAL, DT DATE, TM TIME, TS TIMESTAMP, BIN VARCHAR(64) FOR BIT DATA)");
      }

      source = new JDBCDataSource();
      source.setName("h2test");
   }

   @AfterEach
   void tearDown() throws Exception {
      conn.close();

      try {
         DriverManager.getConnection(url + ";drop=true");
      }
      catch(SQLException expected) {
         // Derby reports the dropped database as an exception
      }
   }

   private PreparedStatementScriptable prepare(String sql) throws Throwable {
      return new PreparedStatementScriptable(sql, conn, source);
   }

   private Object scalar(String sql) throws Exception {
      try(Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
         rs.next();
         return rs.getObject(1);
      }
   }

   private void insertRow() throws Throwable {
      prepare("INSERT INTO T(ID) VALUES (1)").executeUpdate();
   }

   @Test
   void classNameAndTheFunctionsItOffers() throws Throwable {
      PreparedStatementScriptable ps = prepare("VALUES 1");

      assertEquals("PreparedStatementScriptable", ps.getClassName());

      for(String name : new String[] { "addBatch", "clearParameters", "execute", "executeBatch", "executeQuery",
                                       "executeUpdate", "setBigDecimal", "setBoolean", "setByte", "setDate",
                                       "setDouble", "setFloat", "setInt", "setLong", "setNull", "setObject",
                                       "setShort", "setString", "setTime", "setTimestamp", "setBinaryStream" })
      {
         assertTrue(ps.hasMember(name), name);
         assertNotNull(ps.getMember(name), name);
      }

      assertFalse(ps.hasMember("close"));
      assertNull(ps.getMember("close"));
      assertEquals(21, ps.getMemberKeys().length);
   }

   @Test
   void putMemberAddsAMember() throws Throwable {
      PreparedStatementScriptable ps = prepare("VALUES 1");

      ps.putMember("custom", 5);

      assertEquals(5, ps.getMember("custom"));
      assertTrue(ps.hasMember("custom"));
   }

   @Test
   void aBadStatementFailsWhenItIsPrepared() {
      assertThrows(SQLException.class, () -> prepare("SELEKT"));
   }

   @Test
   void numbersAndTextAreBound() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(ID, S, D, B, N, L, SH, BT, F) VALUES (?,?,?,?,?,?,?,?,?)");
      ps.setInt(1, 7);
      ps.setString(2, "abc");
      ps.setDouble(3, 2.5);
      ps.setBoolean(4, true);
      ps.setBigDecimal(5, 12.345);
      ps.setLong(6, 9000000000L);
      ps.setShort(7, 12);
      ps.setByte(8, 3);
      ps.setFloat(9, 1.5);

      assertEquals(1, ps.executeUpdate());

      assertEquals(7, scalar("SELECT ID FROM T"));
      assertEquals("abc", scalar("SELECT S FROM T"));
      assertEquals(2.5, scalar("SELECT D FROM T"));
      assertEquals(Boolean.TRUE, scalar("SELECT B FROM T"));
      assertEquals(0, new BigDecimal("12.345").compareTo((BigDecimal) scalar("SELECT N FROM T")));
      assertEquals(9000000000L, scalar("SELECT L FROM T"));
      assertEquals(12, ((Number) scalar("SELECT SH FROM T")).intValue());
      assertEquals(3, ((Number) scalar("SELECT BT FROM T")).intValue());
      assertEquals(1.5f, scalar("SELECT F FROM T"));
   }

   @Test
   void numbersCanBeGivenAsAnyNumberType() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(L, SH) VALUES (?,?)");
      ps.setLong(1, 5);
      ps.setShort(2, 6L);
      ps.executeUpdate();

      assertEquals(5L, scalar("SELECT L FROM T"));
      assertEquals(6, ((Number) scalar("SELECT SH FROM T")).intValue());
   }

   @Test
   void aValueThatIsNotANumberIsRefused() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(L, SH, BT, F, N) VALUES (?,?,?,?,?)");

      for(int i = 1; i <= 5; i++) {
         final int index = i;
         RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            switch(index) {
            case 1 -> ps.setLong(1, "x");
            case 2 -> ps.setShort(2, "x");
            case 3 -> ps.setByte(3, "x");
            case 4 -> ps.setFloat(4, "x");
            default -> ps.setBigDecimal(5, "x");
            }
         });

         assertTrue(ex.getMessage().contains("Invalid number object found"), ex.getMessage());
      }
   }

   @Test
   void datesAreBoundAsDateTimeAndTimestamp() throws Throwable {
      java.util.Date moment = new java.util.Date(Timestamp.valueOf("2024-03-05 10:20:30").getTime());
      PreparedStatementScriptable ps = prepare("INSERT INTO T(DT, TM, TS) VALUES (?,?,?)");
      ps.setDate(1, moment);
      ps.setTime(2, moment);
      ps.setTimestamp(3, moment);
      ps.executeUpdate();

      assertEquals("2024-03-05", String.valueOf(scalar("SELECT DT FROM T")));
      assertEquals("10:20:30", String.valueOf(scalar("SELECT TM FROM T")));
      assertEquals("2024-03-05 10:20:30.0", String.valueOf(scalar("SELECT TS FROM T")));
   }

   @Test
   void aValueThatIsNotADateIsRefused() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(DT, TM, TS) VALUES (?,?,?)");

      assertTrue(assertThrows(RuntimeException.class, () -> ps.setDate(1, "2024-03-05")).getMessage()
                    .contains("Invalid date found"));
      assertTrue(assertThrows(RuntimeException.class, () -> ps.setTime(2, 12)).getMessage()
                    .contains("Invalid date found"));
      assertTrue(assertThrows(RuntimeException.class, () -> ps.setTimestamp(3, null)).getMessage()
                    .contains("Invalid date found"));
   }

   @Test
   void nullAndObjectParametersAreBound() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(ID, S, D, N) VALUES (?,?,?,?)");
      ps.setObject(1, 3);
      ps.setNull(2, Types.VARCHAR);
      ps.setObject(3, "4.5", Types.DOUBLE);
      ps.setObject(4, 1.23456, Types.DECIMAL, 2);
      ps.executeUpdate();

      assertEquals(3, scalar("SELECT ID FROM T"));
      assertNull(scalar("SELECT S FROM T"));
      assertEquals(4.5, scalar("SELECT D FROM T"));
      assertEquals(0, new BigDecimal("1.23").compareTo((BigDecimal) scalar("SELECT N FROM T")));
   }

   @Test
   void aNamedNullTypeIsPassedToTheDriver() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(S) VALUES (?)");

      assertDoesNotThrow(() -> ps.setNull(1, Types.VARCHAR, "VARCHAR"));
      ps.executeUpdate();
      assertNull(scalar("SELECT S FROM T"));
   }

   @Test
   void aBinaryStreamIsTheUtf8BytesOfTheText() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(BIN) VALUES (?)");
      ps.setBinaryStream(1, "héllo");
      ps.executeUpdate();

      byte[] stored = (byte[]) scalar("SELECT BIN FROM T");
      byte[] expected = "héllo".getBytes(java.nio.charset.StandardCharsets.UTF_8);

      assertArrayEquals(expected, java.util.Arrays.copyOf(stored, expected.length));
   }

   @Test
   void aParameterIndexOutsideTheStatementFails() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(ID) VALUES (?)");

      assertThrows(SQLException.class, () -> ps.setInt(5, 1));
      assertThrows(SQLException.class, () -> ps.setString(0, "x"));
   }

   @Test
   void clearParametersForgetsTheBoundValues() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(ID) VALUES (?)");
      ps.setInt(1, 5);
      ps.clearParameters();

      assertThrows(SQLException.class, ps::executeUpdate);
   }

   @Test
   void executeQueryGivesATableArray() throws Throwable {
      insertRow();
      prepare("INSERT INTO T(ID, S) VALUES (2, 'two')").executeUpdate();

      PreparedStatementScriptable ps = prepare("SELECT ID, S FROM T WHERE ID >= ? ORDER BY ID");
      ps.setInt(1, 2);

      Object result = ps.executeQuery();

      assertInstanceOf(XTableArray.class, result);
      XTableArray table = (XTableArray) result;
      // row 0 is the header row, then the one row with ID >= 2
      assertEquals(2L, table.getArraySize());
      assertEquals(2, table.getMember("size"));
      XTableRow row = (XTableRow) table.getArrayElement(1);
      assertEquals("two", String.valueOf(row.getArrayElement(1)));
   }

   @Test
   void executeReturnsWhetherThereIsAResultSet() throws Throwable {
      assertEquals(Boolean.TRUE, prepare("VALUES 1").execute());
      assertEquals(Boolean.FALSE, prepare("INSERT INTO T(ID) VALUES (9)").execute());
      assertEquals(9, scalar("SELECT ID FROM T"));
   }

   @Test
   void batchesRunAllAddedParameterSets() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(ID) VALUES (?)");

      for(int i = 1; i <= 3; i++) {
         ps.setInt(1, i);
         ps.addBatch();
      }

      int[] counts = (int[]) ps.executeBatch();

      assertArrayEquals(new int[] { 1, 1, 1 }, counts);
      assertEquals(3, scalar("SELECT COUNT(*) FROM T"));
   }

   @Test
   void executeUpdateReturnsTheNumberOfChangedRows() throws Throwable {
      insertRow();
      insertRow();

      assertEquals(2, prepare("UPDATE T SET S = 'x'").executeUpdate());
   }

   @Test
   void anErrorOfTheDriverIsRethrownNotSwallowed() throws Throwable {
      PreparedStatementScriptable ps = prepare("INSERT INTO T(ID) VALUES (?)");
      assertThrows(SQLException.class, () -> ps.setString(1, "not a number"));
      ps.setInt(1, 1);
      prepare("INSERT INTO T(ID) VALUES (1)").executeUpdate();

      assertDoesNotThrow(ps::executeUpdate);
      assertThrows(SQLException.class, () -> prepare("SELECT * FROM NO_SUCH_TABLE"));
   }
}
