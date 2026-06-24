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
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Tag;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.w3c.dom.Element;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

/*
 * Tier: [integration] — TimeRange persistence via SreeEnv (@SreeHome); Spring loads property context.
 *
 * Known source bugs (documented below; NOT fixed at this time — tests record current behavior):
 *
 * [BUG-TR-1] getTimeRanges() NPE when schedule.time.ranges property is null
 *   Location : TimeRange.java:223 (getTimeRanges)
 *   Actual   : property.split(";") NPEs when getProperty returns null. Under @SreeHome,
 *              setTimeRanges(null/empty) removes the user override and falls back to
 *              defaults.properties instead.
 *   UI risk  : Medium — EM Time Ranges allows deleting all ranges; Scheduler settings may fail on reload.
 *   Status   : Deferred. Same root cause as BUG-TBC-2 in TaskBalancerConditionTest.
 *
 * [BUG-TR-2] equals() / hashCode() ignore defaultRange flag
 *   Location : TimeRange.java:197 (equals)
 *   Actual   : same name/start/end with different defaultRange still compare equal.
 *   UI risk  : Low — SchedulerConfigurationService oldRanges.equals(ranges) may skip rebalance
 *              when only the default flag changes.
 *   Status   : Deferred. equalsIgnoresDefaultRangeFlag() documents current behavior.
 *
 * [BUG-TR-3] compareTo() is asymmetric when both ranges are default
 *   Location : TimeRange.java:184 (compareTo)
 *   Actual   : if both isDefault(), each compareTo(other) returns -1; violates Comparable contract.
 *   UI risk  : None — setTimeRanges() rejects multiple defaults before save.
 *   Status   : Deferred. twoDefaultsCompareToIsAsymmetric() documents in-memory behavior.
 */

