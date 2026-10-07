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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Adds a rename transform task to the cluster-global rename queue and starts it. Runs on the
 * {@value DependencyStorageService#QUEUE_STORE} singleton service, which serializes every change
 * of the queue. Tasks on that service read and write the engine and map directly and must never
 * open the store through {@link inetsoft.storage.KeyValueStorageManager}: opening it submits its
 * load task to this same service and waits for it.
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

   private final RenameDependencyInfo info;
   private boolean waitDone = false;
   private static final Logger LOG = LoggerFactory.getLogger(RenameTransformTask.class);

   public static final class Rename implements SingletonRunnableTask {
      public Rename(RenameDependencyInfo info) {
         this.info = info;
      }

      @Override
      public void run() {
         LOG.debug("Rename transform task started: {}", info.getTaskId());

         try {
            String removedOrg = getRemovedOrganization(info);

            if(removedOrg != null) {
               LOG.warn("Skipping a rename transform task of organization {}, which no " +
                           "longer exists: {}", removedOrg, describe(info));
               return;
            }

            DependencyTransformer.renameDep(info);

            if(info.isUpdateStorage()) {
               for(RenameInfo rinfo : info.getRenameInfos()) {
                  DependencyTransformer.renameDepStorage(rinfo);
               }
            }
         }
         finally {
            // Dequeued after the transform, so that a server stopping in the middle of it leaves
            // the task in the queue, to be replayed at the next cluster start. An exception from
            // the transform still dequeues it.
            getServiceBean(Cluster.class)
               .submit(DependencyStorageService.QUEUE_STORE, new Remove(info));
            LOG.debug("Rename transform task finished: {}", info.getTaskId());
         }
      }

      private final RenameDependencyInfo info;
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
