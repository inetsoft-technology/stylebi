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
 * Slice test class for permission-matrix-resources.md § S3 (ADMIN implicit R/W/D semantics +
 * Rule 1-3 parent/child ADMIN bidirectional cascade). See that doc for the full scenario table.
 *
 * Rule 1-3 come from DefaultCheckPermissionStrategy's generic hierarchical-resource walk
 * (ResourceType.isHierarchical()), not from any ASSET-specific code, so the main table uses
 * ASSET as the representative type and S3-CROSS-GROUP repeats the same four assertions on
 * REPORT (a second, independently-implemented "/"-separated hierarchy) to confirm the rule
 * isn't ASSET-specific.
 *
 * Fixtures use plain permission-storage-key strings (e.g. "mx/folder/item") with no backing
 * worksheet/viewsheet entity -- same convention as S2's SECURITY_GROUP instance checks:
 * DefaultCheckPermissionStrategy resolves hierarchy purely from the resource string via
 * ResourceType.getParent(), it never looks up an actual asset repository entry.
 *
 * Also covers permission-matrix-resources.md § 附加 (baseline sampling for resource groups never
 * assigned to a slice in the M7 plan: Portal Dashboard, Schedule, Library/Script) -- merged into
 * this class rather than a separate file/slice, since it's the same ADMIN-implies-R/W/D
 * mechanism this class already tests, just on resource types with no (DASHBOARD/SCHEDULE_TASK)
 * or trivial (SCRIPT) hierarchy:
 *   - DASHBOARD: no Rule 1-3 possible, getParent() always returns null.
 *   - SCHEDULE_TASK (not SCHEDULE_TASK_FOLDER): hierarchical=false, no getParent() override --
 *     a "/"-containing resource string is an opaque key here, not a folder path. Also outside
 *     the security.scheduletask.everyone fallback's scope (that only gates
 *     SCHEDULE_TASK_FOLDER checks), so no interaction to worry about.
 *   - SCRIPT: individual scripts are flat, but SCRIPT.getParent() always resolves to the shared
 *     SCRIPT_LIBRARY root ("*") -- a real, if single-level, parent-child relationship, so this
 *     class also samples SCRIPT_LIBRARY's root cascade (Rule 1-style) and WRITE-promotes-DELETE
 *     (Rule 4-style) onto script instances, unlike DASHBOARD/SCHEDULE_TASK which have nothing to
 *     cascade from. The direct (non-blank) grants used here mean
 *     SecurityEngine.checkPermission()'s security.script.everyone fallback never fires (it only
 *     applies when the direct check already failed), so it doesn't interfere.
 *   - TABLE_STYLE (its own "~"-delimited, genuinely multi-level hierarchy) and
 *     SCHEDULE_TASK_FOLDER (its own "/"-delimited folder hierarchy, independent of the flat
 *     SCHEDULE_TASK type) each get a minimal Rule 1-3 boundary check below (§ TABLE_STYLE /
 *     SCHEDULE_TASK_FOLDER own hierarchy) -- both have their own ResourceType.getParent()
 *     override (distinct from ASSET/REPORT's), so it's worth confirming the override itself
 *     climbs correctly, even though the underlying DefaultCheckPermissionStrategy walk is the
 *     same generic mechanism already proven twice above. Deliberately minimal: just Rule 1
 *     (downward) + Rule 2/3 (no upward/cross-level), not a full re-run of every S3/S4/S5 scenario.
 *
 * Two items already covered generically are deliberately NOT special-cased with their own
 * fixtures, since doing so would just re-exercise a code path already proven above:
 *   - DATA_SOURCE's basic ADMIN-implies-R/W/D + Rule 1-3 semantics: DATA_SOURCE_FOLDER's
 *     getParent() uses the same plain "/"-delimited generic walk as ASSET/REPORT (no type-specific
 *     override worth probing, unlike TABLE_STYLE/SCHEDULE_TASK_FOLDER above), and Rule 1-3 are
 *     already cross-group-proven via REPORT in S3-CROSS-GROUP; DATA_SOURCE's only genuinely
 *     type-specific behavior (Rule 4 promotion across DATA_SOURCE/QUERY/CUBE, and the
 *     security.datasource.everyone fallback) is already covered in PermissionMatrixResourcesS5Test.
 *   - DASHBOARD via S4's three access paths (user->role/group/group-role): S4 exists to prove the
 *     role/group resolution mechanism is resource-type-agnostic, which is already established via
 *     ASSET (main table) + REPORT (S4-CROSS-GROUP) -- one of which is hierarchical, proving the
 *     mechanism doesn't depend on hierarchy either. DASHBOARD is flat, so running the same three
 *     paths against it would exercise the identical resolution code a third time with no new
 *     dimension covered; skipped as redundant, not as a gap.
 */

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
class PermissionMatrixResourcesS3Test {

   private static final String ORG_NAME = "matrix_org";
   private static final String ORG_ID = "matrix_org_id";

   // Main table: ASSET 3-level chain (mx/folder -> item -> deep/sub).
   private static final String ASSET_FOLDER = "mx/folder";
   private static final String ASSET_ITEM = "mx/folder/item";
   private static final String ASSET_DEEP_CHILD = "mx/folder/item/deep";
   private static final String ASSET_SUB = "mx/folder/item/sub";

   // S3-CROSS-GROUP: same shape on REPORT (Repository tree), a second, independently
   // implemented "/"-separated hierarchy.
   private static final String REPORT_FOLDER = "mx_vs/folder";
   private static final String REPORT_ITEM = "mx_vs/folder/viewsheet1";
   private static final String REPORT_DEEP_CHILD = "mx_vs/folder/viewsheet1/deep";
   private static final String REPORT_SUB = "mx_vs/folder/viewsheet1/sub";

   // 附加 baseline sampling: Portal Dashboard / Schedule / Library-Script.
   private static final String DASHBOARD_ITEM = "dashboard1";
   private static final String SCHEDULE_TASK_ITEM = "mx_schedule_folder/task1";
   private static final String SCRIPT_ITEM = "mx_script_lib/script1";
   // Never individually granted -- targets for the SCRIPT_LIBRARY root-cascade/promotion checks.
   private static final String SCRIPT_ITEM_UNGRANTED_FOR_CASCADE = "mx_script_lib/script2";
   private static final String SCRIPT_ITEM_UNGRANTED_FOR_PROMOTION = "mx_script_lib/script3";
   private static final String SCRIPT_LIBRARY_ROOT = "*";

   // TABLE_STYLE own hierarchy: "~"-delimited, same 3-level shape as the ASSET main table
   // (folder -> item -> item's two children) but exercising TABLE_STYLE.getParent()'s own
   // override instead of the generic "/" walk.
   private static final String TABLE_STYLE_FOLDER = "mxstyle";
   private static final String TABLE_STYLE_ITEM = "mxstyle~item";
   private static final String TABLE_STYLE_DEEP_CHILD = "mxstyle~item~deep";
   private static final String TABLE_STYLE_SUB = "mxstyle~item~sub";

   // SCHEDULE_TASK_FOLDER own hierarchy: "/"-delimited, independent of the flat SCHEDULE_TASK
   // type sampled above -- same 3-level shape, exercising SCHEDULE_TASK_FOLDER.getParent()'s own
   // override.
   private static final String SCHEDULE_FOLDER_TOP = "mxschedtree";
   private static final String SCHEDULE_FOLDER_ITEM = "mxschedtree/item";
   private static final String SCHEDULE_FOLDER_DEEP_CHILD = "mxschedtree/item/deep";
   private static final String SCHEDULE_FOLDER_SUB = "mxschedtree/item/sub";

   private static SecurityTestDataBuilder builder;

   private static SRPrincipal contentResourceAdmin;
   private static SRPrincipal deepOnlyAdmin;
   private static SRPrincipal scriptLibraryAdmin;
   private static SRPrincipal scriptLibraryWriteUser;
   private static SRPrincipal tableStyleAdmin;
   private static SRPrincipal tableStyleDeepOnlyAdmin;
   private static SRPrincipal scheduleFolderAdmin;
   private static SRPrincipal scheduleFolderDeepOnlyAdmin;

   @BeforeAll
   static void setUp() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)
         .addUser("contentResourceAdmin", ORG_ID, "password")
         // Doc calls this persona "no-grant" (as in: no grant on the resource actually being
         // checked in the Rule 2/3 assertions) -- named deepOnlyAdmin here to avoid implying it
         // holds zero permissions anywhere, since it does hold ADMIN on a *deeper* descendant.
         .addUser("deepOnlyAdmin", ORG_ID, "password")
         .addUser("scriptLibraryAdmin", ORG_ID, "password")
         .addUser("scriptLibraryWriteUser", ORG_ID, "password")
         .addUser("tableStyleAdmin", ORG_ID, "password")
         .addUser("tableStyleDeepOnlyAdmin", ORG_ID, "password")
         .addUser("scheduleFolderAdmin", ORG_ID, "password")
         .addUser("scheduleFolderDeepOnlyAdmin", ORG_ID, "password")

         // contentResourceAdmin: explicit ADMIN on the "item" node of each hierarchy -- the
         // grant point Rule 1 (downward cascade) and the ADMIN-implies-R/W/D fallback both
         // build on.
         .grantPermission(ResourceType.ASSET, ASSET_ITEM, ResourceAction.ADMIN,
                          "contentResourceAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.REPORT, REPORT_ITEM, ResourceAction.ADMIN,
                          "contentResourceAdmin", Identity.USER, ORG_ID)

         // deepOnlyAdmin: ADMIN only on a resource TWO levels below the node checked by the
         // Rule 2/3 assertions (folder -> item -> sub), so a single resource ("folder") can
         // stand in for both "immediate parent of item" (Rule 2) and "grandparent of sub"
         // (Rule 3) simultaneously, matching the doc's two rows against the same resource.
         .grantPermission(ResourceType.ASSET, ASSET_SUB, ResourceAction.ADMIN,
                          "deepOnlyAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.REPORT, REPORT_SUB, ResourceAction.ADMIN,
                          "deepOnlyAdmin", Identity.USER, ORG_ID)

         // 附加 baseline: direct ADMIN grants on otherwise-untested resource groups.
         .grantPermission(ResourceType.DASHBOARD, DASHBOARD_ITEM, ResourceAction.ADMIN,
                          "contentResourceAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SCHEDULE_TASK, SCHEDULE_TASK_ITEM, ResourceAction.ADMIN,
                          "contentResourceAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SCRIPT, SCRIPT_ITEM, ResourceAction.ADMIN,
                          "contentResourceAdmin", Identity.USER, ORG_ID)

         // SCRIPT_LIBRARY root cascade (Rule 1-style) and WRITE-promotes-DELETE (Rule 4-style) --
         // both checked against script instances that were never individually granted. ADMIN's
         // cumulative-merge helper doesn't need markPermissionEdited() (it never stops early
         // regardless of the edited flag, same as every other ADMIN cascade in this file), but
         // the WRITE grant DOES: DELETE is checked via the generic non-ADMIN inheritance walk,
         // which climbs straight past an unmarked SCRIPT_LIBRARY root (and the LIBRARY root
         // above it, which is never hierarchical anyway) and returns deny.
         .grantPermission(ResourceType.SCRIPT_LIBRARY, SCRIPT_LIBRARY_ROOT, ResourceAction.ADMIN,
                          "scriptLibraryAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SCRIPT_LIBRARY, SCRIPT_LIBRARY_ROOT, ResourceAction.WRITE,
                          "scriptLibraryWriteUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.SCRIPT_LIBRARY, SCRIPT_LIBRARY_ROOT, ORG_ID)

         // TABLE_STYLE own hierarchy -- same shape as the ASSET main table: tableStyleAdmin's
         // ADMIN sits on the mid-level ITEM node (Rule 1 downward-cascade grant point);
         // tableStyleDeepOnlyAdmin's ADMIN sits two levels below FOLDER (Rule 2/3 grant point).
         // FOLDER also gets an unrelated READ grant (to contentResourceAdmin, who is never
         // checked against TABLE_STYLE) purely to make its own Permission non-blank -- otherwise
         // TABLE_STYLE/SCHEDULE_TASK_FOLDER's "everyone" fallback (security.tablestyle.everyone,
         // see PermissionMatrixResourcesS5Test) kicks in for an entirely unconfigured FOLDER and
         // default-allows READ to anyone, masking the Rule 3 denial this section means to prove.
         .grantPermission(ResourceType.TABLE_STYLE, TABLE_STYLE_FOLDER, ResourceAction.READ,
                          "contentResourceAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.TABLE_STYLE, TABLE_STYLE_ITEM, ResourceAction.ADMIN,
                          "tableStyleAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.TABLE_STYLE, TABLE_STYLE_SUB, ResourceAction.ADMIN,
                          "tableStyleDeepOnlyAdmin", Identity.USER, ORG_ID)

         // SCHEDULE_TASK_FOLDER own hierarchy -- same shape again, on the "/"-delimited folder
         // type (distinct from the flat SCHEDULE_TASK sampled above); same non-blank-FOLDER
         // reasoning as TABLE_STYLE above (security.scheduletask.everyone).
         .grantPermission(ResourceType.SCHEDULE_TASK_FOLDER, SCHEDULE_FOLDER_TOP,
                          ResourceAction.READ, "contentResourceAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SCHEDULE_TASK_FOLDER, SCHEDULE_FOLDER_ITEM,
                          ResourceAction.ADMIN, "scheduleFolderAdmin", Identity.USER, ORG_ID)
         .grantPermission(ResourceType.SCHEDULE_TASK_FOLDER, SCHEDULE_FOLDER_SUB,
                          ResourceAction.ADMIN, "scheduleFolderDeepOnlyAdmin", Identity.USER,
                          ORG_ID)

         .setup();

      contentResourceAdmin = builder.principalOf("contentResourceAdmin", ORG_ID);
      deepOnlyAdmin = builder.principalOf("deepOnlyAdmin", ORG_ID);
      scriptLibraryAdmin = builder.principalOf("scriptLibraryAdmin", ORG_ID);
      scriptLibraryWriteUser = builder.principalOf("scriptLibraryWriteUser", ORG_ID);
      tableStyleAdmin = builder.principalOf("tableStyleAdmin", ORG_ID);
      tableStyleDeepOnlyAdmin = builder.principalOf("tableStyleDeepOnlyAdmin", ORG_ID);
      scheduleFolderAdmin = builder.principalOf("scheduleFolderAdmin", ORG_ID);
      scheduleFolderDeepOnlyAdmin = builder.principalOf("scheduleFolderDeepOnlyAdmin", ORG_ID);
   }

   @AfterAll
   static void tearDown() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   // ════════════════════════════════════════════════════════════════════════════
   // Main table (ASSET)
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void contentResourceAdmin_adminOnItem_allowed() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void contentResourceAdmin_readWriteDeleteOnItem_allowed_adminImpliesRWD() {
      // ADMIN -> READ/WRITE/DELETE fallback lives in SecurityEngine.checkPermission() (it
      // retries with ADMIN whenever the originally requested action is denied), not in this
      // fixture's grant data -- same generic mechanism S2 already verified for SECURITY_*.
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_ITEM)
               .expectAllow(contentResourceAdmin,
                            ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void contentResourceAdmin_readOnDeepChild_allowed_rule1DownwardCascade() {
      // Rule 1: ADMIN on a folder propagates down to descendants that have no permission of
      // their own -- DefaultCheckPermissionStrategy's generic parent-walk finds contentResource
      // Admin's grant on ASSET_ITEM when checking its child ASSET_DEEP_CHILD.
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_DEEP_CHILD)
               .expectAllow(contentResourceAdmin, ResourceAction.READ)
            .verify());
   }

   @Test
   void deepOnlyAdmin_adminOnParentFolder_denied_rule2NoUpwardCascade() {
      // Rule 2: ADMIN on a descendant does NOT propagate up -- deepOnlyAdmin's grant is on
      // ASSET_SUB (a child of ASSET_ITEM), checked here against ASSET_FOLDER (ASSET_ITEM's
      // parent). The permission walk only ever climbs from the checked resource toward the
      // root, so a grant on a descendant is structurally invisible to it.
      withContextPrincipal(deepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER)
               .expectDeny(deepOnlyAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void deepOnlyAdmin_readOnParentFolder_denied_rule3NoCrossLevelCascade() {
      // Rule 3: same resource as Rule 2 above, but checked as ASSET_SUB's GRANDPARENT (not just
      // ASSET_ITEM's parent) and for a lesser action (READ, not ADMIN) -- confirms the
      // non-propagation holds across multiple levels and isn't just an ADMIN-specific guard.
      withContextPrincipal(deepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.ASSET, ASSET_FOLDER)
               .expectDeny(deepOnlyAdmin, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S3-CROSS-GROUP (REPORT) -- same four assertions, proving Rule 1-3 aren't ASSET-specific
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void contentResourceAdmin_adminOnReportItem_allowed_crossGroup() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.REPORT, REPORT_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void contentResourceAdmin_readOnReportDeepChild_allowed_rule1CrossGroup() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.REPORT, REPORT_DEEP_CHILD)
               .expectAllow(contentResourceAdmin, ResourceAction.READ)
            .verify());
   }

   @Test
   void deepOnlyAdmin_adminOnReportParentFolder_denied_rule2CrossGroup() {
      withContextPrincipal(deepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.REPORT, REPORT_FOLDER)
               .expectDeny(deepOnlyAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void deepOnlyAdmin_readOnReportParentFolder_denied_rule3CrossGroup() {
      withContextPrincipal(deepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.REPORT, REPORT_FOLDER)
               .expectDeny(deepOnlyAdmin, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // 附加 — Portal Dashboard / Schedule baseline (no hierarchy, ADMIN-implies-R/W/D only)
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void contentResourceAdmin_adminOnDashboard_allowed() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.DASHBOARD, DASHBOARD_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void contentResourceAdmin_readWriteDeleteOnDashboard_allowed_adminImpliesRWD() {
      // DASHBOARD.getParent() always returns null -- no Rule 1-3 to exercise, just the generic
      // ADMIN-implies-R/W/D fallback.
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.DASHBOARD, DASHBOARD_ITEM)
               .expectAllow(contentResourceAdmin,
                            ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE)
            .verify());
   }

   @Test
   void contentResourceAdmin_adminOnScheduleTask_allowed() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCHEDULE_TASK, SCHEDULE_TASK_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void contentResourceAdmin_readOnScheduleTask_allowed_adminImpliesRWD() {
      // ResourceType.SCHEDULE_TASK is not hierarchical (unlike SCHEDULE_TASK_FOLDER) -- the "/"
      // in this resource string is just an opaque character here, not a folder separator.
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCHEDULE_TASK, SCHEDULE_TASK_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // 附加 — Library: Script (flat instances, but a real SCRIPT_LIBRARY root above them)
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void contentResourceAdmin_adminOnScript_allowed() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCRIPT, SCRIPT_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void contentResourceAdmin_readOnScript_allowed_adminImpliesRWD() {
      withContextPrincipal(contentResourceAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCRIPT, SCRIPT_ITEM)
               .expectAllow(contentResourceAdmin, ResourceAction.READ)
            .verify());
   }

   @Test
   void scriptLibraryAdmin_adminOnAnyScript_allowed_rootCascade() {
      // SCRIPT_ITEM_UNGRANTED_FOR_CASCADE was never individually granted -- ADMIN on the shared
      // SCRIPT_LIBRARY root ("*", every SCRIPT.getParent() resolves here regardless of path)
      // cascades to it the same way a folder's ADMIN cascades to its children in the main table.
      withContextPrincipal(scriptLibraryAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCRIPT, SCRIPT_ITEM_UNGRANTED_FOR_CASCADE)
               .expectAllow(scriptLibraryAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void scriptLibraryWriteUser_deleteOnAnyScript_allowed_rootWritePromotesDelete() {
      // Same Rule 4 WRITE-promotes-DELETE mechanism as the main table's partialGrantUser, just
      // with SCRIPT_LIBRARY's root as the "folder" and an ungranted script instance as the
      // "child".
      withContextPrincipal(scriptLibraryWriteUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCRIPT, SCRIPT_ITEM_UNGRANTED_FOR_PROMOTION)
               .expectAllow(scriptLibraryWriteUser, ResourceAction.DELETE)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // TABLE_STYLE own hierarchy ("~"-delimited, genuinely multi-level) -- minimal Rule 1-3
   // boundary check on TABLE_STYLE.getParent()'s own override.
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void tableStyleAdmin_readOnDeepChild_allowed_rule1TableStyleHierarchy() {
      withContextPrincipal(tableStyleAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.TABLE_STYLE, TABLE_STYLE_DEEP_CHILD)
               .expectAllow(tableStyleAdmin, ResourceAction.READ)
            .verify());
   }

   @Test
   void tableStyleDeepOnlyAdmin_adminOnParentFolder_denied_rule2TableStyleHierarchy() {
      withContextPrincipal(tableStyleDeepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.TABLE_STYLE, TABLE_STYLE_FOLDER)
               .expectDeny(tableStyleDeepOnlyAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void tableStyleDeepOnlyAdmin_readOnParentFolder_denied_rule3TableStyleHierarchy() {
      withContextPrincipal(tableStyleDeepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.TABLE_STYLE, TABLE_STYLE_FOLDER)
               .expectDeny(tableStyleDeepOnlyAdmin, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // SCHEDULE_TASK_FOLDER own hierarchy ("/"-delimited, independent of flat SCHEDULE_TASK) --
   // minimal Rule 1-3 boundary check on SCHEDULE_TASK_FOLDER.getParent()'s own override.
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void scheduleFolderAdmin_readOnDeepChild_allowed_rule1ScheduleFolderHierarchy() {
      withContextPrincipal(scheduleFolderAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCHEDULE_TASK_FOLDER, SCHEDULE_FOLDER_DEEP_CHILD)
               .expectAllow(scheduleFolderAdmin, ResourceAction.READ)
            .verify());
   }

   @Test
   void scheduleFolderDeepOnlyAdmin_adminOnParentFolder_denied_rule2ScheduleFolderHierarchy() {
      withContextPrincipal(scheduleFolderDeepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCHEDULE_TASK_FOLDER, SCHEDULE_FOLDER_TOP)
               .expectDeny(scheduleFolderDeepOnlyAdmin, ResourceAction.ADMIN)
            .verify());
   }

   @Test
   void scheduleFolderDeepOnlyAdmin_readOnParentFolder_denied_rule3ScheduleFolderHierarchy() {
      withContextPrincipal(scheduleFolderDeepOnlyAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.SCHEDULE_TASK_FOLDER, SCHEDULE_FOLDER_TOP)
               .expectDeny(scheduleFolderDeepOnlyAdmin, ResourceAction.READ)
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

   private static SecurityEngine engine() {
      return SecurityEngine.getSecurity();
   }
}
