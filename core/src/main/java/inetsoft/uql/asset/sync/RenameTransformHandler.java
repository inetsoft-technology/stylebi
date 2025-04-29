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
package inetsoft.uql.asset.sync;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.asset.*;
import inetsoft.uql.erm.XPartition;
import inetsoft.util.SingletonManager;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

@SingletonManager.Singleton(RenameTransformHandler.Reference.class)
public class RenameTransformHandler implements AutoCloseable {
   /**
    * Gets the shared instance of the RenameTransformHandler.
    *
    * @return the rename transform handler.
    */
   public static RenameTransformHandler getTransformHandler() {
      return SingletonManager.getInstance(RenameTransformHandler.class);
   }

   @Override
   public synchronized void close() throws Exception {
   }

   /**
    * Add a RenameDependencyInfo to the queue.
    */
   public void addTransformTask(RenameDependencyInfo dinfo) {
      addTransformTask(dinfo, false);
   }

   /**
    * Add a RenameDependencyInfo to the queue.
    */
   public void addTransformTask(RenameDependencyInfo dinfo, boolean waitDone) {
      Future<?> dependencyStorage = cluster.submit("dependencyStorage", new RenameTransformTask(dinfo, waitDone));

      if(waitDone) {
         try {
            dependencyStorage.get();
         }
         catch(Exception e) {
            LOG.error("wait dependencyStorage update failure", e);
         }
      }
   }

   public void addExtendPartitionsTransformTask(XPartition partition, RenameInfo rinfo) {
      String[] children = partition.getPartitionNames();
      DependencyStorageService service = DependencyStorageService.getInstance();

      for(String child : children) {
         String nChildPath = Tool.buildString(rinfo.getNewName(), "^", child);
         String oChildPath = Tool.buildString(rinfo.getOldName(), "^", child);
         RenameInfo childInfo = new RenameInfo(oChildPath, nChildPath, rinfo.getType());
         addTransformTask(childInfo);
         String oldKey = DependencyTransformer.getOldKey(childInfo);
         String newKey = DependencyTransformer.getKey(childInfo, false);
         service.rename(oldKey, newKey, rinfo.getOrganizationId());
      }

      addTransformTask(rinfo);
   }

   /**
    * Add a RenameDependencyInfo to the queue.
    */
   public void addTransformTask(RenameInfo rinfo) {
      RenameDependencyInfo dinfo = new RenameDependencyInfo();
      List<RenameInfo> rinfos = new ArrayList<>();
      dinfo.setRenameInfos(rinfos);
      rinfos.add(rinfo);
      String oldKey = DependencyTransformer.getOldKey(rinfo);
      List<AssetObject> entries = DependencyTransformer.getDependencies(oldKey);

      if(entries == null) {
         return;
      }

      boolean changeScope = false;

      // if change worksheet from global to private, should not transform global report and vs.
      // The global report binding private ws is not support in our product.
      if(rinfo.isWorksheet()) {
         String oname = rinfo.getOldName();
         String nname = rinfo.getNewName();

         if(oname.startsWith("1^2^") && nname.startsWith("4^2^")) {
            changeScope = true;
         }
      }

      for(AssetObject entry : entries) {
         if(changeScope && !supportScope(entry)) {
            continue;
         }

         dinfo.setRenameInfo(entry, rinfos);
      }

      addTransformTask(dinfo);
   }

   private boolean supportScope(AssetObject entry) {
      if(entry instanceof AssetEntry) {
         AssetEntry asset = (AssetEntry) entry;
         return !asset.isViewsheet() || asset.getScope() != AssetRepository.USER_SCOPE;
      }

      return true;
   }

   /**
    * Can be called from a groovy script to ensure that a rename has finished before continuing.
    */
   @SuppressWarnings({ "unused", "BusyWait" })
   public void waitUntilRenameFinished() {
      DependencyStorageService service = DependencyStorageService.getInstance();

      try {
         while(!service.getQueue().isEmpty()) {
            try {
               Thread.sleep(5000L);
            }
            catch(InterruptedException e) {
               LOG.warn("Interrupted while waiting for rename task queue to complete");
               break;
            }
         }
      }
      catch(Exception e) {
         LOG.error("Failed to determine if rename task queue is empty", e);
      }
   }

   private final Cluster cluster = Cluster.getInstance();
   private static final Logger LOG = LoggerFactory.getLogger(RenameTransformHandler.class);

   public static final class Reference extends SingletonManager.Reference<RenameTransformHandler> {
      @Override
      public RenameTransformHandler get(Object... parameters) {
         if(renameTransformHandler == null) {
            renameTransformHandler = new RenameTransformHandler();
         }

         return renameTransformHandler;
      }

      @Override
      public void dispose() {
         if(renameTransformHandler != null) {
            try {
               renameTransformHandler.close();
               renameTransformHandler = null;
            }
            catch(Exception e) {
               throw new RuntimeException("Failed to close the RenameTransformHandler", e);
            }
         }
      }

      private RenameTransformHandler renameTransformHandler;
   }
}
