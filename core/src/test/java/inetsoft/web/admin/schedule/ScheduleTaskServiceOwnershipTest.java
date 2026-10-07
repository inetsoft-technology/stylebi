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
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.web.admin.schedule.model.*;
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

import java.util.*;

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
   private ScheduleConditionService scheduleConditionService;
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
                                        scheduleConditionService, securityProvider, null, null);
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
      // Epic 70095, a site admin in another org saves a task owned by its own name there
      asOrgAdminWithAdminOnEveryUser();
      when(organizationManager.isSiteAdmin(principal)).thenReturn(true);
      ScheduleTaskEditorModel model = model(null, options("admin", null));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(new IdentityID("admin", CALLER_ORG)),
                                             eq(principal));
   }

   @Test
   void saveTask_orgAdminUnchangedMissingOwner_isAllowed() throws Exception {
      // an org admin edits a task a site admin created in the org without changing its owner,
      // the task runs with the site admin's roles (Epic 70095); the save neither adds nor
      // changes an action or condition, so it is allowed (Bug #77405)
      IdentityID siteAdminOwner = new IdentityID("admin", CALLER_ORG);
      ScheduleTask existing = new ScheduleTask("Task1");
      existing.setOwner(siteAdminOwner);
      when(scheduleManager.getScheduleTask(anyString())).thenReturn(existing);
      runsAsSiteAdmin(siteAdminOwner);
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = model(null, options("admin", "admin"));

      runSaveIgnoringDownstreamFailures(model);

      verify(scheduleService).updateTaskName(any(), any(), eq(siteAdminOwner), eq(principal));
      verify(scheduleService).saveTask(any(), any(), eq(principal));
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

   // a site admin created the task in the org with its own name as the owner (Epic 70095); it
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

   // ── Bug #77405: the actions and conditions of a task that runs as a site admin ─────────

   private static final IdentityID SITE_ADMIN = new IdentityID("admin", "host-org");
   private final Map<String, ScheduleActionModel> actionModels = new HashMap<>();
   private final Map<String, ScheduleConditionModel> conditionModels = new HashMap<>();

   @Test
   void saveTask_orgAdminAddsActionToSiteAdminTask_isRefused() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();

      assertContentRefused(contentModel(options("admin", null), List.of("child", "other"),
                                        List.of(90)), true);
   }

   @Test
   void saveTask_orgAdminChangesActionOfSiteAdminTask_isRefused() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();

      assertContentRefused(contentModel(options("admin", null), List.of("other"), List.of(90)),
                           true);
   }

   @Test
   void saveTask_orgAdminAddsConditionToSiteAdminTask_isRefused() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();

      assertContentRefused(contentModel(options("admin", null), List.of("child"),
                                        List.of(90, 120)), true);
   }

   @Test
   void saveTask_portalChangesConditionOfSiteAdminTask_isRefused() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();
      // may set the start time, otherwise sanitizeConditions restores the stored time
      when(scheduleService.checkPermission(any(), any(), anyString())).thenReturn(true);

      assertContentRefused(contentModel(options("admin", null), List.of("child"), List.of(120)),
                           false);
   }

   @Test
   void saveTask_orgAdminRenamesAndAddsActionToSiteAdminTask_isRefusedWithoutRename()
      throws Exception
   {
      ScheduleTask stored = siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = ScheduleTaskEditorModel.builder()
         .from(contentModel(options("admin", null), List.of("child", "other"), List.of(90)))
         .taskName("Renamed")
         .build();

      assertContentRefused(model, true);
      assertEquals("Task1", stored.getName());
      assertEquals(1, stored.getActionCount());
      assertEquals("child", ((BatchAction) stored.getAction(0)).getTaskId());
   }

   @Test
   void saveTask_orgAdminRenamesSiteAdminTask_isAllowed() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();
      ScheduleTaskEditorModel model = ScheduleTaskEditorModel.builder()
         .from(contentModel(options("admin", null), List.of("child"), List.of(90)))
         .taskName("Renamed")
         .build();

      ScheduleTask saved = savedTask(model);

      verify(scheduleService).updateTaskName(eq(TASK_ID), eq("Renamed"), eq(MISSING_OWNER),
                                             eq(principal));
      assertEquals(1, saved.getActionCount());
      assertEquals(1, saved.getConditionCount());
      assertEquals(MISSING_OWNER, saved.getOwner());
      assertNull(saved.getIdentity(), "still runs as the site admin");
   }

   @Test
   void saveTask_orgAdminChangesOptionsOfSiteAdminTask_isAllowed() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();
      TaskOptionsPaneModel options = TaskOptionsPaneModel.builder()
         .from(options("admin", null))
         .enabled(false)
         .description("changed")
         .build();

      ScheduleTask saved = savedTask(contentModel(options, List.of("child"), List.of(90)));

      assertFalse(saved.isEnabled());
      assertEquals("changed", saved.getDescription());
   }

   @Test
   void saveTask_orgAdminRemovesActionAndConditionOfSiteAdminTask_isAllowed() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();

      ScheduleTask saved = savedTask(contentModel(options("admin", null), List.of(), List.of()));

      assertEquals(0, saved.getActionCount());
      assertEquals(0, saved.getConditionCount());
   }

   @Test
   void saveTask_orgAdminAddsActionAndSetsExecuteAs_isAllowed() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();
      when(securityProvider.getUsers()).thenReturn(new IdentityID[] { BOB });

      ScheduleTask saved = savedTask(
         contentModel(options("admin", "bob"), List.of("child", "other"), List.of(90)));

      assertEquals(BOB, saved.getIdentity().getIdentityID());
      assertEquals(2, saved.getActionCount());
   }

   @Test
   void saveTask_orgAdminAddsActionAndChangesOwnerToExistingUser_isAllowed() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();

      ScheduleTask saved = savedTask(
         contentModel(options("bob", null), List.of("child", "other"), List.of(90)));

      assertEquals(BOB, saved.getOwner());
      assertEquals(2, saved.getActionCount());
   }

   @Test
   void saveTask_siteAdminAddsActionToSiteAdminTask_isAllowed() throws Exception {
      siteAdminTask(true);
      asOrgAdminWithAdminOnEveryUser();
      when(organizationManager.isSiteAdmin(principal)).thenReturn(true);

      ScheduleTask saved = savedTask(
         contentModel(options("admin", null), List.of("child", "other"), List.of(90, 120)));

      assertEquals(2, saved.getActionCount());
      assertEquals(2, saved.getConditionCount());
   }

   @Test
   void saveTask_orgAdminAddsActionToTaskOfOwnerWithoutSiteAdmin_isAllowed() throws Exception {
      // no site admin is named like the owner, the task runs with the roles of the owner
      siteAdminTask(false);
      asOrgAdminWithAdminOnEveryUser();

      ScheduleTask saved = savedTask(
         contentModel(options("admin", null), List.of("child", "other"), List.of(90, 120)));

      assertEquals(2, saved.getActionCount());
   }

   /**
    * A stored task owned by MISSING_OWNER without an execute-as identity, with an action that
    * runs task "child" and a condition at 1:30.
    */
   private ScheduleTask siteAdminTask(boolean runsAsSiteAdmin) throws Exception {
      ScheduleTask existing = storedTask(MISSING_OWNER, null);
      existing.addAction(batchAction("child"));
      existing.addCondition(TimeCondition.at(1, 30, 0));

      if(runsAsSiteAdmin) {
         runsAsSiteAdmin(MISSING_OWNER);
      }

      // the task editor models: one per task a batch action runs, one per condition time
      when(scheduleService.getActionModel(any(), any(), anyBoolean())).thenAnswer(inv ->
         actionModel(((BatchAction) inv.getArgument(0)).getTaskId()));
      when(scheduleService.getActionFromModel(any(), any(), any(), any())).thenAnswer(inv ->
         batchAction(keyOf(actionModels, inv.getArgument(0))));
      when(scheduleService.getActionFromModel(any(), any(), any(), any(), any())).thenAnswer(inv ->
         batchAction(keyOf(actionModels, inv.getArgument(0))));
      when(scheduleConditionService.getConditionModel(any(), any())).thenAnswer(inv -> {
         TimeCondition condition = inv.getArgument(0);
         return conditionModel(condition.getHour() * 60 + condition.getMinute());
      });
      when(scheduleConditionService.getConditionFromModel(any())).thenAnswer(inv ->
         timeCondition(inv.getArgument(0)));
      when(scheduleService.setTaskCondition(any(), anyInt(), any(), any(), any()))
         .thenAnswer(inv -> {
            TimeCondition condition = timeCondition(inv.getArgument(2));
            ScheduleTask task = inv.getArgument(4);
            int index = inv.getArgument(1);

            if(index < 0) {
               task.addCondition(condition);
            }
            else {
               task.setCondition(index, condition);
            }

            return null;
         });

      return existing;
   }

   private void runsAsSiteAdmin(IdentityID owner) {
      sutilStatic.when(() -> SUtil.getSameNameSiteAdmin(any(), eq(owner))).thenReturn(SITE_ADMIN);
   }

   private ScheduleTaskEditorModel contentModel(TaskOptionsPaneModel options,
                                                List<String> actions, List<Integer> conditions)
   {
      return ScheduleTaskEditorModel.builder()
         .from(model(null, options))
         .actions(actions.stream().map(this::actionModel).toList())
         .conditions(conditions.stream().map(this::conditionModel).toList())
         .build();
   }

   private void assertContentRefused(ScheduleTaskEditorModel model, boolean em)
      throws Exception
   {
      MessageException e = assertThrows(
         MessageException.class, () -> service.saveTask(model, "", principal, em));
      assertTrue(e.getMessage().contains("Execute As"), e.getMessage());
      // refused before anything is written, a rename included
      verify(scheduleService, never()).updateTaskName(any(), any(), any(), any());
      verify(scheduleService, never()).saveTask(any(), any(), any());
   }

   private ScheduleTask savedTask(ScheduleTaskEditorModel model) throws Exception {
      runSaveIgnoringDownstreamFailures(model);
      ArgumentCaptor<ScheduleTask> saved = ArgumentCaptor.forClass(ScheduleTask.class);
      verify(scheduleService).saveTask(any(), saved.capture(), eq(principal));
      return saved.getValue();
   }

   private ScheduleActionModel actionModel(String taskId) {
      return actionModels.computeIfAbsent(taskId, k -> BatchActionModel.builder()
         .taskName(k).actionType("BatchAction").build());
   }

   private ScheduleConditionModel conditionModel(int minutes) {
      // the models are compared on their JSON, a completion condition model names the time
      return conditionModels.computeIfAbsent(String.valueOf(minutes), k ->
         CompletionConditionModel.builder().taskName(k).conditionType("CompletionCondition")
            .build());
   }

   private TimeCondition timeCondition(ScheduleConditionModel model) {
      int minutes = Integer.parseInt(keyOf(conditionModels, model));
      return TimeCondition.at(minutes / 60, minutes % 60, 0);
   }

   private static <T> String keyOf(Map<String, T> models, T model) {
      return models.entrySet().stream()
         .filter(e -> e.getValue() == model)
         .map(Map.Entry::getKey)
         .findFirst().orElseThrow();
   }

   private static BatchAction batchAction(String taskId) {
      BatchAction action = new BatchAction();
      action.setTaskId(taskId);
      return action;
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
