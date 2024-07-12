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
package inetsoft.web.admin.cluster;

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.monitoring.MonitoringDataService;
import inetsoft.web.cluster.ServerClusterClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
public class ClusterController {

   @Autowired
   public ClusterController(ClusterService clusterService,
                            MonitoringDataService monitoringDataService)
   {
      this.clusterService = clusterService;
      this.monitoringDataService = monitoringDataService;
   }

   @GetMapping("/api/em/cluster/get-cluster-nodes")
   public ClusterNodesModel getClusterNodes() {
      Set<String> nodes = "server_cluster".equals(SreeEnv.getProperty("server.type")) ?
         new ServerClusterClient().getMonitoringServersServers() : null;

      if(nodes == null) {
         return null;
      }

      Set<String> hosts = new HashSet<>();

      for(String node : nodes) {
         int index = node.indexOf(':');
         hosts.add(index < 0 ? node : node.substring(0, index));
      }

      return ClusterNodesModel.builder()
         .clusterEnabled(getClusterEnabled().enabled())
         .clusterNodes(hosts)
         .build();
   }

   @SubscribeMapping("/monitoring/cluster/report-cluster")
   public List<ReportClusterNodeModel> subscribeClusterStatus(
      StompHeaderAccessor stompHeaderAccessor)
   {
      return monitoringDataService
         .addSubscriber(stompHeaderAccessor, clusterService::getClusterStatus);
   }

   @GetMapping("/em/monitoring/cluster/cluster-status")
   public List<ReportClusterNodeModel> getClusterStatus() {
      return clusterService.getClusterStatus();
   }

   @GetMapping("/em/monitoring/cluster/cluster-enabled")
   public ClusterEnabledModel getClusterEnabled() {
      return clusterService.getClusterEnabled();
   }

   @PostMapping("/em/monitoring/cluster/pause-server")
   public void pauseServer(@RequestBody String[] servers) {
      clusterService.pauseServers(servers);
   }

   @PostMapping("/em/monitoring/cluster/resume-server")
   public void resumeServer(@RequestBody String[] servers) {
      clusterService.resumeServers(servers);
   }

   private final ClusterService clusterService;
   private final MonitoringDataService monitoringDataService;
}
