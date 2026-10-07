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
import inetsoft.util.dep.XDataSourceAsset;
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
 * Bug #77959: the dependent assets of export/create come from the client, and a global dependent
 * has no owner to check. A delegated EM repository user (no admin role) listed a global worksheet
 * she may not read as a dependent, with nothing or only her own sheet selected, and got it in the
 * ZIP. Only the dependents that are dependencies of the checked selected assets may be written,
 * compared on the asset that is written (a "TableStyleAsset:[...]" detail description replaces
 * its path), while a real dependency of a selected sheet, sent back as get-dependent-assets lists
 * it, is still exported. A global dependent the caller may not READ is dropped as well, since she
 * can make it a real dependency with a sheet of her own (e.g. one she imports).
 *
 * Real SecurityEngine, AssetRepository, DeployService, ExportAssetService and export ZIP.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DeployServiceExportClientDependentTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DeployServiceExportClientDependentTest {
   private static final String ORG_A = "dxcorga";
   private static final IdentityID CAROL = new IdentityID("carol", ORG_A);
   private static final String SECRET_WS = "dxcSecretWs";
   private static final String SHARED_WS = "dxcSharedWs";
   private static final String CAROL_WS = "dxcCarolWs";
   private static final String CAROL_VS = "dxcCarolVs";
   private static final String CAROL_SECRET_VS = "dxcCarolSecretVs";
   private static final IdentityID ERIN = new IdentityID("erin", ORG_A);
   private static final String ERIN_SECRET_VS = "dxcErinSecretVs";
   private static final String SECRET_DS = "dxcSecretDs";

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
   private SRPrincipal carol;   // delegated EM repository user, no admin role
   private int exportCount;

   @BeforeAll
   void setupAll() throws Exception {
      builder = SecurityTestDataBuilder.create()
         .addOrg("dxcOrgA", ORG_A)
         .addOrgAdminRole("dxcOrgAdminA", ORG_A)
         .addUser("alice", ORG_A, "password")
         .addUser("carol", ORG_A, "password")
         .addUser("erin", ORG_A, "password")
         .addUserToRole("alice", "dxcOrgAdminA", ORG_A)
         .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                          "carol", Identity.USER, ORG_A)
         .grantPermission(ResourceType.EM_COMPONENT, "settings/content/repository",
                          ResourceAction.ACCESS, "carol", Identity.USER, ORG_A)
         // erin is a delegate like carol, who may read the secret worksheet and data source
         .grantPermission(ResourceType.EM, "*", ResourceAction.ACCESS,
                          "erin", Identity.USER, ORG_A)
         .grantPermission(ResourceType.EM_COMPONENT, "settings/content/repository",
                          ResourceAction.ACCESS, "erin", Identity.USER, ORG_A)
         .grantPermission(ResourceType.ASSET, SECRET_WS, ResourceAction.READ,
                          "erin", Identity.USER, ORG_A)
         .grantPermission(ResourceType.DATA_SOURCE, SECRET_DS, ResourceAction.READ,
                          "erin", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.DATA_SOURCE, SECRET_DS, ORG_A)
         // only alice may read the secret worksheet, carol may read the shared one
         .grantPermission(ResourceType.ASSET, SECRET_WS, ResourceAction.READ,
                          "alice", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.ASSET, SECRET_WS, ORG_A)
         .grantPermission(ResourceType.ASSET, SHARED_WS, ResourceAction.READ,
                          "carol", Identity.USER, ORG_A)
         .markPermissionEdited(ResourceType.ASSET, SHARED_WS, ORG_A);
      builder.setup();

      SRPrincipal alice = builder.principalOf("alice", ORG_A);
      AssetEntry secretWs = globalWorksheet(SECRET_WS);
      AssetEntry sharedWs = globalWorksheet(SHARED_WS);
      saveSheet(secretWs, new Worksheet(), alice);
      saveSheet(sharedWs, new Worksheet(), alice);
      saveSheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.WORKSHEET, CAROL_WS,
                               CAROL, ORG_A), new Worksheet(), new XPrincipal(CAROL));

      // carol's viewsheet on the shared global worksheet, a real dependency
      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(sharedWs);
      saveSheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET, CAROL_VS,
                               CAROL, ORG_A), vs, new XPrincipal(CAROL));

      // a viewsheet of carol's (e.g. imported) and one of erin's on the secret worksheet
      saveSheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                               CAROL_SECRET_VS, CAROL, ORG_A), basedOn(secretWs),
                new XPrincipal(CAROL));
      saveSheet(new AssetEntry(AssetRepository.USER_SCOPE, AssetEntry.Type.VIEWSHEET,
                               ERIN_SECRET_VS, ERIN, ORG_A), basedOn(secretWs),
                new XPrincipal(ERIN));
   }

   private static Viewsheet basedOn(AssetEntry base) {
      Viewsheet vs = new Viewsheet();
      vs.setBaseEntry(base);
      return vs;
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

   // the fixture must make carol a delegate that may reach export/create but not read the secret
   // worksheet, or the cases below pass without testing anything
   @Test
   void preconditions() throws Exception {
      SecurityEngine security = SecurityEngine.getSecurity();
      SRPrincipal alice = builder.principalOf("alice", ORG_A);

      assertTrue(as(carol, () -> security.checkPermission(
         carol, ResourceType.EM_COMPONENT, "settings/content/repository", ResourceAction.ACCESS)));
      assertFalse(as(carol, () -> security.checkPermission(
         carol, ResourceType.ASSET, SECRET_WS, ResourceAction.READ)));
      assertTrue(as(alice, () -> security.checkPermission(
         alice, ResourceType.ASSET, SECRET_WS, ResourceAction.READ)));
      assertFalse(OrganizationManager.getInstance().isSiteAdmin(carol));
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(carol));

      SRPrincipal erin = builder.principalOf("erin", ORG_A);
      assertTrue(as(erin, () -> security.checkPermission(
         erin, ResourceType.ASSET, SECRET_WS, ResourceAction.READ)));
      assertFalse(OrganizationManager.getInstance().isOrgAdmin(erin));
   }

   // R1: nothing selected, the global worksheet sent as a dependent
   @Test
   void globalDependent_withNothingSelected_isNotWritten() throws Exception {
      Export export = export(List.of(), List.of(required(SECRET_WS, null)));

      export.assertNotWritten(SECRET_WS);
   }

   @Test
   void globalDependent_withNothingSelected_isNotWrittenInSingleTenantMode() throws Exception {
      sutilStatic.when(SUtil::isMultiTenant).thenReturn(false);
      Export export = export(List.of(), List.of(required(SECRET_WS, null)));

      export.assertNotWritten(SECRET_WS);
   }

   // R2: carol's own worksheet selected, an unrelated global worksheet as a dependent
   @Test
   void unrelatedGlobalDependent_withOwnSheetSelected_isNotWritten() throws Exception {
      Export export = export(List.of(selected(RepositoryEntry.WORKSHEET, CAROL_WS, "WORKSHEET")),
                             List.of(required(SECRET_WS, null)));

      export.assertWritten(CAROL_WS);
      export.assertNotWritten(SECRET_WS);
   }

   // R5: the detail description redirects the written path to the secret worksheet, with nothing
   // selected and with the decoy named as a real dependency of the selected viewsheet
   @Test
   void redirectedDependent_isComparedOnTheWrittenAsset() throws Exception {
      Export noSelection = export(List.of(), List.of(redirected(CAROL_WS, SECRET_WS)));
      noSelection.assertNotWritten(SECRET_WS);

      Export realName = export(List.of(selected(RepositoryEntry.VIEWSHEET, CAROL_VS, "VIEWSHEET")),
                               List.of(redirected(SHARED_WS, SECRET_WS)));
      realName.assertWritten(CAROL_VS);
      realName.assertNotWritten(SECRET_WS);
   }

   // the export dialog sends back the dependencies get-dependent-assets lists, a global worksheet
   // the selected viewsheet is based on is still exported
   @Test
   void realDependencyOfSelectedSheet_isWritten() throws Exception {
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.VIEWSHEET, CAROL_VS, "VIEWSHEET"));
      List<RequiredAssetModel> dependencies = as(carol, () ->
         deployService.getDependentAssetsList(selected, carol)).requiredAssets();
      assertTrue(dependencies.stream().anyMatch(d -> SHARED_WS.equals(d.name())),
                 "precondition: " + dependencies);

      Export export = export(selected, dependencies);

      export.assertWritten(CAROL_VS);
      export.assertWritten(SHARED_WS);
      assertTrue(export.jarInfo.contains(SHARED_WS), export.jarInfo);
   }

   // carol owns a viewsheet based on the global worksheet she may not read, e.g. one she imported,
   // so the worksheet is a real dependency. The export dialog's own dependent list writes her
   // viewsheet but not the worksheet.
   @Test
   void unreadableRealDependency_ofOwnSheet_isNotWritten() throws Exception {
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.VIEWSHEET, CAROL_SECRET_VS, "VIEWSHEET", CAROL));
      List<RequiredAssetModel> dependencies = as(carol, () ->
         deployService.getDependentAssetsList(selected, carol)).requiredAssets();
      assertTrue(dependencies.stream().anyMatch(d -> SECRET_WS.equals(d.name())),
                 "precondition: " + dependencies);

      Export export = export(carol, selected, dependencies);

      export.assertWritten(CAROL_SECRET_VS);
      export.assertNotWritten(SECRET_WS);
   }

   // a delegate who may read the global worksheet still gets it with her own viewsheet
   @Test
   void readableRealDependency_ofOwnSheet_isWritten() throws Exception {
      SRPrincipal erin = builder.principalOf("erin", ORG_A);
      erin.setProperty("__internal__", "true");
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.VIEWSHEET, ERIN_SECRET_VS, "VIEWSHEET", ERIN));
      List<RequiredAssetModel> dependencies = as(erin, () ->
         deployService.getDependentAssetsList(selected, erin)).requiredAssets();

      Export export = export(erin, selected, dependencies);

      export.assertWritten(ERIN_SECRET_VS);
      export.assertWritten(SECRET_WS);
   }

   // an org admin exporting carol's viewsheet sees no change, the worksheet is written
   @Test
   void unreadableRealDependency_isWrittenForOrgAdmin() throws Exception {
      SRPrincipal alice = builder.principalOf("alice", ORG_A);
      alice.setProperty("__internal__", "true");
      List<SelectedAssetModel> selected =
         List.of(selected(RepositoryEntry.VIEWSHEET, CAROL_SECRET_VS, "VIEWSHEET", CAROL));
      List<RequiredAssetModel> dependencies = as(alice, () ->
         deployService.getDependentAssetsList(selected, alice)).requiredAssets();

      Export export = export(alice, selected, dependencies);

      export.assertWritten(CAROL_SECRET_VS);
      export.assertWritten(SECRET_WS);
   }

   // the read check follows the asset's security resource, a global data source is checked on
   // DATA_SOURCE. The registry does not resolve a data source in this harness, so the check is
   // tested directly.
   @Test
   void globalDataSourceDependent_needsRead() throws Exception {
      SRPrincipal erin = builder.principalOf("erin", ORG_A);
      SRPrincipal alice = builder.principalOf("alice", ORG_A);
      XDataSourceAsset ds = new XDataSourceAsset(SECRET_DS);

      assertFalse(as(carol, () -> ExportAssetService.isGlobalDependentReadable(ds, carol)));
      assertTrue(as(erin, () -> ExportAssetService.isGlobalDependentReadable(ds, erin)));
      assertTrue(as(alice, () -> ExportAssetService.isGlobalDependentReadable(ds, alice)));
   }

   private record Export(List<String> names, String jarInfo) {
      void assertWritten(String sheet) {
         assertTrue(names.stream().anyMatch(n -> n.contains("^" + sheet + "^")),
                    sheet + " not in " + names);
      }

      void assertNotWritten(String sheet) {
         assertTrue(names.stream().noneMatch(n -> n.contains("^" + sheet + "^")),
                    sheet + " written: " + names);
         assertFalse(jarInfo.contains(sheet), sheet + " listed: " + jarInfo);
      }
   }

   private Export export(List<SelectedAssetModel> selected, List<RequiredAssetModel> dependents)
      throws Exception
   {
      return export(carol, selected, dependents);
   }

   private Export export(SRPrincipal caller, List<SelectedAssetModel> selected,
                         List<RequiredAssetModel> dependents)
      throws Exception
   {
      ExportedAssetsModel model = ExportedAssetsModel.builder()
         .name("export77959")
         .selectedEntities(selected)
         .dependentAssets(dependents)
         .build();
      ExportJarProperties properties =
         as(caller, () -> exportService.createExport("id", model, caller));
      List<String> names = new ArrayList<>();
      String jarInfo = "";

      try(ZipFile zip = new ZipFile(new File(properties.zipFilePath()))) {
         for(ZipEntry entry : Collections.list(zip.entries())) {
            if("JarFileInfo.xml".equals(entry.getName())) {
               jarInfo = new String(zip.getInputStream(entry).readAllBytes(),
                                    StandardCharsets.UTF_8);
            }
            else if(entry.getSize() != 0) {
               names.add(entry.getName());
            }
         }
      }

      return new Export(names, jarInfo);
   }

   private static AssetEntry globalWorksheet(String path) {
      return new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET, path, null,
                            ORG_A);
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

   private static SelectedAssetModel selected(int type, String path, String typeName) {
      return selected(type, path, typeName, CAROL);
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

   private static RequiredAssetModel required(String name, IdentityID owner) {
      return RequiredAssetModel.builder()
         .name(name)
         .type("WORKSHEET")
         .user(owner)
         .lastModifiedTime(0)
         .build();
   }

   // a dependent named after one asset whose written path is another
   private static RequiredAssetModel redirected(String name, String written) {
      return RequiredAssetModel.builder()
         .name(name)
         .type("WORKSHEET")
         .detailDescription("TableStyleAsset:[" + written + "]")
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
