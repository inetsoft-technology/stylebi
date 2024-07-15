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
import inetsoft.util.SingletonManager;
import inetsoft.util.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

@SingletonManager.Singleton(BookmarkLockManager.Reference.class)
public class BookmarkLockManager implements AutoCloseable {
   public BookmarkLockManager() {
      this.lockMap = Cluster.getInstance().getMultiMap(BOOKMARK_LOCK_MAP_NAME);
   }

   /**
    * Return a bookmark lock manager.
    */
   public static synchronized BookmarkLockManager getManager() {
      return SingletonManager.getInstance(BookmarkLockManager.class);
   }

   public void lock(String key, String user, String runtimeId) {
      BookmarkLock bookmarkLock = new BookmarkLock(user, runtimeId);
      Collection<BookmarkLock> locks = lockMap.get(key);

      // already locked
      if(locks != null && locks.contains(bookmarkLock)) {
         return;
      }

      lockMap.put(key, bookmarkLock);
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
      lockMap.remove(key, bookmarkLock);
   }

   /**
    * Unlock all locks with the specified user and runtimeId
    */
   public void unlockAll(String user, String runtimeId) {
      BookmarkLock bookmarkLock = new BookmarkLock(user, runtimeId);

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
      Collection<BookmarkLock> locks = lockMap.get(key);

      if(locks == null) {
         return null;
      }

      for(BookmarkLock lock : locks) {
         if(!Tool.equals(lock.user, user)) {
            return lock.user;
         }
      }

      return null;
   }

   @Override
   public void close() throws Exception {
      lockMap.clear();
      Cluster.getInstance().destroyMap(BOOKMARK_LOCK_MAP_NAME);
   }

   private MultiMap<String, BookmarkLock> lockMap;
   private static final String BOOKMARK_LOCK_MAP_NAME = "BOOKMARK_LOCK_MAP";
   private static final Logger LOG = LoggerFactory.getLogger(BookmarkLockManager.class);

   public static final class BookmarkLock implements Serializable {
      public BookmarkLock() {
      }

      public BookmarkLock(String user, String runtimeId) {
         this.user = user;
         this.runtimeId = runtimeId;
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
   }

   public static final class Reference extends SingletonManager.Reference<BookmarkLockManager> {
      @Override
      public synchronized BookmarkLockManager get(Object... parameters) {
         if(instance == null) {
            instance = new BookmarkLockManager();
         }

         return instance;
      }

      @Override
      public void dispose() {
         if(instance != null) {
            try {
               instance.close();
            }
            catch(Exception e) {
               LOG.warn("Failed to close bookmark lock service", e);
            }

            instance = null;
         }
      }

      private BookmarkLockManager instance;
   }
}
