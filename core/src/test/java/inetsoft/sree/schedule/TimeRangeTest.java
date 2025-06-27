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

import java.io.IOException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TimeRangeTest {

   @Test
   void testInitTimeRange() {
      TimeRange timeRange = new TimeRange("tr1", "09:00:00", "14:00:00", false);

      assertEquals("tr1", timeRange.getName());
      assertEquals("09:00", timeRange.getStartTime().toString());
      assertEquals("14:00", timeRange.getEndTime().toString());
      assertFalse( timeRange.isDefault());

      //2. check compareTo
      TimeRange timeRange2 = new TimeRange();
      timeRange.setDefault(true);
      assertEquals(-1, timeRange.compareTo(timeRange2));

      timeRange.setDefault(false);
      timeRange2.setDefault(true);
      assertEquals(1, timeRange.compareTo(timeRange2));

      timeRange2.setDefault(false);
      timeRange2.setStartTime(timeRange.getStartTime());
      timeRange2.setEndTime(LocalTime.parse("15:00:00"));
      assertEquals(-1, timeRange.compareTo(timeRange2));

      // 3. equals
      assertFalse(timeRange.equals(timeRange2));
   }

   @Test
   void  testSetTimeRanges() throws IOException {
      TimeRange timeRange3 = new TimeRange("tr2", "20:00:00", "09:00:00", true);

      SreeEnv.setProperty("schedule.time.ranges", null);
      TimeRange.setTimeRanges(null);  //check null
      assertFalse(SreeEnv.getProperty("schedule.time.ranges").contains("tr1"));

      Throwable exception = assertThrows(
         IllegalArgumentException.class, () -> {
            TimeRange.setTimeRanges(Arrays.asList(timeRange2, timeRange3));  // check same name
      });
      assertEquals("Duplicate time range names", exception.getMessage());

      exception = assertThrows(
         IllegalArgumentException.class, () -> {
            TimeRange.setTimeRanges(Arrays.asList(timeRange1, timeRange3));  // check default number > 1
         });
      assertEquals("Multiple default time ranges", exception.getMessage());

      TimeRange.setTimeRanges(Arrays.asList(timeRange1, timeRange2)); // check set ok
      assertEquals("tr1,1,09:00:00,14:00:00;tr2,0,14:00:00,20:00:00", SreeEnv.getProperty("schedule.time.ranges"));
   }

   @Test
   void testGetMatchingTimeRange() throws IOException {
      List<TimeRange> timeRanges = Arrays.asList(timeRange1, timeRange2);

      TimeRange.setTimeRanges(Arrays.asList(timeRange1, timeRange2));
      assertEquals("tr1",TimeRange.getMatchingTimeRange(timeRange1, timeRanges).getName());

      TimeRange timeRange3 = new TimeRange("tr3", "20:01:01", "10:00:00", true);
      assertEquals("tr1",TimeRange.getMatchingTimeRange(timeRange3, timeRanges).getName());

      TimeRange timeRange4 = new TimeRange("tr4", "21:00:00", "23:59:59", true);
      assertEquals("tr2", TimeRange.getMatchingTimeRange(timeRange4, timeRanges).getName());
   }

   private final TimeRange timeRange1 = new TimeRange("tr1", "09:00:00", "14:00:00", true);
   private final TimeRange timeRange2 = new TimeRange("tr2", "14:00:00", "20:00:00", false);
}
