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

/*
 * Redmine #76973 -- revoking a role on node A does not take effect on node B where the user
 * logged in.
 *
 * Two FileAuthenticationProvider instances model two cluster nodes: "node B" is the instance the
 * SecurityEngine uses (so login and OrganizationManager.isSiteAdmin read its caches), "node A" is
 * a second instance used only to write. Both read the same replicated key-value storages (the
 * test MockCluster), exactly as nodes share the Ignite replicated maps, but each has its own
 * node-local userRoleCache/userGroupCache.
 *
 * [Revoke: direct role]     B loaded roles(X) + A setUser(X, [Everyone])        → B.getRoles(X) drops Administrator
 * [Revoke: group role]      B loaded roles(X) + A setGroup(G, no roles)         → B.getRoles(X) drops Administrator
 * [Revoke: parent role]     B loaded roles(X) + A setRole(R, no parents)        → B.getRoles(X) drops Administrator
 * [Revoke: delete+recreate] B loaded roles(X) + A removeUser + addUser(Everyone) → B.getRoles(X) drops Administrator
 * [Existing session]        principal/storage [Everyone] after revoke on A      → EM_COMPONENT check on B denied
 * [New login]               login on B after revoke on A                        → new principal has no Administrator
 * [Control: cache only]     B's caches invalidated locally                      → existing session and new login denied
 *
 * Event delivery from the storage to node B is asynchronous (LocalKeyValueStorage re-dispatches
 * on the ThreadPool), so the assertions poll with a bounded timeout.
 */

