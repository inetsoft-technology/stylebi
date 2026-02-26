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
package inetsoft.web.admin.cache;

import inetsoft.report.composition.RuntimeSheet;
import inetsoft.report.internal.paging.PageGroup;
import inetsoft.sree.SreeEnv;
import inetsoft.uql.table.XTableColumn;
import inetsoft.uql.table.XTableFragment;
import inetsoft.util.Catalog;
import inetsoft.util.swap.*;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.cluster.ServerClusterClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CacheService extends MonitorLevelService implements XSwappableMonitor, StatusUpdater {
   @Autowired
   public CacheService(ServerClusterClient clusterClient) {
      super(lowAttrs, medAttrs, highAttrs);
      this.clusterClient = clusterClient;
   }

   @PostConstruct
   public void addListeners() {
      XSwapper.registerMonitor(this);
   }

   @PreDestroy
   public void removeListeners() {
      XSwapper.deregisterMonitor(this);
   }

   @Override
   public void updateStatus(long timestamp) {
      AtomicInteger sheetMemory = new AtomicInteger(0);
      AtomicInteger sheetDisk = new AtomicInteger(0);
      AtomicInteger dataMemory = new AtomicInteger(0);
      AtomicInteger dataDisk = new AtomicInteger(0);

      for(XSwappable swp : XSwapper.getAllSwappables()) {
         if(swp instanceof RuntimeSheet.XSwappableSheet || swp instanceof PageGroup) {
            calculateXFragmentCount(swp, sheetMemory, sheetDisk);
         }
         else if(swp instanceof XIntFragment || swp instanceof XObjectFragment) {
            calculateXFragmentCount(swp, dataMemory, dataDisk);
         }
         else if(swp instanceof XTableFragment) {
            calculateXTableFragmentCount((XTableFragment) swp, dataMemory, dataDisk);
         }
      }

      CacheMetrics.Builder builder = CacheMetrics.builder();
      CacheState.Builder state = CacheState.builder();
      CacheMetrics oldMetrics = clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, null);

      if(oldMetrics != null) {
         builder.from(oldMetrics);

         state
            .sheetMemoryCount(oldMetrics.sheetMemoryCount())
            .sheetDiskCount(oldMetrics.sheetDiskCount())
            .sheetBytesRead(oldMetrics.sheetBytesRead())
            .sheetBytesWritten(oldMetrics.sheetBytesWritten())
            .dataMemoryCount(oldMetrics.dataMemoryCount())
            .dataDiskCount(oldMetrics.dataDiskCount())
            .dataBytesRead(oldMetrics.dataBytesRead())
            .dataBytesWritten(oldMetrics.dataBytesWritten());

         builder.sheetHits(oldMetrics.sheetHits() + counts.sheetHits.getAndSet(0));
         builder.sheetMisses(oldMetrics.sheetMisses() + counts.sheetMisses.getAndSet(0));
         builder.sheetBytesRead(counts.sheetRead.getAndSet(0));
         builder.sheetBytesWritten(counts.sheetWritten.getAndSet(0));
         builder.dataHits(oldMetrics.dataHits() + counts.dataHits.getAndSet(0));
         builder.dataMisses(oldMetrics.dataMisses() + counts.dataMisses.getAndSet(0));
         builder.dataBytesRead(counts.dataRead.getAndSet(0));
         builder.dataBytesWritten(counts.dataWritten.getAndSet(0));
      }
      else {
         builder.sheetHits(counts.sheetHits.getAndSet(0));
         builder.sheetMisses(counts.sheetMisses.getAndSet(0));
         builder.sheetBytesRead(counts.sheetRead.getAndSet(0));
         builder.sheetBytesWritten(counts.sheetWritten.getAndSet(0));
         builder.dataHits(counts.dataHits.getAndSet(0));
         builder.dataMisses(counts.dataMisses.getAndSet(0));
         builder.dataBytesRead(counts.dataRead.getAndSet(0));
         builder.dataBytesWritten(counts.dataWritten.getAndSet(0));
      }

      state.time(timestamp);
      clusterClient.addStatusHistory(StatusMetricsType.CACHE_METRICS, null, null, state.build());

      builder
         .sheetMemoryCount(sheetMemory.get())
         .sheetDiskCount(sheetDisk.get())
         .dataMemoryCount(dataMemory.get())
         .dataDiskCount(dataDisk.get());
      clusterClient.setMetrics(StatusMetricsType.CACHE_METRICS, builder.build());
   }

   /**
    * Calculate the count of the XIntFragment or XObjectFragment.
    */
   private void calculateXFragmentCount(XSwappable data, AtomicInteger dataMemory,
                                        AtomicInteger dataDisk)
   {
      if(!data.isSwappable()) {
         return;
      }

      if(data.isValid()) {
         dataMemory.incrementAndGet();
      }
      else {
         dataDisk.incrementAndGet();
      }
   }

   /**
    * Calculate the count of the column in the table fragment.
    */
   private void calculateXTableFragmentCount(XTableFragment tbl, AtomicInteger dataMemory,
                                             AtomicInteger dataDisk)
   {
      if(tbl.isDisposed()) {
         return;
      }

      if(tbl.isValid()) {
         dataMemory.addAndGet(tbl.getColumns().length);
      }
      else {
         for(XTableColumn column : tbl.getColumns()) {
            if(!column.isSerializable()) {
               dataMemory.incrementAndGet();
            }
            else {
               dataDisk.incrementAndGet();
            }
         }
      }
   }

   /**
    * Get Data Cache Size of the manager.
    * @return the size of Data Cache Size.
    */
   public long getDataCacheSize() {
      return Long.parseLong(SreeEnv.getProperty("query.cache.limit"));
   }

   /**
    * Set Data Cache Size to the manager.
    * @param size the Data Cache Size.
    */
   public void setDataCacheSize(long size) throws Exception {
      if(size < 0) {
         throw new Exception(Catalog.getCatalog().getString(
            "cacheManager.invalidValue"));
      }

      SreeEnv.setProperty("query.cache.limit", size + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "Data Size"));
      }
   }

   /**
    * Set Data Cache File Size to the manager.
    * @param dataCacheSize the Data Cache File Size.
    */
   public void setDataCacheFileSize(long dataCacheSize) throws Exception {
      long val = dataCacheSize * 1024 * 1024;

      if(val < 0) {
         throw new Exception(Catalog.getCatalog().getString(
            "cacheManager.invalidValue"));
      }

      SreeEnv.setProperty("data.cache.file.limit", val + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "Data Cache File Size"));
      }
   }

   /**
    * Get Data Cache File Size of the manager.
    * @return the dataCacheSize value of Data Cache File Size.
    */
   public long getDataCacheFileSize() {
      long value =
         Long.parseLong(SreeEnv.getProperty("data.cache.file.limit"));

      value = value >= Long.MAX_VALUE ? 0 : value;
      return value / 1024 / 1024;
   }

   /**
    * Get Data Cache Timeout of the manager.
    * @return the timeout of Data Cache Timeout.
    */
   public long getDataCacheTimeout() {
      return Long.parseLong(SreeEnv.getProperty("query.cache.timeout"));
   }

   /**
    * Set Data Cache Timeout to the manager.
    * @param timeout the Data Cache Timeout.
    */
   public void setDataCacheTimeout(long timeout) throws Exception {
      if(timeout < 0) {
         throw new Exception(Catalog.getCatalog().getString(
            "cacheManager.invalidValue"));
      }

      SreeEnv.setProperty("query.cache.timeout", timeout + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "Data Cache Timeout"));
      }
   }

   /**
    * Get Workset Size of the manager.
    * @return the worksetSize value of Workset Size.
    */
   public int getWorksetSize() {
      return Integer.parseInt(SreeEnv.getProperty("replet.cache.workset"));
   }

   /**
    * Set Workset Size to the manager.
    * @param worksetSize the Workset Size.
    */
   public void setWorksetSize(int worksetSize) throws Exception {
      if(worksetSize < 5 || worksetSize > 128) {
         throw new Exception(Catalog.getCatalog().getString(
            "em.config.performance.worksetSize"));
      }

      SreeEnv.setProperty("replet.cache.workset", worksetSize + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "Workset Size"));
      }
   }

   /**
    * Check if is Data Set Caching Enabled.
    * @return <tt>true</tt> if not enabled.
    */
   public boolean isDataSetCachingEnabled() {
      return Boolean.parseBoolean(SreeEnv.getProperty("query.cache.data"));
   }

   /**
    * Set the enabled status of this Data Set Caching.
    * @param enabled false to enable the Data Set Caching.
    */
   public void setDataSetCachingEnabled(boolean enabled) throws Exception {
      SreeEnv.setProperty("query.cache.data", enabled + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "the enabled status of this Data Set Caching"));
      }
   }

   /**
    * Check if is Security Caching Enabled.
    * @return <tt>true</tt> if not enabled.
    */
   public boolean isSecurityCachingEnabled() {
      return Boolean.parseBoolean(SreeEnv.getProperty("security.cache"));
   }

   /**
    * Set the enabled status of this Security Caching.
    * @param enabled false to enable the Security Caching.
    */
   public void setSecurityCachingEnabled(boolean enabled) throws Exception {
      SreeEnv.setProperty("security.cache", enabled + "");

      try {
         SreeEnv.save();
      }
      catch(Exception e) {
         throw new Exception(Catalog.getCatalog().getString(
            "monitor.setFail", "the enabled status of this Security Caching"));
      }
   }

   int getDataMemoryCount() {
      CacheMetrics metrics = clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, null);
      return metrics == null ? 0 : metrics.dataMemoryCount();
   }

   int getDataDiskCount() {
      CacheMetrics metrics = clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, null);
      return metrics == null ? 0 : metrics.dataDiskCount();
   }

   long getDataBytesRead() {
      CacheMetrics metrics = clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, null);
      return metrics == null ? 0 : metrics.dataBytesRead();
   }

   long getDataBytesWritten() {
      CacheMetrics metrics = clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, null);
      return metrics == null ? 0 : metrics.dataBytesWritten();
   }

   /**
    * Get the number of bytes which has been restored.
    * @param type sheet or data.
    */
   public long getRead(int type) {
      CacheMetrics metrics = clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, null);

      if(metrics == null) {
         return 0L;
      }

      return switch(type) {
         case CacheInfo.SHEET -> metrics.sheetBytesRead();
         case CacheInfo.DATA -> metrics.dataBytesRead();
         default -> throw new IllegalArgumentException(catalog.getString(
            "The cache type must be sheet or data."));
      };
   }

   /**
    * Add the number of bytes which has been restored.
    * @param num the number of bytes.
    * @param type sheet or data.
    */
   @Override
   public void countRead(long num, int type) {
      if(num > 0) {
         if(type == SHEET) {
            counts.sheetRead.addAndGet(num);
         }
         else {
            counts.dataRead.addAndGet(num);
         }
      }
   }

   /**
    * Add the number of bytes which has been swapped out.
    * @param num the number of bytes.
    * @param type report or data.
    */
   @Override
   public void countWrite(long num, int type) {
      if(num > 0) {
         if(type == SHEET) {
            counts.sheetWritten.addAndGet(num);
         }
         else {
            counts.dataWritten.addAndGet(num);
         }
      }
   }

   /**
    * Count the hits.
    * @param hits the hits.
    */
   @Override
   public void countHits(int type, int hits) {
      if(hits > 0) {
         if(type == SHEET) {
            counts.sheetHits.addAndGet(hits);
         }
         else {
            counts.dataHits.addAndGet(hits);
         }
      }
   }

   /**
    * Count the misses.
    * @param misses the misses.
    */
   @Override
   public void countMisses(int type, int misses) {
      if(misses > 0) {
         if(type == SHEET) {
            counts.sheetMisses.addAndGet(misses);
         }
         else {
            counts.dataMisses.addAndGet(misses);
         }
      }
   }

   Collection<CacheState> getCacheHistory() {
      return getCacheHistory(null);
   }

   public Collection<CacheState> getCacheHistory(String clusterNode) {
      Queue<CacheState> history = clusterClient
         .getStatusHistory(StatusMetricsType.CACHE_METRICS, clusterNode, null);

      return history == null ? Collections.emptyList() : new ArrayDeque<>(history);
   }

   List<CacheMonitoringTableModel> getDataGrid(String address) {
      CacheMetrics metrics =
         clusterClient.getMetrics(StatusMetricsType.CACHE_METRICS, address);

      if(metrics == null) {
         return Collections.emptyList();
      }

      List<CacheMonitoringTableModel> grid = new ArrayList<>();

      grid.add(CacheMonitoringTableModel.builder()
         .location(MEMORY)
         .count(Integer.toString(metrics.dataMemoryCount()))
         .read(String.format("%.1f (Mb)", metrics.dataBytesRead() / 1024D / 1024D))
         .hits(String.format("%d (Hits)", metrics.dataHits()))
         .build());

      grid.add(CacheMonitoringTableModel.builder()
         .location(DISK)
         .count(Integer.toString(metrics.dataDiskCount()))
         .read(String.format("%.1f (Mb)", metrics.dataBytesWritten() / 1024D / 1024D))
         .hits(String.format("%d (Misses)", metrics.dataMisses()))
         .build());

      return grid;
   }

   private final Catalog catalog = Catalog.getCatalog();
   private final ServerClusterClient clusterClient;
   private final CacheCounts counts = new CacheCounts();

   private static final String[] highAttrs = {"misses", "hits"};
   private static final String[] medAttrs = {"written", "read"};
   private static final String[] lowAttrs = {"type", "location", "count"};

   private static final class CacheCounts {
      final AtomicInteger sheetHits = new AtomicInteger(0);
      final AtomicInteger sheetMisses = new AtomicInteger(0);
      final AtomicLong sheetRead = new AtomicLong(0L);
      final AtomicLong sheetWritten = new AtomicLong(0L);
      final AtomicInteger dataHits = new AtomicInteger(0);
      final AtomicInteger dataMisses = new AtomicInteger(0);
      final AtomicLong dataRead = new AtomicLong(0L);
      final AtomicLong dataWritten = new AtomicLong(0L);
   }
}
