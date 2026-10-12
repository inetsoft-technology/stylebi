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
import inetsoft.uql.erm.vpm.VpmProcessor;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.uql.jdbc.util.SQLTypes;
import inetsoft.uql.schema.XTypeNode;
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
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #77189: the portal_data and ignoreVpm properties of a query-scope entry are client
 * controlled on any request body entry, so the asset engine honors them only inside
 * {@link AbstractAssetEngine#getPortalDataEntries}, the server-side portal data tab listing.
 * The additional connection that a portal listing switches to also requires READ.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, SwapperTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class PortalDataListingTest {
   private static final String DS = "DS";
   private static final AssetEntry.Selector SELECTOR = new AssetEntry.Selector(
      AssetEntry.Type.DATA, AssetEntry.Type.PHYSICAL, AssetEntry.Type.FOLDER);

   private AbstractAssetEngine engine;
   private XRepository repository;
   private JDBCDataSource dataSource;
   private JDBCDataSource additionalSource;
   private MockedStatic<XRepository> repositoryStatic;
   private final Principal principal = () -> "bob";

   @BeforeEach
   void setUp() throws Exception {
      engine = mock(AbstractAssetEngine.class, withSettings()
         .useConstructor(null, null).defaultAnswer(CALLS_REAL_METHODS));
      doReturn(false).when(engine).checkPermission(
         any(Principal.class), eq(ResourceType.PHYSICAL_TABLE), eq("*"), any());
      doReturn(true).when(engine).checkDataSourcePermission(anyString(), any());
      doReturn(mock(IndexedStorage.class)).when(engine).getStorage(any());

      dataSource = mock(JDBCDataSource.class);
      when(dataSource.getName()).thenReturn(DS);
      when(dataSource.getFullName()).thenReturn(DS);
      when(dataSource.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ORACLE);
      when(dataSource.getDatabaseTypeString()).thenReturn(JDBCDataSource.ORACLE);
      additionalSource = mock(JDBCDataSource.class);
      when(additionalSource.getName()).thenReturn("conn2");
      when(additionalSource.getFullName()).thenReturn(DS);
      when(additionalSource.getDatabaseType()).thenReturn(JDBCDataSource.JDBC_ORACLE);
      when(additionalSource.getDatabaseTypeString()).thenReturn(JDBCDataSource.ORACLE);
      when(dataSource.getDataSource("conn2")).thenReturn(additionalSource);

      repository = mock(XRepository.class);
      when(repository.getDataSource(DS)).thenReturn(dataSource);
      repositoryStatic = mockStatic(XRepository.class);
      repositoryStatic.when(XRepository::getRepository).thenReturn(repository);
   }

   @AfterEach
   void tearDown() {
      repositoryStatic.close();
   }

   private static AssetEntry entry(AssetEntry.Type type, String path) {
      AssetEntry entry = new AssetEntry(AssetRepository.QUERY_SCOPE, type, path, null);
      entry.setProperty("prefix", DS);
      entry.setProperty(AssetEntry.PATH_ARRAY, path.replace("/", AssetEntry.PATH_ARRAY_SEPARATOR));
      entry.setProperty(XUtil.PORTAL_DATA, "true");
      return entry;
   }

   @Test
   void forgedPortalDataDoesNotSkipPhysicalTableAccess() throws Exception {
      engine.getEntries(entry(AssetEntry.Type.DATA_SOURCE, DS), principal,
                        ResourceAction.READ, SELECTOR);

      verify(repository, never()).getDataSource(DS);
   }

   @Test
   void portalDataListingSkipsPhysicalTableAccess() throws Exception {
      AbstractAssetEngine.getPortalDataEntries(
         engine, entry(AssetEntry.Type.DATA_SOURCE, DS), principal, ResourceAction.READ, SELECTOR);

      verify(repository).getDataSource(DS);
      verify(dataSource).setFromPortal(true);
   }

   @Test
   void forgedPortalDataDoesNotMarkTheSourceFromPortal() throws Exception {
      engine.getEntries(entry(AssetEntry.Type.PHYSICAL_FOLDER, DS + "/TABLE"), principal,
                        ResourceAction.READ, SELECTOR);

      verify(dataSource).setFromPortal(false);
      verify(dataSource, never()).setFromPortal(true);
   }

   @Test
   void portalListingDeniesUnreadableAdditionalConnection() throws Exception {
      doReturn(false).when(engine).checkDataSourcePermission(eq(DS + "::conn2"), any());
      AssetEntry entry = entry(AssetEntry.Type.DATA_SOURCE, DS);
      entry.setProperty("additional", "conn2");

      AbstractAssetEngine.getPortalDataEntries(
         engine, entry, principal, ResourceAction.READ, SELECTOR);

      verify(dataSource, never()).getDataSource("conn2");
      verify(dataSource, never()).setFromPortal(anyBoolean());
   }

   @Test
   void portalListingAllowsReadableAdditionalConnection() throws Exception {
      AssetEntry entry = entry(AssetEntry.Type.DATA_SOURCE, DS);
      entry.setProperty("additional", "conn2");

      AbstractAssetEngine.getPortalDataEntries(
         engine, entry, principal, ResourceAction.READ, SELECTOR);

      verify(dataSource).getDataSource("conn2");
      verify(additionalSource).setFromPortal(true);
   }

   @Test
   void listingFlagDoesNotLeakPastTheCall() throws Exception {
      AbstractAssetEngine.getPortalDataEntries(
         engine, entry(AssetEntry.Type.DATA_SOURCE, DS), principal, ResourceAction.READ, SELECTOR);
      clearInvocations(repository);

      engine.getEntries(entry(AssetEntry.Type.DATA_SOURCE, DS), principal,
                        ResourceAction.READ, SELECTOR);

      verify(repository, never()).getDataSource(DS);
   }

   // ---- ignoreVpm: VPM hidden columns of a physical table ----

   private String[] listColumns(boolean portalListing) throws Exception {
      when(repository.getMetaData(any(), any(), any(XNode.class), anyBoolean(), any()))
         .thenAnswer(inv -> {
            XNode request = inv.getArgument(2);
            Object type = request.getAttribute("type");

            if("TABLETYPES".equals(type)) {
               XNode root = new XNode();
               root.addChild(new XNode("TABLE"));
               return root;
            }
            else if("SCHEMAS".equals(type)) {
               XNode schemas = new XNode();
               schemas.addChild(new XNode("T"));
               return schemas;
            }
            else if("DBPROPERTIES".equals(type)) {
               return new XNode();
            }

            XNode table = new XNode("T");
            XNode result = new XNode("Result");
            result.addChild(new XTypeNode("c1"));
            result.addChild(new XTypeNode("c2"));
            table.addChild(result);
            return table;
         });

      AssetEntry entry = entry(AssetEntry.Type.PHYSICAL_TABLE, DS + "/TABLE");
      entry.setProperty("source", "T");
      entry.setProperty("ignoreVpm", "true");

      VpmProcessor vpm = mock(VpmProcessor.class);
      BiFunction<String, String, Boolean> hideC2 = (table, column) -> "c2".equals(column);
      when(vpm.getHiddenColumnsSelector(any(), any(), any(), any(), any(), any()))
         .thenReturn(hideC2);

      SQLTypes sqlTypes = mock(SQLTypes.class);
      when(sqlTypes.getQualifiedTableNode(any(), anyBoolean(), anyBoolean(), any(), any(), any(),
                                          any())).thenReturn(new XNode("T"));

      try(MockedStatic<VpmProcessor> vpmStatic = mockStatic(VpmProcessor.class);
          MockedStatic<SQLTypes> sqlTypesStatic = mockStatic(SQLTypes.class))
      {
         vpmStatic.when(VpmProcessor::getInstance).thenReturn(vpm);
         sqlTypesStatic.when(() -> SQLTypes.getSQLTypes(any())).thenReturn(sqlTypes);
         AssetEntry[] columns = portalListing ?
            AbstractAssetEngine.getPortalDataEntries(
               engine, entry, principal, ResourceAction.READ, SELECTOR) :
            engine.getEntries(entry, principal, ResourceAction.READ, SELECTOR);

         return Arrays.stream(columns).map(AssetEntry::getName).toArray(String[]::new);
      }
   }

   @Test
   void forgedIgnoreVpmStillHidesColumns() throws Exception {
      assertArrayEquals(new String[] { "c1" }, listColumns(false));
   }

   @Test
   void portalListingIgnoreVpmShowsAllColumns() throws Exception {
      assertArrayEquals(new String[] { "c1", "c2" }, listColumns(true));
   }
}
