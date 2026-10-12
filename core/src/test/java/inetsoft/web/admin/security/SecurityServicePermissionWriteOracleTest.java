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

import inetsoft.sree.portal.CustomThemesManager;
import inetsoft.sree.security.*;
import inetsoft.web.admin.general.LocalizationSettingsService;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import inetsoft.web.admin.security.user.*;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
 * Bug #77425: the permission write endpoints (PUT /api/public/security/permissions and the POST/PUT
 * grant endpoints) validated a request row on an action tree node against the stored grants of
 * every grantee, also the ones hidden from the caller's view (Bug #77404). Whether a row for a
 * hidden grantee was accepted or rejected so told the caller what that grantee holds. Each probe
 * here is run once per stored state of the hidden grantee, with the visible state unchanged, and
 * must give the same response every time.
 */
@Tag("core")
class SecurityServicePermissionWriteOracleTest {
   private static final String ORG = "orga";
   private static final String USERS = "settings/security/users";
   private static final String ACTIONS = "settings/security/actions";
   private static final String OA = "Organization Administrator";
   private static final String NOT_HELD = "403 Unauthorized grant";
   private static final String NOT_OFFERED = "403 Unauthorized action";
   private static final String ACCEPTED = "accepted";

   // the grantees the caller cannot administer (see setUp), one of each type, and a global role
   private static final List<Hidden> HIDDEN = List.of(
      new Hidden("USER", "old", ORG), new Hidden("GROUP", "hr", ORG),
      new Hidden("ROLE", "Designer", ORG), new Hidden("ORGANIZATION", "Org A", ORG),
      new Hidden("ROLE", OA, null));

