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
 * Issue #77078: the EM group edit checked ADMIN on the group in the path, but built every id it
 * edited (and the organization setGroupInfo rewrites memberships in) from the organization and old
 * name in the request body, so an org admin could write memberships and a permission record into
 * another organization.
 */

import inetsoft.sree.internal.DataCycleManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.IndexedStorage;
import inetsoft.web.admin.security.AuthenticationProviderService;
import inetsoft.web.admin.security.IdentityService;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class UserTreeServiceEditGroupOrgTest {
   @BeforeEach
   void setUp() {
      sUtilStatic = mockStatic(SUtil.class, withSettings().strictness(Strictness.LENIENT));
      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG_A);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      orgManagerStatic = mockStatic(OrganizationManager.class,
                                    withSettings().strictness(Strictness.LENIENT));
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      provider = mock(EditableAuthenticationProvider.class, withSettings().lenient());
      providerService = mock(AuthenticationProviderService.class, withSettings().lenient());
      when(providerService.getProviderByName("Primary")).thenReturn(provider);

      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      when(securityProvider.getOrganization(ORG_A)).thenReturn(new FSOrganization(ORG_A));
      when(securityProvider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(securityProvider.getGroupMembers(any())).thenReturn(new Identity[0]);
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      systemAdminService = mock(SystemAdminService.class, withSettings().lenient());
      when(systemAdminService.hasSysAdmin(any())).thenReturn(true);
      when(systemAdminService.hasOrgAdmin(any())).thenReturn(true);

      identityService = mock(IdentityService.class);
      themeService = mock(IdentityThemeService.class);
      storage = mock(IndexedStorage.class);
      cycleManager = mock(DataCycleManager.class);
      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("adminA", ORG_A).convertToKey());

      service = new UserTreeService(
         providerService, systemAdminService, identityService, null, securityEngine, themeService,
         null, null, cycleManager, null, null, storage, null, null, null, null, null);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      sUtilStatic.close();
   }

   @Test
   void bodyOrgDiffersFromPathOrg_rejectedWithoutWrites() {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", pathGroup, model("sales", "sales2", ORG_B), principal));

      assertNothingWritten();
   }

   @Test
   void bodyOrgDiffersFromPathOrg_membershipOnlyEdit_rejectedWithoutWrites() {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", pathGroup, model("sales", "sales", ORG_B), principal));

      assertNothingWritten();
   }

   @Test
   void bodyOldNameDiffersFromPathName_rejectedWithoutWrites() {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", pathGroup, model("victim", "sales2", ORG_A), principal));

      assertNothingWritten();
   }

   // the body organization defaults to the host organization when it is omitted
   @Test
   void bodyOrgOmitted_pathInOtherOrg_rejectedWithoutWrites() {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      when(provider.getGroup(pathGroup)).thenReturn(new FSGroup(pathGroup));
      EditGroupPaneModel model = EditGroupPaneModel.builder()
         .name("sales")
         .oldName("sales")
         .build();

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", pathGroup, model, principal));

      assertNothingWritten();
   }

   @Test
   void rootBranch_bodyOrgDiffersFromPathOrg_rejectedWithoutWrites() {
      IdentityID pathRoot = new IdentityID("Groups", ORG_A);

      assertThrows(java.lang.SecurityException.class, () ->
         service.editGroup("Primary", pathRoot, model("Groups", "Groups", ORG_B), principal));

      assertNothingWritten();
   }

   @Test
   void matchingRename_editsPathGroup() throws Exception {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      FSGroup oldGroup = new FSGroup(pathGroup);
      when(provider.getGroup(pathGroup)).thenReturn(oldGroup);
      EditGroupPaneModel model = model("sales", "sales2", ORG_A);

      service.editGroup("Primary", pathGroup, model, principal);

      verify(identityService).setIdentity(oldGroup, model, provider, principal);
      verify(themeService).updateTheme(eq("sales"), eq("sales2"), eq(ORG_A), any());
      verify(identityService).setIdentityPermissions(
         eq(pathGroup), eq(new IdentityID("sales2", ORG_A)), eq(ResourceType.SECURITY_GROUP),
         eq(principal), any(), eq(""));
      verify(storage).migrateStorageData("sales", "sales2");
   }

   @Test
   void matchingMembershipOnlyEdit_editsPathGroup() throws Exception {
      IdentityID pathGroup = new IdentityID("sales", ORG_A);
      FSGroup oldGroup = new FSGroup(pathGroup);
      when(provider.getGroup(pathGroup)).thenReturn(oldGroup);
      EditGroupPaneModel model = model("sales", "sales", ORG_A);

      service.editGroup("Primary", pathGroup, model, principal);

      verify(identityService).setIdentity(oldGroup, model, provider, principal);
      verify(identityService).setIdentityPermissions(
         eq(pathGroup), eq(pathGroup), eq(ResourceType.SECURITY_GROUP), eq(principal), any(),
         eq(""));
   }

   @Test
   void rootBranch_matching_permissionsWrittenOnPathOrgRoot() throws Exception {
      IdentityID pathRoot = new IdentityID("Groups", ORG_A);

      service.editGroup("Primary", pathRoot, model("Groups", "Groups", ORG_A), principal);

      verify(securityProvider).checkPermission(
         principal, ResourceType.SECURITY_GROUP, pathRoot.convertToKey(), ResourceAction.ADMIN);
      verify(identityService).setIdentityPermissions(
         eq(pathRoot), eq(pathRoot), eq(ResourceType.SECURITY_GROUP), eq(principal), any(),
         eq(ORG_A));
      verify(identityService, never()).setIdentity(any(), any(), any(), any());
   }

   // a site admin managing another organization sends that organization in both the path and the
   // body, which must keep working
   @Test
   void siteAdmin_editsGroupInOtherOrg_withConsistentPathAndBody() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      IdentityID pathGroup = new IdentityID("sales", ORG_B);
      FSGroup oldGroup = new FSGroup(pathGroup);
      when(provider.getGroup(pathGroup)).thenReturn(oldGroup);
      EditGroupPaneModel model = model("sales", "sales2", ORG_B);

      service.editGroup("Primary", pathGroup, model, principal);

      verify(identityService).setIdentity(oldGroup, model, provider, principal);
      verify(themeService).updateTheme(eq("sales"), eq("sales2"), eq(ORG_B), any());
      verify(identityService).setIdentityPermissions(
         eq(pathGroup), eq(new IdentityID("sales2", ORG_B)), eq(ResourceType.SECURITY_GROUP),
         eq(principal), any(), eq(""));
   }

   private void assertNothingWritten() {
      verifyNoInteractions(identityService, themeService, storage, cycleManager);
      verify(securityProvider, never()).checkPermission(any(), any(), anyString(), any());
      verify(provider, never()).getGroup(any());
   }

   private static EditGroupPaneModel model(String oldName, String name, String orgID) {
      return EditGroupPaneModel.builder()
         .name(name)
         .oldName(oldName)
         .organization(orgID)
         .members(List.of())
         .build();
   }

   private static final String ORG_A = "organizationA";
   private static final String ORG_B = "organizationB";
   private EditableAuthenticationProvider provider;
   private AuthenticationProviderService providerService;
   private SecurityProvider securityProvider;
   private SystemAdminService systemAdminService;
   private IdentityService identityService;
   private IdentityThemeService themeService;
   private IndexedStorage storage;
   private DataCycleManager cycleManager;
   private Principal principal;
   private UserTreeService service;
   private OrganizationManager orgManager;
   private MockedStatic<SUtil> sUtilStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
