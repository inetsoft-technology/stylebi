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
package inetsoft.mv.mr;

import inetsoft.mv.comm.XTransferable;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AbstractJob, implements common APIs of Xjob.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class AbstractJob implements XJob {
   /**
    * Create an id.
    */
   private static final String createID(String name) {
      lock.lock();

      try {
         return name + '_' + (counter++);
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Create an instance of AbstractJob.
    */
   public AbstractJob() {
      super();

      String name = getName();
      this.id = createID(name);
   }

   /**
    * Get the name of the job.
    */
   @Override
   public String getName() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      return cls = index < 0 ? cls : cls.substring(index + 1);
   }

   /**
    * Get the id of this job.
    */
   @Override
   public final String getID() {
      return id;
   }

   /**
    * Get the value for the given key.
    */
   @Override
   public final String getProperty(String key) {
      return props.getProperty(key);
   }

   /**
    * Set the key-value pair.
    */
   @Override
   public final void setProperty(String key, String val) {
      if(val == null) {
         props.remove(key);
      }
      else {
         props.setProperty(key, val);
      }
   }

   /**
    * Get the object for the given name.
    */
   @Override
   public final Object get(String name) {
      return map.get(name);
   }

   /**
    * Set the object key-value pair.
    */
   @Override
   public final void set(String name, Object obj) {
      if(obj == null) {
         map.remove(name);
      }
      else {
         map.put(name, obj);
      }
   }

   /**
    * Get the name of the XFile to be accessed.
    */
   @Override
   public final String getXFile() {
      return file;
   }

   /**
    * Set the name of the XFile to be accessed.
    */
   @Override
   public final void setXFile(String name) {
      this.file = name;
   }

   /**
    * Create the map task. The properties will be copied to this map task, and
    * the transferable key-value pairs will be copied to this map task too.
    * @param bid the specified block id.
    */
   @Override
   public final XMapTask createMapper(String host, String bid) {
      XMapTask task = createMapper0();
      task.setID(id);
      task.setHost(host);
      task.setXBlock(bid);

      // copy properties
      Iterator keys = props.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         String val = props.getProperty(key);
         task.setProperty(key, val);
      }

      // only copy transferables to map task
      keys = map.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         Object obj = map.get(key);

         if(obj instanceof XTransferable) {
            task.set(key, (XTransferable) obj);
         }
      }

      return task;
   }

   /**
    * Create the reduce task. The properties will be copied to this reduce
    * task, and all key-value pairs will be copied to this reduce task too.
    */
   @Override
   public final XReduceTask createReducer() {
      XReduceTask task = createReducer0();
      task.setID(id);

      // copy properties
      Iterator keys = props.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         String val = props.getProperty(key);
         task.setProperty(key, val);
      }

      // copy objects
      keys = map.keySet().iterator();

      while(keys.hasNext()) {
         String key = (String) keys.next();
         Object obj = map.get(key);
         task.set(key, obj);
      }

      return task;
   }

   /**
    * Cancel this job.
    */
   @Override
   public void cancel() {
      cancelled = true;
   }
   
   /**
    * Check if the job is cancelled.
    */
   @Override
   public boolean isCancelled() {
      return cancelled;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      String name = getName();
      return name + '-' + id + "<file:" + file + ",prop:"+ props + ",map:" +
         map + '>';
   }

   /**
    * Create a map tack.
    */
   protected abstract XMapTask createMapper0();

   /**
    * Create a reduce task.
    */
   protected abstract XReduceTask createReducer0();

   private static final Lock lock = new ReentrantLock();
   private static long counter = 0L;
   private final Map<String, Object> map = new HashMap<>();
   private final Properties props = new Properties();
   private final String id;
   private String file;
   private boolean cancelled;
}
