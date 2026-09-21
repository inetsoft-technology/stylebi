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
package inetsoft.web.admin.ai.dashboard;

import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.OrganizationManager;
import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.web.dashboard.Dashboard;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistry;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.admin.ai.AdminBackupService;
import inetsoft.web.admin.ai.AdminChangesetApplyService;
import inetsoft.web.admin.content.repository.RepositoryDashboardService;
import inetsoft.web.admin.content.repository.model.NewRepositoryFolderRequest;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DashboardChangesetApplyService}, mirroring {@code
 * ViewsheetChangesetApplyServiceTest}'s own "tiny in-memory fake behind the mocked service"
 * convention. Uses an in-memory {@code dashboardStore} keyed by registry name, mutated by fakes for
 * {@link RepositoryDashboardService}'s addDashboard/setSettings/delete -- this exercises apply AND
 * rollback business logic directly, not through the controller.
 *
 * <p>{@link #deleteRollbackRestoresOriginalPermissionsNotGrantAll()} is the direct regression test
 * for the review finding this class's rollbackDashboardDelete fix addresses: a delete's rollback
 * (re-creating the dashboard via addDashboard, which always grants a fresh default permission) must
 * restore the PRE-delete permissions, not leave the dashboard open to the entire org.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class DashboardChangesetApplyServiceTest {
   @Mock private RepositoryDashboardService repositoryDashboardService;
   @Mock private DashboardRegistryManager dashboardRegistryManager;
   @Mock private DashboardManager dashboardManager;
   @Mock private DashboardRegistry globalRegistry;
   @Mock private DashboardRegistry ownerRegistry;
   @Mock private AdminBackupService backupService;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private DashboardChangePlanService planService;
   private DashboardChangesetApplyService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<inetsoft.util.Tool> tool;

   private static final IdentityID OWNER = new IdentityID("bob", "host-org");

   /** Keyed by registry name (e.g. {@code "Dashboard1__GLOBAL"} for global, {@code "Dashboard1"}
    * with no suffix for an owner-scoped one -- no collision risk between the two key spaces). */
   private final Map<String, RepositoryDashboardSettingsModel> dashboardStore = new LinkedHashMap<>();
   private List<String> globalFolderOrder = new ArrayList<>();
   /** The single owner's ({@link #OWNER}) own selected/ordered dashboard list, as {@link
    * DashboardManager#getDashboards}/{@code #setDashboards} would track it. */
   private List<String> ownerDashboardOrder = new ArrayList<>();
   private int placeholderCounter;

   @BeforeEach void setUp() throws Exception {
      planService = new DashboardChangePlanService(
         repositoryDashboardService, dashboardRegistryManager, dashboardManager);
      service = new DashboardChangesetApplyService(
         planService, repositoryDashboardService, dashboardManager, backupService);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      tool = mockStatic(inetsoft.util.Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> inetsoft.util.Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));
      tool.when(() -> inetsoft.util.Tool.decryptPassword(anyString()))
         .thenAnswer(inv -> {
            String s = inv.getArgument(0);

            if(!s.startsWith("TKN:")) {
               throw new IllegalArgumentException("not a token");
            }

            return s.substring(4);
         });

      lenient().when(backupService.backup(anyString())).thenReturn("backup-ref-1");
      lenient().when(dashboardRegistryManager.getRegistry()).thenReturn(globalRegistry);
      lenient().when(dashboardRegistryManager.getRegistry(eq(OWNER))).thenReturn(ownerRegistry);

      dashboardStore.clear();
      globalFolderOrder = new ArrayList<>();
      ownerDashboardOrder = new ArrayList<>();
      placeholderCounter = 1;

      lenient().when(globalRegistry.getDashboard(anyString())).thenAnswer(inv -> {
         String name = inv.getArgument(0);
         return dashboardStore.containsKey(name) ? mock(Dashboard.class) : null;
      });
      lenient().when(globalRegistry.getDashboardNames())
         .thenAnswer(inv -> dashboardStore.keySet().toArray(new String[0]));
      lenient().when(ownerRegistry.getDashboard(anyString())).thenAnswer(inv -> {
         String name = inv.getArgument(0);
         return dashboardStore.containsKey(name) ? mock(Dashboard.class) : null;
      });
      lenient().when(ownerRegistry.getDashboardNames())
         .thenAnswer(inv -> dashboardStore.keySet().toArray(new String[0]));

      lenient().when(dashboardManager.getDashboards(any()))
         .thenAnswer(inv -> ownerDashboardOrder.toArray(new String[0]));
      lenient().doAnswer(inv -> {
         String[] dashboards = inv.getArgument(1);
         ownerDashboardOrder = new ArrayList<>(List.of(dashboards));
         return null;
      }).when(dashboardManager).setDashboards(any(), any());

      lenient().when(repositoryDashboardService.getSettings(anyString(), any(), eq(user)))
         .thenAnswer(inv -> {
            String name = inv.getArgument(0);
            IdentityID owner = inv.getArgument(1);
            String key = DashboardChangePlanService.fixDashboardName(name, owner);
            RepositoryDashboardSettingsModel stored = dashboardStore.get(key);

            if(stored == null) {
               throw new NullPointerException("no such dashboard: " + key);
            }

            return stored;
         });

      lenient().doAnswer(inv -> {
         NewRepositoryFolderRequest req = inv.getArgument(0);
         String placeholder =
            DashboardChangePlanService.fixDashboardName("Placeholder" + placeholderCounter++, req.getOwner());
         req.setPath(placeholder);
         dashboardStore.put(placeholder, dashboardSettings(placeholder, null, null, true, null));
         return null;
      }).when(repositoryDashboardService).addDashboard(any(), eq(user));

      lenient().when(repositoryDashboardService.setSettings(anyString(), any(), any(), eq(user)))
         .thenAnswer(inv -> {
            RepositoryDashboardSettingsModel model = inv.getArgument(1);
            IdentityID owner = inv.getArgument(2);
            String onameKey = DashboardChangePlanService.fixDashboardName(model.oname(), owner);
            String newKey = DashboardChangePlanService.fixDashboardName(model.name(), owner);
            dashboardStore.remove(onameKey);
            RepositoryDashboardSettingsModel stored = dashboardSettings(
               newKey, model.description(), model.viewsheet(), model.enable(), model.permissions());
            dashboardStore.put(newKey, stored);
            return stored;
         });

      lenient().doAnswer(inv -> {
         String path = inv.getArgument(0);
         dashboardStore.remove(path);
         return null;
      }).when(repositoryDashboardService).delete(anyString(), any());

      lenient().when(repositoryDashboardService.getDashboardFolderSettings(eq(user)))
         .thenAnswer(inv -> RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(new ArrayList<>(globalFolderOrder)).permissions(null).build());
      lenient().when(repositoryDashboardService.setDashboardFolderSettings(any(), eq(user)))
         .thenAnswer(inv -> {
            RepositoryFolderDashboardSettingsModel model = inv.getArgument(0);
            globalFolderOrder = new ArrayList<>(model.dashboards());
            return model;
         });
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      tool.close();
   }

   // -------------------------------------------------------------------------
   // dashboard create
   // -------------------------------------------------------------------------

   @Test void appliesADashboardCreateAndReportsApplied() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");

      var result = service.apply(applyRequest("create", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"));
   }

   @Test void dashboardCreateRollbackRemovesTheCreatedDashboard() throws Exception {
      seedDashboard("OtherDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardChangeRequest create = createDashboardChange("Dashboard1");
      DashboardChangeRequest doomedReorder = reorderFolderChange(List.of("OtherDashboard__GLOBAL"));
      // No-op write: verification (after.equals(requested)) fails without throwing, forcing rollback
      // -- same "silently no-ops rather than throws" technique ViewsheetChangesetApplyServiceTest
      // itself uses to force a verified:false outcome.
      when(repositoryDashboardService.setDashboardFolderSettings(any(), eq(user)))
         .thenAnswer(inv -> RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(globalFolderOrder).permissions(null).build());

      var result = service.apply(applyRequest("mixed", create, doomedReorder), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertFalse(dashboardStore.containsKey("Dashboard1__GLOBAL"),
                 "rollback must remove the dashboard create just applied");
   }

   /**
    * Direct regression test for bug 76856: a SECOND item's own precondition check throwing
    * BEFORE its mutating call ever runs must not, by itself, force {@code STATUS_ROLLBACK_FAILED}
    * once every actually-applied item (here, the first item's create) rolls back cleanly. The
    * concurrent out-of-band creation of the second item's target name is simulated as a side
    * effect of the first item's own successful {@code setSettings} call, landing strictly between
    * {@code apply()}'s top-level plan re-resolve and the second item's own turn in the loop.
    */
   @Test void preMutationThrowOnSecondItemDoesNotForceRollbackFailed() throws Exception {
      DashboardChangeRequest create1 = createDashboardChange("OtherDashboard");
      DashboardChangeRequest create2 = createDashboardChange("Dashboard1");

      when(repositoryDashboardService.setSettings(anyString(), any(), any(), eq(user)))
         .thenAnswer(inv -> {
            RepositoryDashboardSettingsModel model = inv.getArgument(1);
            IdentityID owner = inv.getArgument(2);
            String onameKey = DashboardChangePlanService.fixDashboardName(model.oname(), owner);
            String newKey = DashboardChangePlanService.fixDashboardName(model.name(), owner);
            dashboardStore.remove(onameKey);
            RepositoryDashboardSettingsModel stored = dashboardSettings(
               newKey, model.description(), model.viewsheet(), model.enable(), model.permissions());
            dashboardStore.put(newKey, stored);

            if("OtherDashboard".equals(model.name())) {
               dashboardStore.put("Dashboard1__GLOBAL",
                  dashboardSettings("Dashboard1__GLOBAL", "concurrent", vsAssetId(), true, null));
            }

            return stored;
         });

      var result = service.apply(applyRequest("mixed", create1, create2), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertFalse(dashboardStore.containsKey("OtherDashboard__GLOBAL"),
                 "the first item's create must have been rolled back");
      verify(repositoryDashboardService, times(1)).addDashboard(any(), eq(user));
   }

   /** Owner-scoped dashboards are stored WITHOUT the {@code __GLOBAL} suffix. */
   @Test void appliesAnOwnerScopedDashboardCreateAndReportsApplied() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setOwner("bob:host-org");

      var result = service.apply(applyRequest("create", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertTrue(dashboardStore.containsKey("Dashboard1"));
      assertFalse(dashboardStore.containsKey("Dashboard1__GLOBAL"));
   }

   // -------------------------------------------------------------------------
   // dashboard update
   // -------------------------------------------------------------------------

   @Test void appliesADashboardUpdateAndReportsApplied() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "old desc", vsAssetId(), true, null);
      DashboardChangeRequest change = updateDashboardChange("Dashboard1__GLOBAL", null, "new desc");

      var result = service.apply(applyRequest("update", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("new desc", dashboardStore.get("Dashboard1__GLOBAL").description());
   }

   @Test void dashboardUpdateRollbackRestoresThePriorDescription() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "old desc", vsAssetId(), true, null);
      seedDashboard("OtherDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardChangeRequest update = updateDashboardChange("Dashboard1__GLOBAL", null, "new desc");
      DashboardChangeRequest doomedReorder = reorderFolderChange(List.of("OtherDashboard__GLOBAL"));
      when(repositoryDashboardService.setDashboardFolderSettings(any(), eq(user)))
         .thenAnswer(inv -> RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(globalFolderOrder).permissions(null).build());

      var result = service.apply(applyRequest("mixed", update, doomedReorder), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals("old desc", dashboardStore.get("Dashboard1__GLOBAL").description());
   }

   /** Direct regression coverage for the rollback identity-tracking case reviewers asked for:
    * rollback of a RENAME (not just a description update) must restore the ORIGINAL registry key
    * via the undo's captured {@code beforeOname}, not merely revert field values under the (now
    * wrong) renamed key. */
   @Test void dashboardRenameRollbackRestoresTheOriginalNameViaBeforeOname() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("OtherDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardChangeRequest rename = updateDashboardChange("Dashboard1__GLOBAL", "Dashboard1 Renamed", null);
      DashboardChangeRequest doomedReorder = reorderFolderChange(List.of("OtherDashboard__GLOBAL"));
      when(repositoryDashboardService.setDashboardFolderSettings(any(), eq(user)))
         .thenAnswer(inv -> RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(globalFolderOrder).permissions(null).build());

      var result = service.apply(applyRequest("mixed", rename, doomedReorder), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"),
                "rollback must restore the original registry key via beforeOname");
      assertFalse(dashboardStore.containsKey("Dashboard1 Renamed__GLOBAL"),
                 "the renamed key must no longer exist after rollback");
      assertEquals("desc", dashboardStore.get("Dashboard1__GLOBAL").description());
   }

   /** {@code oname} must resolve the same dashboard whether or not the caller includes the
    * internal {@code __GLOBAL} suffix. */
   @Test void updateAcceptsOnameWithOrWithoutTheGlobalSuffix() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "old desc", vsAssetId(), true, null);
      DashboardChangeRequest change = updateDashboardChange("Dashboard1", null, "new desc");

      var result = service.apply(applyRequest("update", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("new desc", dashboardStore.get("Dashboard1__GLOBAL").description());
   }

   /** Owner-scoped dashboards are stored WITHOUT the {@code __GLOBAL} suffix. */
   @Test void appliesAnOwnerScopedDashboardUpdateAndReportsApplied() throws Exception {
      seedDashboard("Dashboard1", "old desc", vsAssetId(), true, null);
      DashboardChangeRequest change = updateDashboardChange("Dashboard1", null, "new desc");
      change.setOwner("bob:host-org");

      var result = service.apply(applyRequest("update", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals("new desc", dashboardStore.get("Dashboard1").description());
   }

   // -------------------------------------------------------------------------
   // dashboard delete
   // -------------------------------------------------------------------------

   @Test void appliesADashboardDeleteAndReportsApplied() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1__GLOBAL");

      var result = service.apply(applyRequest("delete", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(dashboardStore.containsKey("Dashboard1__GLOBAL"));
   }

   /** Owner-scoped dashboards are stored WITHOUT the {@code __GLOBAL} suffix. */
   @Test void appliesAnOwnerScopedDashboardDeleteAndReportsApplied() throws Exception {
      seedDashboard("Dashboard1", "desc", vsAssetId(), true, null);
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1");
      change.setOwner("bob:host-org");

      var result = service.apply(applyRequest("delete", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertFalse(dashboardStore.containsKey("Dashboard1"));
   }

   /**
    * Direct regression test for the review finding (2026-09): {@code rollbackDashboardDelete}
    * re-creates the dashboard via {@code addDashboard} (which always grants a fresh default
    * permission), then MUST restore the captured pre-delete {@code permissions} rather than pass
    * {@code null} -- otherwise a dashboard previously restricted to one group silently reopens to
    * the entire org whenever an unrelated LATER change in the same all-or-nothing apply batch fails
    * and forces a rollback.
    */
   @Test void deleteRollbackRestoresOriginalPermissionsNotGrantAll() throws Exception {
      ResourcePermissionModel restricted = permissionModel("restricted to Finance group");
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, restricted);
      seedDashboard("OtherDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardChangeRequest delete = deleteDashboardChange("Dashboard1__GLOBAL");
      DashboardChangeRequest doomedReorder = reorderFolderChange(List.of("OtherDashboard__GLOBAL"));
      // Forces verification failure (not a throw) for the SECOND entry, so the whole plan rolls
      // back and the delete's own undo (re-create) actually runs.
      when(repositoryDashboardService.setDashboardFolderSettings(any(), eq(user)))
         .thenAnswer(inv -> RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(globalFolderOrder).permissions(null).build());

      var result = service.apply(applyRequest("mixed", delete, doomedReorder), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"),
                "rollback must re-create the deleted dashboard");
      RepositoryDashboardSettingsModel recreated = dashboardStore.get("Dashboard1__GLOBAL");
      assertEquals("desc", recreated.description());
      assertEquals(vsAssetId(), recreated.viewsheet());
      assertTrue(recreated.enable());
      assertEquals(restricted, recreated.permissions(),
                  "rollback must restore the PRE-delete permissions, not leave the re-created " +
                  "dashboard on addDashboard's own default grant-all permission");
   }

   /** Same regression, owner-scoped: even though an owner-scoped dashboard has no permission table
    * of its own on the wire today (see {@code RepositoryDashboardService#getSettings}'s own {@code
    * owner != null -> tableModel = null} branch), the rollback code path must not special-case
    * owner scope -- it always threads whatever {@code current.permissions()} returned back into
    * the re-created model. */
   @Test void ownerScopedDeleteRollbackRestoresOriginalPermissionsField() throws Exception {
      ResourcePermissionModel restricted = permissionModel("restricted to Finance group");
      seedDashboard("Dashboard1", "desc", vsAssetId(), true, restricted);
      seedDashboard("OtherDashboard", "desc", vsAssetId(), true, null);
      ownerDashboardOrder = new ArrayList<>(List.of("OtherDashboard"));
      DashboardChangeRequest delete = deleteDashboardChange("Dashboard1");
      delete.setOwner("bob:host-org");
      DashboardChangeRequest doomedDelete = deleteDashboardChange("OtherDashboard");
      doomedDelete.setOwner("bob:host-org");
      // No-op delete for the SECOND entry: verification fails without throwing, forcing rollback of
      // the first (already-applied) delete.
      doAnswer(inv -> null).when(repositoryDashboardService).delete(eq("OtherDashboard"), eq(OWNER));

      var result = service.apply(applyRequest("mixed", delete, doomedDelete), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(dashboardStore.containsKey("Dashboard1"));
      assertEquals(restricted, dashboardStore.get("Dashboard1").permissions());
   }

   /** Unlike the viewsheet area's own non-compensable delete, a dashboard delete IS undoable (see
    * the class javadoc): when another change in the same all-or-nothing batch fails, the delete
    * itself gets rolled back (re-created) along with everything else, rather than being left
    * permanently applied. */
   @Test void deleteIsRolledBackLikeAnyOtherVerbWhenAnotherChangeInTheSamePlanFails() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("OtherDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardChangeRequest delete = deleteDashboardChange("Dashboard1__GLOBAL");
      // Reuse: the "delete" itself is HIGH risk and drives requiresAgentSignoff -- a doomed reorder
      // (LOW risk) as the second entry forces the same "verified:false without throw" rollback.
      DashboardChangeRequest doomedReorder = reorderFolderChange(List.of("OtherDashboard__GLOBAL"));
      when(repositoryDashboardService.setDashboardFolderSettings(any(), eq(user)))
         .thenAnswer(inv -> RepositoryFolderDashboardSettingsModel.builder()
            .dashboards(globalFolderOrder).permissions(null).build());

      var result = service.apply(applyRequest("mixed", delete, doomedReorder), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"),
                "the delete must have been re-created as part of rollback");
   }

   // -------------------------------------------------------------------------
   // gates
   // -------------------------------------------------------------------------

   @Test void applyThrowsOnStalePlanHash() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardApplyRequest req = applyRequest("delete", deleteDashboardChange("Dashboard1__GLOBAL"));
      req.setPlanHash("stale-hash");

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"),
                "delete must not have been attempted before the hash gate");
   }

   /**
    * A REAL 409, not a manually-set garbage string: {@code apply} re-resolves fresh internally, so
    * a genuine change to the underlying registry state between {@code applyRequest}'s own preview
    * call and this test's {@code service.apply} call produces a plan hash that no longer matches the
    * one embedded in {@code req} -- the same drift-detection path a concurrent EM edit would trigger
    * live.
    */
   @Test void applyThrowsRealPlanHashMismatchWhenRegistryStateChangedSincePreview() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "old desc", vsAssetId(), true, null);
      DashboardChangeRequest change = updateDashboardChange("Dashboard1__GLOBAL", null, "new desc");
      DashboardApplyRequest req = applyRequest("update", change);
      // Simulates a concurrent change to the SAME dashboard between preview and this apply call.
      seedDashboard("Dashboard1__GLOBAL", "concurrently changed desc", vsAssetId(), true, null);

      assertThrows(AdminChangesetApplyService.PlanHashMismatchException.class,
                  () -> service.apply(req, user));
      assertEquals("concurrently changed desc", dashboardStore.get("Dashboard1__GLOBAL").description(),
                  "apply must not have been attempted before the hash gate");
   }

   @Test void applyThrowsTaskTokenMismatchOnMissingToken() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardApplyRequest req = applyRequest("delete", deleteDashboardChange("Dashboard1__GLOBAL"));
      req.setTaskToken(null);

      AdminChangesetApplyService.TaskTokenMismatchException ex = assertThrows(
         AdminChangesetApplyService.TaskTokenMismatchException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().startsWith("taskToken:"));
      assertNotNull(ex.current());
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"));
   }

   /** Distinct from a missing token: a token that decrypts fine but was issued for a DIFFERENT
    * plan's hash (e.g. a caller previewed one change, then applied against a stale/foreign token). */
   @Test void applyThrowsTaskTokenMismatchOnTokenIssuedForADifferentPlan() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("Dashboard2__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardApplyRequest req = applyRequest("delete", deleteDashboardChange("Dashboard1__GLOBAL"));
      DashboardApplyRequest otherPlan = applyRequest("delete", deleteDashboardChange("Dashboard2__GLOBAL"));
      req.setTaskToken(otherPlan.getTaskToken());

      assertThrows(AdminChangesetApplyService.TaskTokenMismatchException.class,
                  () -> service.apply(req, user));
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"),
                "delete must not have been attempted before the token gate");
   }

   @Test void applyThrowsWhenReviewOutcomeMissingForADeleteVerb() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      DashboardApplyRequest req = applyRequest("delete", deleteDashboardChange("Dashboard1__GLOBAL"));
      req.setReviewOutcome(null);

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.apply(req, user));
      assertTrue(ex.getMessage().contains("reviewOutcome"));
      assertTrue(dashboardStore.containsKey("Dashboard1__GLOBAL"));
   }

   @Test void applyDoesNotRequireReviewOutcomeForACreateOnlyPlan() throws Exception {
      DashboardApplyRequest req = applyRequest("create", createDashboardChange("Dashboard1"));
      req.setReviewOutcome(null);

      var result = service.apply(req, user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
   }

   // -------------------------------------------------------------------------
   // dashboard folder reorder
   // -------------------------------------------------------------------------

   @Test void appliesAFolderReorderAndReportsApplied() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("Dashboard2__GLOBAL", "desc", vsAssetId(), true, null);
      globalFolderOrder = new ArrayList<>(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"));
      DashboardChangeRequest change =
         reorderFolderChange(List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"));

      var result = service.apply(applyRequest("reorder", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"), globalFolderOrder);
   }

   @Test void folderReorderRollbackRestoresThePriorOrder() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("Dashboard2__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("DoomedDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      globalFolderOrder = new ArrayList<>(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"));
      DashboardChangeRequest reorder =
         reorderFolderChange(List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"));
      DashboardChangeRequest doomedDelete = deleteDashboardChange("DoomedDashboard__GLOBAL");
      // No-op delete: verification (registry.getDashboard == null) fails without throwing, forcing a
      // rollback of the already-applied reorder -- same technique as the dashboard-unit rollback
      // tests above, just with the roles reversed (reorder is the one being undone here).
      doAnswer(inv -> null).when(repositoryDashboardService)
         .delete(eq("DoomedDashboard__GLOBAL"), isNull());

      var result = service.apply(applyRequest("mixed", reorder, doomedDelete), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"), globalFolderOrder);
   }

   /** Owner-scoped round trip: {@code readFolderOrder}/{@code writeFolderOrder} go through {@link
    * DashboardManager#getDashboards}/{@code #setDashboards} for the target owner's OWN selection,
    * not {@code RepositoryDashboardService#getDashboardFolderSettings}/
    * {@code #setDashboardFolderSettings} (which are principal-bound and never called here). */
   @Test void appliesAnOwnerScopedFolderReorderAndReportsApplied() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("Dashboard2__GLOBAL", "desc", vsAssetId(), true, null);
      ownerDashboardOrder = new ArrayList<>(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"));
      DashboardChangeRequest change =
         reorderFolderChange(List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"));
      change.setOwner("bob:host-org");

      var result = service.apply(applyRequest("reorder", change), user);

      assertEquals(AdminChangesetApplyService.STATUS_APPLIED, result.status());
      assertEquals(List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"), ownerDashboardOrder);
      verify(repositoryDashboardService, never()).setDashboardFolderSettings(any(), any());
   }

   @Test void ownerScopedFolderReorderRollbackRestoresThePriorOrder() throws Exception {
      seedDashboard("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("Dashboard2__GLOBAL", "desc", vsAssetId(), true, null);
      seedDashboard("DoomedDashboard__GLOBAL", "desc", vsAssetId(), true, null);
      ownerDashboardOrder = new ArrayList<>(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"));
      DashboardChangeRequest reorder =
         reorderFolderChange(List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"));
      reorder.setOwner("bob:host-org");
      DashboardChangeRequest doomedDelete = deleteDashboardChange("DoomedDashboard__GLOBAL");
      doAnswer(inv -> null).when(repositoryDashboardService)
         .delete(eq("DoomedDashboard__GLOBAL"), isNull());

      var result = service.apply(applyRequest("mixed", reorder, doomedDelete), user);

      assertEquals(AdminChangesetApplyService.STATUS_ROLLED_BACK, result.status());
      assertEquals(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"), ownerDashboardOrder);
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static String vsAssetId() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "Examples/Census", null, "host-org").toIdentifier();
   }

   private void seedDashboard(String registryName, String description, String viewsheet,
                              boolean enable, ResourcePermissionModel permissions)
   {
      dashboardStore.put(registryName,
         dashboardSettings(registryName, description, viewsheet, enable, permissions));
   }

   private static RepositoryDashboardSettingsModel dashboardSettings(
      String name, String description, String viewsheet, boolean enable,
      ResourcePermissionModel permissions)
   {
      return RepositoryDashboardSettingsModel.builder()
         .name(name)
         .oname(name)
         .description(description)
         .viewsheet(viewsheet)
         .enable(enable)
         .visible(true)
         .permissions(permissions)
         .build();
   }

   private static ResourcePermissionModel permissionModel(String label) {
      return ResourcePermissionModel.builder()
         .displayActions(EnumSet.of(ResourceAction.ACCESS))
         .securityEnabled(true)
         .requiresBoth(false)
         .derivePermissionLabel(label)
         .grantReadToAllVisible(false)
         .build();
   }

   private DashboardApplyRequest applyRequest(String task, DashboardChangeRequest... changes)
      throws Exception
   {
      DashboardChangePlanRequest probe = planRequest(task, List.of(changes));
      var resolved = planService.resolve(probe, user);

      DashboardApplyRequest req = new DashboardApplyRequest();
      req.setTask(task);
      req.setChanges(List.of(changes));
      req.setPlanHash(resolved.planHash());
      req.setTaskToken(resolved.taskToken());
      req.setReviewOutcome("approved");
      return req;
   }

   private static DashboardChangePlanRequest planRequest(String task, List<DashboardChangeRequest> changes) {
      DashboardChangePlanRequest req = new DashboardChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private DashboardChangeRequest createDashboardChange(String name) {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_CREATE);
      change.setName(name);
      change.setViewsheet(vsAssetId());
      return change;
   }

   private static DashboardChangeRequest updateDashboardChange(String oname, String name,
                                                                String description)
   {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_UPDATE);
      change.setOname(oname);
      change.setName(name);
      change.setDescription(description);
      return change;
   }

   private static DashboardChangeRequest deleteDashboardChange(String oname) {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_DELETE);
      change.setOname(oname);
      return change;
   }

   private static DashboardChangeRequest reorderFolderChange(List<String> dashboards) {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD_FOLDER);
      change.setVerb(DashboardChangeRequest.VERB_REORDER);
      change.setDashboards(dashboards);
      return change;
   }
}
