/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.admin.viewsheet;

import inetsoft.report.composition.ExpiredSheetException;
import inetsoft.web.admin.monitoring.AbstractMonitoringController;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.factory.RemainingPath;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

@RestController
public class ViewsheetMonitorController extends AbstractMonitoringController {
   @Autowired
   public ViewsheetMonitorController(ViewsheetService viewsheetService,
                                     MonitoringDataService monitoringDataService) {
      this.viewsheetService = viewsheetService;
      this.monitoringDataService = monitoringDataService;
   }

   @SubscribeMapping({"/monitoring/viewsheets/executing",
                      "/monitoring/viewsheets/executing/{address}"})
   public List<ViewsheetMonitoringTableModel> subscribeToExecuting(
      StompHeaderAccessor stompHeaderAccessor,
      @DestinationVariable("address") Optional<String> address, Principal principal)
   {
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
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return viewsheetService.getOpenViewsheets(address.orElse(null), principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @PostMapping("/em/monitoring/viewsheets/remove/**")
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
}
