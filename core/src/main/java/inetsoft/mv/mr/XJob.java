/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.mv.mr;

/**
 * XJob, the job to be executed by the server node.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XJob {
   /**
    * Get the name of the job.
    */
   public String getName();

   /**
    * Get the id of this job.
    */
   public String getID();

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
    * Set the object key-value pair.
    */
   public void set(String name, Object obj);

   /**
    * Create the map task.
    * @param bid the specified block id.
    */
   public XMapTask createMapper(String host, String bid);

   /**
    * Create the reduce task.
    */
   public XReduceTask createReducer();

   /**
    * Get the name of the XFile to be accessed.
    */
   public String getXFile();

   /**
    * Set the name of the XFile to be accessed.
    */
   public void setXFile(String name);

   /**
    * Cancel this job.
    */
   public void cancel();
   
   /**
    * Check if the job is cancelled.
    */
   public boolean isCancelled();

   /**
    * Whether the result can be streamed or it needs to wait for
    * all sub-jobs to complete.
    */
   public boolean isStreaming();
}
