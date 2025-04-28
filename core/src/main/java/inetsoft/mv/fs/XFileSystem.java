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
package inetsoft.mv.fs;

import java.util.List;
import java.util.Map;

/**
 * XFileSystem, the distributed file system works like HDFS.
 *
 * @author InetSoft Technology
 * @version 10.2
 */
public interface XFileSystem {
   /**
    * Get the config of this file system.
    */
   FSConfig getConfig();
   /**
    * Add one XFile to this file system.
    * @param name the specified name of this XFile.
    * @param files the specified physical files as blocks.
    */
   XFile add(String name, BlockFile[] files);

   // @TEST
   /**
    * Append file to file system, if the xfile not exist, create it.
    */
   XFile append(String name, BlockFile[] files);

   /**
    * Check if the specified file is contained.
    */
   boolean contains(String name);

   /**
    * Check if the specified file is contained.
    */
   boolean contains(XFile file);

   /**
    * Get the XFile for the given name.
    */
   XFile get(String name);

   /**
    * Get the internal XFile for the given XFile.
    */
   XFile get(XFile file);

   /**
    * List all XFiles in this file system.
    */
   XFile[] list();

   /**
    * List the XFiles match the specified XFileFilter.
    * @param filter the specified XFileFilter.
    */
   XFile[] list(XFileFilter filter);

   /**
    * Rename one XFile.
    */
   boolean rename(String from, String to);

   /**
    * Rename one XFile.
    */
   boolean rename(String from, String fromOrgId, String to, String toOrgId);

   /**
    * Remove the XFile for the given name.
    */
   boolean remove(String name);

   /**
    * Remove the specified XFile.
    */
   boolean remove(XFile file);

   /**
    * Remove the XFiles match the specified XFileFilter.
    */
   boolean remove(XFileFilter filter);

   /**
    * Delete the block record for the given name.
    */
   boolean deleteRecord(String name, List conds) throws Exception;

   /**
    * Update the block record for the given name.
    */
   boolean updateRecord(String name, Map dictMaps, Map sizes,
                        Map dimRanges, Map intRanges) throws Exception;

   /**
    * Append the block record for the given name.
    */
   boolean appendRecord(String name, List<BlockFile> fileList,
                        List<Integer> blockIndexes) throws Exception;

   /**
    * Dispose this file system.
    */
   void dispose();

   /**
    * Updates the in-memory cache from the configuration file.
    */
   void refresh(XBlockSystem bsys, boolean force);

   default void copyTo(XFileSystem target) {
   }
}
