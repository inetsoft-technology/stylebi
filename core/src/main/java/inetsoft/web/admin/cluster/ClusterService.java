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
package inetsoft.web.admin.cluster;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ClusterService extends MonitorLevelService implements StatusUpdater {
   @Autowired
   public ClusterService(ServerClusterClient client) {
      super(lowAttrs, medAttrs, new String[0]);
      this.client = client;
   }

   @Override
   public void updateStatus(long timestamp) {
      List<ReportClusterNodeModel> reportNodeModels;

      try {
         reportNodeModels = getClusterStatus();
      }
      catch(Exception e) {
         LOG.warn("Failed to update cluster status", e);
         reportNodeModels = Collections.emptyList();
      }

      ClusterMetrics.Builder builder = ClusterMetrics.builder();
      ClusterMetrics oldMetrics = client.getMetrics(StatusMetricsType.CLUSTER_METRICS, null);

      if(oldMetrics != null) {
         builder.from(oldMetrics);
      }

      builder.reportNodes(reportNodeModels);
      client.setMetrics(StatusMetricsType.CLUSTER_METRICS, builder.build());
   }

   public List<ReportClusterNodeModel> getClusterStatus() {
      ServerClusterClient client = new ServerClusterClient();
      ArrayList<ReportClusterNodeModel> nodes = new ArrayList<>();

      for(String node : client.getConfiguredServers()) {
         ServerClusterStatus status = client.getStatus(node);
         boolean isRunning = status.getStatus() != ServerClusterStatus.Status.DOWN;
         boolean isPaused = status.isPaused();
         String displayStatus;

         if(isPaused) {
            displayStatus = "Paused";
         }
         else if(isRunning) {
            displayStatus = "Running";
         }
         else {
            displayStatus = "Stopped";
         }

         nodes.add(ReportClusterNodeModel.builder()
                      .server(node)
                      .status(displayStatus)
                      .build()
         );
      }

      return nodes;
   }

   public ClusterEnabledModel getClusterEnabled() {
      return ClusterEnabledModel.builder()
         .enabled(LicenseManager.getInstance().isEnterprise())
         .pauseEnabled("true".equals(SreeEnv.getProperty("cluster.pause.enabled", "false")))
         .build();
   }

   public void pauseServers(String[] servers) {
      ServerClusterClient client = new ServerClusterClient();

      for(String server : servers) {
         if(client.getStatus(server).isPaused()) {
            continue;
         }

         boolean success = client.pauseServer(server);

         if(!success) {
            LOG.warn("Failed to pause server");
         }
      }
   }

   public void resumeServers(String[] servers) {
      ServerClusterClient client = new ServerClusterClient();

      for(String server : servers) {
         if(!client.getStatus(server).isPaused()) {
            continue;
         }

         boolean success = client.resumeServer(server);

         if(!success) {
            LOG.warn("Failed to resume server");
         }
      }
   }

   private final ServerClusterClient client;

   private static final Logger LOG = LoggerFactory.getLogger(ClusterService.class);
   private static final String[] medAttrs = { };
   private static final String[] lowAttrs = {
      "node", "type", "status", "memoryFree", "coresFree"
   };
}
