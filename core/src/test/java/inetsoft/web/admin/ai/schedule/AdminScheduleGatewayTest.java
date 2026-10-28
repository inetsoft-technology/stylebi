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

import inetsoft.report.internal.Util;
import inetsoft.sree.*;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.ScheduleConditionService;
import inetsoft.web.admin.schedule.ScheduleService;
import inetsoft.web.admin.schedule.ScheduleTaskService;
import inetsoft.web.api.schedule.BatchAction;
import inetsoft.web.api.schedule.IndividualAssetBackupAction;
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

      // security disabled, so the owner and execute-as identity checks of Bug #77281 don't
      // restrict the caller; the tests below are about the ADMIN and org-boundary checks
      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      gateway = new AdminScheduleGateway(
         repository, scheduleManager, scheduleService, scheduleConditionService, scheduleTaskService,
         securityEngine);

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

   // -------------------------------------------------------------------------
   // Bug #78959: the folder path of a task must be readable (list and get)
   // -------------------------------------------------------------------------

   private inetsoft.sree.schedule.ScheduleTask taskAt(String name, String path) {
      inetsoft.sree.schedule.ScheduleTask task =
         new inetsoft.sree.schedule.ScheduleTask(name);
      task.setOwner(callerOwner);
      task.setPath(path);
      return task;
   }

   @Test
   void getScheduleTasks_includesFolderPathAndRootForNullOrEmpty() throws Exception {
      inetsoft.sree.schedule.ScheduleTask inFolder = taskAt("inFolder", "A/B");
      inetsoft.sree.schedule.ScheduleTask atRoot = taskAt("atRoot", "/");
      inetsoft.sree.schedule.ScheduleTask nullPath = taskAt("nullPath", null);
      inetsoft.sree.schedule.ScheduleTask emptyPath = taskAt("emptyPath", "");
      when(repository.getScheduleTasks(user)).thenReturn(new String[]{
         inFolder.getTaskId(), atRoot.getTaskId(), nullPath.getTaskId(), emptyPath.getTaskId()});
      when(scheduleManager.getScheduleTask(inFolder.getTaskId())).thenReturn(inFolder);
      when(scheduleManager.getScheduleTask(atRoot.getTaskId())).thenReturn(atRoot);
      when(scheduleManager.getScheduleTask(nullPath.getTaskId())).thenReturn(nullPath);
      when(scheduleManager.getScheduleTask(emptyPath.getTaskId())).thenReturn(emptyPath);

      AdminScheduleTaskList list = gateway.getScheduleTasks(null, user);

      assertEquals(List.of("A/B", "/", "/", "/"),
                   list.getTasks().stream().map(ScheduleTaskEntry::getPath).toList());
      assertEquals("inFolder", list.getTasks().get(0).getName());
      assertEquals(callerOwner, list.getTasks().get(0).getOwner());
   }

   @Test
   void getScheduleTaskPath_returnsFolderPathOfTask() throws Exception {
      inetsoft.sree.schedule.ScheduleTask task = taskAt("t", "A/B");
      when(repository.getScheduleTask(task.getTaskId())).thenReturn(task);
      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, task.getTaskId(),
                                      ResourceAction.READ)).thenReturn(true);

      assertEquals("A/B", gateway.getScheduleTaskPath(task.getTaskId(), user));

      task.setPath(null);
      assertEquals("/", gateway.getScheduleTaskPath(task.getTaskId(), user));
   }

   @Test
   void getScheduleTaskPath_requiresReadPermission() throws Exception {
      inetsoft.sree.schedule.ScheduleTask task = taskAt("t", "A/B");
      when(repository.getScheduleTask(task.getTaskId())).thenReturn(task);

      assertThrows(UnauthorizedAccessException.class,
                   () -> gateway.getScheduleTaskPath(task.getTaskId(), user));
   }

   @Test
   void getScheduleTaskPath_rejectsMissingTask() throws Exception {
      when(repository.getScheduleTask("nope")).thenReturn(null);

      assertThrows(MissingResourceException.class, () -> gateway.getScheduleTaskPath("nope", user));
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

   // -------------------------------------------------------------------------
   // convertAction(ViewsheetAction) -- bookmarkNames/bookmarkUsers null/length guard
   // (Redmine #76628, restoring #76610's ASC-004 guard for this gateway's own convertAction
   // after it was silently lost to a submodule-sync commit -- with the empty-bookmarkNames
   // short-circuit included from the start, unlike the original ASC-004 guard)
   // -------------------------------------------------------------------------

   @Test
   void addScheduleTask_rejectsBookmarkNamesWithoutBookmarkUsers() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setBookmarkNames(List.of("(Home)"));

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      assertThrows(IllegalArgumentException.class, () -> gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user));

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void addScheduleTask_rejectsBookmarkNamesAndBookmarkUsersLengthMismatch() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setBookmarkNames(List.of("(Home)", "bk1"));
      action.setBookmarkUsers(List.of(callerOwner));

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      assertThrows(IllegalArgumentException.class, () -> gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user));

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void addScheduleTask_allowsBookmarkNamesAndBookmarkUsersOmittedTogether() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user);

      verify(scheduleManager).setScheduleTask(anyString(), any(), eq(user));
   }

   @Test
   void addScheduleTask_allowsBookmarkNamesAndBookmarkUsersSameLength() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setBookmarkNames(List.of("(Home)", "bk1"));
      action.setBookmarkUsers(List.of(callerOwner, otherOwner));

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user);

      verify(scheduleManager).setScheduleTask(anyString(), any(), eq(user));
   }

   @Test
   void addScheduleTask_allowsEmptyBookmarkNamesWithoutBookmarkUsers() throws Exception {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setBookmarkNames(List.of());

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user);

      verify(scheduleManager).setScheduleTask(anyString(), any(), eq(user));
   }

   // -------------------------------------------------------------------------
   // runTask / stopTask (bug 76687) -- scheduler-not-running/task-disabled are checked up front
   // so the returned ScheduleActionOutcome.status is a field-named discriminator, not a bare
   // Catalog-localized message string.
   // -------------------------------------------------------------------------

   private MockedStatic<OrganizationManager> mockOrg() {
      MockedStatic<OrganizationManager> orgManagerStatic =
         mockStatic(OrganizationManager.class, withSettings().lenient());
      OrganizationManager orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID(user)).thenReturn("host-org");
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      return orgManagerStatic;
   }

   @Test
   void runTask_schedulerNotRunning() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(false);

         ScheduleActionOutcome result = gateway.runTask("t1", user);

         assertEquals("t1", result.taskName());
         assertEquals(ScheduleActionOutcome.SCHEDULER_NOT_RUNNING, result.status());
         assertNull(result.error());
         verify(scheduleService, never()).runScheduledTask(anyString(), any());
      }
   }

   @Test
   void runTask_taskDisabled() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(true);
         inetsoft.sree.schedule.ScheduleTask task = new inetsoft.sree.schedule.ScheduleTask();
         task.setEnabled(false);
         when(scheduleManager.getScheduleTask("t1", "host-org")).thenReturn(task);

         ScheduleActionOutcome result = gateway.runTask("t1", user);

         assertEquals(ScheduleActionOutcome.TASK_DISABLED, result.status());
         verify(scheduleService, never()).runScheduledTask(anyString(), any());
      }
   }

   @Test
   void runTask_started() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(true);
         when(scheduleManager.getScheduleTask("t1", "host-org")).thenReturn(null);

         ScheduleActionOutcome result = gateway.runTask("t1", user);

         assertEquals(ScheduleActionOutcome.STARTED, result.status());
         assertNull(result.error());
         verify(scheduleService).runScheduledTask("t1", user);
      }
   }

   @Test
   void runTask_failedSurfacesUnderlyingMessage() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(true);
         when(scheduleManager.getScheduleTask("t1", "host-org")).thenReturn(null);
         doThrow(new Exception("boom")).when(scheduleService).runScheduledTask("t1", user);

         ScheduleActionOutcome result = gateway.runTask("t1", user);

         assertEquals(ScheduleActionOutcome.FAILED, result.status());
         assertEquals("boom", result.error());
      }
   }

   @Test
   void stopTask_schedulerNotRunning() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(false);

         ScheduleActionOutcome result = gateway.stopTask("t1", user);

         assertEquals(ScheduleActionOutcome.SCHEDULER_NOT_RUNNING, result.status());
         verify(scheduleService, never()).stopScheduledTask(anyString(), any());
      }
   }

   @Test
   void stopTask_taskDisabled() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(true);
         inetsoft.sree.schedule.ScheduleTask task = new inetsoft.sree.schedule.ScheduleTask();
         task.setEnabled(false);
         when(scheduleManager.getScheduleTask("t1", "host-org")).thenReturn(task);

         ScheduleActionOutcome result = gateway.stopTask("t1", user);

         assertEquals(ScheduleActionOutcome.TASK_DISABLED, result.status());
         verify(scheduleService, never()).stopScheduledTask(anyString(), any());
      }
   }

   @Test
   void stopTask_stoppedRegardlessOfTaskCurrentRunStatus() throws Exception {
      // No precondition read of the task's current run status -- ScheduleTaskStatus.lastRunStatus
      // is a Catalog-localized string, not a stable enum (see AdminScheduleGateway#stopTask's own
      // javadoc), so an idle task's stop is whatever ScheduleService itself reports here, not a
      // plugin-refused call.
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(true);
         when(scheduleManager.getScheduleTask("t1", "host-org")).thenReturn(null);

         ScheduleActionOutcome result = gateway.stopTask("t1", user);

         assertEquals(ScheduleActionOutcome.STOPPED, result.status());
         verify(scheduleService).stopScheduledTask("t1", user);
      }
   }

   @Test
   void stopTask_failedSurfacesUnderlyingMessage() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(scheduleService.isSchedulerReady()).thenReturn(true);
         when(scheduleManager.getScheduleTask("t1", "host-org")).thenReturn(null);
         doThrow(new Exception("boom")).when(scheduleService).stopScheduledTask("t1", user);

         ScheduleActionOutcome result = gateway.stopTask("t1", user);

         assertEquals(ScheduleActionOutcome.FAILED, result.status());
         assertEquals("boom", result.error());
      }
   }

   // -------------------------------------------------------------------------
   // fixes of the public schedule API ported to this gateway
   // -------------------------------------------------------------------------

   // Bug #77297: a create request without endDate has 0, which must not become an end date in
   // 1970 that keeps the task from ever being scheduled
   @Test
   void addScheduleTask_omittedEndDateMeansNoEndDate() throws Exception {
      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, 0, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));
      assertNull(captor.getValue().getEndDate());
   }

   // Bug #79432: a create request without startDate has 0, which must not be stored as a start
   // date in 1970
   @Test
   void addScheduleTask_omittedStartDateMeansNoStartDate() throws Exception {
      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, 0, 0, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));
      assertNull(captor.getValue().getStartDate());
   }

   @Test
   void addScheduleTask_keepsAnExplicitStartDate() throws Exception {
      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, 1_800_000_000_000L, 0, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));
      assertEquals(1_800_000_000_000L, captor.getValue().getStartDate().getTime());
   }
   // Bug #77531: an internal task may only be the target of a batch action of a site admin
   @Test
   void addScheduleTask_rejectsBatchActionTargetingInternalTaskForNonSiteAdmin() throws Exception {
      try(MockedStatic<OrganizationManager> orgManagerStatic = mockOrg()) {
         when(OrganizationManager.getInstance().isSiteAdmin((Principal) any())).thenReturn(false);

         BatchAction action = new BatchAction();
         action.setOwner("batchOwner");
         action.setTaskName(InternalScheduledTaskService.ASSET_FILE_BACKUP);

         ScheduleTaskMetaData taskMetaData =
            new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         assertThrows(UnauthorizedAccessException.class, () -> gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", user));

         verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
      }
   }

   // Bug #77954: an identifier that names no asset would be stored as a null asset
   @Test
   void addScheduleTask_rejectsInvalidBackupAssetIdentifier() throws Exception {
      IndividualAssetBackupAction action = new IndividualAssetBackupAction();
      action.setAssetIdentifiers(List.of("no.such.AssetClass^x"));

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      assertThrows(MissingResourceException.class, () -> gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user));

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   // Bug #77937: the API returns a stored password as a placeholder, which an added task has no
   // stored password for, so it must not be stored as the password itself
   @Test
   void addScheduleTask_doesNotStorePlaceholderPassword() throws Exception {
      ViewsheetAction.SaveToServerFilePath filePath =
         new ViewsheetAction.SaveToServerFilePath(ViewsheetAction.Format.PDF, "ftp://host/dir");
      filePath.setUsername("ftpuser");
      filePath.setPassword(Util.PLACEHOLDER_PASSWORD);

      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setSaveToServerFilePaths(List.of(filePath));

      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(action), null, "", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));
      inetsoft.sree.schedule.ViewsheetAction stored =
         (inetsoft.sree.schedule.ViewsheetAction) captor.getValue().getAction(0);
      inetsoft.sree.schedule.ServerPathInfo info =
         stored.getFilePathInfo(ViewsheetAction.Format.PDF.getTypeValue());
      assertEquals("ftpuser", info.getUsername());
      assertNull(info.getPassword());
   }

   // Bug #78125: an execute-as identity that doesn't resolve must be refused, not stored as no
   // identity, which would make the task run as its owner
   @Test
   void addScheduleTask_rejectsUnresolvableExecuteAsIdentity() throws Exception {
      IdentityID group = new IdentityID("missingGroup", "host-org");
      when(repository.checkPermission(user, ResourceType.SECURITY_GROUP, group, ResourceAction.ADMIN))
         .thenReturn(true);
      ScheduleTask.ExecuteAsID executeAs =
         new ScheduleTask.ExecuteAsID(ScheduleTask.ExecuteAsID.Type.GROUP, group);

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(() -> SUtil.getIdentity(any(IdentityID.class), anyInt())).thenReturn(null);
         ScheduleTaskMetaData taskMetaData =
            new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         assertThrows(MessageException.class, () -> gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, executeAs,
            Collections.emptyList(), Collections.emptyList(), null, "", user));
         assertFalse(gateway.hasOwnerAdminPermission(callerOwner, executeAs, user),
                     "the re-create of the preflight would be refused the same way");
         verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
      }
   }

   private static void mockSession(MockedStatic<XSessionService> sessionStatic) {
      // SRPrincipal's constructor calls XSessionService.getService().createSessionID(), which
      // requires a Spring context. Mock it to avoid that dependency.
      XSessionService mockSessionService = mock(XSessionService.class);
      when(mockSessionService.createSessionID(anyString(), any())).thenReturn("session-id");
      sessionStatic.when(XSessionService::getService).thenReturn(mockSessionService);
   }

   // -------------------------------------------------------------------------
   // viewsheetExists (bug #79020): refuses only a provably absent sheet, fails open
   // -------------------------------------------------------------------------

   private static final String SHEET = "1^128^__NULL__^Examples/Sales Summary^host-org";

   private AdminScheduleGateway spyGateway() {
      return spy(gateway);
   }

   @Test
   void viewsheetExists_trueWhenStorageFindsIt() throws Exception {
      AdminScheduleGateway spy = spyGateway();
      doAnswer(inv -> inv.getArgument(0)).when(spy).findAssetEntry(any());

      assertTrue(spy.viewsheetExists(SHEET));
   }

   @Test
   void viewsheetExists_falseWhenPathAndOrgAreAbsent() throws Exception {
      AdminScheduleGateway spy = spyGateway();
      doReturn(null).when(spy).findAssetEntry(any());

      try(MockedStatic<OrganizationManager> orgStatic = mockStatic(OrganizationManager.class)) {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.getCurrentOrgID()).thenReturn("host-org");
         orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         assertFalse(spy.viewsheetExists(SHEET));
         assertFalse(spy.viewsheetExists("1^128^__NULL__^Examples/Missing^host-org"));
         assertFalse(spy.viewsheetExists("1^128^__NULL__^Examples/Sales Summary^no-such-org"));
      }
   }

   @Test
   void viewsheetExists_failsOpenOnAnyException() throws Exception {
      AdminScheduleGateway spy = spyGateway();
      doThrow(new IllegalStateException("storage down")).when(spy).findAssetEntry(any());

      assertTrue(spy.viewsheetExists(SHEET));
   }

   @Test
   void viewsheetExists_skipsAnUnparseableId() throws Exception {
      AdminScheduleGateway spy = spyGateway();
      doReturn(null).when(spy).findAssetEntry(any());

      assertTrue(spy.viewsheetExists("not-an-asset-id"));
      verify(spy, never()).findAssetEntry(any());
   }

   @Test
   void viewsheetExists_retriesWithTheCurrentOrgWhenOnlyTheCaseDiffers() throws Exception {
      AdminScheduleGateway spy = spyGateway();
      doAnswer(inv -> {
         inetsoft.uql.asset.AssetEntry entry = inv.getArgument(0);
         return "Host-Org".equals(entry.getOrgID()) ? entry : null;
      }).when(spy).findAssetEntry(any());

      try(MockedStatic<OrganizationManager> orgStatic = mockStatic(OrganizationManager.class)) {
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.getCurrentOrgID()).thenReturn("Host-Org");
         orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

         assertTrue(spy.viewsheetExists(SHEET));
      }
   }

   // -------------------------------------------------------------------------
   // previewConditionResets -- what the permission-based sanitize would not store (Bug #79424)
   // -------------------------------------------------------------------------

   @Test
   void previewConditionResets_reportsTimeRangeThatSanitizeResets() {
      doAnswer(inv -> {
         inetsoft.sree.schedule.ScheduleTask scratch = inv.getArgument(0);
         ((inetsoft.sree.schedule.TimeCondition) scratch.getCondition(0)).setTimeRange(
            new TimeRange("Morning", "06:00:00", "12:00:00", false));
         return null;
      }).when(scheduleTaskService).sanitizeConditions(
         any(inetsoft.sree.schedule.ScheduleTask.class),
         any(inetsoft.sree.schedule.ScheduleTask.class), any());
      // the sanitize above only simulates a reset by changing the value, which is what the
      // gateway compares; the real rule is exercised through ScheduleTaskService's own tests
      List<String> resets = gateway.previewConditionResets(List.of(everyDay()), user);

      assertEquals(1, resets.size());
      assertTrue(resets.get(0).startsWith("timeRange:"), resets.get(0));
   }

   @Test
   void previewConditionResets_reportsConditionThatSanitizeDrops() {
      doAnswer(inv -> {
         inetsoft.sree.schedule.ScheduleTask scratch = inv.getArgument(0);
         scratch.removeCondition(0);
         return null;
      }).when(scheduleTaskService).sanitizeConditions(
         any(inetsoft.sree.schedule.ScheduleTask.class),
         any(inetsoft.sree.schedule.ScheduleTask.class), any());

      List<String> resets = gateway.previewConditionResets(List.of(everyDay()), user);

      assertTrue(resets.get(0).startsWith("type:"), resets.get(0));
   }

   @Test
   void previewConditionResets_reportsResetTimeOfDay() {
      doAnswer(inv -> {
         inetsoft.sree.schedule.ScheduleTask scratch = inv.getArgument(0);
         ((inetsoft.sree.schedule.TimeCondition) scratch.getCondition(0)).setHour(1);
         return null;
      }).when(scheduleTaskService).sanitizeConditions(
         any(inetsoft.sree.schedule.ScheduleTask.class),
         any(inetsoft.sree.schedule.ScheduleTask.class), any());

      List<String> resets = gateway.previewConditionResets(List.of(everyDay()), user);

      assertTrue(resets.get(0).startsWith("hour/minute/second:"), resets.get(0));
   }

   @Test
   void previewConditionResets_isNullWhenNothingIsReset() {
      List<String> resets = gateway.previewConditionResets(List.of(everyDay()), user);

      assertEquals(Collections.singletonList(null), resets);
   }

   private static TimeCondition everyDay() {
      TimeCondition condition = new TimeCondition();
      condition.setType(TimeCondition.Type.EVERY_DAY);
      condition.setHour(3);
      condition.setMinute(0);
      condition.setSecond(0);
      condition.setInterval(1);
      return condition;
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
   private SecurityEngine securityEngine;
   private AdminScheduleGateway gateway;
   private Principal user;
   private IdentityID callerOwner;
   private IdentityID otherOwner;
}
