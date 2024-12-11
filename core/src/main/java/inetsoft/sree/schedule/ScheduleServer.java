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
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.*;
import inetsoft.sree.internal.RMICallThread;
import inetsoft.sree.internal.SUtil;
import inetsoft.util.*;
import inetsoft.util.health.HealthService;
import inetsoft.util.health.HealthStatus;
import inetsoft.util.log.LogManager;
import inetsoft.web.admin.monitoring.StatusMetricsType;
import inetsoft.web.admin.query.QueryService;
import inetsoft.web.admin.schedule.*;
import inetsoft.web.admin.server.ServerMetrics;
import inetsoft.web.admin.server.ServerMetricsCalculator;
import inetsoft.web.admin.viewsheet.ViewsheetModel;
import inetsoft.web.admin.viewsheet.ViewsheetThreadModel;
import inetsoft.web.cluster.ServerClusterClient;
import org.slf4j.*;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@SingletonManager.Singleton(ScheduleServer.Reference.class)
public class ScheduleServer extends UnicastRemoteObject implements Schedule {
   /**
    * Default constructor.
    */
   @SuppressWarnings("WeakerAccess")
   private ScheduleServer() throws RemoteException {
      super();
   }

   public static ScheduleServer getInstance() {
      return SingletonManager.getInstance(ScheduleServer.class);
   }

   /**
    * Start the schedule server.
    */
   @Override
   public void start() throws RemoteException {
      LOG.debug(
         "Received start server request on " + Tool.getRmiIP() +
         " with config directory " + ConfigurationContext.getContext().getHome());

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
         "Received stop server request on " + Tool.getRmiIP() +
         " with config directory " + ConfigurationContext.getContext().getHome());

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
      String taskNameForLog = LicenseManager.getInstance().isEnterprise() ?
         taskName : SUtil.getTaskNameWithoutOrg(taskName);
      MDC.put("SCHEDULE_TASK", taskNameForLog);
      LOG.debug(
         "Received start task [" + taskNameForLog + "] request on " +
         Tool.getRmiIP() + " with config directory " +
         ConfigurationContext.getContext().getHome());
      MDC.remove("SCHEDULE_TASK");

      try {
         Scheduler.getScheduler().runTask(taskName);
      }
      catch(Exception exc) {
         throw new RemoteException("Unable to startup task " + taskName, exc);
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
      return HealthService.getInstance().getStatus();
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
         RuntimeViewsheet[] viewsheets = engine.getRuntimeViewsheets(null);

         for(RuntimeViewsheet rvs : viewsheets) {
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
      }

      ScheduleViewsheetsStatus viewsheets = new ScheduleViewsheetsStatus();
      viewsheets.setOpenViewsheets(openViewsheets);
      viewsheets.setExecutingViewsheets(executingViewsheets);
      return viewsheets;
   }

   @Override
   public ScheduleQueriesStatus getQueries() throws RemoteException {
      ScheduleQueriesStatus status = new ScheduleQueriesStatus();
      status.setQueries(QueryService.getQueries(QueryService.getQueryInfos(), true));
      return status;
   }

   /**
    * Create a new schedule process.
    * @param args args[0] can contain the identifier for the scheduler.
    */
   public static void main(String[] args) {
      System.setProperty("ScheduleServer", "true");
      ConfigurationContext.getContext().setHome(System.getProperty("sree.home"));
      System.setProperty("java.rmi.server.hostname", Tool.getRmiIP());
      LogManager.initializeForStartup();

      RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
      List<String> arguments = runtimeBean.getInputArguments();

      for(int i = 0; i < arguments.size() - 1; i++) {
         if("-jar".equals(arguments.get(i))) {
            String jar = arguments.get(i + 1);

            if(jar.matches("^bootstrap.*\\.jar$")) {
               FileSystemService.getInstance().getFile(jar).deleteOnExit();
            }

            break;
         }
      }

      // @by billh, for a unix server, it's likely that there is no graphics
      // env, here we set the default value of java.awt.headless as true, so
      // that the unix server could work as well
      if(OperatingSystem.isUnix()) {
         String val = System.getProperty("java.awt.headless");

         if(val == null || val.length() == 0) {
            System.setProperty("java.awt.headless", "true");
         }
      }

      Catalog.setCatalogGetter(UserEnv.getCatalogGetter());

      // log must be called after sree env has been initialized.
      LOG.info("Initializing schedule server");
      SreeEnv.setProperty("log.output.stderr", "false");

      int port = Integer.parseInt(SreeEnv.getProperty("scheduler.rmi.port"));
      // @by: ChrisSpagnoli feature1366221225905 2014-9-30
      // Support RMI invocation to "localhost", as alternative to the local IP
      String host = Tool.getRmiIP();
      String name = "//" + host + ':' + port + "/ScheduleServer";

      try {
         RMICallThread rct = new RMICallThread();
         Registry reg = rct.getRegistry(host, port, 15000L);
         LOG.debug("Init RMI Call thread.");

         if(reg == null) {
            throw new Exception();
         }
      }
      catch(Exception exc) {
         LOG.error("Failed to locate RMI registry, aborting", exc);
         System.exit(-1);
      }

      try {
         RMICallThread rct = new RMICallThread();
         Schedule schedule = (Schedule) rct.lookup(name, 1500L, false);
         LOG.debug("Look up RMI Call thread.");

         if(schedule != null) {
            LOG.error("Scheduler server is already running, aborting");
            System.exit(-1);
         }
      }
      catch(Exception ignore) {
      }

      try {
         ScheduleServer obj = ScheduleServer.getInstance();
         boolean success;
         RMICallThread rct = new RMICallThread();
         success = rct.rebind(name, obj, 30000);
         LOG.debug("Rebind RMI Call thread:" + success);

         if(!success) {
            LOG.debug("Start RMI registry on port: " + port);
            rct = new RMICallThread();
            rct.startRegistry(host, port, 30000);
            LOG.debug("Rebind RMI call thread");
            rct = new RMICallThread();
            success = rct.rebind(name, obj, 30000);

            if(!success) {
               throw new Exception();
            }
         }

         LOG.info("Schedule server bound in RMI registry.");
         // make sure the health services start tracking status
         HealthService.getInstance();
         obj.start();
      }
      catch(Exception exc) {
         LOG.error("Unable to bind schedule server to RMI registry.", exc);
         System.exit(-1);
      }
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

   public static final class Reference extends SingletonManager.Reference<ScheduleServer> {
      @Override
      public ScheduleServer get(Object... parameters) {
         if(instance == null) {
            lock.lock();

            try {
               if(instance == null) {
                  try {
                     instance = new ScheduleServer();
                  }
                  catch(RemoteException e) {
                     LOG.error("Failed to initialize Schedule", e);
                  }
               }
            }
            finally {
               lock.unlock();
            }
         }

         return instance;
      }

      @Override
      public void dispose() {
         lock.lock();

         try {
            if(instance != null) {
               instance.stop();
               instance = null;
            }
         }
         catch(RemoteException e) {
            LOG.error("Failed to stop Schedule", e);
         }
         finally {
            lock.unlock();
         }
      }

      private ScheduleServer instance;
      private final Lock lock = new ReentrantLock();
   }

   private ServerMetricsCalculator metricsCalculator =
      new ServerMetricsCalculator(new ServerClusterClient(), StatusMetricsType.SCHEDULE_METRICS);
   private static final Logger LOG = LoggerFactory.getLogger(ScheduleServer.class);
}
