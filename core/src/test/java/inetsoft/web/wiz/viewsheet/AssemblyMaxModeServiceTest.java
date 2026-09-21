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
package inetsoft.web.wiz.viewsheet;

import inetsoft.web.viewsheet.controller.chart.VSChartMaxModeService;
import inetsoft.web.viewsheet.controller.table.VSTableMaxModeService;
import inetsoft.web.viewsheet.service.MaxModeAssemblyService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@code width}/{@code height} reach this service directly, not only through the plugin's own
 * tool -- a non-positive value must be refused loud here too, rather than silently building a
 * degenerate {@link java.awt.Dimension} that "maximizes" an assembly to zero (or negative) size.
 */
@Tag("core")
class AssemblyMaxModeServiceTest {
   @Test
   void refusesAZeroWidth() {
      AssemblyMaxModeService service = harness();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), "TableView1", true, 0, 600, ""));
      assertTrue(ex.getMessage().contains("'width' must be a positive number, got 0"));
      verifyNoInteractions(sessions);
   }

   @Test
   void refusesANegativeHeight() {
      AssemblyMaxModeService service = harness();

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), "TableView1", true, 800, -1, ""));
      assertTrue(ex.getMessage().contains("'height' must be a positive number, got -1"));
      verifyNoInteractions(sessions);
   }

   /** A null override (the "use the default" case) is not a validation failure. */
   @Test
   void allowsANullWidthAndHeight() throws Exception {
      AssemblyMaxModeService service = harness();

      assertThrows(IllegalArgumentException.class,
         () -> service.setMaxMode("tok", mock(Principal.class), "", true, null, null, ""));
      // Reaches the (unrelated) 'assembly' guard rather than a width/height one -- confirms null
      // does not itself trip the new check.
   }

   private AssemblyMaxModeService harness() {
      sessions = mock(ViewsheetSessionService.class);
      return new AssemblyMaxModeService(sessions, mock(VSTableMaxModeService.class),
                                        mock(VSChartMaxModeService.class),
                                        mock(MaxModeAssemblyService.class));
   }

   private ViewsheetSessionService sessions;
}
