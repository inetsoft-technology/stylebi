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
package inetsoft.web.admin.monitoring;

import inetsoft.cluster.ClusterProxyKey;
import inetsoft.cluster.ClusterProxyMethod;
import inetsoft.report.composition.WorksheetEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class MonitorDataController {
   @Autowired
   public MonitorDataController(MonitorDataServiceProxy dataServiceProxy)
   {
      this.dataServiceProxy = dataServiceProxy;
   }

   @MessageMapping("/monitoring/refresh")
   public void refresh(StompHeaderAccessor stompHeaderAccessor) {
      final MessageHeaders messageHeaders = stompHeaderAccessor.getMessageHeaders();
      final String sessionId =
         (String) messageHeaders.get(SimpMessageHeaderAccessor.SESSION_ID_HEADER);
      this.dataServiceProxy.refresh(sessionId, stompHeaderAccessor);
   }

   private final MonitorDataServiceProxy dataServiceProxy;
}
