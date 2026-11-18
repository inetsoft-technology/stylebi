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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.postgresql.jdbc.TimestampUtils;
import org.postgresql.util.ByteConverter;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Time;
import java.sql.Types;
import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79508: Postgres timetz must be read as the stored wall clock, independent of the JVM
 * zone, to agree with the SQL pushdown (cast(x as time)).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class PostgreSQLTypesTest {
   private TimeZone oldZone;

   @BeforeEach
   void setZone() {
      oldZone = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"));
   }

   @AfterEach
   void restoreZone() {
      TimeZone.setDefault(oldZone);
   }

   /** Text transfer: the driver hands out the stored text as is. */
   private static ResultSet rs(String typeName, String str, Time time) throws Exception {
      ResultSet rs = mock(ResultSet.class);
      ResultSetMetaData meta = mock(ResultSetMetaData.class);
      when(rs.getMetaData()).thenReturn(meta);
      when(meta.getColumnTypeName(1)).thenReturn(typeName);
      when(rs.getString(1)).thenReturn(str);
      when(rs.getBytes(1)).thenReturn(str == null ? null : str.getBytes(StandardCharsets.UTF_8));
      when(rs.getTime(1)).thenReturn(time);
      return rs;
   }

   /**
    * Binary transfer (pgjdbc after prepareThreshold executions): the 12 byte wire value is
    * (micros since midnight, -offset seconds). getString/getTime are answered by the real
    * pgjdbc TimestampUtils, exactly as PgResultSet does in binary mode.
    */
   private static ResultSet binaryRs(long micros, int offsetSeconds) throws Exception {
      byte[] raw = new byte[12];
      ByteConverter.int8(raw, 0, micros);
      ByteConverter.int4(raw, 8, -offsetSeconds);
      TimestampUtils utils = new TimestampUtils(false, () -> TimeZone.getDefault());
      ResultSet rs = mock(ResultSet.class);
      ResultSetMetaData meta = mock(ResultSetMetaData.class);
      when(rs.getMetaData()).thenReturn(meta);
      when(meta.getColumnTypeName(1)).thenReturn("timetz");
      when(rs.getBytes(1)).thenReturn(raw);
      when(rs.getString(1)).thenAnswer(inv -> utils.toStringOffsetTimeBin(raw));
      when(rs.getTime(1)).thenAnswer(inv -> utils.toTimeBin(TimeZone.getDefault(), raw));
      return rs;
   }

   private static long micros(int h, int m, int s, int micro) {
      return ((h * 3600L + m * 60L + s) * 1_000_000L) + micro;
   }

   private static String hms(Time time) {
      Calendar c = Calendar.getInstance();
      c.setTime(time);
      return String.format("%02d:%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE),
                           c.get(Calendar.SECOND));
   }

   @Test
   void timetzIsReadAsStoredWallClock() throws Exception {
      // what pgjdbc getTime would return in a GMT+8 JVM: 00:23:23
      Time shifted = Time.valueOf("00:23:23");
      ResultSet rs = rs("timetz", "23:59:59+07:36:36", shifted);
      Time time = (Time) new PostgreSQLTypes().getObject(rs, 1, Types.TIME);
      assertEquals("23:59:59", hms(time));
      Calendar c = Calendar.getInstance();
      c.setTime(time);
      assertEquals(23, c.get(Calendar.HOUR_OF_DAY));
   }

   @Test
   void timetzShortOffsetAndFraction() throws Exception {
      ResultSet rs = rs("timetz", "13:05:09.250+07", Time.valueOf("14:00:00"));
      Time time = (Time) new PostgreSQLTypes().getObject(rs, 1, Types.TIME);
      assertEquals("13:05:09", hms(time));
      assertEquals(250, time.getTime() % 1000);
   }

   @Test
   void binaryTransferIsReadAsStoredWallClock() throws Exception {
      // 23:59:59+07:36:36 in binary transfer; the driver's own getString is zone converted
      ResultSet rs = binaryRs(micros(23, 59, 59, 0), 7 * 3600 + 36 * 60 + 36);
      assertNotEquals("23:59:59", hms(Time.valueOf(rs.getString(1).substring(0, 8))));
      Time time = (Time) new PostgreSQLTypes().getObject(rs, 1, Types.TIME);
      assertEquals("23:59:59", hms(time));
   }

   @Test
   void binaryTransferKeepsFraction() throws Exception {
      ResultSet rs = binaryRs(micros(13, 5, 9, 250_000), -5 * 3600);
      Time time = (Time) new PostgreSQLTypes().getObject(rs, 1, Types.TIME);
      assertEquals("13:05:09", hms(time));
      assertEquals(250, time.getTime() % 1000);
   }

   @Test
   void binaryTransfer24HoursDoesNotFail() throws Exception {
      // the driver's getString throws for a stored 24:00:00+00 in binary mode
      ResultSet rs = binaryRs(micros(24, 0, 0, 0), 0);
      assertThrows(Exception.class, () -> rs.getString(1));
      // falls back to the driver's getTime, as before the wall clock handling was added
      Time time = (Time) new PostgreSQLTypes().getObject(rs, 1, Types.TIME);
      assertEquals(rs.getTime(1), time);
   }

   @Test
   void text24HoursFallsBackToDriverTime() throws Exception {
      Time driver = Time.valueOf("08:00:00");
      ResultSet rs = rs("timetz", "24:00:00+00", driver);
      assertEquals(driver, new PostgreSQLTypes().getObject(rs, 1, Types.TIME));
   }

   @Test
   void plainTimeUnchanged() throws Exception {
      Time expected = Time.valueOf("23:59:59");
      ResultSet rs = rs("time", "23:59:59", expected);
      assertEquals(expected, new PostgreSQLTypes().getObject(rs, 1, Types.TIME));
   }

   @Test
   void nullTimetz() throws Exception {
      ResultSet rs = rs("timetz", null, null);
      assertNull(new PostgreSQLTypes().getObject(rs, 1, Types.TIME));
   }

   @Test
   void registeredForPostgres() {
      assertInstanceOf(PostgreSQLTypes.class, SQLTypes.getSQLTypes(postgresDs()));
   }

   private static inetsoft.uql.jdbc.JDBCDataSource postgresDs() {
      inetsoft.uql.jdbc.JDBCDataSource ds = mock(inetsoft.uql.jdbc.JDBCDataSource.class);
      when(ds.getDatabaseTypeString()).thenReturn("POSTGRESQL");
      return ds;
   }
}
