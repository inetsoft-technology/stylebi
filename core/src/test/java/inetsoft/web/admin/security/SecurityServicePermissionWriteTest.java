/*
 * This file is part of StyleBI.
 *
 * Copyright (c) 2026, InetSoft Technology Corp, All Rights Reserved.
 *
 * The software and information contained herein are copyrighted and
 * proprietary to InetSoft Technology Corp. This software is furnished
 * pursuant to a written license agreement and may be used, copied,
 * transmitted, and stored only in accordance with the terms of such
 * license and with the inclusion of the above copyright notice. Please
 * refer to the file "COPYRIGHT" for further copyright and licensing
 * information. This software and information or any other copies
 * thereof may not be provided or otherwise made available to any other
 * person.
 */
package inetsoft.web.admin.security;

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.admin.security.user.*;
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

/*
 * Bug #77274: the public permissions API (PUT/DELETE /api/public/security/permissions and the
 * POST/PUT/DELETE grant endpoints) stored any action on an action tree node, wrote each grant under
 * a caller-chosen org (and relabeled the other grants to it), turned global (org-less) role grants
 * into ineffective current-org role grants on every write, and dropped the grants the caller may
 * not manage. These tests drive the real SecurityService write paths with a mocked action tree,
 * SecurityProvider and OrganizationManager, and assert on the stored Permission.
 */
@Tag("core")
class SecurityServicePermissionWriteTest {
   private static final String ORG = "orga";
   private static final String USERS = "settings/security/users";
   private static final String ACTIONS = "settings/security/actions";
   private static final String OA = "Organization Administrator";

   @BeforeEach
   void setUp() {
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      SecurityEngine securityEngine = mock(SecurityEngine.class, withSettings().lenient());
      when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);

      orgManager = mock(OrganizationManager.class, withSettings().lenient());
      when(orgManager.getCurrentOrgID()).thenReturn(ORG);
      organizationManagerStatic = mockStatic(OrganizationManager.class,
                                             withSettings().strictness(Strictness.LENIENT));
      organizationManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      ActionPermissionService actionService =
         mock(ActionPermissionService.class, withSettings().lenient());
      principal = mock(Principal.class, withSettings().lenient());
      when(principal.getName()).thenReturn(new IdentityID("caller", ORG).convertToKey());
      when(actionService.getActionTree(principal)).thenReturn(tree());

