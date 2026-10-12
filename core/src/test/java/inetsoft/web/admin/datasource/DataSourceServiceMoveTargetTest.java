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
package inetsoft.web.admin.datasource;

import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import inetsoft.report.internal.license.ElasticLicenseService;
import inetsoft.sree.SreeEnv;
import inetsoft.sree.internal.SUtil;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.util.audit.Audit;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.security.auth.ResourceExistsException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77673: the public data source API must not rename or move a data source onto the path of
 * a data source or a data source folder, or under the path of a data source. The data sources of
 * the folder would become additional connections of the data source. A rename reaches the
 * dependency transform, which gets its beans from the Spring context.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class,
                                  DataSourceServiceMoveTargetTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DataSourceServiceMoveTargetTest {
   @BeforeEach
   void setUp() throws Exception {
      sreeEnv = mockStatic(SreeEnv.class);
      tool = mockStatic(Tool.class, CALLS_REAL_METHODS);
      sutil = mockStatic(SUtil.class);
      sutil.when(() -> SUtil.getUserName(any())).thenReturn("alice");
      audit = mockStatic(Audit.class);
      audit.when(Audit::getInstance).thenReturn(mock(Audit.class));
      orgManagerStatic = mockStatic(OrganizationManager.class);
      orgManagerStatic.when(OrganizationManager::getInstance)
         .thenReturn(mock(OrganizationManager.class));

      stored = mock(JDBCDataSource.class);
      when(stored.getDataSourceNames()).thenReturn(new String[0]);
      when(stored.getType()).thenReturn(XDataSource.JDBC);
      when(stored.getFullName()).thenReturn("db");

      repository = mock(XRepository.class);
      when(repository.getDataSource("db")).thenReturn(stored);
      securityEngine = mock(SecurityEngine.class);
      when(securityEngine.isSecurityEnabled()).thenReturn(true);
      when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
      securityEngineStatic = mockStatic(SecurityEngine.class);
      securityEngineStatic.when(SecurityEngine::getSecurity).thenReturn(securityEngine);
      when(securityEngine.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      MD5IdentifierGenerator ids = mock(MD5IdentifierGenerator.class);
      when(ids.getName("id1")).thenReturn("db");
      registry = mock(DataSourceRegistry.class);
      when(registry.getDataSourceFullNames()).thenReturn(new String[] { "db" });
      when(registry.getDataSource("db")).thenReturn(stored);

      service = new DataSourceService(
         repository, securityEngine, mock(DatabaseTypeService.class), ids, registry);
      principal = mock(XPrincipal.class);
      when(principal.getName()).thenReturn(new IdentityID("alice", "orga").convertToKey());
   }

   @AfterEach
   void tearDown() {
      securityEngineStatic.close();
      orgManagerStatic.close();
      audit.close();
      sutil.close();
      tool.close();
      sreeEnv.close();
   }

   // a rename onto the path of a data source folder is refused before anything is changed
   @Test
   void renameOntoAFolderIsRefused() throws Exception {
      when(registry.isDataSourcePathInUse("folder")).thenReturn(true);

      ResourceExistsException ex = assertThrows(ResourceExistsException.class,
         () -> service.updateDataSource("id1", properties("folder"), principal));
      assertTrue(ex.getMessage().contains("folder"), ex.getMessage());

      verifyNothingChanged();
   }

   // a move under the path of a data source is refused, no folder is created next to it
   @Test
   void moveUnderADataSourceIsRefused() throws Exception {
      when(registry.getDataSourceAncestor("other/db")).thenReturn("other");

      ResourceExistsException ex = assertThrows(ResourceExistsException.class,
         () -> service.updateDataSource("id1", properties("other/db"), principal));
      assertTrue(ex.getMessage().contains("other"), ex.getMessage());

      verifyNothingChanged();
   }

   // a move to a free path in a new folder still works
   @Test
   void moveToAFreePathWorks() throws Exception {
      service.updateDataSource("id1", properties("newFolder/db"), principal);

      verify(repository).updateDataSourceFolder(any(), isNull());
      verify(stored).setName("newFolder/db");
      verify(repository).updateDataSource(stored, "db", false);
   }

   // an update that doesn't rename the data source is not checked, even if its own path is in use
   @Test
   void updateWithoutRenameIsNotChecked() throws Exception {
      when(registry.isDataSourcePathInUse(anyString())).thenReturn(true);
      when(registry.getDataSourceAncestor(anyString())).thenReturn("db");

      service.updateDataSource("id1", properties("db"), principal);

      verify(repository).updateDataSource(stored, "db", false);
   }

   private void verifyNothingChanged() throws Exception {
      verify(stored, never()).setName(any());
      verify(repository, never()).updateDataSourceFolder(any(), any());
      verify(repository, never()).updateDataSource(any(), any(), anyBoolean());
   }

   private static JdbcDataSourceProperties properties(String name) {
      JdbcDataSourceProperties properties = new JdbcDataSourceProperties();
      properties.setName(name);
      properties.setUrl("jdbc:h2:mem:test");
      properties.setDriver("org.h2.Driver");
      properties.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      properties.setRequireLogin(false);
      return properties;
   }

   private DataSourceService service;
   private JDBCDataSource stored;
   private XRepository repository;
   private DataSourceRegistry registry;
   private SecurityEngine securityEngine;
   private XPrincipal principal;
   private MockedStatic<SreeEnv> sreeEnv;
   private MockedStatic<Tool> tool;
   private MockedStatic<SUtil> sutil;
   private MockedStatic<Audit> audit;
   private MockedStatic<OrganizationManager> orgManagerStatic;
   private MockedStatic<SecurityEngine> securityEngineStatic;

   @Configuration
   static class Beans {
      @Bean
      public ElasticLicenseService elasticLicenseService() {
         return mock(ElasticLicenseService.class);
      }

      @Bean
      public RenameTransformHandler renameTransformHandler() {
         return mock(RenameTransformHandler.class);
      }

      @Bean
      public DependencyStorageService dependencyStorageService() {
         return mock(DependencyStorageService.class);
      }
   }
}
