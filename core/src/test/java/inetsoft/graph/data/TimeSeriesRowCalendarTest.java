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
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.*;
import java.util.*;
import java.util.stream.Stream;

import static inetsoft.graph.data.TimeSeriesRowFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TimeSeriesRow} gap filling around week numbering, daylight saving and zones. The test
 * sets the default time zone and locale itself and restores both afterwards.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeSeriesRowCalendarTest {
   private static final String DT = "yyyy-MM-dd HH:mm";

   private TimeZone savedZone;
   private Locale savedLocale;

   @BeforeEach
   void saveDefaults() {
      savedZone = TimeZone.getDefault();
      savedLocale = Locale.getDefault();
   }

   @AfterEach
   void restoreDefaults() {
      TimeZone.setDefault(savedZone);
      Locale.setDefault(savedLocale);
   }

   private static void use(Locale locale, String zone) {
      Locale.setDefault(locale);
      TimeZone.setDefault(TimeZone.getTimeZone(zone));
   }

   private static final Locale EN_US = Locale.forLanguageTag("en-US");
   private static final Locale EN_GB = Locale.forLanguageTag("en-GB");
   private static final Locale DE_DE = Locale.forLanguageTag("de-DE");
   private static final Locale TR_TR = Locale.forLanguageTag("tr-TR");
   private static final String NY = "America/New_York";

   static Stream<Locale> locales() {
      return Stream.of(EN_US, EN_GB, DE_DE);
   }

   static Stream<Locale> localesWithTurkish() {
      return Stream.of(EN_US, EN_GB, DE_DE, TR_TR);
   }

   static Stream<String> zones() {
      return Stream.of("UTC", "America/Los_Angeles", "Asia/Kolkata", "Pacific/Auckland", NY);
   }

   private static List<Object[]> weekly(DataSet d) {
      return tsr(TimeScale.WEEK, 0).calculate(d);
   }

   // ---------------------------------------------------------------- week numbering

   @ParameterizedTest(name = "{0}")
   @MethodSource("locales")
   @Disabled("Bug #82038: Week level time series misses weeks next to the first week of a year")
   @DisplayName("a missing week before week 1 of the next year is filled")
   void weekBeforeWeekOneIsFilled(Locale locale) {
      use(locale, "UTC");
      DefaultDataSet d = ds(r(at(2012, 12, 17), "A", 1), r(at(2012, 12, 31), "A", 2));
      assertEquals(exp("2012-12-24|A|0"), render(weekly(d)));
   }

   @Test
   @Disabled("Bug #82038: Week level time series misses weeks next to the first week of a year")
   @DisplayName("a missing week between two Sundays at the year end is filled in en_US")
   void sundayWeekBeforeYearEndIsFilled() {
      use(EN_US, "UTC");
      DefaultDataSet d = ds(r(at(2012, 12, 16), "A", 1), r(at(2012, 12, 30), "A", 2));
      assertEquals(exp("2012-12-23|A|0"), render(weekly(d)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("locales")
   @DisplayName("a long span over the year end fills every week")
   void longSpanOverTheYearEndIsFilled(Locale locale) {
      use(locale, "UTC");
      DefaultDataSet d = ds(r(at(2012, 12, 10), "A", 1), r(at(2013, 1, 14), "A", 2));
      assertEquals(exp("2012-12-17|A|0", "2012-12-24|A|0", "2012-12-31|A|0", "2013-01-07|A|0"),
                   render(weekly(d)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("locales")
   @Disabled("Bug #82038: Week level time series misses weeks next to the first week of a year")
   @DisplayName("leading weeks of a group that starts at the year end are filled")
   void leadingWeeksAcrossTheYearEndAreFilled(Locale locale) {
      use(locale, "UTC");
      DefaultDataSet d = ds(r(at(2012, 12, 17), "A", 1), r(at(2012, 12, 31), "B", 2),
                            r(at(2013, 1, 7), "B", 3));
      assertEquals(exp("2012-12-17|B|0", "2012-12-24|B|0", "2012-12-24|A|0", "2012-12-31|A|0",
                       "2013-01-07|A|0"),
                   render(weekly(d)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("locales")
   @Disabled("Bug #82038: Week level time series misses weeks next to the first week of a year")
   @DisplayName("a missing week after new year between two Sundays is filled")
   void sundayWeekAfterNewYearIsFilled(Locale locale) {
      use(locale, "UTC");
      DefaultDataSet d = ds(r(at(2011, 12, 25), "A", 1), r(at(2012, 1, 8), "A", 2));
      assertEquals(exp("2012-01-01|A|0"), render(weekly(d)));
   }

   @Test
   @DisplayName("a missing week after new year between two Sundays is filled in en_US")
   void sundayWeekAfterNewYearIsFilledInEnUs() {
      use(EN_US, "UTC");
      DefaultDataSet d = ds(r(at(2011, 12, 25), "A", 1), r(at(2012, 1, 8), "A", 2));
      assertEquals(exp("2012-01-01|A|0"), render(weekly(d)));
   }

   @ParameterizedTest(name = "{0}")
   @MethodSource("localesWithTurkish")
   @DisplayName("mid year weeks are filled the same in every locale")
   void midYearWeeksAreFilledInEveryLocale(Locale locale) {
      use(locale, "UTC");
      DefaultDataSet d = ds(r(at(2023, 3, 6), "A", 1), r(at(2023, 3, 27), "A", 2));
      assertEquals(exp("2023-03-13|A|0", "2023-03-20|A|0"), render(weekly(d)));
   }

   // ---------------------------------------------------------------- daylight saving

   @Test
   void daysAreFilledOverSpringForward() {
      use(EN_US, NY);
      DefaultDataSet d = ds(r(at(2023, 3, 11), "A", 1), r(at(2023, 3, 14), "A", 2));
      assertEquals(exp("2023-03-12 12:00|A|0", "2023-03-13 12:00|A|0"),
                   render(tsr(TimeScale.DAY, 0).calculate(d), DT));
   }

   @Test
   void daysAreFilledOverFallBack() {
      use(EN_US, NY);
      DefaultDataSet d = ds(r(at(2023, 11, 4), "A", 1), r(at(2023, 11, 7), "A", 2));
      assertEquals(exp("2023-11-05 12:00|A|0", "2023-11-06 12:00|A|0"),
                   render(tsr(TimeScale.DAY, 0).calculate(d), DT));
   }

   @Test
   void hoursSkipTheHourThatDoesNotExist() {
      use(EN_US, NY);
      DefaultDataSet d = ds(r(at(2023, 3, 12, 0, 0), "A", 1), r(at(2023, 3, 12, 4, 0), "A", 2));
      assertEquals(exp("2023-03-12 01:00|A|0", "2023-03-12 03:00|A|0"),
                   render(tsr(TimeScale.HOUR, 0).calculate(d), DT));
   }

   @Test
   @Disabled("Bug #82039: Hour level time series adds a filler row at the repeated DST hour")
   @DisplayName("the repeated hour of the fall back day is one period")
   void repeatedHourIsOnePeriod() {
      use(EN_US, NY);
      ZonedDateTime start = ZonedDateTime.of(2023, 11, 5, 0, 0, 0, 0, ZoneId.of(NY));
      DefaultDataSet d = ds(r(Date.from(start.toInstant()), "A", 1),
                            r(Date.from(start.plusHours(3).toInstant()), "A", 2));
      assertEquals(exp("2023-11-05 01:00|A|0", "2023-11-05 02:00|A|0"),
                   render(tsr(TimeScale.HOUR, 0).calculate(d), DT));
   }

   @Test
   @Disabled("Bug #82039: Hour level time series adds a filler row at the repeated DST hour")
   @DisplayName("hours 00:00 to 03:00 of the fall back day without a gap add nothing")
   void hoursOfTheFallBackDayWithoutAGapAddNothing() {
      use(EN_US, NY);
      // wall hours 00:00 and 01:00 are EDT, 02:00 and 03:00 are EST (the 01:00 EST hour is
      // not in the data, so no hour is missing in wall-clock buckets)
      ZonedDateTime start = ZonedDateTime.of(2023, 11, 5, 0, 0, 0, 0, ZoneId.of(NY));
      DefaultDataSet d = ds(r(Date.from(start.toInstant()), "A", 1),
                            r(Date.from(start.plusHours(1).toInstant()), "A", 2),
                            r(Date.from(start.plusHours(3).toInstant()), "A", 3),
                            r(Date.from(start.plusHours(4).toInstant()), "A", 4));
      assertEquals(List.of(), tsr(TimeScale.HOUR, 0).calculate(d));
   }

   @Test
   void hoursOnTheSameDaysInUtcAreFilledOnce() {
      use(EN_US, "UTC");
      DefaultDataSet d = ds(r(at(2023, 11, 5, 0, 0), "A", 1), r(at(2023, 11, 5, 3, 0), "A", 2));
      assertEquals(exp("2023-11-05 01:00|A|0", "2023-11-05 02:00|A|0"),
                   render(tsr(TimeScale.HOUR, 0).calculate(d), DT));
   }

   // ---------------------------------------------------------------- zones

   @ParameterizedTest(name = "{0}")
   @MethodSource("zones")
   @DisplayName("calendar fields of the added days and months do not depend on the zone")
   void addedFieldsAreTheSameInEveryZone(String zone) {
      use(EN_US, zone);
      DefaultDataSet days = ds(r(at(2023, 2, 27), "A", 1), r(at(2023, 3, 2), "A", 2));
      assertEquals(exp("2023-02-28|A|0", "2023-03-01|A|0"),
                   render(tsr(TimeScale.DAY, 0).calculate(days)));
      DefaultDataSet months = ds(r(at(2023, 1, 31), "A", 1), r(at(2023, 4, 30), "A", 2));
      assertEquals(exp("2023-02|A|0", "2023-03|A|0"),
                   render(tsr(TimeScale.MONTH, 0).calculate(months), "yyyy-MM"));
   }
}
