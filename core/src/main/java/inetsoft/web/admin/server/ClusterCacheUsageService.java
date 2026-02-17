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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.util.Tool;
import org.apache.ignite.*;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.internal.binary.BinaryObjectImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.cache.Cache;
import java.io.PrintWriter;
import java.util.*;

@Service
public class ClusterCacheUsageService implements MessageListener {
   public ClusterCacheUsageService() {
      cluster = Cluster.getInstance();

      if(SUtil.isCluster()) {
         cluster.addMessageListener(this);
      }
   }

   /**
    * Checks if the cluster is an Ignite cluster.
    *
    * @return true if the cluster is an Ignite cluster
    */
   public boolean isIgniteCluster() {
      return cluster instanceof IgniteCluster;
   }

   /**
    * Writes cache usage data as CSV to the provided PrintWriter.
    * Gets cache data for the specified cluster node, or the local node if not specified.
    *
    * @param clusterNode the cluster node address, or null for the local node
    * @param writer      the PrintWriter to write CSV data to
    */
   public void writeCacheUsageCsv(String clusterNode, PrintWriter writer) {
      List<GetClusterCacheUsageResponse.CacheUsageRow> rows = getCacheUsageRows(clusterNode);

      writer.println("Host,Cache Name,Local Entries,Total Entries,Avg Entry Size (KB),Estimated Local Size (KB),Estimated Total Size (KB)");

      for(GetClusterCacheUsageResponse.CacheUsageRow row : rows) {
         writer.format("%s,%s,%d,%d,%.2f,%.2f,%.2f%n",
                       escapeCsv(row.host()),
                       escapeCsv(row.cacheName()),
                       row.localEntries(),
                       row.totalEntries(),
                       row.avgEntrySize() / 1024.0,
                       row.localEstimatedSize() / 1024.0,
                       row.estimatedTotalSize() / 1024.0);
      }
   }

   /**
    * Gets cache usage rows for the specified cluster node.
    *
    * @param clusterNode the cluster node address, or null for the local node
    *
    * @return the list of cache usage rows
    */
   private List<GetClusterCacheUsageResponse.CacheUsageRow> getCacheUsageRows(String clusterNode) {
      if(SUtil.isCluster() && !isLocalNode(clusterNode)) {
         try {
            String targetNode = resolveClusterNode(clusterNode);

            if(targetNode == null) {
               LOG.error("Could not resolve cluster node: {}", clusterNode);
               return Collections.emptyList();
            }

            GetClusterCacheUsageResponse response = cluster.exchangeMessages(
               targetNode, new GetClusterCacheUsageRequest(),
               GetClusterCacheUsageResponse.class);
            return response.getRows();
         }
         catch(Exception e) {
            LOG.error("Failed to get cache usage from {}", clusterNode, e);
            return Collections.emptyList();
         }
      }

      return getLocalCacheUsageRows();
   }

   private boolean isLocalNode(String clusterNode) {
      if(clusterNode == null || clusterNode.isEmpty()) {
         return true;
      }

      String localMember = cluster.getLocalMember();
      return localMember.equals(clusterNode) || localMember.startsWith(clusterNode + ":");
   }

   private String resolveClusterNode(String clusterNode) {
      if(clusterNode == null || clusterNode.isEmpty()) {
         return cluster.getLocalMember();
      }

      // If it already contains a port, return as-is
      if(clusterNode.contains(":")) {
         return clusterNode;
      }

      // Find the full address from cluster nodes
      for(String node : cluster.getClusterNodes()) {
         if(node.equals(clusterNode) || node.startsWith(clusterNode + ":")) {
            return node;
         }
      }

      return null;
   }

