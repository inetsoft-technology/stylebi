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
package inetsoft.sree.schedule.quartz;

import inetsoft.sree.schedule.TaskBalancerCondition;
import inetsoft.sree.schedule.TimeCondition;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.quartz.TriggerBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Misfire handling of the condition triggers: what {@code updateAfterMisfire} does to the next
 * fire time, how the schedule continues from there, and the misfire instruction plumbing.
 * Time inputs are relative to the wall clock because the code under test reads it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ConditionTriggerMisfireTest {
   private static final long HOUR = 3600000L;
   private static final long DAY = 24 * HOUR;
   private static final long GETFIRETIME_OFFSET = 30000L;

   @Test
   void fireOnceNowMovesTheNextFireTimeToNow() {
      TimeConditionTriggerImpl trigger = hourlyTrigger("UTC", 0);
      trigger.setMisfireInstruction(1);
      trigger.setNextFireTime(new Date(System.currentTimeMillis() - 3 * HOUR));

      Date before = new Date();
      trigger.updateAfterMisfire(null);
      Date after = new Date();

      assertInWindow(trigger.getNextFireTime(), before, after);
   }

   @Test
   void smartPolicyBehavesLikeFireOnceNow() {
      TimeConditionTriggerImpl smart = hourlyTrigger("UTC", 0);
      TimeConditionTriggerImpl fireOnce = hourlyTrigger("UTC", 0);
      smart.setMisfireInstruction(0);
      fireOnce.setMisfireInstruction(1);
      smart.setNextFireTime(new Date(System.currentTimeMillis() - 3 * HOUR));
      fireOnce.setNextFireTime(new Date(System.currentTimeMillis() - 3 * HOUR));

      Date before = new Date();
      smart.updateAfterMisfire(null);
      fireOnce.updateAfterMisfire(null);
      Date after = new Date();

      assertInWindow(smart.getNextFireTime(), before, after);
      assertInWindow(fireOnce.getNextFireTime(), before, after);
   }

   @Test
   void hourlyTriggerMissedSeveralTimesFiresOnceAndContinuesWithTheNextSlot() {
      TimeConditionTriggerImpl trigger = hourlyTrigger("UTC", 0);
      trigger.setMisfireInstruction(1);
      trigger.setNextFireTime(new Date(System.currentTimeMillis() - 3 * HOUR));

      Date before = new Date();
      trigger.updateAfterMisfire(null);
      Date after = new Date();
      Date misfireInstant = trigger.getNextFireTime();
      assertInWindow(misfireInstant, before, after);

      trigger.triggered(null);

      assertEquals(misfireInstant, trigger.getPreviousFireTime(),
                   "the single compensating fire is the misfire instant");
      long next = trigger.getNextFireTime().getTime();
      assertTrue(next > before.getTime() + GETFIRETIME_OFFSET,
                 "the next slot must be after the compensating fire, not one of the missed ones");
      assertTrue(next <= after.getTime() + HOUR + GETFIRETIME_OFFSET,
                 "the next slot must be the very next hourly one: " + new Date(next));
   }

   @Test
   void dailyTriggerMissedSeveralDaysFiresOnceAndContinuesWithTheNextSlot() {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName("daily-1");
      TimeCondition daily = TimeCondition.at(3, 0, 0);
      daily.setInterval(1);
      daily.setTimeZone(TimeZone.getTimeZone("UTC"));
      trigger.setCondition(daily);
      trigger.setMisfireInstruction(1);
      trigger.setNextFireTime(new Date(System.currentTimeMillis() - 3 * DAY));

      Date before = new Date();
      trigger.updateAfterMisfire(null);
      Date after = new Date();
      Date misfireInstant = trigger.getNextFireTime();
      assertInWindow(misfireInstant, before, after);

      trigger.triggered(null);

      assertEquals(misfireInstant, trigger.getPreviousFireTime());
      long next = trigger.getNextFireTime().getTime();
      assertTrue(next > before.getTime() + GETFIRETIME_OFFSET, new Date(next).toString());
      assertTrue(next <= after.getTime() + DAY + GETFIRETIME_OFFSET, new Date(next).toString());
   }

   @Test
   void runOnceConditionInThePastFiresOnceAndThenHasNoNextFireTime() {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName("at-past-1");
      trigger.setCondition(TimeCondition.at(new Date(System.currentTimeMillis() - HOUR)));
      trigger.setMisfireInstruction(1);
      trigger.setNextFireTime(new Date(System.currentTimeMillis() - HOUR));

      Date before = new Date();
      trigger.updateAfterMisfire(null);
      Date after = new Date();
      assertInWindow(trigger.getNextFireTime(), before, after);

      trigger.triggered(null);

      assertNull(trigger.getNextFireTime(),
                 "a run-once condition in the past must have no further fire time");
   }

   @Test
   void runOnceConditionInTheFutureReturnsItsInstant() {
      Date at = new Date(System.currentTimeMillis() + HOUR);
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName("at-future-1");
      trigger.setCondition(TimeCondition.at(at));

      assertEquals(at, trigger.getFireTimeAfter(new Date()));
   }

   @Test
   void pinnedCurrentBehaviorNoConditionOrNoReferenceTimeGivesNoFireTime() {
      TimeConditionTriggerImpl time = new TimeConditionTriggerImpl();
      TaskBalancerConditionTriggerImpl balancer = new TaskBalancerConditionTriggerImpl();

      assertNull(time.getFireTimeAfter(null));
      assertNull(time.getFireTimeAfter(new Date()), "no condition set");
      assertNull(balancer.getFireTimeAfter(null));
      assertNull(balancer.getFireTimeAfter(new Date()), "no condition set");

      time.setCondition(TimeCondition.at(1, 0, 0));
      balancer.setCondition(new TaskBalancerCondition());

      assertNull(time.getFireTimeAfter(null), "no reference time");
      assertNull(balancer.getFireTimeAfter(null), "no reference time");
   }

   @ParameterizedTest(name = "misfire instruction {0} is accepted")
   @ValueSource(ints = { -1, 0, 1 })
   void acceptsTheDocumentedMisfireInstructions(int instruction) {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setMisfireInstruction(instruction);

      assertEquals(instruction, trigger.getMisfireInstruction());
   }

   @ParameterizedTest(name = "misfire instruction {0} is rejected")
   @ValueSource(ints = { 2, -2 })
   void rejectsMisfireInstructionsOutsideTheSupportedRange(int instruction) {
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();

      assertThrows(IllegalArgumentException.class, () -> trigger.setMisfireInstruction(instruction));
   }

   @Test
   void scheduleBuilderMapsMisfireHandlingToTheInstructions() {
      assertEquals(0, buildTrigger(TimeConditionScheduleBuilder.timeConditionSchedule())
                      .getMisfireInstruction(), "default is the smart policy");
      assertEquals(1, buildTrigger(TimeConditionScheduleBuilder.timeConditionSchedule()
                                      .withMisfireHandlingInstructionFireOnceNow())
                      .getMisfireInstruction());
      assertEquals(-1, buildTrigger(TimeConditionScheduleBuilder.timeConditionSchedule()
                                       .withMisfireHandlingInstructionIgnoreMisfires())
                      .getMisfireInstruction());
   }

   @ParameterizedTest(name = "misfired hourly and daily triggers keep the wall time of zone {0}")
   @ValueSource(strings = { "Asia/Kolkata", "America/New_York" })
   void nextFireTimeAfterMisfireHonorsTheConditionTimeZone(String zoneId) {
      ZoneId zone = ZoneId.of(zoneId);
      TimeConditionTriggerImpl hourly = hourlyTrigger(zoneId, 20);
      TimeConditionTriggerImpl daily = new TimeConditionTriggerImpl();
      daily.setName("daily-zone-1");
      TimeCondition dailyCondition = TimeCondition.at(9, 15, 0);
      dailyCondition.setInterval(1);
      dailyCondition.setTimeZone(TimeZone.getTimeZone(zoneId));
      daily.setCondition(dailyCondition);

      for(TimeConditionTriggerImpl trigger : new TimeConditionTriggerImpl[] { hourly, daily }) {
         trigger.setMisfireInstruction(1);
         trigger.setNextFireTime(new Date(System.currentTimeMillis() - 3 * HOUR));
         long before = System.currentTimeMillis();
         trigger.updateAfterMisfire(null);
         trigger.triggered(null);

         assertTrue(trigger.getNextFireTime().getTime() > before + GETFIRETIME_OFFSET,
                    trigger.getNextFireTime().toString());
      }

      ZonedDateTime hourlyNext = hourly.getNextFireTime().toInstant().atZone(zone);
      assertEquals(20, hourlyNext.getMinute(), hourlyNext.toString());
      assertEquals(0, hourlyNext.getSecond());
      ZonedDateTime dailyNext = daily.getNextFireTime().toInstant().atZone(zone);
      assertEquals(9, dailyNext.getHour(), dailyNext.toString());
      assertEquals(15, dailyNext.getMinute());
   }

   /** An hourly condition that ticks every hour, all day, on every day of the week. */
   private static TimeConditionTriggerImpl hourlyTrigger(String zoneId, int minute) {
      TimeCondition condition = TimeCondition.atHours(
         new int[] { 1, 2, 3, 4, 5, 6, 7 }, 0, minute, 0);
      condition.setHourlyInterval(1f);
      condition.setHourEnd(23);
      condition.setMinuteEnd(59);
      condition.setSecondEnd(59);
      condition.setTimeZone(TimeZone.getTimeZone(zoneId));
      TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
      trigger.setName("hourly-1");
      trigger.setCondition(condition);
      return trigger;
   }

   private static AbstractConditionTrigger<?, ?> buildTrigger(
      AbstractConditionScheduleBuilder<TimeCondition, TimeConditionTrigger> builder)
   {
      builder.condition(TimeCondition.at(1, 0, 0));
      return (AbstractConditionTrigger<?, ?>) TriggerBuilder.newTrigger()
         .withSchedule(builder)
         .build();
   }

   private static void assertInWindow(Date actual, Date before, Date after) {
      assertNotNull(actual);
      assertTrue(!actual.before(before) && !actual.after(after),
                 "expected a time within [" + before + ", " + after + "] but was " + actual);
   }
}
