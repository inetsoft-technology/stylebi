/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.composer;

import inetsoft.sree.internal.cluster.*;
import inetsoft.web.session.*;
import inetsoft.web.viewsheet.service.RuntimeViewsheetManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

@Service
@Lazy(false)
public class SessionDisconnectListenerService implements MessageListener {
   @Autowired
   public SessionDisconnectListenerService(RuntimeViewsheetManager runtimeViewsheetManager) {
      this.runtimeViewsheetManager = runtimeViewsheetManager;
      Cluster cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
   }

   @Override
   public void messageReceived(MessageEvent event) {
      Object message = event.getMessage();

      if(message instanceof SessionDeletedEvent || message instanceof SessionExpiredEvent) {
         runtimeViewsheetManager.sessionEnded(((SessionEvent) message).getPrincipalCookie());
      }
   }

   private final RuntimeViewsheetManager runtimeViewsheetManager;
}
