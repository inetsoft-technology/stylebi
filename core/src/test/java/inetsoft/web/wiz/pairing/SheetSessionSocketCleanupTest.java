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
package inetsoft.web.wiz.pairing;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the production wiring, not just the reachable method: a real WebSocket disconnect
 * event must actually end a pane-scoped session bound to that socket, via
 * {@link SheetSessionService#socketClosed}. {@link SheetSessionServiceTest} covers the
 * service-level behaviour directly; this covers the listener that calls it in practice --
 * without it, socketClosed would never fire on a real crash/network drop/killed tab.
 */
@Tag("core")
class SheetSessionSocketCleanupTest {

   @Test
   void aRealDisconnectEventEndsThePaneSessionBoundToThatSocket() {
      SheetSessionService sessions = new SheetSessionService();
      SheetSessionSocketCleanup cleanup = new SheetSessionSocketCleanup(sessions);
      EditorContext ctx = new EditorContext("assemblyMain", "Chart1", null, null);
      JoinSession pane = sessions.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                       "owner", ctx);

      Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
      SessionDisconnectEvent event =
         new SessionDisconnectEvent(this, message, "sock-1", CloseStatus.NORMAL);

      cleanup.onSessionDisconnect(event);

      assertNull(sessions.resolve(pane.sessionToken(), "owner~;~org"));
   }

   @Test
   void aDisconnectOnAnUnrelatedSocketLeavesTheWholeSheetSessionAlone() {
      SheetSessionService sessions = new SheetSessionService();
      SheetSessionSocketCleanup cleanup = new SheetSessionSocketCleanup(sessions);
      JoinSession sheet = sessions.open("vs-1", "owner~;~org", SheetType.VIEWSHEET, "sock-1",
                                        "owner", null);

      Message<byte[]> message = MessageBuilder.withPayload(new byte[0]).build();
      SessionDisconnectEvent event =
         new SessionDisconnectEvent(this, message, "sock-1", CloseStatus.NORMAL);

      cleanup.onSessionDisconnect(event);

      assertNotNull(sessions.resolve(sheet.sessionToken(), "owner~;~org"));
   }
}
