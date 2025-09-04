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

package inetsoft.uql.table;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


import static org.junit.jupiter.api.Assertions.*;

class XSwappableTableTest {

   private XSwappableTable table;

   @BeforeEach
   void setUp() {
      table = new XSwappableTable(3, true);
   }

   @Test
   void testConstructorWithColumnTypes() {
      Class<?>[] colTypes = {String.class, Integer.class, Double.class};
      XSwappableTable typedTable = new XSwappableTable(colTypes);

      assertEquals(3, typedTable.getColCount());
      assertEquals(String.class, typedTable.getColType(0));
   }

   @Test
   void testEmptyTable() {
      assertEquals(3, table.getColCount());
      assertEquals(-1, table.getRowCount());
      assertFalse(table.isCompleted());
      assertFalse(table.isDisposed());
   }

   @Test
   void testSingleHeaderRow() {
      Object[] header = {"Name", "Age", "Salary"};
      table.addRow(header);
      table.complete();

      assertEquals(1, table.getRowCount());
      assertEquals("Name", table.getObject(0, 0));
      assertEquals("Age", table.getObject(0, 1));
   }

   @Test
   void testMultipleDataRows() {
      Object[] header = {"Product", "Price", "Stock"};
      Object[] row1 = {"Laptop", 999.99, 10};
      Object[] row2 = {"Mouse", 25.50, 100};

      table.addRow(header);
      table.addRow(row1);
      table.addRow(row2);
      table.complete();

      assertEquals(3, table.getRowCount());
      assertEquals("Laptop", table.getObject(1, 0));
      assertEquals(25.50, table.getDouble(2, 1), 0.01);
   }

   @Test
   void testNumericType() {
      XSwappableTable fourColumnTable = new XSwappableTable(6, true);
      Object[] header = {"Int", "Long", "Double", "Float", "Short", "Byte"};
      Object[] row = {42, 10000000000L, 123.456, 78.9f, (short) 123, (byte) 42};

      fourColumnTable.addRow(header);
      fourColumnTable.addRow(row);
      fourColumnTable.complete();

      assertEquals(6, fourColumnTable.getColCount());

      assertEquals(42, fourColumnTable.getInt(1, 0));
      assertEquals(10000000000L, fourColumnTable.getLong(1, 1));
      assertEquals(123.456, fourColumnTable.getDouble(1, 2), 0.001);
      assertEquals(78.9f, fourColumnTable.getFloat(1, 3), 0.001f);
      assertEquals((short) 123, fourColumnTable.getShort(1, 4));
      assertEquals((byte) 42, fourColumnTable.getByte(1, 5));
   }

   @Test
   void testBooleanType() {
      Object[] header = {"Bool1", "Bool2", "Dummy"};
      Object[] row = {true, false, null};

      table.addRow(header);
      table.addRow(row);
      table.complete();

      assertTrue(table.getBoolean(1, 0));
      assertFalse(table.getBoolean(1, 1));
   }

   @Test
   void testNull() {
      Object[] header = {"Col1", "Col2", "Col3"};
      Object[] row = {null, "NotNull", null};

      table.addRow(header);
      table.addRow(row);
      table.complete();

      assertTrue(table.isNull(1, 0));
      assertFalse(table.isNull(1, 1));
      assertTrue(table.isNull(1, 2));
      assertNull(table.getObject(1, 0));
   }

   @Test
   void testColumnOutOfBounds() {
      Object[] header = {"Col1", "Col2", "Col3"};
      table.addRow(header);
      table.complete();

      assertThrows(IllegalArgumentException.class, () -> table.getObject(0, 5));
   }

   @Test
   void testColumnIdentifiers() {
      Object[] header = {"Name", "Age", "Salary"};
      table.addRow(header);
      table.complete();

      table.setColumnIdentifier(0, "employee_name");
      table.setColumnIdentifier(1, "employee_age");
      table.setColumnIdentifier(2, "employee_salary");

      assertEquals("employee_name", table.getColumnIdentifier(0));
      assertEquals("employee_age", table.getColumnIdentifier(1));
      assertEquals("employee_salary", table.getColumnIdentifier(2));
   }

   @Test
   void testFindColumn() {
      Object[] header = {"FirstName", "LastName", "Email"};
      table.addRow(header);
      table.complete();

      assertEquals(0, table.findColumn("FirstName"));
      assertEquals(1, table.findColumn("LastName"));
      assertEquals(2, table.findColumn("Email"));
      assertEquals(-1, table.findColumn("NonExistent"));
   }

   @Test
   void testTableProperties() {
      table.setProperty("source", "database");
      table.setProperty("rows", 1000);

      assertEquals("database", table.getProperty("source"));
      assertEquals(1000, table.getProperty("rows"));
      assertNull(table.getProperty("nonexistent"));
   }

   @Test
   void testObjectPoolToggle() {
      assertTrue(table.isObjectPooled());

      table.setObjectPooled(false);
      assertFalse(table.isObjectPooled());

      table.setObjectPooled(true);
      assertTrue(table.isObjectPooled());
   }

   @Test
   void testCompletionState() {
      Object[] header = {"Test", "Test2", "Test3"};
      table.addRow(header);

      assertFalse(table.isCompleted());
      assertTrue(table.getRowCount() < 0);

      table.complete();
      assertTrue(table.isCompleted());
      assertEquals(1, table.getRowCount());
   }

   @Test
   void testDispose() {
      Object[] header = {"Test", "Test2", "Test3"};
      table.addRow(header);
      table.complete();

      assertFalse(table.isDisposed());
      table.dispose();
      assertTrue(table.isDisposed());
   }

   @Test
   void testCapacityManagement() {
      Object[] header = {"ID", "Value", "Data"};
      table.addRow(header);

      for (int i = 0; i < 10000; i++) {
         Object[] row = {i, "Value" + i, "Data" + i};
         table.addRow(row);
      }

      table.complete();

      assertEquals(10001, table.getRowCount());
      assertEquals("Value9999", table.getObject(10000, 1));
      assertEquals("Data5000", table.getObject(5001, 2));
   }
}