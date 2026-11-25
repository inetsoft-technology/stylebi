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

import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #82332: a Hive query that returns no rows must still build its columns from the
 * result set metadata.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HiveTableEmptyResultTest {
   @Test
   void emptyResultKeepsColumnNamesAndTypes() throws Exception {
      ResultSet rs = resultSet(new String[] { "sku", "qty", "price" },
                               new int[] { Types.VARCHAR, Types.INTEGER, Types.DOUBLE });
      when(rs.next()).thenReturn(false);

      HiveTable table = new HiveTable(rs);

      assertEquals(3, table.getColCount());
      assertEquals("sku", table.getName(0));
      assertEquals("qty", table.getName(1));
      assertEquals("price", table.getName(2));
      assertEquals(String.class, table.getType(0));
      assertNotNull(table.getType(1));
      assertNotNull(table.getType(2));
      assertFalse(table.next());
   }

   @Test
   void rowsStillLoadWithColumns() throws Exception {
      ResultSet rs = resultSet(new String[] { "sku" }, new int[] { Types.VARCHAR });
      when(rs.next()).thenReturn(true, true, false);
      when(rs.getString(1)).thenReturn("A1", "B2");
      when(rs.getObject(1)).thenReturn("A1", "B2");

      HiveTable table = new HiveTable(rs);

      assertEquals(1, table.getColCount());
      assertEquals("sku", table.getName(0));
      assertEquals(String.class, table.getType(0));
      assertTrue(table.next());
      assertEquals("A1", table.getObject(0));
      assertTrue(table.next());
      assertEquals("B2", table.getObject(0));
      assertFalse(table.next());
   }

   private static ResultSet resultSet(String[] names, int[] types) throws SQLException {
      ResultSet rs = mock(ResultSet.class);
      ResultSetMetaData meta = mock(ResultSetMetaData.class);
      when(rs.getMetaData()).thenReturn(meta);
      when(meta.getColumnCount()).thenReturn(names.length);

      for(int i = 0; i < names.length; i++) {
         when(meta.getColumnName(i + 1)).thenReturn(names[i]);
         when(meta.getColumnType(i + 1)).thenReturn(types[i]);
      }

      return rs;
   }
}
