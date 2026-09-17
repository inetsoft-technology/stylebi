/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 */
package inetsoft.web.admin.datasource;

import inetsoft.report.internal.Util;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.DataSourceFolder;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetObject;
import inetsoft.uql.asset.DependencyException;
import inetsoft.uql.asset.sync.DependencyTool;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.audit.ActionRecord;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import inetsoft.web.security.auth.ResourceExistsException;
import inetsoft.web.security.auth.UnauthorizedAccessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Bug #76337: DataSourceService.updateDataSource must not overwrite the stored password with
// JdbcDataSourceProperties's own mask literal when a caller echoes getDataSource's response back
// unmodified. (Migrated from the enterprise DataSourceApiServiceTest when the business logic moved
// to this community class -- see DataSourceApiService for the enterprise Public API thin wrapper
// that still carries the organizationid parameter these tests previously exercised as null.)
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, DataSourceServiceTest.SpringOverrides.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@TestPropertySource(properties = { "mock.license.manager=true" })
class DataSourceServiceTest {
   // Overrides BaseTestConfiguration's real, spied SecurityEngine bean -- its @PostConstruct
   // bootstraps real security config (virtual_security.xml / INETSOFT_ADMIN_PASSWORD), which this
   // test doesn't need; it only needs PropertiesEngine/SreeEnv machinery to be available so that
   // DataSourceService.updateDataSource's ActionRecord construction doesn't blow up.
   @Configuration
   static class SpringOverrides {
      @Bean
      @Primary
      public SecurityEngine securityEngine() {
         SecurityEngine securityEngine = mock(SecurityEngine.class);
         when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
         return securityEngine;
      }
   }
   @BeforeEach
   void setUp() throws Exception {
      repository = mock(XRepository.class);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      ids = mock(MD5IdentifierGenerator.class);
      service = new DataSourceService(repository, securityEngine, databaseTypeService, ids);

      ds = mock(JDBCDataSource.class);
      when(ds.getType()).thenReturn(XDataSource.JDBC);
      when(ds.getFullName()).thenReturn(DS_NAME);
      when(ds.isRequireLogin()).thenReturn(true);

      passwordHolder = new String[] { REAL_PASSWORD };
      when(ds.getPassword()).thenAnswer(inv -> passwordHolder[0]);
      doAnswer(inv -> {
         passwordHolder[0] = inv.getArgument(0);
         return null;
      }).when(ds).setPassword(any());

      when(ids.getName(DS_ID)).thenReturn(DS_NAME);
      when(repository.getDataSource(DS_NAME)).thenReturn(ds);
      when(securityEngine.checkPermission(
         any(Principal.class), eq(ResourceType.DATA_SOURCE), anyString(), any(ResourceAction.class)))
         .thenReturn(true);

      principal = new SRPrincipal(
         new IdentityID("admin", "host-org"), new IdentityID[0], new String[0], "host-org", 1L);
   }

   @Test
   void updateDataSourceLeavesPasswordUnchangedWhenPlaceholderEchoedBack() throws Exception {
      JdbcDataSourceProperties properties =
         (JdbcDataSourceProperties) service.getDataSource(DS_ID, principal);
      assertEquals(Util.PLACEHOLDER_PASSWORD, properties.getPassword());

      properties.setUrl("jdbc:derby:classpath:orders2;user=SA");

      service.updateDataSource(DS_ID, properties, principal);

      assertEquals(REAL_PASSWORD, passwordHolder[0]);
   }

   @Test
   void updateDataSourceAppliesGenuineNewPassword() throws Exception {
      JdbcDataSourceProperties properties =
         (JdbcDataSourceProperties) service.getDataSource(DS_ID, principal);
      properties.setPassword("newSecretPassword");

      service.updateDataSource(DS_ID, properties, principal);

      assertEquals("newSecretPassword", passwordHolder[0]);
   }

