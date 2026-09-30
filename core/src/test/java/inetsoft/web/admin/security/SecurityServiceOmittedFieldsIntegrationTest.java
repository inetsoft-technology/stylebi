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

import inetsoft.report.internal.license.ElasticLicenseService;
import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77326: a REST PUT of a role, group or user that omitted a field cleared it. Omitted
 * inheritedRoles/roles/groups became an empty list, an omitted description, alias or email was
 * cleared, an omitted active flag re-enabled a disabled user, and an omitted adminIdentities
 * emptied the identity's ADMIN (ASSIGN for a role) grant.
 *
 * This drives the real SecurityService PUT against the real IdentityService (setIdentity and
 * setIdentityPermissions included) and the real FileAuthenticationProvider / authorization chain,
 * as SecurityServiceRenameSelfGrantIntegrationTest does, and asserts the stored identities and
 * grants. The PUT runs in the target org scope, as the controller's @SwitchOrg does. The locale is
 * covered by SecurityServiceTest, since this harness has no locale properties.
 *
 * LicenseManager.isEnterprise() is false on the community test classpath, so SUtil.isMultiTenant()
 * would be false too. Both are mocked to true, as the multi-tenant REST API runs with them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceOmittedFieldsIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceOmittedFieldsIntegrationTest {
   private static final String HOST = "host-org";
   private static final String ORG_ID = "p77326";

   private static final IdentityID SALES = new IdentityID("sales", ORG_ID);
   private static final IdentityID SALES3 = new IdentityID("sales3", ORG_ID);
   private static final IdentityID GM = new IdentityID("gm", ORG_ID);
   private static final IdentityID G = new IdentityID("g", ORG_ID);
   private static final IdentityID G3 = new IdentityID("g3", ORG_ID);
   private static final IdentityID R = new IdentityID("r", ORG_ID);
   private static final IdentityID R3 = new IdentityID("r3", ORG_ID);
   private static final IdentityID PARENT = new IdentityID("parent", ORG_ID);

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private SecurityService service;
   private SRPrincipal siteAdmin;
   private MockedStatic<Audit> auditStatic;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<LicenseManager> licenseStatic;

   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      licenseStatic = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
      licenseStatic.when(LicenseManager::isEnterprise).thenReturn(true);

      // r inherits parent, g holds parent, and sales holds parent and is a member of g
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77326", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77326", HOST)
         .addOrg("Name77326", ORG_ID)
         .addUser("sales", ORG_ID, "password")
         .addUser("gm", ORG_ID, "password")
         .addGroup("g", ORG_ID)
         .addUserToGroup("sales", "g", ORG_ID)
         .addUserToGroup("gm", "g", ORG_ID)
         .addRole("parent", ORG_ID)
         .addRole("r", ORG_ID)
         .addRoleParent("r", "parent", ORG_ID)
         .addRoleToGroup("parent", "g", ORG_ID)
         .addUserToRole("sales", "parent", ORG_ID)
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      FSRole role = new FSRole(R, fileProvider.getRole(R).getRoles(), "keep me");
      role.setOrganization(ORG_ID);
      fileProvider.setRole(R, role);

      FSUser user = (FSUser) fileProvider.getUser(SALES);
      user.setEmails(new String[] { "a@b.c" });
      user.setAlias("Ali");
      user.setActive(false);
      fileProvider.setUser(SALES, user);

      grant(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN);
      grant(ResourceType.SECURITY_GROUP, G, ResourceAction.ADMIN);
      grant(ResourceType.SECURITY_USER, SALES, ResourceAction.ADMIN);

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      service = new SecurityService(
         SecurityEngine.getSecurity(), newIdentityService(), mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class, RETURNS_DEEP_STUBS), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class),
         mock(CustomThemesManager.class));

      siteAdmin = builder.principalOf("sa", HOST);
      ThreadContext.setContextPrincipal(siteAdmin);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(chain != null) {
         for(IdentityID id : new IdentityID[] { SALES, SALES3 }) {
            chain.removePermission(ResourceType.SECURITY_USER, id, ORG_ID);
         }

         for(IdentityID id : new IdentityID[] { G, G3 }) {
            chain.removePermission(ResourceType.SECURITY_GROUP, id, ORG_ID);
         }

         for(IdentityID id : new IdentityID[] { R, R3 }) {
            chain.removePermission(ResourceType.SECURITY_ROLE, id, ORG_ID);
         }
      }

      if(fileProvider != null) {
         if(fileProvider.getUser(SALES3) != null) {
            fileProvider.removeUser(SALES3);
         }

         if(fileProvider.getGroup(G3) != null) {
            fileProvider.removeGroup(G3);
         }

         if(fileProvider.getRole(R3) != null) {
            fileProvider.removeRole(R3);
         }
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }

      if(licenseStatic != null) {
         licenseStatic.close();
      }

      if(sUtilStatic != null) {
         sUtilStatic.close();
      }
   }

   @Test
   void rolePutWithOnlyIdentityId_keepsInheritedRolesDescriptionAndAssignGrant() throws Exception {
      updateRole(R, roleBody(R));

      Role role = fileProvider.getRole(R);
      assertEquals(List.of(PARENT), Arrays.asList(role.getRoles()));
      assertEquals("keep me", role.getDescription());
      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN));
   }

   @Test
   void rolePutWithEmptyValues_clearsInheritedRolesDescriptionAndAssignGrant() throws Exception {
      SecurityRole body = roleBody(R);
      body.setInheritedRoles(List.of());
      body.setDescription("");
      body.setAdminIdentities(new AdminIdentities());
      updateRole(R, body);

      Role role = fileProvider.getRole(R);
      assertEquals(0, role.getRoles().length);
      assertTrue(role.getDescription() == null || role.getDescription().isEmpty(),
                 "description " + role.getDescription());
      assertEquals(Set.of(), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN));
   }

   @Test
   void groupPutWithOnlyIdentityId_keepsRolesAndAdminGrant() throws Exception {
      updateGroup(G, groupBody(G));

      assertEquals(List.of(PARENT), Arrays.asList(fileProvider.getGroup(G).getRoles()));
      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_GROUP, G, ResourceAction.ADMIN));
   }

   @Test
   void groupPutWithEmptyValues_clearsRolesAndAdminGrant() throws Exception {
      SecurityGroup body = groupBody(G);
      body.setRoles(List.of());
      body.setAdminIdentities(new AdminIdentities());
      updateGroup(G, body);

      assertEquals(0, fileProvider.getGroup(G).getRoles().length);
      assertEquals(Set.of(), grantees(ResourceType.SECURITY_GROUP, G, ResourceAction.ADMIN));
   }

   @Test
   void userPutWithOnlyIdentityId_keepsEveryField() throws Exception {
      updateUser(SALES, userBody(SALES));

      User user = fileProvider.getUser(SALES);
      assertEquals(List.of(PARENT), Arrays.asList(user.getRoles()));
      assertEquals(List.of("g"), Arrays.asList(user.getGroups()));
      assertEquals(List.of("a@b.c"), Arrays.asList(user.getEmails()));
      assertEquals("Ali", user.getAlias());
      assertFalse(user.isActive(), "an omitted active flag must not re-enable the user");
      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_USER, SALES, ResourceAction.ADMIN));
   }

   @Test
   void userPutWithEmptyValues_clearsFields() throws Exception {
      SecurityUser body = userBody(SALES);
      body.setRoles(List.of());
      body.setGroups(List.of());
      body.setEmails(List.of());
      body.setAlias("");
      body.setActive(true);
      body.setAdminIdentities(new AdminIdentities());
      updateUser(SALES, body);

      User user = fileProvider.getUser(SALES);
      assertEquals(0, user.getRoles().length);
      assertEquals(0, user.getGroups().length);
      assertEquals(0, user.getEmails().length);
      assertNull(user.getAlias());
      assertTrue(user.isActive());
      assertEquals(Set.of(), grantees(ResourceType.SECURITY_USER, SALES, ResourceAction.ADMIN));
   }

   @Test
   void renameWithOmittedAdminIdentities_movesGrantToNewKey() throws Exception {
      updateRole(R, roleBody(R3));
      updateGroup(G, groupBody(G3));
      updateUser(SALES, userBody(SALES3));

      assertNotNull(fileProvider.getRole(R3), "precondition: the role was renamed");
      assertNotNull(fileProvider.getGroup(G3), "precondition: the group was renamed");
      assertNotNull(fileProvider.getUser(SALES3), "precondition: the user was renamed");
      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_ROLE, R3, ResourceAction.ASSIGN));
      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_GROUP, G3, ResourceAction.ADMIN));
      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_USER, SALES3, ResourceAction.ADMIN));
      assertNull(chain.getPermission(ResourceType.SECURITY_ROLE, R, ORG_ID), "old role key");
      assertNull(chain.getPermission(ResourceType.SECURITY_GROUP, G, ORG_ID), "old group key");
      assertNull(chain.getPermission(ResourceType.SECURITY_USER, SALES, ORG_ID), "old user key");
   }

   // setIdentity() renames the user where it is a grantee, then the grant moves to the new key,
   // so a self grant names the renamed user (Bug #77114)
   @Test
   void renameWithOmittedAdminIdentities_selfGrantNamesRenamedUser() throws Exception {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(GM.name, SALES.name), ORG_ID);
      permission.updateGrantAllByOrg(ORG_ID, true);
      chain.setPermission(ResourceType.SECURITY_USER, SALES, permission, ORG_ID);

      updateUser(SALES, userBody(SALES3));

      assertNotNull(fileProvider.getUser(SALES3), "precondition: the user was renamed");
      assertEquals(Set.of(GM, SALES3),
                   grantees(ResourceType.SECURITY_USER, SALES3, ResourceAction.ADMIN));
      assertNull(chain.getPermission(ResourceType.SECURITY_USER, SALES, ORG_ID), "old user key");
   }

   @Test
   void renameWithOmittedAdminIdentitiesAndNoGrant_createsNoGrant() throws Exception {
      chain.removePermission(ResourceType.SECURITY_ROLE, R, ORG_ID);

      updateRole(R, roleBody(R3));

      assertNotNull(fileProvider.getRole(R3), "precondition: the role was renamed");
      assertNull(chain.getPermission(ResourceType.SECURITY_ROLE, R3, ORG_ID));
      assertNull(chain.getPermission(ResourceType.SECURITY_ROLE, R, ORG_ID));
   }

   private void updateRole(IdentityID path, SecurityRole body) throws Exception {
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateRole(path, body, siteAdmin);
         return null;
      });
   }

   private void updateGroup(IdentityID path, SecurityGroup body) throws Exception {
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateGroup(path, body, siteAdmin);
         return null;
      });
   }

   private void updateUser(IdentityID path, SecurityUser body) throws Exception {
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateUser(path, body, siteAdmin);
         return null;
      });
   }

   private static SecurityRole roleBody(IdentityID id) {
      SecurityRole body = new SecurityRole();
      body.setIdentityID(id);
      return body;
   }

   private static SecurityGroup groupBody(IdentityID id) {
      SecurityGroup body = new SecurityGroup();
      body.setIdentityID(id);
      return body;
   }

   private static SecurityUser userBody(IdentityID id) {
      SecurityUser body = new SecurityUser();
      body.setIdentityID(id);
      return body;
   }

   // grants user gm the action on the identity, in the org's permission storage
   private void grant(ResourceType type, IdentityID id, ResourceAction action) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(action, Set.of(GM.name), ORG_ID);
      permission.updateGrantAllByOrg(ORG_ID, true);
      chain.setPermission(type, id, permission, ORG_ID);
   }

   private Set<IdentityID> grantees(ResourceType type, IdentityID id, ResourceAction action) {
      Permission permission = chain.getPermission(type, id, ORG_ID);
      assertNotNull(permission, "no grant at " + id);
      return permission.getOrgScopedUserGrants(action, ORG_ID);
   }

   // same construction as SecurityServiceRenameSelfGrantIntegrationTest.newIdentityService()
   private static IdentityService newIdentityService() throws Exception {
      java.lang.reflect.Constructor<?> ctor = IdentityService.class.getConstructors()[0];
      Object[] args = new Object[ctor.getParameterCount()];
      Class<?>[] types = ctor.getParameterTypes();

      for(int i = 0; i < args.length; i++) {
         Class<?> type = types[i];

         if(type == SecurityEngine.class) {
            args[i] = SecurityEngine.getSecurity();
         }
         else if(type == SecurityProvider.class) {
            args[i] = SecurityEngine.getSecurity().getSecurityProvider();
         }
         else if(type == Cluster.class) {
            args[i] = Cluster.getInstance();
         }
         else if(type == inetsoft.util.DataSpace.class) {
            args[i] = inetsoft.util.DataSpace.getDataSpace();
         }
         else if(type == Optional.class) {
            args[i] = Optional.empty();
         }
         else {
            args[i] = mock(type, RETURNS_DEEP_STUBS);
         }
      }

      return (IdentityService) ctor.newInstance(args);
   }

   @Configuration
   static class TestBeans {
      @Bean
      public ElasticLicenseService elasticLicenseService() {
         return mock(ElasticLicenseService.class);
      }

      @Bean
      public CustomThemesManager customThemesManager() {
         return mock(CustomThemesManager.class, RETURNS_DEEP_STUBS);
      }

      @Bean
      public inetsoft.sree.RepletRegistryManager repletRegistryManager() {
         return mock(inetsoft.sree.RepletRegistryManager.class, RETURNS_DEEP_STUBS);
      }
   }
}
