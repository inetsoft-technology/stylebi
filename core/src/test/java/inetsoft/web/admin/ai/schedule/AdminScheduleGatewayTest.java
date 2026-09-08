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
package inetsoft.web.admin.ai.schedule;

/*
 * Direct unit coverage for AdminScheduleGateway, added per PR #5062 review: the four ported tests
 * in this same directory (AdminScheduleControllerTest, ScheduleChangePlanServiceTest,
 * ScheduleChangesetApplyServiceTest, ScheduleXmlProjectionTest) all mock this class out and only
 * assert delegation, so none of them exercise its own logic -- the cross-org boundary check and
 * the two inverse-permission preflights in particular. Mirrors the mocking/fixture pattern of
 * enterprise's ScheduleApiServiceTest (the class this gateway is modeled line-by-line on), since
 * both depend on the same community-native AnalyticRepository/ScheduleManager/OrganizationManager
 * types.
 */

import inetsoft.sree.*;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.web.admin.schedule.ScheduleConditionService;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.api.schedule.BatchAction;
import inetsoft.web.api.schedule.ScheduleAction;
import inetsoft.web.api.schedule.ScheduleActionList;
import inetsoft.web.api.schedule.ScheduleCondition;
import inetsoft.web.api.schedule.ScheduleConditionList;
import inetsoft.web.api.schedule.ScheduleTask;
import inetsoft.web.api.schedule.TimeCondition;
import inetsoft.web.api.schedule.ViewsheetAction;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class AdminScheduleGatewayTest {
   @BeforeEach
   void setUp() throws Exception {
      repository = mock(AnalyticRepository.class, withSettings().lenient());
      scheduleManager = mock(ScheduleManager.class, withSettings().lenient());
      scheduleService = mock(ScheduleService.class, withSettings().lenient());
      scheduleConditionService = mock(ScheduleConditionService.class, withSettings().lenient());
      scheduleTaskService = mock(ScheduleTaskService.class, withSettings().lenient());

      // caller always has generic scheduler access -- most tests here are about the
      // owner/executeAsID/org-boundary checks specifically, not this coarse-grained gate
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(repository.getScheduleTask(anyString())).thenReturn(null);

      gateway = new AdminScheduleGateway(
         repository, scheduleManager, scheduleService, scheduleConditionService, scheduleTaskService);

      callerOwner = new IdentityID("caller", "host-org");
      otherOwner = new IdentityID("admin", "host-org");
      user = mock(Principal.class, withSettings().lenient());
      when(user.getName()).thenReturn(callerOwner.convertToKey());
   }

   // -------------------------------------------------------------------------
   // checkActionOrgBoundary / checkAssetOrgBoundary -- the cross-org security check
   // -------------------------------------------------------------------------

   @Test
   void addScheduleTask_rejectsViewsheetActionFromDifferentOrganization() throws Exception {
      try(MockedStatic<XSessionService> sessionStatic = mockStatic(XSessionService.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         mockSession(sessionStatic);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(false);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         SRPrincipal orgUser = createPrincipal("caller", "host-org");

         ViewsheetAction action = new ViewsheetAction();
         action.setViewsheet("1^128^__NULL__^Examples/Census^other-org");

         ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         assertThrows(UnauthorizedAccessException.class, () -> gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", orgUser));

         verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
      }
   }

   @Test
   void addScheduleTask_allowsViewsheetActionFromSameOrganization() throws Exception {
      try(MockedStatic<XSessionService> sessionStatic = mockStatic(XSessionService.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         mockSession(sessionStatic);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(false);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         SRPrincipal orgUser = createPrincipal("caller", "host-org");

         ViewsheetAction action = new ViewsheetAction();
         action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");

         ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", orgUser);

         verify(scheduleManager).setScheduleTask(anyString(), any(), eq(orgUser));
      }
   }

   @Test
   void addScheduleTask_allowsSiteAdminAcrossOrganizationsForViewsheetAction() throws Exception {
      try(MockedStatic<XSessionService> sessionStatic = mockStatic(XSessionService.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         mockSession(sessionStatic);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(true);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         SRPrincipal siteAdmin = createPrincipal("caller", "host-org");

         // site admin's own org is "host-org" but the referenced viewsheet belongs to
         // "other-org" -- the site-admin bypass must still allow this
         ViewsheetAction action = new ViewsheetAction();
         action.setViewsheet("1^128^__NULL__^Examples/Census^other-org");

         ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", siteAdmin);

         verify(scheduleManager).setScheduleTask(anyString(), any(), eq(siteAdmin));
      }
   }

   @Test
   void addScheduleTask_rejectsBatchActionFromDifferentOrganization() throws Exception {
      try(MockedStatic<XSessionService> sessionStatic = mockStatic(XSessionService.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         mockSession(sessionStatic);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(false);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         SRPrincipal orgUser = createPrincipal("caller", "host-org");

         BatchAction action = new BatchAction();
         action.setOwner("batchOwner");
         action.setTaskName("batchTask");
         action.setQueryEntry("1^2^__NULL__^Products^other-org");

         ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         assertThrows(UnauthorizedAccessException.class, () -> gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", orgUser));

         verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
      }
   }

   // -------------------------------------------------------------------------
   // hasDeletePermission / hasOwnerAdminPermission -- the two inverse-permission preflights
   // -------------------------------------------------------------------------

   @Test
   void hasDeletePermission_true() throws Exception {
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, "t1", ResourceAction.DELETE))
         .thenReturn(true);

      assertTrue(gateway.hasDeletePermission("t1", user));
   }

   @Test
   void hasDeletePermission_false() throws Exception {
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, "t1", ResourceAction.DELETE))
         .thenReturn(false);

      assertFalse(gateway.hasDeletePermission("t1", user));
   }

   @Test
   void hasOwnerAdminPermission_true() throws Exception {
      when(repository.checkPermission(user, ResourceType.SECURITY_USER, otherOwner, ResourceAction.ADMIN))
         .thenReturn(true);

      assertTrue(gateway.hasOwnerAdminPermission(otherOwner, null, user));
   }

   @Test
   void hasOwnerAdminPermission_false() throws Exception {
      when(repository.checkPermission(user, ResourceType.SECURITY_USER, otherOwner, ResourceAction.ADMIN))
         .thenReturn(false);

      assertFalse(gateway.hasOwnerAdminPermission(otherOwner, null, user));
   }

   @Test
   void hasOwnerAdminPermission_trueForSelfOwnedWithoutAdminGrant() throws Exception {
      // caller always administers their own identity, regardless of any ADMIN grant
      assertTrue(gateway.hasOwnerAdminPermission(callerOwner, null, user));
   }

   // -------------------------------------------------------------------------
   // addScheduleTask / removeScheduleTask -- basic success and permission-denied paths
   // -------------------------------------------------------------------------

   @Test
   void addScheduleTask_allowsSelfOwnedTask() throws Exception {
      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user);

      verify(scheduleManager).setScheduleTask(anyString(), any(), eq(user));
   }

   @Test
   void addScheduleTask_rejectsOwnerImpersonationWithoutAdminGrant() throws Exception {
      when(repository.checkPermission(user, ResourceType.SECURITY_USER, otherOwner, ResourceAction.ADMIN))
         .thenReturn(false);

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", otherOwner.convertToKey());

      assertThrows(UnauthorizedAccessException.class, () -> gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user));

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void addScheduleTask_rejectsWithoutGenericSchedulerAccess() throws Exception {
      when(repository.checkPermission(user, ResourceType.SCHEDULER, "*", ResourceAction.ACCESS))
         .thenReturn(false);

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      assertThrows(UnauthorizedAccessException.class, () -> gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user));

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void addScheduleTask_rejectsWhenTaskAlreadyExists() throws Exception {
      String taskId = ScheduleManager.getTaskId(callerOwner.convertToKey(), "task1");
      when(repository.getScheduleTask(taskId)).thenReturn(new inetsoft.sree.schedule.ScheduleTask());

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      assertThrows(ResourceExistsException.class, () -> gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user));

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void removeScheduleTask_succeedsWithDeletePermission() throws Exception {
      String taskId = "t1";
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, taskId, ResourceAction.DELETE))
         .thenReturn(true);

      try(MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(true);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         gateway.removeScheduleTask(taskId, null, user);

         verify(repository).removeScheduleTask(user, taskId);
      }
   }

   @Test
   void removeScheduleTask_rejectsWithoutDeletePermission() throws Exception {
      String taskId = "t1";
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, taskId, ResourceAction.DELETE))
         .thenReturn(false);

      try(MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(true);
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         assertThrows(UnauthorizedAccessException.class,
                      () -> gateway.removeScheduleTask(taskId, null, user));

         verify(repository, never()).removeScheduleTask(any(), anyString());
      }
   }

   @Test
   void removeScheduleTask_rejectsCrossOrgDeleteForNonSiteAdmin() throws Exception {
      String taskId = "t1";

      try(MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class))
      {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(false);
         when(orgManager.getCurrentOrgID(user)).thenReturn("host-org");
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         try(MockedStatic<ScheduleManager> scheduleManagerStatic = mockStatic(ScheduleManager.class))
         {
            scheduleManagerStatic.when(() -> ScheduleManager.getOwner(taskId))
               .thenReturn(new IdentityID("someone", "other-org"));

            assertThrows(UnauthorizedAccessException.class,
                         () -> gateway.removeScheduleTask(taskId, null, user));

            verify(repository, never()).removeScheduleTask(any(), anyString());
         }
      }
   }

   @Test
   void getScheduleTask_rejectsMissingTask() throws Exception {
      when(repository.getScheduleTask("nope")).thenReturn(null);

      assertThrows(MissingResourceException.class,
                   () -> gateway.getScheduleTask("nope", null, user));
   }

   // -------------------------------------------------------------------------
   // condition/action DTO-to-internal-and-back round trip
   // -------------------------------------------------------------------------

   @Test
   void timeConditionRoundTripsThroughAddAndGet() throws Exception {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.Type.EVERY_DAY);
      condition.setHour(9);
      condition.setMinute(30);
      condition.setInterval(2);
      condition.setWeekdayOnly(true);

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());
      String taskId = taskMetaData.getTaskId();

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         List.of(condition), Collections.emptyList(), null, "", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(taskId), captor.capture(), eq(user));

      // feed the persisted internal task back through the read path
      when(repository.getScheduleTask(taskId)).thenReturn(captor.getValue());
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, taskId, ResourceAction.READ))
         .thenReturn(true);

      ScheduleConditionList result = gateway.getTaskConditions(taskId, null, user);

      assertEquals(1, result.getConditions().size());
      ScheduleCondition roundTripped = result.getConditions().get(0);
      assertTrue(roundTripped instanceof TimeCondition);
      TimeCondition rt = (TimeCondition) roundTripped;
      assertEquals(TimeCondition.Type.EVERY_DAY, rt.getType());
      assertEquals(9, rt.getHour());
      assertEquals(30, rt.getMinute());
      assertEquals(2, rt.getInterval());
      assertTrue(rt.isWeekdayOnly());
   }

   @Test
   void viewsheetActionRoundTripsThroughAddAndGet() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());
      String taskId = taskMetaData.getTaskId();

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(taskId), captor.capture(), eq(user));

      inetsoft.sree.schedule.ViewsheetAction persisted =
         (inetsoft.sree.schedule.ViewsheetAction) captor.getValue().getAction(0);
      assertEquals("1^128^__NULL__^Examples/Census^host-org", persisted.getViewsheet());

      // feed the persisted internal task back through the read path
      when(repository.getScheduleTask(taskId)).thenReturn(captor.getValue());
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, taskId, ResourceAction.READ))
         .thenReturn(true);

      ScheduleActionList result = gateway.getTaskActions(taskId, null, user);

      assertEquals(1, result.getActions().size());
      ScheduleAction roundTripped = result.getActions().get(0);
      assertTrue(roundTripped instanceof ViewsheetAction);
      assertEquals("1^128^__NULL__^Examples/Census^host-org",
                   ((ViewsheetAction) roundTripped).getViewsheet());
   }

   private static void mockSession(MockedStatic<XSessionService> sessionStatic) {
      // SRPrincipal's constructor calls XSessionService.getService().createSessionID(), which
      // requires a Spring context. Mock it to avoid that dependency.
      XSessionService mockSessionService = mock(XSessionService.class);
      when(mockSessionService.createSessionID(anyString(), any())).thenReturn("session-id");
      sessionStatic.when(XSessionService::getService).thenReturn(mockSessionService);
   }

   private static SRPrincipal createPrincipal(String name, String orgId) {
      return new SRPrincipal(
         new IdentityID(name, orgId), new IdentityID[0], new String[0], orgId, 1L);
   }

   private AnalyticRepository repository;
   private ScheduleManager scheduleManager;
   private ScheduleService scheduleService;
   private ScheduleConditionService scheduleConditionService;
   private ScheduleTaskService scheduleTaskService;
   private AdminScheduleGateway gateway;
   private Principal user;
   private IdentityID callerOwner;
   private IdentityID otherOwner;
}
