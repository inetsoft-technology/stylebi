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
package inetsoft.web.wiz.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Charter reverse assertion: the new non-JDBC branch must be reachable ONLY when
 * {@code getJDBCDatasource} throws {@link UnsupportedDatasourceException}. For a real JDBC data
 * source, {@link TabularCatalogService} must never be invoked — the JDBC success path is
 * unchanged, not merely "still produces the same output".
 *
 * A mocked {@link JDBCDataSource} plus a permission-denied downstream lookup lets both
 * {@code getMetaData} and {@code getDatabaseTables} advance past the JDBC branch point without
 * requiring a full metadata-provider stack; either way, {@link TabularCatalogService} must see
 * zero interactions.
 */
@Tag("core")
class MetadataApiServiceNonJdbcBranchTest {

   @Test
   void getMetaData_forJdbcDataSource_neverCallsTabularCatalogService() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      JDBCDataSource jdbcDataSource = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("Examples/Orders")).thenReturn(jdbcDataSource);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      GetDatabaseTableMetaRequest request = new GetDatabaseTableMetaRequest();
      request.setDsName("Examples/Orders");
      request.setTableName("CUSTOMERS");

      // getMetaDataProvider() will fail against a mocked DataSourceService with no stubbed
      // model, so the call is expected to throw further down the JDBC path — the point of this
      // test is what got called on the way, not the final result.
      assertThrows(Exception.class, () -> service.getMetaData(request, mock(Principal.class)));

      verifyNoInteractions(tabularCatalogService);
   }

   @Test
   void getDatabaseTables_forJdbcDataSource_neverCallsTabularCatalogService() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      JDBCDataSource jdbcDataSource = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("Examples/Orders")).thenReturn(jdbcDataSource);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Examples/Orders"), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      assertThrows(Exception.class,
         () -> service.getDatabaseTables("Examples/Orders", mock(Principal.class)));

      verifyNoInteractions(tabularCatalogService);
   }

   @Test
   void getDatabaseTables_forNonJdbcDataSource_delegatesToTabularCatalogService() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      XDataSource odataLikeDataSource = mock(XDataSource.class);
      when(odataLikeDataSource.getType()).thenReturn("OData");
      when(xrepository.getDataSource("OData Source")).thenReturn(odataLikeDataSource);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("OData Source"), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      DatasourceTablesResponse canned = new DatasourceTablesResponse();
      when(tabularCatalogService.listTables("OData Source")).thenReturn(canned);

      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      DatasourceTablesResponse response =
         service.getDatabaseTables("OData Source", mock(Principal.class));

      assertNotNull(response);
      verify(tabularCatalogService).listTables("OData Source");
   }
}
