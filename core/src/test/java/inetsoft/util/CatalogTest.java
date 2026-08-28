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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins Catalog.parseLocale's existing contract, including the fact that it throws for
 * shapes other than a bare 2-char code or exactly language_COUNTRY (underscore at
 * index 2). Callers such as DataSourceBrowserService.getLocale must handle this throw
 * themselves -- parseLocale's throwing behavior is intentionally left unchanged.
 */
@Tag("core")
class CatalogTest {
   @Test
   void parseLocale_null_returnsNull() {
      assertNull(Catalog.parseLocale(null));
   }

   @Test
   void parseLocale_empty_returnsNull() {
      assertNull(Catalog.parseLocale(""));
   }

   @Test
   void parseLocale_languageOnly_parses() {
      assertEquals(Locale.of("en", ""), Catalog.parseLocale("en"));
   }

   @Test
   void parseLocale_languageAndCountry_parses() {
      assertEquals(Locale.of("en", "US"), Catalog.parseLocale("en_US"));
   }

   @Test
   void parseLocale_hyphenatedForm_throws() {
      assertThrows(RuntimeException.class, () -> Catalog.parseLocale("en-US"));
   }
}
