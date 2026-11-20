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

import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.XNode;
import inetsoft.uql.XRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.util.XUtil;
import inetsoft.util.IndexedStorage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * J1.0 probe (design section 6, unknown c): does the database tree behind
 * {@code DatabaseTreeService.getDatabaseNodes} and {@code LogicalModelTreeService} need READ on
 * the data source? That service lists through
 * {@link AbstractAssetEngine#getPortalDataEntries}, so the engine is probed directly with the
 * source's permission denied. Same harness as {@code PortalDataListingTest}. The test pins the
 * observed behavior and prints it; the verdict is recorded in {@code 04-build-j1.md}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class DataModelBrowseReadProbeTest {
   private static final String DS = "DS";
   private static final AssetEntry.Selector SELECTOR = new AssetEntry.Selector(
      AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL, AssetEntry.Type.FOLDER);

   private AbstractAssetEngine engine;
   private XRepository repository;
   private MockedStatic<XRepository> repositoryStatic;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      engine = mock(AbstractAssetEngine.class, withSettings()
         .useConstructor(null, null).defaultAnswer(CALLS_REAL_METHODS));
      // the caller holds no READ on the data source and no access to physical tables
      doReturn(false).when(engine).checkDataSourcePermission(anyString(), any());
      doReturn(false).when(engine).checkPermission(
         any(Principal.class), any(ResourceType.class), anyString(), any());
      doReturn(mock(IndexedStorage.class)).when(engine).getStorage(any());

      JDBCDataSource dataSource = mock(JDBCDataSource.class);
      when(dataSource.getName()).thenReturn(DS);
      when(dataSource.getFullName()).thenReturn(DS);
      when(dataSource.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ORACLE);
      when(dataSource.getDatabaseTypeString()).thenReturn(JDBCDataSource.ORACLE);

      // the table listing of the source: one table named ORDERS
      XNode root = new XNode(DS);
      XNode table = new XNode("ORDERS");
      table.setAttribute("type", "TABLE");
      root.addChild(table);
      repository = mock(XRepository.class);
      when(repository.getDataSource(DS)).thenReturn(dataSource);
      when(repository.getMetaData(any(), any(), any(), anyBoolean(), any())).thenReturn(root);
      repositoryStatic = mockStatic(XRepository.class);
      repositoryStatic.when(XRepository::getRepository).thenReturn(repository);
   }

   @AfterEach
   void tearDown() {
      repositoryStatic.close();
   }

   @Test
   void databaseTreeBrowseRequiresReadOnSource() throws Exception {
      AssetEntry entry = new AssetEntry(
         AssetRepository.QUERY_SCOPE, AssetEntry.Type.DATA_SOURCE, DS, null);
      entry.setProperty("prefix", DS);
      entry.setProperty(AssetEntry.PATH_ARRAY, DS);
      entry.setProperty(XUtil.PORTAL_DATA, "true");

      try(MockedStatic<SQLTypes> sqlTypes = mockStatic(SQLTypes.class)) {
         sqlTypes.when(() -> SQLTypes.getSQLTypes(any())).thenReturn(mock(SQLTypes.class));
         AssetEntry[] entries = AbstractAssetEngine.getPortalDataEntries(
            engine, entry, principal, ResourceAction.READ, SELECTOR);
         String[] names = Arrays.stream(entries).map(AssetEntry::getName).toArray(String[]::new);
         System.out.println("PROBE(c) caller without READ on the source lists: " +
                            Arrays.toString(names));

         // premise (design 6 (c), read from AbstractAssetEngine.getQueryEntries/getPhysicalEntries):
         // the READ argument is not consulted for a data source node, so the tables are listed
         assertArrayEquals(new String[] { "ORDERS" }, names,
                           "premise: the portal listing of a source ignores the READ argument");
      }
   }
}
