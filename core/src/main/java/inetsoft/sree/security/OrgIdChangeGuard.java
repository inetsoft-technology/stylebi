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
package inetsoft.sree.security;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

/**
 * Cluster-wide record of an organization whose id is being changed (Bug #79856). The id change
 * copies the old organization's assets, registry and data space from one-shot snapshots and then
 * removes the old organization, so a write into the old organization from any node in between
 * is acknowledged and then lost. While the record is active, the shared write chokepoints
 * (every organization scoped blob store, the org scoped paths of the data space, the dashboards
 * store) refuse writes into the old organization with {@link OrganizationChangingException}. The
 * new id is refused too, until the change completes, because the copy overwrites what is written
 * there meanwhile.
 * <p>
 * The record is kept in a replicated map, which a put updates on every node before it returns.
 * The node running the change renews its records every HEARTBEAT_PERIOD, and a record that is not
 * renewed for ACTIVE_TIMEOUT (the node crashed) is dropped with a warning, so that it can't block
 * the organization forever. After a completed change the old id stays "retired" for a while, so
 * that a node that still holds the old organization's registry can't recreate its files; it is
 * writable again as soon as an organization of that id exists.
 */
public final class OrgIdChangeGuard {
   private OrgIdChangeGuard() {
   }

   /**
    * Marks the organization as being changed. The calling thread, which runs the change, and the
    * threads that adopt its snapshot are exempt from the refusal until {@link #end}.
    */
   public static void begin(String orgId) {
      begin(orgId, null);
   }

   /**
    * Marks the old organization, and the new one when it is a different id, as being changed.
    */
   public static void begin(String oldId, String newId) {
      if(oldId == null) {
         return;
      }

      String opId = UUID.randomUUID().toString();
      String oldKey = key(oldId);
      Set<String> oldIds = new LinkedHashSet<>(List.of(oldId));
      List<String> keys = new ArrayList<>(List.of(oldKey));
      DistributedMap<String, Record> map = getMap();
      boolean caseOnly = newId != null && key(newId).equals(oldKey);

      if(caseOnly) {
         oldIds.add(newId);
      }

      map.put(oldKey, new Record(opId, System.currentTimeMillis(), true, oldIds));

      if(newId != null && !caseOnly) {
         map.put(key(newId), new Record(opId, System.currentTimeMillis(), true, Set.of(newId)));
         keys.add(key(newId));
      }

      Operation op = new Operation(opId, keys, caseOnly);
      OPERATIONS.put(oldKey, op);

      for(String k : keys) {
         OWNER.get().put(k, opId);
      }

      op.heartbeat = HEARTBEAT.scheduleAtFixedRate(() -> renew(op), HEARTBEAT_PERIOD,
                                                   HEARTBEAT_PERIOD, TimeUnit.MILLISECONDS);
   }

   /**
    * Ends the change started by {@link #begin}.
    *
    * @param completed true if the old organization was removed, which keeps writes into its id
    *                  refused for RETIRED_TIMEOUT; false if the change failed and the
    *                  organization stays in use.
    */
   public static void end(String orgId, boolean completed) {
      if(orgId == null) {
         return;
      }

      String oldKey = key(orgId);
      Operation op = OPERATIONS.remove(oldKey);

      if(op == null) {
         return;
      }

      op.heartbeat.cancel(false);
      DistributedMap<String, Record> map = getMap();

      for(String k : op.keys) {
         OWNER.get().remove(k);
         Record record = map.get(k);

         if(record != null && op.id.equals(record.opId)) {
            map.remove(k);
         }
      }

      // a change that only differs in letter case keeps the id, there is nothing to retire
      if(completed && !op.caseOnly) {
         map.put(oldKey, new Record(op.id, System.currentTimeMillis(), false, Set.of(orgId)));
      }
   }

   /**
    * Gets the changes the calling thread runs, to hand to the threads that do part of its work,
    * see {@link #adopt}. A thread that is not given it is not exempt.
    */
   public static Map<String, String> snapshot() {
      return new HashMap<>(OWNER.get());
   }

   /**
    * Makes the calling thread part of the changes of a thread that handed it a {@link #snapshot()}.
    */
   public static void adopt(Map<String, String> owner) {
      OWNER.get().putAll(owner);
   }

