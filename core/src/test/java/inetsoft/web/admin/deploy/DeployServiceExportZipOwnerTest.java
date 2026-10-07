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
package inetsoft.web.admin.deploy;

import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.*;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.service.BinaryTransferService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Bug #77862: the repository export, with real stored assets and the real export ZIP. Another
 * organization's private dashboard, viewsheet and worksheet must not be written to the ZIP of an
 * organization admin or a plain user, whether they are sent as selected entities or only as
 * dependent assets, while a site admin across organizations, an organization admin on an own-org
 * user and an owner still get the stored content in the ZIP.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DeployServiceExportZipOwnerTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeployServiceExportZipOwnerTest {
   private static final String ORG_A = "dxzorga";
   private static final String ORG_B = "dxzorgb";
   private static final IdentityID CAROL = new IdentityID("carol", ORG_A);
   private static final IdentityID BOB = new IdentityID("bob", ORG_B);
   private static final String DASH_PATH = SUtil.MY_DASHBOARD + "/bobDash";

   @Configuration
   static class Config {
      @Bean
      @Primary
      PortalThemesManager portalThemesManager() {
         PortalThemesManager manager = mock(PortalThemesManager.class);
         when(manager.getCssEntries()).thenReturn(new HashMap<>());
         return manager;
      }

      @Bean
      DashboardRegistryManager dashboardRegistryManager(ApplicationEventPublisher eventPublisher,
                                                        SecurityEngine securityEngine,
                                                        DependencyHandler dependencyHandler,
                                                        DataSpace dataSpace)
      {
         return new DashboardRegistryManager(eventPublisher, securityEngine, dependencyHandler,
                                             dataSpace);
      }

      // only consulted for the identities a dashboard is selected for
      @Bean
      DashboardManager dashboardManager() {
         return mock(DashboardManager.class, inv ->
            inv.getMethod().getReturnType() == String[].class ?
               new String[0] : RETURNS_DEFAULTS.answer(inv));
      }
   }

   @Autowired
   private DashboardRegistryManager dashboardRegistryManager;

   @TempDir
   Path tempDir;

   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private DeployService deployService;
   private ExportAssetService exportService;
   private SRPrincipal alice;   // org admin of org A
   private SRPrincipal carol;   // plain user of org A
   private SRPrincipal sadm;    // site admin (org A)
   private int exportCount;

   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("dxzOrgA", ORG_A)
         .addOrg("dxzOrgB", ORG_B)
         .addOrgAdminRole("dxzOrgAdminA", ORG_A)
         .addSysAdminRole("dxzSiteAdmin", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addUser("sadm", ORG_A, "password")
         .addUser("bob", ORG_B, "password")
         .addUserToRole("alice", "dxzOrgAdminA", ORG_A)
         .addUserToRole("sadm", "dxzSiteAdmin", ORG_A);
      builder.setup();

      saveSheet(BOB, AssetEntry.Type.WORKSHEET, "bobWs", new Worksheet());
      saveSheet(BOB, AssetEntry.Type.VIEWSHEET, "bobVs", new Viewsheet());
      saveSheet(CAROL, AssetEntry.Type.WORKSHEET, "carolWs", new Worksheet());

      VSDashboard dashboard = new VSDashboard();
      AssetEntry vsEntry =
         new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, "bobVs", BOB, ORG_B);
      ViewsheetEntry viewsheetEntry = new ViewsheetEntry("bobVs", BOB);
      viewsheetEntry.setIdentifier(vsEntry.toIdentifier());
      dashboard.setViewsheet(viewsheetEntry);
      DashboardRegistry registry = dashboardRegistryManager.getRegistry(BOB);
      registry.addDashboard("bobDash", dashboard);
      registry.save();
   }

   @AfterAll
   void teardownAll() {
      if(builder != null) {
         builder.teardown();
      }
   }

   @BeforeEach
   void setUp() {
      sutilStatic = mockStatic(SUtil.class, CALLS_REAL_METHODS);
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(true);

      alice = loginPrincipalOf("alice", ORG_A);
      carol = loginPrincipalOf("carol", ORG_A);
      sadm = loginPrincipalOf("sadm", ORG_A);

      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
      // no registry entry, a viewsheet stays a viewsheet (not a snapshot)
      RepletRegistryService registryService = mock(RepletRegistryService.class);
      FileSystemService fileSystemService = mock(FileSystemService.class);
      when(fileSystemService.getCacheFile(anyString()))
         .thenAnswer(inv -> tempDir.resolve((exportCount++) + "-" + inv.getArgument(0)).toFile());
      deployService = new DeployService(treeService, SecurityEngine.getSecurity(), null, null,
                                        null, null, null, fileSystemService, registryService);
      exportService = new ExportAssetService(deployService, mock(BinaryTransferService.class),
                                             mock(Cluster.class), fileSystemService);
   }

   @AfterEach
   void tearDown() {
      ThreadContext.setContextPrincipal(null);
      sutilStatic.close();
   }

   // the reproduction: org A's admin selects org B's private dashboard and names org B's
   // worksheet as a dependent asset
   @Test
   void otherOrgDashboardWithDependentWorksheet_writesNothingOfOrgB() {
      for(SRPrincipal caller : List.of(alice, carol)) {
         assertNoOrgBExport(caller, List.of(selected(RepositoryEntry.DASHBOARD, DASH_PATH, "DASHBOARD", BOB)),
                            List.of(required("bobWs", "WORKSHEET", BOB)));
      }
   }

   @Test
   void otherOrgSheets_writeNothingOfOrgB() {
      for(SRPrincipal caller : List.of(alice, carol)) {
         assertNoOrgBExport(caller, List.of(selected(RepositoryEntry.WORKSHEET, "bobWs", "WORKSHEET", BOB)),
                            List.of());
         assertNoOrgBExport(caller, List.of(selected(RepositoryEntry.VIEWSHEET, "bobVs", "VIEWSHEET", BOB)),
                            List.of());
         assertNoOrgBExport(caller, List.of(),
                            List.of(required("bobVs", "VIEWSHEET", BOB)));
      }
   }

   // the owner's organization in another case, or the owner's name with no organization
   @Test
   void otherOrgOwnerVariants_writeNothingOfOrgB() {
      IdentityID upper = new IdentityID("bob", ORG_B.toUpperCase());
      IdentityID noOrg = new IdentityID("bob", null);

      for(IdentityID owner : List.of(upper, noOrg)) {
         assertNoOrgBExport(alice, List.of(selected(RepositoryEntry.WORKSHEET, "bobWs", "WORKSHEET", owner)),
                            List.of());
         assertNoOrgBExport(alice, List.of(selected(RepositoryEntry.DASHBOARD, DASH_PATH, "DASHBOARD", owner)),
                            List.of());
         assertNoOrgBExport(alice, List.of(), List.of(required("bobWs", "WORKSHEET", owner)));
      }
   }

   @Test
   void getDependentAssets_otherOrgDashboard_doesNotListOrgBViewsheet() {
      SelectedAssetModel dashboard =
         selected(RepositoryEntry.DASHBOARD, DASH_PATH, "DASHBOARD", BOB);

      for(SRPrincipal caller : List.of(alice, carol)) {
         try {
            RequiredAssetModelList list = as(caller, () ->
               deployService.getDependentAssetsList(List.of(dashboard), caller));
            assertTrue(list.requiredAssets().stream().noneMatch(a -> BOB.equals(a.user())),
                       caller.getName() + " lists " + list.requiredAssets());
         }
         catch(Exception e) {
            assertInstanceOf(MessageException.class, e);
         }
      }
   }

   @Test
   void siteAdmin_exportsOtherOrgDashboardAndSheets() throws Exception {
      List<String> names = export(sadm, List.of(
         selected(RepositoryEntry.DASHBOARD, DASH_PATH, "DASHBOARD", BOB),
         selected(RepositoryEntry.VIEWSHEET, "bobVs", "VIEWSHEET", BOB)),
         List.of(required("bobWs", "WORKSHEET", BOB)));

      assertContains(names, "DASHBOARD_", "bobDash^bob~;~" + ORG_B);
      assertContains(names, "VIEWSHEET_", "^bob~;~" + ORG_B + "^bobVs^" + ORG_B);
      assertContains(names, "WORKSHEET_", "^bob~;~" + ORG_B + "^bobWs^" + ORG_B);
   }

   @Test
   void siteAdmin_listsOtherOrgDashboardDependency() throws Exception {
      RequiredAssetModelList list = as(sadm, () -> deployService.getDependentAssetsList(
         List.of(selected(RepositoryEntry.DASHBOARD, DASH_PATH, "DASHBOARD", BOB)), sadm));

      assertTrue(list.requiredAssets().stream()
                    .anyMatch(a -> "bobVs".equals(a.name()) && BOB.equals(a.user())),
                 list.requiredAssets().toString());
   }

   @Test
   void orgAdminOnOwnOrgUser_andOwner_exportTheWorksheet() throws Exception {
      for(SRPrincipal caller : List.of(alice, carol)) {
         List<String> names = export(caller, List.of(
            selected(RepositoryEntry.WORKSHEET, "carolWs", "WORKSHEET", CAROL)),
            List.of(required("carolWs", "WORKSHEET", CAROL)));

         assertContains(names, "WORKSHEET_", "^carol~;~" + ORG_A + "^carolWs^" + ORG_A);
      }
   }

   private void assertNoOrgBExport(SRPrincipal caller, List<SelectedAssetModel> selected,
                                   List<RequiredAssetModel> dependents)
   {
      List<String> names;

      try {
         names = export(caller, selected, dependents);
      }
      catch(Exception e) {
         assertInstanceOf(MessageException.class, e, caller.getName());
         return;
      }

      assertTrue(names.stream().noneMatch(n -> n.toLowerCase().contains(ORG_B)),
                 caller.getName() + " exported " + names);
   }

   private List<String> export(SRPrincipal caller, List<SelectedAssetModel> selected,
                               List<RequiredAssetModel> dependents) throws Exception
   {
      ExportedAssetsModel model = ExportedAssetsModel.builder()
         .name("export77862")
         .selectedEntities(selected)
         .dependentAssets(dependents)
         .build();
      ExportJarProperties properties =
         as(caller, () -> exportService.createExport("id", model, caller));
      List<String> names = new ArrayList<>();

      try(ZipFile zip = new ZipFile(new File(properties.zipFilePath()))) {
         for(ZipEntry entry : Collections.list(zip.entries())) {
            if(entry.getSize() != 0) {
               names.add(entry.getName());
            }
         }
      }

      return names;
   }

   private static void assertContains(List<String> names, String prefix, String part) {
      assertTrue(names.stream().anyMatch(n -> n.startsWith(prefix) && n.contains(part)),
                 prefix + "*" + part + " in " + names);
   }

   private static void saveSheet(IdentityID owner, AssetEntry.Type type, String path,
                                 AbstractSheet sheet) throws Exception
   {
      AssetEntry entry = new AssetEntry(AssetRepository.USER_SCOPE, type, path, owner,
                                        owner.getOrgID());
      OrganizationContextHolder.setCurrentOrgId(owner.getOrgID());

      try {
         AssetUtil.getAssetRepository(false).setSheet(entry, sheet, new XPrincipal(owner), true);
      }
      finally {
         OrganizationContextHolder.setCurrentOrgId(null);
      }
   }

   private static SelectedAssetModel selected(int type, String path, String typeName,
                                              IdentityID owner)
   {
      return SelectedAssetModel.builder()
         .path(path)
         .type(type)
         .typeName(typeName)
         .typeLabel("")
         .user(owner)
         .build();
   }

   private static RequiredAssetModel required(String name, String type, IdentityID owner) {
      return RequiredAssetModel.builder()
         .name(name)
         .type(type)
         .user(owner)
         .lastModifiedTime(0)
         .build();
   }

   private static <T> T as(Principal principal, Callable<T> call) throws Exception {
      Principal old = ThreadContext.getContextPrincipal();
      ThreadContext.setContextPrincipal(principal);

      try {
         return call.call();
      }
      finally {
         ThreadContext.setContextPrincipal(old);
      }
   }

   private SRPrincipal loginPrincipalOf(String name, String orgID) {
      SRPrincipal principal = builder.principalOf(name, orgID);
      principal.setProperty("__internal__", "true");
      return principal;
   }
}
