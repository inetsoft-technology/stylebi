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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * Ends every pane-scoped agent-pairing session bound to a WebSocket session when that session
 * disconnects.
 *
 * <p>This is the socket-loss half of Task 9 ("a pane session dies with its editor"). The
 * explicit {@code detach()} call the browser sends when a script pane/formula editor is closed
 * (see {@code SheetPairingController#detachViaSocket}) only covers a deliberate cancel/close --
 * it never fires for a crashed tab, a killed browser process, or a dropped network connection.
 * This listener is what catches those: the STOMP session going away, however it happened, ends
 * any pane-scoped session bound to it immediately.
 *
 * <p>Whole-sheet ("Connect to Claude" toolbar) sessions are untouched --
 * {@link SheetSessionService#socketClosed} only reaps sessions with a non-null
 * {@code editorContext}, so a toolbar-paired user does not lose their session to the same
 * socket blip that a pane-scoped session is deliberately not allowed to survive.
 */
@Component
public class SheetSessionSocketCleanup {
   @Autowired
   public SheetSessionSocketCleanup(SheetSessionService sessions) {
      this.sessions = sessions;
   }

   @EventListener
   public void onSessionDisconnect(SessionDisconnectEvent event) {
      sessions.socketClosed(event.getSessionId());
   }

   private final SheetSessionService sessions;
}
