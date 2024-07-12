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
package inetsoft.mv.fs;

import inetsoft.mv.comm.XReadBuffer;

/**
 * XFileSystem, the distributed block system works like HDFS.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XBlockSystem {
   /**
    * Get the config of this file system.
    */
   FSConfig getConfig();

   /**
    * Add one XBlock to this block system.
    * @param block the specified XBlock.
    * @param read the specified input.
    */
   NBlock add(XBlock block, XReadBuffer read);

   /**
    * Rename one XBlock.
    */
   NBlock rename(XBlock from, XBlock to);

   /**
    * Check if contains the specified block for the given block id.
    */
   boolean contains(String id);

   /**
    * Check if contains the specified block for the given XBlock.
    */
   boolean contains(XBlock block);

   /**
    * Get the NBlock for the given block id.
    */
   NBlock get(String id);

   /**
    * Get the internal NBlock for the given XBlock.
    */
   NBlock get(XBlock block);

   /**
    * Update the NBlock for the given block id.
    */
   NBlock update(String id);

   /**
    * Get the physical file for the given block id.
    * @return the physical file if any, null otherwise.
    */
   BlockFile getFile(String id);

   /**
    * Get the physical file for the given XBlock.
    * @return the physical file if any, null otherwise.
    */
   BlockFile getFile(XBlock block);

   /**
    * List all NBlocks in this block system.
    * @param clone true to clone the blocks.
    */
   NBlock[] list(boolean clone);

   /**
    * List the NBlocks match the specified XBlockFilter.
    * @param filter the specified XBlockFilter.
    * @param clone true to clone the blocks.
    */
   NBlock[] list(XBlockFilter filter, boolean clone);

   /**
    * Remove the XBlock for the given name.
    */
   boolean remove(String name);

   /**
    * Remove the specified XBlock.
    */
   boolean remove(XBlock block);

   /**
    * Remove the XBlocks match the specified XBlockFilter.
    */
   boolean remove(XBlockFilter filter);

   /**
    * Update this block system with uptodate information.
    */
   void update();

   /**
    * Dispose this file system.
    */
   void dispose();

   /**
    * Serializable the block map to file disk.
    */
   void save();

   /**
    * Updates the in-memory cache from the configuration file.
    */
   void refresh(boolean force);
}
