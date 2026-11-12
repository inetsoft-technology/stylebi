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
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.schema.StringType;
import inetsoft.uql.schema.TimeInstantType;
import inetsoft.uql.schema.XTypeNode;
import inetsoft.uql.table.XTimestampColumn;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79862: zone-aware timestamp columns (H2 TIMESTAMP WITH TIME ZONE = 2014, SQL Server
 * datetimeoffset = -155) must be TimeInstant columns read as Timestamp, like TIMESTAMP.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SQLTypesZonedTimestampTest {
   private static final int[] ZONED = { Types.TIMESTAMP_WITH_TIMEZONE, -155 };

   @Test
   void normalizeOnlyMapsZonedCodes() {
      assertEquals(Types.TIMESTAMP, SQLTypes.normalizeZonedTimestampType(2014));
      assertEquals(Types.TIMESTAMP, SQLTypes.normalizeZonedTimestampType(-155));
      assertEquals(Types.TIMESTAMP, SQLTypes.normalizeZonedTimestampType(Types.TIMESTAMP));
      assertEquals(Types.VARCHAR, SQLTypes.normalizeZonedTimestampType(Types.VARCHAR));
      assertEquals(-101, SQLTypes.normalizeZonedTimestampType(-101)); // Oracle TIMESTAMPTZ
      assertEquals(Types.TIME_WITH_TIMEZONE,
                   SQLTypes.normalizeZonedTimestampType(Types.TIME_WITH_TIMEZONE));
   }

   @Test
   void metadataTypeIsTimeInstant() {
      SQLTypes types = new SQLTypes();

      for(int code : ZONED) {
         XTypeNode node = types.createTypeNode("D", code, "any");
         assertTrue(node instanceof TimeInstantType, "code " + code);
         assertFalse(node instanceof StringType, "code " + code);
         assertEquals(Types.TIMESTAMP, types.fixSQLType(code, null));
         assertEquals(XSchema.TIME_INSTANT, types.convertToXType(code));
      }
   }

   @Test
   void runtimeColumnIsTimestamp() {
      SQLTypes types = new SQLTypes();

      for(int code : ZONED) {
         assertEquals(Timestamp.class, types.convertToJava(code));
         // the creator is an anonymous class per column type
         assertEquals(XTimestampColumn.getCreator().getClass(),
                      types.getXTableColumnCreator(code, null).getClass());
      }
   }

   @Test
   void unrelatedTypesUnchanged() {
      SQLTypes types = new SQLTypes();
      assertTrue(types.createTypeNode("D", Types.VARCHAR, null) instanceof StringType);
      assertEquals(XSchema.STRING, types.convertToXType(Types.VARCHAR));
      assertEquals(XSchema.TIME_INSTANT, types.convertToXType(Types.TIMESTAMP));
      assertEquals(Types.VARCHAR, types.fixSQLType(Types.NVARCHAR, "nvarchar"));
   }

   @Test
   void valueIsReadWithGetTimestamp() throws Exception {
      Timestamp ts = new Timestamp(1577932200000L);
      ResultSet rs = mock(ResultSet.class);
      when(rs.getTimestamp(1)).thenReturn(ts);

      for(int code : ZONED) {
         assertSame(ts, new SQLTypes().getObject(rs, 1, code));
      }
   }

   @Test
   void sqlServerFallsBackToDriverObject() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getTimestamp(1)).thenThrow(new SQLException("unsupported"));
      when(rs.getObject(1)).thenReturn(
         java.time.OffsetDateTime.parse("2020-01-02T10:30:00+08:00"));
      SQLServerSQLTypes types = new SQLServerSQLTypes();

      // the runtime path passes the already normalized code, the raw code must work as well
      assertEquals(new Timestamp(1577932200000L), types.getObject(rs, 1, Types.TIMESTAMP));
      assertEquals(new Timestamp(1577932200000L), types.getObject(rs, 1, -155));
   }

   @Test
   void sqlServerRethrowsWhenValueIsNotTemporal() throws Exception {
      ResultSet rs = mock(ResultSet.class);
      when(rs.getTimestamp(1)).thenThrow(new SQLException("bad"));
      when(rs.getObject(1)).thenReturn("garbage");
      assertThrows(SQLException.class,
                   () -> new SQLServerSQLTypes().getObject(rs, 1, Types.TIMESTAMP));
   }
}
