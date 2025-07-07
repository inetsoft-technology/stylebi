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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ScheduleAlertTest {

   @Test
   void testBasicMethod() {
      ScheduleAlert scheduleAlert = new ScheduleAlert();
      scheduleAlert.setElementId("testElementId");
      assertEquals("testElementId", scheduleAlert.getElementId());
      scheduleAlert.setHighlightName("testHighlightName");
      assertEquals("testHighlightName", scheduleAlert.getHighlightName());

      ScheduleAlert scheduleAlert2 = new ScheduleAlert();
      assertEquals(1, scheduleAlert2.compareTo(scheduleAlert)); // check elementId is null
      assertEquals(-1, scheduleAlert.compareTo(scheduleAlert2)); // check elementId is null

      scheduleAlert2.setElementId("testElementId2");
      assertEquals(-1, scheduleAlert.compareTo(scheduleAlert2)); // check elementId are diff
      scheduleAlert2.setElementId("testElementId");
      assertEquals(1, scheduleAlert2.compareTo(scheduleAlert)); // check highlight name is null
      assertEquals(-1, scheduleAlert.compareTo(scheduleAlert2)); // check highlight name is null
      scheduleAlert2.setHighlightName("testHighlightName2");
      assertEquals(-1, scheduleAlert.compareTo(scheduleAlert2)); // check highlight are diff

      ScheduleAlert scheduleAlert3 = (ScheduleAlert)scheduleAlert.clone();
      assertEquals(0, scheduleAlert.compareTo(scheduleAlert3)); // check clone is equal
      assertTrue(scheduleAlert3.equals(scheduleAlert)); // check equals method
   }
}
