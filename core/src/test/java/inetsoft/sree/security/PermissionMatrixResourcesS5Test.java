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
 * Slice test class for permission-matrix-resources.md § S5 (hierarchical inheritance, Rule 4
 * WRITE-promotes-DELETE, and Rule 5 intermediate-permission-cap). See that doc for the full
 * scenario table and the mechanism write-ups this class's fixtures are based on.
 *
 * EVERY fixture here that relies on non-ADMIN inheritance (i.e. anything except the Rule 5
 * ADMIN-bypass case) calls SecurityTestDataBuilder.markPermissionEdited() on the ancestor folder
 * granting the permission. Without it, DefaultCheckPermissionStrategy's non-ADMIN inheritance
 * walk never stops climbing at that folder (Permission.hasOrgEditedGrantAll() is false, so
 * useParent never flips false) and instead keeps climbing past it to the root regardless of any
 * grant recorded there -- confirmed empirically while investigating Rule 5 (a fixture without
 * this call produced a false DENY for a resource that should have inherited READ from its
 * direct parent folder). ADMIN inheritance is unaffected -- it uses a separate, unconditional
 * whole-ancestor-chain merge (see DefaultCheckPermissionStrategy's private cumulative
 * getPermission() helper) that S2/S3 already rely on.
 *
 * Also covers the "everyone" fallback finding documented in permission-matrix-resources.md's
 * 附加 section: SecurityEngine.checkPermission() (L820-939), NOT DefaultCheckPermissionStrategy,
 * has a second fallback layer for DATA_SOURCE (incl. FOLDER/QUERY/CUBE), SCRIPT/SCRIPT_LIBRARY,
 * TABLE_STYLE/TABLE_STYLE_LIBRARY, and SCHEDULE_TASK_FOLDER: when a resource has never been
 * configured at all (Permission.isBlank()), the four security.*.everyone flags (all default
 * "true") grant READ to any authenticated user; setting a flag to "false" removes the fallback,
 * making an unconfigured resource default-deny like ASSET/REPORT. This lives here rather than in
 * S3Test (which already covers SCRIPT's baseline+cascade) because it needs a genuinely BLANK
 * resource all the way to the type's root -- S3Test's fixture has real grants on SCRIPT_LIBRARY's
 * root that would defeat that precondition. This class's fixture never touches DATA_SOURCE_FOLDER/
 * TABLE_STYLE_LIBRARY/SCHEDULE_TASK_FOLDER/SCRIPT_LIBRARY's own roots (only specific sub-paths
 * like "mx_datasource_folder"), so a fresh top-level resource name for each type is safely blank.
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

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionMatrixResourcesS5Test {

   private static final String ORG_NAME = "matrix_org";
   private static final String ORG_ID = "matrix_org_id";

   // Main table (ASSET): folder with READ granted, child inherits.
   private static final String ASSET_FOLDER = "mx/folder";
   private static final String ASSET_ITEM = "mx/folder/item";

   // S5-CROSS-GROUP (REPORT): same shape.
   private static final String REPORT_FOLDER = "mx_vs/folder";
   private static final String REPORT_ITEM = "mx_vs/folder/viewsheet1";

   // S5-RULE4 (ASSET): folder2 with only WRITE granted -- child should inherit WRITE, get
   // promoted to DELETE, but NOT ADMIN; a sibling child with its own (even empty) explicit
   // permission should not receive the promotion.
   private static final String ASSET_FOLDER2 = "mx/folder2";
   private static final String ASSET_FOLDER2_ITEM = "mx/folder2/item";
   private static final String ASSET_FOLDER2_ITEM_EXPLICIT = "mx/folder2/item-explicit";

   // S5-RULE4 cross-group (DATA_SOURCE/QUERY/CUBE): same WRITE-promotes-DELETE rule, proving it
   // isn't Repository/Worksheet-specific. QUERY's resource key is "{queryName}::{dataSourcePath}"
   // (datasource AFTER "::"); CUBE's is the opposite, "{dataSourcePath}::{cubeName}" (datasource
   // BEFORE "::") -- confirmed against ResourceType.CUBE/QUERY.getParent() and
   // AssetEventUtil.java's real resource-string construction, not assumed.
   private static final String DS_FOLDER = "mx_datasource_folder";
   private static final String DS_ITEM = "mx_datasource_folder/ds1";
   private static final String QUERY_ITEM = "Model::mx_datasource_folder/ds1";
   private static final String CUBE_ITEM = "mx_datasource_folder/ds1::cube1";

   // S5-RULE5-INTERMEDIATE-PERMISSION-CAP (ASSET): a 3-level chain where the INTERMEDIATE
   // folder's own explicit (edited) permission caps non-ADMIN inheritance from the grandparent.
   private static final String CAP_GRANDPARENT = "mxcap";
   private static final String CAP_FOLDER = "mxcap/capFolder";
   private static final String CAP_ITEM = "mxcap/capFolder/item";

   // Rule 4 promotion for GROUP/ROLE grantees (not just USER) -- the promotion code merges
   // WRITE grants into the DELETE set separately for all four grantee types
   // (user/group/role/organization); only USER had been exercised so far.
   private static final String ASSET_FOLDER3 = "mx/folder3";
   private static final String ASSET_FOLDER3_ITEM = "mx/folder3/item";
   private static final String ASSET_FOLDER4 = "mx/folder4";
   private static final String ASSET_FOLDER4_ITEM = "mx/folder4/item";

   // Boundary guard #1: TWO capped ancestors stacked in one chain -- the walk must stop at the
   // NEARER cap (CAP_STACK_MID), not skip past it to the farther one (CAP_STACK_TOP).
   private static final String CAP_STACK_TOP = "mxstack";
   private static final String CAP_STACK_MID = "mxstack/mid";
   private static final String CAP_STACK_ITEM = "mxstack/mid/item";

   // Boundary guard #3: S4 (role hierarchy) combined with S5 (folder inheritance) + Rule 4
   // promotion in one fixture -- comboUser holds comboChildRole, which inherits from
   // comboParentRole (the actual grantee on the folder).
   private static final String ASSET_COMBO_FOLDER = "mx/combo";
   private static final String ASSET_COMBO_ITEM = "mx/combo/item";

   // "everyone" fallback: none of these have ever had a Permission written for them, at any
   // ancestor level up to the type's root -- this fixture never touches DATA_SOURCE_FOLDER's,
   // TABLE_STYLE_LIBRARY's, SCHEDULE_TASK_FOLDER's, or SCRIPT_LIBRARY's own root, only specific
   // sub-paths, so these fresh top-level names stay genuinely blank.
   private static final String UNCONFIGURED_DATA_SOURCE = "unconfigured_ds";
   private static final String UNCONFIGURED_SCRIPT = "unconfigured_script";
   private static final String UNCONFIGURED_TABLE_STYLE = "unconfigured_style";
   private static final String UNCONFIGURED_SCHEDULE_TASK_FOLDER = "unconfigured_schedule_folder";

   private static SecurityTestDataBuilder builder;

   private static SRPrincipal orgViewerInherited;
   private static SRPrincipal noGrantUser;
   private static SRPrincipal partialGrantUser;
   private static SRPrincipal partialGrantDsUser;
   private static SRPrincipal grandWriteUser;
   private static SRPrincipal grandAdminUser;
   private static SRPrincipal folderReadUser;
   private static SRPrincipal groupPromoUser;
   private static SRPrincipal rolePromoUser;
   private static SRPrincipal topWriteUser;
   private static SRPrincipal comboUser;
   private static SRPrincipal plainUser;

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)

         .addUser("orgViewerInherited", ORG_ID, "password")
         .addUser("noGrantUser", ORG_ID, "password")
         .addUser("partialGrantUser", ORG_ID, "password")
         .addUser("partialGrantDsUser", ORG_ID, "password")
         .addUser("grandWriteUser", ORG_ID, "password")
         .addUser("grandAdminUser", ORG_ID, "password")
         .addUser("folderReadUser", ORG_ID, "password")
         .addUser("groupPromoUser", ORG_ID, "password")
         .addUser("rolePromoUser", ORG_ID, "password")
         .addUser("topWriteUser", ORG_ID, "password")
         .addUser("comboUser", ORG_ID, "password")
         // plainUser holds no roles/groups/permissions anywhere -- used only for the "everyone"
         // fallback tests, which need a principal with zero grants checking a zero-grant resource.
         .addUser("plainUser", ORG_ID, "password")

         // ── main table + S5-CROSS-GROUP: basic downward inheritance ──
         .grantPermission(ResourceType.ASSET, ASSET_FOLDER, ResourceAction.READ,
                          "orgViewerInherited", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, ASSET_FOLDER, ORG_ID)
         .grantPermission(ResourceType.REPORT, REPORT_FOLDER, ResourceAction.READ,
                          "orgViewerInherited", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.REPORT, REPORT_FOLDER, ORG_ID)

         // ── S5-RULE4: WRITE on folder2 promotes to DELETE (not ADMIN) for its children ──
         .grantPermission(ResourceType.ASSET, ASSET_FOLDER2, ResourceAction.WRITE,
                          "partialGrantUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, ASSET_FOLDER2, ORG_ID)
         // item-explicit has its own (empty) saved permission -- negative control, promotion
         // must not apply since the inheritance branch is never entered for it.
         .markPermissionEdited(ResourceType.ASSET, ASSET_FOLDER2_ITEM_EXPLICIT, ORG_ID)

         // ── S5-RULE4 cross-group: same rule on DATA_SOURCE/QUERY/CUBE ──
         .grantPermission(ResourceType.DATA_SOURCE_FOLDER, DS_FOLDER, ResourceAction.WRITE,
                          "partialGrantDsUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.DATA_SOURCE_FOLDER, DS_FOLDER, ORG_ID)

         // ── S5-RULE5: capFolder's own explicit READ-only permission caps non-ADMIN
         // inheritance from the grandparent (mxcap), but not ADMIN inheritance ──
         .grantPermission(ResourceType.ASSET, CAP_GRANDPARENT, ResourceAction.WRITE,
                          "grandWriteUser", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.ASSET, CAP_GRANDPARENT, ResourceAction.ADMIN,
                          "grandAdminUser", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.ASSET, CAP_FOLDER, ResourceAction.READ,
                          "folderReadUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, CAP_FOLDER, ORG_ID)

         // ── Rule 4 promotion for GROUP/ROLE grantees ──
         .addGroup("promoGroup", ORG_ID)
         .addUserToGroup("groupPromoUser", "promoGroup", ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_FOLDER3, ResourceAction.WRITE,
                          "promoGroup", Identity.GROUP, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, ASSET_FOLDER3, ORG_ID)

         .addRole("promoRole", ORG_ID)
         .addUserToRole("rolePromoUser", "promoRole", ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_FOLDER4, ResourceAction.WRITE,
                          "promoRole", Identity.ROLE, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, ASSET_FOLDER4, ORG_ID)

         // ── Boundary guard #1: stacked caps, nearest one wins ──
         // CAP_STACK_TOP grants WRITE to topWriteUser; CAP_STACK_MID (nearer to the checked
         // item) has its own saved permission granting an unrelated identity -- topWriteUser's
         // grant at the TOP must never be reached.
         .grantPermission(ResourceType.ASSET, CAP_STACK_TOP, ResourceAction.WRITE,
                          "topWriteUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, CAP_STACK_TOP, ORG_ID)
         .grantPermission(ResourceType.ASSET, CAP_STACK_MID, ResourceAction.READ,
                          "unrelatedMidUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, CAP_STACK_MID, ORG_ID)

         // ── Boundary guard #3: S4 role hierarchy + S5 folder inheritance + Rule 4 promotion ──
         // comboUser holds comboChildRole, NOT comboParentRole directly; comboParentRole is the
         // actual WRITE grantee on the folder.
         .addRole("comboParentRole", ORG_ID)
         .addRole("comboChildRole", ORG_ID)
         .addRoleParent("comboChildRole", "comboParentRole", ORG_ID)
         .addUserToRole("comboUser", "comboChildRole", ORG_ID)
         .grantPermission(ResourceType.ASSET, ASSET_COMBO_FOLDER, ResourceAction.WRITE,
                          "comboParentRole", Identity.ROLE, ORG_ID)
         .markPermissionEdited(ResourceType.ASSET, ASSET_COMBO_FOLDER, ORG_ID)

         .setup();

      orgViewerInherited = builder.principalOf("orgViewerInherited", ORG_ID);
      noGrantUser = builder.principalOf("noGrantUser", ORG_ID);
      partialGrantUser = builder.principalOf("partialGrantUser", ORG_ID);
      partialGrantDsUser = builder.principalOf("partialGrantDsUser", ORG_ID);
      grandWriteUser = builder.principalOf("grandWriteUser", ORG_ID);
      grandAdminUser = builder.principalOf("grandAdminUser", ORG_ID);
      folderReadUser = builder.principalOf("folderReadUser", ORG_ID);
      groupPromoUser = builder.principalOf("groupPromoUser", ORG_ID);
      rolePromoUser = builder.principalOf("rolePromoUser", ORG_ID);
      topWriteUser = builder.principalOf("topWriteUser", ORG_ID);
      comboUser = builder.principalOf("comboUser", ORG_ID);
      plainUser = builder.principalOf("plainUser", ORG_ID);
   }

   @AfterAll
   static void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ════════════════════════════════════════════════════════════════════════════
   // Main table (ASSET): basic downward inheritance
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void orgViewerInherited_readOnItem_allowed_parentGrantPropagates() {
      withContextPrincipal(orgViewerInherited, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(orgViewerInherited, ResourceAction.READ)
            .verify());
   }

   @Test
   void noGrantUser_readOnItem_denied_noParentGrant() {
      withContextPrincipal(noGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectDeny(noGrantUser, ResourceAction.READ)
            .verify());
   }

   @Test
   void noGrantUser_readOnFolder_denied_folderItselfUngranted() {
      // ASSET_FOLDER's own permission grants orgViewerInherited only -- noGrantUser isn't in it.
      withContextPrincipal(noGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER)
               .expectDeny(noGrantUser, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S5-CROSS-GROUP (REPORT) -- same inheritance, proving it isn't ASSET-specific
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void orgViewerInherited_readOnReportItem_allowed_crossGroup() {
      withContextPrincipal(orgViewerInherited, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.REPORT, REPORT_ITEM)
               .expectAllow(orgViewerInherited, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S5-RULE4-WRITE-PROMOTES-DELETE
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void partialGrantUser_writeOnItem_allowed_directInherit() {
      withContextPrincipal(partialGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER2_ITEM)
               .expectAllow(partialGrantUser, ResourceAction.WRITE)
            .verify());
   }

   @Test
   void partialGrantUser_deleteOnItem_allowed_writePromotesDelete() {
      withContextPrincipal(partialGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER2_ITEM)
               .expectAllow(partialGrantUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void partialGrantUser_adminOnItem_denied_promotionExcludesAdmin() {
      // The promotion logic (DefaultCheckPermissionStrategy L392-450) only ever adds WRITE/DELETE
      // grantees to the DELETE set -- it never manufactures ADMIN.
      withContextPrincipal(partialGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER2_ITEM)
               .expectDeny(partialGrantUser, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void partialGrantUser_deleteOnExplicitChild_denied_ownExplicitPermissionBlocksPromotion() {
      // ASSET_FOLDER2_ITEM_EXPLICIT has its own saved (if empty) permission -- inheritedPermission
      // resolves to false for it, so the parent's WRITE/DELETE promotion never applies.
      withContextPrincipal(partialGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER2_ITEM_EXPLICIT)
               .expectDeny(partialGrantUser, ResourceAction.DELETE)
            .verify());
   }

   // ── S5-RULE4 cross-group (DATA_SOURCE/QUERY/CUBE) ──────────────────────────────

   @Test
   void partialGrantDsUser_deleteOnDataSource_allowed_ruleAppliesToDataSource() {
      withContextPrincipal(partialGrantDsUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.DATA_SOURCE, DS_ITEM)
               .expectAllow(partialGrantDsUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void partialGrantDsUser_deleteOnQuery_allowed_ruleAppliesToQuery() {
      withContextPrincipal(partialGrantDsUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.QUERY, QUERY_ITEM)
               .expectAllow(partialGrantDsUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void partialGrantDsUser_deleteOnCube_allowed_ruleAppliesToCube() {
      withContextPrincipal(partialGrantDsUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.CUBE, CUBE_ITEM)
               .expectAllow(partialGrantDsUser, ResourceAction.DELETE)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S5-RULE5-INTERMEDIATE-PERMISSION-CAP
   // ════════════════════════════════════════════════════════════════════════════
   //
   // capFolder sits between CAP_GRANDPARENT (mxcap) and CAP_ITEM. capFolder has its OWN saved
   // permission (READ only, granted to folderReadUser). This caps non-ADMIN inheritance for
   // anything under capFolder to what capFolder itself grants (plus the Rule 4 promotion applied
   // to capFolder's own grants) -- the grandparent's WRITE/ADMIN grants are a separate concern.

   @Test
   void grandWriteUser_deleteOnItemUnderCappedFolder_denied_nonAdminCapped() {
      // grandWriteUser's WRITE (and its Rule 4 DELETE promotion) on the grandparent never
      // reaches CAP_ITEM: the non-ADMIN inheritance walk stops at capFolder (it has its own
      // saved permission) and never continues up to CAP_GRANDPARENT.
      withContextPrincipal(grandWriteUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, CAP_ITEM)
               .expectDeny(grandWriteUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void folderReadUser_readOnItemUnderOwnFolder_allowed_basicInheritanceNotCapped() {
      // Basic inheritance from a resource's OWN nearest configured ancestor still works --
      // capFolder's own READ grant reaches its direct child normally.
      withContextPrincipal(folderReadUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, CAP_ITEM)
               .expectAllow(folderReadUser, ResourceAction.READ)
            .verify());
   }

   @Test
   void folderReadUser_deleteOnItemUnderOwnFolder_denied_noPromotionSource() {
      // capFolder only grants READ (no WRITE/DELETE) to folderReadUser, so there's nothing for
      // Rule 4 to promote.
      withContextPrincipal(folderReadUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, CAP_ITEM)
               .expectDeny(folderReadUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void grandAdminUser_deleteOnItemUnderCappedFolder_allowed_adminBypassesCap() {
      // ADMIN inheritance is a completely separate mechanism (unconditional whole-chain merge,
      // see class javadoc) -- it isn't stopped by capFolder's own saved permission the way
      // non-ADMIN inheritance is, and ADMIN implies DELETE via SecurityEngine's generic retry.
      withContextPrincipal(grandAdminUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, CAP_ITEM)
               .expectAllow(grandAdminUser, ResourceAction.DELETE)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // Rule 4 promotion for GROUP/ROLE grantees
   // ════════════════════════════════════════════════════════════════════════════
   //
   // The promotion logic clones the resolved Permission and merges WRITE grants into the DELETE
   // set separately for USER/GROUP/ROLE/ORGANIZATION grantees -- symmetric in the source, but
   // only ever exercised with a USER grantee above. These two tests confirm GROUP and ROLE
   // grantees get the same promotion.

   @Test
   void groupPromoUser_deleteOnItem_allowed_writePromotionAppliesToGroupGrantee() {
      withContextPrincipal(groupPromoUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER3_ITEM)
               .expectAllow(groupPromoUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void rolePromoUser_deleteOnItem_allowed_writePromotionAppliesToRoleGrantee() {
      withContextPrincipal(rolePromoUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER4_ITEM)
               .expectAllow(rolePromoUser, ResourceAction.DELETE)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // Boundary guards: stacked caps, and S4+S5 combined
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void topWriteUser_deleteOnItemUnderStackedCaps_denied_nearestCapWins() {
      // Two capped ancestors in one chain (CAP_STACK_TOP, then CAP_STACK_MID closer to the
      // item). The walk must stop at the NEARER cap (CAP_STACK_MID) and never reach
      // CAP_STACK_TOP's WRITE grant, even though topWriteUser would qualify for DELETE if the
      // walk somehow skipped past CAP_STACK_MID.
      withContextPrincipal(topWriteUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, CAP_STACK_ITEM)
               .expectDeny(topWriteUser, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void comboUser_deleteOnItem_allowed_roleHierarchyPlusFolderInheritancePlusPromotion() {
      // Combines three mechanisms in one assertion: S5 folder inheritance (ASSET_COMBO_ITEM has
      // no permission of its own, climbs to ASSET_COMBO_FOLDER), Rule 4 promotion (the folder's
      // WRITE grant promotes to DELETE), and S4 role hierarchy (comboUser holds comboChildRole,
      // not the actual grantee comboParentRole, and only qualifies via the parent-role chain).
      withContextPrincipal(comboUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_COMBO_ITEM)
               .expectAllow(comboUser, ResourceAction.DELETE)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // "everyone" fallback (security.datasource/script/tablestyle/scheduletask.everyone)
   // ════════════════════════════════════════════════════════════════════════════
   //
   // Each test checks the SAME unconfigured resource twice: once under the default (true) flag
   // value, once forced to "false". plainUser holds no grant anywhere, so any ALLOW here comes
   // entirely from the fallback, not from a real Permission match.

   @Test
   void dataSourceEveryone_togglesDefaultReadOnUnconfiguredDataSource() {
      withContextPrincipal(plainUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.DATA_SOURCE, UNCONFIGURED_DATA_SOURCE)
               .expectAllow(plainUser, ResourceAction.READ)
               .expectDeny(plainUser, ResourceAction.WRITE)
            .verify());

      withEveryoneFlag("security.datasource.everyone",
         SecurityEngine::updateSecurityDatasourceEveryoneValue, () ->
         withContextPrincipal(plainUser, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.DATA_SOURCE, UNCONFIGURED_DATA_SOURCE)
                  .expectDeny(plainUser, ResourceAction.READ)
               .verify()));
   }

   @Test
   void scriptEveryone_togglesDefaultReadOnUnconfiguredScript() {
      withContextPrincipal(plainUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCRIPT, UNCONFIGURED_SCRIPT)
               .expectAllow(plainUser, ResourceAction.READ)
               .expectDeny(plainUser, ResourceAction.WRITE)
            .verify());

      withEveryoneFlag("security.script.everyone",
         SecurityEngine::updateSecurityScriptEveryoneValue, () ->
         withContextPrincipal(plainUser, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.SCRIPT, UNCONFIGURED_SCRIPT)
                  .expectDeny(plainUser, ResourceAction.READ)
               .verify()));
   }

   @Test
   void tableStyleEveryone_togglesDefaultReadOnUnconfiguredTableStyle() {
      withContextPrincipal(plainUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.TABLE_STYLE, UNCONFIGURED_TABLE_STYLE)
               .expectAllow(plainUser, ResourceAction.READ)
               .expectDeny(plainUser, ResourceAction.WRITE)
            .verify());

      withEveryoneFlag("security.tablestyle.everyone",
         SecurityEngine::updateSecurityTablestyleEveryoneValue, () ->
         withContextPrincipal(plainUser, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.TABLE_STYLE, UNCONFIGURED_TABLE_STYLE)
                  .expectDeny(plainUser, ResourceAction.READ)
               .verify()));
   }

   @Test
   void scheduleTaskFolderEveryone_togglesDefaultReadOnUnconfiguredFolder() {
      withContextPrincipal(plainUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCHEDULE_TASK_FOLDER, UNCONFIGURED_SCHEDULE_TASK_FOLDER)
               .expectAllow(plainUser, ResourceAction.READ)
               .expectDeny(plainUser, ResourceAction.WRITE)
            .verify());

      withEveryoneFlag("security.scheduletask.everyone",
         SecurityEngine::updateSecuritySchduletaskEveryoneValue, () ->
         withContextPrincipal(plainUser, () ->
            PermissionMatrixVerifier.of(engine())
               .resource(ResourceType.SCHEDULE_TASK_FOLDER, UNCONFIGURED_SCHEDULE_TASK_FOLDER)
                  .expectDeny(plainUser, ResourceAction.READ)
               .verify()));
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
    * Sets {@code propertyName} to {@code "false"} for the duration of {@code action}, forcing an
    * immediate cache refresh via {@code refresh} both when disabling and when restoring back to
    * unset (the "true" default) -- these {@code SecurityEngine.updateSecurityXXXEveryoneValue()}
    * methods are public, unlike S4Test's reflection-based toggle for permission.andCondition.
    */
   private static void withEveryoneFlag(String propertyName, Runnable refresh, Runnable action) {
      SreeEnv.setProperty(propertyName, "false");
      refresh.run();

      try {
         action.run();
      }
      finally {
         SreeEnv.remove(propertyName);
         refresh.run();
      }
   }

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
