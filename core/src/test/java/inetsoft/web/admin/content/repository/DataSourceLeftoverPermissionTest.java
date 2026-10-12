/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.sree.web.dashboard.DashboardRegistryManager;
import inetsoft.storage.KeyValueStorage;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.ConfirmException;
import inetsoft.uql.asset.DependencyHandler;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.*;
import inetsoft.util.audit.Audit;
import inetsoft.web.RecycleBin;
import inetsoft.web.admin.content.database.model.DataModelFolderManagerService;
import inetsoft.web.admin.content.repository.model.DeleteTreeNodesRequest;
import inetsoft.web.admin.content.repository.model.TreeNodeInfo;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.notifications.NotificationService;
import inetsoft.web.portal.data.*;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.AdditionalAnswers;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77941: the data source registry removes the permissions of the folders, data sources and
 * additional connections it removes in a single pass. When that storage write fails the grant
 * stays at the name, and a folder or data source created later with it receives the grant, so
 * the delete must report it to the user: EM as a "warning:" status, the portal as a
 * notification. Runs against the real registry and a real FileAuthorizationProvider whose
 * storage fails the remove of chosen keys.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DataSourceFolderMoveAdditionalConnectionTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceLeftoverPermissionTest {
   private static final String URL = "jdbc:derby:memory:bug77941;create=true";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   @Autowired
   private Config config;

   @BeforeEach
   void setUp() throws Exception {
      FileAuthenticationProvider authc = new FileAuthenticationProvider();
      AuthenticationChain authcChain = new AuthenticationChain();
      authcChain.setProviders(List.of(authc));
      authcChain.saveConfiguration();

      FileAuthorizationProvider authz = new FileAuthorizationProvider();
      authz.setProviderName("Primary");
      AuthorizationChain authzChain = new AuthorizationChain();
      authzChain.setProviders(List.of(authz));
      authzChain.saveConfiguration();

      SreeEnv.setProperty("security.enabled", "true");
      SreeEnv.save();
      security = SecurityEngine.getSecurity();
      security.init();

      AuthorizationChain chain =
         (AuthorizationChain) security.getSecurityProvider().getAuthorizationProvider();
      provider = (FileAuthorizationProvider) chain.getProviders().get(0);
      // opens the storage
      provider.getPermission(ResourceType.DATA_SOURCE, "probe", ORG);
      real = storage();

      // the grantee must be a known user, or every permission check is false
      AuthenticationChain users =
         (AuthenticationChain) security.getSecurityProvider().getAuthenticationProvider();
      ((FileAuthenticationProvider) users.getProviders().get(0))
         .addUser(new FSUser(new IdentityID("mallory", ORG)));
      mallory = new SRPrincipal(new IdentityID("mallory", ORG), new IdentityID[0],
                                new String[0], ORG, 1L);
      admin = new SRPrincipal(new IdentityID("admin", ORG),
                              new IdentityID[] { new IdentityID("Administrator", null) },
                              new String[0], ORG, 1L);

      registry.init();
      Tool.clearUserMessage();
   }

   @AfterEach
   void tearDown() throws Exception {
      try {
         if(provider != null) {
            setStorage(real);
         }

         for(String folder : List.of(FOLDER, CLASH)) {
            try {
               if(registry.getDataSourceFolder(folder) != null) {
                  registry.removeDataSourceFolder(folder, true);
               }
            }
            catch(Exception ignore) {
               // best-effort cleanup
            }
         }

         if(registry.getDataSource(SOURCE) != null) {
            registry.removeDataSource(SOURCE);
         }

         // the grants a failed remove left, which the next test would receive
         for(String key : List.of(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER),
                                  key(ResourceType.DATA_SOURCE, FOLDER + "/newds"),
                                  key(ResourceType.DATA_SOURCE, CLASH),
                                  key(ResourceType.DATA_SOURCE, SOURCE),
                                  key(ResourceType.DATA_SOURCE, SOURCE + "::conn")))
         {
            real.remove(key).get();
         }
      }
      finally {
         Tool.clearUserMessage();

         if(provider != null) {
            provider.tearDown();
         }

         SreeEnv.remove("security.enabled");
      }
   }

   // the registry delete of a folder (also user delete) reports the grant left at its name, and
   // a folder and data source created there later still receive it
   @Test
   void registryFolderDelete_removeFails_reportsWarning() throws Exception {
      addFolder(FOLDER);
      assertFalse(canRead(ResourceType.DATA_SOURCE_FOLDER, FOLDER), "granted before the grant");
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);
      assertTrue(canRead(ResourceType.DATA_SOURCE_FOLDER, FOLDER), "the grant is not checked");
      failRemoves(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER));

      assertDoesNotThrow(() -> registry.removeDataSourceFolder(FOLDER));

      assertNull(registry.getDataSourceFolder(FOLDER));
      assertLeftoverWarning(Tool.getUserMessage());
      setStorage(real);
      assertNotNull(real.get(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER)));

      // reported, not removed: the exposure the warning is about
      addFolder(FOLDER);
      registry.setDataSource(source(FOLDER + "/newds"), false);
      assertTrue(canRead(ResourceType.DATA_SOURCE_FOLDER, FOLDER));
      assertTrue(canRead(ResourceType.DATA_SOURCE, FOLDER + "/newds"));
   }

   @Test
   void registryFolderDelete_removeSucceeds_reportsNothing() throws Exception {
      addFolder(FOLDER);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);

      registry.removeDataSourceFolder(FOLDER);

      assertNull(Tool.getUserMessage());
      assertNull(real.get(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER)));
      addFolder(FOLDER);
      assertFalse(canRead(ResourceType.DATA_SOURCE_FOLDER, FOLDER));
   }

   // the registry delete of a data source, which no caller revokes again, e.g. the user delete
   @Test
   void registryDataSourceDelete_removeFails_reportsWarning() throws Exception {
      registry.setDataSource(source(SOURCE), false);
      grant(ResourceType.DATA_SOURCE, SOURCE);
      failRemoves(key(ResourceType.DATA_SOURCE, SOURCE));

      assertDoesNotThrow(() -> registry.removeDataSource(SOURCE));

      assertNull(registry.getDataSource(SOURCE));
      assertLeftoverWarning(Tool.getUserMessage());
   }

   // the data source at the path of a folder is removed with the folder by the registry alone
   @Test
   void registryFolderDeleteWithDataSourceAtItsPath_removeFails_reportsWarning()
      throws Exception
   {
      addFolder(CLASH);
      registry.setDataSource(source(CLASH), false);
      grant(ResourceType.DATA_SOURCE, CLASH);
      failRemoves(key(ResourceType.DATA_SOURCE, CLASH));

      assertDoesNotThrow(() -> registry.removeDataSourceFolder(CLASH, true));

      assertNull(registry.getDataSource(CLASH));
      assertLeftoverWarning(Tool.getUserMessage());
   }

   // the additional connection of a removed data source
   @Test
   void registryDataSourceDelete_additionalConnectionRemoveFails_reportsWarning()
      throws Exception
   {
      JDBCDataSource parent = source(SOURCE);
      parent.addDatasource(source("conn"));
      registry.setDataSource(parent, false);
      String conn = SOURCE + "::conn";
      grant(ResourceType.DATA_SOURCE, conn);
      failRemoves(key(ResourceType.DATA_SOURCE, conn));

      registry.removeDataSource(SOURCE);

      assertLeftoverWarning(Tool.getUserMessage());
      setStorage(real);
      assertNotNull(real.get(key(ResourceType.DATA_SOURCE, conn)));
   }

   // EM tree delete of a folder returns the warning that Bug #77939 shows without a re-send
   @Test
   void emFolderDelete_removeFails_returnsWarning() throws Exception {
      addFolder(FOLDER);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);
      failRemoves(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER));

      ConnectionStatus status = emDeleteFolder(FOLDER);

      assertNull(registry.getDataSourceFolder(FOLDER));
      assertNotNull(status, "the permission left at the folder was not reported");
      assertEquals("warning:" + leftoverMessage(), status.getStatus());
   }

   @Test
   void emFolderDelete_removeSucceeds_returnsNothing() throws Exception {
      addFolder(FOLDER);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);

      assertNull(emDeleteFolder(FOLDER));
      assertNull(registry.getDataSourceFolder(FOLDER));
   }

   // the portal Data tab folder delete notifies the user who deleted it
   @Test
   void portalFolderDelete_removeFails_notifiesUser() throws Exception {
      addFolder(FOLDER);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);
      failRemoves(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER));
      NotificationService notifications = mock(NotificationService.class);

      try(MockedStatic<Audit> ignored = mockAudit()) {
         assertNull(portalController(notifications)
                       .deleteDatasourceFolder(FOLDER, true, admin));
      }

      assertNull(registry.getDataSourceFolder(FOLDER));
      verify(notifications).sendNotificationToUser(leftoverMessage(), admin);
   }

   @Test
   void portalFolderDelete_removeSucceeds_notifiesNothing() throws Exception {
      addFolder(FOLDER);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);
      NotificationService notifications = mock(NotificationService.class);
      // left by an earlier request on this pooled thread, not sent
      Tool.addUserMessage("stale");

      try(MockedStatic<Audit> ignored = mockAudit()) {
         assertNull(portalController(notifications)
                       .deleteDatasourceFolder(FOLDER, true, admin));
      }

      verifyNoInteractions(notifications);
   }

   // Bug #78217, the portal data source delete no longer revokes its own key again after the
   // registry did, so a failed removal doesn't fail the delete, and the user is notified
   @Test
   void portalDataSourceDelete_removeFails_succeedsAndNotifiesUser() throws Exception {
      registry.setDataSource(source(SOURCE), false);
      grant(ResourceType.DATA_SOURCE, SOURCE);
      failRemoves(key(ResourceType.DATA_SOURCE, SOURCE));
      NotificationService notifications = mock(NotificationService.class);

      assertDoesNotThrow(() -> portalController(notifications)
         .deleteDataSource(SOURCE, SOURCE, true, admin));

      assertNull(registry.getDataSource(SOURCE));
      verify(notifications).sendNotificationToUser(leftoverMessage(), admin);
   }

   // the portal Data tab "delete selected" of a folder and a data source notifies the user once
   @Test
   void portalBulkDelete_folderRemoveFails_notifiesUser() throws Exception {
      addFolder(FOLDER);
      registry.setDataSource(source(SOURCE), false);
      grant(ResourceType.DATA_SOURCE_FOLDER, FOLDER);
      failRemoves(key(ResourceType.DATA_SOURCE_FOLDER, FOLDER));
      NotificationService notifications = mock(NotificationService.class);
      SelectedDataSourcesRequest request = ImmutableSelectedDataSourcesRequest.builder()
         .addDataSources(ImmutableSelectedDataSourceItem.builder().name(SOURCE).path(SOURCE).build())
         .addFolders(ImmutableSelectedDataSourceItem.builder().name(FOLDER).path(FOLDER).build())
         .build();

      DataSourceController controller = portalController(notifications);
      // the delete permission checks before the delete are not under test
      SecurityEngine allow = mock(SecurityEngine.class);
      when(allow.checkPermission(any(Principal.class), any(), anyString(), any())).thenReturn(true);
      ReflectionTestUtils.setField(controller, "securityEngine", allow);

      try(MockedStatic<Audit> ignored = mockAudit()) {
         controller.deleteDataSources(request, admin);
      }

      assertNull(registry.getDataSourceFolder(FOLDER));
      assertNull(registry.getDataSource(SOURCE));
      verify(notifications).sendNotificationToUser(leftoverMessage(), admin);
   }

   private ConnectionStatus emDeleteFolder(String path) throws Exception {
      DeleteTreeNodesRequest request = DeleteTreeNodesRequest.builder()
         .nodes(new TreeNodeInfo[] {
            TreeNodeInfo.builder().label(path).path(path)
               .type(inetsoft.sree.RepositoryEntry.DATA_SOURCE_FOLDER).build() })
         .force(true)
         .permanent(false)
         .build();

      try(MockedStatic<Audit> ignored = mockAudit()) {
         return new RepositoryObjectController(objectService(), null, null)
            .deleteRepositoryEntry(request, admin);
      }
   }

   private RepositoryObjectService objectService() throws Exception {
      // permission checks are not under test
      SecurityProvider allow = mock(SecurityProvider.class);
      when(allow.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      ResourcePermissionService permissions = mock(ResourcePermissionService.class);
      when(permissions.getRepositoryResourceType(anyInt(), anyString())).thenAnswer(
         inv -> new Resource(ResourceType.DATA_SOURCE_FOLDER, inv.getArgument(1)));
      RepletRegistryManager repletRegistries = mock(RepletRegistryManager.class);
      when(repletRegistries.getRegistry(nullable(IdentityID.class)))
         .thenReturn(mock(RepletRegistry.class));
      return new RepositoryObjectService(
         mock(RepletRegistryService.class), mock(ContentRepositoryTreeService.class), allow,
         permissions, repository, mock(RepositoryDashboardService.class),
         mock(DataModelFolderManagerService.class), registry, mock(LibManagerProvider.class),
         mock(RecycleBin.class), mock(DependencyHandler.class), mock(RenameTransformHandler.class),
         repletRegistries, mock(DashboardRegistryManager.class));
   }

   private DataSourceController portalController(NotificationService notifications)
      throws Exception
   {
      DataSourceBrowserService browser = new DataSourceBrowserService(
         security, objectService(), repository, null, registry, config,
         mock(RenameTransformHandler.class));
      DatasourcesService datasources = new DatasourcesService(
         repository, security, mock(DataSourceStatusService.class), registry, config);
      DatabaseDatasourcesService database = mock(DatabaseDatasourcesService.class);
      when(database.getDataSourceAuditPath(anyString(), any(), any())).thenReturn(SOURCE);
      DataSourceController controller = new DataSourceController(
         datasources, browser, database, security, mock(DataSourceStatusService.class), null);
      ReflectionTestUtils.setField(controller, "notificationService", notifications);
      return controller;
   }

   private static MockedStatic<Audit> mockAudit() {
      MockedStatic<Audit> audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      return audit;
   }

   private void addFolder(String name) {
      registry.setDataSourceFolder(new DataSourceFolder(name, LocalDateTime.now(), null));
   }

   private static JDBCDataSource source(String name) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
      dataSource.setURL(URL);
      dataSource.setCustomUrl(URL);
      return dataSource;
   }

   // a grant edited for the organization, as the EM permission pane saves it, which replaces
   // the permission inherited from the parent folder
   private void grant(ResourceType type, String resource) {
      Permission p = new Permission();
      p.setUserGrantsForOrg(ResourceAction.READ, Set.of("mallory"), ORG);
      p.updateGrantAllByOrg(ORG, true);
      security.setPermission(type, resource, p);
   }

   private boolean canRead(ResourceType type, String resource) {
      return security.getSecurityProvider()
         .checkPermission(mallory, type, resource, ResourceAction.READ);
   }

   private static void assertLeftoverWarning(UserMessage message) {
      assertNotNull(message, "the permission left at the name was not reported");
      assertEquals(ConfirmException.WARNING, message.getLevel());
      assertEquals(leftoverMessage(), message.getMessage());
   }

   private static String leftoverMessage() {
      return Catalog.getCatalog().getString("em.repository.permissionsMayRemain");
   }

   private static String key(ResourceType type, String resource) {
      return type + ":" + ORG + ":" + resource;
   }

   /**
    * Fails every remove of the key, the registry's and a caller's second one. The other writes
    * and the reads go to the real storage.
    */
   @SuppressWarnings("unchecked")
   private void failRemoves(String failKey) throws Exception {
      KeyValueStorage<Permission> failing =
         mock(KeyValueStorage.class, AdditionalAnswers.delegatesTo(real));
      doAnswer(inv -> failKey.equals(inv.getArgument(0)) ?
         CompletableFuture.failedFuture(new IOException("simulated write failure")) :
         real.remove(inv.getArgument(0))).when(failing).remove(anyString());
      setStorage(failing);
   }

   @SuppressWarnings("unchecked")
   private KeyValueStorage<Permission> storage() throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      return (KeyValueStorage<Permission>) f.get(provider);
   }

   private void setStorage(KeyValueStorage<Permission> s) throws Exception {
      Field f = FileAuthorizationProvider.class.getDeclaredField("storage");
      f.setAccessible(true);
      f.set(provider, s);
   }

   private static final String ORG = Organization.getDefaultOrganizationID();
   private static final String FOLDER = "F77941";
   private static final String CLASH = "C77941";
   private static final String SOURCE = "DS77941";
   private SecurityEngine security;
   private FileAuthorizationProvider provider;
   private KeyValueStorage<Permission> real;
   private SRPrincipal mallory;
   private SRPrincipal admin;
}
