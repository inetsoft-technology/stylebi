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
 * Bug #77498 (follow-ups listed as out of scope in Bug #77381 / PR #5971): a caller that is
 * neither a site admin nor an org admin, holding only ADMIN on the org's Roles/Groups roots,
 * must not be able to make itself or anyone else an organization administrator through a role
 * or group edit. (Edits of an org admin user are refused by the endpoint gate, see
 * DefaultCheckPermissionStrategyOrgAdminTargetTest.)
 * <p>
 * Each test runs the two steps IdentityService.setIdentity() takes for the edit: the
 * checkSystemAdminGrant() guard, then the real setRoleInfo()/setGroupInfo() writer against an
 * in-memory provider. The failure messages say whether the delegate ended up as an org admin.
 */
@Tag("core")
class IdentityServiceOrgAdminGrantTest {
   private static final String ORG = "orga";
   private static final IdentityID ORG_ADMIN_ROLE = new IdentityID("Organization Administrator", null);
   private static final IdentityID PLAIN_ROLE = new IdentityID("Analysts", ORG);   // R
   private static final IdentityID FLAGGED_ROLE = new IdentityID("OrgBoss", ORG);  // orgAdmin flag
   private static final IdentityID OA_GROUP = new IdentityID("bosses", ORG);       // holds OrgBoss
   private static final IdentityID PLAIN_GROUP = new IdentityID("staff", ORG);
   private static final IdentityID CHILD_GROUP = new IdentityID("juniorBosses", ORG); // child of bosses
   private static final IdentityID SUB_ROLE = new IdentityID("SubBoss", ORG);       // inherits OrgBoss
   private static final IdentityID DELEGATE = new IdentityID("delegate", ORG);
   private static final IdentityID ACCOMPLICE = new IdentityID("accomplice", ORG);
   private static final IdentityID BOSS = new IdentityID("boss", ORG);             // in OrgBoss + bosses

   private IdentityService service;
   private OrganizationManager orgManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private EditableAuthenticationProvider provider;
   private final Map<IdentityID, Role> roles = new HashMap<>();
   private final Map<IdentityID, Group> groups = new HashMap<>();
   private final Map<IdentityID, User> users = new HashMap<>();
   private final Set<String> grants = new HashSet<>();
   private final Principal principal = mock(Principal.class);

   @BeforeEach
   void setUp() {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);

      FSRole orgAdmin = new FSRole(ORG_ADMIN_ROLE);
      orgAdmin.setOrgAdmin(true);
      roles.put(ORG_ADMIN_ROLE, orgAdmin);
      roles.put(PLAIN_ROLE, new FSRole(PLAIN_ROLE));
      FSRole flagged = new FSRole(FLAGGED_ROLE);
      flagged.setOrgAdmin(true);
      roles.put(FLAGGED_ROLE, flagged);

      FSGroup oaGroup = new FSGroup(OA_GROUP, null, new String[0], new IdentityID[] { FLAGGED_ROLE });
      oaGroup.setOrganization(ORG);
      groups.put(OA_GROUP, oaGroup);
      FSGroup plainGroup = new FSGroup(PLAIN_GROUP, null, new String[0], new IdentityID[0]);
      plainGroup.setOrganization(ORG);
      groups.put(PLAIN_GROUP, plainGroup);
      FSGroup childGroup = new FSGroup(CHILD_GROUP, null, new String[] { OA_GROUP.name },
                                       new IdentityID[0]);
      childGroup.setOrganization(ORG);
      groups.put(CHILD_GROUP, childGroup);
      // a plain role whose inherited role is the flagged one
      FSRole subRole = new FSRole(SUB_ROLE);
      subRole.setRoles(new IdentityID[] { FLAGGED_ROLE });
      roles.put(SUB_ROLE, subRole);

