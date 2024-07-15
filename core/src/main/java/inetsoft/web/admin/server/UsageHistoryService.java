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
package inetsoft.web.admin.server;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.web.admin.query.QueryHistory;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.viewsheet.ViewsheetHistory;
import inetsoft.web.admin.viewsheet.ViewsheetService;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.security.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class UsageHistoryService {
   @Autowired
   public UsageHistoryService(ServerService serverService, ViewsheetService viewsheetService,
                              QueryService queryService)
   {
      this.serverService = serverService;
      this.viewsheetService = viewsheetService;
      this.queryService = queryService;
   }

   @Secured({
      @RequiredPermission(
         resourceType = ResourceType.EM_COMPONENT,
         resource = "monitoring/summary",
         actions = ResourceAction.ACCESS)
   })
   public List<ServerUsage> getUsageHistory(
      String clusterNode,
      @SuppressWarnings("unused") @PermissionUser Principal principal)
   {
      if(clusterNode == null) {
         clusterNode = ServerClusterClient.getLocalServer(Cluster.getInstance());
      }

      String host = clusterNode;

      int index = host.indexOf(':');

      if(index >= 0) {
         host = host.substring(0, index);
      }

      Map<Long, ModifiableServerUsage> history = new TreeMap<>();
      ServerHistory data = serverService.getHistory(clusterNode);

      for(CpuHistory cpu : data.cpu()) {
         ModifiableServerUsage usage = getUsage(cpu.timestamp(), host, history);
         usage.setCpuUsage(cpu.cpuPercent() * 100F);
      }

      for(MemoryHistory memory : data.memory()) {
         ModifiableServerUsage usage = getUsage(memory.timestamp(), host, history);
         usage.setMemoryUsage(memory.usedMemory());
      }

      for(GcHistory gc : data.gc()) {
         ModifiableServerUsage usage = getUsage(gc.timestamp(), host, history);
         usage.setGcCount(gc.collectionCount());
         usage.setGcTime(gc.collectionTime());
      }

      for(ViewsheetHistory viewsheet : viewsheetService.getHistory(clusterNode)) {
         ModifiableServerUsage usage = getUsage(viewsheet.timestamp(), host, history);
         usage.setExecutingViewsheets(viewsheet.executingViewsheets());
      }

      for(QueryHistory query : queryService.getHistory(clusterNode)) {
         ModifiableServerUsage usage = getUsage(query.timestamp(), host, history);
         usage.setExecutingQueries(query.queryCount());
      }

      return history.values().stream()
         .map(ModifiableServerUsage::toImmutable)
         .collect(Collectors.toList());
   }

   private ModifiableServerUsage getUsage(long timestamp, String host, Map<Long, ModifiableServerUsage> map) {
      return map.computeIfAbsent(
         timestamp, ts -> ModifiableServerUsage.create().setTimestamp(ts).setHost(host));
   }

   private final ServerService serverService;
   private final ViewsheetService viewsheetService;
   private final QueryService queryService;
}
