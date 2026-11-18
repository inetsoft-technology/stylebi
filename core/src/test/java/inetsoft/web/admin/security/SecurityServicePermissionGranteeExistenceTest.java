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

import inetsoft.sree.SreeEnv;
import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.security.auth.MissingResourceException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80948, the permission writes (PUT permissions, POST/PUT grant) refuse a grantee that does
 * not exist in the organization, before anything is written. Axis: grantee type (USER, GROUP,
 * ROLE org or global, ORGANIZATION) x authentication mode (provider lookup, SSO, provider that
 * cannot answer) x stored-vs-new grantee x caller (site admin, caller that cannot administer the
 * grantee).
 */
@Tag("core")
class SecurityServicePermissionGranteeExistenceTest {
   private static final String ORG = "orga";
   private static final String PATH = "X";

   @BeforeEach
   void setUp() {
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(true);
      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      // SSO is off: the property is unset
      sreeEnvStatic = mockStatic(SreeEnv.class, withSettings().strictness(Strictness.LENIENT));

      ActionPermissionService actionService =
         mock(ActionPermissionService.class, withSettings().lenient());
      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", ORG).convertToKey());
      // REPORT is not an action tree node: the tree is empty
      when(actionService.getActionTree(principal)).thenReturn(
         ActionTreeNode.builder().label("").folder(true)
            .actions(EnumSet.noneOf(ResourceAction.class)).build());
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, PATH,
                                            ResourceAction.ADMIN)).thenReturn(true);
      // the caller administers every identity unless a test says otherwise
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);

      // the identities that exist: user bob, group sales, org role Designer, global role
      // Auditor, organization orga
      when(securityProvider.getUser(new IdentityID("bob", ORG))).thenReturn(mock(User.class));
      when(securityProvider.getGroup(new IdentityID("sales", ORG))).thenReturn(mock(Group.class));
      when(securityProvider.getRole(new IdentityID("Designer", ORG))).thenReturn(mock(Role.class));
      when(securityProvider.getRole(new IdentityID("Auditor", null))).thenReturn(mock(Role.class));
      when(securityProvider.getOrganization(ORG)).thenReturn(mock(Organization.class));

      service = new SecurityService(
         securityEngine, mock(IdentityService.class), actionService,
         mock(LocalizationSettingsService.class), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class),
         mock(CustomThemesManager.class));
      // Bug #80954, no Spring context here, a JVM-local lock per name stands in for the cluster's
      Map<String, java.util.concurrent.locks.Lock> locks = new java.util.concurrent.ConcurrentHashMap<>();
      service.setPermissionLockFactory(
         name -> locks.computeIfAbsent(name, k -> new java.util.concurrent.locks.ReentrantLock()));
   }

   @AfterEach
   void tearDown() {
      organizationManagerStatic.close();
      sreeEnvStatic.close();
   }

   @Test
   void setPermission_unknownUser_404NamesIt_nothingWritten() {
      MissingResourceException e = assertThrows(MissingResourceException.class, () ->
         service.setPermission(PATH, "REPORT", model(grant("USER", "ghost", ORG, "READ")),
                               principal));

      assertTrue(e.getMessage().contains("USER ghost"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_unknownGroupRoleOrganization_404NamesThem_nothingWritten() {
      MissingResourceException e = assertThrows(MissingResourceException.class, () ->
         service.setPermission(PATH, "REPORT", model(grant("GROUP", "no-group", ORG, "READ"),
                                                     grant("ROLE", "no-role", ORG, "READ"),
                                                     grant("ORGANIZATION", "no-org", ORG, "READ")),
                               principal));

      assertTrue(e.getMessage().contains("GROUP no-group"), e.getMessage());
      assertTrue(e.getMessage().contains("ROLE no-role"), e.getMessage());
      assertTrue(e.getMessage().contains("ORGANIZATION no-org"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_mixedKnownAndUnknown_nothingWritten_onlyUnknownNamed() {
      MissingResourceException e = assertThrows(MissingResourceException.class, () ->
         service.setPermission(PATH, "REPORT", model(grant("USER", "bob", ORG, "READ"),
                                                     grant("USER", "ghost", ORG, "READ"),
                                                     grant("GROUP", "no-group", ORG, "READ")),
                               principal));

      assertTrue(e.getMessage().contains("USER ghost"), e.getMessage());
      assertTrue(e.getMessage().contains("GROUP no-group"), e.getMessage());
      assertFalse(e.getMessage().contains("bob"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_unknownGrantee_storedPermissionObjectUnchanged() {
      Permission stored = new Permission();
      stored.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      stored.setUserGrantsForOrg(ResourceAction.READ, Set.of("bob"), ORG);
      when(securityProvider.getPermission(ResourceType.REPORT, PATH)).thenReturn(stored);

      assertThrows(MissingResourceException.class, () -> service.setPermission(
         PATH, "REPORT", model(grant("USER", "ghost", ORG, "READ")), principal));

      assertEquals(Set.of("bob@orga"), names(stored.getAllUserGrants(ResourceAction.READ)));
      assertTrue(stored.hasOrgEditedGrantAll(ORG));
   }

   @Test
   void createAndUpdatePermissionGrant_unknownGrantee_404() {
      assertThrows(MissingResourceException.class, () -> service.createPermissionGrant(
         PATH, "REPORT", grant("USER", "ghost", ORG, "READ"), principal));
      assertThrows(MissingResourceException.class, () -> service.updatePermissionGrant(
         PATH, "REPORT", new IdentityID("bob", ORG).convertToKey(), "USER",
         grant("USER", "ghost", ORG, "READ"), principal));
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_knownGrantees_stored() throws Exception {
      service.setPermission(PATH, "REPORT",
                            model(grant("USER", "bob", ORG, "READ"),
                                  grant("GROUP", "sales", ORG, "READ"),
                                  grant("ROLE", "Designer", ORG, "READ"),
                                  grant("ORGANIZATION", ORG, ORG, "READ")), principal);

      Permission saved = captureSaved();
      assertEquals(Set.of("bob@orga"), names(saved.getAllUserGrants(ResourceAction.READ)));
   }

   // the stored grant of an identity deleted since is not rechecked, so GET -> PUT still works,
   // but a new ghost next to it is refused
   @Test
   void setPermission_storedGhost_roundTripAccepted_newGhostRejected() throws Exception {
      Permission stored = new Permission();
      stored.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      stored.setUserGrantsForOrg(ResourceAction.READ, Set.of("deleted-user"), ORG);
      when(securityProvider.getPermission(ResourceType.REPORT, PATH)).thenReturn(stored);

      service.setPermission(PATH, "REPORT",
                            model(grant("USER", "deleted-user", ORG, "READ"),
                                  grant("USER", "bob", ORG, "READ")), principal);

      assertEquals(Set.of("bob@orga", "deleted-user@orga"),
                   names(captureSaved().getAllUserGrants(ResourceAction.READ)));

      assertThrows(MissingResourceException.class, () -> service.setPermission(
         PATH, "REPORT", model(grant("USER", "deleted-user", ORG, "READ"),
                               grant("USER", "ghost", ORG, "READ")), principal));
   }

   @Test
   void setPermission_ssoActive_userGroupRoleAllowed_organizationStillChecked() throws Exception {
      sreeEnvStatic.when(() -> SreeEnv.getProperty("sso.protocol.type")).thenReturn("SAML");

      service.setPermission(PATH, "REPORT",
                            model(grant("USER", "sso-user", ORG, "READ"),
                                  grant("GROUP", "sso-group", ORG, "READ"),
                                  grant("ROLE", "sso-role", ORG, "READ")), principal);

      assertEquals(Set.of("sso-user@orga"),
                   names(captureSaved().getAllUserGrants(ResourceAction.READ)));

      MissingResourceException e = assertThrows(MissingResourceException.class, () ->
         service.setPermission(PATH, "REPORT", model(grant("ORGANIZATION", "no-org", ORG, "READ")),
                               principal));
      assertTrue(e.getMessage().contains("ORGANIZATION no-org"), e.getMessage());
   }

   @Test
   void setPermission_globalRole_accepted() throws Exception {
      service.setPermission(PATH, "REPORT", model(grant("ROLE", "Auditor", null, "READ")),
                            principal);

      Permission saved = captureSaved();
      assertEquals(Set.of("Auditor@null"), names(saved.getAllRoleGrants(ResourceAction.READ)));
   }

   @Test
   void setPermission_unknownRoleWithoutOrg_404() {
      assertThrows(MissingResourceException.class, () -> service.setPermission(
         PATH, "REPORT", model(grant("ROLE", "no-role", null, "READ")), principal));
   }

   @Test
   void setPermission_foreignOrg_stillUnauthorized() {
      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         PATH, "REPORT", model(grant("USER", "ghost", "other-org", "READ")), principal));
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // Bug #80948, "cannot administer" no longer exempts a grantee from the lookup: a ghost user,
   // group or role is 404 for a caller that cannot administer it (checkOrgAdminPermission answers
   // false for a missing group, so an org admin's ghost group used to be stored), while an existing
   // identity the caller cannot administer is still accepted (residual: 204 vs 404 tells the
   // caller that the name exists)
   @Test
   void setPermission_callerCannotAdministerGhost_userGroupRole_404() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any()))
         .thenReturn(false);

      for(String type : List.of("USER", "GROUP", "ROLE")) {
         MissingResourceException e = assertThrows(MissingResourceException.class, () ->
            service.setPermission(PATH, "REPORT", model(grant(type, "ghost", ORG, "READ")),
                                  principal), type);
         assertTrue(e.getMessage().contains(type + " ghost"), e.getMessage());
      }

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_orgAdminGhostGroup_404_existingGroupAccepted() throws Exception {
      // org admin: not a site admin, administers a group only if it exists
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(orgManager.isOrgAdmin(any(Principal.class))).thenReturn(true);
      when(securityProvider.checkAnyPermission(any(), eq(ResourceType.SECURITY_GROUP),
                                               anyString(), any()))
         .thenAnswer(inv -> inv.getArgument(2).equals(new IdentityID("sales", ORG).convertToKey()));

      MissingResourceException e = assertThrows(MissingResourceException.class, () ->
         service.setPermission(PATH, "REPORT", model(grant("GROUP", "nogroup-here", ORG, "READ")),
                               principal));
      assertTrue(e.getMessage().contains("GROUP nogroup-here"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());

      service.setPermission(PATH, "REPORT", model(grant("GROUP", "sales", ORG, "READ")), principal);
      verify(securityProvider).setPermission(eq(ResourceType.REPORT), eq(PATH), any());
   }

   // an existing group the caller cannot administer (e.g. one that grants site admin) keeps the
   // old behavior: accepted, not rejected
   @Test
   void setPermission_existingGroupCallerCannotAdminister_accepted() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(securityProvider.checkAnyPermission(any(), eq(ResourceType.SECURITY_GROUP),
                                               anyString(), any())).thenReturn(false);

      service.setPermission(PATH, "REPORT", model(grant("GROUP", "sales", ORG, "READ")), principal);

      verify(securityProvider).setPermission(eq(ResourceType.REPORT), eq(PATH), any());
   }

   // site admin and ghost group: 404
   @Test
   void setPermission_siteAdminGhostGroup_404() {
      assertThrows(MissingResourceException.class, () -> service.setPermission(
         PATH, "REPORT", model(grant("GROUP", "ghost-group", ORG, "READ")), principal));
   }

   // a stored ghost group (written before the fix) can still be sent back by a caller that cannot
   // administer it, and is preserved
   @Test
   void setPermission_storedGhostGroup_orgAdminRoundTripStillSaves() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(securityProvider.checkAnyPermission(any(), eq(ResourceType.SECURITY_GROUP),
                                               anyString(), any())).thenReturn(false);
      Permission stored = new Permission();
      stored.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      stored.setGroupGrantsForOrg(ResourceAction.READ, Set.of("old-ghost"), ORG);
      when(securityProvider.getPermission(ResourceType.REPORT, PATH)).thenReturn(stored);

      service.setPermission(PATH, "REPORT", model(grant("GROUP", "old-ghost", ORG, "READ")),
                            principal);

      verify(securityProvider).setPermission(eq(ResourceType.REPORT), eq(PATH), any());
   }

   // an organization is looked up by name across orgs, so a non site admin only probes the
   // organizations it administers: an unadministered organization is not looked up
   @Test
   void setPermission_unadministeredOrganization_notLookedUp() throws Exception {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);
      when(securityProvider.checkAnyPermission(any(), eq(ResourceType.SECURITY_ORGANIZATION),
                                               anyString(), any())).thenReturn(false);

      service.setPermission(PATH, "REPORT", model(grant("ORGANIZATION", "other-org", ORG, "READ")),
                            principal);

      verify(securityProvider, never()).getOrganization("other-org");
      verify(securityProvider).setPermission(eq(ResourceType.REPORT), eq(PATH), any());
   }

   @Test
   void setPermission_callerAdministersGrantee_unknownIs404() {
      when(orgManager.isSiteAdmin(any(Principal.class))).thenReturn(false);

      assertThrows(MissingResourceException.class, () -> service.setPermission(
         PATH, "REPORT", model(grant("USER", "ghost", ORG, "READ")), principal));
   }

   // providers that do not override getUser/getGroup answer null for every name, so the check
   // cannot tell and fails open
   @Test
   void setPermission_providerCannotAnswerUserGroup_failsOpen() throws Exception {
      AuthenticationProvider silent = new AbstractAuthenticationProvider() {
         @Override
         public Organization getOrganization(String id) {
            return null;
         }

         @Override
         public String getOrgIdFromName(String name) {
            return null;
         }

         @Override
         public String getOrgNameFromID(String id) {
            return null;
         }

         @Override
         public String[] getOrganizationIDs() {
            return new String[0];
         }

         @Override
         public String[] getOrganizationNames() {
            return new String[0];
         }

         @Override
         public boolean authenticate(IdentityID userIdentity, Object credential) {
            return false;
         }

         @Override
         public void tearDown() {
         }
      };
      when(securityProvider.getAuthenticationProvider()).thenReturn(silent);

      service.setPermission(PATH, "REPORT", model(grant("USER", "ghost", ORG, "READ"),
                                                  grant("GROUP", "ghost-group", ORG, "READ")),
                            principal);

      verify(securityProvider).setPermission(eq(ResourceType.REPORT), eq(PATH), any());
   }

   @Test
   void setPermission_virtualProvider_userGroupRoleAllowed() throws Exception {
      AuthenticationProvider virtual = mock(AuthenticationProvider.class, withSettings().lenient());
      when(virtual.isVirtual()).thenReturn(true);
      when(securityProvider.getAuthenticationProvider()).thenReturn(virtual);

      service.setPermission(PATH, "REPORT", model(grant("USER", "ghost", ORG, "READ"),
                                                  grant("ROLE", "ghost-role", ORG, "READ")),
                            principal);

      verify(securityProvider).setPermission(eq(ResourceType.REPORT), eq(PATH), any());
   }

   private Permission captureSaved() {
      ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
      verify(securityProvider, atLeastOnce())
         .setPermission(eq(ResourceType.REPORT), eq(PATH), captor.capture());
      return captor.getValue();
   }

   private static ResourcePermission model(PermissionGrant... grants) {
      ResourcePermission model = new ResourcePermission();
      model.setPermissionGrants(new ArrayList<>(List.of(grants)));
      return model;
   }

   private static PermissionGrant grant(String type, String name, String org, String... actions) {
      PermissionGrant grant = new PermissionGrant();
      grant.setIdentityID(new IdentityID(name, org));
      grant.setType(type);
      grant.setActions(new ArrayList<>(List.of(actions)));
      return grant;
   }

   private static Set<String> names(Set<Permission.PermissionIdentity> grants) {
      return grants.stream()
         .map(g -> g.getName() + "@" + g.getOrganizationID())
         .collect(Collectors.toSet());
   }

   private SecurityProvider securityProvider;
   private OrganizationManager orgManager;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
   private MockedStatic<SreeEnv> sreeEnvStatic;
}
