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
import inetsoft.sree.AnalyticRepository;
import inetsoft.sree.RepletRegistry;
import inetsoft.sree.RepletRegistryManager;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.FileSystemService;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.authz.ComponentAuthorizationService;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.content.repository.model.ScheduleTaskFolderSettingsModel;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.SecuredAspect;
import inetsoft.web.session.IgniteSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77926, the requests of the bug driven through the real controllers and the real
 * SecuredAspect into the real services, registry and repository. Only the permission oracle is
 * mocked: one path-aware grant map shared by the endpoint gate and the services. The caller is a
 * delegated user who holds ADMIN on one root level folder but not on the data source root.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceMoveTargetExistsTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderEmptyNameGateTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private final Map<String, Set<ResourceAction>> grants = new HashMap<>();
   private SecurityEngine security;
   private final Permission folderPermission = new Permission();
   private RenameTransformHandler transforms;
   private ResourcePermissionService emPermissions;
   private MockedStatic<SUtil> sUtilMock;
   private RepositoryDataSourcesController emProxy;
   private DataSourceController portalProxy;
   private RepositoryScheduleTaskController scheduleProxy;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      grants.clear();
      security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any()))
         .thenAnswer(inv -> granted(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
      when(security.getPermission(eq(ResourceType.DATA_SOURCE_FOLDER), anyString()))
         .thenReturn(folderPermission);
      AnalyticRepository analyticRepository = mock(AnalyticRepository.class);
      when(analyticRepository.checkPermission(any(), any(), anyString(), any()))
         .thenAnswer(inv -> granted(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3)));
      sUtilMock = mockStatic(SUtil.class, Answers.CALLS_REAL_METHODS);
      sUtilMock.when(SUtil::getRepletRepository).thenReturn(analyticRepository);

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
      DataSourceBrowserService browserService = new DataSourceBrowserService(
         security, objectService, repository, mock(DataSourceService.class), registry,
         mock(Config.class), transforms);
      emPermissions = mock(ResourcePermissionService.class);
      DatabaseDatasourcesService databaseService = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), repository,
         emPermissions, mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, transforms);

      SecuredAspect aspect = new SecuredAspect(registry, mock(ComponentAuthorizationService.class));
      emProxy = proxy(new RepositoryDataSourcesController(
         databaseService, emPermissions, mock(Config.class), repository), aspect);
      portalProxy = proxy(new DataSourceController(
         mock(DatasourcesService.class), browserService, mock(DatabaseDatasourcesService.class),
         security, mock(DataSourceStatusService.class), mock(FileSystemService.class)), aspect);
      scheduleProxy = proxy(new RepositoryScheduleTaskController(
         new RepositoryScheduleTaskService(security, repository, emPermissions), emPermissions),
         aspect);

      principal = new SRPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(request.getSession()).thenReturn(session);
      when(session.getId()).thenReturn("session-1");
      when(request.getUserPrincipal()).thenReturn(principal);
      when(request.getRequestURI()).thenReturn("/api/em/settings/content/repository/dataSourceFolder");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      RequestContextHolder.resetRequestAttributes();
      sUtilMock.close();
   }

   // POST .../dataSourceFolder?path=rfA {"name":"","root":false} by a user with ADMIN on rfA only:
   // refused before the rename, so the ungated read of "" after the save never runs
   @Test
   void emRenameToEmptyNameByDelegatedUserIsRefused() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, "rfA", ResourceAction.ADMIN);
      folder("rfA");
      folder("rfA/kid");
      folder("rfSecret");

      // the control: the root listing is refused directly
      assertThrows(Exception.class, () -> emProxy.getFolderModel("/", principal));
      assertThrows(Exception.class, () -> emProxy.getFolderModel("", principal));

      for(String name : new String[] { "", "   " }) {
         assertThrows(MessageException.class,
                      () -> emProxy.setFolderModel("rfA", folderModel(name), principal));
      }

      verifyNoInteractions(transforms, emPermissions);
      verify(security, never()).setPermission(any(), anyString(), any());
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder("rfA"));
      assertNotNull(registry.getDataSourceFolder("rfA/kid"));
      assertNull(registry.getDataSourceFolder(""));
      assertNull(registry.getDataSourceFolder("   "));
      assertNull(registry.getDataSourceFolder("/kid"));
   }

   // a grant on "" left by the bug before the fix (the rename copied the folder's permission
   // there): the GET passes the gate on "", the root branch must still refuse a caller without
   // ADMIN on "/"
   @Test
   void rootReadThroughLeftoverEmptyGrantIsRefused() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, "", ResourceAction.ADMIN);
      folder("rlSecret");

      assertThrows(inetsoft.sree.security.SecurityException.class,
                   () -> emProxy.getFolderModel("", principal));
      verifyNoInteractions(emPermissions);
   }

   // a valid rename by the same delegated user still works, and the read after the save returns
   // the renamed folder (a non-root read, not affected by the root check)
   @Test
   void emRenameToValidNameByDelegatedUserStillWorks() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, "rvA", ResourceAction.ADMIN);
      folder("rvA");
      folder("rvA/kid");

      DataSourceFolderSettingsModel result =
         emProxy.setFolderModel("rvA", folderModel("rvA2"), principal);

      assertFalse(result.root());
      assertEquals("rvA2", result.name());
      registry.clearCache();
      assertNull(registry.getDataSourceFolder("rvA"));
      assertNotNull(registry.getDataSourceFolder("rvA2"));
      assertNotNull(registry.getDataSourceFolder("rvA2/kid"));
      verify(security).setPermission(ResourceType.DATA_SOURCE_FOLDER, "rvA2", folderPermission);
   }

   // a user with ADMIN on "/" reads the root and saves its permissions with no name
   @Test
   void rootAdminReadsAndSavesRoot() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, "/", ResourceAction.ADMIN);
      folder("raVisible");

      DataSourceFolderSettingsModel read = emProxy.getFolderModel("/", principal);
      assertTrue(read.root());
      assertTrue(read.siblingFolders().contains("raVisible"), read.siblingFolders().toString());

      DataSourceFolderSettingsModel saved = emProxy.setFolderModel(
         "/", DataSourceFolderSettingsModel.builder().root(true).build(), principal);
      assertTrue(saved.root());
      verify(emPermissions).setResourcePermissions(
         eq("/"), eq(ResourceType.DATA_SOURCE_FOLDER), any(), any(), eq(principal));
      verifyNoInteractions(transforms);
   }

   // a permissions-only save on a folder whose name the portal refuses, by its ADMIN holder
   @Test
   void permissionSaveOnColonFolderThroughGateStillWorks() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, "a:b", ResourceAction.ADMIN);
      folder("a:b");

      DataSourceFolderSettingsModel saved =
         emProxy.setFolderModel("a:b", folderModel("a:b"), principal);

      assertFalse(saved.root());
      assertEquals("a:b", saved.name());
      verify(emPermissions).setResourcePermissions(
         eq("a:b"), eq(ResourceType.DATA_SOURCE_FOLDER), any(), any(), eq(principal));
      verifyNoInteractions(transforms);
   }

   // POST api/data/datasources/browser/folder by a user with WRITE and DELETE on the folder
   @Test
   void portalRenameToEmptyNameIsRefused() throws Exception {
      grant(ResourceType.DATA_SOURCE_FOLDER, "rkC",
            ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE);
      grant(ResourceType.DATA_SOURCE_FOLDER, "rkP/sub",
            ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE);
      folder("rkC");
      folder("rkC/kid");
      folder("rkP");
      folder("rkP/sub");

      for(String name : new String[] { "", "  " }) {
         assertThrows(MessageException.class, () -> rename("rkC", name));
         assertThrows(MessageException.class, () -> rename("rkP/sub", name));
      }

      verifyNoInteractions(transforms);
      verify(security, never()).setPermission(any(), anyString(), any());
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder("rkC"));
      assertNotNull(registry.getDataSourceFolder("rkC/kid"));
      assertNotNull(registry.getDataSourceFolder("rkP/sub"));
      assertNull(registry.getDataSourceFolder(""));
      assertNull(registry.getDataSourceFolder("/kid"));
      assertNull(registry.getDataSourceFolder("rkP/"));

      // a valid name still renames
      rename("rkC", "rkC2");
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder("rkC2/kid"));
   }

   // the schedule task folder save, its discarded check removed, is still gated by ADMIN on path
   @Test
   void scheduleFolderSaveIsGated() throws Exception {
      ScheduleTaskFolderSettingsModel model = ScheduleTaskFolderSettingsModel.builder().build();

      assertThrows(Exception.class, () -> scheduleProxy.setFolderModel("/F", model, principal));
      verifyNoInteractions(emPermissions);

      grant(ResourceType.SCHEDULE_TASK_FOLDER, "/F", ResourceAction.ADMIN);
      scheduleProxy.setFolderModel("/F", model, principal);
      verify(emPermissions).setResourcePermissions(
         eq("/F"), eq(ResourceType.SCHEDULE_TASK_FOLDER), any(), eq(principal));
   }

   private boolean granted(ResourceType type, String path, ResourceAction action) {
      return grants.getOrDefault(type + ":" + path, Set.of()).contains(action);
   }

   private void grant(ResourceType type, String path, ResourceAction... actions) {
      grants.computeIfAbsent(type + ":" + path, k -> EnumSet.noneOf(ResourceAction.class))
         .addAll(Arrays.asList(actions));
   }

   private void rename(String path, String name) throws Exception {
      portalProxy.renameDatasourceFolder(
         ImmutableRenameFolderRequest.builder().path(path).name(name).build(), principal);
   }

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }

   @SuppressWarnings("unchecked")
   private static <T> T proxy(T target, SecuredAspect aspect) {
      AspectJProxyFactory factory = new AspectJProxyFactory(target);
      factory.setProxyTargetClass(true);
      factory.addAspect(aspect);
      return (T) factory.getProxy();
   }

   private static DataSourceFolderSettingsModel folderModel(String name) {
      return DataSourceFolderSettingsModel.builder()
         .name(name)
         .root(false)
         .build();
   }
}