/*
 * Cases deferred - low value or covered elsewhere:
 *
 * [TimeRange] getMatchingTimeRange distance comparator int cast overflow
 *             -> day-bound minute distances; NOT duplicated here
 * [TimeRange] TaskBalancer.isMatchingTimeRange integration
 *             -> covered in TaskBalancerTest
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeRangeTest {

   private static final TimeRange MORNING =
      new TimeRange("tr1", "09:00:00", "14:00:00", true);
   private static final TimeRange AFTERNOON =
      new TimeRange("tr2", "14:00:00", "20:00:00", false);

   private List<TimeRange> originalRanges;

   @BeforeEach
   void captureOriginalRanges() throws IOException {
      originalRanges = new ArrayList<>(TimeRange.getTimeRanges());
   }

   @AfterEach
   void restoreOriginalRanges() throws IOException {
      TimeRange.setTimeRanges(originalRanges);
   }

   // -------------------------------------------------------------------------
   // P1 — construction, compareTo, equals
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("Construction and ordering (compareTo / equals)")
   class CompareToAndEqualsTests {

      @Test
      @DisplayName("constructor and setters expose name, times, and default flag")
      void constructorAndSetters() {
         TimeRange range = new TimeRange("tr1", "09:00:00", "14:00:00", false);

         assertEquals("tr1", range.getName());
         assertEquals(LocalTime.parse("09:00:00"), range.getStartTime());
         assertEquals(LocalTime.parse("14:00:00"), range.getEndTime());
         assertFalse(range.isDefault());

         range.setName("renamed");
         range.setStartTime(LocalTime.parse("10:00:00"));
         range.setEndTime(LocalTime.parse("15:00:00"));
         range.setDefault(true);

         assertEquals("renamed", range.getName());
         assertEquals(LocalTime.parse("10:00:00"), range.getStartTime());
         assertEquals(LocalTime.parse("15:00:00"), range.getEndTime());
         assertTrue(range.isDefault());
      }

      @Test
      @DisplayName("default range sorts before non-default; then start, end, name")
      void compareToOrdersByDefaultThenStartEndName() {
         TimeRange nonDefault = new TimeRange("tr1", "09:00:00", "14:00:00", false);
         TimeRange defaultRange = new TimeRange("tr2", "15:00:00", "20:00:00", true);

         assertTrue(nonDefault.compareTo(defaultRange) > 0);
         assertTrue(defaultRange.compareTo(nonDefault) < 0);

         TimeRange earlierEnd = new TimeRange("tr3", "09:00:00", "15:00:00", false);
         assertTrue(nonDefault.compareTo(earlierEnd) < 0);

         TimeRange sameTimesDifferentName = new TimeRange("tr0", "09:00:00", "14:00:00", false);
         assertTrue(nonDefault.compareTo(sameTimesDifferentName) > 0);

         TimeRange identical = new TimeRange("tr1", "09:00:00", "14:00:00", false);
         assertEquals(0, nonDefault.compareTo(identical));
      }

      @Test
      @DisplayName("distinct start/end yields non-equal instances")
      void equalsReflectsNameAndTimes() {
         TimeRange a = new TimeRange("tr1", "09:00:00", "14:00:00", false);
         TimeRange b = new TimeRange("tr1", "09:00:00", "15:00:00", false);

         assertNotEquals(a, b);
         assertNotEquals(a.hashCode(), b.hashCode());
      }

      @Test
      @DisplayName("BUG-TR-2 (deferred): equals ignores defaultRange flag")
      void equalsIgnoresDefaultRangeFlag() {
         TimeRange withDefault = new TimeRange("tr1", "09:00:00", "14:00:00", true);
         TimeRange withoutDefault = new TimeRange("tr1", "09:00:00", "14:00:00", false);

         assertEquals(withDefault, withoutDefault);
         assertEquals(withDefault.hashCode(), withoutDefault.hashCode());
      }

      @Test
      @DisplayName("BUG-TR-3 (deferred): two default ranges both compare as less than the other")
      void twoDefaultsCompareToIsAsymmetric() {
         TimeRange first = new TimeRange("a", "09:00:00", "12:00:00", true);
         TimeRange second = new TimeRange("b", "13:00:00", "17:00:00", true);

         assertEquals(-1, first.compareTo(second));
         assertEquals(-1, second.compareTo(first));
      }

      @Test
      @DisplayName("compareTo(null) throws NullPointerException")
      void compareToNullThrows() {
         assertThrows(NullPointerException.class, () -> MORNING.compareTo(null));
      }
   }

   // -------------------------------------------------------------------------
   // P1/P2 — SreeEnv persistence (setTimeRanges / getTimeRanges)
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("SreeEnv persistence (setTimeRanges / getTimeRanges)")
   class PersistenceTests {

      @Test
      @DisplayName("setTimeRanges persists encoded property string")
      void setTimeRangesWritesProperty() throws IOException {
         TimeRange.setTimeRanges(Arrays.asList(MORNING, AFTERNOON));

         assertEquals(
            "tr1,1,09:00:00,14:00:00;tr2,0,14:00:00,20:00:00",
            SreeEnv.getProperty("schedule.time.ranges"));
      }

      @Test
      @DisplayName("setTimeRanges round-trips through getTimeRanges")
      void setTimeRangesRoundTripsThroughGetTimeRanges() throws IOException {
         TimeRange.setTimeRanges(Arrays.asList(MORNING, AFTERNOON));

         List<TimeRange> loaded = TimeRange.getTimeRanges();

         assertEquals(2, loaded.size());
         assertEquals(MORNING.getName(), loaded.get(0).getName());
         assertEquals(MORNING.getStartTime(), loaded.get(0).getStartTime());
         assertEquals(MORNING.getEndTime(), loaded.get(0).getEndTime());
         assertTrue(loaded.get(0).isDefault());
         assertEquals(AFTERNOON.getName(), loaded.get(1).getName());
         assertFalse(loaded.get(1).isDefault());
      }

      @Test
      @DisplayName("setTimeRanges(null) removes user override and falls back to bundled defaults")
      void setTimeRangesNullRestoresBundledDefaults() throws IOException {
         TimeRange.setTimeRanges(Arrays.asList(MORNING, AFTERNOON));
         assertTrue(SreeEnv.getProperty("schedule.time.ranges").contains("tr1"));

         TimeRange.setTimeRanges(null);

         assertFalse(SreeEnv.getProperty("schedule.time.ranges").contains("tr1"));
         assertTrue(TimeRange.getTimeRanges().stream()
            .anyMatch(r -> "Morning".equals(r.getName())));
      }

      @Test
      @DisplayName("setTimeRanges(empty) removes user override and falls back to bundled defaults")
      void setTimeRangesEmptyRestoresBundledDefaults() throws IOException {
         TimeRange.setTimeRanges(Arrays.asList(MORNING, AFTERNOON));

         TimeRange.setTimeRanges(Collections.emptyList());

         assertFalse(SreeEnv.getProperty("schedule.time.ranges").contains("tr1"));
         assertEquals(3, TimeRange.getTimeRanges().size());
      }

      @Test
      @DisplayName("BUG-TR-1 (deferred): getTimeRanges NPEs when property is null")
      void getTimeRangesNullPropertyThrows() {
         try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
            sreeEnv.when(() -> SreeEnv.getProperty("schedule.time.ranges")).thenReturn(null);

            assertThrows(NullPointerException.class, TimeRange::getTimeRanges);
         }
      }

      @Test
      @DisplayName("duplicate names rejected")
      void duplicateNamesRejected() {
         TimeRange duplicateName = new TimeRange("tr2", "20:00:00", "09:00:00", true);

         IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> TimeRange.setTimeRanges(Arrays.asList(AFTERNOON, duplicateName)));

         assertEquals("Duplicate time range names", ex.getMessage());
      }

      @Test
      @DisplayName("multiple default ranges rejected")
      void multipleDefaultsRejected() {
         TimeRange secondDefault = new TimeRange("tr3", "20:00:00", "09:00:00", true);

         IllegalArgumentException ex = assertThrows(
            IllegalArgumentException.class,
            () -> TimeRange.setTimeRanges(Arrays.asList(MORNING, secondDefault)));

         assertEquals("Multiple default time ranges", ex.getMessage());
      }
   }

   // -------------------------------------------------------------------------
   // P1 — getMatchingTimeRange
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("getMatchingTimeRange")
   class MatchingTests {

      private List<TimeRange> ranges;

      @BeforeEach
      void setTwoRanges() throws IOException {
         ranges = Arrays.asList(MORNING, AFTERNOON);
         TimeRange.setTimeRanges(ranges);
      }

      @Test
      @DisplayName("matches by name first")
      void matchesByName() {
         assertEquals("tr1", TimeRange.getMatchingTimeRange(MORNING, ranges).getName());
      }

      @Test
      @DisplayName("matches by identical start and end when name differs")
      void matchesByStartAndEndWhenNameDiffers() {
         TimeRange alias = new TimeRange("legacy", "09:00:00", "14:00:00", false);

         assertEquals("tr1", TimeRange.getMatchingTimeRange(alias, ranges).getName());
      }

      @Test
      @DisplayName("falls back to closest start/end distance")
      void fallsBackToClosestDistance() {
         TimeRange nearMorning = new TimeRange("unknown", "09:05:00", "14:05:00", false);
         TimeRange nearAfternoon = new TimeRange("unknown", "21:00:00", "23:59:59", false);

         assertEquals("tr1", TimeRange.getMatchingTimeRange(nearMorning, ranges).getName());
         assertEquals("tr2", TimeRange.getMatchingTimeRange(nearAfternoon, ranges).getName());
      }

      @Test
      @DisplayName("empty defined ranges returns input range")
      void emptyRangesReturnsInput() {
         TimeRange input = new TimeRange("orphan", "09:00:00", "10:00:00", false);

         assertSame(input, TimeRange.getMatchingTimeRange(input, Collections.emptyList()));
      }

      @Test
      @DisplayName("null range argument throws NullPointerException")
      void nullRangeThrows() {
         assertThrows(NullPointerException.class,
            () -> TimeRange.getMatchingTimeRange(null, ranges));
      }
   }

   // -------------------------------------------------------------------------
   // P1 — XML round-trip
   // -------------------------------------------------------------------------

   @Nested
   @DisplayName("XML round-trip (writeXML / parseXML)")
   class XmlRoundTripTests {

      @Test
      @DisplayName("default range serializes and parses back")
      void defaultRangeRoundTrip() throws Exception {
         TimeRange original = new TimeRange("morning", "09:00:00", "14:00:00", true);

         TimeRange loaded = roundTripXml(original);

         assertEquals(original.getName(), loaded.getName());
         assertEquals(original.getStartTime(), loaded.getStartTime());
         assertEquals(original.getEndTime(), loaded.getEndTime());
         assertTrue(loaded.isDefault());
      }

      @Test
      @DisplayName("non-default range serializes and parses back")
      void nonDefaultRangeRoundTrip() throws Exception {
         TimeRange original = new TimeRange("afternoon", "14:00:00", "20:00:00", false);

         TimeRange loaded = roundTripXml(original);

         assertEquals(original.getName(), loaded.getName());
         assertEquals(original.getStartTime(), loaded.getStartTime());
         assertEquals(original.getEndTime(), loaded.getEndTime());
         assertFalse(loaded.isDefault());
      }
   }

   private static TimeRange roundTripXml(TimeRange original) throws Exception {
      StringWriter sw = new StringWriter();
      original.writeXML(new PrintWriter(sw));

      Element element = DocumentBuilderFactory.newInstance()
         .newDocumentBuilder()
         .parse(new ByteArrayInputStream(sw.toString().getBytes(StandardCharsets.UTF_8)))
         .getDocumentElement();

      TimeRange loaded = new TimeRange();
      loaded.parseXML(element);
      return loaded;
   }
}
