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
import inetsoft.sree.internal.cluster.SingletonRunnableTask;
import inetsoft.sree.security.Organization;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.storage.KeyValueEngine;
import inetsoft.storage.KeyValueTask;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.util.ConfigurationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Adds a rename transform task to the cluster-global rename queue and starts it. Runs on the
 * {@value DependencyStorageService#QUEUE_STORE} singleton service, which serializes every change
 * of the queue. Tasks on that service read and write the engine and map directly and must never
 * open the store through {@link inetsoft.storage.KeyValueStorageManager}: opening it submits its
 * load task to this same service and waits for it. For the same reason nothing that runs on the
 * {@code renameTransform} service may wait on the queue store: a waiting
 * {@code RenameTransformTask} holds this service until its {@link Rename} finishes.
 */
public class RenameTransformTask
   extends KeyValueTask<RenameTransformObject> implements SingletonRunnableTask
{
   public RenameTransformTask(RenameDependencyInfo info, boolean waitDone) {
      super(DependencyStorageService.QUEUE_STORE);
      this.info = info;
      this.waitDone = waitDone;
   }

   public RenameTransformTask(RenameDependencyInfo info) {
      this(info, false);
   }

   @Override
   public void run() {
      // replay the queue left by the previous cluster run before the first change of this run
      LoadRenameQueueTask.replayIfFirst(getEngine(), getMap(), getCluster());
      RenameTransformQueue queue = getEngine().get(getId(), DependencyStorageService.QUEUE_KEY);
      RenameTransformAttempts attempts =
         getEngine().get(getId(), DependencyStorageService.ATTEMPTS_KEY);

      if(queue == null) {
         queue = new RenameTransformQueue();
      }

      if(attempts == null) {
         attempts = new RenameTransformAttempts();
      }

      queue.add(info);
      attempts.set(info.getTaskId(), 1);
      putQueue(getEngine(), getMap(), queue, attempts);
      LOG.debug("Rename transform task added to queue: {}", info.getTaskId());
      Future<?> renameTransform = getCluster().submit("renameTransform", new Rename(info));

      if(waitDone) {
         try {
            renameTransform.get(3L, TimeUnit.MINUTES);
         }
         catch(Exception e) {
            LOG.error("wait renameTransform failure", e);
         }
      }
   }

   /**
    * Writes the rename queue and the attempt counts to the engine and to the replicated map, so
    * that {@link DependencyStorageService#getQueue()} sees the change on every node.
    */
   static void putQueue(KeyValueEngine engine, Map<String, RenameTransformObject> map,
                        RenameTransformQueue queue, RenameTransformAttempts attempts)
   {
      engine.put(DependencyStorageService.QUEUE_STORE, DependencyStorageService.QUEUE_KEY, queue);
      map.put(DependencyStorageService.QUEUE_KEY, queue);
      engine.put(DependencyStorageService.QUEUE_STORE, DependencyStorageService.ATTEMPTS_KEY,
                 attempts);
      map.put(DependencyStorageService.ATTEMPTS_KEY, attempts);
   }

   /**
    * Describes a task for the log: the renames (old and new name, organization) and the ids of
    * the dependent assets.
    */
   static String describe(RenameDependencyInfo info) {
      String renames = info.getRenameInfos() == null ? "[]" : info.getRenameInfos().stream()
         .filter(Objects::nonNull)
         .map(r -> r.getOldName() + " -> " + r.getNewName() + " (organization " +
            r.getOrganizationId() + ")")
         .collect(Collectors.joining(", ", "[", "]"));
      String dependents = Arrays.stream(info.getAssetObjects())
         .map(RenameTransformTask::getAssetId)
         .collect(Collectors.joining(", ", "[", "]"));
      return "task " + info.getTaskId() + ", renames " + renames + ", dependents " + dependents;
   }

   private static String getAssetId(AssetObject obj) {
      return obj instanceof AssetEntry entry ? entry.toIdentifier() : String.valueOf(obj);
   }

   /**
    * Gets an organization of a task that no longer exists.
    *
    * @return the organization id, or {@code null} if all of them exist or the organizations
    *         cannot be listed.
    */
   static String getRemovedOrganization(RenameDependencyInfo info) {
      if(info.getRenameInfos() == null) {
         return null;
      }

      Set<String> orgs = info.getRenameInfos().stream()
         .filter(Objects::nonNull)
         .map(RenameInfo::getOrganizationId)
         .filter(Objects::nonNull)
         .collect(Collectors.toSet());

      if(orgs.isEmpty()) {
         return null;
      }

      Set<String> existing = new HashSet<>();

      try {
         String[] ids = SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs();

         // nothing listed means the provider can't tell, not that every organization is gone
         if(ids == null || ids.length == 0) {
            return null;
         }

         existing.addAll(Arrays.asList(ids));
      }
      catch(Exception e) {
         LOG.debug("Failed to list the organizations, transforming the rename anyway", e);
         return null;
      }

      existing.add(Organization.getDefaultOrganizationID());
      existing.add(Organization.getSelfOrganizationID());

      for(String org : orgs) {
         if(!existing.contains(org)) {
            return org;
         }
      }

      return null;
   }

   /**
    * Gets a rename of a replayed task that no longer applies: the asset with the old name exists
    * again, for example because it was renamed back, or re-created or imported under the old name,
    * after the task was queued. Replaying such a task would bind the dependents to the new name
    * again.
    * <p>
    * Only whole repository assets (worksheets, viewsheets, folders and the like, named by their
    * identifiers) are judged. Other renames, a rename whose names differ only in case, and an
    * asset that can't be looked up count as still applying, so the task is never dropped on a
    * guess.
    *
    * @return the old name of the rename that no longer applies, or {@code null} if the task
    *         applies.
    */
   static String getStaleRename(RenameDependencyInfo info) {
      return getStaleRename(info, RenameTransformTask::assetExists);
   }

   static String getStaleRename(RenameDependencyInfo info, Predicate<AssetEntry> exists) {
      if(info.getRenameInfos() == null) {
         return null;
      }

      for(RenameInfo rinfo : info.getRenameInfos()) {
         AssetEntry oldEntry = rinfo == null ? null : getRenamedAsset(rinfo);

         try {
            if(oldEntry != null && exists.test(oldEntry)) {
               return rinfo.getOldName();
            }
         }
         catch(Exception e) {
            LOG.debug("Failed to look up {}, transforming the rename anyway", oldEntry, e);
         }
      }

      return null;
   }

   /**
    * Gets the asset that a rename renamed, if it is a whole repository asset.
    *
    * @return the asset with the old name, or {@code null} if the rename can't be judged.
    */
   private static AssetEntry getRenamedAsset(RenameInfo rinfo) {
      String oname = rinfo.getOldName();
      String nname = rinfo.getNewName();

      if(rinfo.isTable() || rinfo.isColumn() || oname == null || nname == null ||
         oname.equalsIgnoreCase(nname) || oname.indexOf('^') < 0 || nname.indexOf('^') < 0)
      {
         return null;
      }

      try {
         // the identifier carries the organization; the rename's is used if it doesn't
         AssetEntry oentry = AssetEntry.createAssetEntry(oname, rinfo.getOrganizationId());
         AssetEntry nentry = AssetEntry.createAssetEntry(nname, rinfo.getOrganizationId());

         if(oentry == null || nentry == null || oentry.getType() == null ||
            oentry.isRoot() || oentry.getOrgID() == null || !isRepositoryScope(oentry) ||
            !isRepositoryScope(nentry))
         {
            return null;
         }

         return oentry;
      }
      catch(Exception e) {
         return null;
      }
   }

   private static boolean isRepositoryScope(AssetEntry entry) {
      return entry.getScope() == AssetRepository.GLOBAL_SCOPE ||
         entry.getScope() == AssetRepository.USER_SCOPE;
   }

   private static boolean assetExists(AssetEntry entry) {
      try {
         AssetRepository repository = AssetUtil.getAssetRepository(false);
         return repository != null && repository.containsEntry(entry);
      }
      catch(Exception e) {
         // can't tell, so the rename is transformed
         LOG.debug("Failed to look up {}, transforming the rename anyway", entry, e);
         return false;
      }
   }

   /**
    * Determines if this node is stopping: the thread was interrupted (the cluster service is being
    * cancelled) or the Spring context is closing. A context is marked closed before any of its
    * beans, such as the storage, is destroyed.
    */
   static boolean isNodeStopping() {
      if(Thread.currentThread().isInterrupted()) {
         return true;
      }

      try {
         ApplicationContext context = ConfigurationContext.getContext().getApplicationContext();
         return context == null ||
            context instanceof ConfigurableApplicationContext configurable &&
               configurable.isClosed();
      }
      catch(Exception e) {
         return true;
      }
   }

   private final RenameDependencyInfo info;
   private boolean waitDone = false;
   private static final Logger LOG = LoggerFactory.getLogger(RenameTransformTask.class);

   public static final class Rename implements SingletonRunnableTask {
      public Rename(RenameDependencyInfo info) {
         this(info, false);
      }

      /**
       * @param replayed {@code true} if the task is replayed from the queue that an earlier
       *                 cluster run left. Such a task is skipped if it no longer applies.
       */
      public Rename(RenameDependencyInfo info, boolean replayed) {
         this.info = info;
         this.replayed = replayed;
      }

      @Override
      public void run() {
         run(DependencyTransformer::renameDep, RenameTransformTask::isNodeStopping);
      }

      /**
       * Runs the task.
       *
       * @param transform rewrites the dependents, returns {@code true} if all were rewritten.
       * @param stopping  tells if this node is stopping.
       */
      void run(Predicate<RenameDependencyInfo> transform, BooleanSupplier stopping) {
         LOG.debug("Rename transform task started: {}", info.getTaskId());
         boolean done = false;

         try {
            String removedOrg = getRemovedOrganization(info);

            if(removedOrg != null) {
               LOG.warn("Skipping a rename transform task of organization {}, which no " +
                           "longer exists: {}", removedOrg, describe(info));
               done = true;
               return;
            }

            String stale = replayed ? getStaleRename(info) : null;

            if(stale != null) {
               LOG.warn("Skipping a replayed rename transform task, {} exists again (renamed " +
                           "back, re-created or imported after the task was queued): {}",
                        stale, describe(info));
               done = true;
               return;
            }

            // The index moves first, so that the dependents rewritten before a failure or stop
            // are found under the new name by a later rename. The rewrite works from the task's
            // own list of dependents and changes only the values equal to the old name, so a
            // replay finishes it, and its index move finds nothing to move.
            if(info.isUpdateStorage()) {
               for(RenameInfo rinfo : info.getRenameInfos()) {
                  DependencyTransformer.renameDepStorage(rinfo);
               }
            }

            done = transform.test(info);
         }
         finally {
            // A task that did not finish because this node is stopping stays in the queue, to be
            // replayed at the next cluster start. On a running node it is dequeued even if the
            // transform failed, so that it neither waits for the next start nor keeps
            // waitUntilRenameFinished() waiting.
            if(done || !stopping.getAsBoolean()) {
               if(!done) {
                  LOG.warn("Rename transform task did not finish, repeat the rename if " +
                              "references to the old name remain: {}", describe(info));
               }

               getServiceBean(Cluster.class)
                  .submit(DependencyStorageService.QUEUE_STORE, new Remove(info));
               LOG.debug("Rename transform task finished: {}", info.getTaskId());
            }
            else {
               LOG.warn("Rename transform task stopped before it finished, it stays in the " +
                           "queue to be replayed at the next cluster start: {}", describe(info));
            }
         }
      }

      private final RenameDependencyInfo info;
      private final boolean replayed;
   }

   public static final class Remove
      extends KeyValueTask<RenameTransformObject> implements SingletonRunnableTask
   {
      public Remove(RenameDependencyInfo info) {
         super(DependencyStorageService.QUEUE_STORE);
         this.info = info;
      }

      @Override
      public void run() {
         LoadRenameQueueTask.replayIfFirst(getEngine(), getMap(), getCluster());
         RenameTransformQueue queue = getEngine().get(getId(), DependencyStorageService.QUEUE_KEY);
         RenameTransformAttempts attempts =
            getEngine().get(getId(), DependencyStorageService.ATTEMPTS_KEY);

         if(queue == null) {
            queue = new RenameTransformQueue();
         }

         if(attempts == null) {
            attempts = new RenameTransformAttempts();
         }

         queue.remove(info);
         attempts.remove(info.getTaskId());
         putQueue(getEngine(), getMap(), queue, attempts);
         LOG.debug("Rename transform task removed from queue: {}", info.getTaskId());
      }

      private final RenameDependencyInfo info;
   }
}
