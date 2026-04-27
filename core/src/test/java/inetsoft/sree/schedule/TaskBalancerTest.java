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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
   initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class TaskBalancerTest {
   /*
    * TaskBalancer decision tree
    *  [A] isInRange() on a regular range                   -> start <= time < end
    *  [B] isInRange() on an overnight range                -> time >= start || time < end
    *  [C] getDuration() on a regular range                 -> direct minute delta
    *  [D] getDuration() on an overnight range              -> wrap across midnight
    *  [E] getInterval() finds a 5-minute multiple          -> maximize interval within concurrency
    *  [F] getInterval() cannot find a 5-minute multiple    -> clamp to 1 minute
    *  [G] getSlot(TimeCondition) for EVERY_HOUR same end   -> single slot
    *  [H] getSlot(TimeCondition) for EVERY_HOUR range      -> multiple slots
    *  [I] distribute() finds a free slot                   -> assign slot and mutate condition
    *  [J] distribute() finds no free slot in sub-range     -> return remaining condition indexes
    *  [K] isMatchingTimeRange() exact existing range       -> direct equality
    *  [L] isMatchingTimeRange() stale range definition     -> best-match lookup
    *  [M] roundUp()/roundDown() already aligned            -> unchanged
    *  [N] roundUp()/roundDown() not aligned                -> move to nearest 5-minute boundary
    *  [O] roundUp() near midnight                          -> wraps to 00:00 without date context
    *  [P] getStartTime(AT)                                 -> uses condition time zone
    */

   private final TaskBalancer taskBalancer = new TaskBalancer();

   // [Path A/B] range inclusion follows inclusive start and exclusive end semantics.
   @ParameterizedTest
   @CsvSource({
      "09:00, 08:00, 17:00, true",
      "17:00, 08:00, 17:00, false",
      "23:30, 22:00, 02:00, true",
      "01:30, 22:00, 02:00, true",
      "12:00, 22:00, 02:00, false"
   })
   void isInRange_regularAndOvernightRanges_matchExpectedWindow(
      String time, String start, String end, boolean expected)
   {
      boolean actual = invoke("isInRange",
         new Class<?>[] { LocalTime.class, LocalTime.class, LocalTime.class },
         LocalTime.parse(time), LocalTime.parse(start), LocalTime.parse(end));

      assertEquals(expected, actual);
   }

   // [Path C/D] duration counts minutes directly for regular ranges and wraps overnight.
   @ParameterizedTest
   @CsvSource({
      "09:00, 10:30, 90",
      "22:00, 01:00, 180",
      "23:55, 00:05, 10"
   })
   void getDuration_regularAndOvernightRanges_returnsMinutes(
      String start, String end, int expected)
   {
      int actual = invoke("getDuration",
         new Class<?>[] { LocalTime.class, LocalTime.class },
         LocalTime.parse(start), LocalTime.parse(end));

      assertEquals(expected, actual);
   }

   // [Path E/F] interval prefers the largest 5-minute block, but never returns zero.
   @ParameterizedTest
   @CsvSource({
      "3, 60, 4, 15",
      "2, 20, 6, 5",
      "1, 4, 10, 1"
   })
   void getInterval_variedCapacity_returnsExpectedSlotLength(
      int concurrency, int duration, int conditions, int expected)
   {
      int actual = invoke("getInterval",
         new Class<?>[] { int.class, int.class, int.class },
         concurrency, duration, conditions);

      assertEquals(expected, actual);
   }

   // [Path G/H] hourly conditions either collapse to one slot or expand into repeated slots.
   @Test
   void getSlot_everyHourCondition_returnsSingleOrMultipleSlots() {
      TimeCondition single = TimeCondition.atHours(new int[] { Calendar.MONDAY }, 10, 0, 0);
      single.setHourEnd(10);
      single.setMinuteEnd(0);
      single.setSecondEnd(0);

      List<Integer> singleSlots = invoke("getSlot",
         new Class<?>[] { TimeCondition.class, LocalTime.class, LocalTime.class, int.class },
         single, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);

      assertEquals(Collections.singletonList(2), singleSlots);

      TimeCondition repeated = TimeCondition.atHours(new int[] { Calendar.MONDAY }, 10, 0, 0);
      repeated.setHourEnd(12);
      repeated.setMinuteEnd(0);
      repeated.setSecondEnd(0);
      repeated.setHourlyInterval(1);

      List<Integer> repeatedSlots = invoke("getSlot",
         new Class<?>[] { TimeCondition.class, LocalTime.class, LocalTime.class, int.class },
         repeated, LocalTime.of(9, 0), LocalTime.of(13, 0), 30);

      assertEquals(Arrays.asList(2, 4), repeatedSlots);

      // non-EVERY_HOUR condition (AT) inside the range maps to a single slot
      TimeCondition atInRange = TimeCondition.at(Date.from(
         LocalDate.of(2026, 4, 22).atTime(10, 0, 0).atZone(ZoneId.systemDefault()).toInstant()));
      List<Integer> atSlots = invoke("getSlot",
         new Class<?>[] { TimeCondition.class, LocalTime.class, LocalTime.class, int.class },
         atInRange, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
      assertEquals(Collections.singletonList(2), atSlots);

      // non-EVERY_HOUR condition outside the range returns an empty list
      TimeCondition atOutside = TimeCondition.at(Date.from(
         LocalDate.of(2026, 4, 22).atTime(14, 0, 0).atZone(ZoneId.systemDefault()).toInstant()));
      List<Integer> outsideSlots = invoke("getSlot",
         new Class<?>[] { TimeCondition.class, LocalTime.class, LocalTime.class, int.class },
         atOutside, LocalTime.of(9, 0), LocalTime.of(12, 0), 30);
      assertTrue(outsideSlots.isEmpty());
   }

   // [fillSlots] unclaimed slots add a new thread BitSet; already-taken slots are skipped.
   @Test
   void fillSlots_unclaimedAndClaimedSlots_addsOrSkipsNewBitSet() {
      TimeCondition cond = TimeCondition.at(10, 0, 0);

      // slot 2 (10:00 in 09:00–12:00 at 30-min interval) is free in all existing threads:
      // a new BitSet is appended to represent the occupying hard-coded task
      List<BitSet> slots = new ArrayList<>(Collections.singletonList(new BitSet()));
      invoke("fillSlots",
         new Class<?>[] {
            TimeCondition.class, LocalTime.class, LocalTime.class, int.class, List.class
         },
         cond, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, slots);
      assertEquals(2, slots.size());
      assertTrue(slots.get(1).get(2));

      // slot 2 is already occupied by an existing thread: no new BitSet is added
      BitSet preoccupied = new BitSet();
      preoccupied.set(2);
      List<BitSet> claimedSlots = new ArrayList<>(Collections.singletonList(preoccupied));
      invoke("fillSlots",
         new Class<?>[] {
            TimeCondition.class, LocalTime.class, LocalTime.class, int.class, List.class
         },
         cond, LocalTime.of(9, 0), LocalTime.of(12, 0), 30, claimedSlots);
      assertEquals(1, claimedSlots.size());
   }

   // [Path I/J] distribute assigns into the first free slot and reports blocked conditions.
   @Test
   void distribute_freeAndBlockedSlots_updatesAssignedConditionsAndReturnsRemaining() {
      TimeRange range = new TimeRange("workday", "09:00:00", "10:00:00", true);
      LocalTime start = LocalTime.of(9, 0);
      List<TimeCondition> conditions = Arrays.asList(
         TimeCondition.at(0, 0, 0),
         TimeCondition.at(0, 0, 0));

      BitSet freeSlots = new BitSet();
      List<Integer> assigned = invoke("distribute",
         new Class<?>[] {
            int.class, int.class, int.class, int.class, List.class, BitSet.class,
            int.class, LocalTime.class, TimeRange.class
         },
         0, 1, 0, 4, conditions, freeSlots, 15, start, range);

      assertTrue(assigned.isEmpty());
      assertEquals(9, conditions.get(0).getHour());
      assertEquals(0, conditions.get(0).getMinute());
      assertEquals(range, conditions.get(0).getTimeRange());

      BitSet blockedSlots = new BitSet();
      blockedSlots.set(0);
      List<Integer> remaining = invoke("distribute",
         new Class<?>[] {
            int.class, int.class, int.class, int.class, List.class, BitSet.class,
            int.class, LocalTime.class, TimeRange.class
         },
         1, 2, 0, 1, conditions, blockedSlots, 15, start, range);

      assertEquals(Collections.singletonList(1), remaining);
      assertEquals(1, blockedSlots.nextClearBit(0));

      // recursive path: 4 conditions across 8 slots are distributed evenly via divide-and-conquer
      TimeRange recursiveRange = new TimeRange("recursive", "09:00:00", "11:00:00", false);
      List<TimeCondition> fourConditions = Arrays.asList(
         TimeCondition.at(0, 0, 0), TimeCondition.at(0, 0, 0),
         TimeCondition.at(0, 0, 0), TimeCondition.at(0, 0, 0));
      BitSet emptySlots = new BitSet();

      List<Integer> noneRemaining = invoke("distribute",
         new Class<?>[] {
            int.class, int.class, int.class, int.class, List.class, BitSet.class,
            int.class, LocalTime.class, TimeRange.class
         },
         0, 4, 0, 8, fourConditions, emptySlots, 15, LocalTime.of(9, 0), recursiveRange);

      assertTrue(noneRemaining.isEmpty());
      assertEquals(9, fourConditions.get(0).getHour());
      assertEquals(0, fourConditions.get(0).getMinute());
      assertEquals(9, fourConditions.get(1).getHour());
      assertEquals(30, fourConditions.get(1).getMinute());
      assertEquals(10, fourConditions.get(2).getHour());
      assertEquals(0, fourConditions.get(2).getMinute());
      assertEquals(10, fourConditions.get(3).getHour());
      assertEquals(30, fourConditions.get(3).getMinute());
   }

   // [Path K/L] matching uses direct equality for known ranges and closest match for stale ranges.
   @Test
   void isMatchingTimeRange_existingAndChangedRanges_matchExpectedRange() throws Exception {
      TimeRange morning = new TimeRange("morning", "09:00:00", "12:00:00", true);
      TimeRange afternoon = new TimeRange("afternoon", "12:00:00", "18:00:00", false);
      Set<TimeRange> ranges = new HashSet<>(Arrays.asList(morning, afternoon));
      TimeRange.setTimeRanges(Arrays.asList(morning, afternoon));

      TimeCondition exact = TimeCondition.at(9, 0, 0);
      exact.setTimeRange(morning);

      boolean exactMatch = invoke("isMatchingTimeRange",
         new Class<?>[] { TimeCondition.class, TimeRange.class, Set.class },
         exact, morning, ranges);

      assertTrue(exactMatch);

      TimeCondition stale = TimeCondition.at(9, 0, 0);
      stale.setTimeRange(new TimeRange("legacy", "09:05:00", "12:05:00", false));

      boolean staleMatch = invoke("isMatchingTimeRange",
         new Class<?>[] { TimeCondition.class, TimeRange.class, Set.class },
         stale, morning, ranges);

      assertTrue(staleMatch);
   }

   // [Path N] updateCondition mutates all time fields and marks the slot as occupied.
   @Test
   void updateCondition_slotAssignment_setsTimeAndRange() {
      TimeCondition condition = TimeCondition.at(0, 0, 0);
      TimeRange range = new TimeRange("batch", "09:00:00", "11:00:00", false);
      BitSet slots = new BitSet();

      invoke("updateCondition",
         new Class<?>[] {
            TimeCondition.class, int.class, int.class, LocalTime.class, BitSet.class,
            TimeRange.class
         },
         condition, 3, 10, LocalTime.of(9, 5), slots, range);

      assertEquals(9, condition.getHour());
      assertEquals(35, condition.getMinute());
      assertEquals(0, condition.getSecond());
      assertEquals(range, condition.getTimeRange());
      assertTrue(slots.get(3));
   }

   // [Path M/N] rounding is a no-op on aligned values and adjusts unaligned values to 5-minute boundaries.
   @Test
   void roundUpAndRoundDown_alignedAndUnalignedTimes_returnExpectedValues() {
      LocalTime aligned = LocalTime.of(9, 10);
      LocalTime unaligned = LocalTime.of(9, 12);

      LocalTime roundedUpAligned = invoke("roundUp",
         new Class<?>[] { LocalTime.class }, aligned);
      LocalTime roundedDownAligned = invoke("roundDown",
         new Class<?>[] { LocalTime.class }, aligned);
      LocalTime roundedUpUnaligned = invoke("roundUp",
         new Class<?>[] { LocalTime.class }, unaligned);
      LocalTime roundedDownUnaligned = invoke("roundDown",
         new Class<?>[] { LocalTime.class }, unaligned);

      assertEquals(LocalTime.of(9, 10), roundedUpAligned);
      assertEquals(LocalTime.of(9, 10), roundedDownAligned);
      assertEquals(LocalTime.of(9, 15), roundedUpUnaligned);
      assertEquals(LocalTime.of(9, 10), roundedDownUnaligned);

      // seconds are preserved: only the minute field is adjusted, not the second field
      LocalTime withSeconds = LocalTime.of(9, 12, 30);
      LocalTime roundedUpWithSecs = invoke("roundUp", new Class<?>[] { LocalTime.class }, withSeconds);
      LocalTime roundedDownWithSecs = invoke("roundDown", new Class<?>[] { LocalTime.class }, withSeconds);
      assertEquals(LocalTime.of(9, 15, 30), roundedUpWithSecs);
      assertEquals(LocalTime.of(9, 10, 30), roundedDownWithSecs);

      LocalTime alignedWithSeconds = LocalTime.of(9, 10, 45);
      LocalTime roundedUpAlignedSecs = invoke("roundUp", new Class<?>[] { LocalTime.class }, alignedWithSeconds);
      LocalTime roundedDownAlignedSecs = invoke("roundDown", new Class<?>[] { LocalTime.class }, alignedWithSeconds);
      assertEquals(LocalTime.of(9, 10, 45), roundedUpAlignedSecs);
      assertEquals(LocalTime.of(9, 10, 45), roundedDownAlignedSecs);
   }

   // [Path O] rounding near midnight wraps to the next day's local time without carrying a date.
   @Test
   void roundUp_nearMidnight_wrapsToMidnight() {
      LocalTime rounded = invoke("roundUp",
         new Class<?>[] { LocalTime.class }, LocalTime.of(23, 58));

      assertEquals(LocalTime.MIDNIGHT, rounded);

      // seconds are retained after the wrap: the result is 00:00:<seconds>, not exactly MIDNIGHT
      LocalTime roundedWithSecs = invoke("roundUp",
         new Class<?>[] { LocalTime.class }, LocalTime.of(23, 58, 30));
      assertEquals(LocalTime.of(0, 0, 30), roundedWithSecs);
   }

   // [Path B + regular + out-of-range] slot index is computed correctly across all range types.
   @Test
   void getSlot_overnightRange_returnsOffsetFromMidnightSegment() {
      // overnight: time after midnight uses offset from midnight
      int slot = invoke("getSlot",
         new Class<?>[] { LocalTime.class, LocalTime.class, LocalTime.class, int.class },
         LocalTime.of(1, 0), LocalTime.of(22, 0), LocalTime.of(2, 0), 30);
      assertEquals(2, slot);

      // overnight: time before midnight uses offset from range start
      int preMidnightSlot = invoke("getSlot",
         new Class<?>[] { LocalTime.class, LocalTime.class, LocalTime.class, int.class },
         LocalTime.of(23, 0), LocalTime.of(22, 0), LocalTime.of(2, 0), 30);
      assertEquals(2, preMidnightSlot);

      // regular range: offset measured from range start
      int regularSlot = invoke("getSlot",
         new Class<?>[] { LocalTime.class, LocalTime.class, LocalTime.class, int.class },
         LocalTime.of(10, 0), LocalTime.of(9, 0), LocalTime.of(17, 0), 30);
      assertEquals(2, regularSlot);

      // time outside the range returns -1
      int outsideSlot = invoke("getSlot",
         new Class<?>[] { LocalTime.class, LocalTime.class, LocalTime.class, int.class },
         LocalTime.of(18, 0), LocalTime.of(9, 0), LocalTime.of(17, 0), 30);
      assertEquals(-1, outsideSlot);
   }

   // [Path utility] AT conditions use their own zone, while negative minute/second values are clamped.
   @Test
   void getStartTime_atAndEveryDayConditions_returnExpectedLocalTime() {
      TimeCondition atCondition = TimeCondition.at(Date.from(
         LocalDate.of(2026, 4, 22).atTime(10, 15, 20).atZone(ZoneId.systemDefault()).toInstant()));
      TimeCondition everyDay = TimeCondition.at(8, -1, -2);

      LocalTime atStart = invoke("getStartTime",
         new Class<?>[] { TimeCondition.class }, atCondition);
      LocalTime dayStart = invoke("getStartTime",
         new Class<?>[] { TimeCondition.class }, everyDay);

      assertEquals(LocalTime.of(10, 15, 20), atStart);
      assertEquals(LocalTime.of(8, 0, 0), dayStart);
   }

   // [Path P] AT conditions are interpreted in the condition time zone.
   @Execution(ExecutionMode.SAME_THREAD)
   @Test
   void getStartTime_atCondition_usesConditionTimeZone() {
      TimeZone original = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
         Date instant = Date.from(LocalDateTime.of(2026, 4, 22, 10, 15)
            .atZone(ZoneId.of("UTC")).toInstant());
         TimeCondition condition = TimeCondition.at(instant);
         condition.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

         LocalTime start = invoke("getStartTime",
            new Class<?>[] { TimeCondition.class }, condition);

         assertEquals(LocalTime.of(18, 15), start);
      }
      finally {
         TimeZone.setDefault(original);
      }
   }

   // EVERY_HOUR conditions with non-positive or infinite hourlyInterval must be rejected.
   @ParameterizedTest
   @CsvSource({ "0", "-1", "Infinity", "-Infinity" })
   void getSlot_everyHourCondition_withNonPositiveInterval_rejects(float hourlyInterval) {
      TimeCondition invalid = TimeCondition.atHours(new int[] { Calendar.MONDAY }, 10, 0, 0);
      invalid.setHourEnd(12);
      invalid.setMinuteEnd(0);
      invalid.setSecondEnd(0);
      invalid.setHourlyInterval(hourlyInterval);

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
         () -> invoke("getSlot",
            new Class<?>[] { TimeCondition.class, LocalTime.class, LocalTime.class, int.class },
            invalid, LocalTime.of(9, 0), LocalTime.of(13, 0), 30));

      assertEquals("hourlyInterval must be greater than zero", exception.getMessage());
   }

   @Test
   void getSlot_everyHourCondition_withSubMinuteInterval_rejects() {
      TimeCondition invalid = TimeCondition.atHours(new int[] { Calendar.MONDAY }, 10, 0, 0);
      invalid.setHourEnd(12);
      invalid.setMinuteEnd(0);
      invalid.setSecondEnd(0);
      invalid.setHourlyInterval(0.001F);

      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
         () -> invoke("getSlot",
            new Class<?>[] { TimeCondition.class, LocalTime.class, LocalTime.class, int.class },
            invalid, LocalTime.of(9, 0), LocalTime.of(13, 0), 30));

      assertEquals("hourlyInterval must advance time by at least one minute",
         exception.getMessage());
   }

   // AT conditions use TimeCondition.getTimeZone(), not the server default zone.
   @Test
   void getStartTime_atCondition_shouldUseConditionTimeZoneInsteadOfSystemDefault() {
      TimeZone original = TimeZone.getDefault();

      try {
         TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
         Date instant = Date.from(LocalDateTime.of(2026, 4, 22, 10, 15)
            .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
         TimeCondition condition = TimeCondition.at(instant);
         condition.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));

         LocalTime start = invoke("getStartTime",
            new Class<?>[] { TimeCondition.class }, condition);

         assertEquals(LocalTime.of(10, 15), start);
      }
      finally {
         TimeZone.setDefault(original);
      }
   }

   // getInterval() rejects zero conditions with a clear validation error.
   @Test
   void getInterval_zeroConditions_throwsIllegalArgumentException() {
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
         () -> invokeUnchecked("getInterval",
         new Class<?>[] { int.class, int.class, int.class },
         1, 30, 0));

      assertEquals("conditions must be greater than zero", exception.getMessage());
   }

   @SuppressWarnings("unchecked")
   private <T> T invoke(String methodName, Class<?>[] parameterTypes, Object... args) {
      try {
         Method method = TaskBalancer.class.getDeclaredMethod(methodName, parameterTypes);
         method.setAccessible(true);
         return (T) method.invoke(taskBalancer, args);
      }
      catch(InvocationTargetException e) {
         Throwable cause = e.getCause();

         if(cause instanceof RuntimeException re) {
            throw re;
         }

         if(cause instanceof Error err) {
            throw err;
         }

         throw new AssertionError("Method threw checked exception: " + methodName, cause);
      }
      catch(ReflectiveOperationException e) {
         throw new AssertionError("Failed to invoke method: " + methodName, e);
      }
   }

   private void invokeUnchecked(String methodName, Class<?>[] parameterTypes, Object... args) {
      try {
         Method method = TaskBalancer.class.getDeclaredMethod(methodName, parameterTypes);
         method.setAccessible(true);
         method.invoke(taskBalancer, args);
      }
      catch(InvocationTargetException e) {
         Throwable cause = e.getCause();

         if(cause instanceof RuntimeException runtimeException) {
            throw runtimeException;
         }

         if(cause instanceof Error error) {
            throw error;
         }

         throw new AssertionError("Failed to invoke method: " + methodName, cause);
      }
      catch(Exception e) {
         throw new AssertionError("Failed to invoke method: " + methodName, e);
      }
   }
}