      // the delegate holds the plain org role R and is in no group
      users.put(DELEGATE, user(DELEGATE, new IdentityID[] { PLAIN_ROLE }, new String[0]));
      users.put(ACCOMPLICE, user(ACCOMPLICE, new IdentityID[0], new String[0]));
      // an existing org admin, member of the flagged role and of the org admin group
      users.put(BOSS, user(BOSS, new IdentityID[] { FLAGGED_ROLE }, new String[] { OA_GROUP.name }));

      provider = mock(EditableAuthenticationProvider.class);
      when(provider.getRole(any())).thenAnswer(inv -> roles.get(inv.<IdentityID>getArgument(0)));
      when(provider.getGroup(any())).thenAnswer(inv -> groups.get(inv.<IdentityID>getArgument(0)));
      when(provider.getUser(any())).thenAnswer(inv -> users.get(inv.<IdentityID>getArgument(0)));
      doAnswer(inv -> roles.put(inv.getArgument(0), inv.getArgument(1)))
         .when(provider).setRole(any(), any());
      doAnswer(inv -> groups.put(inv.getArgument(0), inv.getArgument(1)))
         .when(provider).setGroup(any(), any());
      doAnswer(inv -> users.put(inv.getArgument(0), inv.getArgument(1)))
         .when(provider).setUser(any(), any());
      when(provider.isSystemAdministratorRole(any())).thenReturn(false);
      when(provider.isOrgAdministratorRole(any())).thenAnswer(inv -> {
         Role role = roles.get(inv.<IdentityID>getArgument(0));
         return role instanceof FSRole fs && fs.isOrgAdmin();
      });
      doCallRealMethod().when(provider).getAllRoles(any());
      doCallRealMethod().when(provider).getAllGroups(any());

      SecurityProvider securityProvider = mock(SecurityProvider.class);
      when(securityProvider.getAuthenticationProvider()).thenReturn(provider);
      when(securityProvider.checkPermission(eq(principal), any(ResourceType.class),
                                            anyString(), any(ResourceAction.class)))
         .thenAnswer(inv -> grants.contains(inv.<ResourceType>getArgument(1) + "|" +
                                            inv.<String>getArgument(2) + "|" +
                                            inv.<ResourceAction>getArgument(3)));

