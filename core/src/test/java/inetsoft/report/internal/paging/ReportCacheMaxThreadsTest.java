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
package inetsoft.report.internal.paging;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bug #80997: an invalid reportCache.thread.count must fall back to the default instead of
 * producing a thread pool with no worker threads.
 */
@Tag("core")
class ReportCacheMaxThreadsTest {
   @Test
   void validValueIsUsed() {
      assertEquals(12, ReportCache.parseMaxThreads("12", 8));
   }

   @Test
   void surroundingWhitespaceIsIgnored() {
      assertEquals(12, ReportCache.parseMaxThreads(" 12 ", 8));
   }

   @Test
   void nonNumericFallsBackToDefault() {
      assertEquals(8, ReportCache.parseMaxThreads("abc", 8));
   }

   @Test
   void emptyAndBlankFallBackToDefault() {
      assertEquals(8, ReportCache.parseMaxThreads("", 8));
      assertEquals(8, ReportCache.parseMaxThreads("   ", 8));
   }

   @Test
   void zeroAndNegativeFallBackToDefault() {
      assertEquals(8, ReportCache.parseMaxThreads("0", 8));
      assertEquals(8, ReportCache.parseMaxThreads("-3", 8));
   }
}
