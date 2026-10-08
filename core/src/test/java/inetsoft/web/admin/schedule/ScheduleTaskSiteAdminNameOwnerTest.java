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

import inetsoft.sree.ClientInfo;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77309: a task whose owner is not a user runs with the roles of a site admin of the same
 * name in another organization (SUtil.getScheduleTaskOwnerPrincipal). A caller that is not a user
 * (an SSO user that isn't in the provider) and has the name of a site admin became the owner of
 * the tasks it created, so they ran as the site admin. Such a caller may no longer be the owner of
 * a task, only a site admin may store such an owner.
 *
 * Bug #77405: only a site admin may add or change the actions and conditions of such a task.
 *
 * Bug #77452: such a task runs with the organization administrator roles of the owner's
 * organization, never with the roles of the site admin, and without roles when the organization
 * has no organization administrator role that may be used.
 *
 * Uses the real SecurityEngine / FileAuthenticationProvider (SecurityTestDataBuilder) and the real
 * ScheduleManager bean; only SUtil.isMultiTenant() is stubbed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  ScheduleTaskSiteAdminNameOwnerTest.LocaleConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleTaskSiteAdminNameOwnerTest {
   private static final String ORG_A = "sanoorga";
   private static final String ORG_B = "sanoorgb";
   private static final String SCHEDULE_ROLE = "sanoSchedRoleB";
   // an org admin role of org B that inherits a system admin role, may never be used
   private static final String ELEVATED_ORG_ADMIN_ROLE = "sanoElevatedOrgAdminB";
   private static final String SYS_ADMIN_ROLE_B = "sanoSysAdminB";
   // an org admin role of org A, not used for an owner in org B
   private static final String ORG_ADMIN_ROLE_A = "sanoOrgAdminA";
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);
   // "sadm~;~sanoorgb" is not a user, sadm is a site admin of org A
   private static final IdentityID SADM_IN_B = new IdentityID("sadm", ORG_B);
   // "zed~;~sanoorgb" is not a user and no site admin is named zed
   private static final IdentityID ZED_IN_B = new IdentityID("zed", ORG_B);

   private SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   private MockedStatic<SUtil> sutilStatic;
   private SecurityProvider securityProvider;
   private ScheduleTaskIdentityChecker checker;
   private final List<String> taskNames = new ArrayList<>();

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("sanoOrgA", ORG_A)
         .addOrg("sanoOrgB", ORG_B)
         .addSysAdminRole("sanoSiteAdmin", ORG_A)
         .addRole(SCHEDULE_ROLE, ORG_B)
         .addSysAdminRole(SYS_ADMIN_ROLE_B, ORG_B)
         .addOrgAdminRole(ELEVATED_ORG_ADMIN_ROLE, ORG_B)
         .addRoleParent(ELEVATED_ORG_ADMIN_ROLE, SYS_ADMIN_ROLE_B, ORG_B)
         .addOrgAdminRole(ORG_ADMIN_ROLE_A, ORG_A)
         .addUser("sadm", ORG_A, "password")
         .addUser("carol", ORG_B, "password")
         .addUserToRole("sadm", "sanoSiteAdmin", ORG_A)
         .addUserToRole("carol", SCHEDULE_ROLE, ORG_B)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_B);
      // Bug #77972, carol's task may only batch a task carol may see, the leaf task of
      // nestedBatchChild_runsWithItsOwnPrincipal
      for(ResourceAction action : List.of(ResourceAction.READ, ResourceAction.WRITE,
                                          ResourceAction.DELETE))
      {
         builder.grantPermission(ResourceType.SCHEDULE_TASK, ZED_IN_B.convertToKey() +
                                 ":SanoBatchLeaf", action, "carol", Identity.USER, ORG_B);
      }

      builder.setup();
      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleIdentityRemovedMissingOwnerTest
      SecurityProvider provider = CompositeSecurityProvider.create(
         provider(), (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      assertNotNull(provider.getUser(new IdentityID("carol", ORG_B)), "security provider set up");
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
      securityProvider = provider;
      checker = new ScheduleTaskIdentityChecker(provider);
   }

   @AfterAll
   void teardownAll() {
      securityEngineOverrides.clear();

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      ScheduleClient scheduleClient =
         (ScheduleClient) ReflectionTestUtils.getField(scheduleManager, "scheduleClient");
      reset(scheduleClient);
      when(scheduleClient.isReady()).thenReturn(true);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      sutilStatic.close();

      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG_B);
      map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
   }

   // --- the shared predicate --------------------------------------------------------------------

   @Test
   void sameNameSiteAdmin_isFoundForAMissingOwnerOnly() {
      assertEquals(new IdentityID("sadm", ORG_A),
                   SUtil.getSameNameSiteAdmin(securityProvider, SADM_IN_B));
      assertNull(SUtil.getSameNameSiteAdmin(securityProvider, ZED_IN_B),
                 "no site admin named zed");
      assertNull(SUtil.getSameNameSiteAdmin(securityProvider, new IdentityID("carol", ORG_B)),
                 "an existing user runs with its own roles");
      assertNull(SUtil.getSameNameSiteAdmin(securityProvider, new IdentityID("sadm", ORG_A)),
                 "the site admin itself");
   }

   // --- Bug #77452, the roles a task owned by such a missing user runs with ---------------------

   @Test
   void ownerNamedLikeSiteAdmin_runsAsOrgAdminNotAsSiteAdmin() {
      SRPrincipal principal = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);
      OrganizationManager manager = OrganizationManager.getInstance();

      assertEquals(SADM_IN_B, IdentityID.getIdentityIDFromKey(principal.getName()), "the owner");
      assertEquals(ORG_B, principal.getOrgId(), "the owner's org");
      assertEquals(List.of(ORG_ADMIN_ROLE), Arrays.asList(principal.getRoles()),
                   "only the usable org admin role");
      assertFalse(manager.isSiteAdmin(principal), "not a site admin");
      assertTrue(manager.isOrgAdmin(principal), "an org admin of the owner's org");
      assertFalse(Arrays.stream(principal.getAllRoles(securityProvider))
                     .anyMatch(securityProvider::isSystemAdministratorRole),
                  "no inherited system admin role");
      assertTrue(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("carol", ORG_B).convertToKey(),
         ResourceAction.ADMIN), "administers the users of its org");
      assertFalse(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("sadm", ORG_A).convertToKey(),
         ResourceAction.ADMIN), "doesn't administer the site admin");
      assertFalse(securityProvider.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/general", ResourceAction.ACCESS),
                  "no site admin EM component");
   }

   @Test
   void orgAdminRoles_ofOtherOrgsOrInheritingSystemAdmin_areNotUsed() {
      List<IdentityID> roles =
         Arrays.asList(SUtil.getScheduleTaskOrgAdminRoles(securityProvider, ORG_B));
      IdentityID elevated = new IdentityID(ELEVATED_ORG_ADMIN_ROLE, ORG_B);

      assertTrue(securityProvider.isOrgAdministratorRole(elevated), "test setup: org admin role");
      assertTrue(Arrays.stream(securityProvider.getAllRoles(new IdentityID[] { elevated }))
                    .anyMatch(securityProvider::isSystemAdministratorRole),
                 "test setup: inherits a system admin role");
      assertEquals(List.of(ORG_ADMIN_ROLE), roles);
      assertFalse(roles.contains(elevated), "inherits a system admin role");
      assertFalse(roles.contains(new IdentityID(ORG_ADMIN_ROLE_A, ORG_A)), "another org's role");
      assertTrue(Arrays.asList(SUtil.getScheduleTaskOrgAdminRoles(securityProvider, ORG_A))
                    .contains(new IdentityID(ORG_ADMIN_ROLE_A, ORG_A)), "its own org's role");
   }

   @Test
   void ownerNamedLikeSiteAdmin_withoutOrgAdminRole_runsWithoutRoles() {
      // e.g. a database provider without organization administrator roles, or LDAP
      SecurityProvider noOrgAdmin = spy(securityProvider);
      doReturn(false).when(noOrgAdmin).isOrgAdministratorRole(any());
      securityEngineOverrides.setSecurityProvider(noOrgAdmin);

      try {
         assertNotNull(SUtil.getSameNameSiteAdmin(noOrgAdmin, SADM_IN_B),
                       "still the elevated case");
         assertEquals(0, SUtil.getScheduleTaskOrgAdminRoles(noOrgAdmin, ORG_B).length);

         SRPrincipal principal = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);

         assertEquals(SADM_IN_B, IdentityID.getIdentityIDFromKey(principal.getName()));
         assertEquals(0, principal.getRoles().length, "no roles, never the site admin's");
         assertFalse(OrganizationManager.getInstance().isSiteAdmin(principal), "not site admin");
         assertFalse(OrganizationManager.getInstance().isOrgAdmin(principal), "not org admin");
         assertFalse(noOrgAdmin.checkPermission(
            principal, ResourceType.SECURITY_USER, new IdentityID("sadm", ORG_A).convertToKey(),
            ResourceAction.ADMIN), "doesn't administer the site admin");
      }
      finally {
         securityEngineOverrides.setSecurityProvider(securityProvider);
      }
   }

   @Test
   void ownerWithoutSameNameSiteAdmin_isUnchanged() {
      SRPrincipal principal = SUtil.getScheduleTaskOwnerPrincipal(ZED_IN_B, null, false);

      assertEquals(0, principal.getRoles().length, "a missing owner has no roles");
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(principal));
      assertEquals(List.of(new IdentityID(SCHEDULE_ROLE, ORG_B)), Arrays.asList(
         SUtil.getScheduleTaskOwnerPrincipal(new IdentityID("carol", ORG_B), null, false)
            .getRoles()), "an existing owner runs with its own roles");
   }

   // Bug #77452 path (a): a batch action lent the principal of its task to the child task it
   // runs. The child of a task owned by a missing user named like a site admin must not get a
   // site admin's principal, nor the parent's org admin roles. The child is owned by a plain
   // user, who could change what it runs, so it runs as that user.
   @Test
   void batchChild_ofTaskOwnedLikeSiteAdmin_isNotRunAsSiteAdmin() throws Throwable {
      String childName = "SanoBatchChild";
      ScheduleTask child = new ScheduleTask(childName);
      child.setOwner(new IdentityID("carol", ORG_B));
      taskNames.add(childName);
      String key = ReflectionTestUtils.invokeMethod(
         scheduleManager, "getTaskIdentifier", child.getTaskId(), ORG_B);
      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG_B);
      map.put(key, child);

      BatchAction batch = new BatchAction();
      batch.setTaskId(child.getTaskId());
      batch.setEmbeddedParameters(List.of(Map.of("p", "v")));

      // the run copy of the child, records the principal the child task is run with
      java.util.concurrent.atomic.AtomicReference<java.security.Principal> received =
         new java.util.concurrent.atomic.AtomicReference<>();
      ScheduleTask childRun = spy(new ScheduleTask(childName));
      doAnswer(inv -> {
         received.set(inv.getArgument(0));
         return null;
      }).when(childRun).run(any());

      SRPrincipal parent = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(parent), "test setup: org admin");
      // as ScheduleTask.doRun, the current org of the action is the owner's org
      ThreadContext.setContextPrincipal(parent);

      try(MockedStatic<ScheduleTask> copy = mockStatic(ScheduleTask.class, inv ->
         "copyScheduleTask".equals(inv.getMethod().getName()) ? childRun : inv.callRealMethod()))
      {
         batch.run(parent);
      }

      assertSame(parent, ThreadContext.getContextPrincipal(), "context principal restored");
      java.security.Principal principal = received.get();
      assertNotNull(principal, "the child task was found and run");
      assertNotSame(parent, principal, "not the parent's principal");
      assertEquals(new IdentityID("carol", ORG_B),
                   IdentityID.getIdentityIDFromKey(principal.getName()), "the child's owner");
      assertEquals(List.of(new IdentityID(SCHEDULE_ROLE, ORG_B)),
                   Arrays.asList(((SRPrincipal) principal).getRoles()), "the owner's roles");
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(principal),
                  "the child doesn't run as an org admin");
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(principal),
                  "the child doesn't run as a site admin");
      assertFalse(Arrays.stream(((inetsoft.uql.XPrincipal) principal).getAllRoles(securityProvider))
                     .anyMatch(securityProvider::isSystemAdministratorRole),
                  "no inherited system admin role");
      assertFalse(securityProvider.checkPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("sadm", ORG_A).convertToKey(),
         ResourceAction.ADMIN), "doesn't administer the site admin");
      assertFalse(securityProvider.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/general", ResourceAction.ACCESS),
                  "no site admin EM component");
   }

   // Bug #77452 Option 1: a child task that runs as a plain user (execute-as) gets that user's
   // principal, not the org admin principal of the task that holds the batch action, nor the
   // principal of its owner (zed, no roles). The child task is run for real, the principal is
   // recorded by a schedule task listener.
   @Test
   void batchChild_withExecuteAsUser_runsAsThatUserNotAsParent() throws Throwable {
      IdentityID carol = new IdentityID("carol", ORG_B);
      ScheduleTask child = registerTask("SanoBatchExecAs", ZED_IN_B, null,
                                        task -> task.setIdentity(new User(carol)));
      SRPrincipal parent = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(parent), "test setup: org admin");

      List<RecordingListener.Run> runs = runBatch(child, parent);

      assertEquals(1, runs.size(), "the child ran once");
      java.security.Principal principal = runs.get(0).principal();
      assertEquals("SanoBatchExecAs", runs.get(0).task());
      assertNotSame(parent, principal);
      assertEquals(carol, IdentityID.getIdentityIDFromKey(principal.getName()));
      assertTrue(Arrays.asList(((inetsoft.uql.XPrincipal) principal).getRoles())
                    .contains(new IdentityID(SCHEDULE_ROLE, ORG_B)), "carol's role");
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(principal), "not org admin");
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(principal), "not site admin");
      assertSame(principal, runs.get(0).contextPrincipal(),
                 "the child's principal is the context principal while it runs");
   }

   // Bug #77452 Option 1: a batch child runs with its own task locale, as when it is run directly
   // (SUtil.runTask), not with the locale of the task that holds the batch action.
   @Test
   void batchChild_runsWithItsOwnTaskLocale() throws Throwable {
      String available = inetsoft.sree.SreeEnv.getProperty("locale.available");
      inetsoft.sree.SreeEnv.setProperty("locale.available", "de_DE:fr_FR");

      try {
         ScheduleTask child = registerTask("SanoBatchLocale", new IdentityID("carol", ORG_B),
                                           null, task -> task.setLocale("de_DE"));
         SRPrincipal parent = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);
         // the parent task runs with its own locale (SUtil.runTask)
         SUtil.applyScheduleTaskLocale(parent, "fr_FR");
         assertEquals("fr_FR", parent.getProperty(SRPrincipal.LOCALE), "test setup");

         List<RecordingListener.Run> runs = runBatch(child, parent);

         assertEquals(1, runs.size(), "the child ran once");
         assertEquals("de_DE", ((SRPrincipal) runs.get(0).principal())
            .getProperty(SRPrincipal.LOCALE), "the child task's locale");
         assertEquals("fr_FR", parent.getProperty(SRPrincipal.LOCALE),
                      "the parent's locale is unchanged");
      }
      finally {
         inetsoft.sree.SreeEnv.setProperty("locale.available", available);
      }
   }

   // Bug #77452 Option 1: each level of nested batch actions runs its child with that child's
   // own principal. parent (org admin) -> batch -> mid (carol, holds a batch) -> leaf (owned by
   // zed, a missing user with no site admin of the same name, so no roles). The mid task's
   // batch action runs in the schedule thread pool. carol may see the leaf task (Bug #77972).
   @Test
   void nestedBatchChild_runsWithItsOwnPrincipal() throws Throwable {
      IdentityID carol = new IdentityID("carol", ORG_B);
      ScheduleTask leaf = registerTask("SanoBatchLeaf", ZED_IN_B, null);
      BatchAction leafBatch = new BatchAction();
      leafBatch.setTaskId(leaf.getTaskId());
      leafBatch.setEmbeddedParameters(List.of(Map.of("p", "v")));
      ScheduleTask mid = registerTask("SanoBatchMid", carol, leafBatch);
      SRPrincipal parent = SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false);

      List<RecordingListener.Run> runs = runBatch(mid, parent);

      assertEquals(List.of("SanoBatchMid", "SanoBatchLeaf"),
                   runs.stream().map(RecordingListener.Run::task).toList(), "both levels ran");
      assertEquals(carol, IdentityID.getIdentityIDFromKey(runs.get(0).principal().getName()),
                   "the mid task runs as its owner");
      java.security.Principal principal = runs.get(1).principal();
      assertEquals(ZED_IN_B, IdentityID.getIdentityIDFromKey(principal.getName()),
                   "the leaf runs as its owner, not as the mid task's or the parent's");
      assertEquals(0, ((inetsoft.uql.XPrincipal) principal).getRoles().length, "no roles");
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(principal), "not org admin");
      assertSame(principal, runs.get(1).contextPrincipal(), "the leaf's context principal");
   }

   private ScheduleTask registerTask(String name, IdentityID owner, ScheduleAction action) {
      return registerTask(name, owner, action, task -> { });
   }

   /**
    * Stores a task in the org task map, the setup is applied before it is stored.
    */
   private ScheduleTask registerTask(String name, IdentityID owner, ScheduleAction action,
                                     java.util.function.Consumer<ScheduleTask> setup)
   {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      task.addCondition(TimeCondition.at(1, 30, 0));

      if(action != null) {
         task.addAction(action);
      }

      setup.accept(task);

      taskNames.add(name);
      String key = ReflectionTestUtils.invokeMethod(
         scheduleManager, "getTaskIdentifier", task.getTaskId(), ORG_B);
      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG_B);
      map.put(key, task);
      return task;
   }

   /**
    * Runs a batch action for the child task with the parent principal, and returns the runs of
    * the tasks it started, as seen by a schedule task listener (ScheduleTask.doRun).
    */
   private List<RecordingListener.Run> runBatch(ScheduleTask child, SRPrincipal parent)
      throws Throwable
   {
      BatchAction batch = new BatchAction();
      batch.setTaskId(child.getTaskId());
      batch.setEmbeddedParameters(List.of(Map.of("p", "v")));
      RecordingListener.RUNS.clear();
      String listener = inetsoft.sree.SreeEnv.getProperty("schedule.task.listener");
      inetsoft.sree.SreeEnv.setProperty("schedule.task.listener",
                                        RecordingListener.class.getName());
      // as ScheduleTask.doRun, the context principal of the action is the parent's
      ThreadContext.setContextPrincipal(parent);

      try {
         batch.run(parent);
      }
      finally {
         inetsoft.sree.SreeEnv.setProperty("schedule.task.listener", listener);
      }

      assertSame(parent, ThreadContext.getContextPrincipal(), "context principal restored");
      return new ArrayList<>(RecordingListener.RUNS);
   }

   /** Records the principal each schedule task is started with. */
   public static final class RecordingListener implements TaskListener {
      record Run(String task, java.security.Principal principal,
                 java.security.Principal contextPrincipal)
      {
      }

      @Override
      public void taskStarted(ScheduleTask task, java.security.Principal user) {
         RUNS.add(new Run(task.getName(), user, ThreadContext.getContextPrincipal()));
      }

      @Override
      public void taskCompleted(ScheduleTask task, java.security.Principal user,
                                List<Throwable> exceptions)
      {
      }

      static final List<Run> RUNS = Collections.synchronizedList(new ArrayList<>());
   }

   // --- ScheduleTaskIdentityChecker -------------------------------------------------------------

   @Test
   void ssoCallerNamedLikeSiteAdmin_mayNotOwnATask() {
      SRPrincipal caller = ssoPrincipal(SADM_IN_B);

      assertFalse(OrganizationManager.getInstance().isSiteAdmin(caller), "not a site admin");
      assertFalse(checker.isNewTaskOwnerAllowed(SADM_IN_B, caller), "create");
      assertFalse(checker.isOwnerAllowed(SADM_IN_B, caller), "owner change / import / API");
      assertFalse(checker.isRunAsOwnerAllowed(null, null, SADM_IN_B, null, caller),
                  "a new task that runs as its owner");
   }

   @Test
   void ssoCallerWithOtherName_mayOwnATask() {
      SRPrincipal caller = ssoPrincipal(ZED_IN_B);

      assertTrue(checker.isNewTaskOwnerAllowed(ZED_IN_B, caller));
      assertTrue(checker.isOwnerAllowed(ZED_IN_B, caller));
      assertTrue(checker.isRunAsOwnerAllowed(null, null, ZED_IN_B, null, caller));
   }

   @Test
   void existingUser_mayOwnATask() {
      IdentityID carol = new IdentityID("carol", ORG_B);
      SRPrincipal caller = builder.principalOf("carol", ORG_B);

      assertTrue(checker.isNewTaskOwnerAllowed(carol, caller));
      assertTrue(checker.isOwnerAllowed(carol, caller));
   }

   @Test
   void siteAdmin_mayCreateATaskOwnedByItsNameInAnotherOrg() {
      // SUtil.getOwnerForNewTask, a site admin creating a task in an organization without admin
      SRPrincipal siteAdmin = builder.principalOf("sadm", ORG_A);

      assertTrue(OrganizationManager.getInstance().isSiteAdmin(siteAdmin));
      assertTrue(checker.isNewTaskOwnerAllowed(SADM_IN_B, siteAdmin));
      assertTrue(checker.isOwnerAllowed(SADM_IN_B, siteAdmin));
   }

   // --- Bug #77405, the actions and conditions of a task that runs as a site admin ---------------

   @Test
   void contentChange_ofTaskRunningAsSiteAdmin_isOnlyAllowedForSiteAdmin() {
      IdentityID carol = new IdentityID("carol", ORG_B);
      SRPrincipal orgUser = builder.principalOf("carol", ORG_B);
      SRPrincipal siteAdmin = builder.principalOf("sadm", ORG_A);

      assertTrue(checker.runsWithSiteAdminRoles(SADM_IN_B, null), "the run path predicate");
      assertFalse(checker.isContentChangeAllowed(SADM_IN_B, null, orgUser));
      assertTrue(checker.isContentChangeAllowed(SADM_IN_B, null, siteAdmin), "site admin");
      assertTrue(checker.isContentChangeAllowed(SADM_IN_B, new User(carol), orgUser),
                 "runs as its execute-as identity");
      assertTrue(checker.isContentChangeAllowed(carol, null, orgUser), "an existing owner");
      assertTrue(checker.isContentChangeAllowed(ZED_IN_B, null, orgUser),
                 "no site admin named zed");
   }

   @Test
   void keptOrRemoved_comparesEachSavedItemWithAStoredItem() {
      assertTrue(ScheduleTaskIdentityChecker.isKeptOrRemoved(
         List.of("a", "b"), List.of("b", "a"), Objects::equals), "reordered");
      assertTrue(ScheduleTaskIdentityChecker.isKeptOrRemoved(
         List.of("a", "b"), List.of("b"), Objects::equals), "removed");
      assertFalse(ScheduleTaskIdentityChecker.isKeptOrRemoved(
         List.of("a", "b"), List.of("a", "c"), Objects::equals), "changed");
      assertFalse(ScheduleTaskIdentityChecker.isKeptOrRemoved(
         List.of("a"), List.of("a", "a"), Objects::equals), "added a copy");
   }

   // --- ScheduleManager, a task without owner gets the caller as owner --------------------------

   @Test
   void ownerlessTask_savedByCallerNamedLikeSiteAdmin_isRefused() {
      SRPrincipal caller = ssoPrincipal(SADM_IN_B);
      ScheduleTask task = newTask("SanoRefused");

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> scheduleManager.setScheduleTask(task.getName(), task, caller));
      assertNull(scheduleManager.getScheduleTask(SADM_IN_B.convertToKey() + ":SanoRefused",
                                                 ORG_B), "not stored");
   }

   @Test
   void ownerlessTask_savedByCallerWithOtherName_isOwnedByTheCaller() throws Exception {
      SRPrincipal caller = ssoPrincipal(ZED_IN_B);
      ScheduleTask task = newTask("SanoAllowed");

      scheduleManager.setScheduleTask(task.getName(), task, caller);

      ScheduleTask stored =
         scheduleManager.getScheduleTask(ZED_IN_B.convertToKey() + ":SanoAllowed", ORG_B);
      assertNotNull(stored, "stored");
      assertEquals(ZED_IN_B, stored.getOwner());
   }

   private ScheduleTask newTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }

   /**
    * A principal the SSO filters build from the IdP claims: the user is not in the provider, the
    * roles come from the claims.
    */
   private SRPrincipal ssoPrincipal(IdentityID user) {
      SRPrincipal principal = new SRPrincipal(
         user, new IdentityID[] { new IdentityID(SCHEDULE_ROLE, ORG_B) }, new String[0], ORG_B,
         1L);
      // registered as logged in, the same as SecurityTestDataBuilder.principalOf
      @SuppressWarnings("unchecked")
      Map<ClientInfo, SRPrincipal> sessionUsers = (Map<ClientInfo, SRPrincipal>)
         ReflectionTestUtils.getField(SecurityEngine.getSecurity(), "users");

      if(sessionUsers != null) {
         sessionUsers.put(principal.getUser().getCacheKey(), principal);
      }

      return principal;
   }

   /**
    * The locale service the schedule task run uses (SUtil.applyScheduleTaskLocale), not in
    * BaseTestConfiguration.
    */
   @org.springframework.context.annotation.Configuration
   static class LocaleConfiguration {
      @org.springframework.context.annotation.Bean
      LocaleService localeService(SecurityEngine securityEngine) {
         return new LocaleService(securityEngine);
      }
   }

   private FileAuthenticationProvider provider() {
      return (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
   }
}
