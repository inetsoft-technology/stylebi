/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.report.internal;

import java.util.*;

/**
 * A cache used to maintain objects. It is used to avoid creating a new
 * object for multiple identical objects. This introduces an overhead
 * but can save large amount of memory.
 *
 * @version 5.1, 9/20/2003
 * @author InetSoft Technology Corp
 */
public class ObjectCache {
   /**
    * Clear the cache for the current thread.
    */
   public static synchronized void clear() {
      mapmap.remove(Thread.currentThread());
      lastThread = null;
      lastmap = null;
   }

   /**
    * Get a cached object.
    */
   public static Object get(Object obj) {
      Thread thread = Thread.currentThread();
      Map map = thread == lastThread ? lastmap : (Map) mapmap.get(thread);

      // in most cases, map is not null
      if(map == null) {
         synchronized(ObjectCache.class) {
            map = (Map) mapmap.get(thread);

            if(map == null) {
               mapmap.put(thread, map = new HashMap());
            }
         }
      }

      lastmap = map;
      lastThread = thread;

      synchronized(map) {
         Object obj2 = map.get(obj);

         // avoid the object cache accumulated too much to cause a memory leak.
         if(map.size() > 3000) {
            map.clear();
         }

         if(obj2 == null) {
            obj2 = ((Cacheable) obj).clone();

            if(obj instanceof BaseElement && obj2 instanceof BaseElement) {
               ((BaseElement) obj2).setInSection(((BaseElement) obj).isInSection());
            }

            map.put(obj2, obj2);
            return obj2;
         }

         return obj2;
      }
   }

   /**
    * Remove an object from the map.
    */
   public static void remove(Object obj) {
      Thread thread = Thread.currentThread();
      Map map = (Map) mapmap.get(thread);

      if(map != null) {
         synchronized(map) {
            map.remove(obj);
         }
      }
   }

   /**
    * Get the status of object cache.
    */
   public static String getStatus() {
      StringBuilder sb = new StringBuilder("ObjectCache");
      sb.append('[');
      Iterator keys = mapmap.keySet().iterator();
      int counter = 0;

      while(keys.hasNext()) {
         Object thread = keys.next();
         Map map = (Map) mapmap.get(thread);

         if(counter > 0) {
            sb.append(';');
         }

         sb.append(thread);
         sb.append('-');
         sb.append('>');
         sb.append(getFirstClass(map));
         sb.append('{');
         sb.append(map.size());
         sb.append('}');
         counter++;
      }

      sb.append(']');
      return sb.toString();
   }

   /**
    * Get the first class.
    */
   private static Object getFirstClass(Map map) {
      Iterator values = map.values().iterator();
      return values.hasNext() ? values.next().getClass() : "undefined";
   }

   private static Map mapmap = new HashMap(); // thread -> Map
   private static Map lastmap; // cached last map of last thread
   private static Thread lastThread; // last thread
}
