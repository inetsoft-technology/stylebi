/*
 * inetsoft-core - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package inetsoft.web.cluster;

import inetsoft.mv.MVTool;
import inetsoft.report.XSessionManager;
import inetsoft.sree.*;
import inetsoft.sree.internal.*;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.AuthenticationService;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XFactory;
import inetsoft.uql.XRepository;
import inetsoft.uql.service.XEngine;
import inetsoft.util.FileSystemService;
import inetsoft.util.swap.XSwapper;
import inetsoft.web.admin.schedule.SchedulerMonitoringService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;

@Component
@Lazy(false)
public class ClusterStatusController implements MessageListener {
   public ClusterStatusController(SchedulerMonitoringService schedulerMonitoringService)
   {
      this.schedulerMonitoringService = schedulerMonitoringService;
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();

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
      if(cluster != null) {
         cluster.removeMessageListener(this);

         if(mvListener != null) {
            cluster.removeMessageListener(mvListener);
         }
      }
   }

   @Scheduled(fixedRate = 60000L)
   public void updateStatus() {
      updateStatus(client.getStatus());
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof RestartMessage) {
         handleRestartMessage(sender);
      }
      else if(event.getMessage() instanceof PauseClusterMessage) {
         handlePause(sender, ((PauseClusterMessage) event.getMessage()).isPaused());
      }
      else if(event.getMessage() instanceof StartSchedulerMessage) {
         handleStartScheduler(sender);
      }
      else if(event.getMessage() instanceof StopSchedulerMessage) {
         handleStopScheduler(sender);
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
         XRepository repository = XFactory.getRepository();

         if(repository instanceof XEngine) {
            final String datasource = dataMessage.getDatasource();

            if(datasource != null) {
               ((XEngine) repository).removeMetaDataFiles(datasource);
            }
            else {
               repository.refreshMetaData();
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

      Cluster cluster = Cluster.getInstance();

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
         XRepository repository = XFactory.getRepository();

         if(repository instanceof XEngine) {
            final String datasource = dataMessage.getDatasource();

            if(datasource != null) {
               ((XEngine) repository).removeMetaCache(datasource);
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

      Cluster cluster = Cluster.getInstance();

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send clear metadata cache complete message", e);
      }
   }

   private void handleRestartMessage(String sender) {
      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(RestartMessage.ACTION);

      try {
         // @by henryh, 2004-10-28
         // save sree.home for SreeEnv.
         String home = SreeEnv.getProperty("sree.home");
         SreeEnv.clear();
         SreeEnv.setProperty("sree.home", home);

         handleRestart();
         message.setSuccess(true);
      }
      catch(Exception exc) {
         String msg = exc.getMessage();

         if(msg == null) {
            msg = exc.toString();
         }

         message.setFailMessage(msg);
         message.setSuccess(false);
         LOG.error("Failed to restart cluster node", exc);
      }

      Cluster cluster = Cluster.getInstance();

      try {
         cluster.sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send restart complete message", e);
      }
   }

   /**
    * Process a request to restart the service.
    */
   private void handleRestart() {
      restartLock.lock();

      // block incoming traffic
      try {
         //TODO SingletonManager.reset();
         RepletRegistry.clear();

         XSessionManager.restart();
         SecurityEngine.clear();

         PortalThemesManager.clear();
         AnalyticRepository engine = SUtil.getRepletRepository();

         if(engine instanceof AnalyticEngine) {
            ((AnalyticEngine) engine).dispose();
            ((AnalyticEngine) engine).init();
         }

         if(!"".equals(SreeEnv.getProperty("security.provider"))) {
            SecurityEngine.getSecurity().init();
         }

         AuthenticationService.getInstance().reset();
      }
      finally {
         restartLock.unlock();
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
         Cluster.getInstance().sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send cluster status message", e);
      }
   }

   private void handleStartScheduler(String sender) {
      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(StartSchedulerMessage.ACTION);

      try {
         schedulerMonitoringService.startScheduler();
         message.setSuccess(true);
      }
      catch(Exception e) {
         LOG.warn("Failed to start scheduler", e);
         message.setSuccess(false);
      }

      try {
         Cluster.getInstance().sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send start schedule complete", e);
      }
   }

   private void handleStopScheduler(String sender) {
      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(StopSchedulerMessage.ACTION);

      try {
         schedulerMonitoringService.stopScheduler();
         message.setSuccess(true);
      }
      catch(Exception e) {
         LOG.warn("Failed to stop scheduler", e);
         message.setSuccess(false);
      }

      try {
         Cluster.getInstance().sendMessage(sender, message);
      }
      catch(Exception e) {
         LOG.warn("Failed to send stop schedule complete", e);
      }
   }

   private void handleCleanCache(String sender) {
      FileSystemService.getInstance().clearCacheFiles(null);

      ServerClusterCompleteMessage message = new ServerClusterCompleteMessage();
      message.setAction(CleanCacheMessage.ACTION);
      message.setSuccess(true);

      try {
         Cluster.getInstance().sendMessage(sender, message);
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
         else if(!ignoreMemoryState && XSwapper.getMemoryState() <= XSwapper.BAD_MEM) {
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
   private Cluster cluster;
   private final SchedulerMonitoringService schedulerMonitoringService;
   private final ServerClusterClient client = new ServerClusterClient(true);
   private final ReentrantLock restartLock = new ReentrantLock();
   private static final Logger LOG = LoggerFactory.getLogger(ClusterStatusController.class);
}
