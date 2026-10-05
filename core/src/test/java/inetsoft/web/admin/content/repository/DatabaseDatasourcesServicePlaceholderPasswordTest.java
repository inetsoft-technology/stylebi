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

import inetsoft.report.internal.Util;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.security.*;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.Config;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.credential.*;
import inetsoft.web.admin.content.database.*;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77174: the placeholder password in the JDBC editor may only stand for a stored password
 * when the caller can edit the data source the stored password is read from.
 */
@Tag("core")
class DatabaseDatasourcesServicePlaceholderPasswordTest {
   @BeforeEach
   void setUp() throws Exception {
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      tool.when(Tool::isCloudSecrets).thenReturn(false);
      sreeEnv = mockStatic(SreeEnv.class);

      CredentialService credentialService = mock(CredentialService.class);
      when(credentialService.createCredential(any(), anyBoolean()))
         .thenAnswer(inv -> new LocalPasswordCredential());
      credentialServiceStatic = mockStatic(CredentialService.class);
      credentialServiceStatic.when(CredentialService::getInstance).thenReturn(credentialService);
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(mock(OrganizationManager.class));
      configStatic = mockStatic(Config.class);
      configStatic.when(Config::getConfig).thenReturn(mock(Config.class));

      DatabaseType<?> databaseType = mock(DatabaseType.class);
      when(databaseType.getDriverClass(any())).thenReturn("org.test.Driver");
      when(databaseType.formatUrl(any(), any())).thenReturn("jdbc:formatted");
      DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      doReturn(databaseType).when(databaseTypeService).getDatabaseType(anyString());

      // permission checks answer false unless a test grants them
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      repository = mock(XRepository.class);
      when(repository.getDataSourceNames()).thenReturn(new String[0]);
      when(repository.getDataSourceFullNames()).thenReturn(new String[0]);
      registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[0]);
      when(registry.getEntries(anyString(), any())).thenReturn(new AssetEntry[0]);
      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getPartitionNames()).thenReturn(new String[0]);
      when(registry.getDataModel(anyString())).thenReturn(dataModel);
      settingsService = mock(DatabaseSettingsService.class);

      service = new DatabaseDatasourcesService(
         databaseTypeService, securityEngine, settingsService, repository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("mallory", "host-org").convertToKey());
   }

   @AfterEach
   void tearDown() {
      orgManagerStatic.close();
      credentialServiceStatic.close();
      configStatic.close();
      sreeEnv.close();
      tool.close();
   }

   @Test
   void testConnectionWithoutWriteIsRefused() throws Exception {
      storeSource("hr/payroll", "stored-secret");

      assertThrows(java.lang.SecurityException.class, () -> service.testDataSourceConnection(
         "hr/payroll", placeholderDefinition("payroll"), principal, false));

      verifyNoInteractions(settingsService);
   }

   @Test
   void testConnectionWithWriteReusesStoredPasswordWithChangedUrl() throws Exception {
      storeSource("hr/payroll", "stored-secret");
      grantWrite("hr/payroll");

      service.testDataSourceConnection(
         "hr/payroll", placeholderDefinition("payroll"), principal, false);

      verify(settingsService).testConnection(argThat(m ->
         "stored-secret".equals(m.password()) && CALLER_URL.equals(m.databaseURL())),
         eq(principal));
   }

   @Test
   void testConnectionWithEmptyPathChecksResolvedSource() throws Exception {
      storeSource("payroll", "stored-secret");
      grantWrite("");

      assertThrows(java.lang.SecurityException.class, () -> service.testDataSourceConnection(
         "", placeholderDefinition("payroll"), principal, false));
      verifyNoInteractions(settingsService);

      grantWrite("payroll");
      service.testDataSourceConnection("", placeholderDefinition("payroll"), principal, false);

      verify(settingsService).testConnection(
         argThat(m -> "stored-secret".equals(m.password())), eq(principal));
   }

   @Test
   void additionalConnectionWithoutWriteOnParentIsRefused() throws Exception {
      storeParentWithChild("hr/payroll", "child", "child-secret");

      assertThrows(java.lang.SecurityException.class, () -> service.testDataSourceConnection(
         "hr/payroll", placeholderDefinition("child"), principal, true));

      verifyNoInteractions(settingsService);
   }

   @Test
   void additionalConnectionWithWriteOnParentReusesChildPassword() throws Exception {
      storeParentWithChild("hr/payroll", "child", "child-secret");
      grantWrite("hr/payroll");

      service.testDataSourceConnection(
         "hr/payroll", placeholderDefinition("child"), principal, true);

      verify(settingsService).testConnection(argThat(m ->
         "child-secret".equals(m.password()) && CALLER_URL.equals(m.databaseURL())),
         eq(principal));
   }

   @Test
   void additionalConnectionUnderFolderPathIsRefused() throws Exception {
      // "F" is a folder, "F/S" is a top-level data source the caller has no rights on, and the
      // caller can write everything else, including the data source root folder
      storeSource("F/S", "other-secret");
      when(securityEngine.checkPermission(any(), any(ResourceType.class), anyString(),
                                          eq(ResourceAction.WRITE))).thenReturn(true);
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq("F/S"),
                                          any(ResourceAction.class))).thenReturn(false);

      assertThrows(java.lang.SecurityException.class, () -> service.testDataSourceConnection(
         "F", placeholderDefinition("S"), principal, true));

      verify(repository, never()).getDataSource("F/S");
      verifyNoInteractions(settingsService);
   }

   @Test
   void cloudModeWithLocalCredentialFlagWithoutWriteIsRefused() throws Exception {
      tool.when(Tool::isCloudSecrets).thenReturn(true);
      storeSource("hr/payroll", "fetched-cloud-secret");
      DatabaseDefinition definition = placeholderDefinition("payroll");
      definition.getAuthentication().setUseCredentialId(false);

      assertThrows(java.lang.SecurityException.class, () -> service.testDataSourceConnection(
         "hr/payroll", definition, principal, false));

      verifyNoInteractions(settingsService);
   }

   @Test
   void customUrlDoesNotLoadStoredSource() throws Exception {
      storeSource("hr/payroll", "stored-secret");

      service.buildDatabaseCustomUrl("hr/payroll", placeholderDefinition("payroll"));

      verify(repository, never()).getDataSource(anyString());
   }

   @Test
   void realPasswordDoesNotLoadStoredSource() throws Exception {
      storeSource("hr/payroll", "stored-secret");
      DatabaseDefinition definition = placeholderDefinition("payroll");
      definition.getAuthentication().setPassword("typed-password");

      service.testDataSourceConnection("hr/payroll", definition, principal, false);

      verify(repository, never()).getDataSource(anyString());
      verify(settingsService).testConnection(
         argThat(m -> "typed-password".equals(m.password())), eq(principal));
   }

   @Test
   void saveOfChildRefusesItsStoredPasswordWithoutWriteOnItsParent() throws Exception {
      // editing "db/child" directly reads the placeholder's password through the parent, as a
      // save of the parent does, and never from a top-level data source called child
      JDBCDataSource parent = storeParentWithChild("db", "child", "child-secret");
      JDBCDataSource child = storeSource("db/child", null);
      when(child.getBaseDatasource()).thenReturn(parent);
      storeSource("child", "top-level-secret");
      grantWrite("db/child");
      grantWrite("child");

      assertThrows(java.lang.SecurityException.class, () -> service.saveDatabase(
         "db/child", settings(placeholderDefinition("child")), ActionRecord.ACTION_NAME_EDIT,
         principal));

      verify(child, never()).setCredential(any());
      verify(parent, never()).addDatasource(any());
      verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
   }

   @Test
   void saveOfChildWithWriteOnItsParentReusesItsOwnPassword() throws Exception {
      JDBCDataSource parent = storeParentWithChild("db", "child", "child-secret");
      when(parent.getDataSourceNames()).thenReturn(new String[0]);
      JDBCDataSource child = storeSource("db/child", null);
      when(child.getBaseDatasource()).thenReturn(parent);
      storeSource("child", "top-level-secret");
      grantWrite("db");
      grantWrite("db/child");

      service.saveDatabase(
         "db/child", settings(placeholderDefinition("child")), ActionRecord.ACTION_NAME_EDIT,
         principal);

      verify(child).setCredential(argThat(c -> "child-secret".equals(c.getPassword())));
      verify(parent).addDatasource(child);
      // the parent is updated, not the child by its path
      verify(repository).updateDataSource(parent, "db", false);
      verify(repository, never()).updateDataSource(eq(child), anyString(), anyBoolean());
   }

   @Test
   void saveWithWriteKeepsStoredPassword() throws Exception {
      JDBCDataSource stored = storeSource("db", "stored-secret");
      grantWrite("db");

      service.saveDatabase(
         "db", settings(placeholderDefinition("db")), ActionRecord.ACTION_NAME_EDIT, principal);

      verify(stored).setCredential(
         argThat(c -> "stored-secret".equals(c.getPassword())));
      verify(repository).updateDataSource(stored, "db", false);
   }

   @Test
   void saveOfAdditionalConnectionWithWriteReusesChildPassword() throws Exception {
      JDBCDataSource parent = storeParentWithChild("db", "child", "child-secret");
      when(parent.getDataSourceNames()).thenReturn(new String[0]);
      grantWrite("db");
      DataSourceSettingsModel model = DataSourceSettingsModel.builder()
         .uploadEnabled(false)
         .dataSource(databaseDefinition("db", "typed-password"))
         .additionalDataSources(new DatabaseDefinition[] { placeholderDefinition("child") })
         .build();

      service.saveDatabase("db", model, ActionRecord.ACTION_NAME_EDIT, principal);

      verify(parent).addDatasource(
         argThat(ds -> "child-secret".equals(((JDBCDataSource) ds).getPassword())));
   }

   private JDBCDataSource storeSource(String path, String password) throws Exception {
      JDBCDataSource stored = mock(JDBCDataSource.class);
      when(stored.getPassword()).thenReturn(password);
      when(stored.getFullName()).thenReturn(path);
      // the save path clones the stored data source, keep the mock so it can be verified
      when(stored.clone()).thenReturn(stored);
      when(repository.getDataSource(path)).thenReturn(stored);
      return stored;
   }

   private JDBCDataSource storeParentWithChild(String path, String childName, String password)
      throws Exception
   {
      JDBCDataSource child = mock(JDBCDataSource.class);
      when(child.getPassword()).thenReturn(password);
      JDBCDataSource parent = storeSource(path, "parent-secret");
      when(parent.getDataSource(childName)).thenReturn(child);
      return parent;
   }

   private void grantWrite(String path) throws Exception {
      when(securityEngine.checkPermission(any(), eq(ResourceType.DATA_SOURCE), eq(path),
                                          eq(ResourceAction.WRITE))).thenReturn(true);
   }

   private static DataSourceSettingsModel settings(DatabaseDefinition database) {
      return DataSourceSettingsModel.builder()
         .uploadEnabled(false)
         .dataSource(database)
         .build();
   }

   private static DatabaseDefinition placeholderDefinition(String name) {
      return databaseDefinition(name, Util.PLACEHOLDER_PASSWORD);
   }

   private static DatabaseDefinition databaseDefinition(String name, String password) {
      AuthenticationDetails authentication = new AuthenticationDetails();
      authentication.setRequired(true);
      authentication.setUserName("any-user");
      authentication.setPassword(password);

      CustomDatabaseType.CustomDatabaseInfo info = new CustomDatabaseType.CustomDatabaseInfo();
      info.setCustomEditMode(true);
      info.setCustomUrl(CALLER_URL);

      DatabaseDefinition database = new DatabaseDefinition();
      database.setName(name);
      database.setOldName(name);
      database.setType(CustomDatabaseType.TYPE);
      database.setInfo(info);
      database.setNetwork(new NetworkLocation());
      database.setAuthentication(authentication);
      return database;
   }

   private static final String CALLER_URL = "jdbc:caller-controlled://example.invalid/db";

   private DatabaseDatasourcesService service;
   private SecurityEngine securityEngine;
   private XRepository repository;
   private DataSourceRegistry registry;
   private DatabaseSettingsService settingsService;
   private XPrincipal principal;
   private MockedStatic<Tool> tool;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Config> configStatic;
   private MockedStatic<CredentialService> credentialServiceStatic;
   private MockedStatic<OrganizationManager> orgManagerStatic;
}
