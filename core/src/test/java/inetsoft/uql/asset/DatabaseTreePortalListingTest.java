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
package inetsoft.uql.asset;

import inetsoft.sree.security.ResourceType;
import inetsoft.test.*;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.util.IndexedStorage;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.portal.controller.database.DatabaseTreeService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77189: the portal Data tab's physical tree (DatabaseTreeService) must still be listed as
 * a portal data listing after portal_data became server-only, so a modeler without
 * PHYSICAL_TABLE ACCESS still sees the physical tables and the source is marked from-portal,
 * through the DatabaseModelUtil walk and the final listing.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DatabaseTreePortalListingTest {
   private static final String DS = "DS";

   private AbstractAssetEngine engine;
   private XRepository repository;
   private JDBCDataSource dataSource;
   private MockedStatic<XRepository> repositoryStatic;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      engine = mock(AbstractAssetEngine.class, withSettings()
         .useConstructor(null, null).defaultAnswer(CALLS_REAL_METHODS));
      doReturn(false).when(engine).checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), any());
      doReturn(true).when(engine).checkDataSourcePermission(anyString(), any());
      doReturn(true).when(engine).checkDataSourceFolderPermission(anyString(), any());
      doReturn(mock(IndexedStorage.class)).when(engine).getStorage(any());

      dataSource = mock(JDBCDataSource.class);
      when(dataSource.getName()).thenReturn(DS);
      when(dataSource.getFullName()).thenReturn(DS);
      when(dataSource.getType()).thenReturn("jdbc");
      when(dataSource.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ORACLE);
      when(dataSource.getDatabaseTypeString()).thenReturn(JDBCDataSource.ORACLE);

      repository = mock(XRepository.class);
      when(repository.getDataSource(DS)).thenReturn(dataSource);
      when(repository.getSubfolderNames(any())).thenReturn(new String[0]);
      when(repository.getSubDataSourceNames(any())).thenReturn(new String[] { DS });
      repositoryStatic = mockStatic(XRepository.class);
      repositoryStatic.when(XRepository::getRepository).thenReturn(repository);
   }

   @AfterEach
   void tearDown() {
      repositoryStatic.close();
   }

   @Test
   void dataTabListsPhysicalTablesWithoutPhysicalTableAccess() throws Exception {
      DatabaseTreeService service =
         new DatabaseTreeService(engine, mock(DataSourceService.class), repository);

      service.getDatabaseNodes(DS, false, false, principal);

      // the physical listing of the data source ran (not stopped by the PHYSICAL_TABLE gate)
      // and the source was marked from-portal, as before the fix
      verify(dataSource, atLeastOnce()).setFromPortal(true);
      verify(dataSource, never()).setFromPortal(false);
   }
}
