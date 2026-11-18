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

import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.schedule.jobstore.ClusterJobStore;
import inetsoft.sree.schedule.jobstore.RunningJob;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.quartz.*;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81673 -- Stop Task only interrupted the run on the node that executes the job; on any
 * other node of the cluster it did nothing. {@code Scheduler.cancelTask} now forwards the cancel to
 * the owner of the run recorded in the cluster job store, and the owner interrupts it.
 *
 * <p>Drives the real {@code cancelTask} / {@code handleCancel} on a Scheduler created without its
 * constructor, with a mock Quartz scheduler and a mock {@link Cluster} (on
 * {@link SchedulerTestHarness}'s mocked beans).
 */
@Tag("core")
class SchedulerCancelForwardTest {
   private static final String TASK = "admin:long-task";
   private static final String FIRE_ID = "fire-1";
   private static final String LOCAL_ID = "node-local";
   private static final String OWNER_ID = "node-owner";
   private static final String OWNER_MEMBER = "10.0.0.2:47500";

   private SchedulerTestHarness harness;
   private Scheduler scheduler;
   private org.quartz.Scheduler quartz;
   private Cluster cluster;
   private ClusterJobStore store;
   private MockedStatic<ClusterJobStore> storeStatic;

   @BeforeEach
   void setUp() throws Exception {
      harness = new SchedulerTestHarness();
      // the harness registers its mock Cluster as the Cluster bean
      cluster = Cluster.getInstance();
      when(cluster.getLocalNodeId()).thenReturn(LOCAL_ID);
      when(cluster.getClusterNodeIds()).thenReturn(Set.of(LOCAL_ID, OWNER_ID));

      scheduler = mock(Scheduler.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      quartz = mock(org.quartz.Scheduler.class);
      set("scheduler", quartz);
      set("running", new AtomicBoolean(true));

      store = mock(ClusterJobStore.class);
      storeStatic = mockStatic(ClusterJobStore.class);
      storeStatic.when(() -> ClusterJobStore.forScheduler(quartz)).thenReturn(store);
   }

   @AfterEach
   void tearDown() throws Exception {
      storeStatic.close();
      harness.close();
   }

   private void set(String field, Object value) throws Exception {
      Field f = Scheduler.class.getDeclaredField(field);
      f.setAccessible(true);
      f.set(scheduler, value);
   }

   private static RunningJob run(String ownerNode, String ownerMember) {
      return new RunningJob(FIRE_ID, TriggerKey.triggerKey("t", Scheduler.GROUP_NAME), ownerNode,
                            ownerMember, System.currentTimeMillis());
   }

   /** Makes this node execute the job with the given fire instance id. */
   private InterruptableJob executeLocally(String fireId) throws Exception {
      InterruptableJob job = mock(InterruptableJob.class);
      JobExecutionContext context = mock(JobExecutionContext.class);
      JobDetail detail = mock(JobDetail.class);
      when(detail.getKey()).thenReturn(new JobKey(TASK, Scheduler.GROUP_NAME));
      when(context.getJobDetail()).thenReturn(detail);
      when(context.getJobInstance()).thenReturn(job);
      when(context.getFireInstanceId()).thenReturn(fireId);
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of(context));
      return job;
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private void verifyNoForward() throws Exception {
      verify(cluster, never()).exchangeMessages(
         anyString(), any(), (Function) any(), anyLong(), any(TimeUnit.class));
   }

   @Test
   void localRunIsInterruptedAndNothingIsForwarded() throws Exception {
      InterruptableJob job = executeLocally(FIRE_ID);
      when(store.getRun(any())).thenReturn(run(OWNER_ID, OWNER_MEMBER));

      scheduler.cancelTask(TASK);

      verify(job).interrupt();
      verifyNoForward();
   }

   @Test
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void runOnAnotherNodeIsForwardedToItsOwnerMember() throws Exception {
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of());
      when(store.getRun(new JobKey(TASK, Scheduler.GROUP_NAME)))
         .thenReturn(run(OWNER_ID, OWNER_MEMBER));
      doReturn(Boolean.TRUE).when(cluster).exchangeMessages(
         anyString(), any(), (Function) any(), anyLong(), any(TimeUnit.class));

      scheduler.cancelTask(TASK);

      ArgumentCaptor<java.io.Serializable> msg = ArgumentCaptor.forClass(java.io.Serializable.class);
      ArgumentCaptor<Function> matcher = ArgumentCaptor.forClass(Function.class);
      verify(cluster).exchangeMessages(eq(OWNER_MEMBER), msg.capture(), matcher.capture(),
                                       anyLong(), eq(TimeUnit.SECONDS));
      CancelScheduleTask sent = assertInstanceOf(CancelScheduleTask.class, msg.getValue());
      assertEquals(TASK, sent.getTaskName());
      assertEquals(FIRE_ID, sent.getFireInstanceId());

      // the matcher accepts the answer to this run only
      Function<inetsoft.sree.internal.cluster.MessageEvent, Boolean> m = matcher.getValue();
      assertEquals(Boolean.TRUE, m.apply(event(new CancelScheduleTaskResult(TASK, FIRE_ID, true))));
      assertNull(m.apply(event(new CancelScheduleTaskResult(TASK, "other", true))));
      assertNull(m.apply(event(new CancelScheduleTaskResult("other", FIRE_ID, true))));
      assertNull(m.apply(event("unrelated")));
   }

   @Test
   void noRunRecordIsANoOp() throws Exception {
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of());
      when(store.getRun(any())).thenReturn(null);

      assertDoesNotThrow(() -> scheduler.cancelTask(TASK));

      verifyNoForward();
   }

   @Test
   void ownerThatLeftTheClusterIsNotAskedAndNoErrorIsRaised() throws Exception {
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of());
      when(store.getRun(any())).thenReturn(run("node-gone", "10.0.0.9:47500"));

      assertDoesNotThrow(() -> scheduler.cancelTask(TASK));

      verifyNoForward();
   }

   @Test
   @SuppressWarnings({ "unchecked", "rawtypes" })
   void ownerThatDoesNotAnswerIsNotAnError() throws Exception {
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of());
      when(store.getRun(any())).thenReturn(run(OWNER_ID, OWNER_MEMBER));
      doThrow(new InterruptedException("timeout")).when(cluster).exchangeMessages(
         anyString(), any(), (Function) any(), anyLong(), any(TimeUnit.class));

      assertDoesNotThrow(() -> scheduler.cancelTask(TASK));
   }

   @Test
   void nonClusterJobStoreIsANoOp() throws Exception {
      when(quartz.getCurrentlyExecutingJobs()).thenReturn(List.of());
      storeStatic.when(() -> ClusterJobStore.forScheduler(quartz)).thenReturn(null);

      assertDoesNotThrow(() -> scheduler.cancelTask(TASK));

      verifyNoForward();
   }

   @Test
   void targetIsNullForOwnRunMissingRecordOrDeadOwner() {
      Set<String> live = Set.of(LOCAL_ID, OWNER_ID);

      assertEquals(OWNER_MEMBER,
                   Scheduler.getCancelTarget(run(OWNER_ID, OWNER_MEMBER), LOCAL_ID, live));
      assertNull(Scheduler.getCancelTarget(null, LOCAL_ID, live));
      // the record of this node's own run but no job instance (finishing, or not started yet)
      assertNull(Scheduler.getCancelTarget(run(LOCAL_ID, "me"), LOCAL_ID, live));
      assertNull(Scheduler.getCancelTarget(run("node-gone", "x"), LOCAL_ID, live));
      assertNull(Scheduler.getCancelTarget(run(OWNER_ID, OWNER_MEMBER), LOCAL_ID, null));
      assertNull(Scheduler.getCancelTarget(run(OWNER_ID, null), LOCAL_ID, live));
   }

   @Test
   void ownerInterruptsTheMatchingRunAndAnswersStopped() throws Exception {
      InterruptableJob job = executeLocally(FIRE_ID);

      scheduler.handleCancel("10.0.0.1:47500", new CancelScheduleTask(TASK, FIRE_ID));

      verify(job).interrupt();
      ArgumentCaptor<java.io.Serializable> reply = ArgumentCaptor.forClass(java.io.Serializable.class);
      verify(cluster).sendMessage(eq("10.0.0.1:47500"), reply.capture());
      CancelScheduleTaskResult result = assertInstanceOf(CancelScheduleTaskResult.class, reply.getValue());
      assertTrue(result.isStopped());
      assertEquals(FIRE_ID, result.getFireInstanceId());
   }

   @Test
   void ownerDoesNotStopANewerRunOfTheSameTask() throws Exception {
      InterruptableJob job = executeLocally("fire-2");

      scheduler.handleCancel("10.0.0.1:47500", new CancelScheduleTask(TASK, FIRE_ID));

      verify(job, never()).interrupt();
      ArgumentCaptor<java.io.Serializable> reply = ArgumentCaptor.forClass(java.io.Serializable.class);
      verify(cluster).sendMessage(eq("10.0.0.1:47500"), reply.capture());
      assertFalse(((CancelScheduleTaskResult) reply.getValue()).isStopped());
   }

   private static inetsoft.sree.internal.cluster.MessageEvent event(Object message) {
      return new inetsoft.sree.internal.cluster.MessageEvent("src", OWNER_MEMBER, false, message);
   }
}
