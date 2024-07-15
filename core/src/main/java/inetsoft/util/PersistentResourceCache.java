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
package inetsoft.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Iterator;
import java.util.Properties;

/**
 * PersistentResourceCache caches resources in a persistent way. Note that only
 * String keys are supported at present.
 *
 * @version 8.5
 * @author InetSoft Technology Corp
 */
public abstract class PersistentResourceCache extends ResourceCache {
   /**
    * Create a persistent resource cache.
    * @param name the specified name.
    */
   public PersistentResourceCache(String name) {
      super();

      this.name = name;
      load();
   }

   /**
    * Create a persistent resource cache with maximum size.
    * @param name the specified name.
    * @param max the specified max size, a value less than or equal to zero
    * means no limit.
    */
   public PersistentResourceCache(String name, int max) {
      super(max);

      this.name = name;
      load();
   }

   /**
    * Create a persistent resource cache with maximum size and timeout.
    * @param name the specified name.
    * @param max the specified max size, a value less than or equal to zero
    * means no limit.
    * @param timeout the specified timeout, a value less than or equal to zero
    * means no timeout.
    */
   public PersistentResourceCache(String name, int max, long timeout) {
      super(max, timeout);

      this.name = name;
      load();
   }

   /**
    * Process changed.
    */
   @Override
   protected void processChanged() {
      super.processChanged();
      save();
   }

   /**
    * Restore the resource cache.
    */
   public void load() {
      Thread thread = new GroupedThread(this::loadProperties);
      thread.start();
   }

   /**
    * Restore the resource cache.
    */
   private void loadProperties() {
      Properties prop = new Properties();
      File file = getCachedFile();

      if(!file.exists()) {
         return;
      }

      try {
         FileInputStream in = new FileInputStream(file);
         prop.load(in);
         in.close();
         String stsStr = prop.getProperty("__save__moment__");
         long sts = stsStr != null ? Long.parseLong(stsStr) : 0;
         prop.remove("__save__moment__");
         long now = System.currentTimeMillis();
         Iterator keys = prop.keySet().iterator();

         while(keys.hasNext()) {
            String key = (String) keys.next();
            long ts = Long.parseLong(prop.getProperty(key));
            ts += (now - sts);
            Object val = create(key);
            ValueEntry entry = new ValueEntry(key, val);
            entry.date = ts;
            map.put(key, entry);
         }
      }
      catch(Exception ex) {
         LOG.error("Failed to load properties: " + file, ex);
      }

      boolean changed = checkTimeOut();
      changed = checkSize() || changed;

      if(changed) {
         save();
      }
   }

   /**
    * Save the resource cache.
    */
   public synchronized void save() {
      Iterator keys = map.keySet().iterator();
      Properties prop = new Properties();

      while(keys.hasNext()) {
         Object key = keys.next();

         if(!(key instanceof String)) {
            LOG.warn("Only string keys are supported in file {}", name);
            return;
         }

         ValueEntry ventry = (ValueEntry) map.get(key);

         if(ventry != null) {
            long ts = ventry.date;
            prop.setProperty((String) key, ts + "");
         }
      }

      File file = getCachedFile();

      try(FileOutputStream out = new FileOutputStream(file)) {
         prop.setProperty("__save__moment__", System.currentTimeMillis() + "");
         prop.store(out, name);
      }
      catch(Exception ex) {
         LOG.error("Failed to write properties: " + file, ex);
      }
   }

   /**
    * Get the cached file.
    * @return the cached file.
    */
   private File getCachedFile() {
      String dir = Tool.getCacheDirectory();
      String fname = Tool.PERSISTENT_PREFIX + name + ".prc";
      return FileSystemService.getInstance().getFile(dir, fname);
   }

   private String name;

   private static final Logger LOG =
      LoggerFactory.getLogger(PersistentResourceCache.class);
}
