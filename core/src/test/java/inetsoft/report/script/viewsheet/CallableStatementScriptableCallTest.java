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

import inetsoft.test.*;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.script.XTableArray;
import inetsoft.util.credential.CredentialService;
import inetsoft.util.script.graal.GraalJavaScriptEngine;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.sql.*;
import java.util.HashMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The callable statement of a database script ({@code db.prepareCall(sql)}): parameters are given by position or by
 * name, out parameters are registered and read by the typed getters. The calls run against stored procedures of an
 * in-memory Derby database.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class,
                                  CallableStatementScriptableCallTest.JdbcConfig.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("slow")
class CallableStatementScriptableCallTest {
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

   /** The bodies of the stored procedures; Derby needs a public class with public static methods. */
   public static final class Procs {
      public static void addOne(int a, int[] out) {
         out[0] = a + 1;
      }

      public static void echo(String in, String[] out) {
         out[0] = in == null ? null : in.toUpperCase();
      }

      public static void numbers(double[] d, long[] l, short[] s, float[] f, boolean[] b) {
         d[0] = 2.5;
         l[0] = 9000000000L;
         s[0] = 7;
         f[0] = 1.5f;
         b[0] = true;
      }

      public static void nothing(Integer[] out) {
         out[0] = null;
      }

      public static void dates(Date[] d, Time[] t, Timestamp[] ts) {
         d[0] = Date.valueOf("2024-03-05");
         t[0] = Time.valueOf("10:20:30");
         ts[0] = Timestamp.valueOf("2024-03-05 10:20:30");
      }

      public static void rows(ResultSet[] rs) throws SQLException {
         Connection c = DriverManager.getConnection("jdbc:default:connection");
         rs[0] = c.createStatement().executeQuery("VALUES (1, 'a'), (2, 'b')");
      }
   }

   private Connection conn;
   private String url;

   @BeforeEach
   void setUp() throws Exception {
      url = "jdbc:derby:memory:cs" + UUID.randomUUID().toString().replace("-", "");
      conn = DriverManager.getConnection(url + ";create=true");
      String cls = Procs.class.getName();

      try(Statement st = conn.createStatement()) {
         st.execute("CREATE PROCEDURE ADD_ONE(IN A INT, OUT B INT) LANGUAGE JAVA PARAMETER STYLE JAVA NO SQL " +
                    "EXTERNAL NAME '" + cls + ".addOne'");
         st.execute("CREATE PROCEDURE ECHO_UP(IN A VARCHAR(30), OUT B VARCHAR(30)) LANGUAGE JAVA " +
                    "PARAMETER STYLE JAVA NO SQL EXTERNAL NAME '" + cls + ".echo'");
         st.execute("CREATE PROCEDURE NUMS(OUT D DOUBLE, OUT L BIGINT, OUT S SMALLINT, OUT F REAL, " +
                    "OUT B BOOLEAN) LANGUAGE JAVA PARAMETER STYLE JAVA NO SQL EXTERNAL NAME '" + cls + ".numbers'");
         st.execute("CREATE PROCEDURE NOTHING(OUT B INT) LANGUAGE JAVA PARAMETER STYLE JAVA NO SQL " +
                    "EXTERNAL NAME '" + cls + ".nothing'");
         st.execute("CREATE PROCEDURE DATES(OUT D DATE, OUT T TIME, OUT TS TIMESTAMP) LANGUAGE JAVA " +
                    "PARAMETER STYLE JAVA NO SQL EXTERNAL NAME '" + cls + ".dates'");
         st.execute("CREATE PROCEDURE LIST_ROWS() LANGUAGE JAVA PARAMETER STYLE JAVA READS SQL DATA " +
                    "DYNAMIC RESULT SETS 1 EXTERNAL NAME '" + cls + ".rows'");
      }
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

   private CallableStatementScriptable call(String sql) throws Throwable {
      return new CallableStatementScriptable(sql, conn, new JDBCDataSource());
   }

   @Test
   void classNameAndTheFunctionsItOffers() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");

      assertEquals("CallableStatementScriptable", cs.getClassName());

      for(String name : new String[] { "execute", "executeQuery", "executeUpdate", "registerOutParameter",
                                       "getInt", "getString", "getDate", "setInt", "setString", "setNull",
                                       "setObject", "wasNull", "clearParameters" })
      {
         assertTrue(cs.hasMember(name), name);
      }

      assertFalse(cs.hasMember("close"));
      assertNull(cs.getMember("nothing"));
      cs.putMember("custom", 1);
      assertEquals(1, cs.getMember("custom"));
   }

