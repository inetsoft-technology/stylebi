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
package inetsoft.web.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.sql.Time;
import java.sql.Timestamp;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77566: {@code ThirdPartySupportModule}'s Jackson serializers for {@code Date}/
 * {@code Timestamp}/{@code Time} used a locale-less {@code SimpleDateFormat}, which picks up the
 * JVM default locale's calendar (Buddhist for th_TH, Japanese imperial for ja_JP_JP) instead of
 * Gregorian. There is no matching custom deserializer registered anywhere for these types (only
 * {@code addSerializer} calls, confirmed by inspection), so nothing in this codebase round-trips
 * through this exact format -- forcing Gregorian here is purely a serialized-value fix, with no
 * persistence/compat risk, unlike the canonical {@code CoreTool}/{@code ExtendedDateFormat} format
 * caches.
 */
@Tag("core")
class ThirdPartySupportModuleCalendarSystemTest {
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
   void dateSerializerAlwaysUsesGregorianYear(Locale locale) throws Exception {
      Locale.setDefault(locale);

      // construct fresh under this default locale, same as what happens when the module is
      // registered with an ObjectMapper during this JVM's lifetime
      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new ThirdPartySupportModule());

      Date date = new GregorianCalendar(1994, Calendar.JUNE, 15, 0, 0, 0).getTime();
      String json = mapper.writeValueAsString(date);

      assertEquals("\"1994-06-15 00:00:00\"", json, "default locale " + locale);
   }

   @ParameterizedTest
   @MethodSource("locales")
   void timestampSerializerAlwaysUsesGregorianYear(Locale locale) throws Exception {
      Locale.setDefault(locale);

      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new ThirdPartySupportModule());

      Timestamp timestamp = new Timestamp(
         new GregorianCalendar(2024, Calendar.JANUARY, 3, 10, 30, 0).getTimeInMillis());
      String json = mapper.writeValueAsString(timestamp);

      assertEquals("\"2024-01-03 10:30:00\"", json, "default locale " + locale);
   }

   @ParameterizedTest
   @MethodSource("locales")
   void timeSerializerIsUnaffectedByCalendarSystem(Locale locale) throws Exception {
      Locale.setDefault(locale);

      ObjectMapper mapper = new ObjectMapper();
      mapper.registerModule(new ThirdPartySupportModule());

      Time time = new Time(
         new GregorianCalendar(1970, Calendar.JANUARY, 1, 13, 45, 30).getTimeInMillis());
      String json = mapper.writeValueAsString(time);

      assertEquals("\"13:45:30\"", json, "default locale " + locale);
   }

   private Locale oldLocale;
}
