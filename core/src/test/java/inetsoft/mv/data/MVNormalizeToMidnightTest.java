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
package inetsoft.mv.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Verifies {@code MV.normalizeToMidnight(Date)}: two timestamps on the same local calendar day
 * must normalize to the same midnight instant, including across a DST spring-forward/fall-back
 * transition day, so date-typed measure columns compare/aggregate correctly regardless of the
 * time-of-day component.
 */
@Tag("core")
class MVNormalizeToMidnightTest {
   @BeforeEach
   void setUp() {
      originalDefault = TimeZone.getDefault();
      // a zone that actually observes DST, so the transition-day scenarios are meaningful.
      TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
   }

   @AfterEach
   void tearDown() {
      TimeZone.setDefault(originalDefault);
   }

   @Test
   void normalizesDifferentTimesOnTheSameDayToTheSameMidnight() throws Exception {
      Date morning = dateAt(2024, Calendar.JANUARY, 15, 8, 15, 0);
      Date night = dateAt(2024, Calendar.JANUARY, 15, 23, 59, 59);

      assertEquals(normalizeToMidnight(morning), normalizeToMidnight(night));
   }

   @Test
   void normalizesDifferentCalendarDaysToDifferentMidnights() throws Exception {
      Date day1 = dateAt(2024, Calendar.JANUARY, 15, 12, 0, 0);
      Date day2 = dateAt(2024, Calendar.JANUARY, 16, 0, 0, 1);

      assertNotEquals(normalizeToMidnight(day1), normalizeToMidnight(day2));
   }

   @Test
   void doesNotShiftDaysAcrossTheSpringForwardDstTransition() throws Exception {
      // 2024-03-10: America/New_York springs forward at 2:00am -> 3:00am.
      Date beforeTransition = dateAt(2024, Calendar.MARCH, 10, 1, 30, 0);
      Date afterTransition = dateAt(2024, Calendar.MARCH, 10, 22, 0, 0);

      long midnight = normalizeToMidnight(beforeTransition);
      assertEquals(midnight, normalizeToMidnight(afterTransition));
      assertEquals(expectedMidnight(2024, Calendar.MARCH, 10), midnight);
   }

   @Test
   void doesNotShiftDaysAcrossTheFallBackDstTransition() throws Exception {
      // 2024-11-03: America/New_York falls back at 2:00am -> 1:00am.
      Date beforeTransition = dateAt(2024, Calendar.NOVEMBER, 3, 0, 30, 0);
      Date afterTransition = dateAt(2024, Calendar.NOVEMBER, 3, 22, 0, 0);

      long midnight = normalizeToMidnight(beforeTransition);
      assertEquals(midnight, normalizeToMidnight(afterTransition));
      assertEquals(expectedMidnight(2024, Calendar.NOVEMBER, 3), midnight);
   }

   private static long expectedMidnight(int year, int month, int day) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month, day, 0, 0, 0);
      return cal.getTimeInMillis();
   }

   private static Date dateAt(int year, int month, int day, int hour, int minute, int second) {
      Calendar cal = Calendar.getInstance();
      cal.clear();
      cal.set(year, month, day, hour, minute, second);
      return cal.getTime();
   }

   private static long normalizeToMidnight(Date date) throws Exception {
      Method method = MV.class.getDeclaredMethod("normalizeToMidnight", Date.class);
      method.setAccessible(true);
      return (long) method.invoke(null, date);
   }

   private TimeZone originalDefault;
}
