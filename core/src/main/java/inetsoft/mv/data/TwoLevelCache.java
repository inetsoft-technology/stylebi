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
package inetsoft.mv.data;

import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Cache with items over the limit swapped to file system.
 *
 * @version 12.2
 * @author InetSoft Technology Corp
 */
public abstract class TwoLevelCache<K, V> extends DataCache<K, V> {
   public TwoLevelCache(int limit, long timeout) {
      super(limit, timeout);
   }

   @Override
   public void put(K key, V data) {
      put(key, data, getEntryTimeout(key, data));
   }

   /**
    * Write the object to output.
    */
   protected abstract void writeObject(OutputStream output, V obj) throws IOException;

   /**
    * Read the object from input.
    */
   protected abstract V readObject(InputStream input);

   @Override
   protected final boolean demote(CacheEntry entry) {
      swapLock.lock();
      File file = null;
      V obj = (V) entry.getData();

      try {
         file = getCacheFile((K) entry.getKey());

         demoted.put(file, entry.getEntryTimeout());

         try(OutputStream output = Tool.createCompressOutputStream(new FileOutputStream(file))) {
            writeObject(output, obj);
         }

         file.setLastModified(entry.getLastAccess());
      }
      catch(Exception exc) {
         if(file != null) {
            demoted.remove(file);
            file.delete();
         }

         LOG.debug("Failed to serialize value: " + obj, exc);
         return false;
      }
      finally {
         swapLock.unlock();
      }

      return true;
   }

   @Override
   protected CacheEntry<K, V> promote(K key, long ts) {
      swapLock.lock();

      try {
         CacheEntry<K, V> entry = null;
         File file = getCacheFile(key);

         if(!isDemoted(file)) {
            return null;
         }

         if(file.isFile() && file.length() > 0) {
            if(ts > 0 && file.lastModified() < ts) {
               return null;
            }

            try(InputStream input = Tool.createUncompressInputStream(new FileInputStream(file))) {
               V obj = (V) readObject(input);
               entry = new CacheEntry<>(key, obj, getEntryTimeout(key, obj));
            }

            demoted.remove(key);
            file.delete();
         }

         return entry;
      }
      catch(Exception exc) {
         LOG.error("Failed to load object", exc);
         return null;
      }
      finally {
         swapLock.unlock();
      }
   }

   /**
    * Check if the entry is candidate for promotion.
    */
   protected boolean isDemoted(File file) {
      return demoted.containsKey(file);
   }

   @Override
   protected void cleanL2Cache() {
      swapLock.lock();

      try {
         File dir = getCacheDirectory();

         if(dir.isDirectory()) {
            for(File file : dir.listFiles()) {
               if(file.getName().endsWith(".tlc") && isTimeout(file)) {
                  file.delete();
                  demoted.remove(file);
               }
            }
         }
      }
      catch(IOException e) {
         LOG.debug("Unable to get Cache Directory.");
      }
      finally {
         swapLock.unlock();
      }
   }

   private File getCacheDirectory() throws IOException {
      FileSystemService fileSystemService = FileSystemService.getInstance();
      File dir = fileSystemService.getFile(fileSystemService.getCacheDirectory());

      if(id != null) {
         dir = new File(dir, id);

         if(!dir.exists()) {
            dir.mkdirs();
         }
      }

      return dir;
   }

   /**
    * Get the timeout for cache entry (override the global timeout).
    */
   public long getEntryTimeout(K key, V val) {
      return -1L;
   }

   /**
    * Get the timeout (for removal) of the secondary file.
    */
   protected long getL2Timeout() {
      return getTimeout() * 5;
   }

   /**
    * Set a cache id. Files in cache with same id will survive restart.
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Get the cache file for an entry to swap out to.
    */
   private File getCacheFile(K key) throws IOException {
      String str = key.toString();

      if(id == null) {
         // file should be unique across different cache even for the same key
         str += "-" + System.identityHashCode(this);
      }

      if(str.length() > 100) {
         str = str.substring(0, 100) + "-" + str.length() + "-" + str.hashCode();
      }

      File dir = getCacheDirectory();
      return new File(dir, Tool.toFileName(str) + ".tlc");
   }

   /**
    * Check if the cached file (L2 cache) should be removed.
    * The entry will be lost.
    */
   private boolean isTimeout(File file) {
      Long timeout0 = demoted.get(file);
      long timeout = (timeout0 != null && timeout0 > 0) ? timeout0 : getL2Timeout();

      return System.currentTimeMillis() - file.lastModified() > timeout;
   }

   private final Lock swapLock = new ReentrantLock();
   private Map<File,Long> demoted = new HashMap<>();
   private String id;
   private static final Logger LOG = LoggerFactory.getLogger(TwoLevelCache.class);
}
