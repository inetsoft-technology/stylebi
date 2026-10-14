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
package inetsoft.util.pojava.datetime;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78529: DateTime.parse validated February 29 with the Julian rule (every year divisible
 * by 4), so 1900-02-29 and 2100-02-29 passed and were rolled over to March 1 by the
 * GregorianCalendar that builds the value. It now follows that calendar's own leap rule.
 */
@Tag("core")
class DateTimeLeapYearTest {
   @ParameterizedTest
   @ValueSource(strings = { "2100-02-29", "1900-02-29", "2200-02-29", "2023-02-29",
                            "2100-02-29 10:00:00", "02/29/2100", "02/29/1900 10:00:00",
                            "{d '2100-02-29'}" })
   void refusesFebruary29OfACommonYear(String value) {
      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> DateTime.parse(value, config()), value);
      assertTrue(ex.getMessage().startsWith("February 29 does not exist"), ex.getMessage());
   }

   @ParameterizedTest
   @ValueSource(strings = { "2000-02-29", "2024-02-29", "2400-02-29", "1600-02-29",
                            "2000-02-29 10:00:00", "02/29/2024" })
   void keepsFebruary29OfALeapYear(String value) {
      assertEquals("02-29", format("MM-dd", DateTime.parse(value, config())), value);
   }

   // the Julian calendar is in effect before the 1582 cutover, so 1500 is a leap year there
   @ParameterizedTest
   @ValueSource(strings = { "1500-02-29", "1100-02-29" })
   void keepsFebruary29OfAJulianLeapYear(String value) {
      int year = Integer.parseInt(value.substring(0, 4));
      assertTrue(new GregorianCalendar().isLeapYear(year));
      assertEquals(value, format("yyyy-MM-dd", DateTime.parse(value, config())));
   }

   // parsed and formatted in the default time zone
   private static String format(String pattern, DateTime dt) {
      return new SimpleDateFormat(pattern).format(dt.toDate());
   }

   // month first, not the global default, which another test can leave day first
   private static IDateTimeConfig config() {
      return DateTimeConfig.fromBuilder(DateTimeConfigBuilder.newInstance().dmyOrder(false));
   }
}
