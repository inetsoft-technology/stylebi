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

import inetsoft.mv.MVTool;
import inetsoft.report.internal.LicenseException;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.UserEnv;
import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.internal.cluster.MessageListener;
import inetsoft.sree.schedule.cloudrunner.ScheduleTaskCloudJob;
import inetsoft.sree.schedule.jobstore.ClusterJobStore;
import inetsoft.sree.schedule.quartz.*;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.util.*;
import inetsoft.util.config.CloudRunnerConfig;
import inetsoft.util.config.InetsoftConfig;
import inetsoft.util.health.SchedulerHealthService;
import inetsoft.util.health.SchedulerStatus;
import inetsoft.web.admin.logviewer.LogMonitoringService;
import inetsoft.web.admin.server.ServerServiceMessageListener;
import org.quartz.*;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.quartz.simpl.SimpleThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

/**
 * The main scheduler class. It is invoked from another process to run
 * as a set of Java thread. The process calling this class should shutdown
 * itself after calling stop() method otherwise the stop won't be complete.
 * There should be only one scheduler running on a server.
 * <p>
 * A scheduler connects to a repository server, or create an internal
 * replet engine. Scheduler uses the same configuration files as the
 * SREE server.
 *
 * @version 7.0
 * @author InetSoft Technology Corp
 */
public class Scheduler {
   /**
    * Enumeration of the status for a task execution.
    */
   public enum Status {
      STARTED(1),
      FINISHED(2),
      FAILED(3),
      INTERRUPTED(4);

      private final int code;

      Status(int code) {
         this.code = code;
      }

      public int code() {
         return code;
      }

      public static Status forCode(int code) {
         Status status = null;

         for(Status candidate : values()) {
            if(candidate.code == code) {
               status = candidate;
               break;
            }
         }

         if(status == null) {
            throw new IllegalArgumentException("Invalid status code: " + code);
         }

         return status;
      }
   }

   /**
    * Creates a new instance of <tt>Scheduler</tt>.
    */
   private Scheduler() {
      // prevent instantiation
   }

   /**
    * Gets the singleton instance of the scheduler.
    *
    * @return the scheduler instance.
    */
   public static synchronized Scheduler getScheduler() {
      if(INSTANCE == null) {
         INSTANCE = new Scheduler();
      }

      return INSTANCE;
   }

   /**
    * Starts this scheduler instance.
    */
   public void start() {
      String home = System.getProperty("sree.home");

      if(home == null) {
         home = SreeEnv.getProperty("sree.home");
      }

      ConfigurationContext.getContext().setHome(home);
      Catalog.setCatalogGetter(UserEnv.getCatalogGetter());

      // @by yiyangliang, For Bug #9336
      // Do initialize() in the main thread so that license error can be
      // properly handled
      if(!initialize()) {
         return;
      }

      if(SreeEnv.isInitialized()) {
         SreeEnv.reloadLoggingFramework();
      }

      (new Thread("scheduler-job") {
         @Override
         public void run() {
            try {
               if(running.compareAndSet(false, true)) {
                  Scheduler.this.run();
               }
            }
            catch(Exception e) {
               startMsg = "Scheduler failed to start: " + e;
               LOG.error("Failed to start scheduler", e);
               running.set(false);
            }
         }
      }).start();
   }

   /**
    * Stops this scheduler instance.
    *
    * @throws SchedulerException if the scheduler could not be stopped.
    */
   public void stop() throws SchedulerException {
      if(healthCheckExecutor != null) {
         healthCheckExecutor.shutdown();
         healthCheckExecutor = null;
      }

      if(scheduler != null) {
         scheduler.shutdown();
         scheduler = null;
      }

      startTime = null;
      Cluster cluster = Cluster.getInstance();

      if(listeners != null) {
         listeners.forEach(cluster::removeMessageListener);
         listeners.clear();
      }

      // only shutdown the cluster when running in a separate process
      if("true".equals(System.getProperty("ScheduleServer"))) {
         try {
            cluster.close();
         }
         catch(Exception ex) {
            LOG.debug("Failed to shut down cluster instance", ex);
         }
      }

      running.set(false);
   }

