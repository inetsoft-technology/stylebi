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
package inetsoft.report.composition.execution;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.security.SecurityProvider;
import inetsoft.storage.BlobStorage;
import inetsoft.util.SingletonManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Distributed task that cleans up expired entries from the table cache store.
 * Runs on a single node in the cluster via DistributedScheduledExecutorService.
 */
public class CleanupTableCacheTask implements Runnable, Serializable {
   @Override
   public void run() {
      try {
         String clusterId = Cluster.getInstance().getId();
         SecurityProvider provider = SecurityEngine.getSecurity().getSecurityProvider();
         String[] orgIds = provider.getOrganizationIDs();
         Instant validInstant = Instant.now().minus(CACHE_EXPIRATION_TIME, ChronoUnit.MINUTES);

         for(String orgId : orgIds) {
            String storeId = orgId.toLowerCase() + "__tableCacheStore";
            BlobStorage<DistributedTableCacheStore.Metadata> storage =
               SingletonManager.getInstance(BlobStorage.class, storeId, false);

            Set<String> keysToRemove = new HashSet<>();
            storage.paths().forEach(key -> {
               try {
                  if(!key.startsWith(clusterId) || storage.getLastModified(key).isBefore(validInstant)) {
                     keysToRemove.add(key);
                  }
               }
               catch(FileNotFoundException e) {
                  // ignore
               }
            });

            if(!keysToRemove.isEmpty()) {
               storage.deleteAll(keysToRemove);
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to clean up table cache", e);
      }
   }

   private static final long CACHE_EXPIRATION_TIME = 30L; // minutes
   private static final Logger LOG = LoggerFactory.getLogger(CleanupTableCacheTask.class);
   private static final long serialVersionUID = 1L;
}
