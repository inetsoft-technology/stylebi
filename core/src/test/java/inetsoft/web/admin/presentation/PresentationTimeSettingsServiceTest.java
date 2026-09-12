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
package inetsoft.web.admin.presentation;

/*
 * Test strategy
 *
 * getModel() reports "schedule.time.12hours" to the Time card with Boolean.parseBoolean, which
 * is case-insensitive. Its consumers -- Tool.getTimeFormat(), TimeCondition.toString(),
 * ScheduleTaskService, ScheduleCycleService, ScheduleDialogService -- resolved it through
 * SreeEnv.getBooleanProperty() with no true-value list, i.e. "true".equals only. A stored TRUE
 * showed the box ticked while every consumer used the 24-hour clock; those consumers now use
 * the same Boolean.parseBoolean rule.
 *
 * This pins the display side so the two cannot drift apart again. The property is not one the
 * Time card writes in mixed case -- it reaches that state via INETSOFTENV_SCHEDULE_TIME_12HOURS
 * at first storage initialisation or a direct property-store edit.
 *
 * Behavioral guarantees covered:
 *
 * [G1] The displayed state is true for "true" in any case.
 * [G2] It is false for "false", an unrecognized value, and null.
 * [G3] week.start passes through untouched.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.presentation.model.PresentationTimeSettingsModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class PresentationTimeSettingsServiceTest {
   private PresentationTimeSettingsService service;
   private MockedStatic<SreeEnv> sreeEnvStatic;

   @BeforeEach
   void setUp() {
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().lenient());
      service = new PresentationTimeSettingsService();
   }

   @AfterEach
   void tearDown() {
      sreeEnvStatic.close();
   }

   private PresentationTimeSettingsModel model(String twelveHours) {
      // getModel(true) reads through getProperty(name, false, !globalProperty)
      sreeEnvStatic.when(() -> SreeEnv.getProperty("schedule.time.12hours", false, false))
         .thenReturn(twelveHours);
      return service.getModel(true);
   }

   @Test
   void storedTrueIsDisplayedAsTicked() {
      for(String value : new String[]{ "true", "TRUE", "True", "tRuE" }) {
         assertTrue(model(value).scheduleTime12Hours(), "schedule.time.12hours=" + value);
      }
   }

   @Test
   void storedNonTrueIsDisplayedAsUnticked() {
      for(String value : new String[]{ "false", "FALSE", "CHECKED", "yes", "1", "", null }) {
         assertFalse(model(value).scheduleTime12Hours(), "schedule.time.12hours=" + value);
      }
   }

   @Test
   void weekStartPassesThrough() {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("week.start", false, false)).thenReturn("2");
      assertEquals("2", model("false").weekStart());
   }
}