   /**
    * Restarts this scheduler instance.
    *
    * @throws SchedulerException if the scheduler could not be restarted.
    */
   public void restart() throws SchedulerException {
      stop();
      start();
   }

   /**
    * Runs the specified task immediately.
    *
    * @param taskName the name of the task.
    *
    * @throws SchedulerException if the task could not be started.
    */
   public void runTask(String taskName) throws SchedulerException {
      if(running.get()) {
         JobKey key = new JobKey(taskName, GROUP_NAME);
         JobDataMap data = new JobDataMap();
         data.put("runNow", true);
         data.put("principal", ThreadContext.getContextPrincipal());
         scheduler.triggerJob(key, data);
      }
   }

   /**
    * Cancels a running task.
    *
    * @param taskName the name of the task.
    */
   @SuppressWarnings("WeakerAccess")
   public void cancelTask(String taskName) throws SchedulerException {
      if(running.get()) {
         JobKey key = new JobKey(taskName, GROUP_NAME);

         for(JobExecutionContext context : scheduler.getCurrentlyExecutingJobs()) {
            if(context.getJobDetail().getKey().equals(key)) {
               Job job = context.getJobInstance();

               if(job instanceof InterruptableJob) {
                  ((InterruptableJob) job).interrupt();
               }

               break;
            }
         }
      }
   }

   public void updateRunning(JobKey jobKey, TaskActivity activity,
                             Map<JobKey, Long> runningJobs,
                             JobExecutionContext context, Catalog catalog)
   {
      activity.setLastRunStatus(catalog.getString("Running"));
      activity.setLastRunEnd(0L);
      activity.setMessage("Execution Started");

      if(context != null) {
         activity.setLastRunStart(context.getFireTime().getTime());
      }
      else if(runningJobs != null) {
         activity.setLastRunStart(runningJobs.get(jobKey));
      }
   }

   public void updateNextRun(JobKey jobKey, TaskActivity activity,
                             boolean running, Catalog catalog)
      throws SchedulerException
   {
      JobDetail detail = scheduler.getJobDetail(jobKey);

      // run-once job, already deleted?
      if(detail == null) {
         return;
      }

      JobDataMap dmap = detail.getJobDataMap();
      ScheduleTask task = (ScheduleTask) dmap.get(ScheduleTask.class.getName());

      updateNextRun(activity, task, running,
                    scheduler.getTriggersOfJob(jobKey), catalog);
   }

   private void updateNextRun(TaskActivity activity, ScheduleTask task,
                              boolean running, Collection<? extends Trigger> triggers,
                              Catalog catalog)
   {
      long nextRunStart = Long.MAX_VALUE;
      long currentTimeMillis = System.currentTimeMillis();

      for(Trigger trigger : triggers) {
         JobDataMap jobDataMap = trigger.getJobDataMap();
         boolean runNow = jobDataMap != null && jobDataMap.getBoolean("runNow");
         Date nextFireTime = trigger.getNextFireTime();
         Date endTime = trigger.getEndTime();

         if(nextFireTime != null && (endTime == null || nextFireTime.getTime() < endTime.getTime())
            && !runNow && nextFireTime.getTime() > currentTimeMillis)
         {
            nextRunStart = Math.min(nextRunStart, nextFireTime.getTime());
         }
      }

      long now = System.currentTimeMillis();

      // if next run time passes task stopOn time, pause trigger
      if(task != null && task.getEndDate() != null && task.getEndDate().getTime() < now) {
         try {
            for(Trigger trigger : triggers) {
               scheduler.pauseTrigger(trigger.getKey());
            }
         }
         catch(Exception ex) {
            //do nothing
         }

         activity.setNextRunStatus(catalog.getString("Not scheduled"));
         return;
      }

      if(nextRunStart < System.currentTimeMillis() && !running) {
         activity.setNextRunStatus(catalog.getString("Ready"));
      }
      else if(nextRunStart < Long.MAX_VALUE) {
         activity.setNextRunStatus(catalog.getString("Pending"));
      }
      else {
         if(task == null) {
            ScheduleManager manager = ScheduleManager.getScheduleManager();
            task = manager.getScheduleTask(activity.getTaskName());
         }

         boolean waiting = false;

         for(int i = 0; i < task.getConditionCount(); i++) {
            if(task.getCondition(i) instanceof CompletionCondition) {
               waiting = true;
            }
         }

         if(waiting) {
            activity.setNextRunStatus(catalog.getString("Wait for trigger"));
         }
         else {
            activity.setNextRunStatus(catalog.getString("Not scheduled"));
         }
      }

      if(nextRunStart < Long.MAX_VALUE) {
         activity.setNextRunStart(nextRunStart);
      }

      SCHEDULE_TEST_LOG.info(
         "TaskStatus: LastRunStatus {}, LastRunStart {}, LastRunEnd {}, NextRunStatus {}, NextRunStart {}, Name {}", activity.getLastRunStatus(),
         activity.getLastRunStart(), activity.getLastRunEnd(), activity.getNextRunStatus(), activity.getNextRunStart(), activity.getTaskName());
   }

