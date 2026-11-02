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

package inetsoft.web.admin.schedule;

import inetsoft.sree.schedule.TimeCondition;
import inetsoft.util.MessageException;
import inetsoft.web.viewsheet.model.dialog.schedule.TimeConditionModel;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79236, the EM condition editor builds a condition from a client model. An hour, minute or
 * second out of range (a daily condition every 2 days with hour -30 repeats its fire) is refused
 * for every type, and the same model in range is converted.
 */
@Tag("core")
class ScheduleConditionServiceRangeTest {
   private final ScheduleConditionService service = new ScheduleConditionService();

   private static TimeConditionModel model(int type, int hour, int minute, int second,
                                           int interval)
   {
      return TimeConditionModel.builder()
         .label("t")
         .type(type)
         .hour(hour)
         .minute(minute)
         .second(second)
         .interval(interval)
         .daysOfWeek(new int[] { 1, 2, 3 })
         .monthsOfYear(new int[] { 0, 1 })
         .dayOfMonth(1)
         .monthlyDaySelected(true)
         .build();
   }

   @Test
   void dailyEveryTwoDaysWithHourMinus30_isRefused() {
      assertThrows(MessageException.class, () -> service.getConditionFromModel(
         model(TimeCondition.EVERY_DAY, -30, 0, 0, 2)));
   }

   @Test
   void everyTypeRefusesAStartOutOfRange() {
      for(int type : new int[] { TimeCondition.EVERY_DAY, TimeCondition.EVERY_WEEK,
                                 TimeCondition.EVERY_MONTH, TimeCondition.EVERY_HOUR })
      {
         for(int[] time : new int[][] { { -1, 0, 0 }, { 24, 0, 0 }, { 0, -1, 0 }, { 0, 60, 0 },
                                        { 0, 0, -1 }, { 0, 0, 60 } })
         {
            assertThrows(MessageException.class, () -> service.getConditionFromModel(
               model(type, time[0], time[1], time[2], 1)), type + " " + time[0] + ":" + time[1]);
         }
      }
   }

   @Test
   void everyTypeConvertsAStartInRange() throws Exception {
      for(int type : new int[] { TimeCondition.EVERY_DAY, TimeCondition.EVERY_WEEK,
                                 TimeCondition.EVERY_MONTH, TimeCondition.EVERY_HOUR })
      {
         TimeCondition condition = (TimeCondition) service.getConditionFromModel(
            model(type, 23, 59, 59, 2));
         assertEquals(23, condition.getHour());
         assertEquals(59, condition.getMinute());
      }

      TimeCondition midnight = (TimeCondition) service.getConditionFromModel(
         model(TimeCondition.EVERY_DAY, 0, 0, 0, 2));
      assertEquals(0, midnight.getHour());
   }

   @Test
   void endOfAnHourlyConditionIsChecked() {
      TimeConditionModel bad = TimeConditionModel.builder()
         .label("t").type(TimeCondition.EVERY_HOUR).hour(1).minute(0).second(0)
         .hourEnd(24).minuteEnd(0).secondEnd(0).hourlyInterval(1f)
         .daysOfWeek(new int[] { 1 }).build();
      assertThrows(MessageException.class, () -> service.getConditionFromModel(bad));
   }
}
