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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.composer.vs.dialog.HighlightDialogService;
import inetsoft.web.wiz.viewsheet.AssemblyHighlightService.Highlight;
import inetsoft.web.wiz.viewsheet.AssemblyHighlightService.Region;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #79554: list/set/delete highlight on an assembly that does not exist reached
 * HighlightDialogService and threw an NPE; they must be refused by name instead, and a refused
 * set/delete must not spend an undo checkpoint (sessions.mutate).
 */
@Tag("core")
class AssemblyHighlightServiceUnknownAssembly79554Test {
   private final ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
   private final HighlightDialogService dialog = mock(HighlightDialogService.class);
   private final AssemblyHighlightService service = new AssemblyHighlightService(sessions, dialog);

   AssemblyHighlightServiceUnknownAssembly79554Test() throws Exception {
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(mock(Viewsheet.class));
      when(rvs.getID()).thenReturn("rt1");
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      doAnswer(inv -> {
         ViewsheetSessionService.Mutation m = inv.getArgument(2);
         m.run(rvs, "rt1", null);
         return null;
      }).when(sessions).mutate(anyString(), any(Principal.class), any());
   }

   private static final Region[] REGIONS = {
      null, Region.whole(), new Region(1, 1, null, false, false, true),
      new Region(1, 1, "Revenue", false, false, true), new Region(0, 0, "Revenue", true, false),
      new Region(0, 0, "Revenue", false, true)
   };

   private static void assertRefused(IllegalArgumentException e) {
      assertTrue(e.getMessage().contains("Unknown assembly"), e.getMessage());
      assertTrue(e.getMessage().contains("NoSuchAsm"), e.getMessage());
   }

   private static Principal user() {
      return () -> "admin";
   }

   @Test
   void listRefusesUnknownAssembly() throws Exception {
      for(Region region : REGIONS) {
         assertRefused(assertThrows(IllegalArgumentException.class,
            () -> service.list("tok", user(), "NoSuchAsm", region)));
      }
   }

   @Test
   void deleteRefusesUnknownAssemblyWithoutMutating() throws Exception {
      for(Region region : REGIONS) {
         assertRefused(assertThrows(IllegalArgumentException.class,
            () -> service.delete("tok", user(), "NoSuchAsm", region, "h1", null)));
      }

      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }

   @Test
   void setRefusesUnknownAssemblyWithoutMutating() throws Exception {
      Highlight highlight = new Highlight("h1", "#FF0000", null, null, List.of(), false);

      for(Region region : REGIONS) {
         assertRefused(assertThrows(IllegalArgumentException.class,
            () -> service.set("tok", user(), "NoSuchAsm", region, highlight, false, null)));
      }

      verify(sessions, never()).mutate(anyString(), any(Principal.class), any());
   }
}
