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

import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Service
@Scope(value = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class ComposerClientService {
   public void removeFromSessionList() {
      LOCK.lock();

      try {
         List<String> simpSessionIdList = COMPOSER_CLIENTS.get(httpSessionId);

         if(simpSessionIdList != null) {
            simpSessionIdList.remove(simpSessionId);
         }
      }
      finally {
         LOCK.unlock();
      }
   }

   @PreDestroy
   public void preDestroy() {
      removeFromSessionList();
   }

   public static String getFirstSimpSessionId(String httpSessionId) {
      LOCK.lock();

      try {
         List<String> simpSessionIdList = COMPOSER_CLIENTS.get(httpSessionId);
         String simpSessionId = simpSessionIdList != null && simpSessionIdList
            .size() > 0 ? simpSessionIdList.get(0) : null;
         return simpSessionId;
      }
      finally {
         LOCK.unlock();
      }
   }

   public void setSessionID(Supplier<String[]> sessionIDSupplier) {
      LOCK.lock();

      try {
         String[] sessions = sessionIDSupplier.get();
         httpSessionId = sessions[0];
         simpSessionId = sessions[1];
         List<String> simpSessionIdList =
            COMPOSER_CLIENTS.computeIfAbsent(httpSessionId, k -> new ArrayList<>());
         simpSessionIdList.add(simpSessionId);
      }
      finally {
         LOCK.unlock();
      }
   }

   private String httpSessionId;
   private String simpSessionId;

   public static final String COMMANDS_TOPIC = "/composer-client";
   // key = http session id, value = list of simpSessionIds
   private static final Map<String, List<String>> COMPOSER_CLIENTS = new HashMap<>();
   private static final Lock LOCK = new ReentrantLock();
}
