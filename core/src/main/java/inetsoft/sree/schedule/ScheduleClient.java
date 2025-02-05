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

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.*;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.util.*;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.health.HealthStatus;
import inetsoft.web.admin.schedule.ScheduleQueriesStatus;
import inetsoft.web.admin.schedule.ScheduleViewsheetsStatus;
import inetsoft.web.admin.server.ServerMetrics;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.*;
import java.util.stream.Stream;

/**
 * Class providing client-side access to the schedule server.
 */
@SuppressWarnings("WeakerAccess")
public class ScheduleClient {
   /**
    * Starts the schedule server with a timeout of 5 minutes.
    *
    * @throws Exception if the scheduler could not be started.
    */
   public void startServer() throws Exception {
      startServer(5L, TimeUnit.MINUTES);
   }

   /**
    * Starts the schedule server.
    *
    * @param timeout     the amount of time to wait for the scheduler to start.
    * @param timeoutUnit the units for <i>timeout</i>.
    *
    * @throws Exception if the scheduler could not be started.
    */
   public void startServer(long timeout, TimeUnit timeoutUnit) throws Exception {
      // make sure the server hasn't been started already
      if(isReady(getSchedulerServer())) {
         if(SreeEnv.getProperty("scheduler.restart.auto").equals("true")) {
            LOG.debug("Auto restart scheduler");
            stopServer();
         }
         else {
            return;
         }
      }

      String cp = System.getProperty("java.class.path", "");
      String cp2 = SreeEnv.getProperty("scheduler.classpath",
                                       SUtil.getApplicationClasspath());
      String javahome = System.getProperty("java.home");
      String separator = System.getProperty("file.separator");
      String javacmd = "java";

      if(javahome != null && separator != null) {
         javacmd = javahome + separator + "bin" + separator + "java";
      }

      if(OperatingSystem.isWindows() && javacmd.indexOf(" ") > 0) {
         javacmd = "\"" + javacmd + "\"";
      }

      cp = cp2.trim() + File.pathSeparator + cp.trim();

      String opts = SreeEnv.getProperty("schedule.java.opts");
      String sreeHome;

      // @by billh, for a unix server, it's likely that there is no graphics
      // env, here we set the default value of java.awt.headless as true, so
      // that the unix server could work as well
      if(OperatingSystem.isUnix()) {
         String val = System.getProperty("java.awt.headless");

         if(val == null || val.length() == 0) {
            System.setProperty("java.awt.headless", "true");
         }
      }

      // @by henryh, add quotation marks to sree.home for Windows
      // @by larryl, single quote seems to be passed to java literally and
      // causes the sree.home to be wrong. must use double quote
      // @by stephenwebster, fix bug1379901694751. Quote entire argument
      // otherwise, could be interpreted incorrectly by Java's ProcessBuilder
      if(OperatingSystem.isWindows()) {
         sreeHome = "\"" + "-Dsree.home=" +
            SreeEnv.getProperty("sree.home") + "\"";
      }
      else {
         sreeHome = "-Dsree.home=" + SreeEnv.getProperty("sree.home");
      }

      String headless =
         System.getProperty("java.awt.headless", "false").equals("true") ?
            "-Djava.awt.headless=true" : "-Djava.awt.headless=false";
      List<String> args = new ArrayList<>();
      args.add(javacmd);

      if(!opts.contains("-Xms")) {
         args.add("-Xms" + SreeEnv.getProperty("schedule.memory.min") + "m");
      }

      if(!opts.contains("-Xmx")) {
         args.add("-Xmx" + SreeEnv.getProperty("schedule.memory.max") + "m");
      }

      if(!opts.contains("UseGCOverheadLimit")) {
         args.add("-XX:-UseGCOverheadLimit");
      }

      if(!opts.contains("-Duser.timezone=")) {
         Calendar calendar = Calendar.getInstance();
         String timezone = calendar.getTimeZone().getID();
         args.add("-Duser.timezone=" + timezone);
      }

      // Bug #58971, use the same locale providers as the server
      if(!opts.contains("-Djava.locale.providers=")) {
         String localeProviders = System.getProperty("java.locale.providers", "COMPAT,SPI");
         args.add("-Djava.locale.providers=" + localeProviders);
      }

      // make sure schedule is bound to the same network interface that the server is
      if(!opts.contains("-Dlocal.ip.addr=")) {
         args.add("-Dlocal.ip.addr=" + Tool.getRmiIP());
      }

      if(!opts.contains("-Dcomm.this.host=")) {
          args.add("-Dcomm.this.host=" + "localhost");
      }

      if(!opts.contains("-Djava.net.preferIPv4Stack=")) {
         String ipv4 =
            System.getProperty("java.net.preferIPv4Stack", "false").equals("true") ?
               "-Djava.net.preferIPv4Stack=true" : "-Djava.net.preferIPv4Stack=false";
         args.add(ipv4);
      }

      InetsoftConfig config = InetsoftConfig.getInstance();

      if(!opts.contains("-Dscheduler.plugin.directory=") && !StringUtils.isEmpty(config.getPluginDirectory())) {
         args.add("-Dscheduler.plugin.directory=" + config.getPluginDirectory());
      }

      if("true".equals(SreeEnv.getProperty("debug.scheduler"))) {
         args.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5007");
      }

      if(!opts.contains("-DinetsoftClusterDir=")) {
         String clusterDir;
         String localClusterDir = System.getProperty("inetsoftClusterDir");

         if(localClusterDir == null) {
            clusterDir = Paths.get(ConfigurationContext.getContext().getHome())
               .resolve("cluster_scheduler").toAbsolutePath().toString();
         }
         else {
            clusterDir = Paths.get(localClusterDir).getParent()
               .resolve("cluster_scheduler").toAbsolutePath().toString();
         }

         args.add("-DinetsoftClusterDir=" + clusterDir);
      }

      String[] opt = Tool.split(opts, ' ');
      Collections.addAll(args, opt);
      args.add("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED");
      args.add("--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED");
      args.add("--add-opens=java.base/sun.nio.ch=ALL-UNNAMED");
      args.add("--add-opens=java.base/sun.util.calendar=ALL-UNNAMED");
      args.add("--add-opens=java.management/com.sun.jmx.mbeanserver=ALL-UNNAMED");
      args.add("--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED");
      args.add("--add-opens=java.base/sun.reflect.generics.reflectiveObjects=ALL-UNNAMED");
      args.add("--add-opens=jdk.management/com.sun.management.internal=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.io=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.nio=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.net=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.util=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.lang=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.lang.invoke=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.math=ALL-UNNAMED");
      args.add("--add-opens=java.sql/java.sql=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.time=ALL-UNNAMED");
      args.add("--add-opens=java.base/java.text=ALL-UNNAMED");
      args.add("--add-opens=java.management/sun.management=ALL-UNNAMED");
      args.add(sreeHome);
      args.add(headless);
      args.add("-jar");
      args.add(createBootstrapJar(cp).getAbsolutePath());

      StringBuilder cmdArgs = new StringBuilder();

      for(String argument : args) {
         cmdArgs.append(" ").append(argument);
      }

      LOG.info("Starting scheduler with arguments: {}", cmdArgs);

      // start the new process
      ProcessBuilder builder = new ProcessBuilder(args);
      Process proc = builder.start();
      ProcessReader pr = new ProcessReader(proc);
      pr.read();

      String server = getSchedulerServer();

      synchronized(attempts) {
         attempts.put(server, new AtomicInteger(0));
      }

      LOG.debug("Waiting for server to be bound");

      try {
         Awaitility.with()
            .pollInterval(500L, TimeUnit.MILLISECONDS)
            .await()
            .atMost(timeout, timeoutUnit)
            .until(() -> isReady(server) || !proc.isAlive());
      }
      catch(ConditionTimeoutException e) {
         LOG.debug("Scheduler process timeout");
         throw new Exception("Timeout waiting for scheduler to start", e);
      }

      if(!proc.isAlive()) {
         LOG.debug("Scheduler process terminated");
         throw new Exception("Unable to bind the scheduler");
      }

      addStartupTasks();
   }