      // a global role and an org role that has no global twin
      when(securityProvider.getRole(new IdentityID(OA, null))).thenReturn(mock(Role.class));
      when(securityProvider.getRole(new IdentityID("Administrator", null)))
         .thenReturn(mock(Role.class));
      when(securityProvider.getRole(new IdentityID("Everyone", ORG))).thenReturn(mock(Role.class));
      // by default the caller administers every identity, so nothing is kept for being hidden
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);

      service = new SecurityService(
         securityEngine, mock(IdentityService.class), actionService,
         mock(LocalizationSettingsService.class), mock(IdentityThemeService.class),
         mock(SystemAdminService.class), mock(UserTreeService.class),
         mock(CustomThemesManager.class));
   }

   @AfterEach
   void tearDown() {
      organizationManagerStatic.close();
   }

   // ── action subset (C1) ──────────────────────────────────────────────────

   @Test
   void setPermission_actionTreeLeaf_actionNotOfferedByNode_rejected() {
      delegate();

      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         USERS, "EM_COMPONENT", model(user("me", ORG, "ACCESS", "ADMIN", "DELETE")), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_actionTreeLeaf_offeredAction_stored() throws Exception {
      delegate();

      service.setPermission(USERS, "EM_COMPONENT", model(user("me", ORG, "ACCESS")), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("me@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertTrue(saved.getAllUserGrants(ResourceAction.ADMIN).isEmpty());
   }

   @Test
   void createPermissionGrant_actionTreeLeaf_actionNotOfferedByNode_rejected() {
      delegate();

      assertThrows(UnauthorizedAccessException.class, () -> service.createPermissionGrant(
         USERS, "EM_COMPONENT", user("me", ORG, "ADMIN"), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // control: a resource outside the action tree keeps the full action set for its admin
   @Test
   void setPermission_adminBranchResource_keepsFullActionSet() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);

      service.setPermission("X", "REPORT",
                            model(user("me", ORG, "READ", "WRITE", "DELETE", "ADMIN")), principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");

      for(ResourceAction action : EnumSet.of(ResourceAction.READ, ResourceAction.WRITE,
                                             ResourceAction.DELETE, ResourceAction.ADMIN))
      {
         assertEquals(Set.of("me@orga"), names(saved.getAllUserGrants(action)), action.name());
      }
   }

   // ── grant org (C2a) ─────────────────────────────────────────────────────

   @Test
   void setPermission_foreignOrgGrant_rejectedWithoutWrite() {
      delegate();

      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         USERS, "EM_COMPONENT",
         model(user("bob", "orgb", "ACCESS"), user("alice", ORG, "ACCESS")), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_globalOrgIdGrant_rejected() {
      delegate();

      for(String type : List.of("USER", "GROUP", "ROLE", "ORGANIZATION")) {
         assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
            USERS, "EM_COMPONENT", model(grant(type, "x", "__GLOBAL__", "ACCESS")), principal),
            type);
      }

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_grantsOfCurrentOrg_eachStoredOnceInCurrentOrg() throws Exception {
      delegate();

      service.setPermission(
         USERS, "EM_COMPONENT",
         model(user("bob", null, "ACCESS"), user("alice", ORG, "ACCESS"),
               grant("GROUP", "sales", ORG, "ACCESS"), grant("ROLE", "Everyone", null, "ACCESS")),
         principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "bob@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("sales@orga"), names(saved.getAllGroupGrants(ResourceAction.ACCESS)));
      // Everyone is an org role, so a request without an org still means the current org
      assertEquals(Set.of("Everyone@orga"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
   }

   // ── global roles (C2b) ──────────────────────────────────────────────────

   @Test
   void createPermissionGrant_nonSiteAdmin_keepsGlobalRoleGrant() throws Exception {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.createPermissionGrant(USERS, "EM_COMPONENT", user("alice", ORG, "ACCESS"), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
   }

   @Test
   void updatePermissionGrant_nonSiteAdmin_keepsGlobalRoleGrant() throws Exception {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.updatePermissionGrant(USERS, "EM_COMPONENT",
                                    new IdentityID("old", ORG).convertToKey(), "USER",
                                    user("new", ORG, "ACCESS"), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("new@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
   }

   @Test
   void deletePermissionGrant_nonSiteAdmin_keepsGlobalRoleGrant() throws Exception {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.deletePermissionGrant(USERS, "EM_COMPONENT",
                                    new IdentityID("old", ORG).convertToKey(), "USER", principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertTrue(saved.getAllUserGrants(ResourceAction.ACCESS).isEmpty());
   }

   // the GET view round trip of a site admin writes the global role back to the null slot
   @Test
   void createPermissionGrant_siteAdmin_keepsGlobalRoleGrantInNullSlot() throws Exception {
      siteAdmin();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.createPermissionGrant(USERS, "EM_COMPONENT", user("alice", ORG, "ACCESS"), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
   }

   // a non site admin's crafted global role rows are ignored, the existing ones are kept
   @Test
   void setPermission_nonSiteAdmin_globalRoleRowsIgnoredAndExistingKept() throws Exception {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.setPermission(USERS, "EM_COMPONENT",
                            model(grant("ROLE", "Administrator", null, "ACCESS")), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
   }

   @Test
   void setPermission_siteAdmin_replacesAndClearsGlobalRoleGrants() throws Exception {
      siteAdmin();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.setPermission(USERS, "EM_COMPONENT",
                            model(grant("ROLE", "Administrator", null, "ACCESS")), principal);
      assertEquals(Set.of("Administrator@null"),
                   names(captureSaved(ResourceType.EM_COMPONENT, USERS)
                            .getAllRoleGrants(ResourceAction.ACCESS)));

      clearInvocations(securityProvider);
      service.setPermission(USERS, "EM_COMPONENT", model(), principal);
      assertTrue(captureSaved(ResourceType.EM_COMPONENT, USERS)
                    .getAllRoleGrants(ResourceAction.ACCESS).isEmpty());
   }

   // ── grantees the caller cannot administer (C3) ──────────────────────────

   @Test
   void setPermission_keepsGranteesCallerCannotAdminister() throws Exception {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("old", ORG).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(false);

      service.setPermission(USERS, "EM_COMPONENT", model(user("alice", ORG, "ACCESS")), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
   }

   @Test
   void deletePermission_nonSiteAdmin_clearsOrgGrantsKeepsGlobalRoleGrant() throws Exception {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertTrue(saved.getAllUserGrants(ResourceAction.ACCESS).isEmpty());
      verify(securityProvider, never()).removePermission(any(), anyString());
   }

   @Test
   void deletePermission_siteAdmin_clearsGlobalRoleGrantAndRemovesPermission() throws Exception {
      siteAdmin();
      stored(ResourceType.EM_COMPONENT, USERS);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      verify(securityProvider).removePermission(ResourceType.EM_COMPONENT, USERS);
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // ── stale out-of-node actions stored before the fix ─────────────────────

   // A stored action tree permission that already holds an action the node does not offer (for
   // example ADMIN written through this API before Bug #77274) comes back in the GET view, and
   // the grant endpoints send the whole GET view through setPermission, so every single-grant
   // write that keeps the stale row is rejected without a write.
   @Test
   void createPermissionGrant_storedOutOfNodeAction_rejectedWithoutWrite() {
      delegate();
      staleAdmin();

      UnauthorizedAccessException e = assertThrows(
         UnauthorizedAccessException.class, () -> service.createPermissionGrant(
            USERS, "EM_COMPONENT", user("bob", ORG, "ACCESS"), principal));

      assertTrue(e.getMessage().contains("ADMIN"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void deletePermissionGrant_otherGrantee_storedOutOfNodeAction_rejectedWithoutWrite() {
      delegate();
      staleAdmin();

      assertThrows(UnauthorizedAccessException.class, () -> service.deletePermissionGrant(
         USERS, "EM_COMPONENT", new IdentityID("alice", ORG).convertToKey(), "USER",
         principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // removing the stale grantee itself drops its row from the request, so it is accepted
   @Test
   void deletePermissionGrant_staleGrantee_accepted() throws Exception {
      delegate();
      staleAdmin();

      service.deletePermissionGrant(USERS, "EM_COMPONENT",
                                    new IdentityID("old", ORG).convertToKey(), "USER", principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertTrue(saved.getAllUserGrants(ResourceAction.ADMIN).isEmpty());
   }

   // a PUT of a clean set rewrites every action, so it also removes the stale out-of-node grant
   @Test
   void setPermission_cleanSet_removesStoredOutOfNodeAction() throws Exception {
      delegate();
      staleAdmin();

      service.setPermission(USERS, "EM_COMPONENT",
                            model(user("old", ORG, "ACCESS"), user("alice", ORG, "ACCESS")),
                            principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertTrue(saved.getAllUserGrants(ResourceAction.ADMIN).isEmpty());
   }

   // ── site admin, not multi-tenant ────────────────────────────────────────

   // a non multi-tenant site admin: full actions on a repository resource, and global roles are
   // written to (and kept in) the null slot
   @Test
   void setPermission_siteAdminSingleTenant_fullActionsAndGlobalRole() throws Exception {
      when(orgManager.getCurrentOrgID()).thenReturn(Organization.getDefaultOrganizationID());
      siteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);

      service.setPermission(
         "X", "REPORT",
         model(user("me", null, "READ", "WRITE", "DELETE", "ADMIN"),
               grant("ROLE", "Administrator", null, "READ", "ADMIN")),
         principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");
      String me = "me@" + Organization.getDefaultOrganizationID();

      for(ResourceAction action : EnumSet.of(ResourceAction.READ, ResourceAction.WRITE,
                                             ResourceAction.DELETE, ResourceAction.ADMIN))
      {
         assertEquals(Set.of(me), names(saved.getAllUserGrants(action)), action.name());
      }

      assertEquals(Set.of("Administrator@null"),
                   names(saved.getAllRoleGrants(ResourceAction.ADMIN)));
   }

   // ── helpers ─────────────────────────────────────────────────────────────

   // a non site admin that may only open the EM actions page (the tree branch of the check)
   private void delegate() {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT, ACTIONS,
                                            ResourceAction.ACCESS)).thenReturn(true);
   }

   private void notSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
   }

   private void siteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT, ACTIONS,
                                            ResourceAction.ACCESS)).thenReturn(true);
   }

   // stored grants: the global Organization Administrator role and the org user "old"
   private Permission stored(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of(OA), null);
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old"), ORG);
      when(securityProvider.getPermission(type, path)).thenReturn(permission);
      return permission;
   }

   // stored: "old" with ACCESS and a stale out-of-node ADMIN, and "alice" with ACCESS
   private void staleAdmin() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old", "alice"), ORG);
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("old"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
   }

   private Permission captureSaved(ResourceType type, String path) {
      ArgumentCaptor<Permission> captor = ArgumentCaptor.forClass(Permission.class);
      verify(securityProvider).setPermission(eq(type), eq(path), captor.capture());
      return captor.getValue();
   }

   private static ActionTreeNode tree() {
      ActionTreeNode leaf = ActionTreeNode.builder()
         .resource(USERS).label(USERS).folder(false)
         .type(ResourceType.EM_COMPONENT).actions(EnumSet.of(ResourceAction.ACCESS))
         .build();
      return ActionTreeNode.builder()
         .label("").folder(true).actions(EnumSet.noneOf(ResourceAction.class))
         .addChildren(leaf)
         .build();
   }

   private static ResourcePermission model(PermissionGrant... grants) {
      ResourcePermission model = new ResourcePermission();
      model.setPermissionGrants(new ArrayList<>(List.of(grants)));
      return model;
   }

   private static PermissionGrant user(String name, String org, String... actions) {
      return grant("USER", name, org, actions);
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
}
