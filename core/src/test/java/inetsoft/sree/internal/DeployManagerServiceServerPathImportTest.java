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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardManager;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.uql.*;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.EmbeddedTableStorage;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import inetsoft.util.credential.CredentialType;
import inetsoft.util.dep.XAssetConfig;
import inetsoft.util.dep.XDataSourceAsset;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.w3c.dom.Element;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #64331: importing a Text/Excel Directory data source is another way to save its root
 * folder. An importer who is not a site admin may only import a root folder under an allowed
 * root, or the root folder of the data source that the import overwrites. A refused entry is
 * reported in the failed list and not parsed.
 */
@Tag("core")
class DeployManagerServiceServerPathImportTest {
   @BeforeEach
   void setUp() throws Exception {
      Config config = mock(Config.class);
      when(config.getDataSourceClass(TYPE)).thenReturn(FolderTestDs.class.getName());
      doReturn(FolderTestDs.class).when(config).getClass(TYPE, FolderTestDs.class.getName());
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      TransformerManager transformer = mock(TransformerManager.class);
      when(transformer.transform(any())).thenAnswer(inv -> inv.getArgument(0));
      transformerStatic = mockStatic(TransformerManager.class);
      transformerStatic.when(() -> TransformerManager.getManager(anyString()))
         .thenReturn(transformer);

      sreeEnv = mockStatic(SreeEnv.class, CALLS_REAL_METHODS);
      sreeEnv.when(() -> SreeEnv.getProperty(anyString(), anyBoolean(), anyBoolean()))
         .thenReturn(null);

      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);

