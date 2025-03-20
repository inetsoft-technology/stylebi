/*
 * This file is part of StyleBI.
 * Copyright (C) 2025  InetSoft Technology
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

import inetsoft.cluster.*;
import inetsoft.report.composition.WorksheetEngine;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;

@Service
@ClusterProxy
public class MonitorDataService {
   public MonitorDataService(MonitoringDataService monitoringDataService)
   {
      this.monitoringDataService = monitoringDataService;
   }

   @ClusterProxyMethod(WorksheetEngine.CACHE_NAME)
   public Boolean refresh(@ClusterProxyKey String sessionId, StompHeaderAccessor stompHeaderAccessor) {
      this.monitoringDataService.updateSession(stompHeaderAccessor);
      return true;
   }

   private final MonitoringDataService monitoringDataService;
}
