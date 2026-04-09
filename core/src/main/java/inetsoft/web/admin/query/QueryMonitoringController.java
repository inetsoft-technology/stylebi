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
package inetsoft.web.admin.query;

import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.AbstractMonitoringController;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
public class QueryMonitoringController extends AbstractMonitoringController {

   @Autowired
   QueryMonitoringController(QueryService queryService,
                             MonitoringDataService monitoringDataService,
                             SecurityEngine securityEngine)
   {
      this.queryService = queryService;
      this.monitoringDataService = monitoringDataService;
      this.securityEngine = securityEngine;
   }

   @SubscribeMapping(value = {"/monitoring/queries/executing",
                              "/monitoring/queries/executing/{server}"})
   public List<QueryMonitoringTableModel> subscribe(StompHeaderAccessor stompHeaderAccessor,
                                                    @DestinationVariable("server") Optional<String> server,
                                                    Principal principal)
      throws SecurityException
   {
      if(!securityEngine.getSecurityProvider().checkPermission(
         principal, ResourceType.EM_COMPONENT, "monitoring/queries/executing", ResourceAction.ACCESS))
      {
         throw new SecurityException("Unauthorized access to query monitoring by user " + principal.getName());
      }

      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return queryService.getQueries(server.orElse(null), principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @Secured(
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/queries/executing",
         actions = ResourceAction.ACCESS
      )
   )
   @PostMapping("/api/em/monitoring/queries/remove/**")
   public void remove(@RequestBody String[] ids, @RemainingPath String server) {
      try {
         queryService.destroyClusterQueries(getServerClusterNode(server), ids);
      }
      catch(Exception e) {
         LOG.error("Failed to destroy cluster queries", e);
      }
   }

   private final QueryService queryService;
   private final MonitoringDataService monitoringDataService;
   private final SecurityEngine securityEngine;
   private static final Logger LOG = LoggerFactory.getLogger(QueryMonitoringController.class);
}
