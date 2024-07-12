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

import inetsoft.mv.comm.XTransferable;
import inetsoft.mv.fs.XBlockSystem;

/**
 * XMapTask, the map task to be executed at one data node.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XMapTask extends XTransferable {
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
    * Get the XTransferable for the given name.
    */
   public XTransferable get(String name);

   /**
    * Set the XTransferable key-value pair.
    */
   public void set(String name, XTransferable obj);

   /**
    * Get the id of the XBlock to be accessed.
    */
   public String getXBlock();

   /**
    * Set the id of the XBlock to be accessed.
    */
   public void setXBlock(String bid);

   /**
    * Get the data node to execute this map task.
    */
   public String getHost();

   /**
    * Set the data node to execute this map task.
    */
   public void setHost(String host);

   /**
    * Run the XMapTask by providing a block system.
    */
   public XMapResult run(XBlockSystem sys) throws Exception;

   /**
    * Cancel the task.
    */
   public void cancel();
   
   /**
    * Check if the task is cancelled.
    */
   public boolean isCancelled();
}
