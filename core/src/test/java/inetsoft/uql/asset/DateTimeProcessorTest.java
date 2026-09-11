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
import inetsoft.util.Tool;
import inetsoft.util.script.JavaScriptEngine;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The full-week date levels must agree with each other and with the {@code datePart('wy')}
 * expression that feeds them, for any configured week start.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateTimeProcessorTest {
   /**
    * {@code getMonthPartOfFullWeek} is the week-start-correct sibling, so it is the oracle
    * for the month half of the {@code (month + 1) * 10 + weekOfMonth} encoding that
    * {@code datePart('wy')} produces.
    */
   @ParameterizedTest
   @ValueSource(strings = { "sunday", "monday", "wednesday" })
   void monthPartOfFullWeekMatchesWeekOfYearDatePart(String weekStart) {
      WeekStartUtil.withWeekStart(weekStart, () -> {
         for(Date day : everyDayOf(2021)) {
            DateTimeProcessor processor = DateTimeProcessor.at(day.getTime());
            int expected = (int) (JavaScriptEngine.datePart("wy", day, true) / 10);

            assertEquals(expected, processor.getMonthPartOfFullWeek(-1),
                         "month of full week disagrees with datePart('wy') for " + day +
                            " (" + weekStart + " week start)");
         }
      });
   }

   /**
    * {@code getMonthOfFullWeek} rewinds differently from its sibling above; the two must
    * still land on the same month for every date and every forced week-of-month.
    */
   @ParameterizedTest
   @ValueSource(strings = { "sunday", "monday", "wednesday" })
   void monthOfFullWeekAgreesWithMonthPartOfFullWeek(String weekStart) {
      WeekStartUtil.withWeekStart(weekStart, () -> {
         Calendar cal = new GregorianCalendar();

         for(Date day : everyDayOf(2021)) {
            for(int forceWM = -1; forceWM <= 5; forceWM++) {
               DateTimeProcessor processor = DateTimeProcessor.at(day.getTime());
               Timestamp month = processor.getMonthOfFullWeek(forceWM);
               cal.setTime(month);

               assertEquals(DateTimeProcessor.at(day.getTime()).getMonthPartOfFullWeek(forceWM),
                            cal.get(Calendar.MONTH) + 1,
                            "getMonthOfFullWeek and getMonthPartOfFullWeek disagree for " +
                               day + " forceWM=" + forceWM + " (" + weekStart + ")");
            }
         }
      });
   }

   /**
    * {@code getYearOfFullWeek} built a bare {@code GregorianCalendar}, so it started weeks
    * wherever the JVM default locale said rather than at the configured week start.
    */
   @Test
   void yearOfFullWeekIgnoresTheDefaultLocale() {
      Locale old = Locale.getDefault();

      try {
         WeekStartUtil.withWeekStart("monday", () -> {
            // one setDefault per pass rather than one per assertion, to keep the window in
            // which this test perturbs JVM-global state as short as possible.
            List<Timestamp> underSunday = yearsOfFullWeek(Locale.US);
            List<Timestamp> underMonday = yearsOfFullWeek(Locale.UK);
            List<String> labels = labels();

            for(int i = 0; i < labels.size(); i++) {
               assertEquals(underSunday.get(i), underMonday.get(i),
                            "year of full week changed with the JVM default locale for " +
                               labels.get(i));
            }
         });
      }
      finally {
         Locale.setDefault(old);
      }
   }

   private static List<Timestamp> yearsOfFullWeek(Locale defaultLocale) {
      Locale.setDefault(defaultLocale);
      List<Timestamp> results = new ArrayList<>();

      for(Date day : everyDayOf(2021)) {
         for(int forceWM = -1; forceWM <= 5; forceWM++) {
            results.add(DateTimeProcessor.at(day.getTime()).getYearOfFullWeek(forceWM));
         }
      }

      return results;
   }

   /** One label per (date, forceWM) pair, in the same order as {@link #yearsOfFullWeek}. */
   private static List<String> labels() {
      List<String> labels = new ArrayList<>();

      for(Date day : everyDayOf(2021)) {
         for(int forceWM = -1; forceWM <= 5; forceWM++) {
            labels.add(day + " forceWM=" + forceWM);
         }
      }

      return labels;
   }

   private static List<Date> everyDayOf(int year) {
      List<Date> days = new ArrayList<>();
      Calendar cal = new GregorianCalendar();
      cal.clear();
      cal.set(year, Calendar.JANUARY, 1, 12, 0, 0);

      while(cal.get(Calendar.YEAR) == year) {
         days.add(cal.getTime());
         cal.add(Calendar.DATE, 1);
      }

      return days;
   }
}
