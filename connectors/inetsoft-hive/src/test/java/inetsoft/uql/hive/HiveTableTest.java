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
package inetsoft.uql.hive;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Column types, values and names of a Hive result (bugs #82335, #82334, #82331). The
 * mocked result set reports what hive-jdbc 4.0.1 reports: Types.BOOLEAN with Boolean
 * values for a BOOLEAN column, Types.BINARY with byte[] values for a BINARY column, and
 * the names of HiveServer2's result schema, which for a SELECT * are qualified with the
 * table name or alias.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HiveTableTest {
   @Test
   void booleanColumnIsTypedBoolean() throws Exception {
      HiveTable table = new HiveTable(resultSet(
         new String[] { "id", "c_boolean" }, new int[] { Types.INTEGER, Types.BOOLEAN },
         new Object[][] { { 1, true }, { 2, false }, { 3, null } }));

      assertEquals(Boolean.class, table.getType(1));
      assertTrue(table.next());
      assertEquals(Boolean.TRUE, table.getObject(1));
      assertTrue(table.next());
      assertEquals(Boolean.FALSE, table.getObject(1));
      assertTrue(table.next());
      assertNull(table.getObject(1));
   }

   @Test
   void binaryValuesAreReadAsText() throws Exception {
      HiveTable table = new HiveTable(resultSet(
         new String[] { "id", "c_binary" }, new int[] { Types.INTEGER, Types.BINARY },
         new Object[][] { { 1, "hi".getBytes(StandardCharsets.UTF_8) }, { 2, null },
                          { 3, new byte[0] } }));

      assertEquals(String.class, table.getType(1));
      assertTrue(table.next());
      assertEquals("hi", table.getObject(1));
      assertTrue(table.next());
      assertNull(table.getObject(1));
      assertTrue(table.next());
      assertEquals("", table.getObject(1));
   }

   @Test
   void selectStarColumnsAreNamedWithoutTableQualifier() throws Exception {
      HiveTable table = new HiveTable(resultSet(
         new String[] { "customers.customer_id", "customers.name" },
         new int[] { Types.INTEGER, Types.VARCHAR }, new Object[][] { { 1, "a" } }));

      assertEquals("customer_id", table.getName(0));
      assertEquals("name", table.getName(1));
   }

   @Test
   void joinColumnsWithSameNameStayDistinguishable() throws Exception {
      HiveTable table = new HiveTable(resultSet(
         new String[] { "o.order_id", "o.customer_id", "c.customer_id", "c.name" },
         new int[] { Types.INTEGER, Types.INTEGER, Types.INTEGER, Types.VARCHAR },
         new Object[0][]));

      assertEquals("order_id", table.getName(0));
      assertEquals("o.customer_id", table.getName(1));
      assertEquals("c.customer_id", table.getName(2));
      assertEquals("name", table.getName(3));
   }

   @Test
   void unqualifiedNamesAreKept() throws Exception {
      HiveTable table = new HiveTable(resultSet(
         new String[] { "customer_id", "name" },
         new int[] { Types.INTEGER, Types.VARCHAR }, new Object[0][]));

      assertEquals("customer_id", table.getName(0));
      assertEquals("name", table.getName(1));
      assertEquals(Integer.class, table.getType(0));
   }

   private static ResultSet resultSet(String[] names, int[] types, Object[][] rows)
      throws Exception
   {
      ResultSetMetaData meta = mock(ResultSetMetaData.class);
      when(meta.getColumnCount()).thenReturn(names.length);

      for(int i = 0; i < names.length; i++) {
         when(meta.getColumnName(i + 1)).thenReturn(names[i]);
         when(meta.getColumnLabel(i + 1)).thenReturn(names[i]);
         when(meta.getColumnType(i + 1)).thenReturn(types[i]);
      }

      int[] row = { -1 };
      ResultSet result = mock(ResultSet.class);
      when(result.getMetaData()).thenReturn(meta);
      when(result.next()).thenAnswer(inv -> ++row[0] < rows.length);
      when(result.getObject(anyInt())).thenAnswer(inv -> value(rows, row, inv.getArgument(0)));
      when(result.getString(anyInt())).thenAnswer(inv -> {
         Object value = value(rows, row, inv.getArgument(0));
         return value == null ? null : value.toString();
      });
      when(result.getBinaryStream(anyInt())).thenAnswer(inv -> {
         Object value = value(rows, row, inv.getArgument(0));
         return value == null ? null : new ByteArrayInputStream((byte[]) value);
      });

      return result;
   }

   private static Object value(Object[][] rows, int[] row, int column) {
      return rows[row[0]][column - 1];
   }
}