   @Test
   void anInParameterIsBoundAndAnOutParameterIsRead() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");
      cs.setInt(1, 41);
      cs.registerOutParameter(2, Types.INTEGER, null);
      cs.execute();

      assertEquals(42, cs.getInt(2));
      assertEquals(42L, cs.getLong(2));
      assertEquals(42, cs.getShort(2));
      assertEquals(42.0, cs.getDouble(2));
      assertEquals("42", cs.getString(2));
      assertEquals(42, cs.getObject(2));
      assertFalse(cs.wasNull());
   }

   @Test
   void positionsCanBeAnyNumberType() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");
      cs.setInt(1L, 1);
      cs.registerOutParameter(2.0, Types.INTEGER, null);
      cs.execute();

      assertEquals(2, cs.getInt(2.0));
   }

   @Test
   void textParametersAreBoundAndRead() throws Throwable {
      CallableStatementScriptable cs = call("{call ECHO_UP(?, ?)}");
      cs.setString(1, "abc");
      cs.registerOutParameter(2, Types.VARCHAR, null);
      cs.execute();

      assertEquals("ABC", cs.getString(2));
   }

   @Test
   void aNullInParameterGivesANullOutParameter() throws Throwable {
      CallableStatementScriptable cs = call("{call ECHO_UP(?, ?)}");
      cs.setNull(1, Types.VARCHAR);
      cs.registerOutParameter(2, Types.VARCHAR, null);
      cs.execute();

      assertNull(cs.getString(2));
      assertTrue(cs.wasNull());
   }

   @Test
   void everyTypedGetterReadsItsOutParameter() throws Throwable {
      CallableStatementScriptable cs = call("{call NUMS(?, ?, ?, ?, ?)}");
      cs.registerOutParameter(1, Types.DOUBLE, null);
      cs.registerOutParameter(2, Types.BIGINT, null);
      cs.registerOutParameter(3, Types.SMALLINT, null);
      cs.registerOutParameter(4, Types.REAL, null);
      cs.registerOutParameter(5, Types.BOOLEAN, null);
      cs.execute();

      assertEquals(2.5, cs.getDouble(1));
      assertEquals(9000000000L, cs.getLong(2));
      assertEquals((short) 7, cs.getShort(3));
      assertEquals(1.5f, cs.getFloat(4));
      assertTrue(cs.getBoolean(5));
      assertEquals(0, new java.math.BigDecimal("2.5").compareTo(cs.getBigDecimal(1)));
      assertEquals((byte) 7, cs.getByte(3));
   }

   @Test
   void dateOutParametersAreRead() throws Throwable {
      CallableStatementScriptable cs = call("{call DATES(?, ?, ?)}");
      cs.registerOutParameter(1, Types.DATE, null);
      cs.registerOutParameter(2, Types.TIME, null);
      cs.registerOutParameter(3, Types.TIMESTAMP, null);
      cs.execute();

      assertEquals("2024-03-05", cs.getDate(1).toString());
      assertEquals("10:20:30", cs.getTime(2).toString());
      assertEquals("2024-03-05 10:20:30.0", cs.getTimestamp(3).toString());
   }

   @Test
   void aNullOutParameterIsReportedByWasNull() throws Throwable {
      CallableStatementScriptable cs = call("{call NOTHING(?)}");
      cs.registerOutParameter(1, Types.INTEGER, null);
      cs.execute();

      assertEquals(0, cs.getInt(1));
      assertTrue(cs.wasNull());
   }

   @Test
   void executeQueryGivesTheResultSetOfTheProcedure() throws Throwable {
      CallableStatementScriptable cs = call("{call LIST_ROWS()}");

      Object result = cs.executeQuery();

      assertInstanceOf(XTableArray.class, result);
      // row 0 is the header row
      assertEquals(3L, ((XTableArray) result).getArraySize());
   }

   @Test
   void executeUpdateRunsTheCall() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");
      cs.setInt(1, 1);
      cs.registerOutParameter(2, Types.INTEGER, null);

      assertDoesNotThrow(cs::executeUpdate);
      assertEquals(2, cs.getInt(2));
   }

   @Test
   void settersAcceptTheirNumberAndDateArguments() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");

      assertDoesNotThrow(() -> cs.setLong(1, 5));
      assertDoesNotThrow(() -> cs.setShort(1, 5));
      assertDoesNotThrow(() -> cs.setByte(1, 5));
      assertDoesNotThrow(() -> cs.setFloat(1, 5.0));
      assertDoesNotThrow(() -> cs.setDouble(1, 5.0));
      assertDoesNotThrow(() -> cs.setBigDecimal(1, 5));
      assertDoesNotThrow(() -> cs.setBoolean(1, true));
      assertDoesNotThrow(() -> cs.setObject(1, 5));
      assertDoesNotThrow(() -> cs.setObject(1, 5, Types.INTEGER));
      assertDoesNotThrow(() -> cs.setObject(1, 5, Types.INTEGER, 0));
      assertDoesNotThrow(() -> cs.setNull(1, Types.INTEGER, null));
      assertDoesNotThrow(() -> cs.clearParameters());
   }

   @Test
   void aValueThatIsNotANumberOrDateIsRefused() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");

      assertTrue(assertThrows(RuntimeException.class, () -> cs.setLong(1, "x")).getMessage()
                    .contains("Invalid number object found"));
      assertTrue(assertThrows(RuntimeException.class, () -> cs.setShort("A", "x")).getMessage()
                    .contains("Invalid number object found"));
      assertTrue(assertThrows(RuntimeException.class, () -> cs.setDate(1, "x")).getMessage()
                    .contains("Invalid date found"));
      assertTrue(assertThrows(RuntimeException.class, () -> cs.setTimestamp(1, 5)).getMessage()
                    .contains("Invalid date found"));
   }

   @Test
   void aGetterRefusesAParameterThatIsNotAPositionOrAName() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");

      for(String getter : new String[] { "getInt", "getString", "getLong", "getDouble", "getDate", "getObject" }) {
         RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            switch(getter) {
            case "getInt" -> cs.getInt(true);
            case "getString" -> cs.getString(true);
            case "getLong" -> cs.getLong(true);
            case "getDouble" -> cs.getDouble(true);
            case "getDate" -> cs.getDate(true);
            default -> cs.getObject(true);
            }
         }, getter);

         assertTrue(ex.getMessage().contains("must be of class String or Num"), ex.getMessage());
      }
   }

   @Test
   void aNameIsPassedToTheDriverAndTheDriverRefusesIt() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");

      // Derby has no named parameters: the name reaches the driver, which says so
      assertThrows(SQLException.class, () -> cs.setInt("A", 1));
      assertThrows(SQLException.class, () -> cs.getInt("B"));
   }

   @Test
   void anErrorOfTheDriverIsRethrown() throws Throwable {
      assertThrows(SQLException.class, () -> call("{call NO_SUCH_PROC()}").execute());

      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");
      assertThrows(SQLException.class, () -> cs.setInt(9, 1));
   }

   /**
    * A script number reaches registerOutParameter(position, type, typeName) as a Double, the only number type that
    * overload casts to; the call must get to the driver (which has no such named type, so it refuses with an
    * SQLException, not a ClassCastException).
    */
   @Test
   void registerOutParameterWithATypeNameWorksFromAScript() throws Throwable {
      CallableStatementScriptable cs = call("{call ADD_ONE(?, ?)}");
      GraalJavaScriptEngine engine = new GraalJavaScriptEngine();

      try {
         engine.init(new HashMap<>());
         engine.put("cs", cs);
         Object result = engine.exec(engine.compile(
            "(function(){try{cs.registerOutParameter(2, 12, 'typ'); return 'ok';}" +
            "catch(e){return 'ERR ' + e;}})()"), null, null);

         assertFalse(String.valueOf(result).contains("ClassCastException"), String.valueOf(result));
      }
      finally {
         engine.close();
      }
   }
}
