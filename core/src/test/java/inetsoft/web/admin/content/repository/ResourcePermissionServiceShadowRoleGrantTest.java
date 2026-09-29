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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Identity;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Tool;
import inetsoft.web.admin.security.ResourcePermissionModel;
import inetsoft.web.admin.security.ResourcePermissionTableModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77276: an org admin's permission save must not copy an org-less (global) role grant
 * R1@null into the org as R1@orga. Unlike ResourcePermissionGlobalRoleGrantTest, the real
 * isIdentityAuthorized() runs here, backed by a real DefaultCheckPermissionStrategy, so the
 * pre-seed sees the global role as not authorized for an org admin, as it is in production.
 */
@Tag("core")
class ResourcePermissionServiceShadowRoleGrantTest {
   private static final String ORG = "orga";
   private static final String PATH = "X";
   private static final ResourceType TYPE = ResourceType.REPORT;
   private static final ResourceAction READ = ResourceAction.READ;
   private static final IdentityID ORG_ADMIN_ROLE =
      new IdentityID("Organization Administrator", null);

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<XSessionService> session;
   private OrganizationManager orgManager;
   private AuthorizationProvider authz;
   private SecurityProvider provider;
   private final Map<IdentityID, Role> roles = new HashMap<>();
   private ResourcePermissionService service;
   private SRPrincipal principal;

   @BeforeEach
   void setUp() {
      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      orgManagerStatic.when(OrganizationManager::getCurrentOrgName).thenReturn(ORG);
      sreeEnv = mockStatic(SreeEnv.class);
      securityEngineStatic = mockStatic(SecurityEngine.class);
      sutil = mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
      sutil.when(SUtil::isMultiTenant).thenReturn(true);
      sutil.when(() -> SUtil.isInternalUser(any())).thenReturn(false);
      session = mockStatic(XSessionService.class);
      session.when(XSessionService::getService).thenReturn(mock(XSessionService.class));

      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(any())).thenReturn(ORG);

