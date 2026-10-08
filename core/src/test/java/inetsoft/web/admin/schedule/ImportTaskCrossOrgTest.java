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
package inetsoft.web.admin.schedule;

import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.ImportTaskDialogModel;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import inetsoft.web.admin.schedule.model.TaskDependencyModel;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.*;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;

import static inetsoft.web.admin.schedule.ImportTaskController.INFO_ATTR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77259, the EM schedule task import must not trust the owner, execute-as identity,
 * type and internal-only content of the uploaded xml, and must not write the global time
 * ranges before the import is confirmed or for a caller that can't edit them.
 *
 * The no-arg OrganizationManager.getCurrentOrgID() stub lower-cases the org id like the real
 * one, the mixedCaseOrg tests cover an org id that isn't lower case.
 */
@Tag("core")
class ImportTaskCrossOrgTest {
   private static final String ORG_A = "orga";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();
   private static final String MIXED_ORG = "OrgM";

   private ScheduleManager scheduleManager;
   private AnalyticRepository repository;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgStatic;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private final Map<String, Object> sessionAttrs = new HashMap<>();
   private XPrincipal caller;
   private String currentOrg;
   private SecurityProvider provider;
   // the users that exist, and the security user/group keys the caller administers
   private final Set<IdentityID> users = new HashSet<>();
   private final Set<String> adminOf = new HashSet<>();

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      repository = mock(AnalyticRepository.class);
      provider = mock(SecurityProvider.class);
      users.addAll(List.of(new IdentityID("admin", ORG_A), new IdentityID("alice", ORG_A),
                           new IdentityID("bob", ORG_A), new IdentityID("carol", ORG_A),
                           new IdentityID("admin", HOST_ORG)));
      when(provider.getUser(any(IdentityID.class)))
         .thenAnswer(inv -> users.contains(inv.<IdentityID>getArgument(0)) ?
            new User(inv.<IdentityID>getArgument(0)) : null);
      when(provider.getUsers()).thenAnswer(inv -> users.toArray(new IdentityID[0]));
      when(provider.getGroups()).thenReturn(new IdentityID[] { new IdentityID("staff", ORG_A) });
      when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                    eq(ResourceAction.ADMIN)))
         .thenAnswer(inv -> adminOf.contains(inv.<String>getArgument(2)));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(provider);
      // the real owner and execute-as checks, over the mocked security provider
      controller = new ImportTaskController(scheduleManager, mock(ScheduleTaskFolderService.class),
                                            repository, securityEngine);

      HttpSession session = mock(HttpSession.class);
      doAnswer(inv -> sessionAttrs.put(inv.getArgument(0), inv.getArgument(1)))
         .when(session).setAttribute(anyString(), any());
      doAnswer(inv -> sessionAttrs.remove(inv.getArgument(0)))
         .when(session).removeAttribute(anyString());
      when(session.getAttribute(anyString())).thenAnswer(inv -> sessionAttrs.get(inv.getArgument(0)));
      request = mock(HttpServletRequest.class);
      when(request.getSession(true)).thenReturn(session);

      orgManager = mock(OrganizationManager.class);
      orgStatic = mockStatic(OrganizationManager.class);
      orgStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      // scheduler access is granted to org admins
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), anyString(),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
      asOrgAdmin();
   }

   @AfterEach
   void tearDown() {
      orgStatic.close();
   }

   // ---------------------------------------------------------------------------------------
   // org admin
   // ---------------------------------------------------------------------------------------

   @Test
   void ownerFromOtherOrg_isMovedToImportingOrg() throws Exception {
      parse(task("Nightly", "bob~;~orgb", null, null, NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted, "precondition: the task was imported");
      assertEquals(ORG_A, persisted.getOwner().getOrgID());
      assertEquals("bob~;~" + ORG_A + ":Nightly", persisted.getTaskId());
   }

   @Test
   void executeAsOtherOrgIdentity_isMovedToImportingOrg() throws Exception {
      parse(task("RunAsAdmin", "admin~;~orga", null,
                 "idname=\"admin~;~" + HOST_ORG + "\" idtype=\"0\"", NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted, "precondition: the task was imported");
      IdentityID executeAs = persisted.getIdentity().getIdentityID();
      assertEquals(ORG_A, executeAs.getOrgID(), "execute-as must stay inside the importing org");
   }

   // A1, the global system administrator role must not be kept as the execute-as identity
   @Test
   void executeAsGlobalAdministratorRole_isRefused() throws Exception {
      parse(task("RunAsSiteAdmin", "admin~;~orga", null,
                 "idname=\"Administrator~;~__GLOBAL__\" idtype=\"2\"", NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(List.of("admin~;~orga:RunAsSiteAdmin"), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   @Test
   void internalTaskOverwrite_isRefused() throws Exception {
      parse(task(InternalScheduledTaskService.ASSET_FILE_BACKUP, "admin~;~orga", "INTERNAL_TASK",
                 null, NEVER_RUN));
      ScheduleTask existing = new ScheduleTask(InternalScheduledTaskService.ASSET_FILE_BACKUP,
                                               ScheduleTask.Type.INTERNAL_TASK);
      existing.setEditable(false);
      existing.setRemovable(false);
      when(scheduleManager.getScheduleTask(InternalScheduledTaskService.ASSET_FILE_BACKUP))
         .thenReturn(existing);

      ImportTaskResponse response = controller.importScheduleTask(
         List.of(InternalScheduledTaskService.ASSET_FILE_BACKUP), request, true, "http://host",
         caller);

      assertEquals(List.of(InternalScheduledTaskService.ASSET_FILE_BACKUP), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // an internal type with any other name would create an owner-less task id
   @Test
   void internalTypeWithNormalName_isRefused() throws Exception {
      parse(task("Nightly", "admin~;~orga", "INTERNAL_TASK", null, NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(List.of("Nightly"), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // A2, internal-only actions and conditions in a normal task
   @Test
   void normalTaskWithInternalActionOrCondition_isRefused() throws Exception {
      parse(task("Backup", "admin~;~orga", "NORMAL_TASK", null,
                 NEVER_RUN + "<Action type=\"AssetFileBackup\"/>") +
            task("Deps", "admin~;~orga", "NORMAL_TASK", null,
                 NEVER_RUN + "<Action type=\"AssetsDependencies\"/>") +
            task("Balance", "admin~;~orga", "NORMAL_TASK", null,
                 "<Condition type=\"TaskBalancer\"/>"));

      ImportTaskResponse response = importAll(false);

      assertEquals(Set.of("admin~;~orga:Backup", "admin~;~orga:Deps", "admin~;~orga:Balance"),
                   new HashSet<>(response.failedTasks()));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // A3, removable="false" in the xml must not skip the scheduler permission
   @Test
   void nonRemovableTask_withoutSchedulerPermission_isRefused() throws Exception {
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER), eq("*"),
                                      eq(ResourceAction.ACCESS))).thenReturn(false);
      parse(task("Nightly", "admin~;~orga", null, "removable=\"false\"", NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(List.of("admin~;~orga:Nightly"), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // A3, the overwrite check no longer depends on the stored task being editable
   @Test
   void overwriteNonEditableTask_withoutPermission_isRefused() throws Exception {
      parse(task("Nightly", "admin~;~orga", null, null, NEVER_RUN));
      ScheduleTask existing = new ScheduleTask("Nightly");
      existing.setEditable(false);
      when(scheduleManager.getScheduleTask("admin~;~orga:Nightly")).thenReturn(existing);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULER),
                                      eq("admin~;~orga:Nightly"), eq(ResourceAction.ACCESS)))
         .thenReturn(false);

      ImportTaskResponse response = importAll(true);

      assertEquals(List.of("admin~;~orga:Nightly"), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   @Test
   void timeRanges_notWrittenOnUpload_norOnConfirmByOrgAdmin() throws Exception {
      try(MockedStatic<SreeEnv> sreeEnv = mockStatic(SreeEnv.class)) {
         parse(task("Nightly", "admin~;~orga", null, null, NEVER_RUN) + TIME_RANGES);
         sreeEnv.verify(() -> SreeEnv.setProperty(eq("schedule.time.ranges"), anyString()),
                        never());

         importAll(false);
         sreeEnv.verify(() -> SreeEnv.setProperty(eq("schedule.time.ranges"), anyString()),
                        never());
      }

      // the tasks themselves are still imported
      verify(scheduleManager).setScheduleTask(eq("admin~;~orga:Nightly"), any(ScheduleTask.class),
                                              eq(caller));
   }

   // positive control, an org admin importing their own org's export
   @Test
   void sameOrgImport_isUnchanged() throws Exception {
      parse(task("Nightly", "alice~;~orga", null, "idname=\"bob~;~orga\" idtype=\"0\"",
                 NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(new IdentityID("alice", ORG_A), persisted.getOwner());
      assertEquals(new IdentityID("bob", ORG_A), persisted.getIdentity().getIdentityID());
      assertEquals(Identity.USER, persisted.getIdentity().getType());
   }

   // B1, an owner name that doesn't exist in the importing org would run the task with the roles
   // of the site admin of the same name (SUtil.getScheduleTaskOwnerPrincipal)
   @Test
   void ownerNamingSiteAdmin_notInImportingOrg_isRefused() throws Exception {
      users.remove(new IdentityID("admin", ORG_A));
      caller = principal("carol", ORG_A);
      adminOf.add(new IdentityID("admin", ORG_A).convertToKey());
      parse(task("Escalate", "admin~;~" + HOST_ORG, "NORMAL_TASK", null, NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(List.of("admin~;~orga:Escalate"), response.failedTasks());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // B1, the legacy "null" owner is the host org system user
   @Test
   void nullOwner_isRefused() throws Exception {
      parse(task("Legacy", "null", null, null, NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(1, response.failedTasks().size(), response.failedTasks().toString());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // B1, an existing user of the org the caller doesn't administer
   @Test
   void ownerNotAdministeredByCaller_isRefused() throws Exception {
      caller = principal("carol", ORG_A);
      adminOf.clear();
      parse(task("Nightly", "alice~;~orga", null, null, NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(List.of("alice~;~orga:Nightly"), response.failedTasks());
   }

   // B1 positive control, a caller may always import a task owned by themselves
   @Test
   void ownTask_withoutUserAdmin_isImported() throws Exception {
      caller = principal("carol", ORG_A);
      adminOf.clear();
      parse(task("Nightly", "carol~;~orgb", null, null, NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(new IdentityID("carol", ORG_A), persisted.getOwner());
   }

   // I1, a same-org user or group the caller doesn't administer can't be the execute-as identity
   @Test
   void executeAsIdentityNotAdministeredByCaller_isRefused() throws Exception {
      caller = principal("carol", ORG_A);
      adminOf.clear();
      parse(task("AsAdmin", "carol~;~orga", null, "idname=\"admin~;~orga\" idtype=\"0\"",
                 NEVER_RUN) +
            task("AsStaff", "carol~;~orga", null, "idname=\"staff~;~orga\" idtype=\"1\"",
                 NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(Set.of("carol~;~orga:AsAdmin", "carol~;~orga:AsStaff"),
                   new HashSet<>(response.failedTasks()));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // I1 positive control, the same identities once the caller administers them
   @Test
   void executeAsIdentityAdministeredByCaller_isImported() throws Exception {
      caller = principal("carol", ORG_A);
      adminOf.clear();
      adminOf.add(new IdentityID("admin", ORG_A).convertToKey());
      adminOf.add(new IdentityID("staff", ORG_A).convertToKey());
      parse(task("AsAdmin", "carol~;~orga", null, "idname=\"admin~;~orga\" idtype=\"0\"",
                 NEVER_RUN) +
            task("AsStaff", "carol~;~orga", null, "idname=\"staff~;~orga\" idtype=\"1\"",
                 NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager, times(2)).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                        eq(caller));
   }

   // M2, a completion condition must not chain a normal task onto an internal task
   @Test
   void completionOfInternalTask_isRefused() throws Exception {
      parse(task("AfterBackup", "admin~;~orga", null, null,
                 "<Condition type=\"Completion\" task=\"" +
                    InternalScheduledTaskService.ASSET_FILE_BACKUP + "\"/>"));

      ImportTaskResponse response = importAll(false);

      assertEquals(List.of("admin~;~orga:AfterBackup"), response.failedTasks());
   }

   // M1, the owner is remapped under the caller as the context principal, and the previous
   // context principal is restored
   @Test
   void parse_usesCallerAsContextPrincipal_andRestoresIt() throws Exception {
      Principal previous = principal("someone", "orgc");
      ThreadContext.setContextPrincipal(previous);

      try {
         parse(task("Nightly", "bob~;~orgb", null, null, NEVER_RUN));
         assertSame(previous, ThreadContext.getContextPrincipal());
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(new IdentityID("bob", ORG_A), persisted.getOwner());
   }

   // n1, the owner is a user the caller administers and also the execute-as identity, which
   // relies on getExecuteAsUsers() including the owner
   @Test
   void administeredOwner_asExecuteAsIdentity_isImported() throws Exception {
      caller = principal("carol", ORG_A);
      adminOf.clear();
      adminOf.add(new IdentityID("alice", ORG_A).convertToKey());
      parse(task("Nightly", "alice~;~orgb", null, "idname=\"alice~;~orgb\" idtype=\"0\"",
                 NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(new IdentityID("alice", ORG_A), persisted.getOwner());
      assertEquals(new IdentityID("alice", ORG_A), persisted.getIdentity().getIdentityID());
   }

   // n1, a host-org delegate that isn't a site admin can't own a task as the host org admin
   @Test
   void hostOrgDelegate_ownerNotAdministered_isRefused() throws Exception {
      caller = principal("dave", HOST_ORG);
      users.add(new IdentityID("dave", HOST_ORG));
      stubCurrentOrg(HOST_ORG);
      adminOf.clear();
      parse(task("Nightly", "admin~;~" + HOST_ORG, null,
                 "idname=\"admin~;~" + HOST_ORG + "\" idtype=\"0\"", NEVER_RUN) +
            task("Mine", "dave~;~" + HOST_ORG, null,
                 "idname=\"admin~;~" + HOST_ORG + "\" idtype=\"0\"", NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(Set.of("admin~;~" + HOST_ORG + ":Nightly", "dave~;~" + HOST_ORG + ":Mine"),
                   new HashSet<>(response.failedTasks()));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // m1, a mixed-case org id, the owner and execute-as identity get the caller's actual org id
   @Test
   void mixedCaseOrg_ownTask_isImported() throws Exception {
      asMixedCaseOrgCaller();
      adminOf.add(new IdentityID("alice", MIXED_ORG).convertToKey());
      parse(task("Nightly", "carol~;~orgb", null, "idname=\"alice~;~orgb\" idtype=\"0\"",
                 NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(new IdentityID("carol", MIXED_ORG), persisted.getOwner());
      assertEquals("carol~;~" + MIXED_ORG + ":Nightly", persisted.getTaskId());
      assertEquals(new IdentityID("alice", MIXED_ORG), persisted.getIdentity().getIdentityID());
      assertEquals(Identity.USER, persisted.getIdentity().getType());
   }

   // m1, a foreign or not administered owner is still refused in a mixed-case org
   @Test
   void mixedCaseOrg_foreignOwner_isRefused() throws Exception {
      asMixedCaseOrgCaller();
      parse(task("Escalate", "admin~;~" + HOST_ORG, null, null, NEVER_RUN) +
            task("Alices", "alice~;~orgb", null, null, NEVER_RUN) +
            task("RunAsAdmin", "carol~;~orgb", null,
                 "idname=\"admin~;~" + HOST_ORG + "\" idtype=\"0\"", NEVER_RUN));

      ImportTaskResponse response = importAll(false);

      assertEquals(Set.of("admin~;~" + MIXED_ORG + ":Escalate", "alice~;~" + MIXED_ORG + ":Alices",
                          "carol~;~" + MIXED_ORG + ":RunAsAdmin"),
                   new HashSet<>(response.failedTasks()));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   private void asMixedCaseOrgCaller() {
      users.addAll(List.of(new IdentityID("carol", MIXED_ORG),
                           new IdentityID("alice", MIXED_ORG)));
      caller = principal("carol", MIXED_ORG);
      stubCurrentOrg(MIXED_ORG);
      adminOf.clear();
   }

   // ---------------------------------------------------------------------------------------
   // site admin
   // ---------------------------------------------------------------------------------------

   @Test
   void siteAdmin_foreignOwnerRemapped_globalRoleKept() throws Exception {
      asSiteAdmin(HOST_ORG);
      parse(task("Nightly", "bob~;~orgb", null,
                 "idname=\"Administrator~;~__GLOBAL__\" idtype=\"2\"", NEVER_RUN));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(HOST_ORG, persisted.getOwner().getOrgID());
      assertNull(persisted.getIdentity().getIdentityID().getOrgID());
      assertEquals(Identity.ROLE, persisted.getIdentity().getType());
   }

   // A4, an internal task imported by a site admin switched to another org stays in the host org
   @Test
   void siteAdmin_internalTask_keepsSystemOwnerInHostOrg() throws Exception {
      asSiteAdmin("orgb");
      parse(task(InternalScheduledTaskService.ASSET_FILE_BACKUP, "INETSOFT_SYSTEM~;~" + HOST_ORG,
                 "INTERNAL_TASK", "removable=\"false\" editable=\"false\"",
                 NEVER_RUN + "<Action type=\"AssetFileBackup\"/>"));

      ScheduleTask persisted = importAndCapture(false);

      assertNotNull(persisted);
      assertEquals(InternalScheduledTaskService.ASSET_FILE_BACKUP, persisted.getTaskId());
      assertEquals(new IdentityID(XPrincipal.SYSTEM, HOST_ORG), persisted.getOwner());
   }

   @Test
   void siteAdmin_timeRanges_appliedOnConfirm() throws Exception {
      asSiteAdmin(HOST_ORG);

      try(MockedStatic<TimeRange> timeRange = mockStatic(TimeRange.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS))
      {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         parse(task("Nightly", "admin~;~" + HOST_ORG, null, null, NEVER_RUN) + TIME_RANGES);
         timeRange.verify(() -> TimeRange.setTimeRanges(any()), never());

         importAll(false);
         timeRange.verify(() -> TimeRange.setTimeRanges(argThat(
            (Collection<TimeRange> r) -> r.size() == 1 &&
               "Injected".equals(r.iterator().next().getName()))));
      }
   }

   @Test
   void siteAdmin_timeRanges_notAppliedFromOtherOrg() throws Exception {
      asSiteAdmin("orgb");

      try(MockedStatic<TimeRange> timeRange = mockStatic(TimeRange.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS))
      {
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         parse(task("Nightly", "admin~;~orgb", null, null, NEVER_RUN) + TIME_RANGES);
         importAll(false);
         timeRange.verify(() -> TimeRange.setTimeRanges(any()), never());
      }
   }

   // ---------------------------------------------------------------------------------------
   // single tenant, security disabled
   // ---------------------------------------------------------------------------------------

   // positive control, with security disabled every check is granted and the caller is not a
   // site admin by role, the import (execute-as and time ranges) must still work as before
   @Test
   void securityDisabled_singleTenant_importAndTimeRangesAllowed() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      controller = new ImportTaskController(scheduleManager, mock(ScheduleTaskFolderService.class),
                                            repository, securityEngine);
      caller = principal("anonymous", HOST_ORG);
      when(orgManager.getCurrentOrgID()).thenReturn(HOST_ORG);
      when(orgManager.getCurrentOrgID(any())).thenReturn(HOST_ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(repository.checkPermission(any(), any(ResourceType.class), anyString(),
                                      any(ResourceAction.class))).thenReturn(true);

      try(MockedStatic<TimeRange> timeRange = mockStatic(TimeRange.class);
          MockedStatic<SUtil> sutil = mockStatic(SUtil.class, CALLS_REAL_METHODS))
      {
         sutil.when(SUtil::isMultiTenant).thenReturn(false);
         parse(task("Nightly", "admin~;~" + HOST_ORG, null,
                    "idname=\"Administrator~;~__GLOBAL__\" idtype=\"2\"", NEVER_RUN) +
               TIME_RANGES);
         timeRange.verify(() -> TimeRange.setTimeRanges(any()), never());

         ScheduleTask persisted = importAndCapture(false);

         assertNotNull(persisted);
         assertEquals(new IdentityID("admin", HOST_ORG), persisted.getOwner());
         assertEquals(Identity.ROLE, persisted.getIdentity().getType());
         timeRange.verify(() -> TimeRange.setTimeRanges(argThat(
            (Collection<TimeRange> r) -> r.size() == 1 &&
               "Injected".equals(r.iterator().next().getName()))));
      }
   }

   // ---------------------------------------------------------------------------------------
   // Bug #77283, the selection is built from the rows returned by setTaskFile, as the EM
   // import dialog does, instead of from the parsed task ids
   // ---------------------------------------------------------------------------------------

   @Test
   void normalTask_exportedByWriteXML_selectedFromRows_isImported() throws Exception {
      ScheduleTask exported = new ScheduleTask("Backdoor");
      exported.setOwner(new IdentityID("admin", ORG_A));
      exported.addCondition(new NeverRunCondition());
      StringWriter xml = new StringWriter();
      exported.writeXML(new PrintWriter(xml));

      List<TaskDependencyModel> rows = parse(xml.toString()).tasks();

      assertEquals(1, rows.size());
      assertEquals("Backdoor", rows.get(0).task(), "the display name stays the bare name");
      assertEquals("admin~;~" + ORG_A + ":Backdoor", rows.get(0).taskId());

      ImportTaskResponse response = importRows(rows, false);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager, times(1)).setScheduleTask(eq("admin~;~" + ORG_A + ":Backdoor"),
                                                        any(ScheduleTask.class), eq(caller));
   }

   @Test
   void internalTask_selectedFromRows_isImported() throws Exception {
      asSiteAdmin(HOST_ORG);
      List<TaskDependencyModel> rows = parse(
         task(InternalScheduledTaskService.ASSET_FILE_BACKUP, "INETSOFT_SYSTEM~;~" + HOST_ORG,
              "INTERNAL_TASK", "removable=\"false\" editable=\"false\"",
              NEVER_RUN + "<Action type=\"AssetFileBackup\"/>")).tasks();

      assertEquals(InternalScheduledTaskService.ASSET_FILE_BACKUP, rows.get(0).taskId());

      ImportTaskResponse response = importRows(rows, false);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager, times(1)).setScheduleTask(
         eq(InternalScheduledTaskService.ASSET_FILE_BACKUP), any(ScheduleTask.class), eq(caller));
   }

   // the owner is remapped to the importing org, the row carries the remapped id
   @Test
   void remappedOwner_selectedFromRows_isImported() throws Exception {
      List<TaskDependencyModel> rows = parse(task("X", "bob~;~orgb", null, null, NEVER_RUN))
         .tasks();

      assertEquals("X", rows.get(0).task());
      assertEquals("bob~;~" + ORG_A + ":X", rows.get(0).taskId());

      importRows(rows, false);

      verify(scheduleManager, times(1)).setScheduleTask(eq("bob~;~" + ORG_A + ":X"),
                                                        any(ScheduleTask.class), eq(caller));
   }

   // the same name under two owners must stay distinguishable, selecting one row imports only it
   @Test
   void sameNameDifferentOwners_onlySelectedRowIsImported() throws Exception {
      List<TaskDependencyModel> rows = parse(
         task("Nightly", "alice~;~orga", null, null, NEVER_RUN) +
         task("Nightly", "bob~;~orga", null, null, NEVER_RUN)).tasks();

      assertEquals(List.of("Nightly", "Nightly"),
                   rows.stream().map(TaskDependencyModel::task).toList());

      importRows(List.of(rows.get(1)), false);

      verify(scheduleManager, times(1)).setScheduleTask(eq("bob~;~" + ORG_A + ":Nightly"),
                                                        any(ScheduleTask.class), eq(caller));
      verify(scheduleManager, never()).setScheduleTask(eq("alice~;~" + ORG_A + ":Nightly"),
                                                       any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // an older (13.x) export stores userName:taskName with a plain user name owner (Bug #73029,
   // e.g. <Task name="user0:Task1" owner="user0">), the row carries the rewritten id
   @Test
   void legacyUserTaskName_selectedFromRows_isImported() throws Exception {
      List<TaskDependencyModel> rows = parse(task("admin:Old", "admin", null, null,
                                                  NEVER_RUN)).tasks();

      assertEquals("admin:Old", rows.get(0).task());
      assertEquals("admin~;~" + ORG_A + ":Old", rows.get(0).taskId());

      ImportTaskResponse response = importRows(rows, false);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager, times(1)).setScheduleTask(eq("admin~;~" + ORG_A + ":Old"),
                                                        any(ScheduleTask.class), eq(caller));
   }

   // Bug #77883, a current export writes the owner key and the bare name, a ':' in the name
   // (even one that starts with the owner's name) is kept
   @Test
   void currentColonTaskName_selectedFromRows_isImportedUnchanged() throws Exception {
      List<TaskDependencyModel> rows = parse(task("admin:Old", "admin~;~orga", null, null,
                                                  NEVER_RUN)).tasks();
      String id = "admin~;~" + ORG_A + ":admin:Old";

      assertEquals("admin:Old", rows.get(0).task());
      assertEquals(id, rows.get(0).taskId());

      ImportTaskResponse response = importRows(rows, false);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager, times(1)).setScheduleTask(eq(id), any(ScheduleTask.class),
                                                        eq(caller));
   }

   // an older export stores owner="null", which is the system user of the host org
   @Test
   void nullOwner_siteAdmin_selectedFromRows_isImported() throws Exception {
      asSiteAdmin(HOST_ORG);
      List<TaskDependencyModel> rows = parse(task("Legacy", "null", null, null, NEVER_RUN))
         .tasks();
      String id = XPrincipal.SYSTEM + "~;~" + HOST_ORG + ":Legacy";

      assertEquals(id, rows.get(0).taskId());

      ImportTaskResponse response = importRows(rows, false);

      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      verify(scheduleManager, times(1)).setScheduleTask(eq(id), any(ScheduleTask.class),
                                                        eq(caller));
   }

   // an existing task is replaced only when overwriting, the selection matches in both cases
   @Test
   void existingNormalTask_selectedFromRows_honorsOverwrite() throws Exception {
      String id = "admin~;~" + ORG_A + ":Backdoor";
      ScheduleTask existing = new ScheduleTask("Backdoor");
      existing.setOwner(new IdentityID("admin", ORG_A));
      when(scheduleManager.getScheduleTask(id)).thenReturn(existing);
      String xml = task("Backdoor", "admin~;~orga", null, null, NEVER_RUN);

      ImportTaskResponse kept = importRows(parse(xml).tasks(), false);

      assertTrue(kept.failedTasks().isEmpty(), kept.failedTasks().toString());
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));

      ImportTaskResponse replaced = importRows(parse(xml).tasks(), true);

      assertTrue(replaced.failedTasks().isEmpty(), replaced.failedTasks().toString());
      verify(scheduleManager, times(1)).setScheduleTask(eq(id), any(ScheduleTask.class),
                                                        eq(caller));
   }

   // the Bug #77259 checks now apply to the rows the stock dialog sends
   @Test
   void internalContent_selectedFromRows_isStillRefused() throws Exception {
      List<TaskDependencyModel> rows = parse(
         task("Backup", "admin~;~orga", "NORMAL_TASK", null,
              NEVER_RUN + "<Action type=\"AssetFileBackup\"/>") +
         task("Nightly", "admin~;~orga", "INTERNAL_TASK", null, NEVER_RUN)).tasks();

      ImportTaskResponse response = importRows(rows, false);

      assertEquals(Set.of("admin~;~orga:Backup", "Nightly"),
                   new HashSet<>(response.failedTasks()));
      verify(scheduleManager, never()).setScheduleTask(anyString(), any(ScheduleTask.class),
                                                       any(Principal.class));
   }

   // ---------------------------------------------------------------------------------------

   private void asOrgAdmin() throws Exception {
      caller = principal("admin", ORG_A);
      stubCurrentOrg(ORG_A);
      adminOf.clear();
      users.stream().filter(u -> ORG_A.equals(u.getOrgID()))
         .forEach(u -> adminOf.add(u.convertToKey()));
      adminOf.add(new IdentityID("staff", ORG_A).convertToKey());
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      // org admins are refused internal tasks and the scheduler settings
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK), anyString(),
                                      eq(ResourceAction.WRITE))).thenReturn(false);
      when(repository.checkPermission(any(), eq(ResourceType.EM_COMPONENT),
                                      eq("settings/schedule/settings"),
                                      eq(ResourceAction.ACCESS))).thenReturn(false);
   }

   private void asSiteAdmin(String currentOrg) throws Exception {
      caller = principal("admin", HOST_ORG);
      stubCurrentOrg(currentOrg);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK), anyString(),
                                      eq(ResourceAction.WRITE))).thenReturn(true);
      when(repository.checkPermission(any(), eq(ResourceType.EM_COMPONENT),
                                      eq("settings/schedule/settings"),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
   }

   // the no-arg getCurrentOrgID() used by parseXML(elem, true) only answers the caller's org
   // when the controller made the caller the context principal
   private void stubCurrentOrg(String org) {
      currentOrg = org;
      when(orgManager.getCurrentOrgID()).thenAnswer(
         inv -> ThreadContext.getContextPrincipal() == caller ?
            currentOrg.toLowerCase() : "no-context-org");
      when(orgManager.getCurrentOrgID(any())).thenReturn(org);
   }

   private static XPrincipal principal(String name, String org) {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID(name, org).convertToKey());
      return principal;
   }

   private ImportTaskResponse importAll(boolean overwriting) throws Exception {
      @SuppressWarnings("unchecked")
      List<ScheduleTask> parsed = (List<ScheduleTask>) sessionAttrs.get(INFO_ATTR);
      // the task ids are sent directly, importRows() sends what the EM dialog sends
      List<String> ids = parsed.stream().map(ScheduleTask::getTaskId).toList();
      return controller.importScheduleTask(ids, request, overwriting, "http://host", caller);
   }

   /**
    * Sends the selection the EM import dialog builds from the setTaskFile rows.
    */
   private ImportTaskResponse importRows(List<TaskDependencyModel> rows, boolean overwriting)
      throws Exception
   {
      List<String> selected = rows.stream()
         .map(row -> row.taskId() != null ? row.taskId() : row.task())
         .toList();
      return controller.importScheduleTask(selected, request, overwriting, "http://host", caller);
   }

   private ScheduleTask importAndCapture(boolean overwriting) throws Exception {
      ImportTaskResponse response = importAll(overwriting);
      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      ArgumentCaptor<ScheduleTask> captor = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager, atMost(1)).setScheduleTask(anyString(), captor.capture(),
                                                         any(Principal.class));
      return captor.getAllValues().isEmpty() ? null : captor.getValue();
   }

   private ImportTaskDialogModel parse(String content) throws Exception {
      String xml = "<schedule>" + content + "</schedule>";
      FileData file = FileData.builder()
         .name("tasks.xml")
         .content(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)))
         .build();
      return controller.setTaskFile(file, request, caller);
   }

   private static String task(String name, String owner, String type, String extra,
                              String body)
   {
      return "<Task name=\"" + name + "\" owner=\"" + owner + "\" enabled=\"true\"" +
         (type == null ? "" : " type=\"" + type + "\"") +
         (extra == null ? "" : " " + extra) + ">" + body + "</Task>";
   }

   private static final String NEVER_RUN = "<Condition type=\"NeverRun\"/>";
   private static final String TIME_RANGES = "<timeRanges>" +
      "<timeRange default=\"true\"><name><![CDATA[Injected]]></name>" +
      "<start>01:00</start><end>02:00</end></timeRange></timeRanges>";
}
