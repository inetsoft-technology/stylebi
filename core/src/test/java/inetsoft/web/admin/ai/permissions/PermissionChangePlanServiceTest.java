/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.web.admin.ai.permissions;

import inetsoft.web.admin.security.PermissionGrant;
import inetsoft.web.admin.security.SecurityService;
import inetsoft.sree.security.*;
import inetsoft.util.Tool;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * The plan hash is the review gate here the same way it is for properties and schedule tasks
 * (spec section 5) -- these tests focus on what is NEW in this area: a total resource-permission
 * projection rather than a single grant or a scalar (spec section 5), the resource-type allowlist
 * (spec section 1), the self-lockout preflight (spec section 4), and that {@code ORGANIZATION} is
 * a valid identity type on every verb (spec section 2's correction).
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class PermissionChangePlanServiceTest {
   @Mock private SecurityService securityService;
   @Mock private SecurityEngine securityEngine;
   @Mock private SecurityProvider securityProvider;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private PermissionChangePlanService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<Tool> tool;

   @BeforeEach void setUp() {
      service = new PermissionChangePlanService(securityService, securityEngine);
      lenient().when(securityEngine.getSecurityProvider()).thenReturn(securityProvider);
      // Bug #78857: create refuses an identity the provider does not know -- these fixtures' bare
      // names (bob, alice, other-org, ...) stand for existing identities
      lenient().when(securityProvider.getUser(any())).thenReturn(mock(User.class));
      lenient().when(securityProvider.getGroup(any())).thenReturn(mock(Group.class));
      lenient().when(securityProvider.getRole(any())).thenReturn(mock(Role.class));
      lenient().when(securityProvider.getOrganization(any())).thenReturn(mock(Organization.class));

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      // Callers are identified as "name~;~orgId" so IdentityID parsing never falls through to
      // the ThreadContext-dependent bare-name branch, per this test class's own design note.
      lenient().when(user.getName()).thenReturn(new IdentityID("caller", "host-org").convertToKey());

      tool = mockStatic(Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() {
      PermissionChangePlanRequest req = request("   ", List.of(deleteChange("alice")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() {
      PermissionChangePlanRequest req = request("do something", List.of());

      assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnUnrecognizedVerb() {
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));
      change.setVerb("rename");
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnExcludedResourceType() {
      PermissionChangeRequest change = grantChange("SCHEDULE_TASK", "t1", "USER", "bob",
         List.of("READ"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("resourceType"));
      verifyNoInteractions(securityService);
   }

   // -------------------------------------------------------------------------
   // widened allowlist (bug 76600 Gap 1) -- Security Actions capability-level types
   // -------------------------------------------------------------------------

   @Test void resolveAcceptsNewlyWidenedCapabilityResourceTypes() throws Exception {
      stubActionTree(ResourceType.AI_ASSISTANT, "*");
      stubActionTree(ResourceType.FREE_FORM_SQL, "*");
      stubRawPermission("AI_ASSISTANT", "*", null);
      when(securityService.getPermissionGrant("*", "AI_ASSISTANT", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest aiAssistant = grantChange("AI_ASSISTANT", "*", "USER", "bob",
         List.of("ACCESS"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(aiAssistant)), user));

      stubRawPermission("FREE_FORM_SQL", "*", null);
      when(securityService.getPermissionGrant("*", "FREE_FORM_SQL", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest freeFormSql = grantChange("FREE_FORM_SQL", "*", "USER", "bob",
         List.of("ACCESS"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(freeFormSql)), user));
   }

   // -------------------------------------------------------------------------
   // Bug #79216 -- a capability-level type's resourcePath must be a node of the action tree
   // -------------------------------------------------------------------------

   @Test void createRefusesAMadeUpPathOnACapabilityType() throws Exception {
      stubActionTree(ResourceType.VIEWSHEET_ACTION, "Bookmark", "OpenBookmark", "ShareBookmark");
      PermissionChangeRequest change = grantChange("VIEWSHEET_ACTION", "UseBookmark", "ROLE",
         "bob", List.of("READ"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));

      assertEquals("changes[0].resourcePath: \"UseBookmark\" is not a resource of type " +
                   "VIEWSHEET_ACTION; call list_grantable_permission_actions for the grantable " +
                   "(resourceType, resourcePath) pairs; valid: Bookmark, OpenBookmark, " +
                   "ShareBookmark", ex.getMessage());
   }

   @Test void createRefusesAnUnknownChildOfAFolderNode() throws Exception {
      stubActionTree(ResourceType.CHART_TYPE, "Bar/Bar", "Bar/Interval");
      PermissionChangeRequest change = grantChange("CHART_TYPE", "Bar/Nope", "USER", "bob",
         List.of("READ"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));

      assertTrue(ex.getMessage().contains("changes[0].resourcePath"));
      assertTrue(ex.getMessage().contains("\"Bar/Nope\""));
   }

   @Test void createRefusesAPathOnATypeAbsentFromTheCallersTree() {
      PermissionChangeRequest change = grantChange("DEVICE", "foo", "USER", "bob",
         List.of("ACCESS"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));

      assertTrue(ex.getMessage().contains("changes[0].resourcePath"));
      assertTrue(ex.getMessage().contains("list_grantable_permission_actions"));
      assertFalse(ex.getMessage().contains("valid:"));
   }

   @Test void refusalOmitsTheInlineListWhenTheTypeHasManyPaths() throws Exception {
      String[] paths = new String[30];

      for(int i = 0; i < paths.length; i++) {
         paths[i] = "Chart" + i;
      }

      stubActionTree(ResourceType.CHART_TYPE, paths);
      PermissionChangeRequest change = grantChange("CHART_TYPE", "foo", "USER", "bob",
         List.of("READ"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));

      assertTrue(ex.getMessage().contains("list_grantable_permission_actions"));
      assertFalse(ex.getMessage().contains("valid:"));
      assertFalse(ex.getMessage().contains("Chart0"));
   }

   @Test void createAcceptsEveryKindOfActionTreeNode() throws Exception {
      // leaf, folder keyed CHART_TYPE_FOLDER (whether it has children or not), a child keyed
      // CHART_TYPE, and a portal tab named by the site's own configuration
      stubActionTree(ResourceType.VIEWSHEET_ACTION, "Bookmark");
      stubActionTree(ResourceType.CHART_TYPE_FOLDER, "Bar", "Point");
      stubActionTree(ResourceType.CHART_TYPE, "Bar/Interval");
      stubActionTree(ResourceType.PORTAL_TAB, "Design", "My Custom Tab");

      for(String[] target : new String[][] {
         { "VIEWSHEET_ACTION", "Bookmark" }, { "CHART_TYPE_FOLDER", "Bar" },
         { "CHART_TYPE_FOLDER", "Point" }, { "CHART_TYPE", "Bar/Interval" },
         { "PORTAL_TAB", "My Custom Tab" } })
      {
         stubRawPermission(target[0], target[1], null);
         when(securityService.getPermissionGrant(target[1], target[0], "bob", "USER", user))
            .thenReturn(null);
         PermissionChangeRequest change = grantChange(target[0], target[1], "USER", "bob",
            List.of("READ"));

         assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user),
                            target[0] + " " + target[1]);
      }
   }

   @Test void updateAndDeleteOfAStaleCapabilityGrantStillWork() throws Exception {
      // the tree no longer lists the path, but the grant exists and must stay editable/removable
      Permission before = new Permission();
      before.setUserGrantsForOrg(ResourceAction.READ, java.util.Set.of("bob"), "host-org");
      stubRawPermission("VIEWSHEET_ACTION", "UseBookmark", before);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("READ"));
      when(securityService.getPermissionGrant("UseBookmark", "VIEWSHEET_ACTION", "bob", "USER",
                                              user)).thenReturn(existing);
      PermissionChangeRequest update = updateChange("VIEWSHEET_ACTION", "UseBookmark", "USER",
         "bob", List.of("READ", "WRITE"));
      PermissionChangeRequest delete = deleteChange("bob");
      delete.setResourceType("VIEWSHEET_ACTION");
      delete.setResourcePath("UseBookmark");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(update)), user));
      assertDoesNotThrow(() -> service.resolve(request("task", List.of(delete)), user));
   }

   @Test void repositoryPathTypesAndDashboardAreNotCheckedAgainstTheTree() throws Exception {
      for(String type : List.of("ASSET", "DATA_SOURCE", "DATA_SOURCE_FOLDER", "SCRIPT",
                                "TABLE_STYLE", "DASHBOARD"))
      {
         stubRawPermission(type, "made/up/path", null);
         when(securityService.getPermissionGrant("made/up/path", type, "bob", "USER", user))
            .thenReturn(null);
         PermissionChangeRequest change = grantChange(type, "made/up/path", "USER", "bob",
            List.of("READ"));

         assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user), type);
      }

      verify(securityService, never()).getActionTreeResourcePaths(any(), any());
   }

   @Test void resolveThrowsOnEmResourceType() {
      PermissionChangeRequest change = grantChange("EM", "*", "USER", "bob", List.of("ACCESS"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("resourceType"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveThrowsOnEmComponentResourceTypeNamingTheMasterGateMechanism() {
      PermissionChangeRequest change = grantChange("EM_COMPONENT", "settings/security/actions",
         "USER", "bob", List.of("ACCESS"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("EM_COMPONENT"));
      assertTrue(ex.getMessage().contains("master gate"));
      verifyNoInteractions(securityService);
   }

   @Test void resolveThrowsOnUnrecognizedIdentityType() {
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "BOGUS", "bob",
         List.of("READ"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("identityType"));
   }

   @Test void resolveAcceptsOrganizationIdentityTypeOnCreate() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "other-org",
         "ORGANIZATION", user)).thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "ORGANIZATION",
         "other-org", List.of("READ"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      assertDoesNotThrow(() -> service.resolve(req, user));
   }

   @Test void resolveThrowsOnDuplicateEntry() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      lenient().when(securityService.getPermissionGrant(anyString(), anyString(), anyString(),
         anyString(), eq(user))).thenReturn(null);
      PermissionChangeRequest change1 = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));
      PermissionChangeRequest change2 = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("WRITE"));
      PermissionChangePlanRequest req = request("task", List.of(change1, change2));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   // -------------------------------------------------------------------------
   // create
   // -------------------------------------------------------------------------

   @Test void resolveCreateThrowsWhenActionsMissing() {
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob", null);
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("actions"));
   }

   /**
    * {@code SecurityService.setPermission} selects grants with an exact-case
    * {@code contains(action.name())}, so a lowercase action silently persists nothing without
    * throwing and the apply service's post-write verification then blames the write. This must
    * fail at plan time instead, naming the offending action.
    */
   @Test void resolveCreateThrowsOnLowercaseAction() {
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("read"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("actions"), ex.getMessage());
      assertTrue(ex.getMessage().contains("read"), ex.getMessage());
      assertTrue(ex.getMessage().contains("case-sensitive"), ex.getMessage());
   }

   @Test void resolveCreateThrowsOnUnrecognizedAction() {
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ", "SUPERUSER"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("SUPERUSER"), ex.getMessage());
   }

   @Test void resolveUpdateThrowsOnLowercaseAction() {
      PermissionChangeRequest change = updateChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("write"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("write"), ex.getMessage());
   }

   @Test void resolveCreateThrowsWhenGrantAlreadyExists() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("READ"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(existing);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("WRITE"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveCreateSucceedsWhenNoPriorGrant() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));
      PermissionChangePlanRequest req = request("create a grant", List.of(change));

      var plan = service.resolve(req, user);

      assertEquals(1, plan.changes().size());
      var pc = plan.changes().get(0);
      assertEquals("ASSET|Examples/Census|USER|bob~;~host-org", pc.property());
      assertEquals("high", pc.risk());
      assertEquals("value", pc.snapshotScope());
      assertFalse(plan.requiresStorageBackup());
      assertTrue(plan.requiresAgentSignoff());
      assertNotNull(plan.planHash());
   }

   // -------------------------------------------------------------------------
   // update / delete
   // -------------------------------------------------------------------------

   @Test void resolveUpdateThrowsWhenGrantDoesNotExist() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = updateChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("WRITE"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no grant exists"));
   }

   // -------------------------------------------------------------------------
   // preview diff (bug 76352 PM-001) -- proposedValue reflects the requested change
   // -------------------------------------------------------------------------

   @Test void resolveCreateOnAResourceWithNoExistingGrantShowsProposedActions() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));
      PermissionChangePlanRequest req = request("create a grant", List.of(change));

      var pc = service.resolve(req, user).changes().get(0);

      assertNull(pc.currentValue());
      assertNotNull(pc.proposedValue());
      assertTrue(pc.proposedValue().contains("USER|host-org|bob=READ"));
      assertNotEquals(pc.currentValue(), pc.proposedValue());
   }

   @Test void resolveUpdateProposedValueReflectsNewActionsAndLeavesUnrelatedGrantUnchanged()
      throws Exception
   {
      Permission before = new Permission();
      before.setUserGrantsForOrg(ResourceAction.READ, java.util.Set.of("bob"), "host-org");
      before.setUserGrantsForOrg(ResourceAction.READ, java.util.Set.of("alice"), "host-org");
      stubRawPermission("ASSET", "Examples/Census", before);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("READ"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(existing);
      PermissionChangeRequest change = updateChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ", "WRITE"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      var pc = service.resolve(req, user).changes().get(0);

      assertNotEquals(pc.currentValue(), pc.proposedValue());
      assertTrue(pc.proposedValue().contains("USER|host-org|bob=READ,WRITE"));
      assertTrue(pc.currentValue().contains("USER|host-org|alice=READ"));
      assertTrue(pc.proposedValue().contains("USER|host-org|alice=READ"));
   }

   @Test void resolveDeleteProposedValueDiffersFromCurrentValue() throws Exception {
      Permission before = new Permission();
      before.setUserGrantsForOrg(ResourceAction.ADMIN, java.util.Set.of("bob"), "host-org");
      stubRawPermission("ASSET", "Examples/Census", before);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("ADMIN"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(existing);
      PermissionChangeRequest change = deleteChange("bob");
      PermissionChangePlanRequest req = request("task", List.of(change));

      var pc = service.resolve(req, user).changes().get(0);

      assertNotEquals(pc.currentValue(), pc.proposedValue());
      assertTrue(pc.currentValue().contains("bob"));
      assertFalse(pc.proposedValue().contains("bob"));
   }

   @Test void resolveDeleteThrowsWhenActionsPresent() {
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));
      change.setVerb(PermissionChangeRequest.VERB_DELETE);
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("actions"));
   }

   @Test void resolveDeleteThrowsWhenGrantDoesNotExist() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = deleteChange("bob");
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("no grant exists"));
   }

   // -------------------------------------------------------------------------
   // self-lockout preflight (spec section 4) -- applied unconditionally
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsWhenCallerWouldRemoveOwnAdminGrant() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("ADMIN"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "caller", "USER",
         user)).thenReturn(existing);
      PermissionChangeRequest change = deleteChange("caller");
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("own ADMIN"));
   }

   @Test void resolveUpdateThrowsWhenCallerWouldDropOwnAdmin() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("ADMIN"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "caller", "USER",
         user)).thenReturn(existing);
      PermissionChangeRequest change = updateChange("ASSET", "Examples/Census", "USER", "caller",
         List.of("READ"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("own ADMIN"));
   }

   @Test void resolveUpdateDoesNotThrowWhenCallerKeepsOwnAdmin() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("ADMIN"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "caller", "USER",
         user)).thenReturn(existing);
      PermissionChangeRequest change = updateChange("ASSET", "Examples/Census", "USER", "caller",
         List.of("ADMIN", "WRITE"));
      PermissionChangePlanRequest req = request("task", List.of(change));

      assertDoesNotThrow(() -> service.resolve(req, user));
   }

   @Test void resolveDeleteDoesNotThrowForAThirdPartyTarget() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      PermissionGrant existing = new PermissionGrant();
      existing.setActions(List.of("ADMIN"));
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(existing);
      PermissionChangeRequest change = deleteChange("bob");
      PermissionChangePlanRequest req = request("task", List.of(change));

      assertDoesNotThrow(() -> service.resolve(req, user));
   }

   // -------------------------------------------------------------------------
   // hash stability -- total-projection collision closed (spec section 5)
   // -------------------------------------------------------------------------

   @Test void hashChangesWhenAConcurrentUnrelatedGrantIsAddedToTheSameResource() throws Exception {
      Permission before = new Permission();
      Permission after = new Permission();
      after.setUserGrantsForOrg(ResourceAction.ADMIN, java.util.Set.of("carol"), "host-org");

      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));

      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(before);
      var beforePlan = service.resolve(request("t", List.of(change)), user);

      when(securityProvider.getPermission(ResourceType.ASSET, "Examples/Census", null))
         .thenReturn(after);
      var afterPlan = service.resolve(request("t", List.of(change)), user);

      assertNotEquals(beforePlan.planHash(), afterPlan.planHash());
   }

   @Test void hashIsStableForIdenticalRequests() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));

      var first = service.resolve(request("t", List.of(change)), user);
      var second = service.resolve(request("t", List.of(change)), user);

      assertEquals(first.planHash(), second.planHash());
   }

   @Test void issuesATaskTokenBoundToThePlanHash() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));

      var plan = service.resolve(request("t", List.of(change)), user);

      assertEquals("TKN:" + plan.planHash() + "\u001ft", plan.taskToken());
   }

   // task is a free-text, audit-only label (bug 76454) -- a caller that does not replay it
   // byte-for-byte between preview and apply must not see a false planHash conflict.
   @Test void hashIsUnaffectedByDifferentTaskStrings() throws Exception {
      stubRawPermission("ASSET", "Examples/Census", null);
      when(securityService.getPermissionGrant("Examples/Census", "ASSET", "bob", "USER", user))
         .thenReturn(null);
      PermissionChangeRequest change = grantChange("ASSET", "Examples/Census", "USER", "bob",
         List.of("READ"));

      var first = service.resolve(request("grant bob read access", List.of(change)), user);
      var second = service.resolve(request("give bob read permission", List.of(change)), user);

      assertEquals(first.planHash(), second.planHash());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private void stubActionTree(ResourceType type, String... paths) {
      lenient().when(securityService.getActionTreeResourcePaths(eq(type), any()))
         .thenReturn(List.of(paths));
   }

   private void stubRawPermission(String resourceType, String resourcePath, Permission value) {
      lenient().when(securityProvider.getPermission(ResourceType.valueOf(resourceType),
         resourcePath, null)).thenReturn(value);
   }

   private static PermissionChangePlanRequest request(String task,
                                                       List<PermissionChangeRequest> changes)
   {
      PermissionChangePlanRequest req = new PermissionChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static PermissionChangeRequest grantChange(String resourceType, String resourcePath,
                                                       String identityType, String identityId,
                                                       List<String> actions)
   {
      PermissionChangeRequest change = new PermissionChangeRequest();
      change.setVerb(PermissionChangeRequest.VERB_CREATE);
      change.setResourceType(resourceType);
      change.setResourcePath(resourcePath);
      change.setIdentityType(identityType);
      change.setIdentityId(identityId);
      change.setActions(actions);
      return change;
   }

   private static PermissionChangeRequest updateChange(String resourceType, String resourcePath,
                                                        String identityType, String identityId,
                                                        List<String> actions)
   {
      PermissionChangeRequest change = grantChange(resourceType, resourcePath, identityType,
         identityId, actions);
      change.setVerb(PermissionChangeRequest.VERB_UPDATE);
      return change;
   }

   private static PermissionChangeRequest deleteChange(String identityId) {
      PermissionChangeRequest change = new PermissionChangeRequest();
      change.setVerb(PermissionChangeRequest.VERB_DELETE);
      change.setResourceType("ASSET");
      change.setResourcePath("Examples/Census");
      change.setIdentityType("USER");
      change.setIdentityId(identityId);
      return change;
   }
}
