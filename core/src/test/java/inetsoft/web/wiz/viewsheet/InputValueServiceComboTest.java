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
package inetsoft.web.wiz.viewsheet;

import inetsoft.uql.viewsheet.internal.ComboBoxVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug 78590 P3: an editable or calendar combo accepts values outside its list, but a value its
 * dataType cannot represent was coerced (saturated, cleared, rolled over) and reported ok.
 */
@Tag("core")
class InputValueServiceComboTest {
   private static ComboBoxVSAssemblyInfo combo(boolean editable, boolean calendar) {
      ComboBoxVSAssemblyInfo info = mock(ComboBoxVSAssemblyInfo.class);
      when(info.isTextEditable()).thenReturn(editable);
      when(info.isCalendar()).thenReturn(calendar);
      return info;
   }

   private static void check(ComboBoxVSAssemblyInfo info, String dataType, Object... values) {
      InputValueService.checkValue(info, dataType, Arrays.asList(values), false, "Combo1",
                                   "a combo box");
   }

   private static Exception refuses(ComboBoxVSAssemblyInfo info, String dataType, Object value) {
      Exception e = assertThrows(IllegalArgumentException.class,
                                 () -> check(info, dataType, value), String.valueOf(value));
      assertTrue(e.getMessage().contains("Combo1"), e.getMessage());
      assertTrue(e.getMessage().contains(String.valueOf(value)), e.getMessage());
      return e;
   }

   @Test
   void refusesImpossibleIntegerOnEditableCombo() {
      ComboBoxVSAssemblyInfo info = combo(true, false);
      refuses(info, "integer", "3000000000");
      refuses(info, "integer", 3000000000L);
      refuses(info, "integer", "abc");
   }

   @Test
   void refusesImpossibleDateOnCalendarComboAndNamesTheFormat() {
      Exception e = refuses(combo(false, true), "date", "2026-02-30");
      assertTrue(e.getMessage().contains("yyyy-MM-dd"), e.getMessage());
   }

   @Test
   void refusesNonIsoDateAndEpochOnCalendarCombo() {
      ComboBoxVSAssemblyInfo info = combo(false, true);
      refuses(info, "date", "03/01/2026");
      refuses(info, "date", "1772323200000");
   }

   @Test
   void acceptsValidValues() {
      assertDoesNotThrow(() -> check(combo(true, false), "integer", "42"));
      assertDoesNotThrow(() -> check(combo(true, false), "integer", 42));
      assertDoesNotThrow(() -> check(combo(false, true), "date", "2026-03-01"));
      assertDoesNotThrow(() -> check(combo(false, true), "date", "2026-03-01 00:00:00"));
   }

   private static ComboBoxVSAssemblyInfo boundedCalendar(String min, String max) {
      ComboBoxVSAssemblyInfo info = combo(false, true);
      when(info.getMinDate()).thenReturn(min == null ? null : Timestamp.valueOf(min));
      when(info.getMaxDate()).thenReturn(max == null ? null : Timestamp.valueOf(max));
      return info;
   }

   /** Bug 79697: a date outside the calendar's minDate/maxDate was stored with ok:true. */
   @Test
   void refusesDateOutsideCalendarBoundsAndNamesTheLimits() {
      ComboBoxVSAssemblyInfo info = boundedCalendar("2024-01-01 00:00:00", "2024-12-31 00:00:00");
      Exception after = refuses(info, "date", "2025-03-01");
      assertTrue(after.getMessage().contains("2024-01-01"), after.getMessage());
      assertTrue(after.getMessage().contains("2024-12-31"), after.getMessage());
      refuses(info, "date", "2023-12-31");
      refuses(info, "date", "2025-03-01 00:00:00");
      refuses(info, "timeInstant", "2025-03-01 10:00:00");
   }

   @Test
   void acceptsDateInsideAndOnCalendarBounds() {
      ComboBoxVSAssemblyInfo info = boundedCalendar("2024-01-01 00:00:00", "2024-12-31 00:00:00");
      assertDoesNotThrow(() -> check(info, "date", "2024-06-15"));
      assertDoesNotThrow(() -> check(info, "date", "2024-01-01"));
      assertDoesNotThrow(() -> check(info, "date", "2024-12-31"));
      // the picker works in whole days, so a time on the max day is still offered
      assertDoesNotThrow(() -> check(info, "timeInstant", "2024-12-31 18:30:00"));
      assertDoesNotThrow(() -> check(info, "date", ""));
   }

   @Test
   void enforcesOnlyTheBoundThatIsSet() {
      assertDoesNotThrow(() -> check(boundedCalendar("2024-01-01 00:00:00", null), "date",
                                     "2090-01-01"));
      refuses(boundedCalendar("2024-01-01 00:00:00", null), "date", "2023-01-01");
      assertDoesNotThrow(() -> check(boundedCalendar(null, "2024-12-31 00:00:00"), "date",
                                     "1990-01-01"));
      refuses(boundedCalendar(null, "2024-12-31 00:00:00"), "date", "2025-01-01");
   }

   @Test
   void acceptsOutOfListValueOnEditableCombo() {
      ComboBoxVSAssemblyInfo info = combo(true, false);
      when(info.getValues()).thenReturn(new Object[] { 1, 2, 3 });
      assertDoesNotThrow(() -> check(info, "integer", "99"));
   }

   @Test
   void blankAndEmptyClearTheCombo() {
      assertDoesNotThrow(() -> check(combo(true, false), "integer", ""));
      assertDoesNotThrow(() -> check(combo(true, false), "integer", "  "));
      assertDoesNotThrow(() -> check(combo(true, false), "integer", (Object) null));
      assertDoesNotThrow(() -> InputValueService.checkValue(
         combo(false, true), "date", Collections.emptyList(), false, "Combo1", "a combo box"));
   }
}
