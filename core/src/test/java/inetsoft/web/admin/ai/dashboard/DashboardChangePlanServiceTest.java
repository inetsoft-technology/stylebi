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
import inetsoft.web.admin.content.repository.RepositoryDashboardService;
import inetsoft.web.admin.content.repository.model.RepositoryDashboardSettingsModel;
import inetsoft.web.admin.content.repository.model.RepositoryFolderDashboardSettingsModel;
import inetsoft.web.admin.security.ResourcePermissionModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DashboardChangePlanService}, mirroring {@code
 * ViewsheetChangePlanServiceTest}'s own conventions: exercises the resolve/preview business logic
 * directly (mocking only {@link RepositoryDashboardService}/{@link DashboardRegistryManager}/
 * {@link DashboardManager}/{@link DashboardRegistry}), not through the controller.
 */
@Tag("core")
@ExtendWith(MockitoExtension.class)
class DashboardChangePlanServiceTest {
   @Mock private RepositoryDashboardService repositoryDashboardService;
   @Mock private DashboardRegistryManager dashboardRegistryManager;
   @Mock private DashboardManager dashboardManager;
   @Mock private DashboardRegistry globalRegistry;
   @Mock private DashboardRegistry ownerRegistry;
   @Mock private Principal user;
   @Mock private OrganizationManager orgManager;
   private DashboardChangePlanService service;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<inetsoft.util.Tool> tool;

   private static final IdentityID OWNER = new IdentityID("bob", "host-org");

   @BeforeEach void setUp() {
      service = new DashboardChangePlanService(
         repositoryDashboardService, dashboardRegistryManager, dashboardManager);

      orgManagerStatic = mockStatic(OrganizationManager.class, withSettings().lenient());
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      lenient().when(orgManager.getCurrentOrgID()).thenReturn("host-org");

      tool = mockStatic(inetsoft.util.Tool.class, withSettings().strictness(Strictness.LENIENT)
         .defaultAnswer(Answers.CALLS_REAL_METHODS));
      tool.when(() -> inetsoft.util.Tool.encryptPassword(anyString()))
         .thenAnswer(inv -> "TKN:" + inv.getArgument(0));

      lenient().when(dashboardRegistryManager.getRegistry()).thenReturn(globalRegistry);
      lenient().when(dashboardRegistryManager.getRegistry(eq(OWNER))).thenReturn(ownerRegistry);
      lenient().when(globalRegistry.getDashboardNames()).thenReturn(new String[0]);
      lenient().when(ownerRegistry.getDashboardNames()).thenReturn(new String[0]);
   }

   @AfterEach void tearDown() {
      orgManagerStatic.close();
      tool.close();
   }

   // -------------------------------------------------------------------------
   // basic request validation
   // -------------------------------------------------------------------------

   @Test void resolveThrowsOnBlankTask() throws Exception {
      DashboardChangePlanRequest req = request("   ", List.of(createDashboardChange("Dashboard1")));

      IllegalArgumentException ex =
         assertThrows(IllegalArgumentException.class, () -> service.resolve(req, user));
      assertTrue(ex.getMessage().contains("task"));
   }