   /**
    * Gets cache usage rows for the local node.
    *
    * @return the list of cache usage rows
    */
   List<GetClusterCacheUsageResponse.CacheUsageRow> getLocalCacheUsageRows() {
      if(!(cluster instanceof IgniteCluster igniteCluster)) {
         return Collections.emptyList();
      }

      Ignite ignite = igniteCluster.getIgniteInstance();
      IgniteBinary binary = ignite.binary();
      String host = cluster.getLocalMember();

      List<GetClusterCacheUsageResponse.CacheUsageRow> rows = new ArrayList<>();

      for(String cacheName : ignite.cacheNames()) {
         try {
            IgniteCache<?, ?> cache = ignite.cache(cacheName);

            if(cache != null) {
               long totalEntries = cache.sizeLong(CachePeekMode.ALL);
               long localEntries = cache.localSizeLong(CachePeekMode.ALL);

               long avgEntrySize = estimateAverageEntrySize(cache, binary);
               long localEstimatedSize = avgEntrySize * localEntries;
               long estimatedTotalSize = avgEntrySize * totalEntries;

               rows.add(new GetClusterCacheUsageResponse.CacheUsageRow(
                  host,
                  cacheName,
                  localEntries,
                  totalEntries,
                  avgEntrySize,
                  localEstimatedSize,
                  estimatedTotalSize
               ));
            }
         }
         catch(Exception e) {
            LOG.debug("Failed to access cache: {}", cacheName, e);
         }
      }

      // Sort by local estimated size descending
      rows.sort(Comparator.comparing(
         GetClusterCacheUsageResponse.CacheUsageRow::localEstimatedSize).reversed());

      return rows;
   }

   @Override
   public void messageReceived(MessageEvent event) {
      if(event.getMessage() instanceof GetClusterCacheUsageRequest) {
         handleGetCacheUsageRequest(event.getSender());
      }
   }

   private void handleGetCacheUsageRequest(String sender) {
      try {
         GetClusterCacheUsageResponse response =
            new GetClusterCacheUsageResponse(getLocalCacheUsageRows());
         cluster.sendMessage(sender, response);
      }
      catch(Exception e) {
         LOG.warn("Failed to send cache usage response", e);
      }
   }

   private long estimateAverageEntrySize(IgniteCache<?, ?> cache, IgniteBinary binary) {
      long totalSize = 0;
      int sampledCount = 0;
      Iterator<? extends Cache.Entry<?, ?>> iterator = null;

      try {
         iterator = cache.iterator();

         while(iterator.hasNext() && sampledCount < SAMPLE_SIZE) {
            Cache.Entry<?, ?> entry = iterator.next();

            try {
               long entrySize = 0;
               Object key = entry.getKey();

               if(key != null) {
                  entrySize += estimateObjectSize(binary.toBinary(key));
               }

               Object value = entry.getValue();

               if(value != null) {
                  entrySize += estimateObjectSize(binary.toBinary(value));
               }

               totalSize += entrySize;
               sampledCount++;
            }
            catch(Exception e) {
               // Skip entries that can't be serialized for size estimation
               LOG.debug("Failed to serialize cache entry for size estimation", e);
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to estimate entry size for cache: {}", cache.getName(), e);
         return 0;
      }
      finally {
         Tool.closeIterator(iterator);
      }

      if(sampledCount == 0) {
         return 0;
      }

      return totalSize / sampledCount;
   }

   private long estimateObjectSize(Object obj) {
      if(obj instanceof BinaryObjectImpl binaryObj) {
         return binaryObj.array().length;
      }
      else if(obj instanceof String str) {
         return (long) str.length() * 2;
      }
      else if(obj instanceof Long || obj instanceof Double) {
         return 8;
      }
      else if(obj instanceof Integer || obj instanceof Float) {
         return 4;
      }
      else if(obj instanceof Short || obj instanceof Character) {
         return 2;
      }
      else if(obj instanceof Byte || obj instanceof Boolean) {
         return 1;
      }

      return 0;
   }

   private String escapeCsv(String value) {
      if(value == null) {
         return "";
      }

      if(value.contains(",") || value.contains("\"") || value.contains("\n")) {
         return "\"" + value.replace("\"", "\"\"") + "\"";
      }

      return value;
   }

   private final Cluster cluster;
   private static final int SAMPLE_SIZE = 50;
   private static final Logger LOG = LoggerFactory.getLogger(ClusterCacheUsageService.class);
}
