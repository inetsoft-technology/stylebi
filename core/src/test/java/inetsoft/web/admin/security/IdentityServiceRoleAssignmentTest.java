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

import inetsoft.sree.security.*;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.security.user.*;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77381: a caller that is not a site administrator, holding only ADMIN over a user (or
 * group, or role), must not be able to add a role it may not assign, and must not be able to
 * grant an organization administrator role, directly, by inheritance or through a parent group,
 * unless it is itself an organization administrator. The rule mirrors the roles the EM role
 * tree offers as assignable (UserTreeService.getOrgRoleList).
 */
@Tag("core")
class IdentityServiceRoleAssignmentTest {
   private static final String ORG = "orga";
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);
   private static final IdentityID FLAGGED_ROLE = new IdentityID("OrgBoss", ORG);
   private static final IdentityID INHERITS_OA = new IdentityID("OaChild", ORG);
   private static final IdentityID DESIGNER = new IdentityID("Designer", ORG);
   private static final IdentityID ANALYST = new IdentityID("Analyst", ORG);
   private static final IdentityID GLOBAL_PLAIN = new IdentityID("GlobalPlain", null);
   private static final IdentityID OTHER_ORG_ROLE = new IdentityID("Analyst", "orgb");
   private static final IdentityID OA_GROUP = new IdentityID("bosses", ORG);
   private static final IdentityID OA_SUBGROUP = new IdentityID("subBosses", ORG);
   private static final IdentityID PLAIN_GROUP = new IdentityID("plain", ORG);

   private IdentityService service;
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private final Set<String> grants = new HashSet<>();
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      Map<IdentityID, Role> roles = new HashMap<>();
      FSRole orgAdmin = new FSRole(ORG_ADMIN_ROLE);
      orgAdmin.setOrgAdmin(true);
      roles.put(ORG_ADMIN_ROLE, orgAdmin);
      FSRole flagged = new FSRole(FLAGGED_ROLE);
      flagged.setOrgAdmin(true);
      roles.put(FLAGGED_ROLE, flagged);
      roles.put(INHERITS_OA, new FSRole(INHERITS_OA, new IdentityID[] { ORG_ADMIN_ROLE }));
      roles.put(DESIGNER, new FSRole(DESIGNER));
      roles.put(ANALYST, new FSRole(ANALYST));
      roles.put(GLOBAL_PLAIN, new FSRole(GLOBAL_PLAIN));
      roles.put(OTHER_ORG_ROLE, new FSRole(OTHER_ORG_ROLE));

      Map<IdentityID, Group> groups = new HashMap<>();
      groups.put(OA_GROUP, new FSGroup(OA_GROUP, null, new String[0],
                                       new IdentityID[] { FLAGGED_ROLE }));
      groups.put(OA_SUBGROUP, new FSGroup(OA_SUBGROUP, null, new String[] { OA_GROUP.name },
                                          new IdentityID[0]));
      groups.put(PLAIN_GROUP, new FSGroup(PLAIN_GROUP, null, new String[0],
                                          new IdentityID[] { DESIGNER }));

      AuthenticationProvider authc = mock(AuthenticationProvider.class);
      when(authc.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      when(authc.getGroup(any())).thenAnswer(inv -> groups.get(inv.<IdentityID>getArgument(0)));
      when(authc.isSystemAdministratorRole(any())).thenReturn(false);
      when(authc.isOrgAdministratorRole(any())).thenAnswer(inv -> {
         Role role = roles.get(inv.<IdentityID>getArgument(0));
         return role instanceof FSRole fs && fs.isOrgAdmin();
      });
      doCallRealMethod().when(authc).getAllRoles(any());
      doCallRealMethod().when(authc).getAllGroups(any());

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthenticationProvider()).thenReturn(authc);
      when(securityProvider.checkPermission(eq(principal), eq(ResourceType.SECURITY_ROLE),
                                            anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> grants.contains(
            inv.<String>getArgument(2) + "|" + inv.<ResourceAction>getArgument(3)));

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(principal)).thenReturn(ORG);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   // --- rule 1: organization administrator roles need an organization administrator ---

   @Test
   void delegate_addingOrgAdminRoleToUser_isRejected() {
      grant(ORG_ADMIN_ROLE, ResourceAction.ASSIGN);
      grantRootAdmin("Organization Roles");
      assertRejected(user(), userModel(List.of(DESIGNER, ORG_ADMIN_ROLE)), List.of());
   }

   @Test
   void delegate_addingFlaggedOrgRoleToUser_isRejectedEvenWithAssign() {
      grant(FLAGGED_ROLE, ResourceAction.ASSIGN);
      grant(FLAGGED_ROLE, ResourceAction.ADMIN);
      assertRejected(user(), userModel(List.of(DESIGNER, FLAGGED_ROLE)), List.of());
   }

   @Test
   void delegate_addingRoleInheritingOrgAdminToUser_isRejected() {
      grant(INHERITS_OA, ResourceAction.ASSIGN);
      assertRejected(user(), userModel(List.of(DESIGNER, INHERITS_OA)), List.of());
   }

   @Test
   void delegate_addingUserToOrgAdminGroup_isRejected() {
      // the client-supplied group org is ignored by setUserInfo, so it is normalized
      assertRejected(user(), userModel(List.of(DESIGNER)),
                     List.of(new IdentityID(OA_GROUP.name, null)));
      assertRejected(user(), userModel(List.of(DESIGNER)), List.of(OA_SUBGROUP));
   }

   @Test
   void delegate_addingOrgAdminRoleToGroup_isRejected() {
      assertRejected(group(), groupModel(List.of(DESIGNER, ORG_ADMIN_ROLE)), List.of());
   }

   @Test
   void delegate_inheritingOrgAdminRoleOnRole_isRejected() {
      grant(ORG_ADMIN_ROLE, ResourceAction.ASSIGN);
      assertRejected(new FSRole(ANALYST), roleModel(List.of(ORG_ADMIN_ROLE)), List.of());
   }

   // --- rule 2: each added role must be assignable by the caller ---

   @Test
   void delegate_addingRoleWithoutPermission_isRejected() {
      assertRejected(user(), userModel(List.of(DESIGNER, ANALYST)), List.of());
      assertRejected(group(), groupModel(List.of(DESIGNER, ANALYST)), List.of());
      assertRejected(new FSRole(DESIGNER), roleModel(List.of(ANALYST)), List.of());
   }

   @Test
   void delegate_withAssignOnRole_isAllowed() {
      grant(ANALYST, ResourceAction.ASSIGN);
      assertAllowed(user(), userModel(List.of(DESIGNER, ANALYST)), List.of());
      assertAllowed(group(), groupModel(List.of(DESIGNER, ANALYST)), List.of());
   }

   @Test
   void delegate_withAdminOnRole_isAllowed() {
      grant(ANALYST, ResourceAction.ADMIN);
      assertAllowed(user(), userModel(List.of(DESIGNER, ANALYST)), List.of());
   }

   @Test
   void delegate_withOrganizationRolesRootAdmin_isAllowed() {
      grantRootAdmin("Organization Roles");
      assertAllowed(user(), userModel(List.of(DESIGNER, ANALYST)), List.of());
   }

   @Test
   void delegate_withRolesRootAdmin_isAllowed() {
      grantRootAdmin("Roles");
      assertAllowed(user(), userModel(List.of(DESIGNER, ANALYST)), List.of());
   }

   @Test
   void delegate_rootAdminDoesNotOpenGlobalRoles() {
      grantRootAdmin("Organization Roles");
      grantRootAdmin("Roles");
      assertRejected(user(), userModel(List.of(DESIGNER, GLOBAL_PLAIN)), List.of());

      // a global role is only offered with ASSIGN on the role itself
      grant(GLOBAL_PLAIN, ResourceAction.ASSIGN);
      assertAllowed(user(), userModel(List.of(DESIGNER, GLOBAL_PLAIN)), List.of());
   }

   @Test
   void delegate_addingOtherOrgRole_isRejectedEvenWithAssign() {
      grant(OTHER_ORG_ROLE, ResourceAction.ASSIGN);
      grant(OTHER_ORG_ROLE, ResourceAction.ADMIN);
      grantRootAdmin("Organization Roles");
      assertRejected(user(), userModel(List.of(DESIGNER, OTHER_ORG_ROLE)), List.of());
   }

   @Test
   void delegate_unchangedResave_keepsUnassignableRoles() {
      FSUser user = user();
      user.setRoles(new IdentityID[] { DESIGNER, ORG_ADMIN_ROLE, ANALYST });
      user.setGroups(new String[] { OA_GROUP.name });
      assertAllowed(user, userModel(List.of(DESIGNER, ORG_ADMIN_ROLE, ANALYST)),
                    List.of(OA_GROUP));

      // removing a role is not an addition
      assertAllowed(user, userModel(List.of(DESIGNER)), List.of());
   }

   @Test
   void delegate_groupEdit_memberGroupsAreNotParents() {
      // in a group edit groupV holds member groups; a member group granting an organization
      // administrator role gives nothing to the edited group
      assertAllowed(group(), groupModel(List.of(DESIGNER)), List.of(OA_GROUP));
   }

   @Test
   void delegate_addingUserToOrdinaryGroup_isAllowed() {
      assertAllowed(user(), userModel(List.of(DESIGNER)), List.of(PLAIN_GROUP));
   }

   // --- organization and site administrators ---

   @Test
   void orgAdmin_addingOrgAdminAndOrgRoles_isAllowed() {
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      assertAllowed(user(), userModel(List.of(DESIGNER, ORG_ADMIN_ROLE, FLAGGED_ROLE, ANALYST)),
                    List.of(OA_GROUP));
      assertAllowed(group(), groupModel(List.of(DESIGNER, ORG_ADMIN_ROLE, INHERITS_OA)), List.of());
   }

   @Test
   void orgAdmin_addingGlobalNonAdminRole_isRejected() {
      // matches the multi-tenant EM role tree, which never offers an org-less non-admin role to
      // an organization administrator; the permission strategy scopes org admins to their org
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      assertRejected(user(), userModel(List.of(DESIGNER, GLOBAL_PLAIN)), List.of());
   }

   @Test
   void orgAdmin_addingOtherOrgRole_isRejected() {
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      assertRejected(user(), userModel(List.of(DESIGNER, OTHER_ORG_ROLE)), List.of());
   }

   @Test
   void siteAdmin_isNotRestricted() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      assertAllowed(user(), userModel(List.of(ORG_ADMIN_ROLE, GLOBAL_PLAIN, OTHER_ORG_ROLE)),
                    List.of(OA_GROUP));
      assertDoesNotThrow(() -> service.checkAssignableRoles(
         List.of(ORG_ADMIN_ROLE, OTHER_ORG_ROLE), List.of(OA_GROUP), principal));
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(OA_GROUP.name, ORG, principal));
   }

   @Test
   void securityDisabled_skipsCheck() {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      assertAllowed(user(), userModel(List.of(ORG_ADMIN_ROLE, ANALYST)), List.of(OA_GROUP));
   }

   // --- create paths ---

   @Test
   void checkAssignableRoles_appliesTheSameRule() {
      grantRootAdmin("Organization Roles");
      assertDoesNotThrow(() -> service.checkAssignableRoles(List.of(ANALYST), List.of(), principal));
      assertDoesNotThrow(() -> service.checkAssignableRoles(null, null, principal));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkAssignableRoles(List.of(ORG_ADMIN_ROLE), null, principal));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkAssignableRoles(List.of(), List.of(OA_GROUP), principal));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkAssignableRoles(List.of(GLOBAL_PLAIN), null, principal));

      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      assertDoesNotThrow(() -> service.checkAssignableRoles(
         List.of(ORG_ADMIN_ROLE, ANALYST), List.of(OA_GROUP), principal));
   }

   @Test
   void checkAssignableRoles_withoutPermission_isRejected() {
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkAssignableRoles(List.of(ANALYST), null, principal));
   }

   @Test
   void delegate_creatingUnderOrgAdminParentGroup_isRejected() {
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkSystemAdminParentGroup(OA_GROUP.name, ORG, principal));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkSystemAdminParentGroup(OA_SUBGROUP.name, ORG, principal));
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(PLAIN_GROUP.name, ORG, principal));
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(null, ORG, principal));
   }

   @Test
   void orgAdmin_creatingUnderOrgAdminParentGroup_isAllowed() {
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(OA_GROUP.name, ORG, principal));
   }

   private void grant(IdentityID role, ResourceAction action) {
      grants.add(role.convertToKey() + "|" + action);
   }

   private void grantRootAdmin(String rootName) {
      grants.add(new IdentityID(rootName, ORG).convertToKey() + "|" + ResourceAction.ADMIN);
   }

   private static FSUser user() {
      FSUser user = new FSUser(new IdentityID("delegate", ORG));
      user.setRoles(new IdentityID[] { DESIGNER });
      user.setGroups(new String[0]);
      return user;
   }

   private static FSGroup group() {
      return new FSGroup(new IdentityID("team", ORG), null, new String[0],
                         new IdentityID[] { DESIGNER });
   }

   private static EditUserPaneModel userModel(List<IdentityID> roles) {
      EditUserPaneModel model = mock(EditUserPaneModel.class);
      when(model.roles()).thenReturn(roles);
      when(model.organization()).thenReturn(ORG);
      return model;
   }

   private static EditGroupPaneModel groupModel(List<IdentityID> roles) {
      EditGroupPaneModel model = mock(EditGroupPaneModel.class);
      when(model.roles()).thenReturn(roles);
      return model;
   }

   private static EditRolePaneModel roleModel(List<IdentityID> roles) {
      EditRolePaneModel model = mock(EditRolePaneModel.class);
      when(model.isSysAdmin()).thenReturn(false);
      when(model.roles()).thenReturn(roles);
      return model;
   }

   private void assertRejected(Identity identity, EntityModel model, List<IdentityID> groupV) {
      Throwable error = assertThrows(InvocationTargetException.class,
                                     () -> invokeCheck(identity, model, groupV)).getCause();
      assertInstanceOf(java.lang.SecurityException.class, error);
   }

   private void assertAllowed(Identity identity, EntityModel model, List<IdentityID> groupV) {
      assertDoesNotThrow(() -> invokeCheck(identity, model, groupV));
   }

   private void invokeCheck(Identity identity, EntityModel model, List<IdentityID> groupV)
      throws Exception
   {
      Method method = IdentityService.class.getDeclaredMethod(
         "checkSystemAdminGrant", Identity.class, EntityModel.class, List.class, Principal.class);
      method.setAccessible(true);
      method.invoke(service, identity, model, groupV, principal);
   }
}
