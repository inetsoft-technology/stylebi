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
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.schedule.model.ImportTaskResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78140: since #77798 a failed permission storage write throws. ScheduleManager.setScheduleTask
 * writes the owner's permission on the task after the task is stored, and only caught
 * SRSecurityException, so a failed write failed a save that had already stored the task, and the
 * EM task import stopped after that task: the request failed, the rest of the tasks weren't
 * imported. The write is best effort now and the import reports the task with a warning.
 *
 * <p>The failure is injected into the storage of every FileAuthorizationProvider the
 * SecurityEngine writes through (its own provider field, which SecurityEngineOverrides doesn't
 * redirect), so the real FileAuthorizationProvider builds the exception.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class,
                                  ScheduleOwnerPermissionWriteFailureTest.DependencyConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleOwnerPermissionWriteFailureTest {
   private static final String ORG_A = "sopwforga";
   private static final String SCHEDULE_ROLE = "sopwfSchedRole";
   private static final String USER = "sopwfUser";

   private SecurityTestDataBuilder builder;
   private final List<String> taskNames = new ArrayList<>();
   private final Map<FileAuthorizationProvider, KeyValueStorage<Permission>> originalStorages =
      new IdentityHashMap<>();
   // the keys of the permission writes that fail
   private volatile Predicate<String> failingKeys = key -> false;
   private Principal savedPrincipal;

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("sopwfA", ORG_A)
         .addRole(SCHEDULE_ROLE, ORG_A)
         .addUser(USER, ORG_A, "password")
         .addUserToRole(USER, SCHEDULE_ROLE, ORG_A)
         .grantPermission(ResourceType.SCHEDULER, "*", ResourceAction.ACCESS,
                          SCHEDULE_ROLE, Identity.ROLE, ORG_A);
      builder.setup();

      SecurityProvider provider = CompositeSecurityProvider.create(
         (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider"),
         (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
      securityEngineOverrides.setSecurityEnabled(true);
      securityEngineOverrides.setSecurityProvider(provider);
      injectFailingStorage();
   }

   @AfterAll
   void teardownAll() {
      originalStorages.forEach((authz, storage) ->
         ReflectionTestUtils.setField(authz, "storage", storage));
      originalStorages.clear();
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
      failingKeys = key -> false;
      ThreadContext.setContextPrincipal(savedPrincipal);

      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map = (Map<String, ScheduleTask>) (Object)
         scheduleManager.getOrgTaskMap(ORG_A);
      map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
      taskNames.clear();
   }

   // the import of two tasks goes on after the owner permission of the first isn't saved, and
   // tells the user about the first task
   @Test
   void import_ownerPermissionNotSaved_importsEveryTaskWithAWarning() throws Exception {
      ScheduleTask first = newTask("SopwfFirst");
      ScheduleTask second = newTask("SopwfSecond");
      failingKeys = key -> key.endsWith(first.getTaskId());

      ImportTaskResponse response = importTasks(first, second);

      assertEquals(List.of(), response.failedTasks(), "both tasks are imported");
      assertPersisted(first.getTaskId());
      assertPersisted(second.getTaskId());
      assertTrue(scheduleManager.isOwnerPermissionMissing(first));
      assertFalse(scheduleManager.isOwnerPermissionMissing(second));
      assertEquals(1, response.warnings().size(), String.valueOf(response.warnings()));
      assertTrue(response.warnings().get(0).contains("SopwfFirst"), response.warnings().get(0));
   }

   // a single save of a task whose owner permission isn't saved doesn't fail
   @Test
   void save_ownerPermissionNotSaved_savesTheTask() throws Exception {
      ScheduleTask task = newTask("SopwfSave");
      failingKeys = key -> key.endsWith(task.getTaskId());

      assertDoesNotThrow(() -> scheduleManager.setScheduleTask(task.getTaskId(), task, caller()));

      assertPersisted(task.getTaskId());
      assertTrue(scheduleManager.isOwnerPermissionMissing(task));
   }

   // control: the owner gets the permissions and the import has no warning
   @Test
   void import_ownerPermissionSaved_grantsTheOwnerWithoutWarning() throws Exception {
      ScheduleTask task = newTask("SopwfControl");

      ImportTaskResponse response = importTasks(task);

      assertEquals(List.of(), response.failedTasks());
      assertEquals(List.of(), response.warnings());
      assertPersisted(task.getTaskId());
      assertFalse(scheduleManager.isOwnerPermissionMissing(task));
      Permission perm = SecurityEngine.getSecurity()
         .getPermission(ResourceType.SCHEDULE_TASK, task.getTaskId());
      assertNotNull(perm, "the owner permission is saved");
      assertTrue(perm.getAllUserGrants(ResourceAction.DELETE).stream()
                    .anyMatch(grant -> USER.equals(grant.getName())));
   }

   private ImportTaskResponse importTasks(ScheduleTask... tasks) throws Exception {
      ImportTaskController controller = new ImportTaskController(
         scheduleManager, mock(ScheduleTaskFolderService.class), SUtil.getRepletRepository(),
         SecurityEngine.getSecurity());
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(request.getSession(anyBoolean())).thenReturn(session);
      when(session.getAttribute(ImportTaskController.INFO_ATTR))
         .thenReturn(new ArrayList<>(Arrays.asList(tasks)));
      List<String> selected = Arrays.stream(tasks).map(ScheduleTask::getTaskId).toList();

      return controller.importScheduleTask(selected, request, false, "http://localhost/",
                                           caller());
   }

   private SRPrincipal caller() {
      return builder.principalOf(USER, ORG_A);
   }

   private ScheduleTask newTask(String name) {
      ScheduleTask task = new ScheduleTask(name);
      task.setOwner(IdentityID.getIdentityIDFromKey(caller().getName()));
      task.addCondition(TimeCondition.at(1, 30, 0));
      taskNames.add(name);
      return task;
   }

   /**
    * Asserts the task is persisted, read past the task map cache.
    */
   private void assertPersisted(String taskId) {
      ReflectionTestUtils.invokeMethod(scheduleManager.getOrgTaskMap(ORG_A), "clearCache");
      assertNotNull(scheduleManager.getScheduleTask(taskId, ORG_A), taskId + " is stored");
   }

   /**
    * Replaces the storage of every FileAuthorizationProvider that the permissions are read from
    * and written to with one whose put of a key matching {@link #failingKeys} fails.
    */
   @SuppressWarnings("unchecked")
   private void injectFailingStorage() {
      Set<FileAuthorizationProvider> providers = Collections.newSetFromMap(new IdentityHashMap<>());
      SecurityEngine engine = SecurityEngine.getSecurity();
      List<Object> roots = new ArrayList<>();
      roots.add(ReflectionTestUtils.getField(builder, "authzProvider"));

      for(SecurityProvider provider : new SecurityProvider[] {
         engine.getSecurityProvider(), (SecurityProvider) ReflectionTestUtils.getField(engine, "provider") })
      {
         if(provider != null) {
            roots.add(provider.getAuthorizationProvider());
         }
      }

      for(Object root : roots) {
         if(root instanceof FileAuthorizationProvider file) {
            providers.add(file);
         }
         else if(root instanceof AuthorizationChain chain) {
            for(Object child : (List<Object>) ReflectionTestUtils.invokeMethod(chain, "getProviderList")) {
               if(child instanceof FileAuthorizationProvider file) {
                  providers.add(file);
               }
            }
         }
      }

      assertFalse(providers.isEmpty(), "test setup: no FileAuthorizationProvider found");

      for(FileAuthorizationProvider authz : providers) {
         // the storage is opened lazily
         ReflectionTestUtils.invokeMethod(authz, "init");
         KeyValueStorage<Permission> original =
            (KeyValueStorage<Permission>) ReflectionTestUtils.getField(authz, "storage");
         assertNotNull(original, "test setup: the permission storage is open");
         KeyValueStorage<Permission> failing =
            mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(original));
         doAnswer(inv -> {
            String key = inv.getArgument(0);

            if(key.startsWith(ResourceType.SCHEDULE_TASK + ":") && failingKeys.test(key)) {
               return CompletableFuture.failedFuture(new IOException("injected write failure"));
            }

            return original.put(key, inv.getArgument(1));
         }).when(failing).put(anyString(), any());
         ReflectionTestUtils.setField(authz, "storage", failing);
         originalStorages.put(authz, original);
      }
   }

   // the save looks up the dependencies of the task
   @Configuration
   static class DependencyConfiguration {
      @Bean
      DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }
}
