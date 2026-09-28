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
import inetsoft.web.admin.model.FileData;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.mockito.*;

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
 * Org ids are lower case because parseXML(elem, true) remaps to the lower-cased
 * OrganizationManager.getCurrentOrgID().
 */
@Tag("core")
class ImportTaskCrossOrgTest {
   private static final String ORG_A = "orga";
   private static final String HOST_ORG = Organization.getDefaultOrganizationID();

   private ScheduleManager scheduleManager;
   private AnalyticRepository repository;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgStatic;
   private ImportTaskController controller;
   private HttpServletRequest request;
   private final Map<String, Object> sessionAttrs = new HashMap<>();
   private XPrincipal caller;

   @BeforeEach
   void setUp() throws Exception {
      scheduleManager = mock(ScheduleManager.class);
      repository = mock(AnalyticRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
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

   private void asOrgAdmin() throws Exception {
      caller = principal("admin", ORG_A);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG_A);
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
      when(orgManager.getCurrentOrgID()).thenReturn(currentOrg);
      when(orgManager.getCurrentOrgID(any())).thenReturn(currentOrg);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      when(repository.checkPermission(any(), eq(ResourceType.SCHEDULE_TASK), anyString(),
                                      eq(ResourceAction.WRITE))).thenReturn(true);
      when(repository.checkPermission(any(), eq(ResourceType.EM_COMPONENT),
                                      eq("settings/schedule/settings"),
                                      eq(ResourceAction.ACCESS))).thenReturn(true);
   }

   private static XPrincipal principal(String name, String org) {
      XPrincipal principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID(name, org).convertToKey());
      return principal;
   }

   private ImportTaskResponse importAll(boolean overwriting) throws Exception {
      @SuppressWarnings("unchecked")
      List<ScheduleTask> parsed = (List<ScheduleTask>) sessionAttrs.get(INFO_ATTR);
      // the task ids are sent directly, the stock EM dialog sends the bare names
      List<String> ids = parsed.stream().map(ScheduleTask::getTaskId).toList();
      return controller.importScheduleTask(ids, request, overwriting, "http://host", caller);
   }

   private ScheduleTask importAndCapture(boolean overwriting) throws Exception {
      ImportTaskResponse response = importAll(overwriting);
      assertTrue(response.failedTasks().isEmpty(), response.failedTasks().toString());
      ArgumentCaptor<ScheduleTask> captor = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleManager, atMost(1)).setScheduleTask(anyString(), captor.capture(),
                                                         any(Principal.class));
      return captor.getAllValues().isEmpty() ? null : captor.getValue();
   }

   private void parse(String content) throws Exception {
      String xml = "<schedule>" + content + "</schedule>";
      FileData file = FileData.builder()
         .name("tasks.xml")
         .content(Base64.getEncoder().encodeToString(xml.getBytes(StandardCharsets.UTF_8)))
         .build();
      controller.setTaskFile(file, request, caller);
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
