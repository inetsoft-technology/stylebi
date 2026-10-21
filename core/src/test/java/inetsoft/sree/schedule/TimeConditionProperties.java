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

import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.time.Month;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.*;

/**
 * Invariants of schedule time conditions read from XML, shared by
 * {@code TimeConditionSeedTest} and the enterprise fuzzer ({@code test/fuzzer}).
 * <p>
 * An input is the XML of one time condition ({@code <Condition type="TimeCondition" ...>}),
 * as task storage, EM task import and deploy import read it. {@link #check(String)} parses it
 * with {@link TimeCondition#parseXML}, which may reject it, skips the values of Bug #79056,
 * and then verifies that:
 * <ol>
 *    <li>parsing returns within {@link #STEP_LIMIT_MS} and throws no {@link Error};</li>
 *    <li>for each of a few start times, {@code getRetryTime} returns within
 *        {@link #STEP_LIMIT_MS} without throwing, and returns -1 (no more runs) or a time no
 *        earlier than the start;</li>
 *    <li>the next fire time after a fire, computed as the trigger computes it, is later than
 *        that fire, so a fire never repeats;</li>
 *    <li>writing the condition again gives XML that parses back to the same XML.</li>
 * </ol>
 */
public final class TimeConditionProperties {
   private TimeConditionProperties() {
   }

   /**
    * @return false if the input is not a time condition that parses, and was skipped.
    */
   public static boolean check(String input) throws Exception {
      if(input.length() > MAX_INPUT) {
         return false;
      }

      Element element = parseElement(input);

      if(element == null) {
         return false;
      }

      TimeCondition condition = new TimeCondition();

      try {
         timed("parsing " + input, () -> {
            condition.parseXML(element);
            return null;
         });
      }
      catch(Exception ex) {
         return false; // rejected
      }

      if(isKnownOutOfRange(condition)) {
         return false;
      }

      String xml = toXml(condition);

      for(long start : STARTS) {
         String where = xml + " from " + start;
         // the clock reads the start time, as when a task is saved (Scheduler.addTask)
         long fire = timed(where, () -> condition.getRetryTime(start, start, start));

         if(fire >= 0 && fire < start) {
            throw new AssertionError(where + " returned the earlier time " + fire);
         }

         if(fire < 0 || condition.getType() == TimeCondition.AT) {
            continue;
         }

         // as TimeConditionTriggerImpl.getFireTimeAfter computes it when the trigger fires
         long next = timed(where + " after the fire at " + fire,
                           () -> condition.getRetryTime(fire + 30000, fire, fire));

         if(next >= 0 && next <= fire) {
            throw new AssertionError(
               where + ": the fire at " + fire + " is followed by the fire at " + next);
         }
      }

      String again = toXml(timed("parsing the written " + xml, () -> {
         TimeCondition copy = new TimeCondition();
         copy.parseXML(parseElement(xml));
         return copy;
      }));

      if(!xml.equals(again)) {
         throw new AssertionError(input + " was written as " + xml + ", then as " + again);
      }

      return true;
   }

   /**
    * Out-of-range values, which no layer refuses (Bug #79056): some make getRetryTime loop
    * forever, an hour or minute outside its range can repeat a fire or return a time before
    * the start. Skipped so that the fuzzer looks for other defects; remove this check with
    * the fix.
    */
   private static boolean isKnownOutOfRange(TimeCondition condition) {
      int type = condition.getType();

      if(type != TimeCondition.AT &&
         (outOfRange(condition.getHour(), 23, false) ||
          outOfRange(condition.getMinute(), 59, false) ||
          outOfRange(condition.getSecond(), 59, false) ||
          outOfRange(condition.getHourEnd(), 23, true) ||
          outOfRange(condition.getMinuteEnd(), 59, true) ||
          outOfRange(condition.getSecondEnd(), 59, true)))
      {
         return true;
      }

      if(type == TimeCondition.EVERY_WEEK || type == TimeCondition.EVERY_HOUR) {
         for(int day : condition.getDaysOfWeek()) {
            if(day < Calendar.SUNDAY || day > Calendar.SATURDAY) {
               return true;
            }
         }
      }

      if(type == TimeCondition.EVERY_HOUR && condition.getHourlyInterval() > 0 &&
         (long) (condition.getHourlyInterval() * 3600000) == 0)
      {
         return true;
      }

      if(type == TimeCondition.EVERY_MONTH) {
         int longest = 0;

         for(int month : condition.getMonthsOfYear()) {
            if(month < Calendar.JANUARY || month > Calendar.DECEMBER) {
               return true;
            }

            longest = Math.max(longest, Month.of(month + 1).maxLength());
         }

         return condition.getWeekOfMonth() < 0 &&
            Math.abs(condition.getDayOfMonth()) > longest;
      }

      return false;
   }

   // an end time field is -1 when the condition has no end time
   private static boolean outOfRange(int value, int max, boolean unset) {
      return (value < 0 || value > max) && !(unset && value == -1);
   }

   private static Element parseElement(String xml) {
      try {
         DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
         factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
         factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
         return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)))
            .getDocumentElement();
      }
      catch(Exception ex) {
         return null;
      }
   }

   private static String toXml(TimeCondition condition) {
      StringWriter buffer = new StringWriter();
      PrintWriter writer = new PrintWriter(buffer);
      condition.writeXML(writer);
      writer.flush();
      return buffer.toString();
   }

   /**
    * Runs a step on a separate thread, so that a step that never returns fails the check
    * instead of hanging it. The thread of such a step keeps running.
    */
   private static <T> T timed(String what, Callable<T> step) throws Exception {
      Future<T> future = EXECUTOR.submit(step);

      try {
         return future.get(STEP_LIMIT_MS, TimeUnit.MILLISECONDS);
      }
      catch(TimeoutException ex) {
         future.cancel(true);
         throw new AssertionError(what + " did not return within " + STEP_LIMIT_MS + " ms");
      }
      catch(ExecutionException ex) {
         if(ex.getCause() instanceof Exception cause) {
            throw cause;
         }

         throw new AssertionError(what + " threw " + ex.getCause(), ex.getCause());
      }
   }

   public static final int MAX_INPUT = 2000;
   public static final long STEP_LIMIT_MS = 2000;

   // a summer day, the hour before the New York spring-forward gap, and the day before
   // a leap day (UTC)
   private static final List<Long> STARTS = List.of(
      1814445296000L, // 2027-07-01T12:34:56Z
      1836456000000L, // 2028-03-12T06:40:00Z
      1835393400000L  // 2028-02-28T23:30:00Z
   );

   private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
      Thread thread = new Thread(r, "TimeConditionProperties");
      thread.setDaemon(true);
      return thread;
   });
}
