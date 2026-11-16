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
package inetsoft.uql.util;

/*
 * Bug #80417: a choice query "[<ws identifier>]^[<col>]" bound to a worksheet that no longer
 * exists reached ColumnCache.getAssetData, where ReportWorksheetProcessor.execute returned null
 * ("Worksheet not found") and tbl.getColCount() threw a NullPointerException. The cache and the
 * processor are the real classes; only the asset storage answer (sheet absent) is stubbed.
 */

import inetsoft.report.LibManagerProvider;
import inetsoft.report.composition.execution.ReportWorksheetProcessor;
import inetsoft.sree.internal.cluster.Cluster;
import inetsoft.sree.security.*;
import inetsoft.test.*;
import inetsoft.uql.VariableTable;
import inetsoft.uql.asset.*;
import inetsoft.uql.asset.internal.AssetUtil;
import inetsoft.uql.service.DataSourceRegistry;
import inetsoft.util.IndexedStorage;
import inetsoft.report.composition.WorksheetService;
import inetsoft.web.composer.model.BrowseDataModel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
   BaseTestConfiguration.class, SwapperTestConfiguration.class,
   ColumnCacheMissingWorksheetTest.WsServiceConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ColumnCacheMissingWorksheetTest {
   private MockedStatic<AssetUtil> assetUtil;
   private ColumnCache cache;

   @BeforeEach
   void setUp() {
      AbstractAssetEngine engine = new MissingSheetAssetEngine();
      assetUtil = Mockito.mockStatic(AssetUtil.class, Mockito.CALLS_REAL_METHODS);
      assetUtil.when(() -> AssetUtil.getAssetRepository(false)).thenReturn(engine);
      cache = new ColumnCache(mock(DataSourceRegistry.class), null);
   }

   @AfterEach
   void tearDown() {
      if(assetUtil != null) {
         assetUtil.close();
      }
   }

   @Test
   void missingWorksheet_returnsEmptyModel() throws Exception {
      AssetEntry entry = new AssetEntry(AssetRepository.GLOBAL_SCOPE, AssetEntry.Type.WORKSHEET,
                                        "missingWs80417", null, "host-org");
      BrowseDataModel data = assertDoesNotThrow(() -> cache.getColumnData(
         new ReportWorksheetProcessor(), entry, "col", null, new VariableTable()));

      assertNotNull(data);
      assertEquals(0, data.values().length);
   }

   /** Storage answer for an absent sheet: no sheet found. */
   private static class MissingSheetAssetEngine extends AbstractAssetEngine {
      private static final IndexedStorage storage = mock(IndexedStorage.class);

      MissingSheetAssetEngine() {
         super((LibManagerProvider) null, (Cluster) null);
         istore = storage;
      }

      @Override
      public IndexedStorage getStorage(AssetEntry entry) {
         return storage;
      }

      @Override
      protected IndexedStorage[] getStorages() {
         return new IndexedStorage[] { storage };
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type, String resource,
                                     EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      public boolean checkPermission(Principal principal, ResourceType type,
                                     IdentityID resource, EnumSet<ResourceAction> action)
      {
         return true;
      }

      @Override
      protected boolean checkDataModelFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryFolderPermission(String folder, String source, Principal user) {
         return true;
      }

      @Override
      protected boolean checkQueryPermission(String query, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourcePermission(String dname, Principal user) {
         return true;
      }

      @Override
      protected boolean checkDataSourceFolderPermission(String folder, Principal user) {
         return true;
      }
   }

   @Configuration
   static class WsServiceConfiguration {
      @Bean
      public WorksheetService worksheetService() {
         return mock(WorksheetService.class);
      }
   }
}
