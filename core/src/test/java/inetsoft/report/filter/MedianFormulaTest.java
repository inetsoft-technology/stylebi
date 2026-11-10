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
package inetsoft.report.filter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MedianFormula} over object values, including dates.
 */
@Tag("core")
class MedianFormulaTest {
   private static Object medianOf(Object... values) {
      MedianFormula formula = new MedianFormula();

      for(Object v : values) {
         formula.addValue(v);
      }

      return formula.getResult();
   }

   @Test
   void getResult_oddDateCount_returnsMiddleDateByTime() {
      java.sql.Date jan = java.sql.Date.valueOf("2024-01-01");
      java.sql.Date feb = java.sql.Date.valueOf("2024-02-01");
      java.sql.Date mar = java.sql.Date.valueOf("2024-03-01");

      assertEquals(feb, medianOf(mar, jan, feb));
      assertEquals(feb, medianOf(feb, mar, jan));
   }

   @Test
   void getResult_oddTimestampCount_returnsMiddleTimestampByTime() {
      Timestamp t1 = Timestamp.valueOf("2024-01-01 08:00:00");
      Timestamp t2 = Timestamp.valueOf("2024-01-01 09:00:00");
      Timestamp t3 = Timestamp.valueOf("2024-01-01 10:00:00");

      assertEquals(t2, medianOf(t3, t1, t2));
   }

   @Test
   void getResult_evenDateCount_returnsNull() {
      // there is no midpoint of two dates, so an even count stays null
      assertNull(medianOf(java.sql.Date.valueOf("2024-03-01"),
                          java.sql.Date.valueOf("2024-01-01"),
                          java.sql.Date.valueOf("2024-02-01"),
                          java.sql.Date.valueOf("2024-04-01")));
   }

   @Test
   void getResult_numbers_unchanged() {
      assertEquals(2.0, medianOf(3, 1, 2));
      assertEquals(2.5, medianOf(4, 1, 3, 2));
   }

   @Test
   void getResult_blankStrings_areSkipped() {
      assertEquals(2.0, medianOf("1", "", "3"));
      assertEquals(2.0, medianOf("1", "  ", "3"));
      assertEquals(2.5, medianOf("1", "", "2", "3", "4"));
   }
}
