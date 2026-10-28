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
package inetsoft.uql.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.datastax.oss.driver.api.core.data.CqlDuration;
import com.datastax.oss.driver.api.core.type.DataType;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.protocol.internal.ProtocolConstants;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the values {@link CassandraTable#getObject(int)} returns for the CQL types the driver
 * cannot hand out as a {@code UdtValue} or a {@code String} collection, and that each value's class
 * agrees with {@link CassandraTable#getType(int)}. Column types are the driver's real
 * {@link DataType}s; the row is a mock returning what the driver's own codec produces.
 */
class CassandraTableValueTest {
   @Test
   void tinyintAndSmallint_areReturnedAsTheirTypedValues() throws Exception {
      assertValue(DataTypes.TINYINT, (byte) 127, (byte) 127, Byte.class);
      assertValue(DataTypes.SMALLINT, (short) 32767, (short) 32767, Short.class);
   }

   @Test
   void date_isReturnedAsSqlDateWithoutTimezoneShift() throws Exception {
      assertValue(DataTypes.DATE, LocalDate.of(2026, 10, 1), java.sql.Date.valueOf("2026-10-01"),
                  java.sql.Date.class);
   }

   @Test
   void time_isReturnedAsSqlTime() throws Exception {
      assertValue(DataTypes.TIME, LocalTime.of(13, 45, 30, 123456789),
                  java.sql.Time.valueOf("13:45:30"), java.sql.Time.class);
   }

   @Test
   void duration_isReturnedAsText() throws Exception {
      CqlDuration duration = CqlDuration.from("1mo2d3h");
      assertValue(DataTypes.DURATION, duration, duration.toString(), String.class);
   }

   @Test
   void nonTextCollections_keepTheirElementTypes() throws Exception {
      assertValue(DataTypes.listOf(DataTypes.INT), Arrays.asList(1, 2, 3),
                  Arrays.asList(1, 2, 3), java.util.List.class);
      assertValue(DataTypes.setOf(DataTypes.INT), new LinkedHashSet<>(Arrays.asList(1, 2)),
                  new LinkedHashSet<>(Arrays.asList(1, 2)), java.util.Set.class);
      assertValue(DataTypes.mapOf(DataTypes.TEXT, DataTypes.INT), Map.of("a", 1),
                  Map.of("a", 1), java.util.Map.class);
   }

   @Test
   void collectionElements_areConvertedLikeColumns() throws Exception {
      assertValue(DataTypes.listOf(DataTypes.DATE), List.of(LocalDate.of(2026, 10, 1)),
                  List.of(java.sql.Date.valueOf("2026-10-01")), java.util.List.class);
      assertValue(DataTypes.mapOf(DataTypes.TEXT, DataTypes.frozenListOf(DataTypes.TIME)),
                  Map.of("k", List.of(LocalTime.of(1, 2, 3))),
                  Map.of("k", List.of(java.sql.Time.valueOf("01:02:03"))), java.util.Map.class);
   }

   @Test
   void listOfUdt_isNotNull() throws Exception {
      // frozen UDT elements pass through as the driver values, as a top-level UDT column does
      UserDefinedType udtType = mock(UserDefinedType.class);
      when(udtType.getProtocolCode()).thenReturn(ProtocolConstants.DataType.UDT);
      UdtValue udt = mock(UdtValue.class);
      DataType type = DataTypes.listOf(udtType);
      CassandraTable table = tableWith(type, List.of(udt));
      assertTrue(table.next());
      assertEquals(List.of(udt), table.getObject(0));
   }

   @Test
   void nullCells_stayNull() throws Exception {
      DataType[] types = {
         DataTypes.TINYINT, DataTypes.SMALLINT, DataTypes.DATE, DataTypes.TIME, DataTypes.DURATION,
         DataTypes.listOf(DataTypes.INT), DataTypes.setOf(DataTypes.INT),
         DataTypes.mapOf(DataTypes.TEXT, DataTypes.INT)
      };

      for(DataType type : types) {
         CassandraTable table = tableWith(type, null);
         assertTrue(table.next());
         assertNull(table.getObject(0), type.toString());
      }
   }

   private static void assertValue(DataType type, Object raw, Object expected, Class<?> cls)
      throws Exception
   {
      CassandraTable table = tableWith(type, raw);
      assertTrue(table.next());
      Object actual = table.getObject(0);

      assertEquals(expected, actual);
      assertTrue(table.getType(0).isInstance(actual));
      assertEquals(cls, table.getType(0));
   }

   private static CassandraTable tableWith(DataType type, Object raw) throws Exception {
      ColumnDefinition column = mock(ColumnDefinition.class);
      when(column.getName()).thenReturn(CqlIdentifier.fromInternal("col"));
      when(column.getType()).thenReturn(type);

      ColumnDefinitions columns = mock(ColumnDefinitions.class);
      when(columns.size()).thenReturn(1);
      when(columns.get(0)).thenReturn(column);

      Row row = mock(Row.class);
      when(row.getObject(0)).thenReturn(raw);

      ResultSet result = mock(ResultSet.class);
      when(result.getColumnDefinitions()).thenReturn(columns);
      when(result.one()).thenReturn(row);

      return new CassandraTable(result, mock(CqlSession.class), 0);
   }
}
