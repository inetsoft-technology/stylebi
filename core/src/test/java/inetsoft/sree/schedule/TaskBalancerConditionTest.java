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

package inetsoft.sree.schedule;

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;

import java.time.*;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TaskBalancerConditionTest {
   TaskBalancerCondition taskBalancerCondition;

   @BeforeEach
   void setUp() {
      SreeEnv.setProperty("schedule.time.ranges", "tr1,1,08:00:00,13:59:00");
      taskBalancerCondition = new TaskBalancerCondition();
   }

   @Test
   void testCheck() {
      long d1= setCustomTimeInMillis(7,59,59);
      boolean check = taskBalancerCondition.check(d1);
      assertTrue(taskBalancerCondition.lastRun > 0);
      assertTrue(check);

      long d2= setCustomTimeInMillis(9,0,0);
      check = taskBalancerCondition.check(d2);
      assertFalse(check);
   }

   @Test
   void testGetRetryTime() {
      long d1= setCustomTimeInMillis(7,59,59);
      long retrytime = taskBalancerCondition.getRetryTime(d1);
      assertFalse(retrytime > d1);

      long d2= setCustomTimeInMillis(9,0,0);
      retrytime = taskBalancerCondition.getRetryTime(d2);
      assertTrue(retrytime > d2);
   }

   private long setCustomTimeInMillis(int hour, int minute, int second) {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime customTime = now.withHour(hour).withMinute(minute).withSecond(second);

      return customTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
   }
}
