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

import inetsoft.sree.internal.cluster.Cluster;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.locks.Lock;

/**
 * {@code IdentifierGenerator} generates numeric identifiers that may be released and
 * reused.
 *
 * @hidden
 */
public class IdentifierGenerator implements Iterable<Map.Entry<Integer, String>> {
   public IdentifierGenerator(String name) {
      Cluster cluster = Cluster.getInstance();
      String prefix = getClass().getName() + "." + name + ".";
      internalToExternal = cluster.getMap(prefix + "internalToExternal");
      externalToInternal = cluster.getMap(prefix + "externalToInternal");
      lastAccess = cluster.getMap(prefix + "lastAccess");
      createLock = cluster.getLock(prefix + "createLock");
   }

   /**
    * Creates a new identifier.
    *
    * @param internal the internal identifier that is mapped to the new identifier.
    *
    * @return the new identifier.
    */
   public int createIdentifier(String internal) {
      createLock.lock();

      try {
         for(int i = 0; i < Integer.MAX_VALUE; i++) {
            if(!externalToInternal.containsKey(i)) {
               externalToInternal.put(i, internal);
               internalToExternal.put(internal, i);
               lastAccess.put(i, System.currentTimeMillis());
               return i;
            }
         }
      }
      finally {
         createLock.unlock();
      }

      throw new IllegalStateException("Runtime identifiers have been exhausted");
   }

   /**
    * Gets the external identifier that is mapped to the specified internal identifier.
    *
    * @param internal the internal identifier.
    *
    * @return the external identifier or -1 if it is not mapped.
    */
   public int getExternalIdentifier(String internal) {
      int external = internalToExternal.getOrDefault(internal, -1);

      if(external != -1) {
         lastAccess.put(external, System.currentTimeMillis());
      }

      return external;
   }

   /**
    * Gets the internal identifier that is mapped to the specified external identifier.
    *
    * @param external the external identifier.
    *
    * @return the internal identifier or {@code null} if it is not mapped.
    */
   public String getInternalIdentifier(int external) {
      String internal = externalToInternal.get(external);

      if(internal != null) {
         lastAccess.put(external, System.currentTimeMillis());
      }

      return internal;
   }

   /**
    * Gets the last access timestamp for the specified external identifier.
    *
    * @param external the external identifier.
    *
    * @return the last access time or 0 if the external identifier is not mapped.
    */
   public long getLastAccess(int external) {
      return lastAccess.getOrDefault(external, 0L);
   }

   /**
    * Releases an identifier back into the pool.
    *
    * @param id the identifier to release.
    */
   public void releaseIdentifier(int id) {
      createLock.lock();

      try {
         String internal = externalToInternal.remove(id);
         lastAccess.remove(id);

         if(internal != null) {
            internalToExternal.remove(internal);
         }
      }
      finally {
         createLock.unlock();
      }
   }

   @Override
   public Iterator<Map.Entry<Integer, String>> iterator() {
      Iterator<Map.Entry<Integer, String>> it = externalToInternal.entrySet().iterator();
      return new Iterator<Map.Entry<Integer, String>>() {
         @Override
         public boolean hasNext() {
            return it.hasNext();
         }

         @Override
         public Map.Entry<Integer, String> next() {
            current = it.next();
            return current;
         }

         @Override
         public void remove() {
            if(current != null) {
               releaseIdentifier(current.getKey());
            }
         }

         private Map.Entry<Integer, String> current;
      };
   }

   private final Map<Integer, String> externalToInternal;
   private final Map<String, Integer> internalToExternal;
   private final Map<Integer, Long> lastAccess;
   private final Lock createLock;
}
