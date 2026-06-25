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

import inetsoft.sree.security.IdentityID;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;

/*
 * Tier: [unit] — ScheduleCondition logic over TimeRange.getTimeRanges(); Spring (@SreeHome)
 * loads SreeEnv / TimeRange configuration only.
 *
 * Known source bugs (documented below; NOT fixed at this time — tests record current behavior):
 *
 * [BUG-TBC-1] Symmetric ~10-minute window around range start (not "about to start" only)
 *   Location : TaskBalancerCondition.java:29 (check); same logic in TaskBalancerAction.java:37
 *   Actual   : Math.abs + Duration.toMinutes() fires within ~10 min before AND after each
 *              TimeRange start; seconds are truncated to whole minutes.
 *   UI risk  : Low — EM only configures range start times; users cannot see or change the window.
 *              Rebalance may still run a few minutes after range start.
 *   Status   : Deferred. Covered by CheckTests minute-boundary parameters.
 *
 * [BUG-TBC-2] getTimeRanges() NPE when schedule.time.ranges property is null
 *   Location : TimeRange.java:223 (getTimeRanges)
 *   Actual   : property.split(";") NPEs when property is null (e.g. after setTimeRanges(empty));
 *              if stream were empty, check() would return false instead.
 *   UI risk  : Medium — EM Time Ranges allows deleting all ranges and saving, which clears the
 *              property; reopening Scheduler settings may then fail.
 *   Status   : Deferred. noNearbyRangeStartReturnsFalse() covers non-trigger path only.
 *
 * [BUG-TBC-3] getRetryTime() mutates lastRun, not only check()
 *   Location : TaskBalancerCondition.java:56 (getRetryTime debounce branch)
 *   Actual   : Quartz getFireTimeAfter() calls getRetryTime(), which sets lastRun for the
 *              5-minute debounce; lastRun is shared scheduling state, not just "last check true".
 *   UI risk  : None — internal __balance tasks__ task; not editable in EM.
 *   Status   : Deferred. GetRetryTimeTests document debounce behavior.
 */