      authz = mock(AuthorizationProvider.class);
      provider = mock(SecurityProvider.class);
      lenient().when(provider.getAuthorizationProvider()).thenReturn(authz);
      lenient().when(provider.getAuthenticationProvider())
         .thenReturn(mock(AuthenticationProvider.class));
      lenient().when(provider.getOrganizationIDs()).thenReturn(new String[]{ ORG });
      Organization organization = mock(Organization.class);
      lenient().when(organization.getOrganizationID()).thenReturn(ORG);
      lenient().when(organization.getId()).thenReturn(ORG);
      lenient().when(organization.getRoles()).thenReturn(new IdentityID[0]);
      lenient().when(provider.getOrganization(anyString())).thenReturn(organization);
      lenient().when(provider.getOrgNameFromID(anyString())).thenReturn(ORG);
      lenient().when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.getArgument(0)));
      lenient().when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(provider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(provider.getAllGroups(any(IdentityID[].class))).thenReturn(new IdentityID[0]);
      lenient().when(provider.getAllRoles(any(IdentityID[].class)))
         .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(provider.isSystemAdministratorRole(any())).thenReturn(false);
      lenient().when(provider.isOrgAdministratorRole(any()))
         .thenAnswer(inv -> ORG_ADMIN_ROLE.equals(inv.getArgument(0)));

      // the real permission check, so isIdentityAuthorized() is not stubbed
      DefaultCheckPermissionStrategy strategy = new DefaultCheckPermissionStrategy(provider);
      lenient().when(provider.checkPermission(any(), any(ResourceType.class), anyString(),
                                              any(ResourceAction.class)))
         .thenAnswer(inv -> strategy.checkPermission(inv.getArgument(0), inv.getArgument(1),
                                                     inv.getArgument(2), inv.getArgument(3)));

      SecurityEngine securityEngine = mock(SecurityEngine.class);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(provider);
      service = new ResourcePermissionService(
         provider, securityEngine, mock(LibManagerProvider.class), mock(DataSourceRegistry.class));

      addRole(ORG_ADMIN_ROLE);
      addRole(new IdentityID("R1", null));
      addRole(new IdentityID("Analyst", ORG));
      principal = new SRPrincipal(new IdentityID("oadmin", ORG), new IdentityID[]{ ORG_ADMIN_ROLE },
                                  new String[0], ORG, Tool.getSecureRandom().nextLong());
   }

   @AfterEach
   void tearDown() {
      session.close();
      sutil.close();
      securityEngineStatic.close();
      sreeEnv.close();
      orgManagerStatic.close();
   }

   // the real check sees the global R1 as not manageable by an org admin, the org role as
   // manageable (the premise of the pre-seed)
   @Test
   void orgAdminIsNotAuthorizedForGlobalRole() {
      siteAdmin(false);

      assertFalse(service.isIdentityAuthorized(new IdentityID("R1", null), Identity.Type.ROLE,
                                               principal));
      assertTrue(service.isIdentityAuthorized(new IdentityID("Analyst", ORG), Identity.Type.ROLE,
                                              principal));
   }

   // normal org admin round trip (GET hides R1@null): no R1@orga shadow written, R1@null kept
   @Test
   void orgAdminSaveDoesNotShadowGlobalRoleGrant() throws Exception {
      Permission permission = stored("R1@null", "Analyst@orga");
      siteAdmin(false);

      save(role("Analyst", ORG));
      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(permission));

      save(role("Analyst", ORG));
      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(permission));
   }

   // an org admin can revoke a real org role R1@orga that shares its name with a global R1
   @Test
   void orgAdminCanRevokeSameNamedOrgRole() throws Exception {
      addRole(new IdentityID("R1", ORG));
      Permission permission = stored("R1@null", "R1@orga", "Analyst@orga");
      siteAdmin(false);

      save(role("Analyst", ORG));

      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(permission));
   }

   // after the org admin's save, a holder of a same-named org role gets no READ through
   // the global grant
   @Test
   void orgAdminSaveDoesNotGrantSameNamedOrgRole() throws Exception {
      addRole(new IdentityID("R1", ORG));
      Permission permission = stored("R1@null", "Analyst@orga");
      siteAdmin(false);

      save(role("Analyst", ORG));

      SecurityProvider checkProvider = mock(SecurityProvider.class);
      lenient().when(checkProvider.getAllRoles(any(IdentityID[].class)))
         .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(checkProvider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(checkProvider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(checkProvider.getAllGroups(any(IdentityID[].class)))
         .thenReturn(new IdentityID[0]);
      lenient().when(checkProvider.getAuthenticationProvider())
         .thenReturn(mock(AuthenticationProvider.class));
      lenient().when(checkProvider.getPermission(eq(TYPE), eq(PATH), eq(ORG)))
         .thenReturn(permission);
      DefaultCheckPermissionStrategy strategy = new DefaultCheckPermissionStrategy(checkProvider);

      assertFalse(strategy.checkPermission(user("u1", new IdentityID("R1", ORG)), TYPE, PATH, READ),
                  "holder of the org role R1@orga gets no READ from the global R1 grant");
      assertTrue(strategy.checkPermission(user("u2", new IdentityID("R1", null)), TYPE, PATH, READ),
                 "holder of the global role R1 keeps READ");
   }

   // site admin behavior is unchanged: the global grant can be kept or removed
   @Test
   void siteAdminSaveUnchanged() throws Exception {
      Permission permission = stored("R1@null", "Analyst@orga");
      siteAdmin(true);

      save(role("Analyst", ORG), role("R1", null));
      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(permission));

      save(role("Analyst", ORG));
      assertEquals(Set.of("Analyst@orga"), roleGrants(permission));
   }

   private void addRole(IdentityID id) {
      roles.put(id, new Role(id));
   }

   private void siteAdmin(boolean siteAdmin) {
      when(orgManager.isSiteAdmin(any(java.security.Principal.class))).thenReturn(siteAdmin);
   }

   private Permission stored(String... grants) {
      Map<String, Set<String>> byOrg = new HashMap<>();

      for(String grant : grants) {
         String[] parts = grant.split("@");
         String org = "null".equals(parts[1]) ? null : parts[1];
         byOrg.computeIfAbsent(org, k -> new HashSet<>()).add(parts[0]);
      }

      Permission permission = new Permission();
      byOrg.forEach((org, names) -> permission.setRoleGrantsForOrg(READ, names, org));
      when(authz.getPermission(TYPE, PATH)).thenReturn(permission);
      return permission;
   }

   private void save(ResourcePermissionTableModel... rows) throws Exception {
      ResourcePermissionModel model = ResourcePermissionModel.builder()
         .permissions(Arrays.asList(rows))
         .displayActions(EnumSet.of(READ))
         .securityEnabled(true)
         .requiresBoth(false)
         .derivePermissionLabel("")
         .grantReadToAllVisible(false)
         .build();
      service.setResourcePermissions(PATH, TYPE, model, principal);
   }

   private static SRPrincipal user(String name, IdentityID role) {
      return new SRPrincipal(new IdentityID(name, ORG), new IdentityID[]{ role },
                             new String[0], ORG, Tool.getSecureRandom().nextLong());
   }

   private static ResourcePermissionTableModel role(String name, String orgID) {
      return ResourcePermissionTableModel.builder()
         .identityID(new IdentityID(name, orgID))
         .type(Identity.Type.ROLE)
         .actions(EnumSet.of(READ))
         .build();
   }

   private static Set<String> roleGrants(Permission permission) {
      return permission.getAllRoleGrants(READ).stream()
         .map(p -> p.getName() + "@" + p.getOrganizationID())
         .collect(Collectors.toSet());
   }
}
