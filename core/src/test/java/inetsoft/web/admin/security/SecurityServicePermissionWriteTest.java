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
import inetsoft.util.Tuple4;
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
 * not manage. Bug #77385: a caller who is neither a site admin nor an org admin could grant action
 * tree permissions it does not hold. These tests drive the real SecurityService write paths with a
 * mocked action tree, SecurityProvider and OrganizationManager, and assert on the stored Permission.
 */
@Tag("core")
class SecurityServicePermissionWriteTest {
   private static final String ORG = "orga";
   private static final String USERS = "settings/security/users";
   private static final String ACTIONS = "settings/security/actions";
   private static final String TOOLBAR = "Email";
   private static final String OA = "Organization Administrator";

   @BeforeEach
   void setUp() {
      securityProvider = mock(SecurityProvider.class, withSettings().lenient());
      securityEngine = mock(SecurityEngine.class, withSettings().lenient());
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
      holdsUsers();

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
      holdsUsers();

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
      holdsUsers();
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
      holdsUsers();
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
      // Bug #78041, no grant the caller sees is left, a flag change that needs the hold
      holdsUsers();
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
      // Bug #78041, no grant the caller sees is left, a flag change that needs the hold
      holdsUsers();
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
      holdsUsers();
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
      // not edited by the org, so the DELETE needs no hold (see the #77385 gate tests)
      stored(ResourceType.EM_COMPONENT, USERS).removeGrantAllByOrg(ORG);

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

   // Bug #77426: on an action tree node, a caller who is not a site admin clears only the grants
   // of the grantees it administers, the ones it sees in the GET view, as PUT keeps the others
   @Test
   void deletePermission_delegate_keepsGranteesCallerCannotAdminister() throws Exception {
      delegate();
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      assertKeptUnadministered(captureSaved(ResourceType.EM_COMPONENT, USERS));
   }

   // unlike EM's action pane, an org admin keeps them too
   @Test
   void deletePermission_orgAdmin_keepsGranteesCallerCannotAdminister() throws Exception {
      delegate();
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      assertKeptUnadministered(captureSaved(ResourceType.EM_COMPONENT, USERS));
   }

   // a global role the caller cannot administer stays a global role grant only, it is not
   // copied into the org's role grants
   @Test
   void deletePermission_delegate_unadministeredGlobalRole_staysGlobalOnly() throws Exception {
      delegate();
      // not edited by the org, so the DELETE needs no hold (see the #77385 gate tests)
      stored(ResourceType.EM_COMPONENT, USERS).removeGrantAllByOrg(ORG);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_ROLE, new IdentityID(OA, null).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(false);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertTrue(saved.getAllUserGrants(ResourceAction.ACCESS).isEmpty());
   }

   // the node is left inheriting from its parent: the edited flag is cleared, not set
   @Test
   void deletePermission_delegate_heldEditedNode_keepsUnadministeredAndClearsEdited()
      throws Exception
   {
      delegate();
      holdsUsers();
      storedEdited();
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("old", ORG).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(false);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertFalse(saved.hasOrgEditedGrantAll(ORG));
      verify(securityProvider, never()).removePermission(any(), anyString());
   }

   // a resource that is not an action tree node still clears every org grant, as EM's
   // repository panes do
   @Test
   void deletePermission_repositoryResource_clearsAllOrgGrants() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);

      service.deletePermission("X", "REPORT", principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");
      assertTrue(saved.getAllUserGrants(ResourceAction.ACCESS).isEmpty());
      assertTrue(saved.getAllGroupGrants(ResourceAction.ACCESS).isEmpty());
      assertTrue(saved.getAllOrganizationGrants(ResourceAction.ACCESS).isEmpty());
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
   }

   // a site admin still clears every grant, also those of grantees it is not given ADMIN on
   @Test
   void deletePermission_siteAdmin_unadministeredGrantees_clearedAndPermissionRemoved()
      throws Exception
   {
      siteAdmin();
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      verify(securityProvider).removePermission(ResourceType.EM_COMPONENT, USERS);
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // grants on actions other than ACCESS are kept the same way, and another org's grants are
   // never touched
   @Test
   void deletePermission_delegate_keepsUnadministeredOnEveryActionAndOtherOrgGrants()
      throws Exception
   {
      delegate();
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old", "alice"), ORG);
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("old", "alice"), ORG);
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("bob"), "orgb");
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("old", ORG).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(false);

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("old@orga", "bob@orgb"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
   }

