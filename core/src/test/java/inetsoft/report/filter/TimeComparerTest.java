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
package inetsoft.report.filter;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Time;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #82477: TimeComparer ignored milliseconds, so a TIME value with ms matched a condition
 * without ms. Axis: ordering/equality over (java.sql.Time, java.util.Date, java.sql.Timestamp,
 * String) operands, with and without ms, and other-day / negative-epoch dates.
 */
@Tag("core")
class TimeComparerTest {
   private final TimeComparer comparer = new TimeComparer(new SimpleDateFormat("HH:mm:ss"));

   private static Time time(int h, int m, int s, int ms) {
      Calendar c = Calendar.getInstance();
      c.clear();
      c.set(1970, Calendar.JANUARY, 1, h, m, s);
      c.set(Calendar.MILLISECOND, ms);
      return new Time(c.getTimeInMillis());
   }

   @Test
   void millisecondsDistinguishEqualSeconds() {
      assertTrue(comparer.compare(time(0, 0, 0, 500), time(0, 0, 0, 0)) > 0);
      assertTrue(comparer.compare(time(0, 0, 0, 0), time(0, 0, 0, 500)) < 0);
      assertTrue(comparer.compare(time(0, 0, 0, 250), time(0, 0, 0, 500)) < 0);
      assertTrue(comparer.compare(time(13, 45, 30, 400), time(13, 45, 30, 300)) > 0);
   }

   @Test
   void identicalIncludingMillisAreEqual() {
      assertEquals(0, comparer.compare(time(13, 45, 30, 123), time(13, 45, 30, 123)));
      assertEquals(0, comparer.compare(time(0, 0, 0, 0), time(0, 0, 0, 0)));
   }

   @Test
   void secondsStillDominateMillis() {
      assertTrue(comparer.compare(time(0, 0, 1, 0), time(0, 0, 0, 999)) > 0);
      assertTrue(comparer.compare(time(0, 0, 0, 999), time(0, 0, 1, 0)) < 0);
      assertTrue(comparer.compare(time(1, 0, 0, 0), time(0, 59, 59, 999)) > 0);
   }

   @Test
   void mixedOperandTypes() {
      Time t = time(10, 20, 30, 250);
      Date d = new Date(t.getTime());
      Timestamp ts = new Timestamp(t.getTime());

      assertEquals(0, comparer.compare(t, d));
      assertEquals(0, comparer.compare(t, ts));
      assertEquals(0, comparer.compare(d, ts));

      Timestamp ts2 = new Timestamp(t.getTime() + 1);
      assertTrue(comparer.compare(ts2, t) > 0);
      assertTrue(comparer.compare(t, ts2) < 0);
   }

   @Test
   void stringOperandHasNoMillis() {
      assertEquals(0, comparer.compare(time(0, 0, 0, 0), "00:00:00"));
      assertTrue(comparer.compare(time(0, 0, 0, 500), "00:00:00") > 0);
      assertTrue(comparer.compare("00:00:00", time(0, 0, 0, 500)) < 0);
   }

   @Test
   void otherDayDatesCompareTimeOfDayOnly() {
      Calendar c = Calendar.getInstance();
      c.set(2024, Calendar.MARCH, 5, 8, 9, 10);
      c.set(Calendar.MILLISECOND, 700);
      Date other = c.getTime();
      assertEquals(0, comparer.compare(other, time(8, 9, 10, 700)));
      assertTrue(comparer.compare(other, time(8, 9, 10, 100)) > 0);
   }

   @Test
   void negativeEpochMillisUseFloorMod() {
      Date a = new Date(-250L);
      Date b = new Date(-750L);
      assertTrue(comparer.compare(a, b) > 0);
      assertTrue(comparer.compare(b, a) < 0);
      assertEquals(0, comparer.compare(a, new Date(-250L)));
   }

   @Test
   void sqlDateAndNullGuards() {
      assertEquals(0, comparer.compare(null, null));
      assertTrue(comparer.compare(null, time(0, 0, 0, 0)) < 0);
      assertTrue(comparer.compare(time(0, 0, 0, 0), null) > 0);
      // java.sql.Date still falls back to the string comparison
      java.sql.Date sd = new java.sql.Date(0L);
      assertEquals(0, comparer.compare(sd, new java.sql.Date(0L)));
   }
}
