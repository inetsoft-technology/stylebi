/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.data;

import inetsoft.graph.scale.TimeScale;
import org.junit.jupiter.api.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calendar edge cases of the time-series gap fill in {@link TimeSeriesRow}:
 * week buckets around the year end (Bug #82038) and sub-day buckets on a DST
 * transition day (Bug #82039).
 * <p>
 * The input values are built the way the product buckets them: week values are
 * week-start dates at local midnight, and hour/minute/second values are built with
 * {@link ZonedDateTime#of}, which (like {@code DateTimeProcessor.getTimestamp})
 * puts both occurrences of a repeated local time into the earlier offset.
 */
@Tag("core")
class TimeSeriesRowCalendarTest {
   private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
   private static final Locale EN_GB = Locale.UK;
   private static final Locale DE_DE = Locale.GERMANY;

   private Locale savedLocale;
   private TimeZone savedZone;

   @BeforeEach
   void saveDefaults() {
      savedLocale = Locale.getDefault();
      savedZone = TimeZone.getDefault();
      TimeZone.setDefault(TimeZone.getTimeZone(NEW_YORK));
   }

   @AfterEach
   void restoreDefaults() {
      Locale.setDefault(savedLocale);
      TimeZone.setDefault(savedZone);
   }

   // ---------------------------------------------------------------------
   // Bug #82038: week keys around the year end
   // ---------------------------------------------------------------------

   @Test
   void weekBeforeWeekOneIsFilled() {
      // en_US: Sunday 2012-12-30 is WEEK_OF_YEAR 1 while its YEAR is still 2012
      Locale.setDefault(Locale.US);
      List<String> filled = fillWeeks(rows(group("A", "2012-12-16", "2012-12-30")), false);
      assertEquals(List.of("A 2012-12-23"), filled);
   }

   @Test
   void weekGapInsideTheYearIsFilled() {
      // control: the same shape away from the year end
      Locale.setDefault(Locale.US);
      List<String> filled = fillWeeks(rows(group("A", "2012-11-04", "2012-11-18")), false);
      assertEquals(List.of("A 2012-11-11"), filled);
   }

   @Test
   void weekBeforeWeekOneIsFilledWithoutZeroFill() {
      // "fill gaps without zero" (missing value null) runs only the middle fill
      Locale.setDefault(Locale.US);
      List<String> filled = fillWeeks(rows(group("A", "2012-12-16", "2012-12-30")), true);
      assertEquals(List.of("A 2012-12-23"), filled);
   }

   @Test
   void sundayWeekBeforeYearEndIsFilled() {
      // en_US trailing fill: A ends on 12-09, the overall max is the 12-30 boundary week
      Locale.setDefault(Locale.US);
      List<String> filled = fillWeeks(rows(group("A", "2012-12-02", "2012-12-09"),
                                           group("B", "2012-12-02", "2012-12-30")), false);
      assertEquals(sorted("A 2012-12-16", "A 2012-12-23", "A 2012-12-30",
                          "B 2012-12-09", "B 2012-12-16", "B 2012-12-23"), sorted(filled));
   }

   @Test
   void leadingWeeksAcrossTheYearEndAreFilled() {
      // en_US leading fill: C starts on the 12-30 boundary week
      Locale.setDefault(Locale.US);
      List<String> filled = fillWeeks(rows(group("B", "2012-12-16", "2012-12-23", "2012-12-30"),
                                           group("C", "2012-12-30")), false);
      assertEquals(sorted("C 2012-12-16", "C 2012-12-23"), sorted(filled));
   }

   @Test
   void sundayWeekAfterNewYearIsFilled() {
      // en_GB/de_DE: Sunday 2012-01-01 is ISO week 52 while its YEAR is already 2012
      for(Locale locale : List.of(EN_GB, DE_DE)) {
         Locale.setDefault(locale);
         List<String> filled = fillWeeks(rows(group("A", "2011-12-25", "2012-01-08")), false);
         assertEquals(List.of("A 2012-01-01"), filled, locale.toString());
      }
   }

   @Test
   void leadingSundayWeeksAfterNewYearAreFilled() {
      for(Locale locale : List.of(EN_GB, DE_DE)) {
         Locale.setDefault(locale);
         List<String> filled = fillWeeks(rows(group("E", "2011-12-25", "2012-01-01", "2012-01-08"),
                                              group("D", "2012-01-08")), false);
         assertEquals(sorted("D 2011-12-25", "D 2012-01-01"), sorted(filled), locale.toString());
      }
   }

   @Test
   void trailingWeeksStopAtTheLastWeek() {
      // en_GB: the old key of 2012-01-01 (201252) let the trailing fill run on for a year
      Locale.setDefault(EN_GB);
      List<String> filled = fillWeeks(rows(group("A", "2011-12-11", "2011-12-18"),
                                           group("B", "2011-12-18", "2012-01-01")), false);
      assertEquals(sorted("A 2011-12-25", "A 2012-01-01", "B 2011-12-11", "B 2011-12-25"),
                   sorted(filled));
   }

   @Test
   void mondayWeekBeforeYearEndIsFilled() {
      // week.start=Monday: Monday 2012-12-31 is ISO and en_US week 1 while its YEAR is 2012
      for(Locale locale : List.of(Locale.US, EN_GB, DE_DE)) {
         Locale.setDefault(locale);
         List<String> filled = fillWeeks(rows(group("A", "2012-12-17", "2012-12-31")), false);
         assertEquals(List.of("A 2012-12-24"), filled, locale.toString());

         filled = fillWeeks(rows(group("A", "2012-12-10", "2012-12-17"),
                                 group("B", "2012-12-10", "2012-12-31")), false);
         assertEquals(sorted("A 2012-12-24", "A 2012-12-31", "B 2012-12-17", "B 2012-12-24"),
                      sorted(filled), locale.toString());
      }
   }

   @Test
   void trailingFillStaysInsideTheRange() {
      // A's last value is in the same month as the overall max: no row after that month
      List<String> filled = fill(TimeScale.MONTH, rows(
         group("A", local("2023-01-05T00:00")),
         group("B", local("2023-01-05T00:00"), local("2023-01-25T00:00"))), 0);
      assertEquals(List.of(), filled);
   }

   // ---------------------------------------------------------------------
   // Bug #82039: sub-day steps on DST transition days (America/New_York)
   // ---------------------------------------------------------------------

   @Test
   void repeatedHourIsOnePeriod() {
      // fall-back 2023-11-05: the repeated 01:00 hour is a single bucket (earlier offset)
      List<String> filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-11-05T00:00"), local("2023-11-05T01:00"), local("2023-11-05T02:00"),
         local("2023-11-05T03:00"), local("2023-11-05T04:00"))), 0);
      assertEquals(List.of(), filled);
   }

   @Test
   void repeatedHourWithAGapIsFilledOnce() {
      List<String> filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-11-05T00:00"), local("2023-11-05T01:00"),
         local("2023-11-05T03:00"), local("2023-11-05T04:00"))), 0);
      assertEquals(List.of("A 2023-11-05T02:00-05:00"), filled);
   }

   @Test
   void missingRepeatedHourIsFilledOnce() {
      List<String> filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-11-05T00:00"), local("2023-11-05T02:00"),
         local("2023-11-05T03:00"), local("2023-11-05T04:00"))), 0);
      assertEquals(List.of("A 2023-11-05T01:00-04:00"), filled);
   }

   @Test
   void twoInstantsOfTheRepeatedHourAddNoFiller() {
      // e.g. date_trunc('hour') on a timestamptz column: both 01:00 instants are present
      List<String> filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-11-05T00:00"), offset("2023-11-05T01:00", -4),
         offset("2023-11-05T01:00", -5), local("2023-11-05T02:00"))), 0);
      assertEquals(List.of(), filled);

      filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-11-05T00:00"), offset("2023-11-05T01:00", -4),
         offset("2023-11-05T01:00", -5), local("2023-11-05T03:00"))), 0);
      assertEquals(List.of("A 2023-11-05T02:00-05:00"), filled);
   }

   @Test
   void leadingAndTrailingHoursAcrossTheRepeatedHour() {
      Timestamp[] all = { local("2023-11-05T00:00"), local("2023-11-05T01:00"),
                          local("2023-11-05T02:00"), local("2023-11-05T03:00"),
                          local("2023-11-05T04:00") };

      List<String> filled = fill(TimeScale.HOUR, rows(group("A", all),
                                                      group("B", local("2023-11-05T03:00"))), 0);
      assertEquals(sorted("B 2023-11-05T00:00-04:00", "B 2023-11-05T01:00-04:00",
                          "B 2023-11-05T02:00-05:00", "B 2023-11-05T04:00-05:00"), sorted(filled));

      filled = fill(TimeScale.HOUR, rows(group("A", all),
                                         group("B", local("2023-11-05T00:00"))), 0);
      assertEquals(sorted("B 2023-11-05T01:00-04:00", "B 2023-11-05T02:00-05:00",
                          "B 2023-11-05T03:00-05:00", "B 2023-11-05T04:00-05:00"), sorted(filled));
   }

   @Test
   void repeatedMinutesAreOnePeriod() {
      List<Timestamp> minutes = new ArrayList<>();
      LocalDateTime t = LocalDateTime.parse("2023-11-05T00:58");

      while(!t.isAfter(LocalDateTime.parse("2023-11-05T02:02"))) {
         minutes.add(local(t.toString()));
         t = t.plusMinutes(1);
      }

      List<String> filled = fill(TimeScale.MINUTE,
                                 rows(group("A", minutes.toArray(new Timestamp[0]))), 0);
      assertEquals(List.of(), filled);
   }

   @Test
   void repeatedSecondsAreOnePeriod() {
      List<Timestamp> seconds = new ArrayList<>();
      LocalDateTime t = LocalDateTime.parse("2023-11-05T01:59:50");

      while(!t.isAfter(LocalDateTime.parse("2023-11-05T02:00:10"))) {
         seconds.add(local(t.toString()));
         t = t.plusSeconds(1);
      }

      List<String> filled = fill(TimeScale.SECOND,
                                 rows(group("A", seconds.toArray(new Timestamp[0]))), 0);
      assertEquals(List.of(), filled);
   }

   @Test
   void springForwardAddsNoFillerForTheSkippedHour() {
      // 2023-03-12: 02:00 does not exist
      List<String> filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-03-12T00:00"), local("2023-03-12T01:00"),
         local("2023-03-12T03:00"), local("2023-03-12T04:00"))), 0);
      assertEquals(List.of(), filled);

      filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-03-12T00:00"), local("2023-03-12T01:00"), local("2023-03-12T04:00"))), 0);
      assertEquals(List.of("A 2023-03-12T03:00-04:00"), filled);
   }

   @Test
   void hourGapOnAnOrdinaryDayIsFilled() {
      List<String> filled = fill(TimeScale.HOUR, rows(group("A",
         local("2023-11-07T00:00"), local("2023-11-07T03:00"))), 0);
      assertEquals(List.of("A 2023-11-07T01:00-05:00", "A 2023-11-07T02:00-05:00"), filled);
   }

   // ---------------------------------------------------------------------
   // helpers
   // ---------------------------------------------------------------------

   private List<String> fillWeeks(List<Object[]> data, boolean missingNull) {
      return fill(TimeScale.WEEK, data, missingNull ? null : 0);
   }

   /**
    * Run the gap fill on a data set (header G, Date, Sales) and describe each added
    * row as "group date".
    */
   private List<String> fill(int dtype, List<Object[]> data, Object missingval) {
      List<Object[]> table = new ArrayList<>();
      table.add(new Object[] { "G", "Date", "Sales" });
      table.addAll(data);
      DefaultDataSet dataSet = new DefaultDataSet(table.toArray(new Object[0][]));
      TimeSeriesRow row = new TimeSeriesRow("Date", dtype, missingval);
      row.setGroupFields("G");

      List<Object[]> added = assertTimeoutPreemptively(
         Duration.ofSeconds(10), () -> row.calculate(dataSet));
      List<String> result = new ArrayList<>();

      for(Object[] r : added) {
         Instant instant = Instant.ofEpochMilli(((Date) r[1]).getTime());
         String date = dtype == TimeScale.WEEK
            ? instant.atZone(NEW_YORK).toLocalDate().toString()
            : instant.atZone(NEW_YORK).toOffsetDateTime().toString();
         result.add(r[0] + " " + date);
      }

      return result;
   }

   @SafeVarargs
   private static List<Object[]> rows(List<Object[]>... groups) {
      List<Object[]> rows = new ArrayList<>();

      for(List<Object[]> group : groups) {
         rows.addAll(group);
      }

      return rows;
   }

   private static List<Object[]> group(String name, String... localDates) {
      Timestamp[] dates = new Timestamp[localDates.length];

      for(int i = 0; i < localDates.length; i++) {
         dates[i] = local(localDates[i] + "T00:00");
      }

      return group(name, dates);
   }

   private static List<Object[]> group(String name, Timestamp... dates) {
      List<Object[]> rows = new ArrayList<>();

      for(Timestamp date : dates) {
         rows.add(new Object[] { name, date, 10 });
      }

      return rows;
   }

   /** A local New York time; a repeated time resolves to the earlier offset. */
   private static Timestamp local(String localDateTime) {
      return new Timestamp(ZonedDateTime.of(LocalDateTime.parse(localDateTime), NEW_YORK)
                              .toInstant().toEpochMilli());
   }

   private static Timestamp offset(String localDateTime, int hours) {
      return new Timestamp(LocalDateTime.parse(localDateTime)
                              .atOffset(ZoneOffset.ofHours(hours)).toInstant().toEpochMilli());
   }

   private static List<String> sorted(String... values) {
      List<String> list = new ArrayList<>(Arrays.asList(values));
      Collections.sort(list);
      return list;
   }

   private static List<String> sorted(List<String> values) {
      List<String> list = new ArrayList<>(values);
      Collections.sort(list);
      return list;
   }
}
