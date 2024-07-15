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

/**
 * XReduceTask, the reduce task to be executed by the server node.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XReduceTask {
   /**
    * Get the job id.
    */
   public String getID();

   /**
    * Set the job id.
    */
   public void setID(String id);

   /**
    * Get the value for the given key.
    */
   public String getProperty(String key);

   /**
    * Set the key-value pair.
    */
   public void setProperty(String key, String val);

   /**
    * Get the object for the given name.
    */
   public Object get(String name);

   /**
    * Set the key-value pair.
    */
   public void set(String name, Object obj);

   /**
    * Add one map result to this reduce task.
    */
   public void add(XMapResult result);

   /**
    * Get the final result.
    */
   public Object getResult();

   /**
    * Cancel this reduce task.
    */
   public void cancel();

   /**
    * Complete this reduce task.
    * @param all true if all blocks are added, false if still streaming.
    */
   public void complete(boolean all);

   /**
    * Check if this reduce task is completed.
    */
   public boolean isCompleted();

   /**
    * Check if the entire task (all sub-tasks) are completed. For example,
    * if the number of rows in the result reaches max rows, no additional
    * tasks need to be executed.
    */
   public boolean isFulfilled();
}
