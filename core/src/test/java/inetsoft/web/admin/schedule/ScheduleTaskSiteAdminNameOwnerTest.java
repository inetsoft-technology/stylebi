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
 * Uses the real SecurityEngine / FileAuthenticationProvider (SecurityTestDataBuilder) and the real
 * ScheduleManager bean; only SUtil.isMultiTenant() is stubbed.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleTaskSiteAdminNameOwnerTest {
   private static final String ORG_A = "sanoorga";
   private static final String ORG_B = "sanoorgb";
   private static final String SCHEDULE_ROLE = "sanoSchedRoleB";
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
         .addUser("sadm", ORG_A, "password")
         .addUser("carol", ORG_B, "password")
         .addUserToRole("sadm", "sanoSiteAdmin", ORG_A)
         .addUserToRole("carol", SCHEDULE_ROLE, ORG_B)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_B);
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
   void sameNameSiteAdmin_isTheSiteAdminTheTaskRunsAs() {
      assertEquals(new IdentityID("sadm", ORG_A),
                   SUtil.getSameNameSiteAdmin(securityProvider, SADM_IN_B));
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(
         SUtil.getScheduleTaskOwnerPrincipal(SADM_IN_B, null, false)),
                 "a task owned by the missing user runs as the site admin");
      assertNull(SUtil.getSameNameSiteAdmin(securityProvider, ZED_IN_B),
                 "no site admin named zed");
      assertNull(SUtil.getSameNameSiteAdmin(securityProvider, new IdentityID("carol", ORG_B)),
                 "an existing user runs with its own roles");
      assertNull(SUtil.getSameNameSiteAdmin(securityProvider, new IdentityID("sadm", ORG_A)),
                 "the site admin itself");
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

   private FileAuthenticationProvider provider() {
      return (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
   }
}
