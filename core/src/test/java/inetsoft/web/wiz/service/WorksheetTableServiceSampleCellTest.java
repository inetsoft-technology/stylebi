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
package inetsoft.web.wiz.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #82291: {@code WorksheetTableService.toSampleCell} must stay identical to
 * {@code WorksheetPreviewService.toJsonSafe}. A Long, BigInteger or BigDecimal a double cannot hold
 * exactly goes out as its decimal text so a JSON reader cannot round it; every other number is
 * returned as is.
 */
@Tag("core")
class WorksheetTableServiceSampleCellTest {
   private static Object cell(Object value) throws Exception {
      Method m = WorksheetTableService.class.getDeclaredMethod("toSampleCell", Object.class);
      m.setAccessible(true);
      return m.invoke(null, value);
   }

   @Test
   void longAbove2Pow53GoesOutAsText() throws Exception {
      assertEquals("639311709851592516", cell(639311709851592516L));
      assertEquals("9007199254740993", cell(9007199254740993L));
      assertEquals("-9007199254740993", cell(-9007199254740993L));
   }

   @Test
   void numbersADoubleHoldsExactlyAreUnchanged() throws Exception {
      assertEquals(9007199254740992L, cell(9007199254740992L));
      assertEquals(42L, cell(42L));
      assertEquals(7, cell(7));
      assertEquals(1.5d, cell(1.5d));
      assertEquals(new BigDecimal("12.34"), cell(new BigDecimal("12.34")));
      assertEquals(BigInteger.valueOf(5), cell(BigInteger.valueOf(5)));
      assertEquals("x", cell("x"));
      assertEquals(true, cell(true));
   }

   @Test
   void bigIntegerAndBigDecimalBeyondADoubleGoOutAsText() throws Exception {
      assertEquals("12345678901234567890", cell(new BigInteger("12345678901234567890")));
      assertEquals("0.1234567890123456789", cell(new BigDecimal("0.1234567890123456789")));
      assertEquals("12345678901234567890.5", cell(new BigDecimal("12345678901234567890.5")));
   }
}
