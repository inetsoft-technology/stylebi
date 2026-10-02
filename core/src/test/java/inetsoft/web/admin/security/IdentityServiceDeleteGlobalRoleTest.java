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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.util.DefaultIdentity;
import inetsoft.uql.util.Identity;
import inetsoft.util.MessageException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77165: deleting a global role must remove it from the users, groups and roles of every
 * organization, so a global role created later with the same name is not held by them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class IdentityServiceDeleteGlobalRoleTest {
   @BeforeEach
   void createProvider() throws Exception {
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

      for(String org : new String[] { "orgA", "orgB" }) {
         FSOrganization o = new FSOrganization(org);
         o.setName(org);
         o.setMembers(new String[0]);
         provider.addOrganization(o);
      }

      provider.addRole(new FSRole(G_ROLE));
      provider.addRole(new FSRole(SAME_B));
      provider.addRole(new FSRole(ROLE_B, new IdentityID[] { G_ROLE, SAME_B }));
      FSGroup group = new FSGroup(GROUP_B);
      group.setRoles(new IdentityID[] { G_ROLE });
      provider.addGroup(group);
      FSUser userA = new FSUser(USER_A);
      userA.setRoles(new IdentityID[] { G_ROLE });
      provider.addUser(userA);
      FSUser userB = new FSUser(USER_B);
      userB.setRoles(new IdentityID[] { G_ROLE, SAME_B });
      provider.addUser(userB);
      FSUser memberB = new FSUser(MEMBER_B);
      memberB.setGroups(new String[] { "grpB" });
      provider.addUser(memberB);

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(service)
         .updateIdentityPermissions(anyInt(), any(), any(), any(), any(), anyBoolean());
   }

   @AfterEach
   void destroyProvider() {
      if(provider != null) {
         provider.tearDown();
         provider = null;
      }

      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void deleteGlobalRole_sameNamedRoleCreatedLater_isNotHeldByOldMembers() throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);
      method.invoke(service, provider, new DefaultIdentity(G_ROLE, Identity.ROLE), null);
      provider.addRole(new FSRole(G_ROLE));

      assertFalse(Arrays.asList(provider.getRoles(USER_A)).contains(G_ROLE));
      assertFalse(Arrays.asList(provider.getRoles(USER_B)).contains(G_ROLE));
      assertFalse(Arrays.asList(provider.getRoles(MEMBER_B)).contains(G_ROLE));
      assertArrayEquals(new IdentityID[] { SAME_B }, provider.getUser(USER_B).getRoles());
      assertArrayEquals(new IdentityID[] { SAME_B }, provider.getRole(ROLE_B).getRoles());
      assertNotNull(provider.getRole(SAME_B));
   }

   // Bug #77354: a failed member update must be reported and must not delete the role while
   // members still hold it
   @Test
   void deleteGlobalRole_memberUpdateFails_reportsErrorAndKeepsRole() throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "syncIdentity", EditableAuthenticationProvider.class, Identity.class, IdentityID.class);
      method.setAccessible(true);
      Field field = FileAuthenticationProvider.class.getDeclaredField("userStorage");
      field.setAccessible(true);
      KeyValueStorage real = (KeyValueStorage) field.get(provider);
      KeyValueStorage failing = mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doReturn(CompletableFuture.failedFuture(new IOException("simulated write failure")))
         .when(failing).put(anyString(), any());
      field.set(provider, failing);
      InvocationTargetException thrown;

      try {
         thrown = assertThrows(InvocationTargetException.class, () -> method.invoke(
            service, provider, new DefaultIdentity(G_ROLE, Identity.ROLE), null));
      }
      finally {
         field.set(provider, real);
      }

      assertInstanceOf(MessageException.class, thrown.getCause());
      assertNotNull(provider.getRole(G_ROLE), "the role must not be deleted");
   }

   private static final IdentityID G_ROLE = new IdentityID("gRole", null);
   private static final IdentityID SAME_B = new IdentityID("gRole", "orgB");
   private static final IdentityID ROLE_B = new IdentityID("roleB", "orgB");
   private static final IdentityID GROUP_B = new IdentityID("grpB", "orgB");
   private static final IdentityID USER_A = new IdentityID("userA", "orgA");
   private static final IdentityID USER_B = new IdentityID("userB", "orgB");
   private static final IdentityID MEMBER_B = new IdentityID("memberB", "orgB");
   private FileAuthenticationProvider provider;
   private IdentityService service;
}
