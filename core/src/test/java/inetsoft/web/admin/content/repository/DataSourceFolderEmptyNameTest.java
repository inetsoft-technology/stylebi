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
package inetsoft.web.admin.content.repository;

import inetsoft.report.LibManagerProvider;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.security.*;
import inetsoft.sree.security.SecurityException;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.Catalog;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77926, a data source folder rename (portal Data page and EM) must refuse an empty or blank
 * name. The rename built the new path from the parent and the name, so a root level folder was
 * renamed to "" and its permission copied onto "". The EM controller then read the folder "" again
 * without a gate, and the root branch of getDataSourceFolder ignored its ADMIN check on "/", so the
 * caller got the root listing. The registry and the repository are the real ones.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceMoveTargetExistsTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderEmptyNameTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private RenameTransformHandler transforms;
   private ResourcePermissionService emPermissions;
   private DataSourceBrowserService browserService;
   private DatabaseDatasourcesService databaseService;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      transforms = mock(RenameTransformHandler.class);
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE, inv.<String>getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      SecurityProvider provider = mock(SecurityProvider.class);
      when(provider.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      RepositoryObjectService objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         provider, permissions, repository,
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         registry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), transforms, repletRegistries,
         mock(DashboardRegistryManager.class));
      browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), transforms);
      emPermissions = mock(ResourcePermissionService.class);
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         emPermissions, mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, transforms);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // POST /api/em/settings/content/repository/dataSourceFolder with an empty, blank or null name
   @Test
   void emRenameToEmptyOrBlankNameIsRefused() throws Exception {
      folder("enA");
      folder("enA/G");
      folder("enP");
      folder("enP/enS");
      folder("enP/enS/G");

      for(String name : new String[] { "", "   ", null }) {
         assertNameNotEmpty(assertThrows(MessageException.class,
            () -> databaseService.setDataSourceFolder("enA", folderModel(name), principal)));
         assertNameNotEmpty(assertThrows(MessageException.class,
            () -> databaseService.setDataSourceFolder("enP/enS", folderModel(name), principal)));
      }

      verifyNoInteractions(emPermissions);
      assertUnchanged("enA", "enP/enS", "", "   ", "enP/", "enP/   ");
   }

   // POST api/data/datasources/browser/folder with an empty or blank name
   @Test
   void portalRenameToEmptyOrBlankNameIsRefused() throws Exception {
      folder("pnA");
      folder("pnA/G");
      folder("pnP");
      folder("pnP/pnS");
      folder("pnP/pnS/G");

      for(String name : new String[] { "", "  ", null }) {
         assertNameNotEmpty(assertThrows(MessageException.class,
            () -> browserService.renameFolder("pnA", name, null, null, principal)));
         assertNameNotEmpty(assertThrows(MessageException.class,
            () -> browserService.renameFolder("pnP/pnS", name, null, null, principal)));
      }

      assertUnchanged("pnA", "pnP/pnS", "", "  ", "pnP/", "pnP/  ");
   }

   // a root save sends no name, its permissions are still saved
   @Test
   void emRootSaveWithoutNameStillWorks() throws Exception {
      DataSourceFolderSettingsModel model = DataSourceFolderSettingsModel.builder()
         .root(true)
         .build();

      assertEquals("/", databaseService.setDataSourceFolder("/", model, principal));
      verify(emPermissions).setResourcePermissions(
         eq("/"), eq(ResourceType.DATA_SOURCE_FOLDER), any(), any(), eq(principal));
      verifyNoInteractions(transforms);
   }

   // a folder created in EM may have a name the portal refuses, a save of its permissions sends
   // the same name again and must still work
   @Test
   void emPermissionSaveOnFolderWithColonStillWorks() throws Exception {
      folder("ec:b");
      folder("ec:b/G");

      assertEquals("ec:b", databaseService.setDataSourceFolder(
         "ec:b", folderModel("ec:b"), principal));
      verify(emPermissions).setResourcePermissions(
         eq("ec:b"), eq(ResourceType.DATA_SOURCE_FOLDER), any(), any(), eq(principal));
      verifyNoInteractions(transforms);
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder("ec:b"));
      assertNotNull(registry.getDataSourceFolder("ec:b/G"));
   }

   // GET /api/em/settings/content/repository/dataSourceFolder?path= (and the read after a save)
   // without ADMIN on the root
   @Test
   void rootReadWithoutRootAdminIsRefused() throws Exception {
      folder("rrSecret");
      when(security.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE_FOLDER), eq("/"), eq(ResourceAction.ADMIN)))
         .thenReturn(false);

      assertThrows(SecurityException.class,
                   () -> databaseService.getDataSourceFolder("", principal));
      assertThrows(SecurityException.class,
                   () -> databaseService.getDataSourceFolder("/", principal));
      verifyNoInteractions(emPermissions);
   }

   // the root listing with ADMIN on the root
   @Test
   void rootReadWithRootAdminStillWorks() throws Exception {
      folder("rwVisible");

      DataSourceFolderSettingsModel model = databaseService.getDataSourceFolder("/", principal);

      assertTrue(model.root());
      assertTrue(model.siblingFolders().contains("rwVisible"), model.siblingFolders().toString());
   }

   private void assertUnchanged(String folder, String subfolder, String... absent)
      throws Exception
   {
      verifyNoInteractions(transforms);
      verify(security, never()).setPermission(any(), anyString(), any());
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder(folder), folder);
      assertNotNull(registry.getDataSourceFolder(folder + "/G"), folder + "/G");
      assertNotNull(registry.getDataSourceFolder(subfolder), subfolder);
      assertNotNull(registry.getDataSourceFolder(subfolder + "/G"), subfolder + "/G");

      for(String path : absent) {
         assertNull(registry.getDataSourceFolder(path), "'" + path + "'");
      }
   }

   private static void assertNameNotEmpty(MessageException ex) {
      assertEquals(Catalog.getCatalog().getString("common.datasource.nameNotEmpty"),
                   ex.getMessage());
   }

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }

   private static DataSourceFolderSettingsModel folderModel(String name) {
      return DataSourceFolderSettingsModel.builder()
         .name(name)
         .root(false)
         .build();
   }
}
