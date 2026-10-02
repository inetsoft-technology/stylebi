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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.schedule.ScheduleTask;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.schedule.model.ScheduleTaskEditorModel;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77050: the EM/portal schedule task create/save path must not let a caller switch into
 * another organization via a client-supplied orgId, or set the task owner / run-as identity to
 * an identity the caller has no admin rights over.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("core")
class ScheduleTaskServiceOwnershipTest {
   @Mock
   private AnalyticRepository analyticRepository;
   @Mock
   private ScheduleManager scheduleManager;
   @Mock
   private ScheduleService scheduleService;
   @Mock
   private SecurityProvider securityProvider;
   @Mock
   private OrganizationManager organizationManager;
   @Mock
   private XPrincipal principal;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SUtil> sutilStatic;
   private ScheduleTaskService service;
   private final List<String> orgsSeenByScheduleManager = new ArrayList<>();

   private static final String CALLER_ORG = "orga";
   private static final String OTHER_ORG = "orgb";
   private static final IdentityID CALLER = new IdentityID("alice", CALLER_ORG);
   private static final String TASK_ID = CALLER.convertToKey() + ":Task1";

   @BeforeEach
   void setUp() throws Exception {
      OrganizationContextHolder.clear();
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      when(organizationManager.getCurrentOrgID(any())).thenAnswer(inv -> {
         String org = OrganizationContextHolder.getCurrentOrgId();
         return org != null ? org : CALLER_ORG;
      });

      // SUtil touches the Spring context; stub only what the save path needs
      sutilStatic = mockStatic(SUtil.class);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      sutilStatic.when(SUtil::loadLocaleProperties).thenReturn(new Properties());
      sutilStatic.when(() -> SUtil.getOwnerForNewTask(any())).thenAnswer(inv -> inv.getArgument(0));
      sutilStatic.when(() -> SUtil.getIdentity(any(), anyInt())).thenAnswer(inv -> {
         IdentityID id = inv.getArgument(0);
         int type = inv.getArgument(1);
         // like the real SUtil.getIdentity, null for an identity that doesn't exist
         return id == null ? null :
            type == Identity.GROUP ? securityProvider.getGroup(id) :
            type == Identity.ROLE ? securityProvider.getRole(id) : securityProvider.getUser(id);
      });

      when(principal.getName()).thenReturn(CALLER.convertToKey());
      when(principal.getOrgId()).thenReturn(CALLER_ORG);

      ScheduleTask existing = new ScheduleTask("Task1");
      existing.setOwner(CALLER);

      when(scheduleManager.getScheduleTask(anyString())).thenAnswer(inv -> {
         orgsSeenByScheduleManager.add(OrganizationContextHolder.getCurrentOrgId());
         return existing;
      });
      when(scheduleManager.getScheduleTask(isNull())).thenReturn(null);
      when(scheduleService.updateTaskName(any(), any(), any(), any())).thenReturn(TASK_ID);

      service = new ScheduleTaskService(analyticRepository, scheduleManager, scheduleService,
                                        null, securityProvider, null, null);
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      orgManagerStatic.close();
      OrganizationContextHolder.clear();
   }

   // ── C4a: client-supplied orgId ───────────────────────────────────────────

