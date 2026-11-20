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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetService;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.web.wiz.pairing.JoinSession;
import inetsoft.web.wiz.pairing.PairingException;
import inetsoft.web.wiz.pairing.SheetAgentBroadcastService;
import inetsoft.web.wiz.pairing.SheetRuntimeAccess;
import inetsoft.web.wiz.pairing.SheetSessionService;
import inetsoft.web.wiz.pairing.SheetType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * A5: a viewer session cannot open or create any other sheet. All four openers refuse with
 * {@code VIEWER_MODE} before a runtime is opened, a session is minted or the browser is told
 * anything; a design session is unaffected (it reaches the guards that were already there).
 */
@Tag("core")
class SheetOpenServiceViewerModeTest {
   private static final String OWNER = "alice~;~host-org";

   private SheetSessionService sheetSessions;
   private ViewsheetSessionService viewsheetSessions;
   private WorksheetService worksheetService;
   private ViewsheetService viewsheetService;
   private SheetRuntimeAccess runtimeAccess;
   private SheetAgentBroadcastService broadcast;
   private SheetOpenService service;
   private final Principal agent = () -> OWNER;

   @BeforeEach
   void setUp() {
      sheetSessions = mock(SheetSessionService.class);
      viewsheetSessions = mock(ViewsheetSessionService.class);
      worksheetService = mock(WorksheetService.class);
      viewsheetService = mock(ViewsheetService.class);
      runtimeAccess = mock(SheetRuntimeAccess.class);
      broadcast = mock(SheetAgentBroadcastService.class);
      service = new SheetOpenService(viewsheetSessions, sheetSessions, worksheetService,
                                     mock(SecurityProvider.class), broadcast, viewsheetService,
                                     runtimeAccess);
   }

   private JoinSession session(JoinSession.ConnectionMode mode, String socket) {
      return new JoinSession("tok", "vs-1", OWNER, SheetType.VIEWSHEET, 0L,
                             SheetSessionService.TTL_MILLIS, mode, socket, "alice", null);
   }

   private void acting(JoinSession s) throws Exception {
      when(sheetSessions.resolve(eq("tok"), eq(OWNER))).thenReturn(s);
      when(viewsheetSessions.requireSessionAllowingPaneScope(eq("tok"), any())).thenReturn(s);
   }

   private void assertRefusedAndNothingHappened(PairingException e) throws Exception {
      assertEquals(PairingException.Kind.VIEWER_MODE, e.getKind());
      verify(viewsheetService, never()).openViewsheet(any(AssetEntry.class), any(Principal.class),
                                                      anyBoolean());
      verify(worksheetService, never()).openWorksheet(any(AssetEntry.class), any(Principal.class));
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class), any(),
                                          any(), any());
      verify(sheetSessions, never()).open(anyString(), anyString(), any(SheetType.class), any(),
                                          any(), any(), any());
      verify(broadcast, never()).sendToComposer(anyString(), any());
      verify(viewsheetSessions, never()).resolve(anyString(), any());
   }

   @Test
   void openBaseWorksheetIsRefusedForAViewerSession() throws Exception {
      acting(session(JoinSession.ConnectionMode.VIEWER, "sock-1"));

      assertRefusedAndNothingHappened(assertThrows(PairingException.class,
         () -> service.openBaseWorksheet("tok", agent, false)));
   }

   @Test
   void openSavedSheetIsRefusedForAViewerSessionForBothSheetTypes() throws Exception {
      acting(session(JoinSession.ConnectionMode.VIEWER, "sock-1"));

      for(SheetType type : SheetType.values()) {
         assertRefusedAndNothingHappened(assertThrows(PairingException.class,
            () -> service.openSavedSheet("tok", agent, type, "Sales/Dash", null, false)));
      }
   }

   @Test
   void createViewsheetIsRefusedForAViewerSession() throws Exception {
      acting(session(JoinSession.ConnectionMode.VIEWER, "sock-1"));

      assertRefusedAndNothingHappened(assertThrows(PairingException.class,
         () -> service.createViewsheet("tok", agent, null)));
   }

   @Test
   void createWorksheetIsRefusedForAViewerSession() throws Exception {
      acting(session(JoinSession.ConnectionMode.VIEWER, "sock-1"));

      assertRefusedAndNothingHappened(assertThrows(PairingException.class,
         () -> service.createWorksheet("tok", agent)));
   }

   /** C1: a session stamped PAIRED whose live runtime is a viewer gets no opener either. */
   @Test
   void aPairedStampOnALiveViewerRuntimeIsRefusedToo() throws Exception {
      acting(session(JoinSession.ConnectionMode.PAIRED, "sock-1"));
      RuntimeViewsheet viewer = mock(RuntimeViewsheet.class);
      when(viewer.isViewer()).thenReturn(true);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.VIEWSHEET, "vs-1")).thenReturn(viewer);

      assertRefusedAndNothingHappened(assertThrows(PairingException.class,
         () -> service.createWorksheet("tok", agent)));
      assertRefusedAndNothingHappened(assertThrows(PairingException.class,
         () -> service.openBaseWorksheet("tok", agent, false)));
   }

   /** C6: a design session reaches the pre-existing guards, not the viewer refusal. */
   @Test
   void aDesignSessionIsNotRefusedAsAViewer() throws Exception {
      acting(session(JoinSession.ConnectionMode.PAIRED, null));   // no socket: stops at its own guard
      RuntimeViewsheet composer = mock(RuntimeViewsheet.class);
      when(composer.isViewer()).thenReturn(false);
      when(runtimeAccess.getRuntimeSheetDirect(SheetType.VIEWSHEET, "vs-1")).thenReturn(composer);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> service.createWorksheet("tok", agent));
      assertTrue(e.getMessage().contains("no active browser connection"), e.getMessage());

      e = assertThrows(IllegalArgumentException.class,
         () -> service.createViewsheet("tok", agent, null));
      assertTrue(e.getMessage().contains("no active browser connection"), e.getMessage());
   }
}