   public void updateLastRun(TaskActivity activity,
                             ScheduleStatusDao.Status status, Catalog catalog)
   {
      if(status != null) {
         switch(status.getStatus()) {
         case FAILED:
            activity.setLastRunStatus(catalog.getString("Failed"));
            activity.setMessage("Execution Failed");
            activity.setError(status.getError());
            break;
         case INTERRUPTED:
            activity.setLastRunStatus(catalog.getString("Interrupted"));
            activity.setMessage("The Task Was Stopped");
            break;
         case FINISHED:
         default:
            activity.setLastRunStatus(catalog.getString("Finished"));
            activity.setMessage("Task Completed");
         }

         activity.setLastRunStart(status.getStartTime());
         activity.setLastRunEnd(status.getEndTime());
      }

      SCHEDULE_TEST_LOG.info(
         "TaskStatus: LastRunStatus {}, LastRunStart {}, LastRunEnd {}, NextRunStatus {}, NextRunStart {}, Name {}", activity.getLastRunStatus(),
         activity.getLastRunStart(), activity.getLastRunEnd(), activity.getNextRunStatus(), activity.getNextRunStart(), activity.getTaskName());
   }

   /**
    * Gets the status of all currently scheduled tasks.
    *
    * @return the task activity.
    *
    * @throws SchedulerException if the next execution state cannot be obtained.
    */
   @SuppressWarnings("WeakerAccess")
   public Map<String, TaskActivity> getScheduleActivities() throws SchedulerException {
      Map<String, TaskActivity> activities = new HashMap<>();

      if(scheduler != null) {
         Catalog catalog = Catalog.getCatalog();
         Map<JobKey, Long> runningJobs = new HashMap<>();

         for(JobExecutionContext context : scheduler.getCurrentlyExecutingJobs()) {
            runningJobs.put(
               context.getJobDetail().getKey(),
               context.getFireTime().getTime());
         }

         for(JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals(GROUP_NAME))) {
            String taskName = jobKey.getName();
            TaskActivity activity = new TaskActivity(taskName);
            boolean running = false;

            if(runningJobs.containsKey(jobKey)) {
               updateRunning(jobKey, activity, runningJobs, null, catalog);
               running = true;
            }

            updateNextRun(jobKey, activity, running, catalog);
            activities.put(taskName, activity);
         }

         for(String taskName : activities.keySet()) {
            ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
            ScheduleStatusDao.Status lastStatus = dao.getStatus(taskName);
            TaskActivity activity = activities.get(taskName);

            if(activity != null && lastStatus != null &&
               !runningJobs.containsKey(new JobKey(taskName, GROUP_NAME)))
            {
               updateLastRun(activity, lastStatus, catalog);
            }
         }
      }

