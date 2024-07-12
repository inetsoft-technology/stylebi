/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.util;

import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SreeHome
public class ExtendedDateFormatTest {
   @Test
   public void localTime() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_TIME_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(1970, 1, 1, 1, 2, 3)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("01:02:03", null);

      assertEquals(expected, actual);
   }

   @Test
   public void localDate() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_DATE_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(2020, 1, 2, 0, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("2020-01-02", null);

      assertEquals(expected, actual);
   }

   @Test
   public void localDateTime() {
      final ExtendedDateFormat format = new ExtendedDateFormat(Tool.DEFAULT_DATETIME_PATTERN);
      final Date expected = Date.from(LocalDateTime.of(2020, 1, 2, 1, 2, 3)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("2020-01-02 01:02:03", null);

      assertEquals(expected, actual);
   }

   @Test
   public void testQQ() {
      final ExtendedDateFormat format = new ExtendedDateFormat("'Q'QQ'('MMM')'");
      final Date date = Date.from(LocalDateTime.of(2020, 2, 2, 1, 2, 3)
                                     .atZone(ZoneId.systemDefault()).toInstant());
      final String actual = format.format(date);

      assertEquals("Q1(Feb)", actual);
   }

   @Test
   public void monthYear() {
      final ExtendedDateFormat format = new ExtendedDateFormat("yyyy-M");
      final Date expected = Date.from(LocalDateTime.of(2020, 2, 1, 0, 0, 0)
                                         .atZone(ZoneId.systemDefault()).toInstant());
      final Date actual = format.parse("2020-02", null);

      assertEquals(expected, actual);
   }
}
