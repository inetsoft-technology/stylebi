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
package inetsoft.web.admin.security.user;

/*
 * Test strategy
 *
 * Class type: request-scoped orchestration service — SecurityTreeServer resolves the
 * requested authentication provider and then delegates the per-identity-type tree building
 * to UserTreeService.
 *
 * Coverage scope:
 *   [unknown provider name]  getProviderByName() returns null -> MessageException, not NPE
 *   [no default provider]    provider name omitted and the chain has none -> same guard
 *   [providerChanged gate]   Bug #77079: curr_org_id / curr_provider_name are written on a
 *                            provider switch only when security is off or the caller is a
 *                            site admin. Axis: {site admin, org admin, delegate(org node),
 *                            delegate(root-only)} x {multi/single tenant} x {provider has /
 *                            lacks own org} x {security on/off}.
 *
 * Regression context: the EM Users tab sends whatever provider name the client has cached.
 * When that provider has been renamed or removed, getProviderByName() returns null and the
 * old code dereferenced it at provider.getOrganizationIDs(), producing a 500
 * "Cannot invoke ...getOrganizationIDs() because \"provider\" is null" with no usable message.
 *
 * Static singletons (SUtil, Catalog) are intercepted with Mockito.mockStatic() using lenient().
 */

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.util.XSessionService;
import inetsoft.util.Catalog;
import inetsoft.util.InvalidOrgException;
import inetsoft.util.MessageException;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.server.ServerService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
@ExtendWith(MockitoExtension.class)
class SecurityTreeServerTest {
   @Mock private AuthenticationProviderService authenticationProviderService;
   @Mock private ServerService serverService;
   @Mock private SecurityEngine securityEngine;
   @Mock private UserTreeService userTreeService;
   @Mock private SecurityProvider securityProvider;
   @Mock private Catalog catalog;
   @Mock private Principal principal;

   private SecurityTreeServer server;

   private MockedStatic<SUtil> sutilStatic;
   private MockedStatic<Catalog> catalogStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<LicenseManager> licenseStatic;
   private MockedStatic<XSessionService> sessionServiceStatic;
   @Mock private XSessionService sessionService;
   @Mock private OrganizationManager organizationManager;
   @Mock private AuthenticationProvider provider;

   @BeforeEach
   void setUp() {
      when(serverService.getLicenseInfos()).thenReturn(List.of());
      server = new SecurityTreeServer(
         authenticationProviderService, serverService, securityEngine, userTreeService);

      sutilStatic = mockStatic(SUtil.class, withSettings().lenient());
      catalogStatic = mockStatic(Catalog.class, withSettings().lenient());

      sutilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      catalogStatic.when(Catalog::getCatalog).thenReturn(catalog);
      // any(Object.class) rather than any(): it pins the getString(String, Object...) overload
      // instead of letting javac pick getString(String, boolean)
      lenient().when(catalog.getString(anyString(), any(Object.class)))
         .thenReturn("provider does not exist");
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      lenient().when(catalog.getString(anyString())).thenAnswer(inv -> inv.getArgument(0));

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
      // resolve the current org the way the real manager does: from the principal
      lenient().when(organizationManager.getCurrentOrgID(any(Principal.class)))
         .thenAnswer(inv -> ((XPrincipal) inv.getArgument(0)).getCurrentOrgId());

      licenseStatic = mockStatic(LicenseManager.class, withSettings().lenient());
      licenseStatic.when(LicenseManager::isEnterprise).thenReturn(true);

      // XPrincipal's constructor asks the (Spring) session service for a session id
      sessionServiceStatic = mockStatic(XSessionService.class, withSettings().lenient());
      sessionServiceStatic.when(XSessionService::getService).thenReturn(sessionService);
      lenient().when(sessionService.createSessionID(any(), any())).thenReturn("session");
   }

   @AfterEach
   void tearDown() {
      sutilStatic.close();
      catalogStatic.close();
      orgManagerStatic.close();
      licenseStatic.close();
      sessionServiceStatic.close();
   }

