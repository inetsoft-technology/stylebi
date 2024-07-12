/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.cache;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.web.admin.monitoring.MonitoringDataService;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

@Controller
public class CacheMonitoringController {
   @Autowired
   public CacheMonitoringController(CacheService cacheService,
                                    MonitoringDataService monitoringDataService) {
      this.cacheService = cacheService;
      this.monitoringDataService = monitoringDataService;
   }

   @SubscribeMapping(value = {"/monitoring/cache/getDataGrid/{address}",
                              "/monitoring/cache/getDataGrid/"})
   public List<CacheMonitoringTableModel> subscribeDataGrid(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address, Principal principal)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
         if (SecurityEngine.getSecurity().isSecurityEnabled() && SUtil.isMultiTenant() && !OrganizationManager.getInstance().isSiteAdmin(principal)) {
            throw new RuntimeException(
                    "Unauthorized access to resource cache  monitoring by user " + pId);
         }

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
}
