/*
 * This file is part of StyleBI.
 * Copyright (C) 2026 InetSoft Technology
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
package inetsoft.web.viewsheet.handler;

import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.internal.CrosstabVSAssemblyInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * A peer whose Enable Drilling is off must be left out of a Drill Down Filter's drill pass, so it
 * keeps its level (Bug #81237).
 */
@Tag("core")
class VSDrillHandlerDrillEnabledTest {
   @Test
   void drillDisabledPeerIsNotDrilled() {
      assertFalse(VSDrillHandler.isDrillEnabled(crosstab(false)));
   }

   @Test
   void drillEnabledPeerIsDrilled() {
      assertTrue(VSDrillHandler.isDrillEnabled(crosstab(true)));
   }

   private CrosstabVSAssembly crosstab(boolean drill) {
      CrosstabVSAssemblyInfo info = mock(CrosstabVSAssemblyInfo.class);
      when(info.isDrillEnabled()).thenReturn(drill);
      CrosstabVSAssembly assembly = mock(CrosstabVSAssembly.class);
      when(assembly.getVSAssemblyInfo()).thenReturn(info);
      return assembly;
   }
}
