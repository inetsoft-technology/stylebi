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
package inetsoft.mv.mr;

import java.util.*;

/**
 * AbstractReduceTask, implements common APIs of XReduceTask.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public abstract class AbstractReduceTask implements XReduceTask {
   /**
    * Create an instance of AbstractReduceTask.
    */
   public AbstractReduceTask() {
      super();
   }

   /**
    * Get the id of this job.
    */
   @Override
   public final String getID() {
      return id;
   }

   /**
    * Set the job id.
    */
   @Override
   public final void setID(String id) {
      this.id = id;
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
    * Check if the entire task (all sub-tasks) are completed. For example,
    * if the number of rows in the result reaches max rows, no additional
    * tasks need to be executed.
    */
   @Override
   public boolean isFulfilled() {
      return false;
   }

   /**
    * Get the string representation.
    */
   public String toString() {
      String cls = getClass().getName();
      int index = cls.lastIndexOf(".");
      cls = index < 0 ? cls : cls.substring(index + 1);
      return cls + "<id:" + id + ">"; //,prop:"+ props + ",map:" + map + '>';
   }

   private final Map<String, Object> map = new HashMap<>();
   private final Properties props = new Properties();
   private String id;
}
