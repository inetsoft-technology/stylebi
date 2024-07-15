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

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * ExecutionMap is a map to handle the executing reports, viewsheet or queries
 * during a sampel period.
 *
 * @author InetSoft Technology Corp.
 * @version 10.2
 */
public class ExecutionMap {
   /**
    * Executing status.
    */
   private static final int EXECUTING = 0;
   /**
    * Completed status.
    */
   private static final int COMPLETED = 1;

   /**
    * Get the current number of the excuting objects, and clean the completed
    * objects from this map.
    */
   public int getCount() {
      int count = 0;
      lock.lock();

      try {
         count = map.size();
         Iterator iter = map.entrySet().iterator();

         while(iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            int status = ((Integer) entry.getValue()).intValue();

            if(entry.getKey() == null) {
               iter.remove();
               continue;
            }

            if(COMPLETED == status) {
               iter.remove();
            }
         }
      }
      finally {
         lock.unlock();
      }

      return count;
   }

   /**
    * Get the executing objects, and clean the completed objects from this map.
    */
   public List getObjects() {
      List objs = new ArrayList();
      lock.lock();

      try{
         Iterator iter = map.entrySet().iterator();

         while(iter.hasNext()) {
            Map.Entry entry = (Map.Entry) iter.next();
            int status = ((Integer) entry.getValue()).intValue();

            if(entry.getKey() == null) {
               iter.remove();
               continue;
            }
            else {
               objs.add(entry.getKey());
            }

            if(COMPLETED == status) {
               iter.remove();
            }
         }
      }
      finally {
         lock.unlock();
      }

      return objs;
   }

   /**
    * Add the executing object. Avoid duplicate object in the map. If the status
    * of the duplicated id is completd, change it as executing.
    */
   public void addObject(Object id) {
      if(id == null) {
         return;
      }

      lock.lock();

      try{
         if(map.containsKey(id)) {
            int status = (int) map.get(id);

            if(COMPLETED == status) {
               map.remove(id);
               map.put(id, EXECUTING);
            }
         }
         else {
            map.put(id, EXECUTING);
         }
      }
      finally {
         lock.unlock();
      }
   }
   
   /**
    * Remove one object.
    */
   public void remove(Object id) {
      lock.lock();

      try{
         map.remove(id);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Set the status of the specified object as completed.
    */
   public void setCompleted(Object id) {
      if(id == null) {
         return;
      }

      lock.lock();

      try{
         if(map.containsKey(id)) {
            int status = (int) map.get(id);

            if(EXECUTING == status) {
               map.remove(id);
               map.put(id, COMPLETED);
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   private final Lock lock = new ReentrantLock();
   // object id -> status
   private HashMap<Object, Integer> map = new HashMap<>();
}