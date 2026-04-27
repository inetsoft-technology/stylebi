/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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

package inetsoft.report;

import inetsoft.report.lib.logical.NoopLibrarySecurity;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.BlobStorageManager;
import inetsoft.util.ConfigurationContext;
import jakarta.annotation.PreDestroy;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LibManagerProvider {
   public LibManagerProvider(Cluster cluster, BlobStorageManager blobStorageManager,
                             SecurityEngine securityEngine)
   {
      this.cluster = cluster;
      this.blobStorageManager = blobStorageManager;
      this.securityEngine = securityEngine;
   }

   public static LibManagerProvider getInstance() {
      return ConfigurationContext.getContext().getSpringBean(LibManagerProvider.class);
   }

   /**
    * Gets the shared instance of the library manager.
    *
    * @return the library manager.
    */
   public LibManager getManager(Principal principal) {
      return getManager(OrganizationManager.getInstance().getCurrentOrgID(principal));
   }

   public LibManager getManager() {
      return getManager(OrganizationManager.getInstance().getCurrentOrgID());
   }

   public LibManager getManager(String orgID) {
      lock.lock();
      try {
         LibManager manager = managers.get(orgID);

         if(manager == null) {
            manager = new LibManager(new NoopLibrarySecurity(), orgID, cluster, blobStorageManager);
            managers.put(orgID, manager);
         }
         else {
            manager.getStorage();
         }

         return manager;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Restarts the library manager.
    */
   public void restart() {
      clear();

      for(String orgID : securityEngine.getOrganizations()) {
         getManager(orgID);
      }
   }

   /**
    * Clears the cached library manager.
    */
   @PreDestroy
   public void clear() {
      lock.lock();
      try {
         for(LibManager manager : managers.values()) {
            manager.tearDown();
         }
         managers.clear();
      }
      finally {
         lock.unlock();
      }
   }

   private final Cluster cluster;
   private final BlobStorageManager blobStorageManager;
   private final SecurityEngine securityEngine;
   private final Lock lock = new ReentrantLock();
   private final Map<String, LibManager> managers = new HashMap<>();
}
