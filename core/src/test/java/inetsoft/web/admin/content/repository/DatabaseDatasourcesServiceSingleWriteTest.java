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
import inetsoft.test.*;
import inetsoft.uql.XDataSourceWrapper;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.sync.RenameTransformHandler;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.JDBCUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.Tool;
import inetsoft.util.audit.ActionRecord;
import inetsoft.web.admin.content.database.DatabaseDefinition;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.database.types.AccessDatabaseType;
import inetsoft.web.admin.content.database.types.CustomDatabaseType;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.security.ConnectionStatus;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.session.IgniteSessionRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #82414: a new data source is stored once, already filled in, and a save never writes back
 * a copy of the data source that the registry cache kept from before the save.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class,
                                  DatabaseDatasourcesServiceAdditionalNodeSaveTest.Beans.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class DatabaseDatasourcesServiceSingleWriteTest {
   private static final String URL = "jdbc:derby:memory:bug82414;create=true";
   private static final String URL2 = "jdbc:derby:memory:bug82414b;create=true";
   private static final String DRIVER = "org.apache.derby.jdbc.EmbeddedDriver";

   @Autowired
   private DataSourceRegistry registry;
   @Autowired
   private XRepository repository;
   private XRepository spyRepository;
   private DatabaseDatasourcesService service;
   private Principal principal;

   @BeforeEach
   void setUp() throws Exception {
      registry.init();
      spyRepository = spy(repository);
      SecurityEngine security = mock(SecurityEngine.class);
      when(security.checkPermission(any(), any(), anyString(), any())).thenReturn(true);
      // the creator may only create data sources, not write to the folder
      when(security.checkPermission(any(), eq(ResourceType.DATA_SOURCE_FOLDER), anyString(),
                                    any())).thenReturn(false);
      service = new DatabaseDatasourcesService(
         new DatabaseTypeService(List.of(new CustomDatabaseType(), new AccessDatabaseType())),
         security, mock(DatabaseSettingsService.class), spyRepository,
         mock(ResourcePermissionService.class), mock(DataSourceStatusService.class),
         mock(IgniteSessionRepository.class), registry, mock(RenameTransformHandler.class));
      principal = new SRPrincipal(new IdentityID("alice", Organization.getDefaultOrganizationID()),
                                  new IdentityID[0], new String[0],
                                  Organization.getDefaultOrganizationID(),
                                  Tool.getSecureRandom().nextLong());
   }

   // also covers a user that may only create data sources, see setUp
   @Test
   void createStoresTheFilledDataSourceOnce() throws Exception {
      assertNull(create("once", URL));

      ArgumentCaptor<JDBCDataSource> stored = ArgumentCaptor.forClass(JDBCDataSource.class);
      verify(spyRepository, times(1)).updateDataSource(stored.capture(), any(), anyBoolean());
      assertEquals(URL, stored.getValue().getURL());
      assertEquals(DRIVER, stored.getValue().getDriver());

      registry.clearCache();
      JDBCDataSource read = (JDBCDataSource) registry.getDataSource("once");
      assertNotNull(read);
      assertEquals(URL, read.getURL());
      assertEquals(DRIVER, read.getDriver());
   }

   @Test
   void failedStoreLeavesNoDataSource() throws Exception {
      IllegalStateException failure = new IllegalStateException("simulated");
      // the source is stored, then the call fails
      doAnswer(invocation -> {
         invocation.callRealMethod();
         throw failure;
      }).when(spyRepository).updateDataSource(any(), any(), anyBoolean());

      IllegalStateException thrown =
         assertThrows(IllegalStateException.class, () -> create("failed", URL));

      assertSame(failure, thrown);
      registry.clearCache();
      assertNull(registry.getDataSource("failed"));
      assertFalse(registry.containDatasource("failed"));
   }

   // the registry cache holds the data source from before the save, as it does until the
   // asynchronous clear event arrives
   @Test
   void editDoesNotWriteBackTheCachedOldCopy() throws Exception {
      create("edited", URL);
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE,
                                        AssetEntry.Type.DATA_SOURCE, "edited", null);
      JDBCDataSource old = (JDBCDataSource) Tool.clone(registry.getDataSource("edited"));
      doAnswer(invocation -> {
         invocation.callRealMethod();
         registry.setCache(entry, new XDataSourceWrapper(old));
         return null;
      }).when(spyRepository).updateDataSource(any(), any(), anyBoolean());

      DatabaseDefinition definition = definition("edited", URL2);
      definition.setOldName("edited");
      assertNull(service.saveDatabase("edited", DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition).build(),
                                      ActionRecord.ACTION_NAME_EDIT, principal));

      registry.clearCache();
      assertEquals(URL2, ((JDBCDataSource) registry.getDataSource("edited")).getURL());
   }

   private ConnectionStatus create(String name, String url) throws Exception {
      return service.saveDatabase("", DataSourceSettingsModel.builder()
         .uploadEnabled(false).dataSource(definition(name, url)).build(),
                                  ActionRecord.ACTION_NAME_CREATE, principal);
   }

   private static DatabaseDefinition definition(String name, String url) {
      JDBCDataSource dataSource = new JDBCDataSource();
      dataSource.setName(name);
      dataSource.setCustom(true);
      dataSource.setCustomEditMode(true);
      dataSource.setDriver(DRIVER);
      dataSource.setURL(url);
      dataSource.setCustomUrl(url);
      return JDBCUtil.buildDatabaseDefinition(
         dataSource, JDBCUtil.getJDBCDatabaseType(CustomDatabaseType.TYPE));
   }
}
