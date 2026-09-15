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

package inetsoft.sree.schedule;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for Redmine Bug #76653: reported against the closed-source
 * InetSoft Studio product, where the scheduler's own TimeCondition.getRetryTime()
 * converted a task's configured time zone into the server's default zone using
 * TimeZone.getRawOffset() (standard-time offset only, DST-blind). When the
 * server's own default zone does not observe DST but the task's configured
 * zone does, that produced a fire time off by exactly the DST delta.
 *
 * StyleBI's getRetryTime() no longer has this defect: since the Bug #70179 fix
 * (commit 508e32d), it builds its Calendar directly in the task's own
 * TimeZone (Calendar.getInstance(getTimeZone())) instead of converting into
 * the server's zone via a manually computed offset, so DST is handled
 * correctly by Calendar/TimeZone itself. This test exists to guard that
 * behavior against regression, not because a code change was needed here.
 */
@Tag("core")
class TimeConditionDstOffsetTest {
   private TimeZone originalDefault;

   @BeforeEach
   void save() {
      originalDefault = TimeZone.getDefault();
   }

   @AfterEach
   void restore() {
      TimeZone.setDefault(originalDefault);
   }

   @Test
   void retryTimeUsesDstAwareOffsetWhenServerZoneHasNoDst() {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
      TimeZone target = TimeZone.getTimeZone("America/New_York");

      // 2026-07-15 00:00:00 GMT == 2026-07-14 20:00 EDT, before the task's
      // 21:30 EDT run later that same Eastern-time day.
      long lastRun = gmtMillis(2026, Calendar.JULY, 15, 0, 0, 0);

      TimeCondition cond = TimeCondition.at(21, 30, 0);
      cond.setTimeZone(target);
      cond.setInterval(2); // >1 so getRetryTime seeds its calendar from lastRun

      long retryTime = cond.getRetryTime(lastRun, lastRun);

      // 21:30 EDT (UTC-4, DST in effect) == 01:30 GMT the same calendar day.
      long expected = gmtMillis(2026, Calendar.JULY, 15, 1, 30, 0);

      assertEquals(expected, retryTime,
         "getRetryTime() must use America/New_York's DST-active UTC-4 offset, " +
         "not its standard-time UTC-5 raw offset, when the server's own zone has no DST");
   }

   private static long gmtMillis(int year, int month, int day, int hour, int minute, int second) {
      Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
      cal.clear();
      cal.set(year, month, day, hour, minute, second);
      return cal.getTimeInMillis();
   }
}