/*
 * Cases deferred — integration / Quartz wiring:
 *
 * [TaskBalancerCondition] TaskBalancerConditionTriggerImpl / Quartz fire cycle
 *             -> covered indirectly by scheduler integration; NOT duplicated here
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TaskBalancerConditionTest {

   private static final TimeRange MORNING_RANGE =
      new TimeRange("tr1", "08:00:00", "13:59:00", true);

   private List<TimeRange> originalRanges;
   private TaskBalancerCondition condition;

   @BeforeEach
   void setUp() throws IOException {
      originalRanges = new ArrayList<>(TimeRange.getTimeRanges());
      setSingleMorningRange();
      condition = new TaskBalancerCondition();
   }

   @AfterEach
   void tearDown() throws IOException {
      TimeRange.setTimeRanges(originalRanges);
   }

   // -------------------------------------------------------------------------
   // P1 — check() window, boundaries, and lastRun
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("check()")
   class CheckTests {

      @Test
      @DisplayName("one minute before start returns true and records lastRun")
      void oneMinuteBeforeStart() {
         long ts = millisAt(7, 59, 59);
         assertTrue(condition.check(ts));
         assertEquals(ts, condition.lastRun);
      }

      @Test
      @DisplayName("well outside window returns false")
      void wellOutsideWindow() {
         assertFalse(condition.check(millisAt(9, 0, 0)));
      }

      @ParameterizedTest(name = "check at {0}:{1}:{2} -> {3}")
      @CsvSource({
         "8,  0,  0, true",   // exactly at start
         "8,  9,  0, true",   // 9 minutes after start (abs)
         "7, 50,  0, true",   // 10 truncated minutes before start
         "7, 49,  0, false",  // 11 truncated minutes before start
         "8, 11,  0, false"   // 11 truncated minutes after start
      })
      void minuteBoundaryCases(int hour, int minute, int second, boolean expected) {
         assertEquals(expected, condition.check(millisAt(hour, minute, second)));
      }

      @Test
      @DisplayName("false result does not update lastRun")
      void falseResultDoesNotUpdateLastRun() {
         assertEquals(0L, condition.lastRun);
         assertFalse(condition.check(millisAt(9, 0, 0)));
         assertEquals(0L, condition.lastRun);
      }

      @Test
      @DisplayName("matches when within ten minutes of any configured range start")
      void matchesAnyRangeStart() throws IOException {
         setTwoRanges();
         TaskBalancerCondition multi = new TaskBalancerCondition();

         assertTrue(multi.check(millisAt(13, 55, 0)));
         assertFalse(multi.check(millisAt(12, 0, 0)));
      }

      @Test
      @DisplayName("returns false when no range start is within ten minutes")
      void noNearbyRangeStartReturnsFalse() throws IOException {
         TimeRange.setTimeRanges(List.of(
            new TimeRange("night", "22:00:00", "23:00:00", true)));
         TaskBalancerCondition isolated = new TaskBalancerCondition();

         assertFalse(isolated.check(millisAt(8, 0, 0)));
         assertEquals(0L, isolated.lastRun);
      }
   }

   // -------------------------------------------------------------------------
   // P1/P2 — getRetryTime() branches with explicit timestamps
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("getRetryTime()")
   class GetRetryTimeTests {

      @Test
      @DisplayName("just before start window returns today when lastRun is stale")
      void justBeforeStartReturnsTodayWhenLastRunStale() {
         long curr = millisAt(7, 59, 59);
         long retry = condition.getRetryTime(curr);
         assertLocalDateTime(curr, 7, 59, 59, retry);
      }

      @Test
      @DisplayName("after last start today schedules tomorrow at start minus ten minutes")
      void afterLastStartSchedulesTomorrow() {
         long curr = millisAt(9, 0, 0);
         long retry = condition.getRetryTime(curr);
         assertLocalDateTime(curr, 7, 50, 0, retry, 1);
      }

      @Test
      @DisplayName("before start minus ten minutes returns today at start minus ten minutes")
      void beforeStartMinusTenReturnsToday() {
         long curr = millisAt(7, 30, 0);
         long retry = condition.getRetryTime(curr);
         assertLocalDateTime(curr, 7, 50, 0, retry);
      }

      @Test
      @DisplayName("within five minutes of lastRun adds five minutes (debounce branch)")
      void withinFiveMinutesOfLastRunAddsFiveMinutes() {
         long curr = millisAt(7, 59, 59);
         assertTrue(condition.check(curr));

         long retry = condition.getRetryTime(curr);
         assertLocalDateTime(curr, 8, 4, 59, retry);
         assertEquals(retry, condition.lastRun, "getRetryTime updates lastRun in debounce branch");
      }
   }

   // -------------------------------------------------------------------------
   // P3 — ScheduleTask XML round-trip
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("ScheduleTask XML round-trip")
   class XmlRoundTripTests {

      @Test
      @DisplayName("TaskBalancer condition survives ScheduleTask writeXML / parseXML")
      void taskBalancerConditionRoundTrip() throws Exception {
         ScheduleTask original = new ScheduleTask("internal-balancer-task");
         original.setOwner(new IdentityID("scheduler-test", "host"));
         original.setPath("/");
         original.addCondition(new TaskBalancerCondition());
         original.addAction(new TaskBalancerAction());

         ScheduleTask loaded = roundTripTask(original);

         assertEquals(1, loaded.getConditionCount());
         assertInstanceOf(TaskBalancerCondition.class, loaded.getCondition(0));
         assertEquals(1, loaded.getActionCount());
         assertInstanceOf(TaskBalancerAction.class, loaded.getAction(0));
      }
   }

   // -------------------------------------------------------------------------
   // Helpers
   // -------------------------------------------------------------------------

   private void setSingleMorningRange() throws IOException {
      TimeRange.setTimeRanges(List.of(MORNING_RANGE));
   }

   private void setTwoRanges() throws IOException {
      TimeRange.setTimeRanges(List.of(
         MORNING_RANGE,
         new TimeRange("tr2", "14:00:00", "18:00:00", false)));
   }

   private static long millisAt(int hour, int minute, int second) {
      LocalDateTime time = LocalDateTime.now()
         .withHour(hour)
         .withMinute(minute)
         .withSecond(second)
         .withNano(0);

      return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
   }

   private static void assertLocalDateTime(long anchorMillis, int hour, int minute, int second, long actual) {
      assertLocalDateTime(anchorMillis, hour, minute, second, actual, 0);
   }

   private static void assertLocalDateTime(long anchorMillis, int hour, int minute, int second,
                                           long actual, int dayOffset)
   {
      LocalDate anchorDate = LocalDateTime.ofInstant(
         Instant.ofEpochMilli(anchorMillis), ZoneId.systemDefault()).toLocalDate();
      LocalDateTime expected = LocalDateTime.of(
         anchorDate.plusDays(dayOffset), LocalTime.of(hour, minute, second));
      LocalDateTime actualDateTime = LocalDateTime.ofInstant(
         Instant.ofEpochMilli(actual), ZoneId.systemDefault());

      assertEquals(expected, actualDateTime);
   }

   private static ScheduleTask roundTripTask(ScheduleTask original) throws Exception {
      StringWriter sw = new StringWriter();
      original.writeXML(new PrintWriter(sw));
      String wrapped = "<root>" + sw + "</root>";

      Document doc = DocumentBuilderFactory.newInstance()
         .newDocumentBuilder()
         .parse(new ByteArrayInputStream(wrapped.getBytes(StandardCharsets.UTF_8)));

      Element taskElem = firstChildElement(doc.getDocumentElement());
      ScheduleTask loaded = new ScheduleTask();
      loaded.parseXML(taskElem);
      return loaded;
   }

   private static Element firstChildElement(Element parent) {
      for(int i = 0; i < parent.getChildNodes().getLength(); i++) {
         if(parent.getChildNodes().item(i) instanceof Element element) {
            return element;
         }
      }

      throw new AssertionError("No child element under " + parent.getTagName());
   }
}
