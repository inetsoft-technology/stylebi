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
package inetsoft.web.cluster;

import inetsoft.mv.MVTool;
import inetsoft.sree.*;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.XEngine;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import inetsoft.web.portal.controller.database.DataSourceService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
@Lazy(false)
public class ClusterStatusController implements MessageListener {
   @Autowired
   public ClusterStatusController(SchedulerMonitoringService schedulerMonitoringService,
                                  DataSourceService dataSourceService,
                                  Cluster cluster,
                                  SecurityEngine securityEngine,
                                  AuthenticationService authenticationService,
                                  FileSystemService fileSystemService,
                                  XRepository repository, XSwapper swapper)
   {
      this.schedulerMonitoringService = schedulerMonitoringService;
      this.dataSourceService = dataSourceService;
      this.cluster = cluster;
      this.securityEngine = securityEngine;
      this.authenticationService = authenticationService;
      this.fileSystemService = fileSystemService;
      this.repository = repository;
      this.swapper = swapper;
   }

   @PostConstruct
   public void addListener() {

      (new Thread(() -> {
         cluster.addMessageListener(ClusterStatusController.this);
         updateStatus();

         mvListener = MVTool.newMVMessageHandler();

         if(mvListener != null) {
            cluster.addMessageListener(mvListener);
         }
      })).start();
   }

   @PreDestroy
   public void removeListener() {
      try {
         cluster.removeMessageListener(this);

         if(mvListener != null) {
            cluster.removeMessageListener(mvListener);
         }
      }
      catch(Exception e) {
         LOG.debug("Failed to remove listeners during shutdown", e);
      }
   }

   @Scheduled(fixedRate = 60000L)
   public void updateStatus() {
      updateStatus(client.getStatus());
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof PauseClusterMessage) {
         handlePause(sender, ((PauseClusterMessage) event.getMessage()).isPaused());
      }
      else if(event.getMessage() instanceof CleanCacheMessage) {
         handleCleanCache(sender);
      }
      else if(event.getMessage() instanceof RefreshMetaDataMessage) {
         handleRefreshMetaDataMessage(sender, (RefreshMetaDataMessage) event.getMessage());
      }
      else if(event.getMessage() instanceof ClearLocalNodeMetaDataCacheMessage) {
         handleReloadMetaDataMessage(sender, (ClearLocalNodeMetaDataCacheMessage) event.getMessage());
      }
   }

   private void handleRefreshMetaDataMessage(String sender, RefreshMetaDataMessage dataMessage) {
      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(RefreshMetaDataMessage.ACTION);

      try {
         if(repository instanceof XEngine) {
            final String datasource = dataMessage.getDatasource();

            if(datasource != null) {
               ((XEngine) repository).removeMetaDataFiles(dataMessage.getOrgId(), datasource);
               // Also clear the DefaultMetaDataProvider cache so logical model sees updated tables
               dataSourceService.clearAllPartitionMetaDataCache(datasource);
            }
            else {
               // Use clearLocalMetaDataCache() instead of refreshMetaData() to avoid
               // broadcasting messages back to cluster nodes, which would cause an
               // infinite message loop.
               ((XEngine) repository).clearLocalMetaDataCache();
            }
         }

         message.setSuccess(true);
      }
      catch(Exception exception) {
         String msg = exception.getMessage();

         if(msg == null) {
            msg = exception.toString();
         }

         message.setSuccess(false);
         message.setFailMessage(msg);
         LOG.error("Unable to remove meta data files", exception);
      }

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send refresh metadata complete message", e);
      }
   }

   private void handleReloadMetaDataMessage(String sender, ClearLocalNodeMetaDataCacheMessage dataMessage) {
      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(RefreshMetaDataMessage.ACTION);

      try {
         if(repository instanceof XEngine) {
            final String datasource = dataMessage.getDatasource();

            if(datasource != null) {
               ((XEngine) repository).removeMetaCache(datasource, dataMessage.getOrgId());
               // Also clear the DefaultMetaDataProvider cache so logical model sees updated tables
               dataSourceService.clearAllPartitionMetaDataCache(datasource);
            }
         }

         message.setSuccess(true);
      }
      catch(Exception exception) {
         String msg = exception.getMessage();

         if(msg == null) {
            msg = exception.toString();
         }

         message.setSuccess(false);
         message.setFailMessage(msg);
         LOG.error("Unable to clear the meta data cache", exception);
      }

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send clear metadata cache complete message", e);
      }
   }

   private void handlePause(String sender, boolean paused) {
      ServerClusterStatus status = new ServerClusterStatus();
      status.setPaused(paused);
      updateStatus(status);

      try {
         ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
         message.setAction(PauseClusterMessage.ACTION);
         message.setSuccess(true);
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send cluster status message", e);
      }
   }

   private void handleCleanCache(String sender) {
      this.fileSystemService.clearCacheFiles(null);

      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(CleanCacheMessage.ACTION);
      message.setSuccess(true);

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send clean cache complete", e);
      }
   }

   private void updateStatus(ServerClusterStatus current) {
      boolean ignoreMemoryState = "true".equals(SreeEnv.getProperty("ignore.swapper.memory.state"));
      boolean paused = current != null && current.isPaused();
      client.setStatus(status -> {
         if(paused) {
            status.setPaused(true);
            status.setStatus(ServerClusterStatus.Status.PAUSED);
         }
         else if(!ignoreMemoryState && swapper.getMemoryState() <= XSwapper.BAD_MEM) {
            status.setPaused(false);
            status.setStatus(ServerClusterStatus.Status.BUSY);
         }
         else {
            status.setPaused(false);
            status.setStatus(ServerClusterStatus.Status.OK);
         }
      });
   }

   private MessageListener mvListener;
   private final Cluster cluster;
   private final SecurityEngine securityEngine;
   private final SchedulerMonitoringService schedulerMonitoringService;
   private final DataSourceService dataSourceService;
   private final AuthenticationService authenticationService;
   private final FileSystemService fileSystemService;
   private final XRepository repository;
   private final XSwapper swapper;
   private final ServerClusterClient client = new ServerClusterClient(true);
   private final ReentrantLock restartLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(ClusterStatusController.class);
}
