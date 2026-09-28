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

package inetsoft.sree.security;

import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Bug #77204: the per-instance user role/group caches of {@link FileAuthenticationProvider} must
 * follow changes to the replicated user/group/role storage made through another provider
 * instance (another cluster node), and a removed user must not keep its cached roles.
 *
 * Both providers share the same replicated storage, so provider {@code nodeB} stands in for a
 * remote node that has already cached the user's roles. Storage events are delivered
 * asynchronously, so the remote assertions wait for the change with a bounded timeout.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
public class FileAuthenticationProviderCacheCoherenceTest {
   @BeforeEach
   void createProviders() throws Exception {
      nodeA = new FileAuthenticationProvider();

      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(nodeA));
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

      nodeB = new FileAuthenticationProvider();
   }

   @AfterEach
   void destroyProviders() {
      nodeB.tearDown();
      nodeA.tearDown();
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void revokingRoleFromGroupReachesOtherInstance() {
      IdentityID user = new IdentityID("groupRevokeUser", ORG);
      IdentityID group = new IdentityID("groupRevokeGroup", ORG);
      IdentityID role = new IdentityID("groupRevokeRole", ORG);
      nodeA.addRole(new FSRole(role));
      FSGroup fsGroup = new FSGroup(group);
      fsGroup.setRoles(new IdentityID[]{ role });
      nodeA.addGroup(fsGroup);
      FSUser fsUser = new FSUser(user);
      fsUser.setGroups(new String[]{ group.name });
      nodeA.addUser(fsUser);

      assertTrue(hasRole(nodeB, user, role), "precondition: group role cached on node B");

      FSGroup revoked = new FSGroup(group);
      revoked.setRoles(new IdentityID[0]);
      nodeA.setGroup(group, revoked);

      awaitAssert(() -> !hasRole(nodeB, user, role),
                  "Role revoked from the group on node A is still granted on node B");
   }

   @Test
   void removingUserFromGroupReachesOtherInstance() {
      IdentityID user = new IdentityID("groupLeaveUser", ORG);
      IdentityID group = new IdentityID("groupLeaveGroup", ORG);
      IdentityID role = new IdentityID("groupLeaveRole", ORG);
      nodeA.addRole(new FSRole(role));
      FSGroup fsGroup = new FSGroup(group);
      fsGroup.setRoles(new IdentityID[]{ role });
      nodeA.addGroup(fsGroup);
      FSUser fsUser = new FSUser(user);
      fsUser.setGroups(new String[]{ group.name });
      nodeA.addUser(fsUser);

      assertTrue(Arrays.asList(nodeB.getUserGroups(user)).contains(group.name),
                 "precondition: group cached on node B");
      assertTrue(hasRole(nodeB, user, role), "precondition: group role cached on node B");

      FSUser updated = new FSUser(user);
      updated.setGroups(new String[0]);
      nodeA.setUser(user, updated);

      awaitAssert(() -> !Arrays.asList(nodeB.getUserGroups(user)).contains(group.name),
                  "Group removed from the user on node A is still reported on node B");
      awaitAssert(() -> !hasRole(nodeB, user, role),
                  "Role of a group the user left on node A is still granted on node B");
   }

   @Test
   void removingDirectRoleReachesOtherInstance() {
      IdentityID user = new IdentityID("directRevokeUser", ORG);
      IdentityID role = new IdentityID("directRevokeRole", ORG);
      nodeA.addRole(new FSRole(role));
      FSUser fsUser = new FSUser(user);
      fsUser.setRoles(new IdentityID[]{ role });
      nodeA.addUser(fsUser);

      assertTrue(hasRole(nodeB, user, role), "precondition: direct role cached on node B");

      FSUser updated = new FSUser(user);
      updated.setRoles(new IdentityID[0]);
      nodeA.setUser(user, updated);

      awaitAssert(() -> !hasRole(nodeB, user, role),
                  "Role removed from the user on node A is still granted on node B");
   }

   @Test
   void removedUserLosesCachedRoles() {
      IdentityID user = new IdentityID("removedUser", ORG);
      IdentityID role = new IdentityID("removedUserRole", ORG);
      nodeA.addRole(new FSRole(role));
      FSUser fsUser = new FSUser(user);
      fsUser.setRoles(new IdentityID[]{ role });
      nodeA.addUser(fsUser);

      assertTrue(hasRole(nodeA, user, role), "precondition: role cached on node A");
      assertTrue(hasRole(nodeB, user, role), "precondition: role cached on node B");

      nodeA.removeUser(user);

      assertEquals(0, nodeA.getRoles(user).length,
                   "Removed user still has cached roles on the node that removed it");
      awaitAssert(() -> nodeB.getRoles(user).length == 0,
                  "User removed on node A still has cached roles on node B");
   }

   @Test
   void promotionToSiteAdminReachesOtherInstance() {
      IdentityID user = new IdentityID("promotedUser", ORG);
      nodeA.addUser(new FSUser(user));

      assertFalse(hasRole(nodeB, user, ADMINISTRATOR), "precondition: not an admin on node B");

      FSUser promoted = new FSUser(user);
      promoted.setRoles(new IdentityID[]{ ADMINISTRATOR });
      nodeA.setUser(user, promoted);

      awaitAssert(() -> hasRole(nodeB, user, ADMINISTRATOR),
                  "User promoted to site admin on node A is not an admin on node B");
   }

   private static boolean hasRole(FileAuthenticationProvider provider, IdentityID user,
                                  IdentityID role)
   {
      return Arrays.asList(provider.getRoles(user)).contains(role);
   }

   private static void awaitAssert(java.util.concurrent.Callable<Boolean> condition, String message) {
      try {
         await().atMost(5L, TimeUnit.SECONDS).pollInterval(50L, TimeUnit.MILLISECONDS)
            .until(condition);
      }
      catch(org.awaitility.core.ConditionTimeoutException e) {
         fail(message);
      }
   }

   private static final String ORG = "cacheOrg";
   private static final IdentityID ADMINISTRATOR = new IdentityID("Administrator", null);
   private FileAuthenticationProvider nodeA;
   private FileAuthenticationProvider nodeB;
}
