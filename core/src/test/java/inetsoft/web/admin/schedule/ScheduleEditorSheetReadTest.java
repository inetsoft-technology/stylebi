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

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.deploy.DeployService;
import inetsoft.web.admin.schedule.model.*;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78129: the portal task editor save ({@code POST /api/portal/schedule/save},
 * {@code ScheduleTaskService.saveTask}) took the sheet of a dashboard action and the query of a
 * batch action from the client JSON ({@code ScheduleService.getActionFromModel}) and stored
 * another same-organization user's private sheet. The save must be refused with a
 * {@code SecurityException}, which the portal maps to HTTP 403. Drives the real editor service,
 * the real model conversion, the real {@code ScheduleManager} bean and the real
 * {@code SecurityEngine} / {@code FileAuthenticationProvider} (SecurityTestDataBuilder).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  ScheduleEditorSheetReadTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleEditorSheetReadTest {
   private static final String ORG = "sesrorg";
   private static final String SCHEDULE_ROLE = "sesrSched";
   private static final String SITE_ADMIN_ROLE = "sesrSiteAdmin";
   private static final String LINK = "http://host/";
   private static final String PRIVATE_SHEET = "4^128^user0~;~" + ORG + "^vs1^" + ORG;
   private static final String PRIVATE_WS = "4^2^user0~;~" + ORG + "^ws1^" + ORG;

   private SecurityTestDataBuilder builder;
   private final List<String> taskNames = new ArrayList<>();
   private Principal savedContextPrincipal;
   private ScheduleTaskService service;

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
         .addOrg("sesr", ORG)
         .addRole(SCHEDULE_ROLE, ORG)
         .addSysAdminRole(SITE_ADMIN_ROLE, ORG)
         .addUser("ub1", ORG, "password")
         .addUser("user0", ORG, "password")
         .addUser("sadm", ORG, "password")
         .addUserToRole("ub1", SCHEDULE_ROLE, ORG)
         .addUserToRole("user0", SCHEDULE_ROLE, ORG)
         .addUserToRole("sadm", SCHEDULE_ROLE, ORG)
         .addUserToRole("sadm", SITE_ADMIN_ROLE, ORG)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG)
         .grantPermission(ResourceType.MY_DASHBOARDS, "*", ResourceAction.READ,
                          SCHEDULE_ROLE, Identity.ROLE, ORG);
      builder.setup();

      // pin the security state to the builder's providers (Bug #77346)
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
   void setUp() throws Exception {
      savedContextPrincipal = ThreadContext.getContextPrincipal();
      SecurityEngine securityEngine = SecurityEngine.getSecurity();
      ScheduleConditionService conditionService = new ScheduleConditionService();
      ScheduleService scheduleService = new ScheduleService(
         analyticRepository, scheduleManager, null, conditionService,
         securityEngine.getSecurityProvider(), mock(DeployService.class), null, null,
         securityEngine, mock(ScheduleTaskFolderService.class), null, null,
         mock(RenameTransformHandler.class));
      service = spy(new ScheduleTaskService(
         analyticRepository, scheduleManager, scheduleService, conditionService,
         securityEngine.getSecurityProvider(), null, securityEngine));
      doNothing().when(service).setTaskOptions(any(), any(), any());
      doReturn(null).when(service).getDialogModel(anyString(), any(), anyBoolean());
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(savedContextPrincipal);

      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
      map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      taskNames.clear();
   }

   // the reported case: ub1 saves a dashboard action naming user0's private viewsheet
   @Test
   void portalSave_otherUsersPrivateViewsheet_isRefused() throws Exception {
      String taskId = storeTask("SesrPortalVs", "ub1");
      SRPrincipal ub1 = principal("ub1");
      ScheduleTaskEditorModel model = editorModel(taskId, viewsheetActionJson(PRIVATE_SHEET));

      assertRefused(() -> as(ub1, () -> service.saveTask(model, LINK, ub1)));

      assertEquals(0, assertPersisted(taskId).getActionCount(), "nothing was saved");
   }

   // the same for a batch action query naming user0's private worksheet
   @Test
   void portalSave_otherUsersPrivateWorksheetQuery_isRefused() throws Exception {
      String taskId = storeTask("SesrPortalWs", "ub1");
      SRPrincipal ub1 = principal("ub1");
      ScheduleTaskEditorModel model = editorModel(taskId, batchQueryActionJson(PRIVATE_WS));

      assertRefused(() -> as(ub1, () -> service.saveTask(model, LINK, ub1)));

      assertEquals(0, assertPersisted(taskId).getActionCount(), "nothing was saved");
   }

   // control: user0 saves the same action with their own private viewsheet, then re-saves the
   // stored task unchanged
   @Test
   void portalSave_ownPrivateViewsheet_isSaved() throws Exception {
      String taskId = storeTask("SesrPortalOwn", "user0");
      SRPrincipal user0 = principal("user0");
      ScheduleTaskEditorModel model = editorModel(taskId, viewsheetActionJson(PRIVATE_SHEET));

      as(user0, () -> service.saveTask(model, LINK, user0));
      as(user0, () -> service.saveTask(model, LINK, user0));

      ScheduleTask stored = assertPersisted(taskId);
      assertEquals(1, stored.getActionCount());
      assertEquals(PRIVATE_SHEET, ((ViewsheetAction) stored.getAction(0)).getViewsheet());
   }

   private String storeTask(String name, String owner) throws Exception {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(new IdentityID(owner, ORG));
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      ThreadContext.setContextPrincipal(principal(owner));

      try {
         scheduleManager.setScheduleTask(task.getTaskId(), task, principal("sadm"));
      }
      finally {
         ThreadContext.setContextPrincipal(savedContextPrincipal);
      }

      assertNotNull(scheduleManager.getScheduleTask(task.getTaskId(), ORG), "test setup");
      return task.getTaskId();
   }

   // the dashboard action JSON the portal editor posts
   private static String viewsheetActionJson(String sheet) {
      return "{\"actionType\":\"ViewsheetAction\",\"actionClass\":\"GeneralActionModel\"," +
         "\"sheet\":\"" + sheet + "\"}";
   }

   // the batch action JSON the portal editor posts, with a query and a missing target task
   private static String batchQueryActionJson(String query) {
      return "{\"actionType\":\"BatchAction\",\"actionClass\":\"BatchActionModel\"," +
         "\"taskName\":\"ub1~;~" + ORG + ":SesrChild\",\"queryEnabled\":true," +
         "\"queryEntry\":{\"identifier\":\"" + query + "\"}," +
         "\"queryParameters\":[]}";
   }

   private ScheduleTaskEditorModel editorModel(String taskId, String actionJson)
      throws Exception
   {
      ScheduleActionModel action =
         new ObjectMapper().readValue(actionJson, ScheduleActionModel.class);
      return ScheduleTaskEditorModel.builder()
         .taskName(taskId)
         .oldTaskName(taskId)
         .options(mock(TaskOptionsPaneModel.class))
         // a task without a condition isn't loaded (ScheduleTask.parseXML)
         .addConditions(new ScheduleConditionService().getConditionModel(
            TimeCondition.at(1, 30, 0), principal("sadm")))
         .addActions(action)
         .build();
   }

   private ScheduleTask assertPersisted(String taskId) {
      ReflectionTestUtils.invokeMethod(scheduleManager.getOrgTaskMap(ORG), "clearCache");
      ScheduleTask stored = scheduleManager.getScheduleTask(taskId, ORG);
      assertNotNull(stored, "the stored task must not be lost");
      return stored;
   }

   // the sheet READ check refused it, not another save refusal
   private static void assertRefused(org.junit.jupiter.api.function.Executable executable) {
      inetsoft.sree.security.SecurityException ex =
         assertThrows(inetsoft.sree.security.SecurityException.class, executable);
      assertTrue(ex.getMessage().contains("isn't readable"), ex.getMessage());
   }

   private SRPrincipal principal(String user) {
      return builder.principalOf(user, ORG);
   }

   private static <T> T as(Principal principal, ThrowingCallable<T> call) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         return call.call();
      }
      catch(Exception | Error e) {
         throw e;
      }
      catch(Throwable e) {
         throw new RuntimeException(e);
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   @FunctionalInterface
   private interface ThrowingCallable<T> {
      T call() throws Throwable;
   }

   /** The editor save looks up the dependencies of the task (ScheduleService). */
   @Configuration
   static class Config {
      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }
}
