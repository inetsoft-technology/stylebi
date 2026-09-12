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
package inetsoft.util;

/*
 * Test strategy
 *
 * Tool.getTimeFormat(locale, true) is the enforcement side of "schedule.time.12hours": when the
 * property is on it rewrites "HH" to "hh" in the time pattern. The Time card displays the same
 * property with Boolean.parseBoolean (case-insensitive) while this consumer resolved it through
 * SreeEnv.getBooleanProperty() with no true-value list ("true".equals only), so a stored TRUE
 * showed the box ticked while the clock stayed on 24 hours.
 *
 * This is the regression test for that split -- it fails on the old case-sensitive read.
 * TimeCondition, ScheduleTaskService, ScheduleCycleService and ScheduleDialogService read the
 * same property the same way and were changed together.
 *
 * Behavioral guarantees covered:
 *
 * [G1] A stored "TRUE"/"True" selects the 12-hour pattern, as "true" already did.
 * [G2] A non-true value leaves the 24-hour pattern.
 *
 * Not covered: the schedule=false path. getTimeFormat() applies the 12-hour rewrite regardless
 * of the flag but omits it from the cache key, so that result depends on call order -- a
 * pre-existing quirk, out of scope here.
 */

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class ToolTimeFormatTest {
   private static final String PATTERN_24 = "HH:mm:ss";
   private static final String PATTERN_12 = "hh:mm:ss";

   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      sreeEnvStatic.when(() -> SreeEnv.getProperty("format.time")).thenReturn(PATTERN_24);
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private String pattern(String twelveHours, boolean schedule) {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("schedule.time.12hours")).thenReturn(twelveHours);
      return Tool.getTimeFormat(Locale.US, schedule).toPattern();
   }

   @Test
   void storedTrueSelectsTwelveHourPatternRegardlessOfCase() {
      for(String value : new String[]{ "true", "TRUE", "True" }) {
         assertEquals(PATTERN_12, pattern(value, true), "schedule.time.12hours=" + value);
      }
   }

   @Test
   void storedNonTrueKeepsTwentyFourHourPattern() {
      for(String value : new String[]{ "false", "FALSE", "CHECKED", "yes", "1", "", null }) {
         assertEquals(PATTERN_24, pattern(value, true), "schedule.time.12hours=" + value);
      }
   }
}
