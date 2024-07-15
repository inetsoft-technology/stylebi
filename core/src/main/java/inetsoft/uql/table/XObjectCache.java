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
package inetsoft.uql.table;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Set;

/**
 * XObjectCache, caches objects to try to reuse them.
 *
 * @version 9.1
 * @author InetSoft Technology Corp
 */
public final class XObjectCache<E> implements Cloneable, Serializable {
   /**
    * Get the size.
    */
   public final int size() {
      return cache.size();
   }

   /**
    * Check if is empty.
    */
   public final boolean isEmpty() {
      return cache.isEmpty();
   }

   /**
    * Check if a key exists.
    */
   public final boolean contains(E key) {
      return cache.contains(key);
   }

   /**
    * Put a key value pair. The key will be returned.
    */
   public final E get(E key) {
      E v = cache.addOrGet(key);
      ptotal++;

      if(v != null) {
         htotal++;
         return v;
      }

      return key;
   }

   /**
    * Get the hit ratio.
    */
   public final float getRatio() {
      return ptotal == 0 ? 1 : ((float) htotal) / ptotal;
   }

   /**
    * Clear the cache.
    */
   public final void clear() {
      ptotal = 0;
      htotal = 0;
      cache.clear();
   }

   /**
    * Clone the cache.
    */
   @Override
   public XObjectCache<E> clone() {
      XObjectCache<E> result = null;

      try {
         result = (XObjectCache) super.clone();
      }
      catch (CloneNotSupportedException e) {
         LOG.error("Failed to clone object", e);
      }

      result.cache = cache.clone();
      return result;
   }

   /**
    * Get the key set.
    */
   public final Set<E> keySet() {
      return cache;
   }

   /**
    * Get the string representation.
    */
   public final String toString() {
      return cache.toString();
   }

   /**
    * Get the hash code value.
    */
   public final int hashCode() {
      return cache.hashCode();
   }

   /**
    * Check if equals another object.
    */
   public final boolean equals(Object o) {
      if(o == this) {
         return true;
      }

      if(!(o instanceof XObjectCache)) {
         return false;
      }

      return ((XObjectCache<?>) o).cache.equals(cache);
   }

   private int htotal = 0, ptotal = 0;
   private ObjectOpenHashSet<E> cache = new ObjectOpenHashSet<>();

   private static final Logger LOG = LoggerFactory.getLogger(XObjectCache.class);
}
