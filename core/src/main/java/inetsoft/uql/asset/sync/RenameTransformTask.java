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
package inetsoft.uql.asset.sync;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.SingletonRunnableTask;
import inetsoft.storage.KeyValueTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

public class RenameTransformTask
   extends KeyValueTask<RenameTransformObject> implements SingletonRunnableTask
{
   public RenameTransformTask(RenameDependencyInfo info, boolean waitDone) {
      super("dependencyStorage");
      this.info = info;
      this.waitDone = waitDone;
   }

   public RenameTransformTask(RenameDependencyInfo info) {
      this(info, false);
   }

   @Override
   public void run() {
      RenameTransformQueue queue = getEngine().get(getId(), DependencyStorageService.QUEUE_KEY);

      if(queue == null) {
         queue = new RenameTransformQueue();
      }

      queue.add(info);
      getEngine().put(getId(), DependencyStorageService.QUEUE_KEY, queue);
      LOG.debug("Rename transform task added to queue: {}", info.getTaskId());
      Future<?> renameTransform = Cluster.getInstance().submit("renameTransform", new Rename(info));

      if(waitDone) {
         try {
            renameTransform.get();
         }
         catch(Exception e) {
            LOG.error("wait renameTransform failure", e);
         }
      }
   }

   private final RenameDependencyInfo info;
   private boolean waitDone = false;
   private static final Logger LOG = LoggerFactory.getLogger(RenameTransformTask.class);

   public static final class Rename implements SingletonRunnableTask {
      public Rename(RenameDependencyInfo info) {
         this.info = info;
      }

      @Override
      public void run() {
         Cluster.getInstance().submit("dependencyStorage", new Remove(info));
         LOG.debug("Rename transform task started: {}", info.getTaskId());

         try {
            DependencyTransformer.renameDep(info);

            if(info.isUpdateStorage()) {
               for(RenameInfo rinfo : info.getRenameInfos()) {
                  DependencyTransformer.renameDepStorage(rinfo);
               }
            }
         }
         finally {
            LOG.debug("Rename transform task finished: {}", info.getTaskId());
         }
      }

      private final RenameDependencyInfo info;
   }

   public static final class Remove
      extends KeyValueTask<RenameDependencyInfo> implements SingletonRunnableTask
   {
      public Remove(RenameDependencyInfo info) {
         super("dependencyStorage");
         this.info = info;
      }

      @Override
      public void run() {
         RenameTransformQueue queue = getEngine().get(getId(), DependencyStorageService.QUEUE_KEY);
         queue.remove(info);
         getEngine().put(getId(), DependencyStorageService.QUEUE_KEY, queue);
         LOG.debug("Rename transform task removed from queue: {}", info.getTaskId());
      }

      private final RenameDependencyInfo info;
   }
}
