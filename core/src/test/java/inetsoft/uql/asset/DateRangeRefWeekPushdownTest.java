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
import inetsoft.uql.erm.AttributeRef;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Calendar;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Week grouping is either pushed down to SQL (which computes Sunday-based weeks) or done in
 * memory from a script. The choice must agree with the first day of week actually in effect,
 * or the database and the engine bucket the same rows differently.
 *
 * <p>The gate used to also require {@code Tool.getWeekStart() != null}, i.e. an explicitly
 * configured property. With a blank {@code week.start} and a Monday locale the first day of
 * week was Monday but pushdown still happened -- the SQL/Java split this pins shut.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DateRangeRefWeekPushdownTest {
   @Test
   void weekPushdownSuppressedForNonSundayWeekStart() {
      WeekStartUtil.withWeekStart("monday", () -> {
         assertEquals(Calendar.MONDAY, Tool.getFirstDayOfWeek());
         assertEquals("", weekExpression(),
                      "a non-Sunday week start must fall back to in-memory grouping");
      });
   }

   @Test
   void weekPushdownUsedForSundayWeekStart() {
      WeekStartUtil.withWeekStart("sunday", () ->
         assertNotEquals("", weekExpression(),
                         "a Sunday week start can be computed by the database"));
   }

   /**
    * The invariant behind both cases: pushdown happens only when the effective week start is
    * Sunday. This failed with a blank {@code week.start} under a Monday locale.
    */
   @Test
   void pushdownAgreesWithEffectiveWeekStartUnderAnyLocale() {
      Locale old = ThreadContext.getLocale();

      try {
         for(String tag : new String[]{ "en-US", "en-GB", "zh-CN", "de-DE" }) {
            ThreadContext.setLocale(Locale.forLanguageTag(tag));

            for(String weekStart : new String[]{ "", "sunday", "monday", "saturday" }) {
               WeekStartUtil.withWeekStart(weekStart, () ->
                  assertTrue(weekExpression().isEmpty() ||
                                Tool.getFirstDayOfWeek() == Calendar.SUNDAY,
                             "week grouping pushed down to SQL while the engine starts " +
                                "weeks on day " + Tool.getFirstDayOfWeek() +
                                " (locale " + tag + ", week.start \"" + weekStart + "\")"));
            }
         }
      }
      finally {
         ThreadContext.setLocale(old);
      }
   }

   private static String weekExpression() {
      return DateRangeRef.getExpression("mysql", new AttributeRef("orderDate"),
                                        DateRangeRef.WEEK_INTERVAL);
   }
}
