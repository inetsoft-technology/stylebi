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
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.BatchActionModel;
import inetsoft.web.admin.schedule.model.TaskOptionsPaneModel;
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
 * Testing #79042, layer 1: the checks a schedule task save makes on what the task refers to
 * and on its owner, against the permission policy ({@code schedule-task-permission-policy.json})
 * for every actor and both settings.
 *
 * <p>Each check runs as the actor on the owner's task and is compared with the policy rule it
 * implements: the references the save checks (dashboard sheets, batch action targets and
 * queries, internal targets, rules B1-B3) and the owner checks (naming the owner on import, C1 /
 * C2; making oneself the owner in the editor, C3). The gate that decides who may edit the task
 * at all is covered by {@link SchedulePermissionHelperMatrixTest}. Differences from the policy are
 * listed in {@code schedule-task-save-permission-known-differences.txt}; the test fails when a
 * new one appears and when a listed one goes away.</p>
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  SchedulePermissionSaveMatrixTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SchedulePermissionSaveMatrixTest {
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
         .addOrg("spsOrg", ORG)
         .addOrg("spsOrgB", ORG_B)
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
         .grantPermission(ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.REPORT, GRANTED_PATH, ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.REPORT, GRANTED_PATH, ResourceAction.READ,
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
      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);

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
      scheduleService = new ScheduleService(
         analyticRepository, scheduleManager, null, conditionService,
         securityEngine.getSecurityProvider(), mock(DeployService.class), null, null,
         securityEngine, mock(ScheduleTaskFolderService.class), null, null,
         mock(RenameTransformHandler.class));
      service = new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, conditionService,
         securityEngine.getSecurityProvider(), null, securityEngine);
      identityChecker = new ScheduleTaskIdentityChecker(securityEngine);
      storeTask(TASK, OWNER);
      storeTask(OWN_TARGET, OWNER);
      storeTask(OTHER_TARGET, OTHER);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedPrincipal);
      taskMap().values().removeIf(t -> t != null && TASKS.contains(t.getName()));
   }

   /**
    * @param profile {@code share}/{@code deleteByOwner} settings, e.g. "true/true".
    */
   @ParameterizedTest(name = "shareTaskInGroup/deleteTaskOnlyByOwner = {0}")
   @ValueSource(strings = { "true/true", "true/false", "false/true", "false/false" })
   void saveChecksAgreeWithThePolicy(String profile) throws Exception {
      String[] settings = profile.split("/");
      SreeEnv.setProperty(SHARE, settings[0]);
      SreeEnv.setProperty(DELETE_BY_OWNER, settings[1]);

      Set<String> differences = new TreeSet<>();

      for(Actor actor : ACTORS) {
         Map<String, Boolean> values = new HashMap<>(actor.attributes());
         values.put("share", Boolean.parseBoolean(settings[0]));
         values.put("deleteOnlyByOwner", Boolean.parseBoolean(settings[1]));
         SRPrincipal principal = builder.principalOf(actor.user(), actor.org());

         for(Check check : Check.values()) {
            boolean expected = policy.allows(check.rule, values);
            // a request thread carries its principal, and the checks resolve the org from it
            ThreadContext.setContextPrincipal(principal);
            boolean actual;

            try {
               actual = check.allows(this, principal);
            }
            finally {
               ThreadContext.setContextPrincipal(savedPrincipal);
            }

            if(actual != expected) {
               differences.add(check.name() + " " + actor.id() + " " + profile + " " +
                               (actual ? "allows" : "refuses"));
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

   /** The checks under test, each with the policy rule it implements. */
   private enum Check {
      // dashboard action sheets (B2)
      SHEET_GRANTED("referenceReadable") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, viewsheetAction(sheet(ORG, GRANTED_PATH)));
         }
      },
      SHEET_NOT_GRANTED("never") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, viewsheetAction(sheet(ORG, "Spm/Hidden")));
         }
      },
      SHEET_OTHER_USER_PRIVATE("never") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, viewsheetAction(privateSheet(OTHER)));
         }
      },
      SHEET_OWNER_PRIVATE("ownerPrivateReference") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, viewsheetAction(privateSheet(OWNER)));
         }
      },
      SHEET_FOREIGN_ORG("never") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, viewsheetAction(sheet(ORG_B, GRANTED_PATH)));
         }
      },
      // batch action targets and queries (B3)
      BATCH_OWN_TARGET("mayView") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, batchAction(taskId(OWNER, OWN_TARGET)));
         }
      },
      BATCH_OTHER_USER_TARGET("never") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.saveWith(principal, batchAction(taskId(OTHER, OTHER_TARGET)));
         }
      },
      BATCH_FOREIGN_QUERY("never") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            BatchAction action = new BatchAction();
            action.setQueryEntry(AssetEntry.createAssetEntry(
               "1^2^__NULL__^" + GRANTED_PATH + "^" + ORG_B));
            return test.saveWith(principal, action);
         }
      },
      // the internal-target rule is applied as the editor's action model is read (#77531)
      BATCH_INTERNAL_TARGET("siteAdminOnly") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            BatchActionModel model = BatchActionModel.builder()
               .actionType("BatchAction")
               .actionClass("BatchActionModel")
               .taskName(INTERNAL_TASK)
               .build();

            try {
               return test.scheduleService.getActionFromModel(
                  model, null, List.of(), principal, null) != null;
            }
            catch(Exception refused) {
               return false;
            }
         }
      },
      // naming the task's owner, e.g. an import that overwrites the task (C1, C2)
      ASSIGN_OWNER("mayAssignOwner") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            return test.identityChecker.isOwnerAllowed(OWNER, principal);
         }
      },
      // the actor makes itself the owner of the task in the editor (C3)
      TAKE_OWNERSHIP("mayTakeOwnership") {
         @Override
         boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal) {
            TaskOptionsPaneModel options = TaskOptionsPaneModel.builder()
               .enabled(true)
               .deleteIfNotScheduledToRun(false)
               .securityEnabled(true)
               .owner(IdentityID.getIdentityIDFromKey(principal.getName()).getName())
               .build();

            try {
               ReflectionTestUtils.invokeMethod(test.service, "checkTaskIdentityPermission",
                                                options, test.task(), principal);
               return true;
            }
            // the refusal is a checked SecurityException, rethrown wrapped by the reflection
            catch(RuntimeException refused) {
               return false;
            }
         }
      };

      Check(String rule) {
         this.rule = rule;
      }

      abstract boolean allows(SchedulePermissionSaveMatrixTest test, SRPrincipal principal)
         throws Exception;

      final String rule;
   }

   private record Actor(String id, String user, String org, Map<String, Boolean> attributes) {
   }

   /**
    * Checks a save of the owner's task with an action added, as the actor; nothing is saved.
    */
   private boolean saveWith(Principal principal, ScheduleAction action) {
      ScheduleTask task = task().clone();
      task.addAction(action);

      try {
         scheduleManager.checkScheduleTaskSave(TASK_ID, task, principal);
         return true;
      }
      catch(Exception refused) {
         return false;
      }
   }

   private ScheduleTask task() {
      return scheduleManager.getScheduleTask(TASK_ID, ORG);
   }

   private void storeTask(String name, IdentityID owner) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(owner);
      task.addCondition(TimeCondition.at(1, 30, 0));
      String key = ReflectionTestUtils.invokeMethod(
         scheduleManager, "getTaskIdentifier", task.getTaskId(), ORG);
      taskMap().put(key, task);
   }

   @SuppressWarnings("unchecked")
   private Map<String, ScheduleTask> taskMap() {
      return (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
   }

   private static ViewsheetAction viewsheetAction(String sheet) {
      ViewsheetAction action = new ViewsheetAction();
      action.setViewsheet(sheet);
      return action;
   }

   private static BatchAction batchAction(String targetTaskId) {
      BatchAction action = new BatchAction();
      action.setTaskId(targetTaskId);
      return action;
   }

   private static String sheet(String org, String path) {
      return "1^128^__NULL__^" + path + "^" + org;
   }

   private static String privateSheet(IdentityID user) {
      return "4^128^" + user.convertToKey() + "^Private^" + user.getOrgID();
   }

   private static String taskId(IdentityID owner, String name) {
      return owner.convertToKey() + ":" + name;
   }

   private static Set<String> readKnownDifferences() throws IOException {
      Set<String> known = new TreeSet<>();

      try(InputStream in = SchedulePermissionSaveMatrixTest.class
         .getResourceAsStream("schedule-task-save-permission-known-differences.txt"))
      {
         if(in == null) {
            return known;
         }

         for(String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
            String entry = line.strip();

            // "<check> <actor> <profile> <answer> : <reason>"
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

   private static final String ORG = "spsorg";
   private static final String ORG_B = "spsorgb";
   private static final String SCHEDULE_ROLE = "spsSched";
   private static final String ORG_ADMIN_ROLE = "spsOrgAdmin";
   private static final String SITE_ADMIN_ROLE = "spsSiteAdmin";
   private static final String TEAM = "spsTeam";
   private static final String PASSWORD = "password";
   private static final String GRANTED_PATH = "Spm/Granted";
   private static final String INTERNAL_TASK = "__balance tasks__";
   private static final String TASK = "SpsTask";
   private static final String OWN_TARGET = "SpsOwnTarget";
   private static final String OTHER_TARGET = "SpsOtherTarget";
   private static final List<String> TASKS = List.of(TASK, OWN_TARGET, OTHER_TARGET);
   private static final IdentityID OWNER = new IdentityID("owner", ORG);
   private static final IdentityID OTHER = new IdentityID("other", ORG);
   private static final String TASK_ID = taskId(OWNER, TASK);
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
   private SchedulePermissionPolicy policy;
   private Set<String> knownDifferences;
   private ScheduleService scheduleService;
   private ScheduleTaskService service;
   private ScheduleTaskIdentityChecker identityChecker;
   private Principal savedPrincipal;
   private String shareInGroup;
   private String deleteByOwner;
}
