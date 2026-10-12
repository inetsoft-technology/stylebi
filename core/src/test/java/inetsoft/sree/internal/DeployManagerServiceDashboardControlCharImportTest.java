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
package inetsoft.sree.internal;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.ViewsheetEntry;
import inetsoft.sree.security.IdentityID;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.sree.web.dashboard.*;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.sync.UpdateDependencyHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.*;
import inetsoft.util.dep.DashboardAsset;
import inetsoft.util.dep.XAssetConfig;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77808, the deploy import step that moves the viewsheet of an imported dashboard (a
 * target folder import, or a site admin importing a dashboard of another organization) must
 * leave a dashboard file that DashboardAsset.parseContent reads back to the moved viewsheet.
 * The dependencies come from the real UpdateDependencyHandler and the file is rewritten by the
 * real DeployManagerService.transformAssetFile -> createRenameInfos -> DependencyTransformer
 * -> DashboardAssetDependencyTransformer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DeployManagerServiceDashboardControlCharImportTest.Config.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DeployManagerServiceDashboardControlCharImportTest {
   // target folder import: the viewsheet moves into the Imported folder of the same org
   @ParameterizedTest
   @ValueSource(strings = { "Census\u001fA", "Census\u0000A", "My[1f]x", "CensusA" })
   void targetFolderImport(String name) throws Exception {
      File file = writeDashboard(name, HOST_ORG);
      DashboardAsset asset = new DashboardAsset("Dash1", null);
      Set<AssetObject> deps =
         UpdateDependencyHandler.getAssetSupportTargetDependencies(file, asset, null);
      AssetEntry vs = (AssetEntry) deps.iterator().next();
      assertEquals(name, vs.getPath());

      Map<AssetObject, AssetObject> changeAssetMap = new HashMap<>();
      changeAssetMap.put(vs, new AssetEntry(AssetRepository.GLOBAL_SCOPE,
                                            AssetEntry.Type.VIEWSHEET, "Imported/" + name, null));
      transformAssetFile(asset, file, deps, changeAssetMap);

      ViewsheetEntry read = readBack(file, asset);
      assertEquals("Imported/" + name, read.getPath());
      assertEquals(AssetEntry.createAssetEntryForCurrentOrg(
         "1^128^__NULL__^Imported/" + name + "^" + HOST_ORG).toIdentifier(),
                   read.getIdentifier());
   }

   // site admin import of a dashboard from orgA: the dependency is moved to the current org,
   // built the way DeployManagerService.importAsset does for a site admin
   @ParameterizedTest
   @ValueSource(strings = { "Census\u001fA", "CensusA" })
   void crossOrgSiteAdminImport(String name) throws Exception {
      File file = writeDashboard(name, ORG_A);
      DashboardAsset asset = new DashboardAsset("Dash1", null);
      Set<AssetObject> deps =
         UpdateDependencyHandler.getAssetSupportTargetDependencies(file, asset, null);
      Map<AssetObject, AssetObject> changeAssetMap = new HashMap<>();
      Set<AssetObject> original = new HashSet<>();

      for(AssetObject dep : deps) {
         AssetEntry orig = (AssetEntry) dep.clone();
         orig.setOrgID(ORG_A);
         orig.toIdentifier(true);
         AssetEntry newOrgAsset = (AssetEntry) orig.clone();
         newOrgAsset.setOrgID(HOST_ORG);
         newOrgAsset.toIdentifier(true);
         changeAssetMap.put(orig, newOrgAsset);
         original.add(orig);
      }

      deps.addAll(original);
      transformAssetFile(asset, file, deps, changeAssetMap);

      ViewsheetEntry read = readBack(file, asset);
      assertEquals(name, read.getPath());
      assertEquals("1^128^__NULL__^" + name + "^" + HOST_ORG, read.getIdentifier());
   }

   private void transformAssetFile(DashboardAsset asset, File file, Set<AssetObject> deps,
                                   Map<AssetObject, AssetObject> changeAssetMap)
      throws Exception
   {
      DeployManagerService service = new DeployManagerService(
         mock(SecurityEngine.class), mock(DependencyHandler.class),
         mock(DataSourceRegistry.class), mock(DashboardRegistryManager.class),
         mock(LibManagerProvider.class), mock(DashboardManager.class), mock(XRepository.class),
         mock(FileSystemService.class), mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));
      Method m = DeployManagerService.class.getDeclaredMethod(
         "transformAssetFile", AssetObject.class, File.class, Set.class, Map.class);
      m.setAccessible(true);
      m.invoke(service, DeployHelper.getAssetObjectByAsset(asset), file, deps, changeAssetMap);
   }

   // the shape DashboardAsset.writeContent produces, identifier in orgID
   private File writeDashboard(String name, String orgID) throws IOException {
      File file = tempDir.resolve("dashboard" + (count++) + ".xml").toFile();
      ViewsheetEntry vs = new ViewsheetEntry(name);
      vs.setIdentifier("1^128^__NULL__^" + name + "^" + orgID);
      VSDashboard dashboard = new VSDashboard();
      dashboard.setViewsheet(vs);

      try(PrintWriter writer = new PrintWriter(new OutputStreamWriter(
         new FileOutputStream(file), StandardCharsets.UTF_8)))
      {
         writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
         writer.println("<dashboardAsset>");
         dashboard.writeXML(writer);
         writer.println("</dashboardAsset>");
      }

      return file;
   }

   // the real import reader, DashboardAsset.parseContent into the dashboard registry
   private ViewsheetEntry readBack(File file, DashboardAsset asset) throws Exception {
      assertTrue(Files.size(file.toPath()) > 0, "dashboard file is empty");
      DashboardRegistry registry = mock(DashboardRegistry.class);
      when(Config.registryManager.getRegistry(nullable(IdentityID.class))).thenReturn(registry);
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(true);

      try(InputStream input = new FileInputStream(file)) {
         asset.parseContent(input, config, true, true);
      }

      org.mockito.ArgumentCaptor<Dashboard> board =
         org.mockito.ArgumentCaptor.forClass(Dashboard.class);
      verify(registry).putDashboard(eq("Dash1"), board.capture());
      return ((VSDashboard) board.getValue()).getViewsheet();
   }

   @Configuration
   static class Config {
      static DashboardRegistryManager registryManager = mock(DashboardRegistryManager.class);

      @Bean
      DashboardRegistryManager dashboardRegistryManager() {
         return registryManager;
      }

      @Bean
      DashboardManager dashboardManager() {
         return mock(DashboardManager.class);
      }
   }

   private static final String HOST_ORG = "host-org";
   private static final String ORG_A = "orga";
   private static int count;

   @TempDir
   Path tempDir;
}
