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

import inetsoft.sree.schedule.ScheduleManager;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.uql.util.Identity;
import inetsoft.web.admin.security.user.EditRolePaneModel;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77112: editing a role must update the role references of its members in every
 * organization the role belongs to (every org for a global role, the role's own org otherwise),
 * not only in the editor's current organization, and only a site administrator may change the
 * membership of another organization's identities.
 */
@Tag("core")
class IdentityServiceSetRoleInfoOrgTest {
   private static final String ORG_A = "orga";
   private static final String ORG_B = "orgb";
   private static final String ORG_C = "orgc";

   private IdentityService service;
   private EditableAuthenticationProvider provider;
   private OrganizationManager organizationManager;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private final Map<IdentityID, FSUser> users = new LinkedHashMap<>();
   private final Map<IdentityID, FSGroup> groups = new LinkedHashMap<>();
   private final Map<IdentityID, FSRole> roles = new LinkedHashMap<>();
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getUsers()).thenAnswer(i -> users.keySet().toArray(new IdentityID[0]));
      when(provider.getGroups()).thenAnswer(i -> groups.keySet().toArray(new IdentityID[0]));
      when(provider.getRoles()).thenAnswer(i -> roles.keySet().toArray(new IdentityID[0]));
      when(provider.getOrganizationIDs()).thenReturn(new String[0]);
      when(provider.getUser(any())).thenAnswer(i -> users.get(i.getArgument(0)));
      when(provider.getGroup(any())).thenAnswer(i -> groups.get(i.getArgument(0)));
      when(provider.getRole(any())).thenAnswer(i -> roles.get(i.getArgument(0)));
      doAnswer(i -> {
         roles.put(((Role) i.getArgument(1)).getIdentityID(), i.getArgument(1));
         return null;
      }).when(provider).setRole(any(), any());
      // same as FileAuthenticationProvider.removeRole(): the removed role is only stripped from
      // the groups and users of the removed role's organization
      doAnswer(i -> {
         IdentityID old = i.getArgument(0);
         roles.remove(old);

         for(FSGroup group : groups.values()) {
            if(Objects.equals(group.getOrganizationID(), old.orgID)) {
               group.setRoles(without(group.getRoles(), old));
            }
         }

         for(FSUser user : users.values()) {
            if(Objects.equals(user.getOrganizationID(), old.orgID)) {
               user.setRoles(without(user.getRoles(), old));
            }
         }

         return null;
      }).when(provider).removeRole(any());

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthorizationProvider()).thenReturn(mock(AuthorizationChain.class));
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "dashboardManager", mock(DashboardManager.class));
      ReflectionTestUtils.setField(service, "scheduleManager", mock(ScheduleManager.class));
      ReflectionTestUtils.setField(service, "LOG", LoggerFactory.getLogger(IdentityService.class));
      doNothing().when(service)
         .updateIdentityPermissions(anyInt(), any(), any(), any(), any(), anyBoolean());

      organizationManager = mock(OrganizationManager.class);
      setCurrentOrg(ORG_A);
      setSiteAdmin(true);
      organizationManagerStatic = mockStatic(OrganizationManager.class);
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(organizationManager);
   }

   @AfterEach
   void tearDown() {
      organizationManagerStatic.close();
   }

   @Test
   void siteAdmin_globalRoleRename_updatesMembersInEveryOrg() throws Exception {
      IdentityID oldRole = new IdentityID("gRole", null);
      IdentityID newRole = new IdentityID("gRole2", null);
      FSGroup groupA = group("grpA", ORG_A, oldRole);
      FSGroup groupB = group("grpB", ORG_B, oldRole);
      FSUser userB = user("userB", ORG_B, oldRole);

      editRole("gRole", "gRole2", null, List.of(userB.getIdentityID()),
               List.of(groupA.getIdentityID(), groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { newRole }, groupA.getRoles());
      assertArrayEquals(new IdentityID[] { newRole }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { newRole }, userB.getRoles());
      assertFalse(roles.containsKey(oldRole));
   }

   @Test
   void siteAdmin_otherOrgRoleRename_keepsThatOrgsMembers() throws Exception {
      IdentityID oldRole = new IdentityID("analyst", ORG_B);
      IdentityID newRole = new IdentityID("analyst2", ORG_B);
      FSGroup groupB = group("grpB", ORG_B, oldRole);
      FSUser userB = user("userB", ORG_B, oldRole);

      editRole("analyst", "analyst2", ORG_B, List.of(userB.getIdentityID()),
               List.of(groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { newRole }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { newRole }, userB.getRoles());
      assertTrue(roles.containsKey(newRole));
   }

   @Test
   void siteAdmin_otherOrgRoleMemberEdit_isApplied() throws Exception {
      IdentityID role = new IdentityID("analyst", ORG_B);
      FSGroup groupB = group("grpB", ORG_B);
      FSUser userB = user("userB", ORG_B);
      FSUser victimB = user("victimB", ORG_B, role);

      editRole("analyst", "analyst", ORG_B, List.of(userB.getIdentityID()),
               List.of(groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { role }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { role }, userB.getRoles());
      assertArrayEquals(new IdentityID[0], victimB.getRoles());
   }

   @Test
   void currentOrgRoleRename_updatesMembers() throws Exception {
      setCurrentOrg(ORG_B);
      IdentityID oldRole = new IdentityID("analyst", ORG_B);
      IdentityID newRole = new IdentityID("analyst2", ORG_B);
      FSGroup groupB = group("grpB", ORG_B, oldRole);
      FSUser userB = user("userB", ORG_B, oldRole);

      editRole("analyst", "analyst2", ORG_B, List.of(userB.getIdentityID()),
               List.of(groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { newRole }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { newRole }, userB.getRoles());
   }

   @Test
   void orgAdmin_editingOtherOrgRole_isRejected() {
      setSiteAdmin(false);
      IdentityID role = new IdentityID("analyst", ORG_B);
      FSUser userB = user("userB", ORG_B);
      FSUser victimB = user("victimB", ORG_B, role);

      InvocationTargetException e = assertThrows(
         InvocationTargetException.class,
         () -> editRole("analyst", "analyst2", ORG_B, List.of(userB.getIdentityID()), List.of()));

      assertInstanceOf(java.lang.SecurityException.class, e.getCause());
      assertArrayEquals(new IdentityID[0], userB.getRoles());
      assertArrayEquals(new IdentityID[] { role }, victimB.getRoles());
      assertTrue(roles.containsKey(role));
      assertFalse(roles.containsKey(new IdentityID("analyst2", ORG_B)));
      verify(provider, never()).removeRole(any());
   }

   @Test
   void orgAdmin_globalRoleMemberEdit_cannotAddOtherOrgMembers() throws Exception {
      setSiteAdmin(false);
      IdentityID role = new IdentityID("gRole", null);
      FSGroup groupA = group("grpA", ORG_A);
      FSGroup groupB = group("grpB", ORG_B);
      FSUser userB = user("userB", ORG_B);

      editRole("gRole", "gRole", null, List.of(userB.getIdentityID()),
               List.of(groupA.getIdentityID(), groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { role }, groupA.getRoles());
      assertArrayEquals(new IdentityID[0], groupB.getRoles());
      assertArrayEquals(new IdentityID[0], userB.getRoles());
   }

   @Test
   void orgAdmin_globalRoleRename_carriesOtherOrgMembersWithoutRemovingThem() throws Exception {
      setSiteAdmin(false);
      IdentityID oldRole = new IdentityID("gRole", null);
      IdentityID newRole = new IdentityID("gRole2", null);
      FSGroup groupA = group("grpA", ORG_A, oldRole);
      FSUser droppedA = user("droppedA", ORG_A, oldRole);
      FSGroup groupB = group("grpB", ORG_B, oldRole);
      FSUser userB = user("userB", ORG_B, oldRole);

      // the EM member list of a non-site admin only shows the current organization's members
      editRole("gRole", "gRole2", null, List.of(), List.of(groupA.getIdentityID()));

      assertArrayEquals(new IdentityID[] { newRole }, groupA.getRoles());
      assertArrayEquals(new IdentityID[0], droppedA.getRoles());
      assertArrayEquals(new IdentityID[] { newRole }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { newRole }, userB.getRoles());
   }

   @Test
   void siteAdmin_globalRoleMemberEdit_addsOtherOrgMembers() throws Exception {
      IdentityID role = new IdentityID("gRole", null);
      FSGroup groupB = group("grpB", ORG_B);
      FSUser userB = user("userB", ORG_B);

      editRole("gRole", "gRole", null, List.of(userB.getIdentityID()),
               List.of(groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { role }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { role }, userB.getRoles());
   }

   @Test
   void orgRoleRename_doesNotTouchOtherOrgMembers() throws Exception {
      setCurrentOrg(ORG_C);
      IdentityID otherRole = new IdentityID("other", ORG_B);
      FSGroup groupB = group("grpB", ORG_B, otherRole);
      FSUser userB = user("userB", ORG_B, otherRole);
      FSUser userC = user("userC", ORG_C, new IdentityID("analyst", ORG_C));

      editRole("analyst", "analyst2", ORG_C,
               List.of(userB.getIdentityID(), userC.getIdentityID()),
               List.of(groupB.getIdentityID()));

      assertArrayEquals(new IdentityID[] { otherRole }, groupB.getRoles());
      assertArrayEquals(new IdentityID[] { otherRole }, userB.getRoles());
      assertArrayEquals(new IdentityID[] { new IdentityID("analyst2", ORG_C) }, userC.getRoles());
   }

   private void setCurrentOrg(String orgID) {
      when(organizationManager.getCurrentOrgID()).thenReturn(orgID);
      when(organizationManager.getCurrentOrgID(any())).thenReturn(orgID);
   }

   private void setSiteAdmin(boolean siteAdmin) {
      when(organizationManager.isSiteAdmin(any(Principal.class))).thenReturn(siteAdmin);
   }

   private FSGroup group(String name, String orgID, IdentityID... groupRoles) {
      FSGroup group = new FSGroup(new IdentityID(name, orgID));
      group.setRoles(groupRoles);
      groups.put(group.getIdentityID(), group);
      return group;
   }

   private FSUser user(String name, String orgID, IdentityID... userRoles) {
      FSUser user = new FSUser(new IdentityID(name, orgID));
      user.setRoles(userRoles);
      users.put(user.getIdentityID(), user);
      return user;
   }

   private static IdentityID[] without(IdentityID[] ids, IdentityID id) {
      return Arrays.stream(ids).filter(r -> !r.equals(id)).toArray(IdentityID[]::new);
   }

   private void editRole(String oldName, String newName, String orgID, List<IdentityID> userV,
                         List<IdentityID> groupV)
      throws Exception
   {
      roles.put(new IdentityID(oldName, orgID), new FSRole(new IdentityID(oldName, orgID)));
      List<IdentityModel> members = new ArrayList<>();
      userV.forEach(u -> members.add(
         IdentityModel.builder().identityID(u).type(Identity.USER).build()));
      groupV.forEach(g -> members.add(
         IdentityModel.builder().identityID(g).type(Identity.GROUP).build()));
      EditRolePaneModel model = EditRolePaneModel.builder()
         .name(newName)
         .oldName(oldName)
         .organization(orgID)
         .members(members)
         .isSysAdmin(false)
         .isOrgAdmin(false)
         .build();
      Method method = IdentityService.class.getDeclaredMethod(
         "setRoleInfo", EditRolePaneModel.class, EditableAuthenticationProvider.class,
         IdentityID[].class, IdentityID[].class, String[].class, List.class, List.class,
         List.class, Principal.class);
      method.setAccessible(true);
      method.invoke(service, model, provider, provider.getUsers(), provider.getGroups(),
                    new String[0], userV, groupV, new ArrayList<IdentityID>(), principal);
   }
}