   // Bug #76339: DataSourceService.updateDataSource/deleteDataSource unconditionally set
   // ACTION_STATUS_SUCCESS on the audit record inside `finally`, clobbering the initial
   // ACTION_STATUS_FAILURE value even when the try block threw.
   @Test
   void updateDataSourceAuditRecordReportsFailureOnThrow() throws Exception {
      JdbcDataSourceProperties properties =
         (JdbcDataSourceProperties) service.getDataSource(DS_ID, principal);
      properties.setName("SomeOtherExistingName");
      when(repository.getDataSource("SomeOtherExistingName")).thenReturn(mock(XDataSource.class));

      try(MockedStatic<Audit> auditStatic = mockStatic(Audit.class)) {
         Audit audit = mock(Audit.class);
         auditStatic.when(Audit::getInstance).thenReturn(audit);

         assertThrows(ResourceExistsException.class,
            () -> service.updateDataSource(DS_ID, properties, principal));

         ArgumentCaptor<ActionRecord> captor = ArgumentCaptor.forClass(ActionRecord.class);
         verify(audit).auditAction(captor.capture(), eq(principal));
         assertEquals(ActionRecord.ACTION_STATUS_FAILURE, captor.getValue().getActionStatus());
      }
   }

   @Test
   void updateDataSourceAuditRecordReportsSuccessOnNormalCompletion() throws Exception {
      JdbcDataSourceProperties properties =
         (JdbcDataSourceProperties) service.getDataSource(DS_ID, principal);
      properties.setPassword("newSecretPassword");

      try(MockedStatic<Audit> auditStatic = mockStatic(Audit.class)) {
         Audit audit = mock(Audit.class);
         auditStatic.when(Audit::getInstance).thenReturn(audit);

         service.updateDataSource(DS_ID, properties, principal);

         ArgumentCaptor<ActionRecord> captor = ArgumentCaptor.forClass(ActionRecord.class);
         verify(audit).auditAction(captor.capture(), eq(principal));
         assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, captor.getValue().getActionStatus());
      }
   }

   // Bug #76338: DataSourceService.deleteDataSource's force parameter was a complete no-op --
   // it never checked for live dependents before calling repository.removeDataSource, regardless
   // of the flag's value.
   @Test
   void deleteDataSourceRefusesWithoutForceWhenDependentsExist() throws Exception {
      AssetObject dependency = mock(AssetObject.class);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependency));

         assertThrows(DependencyException.class,
            () -> service.deleteDataSource(DS_ID, false, principal));
      }

      verify(repository, never()).removeDataSource(anyString(), anyBoolean());
   }

   @Test
   void deleteDataSourceStillSucceedsWithForceWhenDependentsExist() throws Exception {
      AssetObject dependency = mock(AssetObject.class);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependency));

         service.deleteDataSource(DS_ID, true, principal);
      }

      verify(repository).removeDataSource(DS_NAME, true);
   }

   @Test
   void deleteDataSourceSucceedsWithoutForceWhenNoDependentsExist() throws Exception {
      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class)) {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of());

         service.deleteDataSource(DS_ID, false, principal);
      }

      verify(repository).removeDataSource(DS_NAME, false);
   }

   @Test
   void deleteDataSourceAuditRecordReportsFailureOnThrow() throws Exception {
      AssetObject dependency = mock(AssetObject.class);

      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class);
          MockedStatic<Audit> auditStatic = mockStatic(Audit.class))
      {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of(dependency));
         Audit audit = mock(Audit.class);
         auditStatic.when(Audit::getInstance).thenReturn(audit);

         assertThrows(DependencyException.class,
            () -> service.deleteDataSource(DS_ID, false, principal));

         ArgumentCaptor<ActionRecord> captor = ArgumentCaptor.forClass(ActionRecord.class);
         verify(audit).auditAction(captor.capture(), eq(principal));
         assertEquals(ActionRecord.ACTION_STATUS_FAILURE, captor.getValue().getActionStatus());
      }
   }

   @Test
   void deleteDataSourceAuditRecordReportsSuccessOnNormalCompletion() throws Exception {
      try(MockedStatic<DependencyTool> dependencyTool = mockStatic(DependencyTool.class);
          MockedStatic<Audit> auditStatic = mockStatic(Audit.class))
      {
         dependencyTool.when(() -> DependencyTool.getDependencies(anyString()))
            .thenReturn(List.of());
         Audit audit = mock(Audit.class);
         auditStatic.when(Audit::getInstance).thenReturn(audit);

         service.deleteDataSource(DS_ID, false, principal);

         ArgumentCaptor<ActionRecord> captor = ArgumentCaptor.forClass(ActionRecord.class);
         verify(audit).auditAction(captor.capture(), eq(principal));
         assertEquals(ActionRecord.ACTION_STATUS_SUCCESS, captor.getValue().getActionStatus());
      }
   }

   // Bug 76599, Gap 2a: createDataSourceFolder is the standalone counterpart to the
   // rename-side-effect folder auto-creation updateDataSource already performs -- both must share
   // the same underlying create-or-skip primitive.
   @Test
   void createDataSourceFolderCreatesMissingAncestorsAndTheLeaf() throws Exception {
      when(repository.getDataSourceFolder(anyString())).thenReturn(null);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      // Rebuild service with a SecurityEngine mock this test controls directly (the
      // @BeforeEach one always returns true for ResourceType.DATA_SOURCE only).
      DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      service = new DataSourceService(repository, securityEngine, databaseTypeService, ids);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("A/B"), eq(ResourceAction.WRITE)))
         .thenReturn(true);

      service.createDataSourceFolder("A/B", principal);

      ArgumentCaptor<DataSourceFolder> captor = ArgumentCaptor.forClass(DataSourceFolder.class);
      verify(repository, times(2)).updateDataSourceFolder(captor.capture(), isNull());
      List<String> createdNames = captor.getAllValues().stream()
         .map(DataSourceFolder::getFullName).collect(Collectors.toList());
      assertEquals(List.of("A", "A/B"), createdNames);
   }

   @Test
   void createDataSourceFolderSkipsAnAlreadyExistingLevel() throws Exception {
      DataSourceFolder existing = mock(DataSourceFolder.class);
      when(repository.getDataSourceFolder("A")).thenReturn(existing);
      when(repository.getDataSourceFolder("A/B")).thenReturn(null);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      service = new DataSourceService(repository, securityEngine, databaseTypeService, ids);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("A/B"), eq(ResourceAction.WRITE)))
         .thenReturn(true);

      service.createDataSourceFolder("A/B", principal);

      verify(repository, times(1)).updateDataSourceFolder(any(), isNull());
   }

   @Test
   void createDataSourceFolderThrowsWithoutWritePermission() throws Exception {
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      DatabaseTypeService databaseTypeService = mock(DatabaseTypeService.class);
      service = new DataSourceService(repository, securityEngine, databaseTypeService, ids);
      when(securityEngine.checkPermission(
         eq(principal), eq(ResourceType.DATA_SOURCE_FOLDER), eq("A"), eq(ResourceAction.WRITE)))
         .thenReturn(false);

      assertThrows(UnauthorizedAccessException.class,
         () -> service.createDataSourceFolder("A", principal));
      verify(repository, never()).updateDataSourceFolder(any(), any());
   }

   @Test
   void dataSourceFolderExistsReflectsRepository() throws Exception {
      when(repository.getDataSourceFolder("A")).thenReturn(mock(DataSourceFolder.class));
      when(repository.getDataSourceFolder("B")).thenReturn(null);

      assertTrue(service.dataSourceFolderExists("A"));
      assertFalse(service.dataSourceFolderExists("B"));
   }

   @Test
   void removeDataSourceFolderDelegatesToRepository() throws Exception {
      service.removeDataSourceFolder("A/B");

      verify(repository).removeDataSourceFolder("A/B");
   }

   private static final String DS_ID = "id1";
   private static final String DS_NAME = "MyDataSource";
   private static final String REAL_PASSWORD = "realSecretPassword";

   private XRepository repository;
   private MD5IdentifierGenerator ids;
   private JDBCDataSource ds;
   private String[] passwordHolder;
   private DataSourceService service;
   private Principal principal;
}
