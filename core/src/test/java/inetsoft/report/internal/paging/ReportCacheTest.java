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
package inetsoft.report.internal.paging;

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

@Tag("core")
class ReportCacheTest {
   @Test
   void calculateMaxThreadsFallsBackToDefaultOnMalformedValue() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("replet.cache.concurrency")).thenReturn(null);
         sreeEnv.when(() -> SreeEnv.getProperty(eq("reportCache.thread.count"), anyString()))
            .thenReturn("not-a-number");

         int scount = ReportCache.calculateMaxActiveThreads();
         int hcount = ReportCache.calculateMaxThreads();

         assertEquals(scount * 2, hcount);
      }
   }

   @Test
   void calculateMaxThreadsUsesValidConfiguredValue() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("replet.cache.concurrency")).thenReturn(null);
         sreeEnv.when(() -> SreeEnv.getProperty(eq("reportCache.thread.count"), anyString()))
            .thenReturn("7");

         int hcount = ReportCache.calculateMaxThreads();

         assertEquals(7, hcount);
      }
   }
}
