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

package inetsoft.web.admin.server;

import inetsoft.report.composition.RuntimeSheetCache;
import inetsoft.sree.internal.cluster.Cluster;

import java.io.Serializable;
import java.util.*;

/**
 * Computes which nodes should have protection enabled based on the number of local
 * runtime sheets
 */
public class NodeProtectionCoordinatorTask implements Runnable, Serializable {
   private void init() {
      if(cluster == null) {
         cluster = Cluster.getInstance();
      }

      if(sheetCountMap == null) {
         sheetCountMap = cluster.getReplicatedMap(RuntimeSheetCache.LOCAL_SHEET_COUNT);
      }

      if(nodeProtectionMap == null) {
         nodeProtectionMap = cluster.getReplicatedMap(NodeProtectionService.NODE_PROTECTION_MAP);
      }
   }

   @Override
   public void run() {
      init();

      List<String> nodes = new ArrayList<>(cluster.getClusterNodes());
      Collections.sort(nodes);

      String minNode = null;
      int min = Integer.MAX_VALUE;

      // determine the min
      for(String node : nodes) {
         int value = sheetCountMap.getOrDefault(node, 0);

         if(value < min) {
            min = value;
            minNode = node;
         }

         // no need to check further
         if(min == 0) {
            break;
         }
      }

      // set node protections
      for(String node : nodes) {
         boolean oldValue = nodeProtectionMap.getOrDefault(node, false);
         boolean newValue = !node.equals(minNode);

         // reduce the number of update events
         if(oldValue != newValue) {
            nodeProtectionMap.put(node, newValue);
         }
      }
   }

   private transient Cluster cluster;
   private transient Map<String, Integer> sheetCountMap;
   private transient Map<String, Boolean> nodeProtectionMap;
}
