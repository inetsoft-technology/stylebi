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
package inetsoft.mv;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class for holding session information for each Worksheet.
 */
public class MVSession {
   /**
    * Get the current (thread) session.
    */
   public static MVSession getCurrentSession() {
      return curr.get();
   }

   /**
    * Set the current (thread) session.
    */
   public static void setCurrentSession(MVSession session) {
      curr.set(session);
   }

   public void addInitialized(String table) {
      inited.put(table, table);
   }

   public boolean isInitialized(String table) {
      return inited.containsKey(table);
   }

   public void clearInitialized(String table) {
      inited.remove(table);
   }

   public void clearInitialized() {
      inited.clear();
   }

   /**
    * This discriminator is useful for telling apart different MVSession instances, for example
    * when generating unique query keys.
    * 
    * @return the session discriminator
    */
   public int getSessionDiscriminator() {
      return instanceDiscriminator;
   }

   public volatile Object sqlContext;
   private Map inited = new ConcurrentHashMap();
   private final int instanceDiscriminator = counter.incrementAndGet();

   private static ThreadLocal<MVSession> curr = new ThreadLocal<>();
   private static final AtomicInteger counter = new AtomicInteger(0);
}
