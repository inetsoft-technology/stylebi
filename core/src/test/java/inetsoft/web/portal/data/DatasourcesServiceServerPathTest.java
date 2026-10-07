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
package inetsoft.web.portal.data;

import inetsoft.report.internal.license.LicenseManager;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.tabular.*;
import inetsoft.uql.util.Config;
import inetsoft.uql.util.XUtil;
import inetsoft.util.MessageException;
import inetsoft.util.credential.CredentialType;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #64331: a user who is not a site admin may only save a Text/Excel Directory data source
 * whose root folder is under an allowed root, or is the root folder it already has. The save
 * goes through DatasourcesService, the sink of the portal create and update.
 */
@Tag("core")
class DatasourcesServiceServerPathTest {
   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      licenseManager = mockStatic(LicenseManager.class);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManager = mock(OrganizationManager.class);
      when(orgManager.getCurrentOrgID()).thenReturn("orga");
      when(orgManager.getCurrentOrgID(any())).thenReturn("orga");
      orgManagerStatic.when(OrganizationManager::getInstance).thenReturn(orgManager);
      xutil = mockStatic(XUtil.class, CALLS_REAL_METHODS);
      xutil.when(() -> XUtil.isDataSourceNameValid(any(), anyString(), any())).thenReturn("Valid");

      Config config = mock(Config.class);
      when(config.getDataSourceClass(TYPE)).thenReturn(FolderTestDs.class.getName());
      doReturn(FolderTestDs.class).when(config).getClass(TYPE, FolderTestDs.class.getName());
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      repository = mock(XRepository.class);
      when(repository.getDataSourceNames()).thenReturn(new String[0]);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.checkPermission(any(), eq(ResourceType.CREATE_DATA_SOURCE),
                                          anyString(), eq(ResourceAction.ACCESS)))
         .thenReturn(true);
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq("files"),
                                          eq(ResourceAction.WRITE))).thenReturn(true);
      registry = mock(DataSourceRegistry.class);
      when(registry.getEntries(any(), any(AssetEntry.Type.class))).thenReturn(new AssetEntry[0]);
      when(registry.getDataSourceFullNames()).thenReturn(new String[0]);
      service = new DatasourcesService(repository, securityEngine,
                                       mock(DataSourceStatusService.class), registry, config);
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());

      allowed = Files.createDirectories(temp.resolve("allowed"));
      outside = Files.createDirectories(temp.resolve("outside"));
   }

   @AfterEach
   void tearDown() {
      xutil.close();
      configStatic.close();
      orgManagerStatic.close();
      sreeEnv.close();
      licenseManager.close();
   }

   @Test
   void createRefusesRootFolderWithoutAllowedRoots() {
      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(allowed.toFile()), false, principal));

      verifyNothingSaved();
   }

   @Test
   void createRefusesRootFolderOutsideAllowedRoots() {
      setAllowedRoots(allowed);

      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(outside.toFile()), false, principal));
      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(allowed.resolve("../outside").toFile()), false, principal));

      verifyNothingSaved();
   }

   @Test
   void createAcceptsRootFolderUnderAllowedRoot() throws Exception {
      setAllowedRoots(allowed);
      File folder = allowed.resolve("sales").toFile();

      service.createNewDataSource(definition(folder), false, principal);

      assertEquals(folder, savedFolder(null));
   }

   @Test
   void organizationRootsOverrideTheGlobalRoots() throws Exception {
      setAllowedRoots(allowed);
      sreeEnv.when(() -> SreeEnv.getProperty(
         "inetsoft.org.orga." + ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY, false, false))
         .thenReturn(outside.toString());

      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(allowed.toFile()), false, principal));
      service.createNewDataSource(definition(outside.toFile()), false, principal);

      assertEquals(outside.toFile(), savedFolder(null));
   }

   @Test
   void siteAdminMaySaveAnyRootFolder() throws Exception {
      when(orgManager.isSiteAdmin(principal)).thenReturn(true);

      service.createNewDataSource(definition(outside.toFile()), false, principal);

      assertEquals(outside.toFile(), savedFolder(null));
   }

   @Test
   void anyRootFolderIsAllowedWhenSecurityIsDisabled() throws Exception {
      when(securityEngine.isSecurityEnabled()).thenReturn(false);

      service.createNewDataSource(definition(outside.toFile()), false, principal);

      assertEquals(outside.toFile(), savedFolder(null));
   }

   @Test
   void orgAdminIsRestricted() {
      when(orgManager.isOrgAdmin(principal)).thenReturn(true);

      assertThrows(MessageException.class, () -> service.createNewDataSource(
         definition(outside.toFile()), false, principal));

      verifyNothingSaved();
   }

   @Test
   void updateKeepsUnchangedRootFolderOutsideAllowedRoots() throws Exception {
      storeSource(outside.toFile());

      service.updateDataSource("files", definition(outside.toFile()), principal);

      assertEquals(outside.toFile(), savedFolder("files"));
   }

   @Test
   void updateRefusesChangedRootFolderOutsideAllowedRoots() throws Exception {
      storeSource(outside.toFile());

      assertThrows(MessageException.class, () -> service.updateDataSource(
         "files", definition(outside.resolve("sub").toFile()), principal));

      verifyNothingSaved();
   }

   private void setAllowedRoots(Path root) {
      sreeEnv.when(() -> SreeEnv.getProperty(
         ServerFilePathPolicy.ALLOWED_ROOTS_PROPERTY, false, false)).thenReturn(root.toString());
   }

   private void storeSource(File folder) throws Exception {
      FolderTestDs ds = new FolderTestDs();
      ds.setName("files");
      ds.setFile(folder);
      when(repository.getDataSource("files")).thenReturn(ds);
   }

   private File savedFolder(String oldName) throws Exception {
      ArgumentCaptor<XDataSource> saved = ArgumentCaptor.forClass(XDataSource.class);

      if(oldName == null) {
         verify(repository).updateDataSource(saved.capture(), isNull(), eq(false));
      }
      else {
         verify(repository).updateDataSource(saved.capture(), eq(oldName), eq(false));
      }

      return ((FolderTestDs) saved.getValue()).getFile();
   }

   private void verifyNothingSaved() {
      try {
         verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
      }
      catch(Exception e) {
         throw new RuntimeException(e);
      }
   }

   private static DataSourceDefinition definition(File folder) {
      TabularView view = new TabularView();
      view.setValue("file");
      view.setVisible(true);
      TabularEditor editor = new TabularEditor();
      editor.setValue(folder);
      view.setEditor(editor);

      TabularView root = new TabularView();
      root.addTabularView(view);

      DataSourceDefinition def = new DataSourceDefinition();
      def.setType(TYPE);
      def.setName("files");
      def.setParentPath("");
      def.setTabularView(root);
      return def;
   }

   @TempDir
   Path temp;
   private Path allowed;
   private Path outside;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<LicenseManager> licenseManager;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<XUtil> xutil;
   private MockedStatic<Config> configStatic;
   private OrganizationManager orgManager;
   private XRepository repository;
   private SecurityEngine securityEngine;
   private DataSourceRegistry registry;
   private DatasourcesService service;
   private XPrincipal principal;

   private static final String TYPE = "ServerPathTest";

   @View(vertical = true, value = { @View1(value = "file") })
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

      private File file;
   }
}
