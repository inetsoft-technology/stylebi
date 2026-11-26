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
import inetsoft.uql.XPrincipal;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

   // Bug #79974, the preflight builds the candidate task and runs the manager's no-write save check
   @Test
   void checkAddScheduleTaskSavable_delegatesCandidateTaskToManagerSaveCheck() throws Exception {
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
         ScheduleTaskMetaData meta = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());
         org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
            org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);

         gateway.checkAddScheduleTaskSavable(meta, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, orgUser);

         verify(scheduleManager).checkScheduleTaskSave(anyString(), captor.capture(), eq(orgUser));
         assertEquals(1, captor.getValue().getActionCount());
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

   // Bug #80587, a value that does not fit its integer/double dataType used to be saved as a
   // String and then dropped the whole action when the task was reloaded
   @Test
   void addScheduleTask_rejectsParameterValueThatDoesNotFitItsDataType() throws Exception {
      for(String[] bad : new String[][] {
         {"integer", "abc"}, {"integer", "1.5"}, {"integer", "99999999999"}, {"double", "x1"}})
      {
         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> addTaskWithParameter("rate", bad[0], bad[1]),
            bad[0] + " " + bad[1]);
         assertTrue(ex.getMessage().contains("'rate'"), ex.getMessage());
         assertTrue(ex.getMessage().contains(bad[0]), ex.getMessage());
      }

      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void addScheduleTask_acceptsParameterValueThatFitsItsDataType() throws Exception {
      // past validation the conversion needs DynamicValueModel, whose static init needs a Spring
      // context this unit test does not have; only the validation outcome is asserted here
      for(String[] good : new String[][] {{"integer", "42"}, {"double", "3.14"}}) {
         try {
            addTaskWithParameter("p", good[0], good[1]);
         }
         catch(IllegalArgumentException ex) {
            fail("valid " + good[0] + " " + good[1] + " was rejected: " + ex.getMessage());
         }
         catch(Throwable ignore) {
            // environment limitation described above
         }
      }
   }

   private void addTaskWithParameter(String name, String dataType, String value) throws Exception {
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
         action.setParameters(new java.util.HashMap<>(Map.of(name,
            new java.util.HashMap<>(Map.of("value", value, "dataType", dataType)))));

         ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());

         gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", orgUser);
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

   // -------------------------------------------------------------------------
   // Bug #82274: folder of a new task, update of an existing task
   // -------------------------------------------------------------------------

   @Test
   void resolveCreateFolder_rootMeansNoFolderEntry() throws Exception {
      assertNull(gateway.resolveCreateFolder(null, user));
      assertNull(gateway.resolveCreateFolder("/", user));
      assertNull(gateway.resolveCreateFolder("  ", user));
   }

   @Test
   void resolveCreateFolder_refusesWithoutWriteOnTheFolder() throws Exception {
      when(securityEngine.checkPermission(user, ResourceType.SCHEDULE_TASK_FOLDER, "Finance",
                                          ResourceAction.WRITE)).thenReturn(false);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> gateway.resolveCreateFolder("/Finance", user));
      assertTrue(ex.getMessage().startsWith("path:"));
      assertTrue(ex.getMessage().contains("WRITE"));
   }

   @Test
   void resolveCreateFolder_returnsTheRootNormalizedFolderEntry() throws Exception {
      when(securityEngine.checkPermission(user, ResourceType.SCHEDULE_TASK_FOLDER,
                                          "Finance/Reports", ResourceAction.WRITE)).thenReturn(true);

      inetsoft.uql.asset.AssetEntry entry = gateway.resolveCreateFolder("/Finance/Reports", user);

      assertEquals(inetsoft.uql.asset.AssetEntry.Type.SCHEDULE_TASK_FOLDER, entry.getType());
      assertEquals("Finance/Reports", entry.getPath());
   }

   @Test
   void addScheduleTask_withAFolderSavesInThatFolder() throws Exception {
      ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", callerOwner.convertToKey());
      inetsoft.uql.asset.AssetEntry folder = new inetsoft.uql.asset.AssetEntry(
         inetsoft.uql.asset.AssetRepository.GLOBAL_SCOPE,
         inetsoft.uql.asset.AssetEntry.Type.SCHEDULE_TASK_FOLDER, "Finance", null);

      gateway.addScheduleTask(
         taskMetaData, true, false, -1, -1, null, null, null,
         Collections.emptyList(), Collections.emptyList(), null, "", folder, user);

      verify(scheduleManager).setScheduleTask(anyString(), any(), eq(folder), eq(user));
   }

   private inetsoft.sree.schedule.ScheduleTask storedTask() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = new inetsoft.sree.schedule.ScheduleTask();
      stored.setName("nightly");
      stored.setOwner(callerOwner);
      stored.setEnabled(true);
      stored.setDescription("original");
      inetsoft.sree.schedule.TimeCondition time = new inetsoft.sree.schedule.TimeCondition();
      time.setType(inetsoft.sree.schedule.TimeCondition.EVERY_DAY);
      time.setInterval(1);
      time.setHour(4);
      stored.addCondition(time);
      when(repository.getScheduleTask(stored.getTaskId())).thenReturn(stored);
      when(securityEngine.checkPermission(user, ResourceType.SCHEDULE_TASK, stored.getTaskId(),
                                          ResourceAction.WRITE)).thenReturn(true);
      return stored;
   }

   private MockedStatic<OrganizationManager> siteAdminCaller() {
      OrganizationManager manager = mock(OrganizationManager.class, withSettings().lenient());
      when(manager.isSiteAdmin(user)).thenReturn(true);
      MockedStatic<OrganizationManager> statics =
         mockStatic(OrganizationManager.class, withSettings().lenient());
      statics.when(OrganizationManager::getInstance).thenReturn(manager);
      return statics;
   }

   @Test
   void buildUpdatedTask_changesOnlyThePatchedFieldsAndNeverTheStoredTask() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = storedTask();
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);

      try(MockedStatic<OrganizationManager> ignored = siteAdminCaller()) {
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user);

         assertFalse(candidate.isEnabled());
         assertEquals("original", candidate.getDescription());
         assertEquals(1, candidate.getConditionCount());
         assertEquals(4, ((inetsoft.sree.schedule.TimeCondition) candidate.getCondition(0)).getHour());
         assertNotSame(stored, candidate);
         assertTrue(stored.isEnabled());
      }
   }

   @Test
   void buildUpdatedTask_replacesTheConditionListWithACompletionCondition() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = storedTask();
      inetsoft.web.api.schedule.CompletionCondition completion =
         new inetsoft.web.api.schedule.CompletionCondition();
      completion.setOwner("caller");
      completion.setTaskName("first");
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setConditions(List.of(completion));

      try(MockedStatic<OrganizationManager> ignored = siteAdminCaller()) {
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user);

         assertEquals(1, candidate.getConditionCount());
         assertTrue(candidate.getCondition(0) instanceof inetsoft.sree.schedule.CompletionCondition);
         assertEquals(1, stored.getConditionCount());
         assertTrue(stored.getCondition(0) instanceof inetsoft.sree.schedule.TimeCondition);
         // restricted time fields are restored against the stored task, not an empty one
         verify(scheduleTaskService).sanitizeConditions(candidate, stored, user);
      }
   }

   // Bug #82274: a task is written to XML and parsed again when it is stored, and a viewsheet action
   // does not write the same XML before and after that trip. The plan must project the candidate the
   // way it is read back, and an action the patch does not replace must stay the stored action.

   /** A task built through the gateway the way the plugin creates it, as it is read back. */
   private inetsoft.sree.schedule.ScheduleTask readBackTaskWith(List<ScheduleAction> actions)
      throws Exception
   {
      ScheduleTaskMetaData meta = new ScheduleTaskMetaData("nightly", callerOwner.convertToKey());
      TimeCondition time = new TimeCondition();
      time.setType(TimeCondition.Type.EVERY_DAY);
      time.setHour(4);
      time.setMinute(0);
      time.setSecond(0);
      time.setInterval(1);

      gateway.addScheduleTask(meta, true, false, -1, -1, "original", null, null, List.of(time),
                              actions, null, "http://host:8080/sree/", user);

      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(meta.getTaskId()), captor.capture(), eq(user));
      inetsoft.sree.schedule.ScheduleTask stored =
         ScheduleChangesetApplyService.deepCopy(captor.getValue());
      when(repository.getScheduleTask(meta.getTaskId())).thenReturn(stored);
      when(securityEngine.checkPermission(user, ResourceType.SCHEDULE_TASK, meta.getTaskId(),
                                          ResourceAction.WRITE)).thenReturn(true);
      return stored;
   }

   /** Password encryption needs a running server; an identity stub is enough for the XML round trip. */
   private static MockedStatic<inetsoft.util.Tool> toolStub() {
      MockedStatic<inetsoft.util.Tool> statics = mockStatic(inetsoft.util.Tool.class,
         withSettings().lenient().defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
      statics.when(() -> inetsoft.util.Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      statics.when(() -> inetsoft.util.Tool.decryptPassword(anyString())).thenAnswer(inv -> {
         String value = inv.getArgument(0);
         return value.startsWith("TKN:") ? value.substring(4) : value;
      });
      return statics;
   }

   private static ViewsheetAction sheetAction(String... emails) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet("1^128^__NULL__^Examples/Census^host-org");
      action.setEmails(List.of(emails));

      if(emails.length > 0) {
         action.setFormat(ViewsheetAction.Format.PDF);
      }

      return action;
   }

   @Test
   void buildUpdatedTask_aDescriptionOnlyPatchLeavesAViewsheetActionAsStoredAndProjectsOnlyTheDescription()
      throws Exception
   {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setDescription("changed");

      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackTaskWith(List.of(sheetAction("a@x.com")));
         String storedProjection = ScheduleXmlProjection.projectAsStored(stored);
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user);

         assertSame(stored.getAction(0), candidate.getAction(0));
         String planned = ScheduleXmlProjection.projectAsStored(candidate);
         assertEquals(storedProjection.replace("description=\"original\"", "description=\"changed\""),
                      planned);
         // what the store reads back after saving the candidate is the planned task
         assertEquals(planned, ScheduleXmlProjection.project(
            ScheduleChangesetApplyService.deepCopy(candidate)));
      }
   }

   @Test
   void buildUpdatedTask_aPatchThatChangesNothingProjectsLikeTheStoredTask() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(true);

      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackTaskWith(List.of(sheetAction("a@x.com")));
         assertEquals(ScheduleXmlProjection.projectAsStored(stored),
                      ScheduleXmlProjection.projectAsStored(
                         gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user)));
      }
   }

   /** Stored task whose first action zips its email attachment under a password. */
   private inetsoft.sree.schedule.ScheduleTask readBackZippedTask(boolean useCredential)
      throws Exception
   {
      ViewsheetAction zipped = sheetAction("a@x.com");
      zipped.setEmailZip(true);
      inetsoft.sree.schedule.ScheduleTask stored =
         readBackTaskWith(List.of(zipped, sheetAction("d@x.com")));
      inetsoft.sree.schedule.ViewsheetAction first =
         (inetsoft.sree.schedule.ViewsheetAction) stored.getAction(0);
      first.setPassword("zip-secret");
      first.setUseCredential(useCredential);
      first.setSecretId(useCredential ? "secret-1" : null);
      return stored;
   }

   private inetsoft.sree.schedule.ViewsheetAction replaceActions(
      inetsoft.sree.schedule.ScheduleTask stored, ViewsheetAction... actions) throws Exception
   {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(actions));
      return (inetsoft.sree.schedule.ViewsheetAction)
         gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user).getAction(0);
   }

   @Test
   void buildUpdatedTask_replacingActionsKeepsTheStoredEmailZipPassword() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);
         ViewsheetAction replacement = sheetAction("b@x.com");
         replacement.setEmailZip(true);
         inetsoft.sree.schedule.ViewsheetAction rebuilt = replaceActions(stored, replacement);

         assertTrue(rebuilt.isCompressFile());
         assertEquals("zip-secret", rebuilt.getPassword());
         assertFalse(rebuilt.isUseCredential());
      }
   }

   @Test
   void buildUpdatedTask_replacingActionsKeepsTheStoredEmailZipCredential() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(true);
         ViewsheetAction replacement = sheetAction("b@x.com");
         replacement.setEmailZip(true);
         inetsoft.sree.schedule.ViewsheetAction rebuilt = replaceActions(stored, replacement);

         assertTrue(rebuilt.isUseCredential());
         assertEquals("secret-1", rebuilt.getSecretId());
      }
   }

   @Test
   void buildUpdatedTask_theEmailZipPasswordFollowsTheActionNotTheDashboard() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);
         ViewsheetAction replacement = sheetAction("b@x.com");
         replacement.setViewsheet("1^128^__NULL__^Examples/Other^host-org");
         replacement.setEmailZip(true);

         assertEquals("zip-secret", replaceActions(stored, replacement).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_dropsTheEmailZipPasswordWhenTheZipIsTurnedOffOrOmitted() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);
         ViewsheetAction off = sheetAction("b@x.com");
         off.setEmailZip(false);
         assertFalse(replaceActions(stored, off).isCompressFile());
         assertNull(replaceActions(stored, off).getPassword());
         assertNull(replaceActions(stored, sheetAction("b@x.com")).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_theEmailZipPasswordIsMatchedByActionPosition() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);
         ViewsheetAction first = sheetAction("b@x.com");
         ViewsheetAction second = sheetAction("c@x.com");
         first.setEmailZip(true);
         second.setEmailZip(true);
         ScheduleTaskPatch patch = new ScheduleTaskPatch();
         patch.setActions(List.of(first, second));
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user);

         assertEquals("zip-secret", ((inetsoft.sree.schedule.ViewsheetAction) candidate.getAction(0))
            .getPassword());
         assertNull(((inetsoft.sree.schedule.ViewsheetAction) candidate.getAction(1)).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_anActionOfAnotherClassAtTheSamePositionInheritsNothing() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);
         ScheduleTaskPatch patch = new ScheduleTaskPatch();
         patch.setActions(List.of(new IndividualAssetBackupAction()));
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user);

         assertFalse(candidate.getAction(0) instanceof inetsoft.sree.schedule.ViewsheetAction);
      }
   }

   @Test
   void buildUpdatedTask_replacedViewsheetActionsProjectAsTheyAreReadBack() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(sheetAction("b@x.com", "c@x.com")));

      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackTaskWith(List.of(sheetAction("a@x.com")));
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, "http://host:8080/sree/", user);

         String readBack = ScheduleXmlProjection.project(
            ScheduleChangesetApplyService.deepCopy(candidate));
         // the candidate as it is in memory is not what is read back, which is why the plan
         // projects the copy
         assertNotEquals(ScheduleXmlProjection.project(candidate), readBack);
         assertEquals(readBack, ScheduleXmlProjection.projectAsStored(candidate));
         assertNotEquals(ScheduleXmlProjection.projectAsStored(stored), readBack);
      }
   }

   @Test
   void buildUpdatedTask_aViewsheetActionAddedToATaskWithoutActionsProjectsAsItIsReadBack()
      throws Exception
   {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(List.of(sheetAction("a@x.com")));

      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackTaskWith(List.of());
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, "http://host:8080/sree/", user);

         assertEquals(1, candidate.getActionCount());
         assertEquals(ScheduleXmlProjection.project(ScheduleChangesetApplyService.deepCopy(candidate)),
                      ScheduleXmlProjection.projectAsStored(candidate));
      }
   }

   @Test
   void buildUpdatedTask_aTaskWithViewsheetActionsUpdatesThroughPlanAndApply() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);

      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackTaskWith(List.of(sheetAction("a@x.com")));
         // the plan (no request link) and the apply (the request's link) build the same task
         String planned = ScheduleXmlProjection.projectAsStored(
            gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user));
         inetsoft.sree.schedule.ScheduleTask applied =
            gateway.buildUpdatedTask(stored.getTaskId(), patch, "http://host:8080/sree/", user);
         String afterStore = ScheduleXmlProjection.project(ScheduleChangesetApplyService.deepCopy(applied));

         assertEquals(ScheduleXmlProjection.withoutLinkUris(planned),
                      ScheduleXmlProjection.withoutLinkUris(afterStore));
      }
   }

   @Test
   void buildUpdatedTask_refusesACallerWhoCannotEditTheTask() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = storedTask();
      when(securityEngine.checkPermission(user, ResourceType.SCHEDULE_TASK, stored.getTaskId(),
                                          ResourceAction.WRITE)).thenReturn(false);
      when(scheduleTaskService.canDeleteTask(stored, user)).thenReturn(false);
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);

      try(MockedStatic<OrganizationManager> ignored = siteAdminCaller()) {
         assertThrows(UnauthorizedAccessException.class,
            () -> gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user));
      }
   }

   @Test
   void buildUpdatedTask_refusesAnUnknownTask() throws Exception {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);

      assertThrows(MissingResourceException.class,
         () -> gateway.buildUpdatedTask("caller~;~host-org:none", patch, null, user));
   }

   @Test
   void buildUpdatedTask_refusesATaskOfAnotherOrganizationForANonSiteAdmin() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = new inetsoft.sree.schedule.ScheduleTask();
      stored.setName("theirs");
      stored.setOwner(new IdentityID("someone", "other-org"));
      when(repository.getScheduleTask(stored.getTaskId())).thenReturn(stored);
      OrganizationManager manager = mock(OrganizationManager.class, withSettings().lenient());
      when(manager.isSiteAdmin(user)).thenReturn(false);
      when(manager.getCurrentOrgID(user)).thenReturn("host-org");
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setEnabled(false);

      try(MockedStatic<OrganizationManager> statics =
             mockStatic(OrganizationManager.class, withSettings().lenient()))
      {
         statics.when(OrganizationManager::getInstance).thenReturn(manager);
         assertThrows(UnauthorizedAccessException.class,
            () -> gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user));
      }
   }

   @Test
   void storeUpdatedTask_savesInTheFolderTheTaskIsIn() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = storedTask();
      stored.setPath("Finance/Reports");
      inetsoft.sree.schedule.ScheduleTask candidate = new inetsoft.sree.schedule.ScheduleTask();

      gateway.storeUpdatedTask(stored.getTaskId(), candidate, user);

      org.mockito.ArgumentCaptor<inetsoft.uql.asset.AssetEntry> folder =
         org.mockito.ArgumentCaptor.forClass(inetsoft.uql.asset.AssetEntry.class);
      verify(scheduleManager).setScheduleTask(eq(stored.getTaskId()), eq(candidate), folder.capture(),
                                              eq(user));
      assertEquals("Finance/Reports", folder.getValue().getPath());
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

   // Bug #82341: a site admin creates a task of another organization's owner with a backup of a
   // sheet of the default organization: refused in multi-tenant mode, as the viewsheet action is
   @Test
   void addScheduleTask_rejectsBackupOfSheetInAnotherOrganizationThanTheOwner() throws Exception {
      IdentityID owner = new IdentityID("caller", "self-org");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         stubOwnerPrincipal(sutil);

         UnauthorizedAccessException ex = assertThrows(
            UnauthorizedAccessException.class, () -> addBackupTaskAsSiteAdmin(owner, "host-org"));

         assertTrue(ex.getMessage().contains(owner.convertToKey()), ex.getMessage());
         assertTrue(ex.getMessage().contains("host-org"), ex.getMessage());
         verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
      }
   }

   @Test
   void addScheduleTask_allowsBackupOfSheetInTheOwnersOrganization() throws Exception {
      IdentityID owner = new IdentityID("caller", "self-org");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         stubOwnerPrincipal(sutil);
         addBackupTaskAsSiteAdmin(owner, "self-org");
      }

      verify(scheduleManager).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void addScheduleTask_singleTenantDoesNotCompareTheBackupSheetOrganization() throws Exception {
      IdentityID owner = new IdentityID("caller", "self-org");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS)) {
         sutil.when(SUtil::isMultiTenant).thenReturn(false);
         stubOwnerPrincipal(sutil);
         addBackupTaskAsSiteAdmin(owner, "host-org");
      }

      verify(scheduleManager).setScheduleTask(anyString(), any(), any());
   }
   // the owner principal needs a security provider, the default organization isn't shared
   private void stubOwnerPrincipal(MockedStatic<SUtil> sutil) throws Exception {
      SRPrincipal ownerPrincipal = mock(SRPrincipal.class);
      sutil.when(() -> SUtil.getScheduleTaskOwnerPrincipal(any(), any(), anyBoolean()))
         .thenReturn(ownerPrincipal);
      sutil.when(() -> SUtil.isDefaultVSGloballyVisible(any())).thenReturn(false);
   }


   private void addBackupTaskAsSiteAdmin(IdentityID owner, String sheetOrg) throws Exception {
      try(MockedStatic<XSessionService> sessionStatic = mockStatic(XSessionService.class);
          MockedStatic<OrganizationManager> orgManagerStatic = mockStatic(OrganizationManager.class);
          MockedStatic<inetsoft.web.admin.deploy.XAssetExportPermission> exportStatic =
             mockStatic(inetsoft.web.admin.deploy.XAssetExportPermission.class))
      {
         exportStatic.when(() -> inetsoft.web.admin.deploy.XAssetExportPermission.isPermitted(
            any(), any(), anyBoolean(), any())).thenReturn(true);
         mockSession(sessionStatic);
         OrganizationManager orgManager = mock(OrganizationManager.class);
         when(orgManager.isSiteAdmin((Principal) any())).thenReturn(true);
         when(orgManager.getCurrentOrgID()).thenReturn("self-org");
         orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
         SRPrincipal admin = createPrincipal("caller", "self-org");

         IndividualAssetBackupAction action = new IndividualAssetBackupAction();
         action.setAssetIdentifiers(List.of(
            "inetsoft.util.dep.ViewsheetAsset^1^128^__NULL__^Examples/Census^" + sheetOrg));
         ScheduleTaskMetaData taskMetaData = new ScheduleTaskMetaData("task1", owner.convertToKey());

         gateway.addScheduleTask(
            taskMetaData, true, false, -1, -1, null, null, null,
            Collections.emptyList(), List.of(action), null, "", admin);
      }
   }

   // Bug #77937: the API returns a stored password as a placeholder, which an added task has no
   // stored password for, so it must not be stored as the password itself
   @Test
   void addScheduleTask_doesNotStorePlaceholderPassword() throws Exception {
      // an ftp path is looked up in the server locations, none is configured here
      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class, withSettings().lenient()
             .defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS)))
      {
         sutil.when(SUtil::getServerLocationsWithPasswords).thenReturn(List.of());
         assertPlaceholderPasswordIsNotStored();
      }
   }

   private void assertPlaceholderPasswordIsNotStored() throws Exception {
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

   // -------------------------------------------------------------------------
   // requireOwnerResolves -- Bug #79828, owner kind x existence x organization
   // -------------------------------------------------------------------------

   @Test
   void requireOwnerResolves_acceptsExistingUser() {
      SecurityProvider provider = ownerProvider("host-org", "admin");

      assertDoesNotThrow(() -> gateway.requireOwnerResolves(otherOwner, user));
      verify(provider).getUser(otherOwner);
   }

   @Test
   void requireOwnerResolves_acceptsExistingUserOfAnotherOrganization() {
      IdentityID owner = new IdentityID("bob", "org2");
      SecurityProvider provider = ownerProvider("org2");
      when(provider.getUser(owner)).thenReturn(new User(owner));

      assertDoesNotThrow(() -> gateway.requireOwnerResolves(owner, user));
   }

   @Test
   void requireOwnerResolves_acceptsCallerEvenWhenProviderDoesNotKnowIt() {
      ownerProvider("host-org");

      assertDoesNotThrow(() -> gateway.requireOwnerResolves(callerOwner, user));
   }

   @Test
   void requireOwnerResolves_acceptsTheOwnerTheTaskEditorDerivesForAnAdminlessOrganization() {
      ownerProvider("org2");
      IdentityID fallback = new IdentityID("caller", "org2");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         sutil.when(() -> SUtil.getOwnerForNewTask(callerOwner, "org2")).thenReturn(fallback);
         assertDoesNotThrow(() -> gateway.requireOwnerResolves(fallback, user));
      }
   }

   @Test
   void requireOwnerResolves_refusesUnknownUserNamingTheField() {
      ownerProvider("host-org", "admin");
      IdentityID owner = new IdentityID("no-such-user", "host-org");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         MessageException ex = assertThrows(
            MessageException.class, () -> gateway.requireOwnerResolves(owner, user));
         assertTrue(ex.getMessage().contains("no-such-user"));
         assertTrue(ex.getMessage().contains("does not exist"));
         assertTrue(ex.getMessage().contains("host-org"));
      }
   }

   @Test
   void requireOwnerResolves_refusesGroupOrRoleName() {
      // a group or role name isn't a user, so the provider's user lookup returns null for it
      SecurityProvider provider = ownerProvider("host-org");
      IdentityID owner = new IdentityID("Sales", "host-org");
      when(scheduleService.isSecurityEnabled()).thenReturn(true);
      when(provider.getGroup(owner)).thenReturn(new Group(owner));
      when(provider.getRole(owner)).thenReturn(new Role(owner));

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         MessageException ex = assertThrows(
            MessageException.class, () -> gateway.requireOwnerResolves(owner, user));
         assertTrue(ex.getMessage().contains("a group or role cannot own a task"));
      }
   }

   @Test
   void requireOwnerResolves_refusesUnknownOrganization() {
      ownerProvider("host-org", "admin");

      MessageException ex = assertThrows(MessageException.class,
         () -> gateway.requireOwnerResolves(new IdentityID("admin", "no-such-org"), user));
      assertTrue(ex.getMessage().contains("organization \"no-such-org\" does not exist"));
   }

   // requireOwnerOrganizationExists -- Bug #80027, Gap 2: a schedule-task-import owner kept
   // verbatim from the uploaded xml must not resolve to an organization that doesn't exist.
   // Deliberately narrower than requireOwnerResolves -- a built-in system owner (an internal
   // task's own owner, same as EM's own import sets) must still pass.

   @Test
   void requireOwnerOrganizationExists_refusesUnknownOrganization() {
      ownerProvider("host-org", "admin");

      MessageException ex = assertThrows(MessageException.class,
         () -> gateway.requireOwnerOrganizationExists(
            new IdentityID("admin", "qa-no-such-org-xyz")));
      assertTrue(ex.getMessage().contains("admin"), ex.getMessage());
      assertTrue(ex.getMessage().contains("qa-no-such-org-xyz"), ex.getMessage());
   }

   @Test
   void requireOwnerOrganizationExists_acceptsAKnownOrganization() {
      ownerProvider("host-org", "admin");

      assertDoesNotThrow(() -> gateway.requireOwnerOrganizationExists(
         new IdentityID("admin", "host-org")));
   }

   @Test
   void requireOwnerOrganizationExists_acceptsABuiltInSystemOwner() {
      ownerProvider("host-org", "admin");

      assertDoesNotThrow(() -> gateway.requireOwnerOrganizationExists(
         new IdentityID(XPrincipal.SYSTEM, "host-org")));
   }

   @Test
   void requireOwnerOrganizationExists_toleratesANullOrBlankOwner() {
      assertDoesNotThrow(() -> gateway.requireOwnerOrganizationExists(null));
      assertDoesNotThrow(() -> gateway.requireOwnerOrganizationExists(
         new IdentityID("admin", null)));
      assertDoesNotThrow(() -> gateway.requireOwnerOrganizationExists(
         new IdentityID("admin", "  ")));
   }

   @Test
   void requireOwnerResolves_refusesBlankOrMissingOrganizationId() {
      ownerProvider("host-org", "admin");

      for(String orgID : new String[] { null, "", "  " }) {
         MessageException ex = assertThrows(MessageException.class,
            () -> gateway.requireOwnerResolves(new IdentityID("admin", orgID), user));
         assertTrue(ex.getMessage().contains("orgID"));
      }
   }

   @Test
   void requireOwnerResolves_refusesBlankName() {
      ownerProvider("host-org", "admin");

      MessageException ex = assertThrows(MessageException.class,
         () -> gateway.requireOwnerResolves(new IdentityID(" ", "host-org"), user));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test
   void requireOwnerResolves_refusesSystemAndAnonymousEvenWhenTheProviderReportsThem() {
      // the virtual provider (security disabled) reports both as existing users
      SecurityProvider provider = ownerProvider("host-org");
      IdentityID system = new IdentityID(XPrincipal.SYSTEM, "host-org");
      IdentityID anonymous = new IdentityID(XPrincipal.ANONYMOUS, "host-org");
      when(provider.getUser(system)).thenReturn(new User(system));
      when(provider.getUser(anonymous)).thenReturn(new User(anonymous));

      for(IdentityID owner : new IdentityID[] { system, anonymous }) {
         MessageException ex = assertThrows(
            MessageException.class, () -> gateway.requireOwnerResolves(owner, user));
         assertTrue(ex.getMessage().contains("built-in user"));
      }
   }

   @Test
   void requireOwnerResolves_refusesUnknownUserWithSecurityDisabled() {
      ownerProvider("host-org", "admin");
      IdentityID owner = new IdentityID("no-such-user", "host-org");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         MessageException ex = assertThrows(
            MessageException.class, () -> gateway.requireOwnerResolves(owner, user));
         assertTrue(ex.getMessage().contains("security is disabled"));
      }
   }

   // Bug #79828, apply re-checks the owner, for a user removed between preview and apply
   @Test
   void checkAddScheduleTaskAllowed_refusesOwnerThatNoLongerExistsBeforeAnyOtherCheck()
      throws Exception
   {
      ownerProvider("host-org", "admin");
      IdentityID owner = new IdentityID("no-such-user", "host-org");

      try(MockedStatic<SUtil> sutil = mockStatic(SUtil.class)) {
         assertThrows(MessageException.class,
            () -> gateway.checkAddScheduleTaskAllowed(owner, null, user));
      }

      verify(repository, never())
         .checkPermission(any(Principal.class), eq(ResourceType.SECURITY_USER), any(IdentityID.class),
                          any(ResourceAction.class));
   }

   /**
    * Makes the security engine report a provider with the given organizations and, in the first
    * organization, the given user names.
    */
   private SecurityProvider ownerProvider(String firstOrgID, String... userNames) {
      SecurityProvider provider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      when(provider.getOrganization(anyString())).thenReturn(null);
      when(provider.getOrganization(firstOrgID)).thenReturn(mock(Organization.class));

      for(String name : userNames) {
         IdentityID id = new IdentityID(name, firstOrgID);
         when(provider.getUser(id)).thenReturn(new User(id));
      }

      return provider;
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

   // -------------------------------------------------------------------------
   // task-level time zone (the portal Options tab's Time Zone)
   // -------------------------------------------------------------------------

   private inetsoft.sree.schedule.ScheduleTask addTaskWithZone(String zone) throws Exception {
      ScheduleTaskMetaData meta = new ScheduleTaskMetaData("zoned", callerOwner.convertToKey());
      gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
                              Collections.emptyList(), Collections.emptyList(), null, "", null,
                              zone, user);
      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(meta.getTaskId()), captor.capture(), eq(user));
      return captor.getValue();
   }

   @Test
   void addScheduleTask_storesTheTaskTimeZoneAndItSurvivesTheXmlRoundTrip() throws Exception {
      inetsoft.sree.schedule.ScheduleTask saved = addTaskWithZone("  Asia/Tokyo ");

      assertEquals("Asia/Tokyo", saved.getTimeZone());
      assertEquals("Asia/Tokyo", ScheduleChangesetApplyService.deepCopy(saved).getTimeZone());
      assertTrue(ScheduleXmlProjection.project(saved).contains("timeZone=\"Asia/Tokyo\""));
   }

   // C2: a create that names no zone stores none, as before
   @Test
   void addScheduleTask_withoutATimeZoneStoresNone() throws Exception {
      assertNull(addTaskWithZone(null).getTimeZone());
   }

   @Test
   void addScheduleTask_aBlankTimeZoneStoresNone() throws Exception {
      assertNull(addTaskWithZone("   ").getTimeZone());
   }

   @Test
   void buildUpdatedTask_setsAndClearsTheTaskTimeZone() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = storedTask();
      ScheduleTaskPatch set = new ScheduleTaskPatch();
      set.setTimeZone("Europe/Paris");

      try(MockedStatic<OrganizationManager> ignored = siteAdminCaller()) {
         inetsoft.sree.schedule.ScheduleTask candidate =
            gateway.buildUpdatedTask(stored.getTaskId(), set, null, user);

         assertEquals("Europe/Paris", candidate.getTimeZone());
         assertNull(stored.getTimeZone());

         stored.setTimeZone("Europe/Paris");
         ScheduleTaskPatch clear = new ScheduleTaskPatch();
         clear.setTimeZone("");
         assertNull(gateway.buildUpdatedTask(stored.getTaskId(), clear, null, user).getTimeZone());
         assertEquals("Europe/Paris", stored.getTimeZone());
      }
   }

   // C1: a patch that does not mention the zone keeps it, also when it replaces conditions
   @Test
   void buildUpdatedTask_aPatchWithoutATimeZoneKeepsTheStoredOne() throws Exception {
      inetsoft.sree.schedule.ScheduleTask stored = storedTask();
      stored.setTimeZone("Europe/Paris");
      ScheduleTaskPatch disable = new ScheduleTaskPatch();
      disable.setEnabled(false);
      inetsoft.web.api.schedule.CompletionCondition completion =
         new inetsoft.web.api.schedule.CompletionCondition();
      completion.setOwner("caller");
      completion.setTaskName("first");
      ScheduleTaskPatch replaceConditions = new ScheduleTaskPatch();
      replaceConditions.setConditions(List.of(completion));

      try(MockedStatic<OrganizationManager> ignored = siteAdminCaller()) {
         assertEquals("Europe/Paris",
            gateway.buildUpdatedTask(stored.getTaskId(), disable, null, user).getTimeZone());
         assertEquals("Europe/Paris",
            gateway.buildUpdatedTask(stored.getTaskId(), replaceConditions, null, user)
               .getTimeZone());
      }
   }

   @Test
   void getScheduleTasks_includesTheTaskTimeZoneAndNullWhenNone() throws Exception {
      inetsoft.sree.schedule.ScheduleTask zoned = taskAt("zoned", "/");
      zoned.setTimeZone("Asia/Tokyo");
      inetsoft.sree.schedule.ScheduleTask plain = taskAt("plain", "/");
      when(repository.getScheduleTasks(user))
         .thenReturn(new String[]{zoned.getTaskId(), plain.getTaskId()});
      when(scheduleManager.getScheduleTask(zoned.getTaskId())).thenReturn(zoned);
      when(scheduleManager.getScheduleTask(plain.getTaskId())).thenReturn(plain);

      AdminScheduleTaskList list = gateway.getScheduleTasks(null, user);

      assertEquals(Arrays.asList("Asia/Tokyo", null),
                   list.getTasks().stream().map(ScheduleTaskEntry::getTimeZone).toList());
   }

   @Test
   void getScheduleTaskTimeZone_readsTheTaskZoneAndRequiresReadPermission() throws Exception {
      inetsoft.sree.schedule.ScheduleTask task = taskAt("t", "A/B");
      task.setTimeZone("Asia/Tokyo");
      when(repository.getScheduleTask(task.getTaskId())).thenReturn(task);

      assertThrows(UnauthorizedAccessException.class,
                   () -> gateway.getScheduleTaskTimeZone(task.getTaskId(), user));

      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, task.getTaskId(),
                                      ResourceAction.READ)).thenReturn(true);
      assertEquals("Asia/Tokyo", gateway.getScheduleTaskTimeZone(task.getTaskId(), user));

      task.setTimeZone(null);
      assertNull(gateway.getScheduleTaskTimeZone(task.getTaskId(), user));
   }

   // -------------------------------------------------------------------------
   // email zip password (Bug #82325, G1)
   // -------------------------------------------------------------------------

   private static final String ZIP_PW = "PW-PLACEHOLDER-ZIP-1";
   private static final String ZIP_PW_2 = "PW-PLACEHOLDER-ZIP-2";

   private static EmailZipPasswordSpec zipSpec(int index, String password, Boolean useCredential,
                                               String secretId)
   {
      EmailZipPasswordSpec spec = new EmailZipPasswordSpec();
      spec.setActionIndex(index);
      spec.setPassword(password);
      spec.setUseCredential(useCredential);
      spec.setSecretId(secretId);
      return spec;
   }

   /** The XML trip asks the password encryption whether secrets are kept local. */
   private static MockedStatic<inetsoft.util.PasswordEncryption> localEncryption() {
      MockedStatic<inetsoft.util.PasswordEncryption> statics =
         mockStatic(inetsoft.util.PasswordEncryption.class, withSettings().lenient());
      statics.when(inetsoft.util.PasswordEncryption::isEncryptForceLocal).thenReturn(false);
      statics.when(inetsoft.util.PasswordEncryption::isFipsCompliant).thenReturn(false);
      return statics;
   }

   private static ViewsheetAction zipped(String... emails) {
      ViewsheetAction action = sheetAction(emails);
      action.setEmailZip(true);
      return action;
   }

   /** Creates a task through the gateway with the zip entries and returns what it saved. */
   private inetsoft.sree.schedule.ScheduleTask createWithZip(List<ScheduleAction> actions,
                                                             List<EmailZipPasswordSpec> specs)
      throws Exception
   {
      ScheduleTaskMetaData meta = new ScheduleTaskMetaData("zipped", callerOwner.convertToKey());
      // a task without a condition is ignored when it is read back from XML
      TimeCondition time = new TimeCondition();
      time.setType(TimeCondition.Type.EVERY_DAY);
      time.setHour(4);
      time.setMinute(0);
      time.setSecond(0);
      time.setInterval(1);
      gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
                              List.of(time), actions, null, "http://host:8080/sree/",
                              null, null, specs, user);
      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
      verify(scheduleManager).setScheduleTask(eq(meta.getTaskId()), captor.capture(), eq(user));
      return captor.getValue();
   }

   private static inetsoft.sree.schedule.ViewsheetAction vsOf(
      inetsoft.sree.schedule.ScheduleTask task, int index)
   {
      return (inetsoft.sree.schedule.ViewsheetAction) task.getAction(index);
   }

   private inetsoft.sree.schedule.ScheduleTask updateWithZip(
      inetsoft.sree.schedule.ScheduleTask stored, List<ScheduleAction> actions,
      EmailZipPasswordSpec... specs)
      throws Exception
   {
      ScheduleTaskPatch patch = new ScheduleTaskPatch();
      patch.setActions(actions);
      patch.setEmailZipPasswords(specs.length == 0 ? null : List.of(specs));
      return gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user);
   }

   @Test
   void addScheduleTask_storesTheEmailZipPasswordAndItSurvivesTheXmlRoundTrip() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<inetsoft.util.PasswordEncryption> encryption = localEncryption())
      {
         inetsoft.sree.schedule.ScheduleTask saved = createWithZip(
            List.of(zipped("a@x.com")), List.of(zipSpec(0, ZIP_PW, null, null)));

         assertTrue(vsOf(saved, 0).isCompressFile());
         assertEquals(ZIP_PW, vsOf(saved, 0).getPassword());
         assertFalse(vsOf(saved, 0).isUseCredential());
         inetsoft.sree.schedule.ViewsheetAction reread =
            vsOf(ScheduleChangesetApplyService.deepCopy(saved), 0);
         assertEquals(ZIP_PW, reread.getPassword());
         // the plan/preview projection only says a password is set
         String projection = ScheduleXmlProjection.project(saved);
         assertFalse(projection.contains(ZIP_PW), projection);
      }
   }

   @Test
   void addScheduleTask_storesTheEmailZipSecretIdInCredentialMode() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<inetsoft.util.PasswordEncryption> encryption = localEncryption())
      {
         inetsoft.sree.schedule.ScheduleTask saved = createWithZip(
            List.of(zipped("a@x.com")), List.of(zipSpec(0, null, true, "secret-1")));

         assertTrue(vsOf(saved, 0).isUseCredential());
         assertEquals("secret-1", vsOf(saved, 0).getSecretId());
         assertNull(vsOf(saved, 0).getPassword());
         inetsoft.sree.schedule.ViewsheetAction reread =
            vsOf(ScheduleChangesetApplyService.deepCopy(saved), 0);
         assertTrue(reread.isUseCredential());
         assertEquals("secret-1", reread.getSecretId());
      }
   }

   // C2: without entries a create stores no zip password, as before
   @Test
   void addScheduleTask_withoutZipEntriesStoresNoZipPassword() throws Exception {
      inetsoft.sree.schedule.ScheduleTask none = createWithZip(List.of(zipped("a@x.com")), null);

      assertTrue(vsOf(none, 0).isCompressFile());
      assertNull(vsOf(none, 0).getPassword());
      assertNull(vsOf(none, 0).getSecretId());
      assertFalse(vsOf(none, 0).isUseCredential());
   }

   @Test
   void addScheduleTask_aClearOrKeepEntryOnACreateStoresNoPassword() throws Exception {
      inetsoft.sree.schedule.ScheduleTask saved = createWithZip(
         List.of(zipped("a@x.com"), zipped("b@x.com")),
         List.of(zipSpec(0, "", null, null), zipSpec(1, Util.PLACEHOLDER_PASSWORD, null, null)));

      assertNull(vsOf(saved, 0).getPassword());
      assertNull(vsOf(saved, 1).getPassword());
      assertNull(vsOf(saved, 1).getSecretId());
   }

   @Test
   void addScheduleTask_appliesEachEntryToItsOwnActionIndex() throws Exception {
      inetsoft.sree.schedule.ScheduleTask saved = createWithZip(
         List.of(sheetAction("a@x.com"), zipped("b@x.com")),
         List.of(zipSpec(1, ZIP_PW, null, null)));

      assertNull(vsOf(saved, 0).getPassword());
      assertEquals(ZIP_PW, vsOf(saved, 1).getPassword());
   }

   @Test
   void addScheduleTask_refusesAnEntryThatNamesNoViewsheetActionAndNeverEchoesThePassword()
      throws Exception
   {
      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
         createWithZip(List.of(zipped("a@x.com")), List.of(zipSpec(4, ZIP_PW, null, null))));

      assertTrue(ex.getMessage().contains("actionIndex 4"), ex.getMessage());
      assertFalse(ex.getMessage().contains(ZIP_PW));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void checkAddScheduleTaskSavable_buildsTheCandidateWithTheZipPassword() throws Exception {
      ScheduleTaskMetaData meta = new ScheduleTaskMetaData("zipped", callerOwner.convertToKey());
      org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
         org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);

      gateway.checkAddScheduleTaskSavable(meta, true, false, -1, -1, null, null, null,
         Collections.emptyList(), List.of(zipped("a@x.com")), null,
         List.of(zipSpec(0, ZIP_PW, null, null)), user);

      verify(scheduleManager).checkScheduleTaskSave(anyString(), captor.capture(), eq(user));
      assertEquals(ZIP_PW, vsOf(captor.getValue(), 0).getPassword());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(), any());
   }

   @Test
   void buildUpdatedTask_setsANewEmailZipPasswordOverTheStoredOne() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(true);

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(zipped("b@x.com")), zipSpec(0, ZIP_PW_2, null, null));

         assertEquals(ZIP_PW_2, vsOf(candidate, 0).getPassword());
         assertFalse(vsOf(candidate, 0).isUseCredential());
         assertNull(vsOf(candidate, 0).getSecretId());
         // the stored task is never touched
         assertEquals("zip-secret", vsOf(stored, 0).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_switchesTheStoredPasswordToASecretId() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(zipped("b@x.com")), zipSpec(0, null, true, "secret-9"));

         assertTrue(vsOf(candidate, 0).isUseCredential());
         assertEquals("secret-9", vsOf(candidate, 0).getSecretId());
         assertNull(vsOf(candidate, 0).getPassword());
      }
   }

   private void assertABlankEntryClears(boolean storedAsCredential) throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(storedAsCredential);

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(zipped("b@x.com")), zipSpec(0, "", null, null));

         assertTrue(vsOf(candidate, 0).isCompressFile(), "the zip itself stays on");
         assertNull(vsOf(candidate, 0).getPassword());
         assertNull(vsOf(candidate, 0).getSecretId());
         assertFalse(vsOf(candidate, 0).isUseCredential());
      }
   }

   @Test
   void buildUpdatedTask_aBlankEntryClearsTheStoredPassword() throws Exception {
      assertABlankEntryClears(false);
   }

   @Test
   void buildUpdatedTask_aBlankEntryClearsTheStoredSecretId() throws Exception {
      assertABlankEntryClears(true);
   }

   @Test
   void buildUpdatedTask_theMaskedPlaceholderEntryKeepsTheStoredPasswordAndSecret()
      throws Exception
   {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(zipped("b@x.com")),
            zipSpec(0, Util.PLACEHOLDER_PASSWORD, null, null));

         assertEquals("zip-secret", vsOf(candidate, 0).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_aKeepEntryForAnActionWithNoStoredCounterpartStoresNoPassword()
      throws Exception
   {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(zipped("a@x.com"), zipped("c@x.com")),
            zipSpec(1, Util.PLACEHOLDER_PASSWORD, null, null));

         // the stored action at index 1 has no password, whatever empty value it reads back as
         assertTrue(inetsoft.util.Tool.isEmptyString(vsOf(candidate, 1).getPassword()));
         assertTrue(inetsoft.util.Tool.isEmptyString(vsOf(candidate, 1).getSecretId()));
      }
   }

   // A3 / C2: an update that does not mention the zip keeps the stored one, as #82340 did
   @Test
   void buildUpdatedTask_withoutZipEntriesStillKeepsTheStoredPassword() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);

         assertEquals("zip-secret",
            vsOf(updateWithZip(stored, List.of(zipped("b@x.com"))), 0).getPassword());
         // an entry for another action leaves this one on the stored value
         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(zipped("b@x.com"), zipped("c@x.com")),
            zipSpec(1, ZIP_PW_2, null, null));
         assertEquals("zip-secret", vsOf(candidate, 0).getPassword());
         assertEquals(ZIP_PW_2, vsOf(candidate, 1).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_refusesAnEntryThatNamesNoViewsheetAction() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller())
      {
         inetsoft.sree.schedule.ScheduleTask stored = readBackZippedTask(false);
         ScheduleTaskPatch patch = new ScheduleTaskPatch();
         patch.setActions(List.of(new IndividualAssetBackupAction()));
         patch.setEmailZipPasswords(List.of(zipSpec(0, ZIP_PW, null, null)));

         IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> gateway.buildUpdatedTask(stored.getTaskId(), patch, null, user));

         assertTrue(ex.getMessage().contains("actionIndex 0"), ex.getMessage());
         assertFalse(ex.getMessage().contains(ZIP_PW));
      }
   }

   @Test
   void getScheduleTaskEmailZipPasswords_reportsPresenceNeverTheValue() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub()) {
         ViewsheetAction plain = sheetAction("a@x.com");
         ViewsheetAction withPassword = zipped("b@x.com");
         ViewsheetAction withSecret = zipped("c@x.com");
         ViewsheetAction noPassword = zipped("d@x.com");
         inetsoft.sree.schedule.ScheduleTask task = createWithZip(
            List.of(plain, withPassword, withSecret, noPassword),
            List.of(zipSpec(1, ZIP_PW, null, null), zipSpec(2, null, true, "secret-1")));
         when(repository.getScheduleTask(task.getTaskId())).thenReturn(task);
         when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, task.getTaskId(),
                                         ResourceAction.READ)).thenReturn(true);

         List<EmailZipPasswordView> views =
            gateway.getScheduleTaskEmailZipPasswords(task.getTaskId(), user);

         assertEquals(List.of(new EmailZipPasswordView(1, true, false, null),
                              new EmailZipPasswordView(2, false, true, "secret-1")), views);
         assertFalse(views.toString().contains(ZIP_PW));
      }
   }

   @Test
   void getScheduleTaskEmailZipPasswords_requiresReadPermissionAndIsEmptyWithoutAny()
      throws Exception
   {
      inetsoft.sree.schedule.ScheduleTask task = taskAt("t", "A/B");
      when(repository.getScheduleTask(task.getTaskId())).thenReturn(task);

      assertThrows(UnauthorizedAccessException.class,
                   () -> gateway.getScheduleTaskEmailZipPasswords(task.getTaskId(), user));

      when(repository.checkPermission(user, ResourceType.SCHEDULE_TASK, task.getTaskId(),
                                      ResourceAction.READ)).thenReturn(true);
      assertTrue(gateway.getScheduleTaskEmailZipPasswords(task.getTaskId(), user).isEmpty());
   }

   // -------------------------------------------------------------------------
   // a save-to-disk placeholder password resolved against the server locations (Bug #82325, G2)
   // -------------------------------------------------------------------------

   private static final String LOC_PW = "PW-PLACEHOLDER-FTP-LOC";

   private static ViewsheetAction saveTo(String path, String username, String password) {
      ViewsheetAction.SaveToServerFilePath filePath =
         new ViewsheetAction.SaveToServerFilePath(ViewsheetAction.Format.PDF, path);
      filePath.setUsername(username);
      filePath.setPassword(password);
      ViewsheetAction action = sheetAction();
      action.setSaveToServerFilePaths(List.of(filePath));
      return action;
   }

   private static MockedStatic<SUtil> serverLocations(String locationPath, String username,
                                                       String password)
   {
      MockedStatic<SUtil> sutil = mockStatic(SUtil.class, withSettings().lenient()
         .defaultAnswer(org.mockito.Answers.CALLS_REAL_METHODS));
      sutil.when(SUtil::getServerLocationsWithPasswords).thenReturn(List.of(
         inetsoft.web.admin.schedule.model.ServerLocation.builder()
            .path(locationPath).label("Finance share")
            .pathInfoModel(inetsoft.web.admin.schedule.model.ServerPathInfoModel.builder()
               .path(locationPath).username(username).password(password).ftp(true).build())
            .build()));
      return sutil;
   }

   private inetsoft.sree.schedule.ServerPathInfo pdfPath(inetsoft.sree.schedule.ScheduleTask task) {
      return vsOf(task, 0).getFilePathInfo(ViewsheetAction.Format.PDF.getTypeValue());
   }

   @Test
   void addScheduleTask_aPlaceholderPasswordInsideAServerLocationTakesTheLocationPassword()
      throws Exception
   {
      try(MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW)) {
         ScheduleTaskMetaData meta = new ScheduleTaskMetaData("loc", callerOwner.convertToKey());
         gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
            Collections.emptyList(),
            List.of(saveTo("ftp://host/reports/daily", "ftpuser", Util.PLACEHOLDER_PASSWORD)),
            null, "", user);
         org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
            org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
         verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));

         assertEquals(LOC_PW, pdfPath(captor.getValue()).getPassword());
         assertEquals("ftpuser", pdfPath(captor.getValue()).getUsername());
      }
   }

   // C2: outside every location the placeholder is still dropped, never stored as a password
   @Test
   void addScheduleTask_aPlaceholderPasswordOutsideEveryServerLocationIsStillDropped()
      throws Exception
   {
      try(MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW)) {
         for(String path : new String[]{"ftp://host/other/daily", "ftp://elsewhere/reports/x"}) {
            reset(scheduleManager);
            ScheduleTaskMetaData meta = new ScheduleTaskMetaData("loc", callerOwner.convertToKey());
            gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
               Collections.emptyList(),
               List.of(saveTo(path, "ftpuser", Util.PLACEHOLDER_PASSWORD)), null, "", user);
            org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
               org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
            verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));

            assertNull(pdfPath(captor.getValue()).getPassword(), path);
         }
      }
   }

   @Test
   void addScheduleTask_aPlaceholderForAnotherUserOfTheLocationServerIsDropped() throws Exception {
      try(MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW)) {
         ScheduleTaskMetaData meta = new ScheduleTaskMetaData("loc", callerOwner.convertToKey());
         gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
            Collections.emptyList(),
            List.of(saveTo("ftp://host/reports/daily", "someoneelse", Util.PLACEHOLDER_PASSWORD)),
            null, "", user);
         org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
            org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
         verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));

         assertNull(pdfPath(captor.getValue()).getPassword());
      }
   }

   // a typed password is never replaced by the location's
   @Test
   void addScheduleTask_aTypedPasswordInsideAServerLocationIsKept() throws Exception {
      try(MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW)) {
         ScheduleTaskMetaData meta = new ScheduleTaskMetaData("loc", callerOwner.convertToKey());
         gateway.addScheduleTask(meta, true, false, -1, -1, null, null, null,
            Collections.emptyList(),
            List.of(saveTo("ftp://host/reports/daily", "ftpuser", "PW-PLACEHOLDER-FTP-1")),
            null, "", user);
         org.mockito.ArgumentCaptor<inetsoft.sree.schedule.ScheduleTask> captor =
            org.mockito.ArgumentCaptor.forClass(inetsoft.sree.schedule.ScheduleTask.class);
         verify(scheduleManager).setScheduleTask(anyString(), captor.capture(), eq(user));

         assertEquals("PW-PLACEHOLDER-FTP-1", pdfPath(captor.getValue()).getPassword());
      }
   }

   private inetsoft.sree.schedule.ScheduleTask readBackSaveToTask(String path, String password)
      throws Exception
   {
      return readBackTaskWith(List.of(saveTo(path, "ftpuser", password)));
   }

   @Test
   void buildUpdatedTask_aPlaceholderMovedIntoAServerLocationTakesTheLocationPassword()
      throws Exception
   {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller();
          MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW))
      {
         inetsoft.sree.schedule.ScheduleTask stored =
            readBackSaveToTask("ftp://host/old", "PW-PLACEHOLDER-FTP-OLD");

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(saveTo("ftp://host/reports/new", "ftpuser", Util.PLACEHOLDER_PASSWORD)));

         assertEquals(LOC_PW, pdfPath(candidate).getPassword());
      }
   }

   // C2: the same-path rule keeps working when no location is involved
   @Test
   void buildUpdatedTask_aPlaceholderForTheSamePathKeepsTheStoredPassword() throws Exception {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller();
          MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW))
      {
         inetsoft.sree.schedule.ScheduleTask stored =
            readBackSaveToTask("ftp://host/old", "PW-PLACEHOLDER-FTP-OLD");

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(saveTo("ftp://host/old", "ftpuser", Util.PLACEHOLDER_PASSWORD)));

         assertEquals("PW-PLACEHOLDER-FTP-OLD", pdfPath(candidate).getPassword());
      }
   }

   // the portal order: the location's password wins over the stored one for the same path
   @Test
   void buildUpdatedTask_theLocationPasswordWinsOverTheStoredOneForAPathInsideTheLocation()
      throws Exception
   {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller();
          MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW))
      {
         inetsoft.sree.schedule.ScheduleTask stored =
            readBackSaveToTask("ftp://host/reports/daily", "PW-PLACEHOLDER-FTP-OLD");

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored,
            List.of(saveTo("ftp://host/reports/daily", "ftpuser", Util.PLACEHOLDER_PASSWORD)));

         assertEquals(LOC_PW, pdfPath(candidate).getPassword());
      }
   }

   @Test
   void buildUpdatedTask_aPlaceholderOutsideEveryLocationWithNoStoredPathIsDropped()
      throws Exception
   {
      try(MockedStatic<inetsoft.util.Tool> toolStatics = toolStub();
          MockedStatic<OrganizationManager> ignored = siteAdminCaller();
          MockedStatic<SUtil> sutil = serverLocations("ftp://host/reports", "ftpuser", LOC_PW))
      {
         inetsoft.sree.schedule.ScheduleTask stored =
            readBackSaveToTask("ftp://host/old", "PW-PLACEHOLDER-FTP-OLD");

         inetsoft.sree.schedule.ScheduleTask candidate = updateWithZip(
            stored, List.of(saveTo("ftp://host/elsewhere", "ftpuser", Util.PLACEHOLDER_PASSWORD)));

         assertNull(pdfPath(candidate).getPassword());
      }
   }
}
