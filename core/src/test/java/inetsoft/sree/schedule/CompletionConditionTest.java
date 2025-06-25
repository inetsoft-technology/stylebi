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
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

public class CompletionConditionTest {
   CompletionCondition completionCondition;
   long d1= setCustomTimeInMillis(7,59,59);

   @Test
   void testCheck() {
      completionCondition = new CompletionCondition();
      completionCondition.setComplete(true);

      boolean check  =  completionCondition.check(d1);
      assertTrue(check);
      assertTrue(completionCondition.toString().contains("null"));

      SreeEnv.setProperty("security.enabled", "true");
      completionCondition = new CompletionCondition("org1:task1");
      completionCondition.setComplete(false);
      check  =  completionCondition.check(d1);
      assertFalse(check);
      assertTrue(completionCondition.toString().contains("org1:task1"));
      assertEquals("org1:task1", completionCondition.getTaskName());
   }

   @Test
   void testGetRetry() {
      completionCondition = new CompletionCondition();
      completionCondition.setTaskName("org2:task2");
      assertEquals(-1, completionCondition.getRetryTime(d1));

      completionCondition.setComplete(true);
      assertEquals(d1, completionCondition.getRetryTime(d1));

   }

   private long setCustomTimeInMillis(int hour, int minute, int second) {
      LocalDateTime now = LocalDateTime.now();
      LocalDateTime customTime = now.withHour(hour).withMinute(minute).withSecond(second);

      return customTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
   }
}
