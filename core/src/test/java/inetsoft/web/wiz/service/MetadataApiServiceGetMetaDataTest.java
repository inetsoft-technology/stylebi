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
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers the authorization gate added to {@link MetadataApiService#getMetaData}: a
 * {@code READ} permission check on the requested data source must run before any
 * metadata/schema lookup, and must deny access with a {@link SecurityException} when the
 * check fails.
 */
@Tag("core")
class MetadataApiServiceGetMetaDataTest {

   private MetadataApiService createService(DataSourceService dataSourceService,
                                             XRepository xrepository)
   {
      return new MetadataApiService(
         xrepository, dataSourceService, mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper());
   }

   @Test
   void getMetaData_throwsSecurityExceptionAndSkipsLookupWhenPermissionDenied() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      GetDatabaseTableMetaRequest request = new GetDatabaseTableMetaRequest();
      request.setDsName("Examples/Orders");
      request.setTableName("CUSTOMERS");

      when(dataSourceService.checkPermission(
         eq("Examples/Orders"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(false);

      MetadataApiService service = createService(dataSourceService, xrepository);

      SecurityException ex = assertThrows(SecurityException.class,
         () -> service.getMetaData(request, principal));

      assertTrue(ex.getMessage().contains("Examples/Orders"),
         "exception message should reference the denied data source name");

      // No metadata/schema lookup should have happened past the permission gate.
      verifyNoInteractions(xrepository);
      verify(dataSourceService, never()).getDataSource(anyString());
   }

   @Test
   void getMetaData_proceedsPastPermissionCheckWhenGranted() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      GetDatabaseTableMetaRequest request = new GetDatabaseTableMetaRequest();
      request.setDsName("Examples/Orders");
      request.setTableName("CUSTOMERS");

      when(dataSourceService.checkPermission(
         eq("Examples/Orders"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(true);
      // No data source registered: getJDBCDatasource() will fail past the permission
      // gate with a plain Exception (not SecurityException), proving the gate was cleared.
      when(xrepository.getDataSource("Examples/Orders")).thenReturn(null);

      MetadataApiService service = createService(dataSourceService, xrepository);

      Exception ex = assertThrows(Exception.class,
         () -> service.getMetaData(request, principal));

      assertFalse(ex instanceof SecurityException,
         "should have advanced past the permission gate rather than being denied");

      verify(dataSourceService).checkPermission(
         eq("Examples/Orders"), eq(ResourceAction.READ), eq(principal));
      verify(xrepository).getDataSource("Examples/Orders");
   }
}