   private static void renew(Operation op) {
      try {
         DistributedMap<String, Record> map = getMap();

         for(String k : op.keys) {
            Record record = map.get(k);

            if(record != null && op.id.equals(record.opId) && record.active) {
               map.put(k, new Record(op.id, System.currentTimeMillis(), true, record.ids));
            }
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to renew the id change record of an organization", e);
      }
   }

   /**
    * Forgets a retired record, because the id is used by a new organization.
    */
   public static void clear(String orgId) {
      if(orgId != null) {
         Record record = getMap().get(key(orgId));

         // the record of a running change is only removed by its end
         if(record != null && !record.active) {
            getMap().remove(key(orgId));
         }
      }
   }

   public static boolean isBlocked(String orgId) {
      if(orgId == null || orgId.isEmpty()) {
         return false;
      }

      String key = key(orgId);
      return isBlocked(key, getMap().get(key));
   }

   private static boolean isBlocked(String key, Record record) {
      if(record == null) {
         return false;
      }

      long age = System.currentTimeMillis() - record.time;

      if(record.active) {
         if(age > ACTIVE_TIMEOUT) {
            LOG.warn("The id change record of organization {} was not renewed for {} ms, the " +
                     "node that ran the change is gone. Writes into it are accepted again.",
                     key, age);
            getMap().remove(key);
            return false;
         }

         return true;
      }

      // a retired id is writable again once an organization of that id exists
      return age <= RETIRED_TIMEOUT && !exists(key);
   }

   private static boolean exempt(String key, Record record) {
      return record.active && record.opId.equals(OWNER.get().get(key));
   }

   private static boolean exists(String orgId) {
      try {
         String[] ids = SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs();

         if(ids != null) {
            for(String id : ids) {
               if(orgId.equalsIgnoreCase(id)) {
                  return true;
               }
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to check whether organization {} exists, keeping the refusal", orgId, e);
      }

      return false;
   }

   /**
    * Refuses a write into an organization whose id is being changed, or was changed just now.
    *
    * @throws OrganizationChangingException if the write must be refused.
    */
   public static void checkWritable(String orgId) {
      if(orgId == null || orgId.isEmpty()) {
         return;
      }

      DistributedMap<String, Record> map = getMap();

      if(map.isEmpty()) {
         return;
      }

      String key = key(orgId);
      Record record = map.get(key);

      if(record != null && isBlocked(key, record) && !exempt(key, record)) {
         throw new OrganizationChangingException(orgId);
      }
   }

   /**
    * Refuses a write to a data space path that belongs to an organization whose id is being
    * changed, see {@link OrgScopedPaths}.
    *
    * @throws OrganizationChangingException if the write must be refused.
    */
   public static void checkWritablePath(String path) {
      if(path == null) {
         return;
      }

      DistributedMap<String, Record> map = getMap();

      if(map.isEmpty()) {
         return;
      }

      for(Map.Entry<String, Record> entry : new ArrayList<>(map.entrySet())) {
         Record record = entry.getValue();

         if(record == null || !isBlocked(entry.getKey(), record) ||
            exempt(entry.getKey(), record))
         {
            continue;
         }

         for(String id : record.ids) {
            if(OrgScopedPaths.isOrgScopedPath(path, id)) {
               throw new OrganizationChangingException(id);
            }
         }
      }
   }

   private static String key(String orgId) {
      return orgId.toLowerCase();
   }

   private static synchronized DistributedMap<String, Record> getMap() {
      Cluster cluster = Cluster.getInstance();

      if(cluster != mapCluster || map == null) {
         map = cluster.getReplicatedMap(MAP_NAME);
         mapCluster = cluster;
      }

      return map;
   }

   private static DistributedMap<String, Record> map;
   private static Cluster mapCluster;
   private static final Logger LOG = LoggerFactory.getLogger(OrgIdChangeGuard.class);
   private static final String MAP_NAME = OrgIdChangeGuard.class.getName() + ".orgIdChanges";
   private static final long HEARTBEAT_PERIOD = TimeUnit.SECONDS.toMillis(30);
   // a record not renewed for this long belongs to a node that is gone
   private static final long ACTIVE_TIMEOUT = TimeUnit.MINUTES.toMillis(3);
   private static final long RETIRED_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
   private static final Map<String, Operation> OPERATIONS = new ConcurrentHashMap<>();
   private static final ScheduledExecutorService HEARTBEAT =
      Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "OrgIdChangeGuardHeartbeat");
         t.setDaemon(true);
         return t;
      });
   // org key -> id of the change the thread belongs to
   private static final ThreadLocal<Map<String, String>> OWNER =
      ThreadLocal.withInitial(HashMap::new);

   private static final class Operation {
      Operation(String id, List<String> keys, boolean caseOnly) {
         this.id = id;
         this.keys = keys;
         this.caseOnly = caseOnly;
      }

      final String id;
      final List<String> keys;
      final boolean caseOnly;
      volatile ScheduledFuture<?> heartbeat;
   }

   private static final class Record implements Serializable {
      Record(String opId, long time, boolean active, Set<String> ids) {
         this.opId = opId;
         this.time = time;
         this.active = active;
         this.ids = new LinkedHashSet<>(ids);
      }

      final String opId;
      final long time;
      final boolean active;
      // the spellings of the organization id
      final LinkedHashSet<String> ids;
   }

   /**
    * Thrown for a write into an organization whose id is being changed. The write was not made.
    */
   public static final class OrganizationChangingException extends IllegalStateException {
      public OrganizationChangingException(String orgId) {
         super("The ID of organization \"" + orgId + "\" is being changed. The write was not " +
               "made, retry it after the change completes, using the new organization ID.");
      }
   }
}
