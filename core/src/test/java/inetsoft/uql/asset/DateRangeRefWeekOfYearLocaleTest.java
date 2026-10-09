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
package inetsoft.uql.asset;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #78112: the Crosstab "Week of Year" date level
 * ({@code DateRangeRef.getData(WEEK_OF_YEAR_PART, date)}, backed by
 * {@code DateTimeProcessor.getWeekOfYear()}'s {@code jcalendar}) silently disagreed with
 * {@code CALC.weeknum(d, 1)} on the same server, and disagreed with itself between an
 * en_US/zh_CN server and an en_GB/de_DE server, because the underlying
 * {@code GregorianCalendar} only ever had {@code firstDayOfWeek} re-synced, never
 * {@code minimalDaysInFirstWeek} -- so it kept whatever the JVM default locale seeded it
 * with (1 for en_US/zh_CN/root, 4 for en_GB/de_DE/fr_FR).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateRangeRefWeekOfYearLocaleTest {
   /**
    * The report's exact repro dates: 2026-01-01..03 (and the control date 2021-01-01) must
    * all be week 1 regardless of the server's JVM default locale -- not week 53 of the
    * previous year, which is what en-GB/de-DE produced before this fix.
    */
   @Test
   void weekOfYearPartIgnoresTheDefaultLocale() throws InterruptedException {
      Locale old = Locale.getDefault();

      try {
         Date[] days = {
            makeDate(2025, Calendar.DECEMBER, 28), makeDate(2025, Calendar.DECEMBER, 31),
            makeDate(2026, Calendar.JANUARY, 1), makeDate(2026, Calendar.JANUARY, 2),
            makeDate(2026, Calendar.JANUARY, 3), makeDate(2026, Calendar.JANUARY, 4),
            makeDate(2026, Calendar.JANUARY, 15), makeDate(2021, Calendar.JANUARY, 1)
         };

         List<Integer> underEnUS = weekOfYearParts(Locale.US, days);

         for(Locale locale : new Locale[]{
            Locale.UK, Locale.GERMANY, Locale.SIMPLIFIED_CHINESE })
         {
            List<Integer> underLocale = weekOfYearParts(locale, days);

            for(int i = 0; i < days.length; i++) {
               assertEquals(underEnUS.get(i), underLocale.get(i),
                            "Week of Year for " + days[i] + " changed with the JVM default " +
                               "locale (" + locale + ")");
            }
         }
      }
      finally {
         Locale.setDefault(old);
      }
   }

   /**
    * The reporter's headline symptom: 2026-01-01 (a Thursday) must be week 1 under en-GB,
    * matching {@code CALC.weeknum(d, 1)} on the very same server -- not week 53 of 2025.
    */
   @Test
   void weekOfYearPartJan1st2026IsWeekOneUnderEnGB() throws InterruptedException {
      Locale old = Locale.getDefault();

      try {
         Date jan1_2026 = makeDate(2026, Calendar.JANUARY, 1);
         assertEquals(1, weekOfYearParts(Locale.UK, new Date[]{ jan1_2026 }).get(0),
                      "2026-01-01 should be week 1 under en-GB, matching CALC.weeknum");
      }
      finally {
         Locale.setDefault(old);
      }
   }

   /**
    * {@code DateRangeRef} caches its {@code DateTimeProcessor} in a per-thread
    * {@code ThreadLocal}, constructed lazily on first use and kept for that thread's
    * lifetime (only invalidated by a {@code week.start} change, never a locale change). Reusing
    * one thread across locale switches would mask this bug entirely -- the first locale to
    * touch the thread locks in that locale's {@code minimalDaysInFirstWeek} for every later
    * call on the same thread, regardless of {@code Locale.setDefault()} changes made
    * afterward. Run each locale's lookups on a fresh thread so every one actually constructs
    * its own {@code DateTimeProcessor} under the locale being tested.
    */
   private static List<Integer> weekOfYearParts(Locale defaultLocale, Date[] days)
      throws InterruptedException
   {
      Locale.setDefault(defaultLocale);
      List<Integer> results = new ArrayList<>();
      Thread thread = new Thread(() -> {
         for(Date day : days) {
            results.add((Integer) DateRangeRef.getData(DateRangeRef.WEEK_OF_YEAR_PART, day));
         }
      });
      thread.start();
      thread.join();
      return results;
   }

   private static Date makeDate(int year, int month, int day) {
      Calendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, month, day, 12, 0, 0);
      return cal.getTime();
   }
}
