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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers 09-design-dialog-and-persistence.md A.1/A.2: {@code GET /datasource/tables}'s new
 * {@code nameContains}/{@code limit}/{@code cursor} parameters, dispatched through
 * {@link MetadataApiService#getDatabaseTables}.
 *
 * The JDBC-with-paging-params case is a guard, not a feature -- see A.5: JDBC paging is a
 * separate, larger change this round does not implement, so a paging request against a JDBC
 * source must fail loudly rather than silently fall back to the unpaged full listing.
 */
@Tag("core")
class MetadataApiServicePagingTest {

   private static final String JDBC_DS = "Examples/Orders";
   private static final String TABULAR_DS = "OData Source";

   @Test
   void getDatabaseTables_pagedParamsAgainstJdbcSource_throwsNamedGuardError() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(JDBC_DS)).thenReturn(mock(JDBCDataSource.class));

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq(JDBC_DS), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
         () -> service.getDatabaseTables(JDBC_DS, null, 50, null, mock(Principal.class)));

      assertNotNull(ex.getMessage());
      assertTrue(ex.getMessage().contains(JDBC_DS), "message must name the offending data source");
      assertTrue(ex.getMessage().toLowerCase().contains("jdbc"),
         "message must say why -- paging is not supported for JDBC sources yet");
      verifyNoInteractions(tabularCatalogService);
   }

   /**
    * The single most important property of this change: omitting all three paging parameters
    * against a JDBC source must still reach the full, pre-paging code path (and never touch
    * {@link TabularCatalogService}) -- i.e. the guard above must only fire when paging was
    * actually requested.
    */
   @Test
   void getDatabaseTables_noPagingParamsAgainstJdbcSource_doesNotThrowTheGuard() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(JDBC_DS)).thenReturn(mock(JDBCDataSource.class));

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq(JDBC_DS), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      // getMetaDataProvider() fails further down against a mock DataSourceService with no
      // stubbed model -- same seam MetadataApiServiceNonJdbcBranchTest relies on. The point here
      // is that the failure comes from THAT plumbing, not from the paging guard: an
      // IllegalArgumentException naming JDBC paging would mean the guard mis-fired.
      Exception ex = assertThrows(Exception.class,
         () -> service.getDatabaseTables(JDBC_DS, null, null, null, mock(Principal.class)));
      assertFalse(ex.getMessage() != null && ex.getMessage().toLowerCase().contains("paging"));

      verifyNoInteractions(tabularCatalogService);
   }

   @Test
   void getDatabaseTables_pagedParamsAgainstTabularSource_delegatesToPagedListTables()
      throws Exception
   {
      XRepository xrepository = mock(XRepository.class);
      XDataSource odataLikeDataSource = mock(XDataSource.class);
      when(odataLikeDataSource.getType()).thenReturn("OData");
      when(xrepository.getDataSource(TABULAR_DS)).thenReturn(odataLikeDataSource);

      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq(TABULAR_DS), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      DatasourceTablesResponse canned = new DatasourceTablesResponse();
      canned.setNextCursor("3");
      when(tabularCatalogService.listTables(TABULAR_DS, "ord", 25, "prevCursor"))
         .thenReturn(canned);

      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      DatasourceTablesResponse response = service.getDatabaseTables(
         TABULAR_DS, "ord", 25, "prevCursor", mock(Principal.class));

      assertSame(canned, response);
      verify(tabularCatalogService).listTables(TABULAR_DS, "ord", 25, "prevCursor");
      verify(tabularCatalogService, never()).listTables(TABULAR_DS);
   }
}
