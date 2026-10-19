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
package inetsoft.web.admin.ai.datasource;

import inetsoft.sree.security.*;
import inetsoft.storage.KeyValueStorageManager;
import inetsoft.test.*;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.asset.sync.DependencyStorageService;
import inetsoft.uql.erm.XDataModel;
import inetsoft.uql.erm.XLogicalModel;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.datasource.*;
import inetsoft.web.admin.general.DatabaseSettingsService;
import inetsoft.web.admin.idgen.MD5IdentifierGenerator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #76338: a force=false delete of a data source must be refused while a sheet uses one of
 * the data source's logical models. A sheet bound to a logical model is recorded only under that
 * model's dependency key, not under the data source's own key, so both the Public API delete
 * ({@link DataSourceService#deleteDataSource}) and the admin-chat delete preflight
 * ({@link DataSourceChangePlanService#resolve}) missed it and deleted the data source.
 *
 * <p>The dependency storage is the real {@link DependencyStorageService} and the worksheet's
 * dependencies are written by the real {@link LocalDependencyHandler}, so the keys looked up are
 * the keys a saved worksheet actually has. The worksheet uses the second logical model, so a
 * check that only looks at the first model doesn't pass.</p>
 */
@Tag("core")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(
   classes = { BaseTestConfiguration.class, DataSourceDeleteLogicalModelDependentsTest.Beans.class },
   initializers = ConfigurationContextInitializer.class)
@SreeHome
@TestPropertySource(properties = { "mock.license.manager=true" })
class DataSourceDeleteLogicalModelDependentsTest {
   @BeforeEach
   void setUp(TestInfo info) throws Exception {
      // the context is cached across test methods and the dependency storage is real, so each
      // method uses its own data source and sheet names
      dsName = "b76338" + info.getTestMethod().orElseThrow().getName();
      wsEntry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                               dsName + " WS", null);

      reset(repository, registry);
      ds = mock(JDBCDataSource.class);
      when(ds.getType()).thenReturn(XDataSource.JDBC);
      when(ds.getFullName()).thenReturn(dsName);
      when(ds.getName()).thenReturn(dsName);
      when(repository.getDataSource(dsName)).thenReturn(ds);
      when(registry.getDataSource(dsName)).thenReturn(ds);

      XDataModel dataModel = mock(XDataModel.class);
      when(dataModel.getDataSource()).thenReturn(dsName);
      when(dataModel.getLogicalModelNames()).thenReturn(new String[] { LM1, LM2 });
      when(dataModel.getPartitionNames()).thenReturn(new String[0]);
      when(dataModel.getVirtualPrivateModelNames()).thenReturn(new String[0]);

      for(String name : new String[] { LM1, LM2 }) {
         XLogicalModel model = mock(XLogicalModel.class);
         when(model.getDataSource()).thenReturn(dsName);
         when(model.getName()).thenReturn(name);
         when(model.getLogicalModelNames()).thenReturn(new String[0]);
         when(dataModel.getLogicalModel(name)).thenReturn(model);
      }

      when(repository.getDataModel(dsName)).thenReturn(dataModel);
      when(registry.getDataModel(dsName)).thenReturn(dataModel);

      ids = mock(MD5IdentifierGenerator.class);
      when(ids.getName(DS_ID)).thenReturn(dsName);
      SecurityEngine securityEngine = mock(SecurityEngine.class);
      when(securityEngine.checkPermission(
         any(Principal.class), any(ResourceType.class), anyString(), any(ResourceAction.class)))
         .thenReturn(true);
      dataSourceService = spy(new DataSourceService(
         repository, securityEngine, mock(DatabaseTypeService.class), ids, registry));

      // the admin-chat preflight resolves the data source through the wrapped service
      DataSourceDescription description = new DataSourceDescription();
      description.setId(DS_ID);
      description.setName(dsName);
      description.setType("jdbc");
      DataSourceList list = new DataSourceList();
      list.setDataSources(List.of(description));
      doReturn(list).when(dataSourceService).getDataSources(dsName, principal);
      doReturn(jdbcProperties()).when(dataSourceService).getDataSource(DS_ID, principal);
      planService = new DataSourceChangePlanService(
         dataSourceService, mock(DatabaseSettingsService.class));

      // the worksheet exists, so a check that leaves out missing sheets still reports it
      assetRepository = mock(AssetRepository.class);
      when(assetRepository.containsEntry(any(AssetEntry.class))).thenReturn(true);
      AssetUtil.setAssetRepository(false, assetRepository);

      handler = new LocalDependencyHandler(repository);
   }

   @AfterEach
   void tearDown() {
      AssetUtil.setAssetRepository(false, null);
   }

   @Test
   void publicApiDeleteRefusesWhenAWorksheetUsesALogicalModel() throws Exception {
      saveWorksheet(XSourceInfo.MODEL, LM2);

      DependencyException ex = assertThrows(DependencyException.class,
         () -> dataSourceService.deleteDataSource(DS_ID, false, principal));

      assertNamesWorksheet(ex.getEntries());
      verify(repository, never()).removeDataSource(anyString(), anyBoolean());
   }

   @Test
   void publicApiDeleteWithForceStillDeletesWhenAWorksheetUsesALogicalModel() throws Exception {
      saveWorksheet(XSourceInfo.MODEL, LM2);

      dataSourceService.deleteDataSource(DS_ID, true, principal);

      verify(repository).removeDataSource(dsName, true);
   }

   @Test
   void adminChatDeleteRefusesWhenAWorksheetUsesALogicalModel() throws Exception {
      saveWorksheet(XSourceInfo.MODEL, LM2);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(deleteRequest(false), principal));

      assertTrue(ex.getMessage().contains(wsEntry.getPath()), ex.getMessage());
   }

   @Test
   void adminChatDeleteWithForceIsPlannedWhenAWorksheetUsesALogicalModel() throws Exception {
      saveWorksheet(XSourceInfo.MODEL, LM2);

      assertDoesNotThrow(() -> planService.resolve(deleteRequest(true), principal));
   }

   /** Control: a worksheet on a physical table of the data source was refused before the fix. */
   @Test
   void publicApiDeleteRefusesWhenAWorksheetUsesAPhysicalTable() throws Exception {
      saveWorksheet(XSourceInfo.PHYSICAL_TABLE, "CUSTOMERS");

      DependencyException ex = assertThrows(DependencyException.class,
         () -> dataSourceService.deleteDataSource(DS_ID, false, principal));

      assertNamesWorksheet(ex.getEntries());
      verify(repository, never()).removeDataSource(anyString(), anyBoolean());
   }

   @Test
   void adminChatDeleteRefusesWhenAWorksheetUsesAPhysicalTable() throws Exception {
      saveWorksheet(XSourceInfo.PHYSICAL_TABLE, "CUSTOMERS");

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> planService.resolve(deleteRequest(false), principal));

      assertTrue(ex.getMessage().contains(wsEntry.getPath()), ex.getMessage());
   }

   /** Control: the logical models alone (no sheet using them) don't block the delete. */
   @Test
   void deleteWithoutDependentsProceeds() throws Exception {
      assertDoesNotThrow(() -> planService.resolve(deleteRequest(false), principal));

      dataSourceService.deleteDataSource(DS_ID, false, principal);

      verify(repository).removeDataSource(dsName, false);
   }

   private void saveWorksheet(int sourceType, String source) throws Exception {
      Worksheet ws = new Worksheet();
      BoundTableAssembly table = new BoundTableAssembly(ws, "Customers");
      table.setSourceInfo(new SourceInfo(sourceType, dsName, source));
      ws.addAssembly(table);
      handler.updateSheetDependencies(ws, wsEntry, true, false);
   }

   private void assertNamesWorksheet(List<?> entries) {
      assertTrue(entries.stream().anyMatch(
                    e -> e instanceof AssetEntry &&
                       wsEntry.toIdentifier().equals(((AssetEntry) e).toIdentifier())),
                 "dependents: " + entries);
   }

   private DataSourceChangePlanRequest deleteRequest(boolean force) {
      DataSourceChangeRequest change = new DataSourceChangeRequest();
      change.setVerb(DataSourceChangeRequest.VERB_DELETE);
      change.setName(dsName);
      change.setForce(force);
      DataSourceChangePlanRequest request = new DataSourceChangePlanRequest();
      request.setTask("delete the " + dsName + " data source");
      request.setChanges(List.of(change));
      return request;
   }

   private JdbcDataSourceProperties jdbcProperties() {
      JdbcDataSourceProperties p = new JdbcDataSourceProperties();
      p.setId(DS_ID);
      p.setName(dsName);
      p.setUrl("jdbc:h2:mem:test");
      p.setDriver("org.h2.Driver");
      p.setDefaultDatabase("APP");
      p.setTableName(JdbcDataSourceProperties.DEFAULT_OPTION);
      p.setIsolation(JdbcDataSourceProperties.IsolationLevel.DEFAULT);
      p.setAnsiJoin(false);
      p.setRequireLogin(false);
      p.setUseCredentialId(false);
      return p;
   }

   @Configuration
   static class Beans {
      // BaseTestConfiguration's real SecurityEngine bootstraps security config at startup,
      // which this test doesn't need (see DataSourceServiceTest)
      @Bean
      @Primary
      public SecurityEngine securityEngine() {
         SecurityEngine securityEngine = mock(SecurityEngine.class);
         when(securityEngine.getSecurityProvider()).thenReturn(mock(SecurityProvider.class));
         return securityEngine;
      }

      @Bean
      XRepository xRepository() {
         return mock(XRepository.class);
      }

      @Bean
      DataSourceRegistry dataSourceRegistry() {
         return mock(DataSourceRegistry.class);
      }

      // the real storage over BaseTestConfiguration's key-value engine; its constructor is
      // package-private, the way component scan creates it in production
      @Bean
      DependencyStorageService dependencyStorageService(KeyValueStorageManager manager)
         throws Exception
      {
         Constructor<DependencyStorageService> constructor =
            DependencyStorageService.class.getDeclaredConstructor(KeyValueStorageManager.class);
         constructor.setAccessible(true);
         return constructor.newInstance(manager);
      }
   }

   private static final String DS_ID = "id76338";
   private static final String LM1 = "LM1";
   private static final String LM2 = "LM2";

   private final Principal principal = new SRPrincipal(
      new IdentityID("admin", "host-org"), new IdentityID[0], new String[0], "host-org", 1L);

   @Autowired
   private XRepository repository;
   @Autowired
   private DataSourceRegistry registry;

   private String dsName;
   private AssetEntry wsEntry;
   private JDBCDataSource ds;
   private MD5IdentifierGenerator ids;
   private DataSourceService dataSourceService;
   private DataSourceChangePlanService planService;
   private AssetRepository assetRepository;
   private LocalDependencyHandler handler;
}
