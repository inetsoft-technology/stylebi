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
package inetsoft.web.admin.server;

import inetsoft.sree.SreeEnv;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import inetsoft.web.cluster.ServerClusterClient;

import java.lang.management.*;
import java.time.OffsetDateTime;
import java.util.*;

public class ServerMetricsCalculator {
   public ServerMetricsCalculator(ServerClusterClient client, StatusMetricsType type) {
      this.client = client;
      this.type = type;
   }

   public ServerMetrics getServerMetrics(ServerMetrics oldMetrics, long timestamp, String address) {
      ServerMetrics.Builder builder = ServerMetrics.builder();
      int maxHistory = Integer.parseInt(SreeEnv.getProperty("monitor.dataset.size"));

      if(oldMetrics != null) {
         builder.from(oldMetrics);
      }

      updateCpuMetrics(oldMetrics, timestamp, maxHistory, builder, address);
      updateMemoryMetrics(timestamp, maxHistory, builder, address);
      updateGcMetrics(oldMetrics, timestamp, maxHistory, builder, address);
      builder.timeZone(OffsetDateTime.now().getOffset());

      return builder.build();
   }

   private void updateCpuMetrics(ServerMetrics oldMetrics, long now, int maxHistory,
                                 ServerMetrics.Builder builder, String address)
   {
      Queue<CpuHistory> cpuHistories = client
         .getStatusHistory(type, address, HistoryType.CPU);
      long prevUpTime = 0L;
      long prevProcessCpuTime = 0L;

      if(oldMetrics != null) {
         prevUpTime = oldMetrics.upTime();
         prevProcessCpuTime = oldMetrics.cpuTime();
      }

      RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
      long upTime = runtimeBean.getUptime();

      OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
      long processCpuTime = -1;

      if(osBean instanceof com.sun.management.OperatingSystemMXBean) {
         processCpuTime = ((com.sun.management.OperatingSystemMXBean) osBean).getProcessCpuTime();
      }

      int processCnt = osBean.getAvailableProcessors();
      float ratio;

      if(prevUpTime > 0L && upTime > prevUpTime) {
         long cpuTimeDiff = processCpuTime - prevProcessCpuTime;
         cpuTimeDiff /= 1000000L; // cpu time is in nanoseconds
         long upTimeDiff = upTime - prevUpTime;
         ratio = Math.min(99F, (float) cpuTimeDiff /
            ((float) upTimeDiff * (float) processCnt));
      }
      else {
         ratio = 0F;
      }

      cpuHistories.add(CpuHistory.builder()
         .timestamp(now)
         .cpuPercent(ratio)
         .build());

      while(cpuHistories.size() > maxHistory) {
         cpuHistories.poll();
      }

      builder.upTime(upTime);
      builder.cpuTime(processCpuTime);

      if(oldMetrics == null) {
         builder.startDate(now - upTime);
      }
   }

   private void updateMemoryMetrics(long now, int maxHistory, ServerMetrics.Builder builder,
                                    String address)
   {
      Queue<MemoryHistory> memoryHistories =
         client.getStatusHistory(type, address, HistoryType.MEMORY);
      MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
      builder.maxHeapSize(memoryBean.getHeapMemoryUsage().getMax());
      memoryHistories.add(MemoryHistory.builder()
         .timestamp(now)
         .usedMemory(memoryBean.getHeapMemoryUsage().getUsed())
         .build());

      while(memoryHistories.size() > maxHistory) {
         memoryHistories.poll();
      }
   }

   private void updateGcMetrics(ServerMetrics prevServerMetrics, long now, int maxHistory,
                                ServerMetrics.Builder builder, String address)
   {
      List<GcMetrics> newMetrics = new ArrayList<>();
      Map<String, GcMetrics> oldMetrics = new HashMap<>();
      Queue<GcHistory> history =
         client.getStatusHistory(type, address, HistoryType.GC);
      long prevTotalCount = 0L;
      long prevTotalTime = 0L;

      if(prevServerMetrics != null) {
         for(GcMetrics metrics : prevServerMetrics.gcMetrics()) {
            oldMetrics.put(metrics.name(), metrics);
            prevTotalCount += metrics.collectionCount();
            prevTotalTime += metrics.collectionTime();
         }
      }

      for(GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
         oldMetrics.remove(bean.getName());
         newMetrics.add(GcMetrics.builder()
            .name(bean.getName())
            .collectionCount(bean.getCollectionCount())
            .collectionTime(bean.getCollectionTime())
            .build());
      }

      if(!oldMetrics.isEmpty()) {
         // collapse old (dead) gc instances into single, cumulative entry
         long oldCollectionCount = 0L;
         long oldCollectionTime = 0L;

         for(GcMetrics metrics : oldMetrics.values()) {
            oldCollectionCount += metrics.collectionCount();
            oldCollectionTime += metrics.collectionTime();
         }

         newMetrics.add(GcMetrics.builder()
            .name(OLD_COLLECTORS)
            .collectionCount(oldCollectionCount)
            .collectionTime(oldCollectionTime).build());
      }

      long newTotalCount = 0L;
      long newTotalTime = 0L;

      for(GcMetrics metrics : newMetrics) {
         newTotalCount += metrics.collectionCount();
         newTotalTime += metrics.collectionTime();
      }

      if(newTotalCount < prevTotalCount) {
         newTotalCount = prevTotalCount;
      }

      if(newTotalTime < prevTotalTime) {
         newTotalTime = prevTotalTime;
      }

      history.add(GcHistory.builder()
         .timestamp(now)
         .collectionCount(newTotalCount - prevTotalCount)
         .collectionTime(newTotalTime - prevTotalTime)
         .build());

      while(history.size() > maxHistory) {
         history.poll();
      }

      builder.collectionCount(newTotalCount);
      builder.collectionTime(newTotalTime);
      builder.gcMetrics(newMetrics);
   }

   public enum HistoryType {
      CPU,
      MEMORY,
      GC
   }

   private final StatusMetricsType type;
   private final ServerClusterClient client;
   private static final String OLD_COLLECTORS = ServerService.class.getName() + ".oldCollectors";
}
