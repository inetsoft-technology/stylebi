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
package inetsoft.util;

import inetsoft.test.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The first day of week must be a pure function of the {@code week.start} property.
 *
 * <p>It used to fall back to {@code GregorianCalendar.getInstance(locale)} whenever the
 * property was unset -- and the shipped default is blank, so that was the normal path. The
 * locale is {@link ThreadContext#getLocale()}, i.e. the viewing user's, so the same dashboard
 * bucketed weeks differently for different users, and disagreed with the Sunday-based week
 * expression {@code DateRangeRef} pushes down to SQL.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ToolWeekStartTest {
   private static final List<String> DAY_NAMES =
      List.of("sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday");

   @ParameterizedTest
   @ValueSource(strings = { "en-US", "en-GB", "zh-CN", "de-DE", "ja-JP" })
   void blankWeekStartIsSundayRegardlessOfLocale(String languageTag) {
      Locale old = ThreadContext.getLocale();

      try {
         ThreadContext.setLocale(Locale.forLanguageTag(languageTag));
         WeekStartUtil.withWeekStart("", () ->
            assertEquals(Calendar.SUNDAY, Tool.getFirstDayOfWeek(),
                         "blank week.start must not follow the " + languageTag + " locale"));
      }
      finally {
         ThreadContext.setLocale(old);
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { "foo", " ", "2", "MON" })
   void invalidWeekStartIsSunday(String value) {
      WeekStartUtil.withWeekStart(value, () ->
         assertEquals(Calendar.SUNDAY, Tool.getFirstDayOfWeek(),
                      "invalid week.start \"" + value + "\" must fall back to Sunday"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "monday", "Monday", "MONDAY" })
   void dayNameIsMatchedCaseInsensitively(String value) {
      WeekStartUtil.withWeekStart(value, () ->
         assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek()));
   }

   @Test
   void everyDayNameResolves() {
      for(int i = 0; i < DAY_NAMES.size(); i++) {
         final int expected = Calendar.SUNDAY + i;
         WeekStartUtil.withWeekStart(DAY_NAMES.get(i), () ->
            assertEquals(expected, Tool.getFirstDayOfWeek()));
      }
   }

   /**
    * {@code getWeekStart()} reads the property directly while {@code getFirstDayOfWeek()}
    * reads it through a timed cache. {@code DateRangeRef.getExpression()} consults both to
    * decide whether to push week grouping down to SQL, so they must never disagree.
    */
   @Test
   void weekStartAndFirstDayOfWeekAgree() {
      for(String day : new String[]{ "sunday", "monday", "saturday", "", "bogus" }) {
         WeekStartUtil.withWeekStart(day, () -> {
            String weekStart = Tool.getWeekStart();
            int firstDay = Tool.getFirstDayOfWeek();

            if(weekStart == null) {
               assertEquals(Calendar.SUNDAY, firstDay,
                            "no configured week start must mean Sunday");
            }
            else {
               assertEquals(DAY_NAMES.indexOf(weekStart) + Calendar.SUNDAY, firstDay,
                            "\"" + weekStart + "\" resolves to a different day than " +
                               "getWeekStart() reports");
            }
         });
      }
   }

   /**
    * A change made through the EM must be visible at once, not after the property cache
    * timeout -- otherwise the two accessors above disagree for the length of it.
    */
   @Test
   void cacheClearMakesAChangeVisibleImmediately() {
      WeekStartUtil.withWeekStart("monday", () -> {
         assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek());
         WeekStartUtil.withWeekStart("thursday", () ->
            assertEquals(Calendar.THURSDAY, Tool.getFirstDayOfWeek()));
         assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek());
      });
   }
}
