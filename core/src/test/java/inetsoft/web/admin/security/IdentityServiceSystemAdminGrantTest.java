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
import static org.mockito.Mockito.*;

/**
 * Bug #77048: a caller that is not a site administrator (e.g. an org admin) must not be able to
 * grant system administrator privileges through the EM identity save path, whether by adding the
 * Administrator role to a user/group, setting the sysAdmin flag or inheriting Administrator on a
 * role, joining a group that holds Administrator, or editing an identity that already grants it.
 * Nor may it delete a user, group or role that grants system administrator, or create a user or
 * group under a parent group that grants it.
 */
@Tag("core")
class IdentityServiceSystemAdminGrantTest {
   private static final String ORG = "orgA";
   private static final IdentityID ADMIN_ROLE = new IdentityID("Administrator", null);
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);
   private static final IdentityID DESIGNER = new IdentityID("Designer", ORG);
   private static final IdentityID ADMIN_CHILD = new IdentityID("AdminChild", ORG);
   private static final IdentityID ADMIN_GROUP = new IdentityID("admins", ORG);
   private static final IdentityID PLAIN_GROUP = new IdentityID("plain", ORG);
   private static final IdentityID ADMIN_SUBGROUP = new IdentityID("subAdmins", ORG);

   private IdentityService service;
   private SecurityEngine securityEngine;
   private AuthenticationProvider authc;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      Map<IdentityID, Role> roles = new HashMap<>();
      FSRole admin = new FSRole(ADMIN_ROLE);
      admin.setSysAdmin(true);
      roles.put(ADMIN_ROLE, admin);
      FSRole orgAdmin = new FSRole(ORG_ADMIN_ROLE);
      orgAdmin.setOrgAdmin(true);
      roles.put(ORG_ADMIN_ROLE, orgAdmin);
      roles.put(DESIGNER, new FSRole(DESIGNER));
      roles.put(ADMIN_CHILD, new FSRole(ADMIN_CHILD, new IdentityID[] { ADMIN_ROLE }));

      Map<IdentityID, Group> groups = new HashMap<>();
      groups.put(ADMIN_GROUP, new FSGroup(ADMIN_GROUP, null, new String[0],
                                          new IdentityID[] { ADMIN_ROLE }));
      groups.put(PLAIN_GROUP, new FSGroup(PLAIN_GROUP, null, new String[0],
                                          new IdentityID[] { DESIGNER }));
      groups.put(ADMIN_SUBGROUP, new FSGroup(ADMIN_SUBGROUP, null, new String[] { ADMIN_GROUP.name },
                                             new IdentityID[0]));

      authc = mock(AuthenticationProvider.class);
      when(authc.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      when(authc.getGroup(any())).thenAnswer(inv -> groups.get(inv.<IdentityID>getArgument(0)));
      when(authc.isSystemAdministratorRole(any())).thenAnswer(inv -> {
         Role role = roles.get(inv.<IdentityID>getArgument(0));
         return role instanceof FSRole fs && fs.isSysAdmin();
      });
      doCallRealMethod().when(authc).getAllRoles(any());
      doCallRealMethod().when(authc).getAllGroups(any());

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthenticationProvider()).thenReturn(authc);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   @Test
   void orgAdmin_addingAdministratorRoleToUser_isRejected() {
      assertRejected(user(), userModel(List.of(DESIGNER, ADMIN_ROLE)), List.of());
   }

   @Test
   void orgAdmin_addingSpoofedAdministratorRoleToUser_isRejected() {
      assertRejected(user(), userModel(List.of(new IdentityID("Administrator", ORG))), List.of());
   }

   @Test
   void orgAdmin_addingRoleInheritingAdministratorToUser_isRejected() {
      assertRejected(user(), userModel(List.of(ADMIN_CHILD)), List.of());
   }

   @Test
   void orgAdmin_addingUserToAdministratorGroup_isRejected() {
      // the client-supplied group org is ignored by setUserInfo, so normalization must catch it
      assertRejected(user(), userModel(List.of(DESIGNER)),
                     List.of(new IdentityID(ADMIN_GROUP.name, null)));
   }

   @Test
   void orgAdmin_editingExistingSiteAdminUser_isRejected() {
      FSUser user = user();
      user.setRoles(new IdentityID[] { ADMIN_ROLE });
      assertRejected(user, userModel(List.of(ADMIN_ROLE)), List.of());
   }

   @Test
   void orgAdmin_ordinaryUserEdit_isAllowed() {
      assertAllowed(user(), userModel(List.of(DESIGNER, ORG_ADMIN_ROLE)), List.of(PLAIN_GROUP));
   }

   @Test
   void orgAdmin_addingAdministratorRoleToGroup_isRejected() {
      FSGroup group = new FSGroup(PLAIN_GROUP, null, new String[0], new IdentityID[] { DESIGNER });
      EditGroupPaneModel model = mock(EditGroupPaneModel.class);
      when(model.roles()).thenReturn(List.of(DESIGNER, ADMIN_ROLE));
      assertRejected(group, model, List.of());
   }

   @Test
   void orgAdmin_editingAdministratorGroup_isRejected() {
      EditGroupPaneModel model = mock(EditGroupPaneModel.class);
      when(model.roles()).thenReturn(List.of(ADMIN_ROLE));
      assertRejected((Identity) authc.getGroup(ADMIN_GROUP), model, List.of());
   }

   @Test
   void orgAdmin_settingSysAdminFlagOnRole_isRejected() {
      assertRejected(new FSRole(DESIGNER), roleModel(true, List.of()), List.of());
   }

   @Test
   void orgAdmin_inheritingAdministratorOnRole_isRejected() {
      assertRejected(new FSRole(DESIGNER), roleModel(false, List.of(ADMIN_ROLE)), List.of());
   }

   @Test
   void orgAdmin_editingAdministratorRole_isRejected() {
      assertRejected((Identity) authc.getRole(ADMIN_ROLE), roleModel(true, List.of()), List.of());
   }

   @Test
   void orgAdmin_ordinaryRoleEdit_isAllowed() {
      assertAllowed(new FSRole(DESIGNER), roleModel(false, List.of(ORG_ADMIN_ROLE)), List.of());
   }

   @Test
   void siteAdmin_addingAdministratorRole_isAllowed() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      assertAllowed(user(), userModel(List.of(ADMIN_ROLE)), List.of(ADMIN_GROUP));
      assertAllowed(new FSRole(DESIGNER), roleModel(true, List.of(ADMIN_ROLE)), List.of());
   }

   @Test
   void securityDisabled_skipsCheck() {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);
      assertAllowed(user(), userModel(List.of(ADMIN_ROLE)), List.of());
   }

   @Test
   void orgAdmin_deletingSiteAdminTargets_isDenied() throws Exception {
      FSUser direct = new FSUser(new IdentityID("sa-direct", ORG));
      direct.setRoles(new IdentityID[] { ADMIN_ROLE });
      FSUser viaGroup = new FSUser(new IdentityID("sa-group", ORG));
      viaGroup.setGroups(new String[] { ADMIN_GROUP.name });
      FSUser viaInherit = new FSUser(new IdentityID("sa-inherit", ORG));
      viaInherit.setRoles(new IdentityID[] { ADMIN_CHILD });
      stubUsers(direct, viaGroup, viaInherit);

      assertTrue(invokeDeleteCheck(direct.getIdentityID(), Identity.USER));
      assertTrue(invokeDeleteCheck(viaGroup.getIdentityID(), Identity.USER));
      assertTrue(invokeDeleteCheck(viaInherit.getIdentityID(), Identity.USER));
      assertTrue(invokeDeleteCheck(ADMIN_GROUP, Identity.GROUP));
      assertTrue(invokeDeleteCheck(ADMIN_ROLE, Identity.ROLE));
      assertTrue(invokeDeleteCheck(ADMIN_CHILD, Identity.ROLE));
   }

   @Test
   void orgAdmin_deletingOrdinaryTargets_isAllowed() throws Exception {
      FSUser plain = user();
      plain.setGroups(new String[] { PLAIN_GROUP.name });
      stubUsers(plain);

      assertFalse(invokeDeleteCheck(plain.getIdentityID(), Identity.USER));
      assertFalse(invokeDeleteCheck(new IdentityID("missing", ORG), Identity.USER));
      assertFalse(invokeDeleteCheck(PLAIN_GROUP, Identity.GROUP));
      assertFalse(invokeDeleteCheck(DESIGNER, Identity.ROLE));
      assertFalse(invokeDeleteCheck(ORG_ADMIN_ROLE, Identity.ROLE));
   }

   @Test
   void siteAdmin_deletingSiteAdminTargets_isAllowed() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      FSUser direct = new FSUser(new IdentityID("sa-direct", ORG));
      direct.setRoles(new IdentityID[] { ADMIN_ROLE });
      stubUsers(direct);

      assertFalse(invokeDeleteCheck(direct.getIdentityID(), Identity.USER));
      assertFalse(invokeDeleteCheck(ADMIN_ROLE, Identity.ROLE));
   }

   @Test
   void orgAdmin_creatingUnderAdministratorParentGroup_isRejected() {
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkSystemAdminParentGroup(ADMIN_GROUP.name, ORG, principal));
      assertThrows(java.lang.SecurityException.class,
                   () -> service.checkSystemAdminParentGroup(ADMIN_SUBGROUP.name, ORG, principal));
   }

   @Test
   void orgAdmin_creatingUnderOrdinaryOrNoParentGroup_isAllowed() {
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(PLAIN_GROUP.name, ORG, principal));
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup("missing", ORG, principal));
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(null, ORG, principal));
   }

   @Test
   void siteAdmin_creatingUnderAdministratorParentGroup_isAllowed() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      assertDoesNotThrow(() -> service.checkSystemAdminParentGroup(ADMIN_GROUP.name, ORG, principal));
   }

   private void stubUsers(FSUser... users) {
      Map<IdentityID, User> map = new HashMap<>();
      Arrays.stream(users).forEach(u -> map.put(u.getIdentityID(), u));
      when(authc.getUser(any())).thenAnswer(inv -> map.get(inv.<IdentityID>getArgument(0)));
   }

   private boolean invokeDeleteCheck(IdentityID id, int type) throws Exception {
      Method method = IdentityService.class.getDeclaredMethod(
         "isSystemAdminTargetDenied", IdentityID.class, int.class, Principal.class);
      method.setAccessible(true);
      return (Boolean) method.invoke(service, id, type, principal);
   }

   private static FSUser user() {
      FSUser user = new FSUser(new IdentityID("user0", ORG));
      user.setRoles(new IdentityID[] { DESIGNER });
      user.setGroups(new String[0]);
      return user;
   }

   private static EditUserPaneModel userModel(List<IdentityID> roles) {
      EditUserPaneModel model = mock(EditUserPaneModel.class);
      when(model.roles()).thenReturn(roles);
      when(model.organization()).thenReturn(ORG);
      return model;
   }

   private static EditRolePaneModel roleModel(boolean sysAdmin, List<IdentityID> roles) {
      EditRolePaneModel model = mock(EditRolePaneModel.class);
      when(model.isSysAdmin()).thenReturn(sysAdmin);
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
