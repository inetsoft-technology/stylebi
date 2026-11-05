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
 * The record is kept in a replicated map, which a put updates on every node before it returns,
 * and which every check reads locally by key. The record names the node that runs the change.
 * A reader never removes an active record: it only treats it as stale when that node has left
 * the cluster, so a clock difference or a stalled heartbeat can't drop the guard of a running
 * change. The running node re-puts its records every HEARTBEAT_PERIOD, so that a record that was
 * lost for any reason comes back. A change is never given up while its node runs, so a hung
 * change keeps both ids refused (a warning is logged), which is the safe side of the trade.
 * After a completed change the old id stays "retired" for a while, so that a node that still
 * holds the old organization's registry can't recreate its files; it is writable again as soon
 * as an organization of that id exists.
 */
public final class OrgIdChangeGuard {
   private OrgIdChangeGuard() {
   }

   /**
    * Marks the organization as being changed. The calling thread, which runs the change, and the
    * threads that adopt its snapshot are exempt from the refusal until {@link #end}.
    */
   public static String begin(String orgId) {
      return begin(orgId, null);
   }

   /**
    * Marks the old organization, and the new one when it is a different id, as being changed.
    *
    * @return the id of the change, for {@link #end(String, String, boolean)}.
    */
   public static String begin(String oldId, String newId) {
      if(oldId == null) {
         return null;
      }

      String opId = UUID.randomUUID().toString();
      String oldKey = key(oldId);
      Set<String> oldIds = new LinkedHashSet<>(List.of(oldId));
      Map<String, Record> records = new LinkedHashMap<>();
      boolean caseOnly = newId != null && key(newId).equals(oldKey);
      String node = Cluster.getInstance().getLocalNodeId();

      if(caseOnly) {
         oldIds.add(newId);
      }

      records.put(oldKey, new Record(opId, node, System.currentTimeMillis(), true, oldIds));

      if(newId != null && !caseOnly) {
         records.put(key(newId),
                     new Record(opId, node, System.currentTimeMillis(), true, Set.of(newId)));
      }

      DistributedMap<String, Record> map = getMap();

      try {
         for(Map.Entry<String, Record> e : records.entrySet()) {
            map.put(e.getKey(), e.getValue());
         }
      }
      catch(RuntimeException e) {
         // leave nothing behind that nothing would renew or remove
         for(Map.Entry<String, Record> r : records.entrySet()) {
            try {
               map.remove(r.getKey());
            }
            catch(RuntimeException ignore) {
            }
         }

         throw e;
      }

      Operation op = new Operation(opId, oldKey, records, caseOnly);
      OPERATIONS.put(opId, op);

      for(String k : records.keySet()) {
         OWNER.get().put(k, opId);
      }

      op.heartbeat = HEARTBEAT.scheduleAtFixedRate(() -> renew(op), HEARTBEAT_PERIOD,
                                                   HEARTBEAT_PERIOD, TimeUnit.MILLISECONDS);
      return opId;
   }

   /**
    * Ends the change that {@link #begin(String)} started for the old id, the latest of them if
    * several are running.
    */
   public static void end(String orgId, boolean completed) {
      if(orgId == null) {
         return;
      }

      String oldKey = key(orgId);

      for(Operation op : new ArrayList<>(OPERATIONS.values())) {
         if(op.oldKey.equals(oldKey)) {
            end(op.id, orgId, completed);
            return;
         }
      }
   }

   /**
    * Ends the change started by {@link #begin}.
    *
    * @param opId      the id that begin returned.
    * @param completed true if the old organization was removed, which keeps writes into its id
    *                  refused for RETIRED_TIMEOUT; false if the change failed and the
    *                  organization stays in use.
    */
   public static void end(String opId, String orgId, boolean completed) {
      Operation op = opId == null ? null : OPERATIONS.remove(opId);

      if(op == null) {
         return;
      }

      op.heartbeat.cancel(false);
      DistributedMap<String, Record> map = getMap();

      for(String k : op.records.keySet()) {
         OWNER.get().remove(k);
         Record record = map.get(k);

         if(record != null && op.id.equals(record.opId)) {
            map.remove(k);
         }
      }

      // a change that only differs in letter case keeps the id, there is nothing to retire
      if(completed && !op.caseOnly) {
         map.put(op.oldKey, new Record(op.id, null, System.currentTimeMillis(), false,
                                       Set.of(orgId)));
      }
   }