      registry = mock(DataSourceRegistry.class, CALLS_REAL_METHODS);
      doReturn(false).when(registry).containDatasource(anyString());
      doReturn(new String[0]).when(registry).getDataSourceFullNames();
      doReturn(null).when(registry).getDataSource(anyString());
      doReturn(false).when(registry).containObject(any());
      doNothing().when(registry).parseDomain(any());
      doNothing().when(registry).setExistQueryFolders(any());
      doNothing().when(registry).setDataSource(any(XDataSource.class), anyBoolean());
      doNothing().when(registry).updateDataSource(anyString(), any(), anyBoolean());
      registryStatic = mockStatic(DataSourceRegistry.class);
      registryStatic.when(DataSourceRegistry::getRegistry).thenReturn(registry);

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.checkPermission(any(Principal.class), eq(ResourceType.DATA_SOURCE),
                                          anyString(), eq(ResourceAction.ADMIN))).thenReturn(true);

      service = new DeployManagerService(
         securityEngine, mock(DependencyHandler.class), registry,
         mock(DashboardRegistryManager.class), mock(LibManagerProvider.class),
         mock(DashboardManager.class), mock(XRepository.class), mock(FileSystemService.class),
         mock(DataSpace.class), mock(EmbeddedTableStorage.class),
         mock(RepletRegistryManager.class));

      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());

      allowed = Files.createDirectories(tempDir.resolve("allowed"));
      outside = Files.createDirectories(tempDir.resolve("outside"));
   }

   @AfterEach
   void tearDown() {
      registryStatic.close();
      orgManagerStatic.close();
      sreeEnv.close();
      transformerStatic.close();
      configStatic.close();
   }

   @Test
   void rejectsRootFolderOutsideAllowedRoots() throws Exception {
      setAllowedRoots(allowed);

      List<String> failed = importEntry(entry(outside.toFile()), false);

      assertRejected(failed);
   }

   @Test
   void rejectsAnyRootFolderWithoutAllowedRoots() throws Exception {
      List<String> failed = importEntry(entry(allowed.toFile()), false);

      assertRejected(failed);
   }

   @Test
   void acceptsRootFolderUnderAllowedRoot() throws Exception {
      setAllowedRoots(allowed);

      List<String> failed = importEntry(entry(allowed.resolve("sales").toFile()), false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).setDataSource(any(XDataSource.class), eq(true));
   }

   @Test
   void acceptsUnchangedRootFolderOfOverwrittenSource() throws Exception {
      doReturn(true).when(registry).containDatasource("imported");
      doReturn(source(outside.toFile())).when(registry).getDataSource("imported");

      List<String> failed = importEntry(entry(outside.toFile()), true);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).updateDataSource(eq("imported"), any(), eq(true));
   }

   @Test
   void siteAdminMayImportAnyRootFolder() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      List<String> failed = importEntry(entry(outside.toFile()), false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).setDataSource(any(XDataSource.class), eq(true));
   }

   @Test
   void anyRootFolderIsAllowedWhenSecurityIsDisabled() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      List<String> failed = importEntry(entry(outside.toFile()), false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).setDataSource(any(XDataSource.class), eq(true));
   }

   @Test
   void rejectsDataSourceWithoutRootFolder() throws Exception {
      setAllowedRoots(allowed);

      List<String> failed = importEntry(entry(null), false);

      assertRejected(failed);
   }

   @Test
   void rejectsMissingRootFolderOverwritingSourceWithRootFolder() throws Exception {
      setAllowedRoots(allowed);
      doReturn(true).when(registry).containDatasource("imported");
      doReturn(source(allowed.toFile())).when(registry).getDataSource("imported");

      List<String> failed = importEntry(entry(null), true);

      assertRejected(failed);
   }

   @Test
   void acceptsMissingRootFolderOfOverwrittenSourceWithoutOne() throws Exception {
      doReturn(true).when(registry).containDatasource("imported");
      doReturn(source(null)).when(registry).getDataSource("imported");

      List<String> failed = importEntry(entry(null), true);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry).updateDataSource(eq("imported"), any(), eq(true));
   }

   @Test
   void existingSourceThatIsNotOverwrittenIsNotReported() throws Exception {
      doReturn(true).when(registry).containDatasource("imported");
      doReturn(source(allowed.toFile())).when(registry).getDataSource("imported");

      List<String> failed = importEntry(entry(outside.toFile()), false);

      assertTrue(failed.isEmpty(), failed.toString());
      verify(registry, never()).setDataSource(any(XDataSource.class), anyBoolean());
      verify(registry, never()).updateDataSource(anyString(), any(), anyBoolean());
   }

   @Test
   void typesWithoutServerPathAreNotParsed() throws Exception {
      Config config = Config.getConfig();
      when(config.getDataSourceClass(NO_PATH_TYPE)).thenReturn(NoPathTestDs.class.getName());
      doReturn(NoPathTestDs.class).when(config)
         .getClass(NO_PATH_TYPE, NoPathTestDs.class.getName());
      NoPathTestDs.parsed = 0;
      File file = tempDir.resolve("nopath.xml").toFile();
      Files.writeString(file.toPath(), "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><registry>" +
         "<datasource name=\"other\" type=\"" + NO_PATH_TYPE + "\"><ds_" + NO_PATH_TYPE +
         "/></datasource></registry>", StandardCharsets.UTF_8);

      assertTrue(service.isImportedServerPathsAllowed(file, principal));
      assertEquals(0, NoPathTestDs.parsed);
   }

   private void setAllowedRoots(Path root) {
      sreeEnv.when(() -> SreeEnv.getProperty(
         ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY, false, false)).thenReturn(root.toString());
   }

   private void assertRejected(List<String> failed) throws Exception {
      String expected = Catalog.getCatalog().getString(
         "em.import.file.failed.serverPathNotAllowed", XDataSourceAsset.XDATASOURCE + " imported");
      verify(registry, never()).setDataSource(any(XDataSource.class), anyBoolean());
      verify(registry, never()).updateDataSource(anyString(), any(), anyBoolean());
      assertEquals(List.of(expected), failed);
   }

   /**
    * Imports one XDATASOURCE entry through the private per-asset import step.
    */
   private List<String> importEntry(String content, boolean overwriting) throws Exception {
      File file = tempDir.resolve("f" + (++fileCount)).toFile();
      Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
      Map<String, String> names = new HashMap<>();
      names.put(file.getName(), XDataSourceAsset.XDATASOURCE + "_" +
         XDataSourceAsset.class.getName() + "^imported");
      DeploymentInfo info = mock(DeploymentInfo.class);
      when(info.getNames()).thenReturn(names);
      XAssetConfig config = new XAssetConfig();
      config.setOverwriting(overwriting);
      List<String> failed = new ArrayList<>();

      Method method = Arrays.stream(DeployManagerService.class.getDeclaredMethods())
         .filter(m -> m.getName().equals("importAsset") && m.getParameterCount() == 14)
         .findFirst().orElseThrow();
      method.setAccessible(true);
      method.invoke(service, file, new XDataSourceAsset("imported"), new ArrayList<>(), failed,
                    null, new ArrayList<>(), new ArrayList<>(), overwriting, null, info, false,
                    config, null, principal);
      return failed;
   }

   private static FolderTestDs source(File folder) {
      FolderTestDs ds = new FolderTestDs();
      ds.setName("imported");
      ds.setFile(folder);
      return ds;
   }

   private static String entry(File folder) {
      StringWriter out = new StringWriter();
      PrintWriter writer = new PrintWriter(out);
      source(folder).writeXML(writer);
      writer.flush();

      return "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><registry><Version>" +
         FileVersions.DATASOURCE + "</Version><datasource name=\"imported\" type=\"" + TYPE +
         "\">" + out + "</datasource></registry>";
   }

   static final String TYPE = "ServerPathImportTest";
   static final String NO_PATH_TYPE = "ServerPathImportNoPathTest";

   public static class NoPathTestDs extends TabularDataSource<NoPathTestDs> {
      public NoPathTestDs() {
         super(NO_PATH_TYPE, NoPathTestDs.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }

      @Override
      public String[] getDataSourceNames() {
         return new String[0];
      }

      @Property(label = "URL")
      public String getUrl() {
         return url;
      }

      public void setUrl(String url) {
         this.url = url;
      }

      @Override
      public void parseContents(Element root) throws Exception {
         parsed++;
         super.parseContents(root);
      }

      static int parsed;
      private String url;
   }

   public static class FolderTestDs extends TabularDataSource<FolderTestDs> {
      public FolderTestDs() {
         super(TYPE, FolderTestDs.class);
      }

      @Override
      protected CredentialType getCredentialType() {
         return null;
      }

      @Override
      public String[] getDataSourceNames() {
         return new String[0];
      }

      @Property(label = "Root Folder", required = true)
      public File getFile() {
         return file;
      }

      public void setFile(File file) {
         this.file = file;
      }

      // stored the way ServerFileDataSource stores its root folder
      @Override
      public void writeContents(PrintWriter writer) {
         super.writeContents(writer);

         if(file != null) {
            writer.println("<rootFile><![CDATA[" + file.getAbsolutePath() + "]]></rootFile>");
         }
      }

      @Override
      public void parseContents(Element root) throws Exception {
         super.parseContents(root);
         String path = Tool.getChildValueByTagName(root, "rootFile");

         if(path != null) {
            file = new File(path);
         }
      }

      private File file;
   }

   @TempDir
   Path tempDir;
   private Path allowed;
   private Path outside;
   private int fileCount;
   private DataSourceRegistry registry;
   private SecurityEngine securityEngine;
   private OrganizationManager orgManager;
   private DeployManagerService service;
   private XPrincipal principal;
   private MockedStatic<DataSourceRegistry> registryStatic;
   private MockedStatic<TransformerManager> transformerStatic;
   private MockedStatic<Config> configStatic;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
