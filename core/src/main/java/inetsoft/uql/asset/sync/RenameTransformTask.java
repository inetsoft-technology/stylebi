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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
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

      chainPendingRenames(info, queue);
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
    * Chains a new rename to the pending (queued, not yet finished) renames of the same asset.
    * <p>
    * A pending rename A to B may not have rewritten its dependents yet, for example because a
    * node was stopped, so they still name A. A later rename B to C rewrites only the values equal
    * to B, leaves them on A, and the replay of A to B then binds them to B, which no longer
    * exists. So for every dependent of a pending task that renames to the old name of this task,
    * this task also gets the rename of A to C, and the dependents the pending task has but this
    * task found none (the index may not have been moved yet) are added with both renames. If the
    * dependency index was not moved either, it is moved from A to C. The pending task stays
    * queued, its replay finds nothing left to rewrite.
    * <p>
    * Only plain renames are chained, see {@link #isChainable(RenameInfo, RenameInfo)}; anything
    * else is left as it is. A failure leaves the task unchanged.
    */
   static void chainPendingRenames(RenameDependencyInfo info, RenameTransformQueue queue) {
      if(info.getRenameInfos() == null || queue == null || queue.isEmpty()) {
         return;
      }

      try {
         // work on copies so that a failure in the middle leaves the task unchanged
         Map<AssetObject, List<RenameInfo>> merged = new LinkedHashMap<>();
         List<RenameInfo> moves = new ArrayList<>();
         boolean ownDependents = !info.getDependencyMap().isEmpty();

         for(RenameDependencyInfo pending : queue) {
            if(pending == null || pending.equals(info) || pending.getDependencyMap() == null) {
               continue;
            }

            for(Map.Entry<AssetObject, List<RenameInfo>> e : pending.getDependencyMap().entrySet()) {
               for(RenameInfo prior : e.getValue() == null ? List.<RenameInfo>of() : e.getValue()) {
                  for(RenameInfo next : info.getRenameInfos()) {
                     if(!isChainable(prior, next)) {
                        continue;
                     }

                     RenameInfo chained = next.copyWithOldName(prior.getOldName());
                     List<RenameInfo> list = merged.computeIfAbsent(
                        e.getKey(), k -> new ArrayList<>());

                     if(!containsRename(list, chained)) {
                        list.add(chained);
                     }

                     if(!ownDependents && !containsRename(moves, chained)) {
                        moves.add(chained);
                     }
                  }
               }
            }
         }

         if(merged.isEmpty()) {
            return;
         }

         for(Map.Entry<AssetObject, List<RenameInfo>> e : merged.entrySet()) {
            List<RenameInfo> list = new ArrayList<>(info.getRenameInfo(e.getKey()));

            if(list.isEmpty()) {
               list.addAll(info.getRenameInfos());
            }

            for(RenameInfo rinfo : e.getValue()) {
               if(!containsRename(list, rinfo)) {
                  list.add(rinfo);
               }
            }

            info.setRenameInfo(e.getKey(), list);
         }

         // the index is still under the first name when this task found no dependents of its own
         List<RenameInfo> rinfos = new ArrayList<>(info.getRenameInfos());
         moves.stream().filter(m -> !containsRename(rinfos, m)).forEach(rinfos::add);
         info.setRenameInfos(rinfos);
         LOG.debug("Rename transform task {} chained to pending renames: {}",
                   info.getTaskId(), describe(info));
      }
      catch(Exception e) {
         LOG.warn("Failed to chain the rename transform task to the pending renames: {}",
                  info.getTaskId(), e);
      }
   }

   /**
    * Determines if a rename of the old name of {@code next} to the new name of {@code next}
    * continues the pending rename {@code prior}: {@code prior} renamed an asset to the name that
    * {@code next} renames. Only plain renames of a whole asset of the same kind, source and
    * organization are chained, and not a rename back to the first name.
    */
   private static boolean isChainable(RenameInfo prior, RenameInfo next) {
      if(prior == null || next == null || prior.getClass() != RenameInfo.class ||
         next.getClass() != RenameInfo.class || prior.getType() != next.getType() ||
         prior.isTable() || prior.isColumn() || prior.isFolder() || prior.isTableOption() ||
         prior.getOldName() == null || prior.getNewName() == null ||
         next.getOldName() == null || next.getNewName() == null ||
         !prior.getNewName().equals(next.getOldName()) ||
         prior.getOldName().equals(next.getNewName()) ||
         prior.getOldName().equals(next.getOldName()))
      {
         return false;
      }

      return Objects.equals(prior.getOrganizationId(), next.getOrganizationId()) &&
         Objects.equals(prior.getSource(), next.getSource()) &&
         Objects.equals(prior.getTable(), next.getTable()) &&
         Objects.equals(prior.getEntity(), next.getEntity()) &&
         Objects.equals(prior.getOldEntity(), next.getOldEntity()) &&
         Objects.equals(prior.getPrefix(), next.getPrefix()) &&
         prior.isRest() == next.isRest() &&
         prior.getParentRenameInfo() == null && next.getParentRenameInfo() == null &&
         prior.getOldPath() == null && prior.getNewPath() == null &&
         next.getOldPath() == null && next.getNewPath() == null;
   }

   private static boolean containsRename(List<RenameInfo> list, RenameInfo rinfo) {
      return list.stream().anyMatch(r -> r != null && r.getType() == rinfo.getType() &&
         Objects.equals(r.getOldName(), rinfo.getOldName()) &&
         Objects.equals(r.getNewName(), rinfo.getNewName()) &&
         Objects.equals(r.getOrganizationId(), rinfo.getOrganizationId()));
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
         run(info -> DependencyTransformer.renameDepAsync(info, 50L, TimeUnit.SECONDS),
             RenameTransformTask::isNodeStopping);
      }

      /**
       * Runs the task.
       *
       * @param transform rewrites the dependents. Its result tells if all were rewritten; it is
       *                  not done yet if the rewrite goes on in the background, and then the task
       *                  is dequeued, or kept, when the rewrite ends.
       * @param stopping  tells if this node is stopping.
       */
      void run(Function<RenameDependencyInfo, CompletableFuture<Boolean>> transform,
               BooleanSupplier stopping)
      {
         LOG.debug("Rename transform task started: {}", info.getTaskId());
         boolean done = false;
         boolean background = false;

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

            CompletableFuture<Boolean> result = transform.apply(info);

            if(result.isDone()) {
               done = !result.isCompletedExceptionally() && Boolean.TRUE.equals(result.join());
            }
            else {
               // The rewrite takes longer than the wait and goes on in the background. This
               // task returns, so neither the queue store's service nor a waiting caller is held
               // longer than before, but the task stays in the queue until the rewrite ends; a
               // stop before then leaves it to be replayed.
               background = true;
               result.whenComplete((complete, error) -> {
                  try {
                     finish(error == null && Boolean.TRUE.equals(complete), stopping);
                  }
                  catch(Exception e) {
                     LOG.error("Failed to finish the rename transform task: {}",
                               describe(info), e);
                  }
               });
            }
         }
         finally {
            if(!background) {
               finish(done, stopping);
            }
         }
      }

      /**
       * Dequeues the task, unless it did not finish because this node is stopping: then it stays
       * in the queue, to be replayed at the next cluster start. On a running node it is dequeued
       * even if the transform failed, so that it neither waits for the next start nor keeps
       * waitUntilRenameFinished() waiting.
       */
      private void finish(boolean done, BooleanSupplier stopping) {
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