   @Test void resolveThrowsOnEmptyChanges() throws Exception {
      assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of()), user));
   }

   @Test void resolveThrowsOnUnrecognizedUnitType() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setUnitType("viewsheet");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("unitType"));
   }

   @Test void resolveThrowsOnMissingUnitType() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setUnitType(null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("unitType"));
   }

   @Test void resolveThrowsOnMissingVerbForDashboardUnit() throws Exception {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setName("Dashboard1");
      change.setViewsheet(vsAssetId());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   @Test void resolveThrowsOnUnrecognizedVerbForDashboardUnit() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setVerb("rename");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("verb"));
   }

   // -------------------------------------------------------------------------
   // dashboard: create
   // -------------------------------------------------------------------------

   @Test void resolveCreateRequiresName() throws Exception {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_CREATE);
      change.setViewsheet(vsAssetId());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test void resolveCreateThrowsWhenOnameGiven() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setOname("Dashboard1__GLOBAL");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("oname"));
   }

   @Test void resolveCreateThrowsOnMalformedViewsheetIdentifier() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setViewsheet("not-a-valid-identifier");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("viewsheet"));
   }

   @Test void resolveCreateThrowsWhenDashboardAlreadyExists() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      DashboardChangeRequest change = createDashboardChange("Dashboard1");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   @Test void resolveCreateSucceedsAndIsClassifiedLowRisk() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("low", plan.changes().get(0).risk());
      assertFalse(plan.requiresAgentSignoff());
   }

   @Test void resolveCreateThrowsOnDuplicateEntry() throws Exception {
      DashboardChangeRequest change1 = createDashboardChange("Dashboard1");
      DashboardChangeRequest change2 = createDashboardChange("Dashboard1");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change1, change2)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   @Test void resolveCreateAcceptsAddAsAlias() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setVerb("add");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
   }

   @Test void resolveCreateThrowsWhenViewsheetMissing() throws Exception {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_CREATE);
      change.setName("Dashboard1");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("viewsheet"));
   }

   /** Owner-scoped dashboards are stored WITHOUT the {@code __GLOBAL} suffix (only global ones carry
    * it) -- confirms the create path resolves against the OWNER's own registry, not the global one,
    * and does not append the suffix. */
   @Test void resolveCreateOwnerScopedUsesOwnerRegistryAndNoGlobalSuffix() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1");
      change.setOwner("bob:host-org");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("host-org", plan.changes().get(0).orgId());
      verify(ownerRegistry).getDashboard(eq("Dashboard1"));
      verify(globalRegistry, never()).getDashboard(anyString());
   }

   /** {@code fixDashboardName} must be idempotent: a caller passing an already-suffixed name to
    * create must not end up with a double-suffixed registry key. */
   @Test void resolveCreateIsIdempotentWhenCallerAlreadyIncludesTheGlobalSuffix() throws Exception {
      DashboardChangeRequest change = createDashboardChange("Dashboard1__GLOBAL");

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
      verify(globalRegistry).getDashboard(eq("Dashboard1__GLOBAL"));
   }

   // -------------------------------------------------------------------------
   // dashboard: update
   // -------------------------------------------------------------------------

   @Test void resolveUpdateThrowsWhenDashboardNotFound() throws Exception {
      DashboardChangeRequest change = updateDashboardChange("Dashboard1__GLOBAL", null, "new desc");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("oname"));
   }

   @Test void resolveUpdateRequiresOname() throws Exception {
      DashboardChangeRequest change = updateDashboardChange(null, "NewName", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("oname"));
   }

   @Test void resolveUpdateThrowsWhenNoFieldGiven() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_UPDATE);
      change.setOname("Dashboard1__GLOBAL");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("update"));
   }

   @Test void resolveUpdateNonRenameIsClassifiedLowRisk() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      stubCurrentSettings("Dashboard1__GLOBAL", null, "Existing desc", vsAssetId(), true);
      DashboardChangeRequest change = updateDashboardChange("Dashboard1__GLOBAL", null, "new desc");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("low", plan.changes().get(0).risk());
      assertFalse(plan.requiresAgentSignoff());
   }

   @Test void resolveUpdateRenameIsClassifiedHighRisk() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      stubCurrentSettings("Dashboard1__GLOBAL", null, "Existing desc", vsAssetId(), true);
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_UPDATE);
      change.setOname("Dashboard1__GLOBAL");
      change.setName("Dashboard1 Renamed");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("high", plan.changes().get(0).risk());
      assertTrue(plan.requiresAgentSignoff());
   }

   @Test void resolveUpdateThrowsWhenRenamedToAnExistingName() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      when(globalRegistry.getDashboard(eq("Dashboard2__GLOBAL"))).thenReturn(mock(Dashboard.class));
      stubCurrentSettings("Dashboard1__GLOBAL", null, "Existing desc", vsAssetId(), true);
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD);
      change.setVerb(DashboardChangeRequest.VERB_UPDATE);
      change.setOname("Dashboard1__GLOBAL");
      change.setName("Dashboard2");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("already exists"));
   }

   /** Owner-scoped dashboards are stored WITHOUT the {@code __GLOBAL} suffix -- confirms update
    * resolves the CURRENT and TARGET names against the owner's own registry, not the global one. */
   @Test void resolveUpdateOwnerScopedUsesOwnerRegistryAndNoGlobalSuffix() throws Exception {
      when(ownerRegistry.getDashboard(eq("Dashboard1"))).thenReturn(mock(Dashboard.class));
      stubCurrentSettings("Dashboard1", OWNER, "Existing desc", vsAssetId(), true);
      DashboardChangeRequest change = updateDashboardChange("Dashboard1", null, "new desc");
      change.setOwner("bob:host-org");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("low", plan.changes().get(0).risk());
      assertEquals("host-org", plan.changes().get(0).orgId());
      verify(ownerRegistry).getDashboard(eq("Dashboard1"));
      verify(globalRegistry, never()).getDashboard(anyString());
   }

   // -------------------------------------------------------------------------
   // dashboard: delete
   // -------------------------------------------------------------------------

   @Test void resolveDeleteThrowsWhenNameFieldGiven() throws Exception {
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1__GLOBAL");
      change.setName("something");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("name"));
   }

   @Test void resolveDeleteThrowsWhenDashboardNotFound() throws Exception {
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1__GLOBAL");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("oname"));
   }

   @Test void resolveDeleteRequiresOname() throws Exception {
      DashboardChangeRequest change = deleteDashboardChange(null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("oname"));
   }

   /** Owner-scoped dashboards are stored WITHOUT the {@code __GLOBAL} suffix -- confirms delete
    * resolves against the owner's own registry, not the global one. */
   @Test void resolveDeleteOwnerScopedUsesOwnerRegistryAndNoGlobalSuffix() throws Exception {
      when(ownerRegistry.getDashboard(eq("Dashboard1"))).thenReturn(mock(Dashboard.class));
      stubCurrentSettings("Dashboard1", OWNER, "Existing desc", vsAssetId(), true);
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1");
      change.setOwner("bob:host-org");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("high", plan.changes().get(0).risk());
      assertEquals("host-org", plan.changes().get(0).orgId());
      verify(ownerRegistry).getDashboard(eq("Dashboard1"));
      verify(globalRegistry, never()).getDashboard(anyString());
   }

   @Test void resolveDeleteIsClassifiedHighRiskAndRequiresSignoff() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      stubCurrentSettings("Dashboard1__GLOBAL", null, "Existing desc", vsAssetId(), true);
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1__GLOBAL");

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("high", plan.changes().get(0).risk());
      assertTrue(plan.requiresAgentSignoff());
      assertNull(plan.changes().get(0).proposedValue());
   }

   /** Regression coverage for the review finding this fix addresses: the delete plan's
    * beforeProjection/hash must fold in the current permissions, so a permission change between
    * preview and apply is caught as drift the same way a description/viewsheet/enable change
    * already is -- otherwise a stale plan hash could be replayed against a dashboard whose ACL had
    * since changed. */
   @Test void deletePlanHashChangesWhenPermissionsChange() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      ResourcePermissionModel restricted = permissionModel("restricted to Finance");
      ResourcePermissionModel open = permissionModel("everyone");
      RepositoryDashboardSettingsModel before = dashboardSettings(
         "Dashboard1__GLOBAL", "Existing desc", vsAssetId(), true, restricted);
      RepositoryDashboardSettingsModel after = dashboardSettings(
         "Dashboard1__GLOBAL", "Existing desc", vsAssetId(), true, open);
      when(repositoryDashboardService.getSettings(eq("Dashboard1__GLOBAL"), isNull(), eq(user)))
         .thenReturn(before, after);
      DashboardChangeRequest change = deleteDashboardChange("Dashboard1__GLOBAL");

      var beforePlan = service.resolve(request("t", List.of(change)), user);
      var afterPlan = service.resolve(request("t", List.of(change)), user);

      assertNotEquals(beforePlan.planHash(), afterPlan.planHash());
   }

   // -------------------------------------------------------------------------
   // list / getSettings -- __GLOBAL suffix idempotency
   // -------------------------------------------------------------------------

   @Test void listReturnsSettingsForEveryRegisteredDashboard() throws Exception {
      when(globalRegistry.getDashboardNames())
         .thenReturn(new String[]{"Dashboard1__GLOBAL", "Dashboard2__GLOBAL"});
      RepositoryDashboardSettingsModel m1 = dashboardSettings("Dashboard1__GLOBAL", "d1", vsAssetId(), true, null);
      RepositoryDashboardSettingsModel m2 = dashboardSettings("Dashboard2__GLOBAL", "d2", vsAssetId(), true, null);
      when(repositoryDashboardService.getSettings(eq("Dashboard1__GLOBAL"), isNull(), eq(user))).thenReturn(m1);
      when(repositoryDashboardService.getSettings(eq("Dashboard2__GLOBAL"), isNull(), eq(user))).thenReturn(m2);

      List<RepositoryDashboardSettingsModel> result = service.list(null, user);

      assertEquals(List.of(m1, m2), result);
   }

   /** {@code getSettings} must resolve the SAME dashboard whether or not the caller already
    * includes the internal {@code __GLOBAL} suffix -- {@code fixDashboardName} is idempotent, and
    * the existence check must apply it exactly once either way. */
   @Test void getSettingsIsIdempotentWhetherOrNotCallerIncludesTheGlobalSuffix() throws Exception {
      when(globalRegistry.getDashboard(eq("Dashboard1__GLOBAL"))).thenReturn(mock(Dashboard.class));
      RepositoryDashboardSettingsModel model =
         dashboardSettings("Dashboard1__GLOBAL", "desc", vsAssetId(), true, null);
      when(repositoryDashboardService.getSettings(eq("Dashboard1"), isNull(), eq(user))).thenReturn(model);
      when(repositoryDashboardService.getSettings(eq("Dashboard1__GLOBAL"), isNull(), eq(user)))
         .thenReturn(model);

      assertEquals(model, service.getSettings("Dashboard1", null, user));
      assertEquals(model, service.getSettings("Dashboard1__GLOBAL", null, user));
   }

   @Test void getSettingsThrowsMissingResourceExceptionWhenNotFound() {
      assertThrows(inetsoft.web.security.auth.MissingResourceException.class,
         () -> service.getSettings("NoSuchDashboard", null, user));
   }

   // -------------------------------------------------------------------------
   // dashboard folder: reorder
   // -------------------------------------------------------------------------

   @Test void resolveFolderReorderThrowsWhenDashboardsEmpty() throws Exception {
      DashboardChangeRequest change = reorderFolderChange(null, List.of());

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("dashboards"));
   }

   @Test void resolveFolderReorderThrowsOnDuplicateName() throws Exception {
      DashboardChangeRequest change = reorderFolderChange(
         null, List.of("Dashboard1__GLOBAL", "Dashboard1__GLOBAL"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("duplicate"));
   }

   @Test void resolveFolderReorderThrowsOnUnknownName() throws Exception {
      when(globalRegistry.getDashboardNames()).thenReturn(new String[]{"Dashboard1__GLOBAL"});
      DashboardChangeRequest change = reorderFolderChange(null, List.of("NotReal__GLOBAL"));

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.resolve(request("task", List.of(change)), user));
      assertTrue(ex.getMessage().contains("not a known dashboard"));
   }

   @Test void resolveFolderReorderGlobalDelegatesToRepositoryDashboardService() throws Exception {
      when(globalRegistry.getDashboardNames())
         .thenReturn(new String[]{"Dashboard1__GLOBAL", "Dashboard2__GLOBAL"});
      RepositoryFolderDashboardSettingsModel current = RepositoryFolderDashboardSettingsModel.builder()
         .dashboards(List.of("Dashboard1__GLOBAL", "Dashboard2__GLOBAL"))
         .permissions(null)
         .build();
      when(repositoryDashboardService.getDashboardFolderSettings(eq(user))).thenReturn(current);
      DashboardChangeRequest change = reorderFolderChange(
         null, List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"));

      assertDoesNotThrow(() -> service.resolve(request("task", List.of(change)), user));
      verify(repositoryDashboardService).getDashboardFolderSettings(eq(user));
   }

   /** Owner-scoped path (see {@link DashboardChangePlanService}'s own class javadoc): reads the
    * target owner's OWN selected/ordered subset via {@link DashboardManager}, not the calling
    * principal's. */
   @Test void resolveFolderReorderOwnerScopedReadsTargetOwnersOwnSelection() throws Exception {
      when(globalRegistry.getDashboardNames())
         .thenReturn(new String[]{"Dashboard1__GLOBAL", "Dashboard2__GLOBAL"});
      when(dashboardManager.getDashboards(any()))
         .thenReturn(new String[]{"Dashboard1__GLOBAL", "Dashboard2__GLOBAL"});
      DashboardChangeRequest change = reorderFolderChange(
         "bob:host-org", List.of("Dashboard2__GLOBAL", "Dashboard1__GLOBAL"));

      var plan = service.resolve(request("task", List.of(change)), user);

      assertEquals("host-org", plan.changes().get(0).orgId());
      verify(repositoryDashboardService, never()).getDashboardFolderSettings(any());
      verify(dashboardManager).getDashboards(any());
   }

   @Test void getFolderOwnerScopedBuildsModelFromDashboardManager() throws Exception {
      when(dashboardManager.getDashboards(any())).thenReturn(new String[]{"Dashboard1__GLOBAL"});
      when(globalRegistry.getDashboardNames()).thenReturn(new String[]{"Dashboard1__GLOBAL"});

      RepositoryFolderDashboardSettingsModel folder = service.getFolder("bob:host-org", user);

      assertEquals(List.of("Dashboard1__GLOBAL"), folder.dashboards());
   }

   // -------------------------------------------------------------------------
   // helpers
   // -------------------------------------------------------------------------

   private static String vsAssetId() {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.VIEWSHEET,
                            "Examples/Census", null, "host-org").toIdentifier();
   }

   private void stubCurrentSettings(String registryName, IdentityID owner, String description,
                                    String viewsheet, boolean enable)
   {
      RepositoryDashboardSettingsModel model =
         dashboardSettings(registryName, description, viewsheet, enable, null);
      lenient().when(repositoryDashboardService.getSettings(anyString(), eq(owner), eq(user)))
         .thenReturn(model);
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

   private static DashboardChangePlanRequest request(String task, List<DashboardChangeRequest> changes) {
      DashboardChangePlanRequest req = new DashboardChangePlanRequest();
      req.setTask(task);
      req.setChanges(changes);
      return req;
   }

   private static DashboardChangeRequest createDashboardChange(String name) {
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

   private static DashboardChangeRequest reorderFolderChange(String owner, List<String> dashboards) {
      DashboardChangeRequest change = new DashboardChangeRequest();
      change.setUnitType(DashboardChangeRequest.UNIT_DASHBOARD_FOLDER);
      change.setVerb(DashboardChangeRequest.VERB_REORDER);
      change.setOwner(owner);
      change.setDashboards(dashboards);
      return change;
   }
}
