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
package inetsoft.mv;

import inetsoft.mv.data.*;
import inetsoft.mv.fs.*;
import inetsoft.mv.fs.internal.*;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XTable;
import inetsoft.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * MVCompositeDispatcher, builds block data from MVDef and dispatches it to
 * distributed file system.
 *
 * @author InetSoft Technology
 * @since  12.0
 */
class MVCompositeDispatcher extends MVDispatcher implements Runnable {
   /**
    * Create an instanceof MVCompositeDispatcher.
    */
   public MVCompositeDispatcher(MVDef def, VariableTable vars) {
      super(def);
      this.vars = vars;
   }

   @Override
   protected boolean isCompleted() {
      synchronized(lock) {
         while(!completed) {
            try {
               lock.wait(Integer.MAX_VALUE);
            }
            catch(Throwable ex) {
               // ignore it
            }
         }
      }

      return completed;
   }

   @Override
   public void run() {
      try {
         synchronized(lock) {
            try {
               dispatch0();
            }
            finally {
               completed = true;
               lock.notifyAll();
            }
         }
      }
      catch(Exception ex) {
         exception = ex;
      }
   }

   @Override
   public Exception getException() {
      return exception;
   }

   /**
    * Dispatch the MV.
    */
   @Override
   protected void dispatch0() throws Exception {
      LOG.debug("MV Block Job - Start creating MV [" + name +
         "]: node " + Tool.getHost());
      boolean desktop = FSService.getConfig().isDesktop();
      long start = System.currentTimeMillis();

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      XTable data = getData(desktop, vars);

      long end = System.currentTimeMillis();
      LOG.debug("MV Block Job - Query executed [" + name +
              "]: " + (end - start) + " ms, node " + Tool.getHost());
      start = System.currentTimeMillis();
      MVBuilder builder = getMVBuilder();

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      // save to temp files
      List<BlockFile> list = saveTempFile(builder);

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      BlockFile[] arr = new BlockFile[list.size()];
      list.toArray(arr);
      MV mv = builder.getMV();
      end = System.currentTimeMillis();
      LOG.debug("MV Block Job - Block file created [" + name +
              "]: " + (end - start) + " ms, node " + Tool.getHost());
      start = System.currentTimeMillis();

      try {
         saveToLocalFS(mv, arr);
      }
      finally {
         // remove temp files if any
         for(int i = 0; i < arr.length; i++) {
            if(arr[i] != null) {
               arr[i].delete();
            }
         }
      }

      end = System.currentTimeMillis();
      LOG.debug("MV Block Job - Data file created [" + name +
              "]: " + (end - start) + " ms, node " + Tool.getHost());
   }

   /**
    * Create file to save sub mv block.
    */
   @Override
   protected BlockFile createFile(int counter) throws Exception {
      return new StorageBlockFile(
         "name-" + Tool.getIP() + "-" + counter + "-" +
            UUID.randomUUID().toString().replace("-", "") + ".smv");
   }

   /**
    * Get cache sree home directly.
    */
   private File getSharedDir() {
      // make sure the file is saved in sree home, because data server
      // and report server must share sree home, so we don't need to
      // send file to data server
      String cdir = SreeEnv.getProperty("sree.home");

      if(cdir == null) {
         throw new RuntimeException("sree.home is not found");
      }

      cdir = cdir + "/cache";
      File cdirF = FileSystemService.getInstance().getFile(cdir);
      Tool.lock(cdirF.getAbsolutePath());

      try {
         if(!cdirF.exists() && !cdirF.mkdirs()) {
            throw new RuntimeException("Cache directory does not exist: " + cdir);
         }
      }
      finally {
         Tool.unlock(cdirF.getAbsolutePath());
      }

      return cdirF;
   }

   /**
    * Save to local file system.
    */
   private void saveToLocalFS(MV mv, BlockFile[] bfiles) throws Exception {
      final XServerNode server = FSService.getServer();
      FileSystemService fileSystemService = FileSystemService.getInstance();

      if(server == null) {
         throw new RuntimeException("This host(" + Tool.getHost() +
            ") is not server node!");
      }

      XFileSystem fsys = server.getFSystem();
      MVStorage storage = MVStorage.getInstance();
      String file = Tool.getUniqueName(name, 61);
      File smfile = fileSystemService.getFile(getSharedDir(), file + ".mv"); // shared mv file as lock
      String mfile = MVStorage.getFile(name); // stored mv file

      Tool.lock(smfile.getAbsolutePath());

      try {
         // 1: replace old mv file
         MV pmv = null;

         if(storage.exists(mfile)) {
            pmv = storage.get(mfile);
         }

         // 2: save mv to shared dir and local mv dir
         mv = mergeMV(mv, pmv);
         Cluster.getInstance().lockWrite("mv.exec." + def.getName());

         try {
            mv.save(mfile);
            storage.invalidate(mfile);

            // we should not clean up the cluster env, because we may
            // clean up previous created result
            // 3: save to local fs
            fsys.append(name, bfiles);

            // 4: set up cluster local fs env
            long start = System.currentTimeMillis();
            ClusterUtil.setUp();
            long end = System.currentTimeMillis();
            LOG.debug("MV Block Job - Cluster Env Sync [" + name +
                     "]: " + (end - start) + " ms, node " + Tool.getHost());
         }
         finally {
            FSService.refreshCluster(true);
            Cluster.getInstance().unlockWrite("mv.exec." + def.getName());
         }
      }
      finally {
         Tool.unlock(smfile.getAbsolutePath());
      }
   }

   private VariableTable vars;
   private boolean completed = false;
   private Exception exception;
   private transient Object lock = new Object();
   private static final Logger LOG =
      LoggerFactory.getLogger(MVCompositeDispatcher.class);
}
