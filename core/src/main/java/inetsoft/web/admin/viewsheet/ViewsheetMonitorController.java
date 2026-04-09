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
package inetsoft.web.admin.viewsheet;

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.web.admin.monitoring.AbstractMonitoringController;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.factory.RemainingPath;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@RestController
@Lazy(false)
public class ViewsheetMonitorController extends AbstractMonitoringController {
   @Autowired
   public ViewsheetMonitorController(ViewsheetService viewsheetService,
                                     MonitoringDataService monitoringDataService,
                                     SecurityEngine securityEngine)
   {
      this.viewsheetService = viewsheetService;
      this.monitoringDataService = monitoringDataService;
      this.securityEngine = securityEngine;
   }

   @SubscribeMapping({"/monitoring/viewsheets/executing",
                      "/monitoring/viewsheets/executing/{address}"})
   public List<ViewsheetMonitoringTableModel> subscribeToExecuting(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address, Principal principal)
      throws SecurityException
   {
      if(!securityEngine.getSecurityProvider().checkPermission(
         principal, ResourceType.EM_COMPONENT, "monitoring/viewsheets/executing", ResourceAction.ACCESS))
      {
         throw new SecurityException("Unauthorized access to viewsheet monitoring by user " + principal.getName());
      }

      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return viewsheetService.getExecutingViewsheets(address.orElse(null), principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @SubscribeMapping({"/monitoring/viewsheets/open", "/monitoring/viewsheets/open/{address}"})
   public List<ViewsheetMonitoringTableModel> subscribeToOpen(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address, Principal principal)
      throws SecurityException
   {
      if(!securityEngine.getSecurityProvider().checkPermission(
         principal, ResourceType.EM_COMPONENT, "monitoring/viewsheets/open", ResourceAction.ACCESS))
      {
         throw new SecurityException("Unauthorized access to viewsheet monitoring by user " + principal.getName());
      }

      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return viewsheetService.getOpenViewsheets(address.orElse(null), principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @Secured(
      value = {
         @RequiredPermission(
            resourceType = ResourceType.EM_COMPONENT,
            resource = "monitoring/viewsheets/open",
            actions = ResourceAction.ACCESS
         ),
         @RequiredPermission(
            resourceType = ResourceType.EM_COMPONENT,
            resource = "monitoring/viewsheets/executing",
            actions = ResourceAction.ACCESS
         )
      },
      operator = "OR"
   )
   @PostMapping("/api/em/monitoring/viewsheets/remove/**")
   public void closeViewsheets(@RemainingPath String server,
                               @RequestBody() String[] viewsheetIds) throws Exception
   {
      try {
         viewsheetService.destroyClusterNodeViewsheets(getServerClusterNode(server), viewsheetIds);
      }
      catch(IllegalArgumentException ex) {
         // ignore, this is thrown if a vs doesn't exist anymore
      }
      catch(ExpiredSheetException ex) {
         // ignore, if vs is expired.
      }
   }

   private final ViewsheetService viewsheetService;
   private final MonitoringDataService monitoringDataService;
   private final SecurityEngine securityEngine;
}
