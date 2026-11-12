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
import inetsoft.uql.XConstants;
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
 * The value {@link DateRangeRef#getData(int, Date)} gives for each date group level, the value
 * the worksheet groups by when the level is not pushed to the database. The expected values are
 * worked out here with {@code java.time}, for a week that starts on Sunday and where the week of
 * January 1 is week 1 (the default of the product).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateRangeRefLevelValuesTest {
   private static final ZoneId ZONE = ZoneId.systemDefault();

   /** leap day, year and quarter edges, Sundays and Mondays, the last day of a leap year */
   private static final LocalDateTime[] DATES = {
      LocalDateTime.of(2024, 2, 29, 13, 45, 30),
      LocalDateTime.of(2023, 12, 31, 23, 59, 59),
      LocalDateTime.of(2024, 1, 1, 0, 0, 0),
      LocalDateTime.of(2023, 1, 1, 8, 5, 9),
      LocalDateTime.of(2021, 1, 3, 12, 0, 0),
      LocalDateTime.of(2024, 3, 31, 6, 30, 0),
      LocalDateTime.of(2024, 4, 1, 0, 0, 1),
      LocalDateTime.of(2024, 12, 31, 23, 0, 0),
      LocalDateTime.of(2026, 7, 15, 12, 0, 0),
      LocalDateTime.of(2025, 10, 1, 17, 59, 0)
   };

   private static Timestamp ts(LocalDateTime t) {
      return Timestamp.from(t.atZone(ZONE).toInstant());
   }

   private static LocalDateTime startOfQuarter(LocalDateTime t) {
      return LocalDateTime.of(t.getYear(), (t.getMonthValue() - 1) / 3 * 3 + 1, 1, 0, 0, 0);
   }

   /** Sunday is 0. */
   private static int sundayBased(LocalDate d) {
      return d.getDayOfWeek().getValue() % 7;
   }

   /**
    * The week that has January 1 is week 1, a week starts on Sunday. The last days of December
    * that are in the week of the next January 1 are in week 1 as well.
    */
   private static int weekOfYear(LocalDate d) {
      LocalDate weekEnd = d.minusDays(sundayBased(d)).plusDays(6);

      if(weekEnd.getYear() > d.getYear()) {
         return 1;
      }

      int jan1 = sundayBased(LocalDate.of(d.getYear(), 1, 1));
      return (d.getDayOfYear() - 1 + jan1) / 7 + 1;
   }

   private record Level(String name, int option, Function<LocalDateTime, Object> expected) {
   }

   private static final List<Level> LEVELS = new ArrayList<>();

   static {
      LEVELS.add(new Level("year", DateRangeRef.YEAR_INTERVAL,
         t -> ts(LocalDateTime.of(t.getYear(), 1, 1, 0, 0))));
      LEVELS.add(new Level("quarter", DateRangeRef.QUARTER_INTERVAL,
         t -> ts(startOfQuarter(t))));
      LEVELS.add(new Level("month", DateRangeRef.MONTH_INTERVAL,
         t -> ts(LocalDateTime.of(t.getYear(), t.getMonthValue(), 1, 0, 0))));
      LEVELS.add(new Level("week", DateRangeRef.WEEK_INTERVAL,
         t -> ts(t.toLocalDate().minusDays(sundayBased(t.toLocalDate())).atStartOfDay())));
      LEVELS.add(new Level("day", DateRangeRef.DAY_INTERVAL,
         t -> ts(t.truncatedTo(ChronoUnit.DAYS))));
      LEVELS.add(new Level("hour", DateRangeRef.HOUR_INTERVAL,
         t -> ts(t.truncatedTo(ChronoUnit.HOURS))));
      LEVELS.add(new Level("minute", DateRangeRef.MINUTE_INTERVAL,
         t -> ts(t.truncatedTo(ChronoUnit.MINUTES))));
      LEVELS.add(new Level("second", DateRangeRef.SECOND_INTERVAL,
         t -> ts(t.truncatedTo(ChronoUnit.SECONDS))));
      LEVELS.add(new Level("quarter of year", DateRangeRef.QUARTER_OF_YEAR_PART,
         t -> (t.getMonthValue() - 1) / 3 + 1));
      LEVELS.add(new Level("month of year", DateRangeRef.MONTH_OF_YEAR_PART,
         LocalDateTime::getMonthValue));
      LEVELS.add(new Level("week of year", DateRangeRef.WEEK_OF_YEAR_PART,
         t -> weekOfYear(t.toLocalDate())));
      LEVELS.add(new Level("day of month", DateRangeRef.DAY_OF_MONTH_PART,
         LocalDateTime::getDayOfMonth));
      // Sunday is 1
      LEVELS.add(new Level("day of week", DateRangeRef.DAY_OF_WEEK_PART,
         t -> sundayBased(t.toLocalDate()) + 1));
      LEVELS.add(new Level("hour of day", DateRangeRef.HOUR_OF_DAY_PART, LocalDateTime::getHour));
      LEVELS.add(new Level("minute of hour", DateRangeRef.MINUTE_OF_HOUR_PART,
         LocalDateTime::getMinute));
      LEVELS.add(new Level("second of minute", DateRangeRef.SECOND_OF_MINUTE_PART,
         LocalDateTime::getSecond));
      // the levels that only the data cube / date comparison have
      LEVELS.add(new Level("day of year", DateRangeRef.DAY_OF_YEAR_PART,
         LocalDateTime::getDayOfYear));
      LEVELS.add(new Level("day of quarter", DateRangeRef.DAY_OF_QUARTER_PART,
         t -> (int) ChronoUnit.DAYS.between(startOfQuarter(t).toLocalDate(), t.toLocalDate()) + 1));
      LEVELS.add(new Level("month of quarter", DateRangeRef.MONTH_OF_QUARTER_PART,
         t -> (t.getMonthValue() - 1) % 3 + 1));
   }

   @Test
   void eachLevelGivesTheValueOfTheDate() {
      List<String> problems = new ArrayList<>();

      for(Level level : LEVELS) {
         for(LocalDateTime t : DATES) {
            Object actual = DateRangeRef.getData(level.option(), ts(t));
            Object expected = level.expected().apply(t);
            boolean same = expected instanceof Timestamp ?
               actual instanceof Date && ((Date) actual).getTime() == ((Timestamp) expected).getTime() :
               expected.equals(actual);

            if(!same) {
               problems.add(level.name() + " of " + t + ": expected " + expected + " but was " +
                            actual);
            }
         }
      }

      assertTrue(problems.isEmpty(), String.join("\n", problems));
   }

   @Test
   void noDateHasNoValueOnAnyLevel() {
      for(Level level : LEVELS) {
         assertNull(DateRangeRef.getData(level.option(), null), level.name());
      }
   }

   @Test
   void noGroupKeepsTheDate() {
      Timestamp date = ts(DATES[0]);
      assertSame(date, DateRangeRef.getData(DateRangeRef.NONE_INTERVAL, date));
   }

   // values that are the same for every instant of the hour or day group together, others do not
   @Test
   void instantsOfTheSameDayShareADayAndHourButNotTheMinute() {
      Timestamp a = ts(LocalDateTime.of(2024, 5, 6, 10, 15, 0));
      Timestamp b = ts(LocalDateTime.of(2024, 5, 6, 10, 45, 59));
      Timestamp c = ts(LocalDateTime.of(2024, 5, 6, 11, 0, 0));

      assertEquals(DateRangeRef.getData(DateRangeRef.DAY_INTERVAL, a),
                   DateRangeRef.getData(DateRangeRef.DAY_INTERVAL, c));
      assertEquals(DateRangeRef.getData(DateRangeRef.HOUR_INTERVAL, a),
                   DateRangeRef.getData(DateRangeRef.HOUR_INTERVAL, b));
      assertNotEquals(DateRangeRef.getData(DateRangeRef.HOUR_INTERVAL, a),
                      DateRangeRef.getData(DateRangeRef.HOUR_INTERVAL, c));
      assertNotEquals(DateRangeRef.getData(DateRangeRef.MINUTE_INTERVAL, a),
                      DateRangeRef.getData(DateRangeRef.MINUTE_INTERVAL, b));
   }

   // the constants for am/pm and millisecond exist in XConstants (AM_PM_DATE_GROUP,
   // MILLISECOND_DATE_GROUP, AM_PM_OF_DAY_DATE_GROUP) but getData has no such level and rejects
   // them. The web client defines the constants but no dialog uses them; the viewsheet script
   // constants (ChartConstants) list them. Pins the current behavior.
   @Test
   void amPmAndMillisecondAreNotRangeLevels() {
      Timestamp date = ts(DATES[0]);

      for(int option : new int[] { XConstants.AM_PM_DATE_GROUP, XConstants.MILLISECOND_DATE_GROUP,
                                   XConstants.AM_PM_OF_DAY_DATE_GROUP })
      {
         RuntimeException ex = assertThrows(RuntimeException.class,
                                            () -> DateRangeRef.getData(option, date));
         assertTrue(ex.getMessage().contains("Unsupported option"), ex.getMessage());
      }
   }
}
