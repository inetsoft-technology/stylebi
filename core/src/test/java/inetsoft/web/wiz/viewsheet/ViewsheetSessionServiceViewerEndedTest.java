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

import inetsoft.sree.security.IdentityID;
import inetsoft.web.viewsheet.controller.UndoRedoServiceProxy;
import inetsoft.web.wiz.pairing.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A7 / W4: an agent whose viewer page closed or reloaded is told so, by name, with the same
 * {@code SESSION_EXPIRED} kind the plugin's reaper keys on; every other dead token keeps today's
 * generic text.
 */
@Tag("core")
class ViewsheetSessionServiceViewerEndedTest {
   private static final String OWNER = new IdentityID("alice", "host-org").convertToKey();
   private final Principal agent = TestPrincipals.user("alice", "host-org");

   private final SheetSessionService sessions = new SheetSessionService();
   private final ViewsheetSessionService svc =
      new ViewsheetSessionService(sessions, mock(SheetRuntimeAccess.class),
                                  mock(SheetAgentBroadcastService.class),
                                  mock(UndoRedoServiceProxy.class));

   @Test
   void aViewerSessionEndedWithItsPageIsAnsweredWithTheNamedText() {
      JoinSession v = sessions.open("vs-1", OWNER, SheetType.VIEWSHEET, "sock-1", "alice", null,
                                    JoinSession.ConnectionMode.VIEWER);
      sessions.socketClosed("sock-1");

      PairingException e = assertThrows(PairingException.class,
         () -> svc.requireSession(v.sessionToken(), agent));

      assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
      assertEquals(ViewerModePolicy.VIEWER_CLOSED_TEXT, e.getMessage());
   }

   @Test
   void aDetachedViewerSessionIsAnsweredTheSameWay() {
      JoinSession v = sessions.open("vs-1", OWNER, SheetType.VIEWSHEET, "sock-1", "alice", null,
                                    JoinSession.ConnectionMode.VIEWER);
      sessions.endViewerSessions("sock-1", "vs-1");

      PairingException e = assertThrows(PairingException.class,
         () -> svc.requireSessionAllowingPaneScope(v.sessionToken(), agent));

      assertEquals(ViewerModePolicy.VIEWER_CLOSED_TEXT, e.getMessage());
   }

   @Test
   void anUnknownTokenAndAnOrdinaryDetachKeepTheGenericText() {
      JoinSession d = sessions.open("vs-1", OWNER, SheetType.VIEWSHEET, "sock-1", "alice", null);
      sessions.close(d.sessionToken());

      for(String token : new String[] { "NOSUCHTOKEN", d.sessionToken() }) {
         PairingException e = assertThrows(PairingException.class,
            () -> svc.requireSession(token, agent));

         assertEquals(PairingException.Kind.SESSION_EXPIRED, e.getKind());
         assertTrue(e.getMessage().startsWith("Invalid or expired viewsheet session: "),
                    e.getMessage());
      }
   }

   /** The plugin reaps on errorCode SESSION_EXPIRED, so the W4 answer must keep it on the wire. */
   @Test
   void theW4AnswerOnTheWireKeepsSessionExpiredAndTheClosedPageWording() throws Exception {
      JoinSession v = sessions.open("vs-1", OWNER, SheetType.VIEWSHEET, "sock-1", "alice", null,
                                    JoinSession.ConnectionMode.VIEWER);
      sessions.socketClosed("sock-1");
      PairingException e = assertThrows(PairingException.class,
         () -> svc.requireSession(v.sessionToken(), agent));
      ViewsheetAssemblyAgentController handler = mock(ViewsheetAssemblyAgentController.class,
         org.mockito.Mockito.withSettings().defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));

      org.springframework.http.ResponseEntity<java.util.Map<String, String>> r =
         handler.handlePairingException(e);

      assertEquals(404, r.getStatusCode().value());
      assertEquals("SESSION_EXPIRED", r.getBody().get("errorCode"));
      assertTrue(r.getBody().get("error").contains("closed or reloaded"), r.getBody().get("error"));
   }
}
