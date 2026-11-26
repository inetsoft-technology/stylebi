/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression test for Bug #81678: the setters must reject a first parameter that is not a
 * position (Number) or a name (String) instead of silently doing nothing.
 */
@Tag("core")
class CallableStatementScriptableTest {
   @BeforeEach
   void setUp() throws Throwable {
      cstmt = mock(CallableStatement.class);
      Connection conn = mock(Connection.class);
      when(conn.prepareCall("{call p(?)}")).thenReturn(cstmt);
      scriptable = new CallableStatementScriptable("{call p(?)}", conn, null);
   }

   @Test
   void settersRejectBooleanParameter() {
      Object bad = Boolean.TRUE;
      assertRejected("registerOutParameter", () -> scriptable.registerOutParameter(bad, Types.INTEGER, null));
      assertRejected("setBigDecimal", () -> scriptable.setBigDecimal(bad, 1));
      assertRejected("setBoolean", () -> scriptable.setBoolean(bad, true));
      assertRejected("setByte", () -> scriptable.setByte(bad, 1));
      assertRejected("setDate", () -> scriptable.setDate(bad, new Date()));
      assertRejected("setDouble", () -> scriptable.setDouble(bad, 1.0));
      assertRejected("setFloat", () -> scriptable.setFloat(bad, 1));
      assertRejected("setInt", () -> scriptable.setInt(bad, 1));
      assertRejected("setLong", () -> scriptable.setLong(bad, 1));
      assertRejected("setNull", () -> scriptable.setNull(bad, Types.INTEGER));
      assertRejected("setNull", () -> scriptable.setNull(bad, Types.INTEGER, "t"));
      assertRejected("setObject", () -> scriptable.setObject(bad, "x"));
      assertRejected("setObject", () -> scriptable.setObject(bad, "x", Types.VARCHAR));
      assertRejected("setObject", () -> scriptable.setObject(bad, "x", Types.VARCHAR, 0));
      assertRejected("setShort", () -> scriptable.setShort(bad, 1));
      assertRejected("setString", () -> scriptable.setString(bad, "x"));
      assertRejected("setTime", () -> scriptable.setTime(bad, new Date()));
      assertRejected("setTimestamp", () -> scriptable.setTimestamp(bad, new Date()));
      verifyNoInteractions(cstmt);
   }

   @Test
   void settersRejectNullParameter() {
      assertRejected("setInt", () -> scriptable.setInt(null, 1));
      assertRejected("setString", () -> scriptable.setString(null, "x"));
      assertRejected("setNull", () -> scriptable.setNull(null, Types.INTEGER));
      assertRejected("registerOutParameter",
                     () -> scriptable.registerOutParameter(null, Types.INTEGER, null));
      verifyNoInteractions(cstmt);
   }

   @Test
   void numberAndStringParametersStillDelegate() throws Throwable {
      scriptable.setInt(2, 5);
      verify(cstmt).setInt(2, 5);
      scriptable.setInt("p", 6);
      verify(cstmt).setInt("p", 6);
      scriptable.setString(1, "v");
      verify(cstmt).setString(1, "v");
      scriptable.setString("p", "w");
      verify(cstmt).setString("p", "w");
      scriptable.setBoolean(1, true);
      verify(cstmt).setBoolean(1, true);
      scriptable.setNull("p", Types.VARCHAR);
      verify(cstmt).setNull("p", Types.VARCHAR);
      scriptable.registerOutParameter(1, Types.INTEGER, null);
      verify(cstmt).registerOutParameter(1, Types.INTEGER);
      scriptable.registerOutParameter("o", Types.INTEGER, null);
      verify(cstmt).registerOutParameter("o", Types.INTEGER);
   }

   private void assertRejected(String method, Executable call) {
      RuntimeException ex = assertThrows(RuntimeException.class, call, method);
      assertEquals("First parameter of " + method + " must be of class String or Num.",
                   ex.getMessage());
   }

   private CallableStatement cstmt;
   private CallableStatementScriptable scriptable;
}
