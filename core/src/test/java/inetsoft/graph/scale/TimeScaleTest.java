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
package inetsoft.graph.scale;

import inetsoft.test.*;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TimeScaleTest {

   // Jan 1 – Mar 31, 2024 (91 calendar days, range triggers maxTicks guard for DAY type)
   private static final long JAN_1_2024 =
      new GregorianCalendar(2024, Calendar.JANUARY, 1).getTimeInMillis();
   private static final long MAR_31_2024 =
      new GregorianCalendar(2024, Calendar.MARCH, 31).getTimeInMillis();

   @Test
   void allTicksTruePreservesDailyTickDensityForLargeRange() {
      // With allTicks=false the maxTicks guard increments n for a 90+ day range
      // (maxTicks=50 for DAY), yielding ~45 ticks. With allTicks=true the guard
      // must be skipped so every day gets a tick.
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      scale.getAxisSpec().setAllTicks(true);
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length >= 89,
         "allTicks=true should produce one tick per day for a 91-day range, got: " + ticks.length);
   }

   @Test
   void allTicksFalseAppliesMaxTicksGuardForLargeRange() {
      // The default (allTicks=false) maxTicks guard should still cap daily ticks for
      // large ranges to prevent overly dense axes.
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length <= 55,
         "allTicks=false should apply maxTicks guard (~50) for a 91-day range, got: " + ticks.length);
   }

   @Test
   void allTicksTrueBypassesLabelDeduplicationWhenFormatCollapsesToMonth() {
      // Verifies the line 521 fix: allTicks=true must force hasDup=false even when
      // isFormattedToHigherInterval() returns true. Without the fix, only ~3 month-
      // boundary ticks would be produced instead of one per day.
      // isFormattedToHigherInterval() compares today vs. today+1 using the format;
      // skip on the last day of a month where they cross a month boundary.
      Assumptions.assumeTrue(LocalDate.now().getDayOfMonth() < LocalDate.now().lengthOfMonth(),
         "Skipped: month boundary would make isFormattedToHigherInterval return false");
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      scale.getAxisSpec().setAllTicks(true);
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("MMM"));
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length >= 89 && ticks.length <= 93,
         "allTicks=true with MMM format should produce daily ticks for a 91-day range, got: " + ticks.length);
   }

   @Test
   void allTicksFalseWithFormatAppliesLabelDeduplication() {
      // Companion: without allTicks=true the MMM format triggers hasDup=true and
      // deduplication keeps only the first tick per distinct month label (~3 ticks).
      Assumptions.assumeTrue(LocalDate.now().getDayOfMonth() < LocalDate.now().lengthOfMonth(),
         "Skipped: month boundary would make isFormattedToHigherInterval return false");
      TimeScale scale = new TimeScale(
         new java.util.Date(JAN_1_2024), new java.util.Date(MAR_31_2024));
      scale.getAxisSpec().getTextSpec().setFormat(new SimpleDateFormat("MMM"));
      double[] ticks = scale.getTicks();
      assertTrue(ticks.length <= 3,
         "allTicks=false with MMM format should deduplicate to 3 month ticks, got: " + ticks.length);
   }
}
