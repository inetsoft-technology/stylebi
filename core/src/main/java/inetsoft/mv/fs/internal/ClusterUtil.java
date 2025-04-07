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
package inetsoft.mv.fs.internal;

import inetsoft.mv.data.MVStorage;
import inetsoft.mv.fs.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

/**
 * Class that provides methods to synchronize the local file systems of report
 * server nodes.
 *
 * @author InetSoft Technology
 * @since  10.3
 */
public final class ClusterUtil {
   /**
    * Creates a new instance of <tt>ClusterUtil</tt>.
    */
   private ClusterUtil() {
   }

   /**
    * Determines if this action is running in a report cluster configured with
    * a local FS.
    *
    * @return <tt>true</tt> if a DB cluster.
    */
   private static boolean isClusterLocalFS() {
      if(SreeEnv.getProperty("server.type", "").contains("cluster")) {
         XServerNode server = FSService.getServer();

         if(server != null) {
            return true;
         }
      }

      return false;
   }

   /**
    * Set up cluster local fs env.
    */
   public static void setUp() {
      ClusterUtil.refreshFS();
   }

   static void setWorkDir(String node, String dir) {
      Cluster cluster = Cluster.getInstance();
      Map<String, String> map = cluster.getMap(WORK_DIR_MAP);
      map.put(node, dir);
   }

   /**
    * Refreshes the data file system on a cluster node.
    */
   private static void refreshFS() {
      Cluster cluster = Cluster.getInstance();

      try {
         cluster.sendMessage(new RefreshFSMessage());
      }
      catch(Exception e) {
         LOG.error("Failed to send refresh message", e);
      }
   }

   public static void deleteClusterMV(String mv) {
      if(ClusterUtil.isClusterLocalFS()) {
         try {
            String file = MVStorage.getFile(mv);
            MVStorage.getInstance().remove(file);
            addRemovedMVFile(file);
         }
         catch(Exception e) {
            LOG.error("Failed to delete MV file: {}", mv, e);
         }

         XFile xfile = FSService.getServer().getFSystem().get(mv);

         if(xfile != null) {
            for(SBlock block : xfile.getBlocks()) {
               BlockFile bfile = FSService.getDataNode().getBSystem().getFile(block);

               try {
                  BlockFileStorage.getInstance().delete(bfile.getName());
               }
               catch(IOException ignore) {
               }
            }
         }
      }
   }

   private static void addRemovedMVFile(String file) {
      if(REMOVED_MV_FILES.get() == null) {
         REMOVED_MV_FILES.set(new ArrayList<>());
      }

      REMOVED_MV_FILES.get().add(file);
   }

   public static boolean isRemovedMVFile(String file) {
      return REMOVED_MV_FILES.get() != null && REMOVED_MV_FILES.get().contains(file);
   }

   public static void clearRemovedMVFiles() {
      REMOVED_MV_FILES.set(null);
   }

   private static final String WORK_DIR_MAP = "inetsoft.mv.fs.internal.workDirMap";
   private static final ThreadLocal<List<String>> REMOVED_MV_FILES = new ThreadLocal<>();
   private static final Logger LOG =
      LoggerFactory.getLogger(ClusterUtil.class);
}