   // [Scenario: unknown provider name] the client asked for a provider that is no longer in
   // this node's authentication chain (renamed/removed, or a node that has not reloaded
   // authc-chain.json yet). Must report the missing provider instead of NPEing.
   @Test
   void getSecurityTree_unknownProviderName_throwsMessageExceptionNotNpe() {
      when(authenticationProviderService.getProviderByName("gone")).thenReturn(null);

      MessageException ex = assertThrows(MessageException.class,
         () -> server.getSecurityTree("gone", principal, false, false));

      assertEquals("provider does not exist", ex.getMessage());
      verify(catalog).getString("em.security.provider.notFound", "gone");
      verifyNoInteractions(userTreeService);
   }

   // [Scenario: no default provider] with the provider name omitted the chain's own
   // authentication provider is used; the same guard must cover a null there.
   @Test
   void getSecurityTree_noDefaultProvider_throwsMessageExceptionNotNpe() {
      when(securityProvider.getAuthenticationProvider()).thenReturn(null);

      assertThrows(MessageException.class,
         () -> server.getSecurityTree(null, principal, false, false));

      verifyNoInteractions(userTreeService);
   }

   // ---------------------------------------------------------------------------------------
   // Bug #77079: providerChanged=true must not let a non-site-admin move their EM session
   // into another organization (host-org fallback or the provider's first org).
   // ---------------------------------------------------------------------------------------

   private static final String HOST = Organization.getDefaultOrganizationID();

   private XPrincipal orgAUser() {
      return new XPrincipal(new IdentityID("D", "orga"));
   }

