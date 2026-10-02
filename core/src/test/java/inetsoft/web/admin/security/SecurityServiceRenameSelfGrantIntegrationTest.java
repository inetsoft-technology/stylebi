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
import inetsoft.uql.util.Identity;
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
 * Bug #77114: a REST GET of a user/group/role returns its admin identities, including its own
 * ADMIN self grant under its current name. PUTting that body back with a new name stored the old
 * name as the renamed identity's grantee, because setIdentity() (syncIdentity ->
 * updateIdentityPermissions) renamed the grantee first and setIdentityPermissions() then replaced
 * the grantees with the stale request list.
 *
 * Unlike SecurityServiceTest (mocked IdentityService, captured argument), this drives the real
 * SecurityService GET and PUT against the real IdentityService (setIdentity included) and the
 * real FileAuthenticationProvider / FileAuthorizationProvider, and asserts the stored permission and,
 * for user and group, SecurityEngine.checkPermission(). The PUT runs in the target org scope, as the
 * controller's @SwitchOrg does.
 *
 * LicenseManager.isEnterprise() is false on the community test classpath, so SUtil.isMultiTenant()
 * would be false too. Both are mocked to true, as the multi-tenant REST API runs with them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceRenameSelfGrantIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceRenameSelfGrantIntegrationTest {
   private static final String HOST = "host-org";
   private static final String ORG_ID = "p77114";

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private SecurityService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<LicenseManager> licenseStatic;
   private SRPrincipal siteAdmin;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      licenseStatic = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
      licenseStatic.when(LicenseManager::isEnterprise).thenReturn(true);

      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77114", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77114", HOST)
         .addOrg("Name77114", ORG_ID)
         .addUser("sales", ORG_ID, "password")
         .addGroup("g", ORG_ID)
         .addUser("gm", ORG_ID, "password")
         .addUserToGroup("gm", "g", ORG_ID)
         .addRole("r", ORG_ID)
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      // deep stubs: getUser() reads localizationSettingsService.getModel().locales()
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
         for(String name : new String[] { "sales", "sales3" }) {
            chain.removePermission(ResourceType.SECURITY_USER, new IdentityID(name, ORG_ID), ORG_ID);
         }

         for(String name : new String[] { "g", "g3" }) {
            chain.removePermission(ResourceType.SECURITY_GROUP, new IdentityID(name, ORG_ID), ORG_ID);
         }

         for(String name : new String[] { "r", "r3" }) {
            chain.removePermission(ResourceType.SECURITY_ROLE, new IdentityID(name, ORG_ID), ORG_ID);
         }
      }

      if(fileProvider != null) {
         if(fileProvider.getUser(new IdentityID("sales3", ORG_ID)) != null) {
            fileProvider.removeUser(new IdentityID("sales3", ORG_ID));
         }

         if(fileProvider.getGroup(new IdentityID("g3", ORG_ID)) != null) {
            fileProvider.removeGroup(new IdentityID("g3", ORG_ID));
         }

         if(fileProvider.getRole(new IdentityID("r3", ORG_ID)) != null) {
            fileProvider.removeRole(new IdentityID("r3", ORG_ID));
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
   void userGetThenPutWithNewName_selfGrantNamesRenamedUser() throws Exception {
      IdentityID sales = new IdentityID("sales", ORG_ID);
      IdentityID sales3 = new IdentityID("sales3", ORG_ID);
      selfGrant(ResourceType.SECURITY_USER, sales, Identity.USER);

      SecurityUser body = OrganizationManager.runInOrgScope(
         ORG_ID, () -> service.getUser(sales, siteAdmin));
      assertTrue(body.getAdminIdentities().getUsers().contains(sales),
                 "precondition: the GET returns the self grant under the old name");
      body.setIdentityID(sales3);
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateUser(sales, body, siteAdmin);
         return null;
      });

      assertNotNull(fileProvider.getUser(sales3), "precondition: the user was renamed");
      Permission permission = chain.getPermission(ResourceType.SECURITY_USER, sales3, ORG_ID);
      assertNotNull(permission, "the self grant moves to the new key");
      assertEquals(Set.of(sales3), permission.getOrgScopedUserGrants(ResourceAction.ADMIN, ORG_ID),
                   "the renamed user's self grant must name the renamed user, not the old name");
      assertNull(chain.getPermission(ResourceType.SECURITY_USER, sales, ORG_ID),
                 "the old key is removed");
      assertTrue(canAdmin("sales3", ResourceType.SECURITY_USER, sales3),
                 "the renamed user must still administer itself");
   }

   @Test
   void groupGetThenPutWithNewName_selfGrantNamesRenamedGroup() throws Exception {
      IdentityID g = new IdentityID("g", ORG_ID);
      IdentityID g3 = new IdentityID("g3", ORG_ID);
      selfGrant(ResourceType.SECURITY_GROUP, g, Identity.GROUP);

      SecurityGroup body = OrganizationManager.runInOrgScope(
         ORG_ID, () -> service.getGroup(g, siteAdmin));
      assertTrue(body.getAdminIdentities().getGroups().contains(g),
                 "precondition: the GET returns the self grant under the old name");
      body.setIdentityID(g3);
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateGroup(g, body, siteAdmin);
         return null;
      });

      assertNotNull(fileProvider.getGroup(g3), "precondition: the group was renamed");
      Permission permission = chain.getPermission(ResourceType.SECURITY_GROUP, g3, ORG_ID);
      assertNotNull(permission, "the self grant moves to the new key");
      assertEquals(Set.of(g3), permission.getOrgScopedGroupGrants(ResourceAction.ADMIN, ORG_ID),
                   "the renamed group's self grant must name the renamed group");
      assertNull(chain.getPermission(ResourceType.SECURITY_GROUP, g, ORG_ID),
                 "the old key is removed");
      assertTrue(canAdmin("gm", ResourceType.SECURITY_GROUP, g3),
                 "a member of the renamed group must still administer the group");
   }

   @Test
   void roleGetThenPutWithNewName_assignGrantNamesRenamedRole() throws Exception {
      IdentityID r = new IdentityID("r", ORG_ID);
      IdentityID r3 = new IdentityID("r3", ORG_ID);
      selfGrant(ResourceType.SECURITY_ROLE, r, Identity.ROLE);

      SecurityRole body = OrganizationManager.runInOrgScope(
         ORG_ID, () -> service.getRole(r, siteAdmin));
      assertTrue(body.getAdminIdentities().getRoles().contains(r),
                 "precondition: the GET returns the self grant under the old name");
      body.setIdentityID(r3);
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateRole(r, body, siteAdmin);
         return null;
      });

      assertNotNull(fileProvider.getRole(r3), "precondition: the role was renamed");
      Permission permission = chain.getPermission(ResourceType.SECURITY_ROLE, r3, ORG_ID);
      assertNotNull(permission, "the self grant moves to the new key");
      // The REST role GET reads the ADMIN grantees, but the PUT writes them as ASSIGN for a non-root
      // role (IdentityService.setIdentityPermissions). The seeded ADMIN self grant is renamed by
      // setIdentity() and left in place by the PUT, so both actions name the renamed role.
      assertEquals(Set.of(r3), permission.getOrgScopedRoleGrants(ResourceAction.ASSIGN, ORG_ID),
                   "the renamed role's ASSIGN grant names the renamed role");
      assertEquals(Set.of(r3), permission.getOrgScopedRoleGrants(ResourceAction.ADMIN, ORG_ID),
                   "the seeded ADMIN self grant names the renamed role");
      assertNull(chain.getPermission(ResourceType.SECURITY_ROLE, r, ORG_ID),
                 "the old key is removed");
   }

   private void selfGrant(ResourceType type, IdentityID id, int identityType) {
      Permission permission = new Permission();

      switch(identityType) {
      case Identity.USER:
         permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of(id.name), ORG_ID);
         break;
      case Identity.GROUP:
         permission.setGroupGrantsForOrg(ResourceAction.ADMIN, Set.of(id.name), ORG_ID);
         break;
      default:
         permission.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of(id.name), ORG_ID);
      }

      permission.updateGrantAllByOrg(ORG_ID, true);
      chain.setPermission(type, id, permission, ORG_ID);
   }

   private boolean canAdmin(String userName, ResourceType type, IdentityID resource)
      throws Exception
   {
      SRPrincipal user = builder.principalOf(userName, ORG_ID);

      try {
         ThreadContext.setContextPrincipal(user);
         return SecurityEngine.getSecurity().checkPermission(
            user, type, resource.convertToKey(), ResourceAction.ADMIN);
      }
      finally {
         ThreadContext.setContextPrincipal(siteAdmin);
      }
   }

   // same construction as SecurityServiceOmittedFieldsIntegrationTest.newIdentityService()
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