      return activities;
   }

   /**
    * Gets the time at which the scheduler was started.
    *
    * @return the start time.
    */
   public Date getStartTime() {
      return startTime;
   }

   /**
    * Return the startup error message.
    */
   public String getStartMessage() {
      return startMsg;
   }

   /**
    * Initializes this scheduler instance.
    */
   boolean initialize() {
      try {
         if(initialized.compareAndSet(false, true)) {
            startMsg = null;

            try {
               initialize0();
               startTime = new Date(System.currentTimeMillis());
            }
            catch(Exception ex) {
               startMsg = "Scheduler failed to start: " + ex;
               throw ex;
            }
         }
      }
      catch(LicenseException e) {
         throw e;
      }
      catch(Exception e) {
         LOG.error("Failed to initialize scheduler", e);
         initialized.set(false);
         return false;
      }

      return true;
   }

   private void initialize0() throws Exception {
      // initialize the licensed components.
      // try to initialize the analytic components only if
      // the Analytic Edition is being used
      SreeEnv.getProperty("init.home");

      // for scheduler, do not initialize dependency finder
      System.setProperty("comm.io.monitor", "false");
      System.setProperty("fs.monitor", "false");
      System.setProperty("inetsoft.scheduler.execution", "true");

      if(scheduler == null) {
         checkLicense();
         Cluster cluster = Cluster.getInstance();

         // Setting Cluster Job Store
         ClusterJobStore jobStore = new ClusterJobStore();

         DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
         int maxThread =
            Integer.parseInt(SreeEnv.getProperty("schedule.concurrency"));
         factory.createScheduler(
            "inetsoft", "AUTO",
            new SimpleThreadPool(maxThread, Thread.NORM_PRIORITY), jobStore,
            null, 0, 20000, -1);
         scheduler = factory.getScheduler("inetsoft");
         scheduler.getListenerManager().addJobListener(
            new JobCompletionListener("TaskCompletionListener"));
         scheduler.getListenerManager().addTriggerListener(
            new DefaultTriggerListener("DefaultTriggerListener"));

         loadTasks();
         first = false;
         listeners = new ArrayList<>();
         MessageListener listener = MVTool.newMVMessageHandler();

         if(listener != null) {
            listeners.add(listener);
         }

         listeners.add(new ServerServiceMessageListener(cluster));
         listeners.add(new LogMonitoringService());
         listeners.forEach(cluster::addMessageListener);
      }
   }

   /**
    * Checks that there are sufficient licenses to start another scheduler
    * instance.
    */
   private void checkLicense() {
      Cluster cluster = Cluster.getInstance();
      Lock lock = cluster.getLock(INIT_LOCK);
      lock.lock();

      try {
         int schedulerCount = 0;

         for(String node : cluster.getClusterNodes()) {
            if(Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"))) {
               ++schedulerCount;
            }
         }

         // a single instance is included with a valid server key, more than one
         // instance require supplemental keys
         if(schedulerCount > 1) {
            throw new LicenseException(
               "Only one scheduler instance is allowed with the Community Edition");
         }
      }
      finally {
         lock.unlock();
      }
   }

   /**
    * Get the number of schedulers currently running.
    */
   public static int getSchedulerCount() {
      Cluster cluster = Cluster.getInstance();
      int schedulerCount = 0;

      for(String node : cluster.getClusterNodes()) {
         if(Boolean.TRUE.equals(cluster.getClusterNodeProperty(node, "scheduler"))) {
            ++schedulerCount;
         }
      }

      return schedulerCount;
   }

   /**
    * Loads the tasks into the Quartz scheduler.
    *
    * @throws SchedulerException if the tasks could not be loaded.
    */
   private void loadTasks() throws Exception {
      Cluster cluster = Cluster.getInstance();
      Lock lock = cluster.getLock(INIT_LOCK);
      lock.lock();

      try {
         boolean isCloudRun = InetsoftConfig.getInstance().getCloudRunner() != null;
         boolean scheduleServer = "true".equals(System.getProperty("ScheduleServer"));

         if(isCloudRun ? !scheduleServer && !cloudRunnerScheduleTaskLoaded() : getSchedulerCount() == 1) {
            scheduler.clear();
            // ensure that data cycle tasks have been loaded
            DataCycleManager.getDataCycleManager();

            for(ScheduleTask task : ScheduleManager.getScheduleManager().getScheduleTasks()) {
               try {
                  addTask(task, true);
               }
               catch(Exception ex) {
                  LOG.error("Failed to load task: " + task.getTaskId(), ex);
               }
            }

            if(isCloudRun) {
               cluster.setLocalNodeProperty(CLOUD_RUNNER_SCHEDULER_TASK_LOADED, "true");
            }
         }
      }
      finally {
         lock.unlock();
      }
   }

   private boolean cloudRunnerScheduleTaskLoaded() {
      if(InetsoftConfig.getInstance().getCloudRunner() == null) {
         return false;
      }

      Cluster cluster = Cluster.getInstance();
      Set<String> clusterNodes = cluster.getClusterNodes();

      if(clusterNodes == null) {
         return false;
      }

      for(String clusterNode : clusterNodes) {
         if("true".equals(cluster.getClusterNodeProperty(clusterNode, CLOUD_RUNNER_SCHEDULER_TASK_LOADED))) {
            return true;
         }
      }

      return false;
   }

   /**
    * Main worker for the scheduler.
    */
   private void run() throws Exception {
      healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(
         r -> new Thread(r, "SchedulerHealthCheck"));
      scheduler.start();

      JobDetail job = JobBuilder
         .newJob(HealthCheckJob.class)
         .withIdentity("__health check job__", INTERNAL_GROUP_NAME)
         .storeDurably()
         .build();
      scheduler.addJob(job, true);
      healthCheckFuture = healthCheckExecutor
         .scheduleAtFixedRate(this::checkHealth, 0L, 1L, TimeUnit.MINUTES);
   }

   private void checkHealth() {
      try {
         scheduler.triggerJob(new JobKey("__health check job__", INTERNAL_GROUP_NAME));
      }
      catch(SchedulerException e) {
         LOG.warn("Failed to check health", e);
      }
   }

   /**
    * Check whether scheduler is running.
    */
   public boolean isRunning() {
      if(scheduler == null) {
         return false;
      }

      try {
         return scheduler.isStarted();
      }
      catch(SchedulerException e) {
         return false;
      }
   }

   public SchedulerStatus getSchedulerStatus() {
      boolean started = false;
      boolean shutdown = false;
      boolean standby = false;
      long lastCheck = SchedulerHealthService.getInstance().getLastCheck();
      long nextCheck = healthCheckFuture == null ?
         0L : healthCheckFuture.getDelay(TimeUnit.MILLISECONDS);
      Future.State checkState = healthCheckFuture.state();
      int executingCount = 0;
      int threadCount = 0;

      if(scheduler != null) {
         try {
            started = scheduler.isStarted();
            shutdown = scheduler.isShutdown();
            standby = scheduler.isInStandbyMode();
            executingCount = scheduler.getCurrentlyExecutingJobs().size();
            threadCount = scheduler.getMetaData().getThreadPoolSize();
         }
         catch(SchedulerException ignore) {
         }
      }

      return new SchedulerStatus(
         started, shutdown, standby, lastCheck, nextCheck, checkState, executingCount, threadCount);
   }

   /**
    * Adds or updates a schedule task.
    *
    * @param task the task to add.
    *
    * @throws SchedulerException if the task could not be removed.
    */
   @SuppressWarnings("WeakerAccess")
   public void addTask(ScheduleTask task) throws SchedulerException {
      addTask(task, false);
   }

   private void addTask(ScheduleTask task, boolean loading) throws SchedulerException {
      if(scheduler != null && task != null) {
         Set<Trigger> triggers = new HashSet<>();
         long now = System.currentTimeMillis();
         long lastRun = getLastScheduledRunTime(task.getTaskId());

         for(int i = 0; i < task.getConditionCount(); i++) {
            ScheduleCondition condition = task.getCondition(i);

            if(condition instanceof TimeCondition) {
               if(loading && ((TimeCondition) condition).getType() == TimeCondition.AT) {
                  ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
                  ScheduleStatusDao.Status status = dao.getStatus(task.getTaskId());

                  if(status != null && status.getStatus() == Status.FINISHED) {
                     // run once that has already finished, skip it
                     LOG.debug(
                        "Run Once job skipped since it's already done: " +
                           task.getTaskId());
                     continue;
                  }
               }

               TimeConditionTriggerImpl trigger = new TimeConditionTriggerImpl();
               trigger.setName(String.format("%s-%d",task.getTaskId(), i + 1));
               trigger.setCondition((TimeCondition) condition);

               long next = (lastRun > 0) ? ((TimeCondition) condition).getRetryTime(now, lastRun)
                  : condition.getRetryTime(now);

               if(next > 0) {
                  setStartAndEndTime(next, trigger, task);
                  triggers.add(trigger);
               }
               else {
                  LOG.debug("Time condition skipped: " +
                               task.getTaskId() + " " + condition +
                               " current time: " + (new Date(now)));
               }
            }
            else if(condition instanceof TaskBalancerCondition) {
               TaskBalancerConditionTriggerImpl trigger = new TaskBalancerConditionTriggerImpl();
               trigger.setName(String.format("%s-%d", task.getTaskId(), i + 1));
               trigger.setCondition((TaskBalancerCondition) condition);
               long next = condition.getRetryTime(now);

               if(next > 0) {
                  setStartAndEndTime(next, trigger, task);
                  triggers.add(trigger);
               }
               else {
                  LOG.debug("Task balancer skipped: " + task.getTaskId());
               }
            }
         }

         JobDataMap dataMap = new JobDataMap();
         dataMap.put(ScheduleTask.class.getName(), task);

         CloudRunnerConfig cloudRunnerConfig = InetsoftConfig.getInstance().getCloudRunner();
         Class <? extends Job> jobClass;

         if(cloudRunnerConfig == null ||
            InternalScheduledTaskService.BALANCE_TASKS.equals(task.getTaskId()))
         {
            jobClass = ScheduleTaskJob.class;
         }
         else {
            jobClass = ScheduleTaskCloudJob.class;
         }

         JobDetail job = JobBuilder
            .newJob(jobClass)
            .withIdentity(task.getTaskId(), GROUP_NAME)
            .storeDurably(task.isDurable() || triggers.isEmpty() || !task.isDeleteIfNoMoreRun())
            .usingJobData(dataMap)
            .build();
         boolean modified = !first && scheduler.deleteJob(job.getKey());
         scheduler.scheduleJob(job, triggers, true);

         if(!task.isEnabled()) {
            //scheduler.pauseJob(new JobKey(task.getName(), Scheduler.GROUP_NAME));
            // optimization, avoid querying for trigger in pauseJob()
            for(Trigger trigger : triggers) {
               scheduler.pauseTrigger(trigger.getKey());
            }
         }
         else {
            scheduler.resumeJob(new JobKey(task.getTaskId(), GROUP_NAME));
         }

         try {
            Catalog catalog = Catalog.getCatalog();
            ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
            ScheduleTaskMessage taskMessage = new ScheduleTaskMessage();
            taskMessage.setTaskName(task.getTaskId());
            taskMessage.setAction(
               modified ?
                  ScheduleTaskMessage.Action.MODIFIED :
                  ScheduleTaskMessage.Action.ADDED);
            taskMessage.setTask(task);
            Cluster.getInstance().sendMessage(taskMessage);

            TaskActivity activity = new TaskActivity(task.getTaskId());
            updateNextRun(activity, task, false, triggers, catalog);
            updateLastRun(activity, dao.getStatus(task.getTaskId()), catalog);
            TaskActivityMessage activityMessage = new TaskActivityMessage();
            activityMessage.setTaskName(task.getTaskId());
            activityMessage.setActivity(activity);
            Cluster.getInstance().sendMessage(activityMessage);
         }
         catch(Exception e) {
            LOG.warn("Failed to notify cluster of task status change", e);
         }
      }
   }

   /**
    * set start and end time.
    *
    * @param next    is next time.
    * @param trigger is abstractConditionTrigger.
    * @param task    is scheduleTask.
    */
   private void setStartAndEndTime(Long next, AbstractConditionTrigger<?, ?> trigger,
                                   ScheduleTask task)
   {
      Date time = new Date(next);
      Date startDate = getDate(time, task.getStartDate(), task.getTimeZone());
      Date endDate = task.getEndDate() != null ? getDate(time, task.getEndDate(), task.getTimeZone()) : null;
      startDate = time.getTime() > startDate.getTime() ? time : startDate;
      trigger.setStartTime(startDate);
      trigger.setEndTime(endDate);
      trigger.setMisfireInstruction(
         ConditionTrigger.MISFIRE_INSTRUCTION_FIRE_ONCE_NOW);
      LOG.debug(
         "Task scheduled at " + startDate + ": " +
            task.getTaskId());
   }

   /**
    * get the required date
    *
    * @param time is next time.
    * @param date is start or end date.
    */
   private static Date getDate(Date time, Date date, String timeZone) {
      if(date != null && time != null) {
         Calendar time1 = Calendar.getInstance();
         Calendar date1 = Calendar.getInstance();

         if(timeZone != null) {
            time1.setTimeZone(TimeZone.getTimeZone(timeZone));
            date1.setTimeZone(TimeZone.getTimeZone(timeZone));
         }

         time1.setTime(time);
         date1.setTime(date);
         date1.set(Calendar.HOUR_OF_DAY, time1.get(Calendar.HOUR_OF_DAY));
         date1.set(Calendar.MINUTE, time1.get(Calendar.MINUTE));
         date1.set(Calendar.SECOND, time1.get(Calendar.SECOND));
         time = date1.getTime();
      }

      return time;
   }

   /**
    * Removes a schedule task.
    *
    * @param taskName the name of the task to remove.
    *
    * @throws SchedulerException if the task could not be removed.
    */
   public void removeTask(String taskName) throws SchedulerException {
      if(scheduler != null) {
         JobKey key = new JobKey(taskName, GROUP_NAME);
         scheduler.deleteJob(key);

         ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
         dao.clearStatus(taskName);

         try {
            ScheduleTaskMessage message = new ScheduleTaskMessage();
            message.setTaskName(taskName);
            message.setAction(ScheduleTaskMessage.Action.REMOVED);
            Cluster.getInstance().sendMessage(message);
         }
         catch(Exception e) {
            LOG.warn("Failed to notify cluster that task was removed: " + taskName,
                     e);
         }
      }
   }

   public void removeTaskCacheOfOrg(String oldOrgId) {
      ScheduleManager.getScheduleManager().removeTaskCacheOfOrg(oldOrgId);
   }

   /**
    * Get the last scheduled (ignore run-now) start time for the task.
    */
   long getLastScheduledRunTime(String taskId) {
      ScheduleStatusDao dao = ScheduleStatusDao.getInstance();
      ScheduleStatusDao.Status status = dao.getStatus(taskId);
      return status != null ? status.getLastScheduledStartTime() : 0;
   }

   public static String getTaskOrg(ScheduleTask task) {
      DataCycleManager.CycleInfo cycleInfo = task.getCycleInfo();

      if(cycleInfo != null) {
         return cycleInfo.getOrgId();
      }
      else {
         return OrganizationManager.getInstance().getCurrentOrgID();
      }
   }

   private final AtomicBoolean initialized = new AtomicBoolean(false);
   private final AtomicBoolean running = new AtomicBoolean(false);
   private org.quartz.Scheduler scheduler;
   private ScheduledExecutorService healthCheckExecutor;
   private ScheduledFuture<?> healthCheckFuture;
   private Date startTime;
   private String startMsg;
   private boolean first = true;
   private List<MessageListener> listeners;

   private static Scheduler INSTANCE;
   private static final Logger LOG = LoggerFactory.getLogger(Scheduler.class);
   private static final Logger SCHEDULE_TEST_LOG =
      LoggerFactory.getLogger("inetsoft.scheduler_test");
   public static final String GROUP_NAME = "inetsoft";
   private static final String INTERNAL_GROUP_NAME = "inetsoft_internal";
   public static final String INIT_LOCK = Scheduler.class.getName() + ".initLock";
   public static final String CLOUD_RUNNER_SCHEDULER_TASK_LOADED = "CLOUD_RUNNER_SCHEDULER_TASK_LOADED";
}
