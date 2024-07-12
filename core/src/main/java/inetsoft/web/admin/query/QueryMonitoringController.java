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
package inetsoft.web.admin.query;

import inetsoft.web.admin.monitoring.AbstractMonitoringController;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.factory.RemainingPath;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

@RestController
public class QueryMonitoringController extends AbstractMonitoringController {

   @Autowired
   QueryMonitoringController(QueryService queryService,
                             MonitoringDataService monitoringDataService) {
      this.queryService = queryService;
      this.monitoringDataService = monitoringDataService;
   }

   @SubscribeMapping(value = {"/monitoring/queries/executing",
                              "/monitoring/queries/executing/{server}"})
   public List<QueryMonitoringTableModel> subscribe(StompHeaderAccessor stompHeaderAccessor,
                                                    @DestinationVariable("server") Optional<String> server,
                                                    Principal principal)
   {
      return this.monitoringDataService.addSubscriber(stompHeaderAccessor, () -> {
         try {
            return queryService.getQueries(server.orElse(null), principal);
         }
         catch(Exception e) {
            throw new RuntimeException(e);
         }
      });
   }

   @PostMapping("/em/monitoring/queries/remove/**")
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
   private static final Logger LOG = LoggerFactory.getLogger(QueryMonitoringController.class);
}
