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

import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Bug #81249: every save of a task (including the one that moves it into a folder) replaced the
 * stored permission of the task with an owner-only one, so the explicit grants of the task were
 * lost. A save keeps the grants and adds the owner to them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  SchedulePermissionKeptOnSaveTest.DependencyConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulePermissionKeptOnSaveTest {
   private static final String ORG_A = "spkosorga";
   private static final String SCHEDULE_ROLE = "spkosSchedRole";
   private static final String USER = "spkosUser";
   private static final String USER2 = "spkosUser2";
   private static final String OTHER = "spkosOther";

   private SecurityTestDataBuilder builder;
   private final List<String> taskNames = new ArrayList<>();
   private Principal savedPrincipal;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("spkosA", ORG_A)
         .addRole(SCHEDULE_ROLE, ORG_A)
         .addUser(USER, ORG_A, "password")
         .addUser(USER2, ORG_A, "password")
         .addUser(OTHER, ORG_A, "password")
         .addUserToRole(USER, SCHEDULE_ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A);
      builder.setup();

      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
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
      savedPrincipal = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(caller());
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);

      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map = (Map<String, ScheduleTask>) (Object)
         scheduleManager.getOrgTaskMap(ORG_A);
      map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      taskNames.clear();
   }

   // a new task gets the owner-only permission
   @Test
   void newTask_grantsOnlyTheOwner() throws Exception {
      ScheduleTask task = newTask("SpkosNew", USER);

      scheduleManager.setScheduleTask(task.getTaskId(), task, caller());

      Permission perm = permission(task);
      assertNotNull(perm);

      for(ResourceAction action : actions()) {
         assertEquals(Set.of(USER), names(perm.getAllUserGrants(action)), action.toString());
      }

      assertTrue(perm.getAllRoleGrants(ResourceAction.READ).isEmpty());
   }

   // an ordinary save keeps the explicit grants of the task. A move into a folder saves the task
   // through the same method (ScheduleTaskFolderService.changeTaskFolder passes the folder as the
   // parent entry), so it needs no separate case
   @Test
   void save_keepsExplicitGrants() throws Exception {
      ScheduleTask task = newTask("SpkosKeep", USER);
      scheduleManager.setScheduleTask(task.getTaskId(), task, caller());
      storeExplicitPermission(task);

      scheduleManager.setScheduleTask(task.getTaskId(), task, caller());

      Permission perm = permission(task);
      assertEquals(Set.of(USER, OTHER), names(perm.getAllUserGrants(ResourceAction.READ)));
      assertEquals(Set.of(USER, OTHER), names(perm.getAllUserGrants(ResourceAction.WRITE)));
      assertEquals(Set.of(USER), names(perm.getAllUserGrants(ResourceAction.DELETE)));
      assertEquals(Set.of(SCHEDULE_ROLE), names(perm.getAllRoleGrants(ResourceAction.READ)));
      assertEquals(Set.of(SCHEDULE_ROLE), names(perm.getAllRoleGrants(ResourceAction.ACCESS)));
   }

   // an explicit permission without the owner gets the owner added and keeps its other grants (the
   // task id includes the owner, so an owner change is a save under a new id, which has no stored
   // permission yet)
   @Test
   void save_addsOwnerToExplicitPermissionWithoutIt() throws Exception {
      ScheduleTask task = newTask("SpkosOwner", USER2);
      Permission perm = new Permission();
      Set<Permission.PermissionIdentity> other =
         Set.of(new Permission.PermissionIdentity(OTHER, ORG_A));
      Set<Permission.PermissionIdentity> role =
         Set.of(new Permission.PermissionIdentity(SCHEDULE_ROLE, ORG_A));
      perm.setUserGrants(ResourceAction.READ, other);
      perm.setRoleGrants(ResourceAction.READ, role);
      SecurityEngine.getSecurity()
         .setPermission(ResourceType.SCHEDULE_TASK, task.getTaskId(), perm);

      scheduleManager.setScheduleTask(task.getTaskId(), task, caller());

      Permission saved = permission(task);

      for(ResourceAction action : actions()) {
         assertTrue(names(saved.getAllUserGrants(action)).contains(USER2), action.toString());
      }

      assertTrue(names(saved.getAllUserGrants(ResourceAction.READ)).contains(OTHER));
      assertEquals(Set.of(SCHEDULE_ROLE), names(saved.getAllRoleGrants(ResourceAction.READ)));
   }

   private void storeExplicitPermission(ScheduleTask task) {
      Permission perm = new Permission();
      Set<Permission.PermissionIdentity> owner =
         Set.of(new Permission.PermissionIdentity(USER, ORG_A));
      Set<Permission.PermissionIdentity> both = Set.of(
         new Permission.PermissionIdentity(USER, ORG_A),
         new Permission.PermissionIdentity(OTHER, ORG_A));
      Set<Permission.PermissionIdentity> role =
         Set.of(new Permission.PermissionIdentity(SCHEDULE_ROLE, ORG_A));
      perm.setUserGrants(ResourceAction.READ, both);
      perm.setUserGrants(ResourceAction.WRITE, both);
      perm.setUserGrants(ResourceAction.DELETE, owner);
      perm.setRoleGrants(ResourceAction.READ, role);
      perm.setRoleGrants(ResourceAction.ACCESS, role);
      SecurityEngine.getSecurity()
         .setPermission(ResourceType.SCHEDULE_TASK, task.getTaskId(), perm);
   }

   private Permission permission(ScheduleTask task) {
      return SecurityEngine.getSecurity()
         .getPermission(ResourceType.SCHEDULE_TASK, task.getTaskId());
   }

   private static ResourceAction[] actions() {
      return new ResourceAction[] { ResourceAction.READ, ResourceAction.WRITE,
                                    ResourceAction.DELETE };
   }

   private static Set<String> names(Set<Permission.PermissionIdentity> grants) {
      Set<String> result = new HashSet<>();
      grants.forEach(g -> result.add(g.getName()));
      return result;
   }

   private SRPrincipal caller() {
      return builder.principalOf(USER, ORG_A);
   }

   private ScheduleTask newTask(String name, String owner) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(IdentityID.getIdentityIDFromKey(builder.principalOf(owner, ORG_A).getName()));
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }

   @Configuration
   static class DependencyConfiguration {
      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }
}
