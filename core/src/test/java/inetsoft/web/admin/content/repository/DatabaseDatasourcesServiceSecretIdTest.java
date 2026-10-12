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

import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.MessageException;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.*;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.data.DatabaseDatasourcesController;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.security.RequiredPermission;
import inetsoft.web.security.Secured;
import inetsoft.web.session.IgniteSessionRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.test.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Method;
import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77157: the JDBC save and test-connection paths must not resolve a cloud secret id that the
 * caller does not already manage, and the test-connection endpoints must require access to the
 * data source editors.
 */
// the save is audited, which reaches Spring beans (Bug #77844)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceSecretIdTest {
   @BeforeEach
   void setUp() throws Exception {
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      tool.when(() -> Tool.decryptPasswordToCredential(anyString(), any(), any()))
         .thenAnswer(inv -> {
            PasswordCredential credential = new LocalPasswordCredential();
            credential.setUser("user-of-" + inv.getArgument(0));
            credential.setPassword("password-of-" + inv.getArgument(0));
            return credential;
         });
      tool.clearInvocations();

      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(any(), anyBoolean()))
         .thenAnswer(inv -> new LocalPasswordCredential());
      credentialServiceStatic = mockStatic(CredentialService.class);
      credentialServiceStatic.when(CredentialService::getInstance).thenReturn(credentialService);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(mock(OrganizationManager.class));

      Config config = mock(Config.class);
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(config);

      DatabaseType<?> databaseType = mock(DatabaseType.class);
      when(databaseType.getDriverClass(any())).thenReturn("org.test.Driver");
      when(databaseType.formatUrl(any(), any())).thenReturn("jdbc:h2:mem:test");
      DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      doReturn(databaseType).when(databaseTypeService).getDatabaseType(anyString());

      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.checkPermission(any(), eq(ResourceType.CREATE_DATA_SOURCE), anyString(),
                                          eq(ResourceAction.ACCESS))).thenReturn(true);
      repository = mock(XRepository.class);
      when(repository.getDataSourceNames()).thenReturn(new String[0]);
      when(repository.getDataSourceFullNames()).thenReturn(new String[0]);
      registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[0]);
      settingsService = mock(DatabaseSettingsService.class);

      service = new DatabaseDatasourcesService(
         databaseTypeService, securityEngine, settingsService, repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      credentialServiceStatic.close();
      configStatic.close();
      tool.close();
   }

   @Test
   void testConnectionRejectsForeignSecretIdWithoutResolvingIt() {
      assertThrows(MessageException.class, () -> service.testDataSourceConnection(
         "other", database("db", FOREIGN_ID), principal, false));

      tool.verify(() -> Tool.decryptPasswordToCredential(anyString(), any(), any()), never());
      verifyNoInteractions(settingsService);
   }

   @Test
   void testConnectionResolvesSecretIdStoredOnWritableSource() throws Exception {
      storeWritableSource("shared", FOREIGN_ID);

      service.testDataSourceConnection("shared", database("db", FOREIGN_ID), principal, false);

      tool.verify(() -> Tool.decryptPasswordToCredential(eq(FOREIGN_ID), any(), any()));
      verify(settingsService).testConnection(argThat(m -> ("user-of-" + FOREIGN_ID)
         .equals(m.username())), eq(principal));
   }

   @Test
   void saveRejectsForeignSecretIdBeforeAnySave() throws Exception {
      assertThrows(MessageException.class, () -> service.saveDatabase(
         "", settings(database("db", FOREIGN_ID)), ActionRecord.ACTION_NAME_CREATE, principal));

      tool.verify(() -> Tool.decryptPasswordToCredential(anyString(), any(), any()), never());
      verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
   }

   @Test
   void saveRejectsAdditionalConnectionWithForeignSecretIdBeforeAnySave() throws Exception {
      DataSourceSettingsModel model = DataSourceSettingsModel.builder()
         .uploadEnabled(false)
         .dataSource(database("db", null))
         .additionalDataSources(new DatabaseDefinition[] { database("child", FOREIGN_ID) })
         .build();

      assertThrows(MessageException.class, () -> service.saveDatabase(
         "", model, ActionRecord.ACTION_NAME_CREATE, principal));

      tool.verify(() -> Tool.decryptPasswordToCredential(anyString(), any(), any()), never());
      verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
   }

   @Test
   void localModeTestConnectionIsUnaffected() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(false);
      DatabaseDefinition database = database("db", FOREIGN_ID);
      database.getAuthentication().setUserName("local-user");
      database.getAuthentication().setPassword("local-password");

      service.testDataSourceConnection("other", database, principal, false);

      tool.verify(() -> Tool.decryptPasswordToCredential(anyString(), any(), any()), never());
      verify(registry, never()).getDataSourceFullNames();
      verify(settingsService).testConnection(
         argThat(m -> "local-user".equals(m.username())), eq(principal));
   }

   @Test
   void testConnectionEndpointsRequireDataSourceEditorAccess() throws Exception {
      Method em = RepositoryDataSourcesController.class.getMethod(
         "testDataSourceConnection", String.class, DataSourceSettingsModel.class,
         Principal.class);
      Method portal = DatabaseDatasourcesController.class.getMethod(
         "testDataSourceConnection", String.class, boolean.class, DatabaseDefinition.class,
         Principal.class);

      for(Method method : new Method[] { em, portal }) {
         Secured secured = method.getAnnotation(Secured.class);
         assertNotNull(secured, method.toString());
         assertEquals("OR", secured.operator());
         assertTrue(Arrays.stream(secured.value()).anyMatch(
            p -> isPermission(p, ResourceType.PORTAL_TAB, "Data")), method.toString());
         assertTrue(Arrays.stream(secured.value()).anyMatch(
            p -> isPermission(p, ResourceType.EM_COMPONENT, "settings/content/repository")),
                    method.toString());
      }
   }

   private static boolean isPermission(RequiredPermission permission, ResourceType type,
                                       String resource)
   {
      return permission.resourceType() == type && resource.equals(permission.resource()) &&
         Arrays.asList(permission.actions()).contains(ResourceAction.ACCESS);
   }

   private void storeWritableSource(String path, String secretId) throws Exception {
      JDBCDataSource stored = mock(JDBCDataSource.class);
      PasswordCredential credential = mock(PasswordCredential.class,
         withSettings().extraInterfaces(CloudCredential.class));
      when(credential.getId()).thenReturn(secretId);
      when(stored.getCredential()).thenReturn(credential);
      when(stored.getDataSourceNames()).thenReturn(new String[0]);
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { path });
      when(registry.getDataSource(path)).thenReturn(stored);
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq(path),
                                          eq(ResourceAction.WRITE))).thenReturn(true);
   }

   private static DataSourceSettingsModel settings(DatabaseDefinition database) {
      return DataSourceSettingsModel.builder()
         .uploadEnabled(false)
         .dataSource(database)
         .build();
   }

   private static DatabaseDefinition database(String name, String secretId) {
      AuthenticationDetails authentication = new AuthenticationDetails();
      authentication.setRequired(secretId != null);
      authentication.setUseCredentialId(secretId != null);
      authentication.setCredentialId(secretId);

      DatabaseDefinition database = new DatabaseDefinition();
      database.setName(name);
      database.setType(CustomDatabaseType.TYPE);
      database.setInfo(new CustomDatabaseType.CustomDatabaseInfo());
      database.setNetwork(new NetworkLocation());
      database.setAuthentication(authentication);
      return database;
   }

   private static final String FOREIGN_ID = "org-b-db-secret";

   private DatabaseDatasourcesService service;
   private SecurityEngine securityEngine;
   private XRepository repository;
   private DataSourceRegistry registry;
   private DatabaseSettingsService settingsService;
   private XPrincipal principal;
   private MockedStatic<Tool> tool;
   private MockedStatic<Config> configStatic;
   private MockedStatic<CredentialService> credentialServiceStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
