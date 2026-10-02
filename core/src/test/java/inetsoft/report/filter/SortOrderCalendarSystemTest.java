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
package inetsoft.report.filter;

import inetsoft.test.*;
import inetsoft.uql.XConstants;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77527: in-memory date grouping must use Gregorian years whatever the calendar system
 * of the JVM default locale is (Buddhist for th_TH, Japanese imperial for ja_JP_JP).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SortOrderCalendarSystemTest {
   static Stream<Locale> locales() {
      return Stream.of(Locale.US, Locale.of("th", "TH"), Locale.of("ja", "JP", "JP"));
   }

   @ParameterizedTest
   @MethodSource("locales")
   void yearInterval10GroupsByGregorianDecade(Locale locale) throws Exception {
      Object[] result = runWithDefaultLocale(locale, () -> {
         SortOrder order = new SortOrder(SortOrder.SORT_ASC);
         order.setInterval(10, XConstants.YEAR_DATE_GROUP);
         int cmp = order.compare(date(2019, Calendar.JUNE, 15), date(2024, Calendar.JUNE, 15), true);
         return new Object[] { cmp, order.getGroupDate() };
      });

      assertTrue((Integer) result[0] < 0, "2019 and 2024 are in different decades");
      assertGregorianDate(2020, Calendar.JANUARY, 1, (Date) result[1]);
   }

   @ParameterizedTest
   @MethodSource("locales")
   void yearInterval1DoesNotMergeYearsOfDifferentEras(Locale locale) throws Exception {
      Object[] result = runWithDefaultLocale(locale, () -> {
         SortOrder order = new SortOrder(SortOrder.SORT_ASC);
         order.setInterval(1, XConstants.YEAR_DATE_GROUP);
         int cmp = order.compare(date(2024, Calendar.JUNE, 15), date(1994, Calendar.JUNE, 15), true);
         return new Object[] { cmp, order.getGroupDate() };
      });

      // Heisei 6 (1994) and Reiwa 6 (2024) have the same year field in the Japanese calendar
      assertTrue((Integer) result[0] > 0, "1994 and 2024 are different groups");
      assertGregorianDate(1994, Calendar.JANUARY, 1, (Date) result[1]);
   }

   @ParameterizedTest
   @MethodSource("locales")
   void dayOfYearPartGroupDateIsInGregorian1970(Locale locale) throws Exception {
      Object[] result = runWithDefaultLocale(locale, () -> {
         SortOrder order = new SortOrder(SortOrder.SORT_ASC);
         order.setInterval(1, XConstants.DAY_OF_YEAR_DATE_GROUP);
         // both are day 61 of their year
         int cmp = order.compare(date(2023, Calendar.MARCH, 2), date(2024, Calendar.MARCH, 1), true);
         return new Object[] { cmp, order.getGroupDate() };
      });

      assertEquals(0, result[0]);
      // day 61 of the year, placed in 1970 like the en_US result
      assertGregorianDate(1970, Calendar.MARCH, 2, (Date) result[1]);
   }

   /**
    * Runs the task on a new thread so the SortOrder calendar thread locals are created under
    * the given default locale, and restores the default locale afterwards.
    */
   private static <T> T runWithDefaultLocale(Locale locale, Callable<T> task) throws Exception {
      Locale old = Locale.getDefault();
      Locale.setDefault(locale);
      ExecutorService executor = Executors.newSingleThreadExecutor();

      try {
         return executor.submit(task).get(30, TimeUnit.SECONDS);
      }
      finally {
         executor.shutdownNow();
         Locale.setDefault(old);
      }
   }

   private static Date date(int year, int month, int day) {
      return new GregorianCalendar(year, month, day).getTime();
   }

   private static void assertGregorianDate(int year, int month, int day, Date actual) {
      assertNotNull(actual);
      Calendar cal = new GregorianCalendar();
      cal.setTime(actual);
      assertEquals(year, cal.get(Calendar.YEAR), "year of " + actual);
      assertEquals(month, cal.get(Calendar.MONTH), "month of " + actual);
      assertEquals(day, cal.get(Calendar.DATE), "day of " + actual);
   }
}
