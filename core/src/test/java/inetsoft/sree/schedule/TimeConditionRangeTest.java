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

import inetsoft.test.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #79236. An hourly condition without an end time, whose start is a negative offset from
 * midnight (an hour or minute below 0), repeated the fire that had just happened. The hour, minute
 * and second of a condition that comes from outside the server are range checked by
 * {@link TimeCondition#validateRanges()}, and the walk no longer returns an instant before curr
 * for an empty window.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeConditionRangeTest {
   private static final long PREV =
      ZonedDateTime.of(2027, 7, 1, 23, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli();

   private static TimeCondition hourly(int hour, int minute, String end) throws Exception {
      return parse("<Condition type=\"TimeCondition\" timeType=\"8\" timezone=\"0:UTC\" hour=\"" +
                   hour + "\" minute=\"" + minute + "\" second=\"0\" " + end +
                   " daysOfWeek=\"1,2,3,4,5,6,7\" hourlyInterval=\"0.5\"/>");
   }

   private static TimeCondition parse(String xml) throws Exception {
      Element element = Tool.parseXML(new StringReader(xml)).getDocumentElement();
      TimeCondition condition = new TimeCondition();
      condition.parseXML(element);
      return condition;
   }

   /** The fire after the one at prev, as TimeConditionTriggerImpl.getFireTimeAfter computes it. */
   private static long next(TimeCondition condition, long prev) {
      return condition.getRetryTime(prev + 30000, prev, null, prev);
   }

   private static String utc(long millis) {
      return Instant.ofEpochMilli(millis).toString();
   }

   private static TimeCondition condition(int type, int hour, int minute, int second) {
      TimeCondition condition = new TimeCondition();
      condition.setType(type);
      condition.setHour(hour);
      condition.setMinute(minute);
      condition.setSecond(second);
      return condition;
   }

   @ParameterizedTest(name = "hour {0} minute {1}")
   @CsvSource({ "-1,30", "0,-30", "2,-150", "0,-1", "-1,0", "-1,-1" })
   void negativeStartWithoutEndNeverRepeatsTheFire(int hour, int minute) throws Exception {
      TimeCondition condition = hourly(hour, minute, "");
      long prev = PREV;

      for(int i = 0; i < 4; i++) {
         long next = next(condition, prev);
         assertTrue(next > prev, "hour " + hour + " minute " + minute + ": " + utc(prev) +
            " is followed by " + utc(next));
         prev = next;
      }
   }

   @ParameterizedTest(name = "hour {0} minute {1}")
   @CsvSource({ "0,30", "5,0", "23,30", "0,0" })
   void inRangeStartWithoutEndRunsOnTheNextDate(int hour, int minute) throws Exception {
      TimeCondition condition = hourly(hour, minute, "");
      long prev = PREV;

      for(int i = 0; i < 3; i++) {
         long next = next(condition, prev);
         ZonedDateTime expected =
            ZonedDateTime.of(2027, 7, 2 + i, hour, minute, 0, 0, ZoneOffset.UTC);
         assertEquals(expected.toInstant().toEpochMilli(), next,
                      "got " + utc(next) + " after " + utc(prev));
         prev = next;
      }
   }

   @Test
   void negativeStartWithAnEndKeepsItsWindow() throws Exception {
      // the window of the next date is not empty, so the fire after 23:30 is 00:00 (07-02)
      TimeCondition condition = hourly(-1, 30, "hour_end=\"23\" minute_end=\"0\" second_end=\"0\"");
      long prev = PREV;

      for(int i = 1; i <= 3; i++) {
         long next = next(condition, prev);
         assertEquals(prev + 30 * 60000L, next, utc(prev) + " is followed by " + utc(next));
         prev = next;
      }
   }

   @Test
   void storedFormOfAnUnsetEndKeepsItsWindow() throws Exception {
      TimeCondition condition =
         hourly(-1, 30, "hour_end=\"23\" minute_end=\"-1\" second_end=\"-1\"");
      assertEquals(PREV + 30 * 60000L, next(condition, PREV));
   }

   @ParameterizedTest(name = "type {0}")
   @CsvSource({ "1", "2", "6", "7", "8" })
   void validateRangesRejectsAStartOutOfRange(int type) {
      int[][] bad = { { -1, 0, 0 }, { 24, 0, 0 }, { 90, 0, 0 }, { 0, -1, 0 }, { 0, 60, 0 },
                      { 0, 0, -1 }, { 0, 0, 60 }, { -30, 0, 0 } };

      for(int[] time : bad) {
         TimeCondition condition = condition(type, time[0], time[1], time[2]);
         assertThrows(IllegalArgumentException.class, condition::validateRanges,
                      "type " + type + " time " + time[0] + ":" + time[1] + ":" + time[2]);
      }

      int[][] good = { { 0, 0, 0 }, { 23, 59, 59 }, { 12, 30, 0 } };

      for(int[] time : good) {
         condition(type, time[0], time[1], time[2]).validateRanges();
      }
   }

   @Test
   void validateRangesChecksTheEndOfAnHourlyConditionFieldByField() {
      TimeCondition condition = condition(TimeCondition.EVERY_HOUR, 1, 0, 0);

      // no end, and the stored form of no end
      condition.setHourEnd(-1);
      condition.setMinuteEnd(-1);
      condition.setSecondEnd(-1);
      condition.validateRanges();
      condition.setHourEnd(23);
      condition.validateRanges();
      condition.setHourEnd(0);
      condition.setMinuteEnd(59);
      condition.setSecondEnd(59);
      condition.validateRanges();

      for(int[] end : new int[][] { { 24, 0, 0 }, { 5, 60, 0 }, { 5, 0, 60 }, { -2, 0, 0 },
                                    { 5, -2, 0 }, { 5, 0, -2 } })
      {
         condition.setHourEnd(end[0]);
         condition.setMinuteEnd(end[1]);
         condition.setSecondEnd(end[2]);
         assertThrows(IllegalArgumentException.class, condition::validateRanges,
                      end[0] + ":" + end[1] + ":" + end[2]);
      }
   }

   @Test
   void validateRangesIgnoresTheEndOfOtherTypesAndTheTimeOfAnAtCondition() {
      TimeCondition daily = condition(TimeCondition.EVERY_DAY, 1, 0, 0);
      daily.setHourEnd(99);
      daily.validateRanges();

      condition(TimeCondition.AT, -1, -1, -1).validateRanges();
   }

   @Test
   void parseXmlStillAcceptsAnOutOfRangeStoredCondition() throws Exception {
      // a stored task must still load: a parse that throws makes ScheduleTaskMap skip the task
      TimeCondition condition = hourly(-1, 30, "");
      assertEquals(-1, condition.getHour());
      assertThrows(IllegalArgumentException.class, condition::validateRanges);
      assertDoesNotThrow(() -> hourly(90, 70, "hour_end=\"-1\" minute_end=\"-1\" second_end=\"-1\""));
   }

   @Test
   void taskValidatesItsTimeConditionsAndIgnoresOthers() {
      ScheduleTask task = new ScheduleTask("range");
      task.addCondition(condition(TimeCondition.EVERY_DAY, 1, 0, 0));
      task.addCondition(new CompletionCondition("other"));
      task.validateTimeConditions();

      task.addCondition(condition(TimeCondition.EVERY_HOUR, -1, 30, 0));
      assertThrows(IllegalArgumentException.class, task::validateTimeConditions);
   }
}
