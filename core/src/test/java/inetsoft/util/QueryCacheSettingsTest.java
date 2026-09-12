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

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("core")
class QueryCacheSettingsTest {
   @Test
   void appliesNewLimitToRegisteredCache() {
      DataCache<?, ?> cache = registerMock();

      QueryCacheSettings.applyLimit("250");

      verify(cache).setLimit(250);
   }

   @Test
   void appliesNewTimeoutToRegisteredCache() {
      DataCache<?, ?> cache = registerMock();

      QueryCacheSettings.applyTimeout("900000");

      verify(cache).setTimeout(900000L);
   }

   @Test
   void removedLimitFallsBackToDefault() {
      DataCache<?, ?> cache = registerMock();

      QueryCacheSettings.applyLimit(null);

      verify(cache).setLimit(QueryCacheSettings.DEFAULT_LIMIT);
   }

   @Test
   void removedTimeoutFallsBackToDefault() {
      DataCache<?, ?> cache = registerMock();

      QueryCacheSettings.applyTimeout(null);

      verify(cache).setTimeout(QueryCacheSettings.DEFAULT_TIMEOUT);
   }

   @Test
   void appliesToEveryRegisteredCache() {
      DataCache<?, ?> cache1 = registerMock();
      DataCache<?, ?> cache2 = registerMock();

      QueryCacheSettings.applyLimit("42");

      verify(cache1).setLimit(42);
      verify(cache2).setLimit(42);
   }

   @Test
   void limitAboveIntRangeIsBounded() {
      // the caches take an int, so a value larger than that stored in the property must
      // not fail the parse the way Integer.parseInt() did
      assertEquals(Integer.MAX_VALUE, QueryCacheSettings.parseLimit("9999999999"));
   }

   @Test
   void negativeLimitIsBounded() {
      assertEquals(0, QueryCacheSettings.parseLimit("-5"));
   }

   @Test
   void negativeTimeoutIsBounded() {
      assertEquals(0L, QueryCacheSettings.parseTimeout("-5"));
   }

   @Test
   void unparsableLimitFallsBackToDefault() {
      assertEquals(QueryCacheSettings.DEFAULT_LIMIT, QueryCacheSettings.parseLimit("abc"));
   }

   @Test
   void unparsableTimeoutFallsBackToDefault() {
      assertEquals(QueryCacheSettings.DEFAULT_TIMEOUT, QueryCacheSettings.parseTimeout("abc"));
   }

   @Test
   void surroundingWhitespaceIsIgnored() {
      assertEquals(250, QueryCacheSettings.parseLimit(" 250 "));
      assertEquals(250L, QueryCacheSettings.parseTimeout(" 250 "));
   }

   /**
    * Register a mock rather than a real cache, because the DataCache constructor
    * registers with the sweeper, which needs the application context.
    */
   private DataCache<?, ?> registerMock() {
      DataCache<?, ?> cache = mock(DataCache.class);
      QueryCacheSettings.add(cache);
      return cache;
   }
}
