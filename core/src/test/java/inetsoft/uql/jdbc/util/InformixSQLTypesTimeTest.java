/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.uql.jdbc.util;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Calendar;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82477: the Informix driver reports DATETIME HOUR TO FRACTION as Types.TIME and its
 * getTime() drops the fraction (verified against informix-developer-database with JDBC
 * 4.50.11: getTime ms=0, getTimestamp ms=500, date part 1700-11-01). InformixSQLTypes must read
 * such a column through getTimestamp() and keep the milliseconds.
 */
// SQLTypes static initialization reads configuration through Spring beans
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class InformixSQLTypesTimeTest {
   private final InformixSQLTypes types = new InformixSQLTypes();

   private static Timestamp ts(int h, int m, int s, int ms) {
      return Timestamp.valueOf(LocalDateTime.of(LocalDate.of(1700, 11, 1), java.time.LocalTime
         .of(h, m, s, ms * 1_000_000)));
   }

   private static Time expected(int h, int m, int s, int ms) {
      Calendar c = Calendar.getInstance();
      c.clear();
      c.set(1970, Calendar.JANUARY, 1, h, m, s);
      c.set(Calendar.MILLISECOND, ms);
      return new Time(c.getTimeInMillis());
   }

   @Test
   void resultSetKeepsMillisAndNormalizesDate() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getTimestamp(1)).thenReturn(ts(0, 0, 0, 500));
      when(rs.getTime(1)).thenReturn(Time.valueOf("00:00:00")); // what the driver truncates to

      Object o = types.getObject(rs, 1, Types.TIME);
      assertInstanceOf(Time.class, o);
      assertEquals(expected(0, 0, 0, 500).getTime(), ((Time) o).getTime());
   }

   @Test
   void callableStatementKeepsMillis() throws Exception {
      CallableStatement cs = mock(CallableStatement.class);
      when(cs.getTimestamp(2)).thenReturn(ts(13, 45, 30, 250));

      Object o = types.getObject(cs, 2, Types.TIME);
      assertEquals(expected(13, 45, 30, 250).getTime(), ((Time) o).getTime());
   }

   @Test
   void nullAndWholeSecondValues() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getTimestamp(1)).thenReturn(null);
      when(rs.getTimestamp(2)).thenReturn(ts(8, 9, 10, 0));

      assertNull(types.getObject(rs, 1, Types.TIME));
      assertEquals(expected(8, 9, 10, 0).getTime(), ((Time) types.getObject(rs, 2, Types.TIME)).getTime());
   }

   @Test
   void otherTypesUnchanged() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      Timestamp t = ts(1, 2, 3, 4);
      when(rs.getTimestamp(1)).thenReturn(t);

      assertSame(t, types.getObject(rs, 1, Types.TIMESTAMP));
      verify(rs, never()).getTime(anyInt());
   }
}
