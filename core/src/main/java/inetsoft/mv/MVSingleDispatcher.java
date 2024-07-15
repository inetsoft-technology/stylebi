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
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.uql.XTable;
import inetsoft.util.CancelledException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * MVSingleDispatcher, builds data from MVDef and dispatches it to
 * distributed file system.
 *
 * @author InetSoft Technology
 * @since  10.2
 */
class MVSingleDispatcher extends MVDispatcher {
   /**
    * Create an instanceof MVSingleDispatcher.
    */
   public MVSingleDispatcher(MVDef def) {
      super(def);
   }

   /**
    * Dispatch the MV.
    */
   @Override
   protected void dispatch0() throws Exception {
      XServerNode server = FSService.getServer();

      if(server == null) {
         throw new RuntimeException("This host is not server node!");
      }

      XFileSystem fsys = server.getFSystem();
      boolean desktop = server.getConfig().isDesktop();
      long start = System.currentTimeMillis();
      LOG.debug("Start creating materialized view " + def.getName());
      XTable data = getData(desktop, null);
      long end = System.currentTimeMillis();
      LOG.debug("Query executed for materialized view " + def.getName() + " in " +
	       (end - start) + "ms");

      MVBuilder builder = getMVBuilder();

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      start = System.currentTimeMillis();

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      MV mv = builder.getMV();
      String name = this.name + "_temp";
      String oname = this.name;

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      // save to temp files
      List<BlockFile> list = saveTempFile(builder);

      if(isCanceled()) {
         throw new CancelledException("The MV creation was interrupted.");
      }

      long built = System.currentTimeMillis();
      LOG.debug("Data loaded and saved for materialized view " + def.getName() + " in " +
                   (built - end) + "ms");

      // update MV file at server node
      MVStorage storage = MVStorage.getInstance();
      String file = MVStorage.getFile(name);

      if(storage.exists(file)) {
         try {
            storage.remove(file);
         }
         catch(Exception ignore) {
         }
      }

      // save it last
      // mv.save(file);
      BlockFile[] arr = list.toArray(new BlockFile[0]);

      // remove old mv, and rename new mv to old mv
      // if we lock the xfile's write lock here, and lock the xfile's
      // read lock in mv executation, it may be dangerous if administrator
      // want to recreate mv
      Cluster.getInstance().lockWrite("mv.exec." + def.getName());

      // there may be more than one scheduler updating fs (in memory),
      // lock to make sure only one copy is modified and saved before
      // the other is modified.
      Cluster.getInstance().lockKey("mv.fs.update");

      try {
         // should call with force=false, but that still causes
         // data missing (Has Data = No). not sure why. we should
         // consider refactoring the how local MV to avoid the
         // problem with synchronizing in-memory copies across
         // a cluster. Bug #9683
         fsys.refresh(FSService.getDataNode().getBSystem(), true);

         // update sub mv files in the distributed file system
         try {
            boolean removed = fsys.remove(name);

            if(!removed) {
               throw new Exception("Failed to remove XFile: " + name);
            }

            fsys.add(name, arr);
         }
         finally {
            // remove temp files if any
            for(int i = 0; i < arr.length; i++) {
               if(arr[i] != null) {
                  try {
                     arr[i].delete();
                  }
                  catch(Exception ex) {
                     LOG.debug("Failed to delete temp file: " + arr[i] + " " + ex);
                  }
               }
            }
         }

         // @by stephenwebster, For Bug #6637
         // It seems we make an effort to create a _temp mv file, but we
         // really never wrote to it.  If we get to the else statement, then
         // there must be an original mv file that exists.
         // So, first we will save to the _temp file, so that if it fails
         // during save, we do not end up with a partial mv file and the
         // current mv will continue to work, even though it may be stale.
         mv.save(file);

         // The file has to be removed first before sys.rename otherwise
         // sys.rename will fail because the original file already exists.
         MVBackupManager.remove(oname);
         fsys.rename(name, oname);

         // Get the original mv name and do an atomic move from the _temp
         // file to the main mv file.
         String mfile = MVStorage.getFile(oname);
         storage.rename(file, mfile);

         long saved = System.currentTimeMillis();
         LOG.debug("Files saved and copied for materialized view " + def.getName() + " in " +
                      (saved - built) + "ms");
      }
      catch(Exception ex) {
         try {
            fsys.remove(name);
         }
         catch(Exception e1) {
            // ignore it
         }

         throw ex;
      }
      finally {
         Cluster.getInstance().unlockKey("mv.fs.update");
         FSService.refreshCluster(true);
         Cluster.getInstance().unlockWrite("mv.exec." + def.getName());

         try {
            storage.remove(file);
         }
         catch(Exception ignore) {
         }
      }

      end = System.currentTimeMillis();
      LOG.debug("Data file created for materialized view " + def.getName() +
	       " in " + (end - start) + "ms");
   }

   private static final Logger LOG =
      LoggerFactory.getLogger(MVSingleDispatcher.class);
}
