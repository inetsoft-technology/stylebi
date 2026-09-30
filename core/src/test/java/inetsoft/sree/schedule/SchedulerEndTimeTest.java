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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77297, the end time of a trigger built from a task's end date.
 */
@Tag("core")
class SchedulerEndTimeTest {
   @Test
   void endDateLaterOnTheFireDayIsKept() {
      Date fire = date("2026-09-29T14:00:00", UTC);
      Date end = date("2026-09-29T15:00:00", UTC);

      // before the fix the end time was 14:00, the fire time, and the trigger never fired
      assertEquals(end, Scheduler.getTriggerEndTime(fire, end, UTC));
   }

   @Test
   void stopOnDateStillEndsBeforeTheFireOnThatDay() {
      Date fire = date("2026-09-29T09:30:00", UTC);
      Date stopOn = date("2026-10-05T00:00:00", UTC);

      assertEquals(date("2026-10-05T09:30:00", UTC),
                   Scheduler.getTriggerEndTime(fire, stopOn, UTC));
   }

   @Test
   void endDateOnALaterDayAtAnEarlierTimeOfDayIsMovedToTheFireTimeOfDay() {
      Date fire = date("2026-09-29T14:00:00", UTC);
      Date end = date("2026-09-30T08:00:00", UTC);

      assertEquals(date("2026-09-30T14:00:00", UTC),
                   Scheduler.getTriggerEndTime(fire, end, UTC));
   }

   @Test
   void stopOnDateUsesTheTaskTimeZone() {
      String tz = "America/New_York";
      Date fire = date("2026-09-29T09:30:00", tz);
      Date stopOn = date("2026-10-05T00:00:00", tz);

      assertEquals(date("2026-10-05T09:30:00", tz), Scheduler.getTriggerEndTime(fire, stopOn, tz));
   }

   @Test
   void endDateLaterOnTheFireDayIsKeptInTheTaskTimeZone() {
      String tz = "Asia/Shanghai";
      Date fire = date("2026-09-29T23:00:00", tz);
      Date end = date("2026-09-29T23:30:00", tz);

      assertEquals(end, Scheduler.getTriggerEndTime(fire, end, tz));
   }

   @Test
   void noEndDateHasNoEndTime() {
      assertNull(Scheduler.getTriggerEndTime(date("2026-09-29T14:00:00", UTC), null, UTC));
   }

   private static Date date(String local, String timeZone) {
      return Date.from(LocalDateTime.parse(local).atZone(ZoneId.of(timeZone)).toInstant());
   }

   private static final String UTC = "UTC";
}
