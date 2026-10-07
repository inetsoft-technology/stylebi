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
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.portal.PortalThemesManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.support.SecurityTestDataBuilder;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.util.Identity;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.util.*;
import inetsoft.web.admin.content.repository.*;
import inetsoft.web.admin.content.repository.model.*;
import inetsoft.web.service.BinaryTransferService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.File;
import java.nio.charset.StandardCharsets;
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
 * Bug #77959: more kinds of export/create dependents. A global viewsheet or data source the
 * caller may not read, sent as a dependent that is not a dependency of the selected assets, is not
 * written. Legitimate dependents are still written: a transitive one (her viewsheet, her worksheet,
 * a global worksheet she may read) in multi-tenant and single-tenant mode, and the dependency of a
 * selected global worksheet she may administer. With no dependents, only the selected sheet is
 * written.
 *
 * Real SecurityEngine, AssetRepository, DeployService, ExportAssetService and export ZIP.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DeployServiceExportDependentKindsTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeployServiceExportDependentKindsTest {
   private static final String ORG_A = "v59orga";
   private static final IdentityID CAROL = new IdentityID("carol", ORG_A);
   private static final String SECRET_WS = "v59SecretWs";
   private static final String SECRET_VS = "v59SecretVs";
   private static final String SECRET_DS = "v59SecretDs";
   private static final String SHARED_WS = "v59SharedWs";
   private static final String ADMIN_WS = "v59AdminWs";
   private static final String MID_WS = "v59CarolMidWs";
   private static final String TOP_VS = "v59CarolTopVs";

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

      @Bean
      DashboardManager dashboardManager() {
         return mock(DashboardManager.class, inv ->
            inv.getMethod().getReturnType() == String[].class ?
               new String[0] : RETURNS_DEFAULTS.answer(inv));
      }
   }

   @TempDir
   Path tempDir;

   private SecurityTestDataBuilder builder;
   private MockedStatic<SUtil> sutilStatic;
   private DeployService deployService;
   private ExportAssetService exportService;
   private SRPrincipal carol;
   private int exportCount;

   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("v59OrgA", ORG_A)
         .addOrgAdminRole("v59OrgAdminA", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUserToRole("alice", "v59OrgAdminA", ORG_A)
         .addUser("carol", ORG_A, "password")
         .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                          "carol", Identity.USER, ORG_A)
         .grantPermission(ResourceType.EM_COMPONENT, "settings/content/repository",
                          ResourceAction.ACCESS, "carol", Identity.USER, ORG_A)
         .grantPermission(ResourceType.ASSET, SECRET_WS, ResourceAction.READ,
                          "alice", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.ASSET, SECRET_WS, ORG_A)
         .grantPermission(ResourceType.REPORT, SECRET_VS, ResourceAction.READ,
                          "alice", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.REPORT, SECRET_VS, ORG_A)
         .grantPermission(ResourceType.DATA_SOURCE, SECRET_DS, ResourceAction.READ,
                          "alice", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.DATA_SOURCE, SECRET_DS, ORG_A)
         .grantPermission(ResourceType.ASSET, SHARED_WS, ResourceAction.READ,
                          "carol", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.ASSET, SHARED_WS, ORG_A)
         .grantPermission(ResourceType.ASSET, ADMIN_WS, ResourceAction.READ,
                          "carol", Identity.USER, ORG_A)
         .grantPermission(ResourceType.ASSET, ADMIN_WS, ResourceAction.ADMIN,
                          "carol", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.ASSET, ADMIN_WS, ORG_A);
      builder.setup();

      SRPrincipal alice = builder.principalOf("alice", ORG_A);
      AssetEntry secretWs = global(AssetEntry.Type.WORKSHEET, SECRET_WS);
      AssetEntry sharedWs = global(AssetEntry.Type.WORKSHEET, SHARED_WS);
      saveSheet(secretWs, wsWithTable(), alice);
      saveSheet(sharedWs, wsWithTable(), alice);
      saveSheet(global(AssetEntry.Type.VIEWSHEET, SECRET_VS), new Viewsheet(), alice);
      // global worksheet carol may administer, mirroring the shared one
      saveSheet(global(AssetEntry.Type.WORKSHEET, ADMIN_WS), mirrorOf(sharedWs), alice);

      // transitive: carol's vs -> carol's ws -> global shared ws (readable)
      AssetEntry midWs = new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET,
                                        MID_WS, CAROL, ORG_A);
      saveSheet(midWs, mirrorOf(sharedWs), new XPrincipal(CAROL));
      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(midWs);
      saveSheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, TOP_VS,
                               CAROL, ORG_A), vs, new XPrincipal(CAROL));
   }

   private static Worksheet wsWithTable() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(new EmbeddedTableAssembly(ws, "T1"));
      return ws;
   }

   private static Worksheet mirrorOf(AssetEntry base) {
      Worksheet ws = new Worksheet();
      EmbeddedTableAssembly t1 = new EmbeddedTableAssembly(ws, "T1");
      ws.addAssembly(new MirrorTableAssembly(ws, "M1", base, true, t1));
      return ws;
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

      carol = builder.principalOf("carol", ORG_A);
      carol.setProperty("__internal__", "true");

      ContentRepositoryTreeService treeService = mock(ContentRepositoryTreeService.class);
      when(treeService.getUnscopedPath(anyString()))
         .thenAnswer(inv -> SUtil.getUnscopedPath(inv.getArgument(0)));
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

   // the fixture must make carol unable to read the secret assets, or the cases pass vacuously
   @Test
   void preconditions() throws Exception {
      SecurityEngine security = SecurityEngine.getSecurity();
      assertFalse(as(carol, () -> security.checkPermission(
         carol, ResourceType.ASSET, SECRET_WS, ResourceAction.READ)));
      assertFalse(as(carol, () -> security.checkPermission(
         carol, ResourceType.REPORT, SECRET_VS, ResourceAction.READ)));
      assertFalse(as(carol, () -> security.checkPermission(
         carol, ResourceType.DATA_SOURCE, SECRET_DS, ResourceAction.READ)));
      assertTrue(as(carol, () -> security.checkPermission(
         carol, ResourceType.ASSET, SHARED_WS, ResourceAction.READ)));
      assertTrue(as(carol, () -> security.checkPermission(
         carol, ResourceType.ASSET, ADMIN_WS, ResourceAction.ADMIN)));
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(carol));
   }

   // a global viewsheet she may not read, not a dependency, nothing selected
   @Test
   void unrelatedGlobalViewsheetDependent_isNotWritten() throws Exception {
      Export export = export(List.of(), List.of(required(SECRET_VS, "VIEWSHEET")));
      export.assertNotWritten(SECRET_VS);
   }

   // a global data source she may not read, not a dependency, nothing selected. The registry does
   // not resolve it in this harness, the ZIP entry name (written before the fix) is checked
   @Test
   void unrelatedGlobalDataSourceDependent_isNotWritten() throws Exception {
      Export export = export(List.of(), List.of(required(SECRET_DS, "XDATASOURCE")));
      export.assertNotWritten(SECRET_DS);
   }

   // her viewsheet on her worksheet that mirrors a global worksheet she may read
   @Test
   void transitiveReadableGlobalDependency_isWritten() throws Exception {
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.VIEWSHEET, TOP_VS, "VIEWSHEET", CAROL));
      List<RequiredAssetModel> deps = as(carol, () ->
         deployService.getDependentAssetsList(selected, carol)).requiredAssets();
      assertTrue(deps.stream().anyMatch(d -> SHARED_WS.equals(d.name())), "precondition " + deps);
      assertTrue(deps.stream().anyMatch(d -> MID_WS.equals(d.name())), "precondition " + deps);

      Export export = export(selected, deps);
      export.assertWritten(TOP_VS);
      export.assertWritten(MID_WS);
      export.assertWritten(SHARED_WS);
   }

   // the same in single-tenant mode
   @Test
   void transitiveDependencies_areWrittenInSingleTenantMode() throws Exception {
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.VIEWSHEET, TOP_VS, "VIEWSHEET", CAROL));
      List<RequiredAssetModel> deps = as(carol, () ->
         deployService.getDependentAssetsList(selected, carol)).requiredAssets();

      Export export = export(selected, deps);
      export.assertWritten(TOP_VS);
      export.assertWritten(MID_WS);
      export.assertWritten(SHARED_WS);
   }

   // no dependents, only the selected viewsheet is written
   @Test
   void noDependents_onlySelectedIsWritten() throws Exception {
      Export export = export(List.of(selected(RepositoryEntry.VIEWSHEET, TOP_VS, "VIEWSHEET", CAROL)),
                             List.of());
      export.assertWritten(TOP_VS);
      export.assertNotWritten(SHARED_WS);
   }

   // a selected global worksheet she may administer is written with its dependency
   @Test
   void selectedAdministeredGlobal_isWrittenWithItsDependency() throws Exception {
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.WORKSHEET, ADMIN_WS, "WORKSHEET", null));
      List<RequiredAssetModel> deps = as(carol, () ->
         deployService.getDependentAssetsList(selected, carol)).requiredAssets();
      assertTrue(deps.stream().anyMatch(d -> SHARED_WS.equals(d.name())), "precondition " + deps);

      Export export = export(selected, deps);
      export.assertWritten(ADMIN_WS);
      export.assertWritten(SHARED_WS);
   }

   private record Export(List<String> names, List<String> allNames, String jarInfo) {
      void assertWritten(String sheet) {
         assertTrue(names.stream().anyMatch(n -> n.contains("^" + sheet + "^")),
                    sheet + " not in " + names);
      }

      void assertNotWritten(String name) {
         assertTrue(allNames.stream().noneMatch(n -> n.contains(name)),
                    name + " written: " + allNames);
         assertFalse(jarInfo.contains(name), name + " listed: " + jarInfo);
      }
   }

   private Export export(List<SelectedAssetModel> selected, List<RequiredAssetModel> dependents)
      throws Exception
   {
      ExportedAssetsModel model = ExportedAssetsModel.builder()
         .name("export77959")
         .selectedEntities(selected)
         .dependentAssets(dependents)
         .build();
      ExportJarProperties properties =
         as(carol, () -> exportService.createExport("id", model, carol));
      List<String> names = new ArrayList<>();
      List<String> all = new ArrayList<>();
      String jarInfo = "";

      try(ZipFile zip = new ZipFile(new File(properties.zipFilePath()))) {
         for(ZipEntry entry : Collections.list(zip.entries())) {
            if("JarFileInfo.xml".equals(entry.getName())) {
               jarInfo = new String(zip.getInputStream(entry).readAllBytes(),
                                    StandardCharsets.UTF_8);
            }
            else {
               all.add(entry.getName() + "(" + entry.getSize() + ")");

               if(entry.getSize() != 0) {
                  names.add(entry.getName());
               }
            }
         }
      }

      return new Export(names, all, jarInfo);
   }

   private static AssetEntry global(AssetEntry.Type type, String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, type, path, null, ORG_A);
   }

   private static void saveSheet(AssetEntry entry, AbstractSheet sheet, Principal user)
      throws Exception
   {
      OrganizationContextHolder.setCurrentOrgId(ORG_A);

      try {
         AssetUtil.getAssetRepository(false).setSheet(entry, sheet, user, true);
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

   private static RequiredAssetModel required(String name, String type) {
      return RequiredAssetModel.builder()
         .name(name)
         .type(type)
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
}
