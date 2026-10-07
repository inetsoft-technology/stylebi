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
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.SecuredAspect;
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
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77840, the portal folder rename (POST api/data/datasources/browser/folder) is gated by
 * WRITE on the folder only, and the service discarded the result of its own permission check, so
 * a user with WRITE but without DELETE renamed the folder and its subtree. The rename must need
 * WRITE and DELETE on the folder, as the move and the portal's Rename action do. The controller is
 * driven through the real SecuredAspect into the real service, registry and repository; only the
 * permission oracle is mocked, with one grant set shared by the aspect and the service.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceMoveTargetExistsTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceFolderRenamePermissionTest {
   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private Set<ResourceAction> granted;
   private RenameTransformHandler transforms;
   private MockedStatic<SUtil> sUtilMock;
   private DataSourceController proxy;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      granted = EnumSet.noneOf(ResourceAction.class);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), eq(ResourceType.DATA_SOURCE_FOLDER), anyString(), any()))
         .thenAnswer(inv -> granted.contains(inv.<ResourceAction>getArgument(3)));
      AnalyticRepository analyticRepository = mock(AnalyticRepository.class);
      when(analyticRepository.checkPermission(
         any(), eq(ResourceType.DATA_SOURCE_FOLDER), anyString(), any()))
         .thenAnswer(inv -> granted.contains(inv.<ResourceAction>getArgument(3)));
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

      DataSourceController controller = new DataSourceController(
         mock(DatasourcesService.class), browserService, mock(DatabaseDatasourcesService.class),
         security, mock(DataSourceStatusService.class), mock(FileSystemService.class));
      AspectJProxyFactory factory = new AspectJProxyFactory(controller);
      factory.setProxyTargetClass(true);
      factory.addAspect(new SecuredAspect(registry, mock(ComponentAuthorizationService.class)));
      proxy = factory.getProxy();

      principal = new SRPrincipal(new IdentityID("bob", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
      HttpServletRequest request = mock(HttpServletRequest.class);
      HttpSession session = mock(HttpSession.class);
      when(request.getSession()).thenReturn(session);
      when(session.getId()).thenReturn("session-1");
      when(request.getUserPrincipal()).thenReturn(principal);
      when(request.getRequestURI()).thenReturn("/api/data/datasources/browser/folder");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
   }

   @AfterEach
   void tearDown() {
      RequestContextHolder.resetRequestAttributes();
      sUtilMock.close();
   }

   @Test
   void renameWithoutDeleteIsRefused() throws Exception {
      granted.addAll(EnumSet.of(ResourceAction.READ, ResourceAction.WRITE));
      folder("rpA");
      folder("rpA/G");

      assertThrows(MessageException.class, () -> rename("rpA", "rpA2"));

      verifyNoInteractions(transforms);
      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder("rpA"));
      assertNotNull(registry.getDataSourceFolder("rpA/G"));
      assertNull(registry.getDataSourceFolder("rpA2"));
      assertNull(registry.getDataSourceFolder("rpA2/G"));
   }

   @Test
   void renameWithoutWriteIsRefusedByTheGate() throws Exception {
      granted.addAll(EnumSet.of(ResourceAction.READ, ResourceAction.DELETE));
      folder("rpC");

      assertThrows(java.lang.SecurityException.class, () -> rename("rpC", "rpC2"));

      registry.clearCache();
      assertNotNull(registry.getDataSourceFolder("rpC"));
      assertNull(registry.getDataSourceFolder("rpC2"));
   }

   // WRITE and DELETE without ADMIN is enough, as for the portal's Rename action
   @Test
   void renameWithWriteAndDeleteSucceeds() throws Exception {
      granted.addAll(EnumSet.of(ResourceAction.READ, ResourceAction.WRITE, ResourceAction.DELETE));
      folder("rpB");
      folder("rpB/G");

      rename("rpB", "rpB2");

      registry.clearCache();
      assertNull(registry.getDataSourceFolder("rpB"));
      assertNotNull(registry.getDataSourceFolder("rpB2"));
      assertNotNull(registry.getDataSourceFolder("rpB2/G"));
   }

   private void rename(String path, String name) throws Exception {
      proxy.renameDatasourceFolder(
         ImmutableRenameFolderRequest.builder().path(path).name(name).build(), principal);
   }

   private void folder(String path) {
      registry.setDataSourceFolder(new DataSourceFolder(path, LocalDateTime.now(), null));
   }
}