   /**
    * Stops the schedule server.
    */
   public void stopServer() throws RemoteException {
      stopServer(getSchedulerServer());
   }

   /**
    * Stops the schedule server.
    *
    * @param server the schedule server name.
    */
   public void stopServer(String server) throws RemoteException {
      if(server != null && isReady(server)) {
         LOG.debug("Send stop server request from " + Tool.getIP() + " to " + server +
            " with config directory " + ConfigurationContext.getContext().getHome());
         getSchedule(server).stop();
      }
   }

   /**
    * Cancels a running task.
    *
    * @param task the task name.
    *
    * @return <tt>true</tt> if the task was cancelled.
    */
   public boolean stopNow(String task) throws RemoteException {
      return stopNow(getSchedulerServer(), task);
   }

   /**
    * Cancels a running task.
    *
    * @param server the schedule server name.
    * @param task   the task name.
    *
    * @return <tt>true</tt> if the task was cancelled.
    */
   public boolean stopNow(String server, String task) throws RemoteException {
      boolean result = false;

      if(server != null && isReady(server)) {
         LOG.debug("Send cancel task request [" + task + "] from " + Tool.getIP() +
            " to " + server + " with config directory " +
            ConfigurationContext.getContext().getHome());
         getSchedule(server).stopNow(task);
         result = true;
      }

      return result;
   }

