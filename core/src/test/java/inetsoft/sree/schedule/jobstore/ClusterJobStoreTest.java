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
package inetsoft.sree.schedule.jobstore;

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.Group;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SRPrincipal;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77168 -- same regression as {@code ScheduleTaskJobTest}, for
 * {@link ClusterJobStore}'s private {@code setPrincipal(JobDetail)}, called from
 * {@code storeJob()}/{@code storeJobAndTrigger()} every time a job is (re)stored. With security
 * disabled, a kept-but-unresolved execute-as placeholder identity (Bug #77120/#77168) must not
 * be used to build the context principal -- it must fall back to the task owner.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ClusterJobStoreTest {
   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void setPrincipal_securityDisabled_placeholderIdentity_fallsBackToOwnerPrincipal()
      throws Exception
   {
      IdentityID owner = new IdentityID("owner1", "host-org");
      IdentityID unresolvedGroup = new IdentityID("phantom-group", "host-org");

      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getOwner()).thenReturn(owner);
      when(task.getIdentity()).thenReturn(new Group(unresolvedGroup));

      SRPrincipal ownerPrincipal = mock(SRPrincipal.class);

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, org.mockito.Answers.CALLS_REAL_METHODS);
          MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<SecurityEngine> securityEngineStatic = mockStatic(SecurityEngine.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         tool.when(Tool::getIP).thenReturn("127.0.0.1");
         scheduleManager.when(() -> ScheduleManager.isInternalTask(anyString())).thenReturn(false);

         SecurityEngine engine = mock(SecurityEngine.class);
         when(engine.isSecurityEnabled()).thenReturn(false);
         securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

         sUtil.when(() -> SUtil.getScheduleTaskOwnerPrincipal(eq(owner), anyString(), eq(true)))
            .thenReturn(ownerPrincipal);

         invokeSetPrincipal(buildJobDetail("t1", task));

         sUtil.verify(() -> SUtil.getPrincipal(any(Identity.class), anyString(), anyBoolean()),
                       never());
         assertSame(ownerPrincipal, ThreadContext.getContextPrincipal());
      }
   }

   @Test
   void setPrincipal_securityEnabled_resolvableIdentity_usesIdentityPrincipal() throws Exception {
      Group group = new Group(new IdentityID("g1", "host-org"));

      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getIdentity()).thenReturn(group);

      SRPrincipal identityPrincipal = mock(SRPrincipal.class);

      try(MockedStatic<Tool> tool = mockStatic(Tool.class, org.mockito.Answers.CALLS_REAL_METHODS);
          MockedStatic<ScheduleManager> scheduleManager = mockStatic(ScheduleManager.class);
          MockedStatic<SecurityEngine> securityEngineStatic = mockStatic(SecurityEngine.class);
          MockedStatic<SUtil> sUtil = mockStatic(SUtil.class))
      {
         tool.when(Tool::getIP).thenReturn("127.0.0.1");
         scheduleManager.when(() -> ScheduleManager.isInternalTask(anyString())).thenReturn(false);

         SecurityEngine engine = mock(SecurityEngine.class);
         when(engine.isSecurityEnabled()).thenReturn(true);
         securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(engine);

         sUtil.when(() -> SUtil.getPrincipal(same(group), anyString(), eq(true)))
            .thenReturn(identityPrincipal);

         invokeSetPrincipal(buildJobDetail("t1", task));

         sUtil.verify(() -> SUtil.getScheduleTaskOwnerPrincipal(any(), anyString(), anyBoolean()),
                       never());
         assertSame(identityPrincipal, ThreadContext.getContextPrincipal());
      }
   }

   private static JobDetail buildJobDetail(String taskName, ScheduleTask task) {
      JobDataMap dataMap = new JobDataMap();
      dataMap.put(ScheduleTask.class.getName(), task);

      JobDetail jobDetail = mock(JobDetail.class);
      when(jobDetail.getKey()).thenReturn(new JobKey(taskName, "group"));
      when(jobDetail.getJobDataMap()).thenReturn(dataMap);
      return jobDetail;
   }

   /** {@code setPrincipal(JobDetail)} is private -- invoked via reflection, as it is only ever
    *  called internally from {@code storeJob()}/{@code storeJobAndTrigger()}, which need a fully
    *  initialized cluster (Ignite) job store that is unnecessary to exercise this guard. */
   private static void invokeSetPrincipal(JobDetail job) throws Exception {
      ClusterJobStore store = new ClusterJobStore();
      Method method = ClusterJobStore.class.getDeclaredMethod("setPrincipal", JobDetail.class);
      method.setAccessible(true);
      method.invoke(store, job);
   }
}
