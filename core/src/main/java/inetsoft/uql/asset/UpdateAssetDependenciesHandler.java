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
package inetsoft.uql.asset;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.DistributedLong;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.uql.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.xmla.XMLADataSource;
import inetsoft.util.*;
import inetsoft.util.dep.*;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;

import static inetsoft.util.dep.XAssetEnumeration.getXAssetEnumeration;

/**
 * Read property `inetsoft.uql.asset.UpdateAssetDependenciesHandler.updated`
 * of file 'sree.properties' to provider asset updated flag.
 */
public final class UpdateAssetDependenciesHandler implements AutoCloseable {
   public UpdateAssetDependenciesHandler() {
      Cluster cluster = Cluster.getInstance();
      state = cluster.getLong(STATE_NAME);
      lock = cluster.getLock(LOCK_NAME);
   }

   @Override
   public void close() {
      lock.lock();

      try {
         closed = true;

         if(thread != null) {
            thread.cancel();
            thread = null;
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get UpdateAssetDependenciesHandler instance.
    */
   public static UpdateAssetDependenciesHandler getInstance() {
      return SingletonManager.getInstance(UpdateAssetDependenciesHandler.class);
   }

   /**
    * update asset dependencies.
    */
   private void updateAssetDependencies() {
      try {
         LOG.warn(catalog.getString("maintain.dependencies.start"));
         DependencyStorageService.getInstance().clear();
         fixViewsheetsDependencies();
         fixVSSnapshotsDependencies();
         fixWorksheetsDependencies();
         fixScheduleTaskDependencies();
         fixDatasourceDependencies();

         assetUpdated();
         LOG.warn(catalog.getString("maintain.dependencies.finished"));
      }
      catch(Exception e) {
         LOG.error(catalog.getString("maintain.dependencies.failed"), e);
      }
      finally {
         rebuilding = false;
         completeThread();
      }
   }

   private void completeThread() {
      lock.lock();

      try {
         thread = null;
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Smaller than FIX_DEPENDENCIES_MIN_VERSION need to be identified as fix dependencies
    */
   private boolean needUpdate(String assetVersion) {
      if(!StringUtils.isEmpty(assetVersion)) {
         if(rebuilding) {
            return true;
         }

         try {
            float versionNum = Float.parseFloat(assetVersion);
            return versionNum < FIX_DEPENDENCIES_MIN_VERSION;
         }
         catch(Exception ignoreEx) {
         }
      }

      return true;
   }

   /**
    * Set fixed flag when asset dependencies is updated.
    */
   private void assetUpdated() {
      lock.lock();

      try {
         state.set(STATE_UPDATED);
         SreeEnv.setProperty(getUpdateKey(), "true");
         SreeEnv.save();
      }
      catch(Exception e) {
         LOG.error(catalog.getString("maintain.dependencies.failed.saveProperty"), e);
      }
      finally {
         lock.unlock();
      }
   }

   private void fixViewsheetsDependencies() {
      if(closed) {
         return;
      }

      XAssetEnumeration assetEnum = getXAssetEnumeration(ViewsheetAsset.VIEWSHEET);

      if(!(assetEnum instanceof ViewsheetEnumeration)) {
         return;
      }

      ViewsheetEnumeration vsAssets = (ViewsheetEnumeration) assetEnum;
      AssetRepository engine = AssetUtil.getAssetRepository(false);
      ExecutorService executors = newFixedThreadPool();

      while(vsAssets.hasMoreElements()) {
         if(closed) {
            break;
         }

         ViewsheetAsset vsAsset = (ViewsheetAsset) vsAssets.nextElement();

         executors.execute(() -> {
            AbstractSheet sheet = vsAsset.getCurrentSheet(engine);

            if(sheet == null) {
               LOG.warn(
                  "Failed to update dependencies of viewsheet {}, not found", vsAsset.getPath());
            }
            else if(needUpdate(sheet.getVersion())) {
               AssetEntry assetEntry = vsAsset.getAssetEntry();

               try {
                  DependencyHandler.getInstance().updateSheetDependencies(sheet, assetEntry, true, true);
               }
               catch(Exception ex) {
                  if(LOG.isDebugEnabled()) {
                     LOG.warn(
                        "Failed to update dependencies of viewsheet {}", assetEntry.getPath(), ex);
                  }
                  else {
                     LOG.warn(
                        "Failed to update dependencies of viewsheet {}", assetEntry.getPath());
                  }
               }
            }
         });
      }

      flushDependencyFiles(executors);
   }

   private void fixVSSnapshotsDependencies() {
      if(closed) {
         return;
      }

      XAssetEnumeration snapEnum = getXAssetEnumeration(VSSnapshotAsset.VSSNAPSHOT);

      if(!(snapEnum instanceof VSSnapshotEnumeration)) {
         return;
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      ExecutorService executors = newFixedThreadPool();

      while(snapEnum.hasMoreElements()) {
         if(closed) {
            break;
         }

         VSSnapshotAsset asset = (VSSnapshotAsset) snapEnum.nextElement();

         executors.execute(() -> {
            AbstractSheet sheet = asset.getCurrentSheet(engine);

            if(sheet == null) {
               LOG.warn(
                  "Failed to update dependencies of viewsheet snapshot {}, not found",
                  asset.getPath());
            }
            else if(needUpdate(sheet.getVersion())) {
               AssetEntry assetEntry = asset.getAssetEntry();

               try {
                  DependencyHandler.getInstance().updateSheetDependencies(sheet, assetEntry, true, true);
               }
               catch(Exception ex) {
                  if(LOG.isDebugEnabled()) {
                     LOG.warn(
                        "Failed to update dependencies of viewsheet snapshot {}",
                        assetEntry.getPath(), ex);
                  }
                  else {
                     LOG.warn(
                        "Failed to update dependencies of viewsheet snapshot {}",
                        assetEntry.getPath());
                  }
               }
            }
         });
      }

      flushDependencyFiles(executors);
   }

   private void fixWorksheetsDependencies() {
      if(closed) {
         return;
      }

      XAssetEnumeration assetEnum = getXAssetEnumeration(WorksheetAsset.WORKSHEET);

      if(!(assetEnum instanceof WorksheetEnumeration)) {
         return;
      }

      AssetRepository engine = AssetUtil.getAssetRepository(false);
      ExecutorService executors = newFixedThreadPool();

      while(assetEnum.hasMoreElements()) {
         if(closed) {
            break;
         }

         WorksheetAsset wsAsset = (WorksheetAsset) assetEnum.nextElement();

         executors.execute(() -> {
            AbstractSheet sheet = wsAsset.getCurrentSheet(engine);

            if(sheet == null) {
               LOG.warn("Failed update dependencies of worksheet {}, not found", wsAsset.getPath());
            }
            else if(needUpdate(sheet.getVersion())) {
               AssetEntry assetEntry = wsAsset.getAssetEntry();

               try {
                  DependencyHandler.getInstance().updateSheetDependencies(sheet, assetEntry, true, true);
               }
               catch(Exception ex) {
                  if(LOG.isDebugEnabled()) {
                     LOG.warn(
                        "Failed to update dependencies of worksheet {}", assetEntry.getPath(), ex);
                  }
                  else {
                     LOG.warn("Failed to update dependencies of worksheet {}", assetEntry.getPath());
                  }
               }
            }
         });
      }

      flushDependencyFiles(executors);
   }

   private void fixScheduleTaskDependencies() {
      if(closed) {
         return;
      }

      ScheduleManager manager = ScheduleManager.getScheduleManager();

      for(ScheduleTask task : manager.getScheduleTasks()) {
         if(closed) {
            break;
         }

         DependencyHandler.getInstance().updateTaskDependencies(task, true);
      }
   }

   private void fixDatasourceDependencies() {
      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      String[] names = registry.getDataSourceFullNames();

      if(names == null || names.length == 0) {
         return;
      }

      Arrays.stream(names).forEach(dsName -> {
         fixDatasourceDependencies(dsName);
      });
   }

   private void fixDatasourceDependencies(String dsFullName) {
      if(StringUtils.isEmpty(dsFullName)) {
         return;
      }

      DataSourceRegistry registry = DataSourceRegistry.getRegistry();
      XDataModel model = registry.getDataModel(dsFullName);

      if(model != null) {
         for(String lname : model.getLogicalModelNames()) {
            XLogicalModel lg = model.getLogicalModel(lname);
            DependencyHandler.getInstance().updateModelDependencies(lg, true);
         }
      }

      XDataSource ds = registry.getDataSource(dsFullName);

      if(ds instanceof XMLADataSource) {
         XDomain domain = null;

         try {
            domain = XFactory.getRepository().getDomain(dsFullName);
         }
         catch(RemoteException ex) {
            LOG.error(ex.getMessage(), ex);
         }

         DependencyHandler.getInstance().updateCubeDomainDependencies(domain, true);
      }
   }

   /**
    * Get a cached thread pool.
    */
   private ExecutorService newFixedThreadPool() {
      return Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
   }

   /**
    * Each asset may need to modify several different dependency files, so in order to
    * avoid too frequent IO operations, we will cache the dependency objects and write
    * them into dependency files after finish each kind of assets maintain operation.
    *
    * @param executors thread pool for each kind of assets maintain operation.
    */
   private void flushDependencyFiles(ExecutorService executors) {
      if(closed) {
         executors.shutdownNow();
         return;
      }

      executors.shutdown();

      try {
         executors.awaitTermination(1, TimeUnit.HOURS);
      }
      catch(InterruptedException ex) {
         LOG.debug(catalog.getString("maintain.dependencies.threadInterrupted"), ex);
      }

      if(!closed) {
         DependencyHandler.getInstance().flushDependencyMap();
      }
   }

   /**
    * Rebuilds the entire dependency graph.
    *
    * @param timeout     the timeout for obtaining the update lock.
    * @param timeoutUnit the time unit for the timeout.
    */
   public boolean rebuild(long timeout, TimeUnit timeoutUnit) throws Exception {
      long timeoutAt = System.currentTimeMillis() + timeoutUnit.toMillis(timeout);
      boolean timedOut = true;

      while(System.currentTimeMillis() < timeoutAt) {
         lock.lock();

         try {
            if(state.get() != STATE_UPDATING) {
               state.set(STATE_UPDATING);
               timedOut = false;
               break;
            }
            else {
               Thread.sleep(1000L);
            }
         }
         finally {
            lock.unlock();
         }
      }

      if(timedOut) {
         throw new TimeoutException("Timed out waiting to rebuild dependencies");
      }
      else {
         rebuilding = true;

         try {
            updateAssetDependencies();
         }
         finally {
            rebuilding = false;
         }
      }

      return true;
   }

   /**
    * Fix asset dependencies.
    *
    * @param background {@code true} to run the update in a background thread; {@code false} to
    *                   block until the update is complete.
    */
   public void update(boolean background) {
      long currentState = state.get();

      if(currentState == STATE_UPDATING || currentState == STATE_UPDATED) {
         return;
      }

      boolean runInForeground = false;
      lock.lock();

      try {
         currentState = state.get();

         if(currentState == 0L) {
            if("true".equals(SreeEnv.getProperty(getUpdateKey()))) {
               state.set(STATE_UPDATED);
               return;
            }
         }
         else if(currentState == STATE_UPDATING || currentState == STATE_UPDATED) {
            return;
         }

         state.set(STATE_UPDATING);

         if(background) {
            thread = new GroupedThread(this::updateAssetDependencies, ThreadContext.getPrincipal());
            thread.setDaemon(true);
            thread.start();
         }
         else {
            runInForeground = true;
         }
      }
      finally {
         lock.unlock();
      }

      if(runInForeground) {
         updateAssetDependencies();
      }
   }

   private String getUpdateKey() {
      String version = FileVersions.REPORT;
      double currentVersion = Double.parseDouble(version);
      return currentVersion < 13.7 ? PROPERTY : PROPERTY_13_7;
   }

   private Catalog catalog = Catalog.getCatalog();
   private GroupedThread thread;
   private volatile boolean closed = false;
   private final DistributedLong state;
   private final Lock lock;
   private boolean rebuilding = false;

   // Work environments that are smaller than this version need to be identified as fix dependencies.
   private static final float FIX_DEPENDENCIES_MIN_VERSION = 13.7f;
   private static final Logger LOG = LoggerFactory.getLogger(UpdateAssetDependenciesHandler.class);

   private static final long STATE_UPDATING = 1L;
   private static final long STATE_UPDATED = 2L;

   private static final String PROPERTY = "source.dependencies.transformed";
   private static final String PROPERTY_13_7 = "source.dependencies.transformed.13_7";
   private static final String STATE_NAME = UpdateAssetDependenciesHandler.class.getName() + ".state";
   private static final String LOCK_NAME = UpdateAssetDependenciesHandler.class.getName() + ".lock";
}