   private static void renew(Operation op) {
      try {
         DistributedMap<String, Record> map = getMap();
         long now = System.currentTimeMillis();

         // put unconditionally, a record that is gone is restored
         for(Map.Entry<String, Record> e : op.records.entrySet()) {
            Record r = e.getValue();
            map.put(e.getKey(), new Record(op.id, r.node, now, true, r.ids));
         }

         if(now - op.started > HUNG_WARNING && ++op.renewals % 20 == 0) {
            LOG.warn("The id change of organization {} has run for {} min and still refuses " +
                     "writes into it.", op.oldKey, (now - op.started) / 60000);
         }
      }
      catch(Exception e) {
         LOG.warn("Failed to renew the id change record of an organization", e);
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

      if(record.active) {
         if(record.node != null && !Cluster.getInstance().getClusterNodeIds().contains(record.node))
         {
            // never remove it, only stop refusing, the node may be back
            if(WARNED.add(record.opId)) {
               LOG.warn("The node {} that ran the id change of organization {} has left the " +
                        "cluster. Writes into it are accepted again.", record.node, key);
            }

            return false;
         }

         return true;
      }

      return System.currentTimeMillis() - record.time <= RETIRED_TIMEOUT && !exists(key, record);
   }

   private static boolean exempt(String key, Record record) {
      return record.active && record.opId.equals(OWNER.get().get(key));
   }

   // a retired id is writable again once an organization of that id exists, which is looked up
   // once in a while, not on every write
   private static boolean exists(String key, Record record) {
      long now = System.currentTimeMillis();
      long[] cached = EXISTS_CACHE.get(key);

      if(cached != null && now - cached[0] < EXISTS_TTL) {
         return cached[1] == 1L;
      }

      boolean exists = false;

      try {
         String[] ids = SecurityEngine.getSecurity().getSecurityProvider().getOrganizationIDs();

         if(ids != null) {
            for(String id : ids) {
               if(key.equalsIgnoreCase(id)) {
                  exists = true;
                  break;
               }
            }
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to check whether organization {} exists, keeping the refusal", key, e);
      }

      EXISTS_CACHE.put(key, new long[] { now, exists ? 1L : 0L });
      return exists;
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

      String key = key(orgId);
      Record record = getMap().get(key);

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

      // the org id is one segment of the path, so only a few candidates have to be looked up
      for(String candidate : getOrgCandidates(path)) {
         String key = key(candidate);
         Record record = map.get(key);

         if(record == null || !isBlocked(key, record) || exempt(key, record)) {
            continue;
         }

         for(String id : record.ids) {
            if(OrgScopedPaths.isOrgScopedPath(path, id)) {
               throw new OrganizationChangingException(id);
            }
         }
      }
   }

   private static List<String> getOrgCandidates(String path) {
      List<String> list = new ArrayList<>();
      String[] segments = path.split("/");
      String first = segments[0];

      if(first.equals("portal") && segments.length > 1) {
         list.add(segments[1]);
      }
      else if(first.equals("sreeUserData") && segments.length > 1) {
         String file = segments[segments.length - 1];

         if(file.endsWith(".xml")) {
            file = file.substring(0, file.length() - 4);

            // a legacy org id may contain "_"
            for(int i = file.indexOf('_'); i >= 0; i = file.indexOf('_', i + 1)) {
               list.add(file.substring(i + 1));
            }
         }
      }
      else {
         list.add(first);
         int index = first.indexOf("__");

         if(index > 0) {
            list.add(first.substring(0, index));
         }
      }

      return list;
   }

   private static String key(String orgId) {
      return orgId.toLowerCase();
   }

   private static DistributedMap<String, Record> getMap() {
      MapHolder h = holder;
      Cluster cluster = Cluster.getInstance();

      if(h == null || h.cluster != cluster) {
         h = new MapHolder(cluster, cluster.getReplicatedMap(MAP_NAME));
         holder = h;
      }

      return h.map;
   }

   private static volatile MapHolder holder;
   private static final Logger LOG = LoggerFactory.getLogger(OrgIdChangeGuard.class);
   private static final String MAP_NAME = OrgIdChangeGuard.class.getName() + ".orgIdChanges";
   private static final long HEARTBEAT_PERIOD = TimeUnit.SECONDS.toMillis(30);
   private static final long HUNG_WARNING = TimeUnit.MINUTES.toMillis(30);
   private static final long RETIRED_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
   private static final long EXISTS_TTL = TimeUnit.SECONDS.toMillis(5);
   private static final Map<String, Operation> OPERATIONS = new ConcurrentHashMap<>();
   private static final Map<String, long[]> EXISTS_CACHE = new ConcurrentHashMap<>();
   private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
   private static final ScheduledExecutorService HEARTBEAT =
      Executors.newSingleThreadScheduledExecutor(r -> {
         Thread t = new Thread(r, "OrgIdChangeGuardHeartbeat");
         t.setDaemon(true);
         return t;
      });
   // org key -> id of the change the thread belongs to
   private static final ThreadLocal<Map<String, String>> OWNER =
      ThreadLocal.withInitial(HashMap::new);

   private static final class MapHolder {
      MapHolder(Cluster cluster, DistributedMap<String, Record> map) {
         this.cluster = cluster;
         this.map = map;
      }

      final Cluster cluster;
      final DistributedMap<String, Record> map;
   }

   private static final class Operation {
      Operation(String id, String oldKey, Map<String, Record> records, boolean caseOnly) {
         this.id = id;
         this.oldKey = oldKey;
         this.records = records;
         this.caseOnly = caseOnly;
         this.started = System.currentTimeMillis();
      }

      final String id;
      final String oldKey;
      final Map<String, Record> records;
      final boolean caseOnly;
      final long started;
      int renewals;
      volatile ScheduledFuture<?> heartbeat;
   }

   private static final class Record implements Serializable {
      Record(String opId, String node, long time, boolean active, Set<String> ids) {
         this.opId = opId;
         this.node = node;
         this.time = time;
         this.active = active;
         this.ids = new LinkedHashSet<>(ids);
      }

      final String opId;
      // the node that runs the change
      final String node;
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
