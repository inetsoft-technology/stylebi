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

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Cluster-wide record of an organization whose id is being changed (Bug #79856). The id change
 * copies the old organization's assets, registry and data space from one-shot snapshots and then
 * removes the old organization, so a write into the old organization from any node in between
 * is acknowledged and then lost. While the record is active, the shared write chokepoints
 * (the indexed storage and the replet registry) refuse writes into the old organization with
 * {@link OrganizationChangingException}, so the client can retry against the new id.
 * <p>
 * The record is kept in a replicated map, which a put updates on every node before it returns.
 * It stays "retired" for a while after a successful change, so that a node that still holds the
 * old organization's registry can't recreate its files, and it expires, so that a node that
 * crashed in the middle of a change can't block the organization forever.
 */
public final class OrgIdChangeGuard {
   private OrgIdChangeGuard() {
   }

   /**
    * Marks the organization as being changed. The calling thread, which runs the change, is
    * exempt from the refusal until {@link #end}.
    */
   public static void begin(String orgId) {
      if(orgId == null) {
         return;
      }

      getMap().put(key(orgId), new Record(System.currentTimeMillis(), true));
      OWNER.get().add(key(orgId));
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

      OWNER.get().remove(key(orgId));

      if(completed) {
         getMap().put(key(orgId), new Record(System.currentTimeMillis(), false));
      }
      else {
         getMap().remove(key(orgId));
      }
   }

   /**
    * Forgets the record, because the id is used by a new organization.
    */
   public static void clear(String orgId) {
      if(orgId != null) {
         getMap().remove(key(orgId));
      }
   }

   public static boolean isBlocked(String orgId) {
      if(orgId == null || orgId.isEmpty()) {
         return false;
      }

      Record record = getMap().get(key(orgId));

      if(record == null) {
         return false;
      }

      long timeout = record.active ? ACTIVE_TIMEOUT : RETIRED_TIMEOUT;

      if(System.currentTimeMillis() - record.time > timeout) {
         return false;
      }

      // a retired id is writable again once an organization of that id exists
      return record.active || !exists(orgId);
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
         // can't tell, keep refusing
      }

      return false;
   }

   /**
    * Refuses a write into an organization whose id is being changed, or was changed just now.
    *
    * @throws OrganizationChangingException if the write must be refused.
    */
   public static void checkWritable(String orgId) {
      if(isBlocked(orgId) && !OWNER.get().contains(key(orgId))) {
         throw new OrganizationChangingException(orgId);
      }
   }

   private static String key(String orgId) {
      return orgId.toLowerCase();
   }

   private static DistributedMap<String, Record> getMap() {
      return Cluster.getInstance().getReplicatedMap(MAP_NAME);
   }

   private static final String MAP_NAME = OrgIdChangeGuard.class.getName() + ".orgIdChanges";
   // longer than the longest id change, a crashed node's record expires after it
   private static final long ACTIVE_TIMEOUT = TimeUnit.MINUTES.toMillis(30);
   private static final long RETIRED_TIMEOUT = TimeUnit.MINUTES.toMillis(10);
   private static final ThreadLocal<Set<String>> OWNER = ThreadLocal.withInitial(HashSet::new);

   private static final class Record implements Serializable {
      Record(long time, boolean active) {
         this.time = time;
         this.active = active;
      }

      final long time;
      final boolean active;
   }

   /**
    * Thrown for a write into an organization whose id is being changed. The write was not made.
    */
   public static final class OrganizationChangingException extends IllegalStateException {
      public OrganizationChangingException(String orgId) {
         super("The ID of organization \"" + orgId + "\" is being changed. The write was not " +
               "made, retry it using the new organization ID.");
      }
   }
}
