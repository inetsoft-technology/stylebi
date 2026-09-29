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

import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
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

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77332: removing the "execute as" user, group or role of a task cleared it, so the task ran
 * as its owner. An owner that is not a user (a task a site admin created while switched into
 * another org, Bug #70087) runs with the roles of the same-named site admin
 * (SUtil.getScheduleTaskOwnerPrincipal), so deleting the execute-as made the task run as a site
 * admin. The removed identity must be kept for such a task so ScheduleTask.run refuses it.
 *
 * Uses the real SecurityEngine / FileAuthenticationProvider (SecurityTestDataBuilder) and the real
 * ScheduleManager bean; only SUtil.isMultiTenant() is stubbed. The run principal is built the way
 * ScheduleTaskJob.execute builds it. The provider is set up once the Spring context exists
 * (PER_CLASS lifecycle), so it is installed in the SecurityEngine that ScheduleManager uses.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleIdentityRemovedMissingOwnerTest {
   private static final String ORG_A = "idrmorga";
   private static final String ORG_B = "idrmorgb";
   // "sadm~;~idrmorgb" is not a user, sadm is a site admin of org A
   private static final IdentityID SADM_IN_B = new IdentityID("sadm", ORG_B);
   private static final IdentityID DAVE = new IdentityID("dave", ORG_B);
   private static final IdentityID SYSTEM_IN_B = new IdentityID(XPrincipal.SYSTEM, ORG_B);

   private SecurityTestDataBuilder builder;

   @Autowired
   ScheduleManager scheduleManager;

   private MockedStatic<SUtil> sutilStatic;
   private SRPrincipal dave;    // org admin of org B
   private final List<String> taskNames = new ArrayList<>();

   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("idrmOrgA", ORG_A)
         .addOrg("idrmOrgB", ORG_B)
         .addOrgAdminRole("idrmOrgAdminB", ORG_B)
         .addSysAdminRole("idrmSiteAdmin", ORG_A)
         .addUser("sadm", ORG_A, "password")
         .addUser("dave", ORG_B, "password")
         .addUser("erin1", ORG_B, "password")
         .addUser("erin2", ORG_B, "password")
         .addUser("erin3", ORG_B, "password")
         .addUser("erin4", ORG_B, "password")
         .addGroup("idrmGroupB", ORG_B)
         .addRole("idrmRoleB", ORG_B)
         .addRole("idrmRoleB2", ORG_B)
         .addGlobalRole("idrmGlobalRole")
         .addUserToRole("sadm", "idrmSiteAdmin", ORG_A)
         .addUserToRole("dave", "idrmOrgAdminB", ORG_B);
      builder.setup();
      // pin the security state to the builder's providers: a properties reload scheduled by a
      // previous test class can otherwise reset security.enabled and rebuild the provider of the
      // shared engine
      SecurityEngine engine = SecurityEngine.getSecurity();
      SecurityProvider provider = CompositeSecurityProvider.create(
         provider(), (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      assertNotNull(provider.getUser(DAVE), "security provider set up");
      doReturn(true).when(engine).isSecurityEnabled();
      doReturn(provider).when(engine).getSecurityProvider();
   }

   @AfterAll
   void teardownAll() {
      // back to a plain spy (the engine is a Spring-singleton spy, see BaseTestConfiguration)
      reset(SecurityEngine.getSecurity());

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      dave = builder.principalOf("dave", ORG_B);
      dave.setProperty("__internal__", "true");
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

   // --- owner is not a user: the removed identity is kept and the run is refused -------------

   @Test
   void userRemoved_ownerNotAUser_keepsExecuteAsAndRunIsRefused() throws Exception {
      User erin = provider().getUser(new IdentityID("erin1", ORG_B));
      ScheduleTask task = saveTask("JobUser", SADM_IN_B, erin);
      assertFalse(isSiteAdmin(runPrincipal(task)), "runs as erin before the delete");

      removeIdentity(erin);

      assertKeptAndRefused(task, erin);
   }

   @Test
   void groupRemoved_ownerNotAUser_keepsExecuteAsAndRunIsRefused() throws Exception {
      Group group = provider().getGroup(new IdentityID("idrmGroupB", ORG_B));
      ScheduleTask task = saveTask("JobGroup", SADM_IN_B, group);

      removeIdentity(group);

      assertKeptAndRefused(task, group);
   }

   @Test
   void orgRoleRemoved_ownerNotAUser_keepsExecuteAsAndRunIsRefused() throws Exception {
      Role role = provider().getRole(new IdentityID("idrmRoleB", ORG_B));
      ScheduleTask task = saveTask("JobRole", SADM_IN_B, role);

      removeIdentity(role);

      assertKeptAndRefused(task, role);
   }

   @Test
   void globalRoleRemoved_ownerNotAUser_keepsExecuteAsAndRunIsRefused() throws Exception {
      Role role = provider().getRole(new IdentityID("idrmGlobalRole", null));
      assertNotNull(role, "global role");
      ScheduleTask task = saveTask("JobGlobalRole", SADM_IN_B, role);

      removeIdentity(role);

      assertKeptAndRefused(task, role);
   }

   @Test
   void removalImpact_ownerNotAUser_reportedAsRefused() throws Exception {
      User erin = provider().getUser(new IdentityID("erin2", ORG_B));
      saveTask("JobImpactRefused", SADM_IN_B, erin);
      saveTask("JobImpactReset", DAVE, erin);

      ScheduleManager.IdentityTaskImpact impact =
         scheduleManager.getIdentityRemovalImpact(erin, provider());

      assertEquals(List.of("JobImpactReset"), impact.executeAsTasks());
      assertEquals(List.of("JobImpactRefused"), impact.refusedTasks());
      assertTrue(impact.ownedTasks().isEmpty());
   }

   // --- owner exists or task is a system task: reset to the owner, as before -----------------

   @Test
   void userRemoved_ownerExists_resetsToOwner() throws Exception {
      User erin = provider().getUser(new IdentityID("erin3", ORG_B));
      ScheduleTask task = saveTask("JobOwnerExists", DAVE, erin);

      removeIdentity(erin);

      assertNull(task.getIdentity(), "execute-as reset to the owner");
      Principal principal = runPrincipal(task);
      assertEquals(DAVE.convertToKey(), principal.getName());
      assertFalse(isSiteAdmin(principal));
   }

   @Test
   void roleRemoved_ownerExists_resetsToOwner() throws Exception {
      User erin = provider().getUser(new IdentityID("erin4", ORG_B));
      ScheduleTask task = saveTask("JobOwnerExistsKeep", DAVE, erin);
      Role role = provider().getRole(new IdentityID("idrmRoleB2", ORG_B));
      ScheduleTask roleTask = saveTask("JobRoleOwnerExists", DAVE, role);
      ScheduleTask systemTask = saveTask("JobSystem", SYSTEM_IN_B, role);

      removeIdentity(role);

      assertNull(roleTask.getIdentity(), "execute-as reset to the owner");
      assertNull(systemTask.getIdentity(), "a system task keeps running as before");
      assertEquals(erin.getIdentityID(), task.getIdentity().getIdentityID(),
                   "unrelated execute-as untouched");
   }

   private void assertKeptAndRefused(ScheduleTask task, Identity removed) {
      assertNotNull(task.getIdentity(), "execute-as not cleared");
      assertEquals(removed.getIdentityID(), task.getIdentity().getIdentityID());
      assertEquals(removed.getType(), task.getIdentity().getType());

      Principal principal = runPrincipal(task);
      assertFalse(isSiteAdmin(principal), "does not run with the site admin's roles");
      IllegalStateException ex = assertThrows(IllegalStateException.class, () -> task.run(principal));
      assertTrue(ex.getMessage().contains("cannot be resolved"), ex.getMessage());
   }

   /**
    * As IdentityService.deleteIdentities: the schedule is synced before the provider drops the
    * identity.
    */
   private void removeIdentity(Identity identity) {
      scheduleManager.identityRemoved(identity, provider());
      IdentityID id = identity.getIdentityID();

      switch(identity.getType()) {
         case Identity.USER -> provider().removeUser(id);
         case Identity.GROUP -> provider().removeGroup(id);
         case Identity.ROLE -> provider().removeRole(id);
         default -> fail("unexpected identity type");
      }
   }

   private ScheduleTask saveTask(String name, IdentityID owner, Identity executeAs)
      throws Exception
   {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      task.setIdentity(executeAs);
      taskNames.add(name);
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(dave);

      try {
         scheduleManager.save(List.of(task), ORG_B);
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }

      return scheduleManager.getScheduleTask(task.getTaskId(), ORG_B);
   }

   // ScheduleTaskJob.execute
   private static Principal runPrincipal(ScheduleTask task) {
      Identity identity = task.getIdentity();
      return identity == null ?
         SUtil.getScheduleTaskOwnerPrincipal(task.getOwner(), null, false) :
         SUtil.getPrincipal(identity, null, false);
   }

   private static boolean isSiteAdmin(Principal principal) {
      return principal != null && OrganizationManager.getInstance().isSiteAdmin(principal);
   }

   private FileAuthenticationProvider provider() {
      return (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
   }
}