   /**
    * Executes a task immediately.
    *
    * @param task the task name.
    *
    * @return <tt>true</tt> if the task was started.
    */
   public boolean runNow(String task) throws RemoteException {
      return runNow(getSchedulerServer(), task);
   }

   /**
    * Executes a task immediately.
    *
    * @param server the schedule server name.
    * @param task   the task name.
    *
    * @return <tt>true</tt> if the task was started.
    */
   public boolean runNow(String server, String task) throws RemoteException {
      boolean result = false;

      if(server != null && isReady(server)) {
         String taskName =
            LicenseManager.getInstance().isEnterprise() ? task : SUtil.getTaskNameWithoutOrg(task);
         LOG.debug("Send start task request [" + taskName + "] from " + Tool.getIP() +
            " to " + server + " with config directory " +
            ConfigurationContext.getContext().getHome());
         getSchedule(server).runNow(task);
         result = true;
      }

      return result;
   }

   /**
    * Notifies the schedule cluster that a task has been added or updated.
    *
    * @param task the added task.
    */
   public void taskAdded(ScheduleTask task) throws RemoteException {
      taskAdded(getSchedulerServer(), task);
   }

   /**
    * Notifies the schedule cluster that a task has been added or updated.
    *
    * @param server the schedule server name.
    * @param task   the added task.
    */
   public void taskAdded(String server, ScheduleTask task)
      throws RemoteException
   {
      if(server != null) {
         if(isReady(server)) {
            LOG.debug("Send add task request [" + task + "] from " + Tool.getIP() +
               " to " + server + " with config directory " +
               ConfigurationContext.getContext().getHome());
            getSchedule(server).addTask(task);
         }
         else {
            LOG.info("Unable to add task request [" + task + "] from " +
               Tool.getIP() + " to " + server + " with config directory " +
               ConfigurationContext.getContext().getHome());
            saveStartupTask(server, task);
         }
      }
   }

   private synchronized void saveStartupTask(String server, ScheduleTask task) {
      if(startupTasks == null) {
         startupTasks = new ArrayList<>();
      }

      startupTasks.add(new StartupTask(server, task));
   }

   private void addStartupTasks() {
      if(startupTasks != null && startupTasks.size() > 0) {
         for(int d = 0; d < startupTasks.size(); d++) {
            StartupTask st = startupTasks.get(d);

            if(getSchedulerServer().equals(st.getServer())) {
               startupTasks.remove(d--);

               try {
                  taskAdded(st.getServer(), st.getTask());
               }
               catch(RemoteException ex) {
                  LOG.error("Failed to update scheduler with delayed task: " +
                     st.getTask().getTaskId(), ex);
               }
            }
         }
      }
   }

   /**
    * Notifies the schedule cluster that a task has been removed.
    *
    * @param task the name of the task.
    */
   public void taskRemoved(String task) throws RemoteException {
      taskRemoved(getSchedulerServer(), task);
   }

   /**
    * Notifies the schedule cluster that a task has been removed.
    *
    * @param server the schedule server name.
    * @param task   the name of the task.
    */
   public void taskRemoved(String server, String task) throws RemoteException {
      if(server != null && isReady(server)) {
         LOG.debug("Send remove task request [" + task + "] from " + Tool.getIP() +
            " to " + server + " with config directory " +
            ConfigurationContext.getContext().getHome());
         getSchedule(server).removeTask(task);
      }
   }

   public void removeTaskCacheOfOrg(String orgId) throws RemoteException {
      removeTaskCacheOfOrg(getSchedulerServer(), orgId);
   }

