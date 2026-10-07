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
package inetsoft.uql.asset.sync;

import inetsoft.storage.LoadKeyValueTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Loads the cluster-global rename queue store and, on the first load after a full cluster start,
 * replays the rename transform tasks that the previous run left in the queue.
 * <p>
 * The replay runs only when the replicated map of the store was empty before the load, i.e. for
 * the first loader since the cluster started. A second node opening the store, or the store being
 * evicted from {@link inetsoft.storage.KeyValueStorageManager} and opened again, finds the map
 * populated and does not replay the tasks again. A consequence is that a task whose node died in
 * the middle of its transform on a running cluster is replayed only at the next full cluster
 * start.
 * <p>
 * This task runs on the {@value DependencyStorageService#QUEUE_STORE} singleton service, like
 * every other change of the queue, so it reads and writes the engine and map directly.
 */
public class LoadRenameQueueTask extends LoadKeyValueTask<RenameTransformObject> {
   public LoadRenameQueueTask() {
      super(DependencyStorageService.QUEUE_STORE);
   }

   @Override
   public void run() {
      boolean firstLoad = getMap().isEmpty();
      super.run();

      if(firstLoad) {
         try {
            dropLegacyQueue();
            replayQueue();
         }
         catch(Exception e) {
            LOG.error("Failed to replay the rename transform queue", e);
         }
      }
   }

   /**
    * Logs and removes the queue written by older versions. Those versions never replayed it, so
    * its tasks may be years old; replaying them now could rewrite assets that were since
    * repaired or re-created under the old name.
    */
   private void dropLegacyQueue() {
      Object legacy = getEngine().get(getId(), DependencyStorageService.LEGACY_QUEUE_KEY);

      if(legacy == null) {
         return;
      }

      if(legacy instanceof RenameTransformQueue queue) {
         for(RenameDependencyInfo info : queue) {
            LOG.warn("Dropping a rename transform task queued by an older version, which was " +
                        "never applied. Repeat the rename if references to the old name remain: {}",
                     RenameTransformTask.describe(info));
         }
      }

      getEngine().remove(getId(), DependencyStorageService.LEGACY_QUEUE_KEY);
      getMap().remove(DependencyStorageService.LEGACY_QUEUE_KEY);
   }

   private void replayQueue() {
      RenameTransformQueue queue = getEngine().get(getId(), DependencyStorageService.QUEUE_KEY);
      RenameTransformAttempts attempts =
         getEngine().get(getId(), DependencyStorageService.ATTEMPTS_KEY);

      if(queue == null || queue.isEmpty()) {
         if(attempts != null && !attempts.isEmpty()) {
            RenameTransformTask.putQueue(getEngine(), getMap(), new RenameTransformQueue(),
                                         new RenameTransformAttempts());
         }

         return;
      }

      if(attempts == null) {
         attempts = new RenameTransformAttempts();
      }

      List<RenameDependencyInfo> replay = new ArrayList<>();
      Set<String> queued = new HashSet<>();

      for(RenameDependencyInfo info : new ArrayList<>(queue)) {
         int count = attempts.get(info.getTaskId());

         if(count >= RenameTransformAttempts.MAX_ATTEMPTS) {
            LOG.warn("Dropping a rename transform task that was started {} times without " +
                        "finishing. Repeat the rename if references to the old name remain: {}",
                     count, RenameTransformTask.describe(info));
            queue.remove(info);
         }
         else {
            // counted before the task is started, so a task that kills the server is counted
            attempts.set(info.getTaskId(), count + 1);
            replay.add(info);
            queued.add(info.getTaskId());
         }
      }

      attempts.retainAll(queued);
      RenameTransformTask.putQueue(getEngine(), getMap(), queue, attempts);

      for(RenameDependencyInfo info : replay) {
         LOG.info("Replaying rename transform task left in the queue: {}",
                  RenameTransformTask.describe(info));
         getCluster().submit("renameTransform", new RenameTransformTask.Rename(info));
      }
   }

   private static final Logger LOG = LoggerFactory.getLogger(LoadRenameQueueTask.class);
}
