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

import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class ComposerClientService {
   public void removeFromSessionList(StompHeaderAccessor headers) {

      if(headers.getSessionAttributes() != null) {
         String httpSessionId = headers.getSessionAttributes().get("HTTP.SESSION.ID").toString();
         List<String> sessionList = composerClients.get(httpSessionId);

         if(sessionList != null) {
            sessionList.remove(headers.getSessionId());

            if(sessionList.isEmpty()) {
               composerClients.remove(httpSessionId);
            }
         }
      }
   }

   public String getFirstSimpSessionId(String httpSessionId) {
      List<String> simpSessionIdList = composerClients.get(httpSessionId);

      if(simpSessionIdList != null && !simpSessionIdList.isEmpty()) {
         return simpSessionIdList.getFirst();
      }

      return null;
   }

   public void setSessionID(Supplier<String[]> sessionIDSupplier) {
      String[] sessions = sessionIDSupplier.get();
      String httpSessionId = sessions[0];
      String simpSessionId = sessions[1];
      List<String> simpSessionIdList =
         composerClients.computeIfAbsent(httpSessionId, k -> new ArrayList<>());
      simpSessionIdList.add(simpSessionId);
   }

   public static final String COMMANDS_TOPIC = "/composer-client";
   // key = http session id, value = list of simpSessionIds
   private final Map<String, List<String>> composerClients = new ConcurrentHashMap<>();
}