   private void multiTenant(String providerName, String... orgIds) {
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);
      when(authenticationProviderService.getProviderByName(providerName)).thenReturn(provider);
      lenient().when(provider.getOrganizationIDs()).thenReturn(orgIds);
      lenient().when(provider.getOrgNameFromID(anyString())).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(securityEngine.isSecurityEnabled()).thenReturn(true);
   }

   // [delegate(root-only), multi-tenant, provider has own org, security on] no org-level
   // ADMIN anywhere -> filtered list empty. Before the fix curr_org_id fell back to host-org.
   @Test
   void providerChanged_delegateWithEmptyOrgList_keepsOwnOrgAndProvider() {
      multiTenant("P", HOST, "orga");
      XPrincipal p = orgAUser();
      p.setProperty("curr_provider_name", "Home");
      lenient().when(organizationManager.isSiteAdmin(p)).thenReturn(false);
      lenient().when(securityProvider.checkPermission(eq(p), eq(ResourceType.SECURITY_ORGANIZATION),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(false);

      SecurityTreeRootModel model = server.getSecurityTree("P", p, false, true);

      assertNotNull(model);
      assertNull(p.getProperty("curr_org_id"));
      assertEquals("orga", p.getCurrentOrgId());
      assertEquals("Home", p.getProperty("curr_provider_name"));
      verify(userTreeService).createOrgSecurityTreeNode(eq(new IdentityID("orga", "orga")),
         eq("orga"), eq(provider), eq(p), eq(false), eq(false), eq(false));
   }

   // [delegate(org node) / org admin, multi-tenant, provider has own org] list is [orga]:
   // the tree is still org A and nothing is written for a non-site-admin.
   @Test
   void providerChanged_orgAdminProviderHasOwnOrg_buildsOwnOrgTreeWithoutWrite() {
      multiTenant("P", HOST, "orga");
      XPrincipal p = orgAUser();
      lenient().when(organizationManager.isSiteAdmin(p)).thenReturn(false);
      lenient().when(securityProvider.checkPermission(eq(p), eq(ResourceType.SECURITY_ORGANIZATION),
         anyString(), eq(ResourceAction.ADMIN))).thenAnswer(inv -> "orga".equals(inv.getArgument(2)));

      server.getSecurityTree("P", p, false, true);

      assertNull(p.getProperty("curr_org_id"));
      assertNull(p.getProperty("curr_provider_name"));
      verify(userTreeService).createOrgSecurityTreeNode(eq(new IdentityID("orga", "orga")),
         eq("orga"), eq(provider), eq(p), eq(false), eq(false), eq(false));
   }

   // [org admin, multi-tenant, provider lacks own org, security on] after #5646 the filtered
   // list is empty; before the fix curr_org_id became host-org and the org admin then worked
   // on host-org storage. Now: fail closed with InvalidOrgException, principal untouched, so
   // #5646's isTargetOutOfOrg (relative to curr_org_id) still sees org A.
   @Test
   void providerChanged_orgAdminProviderLacksOwnOrg_throwsInvalidOrgAndKeepsOrg() {
      multiTenant("Q", HOST, "orgb");
      XPrincipal p = orgAUser();
      lenient().when(organizationManager.isSiteAdmin(p)).thenReturn(false);
      lenient().when(securityProvider.checkPermission(eq(p), eq(ResourceType.SECURITY_ORGANIZATION),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(false);

      assertThrows(InvalidOrgException.class, () -> server.getSecurityTree("Q", p, false, true));

      assertNull(p.getProperty("curr_org_id"));
      assertNull(p.getProperty("curr_provider_name"));
      assertEquals("orga", p.getCurrentOrgId());
      verifyNoInteractions(userTreeService);
   }

   // [any non-site-admin, single-tenant, security on] the UI dropdown sends
   // providerChanged=true legitimately: tree built, nothing written, no exception.
   @Test
   void providerChanged_singleTenantNonSiteAdmin_buildsTreeWithoutWrite() {
      when(authenticationProviderService.getProviderByName("P")).thenReturn(provider);
      when(provider.getOrganizationIDs()).thenReturn(new String[] { HOST });
      lenient().when(provider.getOrgNameFromID(anyString())).thenAnswer(inv -> inv.getArgument(0));
      lenient().when(securityEngine.isSecurityEnabled()).thenReturn(true);
      XPrincipal p = new XPrincipal(new IdentityID("u", HOST));
      lenient().when(organizationManager.isSiteAdmin(p)).thenReturn(false);

      SecurityTreeRootModel model = assertDoesNotThrow(
         () -> server.getSecurityTree("P", p, false, true));

      assertNotNull(model);
      assertNull(p.getProperty("curr_org_id"));
      assertNull(p.getProperty("curr_provider_name"));
      verify(userTreeService).getUserRoot(provider, p, false, HOST, HOST);
   }

   // [site admin, multi-tenant] the legitimate switch: first ADMIN org by the org
   // comparator, and curr_provider_name = the chosen provider (preserved behaviour).
   @Test
   void providerChanged_siteAdmin_switchesToFirstAdminOrg() {
      multiTenant("P", "orgc", "orgb");
      XPrincipal p = new XPrincipal(new IdentityID("admin", HOST));
      lenient().when(organizationManager.isSiteAdmin(p)).thenReturn(true);
      when(securityProvider.checkPermission(eq(p), eq(ResourceType.SECURITY_ORGANIZATION),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      server.getSecurityTree("P", p, false, true);

      assertEquals("orgb", p.getProperty("curr_org_id"));
      assertEquals("P", p.getProperty("curr_provider_name"));
      verify(userTreeService).createOrgSecurityTreeNode(eq(new IdentityID("orgb", "orgb")),
         eq("orgb"), eq(provider), eq(p), eq(false), eq(false), eq(false));
   }

   // [site admin, multi-tenant, empty filtered list] host-org fallback is kept.
   @Test
   void providerChanged_siteAdminEmptyList_fallsBackToHostOrg() {
      multiTenant("P", HOST, "orgb");
      XPrincipal p = new XPrincipal(new IdentityID("admin", "orgb"));
      lenient().when(organizationManager.isSiteAdmin(p)).thenReturn(true);
      when(securityProvider.checkPermission(eq(p), eq(ResourceType.SECURITY_ORGANIZATION),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(false);

      server.getSecurityTree("P", p, false, true);

      assertEquals(HOST, p.getProperty("curr_org_id"));
      assertEquals("P", p.getProperty("curr_provider_name"));
   }

   // [security off] no site-admin concept: switching still writes the properties.
   @Test
   void providerChanged_securityDisabled_stillSwitches() {
      multiTenant("P", "orgb");
      lenient().when(securityEngine.isSecurityEnabled()).thenReturn(false);
      XPrincipal p = new XPrincipal(new IdentityID("anonymous", HOST));
      when(securityProvider.checkPermission(eq(p), eq(ResourceType.SECURITY_ORGANIZATION),
         anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      server.getSecurityTree("P", p, false, true);

      assertEquals("orgb", p.getProperty("curr_org_id"));
      assertEquals("P", p.getProperty("curr_provider_name"));
   }
}
