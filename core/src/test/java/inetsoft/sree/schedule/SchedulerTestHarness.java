/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
import inetsoft.sree.PropertiesEngine;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.quartz.JobCompletionListener;
import inetsoft.sree.security.*;
import inetsoft.test.schedule.InMemoryKeyValueStorage;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.ConfigurationContext;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.context.ApplicationContext;

import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test harness for scheduler integration tests.
 *
 * <p>Spins up a real in-process Quartz scheduler (RAMJobStore) backed by InMemoryKeyValueStorage
 * and a mock Spring ApplicationContext. Since Quartz jobs run in worker threads, standard
 * {@code mockStatic} (which is thread-local) does not work here. Instead, we register test
 * doubles via {@link ConfigurationContext#setApplicationContext} so they are visible from all
 * threads.
 *
 * <p>Usage: create in {@code @BeforeEach}, call {@link #close()} in {@code @AfterEach}.
 */
public class SchedulerTestHarness implements AutoCloseable {

   // IdentityID used as the task owner so SUtil.getPrincipal() never receives a null owner.
   public static final IdentityID TEST_OWNER = new IdentityID("scheduler-test", "host");

   private final org.quartz.Scheduler quartz;
   private final ScheduleStatusDao dao;
   private final BlockingQueue<Object> clusterMessages = new LinkedBlockingQueue<>();
   private final ApplicationContext savedAppContext;

   public SchedulerTestHarness() throws SchedulerException {
      // Real DAO backed by in-memory storage — package-private constructor accessible here
      InMemoryKeyValueStorage<ScheduleStatusDao.Status> storage = new InMemoryKeyValueStorage<>();
      dao = new ScheduleStatusDao(storage);

      // Mock SecurityEngine for SUtil.getPrincipal() — needs non-null provider chain
      SecurityEngine mockEngine = mock(SecurityEngine.class);
      SecurityProvider mockProvider = mock(SecurityProvider.class);
      AuthenticationProvider mockAuthProvider = mock(AuthenticationProvider.class);
      when(mockEngine.getSecurityProvider()).thenReturn(mockProvider);
      when(mockProvider.getAuthenticationProvider()).thenReturn(mockAuthProvider);
      // getUser() and getOrganization() return null by default → SUtil falls back to ANONYMOUS
      // AuthenticationProvider.isVirtual() returns false by default → safe for SUtil

      // Mock Cluster — intercept sendMessage calls across threads
      Cluster mockCluster = mock(Cluster.class);
      try {
         doAnswer(inv -> {
            clusterMessages.add(inv.getArgument(0));
            return null;
         }).when(mockCluster).sendMessage(any(java.io.Serializable.class));
      }
      catch(Exception ignored) {}

      // SRPrincipal constructor calls XSessionService.getService() to create a session ID.
      // If not stubbed, applicationContext.getBean(XSessionService.class) returns null and
      // Caffeine's beanCache.put(type, null) throws NPE (Caffeine disallows null values).
      XSessionService mockSessionService = mock(XSessionService.class);
      when(mockSessionService.createSessionID(any(), any())).thenReturn("test-session-id");

      // ActionRecord constructor calls Tool.getHost() → SreeEnv.getProperty("local.host.name") →
      // PropertiesEngine.getInstance() — same Caffeine null-value NPE if not stubbed.
      // Stub the hostname property to avoid InetAddress.getLocalHost() DNS lookup (which can
      // take 2-5 seconds in test environments without proper reverse DNS, blowing the 5s timeout).
      PropertiesEngine mockPropertiesEngine = mock(PropertiesEngine.class);
      // SreeEnv.getProperty() has several overloads that delegate to PropertiesEngine:
      //   getProperty(name)           → PropertiesEngine.getProperty(name)
      //   getProperty(name, bool)     → PropertiesEngine.getProperty(name, bool)
      //   getProperty(name, bool,bool)→ PropertiesEngine.getProperty(name, bool, bool)
      //   getProperty(name, def)      → PropertiesEngine.getProperty(name, def, false)
      // Any unstubbed call returns null from Mockito, causing callers to NPE or fail.
      // Stub "local.host.name" to avoid InetAddress.getLocalHost() DNS lookup.
      when(mockPropertiesEngine.getProperty(eq("local.host.name"))).thenReturn("localhost");
      when(mockPropertiesEngine.getProperty(eq("local.host.name"), anyBoolean())).thenReturn("localhost");
      when(mockPropertiesEngine.getProperty(eq("local.host.name"), anyBoolean(), anyBoolean())).thenReturn("localhost");
      // For any getProperty(name, default, earlyLoaded) call return the default, mirroring
      // real PropertiesEngine behaviour when a property isn't configured.
      when(mockPropertiesEngine.getProperty(any(String.class), any(String.class), anyBoolean()))
         .thenAnswer(inv -> inv.getArgument(1));

      // Register all test doubles via a mock ApplicationContext.
      // ConfigurationContext.getSpringBean() is called from Quartz worker threads, so we need a
      // global (not thread-local) mechanism. Setting the ApplicationContext achieves this.
      ApplicationContext mockAppCtx = mock(ApplicationContext.class);
      // ScheduleTask.getThreadPool() calls LicenseManager.getInstance() to determine the thread
      // count. Without this stub, getSpringBean(LicenseManager.class) returns null from the mock
      // and Caffeine throws NPE (null values not allowed). That causes task.run() to throw, which
      // makes jobWasExecuted see jobException != null and write FAILED instead of FINISHED.
      LicenseManager mockLicenseManager = mock(LicenseManager.class);
      when(mockLicenseManager.getAvailableCpuCount()).thenReturn(2);

      when(mockAppCtx.getBean(ScheduleStatusDao.class)).thenReturn(dao);
      when(mockAppCtx.getBean(SecurityEngine.class)).thenReturn(mockEngine);
      when(mockAppCtx.getBean(Cluster.class)).thenReturn(mockCluster);
      when(mockAppCtx.getBean(XSessionService.class)).thenReturn(mockSessionService);
      when(mockAppCtx.getBean(PropertiesEngine.class)).thenReturn(mockPropertiesEngine);
      when(mockAppCtx.getBean(LicenseManager.class)).thenReturn(mockLicenseManager);

      savedAppContext = ConfigurationContext.getContext().getApplicationContext();
      ConfigurationContext.getContext().setApplicationContext(mockAppCtx);

      // Quartz with RAMJobStore — no Ignite, no external process
      Properties props = new Properties();
      props.setProperty("org.quartz.scheduler.instanceName", "SchedulerTestHarness");
      props.setProperty("org.quartz.threadPool.threadCount", "4");
      props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
      quartz = new StdSchedulerFactory(props).getScheduler();
      quartz.getListenerManager()
            .addJobListener(new JobCompletionListener("test-listener"));
      quartz.start();
   }

   /**
    * Register a task in Quartz under the production group name.
    * The task is parked with a far-future trigger; call {@link #triggerNow} to fire it.
    * Sets {@link #TEST_OWNER} as the task owner so SUtil.getPrincipal() gets a non-null IdentityID.
    */
   public void registerTask(ScheduleTask task) throws SchedulerException {
      if(task.getOwner() == null) {
         task.setOwner(TEST_OWNER);
      }

      JobDetail job = JobBuilder.newJob(HarnessTaskJob.class)
         .withIdentity(task.getTaskId(), Scheduler.GROUP_NAME)
         .storeDurably()
         .build();
      job.getJobDataMap().put(ScheduleTask.class.getName(), task);

      Trigger parkTrigger = TriggerBuilder.newTrigger()
         .withIdentity(task.getTaskId() + "-park", Scheduler.GROUP_NAME)
         .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                          .withRepeatCount(0)
                          .withIntervalInMilliseconds(1))
         .startAt(new java.util.Date(System.currentTimeMillis() + 365L * 24 * 3600 * 1000))
         .build();

      quartz.scheduleJob(job, parkTrigger);
   }

   /**
    * Trigger an already-registered task immediately.
    * JobCompletionListener will run after the job and write status to the DAO.
    */
   public void triggerNow(String taskId) throws SchedulerException {
      quartz.triggerJob(new JobKey(taskId, Scheduler.GROUP_NAME));
   }

   /**
    * Run a task's actions directly without going through Quartz.
    * Does NOT write any ScheduleStatusDao status. Use for Layer 2 action boundary tests.
    */
   public void runTask(ScheduleTask task) {
      try {
         task.run(null);
      }
      catch(Throwable e) {
         throw new RuntimeException("Task failed: " + task.getTaskId(), e);
      }
   }

   /**
    * Wait until the given task has the expected status in ScheduleStatusDao.
    *
    * @throws org.awaitility.core.ConditionTimeoutException if the timeout elapses
    */
   public ScheduleStatusDao.Status waitForStatus(String taskId,
                                                  Scheduler.Status expected,
                                                  Duration timeout)
   {
      await().atMost(timeout)
             .until(() -> {
                ScheduleStatusDao.Status s = dao.getStatus(taskId);
                return s != null && s.getStatus() == expected;
             });
      return dao.getStatus(taskId);
   }

   /** Direct access to the in-memory DAO for status assertions. */
   public ScheduleStatusDao getDao() {
      return dao;
   }

   /** Messages delivered via Cluster.sendMessage() — available after each task run. */
   public BlockingQueue<Object> getClusterMessages() {
      return clusterMessages;
   }

   /** Direct access to the Quartz scheduler for advanced test setup. */
   public org.quartz.Scheduler getQuartz() {
      return quartz;
   }

   @Override
   public void close() {
      try {
         quartz.shutdown(true);
      }
      catch(Exception ignored) {}

      // Restore the Spring ApplicationContext so subsequent tests see the original state
      ConfigurationContext.getContext().setApplicationContext(savedAppContext);
   }

   /**
    * Minimal Quartz Job that calls task.run() and lets JobCompletionListener handle the rest.
    * Avoids the SUtil.runTask() / DataCycleManager dependencies of the production ScheduleTaskJob.
    */
   public static class HarnessTaskJob implements Job {
      @Override
      public void execute(JobExecutionContext context) throws JobExecutionException {
         ScheduleTask task = (ScheduleTask) context.getJobDetail().getJobDataMap()
            .get(ScheduleTask.class.getName());

         if(task == null) {
            return;
         }

         try {
            task.run(null);
         }
         catch(Exception e) {
            throw new JobExecutionException(e);
         }
         catch(Throwable e) {
            throw new JobExecutionException(new RuntimeException(e));
         }
      }
   }
}