   @Test
   void saveTask_nonSiteAdminWithOtherOrgId_isRejectedBeforeTouchingOtherOrg() {
      ScheduleTaskEditorModel model = model(OTHER_ORG, options(CALLER.getName(), null));

      assertThrows(SecurityException.class, () -> service.saveTask(model, "", principal, true));
      assertFalse(orgsSeenByScheduleManager.contains(OTHER_ORG),
                  "the task map of another organization must never be consulted");
      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void newTask_nonSiteAdminWithOtherOrgId_isRejected() throws Exception {
      // no task-name collision, so the new-task path reaches the save
      when(scheduleManager.getScheduleTask(anyString())).thenAnswer(inv -> {
         orgsSeenByScheduleManager.add(OrganizationContextHolder.getCurrentOrgId());
         return null;
      });

      assertThrows(SecurityException.class, () -> service.getNewTaskDialogModel(
         null, principal, true, true, null, null, OTHER_ORG));
      assertFalse(orgsSeenByScheduleManager.contains(OTHER_ORG));
      verify(scheduleManager, never()).setScheduleTask(any(), any(), any(), any());
      assertNull(OrganizationContextHolder.getCurrentOrgId());
   }

   @Test
   void saveTask_nonSiteAdminWithOwnOrgId_isAllowed() throws Exception {
      ScheduleTaskEditorModel model = model(CALLER_ORG, options(CALLER.getName(), null));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(eq(TASK_ID), eq("Task1"), eq(CALLER), eq(principal));
   }

   @Test
   void saveTask_siteAdminWithOtherOrgId_switchesOrg() throws Exception {
      when(organizationManager.isSiteAdmin(principal)).thenReturn(true);
      ScheduleTaskEditorModel model = model(OTHER_ORG,
                                            options(CALLER.getName(), null));
      // owner "alice" resolves into the target org; the site admin administers it
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_USER),
                                            anyString(), eq(ResourceAction.ADMIN)))
         .thenReturn(true);

      runSaveIgnoringDownstreamFailures(model);

      assertTrue(orgsSeenByScheduleManager.contains(OTHER_ORG));
      assertNull(OrganizationContextHolder.getCurrentOrgId(), "original org must be restored");
   }

   // ── C4b: owner / run-as identity ─────────────────────────────────────────

   @Test
   void saveTask_ownerChangedToUnadministeredUser_isRejected() throws Exception {
      denyAdmin();
      ScheduleTaskEditorModel model = model(null, options("admin", null));

      assertThrows(SecurityException.class, () -> service.saveTask(model, "", principal, true));
      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_runAsUnadministeredUser_isRejected() throws Exception {
      denyAdmin();
      ScheduleTaskEditorModel model = model(null, options(CALLER.getName(), "admin"));

      assertThrows(SecurityException.class, () -> service.saveTask(model, "", principal, true));
      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_runAsUnadministeredGroup_isRejected() throws Exception {
      denyAdmin();
      TaskOptionsPaneModel options = TaskOptionsPaneModel.builder()
         .from(options(CALLER.getName(), "Everyone"))
         .idType(Identity.GROUP)
         .build();

      assertThrows(SecurityException.class,
                   () -> service.saveTask(model(null, options), "", principal, true));
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_ownerAndRunAsSelf_isAllowedWithoutAdminRights() throws Exception {
      denyAdmin();
      ScheduleTaskEditorModel model = model(null, options(CALLER.getName(), CALLER.getName()));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(CALLER), eq(principal));
   }

   @Test
   void saveTask_ownerChangedToAdministeredUser_isAllowed() throws Exception {
      when(securityProvider.checkPermission(principal, ResourceType.SECURITY_USER,
                                            new IdentityID("bob", CALLER_ORG).convertToKey(),
                                            ResourceAction.ADMIN))
         .thenReturn(true);
      when(securityProvider.getUser(new IdentityID("bob", CALLER_ORG)))
         .thenReturn(new User(new IdentityID("bob", CALLER_ORG)));
      ScheduleTaskEditorModel model = model(null, options("bob", "bob"));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(new IdentityID("bob", CALLER_ORG)),
                                             eq(principal));
   }

   @Test
   void saveTask_unchangedOwnerAndRunAs_isAllowedWithoutAdminRights() throws Exception {
      // e.g. a user permitted to edit someone else's task leaves owner/run-as untouched
      IdentityID other = new IdentityID("carol", CALLER_ORG);
      ScheduleTask existing = new ScheduleTask("Task1");
      existing.setOwner(other);
      existing.setIdentity(new User(other));
      when(scheduleManager.getScheduleTask(anyString())).thenReturn(existing);
      when(organizationManager.isOrgAdmin(principal)).thenReturn(true);
      denyAdmin();
      ScheduleTaskEditorModel model = model(null, options("carol", "carol"));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(other), eq(principal));
   }

   @ParameterizedTest
   @NullAndEmptySource
   void saveTask_ownerOmitted_keepsExistingOwner(String owner) throws Exception {
      // an omitted owner must not clear the owner of someone else's task
      IdentityID other = new IdentityID("carol", CALLER_ORG);
      ScheduleTask existing = new ScheduleTask("Task1");
      existing.setOwner(other);
      existing.setIdentity(new User(other));
      when(scheduleManager.getScheduleTask(anyString())).thenReturn(existing);
      when(organizationManager.isOrgAdmin(principal)).thenReturn(true);
      denyAdmin();
      ScheduleTaskEditorModel model = model(null, options(owner, "carol"));

      runSaveIgnoringDownstreamFailures(model);

      ArgumentCaptor<ScheduleTask> saved = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleService).saveTask(any(), saved.capture(), eq(principal));
      assertEquals(other, saved.getValue().getOwner());
   }

   // ── Bug #77281: an owner or execute-as identity that doesn't exist ──────

   // an org admin has SECURITY_USER ADMIN on any user name of the org, even one that doesn't
   // exist (Bug #66393), and a task owned by "admin~;~orga" runs as the site admin "admin"
   @Test
   void saveTask_orgAdminOwnerNamingMissingUser_isRejected() throws Exception {
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = model(null, options("admin", null));

      assertThrows(SecurityException.class, () -> service.saveTask(model, "", principal, true));
      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_orgAdminOwnerIsExistingOrgUser_isAllowed() throws Exception {
      asOrgAdminWithAdminOnEveryUser();
      IdentityID bob = new IdentityID("bob", CALLER_ORG);
      when(securityProvider.getUser(bob)).thenReturn(new User(bob));
      ScheduleTaskEditorModel model = model(null, options("bob", null));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(bob), eq(principal));
   }

   @Test
   void saveTask_siteAdminOwnerNamingMissingUser_isAllowed() throws Exception {
      // Bug #73978, a site admin in another org saves a task owned by its own name there
      asOrgAdminWithAdminOnEveryUser();
      when(organizationManager.isSiteAdmin(principal)).thenReturn(true);
      ScheduleTaskEditorModel model = model(null, options("admin", null));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(new IdentityID("admin", CALLER_ORG)),
                                             eq(principal));
   }

   @Test
   void saveTask_orgAdminUnchangedMissingOwner_isAllowed() throws Exception {
      // an org admin edits a task a site admin created in the org without changing its owner
      IdentityID siteAdminOwner = new IdentityID("admin", CALLER_ORG);
      ScheduleTask existing = new ScheduleTask("Task1");
      existing.setOwner(siteAdminOwner);
      when(scheduleManager.getScheduleTask(anyString())).thenReturn(existing);
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = model(null, options("admin", "admin"));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(siteAdminOwner), eq(principal));
   }

   @Test
   void saveTask_orgAdminRunAsMissingUser_isRejected() throws Exception {
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = model(null, options(CALLER.getName(), "admin"));

      assertThrows(SecurityException.class, () -> service.saveTask(model, "", principal, true));
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_orgAdminRunAsRole_isRejected() throws Exception {
      asOrgAdminWithAdminOnEveryUser();
      TaskOptionsPaneModel options = TaskOptionsPaneModel.builder()
         .from(options(CALLER.getName(), "Administrator"))
         .idType(Identity.ROLE)
         .build();

      assertThrows(SecurityException.class,
                   () -> service.saveTask(model(null, options), "", principal, true));
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   @Test
   void saveTask_orgAdminRunAsExistingOrgGroup_isAllowed() throws Exception {
      asOrgAdminWithAdminOnEveryUser();
      IdentityID staff = new IdentityID("staff", CALLER_ORG);
      when(securityProvider.getGroups()).thenReturn(new IdentityID[] { staff });
      TaskOptionsPaneModel options = TaskOptionsPaneModel.builder()
         .from(options(CALLER.getName(), "staff"))
         .idType(Identity.GROUP)
         .build();

      runSaveIgnoringDownstreamFailures(model(null, options));

      verify(scheduleService).updateTaskName(any(), any(), eq(CALLER), eq(principal));
   }

   // ── Bug #77281 (reopen): making a task run as its owner ─────────────────

   private static final IdentityID MISSING_OWNER = new IdentityID("admin", CALLER_ORG);
   private static final IdentityID UA = new IdentityID("ua", CALLER_ORG);
   private static final IdentityID BOB = new IdentityID("bob", CALLER_ORG);

   // a site admin created the task in the org with its own name as the owner (Bug #73978); it
   // runs as an existing user of the org
   private ScheduleTask storedTask(IdentityID owner, Identity identity) {
      ScheduleTask existing = new ScheduleTask("Task1");
      existing.setOwner(owner);
      existing.setIdentity(identity);
      when(scheduleManager.getScheduleTask(anyString())).thenAnswer(inv -> existing.clone());
      when(securityProvider.getUser(UA)).thenReturn(new User(UA));
      when(securityProvider.getUser(BOB)).thenReturn(new User(BOB));
      return existing;
   }

   private void assertRefusedBeforeAnyWrite(ScheduleTaskEditorModel model, boolean em)
      throws Exception
   {
      assertThrows(SecurityException.class, () -> service.saveTask(model, "", principal, em));
      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   private Identity savedIdentity(ScheduleTaskEditorModel model) throws Exception {
      // the renamed task lookup returns the stored task, so the save reaches scheduleService
      runSaveIgnoringDownstreamFailures(model);
      ArgumentCaptor<ScheduleTask> saved = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleService).saveTask(any(), saved.capture(), eq(principal));
      return saved.getValue().getIdentity();
   }

   @ParameterizedTest
   @NullAndEmptySource
   void saveTask_orgAdminClearsExecuteAsOfMissingOwnerTask_isRejected(String idName)
      throws Exception
   {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertRefusedBeforeAnyWrite(model(null, options("admin", idName)), true);
   }

   @Test
   void saveTask_orgAdminOmitsOwnerAndExecuteAsOfMissingOwnerTask_isRejected() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertRefusedBeforeAnyWrite(model(null, options(null, null)), true);
   }

   @Test
   void saveTask_orgAdminSetsExecuteAsToMissingOwner_isRejected() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertRefusedBeforeAnyWrite(model(null, options("admin", "admin")), true);
   }

   @Test
   void saveTask_orgAdminSetsExecuteAsToNameThatDoesNotResolve_isRejected() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertRefusedBeforeAnyWrite(model(null, options("admin", "ghost")), true);
   }

   @Test
   void saveTask_orgAdminClearsExecuteAsOnRename_isRejectedBeforeRename() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = ScheduleTaskEditorModel.builder()
         .from(model(null, options("admin", "")))
         .taskName("Renamed")
         .build();

      assertRefusedBeforeAnyWrite(model, true);
   }

   @Test
   void saveTask_portalClearsExecuteAsOfMissingOwnerTask_isRejected() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertRefusedBeforeAnyWrite(model(null, options("admin", "")), false);
   }

   @Test
   void saveTask_orgAdminClearsPlaceholderNamingMissingOwner_isRejected() throws Exception {
      // an unresolved User(owner) placeholder (Bug #77120) doesn't run, clearing it would make
      // the task run with the site admin's roles
      storedTask(MISSING_OWNER, new User(MISSING_OWNER));
      asOrgAdminWithAdminOnEveryUser();

      assertRefusedBeforeAnyWrite(model(null, options("admin", "")), true);
   }

   @Test
   void saveTask_orgAdminKeepsPlaceholderNamingMissingOwner_isAllowed() throws Exception {
      storedTask(MISSING_OWNER, new User(MISSING_OWNER));
      asOrgAdminWithAdminOnEveryUser();

      Identity identity = savedIdentity(model(null, options("admin", "admin")));

      assertEquals(MISSING_OWNER, identity.getIdentityID());
   }

   @Test
   void saveTask_orgAdminDescriptionOnlyEditOfMissingOwnerTask_keepsExecuteAs()
      throws Exception
   {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      Identity identity = savedIdentity(model(null, options("admin", "ua")));

      assertEquals(UA, identity.getIdentityID());
   }

   @Test
   void saveTask_orgAdminResendsExecuteAsThatNoLongerResolves_keepsItAndDoesNotRunAsOwner()
      throws Exception
   {
      // the execute-as user was removed from the provider; the unchanged exemption must keep
      // the stored placeholder (Bug #77120), never store no identity (run as the missing owner)
      storedTask(MISSING_OWNER, new User(UA));
      when(securityProvider.getUser(UA)).thenReturn(null);
      asOrgAdminWithAdminOnEveryUser();

      Identity identity = savedIdentity(model(null, options("admin", "ua")));

      assertNotNull(identity);
      assertEquals(UA, identity.getIdentityID());
      assertEquals(Identity.USER, identity.getType());
   }

   @Test
   void saveTask_orgAdminResendsUnresolvedExecuteAsWithOtherType_isRejected() throws Exception {
      // same name, different type: the keep-rule doesn't apply, so the stored identity would be
      // null and the task would run as the missing owner
      storedTask(MISSING_OWNER, new User(UA));
      when(securityProvider.getUser(UA)).thenReturn(null);
      asOrgAdminWithAdminOnEveryUser();
      TaskOptionsPaneModel options = TaskOptionsPaneModel.builder()
         .from(options("admin", "ua"))
         .idType(Identity.GROUP)
         .build();

      assertRefusedBeforeAnyWrite(model(null, options), true);
   }

   @ParameterizedTest
   @NullAndEmptySource
   void saveTask_orgAdminMissingOwnerTaskAlreadyWithoutExecuteAs_isAllowed(String idName)
      throws Exception
   {
      storedTask(MISSING_OWNER, null);
      asOrgAdminWithAdminOnEveryUser();

      assertNull(savedIdentity(model(null, options("admin", idName))));
   }

   @Test
   void saveTask_orgAdminSetsExecuteAsToSelfOnMissingOwnerTask_isAllowed() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();
      when(securityProvider.getUser(CALLER)).thenReturn(new User(CALLER));

      assertEquals(CALLER, savedIdentity(model(null, options("admin", "alice"))).getIdentityID());
   }

   @Test
   void saveTask_orgAdminTakesOwnershipAndClearsExecuteAs_isAllowed() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      runSaveIgnoringDownstreamFailures(model(null, options("alice", "")));

      verify(scheduleService).updateTaskName(any(), any(), eq(CALLER), eq(principal));
   }

   @Test
   void saveTask_orgAdminClearsOrSetsExecuteAsToExistingOwner_isAllowed() throws Exception {
      storedTask(BOB, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertNull(savedIdentity(model(null, options("bob", ""))));
   }

   @Test
   void saveTask_orgAdminSetsExecuteAsToExistingOwner_isAllowed() throws Exception {
      storedTask(BOB, new User(UA));
      asOrgAdminWithAdminOnEveryUser();

      assertEquals(BOB, savedIdentity(model(null, options("bob", "bob"))).getIdentityID());
   }

   @Test
   void saveTask_siteAdminClearsExecuteAsOfMissingOwnerTask_isAllowed() throws Exception {
      storedTask(MISSING_OWNER, new User(UA));
      asOrgAdminWithAdminOnEveryUser();
      when(organizationManager.isSiteAdmin(principal)).thenReturn(true);

      assertNull(savedIdentity(model(null, options("admin", ""))));
   }

   @Test
   void saveTask_editorNotAdministeringExistingOwnerClearsExecuteAs_isRejected()
      throws Exception
   {
      // a user allowed to edit bob's task (not delete-only-by-owner) doesn't administer bob
      storedTask(BOB, new User(UA));
      when(organizationManager.isOrgAdmin(principal)).thenReturn(true);
      denyAdmin();

      assertRefusedBeforeAnyWrite(model(null, options("bob", "")), false);
   }

   @Test
   void saveTask_editorNotAdministeringOwnerKeepsTaskWithoutExecuteAs_isAllowed()
      throws Exception
   {
      // the editor sends the owner as execute-as for a task without one
      storedTask(BOB, null);
      when(organizationManager.isOrgAdmin(principal)).thenReturn(true);
      denyAdmin();

      runSaveIgnoringDownstreamFailures(model(null, options("bob", "bob")));

      verify(scheduleService).updateTaskName(any(), any(), eq(BOB), eq(principal));
   }

   private void asOrgAdminWithAdminOnEveryUser() {
      when(organizationManager.isOrgAdmin(principal)).thenReturn(true);
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(true);
      when(securityProvider.getUsers()).thenReturn(new IdentityID[0]);
      when(securityProvider.getGroups()).thenReturn(new IdentityID[0]);
   }

   private void runSaveIgnoringDownstreamFailures(ScheduleTaskEditorModel model) {
      try {
         service.saveTask(model, "", principal, true);
      }
      catch(SecurityException e) {
         fail("unexpected security rejection: " + e.getMessage());
      }
      catch(Exception ignore) {
         // the mocked downstream (renamed task lookup etc.) is not wired; only the
         // authorization outcome matters here
      }
   }

   private void denyAdmin() {
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class), anyString(),
                                            eq(ResourceAction.ADMIN)))
         .thenReturn(false);
   }

   private static TaskOptionsPaneModel options(String owner, String idName) {
      return TaskOptionsPaneModel.builder()
         .enabled(true)
         .deleteIfNotScheduledToRun(false)
         .securityEnabled(true)
         .owner(owner)
         .idName(idName)
         .idType(Identity.USER)
         .build();
   }

   private static ScheduleTaskEditorModel model(String orgId, TaskOptionsPaneModel options) {
      return ScheduleTaskEditorModel.builder()
         .taskName("Task1")
         .oldTaskName(TASK_ID)
         .options(options)
         .orgId(orgId)
         .build();
   }
}
