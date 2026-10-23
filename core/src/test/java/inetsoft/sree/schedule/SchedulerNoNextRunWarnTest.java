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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.config.InetsoftConfig;
import org.junit.jupiter.api.*;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #79056 -- a repeating time condition that has no next run time (an empty or impossible
 * schedule) leaves the task saved with no trigger. {@code Scheduler.addTask} (also reached from
 * {@code loadTasks}) now says so at WARN, once per add or load, with the task id and the
 * condition fields. An AT condition in the past is normal and is not logged.
 *
 * <p>Drives the real private {@code addTask} on a Scheduler instance created without its
 * constructor, with a mock Quartz scheduler, on {@link SchedulerTestHarness}'s mocked beans.
 */
@Tag("core")
class SchedulerNoNextRunWarnTest {
   private SchedulerTestHarness harness;
   private Scheduler scheduler;
   private Logger logger;
   private Level savedLevel;
   private ListAppender<ILoggingEvent> appender;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();

      // addTask reads the cloud runner config; none configured means the in-process job class
      when(ConfigurationContext.getContext().getApplicationContext().getBean(InetsoftConfig.class))
         .thenReturn(new InetsoftConfig());

      scheduler = mock(Scheduler.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      Field quartz = Scheduler.class.getDeclaredField("scheduler");
      quartz.setAccessible(true);
      quartz.set(scheduler, mock(org.quartz.Scheduler.class));

      logger = (Logger) LoggerFactory.getLogger(Scheduler.class);
      savedLevel = logger.getLevel();
      logger.setLevel(Level.DEBUG);
      appender = new ListAppender<>();
      appender.start();
      logger.addAppender(appender);
   }

   @AfterEach
   void tearDown() throws Exception {
      logger.detachAppender(appender);
      logger.setLevel(savedLevel);
      harness.close();
   }

   @Test
   void impossibleRepeatingConditionIsLoggedAtWarn() throws Exception {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_MONTH);
      condition.setTimeZone(TimeZone.getTimeZone("UTC"));
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      condition.setDayOfMonth(30);
      condition.setMonthsOfYear(new int[] { 1 }); // February only

      ScheduleTask task = task("warn-impossible-feb-30", condition);
      addTask(task, true);

      List<ILoggingEvent> warnings = warnings();
      assertEquals(1, warnings.size(), warnings.toString());
      String message = warnings.get(0).getFormattedMessage();
      assertTrue(message.contains("warn-impossible-feb-30"), message);
      assertTrue(message.contains("can never fire"), message);
      assertTrue(message.contains("dayOfMonth=30"), message);
      assertTrue(message.contains("monthsOfYear=[1]"), message);
   }

   @Test
   void emptyWeeklyConditionIsLoggedAtWarn() throws Exception {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_WEEK);
      condition.setTimeZone(TimeZone.getTimeZone("UTC"));
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      condition.setDaysOfWeek(new int[] { 0, 9 });

      addTask(task("warn-weekly-no-valid-day", condition), false);

      List<ILoggingEvent> warnings = warnings();
      assertEquals(1, warnings.size(), warnings.toString());
      assertTrue(warnings.get(0).getFormattedMessage().contains("warn-weekly-no-valid-day"));
      assertTrue(warnings.get(0).getFormattedMessage().contains("daysOfWeek=[0, 9]"));
   }

   @Test
   void conditionWithANextRunIsNotLoggedAtWarn() throws Exception {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.EVERY_MONTH);
      condition.setTimeZone(TimeZone.getTimeZone("UTC"));
      condition.setHour(9);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      condition.setDayOfMonth(31);
      condition.setMonthsOfYear(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11 });

      addTask(task("no-warn-valid", condition), true);

      assertTrue(logged("Task scheduled at"), "addTask did not schedule the trigger");
      assertTrue(warnings().isEmpty(), warnings().toString());
   }

   @Test
   void atConditionInThePastIsNotLoggedAtWarn() throws Exception {
      TimeCondition condition = TimeCondition.at(new Date(System.currentTimeMillis() - 86400000L));

      addTask(task("no-warn-at-in-the-past", condition), false);

      assertTrue(logged("Time condition skipped"), "addTask did not skip the condition");
      assertTrue(warnings().isEmpty(), warnings().toString());
   }

   private ScheduleTask task(String name, TimeCondition condition) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(SchedulerTestHarness.TEST_OWNER);
      task.addCondition(condition);
      return task;
   }

   /** Runs the private addTask(task, loading) on a thread that is abandoned after a timeout. */
   private void addTask(ScheduleTask task, boolean loading) throws Exception {
      Method method = Scheduler.class.getDeclaredMethod("addTask", ScheduleTask.class,
                                                        boolean.class);
      method.setAccessible(true);
      FutureTask<Void> call = new FutureTask<>(() -> {
         method.invoke(scheduler, task, loading);
         return null;
      });
      Thread thread = new Thread(call, "addTask-79056");
      thread.setDaemon(true);
      thread.start();

      try {
         call.get(30, TimeUnit.SECONDS);
      }
      catch(TimeoutException e) {
         thread.interrupt();
         fail("Scheduler.addTask did not return");
      }
   }

   private boolean logged(String text) {
      for(ILoggingEvent event : appender.list) {
         if(event.getFormattedMessage().contains(text)) {
            return true;
         }
      }

      return false;
   }

   private List<ILoggingEvent> warnings() {
      List<ILoggingEvent> result = new ArrayList<>();

      for(ILoggingEvent event : appender.list) {
         if(event.getLevel() == Level.WARN && event.getFormattedMessage().contains("can never fire")) {
            result.add(event);
         }
      }

      return result;
   }
}
