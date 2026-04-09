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
package inetsoft.sree.schedule;

import inetsoft.analytic.composition.ViewsheetEngine;
import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.WorksheetEngine;
import inetsoft.report.internal.LicenseException;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.IdentityID;
import inetsoft.util.*;
import inetsoft.util.health.HealthService;
import inetsoft.util.health.HealthStatus;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.schedule.ScheduleQueriesStatus;
import inetsoft.web.admin.schedule.ScheduleViewsheetsStatus;
import inetsoft.web.admin.server.ServerMetrics;
import inetsoft.web.admin.server.ServerMetricsCalculator;
import inetsoft.web.admin.viewsheet.ViewsheetModel;
import inetsoft.web.admin.viewsheet.ViewsheetThreadModel;
import inetsoft.web.cluster.ServerClusterClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Lazy
public class ScheduleServer extends UnicastRemoteObject implements Schedule {
   /**
    * Default constructor.
    */
   public ScheduleServer(Cluster cluster, HealthService healthService,
                         StatusDumpService statusDumpService) throws RemoteException
   {
      super();
      this.cluster = cluster;
      this.healthService = healthService;
      this.statusDumpService = statusDumpService;
   }

   /**
    * Start the schedule server.
    */
   @Override
   public void start() throws RemoteException {
      LOG.debug(
         "Received start server request on {} with config directory {}",
         Tool.getRmiIP(), ConfigurationContext.getContext().getHome());

      try {
         Scheduler.getScheduler().start();
         LOG.info("Schedule server started");
      }
      catch(LicenseException e) {
         LOG.error("There is a problem with the license key", e);
         System.exit(1);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to start scheduler", exc);
      }
   }

   /**
    * Stop the schedule server.
    */
   @Override
   public void stop() throws RemoteException {
      LOG.debug(
         "Received stop server request on {} with config directory {}",
         Tool.getRmiIP(), ConfigurationContext.getContext().getHome());

      new Thread(() -> {
         try {
            Scheduler.getScheduler().stop();
            LOG.info("Schedule server stopped");
         }
         catch(Exception exc) {
            LOG.error("Failed to shut down schedule server", exc);
         }

         System.exit(0);
      }).start();
   }

   /**
    * Run a task immediately.
    *
    * @param taskName the name of task
    */
   @Override
   public void runNow(String taskName) throws RemoteException {
      runNow(taskName, null);
   }

   @Override
   public void runNow(String task, IdentityID triggerUser) throws RemoteException {
      String taskNameForLog = SUtil.getTaskNameForLogging(task);
      MDC.put("SCHEDULE_TASK", taskNameForLog);
      LOG.debug(
         "Received start task [" + taskNameForLog + "] request on " +
            Tool.getRmiIP() + " with config directory " +
            ConfigurationContext.getContext().getHome());
      MDC.remove("SCHEDULE_TASK");
      Principal oldContextPrincipal = ThreadContext.getContextPrincipal();

      try {
         if(triggerUser != null) {
            ThreadContext.setContextPrincipal(SUtil.getPrincipal(triggerUser, Tool.getIP(), false));
         }

         Scheduler.getScheduler().runTask(task);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to startup task " + task, exc);
      }
      finally {
         ThreadContext.setContextPrincipal(oldContextPrincipal);
      }
   }

   /**
    * Stop a task immediately.
    * @param taskName the name of task
    */
   @Override
   public void stopNow(String taskName) throws RemoteException {
      LOG.debug(
         "Received cancel task [" + taskName + "] request on " +
         Tool.getRmiIP() + " with config directory " +
         ConfigurationContext.getContext().getHome());

      try {
         Scheduler.getScheduler().cancelTask(taskName);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to stop task " + taskName, exc);
      }
   }

   @Override
   public void addTask(ScheduleTask task) throws RemoteException {
      LOG.debug(
         "Received add task [" + task.getTaskId() + "] request on " +
         Tool.getRmiIP() + " with config directory " +
         ConfigurationContext.getContext().getHome());

      try {
         Scheduler.getScheduler().addTask(task);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to add task " + task.getTaskId(), exc);
      }
   }

   @Override
   public void removeTask(String taskName) throws RemoteException {
      LOG.debug(
         "Received remove task [" + taskName + "] request on " +
         Tool.getRmiIP() + " with config directory " +
         ConfigurationContext.getContext().getHome());

      try {
         Scheduler.getScheduler().removeTask(taskName);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to remove task " + taskName, exc);
      }
   }

   @Override
   public void removeTaskCacheOfOrg(String orgId) throws RemoteException {
      try {
         Scheduler.getScheduler().removeTaskCacheOfOrg(orgId);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to remove old org task cache " + orgId, exc);
      }
   }

