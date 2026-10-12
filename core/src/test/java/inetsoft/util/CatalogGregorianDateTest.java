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
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #78226: a catalog message with a {n,date,...} subformat must print Gregorian years even when
 * the JVM default format locale uses another calendar (th_TH Buddhist, ja_JP_JP Japanese).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class CatalogGregorianDateTest {
   private static final String KEY = "date.comparison.standardPeriod.desc";

   private Locale savedFormatLocale;

   @BeforeEach
   void saveLocale() {
      savedFormatLocale = Locale.getDefault(Locale.Category.FORMAT);
   }

   @AfterEach
   void restoreLocale() {
      Locale.setDefault(Locale.Category.FORMAT, savedFormatLocale);
   }

   @Test
   void thaiBuddhistDefaultStillPrintsGregorianYears() {
      Locale.setDefault(Locale.Category.FORMAT, new Locale("th", "TH"));
      assertEquals("Last 1 years starting from 2025-01-01 to 2026-03-31", format());
   }

   @Test
   void japaneseImperialDefaultStillPrintsGregorianYears() {
      Locale.setDefault(Locale.Category.FORMAT, new Locale("ja", "JP", "JP"));
      assertEquals("Last 1 years starting from 2025-01-01 to 2026-03-31", format());
   }

   @Test
   void gregorianDefaultIsUnchanged() {
      Locale.setDefault(Locale.Category.FORMAT, Locale.US);
      assertEquals("Last 1 years starting from 2025-01-01 to 2026-03-31", format());
   }

   private String format() {
      Date start = new GregorianCalendar(2025, 0, 1).getTime();
      Date end = new GregorianCalendar(2026, 2, 31).getTime();
      return Catalog.getCatalog().getString(KEY, 1, "year", start, end);
   }
}
