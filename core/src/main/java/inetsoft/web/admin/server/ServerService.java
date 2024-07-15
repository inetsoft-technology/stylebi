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

import inetsoft.report.internal.license.License;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ServerService extends MonitorLevelService implements StatusUpdater {
   @Autowired
   public ServerService(ServerClusterClient client){
      super(lowAttrs, new String[0], new String[0]);
      this.client = client;
      this.calculator = new ServerMetricsCalculator(client, StatusMetricsType.SERVER_METRICS);
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      serverServiceMessageListener = new ServerServiceMessageListener(cluster);
      cluster.addMessageListener(serverServiceMessageListener);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(serverServiceMessageListener);
      }
   }

   @Override
   public void updateStatus(long timestamp) {
      ServerMetrics oldMetrics = client.getMetrics(StatusMetricsType.SERVER_METRICS, null);
      String address = cluster.getLocalMember();
      ServerMetrics newMetrics = calculator.getServerMetrics(oldMetrics, timestamp, address);
      client.setMetrics(StatusMetricsType.SERVER_METRICS, newMetrics);
   }

   /**
    * Add a license key to the server.
    */
   public void addLicense(String license) throws Exception {
      LicenseManager.getInstance().addLicense(license);
   }

   /**
    * Get the count of license.
    */
   public int getLicenseCount() {
      return LicenseManager.getInstance().getInstalledLicenses().size();
   }

   /**
    * Get array of LicenseInfo.
    */
   public List<LicenseInfo> getLicenseInfos() {
      return LicenseManager.getInstance().getInstalledLicenses().stream()
         .map(License::key)
         .map(LicenseInfo::new)
         .collect(Collectors.toList());
   }

   /**
    * Remove a license key from the server.
    */
   public void removeLicense(String license) throws Exception {
      LicenseManager.getInstance().removeLicense(license);
   }

   /**
    * Get the server start date.
    */
   public Date getStartDate() {
      if(!isLevelQualified("startDate")) {
         return null;
      }

      ServerMetrics metrics = client.getMetrics(StatusMetricsType.SERVER_METRICS, null);

      if(metrics == null) {
         return null;
      }

      return new Date(metrics.startDate());
   }

   /**
    * Get server uptime.
    * This is an internal method.
    */
   public Long getUpTime() {
      Date startDate = getStartDate();

      if(startDate == null) {
         return 0L;
      }

      return System.currentTimeMillis() - startDate.getTime();
   }

   public List<CpuHistory> getCpuHistory(String clusterNode) {
      return getHistory(clusterNode, ServerMetricsCalculator.HistoryType.CPU);
   }

   public List<MemoryHistory> getMemoryHistory(String clusterNode) {
      return getHistory(clusterNode, ServerMetricsCalculator.HistoryType.MEMORY);
   }

   public List<GcHistory> getGcHistory(String clusterNode) {
      return getHistory(clusterNode, ServerMetricsCalculator.HistoryType.GC);
   }

   public long getMaxHeapSize(String clusterNode) {
      ServerClusterStatus status = getServerClusterStatus(clusterNode);

      if(status != null) {
         ServerMetrics metrics =
            client.getMetrics(StatusMetricsType.SERVER_METRICS, status.getAddress());

         if(metrics != null) {
            return metrics.maxHeapSize();
         }
      }

      return 0L;
   }

   public ServerHistory getHistory(String clusterNode) {
      return ServerHistory.builder()
         .cpu(getHistory(clusterNode, ServerMetricsCalculator.HistoryType.CPU))
         .memory(getHistory(clusterNode, ServerMetricsCalculator.HistoryType.MEMORY))
         .gc(getHistory(clusterNode, ServerMetricsCalculator.HistoryType.GC)).build();
   }

   private <T> List<T> getHistory(String clusterNode, ServerMetricsCalculator.HistoryType type) {
      ServerClusterStatus status = getServerClusterStatus(clusterNode);

      if(status == null) {
         return Collections.emptyList();
      }

      return getHistory(status, type);
   }

   private <T> List<T> getHistory(ServerClusterStatus status,
                                  ServerMetricsCalculator.HistoryType type)
   {
      if(status == null) {
         return Collections.emptyList();
      }

      String address = status.getAddress();
      Queue<T> history = client
         .getStatusHistory(StatusMetricsType.SERVER_METRICS, address, type.name());

      return history == null ? Collections.emptyList() : new ArrayList<>(history);
   }

   private ServerClusterStatus getServerClusterStatus(String clusterNode) {
      ServerClusterStatus status;

      if(clusterNode == null || clusterNode.isEmpty()) {
         status = client.getStatus();
      }
      else {
         status = client.getStatus(clusterNode);
      }

      return status;
   }

   /**
    * Gets a dump of the current stack traces for all threads.
    *
    * @param node the address of the cluster node.
    *
    * @return the stack dump.
    */
   public String getThreadDump(String node) throws Exception {
      if(node == null) {
         return serverServiceMessageListener.getThreadDump();
      }

      GetThreadDumpCompleteMessage msg =
         cluster.exchangeMessages(node, new GetThreadDumpMessage(),
                                  GetThreadDumpCompleteMessage.class);
      return msg.getThreadDump();
   }

   /**
    * Creates a heap dump for the VM in the cache directory.
    *
    * @param node the address of the cluster node.
    *
    * @return the identifier used to access the heap dump.
    */
   public String createHeapDump(String node) throws Exception {
      if(node == null) {
         return serverServiceMessageListener.createHeapDump();
      }

      CreateHeapDumpCompleteMessage msg =
         cluster.exchangeMessages(node, new CreateHeapDumpMessage(),
                                  CreateHeapDumpCompleteMessage.class);
      return msg.getId();
   }

   /**
    * Rotates the log file. When the log file is rotated, a backup of the
    * current log file is created and a new log file is started.
    */
   public void rotateLogFile() {
      LogManager.getInstance().rotateLogFile();
   }

   public String getThreadDump() {
      return serverServiceMessageListener.getThreadDump();
   }

   public boolean isHeapDumpComplete(String heapId, String clusterNode) throws Exception {
      return serverServiceMessageListener.isHeapDumpComplete(heapId, clusterNode);
   }

   public int getHeapDumpLength(String heapId, String clusterNode) throws Exception {
      return serverServiceMessageListener.getHeapDumpLength(heapId, clusterNode);
   }

   public int getHeapDumpLength(String heapId) {
      return serverServiceMessageListener.getHeapDumpLength(heapId);
   }

   public String createHeapDump() {
      return serverServiceMessageListener.createHeapDump();
   }

   public boolean isHeapDumpComplete(String id) {
      return serverServiceMessageListener.isHeapDumpComplete(id);
   }

   public byte[] getHeapDumpContent(String id, int offset, int length) {
      return serverServiceMessageListener.getHeapDumpContent(id, offset, length);
   }

   public void disposeHeapDump(String id) {
      serverServiceMessageListener.disposeHeapDump(id);
   }

   public byte[] getHeapDumpContent(String heapId, int offset,
                                    int toRead, String clusterNode) throws Exception
   {
      return serverServiceMessageListener.getHeapDumpContent(heapId, offset, toRead, clusterNode);
   }

   public void disposeHeapDump(String heapId, String clusterNode) throws Exception {
      serverServiceMessageListener.disposeHeapDump(heapId, clusterNode);
   }

   public String getStackTrace(long id) {
      return serverServiceMessageListener.getStackTrace(id);
   }

   private final ServerClusterClient client;
   private ServerServiceMessageListener serverServiceMessageListener;
   private Cluster cluster;
   private final ServerMetricsCalculator calculator;

   private static final String[] lowAttrs = {"startDate"};
}
