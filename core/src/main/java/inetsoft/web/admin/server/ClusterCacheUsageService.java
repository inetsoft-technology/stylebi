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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.ignite.IgniteCluster;
import inetsoft.util.Tool;
import org.apache.ignite.*;
import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.cache.CachePeekMode;
import org.apache.ignite.internal.binary.BinaryObjectImpl;
import org.springframework.stereotype.Service;

import javax.cache.Cache;
import java.io.PrintWriter;
import java.util.*;

@Service
public class ClusterCacheUsageService {

   /**
    * Writes local cache usage data as CSV to the provided PrintWriter.
    * Gets cache data for the local node only.
    *
    * @param writer the PrintWriter to write CSV data to
    *
    * @return true if successful, false if cluster is not an Ignite cluster
    */
   public boolean writeCacheUsageCsv(PrintWriter writer) {
      Cluster cluster = Cluster.getInstance();

      if(!(cluster instanceof IgniteCluster igniteCluster)) {
         return false;
      }

      writer.println("Host,Cache Name,Local Entries,Total Entries,Avg Entry Size (KB),Estimated Local Size (KB),Estimated Total Size (KB)");

      Ignite ignite = igniteCluster.getIgniteInstance();
      IgniteBinary binary = ignite.binary();

      String host = ignite.cluster().localNode().attribute("local.ip.addr");

      List<CacheUsageRow> rows = new ArrayList<>();

      for(String cacheName : ignite.cacheNames()) {
         try {
            IgniteCache<?, ?> cache = ignite.cache(cacheName);

            if(cache != null) {
               long totalEntries = cache.sizeLong(CachePeekMode.ALL);
               long localEntries = cache.localSizeLong(CachePeekMode.ALL);

               long avgEntrySize = estimateAverageEntrySize(cache, binary);
               long localEstimatedSize = avgEntrySize * localEntries;
               long estimatedTotalSize = avgEntrySize * totalEntries;

               rows.add(new CacheUsageRow(
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
            // Skip caches that can't be accessed
         }
      }

      // Sort by local estimated size descending
      rows.sort(Comparator.comparing(CacheUsageRow::localEstimatedSize).reversed());

      for(CacheUsageRow row : rows) {
         writer.format("%s,%s,%d,%d,%.2f,%.2f,%.2f%n",
                       escapeCsv(row.host()),
                       escapeCsv(row.cacheName()),
                       row.localEntries(),
                       row.totalEntries(),
                       row.avgEntrySize() / 1024.0,
                       row.localEstimatedSize() / 1024.0,
                       row.estimatedTotalSize() / 1024.0);
      }

      return true;
   }

   private long estimateAverageEntrySize(IgniteCache<?, ?> cache, IgniteBinary binary) {
      final int SAMPLE_SIZE = 50;
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
                  BinaryObject keyBinary = binary.toBinary(key);

                  if(keyBinary instanceof BinaryObjectImpl keyImpl) {
                     entrySize += keyImpl.array().length;
                  }
               }

               Object value = entry.getValue();

               if(value != null) {
                  BinaryObject valueBinary = binary.toBinary(value);

                  if(valueBinary instanceof BinaryObjectImpl valueImpl) {
                     entrySize += valueImpl.array().length;
                  }
               }

               totalSize += entrySize;
               sampledCount++;
            }
            catch(Exception e) {
               totalSize += 1024;
               sampledCount++;
            }
         }
      }
      catch(Exception e) {
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

   private String escapeCsv(String value) {
      if(value == null) {
         return "";
      }

      if(value.contains(",") || value.contains("\"") || value.contains("\n")) {
         return "\"" + value.replace("\"", "\"\"") + "\"";
      }

      return value;
   }

   private record CacheUsageRow(
      String host,
      String cacheName,
      long localEntries,
      long totalEntries,
      long avgEntrySize,
      long localEstimatedSize,
      long estimatedTotalSize
   ) {
   }
}
