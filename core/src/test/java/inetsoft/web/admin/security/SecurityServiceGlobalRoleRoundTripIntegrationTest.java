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

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.test.*;
import inetsoft.util.ThreadContext;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77327: getRole reported a global role's assignees of every org by bare name, and
 * updateRole resolves a bare name in the caller's current org, so an unchanged GET then PUT
 * failed or granted the role to a same-named identity of the current org. Assignees of another
 * org are now reported by key when the caller can administer them, and left out otherwise, so
 * that a delegate of one org doesn't see another org's holders.
 *
 * This drives the real SecurityService against the real IdentityService,
 * FileAuthenticationProvider and DefaultCheckPermissionStrategy, because SecurityServiceTest
 * mocks the permission checks and IdentityService.setRoleInfo().
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceRoleAssignmentIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceGlobalRoleRoundTripIntegrationTest {
   private static final String HOST = "host-org";
   private static final String ORG1 = "p77327a";
   private static final String ORG2 = "p77327b";
   private static final IdentityID G_ROLE = new IdentityID("gRole77327", null);
   // the id a caller addresses a global role with
   private static final IdentityID G_ROLE_PATH = new IdentityID("gRole77327", "__GLOBAL__");
   private static final IdentityID ROLES_ROOT = new IdentityID("Roles", ORG1);
   private static final IdentityID U1 = new IdentityID("u1", ORG1);
   private static final IdentityID U2 = new IdentityID("u2", ORG2);

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private SecurityService service;
   private SRPrincipal delegate;
   private SRPrincipal siteAdmin;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      // the global role gRole77327 is held by u1 of org1 and u2 of org2; delegate, a user of
      // org1 that is not an org admin, holds ADMIN on org1's Roles root, which covers the role
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77327", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77327", HOST)
         .addOrg("Name77327a", ORG1)
         .addOrg("Name77327b", ORG2)
         .addGlobalRole(G_ROLE.name)
         .addUser("delegate", ORG1, "password")
         .addUser(U1.name, ORG1, "password")
         .addUser(U2.name, ORG2, "password")
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      // the builder assigns a role in the user's org, so a global role is given directly
      for(IdentityID id : new IdentityID[] { U1, U2 }) {
         FSUser user = (FSUser) fileProvider.getUser(id);
         user.setRoles(new IdentityID[] { G_ROLE });
         fileProvider.setUser(id, user);
      }

      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("delegate"), ORG1);
      permission.updateGrantAllByOrg(ORG1, true);
      chain.setPermission(ResourceType.SECURITY_ROLE, ROLES_ROOT, permission, ORG1);

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      service = new SecurityService(
         SecurityEngine.getSecurity(), SecurityServiceRoleAssignmentIntegrationTest.newIdentityService(),
         mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class, RETURNS_DEEP_STUBS), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class), mock(CustomThemesManager.class));

      delegate = builder.principalOf("delegate", ORG1);
      siteAdmin = builder.principalOf("sa", HOST);

      assertFalse(OrganizationManager.getInstance().isOrgAdmin(delegate),
                  "precondition: delegate is not an organization administrator");
      assertTrue(OrganizationManager.getInstance().isSiteAdmin(siteAdmin),
                 "precondition: sa is a site administrator");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(chain != null) {
         chain.removePermission(ResourceType.SECURITY_ROLE, ROLES_ROOT, ORG1);
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void siteAdmin_globalRoleRoundTrip_keepsHoldersOfEveryOrg() throws Exception {
      SecurityRole body = asCaller(siteAdmin, HOST, () -> service.getRole(G_ROLE_PATH, siteAdmin));
      // the holders are of other orgs than the site admin's host org, so both are given by key
      assertEquals(Set.of(U1.convertToKey(), U2.convertToKey()),
                   new HashSet<>(body.getAssignedUsers()));

      asCaller(siteAdmin, HOST, () -> {
         service.updateRole(G_ROLE_PATH, body, siteAdmin);
         return null;
      });

      assertTrue(hasGlobalRole(U1), "u1 keeps the global role");
      assertTrue(hasGlobalRole(U2), "u2 keeps the global role");
   }

   @Test
   void delegate_globalRoleRoundTrip_otherOrgHolderNotReportedAndKept() throws Exception {
      assertTrue(admin(delegate, ResourceType.SECURITY_ROLE, G_ROLE_PATH),
                 "precondition: the Roles root grant covers the global role");
      assertFalse(admin(delegate, ResourceType.SECURITY_USER, U2),
                  "precondition: the delegate can't administer u2 of org2");
      SecurityRole body = asCaller(delegate, ORG1, () -> service.getRole(G_ROLE_PATH, delegate));
      assertEquals(List.of(U1.name), body.getAssignedUsers(),
                   "org2's holder is neither named nor keyed");

      asCaller(delegate, ORG1, () -> {
         service.updateRole(G_ROLE_PATH, body, delegate);
         return null;
      });

      assertTrue(hasGlobalRole(U1), "u1 keeps the global role");
      assertTrue(hasGlobalRole(U2), "u2 of org2 keeps the global role");
   }

   private interface Call<T> {
      T run() throws Exception;
   }

   private static <T> T asCaller(SRPrincipal caller, String org, Call<T> call) throws Exception {
      ThreadContext.setContextPrincipal(caller);
      return OrganizationManager.runInOrgScope(org, call::run);
   }

   private boolean admin(SRPrincipal caller, ResourceType type, IdentityID id) throws Exception {
      return asCaller(caller, ORG1, () -> SecurityEngine.getSecurity().getSecurityProvider()
         .checkPermission(caller, type, id.convertToKey(), ResourceAction.ADMIN));
   }

   private boolean hasGlobalRole(IdentityID user) {
      IdentityID[] roles = fileProvider.getUser(user).getRoles();
      return roles != null && Arrays.asList(roles).contains(G_ROLE);
   }
}
