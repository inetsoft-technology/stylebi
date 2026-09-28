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
package inetsoft.sree.schedule.quartz;

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
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77168 -- with security disabled, a non-null {@code task.getIdentity()} may now be an
 * unresolved execute-as placeholder that {@code ScheduleTask.parseXML} keeps unconditionally
 * (Bug #77120/#77168), instead of always being {@code null} the way it was before. Because
 * {@code SUtil.getPrincipal(Identity, ...)} unconditionally fabricates a real (but meaningless)
 * principal for such a placeholder, {@link ScheduleTaskJob#execute} must only take the
 * identity-principal branch when security is enabled -- otherwise it must fall back to the
 * owner, exactly as it already does when {@code identity == null}.
 *
 * <p>{@code execute()} calls {@code ThreadContext.setContextPrincipal(principal)} and never
 * clears it -- by design, since in production this runs on a dedicated Quartz worker thread that
 * is about to execute the task under that principal. Run directly on the JUnit thread, that would
 * otherwise leak a mock principal into every later test sharing this JVM/thread, so it is reset in
 * {@link #tearDown()}.
 */
@ExtendWith(MockitoExtension.class)
@Tag("core")
class ScheduleTaskJobTest {
   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
   }

   @Test
   void execute_securityDisabled_placeholderIdentity_fallsBackToOwnerPrincipal() throws Exception {
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

         JobExecutionContext context = buildContext("t1", task);
         assertDoesNotThrow(() -> new ScheduleTaskJob().execute(context));

         // the placeholder identity must never reach SUtil.getPrincipal(Identity, ...)
         sUtil.verify(() -> SUtil.getPrincipal(any(Identity.class), anyString(), anyBoolean()),
                       never());
         sUtil.verify(() -> SUtil.runTask(same(ownerPrincipal), same(task), anyString()));
      }
   }

   @Test
   void execute_securityEnabled_resolvableIdentity_usesIdentityPrincipal() throws Exception {
      IdentityID owner = new IdentityID("owner1", "host-org");
      IdentityID groupId = new IdentityID("g1", "host-org");
      Group group = new Group(groupId);

      ScheduleTask task = mock(ScheduleTask.class);
      when(task.getOwner()).thenReturn(owner);
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

         JobExecutionContext context = buildContext("t1", task);
         assertDoesNotThrow(() -> new ScheduleTaskJob().execute(context));

         sUtil.verify(() -> SUtil.getScheduleTaskOwnerPrincipal(any(), anyString(), anyBoolean()),
                       never());
         sUtil.verify(() -> SUtil.runTask(same(identityPrincipal), same(task), anyString()));
      }
   }

   private static JobExecutionContext buildContext(String taskName, ScheduleTask task) {
      JobDataMap dataMap = new JobDataMap();
      dataMap.put(ScheduleTask.class.getName(), task);

      JobDetail jobDetail = mock(JobDetail.class);
      when(jobDetail.getKey()).thenReturn(new JobKey(taskName, "group"));
      when(jobDetail.getJobDataMap()).thenReturn(dataMap);

      JobExecutionContext context = mock(JobExecutionContext.class);
      when(context.getJobDetail()).thenReturn(jobDetail);
      return context;
   }
}
