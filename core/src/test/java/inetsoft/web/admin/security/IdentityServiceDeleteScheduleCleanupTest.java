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
package inetsoft.web.admin.security;

import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.schedule.*;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import inetsoft.util.ThreadContext;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.security.user.IdentityThemeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77797: the identity delete runs its schedule cleanup only once the provider removal has
 * succeeded. Uses the real ScheduleManager bean and a real FileAuthenticationProvider, so the
 * tasks owned by a deleted user are really removed from the org task map on success (through
 * ScheduleManager.identityRemoved(Identity, String), after the user is gone from the provider),
 * and are really kept when the provider's storage fails to remove the user.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  SecurityEngineDispatchConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityServiceDeleteScheduleCleanupTest {
   private static final String ORG = "idsclorg";
   private static final IdentityID DAVE = new IdentityID("dave", ORG);

   @Autowired
   ScheduleManager scheduleManager;

   @Autowired
   SecurityEngineOverrides securityEngineOverrides;

   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private SRPrincipal dave;
   private IdentityService service;
   private DashboardManager dashboardManager;
   private RepletRegistryManager repletRegistryManager;
   private DashboardRegistryManager dashboardRegistryManager;
   private final List<String> taskNames = new ArrayList<>();

   @BeforeAll
   void setupAll() throws Exception {
      SecurityEngineOverrides.assertInstalled(SecurityEngine.getSecurity());
      builder = SecurityTestDataBuilder.create()
         .addOrg("idsclOrg", ORG)
         .addOrgAdminRole("idsclOrgAdmin", ORG)
         .addUser("dave", ORG, "password")
         .addUserToRole("dave", "idsclOrgAdmin", ORG);
      builder.setup();
      SecurityProvider provider = CompositeSecurityProvider.create(
         provider(), (AuthorizationProvider) ReflectionTestUtils.getField(builder, "authzProvider"));
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
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      dave = builder.principalOf("dave", ORG);
      dave.setProperty("__internal__", "true");
      ScheduleClient scheduleClient =
         (ScheduleClient) ReflectionTestUtils.getField(scheduleManager, "scheduleClient");
      reset(scheduleClient);
      when(scheduleClient.isReady()).thenReturn(true);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      dashboardManager = mock(DashboardManager.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "dashboardManager", dashboardManager);
      ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      ReflectionTestUtils.setField(service, "indexedStorage", mock(IndexedStorage.class));
      ReflectionTestUtils.setField(service, "themeService", mock(IdentityThemeService.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(service)
         .updateIdentityPermissions(anyInt(), any(), any(), any(), any(), anyBoolean());
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      ThreadContext.setPrincipal(null);
      sutilStatic.close();

      @SuppressWarnings("unchecked")
      Map<String, ScheduleTask> map =
         (Map<String, ScheduleTask>) (Object) scheduleManager.getOrgTaskMap(ORG);
      map.values().removeIf(t -> t != null && taskNames.contains(t.getName()));
   }

   @Test
   void deleteUser_succeeds_ownedTaskDeletedAndExecuteAsReset() throws Exception {
      FSUser user = addUser("ownok");
      ScheduleTask owned = saveTask("OwnedOk", user.getIdentityID(), null);
      ScheduleTask runAs = saveTask("RunAsOk", DAVE, user);

      assertNull(delete(user, "userStorage", Fault.NONE));

      assertNull(provider().getUser(user.getIdentityID()), "the user is removed");
      assertNull(scheduleManager.getScheduleTask(owned.getTaskId(), ORG),
                 "the task owned by the deleted user is deleted");
      assertNotNull(scheduleManager.getScheduleTask(runAs.getTaskId(), ORG));
      assertNull(scheduleManager.getScheduleTask(runAs.getTaskId(), ORG).getIdentity(),
                 "execute-as reset to the owner");
      verify(dashboardManager).setDashboards(new DefaultIdentity(user.getIdentityID(), Identity.USER), null);
      verify(repletRegistryManager).removeUser(user.getIdentityID());
      verify(dashboardRegistryManager).clear(user.getIdentityID());
   }

   @Test
   void deleteUser_removeFails_ownedTaskAndExecuteAsKept() throws Exception {
      FSUser user = addUser("ownfail");
      ScheduleTask owned = saveTask("OwnedFail", user.getIdentityID(), null);
      ScheduleTask runAs = saveTask("RunAsFail", DAVE, user);

      assertInstanceOf(MessageException.class, delete(user, "userStorage", Fault.FAIL));

      assertNotNull(provider().getUser(user.getIdentityID()), "the user is kept");
      assertNotNull(scheduleManager.getScheduleTask(owned.getTaskId(), ORG),
                    "the owned task is kept");
      Identity executeAs = scheduleManager.getScheduleTask(runAs.getTaskId(), ORG).getIdentity();
      assertNotNull(executeAs, "execute-as kept");
      assertEquals(user.getIdentityID(), executeAs.getIdentityID());
      verify(dashboardManager, never()).setDashboards(any(), any());
      verify(repletRegistryManager, never()).removeUser(any());
      verify(dashboardRegistryManager, never()).clear(any());
   }

   @Test
   void deleteUser_removeFailsAfterRemoval_ownedTaskDeleted() throws Exception {
      FSUser user = addUser("owngone");
      ScheduleTask owned = saveTask("OwnedGone", user.getIdentityID(), null);

      assertNull(delete(user, "userStorage", Fault.FAIL_AFTER_REMOVE));

      assertNull(provider().getUser(user.getIdentityID()), "the user is removed");
      assertNull(scheduleManager.getScheduleTask(owned.getTaskId(), ORG),
                 "the task owned by the deleted user is deleted");
      verify(repletRegistryManager).removeUser(user.getIdentityID());
   }

   @Test
   void deleteGroup_succeeds_executeAsReset() throws Exception {
      FSGroup group = addGroup("grpok");
      ScheduleTask runAs = saveTask("GroupRunAsOk", DAVE, group);
      // updatePrincipalGroup edits the groups of the acting principal
      ThreadContext.setPrincipal(dave);

      assertNull(delete(group, "groupStorage", Fault.NONE));

      assertNull(provider().getGroup(group.getIdentityID()), "the group is removed");
      assertNull(scheduleManager.getScheduleTask(runAs.getTaskId(), ORG).getIdentity(),
                 "execute-as reset to the owner");
      verify(dashboardManager).setDashboards(new DefaultIdentity(group.getIdentityID(), Identity.GROUP), null);
   }

   @Test
   void deleteGroup_removeFails_executeAsKept() throws Exception {
      FSGroup group = addGroup("grpfail");
      ScheduleTask runAs = saveTask("GroupRunAsFail", DAVE, group);

      assertInstanceOf(MessageException.class, delete(group, "groupStorage", Fault.FAIL));

      assertNotNull(provider().getGroup(group.getIdentityID()), "the group is kept");
      Identity executeAs = scheduleManager.getScheduleTask(runAs.getTaskId(), ORG).getIdentity();
      assertNotNull(executeAs, "execute-as kept");
      assertEquals(group.getIdentityID(), executeAs.getIdentityID());
      verify(dashboardManager, never()).setDashboards(any(), any());
   }

   private enum Fault { NONE, FAIL, FAIL_AFTER_REMOVE }

   private FSUser addUser(String name) {
      FSUser user = new FSUser(new IdentityID(name, ORG));
      user.setOrganization(ORG);
      provider().addUser(user);
      return (FSUser) provider().getUser(user.getIdentityID());
   }

   private FSGroup addGroup(String name) {
      FSGroup group = new FSGroup(new IdentityID(name, ORG));
      group.setOrganization(ORG);
      provider().addGroup(group);
      return (FSGroup) provider().getGroup(group.getIdentityID());
   }

   @SuppressWarnings({ "unchecked", "rawtypes" })
   private Throwable delete(Identity identity, String storageField, Fault fault) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);
      Field field = FileAuthenticationProvider.class.getDeclaredField(storageField);
      field.setAccessible(true);
      KeyValueStorage real = (KeyValueStorage) field.get(provider());

      if(fault != Fault.NONE) {
         KeyValueStorage failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
         doAnswer(inv -> {
            if(fault == Fault.FAIL_AFTER_REMOVE) {
               real.remove(inv.getArgument(0)).get();
            }

            return CompletableFuture.failedFuture(new IOException("simulated remove failure"));
         }).when(failing).remove(anyString());
         field.set(provider(), failing);
      }

      try {
         method.invoke(service, provider(),
                       new DefaultIdentity(identity.getIdentityID(), identity.getType()), null);
         return null;
      }
      catch(InvocationTargetException e) {
         return e.getCause();
      }
      finally {
         field.set(provider(), real);
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
         scheduleManager.save(List.of(task), ORG);
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }

      ScheduleTask saved = scheduleManager.getScheduleTask(task.getTaskId(), ORG);
      assertNotNull(saved, "task saved");
      return saved;
   }

   private FileAuthenticationProvider provider() {
      return (FileAuthenticationProvider) ReflectionTestUtils.getField(builder, "authcProvider");
   }
}
