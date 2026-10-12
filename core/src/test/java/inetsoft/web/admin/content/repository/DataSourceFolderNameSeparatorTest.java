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
import inetsoft.sree.RepositoryEntry;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.content.repository.model.NewRepositoryFolderRequest;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
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
 * Bug #77733, a data source folder rename (portal Data page and EM) or create must refuse a name
 * with a slash. The rename built "parent/name" and moved the folder under another parent without
 * the checks of the move (WRITE on the target, DELETE on the source, the target exists). The
 * registry and the repository are the real ones, and the security engine allows everything, so
 * that only the name check can refuse.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceMoveTargetExistsTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderNameSeparatorTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private SecurityEngine security;
   private RenameTransformHandler transforms;
   private RepositoryObjectService objectService;
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
      objectService = new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class),
         provider, permissions, repository,
         mock(RepositoryDashboardService.class), mock(DataModelFolderManagerService.class),
         registry, mock(LibManagerProvider.class), mock(RecycleBin.class),
         mock(DependencyHandler.class), transforms, repletRegistries,
         mock(DashboardRegistryManager.class));
      browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), transforms);
      databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, transforms);
      principal = new SRPrincipal(new IdentityID("admin", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // POST api/data/datasources/browser/folder: S renamed to "T/S" and to "Nope/S"
   @Test
   void portalRenameWithSlashIsRefused() throws Exception {
      folder("pS");
      folder("pS/G");
      folder("pT");

      assertInvalidName(assertThrows(MessageException.class,
         () -> browserService.renameFolder("pS", "pT/pS", null, null, principal)));
      assertInvalidName(assertThrows(MessageException.class,
         () -> browserService.renameFolder("pS", "pNope/pS", null, null, principal)));

      assertUnchanged("pS", "pT/pS", "pNope/pS", "pNope");
   }

   // POST /api/em/settings/content/repository/dataSourceFolder: the same with the EM settings
   @Test
   void emRenameWithSlashIsRefused() throws Exception {
      folder("eS");
      folder("eS/G");
      folder("eT");

      assertInvalidName(assertThrows(MessageException.class,
         () -> databaseService.setDataSourceFolder("eS", folderModel("eT/eS"), principal)));
      assertInvalidName(assertThrows(MessageException.class,
         () -> databaseService.setDataSourceFolder("eS", folderModel("eNope/eS"), principal)));

      assertUnchanged("eS", "eT/eS", "eNope/eS", "eNope");
   }

   // a plain rename still works in both
   @Test
   void renameWithoutSlashStillWorks() throws Exception {
      folder("wP");
      folder("wP/wS");
      folder("wP/wS/G");
      folder("wE");

      assertEquals("wP/wS2", browserService.renameFolder("wP/wS", "wS2", null, null, principal));
      assertEquals("wE2", databaseService.setDataSourceFolder("wE", folderModel("wE2"), principal));

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("wP/wS"));
      assertNotNull(registry.getDataSourceFolder("wP/wS2"));
      assertNotNull(registry.getDataSourceFolder("wP/wS2/G"));
      assertNull(registry.getDataSourceFolder("wE"));
      assertNotNull(registry.getDataSourceFolder("wE2"));
   }

   // POST /api/data/datasources/browser/folder/add: the permission is checked on the parent of
   // the request, a name with a slash would create the folder under another one
   @Test
   void portalCreateWithSlashIsRefused() throws Exception {
      folder("cT");
      DataSourceBrowserService browser = mock(DataSourceBrowserService.class);
      DataSourceController controller = new DataSourceController(null, browser, null, security,
                                                                 null, null);

      assertInvalidName(assertThrows(MessageException.class, () -> controller.addDatasourceFolder(
         ImmutableAddFolderRequest.builder().name("cT/X").parentPath("/").scope(0).build(),
         principal)));

      verifyNoInteractions(browser);
   }

   // POST /api/em/settings/content/repository/folder/add/ with a data source folder parent
   @Test
   void emCreateWithSlashIsRefused() throws Exception {
      folder("ecT");
      NewRepositoryFolderRequest request = new NewRepositoryFolderRequest();
      request.setParentFolder("/");
      request.setType(RepositoryEntry.DATA_SOURCE_FOLDER);
      request.setFolderName("ecT/X");

      assertInvalidName(assertThrows(MessageException.class,
         () -> objectService.addFolder(request, false, principal)));

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("ecT/X"));
   }

   private void assertUnchanged(String folder, String... absent) {
      verifyNoInteractions(transforms);
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder(folder), folder);
      assertNotNull(registry.getDataSourceFolder(folder + "/G"), folder + "/G");

      for(String path : absent) {
         assertNull(registry.getDataSourceFolder(path), path);
      }
   }

   private static void assertInvalidName(MessageException ex) {
      assertEquals(Tool.getInvalidFolderNameMessage(), ex.getMessage());
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
