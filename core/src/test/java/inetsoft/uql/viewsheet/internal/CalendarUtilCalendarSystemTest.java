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
package inetsoft.uql.viewsheet.internal;

import inetsoft.test.*;
import inetsoft.uql.viewsheet.VSCompositeFormat;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77527: calendar assembly date strings ("2024-8", "2024-01-03") hold Gregorian years and
 * must be read as Gregorian whatever the calendar system of the JVM default locale is
 * (Buddhist for th_TH, Japanese imperial for ja_JP_JP). Display formats are Gregorian too,
 * with the month and day names of the locale (Bug #77605).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class CalendarUtilCalendarSystemTest {
   private static final Locale TH = Locale.of("th", "TH");
   private static final Locale JA = Locale.of("ja", "JP", "JP");

   private Locale oldLocale;

   @BeforeEach
   void saveLocale() {
      oldLocale = Locale.getDefault();
   }

   @AfterEach
   void restoreLocale() {
      Locale.setDefault(oldLocale);
   }

   @Test
   void formatTitleUsesGregorianYear() {
      for(Locale locale : List.of(Locale.US, TH, JA)) {
         Locale.setDefault(locale);
         assertEquals("September 2024",
                      CalendarUtil.formatTitle("2024-8", false, new VSCompositeFormat()),
                      "default locale " + locale);
         assertEquals("2024", CalendarUtil.formatTitle("2024", true, new VSCompositeFormat()),
                      "default locale " + locale);
      }
   }

   // the default title in VSCalendarModel is built from the current year and month
   @Test
   void currentMonthTitleRoundTrips() {
      for(Locale locale : List.of(Locale.US, TH, JA)) {
         Locale.setDefault(locale);
         Calendar calendar = new GregorianCalendar();
         String title = CalendarUtil.formatTitle(
            calendar.get(Calendar.YEAR) + "-" + calendar.get(Calendar.MONTH), false,
            new VSCompositeFormat());
         String expected = new SimpleDateFormat("MMMM yyyy", Locale.US).format(calendar.getTime());
         assertEquals(expected, title, "default locale " + locale);
      }
   }

   // Bug #77598: a custom CALENDAR_TITLE date pattern must also show the Gregorian year
   @Test
   void customPatternTitleUsesGregorianYear() {
      VSCompositeFormat format = new VSCompositeFormat();
      format.getUserDefinedFormat().setFormatValue("DateFormat");
      format.getUserDefinedFormat().setFormatExtentValue("yyyy-MM-dd");

      for(Locale locale : List.of(Locale.US, TH, JA)) {
         Locale.setDefault(locale);
         assertEquals("2026-01", CalendarUtil.formatTitle("2026-0", false, format),
                      "default locale " + locale);
         assertEquals("2026", CalendarUtil.formatTitle("2026", true, format),
                      "default locale " + locale);
      }
   }

   @Test
   void fullFormatEnUs() {
      Locale.setDefault(Locale.US);
      String result = CalendarUtil.formatSelectedDates("2024-01-03", "FULL", false, false, true);
      assertEquals("Wednesday, January 3, 2024", result);
   }

   @Test
   void fullFormatThaiShowsGregorianYear() {
      Locale.setDefault(TH);
      String result = CalendarUtil.formatSelectedDates("2024-01-03", "FULL", false, false, true);
      assertTrue(result.contains("2024"), result);
      assertTrue(result.contains("มกราคม"), result);
      assertFalse(result.contains("2567"), result);
   }

   @Test
   void fullFormatJapaneseShowsGregorianYear() {
      Locale.setDefault(JA);
      String result = CalendarUtil.formatSelectedDates("2024-01-03", "FULL", false, false, true);
      assertTrue(result.contains("2024年1月3日"), result);
      assertFalse(result.contains("令和"), result);
   }

   @Test
   void leapDayIsNotRolledOver() {
      Locale.setDefault(Locale.US);
      assertEquals("2024-02-29",
                   CalendarUtil.formatSelectedDates("2024-02-29", "yyyy-MM-dd", false, false, true));

      for(Locale locale : List.of(TH, JA)) {
         Locale.setDefault(locale);
         String result =
            CalendarUtil.formatSelectedDates("2024-02-29", "yyyy-MM-dd", false, false, true);
         assertTrue(result.endsWith("-02-29"), locale + ": " + result);
      }
   }
}
