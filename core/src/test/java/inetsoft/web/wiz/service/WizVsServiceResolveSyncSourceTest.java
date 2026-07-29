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
package inetsoft.web.wiz.service;

import inetsoft.uql.viewsheet.VSAssembly;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

@Tag("core")
class WizVsServiceResolveSyncSourceTest {
   @Test
   void syncModeWithExistingTargetPrefersIt() {
      VSAssembly existingTarget = mock(VSAssembly.class);
      VSAssembly prevPrimary = mock(VSAssembly.class);

      assertSame(existingTarget,
         WizVsService.resolveSyncSource(true, existingTarget, null, prevPrimary));
   }

   @Test
   void syncModeWithoutExistingTargetFallsBackToDisplaced() {
      VSAssembly replaced = mock(VSAssembly.class);
      VSAssembly prevPrimary = mock(VSAssembly.class);

      // replacedAssembly wins over previousPrimaryAssembly (mirrors displacedForCondition).
      assertSame(replaced,
         WizVsService.resolveSyncSource(true, null, replaced, prevPrimary));
      assertSame(prevPrimary,
         WizVsService.resolveSyncSource(true, null, null, prevPrimary));
   }

   @Test
   void nonSyncModeReturnsNull() {
      VSAssembly existingTarget = mock(VSAssembly.class);

      assertNull(WizVsService.resolveSyncSource(false, existingTarget, null, existingTarget));
   }
}
