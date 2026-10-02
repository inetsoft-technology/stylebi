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
package inetsoft.uql.table;

import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77566: {@code XTimestampColumn.addObject} parses an external tabular/connector timestamp
 * string that is always Gregorian ISO-8601 digits (fix for Bug #36081's "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
 * format, and the "dd-MM-yyyy" fallback for Bug #47216) with a locale-less {@code SimpleDateFormat},
 * which picked up the JVM default locale's calendar (Buddhist for th_TH, Japanese imperial for
 * ja_JP_JP) instead of Gregorian, misinterpreting the Gregorian year digits on ingestion. There is
 * no legacy non-Gregorian variant of this wire format to preserve, so forcing Gregorian here only
 * fixes ingestion, with no persistence/compat risk.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class XTimestampColumnCalendarSystemTest {
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

   @ParameterizedTest
   @MethodSource("locales")
   void isoZuluStringIngestsAsGregorianYear(Locale locale) {
      Locale.setDefault(locale);

      XTimestampColumn column = new XTimestampColumn((char) 10, (char) 20);
      column.addObject("1994-06-15T00:00:00.000Z");
      Timestamp result = (Timestamp) column.getObject(0);

      assertNotNull(result, "default locale " + locale);
      assertGregorianDate(1994, Calendar.JUNE, 15, result);
   }

   @ParameterizedTest
   @MethodSource("locales")
   void ddMmYyyyFallbackIngestsAsGregorianYear(Locale locale) {
      Locale.setDefault(locale);

      // not parseable by the ISO 'Z' branch, and not something pojava's DateTime.parse accepts,
      // so it falls through to the "dd-MM-yyyy" fallback (Bug #47216)
      XTimestampColumn column = new XTimestampColumn((char) 10, (char) 20);
      column.addObject("15-06-1994");
      Timestamp result = (Timestamp) column.getObject(0);

      assertNotNull(result, "default locale " + locale);
      assertGregorianDate(1994, Calendar.JUNE, 15, result);
   }

   private static void assertGregorianDate(int year, int month, int day, Date actual) {
      Calendar cal = new GregorianCalendar();
      cal.setTime(actual);
      assertEquals(year, cal.get(Calendar.YEAR), "year of " + actual);
      assertEquals(month, cal.get(Calendar.MONTH), "month of " + actual);
      assertEquals(day, cal.get(Calendar.DATE), "day of " + actual);
   }

   private Locale oldLocale;
}