   // the stored states of the hidden grantee, ADMIN is not offered by the node
   private static final List<Set<ResourceAction>> STATES = List.of(
      EnumSet.noneOf(ResourceAction.class), EnumSet.of(ResourceAction.ACCESS),
      EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN), EnumSet.of(ResourceAction.ADMIN));

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

      when(securityProvider.getRole(new IdentityID(OA, null))).thenReturn(mock(Role.class));
      when(securityProvider.getRole(new IdentityID("Designer", ORG))).thenReturn(mock(Role.class));
      when(securityProvider.getOrgNameFromID(ORG)).thenReturn("Org A");

      // the caller administers every identity but old, hr, Designer and the org, keyed as the
      // write keeps them
      EnumSet<ResourceAction> admin = EnumSet.of(ResourceAction.ADMIN);
      when(securityProvider.checkAnyPermission(any(), any(), anyString(), any())).thenReturn(true);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_USER,
         new IdentityID("old", ORG).convertToKey(), admin)).thenReturn(false);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_GROUP,
         new IdentityID("hr", ORG).convertToKey(), admin)).thenReturn(false);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_ROLE,
         new IdentityID("Designer", ORG).convertToKey(), admin)).thenReturn(false);
      when(securityProvider.checkAnyPermission(principal, ResourceType.SECURITY_ORGANIZATION,
         new IdentityID("Org A", ORG).convertToKey(), admin)).thenReturn(false);

      // a non site admin that may open the EM actions page
      when(orgManager.isSiteAdmin(principal)).thenReturn(false);
      when(securityProvider.checkPermission(principal, ResourceType.EM_COMPONENT, ACTIONS,
                                            ResourceAction.ACCESS)).thenReturn(true);

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

   // ── the three oracles of the report ────────────────────────────────────

   // A: a delegate that does not hold the node, sending an offered action for a hidden user, was
   // accepted when the user already held it and rejected when not
   @Test
   void delegateNotHoldingNode_offeredActionForHiddenUser_alwaysRejected() throws Exception {
      Hidden old = HIDDEN.get(0);

      for(Set<ResourceAction> state : STATES) {
         assertEquals(NOT_HELD, run(Verb.PUT, old, state, false, "ACCESS").outcome, state::toString);
      }
   }

   // B: a delegate that holds the node, resending the not offered ADMIN of a hidden user, was
   // accepted when the user held {ACCESS, ADMIN} and rejected when it held {ACCESS}
   @Test
   void delegateHoldingNode_notOfferedActionForHiddenUser_alwaysRejected() throws Exception {
      Caller.HOLDING.setUp(this);
      Hidden old = HIDDEN.get(0);

      for(Set<ResourceAction> state : STATES) {
         assertEquals(NOT_OFFERED, run(Verb.PUT, old, state, false, "ACCESS", "ADMIN").outcome,
                      state::toString);
      }
   }

   // C: an org admin, which does not see global roles, probing a global role with the not offered
   // ADMIN, was accepted when the role held it and rejected when not
   @Test
   void orgAdmin_notOfferedActionForGlobalRole_alwaysRejected() throws Exception {
      Caller.ORG_ADMIN.setUp(this);
      Hidden globalRole = HIDDEN.get(4);

      for(Set<ResourceAction> state : STATES) {
         assertEquals(NOT_OFFERED, run(Verb.PUT, globalRole, state, false, "ADMIN").outcome,
                      state::toString);
      }
   }

   // ── every caller, grantee type, verb, action and edited state ──────────

   // the response depends only on the request and the caller's own view, and an accepted write
   // stores stored(H) ∪ request(H) for the hidden grantee (stored(H) for a global role row of a
   // non site admin, which the write ignores)
   @ParameterizedTest
   @EnumSource(Caller.class)
   void hiddenGrantee_sameOutcomeForEveryStoredState_andMerged(Caller caller) throws Exception {
      caller.setUp(this);

      for(Hidden hidden : HIDDEN) {
         for(Verb verb : Verb.values()) {
            for(String action : List.of("ACCESS", "ADMIN")) {
               for(boolean edited : List.of(false, true)) {
                  String probe = caller + " " + verb + " " + hidden + " " + action +
                     (edited ? " edited" : "");
                  String expected = expected(caller, verb, hidden, action, edited);

                  for(Set<ResourceAction> state : STATES) {
                     Result result = run(verb, hidden, state, edited, action);
                     assertEquals(expected, result.outcome, probe + " stored " + state);

                     if(result.saved != null) {
                        Set<ResourceAction> merged = EnumSet.noneOf(ResourceAction.class);
                        merged.addAll(state);

                        if(!hidden.globalRole()) {
                           merged.add(ResourceAction.valueOf(action));
                        }

                        assertEquals(merged, hidden.actions(result.saved),
                                     probe + " stored " + state);
                     }
                  }
               }
            }
         }
      }
   }

   // Bug #78041, an empty PUT keeps the node edited when kept hidden grants remain. For a
   // delegate that does not hold the node, whether the write changes the flag is judged on its own
   // request, so the outcome does not tell whether a hidden grantee is stored, and the flag is
   // left as stored instead of being changed by the hidden grants.
   @Test
   void notHolding_emptyPut_sameOutcomeWhetherHiddenGranteeStored() throws Exception {
      Hidden old = HIDDEN.get(0);

      for(boolean edited : List.of(false, true)) {
         for(boolean hiddenStored : List.of(false, true)) {
            Permission permission = new Permission();

            if(edited) {
               permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
            }

            permission.setUserGrantsForOrg(
               ResourceAction.ACCESS, hiddenStored ? Set.of(old.name) : Set.of(), ORG);
            when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS))
               .thenReturn(permission);
            clearInvocations(securityProvider);
            String probe = (edited ? "edited" : "not edited") + " hidden " + hiddenStored;

            if(edited) {
               assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
                  USERS, "EM_COMPONENT", model(), principal), probe);
               verify(securityProvider, never()).setPermission(any(), anyString(), any());
            }
            else {
               service.setPermission(USERS, "EM_COMPONENT", model(), principal);
               Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
               assertFalse(saved.hasOrgEditedGrantAll(ORG), probe);
               assertEquals(hiddenStored ? Set.of("old@orga") : Set.of(),
                            names(saved.getAllUserGrants(ResourceAction.ACCESS)), probe);
            }
         }
      }
   }

   // ── controls ───────────────────────────────────────────────────────────

   // a hidden user's stored out-of-node ADMIN, not in the caller's view, is still kept when the
   // caller sends its view back unchanged
   @Test
   void unchangedRoundTrip_keepsHiddenOutOfNodeAction() throws Exception {
      Caller.HOLDING.setUp(this);
      Hidden old = HIDDEN.get(0);
      stub(old, EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN), false);

      service.setPermission(USERS, "EM_COMPONENT",
                            service.getPermission(USERS, "EM_COMPONENT", principal), principal);

      assertEquals(EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN),
                   old.actions(captureSaved(ResourceType.EM_COMPONENT, USERS)));
   }

   // a visible grantee still resends its stored out-of-node action (Bug #77376) and is not a new
   // grant for a delegate that does not hold the node (Bug #77385)
   @Test
   void visibleGrantee_storedActions_stillCountAsStored() throws Exception {
      Permission permission = new Permission();
      permission.setUserGrantsForOrg(ResourceAction.ACCESS, Set.of("alice"), ORG);
      permission.setUserGrantsForOrg(ResourceAction.ADMIN, Set.of("alice"), ORG);
      when(securityProvider.getPermission(ResourceType.EM_COMPONENT, USERS)).thenReturn(permission);

      service.setPermission(USERS, "EM_COMPONENT",
                            service.getPermission(USERS, "EM_COMPONENT", principal), principal);

      Permission saved = captureSaved(ResourceType.EM_COMPONENT, USERS);
      assertEquals(Set.of("alice@orga"), names(saved.getAllUserGrants(ResourceAction.ACCESS)));
      assertEquals(Set.of("alice@orga"), names(saved.getAllUserGrants(ResourceAction.ADMIN)));
   }

   // a site admin sees every grantee, so its stored actions still count, also for a global role
   @Test
   void siteAdmin_storedOutOfNodeActionOfGlobalRole_accepted() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);
      Hidden globalRole = HIDDEN.get(4);
      stub(globalRole, EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN), false);

      service.setPermission(USERS, "EM_COMPONENT",
                            service.getPermission(USERS, "EM_COMPONENT", principal), principal);

      assertEquals(EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN),
                   globalRole.actions(captureSaved(ResourceType.EM_COMPONENT, USERS)));
   }

   // a repository resource is not validated against the stored grants: its admin may still grant
   // to a colleague it cannot administer, merged with that colleague's stored actions
   @Test
   void repositoryResource_hiddenGrantee_acceptedAndMerged() throws Exception {
      when(securityProvider.checkPermission(principal, ResourceType.REPORT, "X",
                                            ResourceAction.ADMIN)).thenReturn(true);

      for(Set<ResourceAction> state : List.<Set<ResourceAction>>of(
         EnumSet.noneOf(ResourceAction.class), EnumSet.of(ResourceAction.READ)))
      {
         Permission permission = new Permission();
         permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice"), ORG);

         if(!state.isEmpty()) {
            permission.setUserGrantsForOrg(ResourceAction.READ, Set.of("alice", "old"), ORG);
         }

         when(securityProvider.getPermission(ResourceType.REPORT, "X")).thenReturn(permission);
         clearInvocations(securityProvider);

         service.createPermissionGrant("X", "REPORT", grant("USER", "old", ORG, "WRITE"),
                                       principal);

         Permission saved = captureSaved(ResourceType.REPORT, "X");
         assertEquals(state.isEmpty() ? Set.of("alice@orga") : Set.of("alice@orga", "old@orga"),
                      names(saved.getAllUserGrants(ResourceAction.READ)));
         assertEquals(Set.of("old@orga"), names(saved.getAllUserGrants(ResourceAction.WRITE)));
      }
   }

   // a row without a type is not administered and does not throw while it is validated
   @Test
   void rowWithoutType_notAdministered_rejectedWithoutNpe() {
      stub(HIDDEN.get(0), EnumSet.of(ResourceAction.ACCESS), false);
      PermissionGrant row = grant(null, "old", ORG, "ACCESS");

      assertThrows(UnauthorizedAccessException.class, () -> service.setPermission(
         USERS, "EM_COMPONENT", model(row), principal));

      verify(securityProvider, never()).setPermission(any(), anyString(), any());
   }

   // a row for a hidden grantee of the current org may name it without an org or with the org in
   // another case, both are stored in the current org. The caller's rights are checked on that key,
   // not on the org as sent, else such a row would count as administered and read the stored state.
   // A row of another org is rejected whatever is stored.
   @Test
   void hiddenGrantee_rowOrgNullOrOtherCase_sameOutcomeForEveryStoredState() throws Exception {
      for(Hidden hidden : List.of(HIDDEN.get(0), HIDDEN.get(2))) {
         for(String rowOrg : Arrays.asList(null, "ORGA", "orgb")) {
            Set<String> notHolding = new HashSet<>();
            Set<String> holding = new HashSet<>();

            for(Set<ResourceAction> state : STATES) {
               notHolding.add(runRow(hidden, rowOrg, state, "ACCESS"));
            }

            Caller.HOLDING.setUp(this);

            for(Set<ResourceAction> state : STATES) {
               holding.add(runRow(hidden, rowOrg, state, "ACCESS", "ADMIN"));
            }

            reset(securityEngine);
            when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
            String invalid = "403 Invalid organization";
            String where = hidden.type + " " + hidden.name + "@" + rowOrg;

            assertEquals(Set.of("orgb".equals(rowOrg) ? invalid : NOT_HELD), notHolding, where);
            assertEquals(Set.of("orgb".equals(rowOrg) ? invalid : NOT_OFFERED), holding, where);
         }
      }
   }

   // runs a PUT of the caller's view plus a row for the hidden grantee sent with the given org
   private String runRow(Hidden hidden, String rowOrg, Set<ResourceAction> state,
                         String... actions) throws Exception
   {
      stub(hidden, state, false);
      clearInvocations(securityProvider);
      ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);
      view.getPermissionGrants().add(grant(hidden.type, hidden.name, rowOrg, actions));

      try {
         service.setPermission(USERS, "EM_COMPONENT", view, principal);
      }
      catch(UnauthorizedAccessException e) {
         verify(securityProvider, never()).setPermission(any(), anyString(), any());
         String message = e.getMessage();
         return message.startsWith("Unauthorized action") ? NOT_OFFERED :
            message.startsWith("Unauthorized grant") ? NOT_HELD :
            message.startsWith("Invalid organization") ? "403 Invalid organization" :
            "403 " + message;
      }

      return ACCEPTED;
   }

   // ── helpers ─────────────────────────────────────────────────────────────

   // the outcome without a hidden grantee's stored state: an action the node does not offer is
   // never stored for it, an offered one is always a new grant, and a non site admin's global role
   // rows are ignored by the write. Bug #78041, such an ignored row still needs the hold when the
   // write changes the org's edited flag as the caller sees it: the PUT sends the view's
   // inheritFromParent back unchanged, the POST keeps alice's grant (edited) and the RENAME
   // replaces alice's grant with the ignored row (no grant left, not edited).
   private static String expected(Caller caller, Verb verb, Hidden hidden, String action,
                                  boolean edited)
   {
      if("ADMIN".equals(action)) {
         return NOT_OFFERED;
      }

      if(caller != Caller.NOT_HOLDING) {
         return ACCEPTED;
      }

      if(!hidden.globalRole()) {
         return NOT_HELD;
      }

      boolean editedAfter = switch(verb) {
         case PUT -> edited;
         case POST -> true;
         case RENAME -> false;
      };

      return editedAfter == edited ? ACCEPTED : NOT_HELD;
   }

   // runs one probe: the caller's visible state is alice with ACCESS, the hidden grantee holds
   // the given actions
   private Result run(Verb verb, Hidden hidden, Set<ResourceAction> state, boolean edited,
                      String... actions) throws Exception
   {
      stub(hidden, state, edited);
      clearInvocations(securityProvider);
      PermissionGrant row = grant(hidden.type, hidden.name, hidden.org, actions);

      try {
         switch(verb) {
         case PUT -> {
            ResourcePermission view = service.getPermission(USERS, "EM_COMPONENT", principal);
            view.getPermissionGrants().add(row);
            service.setPermission(USERS, "EM_COMPONENT", view, principal);
         }
         case POST -> service.createPermissionGrant(USERS, "EM_COMPONENT", row, principal);
         case RENAME -> service.updatePermissionGrant(
            USERS, "EM_COMPONENT", new IdentityID("alice", ORG).convertToKey(), "USER", row,
            principal);
         }
      }
      catch(UnauthorizedAccessException e) {
         verify(securityProvider, never()).setPermission(any(), anyString(), any());
         String message = e.getMessage();
         return new Result(message.startsWith("Unauthorized action") ? NOT_OFFERED :
                              message.startsWith("Unauthorized grant") ? NOT_HELD :
                              "403 " + message, null);
      }

      return new Result(ACCEPTED, captureSaved(ResourceType.EM_COMPONENT, USERS));
   }

   private void stub(Hidden hidden, Set<ResourceAction> state, boolean edited) {
      Permission permission = new Permission();

      if(edited) {
         permission.setOrgEditedGrantAll(new HashMap<>(Map.of(ORG, true)));
      }

      for(ResourceAction action : EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN)) {
         Set<String> users = new HashSet<>();
         Set<String> held = state.contains(action) ? Set.of(hidden.name) : Set.of();

         if(action == ResourceAction.ACCESS) {
            users.add("alice");
         }

         switch(hidden.type) {
         case "USER" -> users.addAll(held);
         case "GROUP" -> permission.setGroupGrantsForOrg(action, held, ORG);
         case "ROLE" -> permission.setRoleGrantsForOrg(action, held, hidden.org);
         case "ORGANIZATION" -> permission.setOrganizationGrantsForOrg(action, held, ORG);
         }

         permission.setUserGrantsForOrg(action, users, ORG);
      }

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

   enum Caller {
      // holds ACCESS on the actions page only
      NOT_HOLDING,
      // also holds the node's only action
      HOLDING,
      // an org admin that does not hold the node
      ORG_ADMIN;

      void setUp(SecurityServicePermissionWriteOracleTest test) throws Exception {
         if(this == HOLDING) {
            when(test.securityEngine.checkPermission(test.principal, ResourceType.EM_COMPONENT,
                                                     USERS, ResourceAction.ACCESS))
               .thenReturn(true);
         }
         else if(this == ORG_ADMIN) {
            when(test.orgManager.isOrgAdmin(test.principal)).thenReturn(true);
         }
      }
   }

   enum Verb { PUT, POST, RENAME }

   // a grantee hidden from the caller, org is the request's org and the stored bucket
   private record Hidden(String type, String name, String org) {
      boolean globalRole() {
         return org == null;
      }

      Set<ResourceAction> actions(Permission saved) {
         Set<ResourceAction> result = EnumSet.noneOf(ResourceAction.class);

         for(ResourceAction action : EnumSet.of(ResourceAction.ACCESS, ResourceAction.ADMIN)) {
            Set<Permission.PermissionIdentity> grants = switch(type) {
               case "USER" -> saved.getAllUserGrants(action);
               case "GROUP" -> saved.getAllGroupGrants(action);
               case "ROLE" -> saved.getAllRoleGrants(action);
               default -> saved.getAllOrganizationGrants(action);
            };

            if(names(grants).contains(name + "@" + org)) {
               result.add(action);
            }
         }

         return result;
      }
   }

   private record Result(String outcome, Permission saved) {
   }

   private SecurityProvider securityProvider;
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private Principal principal;
   private SecurityService service;
   private MockedStatic<OrganizationManager> organizationManagerStatic;
}
