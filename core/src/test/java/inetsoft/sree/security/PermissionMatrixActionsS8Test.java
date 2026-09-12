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
 * Slice test class for permission-matrix-actions.md § S8 (ordinary-user feature toggles --
 * dep-on-grant). Built up slice by slice per that doc's structure: main table (this slice) ->
 * S8-BOOKMARK -> S8-CHART-TYPE -> S8-SWEEP.
 *
 * ── This slice: main table (AI_ASSISTANT / FREE_FORM_SQL) ──────────────────────────────────
 *
 * AI_ASSISTANT and FREE_FORM_SQL both use the same grant/no-grant pair shape: role0 is granted
 * ACCESS on the resource, and the permission is marked edited (SecurityTestDataBuilder.
 * markPermissionEdited()) so DefaultCheckPermissionStrategy treats it as an admin-saved record
 * (hasOrgEditedGrantAll=true) rather than "never configured". That distinction matters here
 * specifically for AI_ASSISTANT: it is one of the resource types in DefaultCheckPermissionStrategy
 * L273-287's "no permission record -> default allow" list, so without markPermissionEdited(),
 * user1 (who holds no grant) would default-allow instead of being denied, and the test would not
 * be exercising the identity-resolution branch it claims to. FREE_FORM_SQL is not in that list, so
 * marking it edited doesn't change the outcome there, but the fixture still marks it for
 * consistency with the doc's stated intent (an admin-edited record, not an untouched one).
 *
 * siteAdmin's FREE_FORM_SQL bypass is definitional (isSysAdmin/isSiteAdmin short-circuits in
 * DefaultCheckPermissionStrategy before any grant lookup), so one resource is enough to prove it --
 * this is the same reasoning permission-matrix-resources.md uses for not giving siteAdmin its own
 * per-resource-type slice (S1), and that PermissionMatrixActionsS6Test uses for not re-asserting
 * siteAdmin's bypass alongside every orgAdminActionExclusions entry.
 *
 * No SUtil.isMultiTenant() mocking is needed here (unlike S6): the only place isMultiTenant()
 * affects DefaultCheckPermissionStrategy.checkPermission() is the org-admin-exclusion early-return,
 * which only fires for org-admin principals. user0/user1 here hold no org-admin role, so that
 * branch is never reached regardless of isMultiTenant()'s value.
 *
 * ── S8-BOOKMARK slice (VIEWSHEET_ACTION: Bookmark / OpenBookmark / ShareBookmark / ShareToAll) ──
 *
 * LANES repeats S4's three content-access paths (User->Role, User->Group, User->Group->Role) plus
 * S4-ROLE-HIERARCHY/S4-GROUP-HIERARCHY's two inheritance chains, all on the same "Bookmark"
 * resource, to confirm none of that generic identity-resolution machinery is ASSET-specific.
 *
 * Deviation from the doc's S8-BOOKMARK-LANES table as originally written: it named both the
 * via-group and via-group-role rows as "group0". Implemented literally, that would make them the
 * SAME group -- directly granted (for via-group) AND holding role0 (for via-group-role) -- so the
 * via-group-role user would pass via the direct group grant alone, never actually exercising the
 * group->role lookup the row claims to test. Split into two distinct groups instead (group0,
 * directly granted; bookmarkRoleHolderGroup, granted nothing directly but holding role0), mirroring
 * PermissionMatrixResourcesS4Test's viewerGroup/roleHolderGroup split for the same reason.
 *
 * INDEPENDENCE proves OpenBookmark/ShareBookmark/ShareToAll don't cascade. All three (plus
 * Bookmark) must be markPermissionEdited(): VIEWSHEET_ACTION is in DefaultCheckPermissionStrategy's
 * default-allow list, so an unedited ShareBookmark/OpenBookmark would default-allow regardless of
 * shareToAllOnlyUser's lack of a grant there, making the "denied" assertions pass for the wrong
 * reason (default-allow, not proven independence) instead of failing loudly.
 *
 * ── S8-CHART-TYPE slice (CHART_TYPE / CHART_TYPE_FOLDER cross-type hierarchy) ──────────────────
 *
 * CHART_TYPE.getParent() (ResourceType.java) is a genuine type-specific override -- it returns a
 * Resource of a DIFFERENT type (CHART_TYPE_FOLDER), not the same-type "/"-split parent that
 * ASSET/REPORT use. Traced through DefaultCheckPermissionStrategy.checkPermission() to confirm the
 * exact mechanics before writing fixtures:
 *   - CHART_TYPE_FOLDER is declared non-hierarchical (isHierarchical()==false), so the while-loop
 *     climb from a CHART_TYPE leaf always stops after exactly one hop at its folder -- there is no
 *     grandparent to climb further to, unlike ASSET/REPORT folders.
 *   - The "unconfigured -> default allow" list check (L265-293) only runs ONCE, against the
 *     ORIGINALLY-REQUESTED type, before the hierarchical while-loop. Climbing from CHART_TYPE (not
 *     in the list) to CHART_TYPE_FOLDER (in the list) during the loop does NOT get a second chance
 *     at that check -- it only applies when CHART_TYPE_FOLDER is the type directly requested. This
 *     is what makes the HIERARCHY and DEFAULT-ASYMMETRY sub-slices genuinely different scenarios,
 *     not restatements of each other.
 *   - checkPermission() also starts with isAllowedDefaultGlobalVSAction(), a separate bypass for
 *     CHART_TYPE/SHARE/VIEWSHEET_TOOLBAR_ACTION when isOpeningShareGlobalAsset() is true (viewing a
 *     globally-shared VS from a non-default org). That requires the checked principal's org to
 *     differ from the current org AND the current org to be the default org -- neither holds for
 *     this test class's single custom org, so it never fires here and needs no fixture handling.
 *
 * Real chart-type resource paths (ActionPermissionService.getChartNode()) are "{folder}/{folder}"
 * for a category's base type plus "{folder}/{subtype}" for each variant -- e.g. folder "Area" has
 * both "Area/Area" and "Area/Step Area"; folder "Others" has no "Others/Others" (its first variant
 * is "Others/Funnel"). Confirmed by reading the real builder calls rather than guessing from the
 * architecture doc's shorthand ("Area(-> Step Area)"), same motivation as S8-SWEEP's tree-reading
 * approach.
 *
 * HIERARCHY's third row (own explicit-empty permission blocks promotion) deliberately does NOT
 * reuse "Area"/"Area/Step Area" from the first two rows: markPermissionEdited() sets a flag on the
 * whole Permission object for that resource, not per-identity, so marking "Area/Step Area" edited
 * for this row would also flip the first row's fixture (which depends on that same child resource
 * being UNTOUCHED so the climb to its parent folder actually happens) -- the two states can't
 * coexist on one resource. Uses a separate folder/child pair ("Bar"/"Bar/Interval") instead, same
 * shape as PermissionMatrixResourcesS5Test's ASSET_FOLDER2_ITEM_EXPLICIT pattern.
 *
 * DEFAULT-ASYMMETRY reuses user1 (already role-less from the main-table fixtures) as the "anyViewer"
 * probe -- CHART_TYPE_FOLDER "Point" and CHART_TYPE "Others/Waterfall" are never touched by
 * grantPermission/markPermissionEdited anywhere in this class, so any identity works; user1 is
 * simplest since it already exists.
 *
 * Both HIERARCHY's sibling-isolation row and DEFAULT-ASYMMETRY's leaf row were originally written
 * assuming DefaultCheckPermissionStrategy is the whole story for CHART_TYPE, and both had to be
 * corrected after running them turned up a mechanism neither the architecture doc nor the initial
 * trace accounted for: SecurityEngine.checkPermission() (SecurityEngine.java L834-847) has a
 * CHART_TYPE-specific fallback that DefaultCheckPermissionStrategy knows nothing about -- if the
 * provider's own resolution denies a CHART_TYPE leaf, SecurityEngine retries by issuing a brand
 * new, TOP-LEVEL checkPermission() call against the parent CHART_TYPE_FOLDER (not an internal
 * climb within the same strategy call). That fresh call gives CHART_TYPE_FOLDER a chance to hit
 * the default-allow list as the *originally-requested* type, something that can never happen via
 * DefaultCheckPermissionStrategy's own internal while-loop (which only consults the default-allow
 * list once, against the type the top-level caller asked for, before any climbing starts).
 *
 * Two concrete consequences, discovered by running the tests, not by re-reading the doc harder:
 *
 *   1. HIERARCHY's sibling-isolation row must NOT leave the sibling folder ("Line") completely
 *      untouched. An untouched CHART_TYPE_FOLDER always default-allows via this fallback,
 *      regardless of whether chartFolderGrantUser holds any grant on "Area" at all -- the row
 *      would pass without ever exercising sibling isolation. Fixed by marking "Line"
 *      edited-and-empty (an explicit, saved deny) instead of leaving it unconfigured, so the
 *      fallback's retry against "Line" actually goes through the identity-match branch and fails,
 *      the way a real "this org configured Line and didn't grant it" state would.
 *
 *   2. DEFAULT-ASYMMETRY's leaf row ("Others/Waterfall", parent "Others" also fully unconfigured)
 *      does NOT default-deny as the architecture doc claims (its L647 reasoning only traced
 *      DefaultCheckPermissionStrategy, not SecurityEngine's wrapper). The fallback retries against
 *      "Others" as a fresh top-level CHART_TYPE_FOLDER check, which itself is unconfigured and
 *      therefore hits the default-allow list -- so the leaf ends up ALLOWED too. There is no real
 *      allow/deny asymmetry between CHART_TYPE_FOLDER and CHART_TYPE in the fully-unconfigured
 *      case; both converge on allow, just via different code paths (CHART_TYPE_FOLDER hits the
 *      list directly; CHART_TYPE gets there indirectly through this retry). The test below asserts
 *      the actual (allowed) outcome and exists to pin down that this specific fallback path is
 *      what's responsible, so a future refactor that removes it doesn't silently flip the result
 *      without anything catching it.
 *
 * ── S8-SWEEP slice (all remaining independent feature toggles) ────────────────────────────────
 *
 * Reads the REAL production tree (ActionPermissionService.getActionTree()) instead of hand-typing
 * ~38 resource strings, for the same reason S6 parameterizes over orgAdminActionExclusions instead
 * of copying it: hand-typed strings drift. This was not a hypothetical concern here -- designing
 * this slice's doc table by hand got two resource strings wrong before any test was written
 * (Portal "Repository" tab's real resource is "Report", not "Repository"; Social Sharing's "Copy
 * Link" is "link", not "CopyLink") and the doc's guess at Portal Tabs' item count was also wrong
 * (see below). Both were caught by reading ActionPermissionService.java directly, which is the
 * whole justification for reading the tree at test time too.
 *
 * getActionTree(Principal) needs a Principal for a handful of internal null-checks and
 * Catalog.getCatalog(principal), but -- like S6's finding that isMultiTenant() is structurally
 * false in this module (LicenseManager.isEnterprise() can't find inetsoft.enterprise.
 * EnterpriseConfig on community/core's test classpath) -- every one of those checks that also
 * looks at isSiteAdmin(principal)/isMultiTenant() resolves to "include the node" regardless of
 * which principal is passed, because they're all `!isMultiTenant() || isSiteAdmin(...)` ORs and
 * isMultiTenant() is already false. So any registered, non-null principal produces the complete,
 * unfiltered tree; a plain unregistered SRPrincipal (never passed through SecurityTestDataBuilder)
 * is enough -- it never reaches SecurityEngine.checkPermission(), only field inspection.
 *
 * LOGIN_AS is the one node genuinely gated by something other than isMultiTenant/isSiteAdmin --
 * "on".equals(SreeEnv.getProperty("login.loginAs")). That property doesn't affect
 * DefaultCheckPermissionStrategy's LOGIN_AS resolution at all (grep confirms the only other
 * reader is AuthenticationService's actual login-as feature gate, unrelated to permission
 * checking), so it's safe to flip on only for the duration of the tree walk and restore
 * immediately after, same pattern as PermissionMatrixResourcesS4Test's withAndCondition().
 *
 * PortalThemesManager is constructed directly with `new` (not through Spring), using its no-arg
 * constructor explicitly commented "For non-Spring environments (tests, non-Spring processes)".
 * Its loadThemes() (normally @PostConstruct, called manually here) falls back to the bundled
 * classpath default (core/src/main/resources/inetsoft/sree/portal/portalthemes.xml) when DataSpace
 * has no persisted portalthemes.xml, which is exactly this test environment's state -- so calling
 * it manually reproduces a fresh install's real 4 default tabs (Dashboard/Report/Schedule/Data),
 * not a guessed list. (The doc's original draft guessed "~2" Portal Tabs items and didn't know
 * "Design" isn't one of the shipped defaults; reading the actual default XML resource corrected
 * both.)
 *
 * ComponentAuthorizationService, by contrast, is Mockito-mocked rather than constructed with `new`
 * -- its loadComponents() deserializes view-components.json, a resource the *web* module's
 * frontend build generates (web/target/generated-resources/gulp/...), not something core's own
 * test classpath has. getEnterpriseManagerNode() calls componentService.getComponentTree()
 * unconditionally while building the tree, but that only feeds the EM_COMPONENT subtree, which
 * this sweep excludes anyway -- so a mock returning an empty ViewComponent is a faithful stand-in
 * for what this test actually needs from it, not a workaround for something the test depends on.
 *
 * Excluded from the sweep (each already covered by a different slice, with a different fixture
 * shape than "grant role0 wholesale, deny everyone else" would produce):
 *   - EM / EM_COMPONENT, SCHEDULE_TASK, DEVICE, UPLOAD_DRIVERS, SCHEDULE_OPTION:timeRange -- S6
 *   - CHART_TYPE / CHART_TYPE_FOLDER -- S8-CHART-TYPE (cross-type hierarchy, default asymmetry)
 *   - AI_ASSISTANT, FREE_FORM_SQL -- S8 main table
 *   - VIEWSHEET_ACTION -- S8-BOOKMARK
 * Everything else the tree produces (Viewsheet Toolbar's 11 actions, Visual Composer, Physical
 * Table, My Dashboard, Portal Repository Tree Drag-and-Drop, Materialize Assets, the 4 real Portal
 * Tabs, the 4 non-timeRange Schedule Options, Log in As, Social Sharing's 7 channels, Profile,
 * Cross Join, Create New DataSource, Edit Dashboard Calculated Fields, Edit Worksheet Expression
 * Columns) is granted to role0 wholesale and checked via user0 (allowed) / user1 (denied), one
 * @ParameterizedTest pair covering every leaf x every one of its declared actions. Every leaf is
 * markPermissionEdited() regardless of whether its type is on DefaultCheckPermissionStrategy's
 * default-allow list, for the same reason the main table's AI_ASSISTANT grant is: several of these
 * types (MY_DASHBOARDS, PORTAL_REPOSITORY_TREE_DRAG_AND_DROP, MATERIALIZATION, SCHEDULE_OPTION,
 * VIEWSHEET_TOOLBAR_ACTION, SHARE) are on that list, and PORTAL_TAB:"Report" specifically is too
 * (DefaultCheckPermissionStrategy L278) -- without marking it edited, user1's "denied" assertion
 * for that one leaf would default-allow instead.
 *
 * Role/group inheritance is deliberately NOT re-verified per sweep leaf -- see
 * S8-BOOKMARK-LANES's closing comment for why one representative resource (Bookmark) is enough.
 */

import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.support.*;
import inetsoft.util.DataSpace;
import inetsoft.test.*;
import inetsoft.uql.util.Identity;
import inetsoft.util.ThreadContext;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.authz.ViewComponent;
import inetsoft.web.admin.security.action.ActionPermissionService;
import inetsoft.web.admin.security.action.ActionTreeNode;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PermissionMatrixActionsS8Test {

   private static final String ORG_NAME = "actionsS8Org";
   private static final String ORG_ID = "actions_s8_org_id";

   private static final String WILDCARD_RESOURCE = "*";
   private static final String BOOKMARK = "Bookmark";
   private static final String OPEN_BOOKMARK = "OpenBookmark";
   private static final String SHARE_BOOKMARK = "ShareBookmark";
   private static final String SHARE_TO_ALL = "ShareToAll";

   private static final String CHART_FOLDER_AREA = "Area";
   private static final String CHART_TYPE_AREA_STEP_AREA = "Area/Step Area";
   private static final String CHART_FOLDER_LINE = "Line";
   private static final String CHART_TYPE_LINE_STEP_LINE = "Line/Step Line";
   private static final String CHART_FOLDER_BAR = "Bar";
   private static final String CHART_TYPE_BAR_INTERVAL = "Bar/Interval";
   private static final String CHART_FOLDER_POINT = "Point";
   private static final String CHART_FOLDER_OTHERS = "Others";
   private static final String CHART_TYPE_OTHERS_WATERFALL = "Others/Waterfall";

   private static SecurityTestDataBuilder builder;

   private static SRPrincipal user0;
   private static SRPrincipal user1;
   private static SRPrincipal siteAdmin;

   private static SRPrincipal user0ViaGroup;
   private static SRPrincipal user0ViaGroupRole;
   private static SRPrincipal user0ViaRoleHierarchy;
   private static SRPrincipal user0ViaGroupHierarchy;
   private static SRPrincipal shareToAllOnlyUser;

   private static SRPrincipal chartFolderGrantUser;
   private static SRPrincipal chartFolderExplicitEmptyUser;

   // ── S8-SWEEP ──
   // Resource types already covered by other slices, using fixture shapes this sweep's "grant
   // role0 wholesale" approach doesn't reproduce -- see class javadoc for the per-slice mapping.
   private static final EnumSet<ResourceType> SWEEP_EXCLUDED_TYPES = EnumSet.of(
      ResourceType.EM, ResourceType.EM_COMPONENT, ResourceType.SCHEDULE_TASK,
      ResourceType.DEVICE, ResourceType.UPLOAD_DRIVERS,
      ResourceType.CHART_TYPE, ResourceType.CHART_TYPE_FOLDER,
      ResourceType.AI_ASSISTANT, ResourceType.FREE_FORM_SQL, ResourceType.VIEWSHEET_ACTION);

   private static final String SCHEDULE_OPTION_TIME_RANGE = "timeRange";

   private static List<SweepLeaf> sweepLeaves;

   private record SweepLeaf(ResourceType type, String resource, EnumSet<ResourceAction> actions) {}

   @BeforeAll
   static void setUpAll() throws Exception {
      sweepLeaves = collectSweepLeaves();

      SecurityTestDataBuilder b = SecurityTestDataBuilder.create()
         .addOrg(ORG_NAME, ORG_ID)

         .addRole("role0", ORG_ID)
         .addUser("user0", ORG_ID, "password")
         .addUser("user1", ORG_ID, "password")
         .addUserToRole("user0", "role0", ORG_ID)

         // AI_ASSISTANT: edited record, granted to role0 only. user1 holds no role -> denied on
         // the same record (see class javadoc for why markPermissionEdited matters here).
         .grantPermission(ResourceType.AI_ASSISTANT, WILDCARD_RESOURCE, ResourceAction.ACCESS,
                          "role0", Identity.ROLE, ORG_ID)
         .markPermissionEdited(ResourceType.AI_ASSISTANT, WILDCARD_RESOURCE, ORG_ID)

         // FREE_FORM_SQL: edited record, granted to nobody -- user1 negative control.
         .markPermissionEdited(ResourceType.FREE_FORM_SQL, WILDCARD_RESOURCE, ORG_ID)

         // siteAdmin, for the FREE_FORM_SQL bypass row.
         .addSysAdminRole("Administrator", ORG_ID)
         .addUser("siteAdminUser", ORG_ID, "password")
         .addUserToRole("siteAdminUser", "Administrator", ORG_ID)

         // ── S8-BOOKMARK-LANES ──
         // via-role: reuses role0/user0/user1 from the main table above -- role0 gets a second,
         // independent grant on a different resource; user1 remains the negative control.
         .grantPermission(ResourceType.VIEWSHEET_ACTION, BOOKMARK, ResourceAction.READ,
                          "role0", Identity.ROLE, ORG_ID)
         .markPermissionEdited(ResourceType.VIEWSHEET_ACTION, BOOKMARK, ORG_ID)

         // via-group: group0 is granted directly.
         .addGroup("group0", ORG_ID)
         .addUser("user0ViaGroup", ORG_ID, "password")
         .addUserToGroup("user0ViaGroup", "group0", ORG_ID)
         .grantPermission(ResourceType.VIEWSHEET_ACTION, BOOKMARK, ResourceAction.READ,
                          "group0", Identity.GROUP, ORG_ID)

         // via-group-role: bookmarkRoleHolderGroup holds role0 (already granted above) but has
         // no grant of its own -- deliberately a different group from group0 (see class javadoc).
         .addGroup("bookmarkRoleHolderGroup", ORG_ID)
         .addRoleToGroup("role0", "bookmarkRoleHolderGroup", ORG_ID)
         .addUser("user0ViaGroupRole", ORG_ID, "password")
         .addUserToGroup("user0ViaGroupRole", "bookmarkRoleHolderGroup", ORG_ID)

         // role-hierarchy: role1 -> role2 (role2 granted).
         .addRole("role1", ORG_ID)
         .addRole("role2", ORG_ID)
         .addRoleParent("role1", "role2", ORG_ID)
         .addUser("user0ViaRoleHierarchy", ORG_ID, "password")
         .addUserToRole("user0ViaRoleHierarchy", "role1", ORG_ID)
         .grantPermission(ResourceType.VIEWSHEET_ACTION, BOOKMARK, ResourceAction.READ,
                          "role2", Identity.ROLE, ORG_ID)

         // group-hierarchy: group1 -> group2 (group2 granted).
         .addGroup("group1", ORG_ID)
         .addGroup("group2", ORG_ID)
         .addGroupParent("group1", "group2", ORG_ID)
         .addUser("user0ViaGroupHierarchy", ORG_ID, "password")
         .addUserToGroup("user0ViaGroupHierarchy", "group1", ORG_ID)
         .grantPermission(ResourceType.VIEWSHEET_ACTION, BOOKMARK, ResourceAction.READ,
                          "group2", Identity.GROUP, ORG_ID)

         // ── S8-BOOKMARK-INDEPENDENCE ──
         // shareToAllOnlyUser is granted ONLY ShareToAll; ShareBookmark/OpenBookmark are marked
         // edited with no grant at all (see class javadoc on why markPermissionEdited is required
         // on all three, not just the granted one).
         .addUser("shareToAllOnlyUser", ORG_ID, "password")
         .grantPermission(ResourceType.VIEWSHEET_ACTION, SHARE_TO_ALL, ResourceAction.READ,
                          "shareToAllOnlyUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.VIEWSHEET_ACTION, SHARE_TO_ALL, ORG_ID)
         .markPermissionEdited(ResourceType.VIEWSHEET_ACTION, SHARE_BOOKMARK, ORG_ID)
         .markPermissionEdited(ResourceType.VIEWSHEET_ACTION, OPEN_BOOKMARK, ORG_ID)

         // ── S8-CHART-TYPE-HIERARCHY ──
         // Rule1: chartFolderGrantUser is granted READ on folder "Area" only. "Area/Step Area"
         // itself is left completely untouched so the climb to its parent actually happens.
         .addUser("chartFolderGrantUser", ORG_ID, "password")
         .grantPermission(ResourceType.CHART_TYPE_FOLDER, CHART_FOLDER_AREA, ResourceAction.READ,
                          "chartFolderGrantUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.CHART_TYPE_FOLDER, CHART_FOLDER_AREA, ORG_ID)

         // Rule2/3: "Line" must be edited-and-empty, NOT left untouched -- see class javadoc on
         // SecurityEngine's CHART_TYPE-specific parent-retry fallback. An untouched sibling folder
         // would default-allow regardless of chartFolderGrantUser's grants, defeating the point of
         // this row.
         .markPermissionEdited(ResourceType.CHART_TYPE_FOLDER, CHART_FOLDER_LINE, ORG_ID)

         // Own-explicit-empty-blocks-promotion: a separate folder/child pair from Area/Line (see
         // class javadoc on why "Area/Step Area" can't be reused here). "Bar" folder is granted to
         // chartFolderExplicitEmptyUser, but "Bar/Interval" has its own edited-and-empty record.
         .addUser("chartFolderExplicitEmptyUser", ORG_ID, "password")
         .grantPermission(ResourceType.CHART_TYPE_FOLDER, CHART_FOLDER_BAR, ResourceAction.READ,
                          "chartFolderExplicitEmptyUser", Identity.USER, ORG_ID)
         .markPermissionEdited(ResourceType.CHART_TYPE_FOLDER, CHART_FOLDER_BAR, ORG_ID)
         .markPermissionEdited(ResourceType.CHART_TYPE, CHART_TYPE_BAR_INTERVAL, ORG_ID)

         // ── S8-CHART-TYPE-DEFAULT-ASYMMETRY ──
         // CHART_FOLDER_POINT and CHART_TYPE_OTHERS_WATERFALL (plus its parent CHART_FOLDER_OTHERS)
         // are deliberately never touched by grantPermission/markPermissionEdited anywhere in this
         // class -- user1 (already role-less) is reused as the "anyViewer" probe against them.
         ;

      // ── S8-SWEEP ──
      // role0 (already held by user0, not by user1) is granted every action on every leaf
      // discovered by collectSweepLeaves(). markPermissionEdited() unconditionally, not just for
      // types known to be on the default-allow list -- see class javadoc. Accumulated on the same
      // builder instance as everything above, flushed by a single setup() call below --
      // SecurityTestDataBuilder.setup() creates fresh provider instances each time it runs, so
      // calling it more than once per class would silently discard whatever was registered
      // against the first set of providers.
      for(SweepLeaf leaf : sweepLeaves) {
         for(ResourceAction action : leaf.actions()) {
            b.grantPermission(leaf.type(), leaf.resource(), action,
                              "role0", Identity.ROLE, ORG_ID);
         }

         b.markPermissionEdited(leaf.type(), leaf.resource(), ORG_ID);
      }

      builder = b.setup();

      user0 = builder.principalOf("user0", ORG_ID);
      user1 = builder.principalOf("user1", ORG_ID);
      siteAdmin = builder.principalOf("siteAdminUser", ORG_ID);

      user0ViaGroup = builder.principalOf("user0ViaGroup", ORG_ID);
      user0ViaGroupRole = builder.principalOf("user0ViaGroupRole", ORG_ID);
      user0ViaRoleHierarchy = builder.principalOf("user0ViaRoleHierarchy", ORG_ID);
      user0ViaGroupHierarchy = builder.principalOf("user0ViaGroupHierarchy", ORG_ID);
      shareToAllOnlyUser = builder.principalOf("shareToAllOnlyUser", ORG_ID);

      chartFolderGrantUser = builder.principalOf("chartFolderGrantUser", ORG_ID);
      chartFolderExplicitEmptyUser = builder.principalOf("chartFolderExplicitEmptyUser", ORG_ID);
   }

   @AfterAll
   static void tearDownAll() {
      if(builder != null) {
         builder.teardown();
         builder = null;
      }
   }

   @Test
   void user0_aiAssistantAccess_allowed_viaRoleGrant() {
      withContextPrincipal(user0, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.AI_ASSISTANT, WILDCARD_RESOURCE)
               .expectAllow(user0, ResourceAction.ACCESS)
            .verify());
   }

   @Test
   void user1_aiAssistantAccess_denied_editedRecordWithoutGrant() {
      // Negative control: proves AI_ASSISTANT's own "unconfigured -> default allow" branch
      // (DefaultCheckPermissionStrategy L287) isn't leaking through for a resource whose
      // permission record HAS been edited -- see class javadoc.
      withContextPrincipal(user1, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.AI_ASSISTANT, WILDCARD_RESOURCE)
               .expectDeny(user1, ResourceAction.ACCESS)
            .verify());
   }

   @Test
   void user1_freeFormSqlAccess_denied_editedRecordWithoutGrant() {
      withContextPrincipal(user1, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.FREE_FORM_SQL, WILDCARD_RESOURCE)
               .expectDeny(user1, ResourceAction.ACCESS)
            .verify());
   }

   @Test
   void siteAdmin_freeFormSqlAccess_allowed_bypassesGrantCheck() {
      // siteAdmin is granted nothing on FREE_FORM_SQL -- allowed purely by the isSiteAdmin
      // short-circuit, not by any grant. Representative for the definitional bypass; not
      // re-asserted per resource type (see class javadoc).
      withContextPrincipal(siteAdmin, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.FREE_FORM_SQL, WILDCARD_RESOURCE)
               .expectAllow(siteAdmin, ResourceAction.ACCESS)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S8-BOOKMARK-LANES
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void user0_bookmarkRead_allowed_viaRole() {
      withContextPrincipal(user0, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, BOOKMARK)
               .expectAllow(user0, ResourceAction.READ)
            .verify());
   }

   @Test
   void user1_bookmarkRead_denied_viaRole_notDefaultAllow() {
      // Negative control: VIEWSHEET_ACTION is in the default-allow list, so this also proves
      // markPermissionEdited() on BOOKMARK is doing its job (see class javadoc).
      withContextPrincipal(user1, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, BOOKMARK)
               .expectDeny(user1, ResourceAction.READ)
            .verify());
   }

   @Test
   void user0ViaGroup_bookmarkRead_allowed_userGroupResource() {
      withContextPrincipal(user0ViaGroup, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, BOOKMARK)
               .expectAllow(user0ViaGroup, ResourceAction.READ)
            .verify());
   }

   @Test
   void user0ViaGroupRole_bookmarkRead_allowed_userGroupRoleResource() {
      // user0ViaGroupRole holds no role directly -- it's a member of bookmarkRoleHolderGroup,
      // which itself holds role0 (the actual grantee), and bookmarkRoleHolderGroup has no grant
      // of its own -- see class javadoc on why this is a different group from group0.
      withContextPrincipal(user0ViaGroupRole, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, BOOKMARK)
               .expectAllow(user0ViaGroupRole, ResourceAction.READ)
            .verify());
   }

   @Test
   void user0ViaRoleHierarchy_bookmarkRead_allowed_parentRoleGrantPropagates() {
      withContextPrincipal(user0ViaRoleHierarchy, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, BOOKMARK)
               .expectAllow(user0ViaRoleHierarchy, ResourceAction.READ)
            .verify());
   }

   @Test
   void user0ViaGroupHierarchy_bookmarkRead_allowed_parentGroupGrantPropagates() {
      withContextPrincipal(user0ViaGroupHierarchy, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, BOOKMARK)
               .expectAllow(user0ViaGroupHierarchy, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S8-BOOKMARK-INDEPENDENCE
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void shareToAllOnlyUser_bookmarkFlags_independentlyChecked() {
      // shareToAllOnlyUser is granted ONLY ShareToAll. Allowed on ShareToAll but denied on
      // ShareBookmark/OpenBookmark proves the three VIEWSHEET_ACTION flags are checked
      // independently -- one being granted doesn't cascade to the others.
      withContextPrincipal(shareToAllOnlyUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.VIEWSHEET_ACTION, SHARE_TO_ALL)
               .expectAllow(shareToAllOnlyUser, ResourceAction.READ)
            .resource(ResourceType.VIEWSHEET_ACTION, SHARE_BOOKMARK)
               .expectDeny(shareToAllOnlyUser, ResourceAction.READ)
            .resource(ResourceType.VIEWSHEET_ACTION, OPEN_BOOKMARK)
               .expectDeny(shareToAllOnlyUser, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S8-CHART-TYPE-HIERARCHY
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void chartFolderGrantUser_areaStepAreaRead_allowed_climbsToCrossTypeParent() {
      // Rule1: "Area/Step Area" itself has no permission record -- the CHART_TYPE.getParent()
      // override resolves it to CHART_TYPE_FOLDER:"Area" (a different type), where the grant
      // actually lives.
      withContextPrincipal(chartFolderGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.CHART_TYPE, CHART_TYPE_AREA_STEP_AREA)
               .expectAllow(chartFolderGrantUser, ResourceAction.READ)
            .verify());
   }

   @Test
   void chartFolderGrantUser_lineStepLineRead_denied_siblingFolderNotGranted() {
      // Rule2/3: same user, but "Line/Step Line" climbs to CHART_TYPE_FOLDER:"Line" -- an
      // edited-and-empty sibling folder, not "Area" -- proving the grant doesn't leak across
      // siblings. "Line" must be edited (not merely untouched) or SecurityEngine's CHART_TYPE
      // parent-retry fallback would default-allow it regardless (see class javadoc).
      //
      // This fixture shape (folder explicitly edited-and-denied, child never touched) also happens
      // to be the doc's "category denied, subtype unconfigured" combination -- doubles as that
      // case's coverage too (see permission-matrix-actions.md's S8-CHART-TYPE cross-reference).
      withContextPrincipal(chartFolderGrantUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.CHART_TYPE, CHART_TYPE_LINE_STEP_LINE)
               .expectDeny(chartFolderGrantUser, ResourceAction.READ)
            .verify());
   }

   @Test
   void chartFolderExplicitEmptyUser_barIntervalRead_denied_ownEmptyRecordBlocksPromotion() {
      // Same "own explicit permission record blocks parent-folder promotion" rule as
      // PermissionMatrixResourcesS5Test's *_deleteOnExplicitChild_denied_ownExplicitPermission-
      // BlocksPromotion, here confirmed across CHART_TYPE's cross-type parent link: "Bar/Interval"
      // has its own edited-but-empty record, so the while-loop climb to folder "Bar" (which DOES
      // grant this user READ) never happens -- useParent is false before the loop is even entered.
      withContextPrincipal(chartFolderExplicitEmptyUser, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.CHART_TYPE, CHART_TYPE_BAR_INTERVAL)
               .expectDeny(chartFolderExplicitEmptyUser, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S8-CHART-TYPE-DEFAULT-ASYMMETRY
   // ════════════════════════════════════════════════════════════════════════════

   @Test
   void anyViewer_chartTypeFolderPointRead_allowed_unconfiguredDefaultsAllow() {
      // CHART_TYPE_FOLDER is on DefaultCheckPermissionStrategy's "unconfigured -> default allow"
      // list; "Point" has no permission record anywhere in this class.
      withContextPrincipal(user1, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.CHART_TYPE_FOLDER, CHART_FOLDER_POINT)
               .expectAllow(user1, ResourceAction.READ)
            .verify());
   }

   @Test
   void anyViewer_chartTypeOthersWaterfallRead_allowed_viaParentRetryFallback_notAnAsymmetry() {
      // CHART_TYPE (the leaf) is NOT on DefaultCheckPermissionStrategy's default-allow list, and
      // that class's own internal climb never re-consults the list after reaching
      // CHART_TYPE_FOLDER -- taken in isolation, that would deny. But it doesn't: SecurityEngine's
      // CHART_TYPE-specific fallback (see class javadoc) retries with a brand new top-level check
      // against parent folder "Others", which IS on the list and is itself fully unconfigured --
      // so that fresh check allows, and the leaf ends up allowed too. Same "completely
      // unconfigured" starting condition as the CHART_TYPE_FOLDER:"Point" test below, same
      // outcome -- contradicts the architecture doc's claimed asymmetry (see class javadoc).
      withContextPrincipal(user1, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(ResourceType.CHART_TYPE, CHART_TYPE_OTHERS_WATERFALL)
               .expectAllow(user1, ResourceAction.READ)
            .verify());
   }

   // ════════════════════════════════════════════════════════════════════════════
   // S8-SWEEP
   // ════════════════════════════════════════════════════════════════════════════

   @ParameterizedTest(name = "{0}:{1}:{2}")
   @MethodSource("sweepCases")
   void user0_sweepLeafDeclaredAction_allowed_viaRoleGrant(
      ResourceType type, String resource, ResourceAction action)
   {
      withContextPrincipal(user0, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(type, resource)
               .expectAllow(user0, action)
            .verify());
   }

   @ParameterizedTest(name = "{0}:{1}:{2}")
   @MethodSource("sweepCases")
   void user1_sweepLeafDeclaredAction_denied_editedRecordWithoutGrant(
      ResourceType type, String resource, ResourceAction action)
   {
      withContextPrincipal(user1, () ->
         PermissionMatrixVerifier.of(engine())
            .resource(type, resource)
               .expectDeny(user1, action)
            .verify());
   }

   private static Stream<Arguments> sweepCases() {
      return sweepLeaves.stream()
         .flatMap(leaf -> leaf.actions().stream()
            .map(action -> Arguments.of(leaf.type(), leaf.resource(), action)));
   }

   /**
    * Walks the real {@code ActionPermissionService.getActionTree()} production tree and returns
    * every leaf not already covered by another slice (see class javadoc for the exclusion list
    * and the two resource-string mistakes this approach caught that hand-typing would have
    * missed).
    */
   private static List<SweepLeaf> collectSweepLeaves() throws Exception {
      // ComponentAuthorizationService.loadComponents() deserializes view-components.json, a
      // resource generated by the web module's frontend build (web/target/generated-resources/
      // gulp/...) -- not present on core's own test classpath, so a real instance throws here.
      // Mocked instead of loaded: getEnterpriseManagerNode() calls componentService.
      // getComponentTree() unconditionally while building the tree, but its output only feeds the
      // EM_COMPONENT subtree, which this sweep excludes anyway (see class javadoc) -- an empty
      // ViewComponent is a faithful stand-in for what this test actually needs from it.
      ComponentAuthorizationService componentService = Mockito.mock(ComponentAuthorizationService.class);
      Mockito.when(componentService.getComponentTree())
         .thenReturn(ViewComponent.builder().name("root").label("root").build());

      // The no-arg constructor passes a null Cluster, which NPEs inside loadThemes() -> save()
      // (it acquires a distributed lock via cluster.getLock() whenever it falls back to the
      // bundled default XML, since that fallback path always re-saves). BaseTestConfiguration
      // registers a real Cluster bean (MockCluster) for this Spring context, so Cluster.
      // getInstance() resolves to a working instance instead of null.
      PortalThemesManager portalThemesManager =
         new PortalThemesManager(Cluster.getInstance(), DataSpace.getDataSpace());
      portalThemesManager.loadThemes();

      ActionPermissionService actionPermissionService = new ActionPermissionService(
         componentService, SecurityEngine.getSecurity(), portalThemesManager);

      // Any registered-or-not principal produces the complete tree in this module (see class
      // javadoc) -- this one is never passed through SecurityTestDataBuilder and never reaches
      // SecurityEngine.checkPermission(), only field inspection inside getActionTree() itself.
      Principal treeWalker = new SRPrincipal(new IdentityID("sweepTreeWalker", ORG_ID));

      SreeEnv.setProperty("login.loginAs", "on");
      ActionTreeNode root;

      try {
         root = actionPermissionService.getActionTree(treeWalker);
      }
      finally {
         SreeEnv.remove("login.loginAs");
      }

      List<SweepLeaf> allLeaves = new ArrayList<>();
      collectLeaves(root, allLeaves);

      List<SweepLeaf> filtered = new ArrayList<>();

      for(SweepLeaf leaf : allLeaves) {
         boolean excluded = SWEEP_EXCLUDED_TYPES.contains(leaf.type()) ||
            (leaf.type() == ResourceType.SCHEDULE_OPTION &&
             SCHEDULE_OPTION_TIME_RANGE.equals(leaf.resource()));

         if(!excluded) {
            filtered.add(leaf);
         }
      }

      return filtered;
   }

   private static void collectLeaves(ActionTreeNode node, List<SweepLeaf> out) {
      if(!node.folder()) {
         out.add(new SweepLeaf(node.type(), node.resource(), node.actions()));
         return;
      }

      for(ActionTreeNode child : node.children()) {
         collectLeaves(child, out);
      }
   }

   // ── helpers ────────────────────────────────────────────────────────────────

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
