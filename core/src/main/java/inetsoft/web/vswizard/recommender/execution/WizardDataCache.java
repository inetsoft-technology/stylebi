/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.vswizard.recommender.execution;

import inetsoft.sree.SreeEnv;
import inetsoft.util.*;
import inetsoft.web.vswizard.recommender.execution.data.WizardDataMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @version 13.2
 * @author InetSoft Technology Corp
 */
@SingletonManager.Singleton(WizardDataCache.Reference.class)
public class WizardDataCache extends DataCache<String, WizardDataMap> {
   /**
    * Creates a new instance of <tt>WizardDataCache</tt>.
    *
    * @since 10.3
    */
   private WizardDataCache() {
      try {
         String val = SreeEnv.getProperty("wizard.cache.file.timeout");

         if(val != null) {
            fileTimeout = Long.parseLong(val);
         }
      }
      catch(NumberFormatException ignored) {
         // ignored
      }

      setTimeout(360000);
   }

   /**
    * Get the data cache.
    * @return the data cache.
    */
   public static synchronized WizardDataCache getCache() {
      return SingletonManager.getInstance(WizardDataCache.class);
   }

   /**
    * Get the cached data.
    * @param key the data key in the cache.
    * @return cached data or null if cache doesn't exist.
    */
   public static WizardDataMap getCache(String key) {
      return getCache().get(key);
   }

   /**
    * Remove the cache which key starts with the target keyPrefix.
    * @param keyPrefix the encode worksheet path.
    */
   public static void clearCache(String keyPrefix) {
      if(keyPrefix == null) {
         return;
      }

      Set<String> keyset = getCache().keySet();

      for(String key : keyset) {
         if(key != null && key.startsWith(keyPrefix)) {
            getCache().remove(key);
         }
      }
   }

   @Override
   protected boolean isOutOfDate(CacheEntry entry) {
      boolean timeout = super.isOutOfDate(entry);

      // demote before remove.
      if(timeout) {
         demote(entry);
      }

      return timeout;
   }

   @Override
   protected CacheEntry<String, WizardDataMap> promote(String key, long ts) {
      CacheEntry<String, WizardDataMap> entry;
      FileSystemService fileSystemService = null;
      File file = null;
      swapLock.lock();

      try {
         fileSystemService = FileSystemService.getInstance();
         File folder = getCacheFolder();
         file = fileSystemService.getFile(folder, getFileName(key));

         if(file.exists() && file.isFile()) {
            if(checkTimeout(file)) {
               try(ObjectInputStream in =
                      new ObjectInputStream(new BufferedInputStream(new FileInputStream(file))))
               {
                  WizardDataMap data = (WizardDataMap) in.readObject();
                  entry = new CacheEntry<>(key, data);
               }
            }
            // clear cache if the cache file timeout.
            else {
               entry = new CacheEntry<>(key, createWizardDataMap(), getTimeout());
            }

            fileSystemService.deleteFile(file);
         }
         else {
            entry = new CacheEntry<>(key, createWizardDataMap(), getTimeout());
         }
      }
      catch(Exception ex) {
         entry = new CacheEntry<>(key, createWizardDataMap(), getTimeout());

         if(fileSystemService != null && file != null) {
            fileSystemService.deleteFile(file);
         }

         LOG.warn("Failed to load wizard cache file.");
      }
      finally {
         swapLock.unlock();
      }

      return entry;
   }

   @Override
   protected boolean demote(CacheEntry entry) {
      FileSystemService fileSystemService = null;
      File file = null;
      swapLock.lock();

      try {
         fileSystemService = FileSystemService.getInstance();
         File folder = getCacheFolder();
         file = fileSystemService.getFile(folder, getFileName((String) entry.getKey()));

         if(!folder.exists() && !folder.mkdirs()) {
            LOG.error("Failed to create wizard cache folder: {}", folder);
         }

         if(!file.exists()) {
            if(!file.createNewFile()) {
               LOG.error("Failed to create wizard cache file: {}", file);
            }

            fileSystemService.setLastModifiedTime(file, entry.getLastAccess());
         }

         if(file.lastModified() <= entry.getLastAccess()) {
            try(ObjectOutputStream out =
                   new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file))))
            {
               out.writeObject(entry.getData());
               out.flush();
               fileSystemService.setLastModifiedTime(file, entry.getLastAccess());
            }
         }
      }
      catch(IOException ex) {
         if(fileSystemService != null && file != null) {
            fileSystemService.deleteFile(file);
         }

         LOG.error("Failed to save wizard cache", ex);
         return false;
      }
      finally {
         swapLock.unlock();
      }

      return true;
   }

   private static String getFileName(String key) {
      return key.length() > 255 ? key.substring(0, 230) + key.hashCode() : key;
   }

   /**
    * @return the cache folder for wizard cache data.
    */
   private File getCacheFolder() {
      return FileSystemService.getInstance().getFile(Tool.getCacheDirectory() + "/wizard");
   }

   private boolean checkTimeout(File file) {
      return System.currentTimeMillis() - file.lastModified() < fileTimeout;
   }

   protected WizardDataMap createWizardDataMap() {
      return new WizardDataMap();
   }

   public static final class Reference extends SingletonManager.Reference<WizardDataCache> {
      @Override
      public synchronized WizardDataCache get(Object ... parameters) {
         if(cache == null) {
            cache = new WizardDataCache();
         }

         return cache;
      }

      @Override
      public synchronized void dispose() {
         if(cache != null) {
            cache = null;
         }
      }

      private WizardDataCache cache;
   }

   private long fileTimeout = 3600000 * 24L * 7; // expiration period, ms
   private final Lock swapLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(WizardDataCache.class);
}
