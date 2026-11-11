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
import org.springframework.stereotype.Service;

import java.security.Principal;

/**
 * Keeps an idle paired runtime from being reaped by the 3-minute heartbeat timeout.
 *
 * <p>The session is authorized with a non-sliding lookup, so a periodic keep-alive cannot extend
 * the pairing session's own TTL. The runtime is refreshed with {@code access(false)} only, so the
 * idle limit still reclaims a runtime nobody uses.
 */
@Service
public class SheetKeepAliveService {
   private final SheetSessionService sessions;
   private final SheetRuntimeAccess runtimeAccess;

   @Autowired
   public SheetKeepAliveService(SheetSessionService sessions, SheetRuntimeAccess runtimeAccess) {
      this.sessions = sessions;
      this.runtimeAccess = runtimeAccess;
   }

   /**
    * @throws PairingException SESSION_EXPIRED if the session is unknown, expired or not owned by
    *                          the agent, or the runtime is not resident on this node.
    */
   public void keepAlive(String sessionToken, Principal agent) throws PairingException {
      JoinSession session = sessions.peek(sessionToken, PairingUtil.identityKey(agent));

      if(session == null) {
         throw new PairingException(
            PairingException.Kind.SESSION_EXPIRED,
            "Invalid or expired sheet session: " + sessionToken);
      }

      runtimeAccess.keepAlive(session.sheetType(), session.runtimeId());
   }
}
