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
package inetsoft.web.admin.schedule;

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.*;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.util.Catalog;
import inetsoft.web.admin.monitoring.*;
import inetsoft.web.admin.server.*;
import inetsoft.web.cluster.ServerClusterClient;
import inetsoft.web.cluster.ServerClusterStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.rmi.RemoteException;
import java.security.Principal;
import java.util.*;

@Service
public class SchedulerMonitoringService
   extends MonitorLevelService implements MessageListener, StatusUpdater
{
   @Autowired
   public SchedulerMonitoringService(ScheduleManager scheduleManager, DataCycleManager cycleManager,
                                     SecurityProvider securityProvider, ServerClusterClient client)
   {
      super(lowAttrs, new String[0], new String[0]);
      this.scheduleManager = scheduleManager;
      this.cycleManager = cycleManager;
      this.securityProvider = securityProvider;
      this.client = client;
   }

   @PostConstruct
   public void addListener() {
      cluster = Cluster.getInstance();
      cluster.addMessageListener(this);
   }

   @PreDestroy
   public void removeListener() {
      if(cluster != null) {
         cluster.removeMessageListener(this);
      }
   }

   @Override
   public void updateStatus(long timestamp) {
      ScheduleMetrics metrics = oldMetrics;

      try {
         if(ScheduleClient.getScheduleClient().isReady()) {
            ServerMetrics serverMetrics = metrics == null ? null : metrics.getServerMetrics();
            ScheduleViewsheetsStatus viewsheets = metrics == null ? null : metrics.getViewsheets();
            ScheduleQueriesStatus queries = metrics == null ? null : metrics.getQueries();
            metrics = getCurrentMetrics(serverMetrics, viewsheets, queries, timestamp,
                                        Cluster.getInstance().getLocalMember());
         }
      }
      catch(Exception e) {
         if(LOG.isDebugEnabled()) {
            LOG.warn("Error getting schedule metrics, status not updated", e);
         }
         else {
            LOG.warn("Error getting schedule metrics, status not updated");
         }
      }

      if(metrics != null) {
         client.setMetrics(StatusMetricsType.SCHEDULE_METRICS, metrics);
         oldMetrics = metrics;
      }
   }

   private ScheduleMetrics getCurrentMetrics(ServerMetrics serverMetrics,
                                             ScheduleViewsheetsStatus viewsheets,
                                             ScheduleQueriesStatus queries,
                                             long timestamp,
                                             String address) throws Exception
   {
      ScheduleMetrics metrics = new ScheduleMetrics();
      Map<String, TaskActivity> activities = scheduleManager.getScheduleActivities();

      metrics.setCycleCount(getCycleCount());
      metrics.setCycleInfo(getCycleInfo());
      metrics.setStartDate(getStartDate());
      metrics.setUpTime(getUpTime());
      metrics.setTaskCount(getTaskCount(activities));
      metrics.setTaskInfo(getTaskInfo(activities, null, true));
      ServerMetrics newServerMetrics = serverMetrics;
      ScheduleViewsheetsStatus newViewsheets = viewsheets;
      ScheduleQueriesStatus newQueries = queries;

      if(ScheduleClient.getScheduleClient().isReady()) {
         newServerMetrics = ScheduleClient.getServerMetrics(serverMetrics, timestamp, address);
         newViewsheets = ScheduleClient.getViewsheets(viewsheets);
         newQueries = ScheduleClient.getQueries(queries);
      }

      metrics.setServerMetrics(newServerMetrics);
      metrics.setViewsheets(newViewsheets);
      metrics.setQueries(newQueries);
      return metrics;
   }

   @Override
   public void messageReceived(MessageEvent event) {
      String sender = event.getSender();

      if(event.getMessage() instanceof RunTaskMessage) {
         handleRunTaskMessage(sender, (RunTaskMessage) event.getMessage());
      }
      else if(event.getMessage() instanceof StopTaskMessage) {
         handleStopTaskMessage(sender, (StopTaskMessage) event.getMessage());
      }
   }

   private void handleRunTaskMessage(String sender, RunTaskMessage message) {
      RunTaskCompleteMessage completeMessage = new RunTaskCompleteMessage();
      completeMessage.setTaskName(message.getTaskName());

      try {
         cluster.sendMessage(sender, completeMessage);
      }
      catch(Exception e) {
         LOG.warn("Failed to run task: " + message.getTaskName(), e);
      }
   }

   private void handleStopTaskMessage(String sender, StopTaskMessage message) {
      StopTaskCompleteMessage completeMessage = new StopTaskCompleteMessage();
      completeMessage.setTaskName(message.getTaskName());

      try {
         cluster.sendMessage(sender, completeMessage);
      }
      catch(Exception e) {
         LOG.warn("Failed to stop task: " + message.getTaskName(), e);
      }
   }

   /**
    * Get the number of cycles that are currently registered with the scheduler.
    * @return the data cycle count.
    */
   public int getCycleCount() {
      if(!isLevelQualified("cycleCount")) {
         return 0;
      }

      return cycleManager.getDataCycleCount();
   }

   /**
    * Get Datacycle infos from DataCycleManager.
    */
   public DataCycleInfo[] getCycleInfo() {
      if(!isLevelQualified("cycleInfo")) {
         return new DataCycleInfo[0];
      }

      String orgId = OrganizationManager.getInstance().getCurrentOrgID();
      Enumeration<String> cycles = cycleManager.getDataCycles(orgId);
      ScheduleCondition scond;
      List<DataCycleInfo> infos = new ArrayList<>();

      while(cycles.hasMoreElements()) {
         String cycle = cycles.nextElement();
         DataCycleInfo info = new DataCycleInfo();
         info.setName(cycle);
         String[] conditions = new String[cycleManager.getConditionCount(cycle, orgId)];

         for(int j = 0; j < cycleManager.getConditionCount(cycle, orgId); j++) {
            scond = cycleManager.getCondition(cycle, orgId, j);
            conditions[j] = scond.toString();
         }

         info.setConditions(conditions);
         infos.add(info);
      }

      DataCycleInfo[] dataCycles = new DataCycleInfo[infos.size()];

      return infos.toArray(dataCycles);
   }

   /**
    * Get the date and time at which the scheduler was started.
    * This attribute can be used to calculate the scheduler uptime.
    * @return the schedule start date.
    */
   public Date getStartDate() {
      if(!isLevelQualified("startDate")) {
         return null;
      }

      if(!ScheduleClient.getScheduleClient().isReady()) {
         return null;
      }

      return ScheduleClient.getScheduleStartDate();
   }

   /**
    * Get schedule uptime.
    * This is an internal method.
    */
   public long getUpTime() {
      Date startDate = getStartDate();

      if(startDate == null) {
         return -1;
      }

      return System.currentTimeMillis() - startDate.getTime();
   }

   /**
    * Get the number of tasks that are currently.
    * registered with the scheduler.
    * @return the task count.
    */
   public int getTaskCount() {
      try {
         Map<String, TaskActivity> activities = scheduleManager.getScheduleActivities();
         return getTaskCount(activities);
      }
      catch(Exception e){
         throw new RuntimeException("Failed to get task count", e);
      }
   }

   private int getTaskCount(Map<String, TaskActivity> activities) {
      if(!isLevelQualified("taskCount")) {
         return 0;
      }

      Vector<ScheduleTask> allTasks = scheduleManager.getScheduleTasks();
      int count = 0;

      String orgID = OrganizationManager.getInstance().getCurrentOrgID(getSystemPrincipal());

      for(String taskName : activities.keySet()) {
         if(allTasks.contains(scheduleManager.getScheduleTask(taskName, orgID))) {
            ++count;
         }
      }

      return allTasks.size() + activities.size() - count;
   }

   /**
    * Gets information for the scheduled tasks.
    *
    * @param principal a principal that identifies the current user. This may be null.
    * @param all       a flag indicating if all tasks should be returned, or just those owned by
    *                  the current user.
    */
   public ScheduleTaskInfo[] getTaskInfo(Principal principal, boolean all) {
      try {
         Map<String, TaskActivity> activities = scheduleManager.getScheduleActivities();
         return getTaskInfo(activities, principal, all);
      }
      catch(Exception e) {
         throw new RuntimeException("Failed to get task info", e);
      }
   }

   private ScheduleTaskInfo[] getTaskInfo(Map<String, TaskActivity> activities,
                                          Principal principal, boolean all)
   {
      if(!isLevelQualified("taskInfo")) {
         return new ScheduleTaskInfo[0];
      }

      ScheduleClient client = ScheduleClient.getScheduleClient();
      boolean running = client.isReady();
      Map<String, ScheduleTask> tasks = new HashMap<>();

      for(ScheduleTask task : scheduleManager.getScheduleTasks()) {
         if(checkPermission(task, principal, all)) {
            tasks.put(task.getName(), task);
         }
      }

      List<ScheduleTaskInfo> infos = new ArrayList<>();

      tasks.values().stream()
         .map(t -> createTaskInfo(t, activities.get(t.getName()), running))
         .forEach(infos::add);

      activities.entrySet().stream()
         .filter(e -> !tasks.containsKey(e.getKey()))
         .filter(e -> checkPermission(e.getKey(), principal, all))
         .map(Map.Entry::getValue)
         .map(a -> createTaskInfo(a, running))
         .forEach(infos::add);

      return infos.toArray(new ScheduleTaskInfo[0]);
   }

   private ScheduleTaskInfo createTaskInfo(ScheduleTask task, TaskActivity activity,
                                           boolean running)
   {
      ScheduleTaskInfo info = new ScheduleTaskInfo();
      info.setEnabled(task.isEnabled());
      info.setLastRunFinish(activity == null ? null :
                               new Date(activity.getLastRunEnd()));
      info.setLastRunStart(activity == null ? null :
                              new Date(activity.getLastRunStart()));
      info.setLastRunStatus(activity  == null ? null :
                               activity.getLastRunStatus());
      info.setName(task.getName());
      info.setNextRunStart((activity == null || !running ||
         !task.isEnabled()) ? null :
                              new Date(activity.getNextRunStart()));
      info.setNextRunStatus((activity == null || !running ||
         !task.isEnabled()) ? null :
                               activity.getNextRunStatus());
      info.setUser(task.getOwner());
      return info;
   }

   private ScheduleTaskInfo createTaskInfo(TaskActivity activity, boolean running) {
      ScheduleTaskInfo info = new ScheduleTaskInfo();
      info.setName(activity.getTaskName());
      info.setEnabled(true);
      info.setLastRunFinish(new Date(activity.getLastRunEnd()));
      info.setLastRunStart(new Date(activity.getLastRunStart()));
      info.setLastRunStatus(activity.getLastRunStatus());
      info.setNextRunStart(!running ? null : new Date(activity.getNextRunStart()));
      info.setNextRunStatus(!running ? null : activity.getNextRunStatus());
      return info;
   }

   private boolean checkPermission(ScheduleTask task, Principal principal, boolean all) {
      IdentityID pId = principal == null ? null : IdentityID.getIdentityIDFromKey(principal.getName());
      if(principal == null || Objects.equals(task.getOwner(), pId)) {
         return true;
      }

      return all && securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, task.getOwner().convertToKey(), ResourceAction.ADMIN);
   }

   private boolean checkPermission(String taskName, Principal principal, boolean all) {
      ScheduleTask task = scheduleManager.getScheduleTask(taskName);
      return checkPermission(task, principal, all);
   }

   /**
    * Unregisters the task with the specified name from the schedule server.
    * @param name the task name.
    * @param owner the task owner.
    * @throws Exception if failed to remove task.
    */
   public void removeTask(String name, String owner) throws Exception {
      boolean supportsSecurity =
         !"".equals(SreeEnv.getProperty("security.provider"));

      if(name == null || "".equals(name.trim())){
         throw new Exception(Catalog.getCatalog()
                                .getString("em.schedule.task.nameOrOwnerEmpty"));
      }

      if(supportsSecurity && (owner == null || "".equals(owner.trim()))) {
         throw new Exception(Catalog.getCatalog()
                                .getString("em.schedule.task.nameOrOwnerEmpty"));
      }

      if(!supportsSecurity && owner != null && !"".equals(owner.trim())) {
         throw new Exception(Catalog.getCatalog()
                                .getString("em.schedule.task.ownerNotEmpty"));
      }

      String taskName = supportsSecurity ? owner + ":" + name : name;

      if(scheduleManager.getScheduleTask(taskName) != null) {
         scheduleManager.removeScheduleTask(taskName, getSystemPrincipal(), true);
      }
      else {
         throw new Exception(Catalog.getCatalog().getString(
            "scheduleManager.taskNotFound"));
      }
   }

   /**
    * Runs the named task immediately.
    * @param taskName the task name
    * @param node the address of the cluster node.
    */
   public void runTask(String taskName, String node) throws Exception {
      RunTaskMessage message = new RunTaskMessage();
      message.setTaskName(taskName);

      cluster.exchangeMessages(node, message, e -> {
         Boolean result = null;

         if(e.getMessage() instanceof RunTaskCompleteMessage) {
            RunTaskCompleteMessage msg = (RunTaskCompleteMessage) e.getMessage();

            if(msg.getTaskName().equals(taskName)) {
               result = true;
            }
         }

         return result;
      });
   }

   /**
    * Runs the named task immediately.
    * @param taskName the task name.
    */
   public void runTask(String taskName) throws Exception {
      ScheduleClient client = ScheduleClient.getScheduleClient();

      if(!client.isReady()) {
         throw new Exception(
            Catalog.getCatalog().getString("em.scheduler.notStarted"));
      }

      ScheduleTask task = scheduleManager.getScheduleTask(taskName);

      if(task != null && !task.isEnabled()) {
         throw new Exception(Catalog.getCatalog().
            getString("em.schedule.task.failedRun", taskName));
      }

      try {
         client.runNow(taskName);
      }
      catch(Throwable ex) {
         throw new Exception(Catalog.getCatalog().
            getString("em.schedule.task.failedRun", taskName));
      }
   }

   /**
    * Stops the named task if it is running.
    * @param taskName the task name.
    */
   public void stopTask(String taskName) throws Exception {
      if(taskName == null) {
         return;
      }

      ScheduleClient client = ScheduleClient.getScheduleClient();

      if(!client.isReady()) {
         throw new Exception(Catalog.getCatalog().
            getString("em.schedule.task.failedStop", taskName));
      }
      else {
         ScheduleTask task = scheduleManager.getScheduleTask(taskName);

         if(task != null && !task.isEnabled()) {
            throw new Exception(Catalog.getCatalog().
               getString("em.schedule.task.failedStop", taskName));
         }

         try {
            client.stopNow(taskName);
         }
         catch(Throwable ex) {
            throw new Exception(Catalog.getCatalog().
               getString("em.schedule.task.failedStop", taskName));
         }
      }
   }

   /**
    * Starts the schedule server.
    */
   public void startScheduler() {
      SUtil.startScheduler();
   }

   /**
    * Stops the schedule server.
    */
   public void stopScheduler() throws RemoteException {
      SUtil.stopScheduler();
   }

   @Override
   public boolean isComponentAvailable() {
      return true;
   }

   private Principal getSystemPrincipal() {
      return SUtil.getPrincipal(new IdentityID(XPrincipal.SYSTEM, OrganizationManager.getCurrentOrgName()), null, false);
   }

   public List<CpuHistory> getCpuHistory(String server) {
      return getHistory(ServerMetricsCalculator.HistoryType.CPU, server);
   }

   public List<MemoryHistory> getMemoryHistory(String server) {
      return getHistory(ServerMetricsCalculator.HistoryType.MEMORY, server);
   }

   public List<GcHistory> getGcHistory(String server) {
      return getHistory(ServerMetricsCalculator.HistoryType.GC, server);
   }

   public long getMaxHeapSize(String server) throws RemoteException {
      String address = getServerClusterStatus(server).getAddress();
      address = Objects.toString(address, server);
      ServerMetrics serverMetrics = this.oldMetrics == null ? null : this.oldMetrics.getServerMetrics();
      ServerMetrics metrics =
         ScheduleClient.getServerMetrics(serverMetrics, System.currentTimeMillis(), address);
      return metrics.maxHeapSize();
   }

   public Optional<ServerMetrics> getServerMetrics() {
      ScheduleMetrics metrics =
         client.getMetrics(StatusMetricsType.SERVER_METRICS, Cluster.getInstance().getLocalMember());
      return Optional.ofNullable(metrics == null ? null : metrics.getServerMetrics());
   }

   private <T> List<T> getHistory(ServerMetricsCalculator.HistoryType type, String server) {
      ServerClusterStatus status = getServerClusterStatus(server);

      if(status == null) {
         return Collections.emptyList();
      }

      return getHistory(status, type, server);
   }

   private ServerClusterStatus getServerClusterStatus(String server) {
      return client.getClusterNodeStatus(server);
   }

   private <T> List<T> getHistory(ServerClusterStatus status,
                                  ServerMetricsCalculator.HistoryType type,
                                  String scheduleServer)
   {
      if(status == null) {
         return Collections.emptyList();
      }

      String address = Objects.toString(status.getAddress(), scheduleServer);

      return getHistory(address, type);
   }

   private <T> List<T> getHistory(String address,
                                  ServerMetricsCalculator.HistoryType type)
   {
      Queue<T> history = client
         .getStatusHistory(StatusMetricsType.SCHEDULE_METRICS, address, type.name());

      return history == null ? Collections.emptyList() : new ArrayList<>(history);
   }

   private final DataCycleManager cycleManager;
   private final ScheduleManager scheduleManager;
   private final SecurityProvider securityProvider;
   private final ServerClusterClient client;
   private Cluster cluster;
   private ScheduleMetrics oldMetrics;

   private static final String[] lowAttrs = {"taskInfo", "taskCount", "cycleCount", "cycleInfo", "startDate"};
   private static final Logger LOG =
      LoggerFactory.getLogger(SchedulerMonitoringService.class);
}
