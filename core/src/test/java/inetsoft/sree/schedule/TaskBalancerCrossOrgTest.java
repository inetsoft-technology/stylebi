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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77380: the task balancer balances the time range tasks of every organization, it must save
 * each task back to the organization it's stored in. It saved them all as internal tasks, which
 * copied the tasks of the other organizations into the host organization.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class TaskBalancerCrossOrgTest {
   private static final String HOST = Organization.getDefaultOrganizationID();
   private static final String ORG_A = "tborga";
   private static final String ORG_B = "tborgb";
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);

   @Autowired
   ScheduleManager scheduleManager;

   private final List<String> hostKeys = new ArrayList<>();

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);

      for(String key : hostKeys) {
         scheduleManager.getOrgTaskMap(HOST).remove(key);
      }

      hostKeys.clear();

      for(String org : new String[] { ORG_A, ORG_B }) {
         @SuppressWarnings("unchecked")
         Map<String, ScheduleTask> map =
            (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(org);
         map.clear();
      }
   }

   @Test
   void balanceTasks_taskOfOtherOrg_isSavedInItsOwnOrg() throws Exception {
      TimeRange range = getRangeNotContainingNow();
      ScheduleTask task = newRangeTask("tbNightly", BOB, range);
      String key = put(task, ORG_B);
      hostKeys.add(key(task.getTaskId(), HOST));

      new TaskBalancer().balanceTasks(range);

      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), HOST));
      assertFalse(scheduleManager.getScheduleTasks(HOST).stream()
                     .anyMatch(t -> t.getTaskId().equals(task.getTaskId())));
      assertBalanced(load(ORG_B, key));
      assertTrue(scheduleManager.logDuplicateTaskIds().isEmpty());
   }

   @Test
   void balanceTasks_sameTaskInTwoOrgs_isSavedInEachOrg() throws Exception {
      // a task stored with the same id in two organizations (e.g. saved before #77359), each is
      // balanced and saved in its own organization
      TimeRange range = getRangeNotContainingNow();
      IdentityID hostSystem = new IdentityID(XPrincipal.SYSTEM, HOST);
      ScheduleTask a = newRangeTask("tbShared", hostSystem, range);
      ScheduleTask b = newRangeTask("tbShared", hostSystem, range);
      String keyA = put(a, ORG_A);
      String keyB = put(b, ORG_B);
      hostKeys.add(key(a.getTaskId(), HOST));

      new TaskBalancer().balanceTasks(range);

      assertNull(scheduleManager.getScheduleTask(a.getTaskId(), HOST));
      assertBalanced(load(ORG_A, keyA));
      assertBalanced(load(ORG_B, keyB));
   }

   @Test
   void updateTask_unstoredTask_isSavedInCallerOrg() throws Exception {
      TimeRange range = getRangeContainingNow();
      ScheduleTask task = newRangeTask("tbUpdated", BOB, range);
      hostKeys.add(key(task.getTaskId(), HOST));
      ThreadContext.setContextPrincipal(principal(BOB, ORG_B));

      new TaskBalancer().updateTask(task, range);

      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), HOST));
      assertNotNull(load(ORG_B, key(task.getTaskId(), ORG_B)));
   }

   @Test
   void balanceTasks_hostOrgTask_staysInHostOrg() throws Exception {
      TimeRange range = getRangeNotContainingNow();
      ScheduleTask task = newRangeTask("tbHost", new IdentityID("admin", HOST), range);
      String key = put(task, HOST);
      hostKeys.add(key);

      new TaskBalancer().balanceTasks(range);

      assertBalanced(load(HOST, key));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_A));
      assertNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG_B));
   }

   @Test
   void balanceTasks_internalTask_isSavedInHostOrg() throws Exception {
      // an internal task is saved through the internal (host organization) save
      String name = InternalScheduledTaskService.UPDATE_ASSETS_DEPENDENCIES;
      String key = key(name, HOST);
      ScheduleTaskMap hostMap = scheduleManager.getOrgTaskMap(HOST);
      hostMap.clearCache();
      ScheduleTask original = hostMap.containsKey(key, HOST) ? hostMap.get(key) : null;
      TimeRange range = getRangeNotContainingNow();
      ScheduleTask task = new ScheduleTask(name, ScheduleTask.Type.INTERNAL_TASK);
      task.setOwner(new IdentityID(XPrincipal.SYSTEM, HOST));
      TimeCondition condition = TimeCondition.at(range.getStartTime().getHour(), 3, 33);
      condition.setTimeRange(range);
      task.addCondition(condition);
      put(task, HOST);

      try {
         new TaskBalancer().balanceTasks(range);

         assertBalanced(load(HOST, key));
         // getScheduleTask() always reads an internal task from the host organization
         for(String org : new String[] { ORG_A, ORG_B }) {
            assertFalse(scheduleManager.getOrgTaskMap(org).containsKey(key(name, org), org), org);
         }
      }
      finally {
         if(original != null) {
            hostMap.put(key, original, HOST);
         }
         else {
            hostMap.remove(key);
         }
      }
   }

   @Test
   void balanceTasks_taskNotSaved_otherTasksAreSaved() throws Exception {
      // the first balanced task that is saved fails, whichever it is, the other is still saved
      TimeRange range = getRangeNotContainingNow();
      ScheduleTask a = new FailingOnceTask("tbFailA");
      ScheduleTask b = new FailingOnceTask("tbFailB");
      String keyA = put(initRangeTask(a, new IdentityID("alice", ORG_A), range), ORG_A);
      String keyB = put(initRangeTask(b, BOB, range), ORG_B);
      hostKeys.add(key(a.getTaskId(), HOST));
      hostKeys.add(key(b.getTaskId(), HOST));
      FailingOnceTask.FAIL.set(true);

      try {
         new TaskBalancer().balanceTasks(range);
      }
      finally {
         FailingOnceTask.FAIL.set(false);
      }

      int balanced = (isBalanced(load(ORG_A, keyA)) ? 1 : 0) +
         (isBalanced(load(ORG_B, keyB)) ? 1 : 0);
      assertEquals(1, balanced);
   }

   // ---- helpers ----

   // a task with a time condition in a time range, with a start time that the balancer changes
   // (it only assigns times on 5 minute boundaries)
   private static ScheduleTask newRangeTask(String name, IdentityID owner, TimeRange range) {
      return initRangeTask(new ScheduleTask(name), owner, range);
   }

   private static ScheduleTask initRangeTask(ScheduleTask task, IdentityID owner,
                                             TimeRange range)
   {
      task.setOwner(owner);
      LocalTime start = range.getStartTime();
      TimeCondition condition = TimeCondition.at(start.getHour(), 3, 33);
      condition.setTimeRange(range);
      task.addCondition(condition);
      return task;
   }

   private static boolean isBalanced(ScheduleTask stored) {
      TimeCondition condition = (TimeCondition) stored.getCondition(0);
      return condition.getSecond() == 0 && condition.getMinute() % 5 == 0;
   }

   private static void assertBalanced(ScheduleTask stored) {
      TimeCondition condition = (TimeCondition) stored.getCondition(0);
      assertEquals(0, condition.getSecond(), stored.getTaskId());
      assertEquals(0, condition.getMinute() % 5, stored.getTaskId());
   }

   private static TimeRange getRangeContainingNow() {
      LocalTime now = LocalTime.now();
      return TimeRange.getTimeRanges().stream()
         .filter(r -> isInRange(now, r))
         .findFirst()
         .orElseThrow();
   }

   // not containing the current time, so the balancer starts at the range start (whole seconds)
   private static TimeRange getRangeNotContainingNow() {
      LocalTime now = LocalTime.now();
      return TimeRange.getTimeRanges().stream()
         .filter(r -> !isInRange(now.minusMinutes(10), r) && !isInRange(now.plusMinutes(10), r))
         .findFirst()
         .orElseThrow();
   }

   private static boolean isInRange(LocalTime time, TimeRange range) {
      LocalTime start = range.getStartTime();
      LocalTime end = range.getEndTime();
      return start.isAfter(end) ? !time.isBefore(start) || time.isBefore(end) :
         !time.isBefore(start) && time.isBefore(end);
   }

   private static String key(String taskId, String orgID) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.SCHEDULE_TASK,
                            "/" + taskId, SUtil.getTaskOwner(taskId), orgID).toIdentifier();
   }

   private String put(ScheduleTask task, String orgID) throws Exception {
      String key = key(task.getTaskId(), orgID);
      scheduleManager.getOrgTaskMap(orgID).put(key, task, orgID);
      return key;
   }

   // the task as it's stored, not the cached instance the balancer changed
   private ScheduleTask load(String orgID, String key) {
      ScheduleTaskMap map = scheduleManager.getOrgTaskMap(orgID);
      map.clearCache();
      ScheduleTask task = map.get(key);
      assertNotNull(task, key);
      return task;
   }

   private static SRPrincipal principal(IdentityID user, String orgID) {
      SRPrincipal principal = new SRPrincipal(user, new IdentityID[0], new String[0], orgID,
                                              Tool.getSecureRandom().nextLong());
      principal.setIgnoreLogin(true);
      return principal;
   }
   /**
    * A task that fails the first save after {@link #FAIL} is set. It's stored with its class, so
    * the balancer gets instances of it.
    */
   public static class FailingOnceTask extends ScheduleTask {
      public FailingOnceTask() {
      }

      FailingOnceTask(String name) {
         super(name);
      }

      @Override
      public void setLastModified(long lastModified) {
         if(FAIL.compareAndSet(true, false)) {
            throw new IllegalStateException("save failed: " + getTaskId());
         }

         super.setLastModified(lastModified);
      }

      static final java.util.concurrent.atomic.AtomicBoolean FAIL =
         new java.util.concurrent.atomic.AtomicBoolean();
   }
}
