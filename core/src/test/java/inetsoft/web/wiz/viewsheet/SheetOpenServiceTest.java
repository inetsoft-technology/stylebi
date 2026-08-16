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
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class SheetOpenServiceTest {
   @Test
   void refusesWhenTheViewsheetHasNoBaseWorksheet() {
      SheetOpenService service = serviceWithBase(null, true, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("no base worksheet"), thrown.getMessage());
   }

   /**
    * A logical model or data source is a legitimate base and is NOT a worksheet. The message must
    * name what it actually is, or the caller retries the same call expecting a different answer.
    */
   @Test
   void refusesWhenTheBaseIsNotAWorksheetAndNamesTheActualType() {
      AssetEntry logicModel = mock(AssetEntry.class);
      when(logicModel.isWorksheet()).thenReturn(false);
      when(logicModel.getType()).thenReturn(AssetEntry.Type.LOGIC_MODEL);
      SheetOpenService service = serviceWithBase(logicModel, true, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("LOGIC_MODEL"), thrown.getMessage());
   }

   @Test
   void refusesWhenTheAgentLacksWorksheetPermission() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), false, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().toLowerCase().contains("permission"), thrown.getMessage());
   }

   /**
    * Silently replacing a held session is the defect that shipped once already: it ends a session
    * the user may be editing, with a success response.
    */
   @Test
   void refusesWhenAWorksheetSessionIsAlreadyHeldAndNamesItsRuntimeId() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, "ws-existing");

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().contains("ws-existing"), thrown.getMessage());
      assertTrue(thrown.getMessage().contains("detach_sheet"), thrown.getMessage());
   }

   /**
    * A session with no recorded socket cannot reach a browser, so the worksheet would open
    * nowhere. Refusing beats opening a runtime the user never sees and cannot close.
    */
   @Test
   void refusesWhenTheViewsheetSessionHasNoSocketSession() {
      SheetOpenService service = serviceWithBase(worksheetEntry(), true, null, null);

      IllegalArgumentException thrown = assertThrows(
         IllegalArgumentException.class, () -> service.openBaseWorksheet("tok-vs", principal()));

      assertTrue(thrown.getMessage().toLowerCase().contains("re-pair"), thrown.getMessage());
   }

   // ── harness ───────────────────────────────────────────────────────────────

   /** The four-guard helper. Defaults the viewsheet session's socketSessionId to {@code "sock-1"}. */
   private static SheetOpenService serviceWithBase(AssetEntry base, boolean hasPermission,
                                                     String heldWorksheetRuntimeId)
   {
      return serviceWithBase(base, hasPermission, heldWorksheetRuntimeId, "sock-1");
   }

   private static SheetOpenService serviceWithBase(AssetEntry base, boolean hasPermission,
                                                     String heldWorksheetRuntimeId,
                                                     String socketSessionId)
   {
      try {
         Viewsheet vs = mock(Viewsheet.class);
         when(vs.getBaseEntry()).thenReturn(base);

         RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
         when(rvs.getViewsheet()).thenReturn(vs);

         JoinSession vsSession = new JoinSession(
            "tok-vs", "vs-runtime-1", "alice", SheetType.VIEWSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED,
            socketSessionId, "alice");

         ViewsheetSessionService viewsheetSessions = mock(ViewsheetSessionService.class);
         when(viewsheetSessions.requireSession(anyString(), any(Principal.class)))
            .thenReturn(vsSession);
         when(viewsheetSessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);

         SheetSessionService sheetSessions = mock(SheetSessionService.class);
         JoinSession held = heldWorksheetRuntimeId == null ? null : new JoinSession(
            "tok-ws-held", heldWorksheetRuntimeId, "alice", SheetType.WORKSHEET, 0L,
            SheetSessionService.TTL_MILLIS, JoinSession.ConnectionMode.PAIRED, "sock-1", "alice");
         when(sheetSessions.findOpen("alice", SheetType.WORKSHEET)).thenReturn(held);

         WorksheetService worksheetService = mock(WorksheetService.class);

         SecurityProvider securityProvider = mock(SecurityProvider.class);
         when(securityProvider.checkPermission(any(Principal.class), eq(ResourceType.WORKSHEET),
                                                eq("*"), eq(ResourceAction.ACCESS)))
            .thenReturn(hasPermission);

         return new SheetOpenService(viewsheetSessions, sheetSessions, worksheetService,
                                      securityProvider);
      }
      catch(Exception e) {
         throw new IllegalStateException(e);
      }
   }

   private static AssetEntry worksheetEntry() {
      AssetEntry entry = mock(AssetEntry.class);
      when(entry.isWorksheet()).thenReturn(true);
      when(entry.getType()).thenReturn(AssetEntry.Type.WORKSHEET);
      return entry;
   }

   private static Principal principal() {
      return () -> "alice";
   }
}
