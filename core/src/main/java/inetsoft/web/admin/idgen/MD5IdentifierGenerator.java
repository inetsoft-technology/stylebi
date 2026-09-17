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
package inetsoft.web.admin.idgen;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * {@code MD5IdentifierGenerator} generates identifiers based on the MD5 digest of an object name.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MD5IdentifierGenerator {
   /**
    * Gets the identifier for the specified name.
    *
    * @param name the object name.
    *
    * @return the identifier.
    */
   public String getId(String name) {
      lock.lock();

      try {
         if(ids.containsKey(name)) {
            return ids.get(name);
         }

         String id = this.generateId(name);

         if(ids.containsValue(this.generateId(name))) {
            ids.inverse().remove(id);
         }

         ids.put(name, id);
         idTimestamps.put(name, System.currentTimeMillis());
         return id;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Gets the object name associated with the specified identifier.
    *
    * @param id the identifier.
    *
    * @return the name or {@code null} if not found.
    */
   public String getName(String id) {
      lock.lock();

      try {
         String name = ids.inverse().get(id);

         if(name != null) {
            idTimestamps.put(name, System.currentTimeMillis());
         }

         return name;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Updates a name in the identifier cache.
    *
    * @param id      the identifier associated with the name.
    * @param oldName the old object name.
    * @param newName the new object name.
    */
   public void updateName(String id, String oldName, String newName) {
      lock.lock();

      try {
         ids.remove(oldName);
         ids.put(newName, id);
         idTimestamps.remove(oldName);
         idTimestamps.put(newName, System.currentTimeMillis());
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Removes a name from the identifier cache.
    *
    * @param name the name to remove.
    */
   public void removeName(String name) {
      lock.lock();

      try {
         if(ids.remove(name) != null) {
            idTimestamps.remove(name);
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Cleans expired identifiers from the cache.
    *
    * @param timeout the timeout interval in milliseconds.
    */
   public void cleanIds(long timeout) {
      lock.lock();

      try {
         long now = System.currentTimeMillis();

         for(Iterator<Map.Entry<String, Long>> i = idTimestamps.entrySet().iterator(); i.hasNext(); )
         {
            Map.Entry<String, Long> e = i.next();

            if(now - e.getValue() > timeout) {
               ids.remove(e.getKey());
               i.remove();
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   private String generateId(String name) {
      try {
         // MD5 is used only to derive a short, deterministic, non-persisted, in-memory cache key
         // for a name -- not for any cryptographic, password-hashing, or signature purpose -- so
         // its known collision weaknesses do not apply here.
         // nosemgrep: java.lang.security.audit.crypto.use-of-md5.use-of-md5
         MessageDigest digest = MessageDigest.getInstance("MD5");
         StringBuilder buffer = new StringBuilder();

         for(byte b : digest.digest(name.getBytes(StandardCharsets.UTF_16))) {
            buffer.append(String.format("%02X", ((int) b) & 0xff));
         }

         return buffer.toString();
      }
      catch(Exception e) {
         if(!generateErrorLogged) {
            LOG.warn("Failed to create MD5 digest for ID generation", e);
            generateErrorLogged = true;
         }

         return UUID.randomUUID().toString();
      }
   }

   private final Lock lock = new ReentrantLock();
   private final BiMap<String, String> ids = HashBiMap.create();
   private final Map<String, Long> idTimestamps = new HashMap<>();
   private boolean generateErrorLogged = false;

   private static final Logger LOG = LoggerFactory.getLogger(MD5IdentifierGenerator.class);
}
