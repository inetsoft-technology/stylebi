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
package inetsoft.sree.security;

/*
 * Slice test class for permission-matrix-resources.md § S4 (three content-access paths --
 * User->Role, User->Group, User->Group->Role -- plus Role/Group hierarchy inheritance and the
 * AND/OR grant-combination switch). See that doc for the full scenario table.
 *
 * All three paths and the hierarchy walks are generic identity-resolution machinery in
 * PermissionChecker (checkRolePermission()/checkUserGroupPermission()), independent of resource
 * type, so the main table uses ASSET as the representative type and S4-CROSS-GROUP repeats one
 * path (via-role) on REPORT to confirm it isn't ASSET-specific.
 *
 * The grant in every main-table/hierarchy scenario is placed directly ON the checked resource
 * (not an ancestor folder), so none of these need SecurityTestDataBuilder.markPermissionEdited()
 * -- that only matters for folder-inheritance scenarios (see S5-RULE5-INTERMEDIATE-PERMISSION-CAP
 * in the doc), not identity-resolution ones.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.support.*;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionMatrixResourcesS4Test {

   private static final String ORG_NAME = "matrix_org";
   private static final String ORG_ID = "matrix_org_id";

   private static final String ASSET_ITEM = "mx/folder/item";
   private static final String ASSET_AND_ITEM = "mx/folder/anditem";
   private static final String REPORT_ITEM = "mx_vs/folder/viewsheet1";

   private static SecurityTestDataBuilder builder;

   private static SRPrincipal orgViewerViaRole;
   private static SRPrincipal orgViewerViaGroup;
   private static SRPrincipal orgViewerViaGroupRole;
   private static SRPrincipal roleHierarchyUser;
   private static SRPrincipal roleHierarchyNoGrantUser;
   private static SRPrincipal groupHierarchyUser;
   private static SRPrincipal andRoleOnlyUser;
   private static SRPrincipal andBothUser;

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)

         // ── main table: three access paths, all granted READ only on ASSET_ITEM ──
         .addRole("viewerRole", ORG_ID)
         .addGroup("viewerGroup", ORG_ID)
         .addRole("groupRole", ORG_ID)
         .addGroup("roleHolderGroup", ORG_ID)

         .addUser("orgViewerViaRole", ORG_ID, "password")
         .addUser("orgViewerViaGroup", ORG_ID, "password")
         .addUser("orgViewerViaGroupRole", ORG_ID, "password")

         .addUserToRole("orgViewerViaRole", "viewerRole", ORG_ID)
         .addUserToGroup("orgViewerViaGroup", "viewerGroup", ORG_ID)
         // User -> Group -> Role: orgViewerViaGroupRole holds no role of its own, but is a
         // member of roleHolderGroup, which itself holds groupRole.
         .addRoleToGroup("groupRole", "roleHolderGroup", ORG_ID)
         .addUserToGroup("orgViewerViaGroupRole", "roleHolderGroup", ORG_ID)

         // orgViewer-via-role is granted WRITE (not READ) here, deliberately different from the
         // other two paths below -- the three-path resolution machinery
         // (checkRolePermission()/checkUserGroupPermission()) is action-agnostic, it just looks
         // up whichever action key is being checked within the same Permission object, so this
         // proves the mechanism isn't implicitly READ-only.
         .grantPermission(ResourceType.ASSET, ASSET_ITEM, ResourceAction.WRITE,
                          "viewerRole", Identity.ROLE, ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_ITEM, ResourceAction.READ,
                          "viewerGroup", Identity.GROUP, ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_ITEM, ResourceAction.READ,
                          "groupRole", Identity.ROLE, ORG_ID)

         // cross-group: same via-role grant repeated on REPORT
         .grantPermission(ResourceType.REPORT, REPORT_ITEM, ResourceAction.READ,
                          "viewerRole", Identity.ROLE, ORG_ID)

         // ── S4-ROLE-HIERARCHY: role1 -> role2 (role2 granted); role3 -> role4 (role4 NOT granted) ──
         .addRole("role1", ORG_ID)
         .addRole("role2", ORG_ID)
         .addRole("role3", ORG_ID)
         .addRole("role4", ORG_ID)
         .addRoleParent("role1", "role2", ORG_ID)
         .addRoleParent("role3", "role4", ORG_ID)
         .addUser("roleHierarchyUser", ORG_ID, "password")
         .addUser("roleHierarchyNoGrantUser", ORG_ID, "password")
         .addUserToRole("roleHierarchyUser", "role1", ORG_ID)
         .addUserToRole("roleHierarchyNoGrantUser", "role3", ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_ITEM, ResourceAction.READ,
                          "role2", Identity.ROLE, ORG_ID)
         // role4 deliberately has no grant -- negative control.

         // ── S4-GROUP-HIERARCHY: group1 -> group2 (group2 granted) ──
         .addGroup("group1", ORG_ID)
         .addGroup("group2", ORG_ID)
         .addGroupParent("group1", "group2", ORG_ID)
         .addUser("groupHierarchyUser", ORG_ID, "password")
         .addUserToGroup("groupHierarchyUser", "group1", ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_ITEM, ResourceAction.READ,
                          "group2", Identity.GROUP, ORG_ID)

         // ── S4-AND: a dedicated resource with both a ROLE grant (viewerRole) and a GROUP
         // grant (andGroup), exercised under permission.andCondition=true. andRoleOnlyUser only
         // matches the role arm; andBothUser matches both.
         .addGroup("andGroup", ORG_ID)
         .addUser("andRoleOnlyUser", ORG_ID, "password")
         .addUser("andBothUser", ORG_ID, "password")
         .addUserToRole("andRoleOnlyUser", "viewerRole", ORG_ID)
         .addUserToRole("andBothUser", "viewerRole", ORG_ID)
         .addUserToGroup("andBothUser", "andGroup", ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_AND_ITEM, ResourceAction.READ,
                          "viewerRole", Identity.ROLE, ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_AND_ITEM, ResourceAction.READ,
                          "andGroup", Identity.GROUP, ORG_ID)

         .setup();

      orgViewerViaRole = builder.principalOf("orgViewerViaRole", ORG_ID);
      orgViewerViaGroup = builder.principalOf("orgViewerViaGroup", ORG_ID);
      orgViewerViaGroupRole = builder.principalOf("orgViewerViaGroupRole", ORG_ID);
      roleHierarchyUser = builder.principalOf("roleHierarchyUser", ORG_ID);
      roleHierarchyNoGrantUser = builder.principalOf("roleHierarchyNoGrantUser", ORG_ID);
      groupHierarchyUser = builder.principalOf("groupHierarchyUser", ORG_ID);
      andRoleOnlyUser = builder.principalOf("andRoleOnlyUser", ORG_ID);
      andBothUser = builder.principalOf("andBothUser", ORG_ID);
   }

   @AfterAll
   static void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ════════════════════════════════════════════════════════════════════════════
   // Main table (ASSET): three access paths
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void orgViewerViaRole_writeOnItem_allowed_userRoleResource_actionAgnostic() {
      // Granted WRITE here (not READ, see setUp()'s comment) -- confirms the User->Role
      // resolution path isn't implicitly READ-only.
      withContextPrincipal(orgViewerViaRole, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(orgViewerViaRole, ResourceAction.WRITE)
            .verify());
   }

   @Test
   void orgViewerViaRole_readAndAdminOnItem_denied_onlyWriteGranted() {
      withContextPrincipal(orgViewerViaRole, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectDeny(orgViewerViaRole, ResourceAction.READ, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void orgViewerViaGroup_readOnItem_allowed_userGroupResource() {
      // Group is directly granted READ; orgViewerViaGroup is a direct member.
      withContextPrincipal(orgViewerViaGroup, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(orgViewerViaGroup, ResourceAction.READ)
            .verify());
   }

   @Test
   void orgViewerViaGroup_writeOnItem_denied_onlyReadGranted() {
      withContextPrincipal(orgViewerViaGroup, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectDeny(orgViewerViaGroup, ResourceAction.WRITE)
            .verify());
   }

   @Test
   void orgViewerViaGroupRole_readOnItem_allowed_userGroupRoleResource() {
      // orgViewerViaGroupRole holds no role directly -- it's a member of roleHolderGroup, which
      // itself holds groupRole (the actual grantee). PermissionChecker.checkRolePermission()'s
      // USER branch falls back to the user's groups when direct roles don't match, then walks
      // each group's own roles.
      withContextPrincipal(orgViewerViaGroupRole, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(orgViewerViaGroupRole, ResourceAction.READ)
            .verify());
   }

   @Test
   void orgViewerViaGroupRole_writeOnItem_denied_onlyReadGranted() {
      withContextPrincipal(orgViewerViaGroupRole, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectDeny(orgViewerViaGroupRole, ResourceAction.WRITE)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S4-ROLE-HIERARCHY / S4-GROUP-HIERARCHY
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void roleHierarchyUser_readOnItem_allowed_parentRoleGrantPropagates() {
      // roleHierarchyUser holds role1; role1's parent is role2 (the actual grantee).
      // PermissionChecker.checkRolePermission() recurses through a role identity's own
      // getRoles() (its parent-role chain), not just a user's directly-held roles.
      withContextPrincipal(roleHierarchyUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(roleHierarchyUser, ResourceAction.READ)
            .verify());
   }

   @Test
   void roleHierarchyNoGrantUser_readOnItem_denied_notDefaultAllow() {
      // Negative control: role3 -> role4, but role4 has no grant either -- proves the hierarchy
      // walk doesn't default-allow just because a parent-role chain exists.
      withContextPrincipal(roleHierarchyNoGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectDeny(roleHierarchyNoGrantUser, ResourceAction.READ)
            .verify());
   }

   @Test
   void groupHierarchyUser_readOnItem_allowed_parentGroupGrantPropagates() {
      // groupHierarchyUser is a member of group1; group1's parent is group2 (the actual
      // grantee).
      withContextPrincipal(groupHierarchyUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(groupHierarchyUser, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S4-AND (permission.andCondition=true)
   // ════════════════════════════════════════════════════════════════════════════
   //
   // ASSET_AND_ITEM has both a ROLE grant (viewerRole) and a GROUP grant (andGroup). Default
   // (OR) mode only requires matching one; AND mode requires matching both simultaneously.

   @Test
   void andRoleOnlyUser_readOnAndItem_denied_andModeRequiresBoth() {
      // andRoleOnlyUser holds viewerRole but is not a member of andGroup -- satisfies only the
      // role arm.
      withAndCondition(true, () ->
         withContextPrincipal(andRoleOnlyUser, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.ASSET, ASSET_AND_ITEM)
                  .expectDeny(andRoleOnlyUser, ResourceAction.READ)
               .verify()));
   }

   @Test
   void andBothUser_readOnAndItem_allowed_andModeBothSatisfied() {
      // andBothUser holds viewerRole AND is a member of andGroup -- satisfies both arms.
      withAndCondition(true, () ->
         withContextPrincipal(andBothUser, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.ASSET, ASSET_AND_ITEM)
                  .expectAllow(andBothUser, ResourceAction.READ)
               .verify()));
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S4-CROSS-GROUP (REPORT) -- via-role path repeated, proving it isn't ASSET-specific
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void orgViewerViaRole_readOnReportItem_allowed_crossGroup() {
      withContextPrincipal(orgViewerViaRole, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.REPORT, REPORT_ITEM)
               .expectAllow(orgViewerViaRole, ResourceAction.READ)
            .verify());
   }

   // ── helpers ────────────────────────────────────────────────────────────────

   /**
    * Runs {@code action} with {@code principal} set as the thread's context principal, then
    * always restores {@code null}. Needed for assertions that depend on
    * {@link OrganizationManager}'s ThreadContext-backed "current org" resolution.
    */
   private static void withContextPrincipal(SRPrincipal principal, Runnable action) {
      ThreadContext.setContextPrincipal(principal);

      try {
         action.run();
      }
      finally {
         ThreadContext.setContextPrincipal(null);
      }
   }

   /**
    * Toggles {@code permission.andCondition} for the duration of {@code action}, then always
    * restores it. {@link PermissionChecker}'s {@code andCond} field is a 10-second-cached
    * {@link SreeEnv.Value}; reflection is used to force an immediate refresh both when enabling
    * and when restoring, since there is no public equivalent of {@code SecurityEngine}'s
    * {@code updateSecurityXXXEveryoneValue()} helpers for this particular flag.
    */
   private static void withAndCondition(boolean enabled, Runnable action) {
      SreeEnv.setProperty("permission.andCondition", String.valueOf(enabled));
      refreshAndConditionCache();

      try {
         action.run();
      }
      finally {
         SreeEnv.remove("permission.andCondition");
         refreshAndConditionCache();
      }
   }

   private static void refreshAndConditionCache() {
      SreeEnv.Value andCond = (SreeEnv.Value) ReflectionTestUtils.getField(PermissionChecker.class, "andCond");
      andCond.updateValue();
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