   // ── stale out-of-node actions stored before the fix ─────────────────────

   // A stored action tree permission may already hold an action the node does not offer (for
   // example ADMIN written through this API before Bug #77274, or the default Advanced ACCESS on
   // DASHBOARD *, Bug #77376). It comes back in the GET view, and the grant endpoints send the
   // whole GET view through setPermission. Sending it back unchanged for the same grantee is
   // accepted and keeps it; adding it or moving it to another grantee is still rejected.
   @Test
   void setPermission_unchangedRoundTrip_storedOutOfNodeAction_accepted() throws Exception {
      delegate();
      staleAdmin();
      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);

      service.setPermission(USERS, "EM_COMPONENT", view, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
   }

   @Test
   void setPermission_newOutOfNodeAction_otherGrantee_rejectedWithoutWrite() {
      delegate();
      staleAdmin();

      UnauthorizedAccessException e = assertThrows(
         UnauthorizedAccessException.class, () -> service.setPermission(
            USERS, "EM_COMPONENT",
            model(user("old", ORG, "ACCESS", "ADMIN"), user("alice", ORG, "ACCESS", "ADMIN")),
            principal));

      assertTrue(e.getMessage().contains("ADMIN"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // a grantee type other than the stored one does not inherit the stored out-of-node action
   @Test
   void setPermission_storedOutOfNodeAction_otherGranteeType_rejectedWithoutWrite() {
      delegate();
      staleAdmin();

      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         USERS, "EM_COMPONENT",
         model(user("old", ORG, "ACCESS", "ADMIN"), grant("GROUP", "old", ORG, "ADMIN")),
         principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void updatePermissionGrant_moveStoredOutOfNodeActionToOtherIdentity_rejectedWithoutWrite() {
      delegate();
      staleAdmin();

      UnauthorizedAccessException e = assertThrows(
         UnauthorizedAccessException.class, () -> service.updatePermissionGrant(
            USERS, "EM_COMPONENT", new IdentityID("old", ORG).convertToKey(), "USER",
            user("carol", ORG, "ACCESS", "ADMIN"), principal));

      assertTrue(e.getMessage().contains("ADMIN"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void createPermissionGrant_storedOutOfNodeAction_acceptedAndKept() throws Exception {
      delegate();
      holdsUsers();
      staleAdmin();

      service.createPermissionGrant(USERS, "EM_COMPONENT", user("bob", ORG, "ACCESS"), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "bob@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
   }

   @Test
   void createPermissionGrant_newOutOfNodeAction_rejectedWithoutWrite() {
      delegate();
      staleAdmin();

      assertThrows(UnauthorizedAccessException.class, () -> service.createPermissionGrant(
         USERS, "EM_COMPONENT", user("bob", ORG, "ACCESS", "ADMIN"), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void deletePermissionGrant_otherGrantee_storedOutOfNodeAction_acceptedAndKept()
      throws Exception
   {
      delegate();
      staleAdmin();

      service.deletePermissionGrant(USERS, "EM_COMPONENT",
                                    new IdentityID("alice", ORG).convertToKey(), "USER",
                                    principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
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

   // the stored action is matched where the write puts the row, so a row naming the grantee
   // without its org, or with its org in another case, is also accepted, as the org check is
   @Test
   void setPermission_storedOutOfNodeAction_grantOrgOmittedOrOtherCase_accepted()
      throws Exception
   {
      delegate();
      staleAdmin();

      service.setPermission(USERS, "EM_COMPONENT",
                            model(user("old", null, "ACCESS", "ADMIN"),
                                  user("alice", "ORGA", "ACCESS")),
                            principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
   }

   // an org role (the reported grant is a role) keeps its stored out-of-node action
   @Test
   void setPermission_orgRole_unchangedRoundTrip_storedOutOfNodeAction_accepted()
      throws Exception
   {
      delegate();
      Permission permission = new Permission();
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of("Everyone"), ORG);
      permission.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of("Everyone"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);

      service.setPermission(USERS, "EM_COMPONENT", view, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("Everyone@orga"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Everyone@orga"), names(saved.getAllRoleGrants(ResourceAction.ADMIN)));
   }

   // a global role keeps its stored out-of-node action in the global (null) bucket
   @Test
   void setPermission_globalRole_unchangedRoundTrip_storedOutOfNodeAction_acceptedAndStaysGlobal()
      throws Exception
   {
      siteAdmin();
      Permission permission = new Permission();
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of(OA), null);
      permission.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of(OA), null);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);

      service.setPermission(USERS, "EM_COMPONENT", view, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.ADMIN)));
   }

   // a request row (name, null) for a global role that has a same-name org role is put by the
   // write on the org role. The global role's stored out-of-node action must not be
   // copied onto the org role.
   @Test
   void setPermission_globalRoleActionWithSameNameOrgRole_rejectedWithoutWrite()
      throws Exception
   {
      delegate();
      when(securityProvider.getRole(new IdentityID("Administrator", ORG)))
         .thenReturn(mock(Role.class));
      Permission permission = new Permission();
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of("Administrator"), null);
      permission.setRoleGrantsForOrg(ResourceAction.ADMIN, Set.of("Administrator"), null);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      // Bug #77404, the delegate's GET view no longer shows the global role, so the request row
      // (name, null) is built by hand
      ResourcePermission view =
         model(grant("ROLE", "Administrator", null, "ACCESS", "ADMIN"));

      UnauthorizedAccessException e = assertThrows(
         UnauthorizedAccessException.class,
         () -> service.setPermission(USERS, "EM_COMPONENT", view, principal));

      assertTrue(e.getMessage().contains("ADMIN"), e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // the reported case (Bug #77376): a site admin in the default org GETs DASHBOARD * as seeded by
   // the old bootstrap (Designer READ/WRITE plus the stray Advanced ACCESS, which the node does not
   // offer) and PUTs it back unchanged. It is accepted and nothing changes; giving ACCESS to
   // another role is still rejected.
   @Test
   void setPermission_defaultOrgDashboard_unchangedRoundTripAccepted_newAccessRejected()
      throws Exception
   {
      String hostOrg = Organization.getDefaultOrganizationID();
      when(orgManager.getCurrentOrgID()).thenReturn(hostOrg);
      siteAdmin();
      Permission seeded = new Permission();
      seeded.setOrgEditedGrantAll(new HashMap<>(Map.of(hostOrg, true)));
      seeded.setRoleGrantsForOrg(ResourceAction.READ, Set.of("Designer"), hostOrg);
      seeded.setRoleGrantsForOrg(ResourceAction.WRITE, Set.of("Designer"), hostOrg);
      seeded.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of("Advanced"), hostOrg);
      when(securityProvider.getPermission(ResourceType.DASHBOARD, "*")).thenReturn(seeded);

      ResourcePermission view = service.getPermission("*", "DASHBOARD", principal);
      assertTrue(view.getPermissionGrants().stream().anyMatch(
         g -> "ROLE".equals(g.getType()) && "Advanced".equals(g.getIdentityID().name) &&
            g.getActions().equals(List.of("ACCESS"))), String.valueOf(view.getPermissionGrants()));

      UnauthorizedAccessException e = assertThrows(
         UnauthorizedAccessException.class, () -> service.setPermission(
            "*", "DASHBOARD",
            model(grant("ROLE", "Designer", hostOrg, "READ", "WRITE", "ACCESS"),
                  grant("ROLE", "Advanced", hostOrg, "ACCESS")),
            principal));
      assertTrue(e.getMessage().contains("ACCESS for action permission DASHBOARD:*"),
                 e.getMessage());
      verify(securityProvider, never()).setPermission(any(), anyString(), any());

      service.setPermission("*", "DASHBOARD", view, principal);

      Permission saved = captureSaved(ResourceType.DASHBOARD, "*");
      String designer = "Designer@" + hostOrg;
      assertEquals(Set.of(designer), names(saved.getAllRoleGrants(ResourceAction.READ)));
      assertEquals(Set.of(designer), names(saved.getAllRoleGrants(ResourceAction.WRITE)));
      assertEquals(Set.of("Advanced@" + hostOrg),
                   names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
   }

   // ── hold to grant (Bug #77385) ──────────────────────────────────────────

   // the reported case: a user who only holds ACCESS on settings/security/actions grants ACCESS
   // on another EM page to itself, another user, a group, a role or the org
   @Test
   void setPermission_notHeldNode_newGrantOfAnyGranteeType_rejectedWithoutWrite() {
      delegate();

      for(PermissionGrant grant : List.of(user("caller", ORG, "ACCESS"),
                                          user("bob", ORG, "ACCESS"),
                                          grant("GROUP", "sales", ORG, "ACCESS"),
                                          grant("ROLE", "Everyone", null, "ACCESS"),
                                          grant("ORGANIZATION", ORG, ORG, "ACCESS")))
      {
         UnauthorizedAccessException e = assertThrows(
            UnauthorizedAccessException.class,
            () -> service.setPermission(USERS, "EM_COMPONENT", model(grant), principal),
            grant.getType());
         assertTrue(e.getMessage().contains("not held"), e.getMessage());
      }

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void createPermissionGrant_notHeldNode_rejectedWithoutWrite() {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      assertThrows(UnauthorizedAccessException.class, () -> service.createPermissionGrant(
         USERS, "EM_COMPONENT", user("caller", ORG, "ACCESS"), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // renaming the grantee gives the new grantee a grant it did not have
   @Test
   void updatePermissionGrant_notHeldNode_renameGrantee_rejectedWithoutWrite() {
      delegate();
      stored(ResourceType.EM_COMPONENT, USERS);

      assertThrows(UnauthorizedAccessException.class, () -> service.updatePermissionGrant(
         USERS, "EM_COMPONENT", new IdentityID("old", ORG).convertToKey(), "USER",
         user("caller", ORG, "ACCESS"), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // removing grants and unchanged re-saves on a node that is not edited by the org are still
   // allowed, as in EM, when they keep the node deriving from its parent. The GET view round trip
   // matches the stored grants and carries inheritFromParent (Bug #78041).
   @Test
   void notHeldNode_notEdited_removalAndUnchangedResave_stored() throws Exception {
      delegate();
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old", "alice"), ORG);
      permission.setGroupGrantsForOrg(ResourceAction.ACCESS, Set.of("sales"), ORG);
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of("Everyone"), ORG);
      // an organization grant is stored under the org name, which the GET view reports
      permission.setOrganizationGrantsForOrg(ResourceAction.ACCESS, Set.of("Org A"), ORG);
      when(securityProvider.getOrgNameFromID(ORG)).thenReturn("Org A");
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);
      assertEquals(Boolean.TRUE, view.getInheritFromParent());
      service.setPermission(USERS, "EM_COMPONENT", view, principal);
      clearInvocations(securityProvider);

      view = service.getPermission(USERS, "EM_COMPONENT", principal);
      view.getPermissionGrants().removeIf(g -> "alice".equals(g.getIdentityID().name));
      service.setPermission(USERS, "EM_COMPONENT", view, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertFalse(saved.hasOrgEditedGrantAll(ORG));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("sales@orga"), names(saved.getAllGroupGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Everyone@orga"), names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Org A@orga"),
                   names(saved.getAllOrganizationGrants(ResourceAction.ACCESS)));
   }

   // Bug #78041, as in EM (isGrantOrUseParentChange), a write that changes the org's edited flag
   // needs the hold. Removing the only grant of an org edited node (an explicit restriction)
   // without inheritFromParent=false makes it inherit from its parent again, so it is gated.
   @Test
   void deletePermissionGrant_notHeldNode_edited_rejectedWithoutWrite() {
      delegate();
      storedEdited();

      assertThrows(UnauthorizedAccessException.class, () -> service.deletePermissionGrant(
         USERS, "EM_COMPONENT", new IdentityID("old", ORG).convertToKey(), "USER", principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   @Test
   void setPermission_notHeldNode_edited_emptySet_rejectedWithoutWrite() {
      delegate();
      storedEdited();

      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         USERS, "EM_COMPONENT", model(), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // Bug #78041, removing one of two grants keeps the node edited, so as in EM it needs no hold
   @Test
   void deletePermissionGrant_notHeldNode_edited_otherGrantLeft_stored() throws Exception {
      delegate();
      Permission permission = new Permission();
      permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old", "alice"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      service.deletePermissionGrant(USERS, "EM_COMPONENT",
                                    new IdentityID("alice", ORG).convertToKey(), "USER",
                                    principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertTrue(saved.hasOrgEditedGrantAll(ORG));
   }

   // Bug #78041, inheritFromParent=false with no grants keeps an edited node edited ("nobody"),
   // which is not a flag change, so it needs no hold
   @Test
   void setPermission_notHeldNode_edited_emptySetKeepEdited_stored() throws Exception {
      delegate();
      storedEdited();
      ResourcePermission model = model();
      model.setInheritFromParent(false);

      service.setPermission(USERS, "EM_COMPONENT", model, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertTrue(saved.getAllUserGrants(ResourceAction.ACCESS).isEmpty());
      assertTrue(saved.hasOrgEditedGrantAll(ORG));
   }

   // Bug #78041, a grant edit on a node that derives from its parent marks it edited (a flag
   // change), so a caller that does not hold the node may not make it, even to remove a grant
   @Test
   void deletePermissionGrant_notHeldNode_notEdited_grantLeft_rejectedWithoutWrite() {
      delegate();
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old", "alice"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      assertThrows(UnauthorizedAccessException.class, () -> service.deletePermissionGrant(
         USERS, "EM_COMPONENT", new IdentityID("alice", ORG).convertToKey(), "USER", principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // Bug #78041, switching derive off without adding a grant is a flag change too
   @Test
   void setPermission_notHeldNode_notEdited_explicitNoDerive_rejectedWithoutWrite()
      throws Exception
   {
      delegate();
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);
      view.setInheritFromParent(false);

      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         USERS, "EM_COMPONENT", view, principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // Bug #78041, the grants of identities the caller cannot administer are kept and count as
   // grants after the write, so an empty PUT by such a caller leaves the node edited
   @Test
   void setPermission_emptySet_keptUnadministeredGrant_marksEdited() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("old"), ORG);
      when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);
      when(securityProvider.checkAnyPermission(
         principal, ResourceType.SECURITY_USER, new IdentityID("old", ORG).convertToKey(),
         EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(false);

      service.setPermission("X", "REPORT", model(), principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.READ)));
      assertTrue(saved.hasOrgEditedGrantAll(ORG));
   }

   // Bug #78041, a non site admin's write keeps the stored global role grants, which still apply
   // to the org, so the node is edited
   @Test
   void setPermission_emptySet_keptGlobalRoleGrant_marksEdited() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);
      Permission permission = new Permission();
      permission.setRoleGrantsForOrg(ResourceAction.READ, Set.of(OA), null);
      when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);

      service.setPermission("X", "REPORT", model(), principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");
      assertEquals(Set.of(OA + "@null"), names(saved.getAllRoleGrants(ResourceAction.READ)));
      assertTrue(saved.hasOrgEditedGrantAll(ORG));
   }

   // Bug #78041, an empty PUT that leaves no grant derives from the parent again
   @Test
   void setPermission_emptySet_noGrantLeft_derives() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);
      Permission permission = new Permission();
      permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("old"), ORG);
      when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);

      service.setPermission("X", "REPORT", model(), principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");
      assertTrue(saved.getAllUserGrants(ResourceAction.READ).isEmpty());
      assertFalse(saved.hasOrgEditedGrantAll(ORG));
   }

   @Test
   void deletePermission_notHeldNode_edited_rejectedWithoutWrite() {
      delegate();
      storedEdited();

      assertThrows(UnauthorizedAccessException.class, () -> service.deletePermission(
         USERS, "EM_COMPONENT", principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
      verify(securityProvider, never()).removePermission(any(), anyString());
   }

   @Test
   void heldNode_edited_delete_stored() throws Exception {
      delegate();
      holdsUsers();
      storedEdited();

      service.deletePermission(USERS, "EM_COMPONENT", principal);

      verify(securityProvider).removePermission(ResourceType.EM_COMPONENT, USERS);
   }

   @Test
   void orgAdmin_notHeldNode_newGrantOnEditedNode_stored() throws Exception {
      delegate();
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      storedEdited();

      service.createPermissionGrant(USERS, "EM_COMPONENT", user("bob", ORG, "ACCESS"), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("bob@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
   }

   @Test
   void siteAdmin_notHeldNode_newGrantOnEditedNode_stored() throws Exception {
      siteAdmin();
      storedEdited();

      service.createPermissionGrant(USERS, "EM_COMPONENT", user("bob", ORG, "ACCESS"), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("bob@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
   }

   // the hold is decided by SecurityEngine.checkPermission, which also counts the default grant
   // of a type like VIEWSHEET_TOOLBAR_ACTION, so a caller that holds it by default may grant it
   @Test
   void setPermission_nodeHeldByDefault_selfGrant_stored() throws Exception {
      delegate();
      when(securityEngine.checkPermission(principal, ResourceType.VIEWSHEET_TOOLBAR_ACTION,
                                          TOOLBAR, ResourceAction.READ)).thenReturn(true);

      service.setPermission(TOOLBAR, "VIEWSHEET_TOOLBAR_ACTION",
                            model(user("caller", ORG, "READ")), principal);

      Permission saved = captureSaved(ResourceType.VIEWSHEET_TOOLBAR_ACTION, TOOLBAR);
      assertEquals(Set.of("caller@orga"), names(saved.getAllUserGrants(ResourceAction.READ)));
   }

   // ── read filter: grantees the caller cannot administer (Bug #77404) ────

   // a delegate's view hides the users, groups, roles and orgs it cannot administer, and global
   // roles, on every read endpoint, as EM does
   @Test
   void delegate_reads_hideUnadministeredGranteesAndGlobalRoles() throws Exception {
      delegate();
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      when(securityProvider.getPermissions()).thenReturn(
         List.of(new Tuple4<>(ResourceType.EM_COMPONENT, ORG, USERS, permission)));
      // the list holds the permissions of the organization get/set permission use (Bug #77946)
      when(securityProvider.getResourceOrgID(null)).thenReturn(ORG);

      Set<String> expected = Set.of("USER:alice@orga", "GROUP:sales@orga", "ROLE:Everyone@orga");
      assertEquals(expected, grantees(service.getPermission(USERS, "EM_COMPONENT", principal)));

      ResourcePermissionList list = service.listPermissions(principal);
      assertEquals(1, list.getPermissions().size());
      assertEquals(expected, grantees(list.getPermissions().get(0)));

      assertNotNull(service.getPermissionGrant(USERS, "EM_COMPONENT", "alice", "USER", principal));
      assertNull(service.getPermissionGrant(USERS, "EM_COMPONENT", "old", "USER", principal));
      assertNull(service.getPermissionGrant(USERS, "EM_COMPONENT", "hr", "GROUP", principal));
      assertNull(service.getPermissionGrant(USERS, "EM_COMPONENT", "Designer", "ROLE", principal));
   }

   // the filtered view sent back unchanged keeps every hidden grantee of every type, and the
   // global role
   @Test
   void delegate_filteredGetPutRoundTrip_keepsHiddenGrantees() throws Exception {
      delegate();
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      service.setPermission(USERS, "EM_COMPONENT",
                            service.getPermission(USERS, "EM_COMPONENT", principal), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("sales@orga", "hr@orga"),
                   names(saved.getAllGroupGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Everyone@orga", "Designer@orga", OA + "@null"),
                   names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Org A@orga"),
                   names(saved.getAllOrganizationGrants(ResourceAction.ACCESS)));
   }

   // a site admin sees every grantee, also those it is not given ADMIN on, and global roles
   @Test
   void siteAdmin_read_showsAllGranteesAndGlobalRoles() throws Exception {
      siteAdmin();
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(false);
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      assertEquals(Set.of("USER:alice@orga", "USER:old@orga", "GROUP:sales@orga",
                          "GROUP:hr@orga", "ROLE:Everyone@orga", "ROLE:Designer@orga",
                          "ROLE:" + OA + "@null", "ORGANIZATION:Org A@orga"),
                   grantees(service.getPermission(USERS, "EM_COMPONENT", principal)));
   }

   // an org admin administers its org's identities but, as in EM, does not see global roles
   @Test
   void orgAdmin_read_hidesGlobalRoles() throws Exception {
      delegate();
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      stored(ResourceType.EM_COMPONENT, USERS);

      assertEquals(Set.of("USER:old@orga"),
                   grantees(service.getPermission(USERS, "EM_COMPONENT", principal)));
   }

   // an org admin is not short-circuited on read: a grantee it is denied ADMIN on (as
   // DefaultCheckPermissionStrategy denies it a user granting system administrator, #77347) is
   // hidden, and the filtered view sent back unchanged keeps it and the global role
   @Test
   void orgAdmin_read_hidesUnadministeredGrantees_andRoundTripKeepsThem() throws Exception {
      delegate();
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);
      assertEquals(Set.of("USER:alice@orga", "GROUP:sales@orga", "ROLE:Everyone@orga"),
                   grantees(view));

      service.setPermission(USERS, "EM_COMPONENT", view, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga", "old@orga"),
                   names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("sales@orga", "hr@orga"),
                   names(saved.getAllGroupGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Everyone@orga", "Designer@orga", OA + "@null"),
                   names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Org A@orga"),
                   names(saved.getAllOrganizationGrants(ResourceAction.ACCESS)));
   }

   // an org grant stored under the org's old name (the org was renamed since) is filtered on its
   // stored (name, org) key, the key the write keeps it by, not on the current display name
   @Test
   void delegate_renamedOrgGrant_filteredOnStoredKey_roundTripKeepsIt() throws Exception {
      delegate();
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("alice"), ORG);
      permission.setOrganizationGrantsForOrg(ResourceAction.ACCESS, Set.of("Old Name"), ORG);
      when(securityProvider.getOrgNameFromID(ORG)).thenReturn("New Name");
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
      // denied on the stored key only, the display key (New Name, orga) stays allowed
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_ORGANIZATION,
         new IdentityID("Old Name", ORG).convertToKey(), EnumSet.of(ResourceAction.ADMIN)))
         .thenReturn(false);

      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);
      assertEquals(Set.of("USER:alice@orga"), grantees(view));

      service.setPermission(USERS, "EM_COMPONENT", view, principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Old Name@orga"),
                   names(saved.getAllOrganizationGrants(ResourceAction.ACCESS)));
   }

   // a caller with ADMIN on a resource outside the action tree only sees the grantees it
   // administers too
   @Test
   void resourceAdmin_read_hidesUnadministeredGrantees() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);
      Permission permission = mixedGrantees();
      when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);

      assertEquals(Set.of("USER:alice@orga", "GROUP:sales@orga", "ROLE:Everyone@orga"),
                   grantees(service.getPermission("X", "REPORT", principal)));
   }

   // a POST for a stored grantee hidden from the caller is not a duplicate in its view, it is
   // merged with the stored (kept) actions
   @Test
   void createPermissionGrant_hiddenStoredGrantee_mergedWithStoredActions() throws Exception {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("old"), ORG);
      when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);
      denyAdmin();

      service.createPermissionGrant("X", "REPORT", user("old", ORG, "WRITE"), principal);

      Permission saved = captureSaved(ResourceType.REPORT, "X");
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.READ)));
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.WRITE)));
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

   // stored ACCESS grants: users alice and old, groups sales and hr, org roles Everyone and
   // Designer, the global Organization Administrator role and the org itself (stored under its
   // name). See denyAdmin for the ones a non site admin cannot administer.
   private Permission mixedGrantees() {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("alice", "old"), ORG);
      permission.setGroupGrantsForOrg(ResourceAction.ACCESS, Set.of("sales", "hr"), ORG);
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of("Everyone", "Designer"), ORG);
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of(OA), null);
      permission.setOrganizationGrantsForOrg(ResourceAction.ACCESS, Set.of("Org A"), ORG);
      when(securityProvider.getOrgNameFromID(ORG)).thenReturn("Org A");
      denyAdmin();
      return permission;
   }

   // the caller cannot administer old, hr, Designer and the org, keyed as the write keeps them
   private void denyAdmin() {
      EnumSet<ResourceAction> admin = EnumSet.of(ResourceAction.ADMIN);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_USER,
         new IdentityID("old", ORG).convertToKey(), admin)).thenReturn(false);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_GROUP,
         new IdentityID("hr", ORG).convertToKey(), admin)).thenReturn(false);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_ROLE,
         new IdentityID("Designer", ORG).convertToKey(), admin)).thenReturn(false);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_ORGANIZATION,
         new IdentityID("Org A", ORG).convertToKey(), admin)).thenReturn(false);
   }

   // after a DELETE of mixedGrantees: only the unadministered grantees and the global role are
   // left, and the node is not edited
   private static void assertKeptUnadministered(Permission saved) {
      assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("hr@orga"), names(saved.getAllGroupGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Designer@orga", OA + "@null"),
                   names(saved.getAllRoleGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("Org A@orga"),
                   names(saved.getAllOrganizationGrants(ResourceAction.ACCESS)));
      assertFalse(saved.hasOrgEditedGrantAll(ORG));
   }

   private static Set<String> grantees(ResourcePermission view) {
      return view.getPermissionGrants().stream()
         .map(g -> g.getType() + ":" + g.getIdentityID().name + "@" + g.getIdentityID().orgID)
         .collect(Collectors.toSet());
   }

   // a non site admin that may only open the EM actions page (the tree branch of the check)
   private void delegate() {
      notSiteAdmin();
      when(securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT, ACTIONS,
                                            ResourceAction.ACCESS)).thenReturn(true);
   }

   // the caller holds ACCESS on settings/security/users, the only action of that node
   private void holdsUsers() throws Exception {
      when(securityEngine.checkPermission(principal, ResourceType.EM_COMPONENT, USERS,
                                          ResourceAction.ACCESS)).thenReturn(true);
   }

   // stored: "old" with ACCESS, the node edited by the org (an explicit restriction)
   private void storedEdited() {
      Permission permission = new Permission();
      permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);
   }

   private void notSiteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
   }

   private void siteAdmin() {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      when(securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT, ACTIONS,
                                            ResourceAction.ACCESS)).thenReturn(true);
   }

   // stored grants: the global Organization Administrator role and the org user "old", saved by
   // EM (edited by the org), so a write that leaves grants does not change the edited flag
   private Permission stored(ResourceType type, String path) {
      Permission permission = new Permission();
      permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      permission.setRoleGrantsForOrg(ResourceAction.ACCESS, Set.of(OA), null);
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("old"), ORG);
      when(securityProvider.getPermission(type, path)).thenReturn(permission);
      return permission;
   }

   // stored: "old" with ACCESS and a stale out-of-node ADMIN, and "alice" with ACCESS, edited by
   // the org as stored()
   private void staleAdmin() {
      Permission permission = new Permission();
      permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
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
      // the portal Dashboard tab leaf, as in ActionPermissionService.getPortalTabsNode
      ActionTreeNode dashboard = ActionTreeNode.builder()
         .resource("*").label("Dashboard").folder(false)
         .type(ResourceType.DASHBOARD).actions(EnumSet.of(ResourceAction.READ, ResourceAction.WRITE))
         .build();
      // a viewsheet toolbar action leaf, as in ActionPermissionService, granted by default
      ActionTreeNode toolbar = ActionTreeNode.builder()
         .resource(TOOLBAR).label(TOOLBAR).folder(false).grant(true)
         .type(ResourceType.VIEWSHEET_TOOLBAR_ACTION).actions(EnumSet.of(ResourceAction.READ))
         .build();
      return ActionTreeNode.builder()
         .label("").folder(true).actions(EnumSet.noneOf(ResourceAction.class))
         .addChildren(leaf, dashboard, toolbar)
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
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
}
