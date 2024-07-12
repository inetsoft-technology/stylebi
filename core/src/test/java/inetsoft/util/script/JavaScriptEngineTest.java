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
package inetsoft.util.script;

import org.junit.jupiter.api.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaScriptEngineTest {
   @BeforeAll
   static void before() {
      defaultZone = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("US/Eastern")));
   }

   @AfterAll
   static void after() {
      TimeZone.setDefault(defaultZone);
   }

   @Test
   void test47883() {
      // Bug #47883
      // Script taken from expression column, turns the result into a string representing
      // the difference in time between the two dates. e.g. 4 d 5 h 54 m
      java.util.Date d1 = new Timestamp(toDate("2021-03-05T22:05").getTime());
      java.util.Date d2 = new Timestamp(toDate("2021-03-10T03:59").getTime());

      double D = JavaScriptEngine.dateDiff("d", d1, d2);
      assertEquals(4, D);
      d1 = JavaScriptEngine.dateAdd("d", (int) D, d1);

      double H = JavaScriptEngine.dateDiff("h", d1, d2);
      assertEquals(5, H);
      d1 = JavaScriptEngine.dateAdd("h", (int) H, d1);

      double M = JavaScriptEngine.dateDiff("n", d1, d2);  // n = mins
      assertEquals(54, M);
   }

   @Test
   void DSTBoundaryIs23HourDiff() {
      java.util.Date d1 = toDate("2021-03-13T12:00");
      java.util.Date d2 = toDate("2021-03-14T12:00");
      assertEquals(23, JavaScriptEngine.dateDiff("h", d1, d2));
   }

   @Test
   void DSTBoundaryIs25HourDiff() {
      java.util.Date d1 = toDate("2021-11-06T12:00");
      java.util.Date d2 = toDate("2021-11-07T12:00");
      assertEquals(25, JavaScriptEngine.dateDiff("h", d1, d2));
   }

   @Test
   void noDSTBoundaryIs24HourDiff() {
      java.util.Date d1 = toDate("2021-04-14T12:00");
      java.util.Date d2 = toDate("2021-04-15T12:00");
      assertEquals(24, JavaScriptEngine.dateDiff("h", d1, d2));
   }

   /**
    * @param localDateTime an ISO-8601 datetime string, e.g. 2007-12-03T10:15:30
    *
    * @return the corresponding date in the default time zone.
    */
   private java.util.Date toDate(String localDateTime) {
      return java.util.Date.from(LocalDateTime.parse(localDateTime)
         .atZone(ZoneId.systemDefault())
         .toInstant());
   }

   private static TimeZone defaultZone;
}