   public void removeTaskCacheOfOrg(String server, String orgId) throws RemoteException {
      if(server != null && isReady(server)) {
         getSchedule(server).removeTaskCacheOfOrg(orgId);
      }
   }

   /**
    * Gets the current status of all tasks.
    *
    * @return the task status.
    */
   public Map<String, TaskActivity> getScheduleActivities()
      throws RemoteException
   {
      return getScheduleActivities(getSchedulerServer());
   }

   /**
    * Gets the current status of all tasks.
    *
    * @param server the schedule server name.
    *
    * @return the task status.
    */
   public Map<String, TaskActivity> getScheduleActivities(String server)
      throws RemoteException
   {
      Map<String, TaskActivity> activities = new HashMap<>();
      Schedule scheduleService = server != null && isReady(server) ? getSchedule(server) : null;

      if(scheduleService != null) {
         for(TaskActivity activity : scheduleService.getScheduleActivities()) {
            activities.put(activity.getTaskName(), activity);
         }
      }

      return activities;
   }

   /**
    * Determines if the schedule server is running.
    *
    * @return <tt>true</tt> if running; <tt>false</tt> otherwise.
    */
   public boolean isRunning() {
      String server = getSchedulerServer();
      return server != null && isRunning(server);
   }

   /**
    * Determines if the schedule server is running.
    *
    * @param server the name of the scheduler node to check.
    *
    * @return <tt>true</tt> if running; <tt>false</tt> otherwise.
    */
   public boolean isRunning(String server) {
      boolean found = false;
      Cluster cluster = Cluster.getInstance();

      for(String node : cluster.getClusterNodes()) {
         if(cluster.getClusterNodeHost(node).equals(server) &&
            Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler")))
         {
            found = true;
            break;
         }
      }

      return found;
   }

   /**
    * Determines if the schedule server is running and ready to accept requests.
    *
    * @return <tt>true</tt> if ready; <tt>false</tt> otherwise.
    */
   public boolean isReady() {
      // @by billh, fix customer bug bug1305124753310
      // try getting scheduler server for cluster
      String server = getSchedulerServer();
      return server != null && isReady(server);
   }

   /**
    * Determines if the schedule server is running and ready to accept requests.
    *
    * @param server the name of the scheduler node to check.
    *
    * @return <tt>true</tt> if running; <tt>false</tt> otherwise.
    */
   public boolean isReady(String server) {
      String result = null;

      if(isRunning(server)) {
         try {
            Schedule scheduleService = getSchedule(server);
            result = scheduleService != null ? scheduleService.ping() : null;

            if(result != null && !"OK".equals(result)) {
               throw new StatusCheckException(result);
            }
         }
         catch(Exception ex) {
            AtomicInteger counter;

            synchronized(attempts) {
               counter = attempts.computeIfAbsent(server, (s) -> new AtomicInteger(0));
            }

            // @by stephenwebster, For Bug #8720
            // Previously ignored error coming from the RMI lookup produced a
            // NullPointerException.
            if(counter.incrementAndGet() >= 20) {
               LOG.error("Failed to check schedule running status", ex);
               counter.set(0);
               throw new StatusCheckException("Failed to check schedule running status", ex);
            }

            result = null;
         }
      }

      return "OK".equals(result);
   }

   /**
    * Returns the server being used or the default server, localhost.
    *
    * @return String the server name of the RMI Registry.
    */
   public String getSchedulerServer() {
      // return the first available server
      for(String server : getScheduleServers()) {
         if(isReady(server)) {
            return server;
         }
      }

      // return the default
      return getScheduleServers()[0];
   }

   /**
    * Gets the names of the servers on which scheduler nodes are running.
    *
    * @return the server names.
    */
   public String[] getScheduleServers() {
      List<String> servers = new ArrayList<>();
      final Cluster cluster = Cluster.getInstance();

      for(String node : cluster.getClusterNodes()) {
         if(Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"))) {
            servers.add(cluster.getClusterNodeHost(node));
         }
      }

      if(servers.isEmpty()) {
         servers.add(Tool.getRmiIP());
      }

      servers.sort((o1, o2) -> {
         if(Objects.equals(o1, o2)) {
            return 0;
         }

         if(Tool.getRmiIP().equals(o1)) {
            return -1;
         }

         if(Tool.getRmiIP().equals(o2)) {
            return 1;
         }

         if(o1 == null) {
            return -1;
         }

         if(o2 == null) {
            return 1;
         }

         return o1.compareTo(o2);
      });

      return servers.toArray(new String[0]);
   }

   /**
    * Determines if a schedule cluster is running.
    *
    * @return <tt>true</tt> if a schedule cluster.
    */
   public boolean isCluster() {
      String[] servers = getScheduleServers();
      return servers.length > 1 ||
         servers.length == 1 && !servers[0].equals(Tool.getRmiIP());
   }

   /**
    * Determines if a cloud runner is being used to execute tasks.
    *
    * @return {@code true} if using a cloud runner or {@code false} if not.
    */
   public boolean isCloud() {
      return false;
   }

   /**
    * Determines if the scheduler is automatically started.
    */
   public boolean isAutoStart() {
      return !("server_cluster".equals(SreeEnv.getProperty("server.type")) || isCluster()) &&
         "true".equals(SreeEnv.getProperty("schedule.auto.start"));
   }

   /**
    * Gets the health of the scheduler.
    *
    * @return the health status.
    */
   public Optional<HealthStatus> getHealthStatus() throws RemoteException {
      String server = getSchedulerServer();

      if(server == null) {
         return Optional.empty();
      }
      else {
         return getHealthStatus(server);
      }
   }

   /**
    * Gets the health of the scheduler.
    *
    * @param server the schedule server host name.
    *
    * @return the health status.
    */
   public Optional<HealthStatus> getHealthStatus(String server) throws RemoteException {
      Schedule scheduleService = getSchedule(server);

      if(scheduleService == null) {
         return Optional.empty();
      }
      else {
         return Optional.of(scheduleService.getHealth());
      }
   }

   /**
    * Get a singleton of the ScheduleClient
    * @return a singleton of this class
    */
   public static ScheduleClient getScheduleClient() {
      if(client == null) {
         if(InetsoftConfig.getInstance().getCloudRunner() == null) {
            client = new ScheduleClient();
         }
         else {
            client = new CloudRunnerServerScheduleClient();
         }

      }

      return client;
   }

   /**
    * Get the schedule start Date.
    */
   public static Date getScheduleStartDate() {
      return getScheduleStartDate(getScheduleClient().getSchedulerServer());
   }

   /**
    * Get the schedule start Date.
    */
   public static Date getScheduleStartDate(String server) {
      ScheduleClient client = ScheduleClient.getScheduleClient();

      if(client.isReady(server)) {
         try {
            Schedule schedule = client.getSchedule(server);

            if(schedule != null) {
               return schedule.getStartTime();
            }
            else {
               LOG.error("Failed to get status of server: " + server);
            }
         }
         catch(RemoteException e) {
            LOG.error("Failed to get the start time of server: " + server, e);
         }
      }

      return null;
   }

   public static ServerMetrics getServerMetrics(ServerMetrics oldMetrics, long timestamp,
                                                String address) throws RemoteException
   {
      String server = getScheduleClient().getSchedulerServer();
      ServerMetrics metrics = oldMetrics;
      ScheduleClient client1 = ScheduleClient.getScheduleClient();

      if(client1.isReady(server)) {
         metrics = client1.getSchedule(server).getServerMetrics(oldMetrics, timestamp, address);
      }

      return metrics;
   }

   public static ScheduleViewsheetsStatus getViewsheets(ScheduleViewsheetsStatus oldViewsheets)
      throws RemoteException
   {
      return getViewsheets(oldViewsheets, null);
   }

   public static ScheduleViewsheetsStatus getViewsheets(ScheduleViewsheetsStatus oldViewsheets,
                                                        String address)
      throws RemoteException
   {
      String server = address == null ? getScheduleClient().getSchedulerServer() : address;
      ScheduleClient client1 = ScheduleClient.getScheduleClient();
      ScheduleViewsheetsStatus viewsheets = oldViewsheets;

      if(client1.isReady(server)) {
         viewsheets = client1.getSchedule(server).getViewsheets();
      }

      return viewsheets;
   }

   public static ScheduleQueriesStatus getQueries(ScheduleQueriesStatus oldQueries)
      throws RemoteException
   {
      return getQueries(oldQueries, null);
   }

   public static ScheduleQueriesStatus getQueries(ScheduleQueriesStatus oldQueries, String address)
      throws RemoteException
   {
      String server = address == null ? getScheduleClient().getSchedulerServer() : address;
      ScheduleClient client1 = ScheduleClient.getScheduleClient();
      ScheduleQueriesStatus queries = oldQueries;

      if(client1.isReady(server)) {
         queries = client1.getSchedule(server).getQueries();
      }

      return queries;
   }

   /**
    * Gets the schedule RMI client.
    *
    * @param server the server name.
    *
    * @return the schedule client.
    */
   protected Schedule getSchedule(String server) {
      if(server.equals(Tool.getRmiIP()) ||
         Tool.getRmiIP().equals("localhost") && server.equals(Tool.getIP()))
      {
         server = "localhost";
      }

      String name = "//" + server + ":" + getSchedulerPort() +"/ScheduleServer";
      RMICallThread rct = new RMICallThread();
      return (Schedule) rct.lookup(name, 1500L, false);
   }

   /**
    * Returns the RMI port being used or the default port, 1099.
    * @return int The port number to which the RMI registry is
    *             listening.
    */
   public static int getSchedulerPort() {
      int nPort = 1099;

      try {
         String port = SreeEnv.getProperty("scheduler.rmi.port");

         if(port != null && !port.equals("")) {
            nPort = Integer.parseInt(port);
         }
      }
      catch(Exception e) {
         nPort = 1099;
      }

      return nPort;
   }

   /**
    * Creates the bootstrap JAR file used to launch the schedule server.
    *
    * @param classpath the classpath for the schedule server.
    *
    * @return the bootstrap JAR file.
    *
    * @throws Exception if the JAR could not be generated.
    */
   private static File createBootstrapJar(String classpath) throws Exception {
      File file = File.createTempFile("schedule-launcher", ".jar");
      file.deleteOnExit();
      Manifest manifest = new Manifest();
      manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
      manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, ScheduleLauncher.class.getName());

      try(JarOutputStream output = new JarOutputStream(new FileOutputStream(file), manifest)) {
         JarEntry entry = new JarEntry("inetsoft/sree/schedule/");
         entry.setTime(System.currentTimeMillis());
         output.putNextEntry(entry);
         output.closeEntry();

         String launcherPath = ScheduleLauncher.class.getName().replace('.', '/') + ".class";
         entry = new JarEntry(launcherPath);
         entry.setTime(System.currentTimeMillis());
         output.putNextEntry(entry);

         try(InputStream input = ScheduleClient.class.getResourceAsStream("/" + launcherPath)) {
            IOUtils.copy(input, output);
         }

         output.closeEntry();

         launcherPath = ScheduleLauncher.class.getName().replace('.', '/') + ".txt";
         entry = new JarEntry(launcherPath);
         entry.setTime(System.currentTimeMillis());
         output.putNextEntry(entry);

         PrintWriter writer = new PrintWriter(new OutputStreamWriter(output));
         Arrays.stream(classpath.split(File.pathSeparator))
            .map(p -> p.endsWith("/lib/") ? p + "*" : p)
            .flatMap(p -> p.endsWith("*") ? listFiles(p) : Stream.of(p))
            .map(p -> Paths.get(p).toAbsolutePath().toString())
            .forEach(writer::println);
         writer.flush();

         output.closeEntry();
      }

      return file;
   }

   private static Stream<String> listFiles(String path) {
      File dir = FileSystemService.getInstance().getFile(path.substring(0, path.length() - 2));

      try {
         return Files.list(dir.toPath()).map(Path::toString);
      }
      catch(IOException e) {
         throw new RuntimeException("Failed to list files in directory \"" + path + "\"", e);
      }
   }

   private class StartupTask {
      StartupTask(String server, ScheduleTask task) {
         this.server = server;
         this.task = task;
      }

      public String getServer() {
         return server;
      }

      public ScheduleTask getTask() {
         return task;
      }

      private String server;
      private ScheduleTask task;
   }

   private ArrayList<StartupTask> startupTasks = null;
   private final Map<String, AtomicInteger> attempts = new HashMap<>();
   private static ScheduleClient client = null;
   private static final Logger LOG =
      LoggerFactory.getLogger(ScheduleClient.class);

   private static final class StatusCheckException extends RuntimeException {
      StatusCheckException(String message) {
         super(message);
      }

      StatusCheckException(String message, Throwable cause) {
         super(message, cause);
      }
   }
}
