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
package inetsoft.uql.asset;

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The date group levels that are made of whole weeks (week of month, week of quarter, and the
 * "full week" levels of the date comparison). Weeks start on Sunday. A week belongs to the month
 * its first day is in: the days at the start of a month that are in the week of the previous
 * month belong to that month, quarter and year (2024-01-01 is in the last week of 2023). The
 * first week of a month is the first week that starts in it, the first week of a quarter the
 * first week of its first month. The expected values are worked out here with {@code java.time}
 * from these rules.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateRangeRefFullWeekLevelsTest {
   private static final ZoneId ZONE = ZoneId.systemDefault();

   private static final LocalDateTime[] DATES = {
      LocalDateTime.of(2024, 1, 1, 9, 30),    // Monday, in the last week of December
      LocalDateTime.of(2024, 1, 2, 9, 30),
      LocalDateTime.of(2024, 1, 7, 9, 30),    // Sunday, the first week of January
      LocalDateTime.of(2024, 1, 28, 9, 30),   // Sunday, the week into February
      LocalDateTime.of(2024, 1, 31, 9, 30),
      LocalDateTime.of(2024, 2, 1, 9, 30),    // Thursday, in the week of January
      LocalDateTime.of(2024, 2, 3, 9, 30),    // Saturday, the end of that week
      LocalDateTime.of(2024, 2, 4, 9, 30),    // Sunday, the first week of February
      LocalDateTime.of(2024, 2, 29, 13, 45),
      LocalDateTime.of(2024, 3, 31, 6, 30),   // Sunday, the week into April
      LocalDateTime.of(2024, 4, 1, 0, 0),     // Monday, in the week of March
      LocalDateTime.of(2024, 7, 1, 8, 0),     // Monday, in the week of June
      LocalDateTime.of(2024, 10, 1, 17, 59),  // Tuesday, in the week of September
      LocalDateTime.of(2024, 12, 31, 23, 0),  // Tuesday, in the week of 2025-01-01
      LocalDateTime.of(2025, 1, 1, 12, 0),
      LocalDateTime.of(2023, 12, 31, 23, 59)  // Sunday
   };

   private static Timestamp ts(LocalDateTime t) {
      return Timestamp.from(t.atZone(ZONE).toInstant());
   }

   private static LocalDate weekStart(LocalDate d) {
      return d.minusDays(d.getDayOfWeek().getValue() % 7);
   }

   /** The first Sunday of the month of the date. */
   private static LocalDate firstWeekStart(LocalDate monthDate) {
      LocalDate first = monthDate.withDayOfMonth(1);
      return first.plusDays((7 - first.getDayOfWeek().getValue() % 7) % 7);
   }

   private static LocalDate startOfQuarterMonth(LocalDate d) {
      return LocalDate.of(d.getYear(), (d.getMonthValue() - 1) / 3 * 3 + 1, 1);
   }

   private static int weekOfMonth(LocalDate d) {
      LocalDate first = firstWeekStart(d);

      if(!d.isBefore(first)) {
         return (d.getDayOfMonth() - first.getDayOfMonth()) / 7 + 1;
      }

      // before the first week of its month: the last week of the month before
      LocalDate start = weekStart(d);
      return (start.getDayOfMonth() - firstWeekStart(start).getDayOfMonth()) / 7 + 1;
   }

   private static int weekOfQuarter(LocalDate d) {
      LocalDate quarter = startOfQuarterMonth(weekStart(d));
      long days = ChronoUnit.DAYS.between(firstWeekStart(quarter), d) + 1;
      return (int) ((days + 6) / 7);
   }

   private record Level(String name, int option, Function<LocalDate, Object> expected) {
   }

   private static final List<Level> LEVELS = new ArrayList<>();

   static {
      LEVELS.add(new Level("week of month", DateRangeRef.WEEK_OF_MONTH_PART,
         DateRangeRefFullWeekLevelsTest::weekOfMonth));
      LEVELS.add(new Level("week of quarter", DateRangeRef.WEEK_OF_QUARTER_PART,
         DateRangeRefFullWeekLevelsTest::weekOfQuarter));
      LEVELS.add(new Level("month of quarter of full week",
         DateRangeRef.MONTH_OF_QUARTER_FULL_WEEK_PART,
         d -> (weekStart(d).getMonthValue() - 1) % 3 + 1));
      LEVELS.add(new Level("month of full week", DateRangeRef.MONTH_OF_FULL_WEEK, d -> {
         LocalDate start = weekStart(d);
         return ts(start.withDayOfMonth(1).atStartOfDay());
      }));
      LEVELS.add(new Level("month part of full week", DateRangeRef.MONTH_OF_FULL_WEEK_PART,
         d -> weekStart(d).getMonthValue()));
      LEVELS.add(new Level("quarter part of full week", DateRangeRef.QUARTER_OF_FULL_WEEK_PART,
         d -> (weekStart(d).getMonthValue() - 1) / 3 + 1));
      LEVELS.add(new Level("quarter of full week", DateRangeRef.QUARTER_OF_FULL_WEEK,
         d -> ts(startOfQuarterMonth(weekStart(d)).atStartOfDay())));
      LEVELS.add(new Level("year of full week", DateRangeRef.YEAR_OF_FULL_WEEK,
         d -> ts(LocalDate.of(weekStart(d).getYear(), 1, 1).atStartOfDay())));
   }

   @Test
   void eachLevelGivesTheValueOfTheWeek() {
      List<String> problems = new ArrayList<>();

      for(Level level : LEVELS) {
         for(LocalDateTime t : DATES) {
            Object actual = DateRangeRef.getData(level.option(), ts(t));
            Object expected = level.expected().apply(t.toLocalDate());
            boolean same = expected instanceof Timestamp ?
               actual instanceof Date && ((Date) actual).getTime() == ((Timestamp) expected).getTime() :
               expected.equals(actual);

            if(!same) {
               problems.add(level.name() + " of " + t.toLocalDate() + ": expected " + expected +
                            " but was " + actual);
            }
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   // the example of the code: 2024-01-01 is in the last full week of 2023
   @Test
   void theFirstDaysOfAYearAreInTheYearOfTheWeek() {
      Object year = DateRangeRef.getData(DateRangeRef.YEAR_OF_FULL_WEEK,
                                         ts(LocalDateTime.of(2024, 1, 1, 0, 0)));

      assertEquals(ts(LocalDateTime.of(2023, 1, 1, 0, 0)), year);
   }

   // a month that starts on a Sunday has no partial week: 2023-10-01 is a Sunday
   @Test
   void monthStartingOnTheWeekStartHasNoPartialWeek() {
      assertEquals(1, DateRangeRef.getData(DateRangeRef.WEEK_OF_MONTH_PART,
                                           ts(LocalDateTime.of(2023, 10, 1, 8, 0))));
      assertEquals(1, DateRangeRef.getData(DateRangeRef.WEEK_OF_MONTH_PART,
                                           ts(LocalDateTime.of(2023, 10, 7, 8, 0))));
      assertEquals(2, DateRangeRef.getData(DateRangeRef.WEEK_OF_MONTH_PART,
                                           ts(LocalDateTime.of(2023, 10, 8, 8, 0))));
   }
}
