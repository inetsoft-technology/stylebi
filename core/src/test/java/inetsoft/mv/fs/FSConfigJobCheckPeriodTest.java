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
package inetsoft.mv.fs;

import inetsoft.sree.SreeEnv;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;

/**
 * Verifies that the sanity check interval is read live rather than frozen at first use.
 * {@code XJobPool} used to parse fs.job.check.period once in its constructor, so the first
 * organization to run a materialization job fixed the interval for the whole JVM; it now reads
 * this method on every iteration of its sanity check loop.
 */
@Tag("core")
class FSConfigJobCheckPeriodTest {
   @Test
   void defaultsWhenPropertyIsNotSet() {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("fs.job.check.period")).thenReturn(null);

         assertEquals(500, FSService.getConfig().getJobCheckPeriod());
      }
   }

   @Test
   void reflectsAChangedPropertyWithoutRestart() {
      FSConfig config = FSService.getConfig();

      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         sreeEnv.when(() -> SreeEnv.getProperty("fs.job.check.period")).thenReturn("250");
         assertEquals(250, config.getJobCheckPeriod());

         // the same config instance must pick up a later change, as the sanity check thread
         // relies on re-reading the property rather than caching it
         sreeEnv.when(() -> SreeEnv.getProperty("fs.job.check.period")).thenReturn("1000");
         assertEquals(1000, config.getJobCheckPeriod());
      }
   }
}
