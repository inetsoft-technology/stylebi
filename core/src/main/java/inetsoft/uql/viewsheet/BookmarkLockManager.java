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
package inetsoft.uql.viewsheet;

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MultiMap;
import inetsoft.util.ConfigurationContext;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

public class BookmarkLockManager {
   public BookmarkLockManager() {
   }

   /**
    * Get the cluster-wide lock map. The map is shared by every node, so it is looked up on each
    * use instead of being held in a field: a proxy held across a cache stop (a node that
    * destroyed the cache, or a cluster that recreated it) throws CacheStoppedException for
    * the life of the JVM, while a fresh lookup re-attaches to the live cache.
    */
   private MultiMap<String, BookmarkLock> getLockMap() {
      return Cluster.getInstance().getMultiMap(BOOKMARK_LOCK_MAP_NAME);
   }

   /**
    * Return a bookmark lock manager.
    */
   public static synchronized BookmarkLockManager getManager() {
      return ConfigurationContext.getContext().getSpringBean(BookmarkLockManager.class);
   }

   public void lock(String key, String user, String runtimeId) {
      BookmarkLock bookmarkLock = new BookmarkLock(user, runtimeId, getLocalNodeId());
      MultiMap<String, BookmarkLock> lockMap = getLockMap();
      Collection<BookmarkLock> locks = lockMap.get(key);
      BookmarkLock existing = find(locks, bookmarkLock);

      // already locked by this node
      if(existing != null && Objects.equals(existing.nodeId, bookmarkLock.nodeId)) {
         return;
      }

      // Bug #82336, an equal lock written by another node (the sheet was reloaded here after
      // its owner died) is replaced, so the lock carries the node that now holds the sheet.
      // BookmarkLock.equals ignores the node id, so the remove drops the old entry.
      if(existing != null) {
         lockMap.remove(key, existing);
      }

      lockMap.put(key, bookmarkLock);
   }

   /**
    * Re-assert the bookmark locks of a runtime sheet that was loaded on this node from the
    * cluster cache, for example after the node that held it died. Every lock that the sheet
    * still holds is moved to this node, so it is no longer treated as the lock of a dead
    * node. A lock that the sheet no longer holds is not acquired again.
    *
    * @param user      the sheet's user name.
    * @param runtimeId the sheet's runtime id.
    */
   public void relockAll(String user, String runtimeId) {
      String nodeId = getLocalNodeId();

      if(nodeId == null) {
         return;
      }

      BookmarkLock bookmarkLock = new BookmarkLock(user, runtimeId);
      MultiMap<String, BookmarkLock> lockMap = getLockMap();

      for(Map.Entry<String, BookmarkLock> entry : lockMap.entrySet()) {
         BookmarkLock lock = entry.getValue();

         if(Tool.equals(lock, bookmarkLock) && !nodeId.equals(lock.nodeId)) {
            lock(entry.getKey(), user, runtimeId);
         }
      }
   }

   /**
    * Clear the specified runtime sheet's bookmark locks.
    *
    * @param @param    entry the sheet's entry.
    * @param runtimeId the sheet's runtimeId.
    * @return true if successful, false otherwise.
    */
   public void unlock(String key, String user, String runtimeId) {
      BookmarkLock bookmarkLock = new BookmarkLock(user, runtimeId);
      getLockMap().remove(key, bookmarkLock);
   }

   /**
    * Unlock all locks with the specified user and runtimeId
    */
   public void unlockAll(String user, String runtimeId) {
      BookmarkLock bookmarkLock = new BookmarkLock(user, runtimeId);
      MultiMap<String, BookmarkLock> lockMap = getLockMap();

      for(Map.Entry<String, BookmarkLock> entry : lockMap.entrySet()) {
         if(Tool.equals(entry.getValue(), bookmarkLock)) {
            lockMap.remove(entry.getKey(), entry.getValue());
         }
      }
   }

   /**
    * Check if the specified path is locked by another user.
    *
    * @param key  asset + bookmark path
    * @param user current user
    *             return user name of the first found lock.
    */
   public String getLockedBookmarkUser(String key, String user) {
      Collection<BookmarkLock> locks = getLockMap().get(key);

      if(locks == null) {
         return null;
      }

      Set<String> liveNodes = null;

      for(BookmarkLock lock : locks) {
         if(Tool.equals(lock.user, user)) {
            continue;
         }

         // Bug #82336, a lock held by a node that has left the cluster is not a lock: the
         // runtime sheet that held it died with that node, and no node sweeps it any more.
         // A lock without a node id was written by an older version and still counts.
         if(lock.nodeId != null) {
            if(liveNodes == null) {
               liveNodes = getClusterNodeIds();
            }

            if(!liveNodes.contains(lock.nodeId)) {
               continue;
            }
         }

         return lock.user;
      }

      return null;
   }

   private static BookmarkLock find(Collection<BookmarkLock> locks, BookmarkLock lock) {
      if(locks != null) {
         for(BookmarkLock item : locks) {
            if(Objects.equals(item, lock)) {
               return item;
            }
         }
      }

      return null;
   }

   private static String getLocalNodeId() {
      try {
         return Cluster.getInstance().getLocalNodeId();
      }
      catch(Exception ex) {
         LOG.debug("Failed to get the local node id", ex);
         return null;
      }
   }

   private static Set<String> getClusterNodeIds() {
      Set<String> nodes = Cluster.getInstance().getClusterNodeIds();
      return nodes == null ? Collections.emptySet() : nodes;
   }

   private static final String BOOKMARK_LOCK_MAP_NAME = "BOOKMARK_LOCK_MAP";
   private static final Logger LOG = LoggerFactory.getLogger(BookmarkLockManager.class);

   public static final class BookmarkLock implements Serializable {
      public BookmarkLock() {
      }

      public BookmarkLock(String user, String runtimeId) {
         this(user, runtimeId, null);
      }

      public BookmarkLock(String user, String runtimeId, String nodeId) {
         this.user = user;
         this.runtimeId = runtimeId;
         this.nodeId = nodeId;
      }

      /**
       * The id of the cluster node that holds the runtime sheet, or null for a lock written
       * by an older version. It is not part of equals and hashCode, so a lock is found by its
       * user and runtime id whichever node wrote it.
       */
      public String getNodeId() {
         return nodeId;
      }

      @Override
      public boolean equals(Object o) {
         if(this == o) {
            return true;
         }
         if(o == null || getClass() != o.getClass()) {
            return false;
         }
         BookmarkLock that = (BookmarkLock) o;
         return Objects.equals(user, that.user) && Objects.equals(runtimeId, that.runtimeId);
      }

      @Override
      public int hashCode() {
         return Objects.hash(user, runtimeId);
      }

      private String user;
      private String runtimeId;
      private String nodeId;
   }

}
