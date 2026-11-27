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

import inetsoft.test.*;
import inetsoft.uql.XTable;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Bug #82378: an integral BigDecimal above 2^53 (an Oracle NUMBER or SQL Server numeric id) was
 * stored as a double, so neighbouring values were rounded to the same value.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XBDDoubleColumnTest {
   private static final long TWO_53 = 9007199254740992L;

   @Test
   void valuesAbove2Pow53KeepTheirExactValue() {
      XBDDoubleColumn column = new XBDDoubleColumn((char) 4, (char) 20);
      XTableColumn current = column;

      for(long i = 1; i <= 5; i++) {
         XTableColumn replacement = current.addObject(new BigDecimal(TWO_53).add(BigDecimal.valueOf(i)));

         if(replacement != null) {
            current = replacement;
         }
      }

      for(int i = 0; i < 5; i++) {
         assertEquals(new BigDecimal(TWO_53 + i + 1), current.getObject(i));
      }
   }

   @Test
   void earlierSmallValuesAndNullsSurviveTheSwitchToExactObjects() {
      XTableColumn current = new XBDDoubleColumn((char) 4, (char) 20);
      XTableColumn[] holder = { current };
      add(holder, new BigDecimal("5"));
      add(holder, null);
      add(holder, new BigDecimal("1.5"));
      add(holder, new BigDecimal(TWO_53 + 1));

      assertNotSame(current, holder[0]);
      assertEquals(0, new BigDecimal("5").compareTo((BigDecimal) holder[0].getObject(0)));
      assertNull(holder[0].getObject(1));
      assertEquals(0, new BigDecimal("1.5").compareTo((BigDecimal) holder[0].getObject(2)));
      assertEquals(new BigDecimal(TWO_53 + 1), holder[0].getObject(3));
   }

   @Test
   void normalValuesStayInTheDoubleColumn() {
      XBDDoubleColumn column = new XBDDoubleColumn((char) 10, (char) 20);

      // integral, within 2^53, fractional, negative, fractional with many digits, and the
      // largest long that is still exact in a double (2^53 itself)
      assertNull(column.addObject(new BigDecimal("12345")));
      assertNull(column.addObject(new BigDecimal("-7")));
      assertNull(column.addObject(new BigDecimal("1.25")));
      assertNull(column.addObject(new BigDecimal("0.1")));
      assertNull(column.addObject(new BigDecimal("123456789012345678.5")));
      assertNull(column.addObject(new BigDecimal(TWO_53)));
      assertNull(column.addObject(Long.valueOf(42L)));
      assertNull(column.addObject(Double.valueOf(2.5)));
      assertNull(column.addObject(null));

      assertEquals(12345.0, column.getDouble(0));
      assertEquals(-7.0, column.getDouble(1));
      assertEquals(TWO_53, column.getLong(5));
      assertEquals(42L, column.getLong(6));
      assertEquals(2.5, column.getDouble(7));
      assertNull(column.getObject(8));
   }

   @Test
   void exactlyRepresentableBigValueStaysInTheDoubleColumn() {
      // 2^60 is above 2^53 but a double holds it exactly
      XBDDoubleColumn column = new XBDDoubleColumn((char) 4, (char) 20);
      assertNull(column.addObject(new BigDecimal(1L << 60)));
   }

   @Test
   void tableKeepsExactValuesAndOtherRowsAcrossAnObjectColumn() {
      assumeTrue(XBDDoubleColumn.isRecommended());

      XSwappableTable table = new XSwappableTable(2, true);
      table.addRow(new Object[] { "ID", "BIG" });

      for(int i = 1; i <= 6; i++) {
         table.addRow(new Object[] { new BigDecimal(i), new BigDecimal(TWO_53).add(new BigDecimal(i)) });
      }

      table.complete();
      assertCol(table);
   }

   private static void assertCol(XTable table) {
      for(int i = 1; i <= 6; i++) {
         assertEquals(0, new BigDecimal(i).compareTo(new BigDecimal(table.getObject(i, 0).toString())));
         assertEquals(new BigDecimal(TWO_53 + i), table.getObject(i, 1));
      }
   }

   private static void add(XTableColumn[] holder, Object value) {
      XTableColumn replacement = holder[0].addObject(value);

      if(replacement != null) {
         holder[0] = replacement;
      }
   }
}