import com.github.benmanes.caffeine.cache.Cache;
import inetsoft.sree.ClientInfo;
import inetsoft.sree.SreeEnv;
import inetsoft.test.*;
import inetsoft.util.PasswordEncryption;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Principal;
import java.util.*;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class FileAuthenticationProviderClusterCacheTest {
   @BeforeEach
   void createProviders() throws Exception {
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(new FileAuthenticationProvider()));
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

      nodeB = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);
      nodeA = new FileAuthenticationProvider();
      hashedPassword = PasswordEncryption.newInstance().hash(PASSWORD, "bcrypt", null, false).getHash();
   }

   @AfterEach
   void destroyProviders() {
      // node A shares the storages with node B (KeyValueStorageManager caches them by id), so it
      // is not torn down here: that would close them under node B
      nodeA = null;
      nodeB = null;
      SreeEnv.remove("security.enabled");
      SreeEnv.remove("security.users.multiTenant");
   }

   @Test
   void revokeDirectRoleOnOtherNodeReachesLoginNode() {
      IdentityID x = new IdentityID("u76973direct", ORG);
      nodeA.addUser(user(x, new String[0], EVERYONE, ADMIN));
      assertHasAdmin(nodeB.getRoles(x), "precondition: login on B loads Administrator");

      nodeA.setUser(x, user(x, new String[0], EVERYONE));

      assertArrayEquals(new IdentityID[] { EVERYONE }, nodeB.getUser(x).getRoles(),
                        "B's storage replica must already hold the revoked roles");
      assertFalse(isAdmin(nodeA.getRoles(x)), "writer node A must see the revoke");
      assertEventuallyNoAdmin(nodeB, x);
   }

   @Test
   void revokeGroupRoleOnOtherNodeReachesLoginNode() {
      IdentityID x = new IdentityID("u76973group", ORG);
      IdentityID g = new IdentityID("g76973", ORG);
      nodeA.addGroup(group(g, ADMIN));
      nodeA.addUser(user(x, new String[] { g.name }, EVERYONE));
      assertHasAdmin(nodeB.getRoles(x), "precondition: B loads Administrator through group");

      nodeA.setGroup(g, group(g));

      assertEquals(0, nodeB.getGroup(g).getRoles().length,
                   "B's storage replica must already hold the group without Administrator");
      assertFalse(isAdmin(nodeA.getRoles(x)), "writer node A must see the revoke");
      assertEventuallyNoAdmin(nodeB, x);
   }

   @Test
   void revokeParentRoleOnOtherNodeReachesLoginNode() {
      IdentityID x = new IdentityID("u76973parent", ORG);
      IdentityID r = new IdentityID("r76973", ORG);
      nodeA.addRole(new FSRole(r, new IdentityID[] { ADMIN }, ""));
      nodeA.addUser(user(x, new String[0], EVERYONE, r));
      assertHasAdmin(nodeB.getRoles(x), "precondition: B loads Administrator through parent role");

      nodeA.setRole(r, new FSRole(r, new IdentityID[0], ""));

      assertEquals(0, nodeB.getRole(r).getRoles().length,
                   "B's storage replica must already hold the role without its parent");
      assertFalse(isAdmin(nodeA.getRoles(x)), "writer node A must see the revoke");
      assertEventuallyNoAdmin(nodeB, x);
   }

   @Test
   void deleteAndRecreateOnOtherNodeReachesLoginNode() {
      IdentityID x = new IdentityID("u76973recreate", ORG);
      nodeA.addUser(user(x, new String[0], EVERYONE, ADMIN));
      assertHasAdmin(nodeB.getRoles(x), "precondition: login on B loads Administrator");

      nodeA.removeUser(x);
      nodeA.addUser(user(x, new String[0], EVERYONE));

      assertArrayEquals(new IdentityID[] { EVERYONE }, nodeB.getUser(x).getRoles(),
                        "B's storage replica must already hold the recreated user");
      assertEventuallyNoAdmin(nodeB, x);
   }

   /**
    * X's session principal is already corrected to [Everyone] cluster-wide by the edit path
    * (IgniteSessionRepository.updatePrincipalRolesAndGroups), and B's storage holds [Everyone],
    * yet OrganizationManager.isSiteAdmin(Principal) falls back to B's cached getRoles(X).
    */
   @Test
   void existingSessionLosesEmAccessOnLoginNodeAfterRevoke() {
      IdentityID x = new IdentityID("u76973session", ORG);
      nodeA.addUser(user(x, new String[0], EVERYONE, ADMIN));
      Principal login = login(x);
      assertHasAdmin(((SRPrincipal) login).getRoles(), "precondition: login on B is admin");

      nodeA.setUser(x, user(x, new String[0], EVERYONE));
      SRPrincipal corrected = principal(x, EVERYONE);

      waitFor(() -> !emAccess(corrected));
      assertFalse(emAccess(corrected),
                  "existing session on B (principal and storage roles [Everyone]) must lose EM " +
                  "access after the revoke on A; B.getRoles(X)=" + Arrays.toString(nodeB.getRoles(x)) +
                  ", B storage roles=" + Arrays.toString(nodeB.getUser(x).getRoles()) +
                  ", isSiteAdmin(principal)=" + OrganizationManager.getInstance().isSiteAdmin(corrected));
   }

   @Test
   void newLoginOnLoginNodeIsNotAdminAfterRevoke() {
      IdentityID x = new IdentityID("u76973relogin", ORG);
      nodeA.addUser(user(x, new String[0], EVERYONE, ADMIN));
      assertHasAdmin(((SRPrincipal) login(x)).getRoles(), "precondition: login on B is admin");

      nodeA.setUser(x, user(x, new String[0], EVERYONE));

      waitFor(() -> !isAdmin(((SRPrincipal) login(x)).getRoles()));
      IdentityID[] roles = ((SRPrincipal) login(x)).getRoles();
      assertFalse(isAdmin(roles),
                  "a new login on B after the revoke on A must not carry Administrator; principal " +
                  "roles=" + Arrays.toString(roles) + ", B storage roles=" +
                  Arrays.toString(nodeB.getUser(x).getRoles()));
   }

   /**
    * Sufficiency control: with only B's provider caches invalidated, both the existing session and a
    * new login lose admin, so no other node-local state feeds the stale role.
    */
   @Test
   void invalidatingOnlyLoginNodeCachesRemovesAdmin() {
      IdentityID x = new IdentityID("u76973control", ORG);
      nodeA.addUser(user(x, new String[0], EVERYONE, ADMIN));
      assertHasAdmin(((SRPrincipal) login(x)).getRoles(), "precondition: login on B is admin");
      nodeA.setUser(x, user(x, new String[0], EVERYONE));

      ((Cache<?, ?>) ReflectionTestUtils.getField(nodeB, "userRoleCache")).invalidateAll();
      ((Cache<?, ?>) ReflectionTestUtils.getField(nodeB, "userGroupCache")).invalidateAll();

      assertFalse(emAccess(principal(x, EVERYONE)), "existing session must lose EM access");
      assertFalse(isAdmin(((SRPrincipal) login(x)).getRoles()), "new login must not be admin");
   }

   private Principal login(IdentityID x) {
      ClientInfo info = new ClientInfo(x, "127.0.0.1", UUID.randomUUID().toString());
      Principal principal = SecurityEngine.getSecurity().authenticate(
         info, new DefaultTicket(x, PASSWORD));
      assertNotNull(principal, "login of " + x + " must succeed");
      return principal;
   }

   private static boolean emAccess(SRPrincipal principal) {
      DefaultCheckPermissionStrategy strategy =
         new DefaultCheckPermissionStrategy(SecurityEngine.getSecurity().getSecurityProvider());
      return strategy.checkPermission(
         principal, ResourceType.EM_COMPONENT, "settings/security/provider", ResourceAction.ACCESS);
   }

   private static SRPrincipal principal(IdentityID x, IdentityID... roles) {
      return new SRPrincipal(x, roles, new String[0], x.orgID, 1L);
   }

   private FSUser user(IdentityID id, String[] groups, IdentityID... roles) {
      FSUser user = new FSUser(id);
      user.setPassword(hashedPassword);
      user.setPasswordAlgorithm("bcrypt");
      user.setActive(true);
      user.setGroups(groups);
      user.setRoles(roles);
      return user;
   }

   private static FSGroup group(IdentityID id, IdentityID... roles) {
      FSGroup group = new FSGroup(id);
      group.setRoles(roles);
      return group;
   }

   private static boolean isAdmin(IdentityID[] roles) {
      return roles != null && Arrays.asList(roles).contains(ADMIN);
   }

   private static void assertHasAdmin(IdentityID[] roles, String message) {
      assertTrue(isAdmin(roles), message + ": " + Arrays.toString(roles));
   }

   private static void assertEventuallyNoAdmin(FileAuthenticationProvider node, IdentityID x) {
      waitFor(() -> !isAdmin(node.getRoles(x)));
      assertFalse(isAdmin(node.getRoles(x)),
                  "node B must drop Administrator after the revoke on node A; B.getRoles(X)=" +
                  Arrays.toString(node.getRoles(x)) + ", B storage roles=" +
                  Arrays.toString(node.getUser(x).getRoles()));
   }

   private static void waitFor(BooleanSupplier condition) {
      long deadline = System.currentTimeMillis() + TIMEOUT_MS;

      while(!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
         try {
            Thread.sleep(50L);
         }
         catch(InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
         }
      }
   }

   private FileAuthenticationProvider nodeA;
   private FileAuthenticationProvider nodeB;
   private String hashedPassword;

   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final IdentityID EVERYONE = new IdentityID("Everyone", ORG);
   private static final IdentityID ADMIN = new IdentityID("Administrator", null);
   private static final String PASSWORD = "p76973";
   private static final long TIMEOUT_MS = 5000L;
}
