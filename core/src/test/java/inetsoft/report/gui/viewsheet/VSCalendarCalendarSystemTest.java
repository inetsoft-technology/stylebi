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
package inetsoft.report.gui.viewsheet;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.CalendarVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.internal.CalendarVSAssemblyInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77527: the exported calendar fills a one-sided selection range to the end of the
 * Gregorian month of the selection value, whatever the calendar system of the JVM default
 * locale is (Buddhist for th_TH, Japanese imperial for ja_JP_JP).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class VSCalendarCalendarSystemTest {
   static Stream<Locale> locales() {
      return Stream.of(Locale.US, Locale.of("th", "TH"), Locale.of("ja", "JP", "JP"));
   }

   @BeforeEach
   void saveLocale() {
      oldLocale = Locale.getDefault();
   }

   @AfterEach
   void restoreLocale() {
      Locale.setDefault(oldLocale);
   }

   // February 2024 has 29 days, the range runs from the selected day to the end of the month
   @ParameterizedTest
   @MethodSource("locales")
   void daySelectionRangeEndsOnLastDayOfGregorianMonth(Locale locale) throws Exception {
      Locale.setDefault(locale);
      String[] result = getSelectedRangeDate(true, "d2024-1-10", "d2024-2-5");

      assertEquals(20, result.length, Arrays.toString(result));
      assertEquals("d2024-1-10", result[0]);
      assertEquals("d2024-1-29", result[result.length - 1]);
   }

   // February 2024 spans 5 weeks when the week starts on Sunday
   @ParameterizedTest
   @MethodSource("locales")
   void weekSelectionRangeEndsOnLastWeekOfGregorianMonth(Locale locale) throws Exception {
      Locale.setDefault(locale);
      String[] result = getSelectedRangeDate(false, "w2024-1-2", "w2024-2-1");

      assertArrayEquals(new String[] { "w2024-1-2", "w2024-1-3", "w2024-1-4", "w2024-1-5" },
                        result);
   }

   private static String[] getSelectedRangeDate(boolean daySelection, String value,
                                                String anotherValue)
      throws Exception
   {
      Viewsheet vs = new Viewsheet();
      CalendarVSAssembly assembly = new CalendarVSAssembly();
      CalendarVSAssemblyInfo info = (CalendarVSAssemblyInfo) assembly.getVSAssemblyInfo();
      info.setDaySelectionValue(daySelection);
      info.setCurrentDate1("2024-1");
      info.setCurrentDate2("2024-2");

      VSCalendar calendar = new VSCalendar(vs);
      calendar.setAssemblyInfo(info);
      Method method = VSCalendar.class.getDeclaredMethod(
         "getSelectedRangeDate", String[].class, String[].class, boolean.class);
      method.setAccessible(true);
      return (String[]) method.invoke(
         calendar, new String[] { value }, new String[] { anotherValue }, false);
   }

   private Locale oldLocale;
}
