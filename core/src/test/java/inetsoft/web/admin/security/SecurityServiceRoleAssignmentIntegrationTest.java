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
import inetsoft.web.security.auth.UnauthorizedAccessException;
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
 * Bug #77381: a caller that is neither a site nor an organization administrator, holding ADMIN
 * over a user, could add the Organization Administrator role (or a role or parent group leading
 * to an organization administrator role) to that user through the public API.
 *
 * Unlike SecurityServiceTest (mocked IdentityService) and IdentityServiceRoleAssignmentTest
 * (mocked provider and permission checks), this drives the real SecurityService PUT and POST
 * against the real IdentityService, FileAuthenticationProvider, authorization chain, permission
 * strategy and OrganizationManager, with non-site-admin callers, as
 * SecurityServiceOmittedFieldsIntegrationTest does for site admins.
 *
 * LicenseManager.isEnterprise() is false on the community test classpath, so SUtil.isMultiTenant()
 * would be false too. Both are mocked to true, as the multi-tenant REST API runs with them; without
 * multi-tenancy the "Organization Administrator" role is hidden from the org admin checks.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceRoleAssignmentIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceRoleAssignmentIntegrationTest {
   private static final String HOST = "host-org";
   private static final String ORG_ID = "p77381";

   private static final IdentityID OA = new IdentityID("Organization Administrator", null);
   private static final IdentityID BOSS_ROLE = new IdentityID("boss77381", ORG_ID);
   private static final IdentityID PLAIN = new IdentityID("plain77381", ORG_ID);
   private static final IdentityID OTHER = new IdentityID("other77381", ORG_ID);
   private static final IdentityID BOSSES = new IdentityID("bosses", ORG_ID);
   private static final IdentityID VICTIM = new IdentityID("victim", ORG_ID);
   private static final IdentityID NEW_USER = new IdentityID("new77381", ORG_ID);
   // the "*" identity root, used as both the SECURITY_USER and the SECURITY_ROLE root
   private static final IdentityID IDENTITY_ROOT = new IdentityID("*", ORG_ID);

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private SecurityService service;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<LicenseManager> licenseStatic;
   private SRPrincipal delegate;
   private SRPrincipal boss;
   private MockedStatic<Audit> auditStatic;
   private boolean addedOa;

   @BeforeEach
   void setUp() throws Exception {
      sUtilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sUtilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      licenseStatic = mockStatic(LicenseManager.class, CALLS_REAL_METHODS);
      licenseStatic.when(LicenseManager::isEnterprise).thenReturn(true);

      // boss holds an org-scoped organization administrator role; delegate holds no role and
      // only the ADMIN grants below; the group "bosses" carries the organization admin role
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77381", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77381", HOST)
         .addOrg("Name77381", ORG_ID)
         .addOrgAdminRole(BOSS_ROLE.name, ORG_ID)
         .addRole(PLAIN.name, ORG_ID)
         .addRole(OTHER.name, ORG_ID)
         .addGroup(BOSSES.name, ORG_ID)
         .addRoleToGroup(BOSS_ROLE.name, BOSSES.name, ORG_ID)
         .addUser("delegate", ORG_ID, "password")
         .addUser("boss", ORG_ID, "password")
         .addUser(VICTIM.name, ORG_ID, "password")
         .addUserToRole("boss", BOSS_ROLE.name, ORG_ID)
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      if(fileProvider.getRole(OA) == null) {
         FSRole oa = new FSRole(OA, new IdentityID[0], "");
         oa.setOrgAdmin(true);
         fileProvider.addRole(oa);
         addedOa = true;
      }

      grant(ResourceType.SECURITY_USER, VICTIM, ResourceAction.ADMIN, "delegate", "boss");
      grant(ResourceType.SECURITY_GROUP, BOSSES, ResourceAction.ADMIN, "delegate");
      grant(ResourceType.SECURITY_ROLE, PLAIN, ResourceAction.ASSIGN, "delegate");

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      service = new SecurityService(
         SecurityEngine.getSecurity(), newIdentityService(), mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class, RETURNS_DEEP_STUBS), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class),
         mock(CustomThemesManager.class));

      delegate = builder.principalOf("delegate", ORG_ID);
      boss = builder.principalOf("boss", ORG_ID);

      assertFalse(OrganizationManager.getInstance().isOrgAdmin(delegate),
                  "precondition: delegate is not an organization administrator");
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(boss),
                 "precondition: boss is an organization administrator");
      assertTrue(fileProvider.isOrgAdministratorRole(OA), "precondition: OA is an org admin role");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(chain != null) {
         chain.removePermission(ResourceType.SECURITY_USER, VICTIM, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_USER, IDENTITY_ROOT, ORG_ID);
         // clears a grant createUser may have stored on the user it creates
         chain.removePermission(ResourceType.SECURITY_USER, NEW_USER, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_GROUP, BOSSES, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_ROLE, PLAIN, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_ROLE, IDENTITY_ROOT, ORG_ID);
      }

      if(fileProvider != null) {
         if(fileProvider.getUser(NEW_USER) != null) {
            fileProvider.removeUser(NEW_USER);
         }

         if(addedOa && fileProvider.getRole(OA) != null) {
            fileProvider.removeRole(OA);
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
   void delegate_putAddingOrgAdminRole_isRefused() throws Exception {
      assertSetIdentityRefused(() -> updateUser(delegate, userBody(List.of(OA), null)));
      assertSetIdentityRefused(() -> updateUser(delegate, userBody(List.of(BOSS_ROLE), null)));
      assertSetIdentityRefused(() -> updateUser(delegate, userBody(null, List.of(BOSSES.name))));
      assertEquals(Set.of(), roles(VICTIM), "the victim's roles must be unchanged");
      assertEquals(0, fileProvider.getUser(VICTIM).getGroups().length,
                   "the victim's groups must be unchanged");
   }

   @Test
   void delegate_putAddingRoleWithoutAssign_isRefused_withAssign_isSaved() throws Exception {
      assertSetIdentityRefused(() -> updateUser(delegate, userBody(List.of(OTHER), null)));
      assertEquals(Set.of(), roles(VICTIM));

      updateUser(delegate, userBody(List.of(PLAIN), null));
      assertEquals(Set.of(PLAIN), roles(VICTIM), "a role the delegate may assign is saved");

      // an unchanged re-save keeps a stored role the delegate could not assign
      setRoles(VICTIM, PLAIN, OA);
      updateUser(delegate, userBody(List.of(PLAIN, OA), null));
      assertEquals(Set.of(PLAIN, OA), roles(VICTIM));
   }

   @Test
   void orgAdmin_putAddingOrgAdminRole_isSaved() throws Exception {
      updateUser(boss, userBody(List.of(OA, BOSS_ROLE), null));
      assertEquals(Set.of(OA, BOSS_ROLE), roles(VICTIM));
   }

   @Test
   void delegate_postCreatingUserWithOrgAdminRole_isRefused() throws Exception {
      grant(ResourceType.SECURITY_USER, IDENTITY_ROOT, ResourceAction.ADMIN, "delegate");
      grant(ResourceType.SECURITY_ROLE, IDENTITY_ROOT, ResourceAction.ADMIN, "delegate");

      // refused by the role assignment check, not by the create or roles-root gates
      assertRoleAssignmentRefused(() -> createUser(delegate, List.of(OA)));
      assertRoleAssignmentRefused(() -> createUser(delegate, List.of(BOSS_ROLE)));
      assertNull(fileProvider.getUser(NEW_USER), "the user must not be created");

      // positive control: the same caller passes both gates and the requested role is kept
      createUser(delegate, List.of(PLAIN));
      assertNotNull(fileProvider.getUser(NEW_USER), "the user must be created");
      assertEquals(Set.of(PLAIN), roles(NEW_USER), "the requested role is saved");
   }

   private interface Call {
      void run() throws Exception;
   }

   // an update is refused by IdentityService.setIdentity(), which SecurityService.updateUser()
   // calls after its pre-mutation checks, so the refusal is not a PreMutationRefusalException
   private static void assertSetIdentityRefused(Call call) {
      assertThrows(java.lang.SecurityException.class, call::run);
   }

   // a create is refused by SecurityService.checkAssignableRoles(); creates are not wrapped
   private static void assertRoleAssignmentRefused(Call call) {
      UnauthorizedAccessException error = assertThrows(UnauthorizedAccessException.class, call::run);
      assertEquals("Permission denied to assign the requested roles", error.getMessage());
   }

   private void updateUser(SRPrincipal caller, SecurityUser body) throws Exception {
      ThreadContext.setContextPrincipal(caller);
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.updateUser(VICTIM, body, caller);
         return null;
      });
   }

   private void createUser(SRPrincipal caller, List<IdentityID> roles) throws Exception {
      SecurityUser body = new SecurityUser();
      body.setIdentityID(NEW_USER);
      body.setPassword("Str0ng!Passw0rd77381");
      body.setRoles(roles);
      ThreadContext.setContextPrincipal(caller);
      OrganizationManager.runInOrgScope(ORG_ID, () -> {
         service.createUser(body, ORG_ID, caller);
         return null;
      });
   }

   private static SecurityUser userBody(List<IdentityID> roles, List<String> groups) {
      SecurityUser body = new SecurityUser();
      body.setIdentityID(VICTIM);
      body.setRoles(roles);
      body.setGroups(groups);
      return body;
   }

   private Set<IdentityID> roles(IdentityID user) {
      IdentityID[] roles = fileProvider.getUser(user).getRoles();
      return roles == null ? Set.of() : new HashSet<>(Arrays.asList(roles));
   }

   private void setRoles(IdentityID userId, IdentityID... roles) {
      FSUser user = (FSUser) fileProvider.getUser(userId);
      user.setRoles(roles);
      fileProvider.setUser(userId, user);
   }

   private void grant(ResourceType type, IdentityID id, ResourceAction action, String... users) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(action, Set.of(users), ORG_ID);
      permission.updateGrantAllByOrg(ORG_ID, true);
      chain.setPermission(type, id, permission, ORG_ID);
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
