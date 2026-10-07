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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77797: when the provider fails to remove a deleted user, group or role, the identity is
 * kept and the admin is told to delete it again, so its schedule tasks, dashboards, portal
 * registry and (for a global role) memberships must be kept too. The provider is a real
 * {@link FileAuthenticationProvider} whose storage fails to remove the identity.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class IdentityServiceDeleteFailureTest {
   @BeforeEach
   void setUp() throws Exception {
      provider = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(provider));
      authcChain.saveConfiguration();
      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();
      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.setProperty("security.users.multiTenant", "true");
      SreeEnv.save();
      SecurityEngine.getSecurity().init();

      FSOrganization o = new FSOrganization(ORG_A);
      o.setName(ORG_A);
      o.setMembers(new String[0]);
      provider.addOrganization(o);
      provider.addRole(new FSRole(G_ROLE));
      provider.addRole(new FSRole(ROLE_A, new IdentityID[] { G_ROLE }));
      FSGroup group = new FSGroup(GROUP_A);
      group.setRoles(new IdentityID[] { G_ROLE });
      provider.addGroup(group);
      FSUser user = new FSUser(USER_A);
      user.setRoles(new IdentityID[] { G_ROLE });
      provider.addUser(user);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      dashboardManager = mock(DashboardManager.class);
      scheduleManager = mock(ScheduleManager.class);
      repletRegistryManager = mock(RepletRegistryManager.class);
      dashboardRegistryManager = mock(DashboardRegistryManager.class);
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "dashboardManager", dashboardManager);
      ReflectionTestUtils.setField(service, "scheduleManager", scheduleManager);
      ReflectionTestUtils.setField(service, "repletRegistryManager", repletRegistryManager);
      ReflectionTestUtils.setField(service, "dashboardRegistryManager", dashboardRegistryManager);
      // LOG is a final instance field set by the constructor, which the mock bypasses
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      // the permission cleanup is covered elsewhere and is not what these tests are about
      doNothing().when(service)
         .updateIdentityPermissions(anyInt(), any(), any(), any(), any(), anyBoolean());
   }

   @AfterEach
   void tearDown() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }

      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void deleteUser_removeFails_keepsTasksDashboardsAndPortalRegistry() throws Exception {
      Throwable error = deleteWithFailingRemove("userStorage", USER_A, Identity.USER, false);

      assertInstanceOf(MessageException.class, error);
      assertNotNull(provider.getUser(USER_A), "the user must be kept");
      verifyNoCleanup();
      verify(repletRegistryManager, never()).removeUser(any());
      verify(dashboardRegistryManager, never()).clear(any());
   }

   @Test
   void deleteGroup_removeFails_keepsTasksAndDashboards() throws Exception {
      Throwable error = deleteWithFailingRemove("groupStorage", GROUP_A, Identity.GROUP, false);

      assertInstanceOf(MessageException.class, error);
      assertNotNull(provider.getGroup(GROUP_A), "the group must be kept");
      verifyNoCleanup();
   }

   @Test
   void deleteOrgRole_removeFails_keepsTasksAndDashboards() throws Exception {
      Throwable error = deleteWithFailingRemove("roleStorage", ROLE_A, Identity.ROLE, false);

      assertInstanceOf(MessageException.class, error);
      assertNotNull(provider.getRole(ROLE_A), "the role must be kept");
      verifyNoCleanup();
   }

   // the global role is removed from its members before it is removed (Bug #77354), so a kept
   // role must be given back to them
   @Test
   void deleteGlobalRole_removeFails_membersKeepRole() throws Exception {
      Throwable error = deleteWithFailingRemove("roleStorage", G_ROLE, Identity.ROLE, false);

      assertInstanceOf(MessageException.class, error);
      assertNotNull(provider.getRole(G_ROLE), "the role must be kept");
      verifyNoCleanup();
      assertAll(
         () -> assertTrue(Arrays.asList(provider.getUser(USER_A).getRoles()).contains(G_ROLE),
                          "the user must keep the global role"),
         () -> assertTrue(Arrays.asList(provider.getGroup(GROUP_A).getRoles()).contains(G_ROLE),
                          "the group must keep the global role"),
         () -> assertTrue(Arrays.asList(provider.getRole(ROLE_A).getRoles()).contains(G_ROLE),
                          "the role must keep inheriting the global role"));
   }

   // a removal that reports a failure after the role is gone (e.g. a storage timeout) cannot be
   // repeated, so the role must be cleaned up anyway, with the organization read before removal
   @Test
   void deleteOrgRole_removeFailsAfterRemoval_cleanedUp() throws Exception {
      assertNull(deleteWithFailingRemove("roleStorage", ROLE_A, Identity.ROLE, true));

      assertNull(provider.getRole(ROLE_A), "the role must be removed");
      verify(dashboardManager).setDashboards(new DefaultIdentity(ROLE_A, Identity.ROLE), null);
      verify(scheduleManager).identityRemoved(
         argThat(i -> ROLE_A.equals(i.getIdentityID())), eq(ORG_A));
   }

   private void verifyNoCleanup() {
      verify(dashboardManager, never()).setDashboards(any(), any());
      verify(scheduleManager, never()).identityRemoved(any(), nullable(String.class));
      verify(scheduleManager, never())
         .identityRemoved(any(), nullable(EditableAuthenticationProvider.class));
   }

   /**
    * Deletes the identity through syncIdentity() while the storage of the identity type fails
    * to remove it.
    *
    * @param removeFirst {@code true} to remove the identity before reporting the failure.
    *
    * @return the error thrown by syncIdentity(), or {@code null} if it returned normally.
    */
   @SuppressWarnings({ "unchecked", "rawtypes" })
   private Throwable deleteWithFailingRemove(String storageField, IdentityID id, int type,
                                             boolean removeFirst)
      throws Exception
   {
      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);
      Field field = FileAuthenticationProvider.class.getDeclaredField(storageField);
      field.setAccessible(true);
      KeyValueStorage real = (KeyValueStorage) field.get(provider);
      KeyValueStorage failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> {
         if(removeFirst) {
            real.remove(inv.getArgument(0)).get();
         }

         return CompletableFuture.failedFuture(new IOException("simulated remove failure"));
      }).when(failing).remove(anyString());
      field.set(provider, failing);

      try {
         method.invoke(service, provider, new DefaultIdentity(id, type), null);
         return null;
      }
      catch(InvocationTargetException e) {
         return e.getCause();
      }
      finally {
         field.set(provider, real);
      }
   }

   private static final String ORG_A = "orgA";
   private static final IdentityID G_ROLE = new IdentityID("gRole", null);
   private static final IdentityID ROLE_A = new IdentityID("roleA", ORG_A);
   private static final IdentityID GROUP_A = new IdentityID("grpA", ORG_A);
   private static final IdentityID USER_A = new IdentityID("userA", ORG_A);
   private FileAuthenticationProvider provider;
   private IdentityService service;
   private DashboardManager dashboardManager;
   private ScheduleManager scheduleManager;
   private RepletRegistryManager repletRegistryManager;
   private DashboardRegistryManager dashboardRegistryManager;
}
