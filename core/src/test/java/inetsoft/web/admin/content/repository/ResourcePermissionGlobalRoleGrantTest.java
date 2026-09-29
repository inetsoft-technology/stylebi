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

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77254: only a site admin may change the org-less (global) role grants of a
 * resource. A non site admin's save must keep them unchanged, even if the request body
 * carries crafted global role rows.
 */
@Tag("core")
class ResourcePermissionGlobalRoleGrantTest {
   private static final String PATH = "X";
   private static final ResourceType TYPE = ResourceType.REPORT;
   private static final ResourceAction READ = ResourceAction.READ;

   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<SecurityEngine> securityEngineStatic;
   private OrganizationManager orgManager;
   private AuthorizationProvider authz;
   private ResourcePermissionService service;
   private Principal principal;

   @BeforeEach
   void setUp() {
      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      sreeEnv = mockStatic(SreeEnv.class);
      securityEngineStatic = mockStatic(SecurityEngine.class);

      authz = mock(AuthorizationProvider.class);
      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(authz);

      service = spy(new ResourcePermissionService(
         securityProvider, mock(SecurityEngine.class), mock(LibManagerProvider.class),
         mock(DataSourceRegistry.class)));
      // the caller is authorized for every stored identity, so the pre-seed keeps nothing
      // and only the global role handling decides what happens to R1@null
      doReturn(true).when(service).isIdentityAuthorized(any(), any(), any());
      principal = mock(Principal.class);
   }

   @AfterEach
   void tearDown() {
      securityEngineStatic.close();
      sreeEnv.close();
      orgManagerStatic.close();
   }

   // org admin, crafted body replaces R1@null with R2@null -> R1 kept, R2 not added
   @Test
   void orgAdminCannotReplaceGlobalRoleGrant() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", false);

      save(role("Analyst", "orga"), role("R2", null));

      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(permission));
   }

   // org admin, crafted body adds Administrator@null -> not added, R1 kept
   @Test
   void orgAdminCannotAddAdministratorGlobalRoleGrant() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", false);

      save(role("Analyst", "orga"), role("Administrator", null), role("R1", null));

      assertEquals(Set.of("Analyst@orga", "R1@null"), roleGrants(permission));
   }

   // normal EM round-trip for an org admin (global rows hidden by GET) is unchanged
   @Test
   void orgAdminNormalRoundTripKeepsGlobalRoleGrant() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", false);

      save(role("Analyst", "orga"), role("Viewer", "orga"));

      assertEquals(Set.of("Analyst@orga", "Viewer@orga", "R1@null"), roleGrants(permission));
   }

   // org admin empty body clears org grants only, global grant kept (unchanged behavior)
   @Test
   void orgAdminEmptyBodyKeepsGlobalRoleGrant() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", false);

      save();

      assertEquals(Set.of("R1@null"), roleGrants(permission));
   }

   // multi-tenant site admin can still replace the global role grants
   @Test
   void siteAdminCanReplaceGlobalRoleGrant() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", true);

      save(role("Analyst", "orga"), role("R2", null));

      assertEquals(Set.of("Analyst@orga", "R2@null"), roleGrants(permission));
   }

   // multi-tenant site admin empty body still clears global role grants
   @Test
   void siteAdminEmptyBodyClearsGlobalRoleGrant() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", true);

      save();

      assertEquals(Set.of(), roleGrants(permission));
   }

   // single-tenant site admin: default-org roles persist, global grants can be changed/cleared
   @Test
   void singleTenantSiteAdminCanChangeGlobalRoleGrant() throws Exception {
      String defaultOrg = Organization.getDefaultOrganizationID();
      Permission permission = storedPermission(defaultOrg);
      caller(defaultOrg, true);

      save(role("Analyst", defaultOrg), role("Administrator", null));
      assertEquals(Set.of("Analyst@" + defaultOrg, "Administrator@null"), roleGrants(permission));

      save(role("Analyst", defaultOrg));
      assertEquals(Set.of("Analyst@" + defaultOrg), roleGrants(permission));
   }

   // enforcement: after an org admin's crafted save, a real permission check still grants
   // READ to a holder of the site-admin-placed R1@null, and not to a holder of only R2@null
   @Test
   void orgAdminCraftedSaveDoesNotChangeGlobalRoleEnforcement() throws Exception {
      Permission permission = storedPermission("orga");
      caller("orga", false);

      save(role("Analyst", "orga"), role("R2", null));

      SecurityProvider provider = mock(SecurityProvider.class);
      lenient().when(provider.getAllRoles(any(IdentityID[].class)))
         .thenAnswer(inv -> inv.getArgument(0));
      lenient().when(provider.getRoles(any())).thenReturn(new IdentityID[0]);
      lenient().when(provider.getUserGroups(any())).thenReturn(new String[0]);
      lenient().when(provider.getAllGroups(any(IdentityID[].class))).thenReturn(new IdentityID[0]);
      lenient().when(provider.getAuthenticationProvider())
         .thenReturn(mock(AuthenticationProvider.class));
      lenient().when(provider.getPermission(eq(TYPE), eq(PATH), eq("orga"))).thenReturn(permission);
      DefaultCheckPermissionStrategy strategy = new DefaultCheckPermissionStrategy(provider);

      try(MockedStatic<SUtil> sutil = Mockito.mockStatic(SUtil.class, Mockito.CALLS_REAL_METHODS);
          MockedStatic<XSessionService> session = Mockito.mockStatic(XSessionService.class))
      {
         session.when(XSessionService::getService).thenReturn(mock(XSessionService.class));
         sutil.when(SUtil::isMultiTenant).thenReturn(true);
         sutil.when(() -> SUtil.isInternalUser(any())).thenReturn(false);

         assertTrue(strategy.checkPermission(user("u1", new IdentityID("R1", null)), TYPE, PATH, READ),
                    "holder of the site-admin-granted global role keeps READ");
         assertFalse(strategy.checkPermission(user("u2", new IdentityID("R2", null)), TYPE, PATH, READ),
                     "holder of only the crafted global role gets no READ");
      }
   }

   private static SRPrincipal user(String name, IdentityID role) {
      return new SRPrincipal(new IdentityID(name, "orga"), new IdentityID[]{ role },
                             new String[0], "orga", Tool.getSecureRandom().nextLong());
   }

   private Permission storedPermission(String orgID) {
      Permission permission = new Permission();
      permission.setRoleGrantsForOrg(READ, Set.of("R1"), null);
      permission.setRoleGrantsForOrg(READ, Set.of("Analyst"), orgID);
      when(authz.getPermission(TYPE, PATH)).thenReturn(permission);
      return permission;
   }

   private void caller(String orgID, boolean siteAdmin) {
      when(orgManager.getCurrentOrgID()).thenReturn(orgID);
      when(orgManager.getCurrentOrgID(any(Principal.class))).thenReturn(orgID);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(siteAdmin);
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
