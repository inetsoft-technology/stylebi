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
import inetsoft.sree.RepletEngine;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.deploy.DeployService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Testing #79042, layer 1: the schedule task permission helpers against the permission policy
 * ({@code schedule-task-permission-policy.json}) for every actor and both settings.
 *
 * <p>Each helper is compared with the policy rule it implements, without the SCHEDULER gate,
 * which the entry points check (the e2e matrix covers that). Where the code differs from the
 * policy, the difference is listed in {@code schedule-task-permission-known-differences.txt}
 * with its reason: the test fails when a new difference appears and when a listed one goes
 * away, so a fix or a regression always shows up and the list stays true.</p>
 *
 * <p>Uses the real SecurityEngine (SecurityTestDataBuilder), the real ScheduleManager and its
 * storage, and the real ScheduleTaskService.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  SchedulePermissionHelperMatrixTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulePermissionHelperMatrixTest {
   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @Autowired
   AnalyticRepository analyticRepository;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("spmOrg", ORG)
         .addOrg("spmOrgB", ORG_B)
         .addRole(SCHEDULE_ROLE, ORG)
         .addRole(SCHEDULE_ROLE, ORG_B)
         .addOrgAdminRole(ORG_ADMIN_ROLE, ORG)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG)
         .addGroup(TEAM, ORG)
         .addUser("sadm", ORG, PASSWORD)
         .addUser("oadmin", ORG, PASSWORD)
         .addUser("owner", ORG, PASSWORD)
         .addUser("uadmin", ORG, PASSWORD)
         .addUser("mate", ORG, PASSWORD)
         .addUser("granted", ORG, PASSWORD)
         .addUser("other", ORG, PASSWORD)
         .addUser("foreign", ORG_B, PASSWORD)
         .addUserToRole("sadm", SITE_ADMIN_ROLE, ORG)
         .addUserToRole("oadmin", ORG_ADMIN_ROLE, ORG)
         .addUserToGroup("owner", TEAM, ORG)
         .addUserToGroup("mate", TEAM, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_B)
         .grantPermission(ResourceType.SECURITY_USER, OWNER.convertToKey(), ResourceAction.ADMIN,
                          "uadmin", Identity.USER, ORG);

      for(String user : List.of("sadm", "oadmin", "owner", "uadmin", "mate", "granted", "other")) {
         builder.addUserToRole(user, SCHEDULE_ROLE, ORG);
      }

      builder.addUserToRole("foreign", SCHEDULE_ROLE, ORG_B);

      // the owner's grants are the ones ScheduleManager.setScheduleTask writes; the explicit
      // grant holder gets the same three actions, which the policy says grant nothing (#79853)
      for(String user : List.of("owner", "granted")) {
         for(ResourceAction action : List.of(ResourceAction.READ, ResourceAction.WRITE,
                                             ResourceAction.DELETE))
         {
            builder.grantPermission(ResourceType.SCHEDULE_TASK, TASK_ID, action, user,
                                    Identity.USER, ORG);
         }
      }

      builder.setup();

      // pin the security state to the builder's providers (Bug #77346), see
      // ScheduleSaveRefusalKeepsTaskTest
      authzProvider = (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider");
      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         authzProvider);
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
      // removeScheduleTask clears the task's grants; a successful REMOVE puts them back
      taskPermission = authzProvider.getPermission(ResourceType.SCHEDULE_TASK, TASK_ID, ORG);

      shareInGroup = SreeEnv.getProperty(SHARE);
      deleteByOwner = SreeEnv.getProperty(DELETE_BY_OWNER);
      policy = SchedulePermissionPolicy.load();
      knownDifferences = readKnownDifferences();
   }

   @AfterAll
   void teardownAll() {
      SreeEnv.setProperty(SHARE, shareInGroup);
      SreeEnv.setProperty(DELETE_BY_OWNER, deleteByOwner);
      securityEngineOverrides.clear();

      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      savedPrincipal = ThreadContext.getContextPrincipal();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      ScheduleConditionService conditionService = new ScheduleConditionService();
      ScheduleService scheduleService = new ScheduleService(
         analyticRepository, scheduleManager, null, conditionService,
         securityEngine.getSecurityProvider(), mock(DeployService.class), null, null,
         securityEngine, mock(ScheduleTaskFolderService.class), null, null,
         mock(RenameTransformHandler.class));
      service = new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, conditionService,
         securityEngine.getSecurityProvider(), null, securityEngine);
      storeTask();
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      taskMap().values().removeIf(t -> t != null && TASK.equals(t.getName()));
   }

   /**
    * @param profile {@code share}/{@code deleteByOwner} settings, e.g. "true/true".
    */
   @ParameterizedTest(name = "shareTaskInGroup/deleteTaskOnlyByOwner = {0}")
   @ValueSource(strings = { "true/true", "true/false", "false/true", "false/false" })
   void permissionHelpersAgreeWithThePolicy(String profile) throws Exception {
      String[] settings = profile.split("/");
      SreeEnv.setProperty(SHARE, settings[0]);
      SreeEnv.setProperty(DELETE_BY_OWNER, settings[1]);

      Set<String> differences = new TreeSet<>();

      for(Actor actor : ACTORS) {
         Map<String, Boolean> values = new HashMap<>(actor.attributes());
         values.put("share", Boolean.parseBoolean(settings[0]));
         values.put("deleteOnlyByOwner", Boolean.parseBoolean(settings[1]));
         SRPrincipal principal = builder.principalOf(actor.user(), actor.org());

         for(Helper helper : Helper.values()) {
            boolean expected = policy.allows(helper.rule, values);
            // a request thread carries its principal, and the checks resolve the org from it
            ThreadContext.setContextPrincipal(principal);
            boolean actual;

            try {
               actual = helper.check(this, principal);
            }
            finally {
               ThreadContext.setContextPrincipal(savedPrincipal);
            }

            if(actual != expected) {
               differences.add(key(helper, actor, profile, actual));
            }
         }
      }

      Set<String> known = knownDifferences.stream()
         .filter(k -> k.contains(" " + profile + " "))
         .collect(Collectors.toCollection(TreeSet::new));
      Set<String> unexpected = new TreeSet<>(differences);
      unexpected.removeAll(known);
      Set<String> gone = new TreeSet<>(known);
      gone.removeAll(differences);

      assertTrue(unexpected.isEmpty() && gone.isEmpty(), () ->
         "differences from the policy changed for " + profile +
         "\n  new (fix the code, or list with a reason):\n    " + String.join("\n    ", unexpected) +
         "\n  no longer seen (remove from the list):\n    " + String.join("\n    ", gone));
   }

   // "<helper> <actor> <share>/<deleteByOwner> <actual answer>"
   private static String key(Helper helper, Actor actor, String profile, boolean actual) {
      return helper.name() + " " + actor.id() + " " + profile + " " + (actual ? "allows" : "refuses");
   }

   /** The helpers under test, each with the policy rule it implements. */
   private enum Helper {
      // "may see": task lists, export, the editor gate, batch targets
      MAY_SEE("mayView") {
         @Override
         boolean check(SchedulePermissionHelperMatrixTest test, SRPrincipal principal) {
            RepletEngine engine = SUtil.getRepletEngine(test.analyticRepository);
            return engine.hasTaskPermission(test.task(), principal);
         }
      },
      // EM / Portal run and stop
      RUN_STOP("mayView") {
         @Override
         boolean check(SchedulePermissionHelperMatrixTest test, SRPrincipal principal) {
            return ScheduleManager.hasTaskPermission(OWNER, principal, ResourceAction.READ);
         }
      },
      // EM / Portal delete, before removeScheduleTask
      DELETE_GATE("mayManage") {
         @Override
         boolean check(SchedulePermissionHelperMatrixTest test, SRPrincipal principal) {
            return ScheduleManager.hasTaskPermission(OWNER, principal, ResourceAction.DELETE);
         }
      },
      // EM / Portal task editor (open, save, rename)
      EDIT_GATE("mayManage") {
         @Override
         boolean check(SchedulePermissionHelperMatrixTest test, SRPrincipal principal) {
            return test.service.canDeleteTask(test.task(), principal);
         }
      },
      // the delete every entry point ends in
      REMOVE("mayManage") {
         @Override
         boolean check(SchedulePermissionHelperMatrixTest test, SRPrincipal principal) {
            try {
               test.scheduleManager.removeScheduleTask(TASK_ID, principal);
            }
            catch(Exception refused) {
               return false;
            }

            test.storeTask();
            test.authzProvider.setPermission(ResourceType.SCHEDULE_TASK, TASK_ID, test.taskPermission,
                                             ORG);
            return true;
         }
      };

      Helper(String rule) {
         this.rule = rule;
      }

      abstract boolean check(SchedulePermissionHelperMatrixTest test, SRPrincipal principal)
         throws Exception;

      final String rule;
   }

   private record Actor(String id, String user, String org, Map<String, Boolean> attributes) {
   }

   private ScheduleTask task() {
      return scheduleManager.getScheduleTask(TASK_ID, ORG);
   }

   // stored without the owner grants setScheduleTask would write; the builder holds them
   private void storeTask() {
      ScheduleTask task = new ScheduleTask(TASK);
      task.setOwner(OWNER);
      task.addCondition(TimeCondition.at(1, 30, 0));
      String key = ReflectionTestUtils.invokeMethod(
         scheduleManager, "getTaskIdentifier", task.getTaskId(), ORG);
      taskMap().put(key, task);
   }

   @SuppressWarnings("unchecked")
   private Map<String, ScheduleTask> taskMap() {
      return (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
   }

   private static Set<String> readKnownDifferences() throws IOException {
      Set<String> known = new TreeSet<>();

      try(InputStream in = SchedulePermissionHelperMatrixTest.class
         .getResourceAsStream("schedule-task-permission-known-differences.txt"))
      {
         if(in == null) {
            return known;
         }

         for(String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
            String entry = line.strip();

            // "<helper> <actor> <profile> <answer> : <reason>"
            if(!entry.isEmpty() && !entry.startsWith("#")) {
               known.add(entry.split(" : ", 2)[0].strip());
            }
         }
      }

      return known;
   }

   @Configuration
   static class Config {
      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }

      @Bean
      LocaleService localeService(SecurityEngine securityEngine) {
         return new LocaleService(securityEngine);
      }
   }

   private static final String ORG = "spmorg";
   private static final String ORG_B = "spmorgb";
   private static final String SCHEDULE_ROLE = "spmSched";
   private static final String ORG_ADMIN_ROLE = "spmOrgAdmin";
   private static final String SITE_ADMIN_ROLE = "spmSiteAdmin";
   private static final String TEAM = "spmTeam";
   private static final String PASSWORD = "password";
   private static final String TASK = "SpmTask";
   private static final IdentityID OWNER = new IdentityID("owner", ORG);
   private static final String TASK_ID = OWNER.convertToKey() + ":" + TASK;
   private static final String SHARE = "schedule.options.shareTaskInGroup";
   private static final String DELETE_BY_OWNER = "schedule.options.deleteTaskOnlyByOwner";

   private static final List<Actor> ACTORS = List.of(
      new Actor("siteAdmin", "sadm", ORG, Map.of("siteAdmin", true, "sameOrg", true)),
      new Actor("orgAdmin", "oadmin", ORG, Map.of("orgAdmin", true, "sameOrg", true)),
      new Actor("owner", "owner", ORG, Map.of("owner", true, "sameOrg", true)),
      new Actor("ownerAdmin", "uadmin", ORG, Map.of("ownerAdmin", true, "sameOrg", true)),
      new Actor("shareMember", "mate", ORG, Map.of("shareMember", true, "sameOrg", true)),
      new Actor("grantHolder", "granted", ORG, Map.of("grantHolder", true, "sameOrg", true)),
      new Actor("otherUser", "other", ORG, Map.of("sameOrg", true)),
      new Actor("foreignUser", "foreign", ORG_B, Map.of()));

   private SecurityTestDataBuilder builder;
   private AuthorizationProvider authzProvider;
   private Permission taskPermission;
   private SchedulePermissionPolicy policy;
   private Set<String> knownDifferences;
   private ScheduleTaskService service;
   private Principal savedPrincipal;
   private String shareInGroup;
   private String deleteByOwner;
}
