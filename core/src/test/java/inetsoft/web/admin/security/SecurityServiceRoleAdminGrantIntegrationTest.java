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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/*
 * Bug #77868: the REST/Shell role GET read the ADMIN grantees of a role, while the role PUT
 * writes them as ASSIGN (community IdentityService.setIdentityPermissions), so the GET returned
 * an empty adminIdentities and a GET then PUT cleared the grant. The role POST wrote the listed
 * grantees as ADMIN. A global role's grant is written in the caller's current org storage, which
 * the GET did not read.
 *
 * This drives the real SecurityService against the real IdentityService,
 * FileAuthenticationProvider and authorization chain, and asserts the stored grants. Calls run in
 * the caller's org scope, as the controller's @SwitchOrg does.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  PermissionMatrixOrgLifecycleTest.CopyOnReadClusterConfig.class,
                                  SecurityServiceRoleAssignmentIntegrationTest.TestBeans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SecurityServiceRoleAdminGrantIntegrationTest {
   private static final String HOST = "host-org";
   private static final String ORG_ID = "p77868";

   private static final IdentityID SALES = new IdentityID("sales", ORG_ID);
   private static final IdentityID GM = new IdentityID("gm", ORG_ID);
   private static final IdentityID DELEGATE = new IdentityID("delegate", ORG_ID);
   private static final IdentityID OADM = new IdentityID("oadm", ORG_ID);
   private static final IdentityID H1 = new IdentityID("h1", HOST);
   private static final IdentityID G = new IdentityID("g", ORG_ID);
   private static final IdentityID R = new IdentityID("r", ORG_ID);
   private static final IdentityID R9 = new IdentityID("r9", ORG_ID);
   private static final IdentityID ROLES_ROOT = new IdentityID("Roles", ORG_ID);
   private static final IdentityID ORG_ROLES_ROOT = new IdentityID("Organization Roles", ORG_ID);
   private static final IdentityID G_ROLE = new IdentityID("gRole77868", null);
   // the id a caller addresses a global role with
   private static final IdentityID G_ROLE_PATH = new IdentityID("gRole77868", "__GLOBAL__");

   private SecurityTestDataBuilder builder;
   private AuthorizationChain chain;
   private FileAuthenticationProvider fileProvider;
   private IdentityService identityService;
   private SecurityService service;
   private SRPrincipal siteAdmin;
   private SRPrincipal delegate;
   private SRPrincipal orgAdmin;
   private MockedStatic<Audit> auditStatic;

   @BeforeEach
   void setUp() throws Exception {
      // delegate, a user of the org that is not an org admin, holds ADMIN on the org's role roots
      // and on sales
      builder = SecurityTestDataBuilder.create()
         .addOrg(Organization.getDefaultOrganizationName(), HOST)
         .addSysAdminRole("SiteAdmin77868", HOST)
         .addUser("sa", HOST, "password")
         .addUserToRole("sa", "SiteAdmin77868", HOST)
         .addUser(H1.name, HOST, "password")
         .addOrg("Name77868", ORG_ID)
         .addUser(SALES.name, ORG_ID, "password")
         .addUser(GM.name, ORG_ID, "password")
         .addUser(DELEGATE.name, ORG_ID, "password")
         .addOrgAdminRole("OrgAdmin77868", ORG_ID)
         .addUser(OADM.name, ORG_ID, "password")
         .addUserToRole(OADM.name, "OrgAdmin77868", ORG_ID)
         .addGroup(G.name, ORG_ID)
         .addRole(R.name, ORG_ID)
         .addGlobalRole(G_ROLE.name)
         .setup();

      chain = SecurityEngine.getSecurity().getAuthorizationChain()
         .orElseThrow(() -> new AssertionError("expected an AuthorizationChain"));
      fileProvider = (FileAuthenticationProvider)
         ((AuthenticationChain) SecurityEngine.getSecurity().getSecurityProvider()
            .getAuthenticationProvider()).getProviders().get(0);

      grant(ResourceType.SECURITY_ROLE, ROLES_ROOT, ResourceAction.ADMIN, DELEGATE);
      grant(ResourceType.SECURITY_ROLE, ORG_ROLES_ROOT, ResourceAction.ADMIN, DELEGATE);
      grant(ResourceType.SECURITY_USER, SALES, ResourceAction.ADMIN, DELEGATE);

      Audit audit = mock(Audit.class);
      auditStatic = mockStatic(Audit.class);
      auditStatic.when(Audit::getInstance).thenReturn(audit);

      identityService = SecurityServiceRoleAssignmentIntegrationTest.newIdentityService();
      service = new SecurityService(
         SecurityEngine.getSecurity(), identityService, mock(ActionPermissionService.class),
         mock(LocalizationSettingsService.class, RETURNS_DEEP_STUBS), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class), mock(CustomThemesManager.class));

      siteAdmin = builder.principalOf("sa", HOST);
      delegate = builder.principalOf(DELEGATE.name, ORG_ID);
      orgAdmin = builder.principalOf(OADM.name, ORG_ID);

      assertFalse(OrganizationManager.getInstance().isOrgAdmin(delegate),
                  "precondition: delegate is not an organization administrator");
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);

      if(auditStatic != null) {
         auditStatic.close();
      }

      if(chain != null) {
         for(IdentityID id : new IdentityID[] { R, R9, ROLES_ROOT, ORG_ROLES_ROOT, G_ROLE }) {
            chain.removePermission(ResourceType.SECURITY_ROLE, id, ORG_ID);
         }

         chain.removePermission(ResourceType.SECURITY_ROLE, G_ROLE, HOST);
         chain.removePermission(ResourceType.SECURITY_USER, SALES, ORG_ID);
         chain.removePermission(ResourceType.SECURITY_GROUP, G, ORG_ID);
      }

      if(fileProvider != null && fileProvider.getRole(R9) != null) {
         fileProvider.removeRole(R9);
      }

      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // the reported case: a role PUT stores ASSIGN, and the GET returns it
   @Test
   void orgRolePutThenGet_returnsGrantee() throws Exception {
      updateRole(siteAdmin, ORG_ID, R, roleBody(R, SALES));

      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN, ORG_ID));
      assertEquals(List.of(SALES), getRole(siteAdmin, ORG_ID, R).getAdminIdentities().getUsers());
      assertEquals(List.of(SALES), getRole(delegate, ORG_ID, R).getAdminIdentities().getUsers());
   }

   // an unchanged GET then PUT keeps the grant
   @Test
   void orgRoleGetThenPut_keepsGrant() throws Exception {
      updateRole(siteAdmin, ORG_ID, R, roleBody(R, SALES));

      SecurityRole body = getRole(siteAdmin, ORG_ID, R);
      updateRole(siteAdmin, ORG_ID, R, body);

      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN, ORG_ID));
      assertEquals(List.of(SALES), getRole(siteAdmin, ORG_ID, R).getAdminIdentities().getUsers());
   }

   // a group is still written and read as ADMIN
   @Test
   void groupPutThenGet_unchanged() throws Exception {
      SecurityGroup body = new SecurityGroup();
      body.setIdentityID(G);
      body.setAdminIdentities(admins(SALES));
      asCaller(siteAdmin, ORG_ID, () -> {
         service.updateGroup(G, body, siteAdmin);
         return null;
      });

      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_GROUP, G, ResourceAction.ADMIN, ORG_ID));
      SecurityGroup read = asCaller(siteAdmin, ORG_ID, () -> service.getGroup(G, siteAdmin));
      assertEquals(List.of(SALES), read.getAdminIdentities().getUsers());
   }

   // a role POST gives the listed grantees ASSIGN and the creator ADMIN, as stored
   @Test
   void createRole_listedGranteesAssign_creatorAdmin() throws Exception {
      SecurityRole body = roleBody(R9, SALES);
      asCaller(delegate, ORG_ID, () -> {
         service.createRole(body, ORG_ID, delegate);
         return null;
      });

      assertNotNull(fileProvider.getRole(R9), "precondition: the role was created");
      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, R9, ResourceAction.ASSIGN, ORG_ID));
      assertEquals(Set.of(DELEGATE), grantees(ResourceType.SECURITY_ROLE, R9, ResourceAction.ADMIN, ORG_ID),
                   "only the creator holds ADMIN, no listed grantee does");
      assertEquals(List.of(SALES), getRole(siteAdmin, ORG_ID, R9).getAdminIdentities().getUsers());

      // the String overload EM's role pane uses reads the same grant
      List<IdentityModel> emRead = asCaller(siteAdmin, ORG_ID, () -> identityService.getPermission(
         R9.convertToKey(), ResourceType.SECURITY_ROLE, siteAdmin));
      assertEquals(Set.of(SALES), emRead.stream().map(IdentityModel::identityID).collect(Collectors.toSet()));
   }

   // a role POST without adminIdentities writes no grant at all, as before
   @Test
   void createRole_omittedAdminIdentities_writesNoGrant() throws Exception {
      SecurityRole body = new SecurityRole();
      body.setIdentityID(R9);
      asCaller(delegate, ORG_ID, () -> {
         service.createRole(body, ORG_ID, delegate);
         return null;
      });

      assertNotNull(fileProvider.getRole(R9), "precondition: the role was created");
      assertNull(chain.getPermission(ResourceType.SECURITY_ROLE, R9, ORG_ID));
   }

   // a legacy ADMIN grant on a role is not shown, and a PUT writes ASSIGN without touching it
   @Test
   void legacyAdminGrant_hiddenAndKeptByPut() throws Exception {
      grant(ResourceType.SECURITY_ROLE, R, ResourceAction.ADMIN, GM);

      assertEquals(List.of(), getRole(siteAdmin, ORG_ID, R).getAdminIdentities().getUsers());

      updateRole(siteAdmin, ORG_ID, R, roleBody(R, SALES));

      assertEquals(Set.of(GM), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ADMIN, ORG_ID));
      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN, ORG_ID));
   }

   // a global role's grant is read in the caller's current org, where the PUT writes it, by the
   // role GET and by the role list
   @Test
   void siteAdmin_globalRolePutThenGetAndList_returnsCurrentOrgGrantee() throws Exception {
      updateRole(siteAdmin, HOST, G_ROLE_PATH, roleBody(G_ROLE_PATH, H1));

      assertEquals(Set.of(H1), grantees(ResourceType.SECURITY_ROLE, G_ROLE, ResourceAction.ASSIGN, HOST));
      SecurityRole body = getRole(siteAdmin, HOST, G_ROLE_PATH);
      assertEquals(List.of(H1), body.getAdminIdentities().getUsers());

      SecurityRole listed = asCaller(siteAdmin, HOST, () -> service.getRoles("", siteAdmin))
         .getRoles().stream()
         .filter(role -> G_ROLE.equals(role.getIdentityID()))
         .findFirst()
         .orElseThrow(() -> new AssertionError("the role list has the global role"));
      assertEquals(List.of(H1), listed.getAdminIdentities().getUsers());

      // an unchanged GET then PUT keeps the grant
      updateRole(siteAdmin, HOST, G_ROLE_PATH, body);
      assertEquals(Set.of(H1), grantees(ResourceType.SECURITY_ROLE, G_ROLE, ResourceAction.ASSIGN, HOST));
   }

   // a Roles-root delegate's global-role grant is in its own org, and is not shown to a caller
   // whose current org is another one
   @Test
   void delegate_globalRoleGrantIsPerOrganization() throws Exception {
      updateRole(delegate, ORG_ID, G_ROLE_PATH, roleBody(G_ROLE_PATH, SALES));

      SecurityRole body = getRole(delegate, ORG_ID, G_ROLE_PATH);
      assertEquals(List.of(SALES), body.getAdminIdentities().getUsers());
      updateRole(delegate, ORG_ID, G_ROLE_PATH, body);
      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, G_ROLE, ResourceAction.ASSIGN, ORG_ID));

      assertEquals(List.of(), getRole(siteAdmin, HOST, G_ROLE_PATH).getAdminIdentities().getUsers(),
                   "the org's grant is not shown in the host org");
   }

   // the reporter's caller: an org admin PUTs a role's grantees, reads them back, and an
   // unchanged GET then PUT keeps them; the same caller's group PUT/GET is the control
   @Test
   void orgAdmin_rolePutGetPut_keepsGrant() throws Exception {
      assertTrue(OrganizationManager.getInstance().isOrgAdmin(orgAdmin),
                 "precondition: oadm is an organization administrator");

      updateRole(orgAdmin, ORG_ID, R, roleBody(R, SALES));
      SecurityRole body = getRole(orgAdmin, ORG_ID, R);
      assertEquals(List.of(SALES), body.getAdminIdentities().getUsers());

      updateRole(orgAdmin, ORG_ID, R, body);
      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, R, ResourceAction.ASSIGN, ORG_ID));
      assertEquals(List.of(SALES), getRole(orgAdmin, ORG_ID, R).getAdminIdentities().getUsers());

      SecurityGroup group = new SecurityGroup();
      group.setIdentityID(G);
      group.setAdminIdentities(admins(SALES));
      asCaller(orgAdmin, ORG_ID, () -> {
         service.updateGroup(G, group, orgAdmin);
         return null;
      });
      SecurityGroup readGroup = asCaller(orgAdmin, ORG_ID, () -> service.getGroup(G, orgAdmin));
      assertEquals(List.of(SALES), readGroup.getAdminIdentities().getUsers());
   }

   // a role created with grantees keeps them through an unchanged GET then PUT, and the
   // creator's ADMIN self grant is not touched by the PUT
   @Test
   void createdRoleGetThenPut_keepsGrant() throws Exception {
      SecurityRole created = roleBody(R9, SALES);
      asCaller(orgAdmin, ORG_ID, () -> {
         service.createRole(created, ORG_ID, orgAdmin);
         return null;
      });

      SecurityRole body = getRole(orgAdmin, ORG_ID, R9);
      assertEquals(List.of(SALES), body.getAdminIdentities().getUsers());

      updateRole(orgAdmin, ORG_ID, R9, body);
      assertEquals(Set.of(SALES), grantees(ResourceType.SECURITY_ROLE, R9, ResourceAction.ASSIGN, ORG_ID));
      assertEquals(Set.of(OADM), grantees(ResourceType.SECURITY_ROLE, R9, ResourceAction.ADMIN, ORG_ID));
      assertEquals(List.of(SALES), getRole(orgAdmin, ORG_ID, R9).getAdminIdentities().getUsers());
   }

   private interface Call<T> {
      T run() throws Exception;
   }

   private static <T> T asCaller(SRPrincipal caller, String org, Call<T> call) throws Exception {
      ThreadContext.setContextPrincipal(caller);
      return OrganizationManager.runInOrgScope(org, call::run);
   }

   private SecurityRole getRole(SRPrincipal caller, String org, IdentityID path) throws Exception {
      return asCaller(caller, org, () -> service.getRole(path, caller));
   }

   private void updateRole(SRPrincipal caller, String org, IdentityID path, SecurityRole body)
      throws Exception
   {
      asCaller(caller, org, () -> {
         service.updateRole(path, body, caller);
         return null;
      });
   }

   private static SecurityRole roleBody(IdentityID id, IdentityID grantee) {
      SecurityRole body = new SecurityRole();
      body.setIdentityID(id);
      body.setAdminIdentities(admins(grantee));
      return body;
   }

   private static AdminIdentities admins(IdentityID user) {
      AdminIdentities ids = new AdminIdentities();
      ids.setUsers(List.of(user));
      ids.setGroups(List.of());
      ids.setRoles(List.of());
      ids.setOrganizations(List.of());
      return ids;
   }

   // grants the user the action on the identity, in the org's permission storage
   private void grant(ResourceType type, IdentityID id, ResourceAction action, IdentityID user) {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(action, Set.of(user.name), ORG_ID);
      permission.updateGrantAllByOrg(ORG_ID, true);
      chain.setPermission(type, id, permission, ORG_ID);
   }

   private Set<IdentityID> grantees(ResourceType type, IdentityID id, ResourceAction action,
                                    String org)
   {
      Permission permission = chain.getPermission(type, id, org);
      assertNotNull(permission, "no grant at " + id);
      return permission.getOrgScopedUserGrants(action, org);
   }
}