      service = mock(IdentityService.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
      ReflectionTestUtils.setField(service, "securityEngine", securityEngine);
      ReflectionTestUtils.setField(service, "securityProvider", securityProvider);

      orgManager = mock(OrganizationManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.getCurrentOrgID(principal)).thenReturn(ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(false);

      // the delegate: ADMIN on the org's Roles and Groups roots, nothing else
      grants.add(ResourceType.SECURITY_ROLE + "|" + new IdentityID("Roles", ORG).convertToKey() +
                 "|" + ResourceAction.ADMIN);
      grants.add(ResourceType.SECURITY_ROLE + "|" +
                 new IdentityID("Organization Roles", ORG).convertToKey() + "|" + ResourceAction.ADMIN);
      grants.add(ResourceType.SECURITY_GROUP + "|" + new IdentityID("Groups", ORG).convertToKey() +
                 "|" + ResourceAction.ADMIN);
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
   }

   /**
    * The delegate edits its own org role R, unchanged except isOrgAdmin=true. The isSysAdmin
    * twin of this is refused by checkSystemAdminGrant(); isOrgAdmin must be too.
    */
   @Test
   void rolesRootDelegate_settingIsOrgAdminOnOwnRole_isRefused() throws Exception {
      assertFalse(isOrgAdmin(DELEGATE), "precondition: delegate is not an org admin");
      EditRolePaneModel model = roleModel(PLAIN_ROLE, true, List.of(member(DELEGATE)));

      Throwable error = saveRole(PLAIN_ROLE, model);

      assertInstanceOf(java.lang.SecurityException.class, error,
         "non-org-admin Roles-root delegate saved isOrgAdmin=true on role " + PLAIN_ROLE +
         "; stored role orgAdmin=" + ((FSRole) roles.get(PLAIN_ROLE)).isOrgAdmin() +
         ", delegate is now org admin=" + isOrgAdmin(DELEGATE));
      assertFalse(((FSRole) roles.get(PLAIN_ROLE)).isOrgAdmin());
   }

   /** The delegate edits the org-admin-flagged role OrgBoss and adds itself as a member. */
   @Test
   void rolesRootDelegate_addingSelfToOrgAdminRole_isRefused() throws Exception {
      assertFalse(isOrgAdmin(DELEGATE), "precondition: delegate is not an org admin");
      EditRolePaneModel model = roleModel(FLAGGED_ROLE, true, List.of(member(DELEGATE)));

      Throwable error = saveRole(FLAGGED_ROLE, model);

      assertInstanceOf(java.lang.SecurityException.class, error,
         "non-org-admin Roles-root delegate added itself to org-admin role " + FLAGGED_ROLE +
         "; delegate roles now " + Arrays.toString(users.get(DELEGATE).getRoles()) +
         ", delegate is now org admin=" + isOrgAdmin(DELEGATE));
   }

   /** Adding a third user or a member group to an org admin role also creates org admins. */
   @Test
   void rolesRootDelegate_addingOtherUserOrGroupToOrgAdminRole_isRefused() throws Exception {
      Throwable error = saveRole(FLAGGED_ROLE, roleModel(FLAGGED_ROLE, true,
         List.of(member(BOSS), member(ACCOMPLICE))));
      assertInstanceOf(java.lang.SecurityException.class, error,
         "delegate added " + ACCOMPLICE + " to org-admin role " + FLAGGED_ROLE);
      assertFalse(isOrgAdmin(ACCOMPLICE));

      error = saveRole(FLAGGED_ROLE, roleModel(FLAGGED_ROLE, true,
         List.of(member(BOSS), groupMember(PLAIN_GROUP))));
      assertInstanceOf(java.lang.SecurityException.class, error,
         "delegate added group " + PLAIN_GROUP + " to org-admin role " + FLAGGED_ROLE);
   }

   /**
    * The EM GET echoes isOrgAdmin=true for a flagged role, so re-saving it with its existing
    * members and the stored flag passes the write-layer check.
    */
   @Test
   void unchangedResaveOfOrgAdminRole_isAllowed() throws Exception {
      assertNull(saveRole(FLAGGED_ROLE, roleModel(FLAGGED_ROLE, true, List.of(member(BOSS)))));
      assertTrue(((FSRole) roles.get(FLAGGED_ROLE)).isOrgAdmin());
      assertTrue(Arrays.asList(users.get(BOSS).getRoles()).contains(FLAGGED_ROLE));
   }

   /** The delegate edits group "bosses", which holds OrgBoss, and adds itself as a user member. */
   @Test
   void groupsRootDelegate_addingSelfToOrgAdminGroup_isRefused() throws Exception {
      assertFalse(isOrgAdmin(DELEGATE), "precondition: delegate is not an org admin");

      Throwable error = saveGroup(OA_GROUP, List.of(member(DELEGATE)));

      assertInstanceOf(java.lang.SecurityException.class, error,
         "non-org-admin Groups-root delegate added itself to org-admin group " + OA_GROUP +
         "; delegate groups now " + Arrays.toString(users.get(DELEGATE).getGroups()) +
         ", delegate is now org admin=" + isOrgAdmin(DELEGATE));
   }

   /** A member group added to an org admin group makes its members org admins too. */
   @Test
   void groupsRootDelegate_addingMemberGroupToOrgAdminGroup_isRefused() throws Exception {
      Throwable error = saveGroup(OA_GROUP, List.of(member(BOSS), groupMember(PLAIN_GROUP)));

      assertInstanceOf(java.lang.SecurityException.class, error,
         "delegate added group " + PLAIN_GROUP + " to org-admin group " + OA_GROUP);
      assertEquals(0, groups.get(PLAIN_GROUP).getGroups().length);
   }

   /** A child group of an org admin group grants org admin to its members through its parent. */
   @Test
   void groupsRootDelegate_addingSelfToChildOfOrgAdminGroup_isRefused() throws Exception {
      Throwable error = saveGroup(CHILD_GROUP, List.of(member(DELEGATE)));

      assertInstanceOf(java.lang.SecurityException.class, error,
         "delegate added itself to " + CHILD_GROUP + ", a child of org-admin group " + OA_GROUP +
         "; delegate is now org admin=" + isOrgAdmin(DELEGATE));
      assertFalse(isOrgAdmin(DELEGATE));
   }

   /** A role that inherits a flagged role grants org admin to its members. */
   @Test
   void rolesRootDelegate_addingSelfToRoleInheritingOrgAdminRole_isRefused() throws Exception {
      EditRolePaneModel model = roleModel(SUB_ROLE, false, List.of(member(DELEGATE)));
      when(model.roles()).thenReturn(List.of(FLAGGED_ROLE));

      Throwable error = saveRole(SUB_ROLE, model);

      assertInstanceOf(java.lang.SecurityException.class, error,
         "delegate added itself to " + SUB_ROLE + ", which inherits org-admin role " +
         FLAGGED_ROLE + "; delegate is now org admin=" + isOrgAdmin(DELEGATE));
      assertFalse(isOrgAdmin(DELEGATE));
   }

   @Test
   void unchangedResaveOfOrgAdminGroup_isAllowed() throws Exception {
      assertNull(saveGroup(OA_GROUP, List.of(member(BOSS))));
      assertTrue(Arrays.asList(users.get(BOSS).getGroups()).contains(OA_GROUP.name));
   }

   /** Control: adding members to a plain group is unaffected. */
   @Test
   void groupsRootDelegate_addingSelfToPlainGroup_isAllowed() throws Exception {
      assertNull(saveGroup(PLAIN_GROUP, List.of(member(DELEGATE))));
      assertTrue(Arrays.asList(users.get(DELEGATE).getGroups()).contains(PLAIN_GROUP.name));
   }

   /** Control: an org admin may flag a role as org admin and add members to it. */
   @Test
   void orgAdmin_settingIsOrgAdminOnRole_isAllowed() throws Exception {
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(true);
      EditRolePaneModel model = roleModel(PLAIN_ROLE, true, List.of(member(DELEGATE)));
      assertNull(saveRole(PLAIN_ROLE, model));
      assertTrue(((FSRole) roles.get(PLAIN_ROLE)).isOrgAdmin());
      assertNull(saveGroup(OA_GROUP, List.of(member(BOSS), member(ACCOMPLICE))));
   }

   // --- helpers ---

   private static FSUser user(IdentityID id, IdentityID[] roles, String[] groups) {
      FSUser user = new FSUser(id);
      user.setRoles(roles);
      user.setGroups(groups);
      user.setOrganization(ORG);
      return user;
   }

   private Throwable saveRole(IdentityID roleID, EditRolePaneModel model) throws Exception {
      try {
         invokeCheck(roles.get(roleID), model, memberGroups(model.members()));
         Method set = IdentityService.class.getDeclaredMethod(
            "setRoleInfo", EditRolePaneModel.class, EditableAuthenticationProvider.class,
            IdentityID[].class, IdentityID[].class, String[].class, List.class, List.class,
            List.class, Principal.class);
         set.setAccessible(true);
         set.invoke(service, model, provider, users.keySet().toArray(new IdentityID[0]),
                    groups.keySet().toArray(new IdentityID[0]), new String[0],
                    memberUsers(model.members()), memberGroups(model.members()), List.of(),
                    principal);
         return null;
      }
      catch(InvocationTargetException e) {
         return e.getCause();
      }
   }

   private Throwable saveGroup(IdentityID groupID, List<IdentityModel> members) throws Exception {
      Group group = groups.get(groupID);
      EditGroupPaneModel model = mock(EditGroupPaneModel.class);
      when(model.name()).thenReturn(groupID.name);
      when(model.oldName()).thenReturn(groupID.name);
      when(model.organization()).thenReturn(ORG);
      when(model.roles()).thenReturn(Arrays.asList(group.getRoles()));
      when(model.members()).thenReturn(members);

      try {
         // a group edit passes member groups as groupV, see IdentityService.setIdentity()
         invokeCheck(group, model, memberGroups(members));
         Method set = IdentityService.class.getDeclaredMethod(
            "setGroupInfo", FSGroup.class, EditGroupPaneModel.class,
            EditableAuthenticationProvider.class, IdentityID[].class, IdentityID[].class,
            List.class, List.class, Map.class);
         set.setAccessible(true);
         Map<IdentityID, String> memberMap = new HashMap<>();
         members.forEach(m -> memberMap.put(m.identityID(), null));
         set.invoke(service, group, model, provider, users.keySet().toArray(new IdentityID[0]),
                    groups.keySet().toArray(new IdentityID[0]), memberUsers(members),
                    memberGroups(members), memberMap);
         return null;
      }
      catch(InvocationTargetException e) {
         return e.getCause();
      }
   }

   private void invokeCheck(Identity identity, EntityModel model, List<IdentityID> groupV)
      throws Exception
   {
      Method method = IdentityService.class.getDeclaredMethod(
         "checkSystemAdminGrant", Identity.class, EntityModel.class, List.class, Principal.class);
      method.setAccessible(true);
      method.invoke(service, identity, model, groupV, principal);
   }

   private static List<IdentityID> memberUsers(List<IdentityModel> members) {
      return members.stream().filter(m -> m.type() == Identity.USER)
         .map(IdentityModel::identityID).toList();
   }

   private static List<IdentityID> memberGroups(List<IdentityModel> members) {
      return members.stream().filter(m -> m.type() == Identity.GROUP)
         .map(IdentityModel::identityID).toList();
   }

   private static EditRolePaneModel roleModel(IdentityID role, boolean orgAdmin,
                                              List<IdentityModel> members)
   {
      EditRolePaneModel model = mock(EditRolePaneModel.class);
      when(model.name()).thenReturn(role.name);
      when(model.oldName()).thenReturn(role.name);
      when(model.organization()).thenReturn(role.orgID);
      when(model.description()).thenReturn("");
      when(model.defaultRole()).thenReturn(false);
      when(model.isSysAdmin()).thenReturn(false);
      when(model.isOrgAdmin()).thenReturn(orgAdmin);
      when(model.roles()).thenReturn(List.of());
      when(model.members()).thenReturn(members);
      return model;
   }

   private static IdentityModel member(IdentityID user) {
      return IdentityModel.builder().identityID(user).type(Identity.USER).build();
   }

   private static IdentityModel groupMember(IdentityID group) {
      return IdentityModel.builder().identityID(group).type(Identity.GROUP).build();
   }

   /** Whether the user's stored roles and groups now lead to an org administrator role. */
   private boolean isOrgAdmin(IdentityID userID) {
      User user = users.get(userID);
      List<IdentityID> all = new ArrayList<>(Arrays.asList(user.getRoles()));
      IdentityID[] userGroups = Arrays.stream(user.getGroups())
         .map(g -> new IdentityID(g, ORG)).toArray(IdentityID[]::new);

      for(IdentityID g : provider.getAllGroups(userGroups)) {
         Group group = groups.get(g);

         if(group != null) {
            all.addAll(Arrays.asList(group.getRoles()));
         }
      }

      return Arrays.stream(provider.getAllRoles(all.toArray(new IdentityID[0])))
         .anyMatch(r -> r != null && provider.isOrgAdministratorRole(r));
   }
}
