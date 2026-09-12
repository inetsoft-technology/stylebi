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
package inetsoft.web.admin.cache;

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
@Lazy(false)
public class CacheMonitoringController {
   @Autowired
   public CacheMonitoringController(CacheService cacheService,
                                    MonitoringDataService monitoringDataService,
                                    SecurityEngine securityEngine)
   {
      this.cacheService = cacheService;
      this.monitoringDataService = monitoringDataService;
      this.securityEngine = securityEngine;
   }

   @SubscribeMapping(value = {"/monitoring/cache/getDataGrid/{address}",
                              "/monitoring/cache/getDataGrid/"})
   public List<CacheMonitoringTableModel> subscribeDataGrid(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address, Principal principal)
      throws SecurityException
   {
      if(!securityEngine.getSecurityProvider().checkPermission(
         principal, ResourceType.EM_COMPONENT, "monitoring/cache", ResourceAction.ACCESS))
      {
         throw new SecurityException("Unauthorized access to cache monitoring by user " + principal.getName());
      }

      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return cacheService.getDataGrid(address.orElse(null));
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   private final CacheService cacheService;
   private final MonitoringDataService monitoringDataService;
   private final SecurityEngine securityEngine;
}