   @Override
   public TaskActivity[] getScheduleActivities() throws RemoteException {
      LOG.debug(
         "Received get schedule activities request on " +
            Tool.getRmiIP() + " with config directory " +
            ConfigurationContext.getContext().getHome());

      TaskActivity[] activities;

      try {
         activities = Scheduler.getScheduler().getScheduleActivities().values()
            .toArray(new TaskActivity[0]);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to get schedule activities", exc);
      }

      return activities;
   }

   @Override
   public Date getStartTime() throws RemoteException {
      return Scheduler.getScheduler().getStartTime();
   }

   @Override
   public String ping() throws RemoteException {
      if(getStartTime() != null) {
         return "OK";
      }

      return Scheduler.getScheduler().getStartMessage();
   }

   @Override
   public HealthStatus getHealth() throws RemoteException {
      HealthStatus status = healthService.getStatus();

      if(status.isDown()) {
         statusDumpService.dumpStatus();
      }

      return status;
   }

   @Override
   public ServerMetrics getServerMetrics(ServerMetrics oldMetrics, long timestamp, String address) {
      return metricsCalculator.getServerMetrics(oldMetrics, timestamp, address);
   }

   @Override
   public ScheduleViewsheetsStatus getViewsheets() {
      ViewsheetService engine = ViewsheetEngine.getViewsheetEngine();
      List<ViewsheetModel> openViewsheets = new ArrayList<>();
      List<ViewsheetModel> executingViewsheets = new ArrayList<>();

      if(engine != null) {
         for(ScheduleViewsheetsStatus status : engine.invokeOnAll(new GetViewsheetsTask())) {
            openViewsheets.addAll(status.getOpenViewsheets());
            executingViewsheets.addAll(status.getExecutingViewsheets());
         }
      }

      ScheduleViewsheetsStatus viewsheets = new ScheduleViewsheetsStatus();
      viewsheets.setOpenViewsheets(openViewsheets);
      viewsheets.setExecutingViewsheets(executingViewsheets);
      return viewsheets;
   }

   private static final class GetViewsheetsTask implements ViewsheetService.Task<ScheduleViewsheetsStatus> {
      @Override
      public ScheduleViewsheetsStatus apply(ViewsheetService engine) throws Exception {
         List<ViewsheetModel> openViewsheets = new ArrayList<>();
         List<ViewsheetModel> executingViewsheets = new ArrayList<>();

         for(RuntimeViewsheet rvs : engine.getRuntimeViewsheets(null)) {
            Vector<?> threads = engine.getExecutingThreads(rvs.getID());
            List<ViewsheetThreadModel> threadModels;

            // synchronize on returned vector to prevent concurrent modification exception
            synchronized(threads) {
               threadModels = threads.stream()
                  .filter(o -> o instanceof WorksheetEngine.ThreadDef)
                  .map(o -> (WorksheetEngine.ThreadDef) o)
                  .map(t -> ViewsheetThreadModel.builder().from(t).build())
                  .collect(Collectors.toList());
            }

            openViewsheets.add(ViewsheetModel.builder()
                                  .from(rvs)
                                  .threads(threadModels)
                                  .state(ViewsheetModel.State.OPEN)
                                  .build());

            if(threads.size() > 0) {
               executingViewsheets.add(ViewsheetModel.builder()
                                          .from(rvs)
                                          .threads(threadModels)
                                          .state(ViewsheetModel.State.EXECUTING)
                                          .build());
            }
         }

         ScheduleViewsheetsStatus viewsheets = new ScheduleViewsheetsStatus();
         viewsheets.setOpenViewsheets(openViewsheets);
         viewsheets.setExecutingViewsheets(executingViewsheets);
         return viewsheets;
      }
   }

   @Override
   public ScheduleQueriesStatus getQueries() throws RemoteException {
      ScheduleQueriesStatus status = new ScheduleQueriesStatus();
      status.setQueries(QueryService.getQueries(QueryService.getQueryInfos(), true));
      return status;
   }

   /**
    * To test whether the remote schedule is running.
    */
   @Override
   public void test() throws RemoteException {
   }

   /**
    *Check whether the local schedule server is running.
    */
   public boolean isLocalServerRunning() {
      return Scheduler.getScheduler().isRunning();
   }

   @PreDestroy
   public void shutdown() {
      try {
         stop();
      }
      catch(RemoteException e) {
         LOG.error("Failed to stop schedule server during shutdown", e);
      }
   }

   @PostConstruct
   private void init() {
      metricsCalculator = new ServerMetricsCalculator(
         new ServerClusterClient(false, cluster), StatusMetricsType.SCHEDULE_METRICS);
   }

   private final Cluster cluster;
   private final HealthService healthService;
   private final StatusDumpService statusDumpService;
   private ServerMetricsCalculator metricsCalculator;
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleServer.class);
}
