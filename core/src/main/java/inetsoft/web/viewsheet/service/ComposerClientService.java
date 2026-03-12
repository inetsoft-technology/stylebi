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

package inetsoft.web.viewsheet.service;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ComposerClientService {
   @PostConstruct
   public void init() {
      composerClients = Cluster.getInstance().getReplicatedMap(getClass().getName() + ".sessions");
   }

   public void removeFromSessionList(StompHeaderAccessor headers) {
      String simpSessionId = headers.getSessionId();

      if(simpSessionId != null) {
         composerClients.remove(simpSessionId);
      }
   }

   public String getFirstSimpSessionId(String httpSessionId) {
      return composerClients.entrySet().stream()
         .filter(e -> httpSessionId.equals(e.getValue()))
         .map(Map.Entry::getKey)
         .findFirst()
         .orElse(null);
   }

   public void setSessionID(String httpSessionId, String simpSessionId) {
      composerClients.put(simpSessionId, httpSessionId);
   }

   public static final String COMMANDS_TOPIC = "/composer-client";
   // key = simpSessionId, value = httpSessionId (cluster-wide replicated map)
   private DistributedMap<String, String> composerClients;
}
