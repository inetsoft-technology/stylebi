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
import inetsoft.web.wiz.model.DatasourceRelationshipsResponse;
import inetsoft.web.wiz.model.osi.OsiRelationship;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Charter B12 (added at P2 reconcile): {@code POST /datasource/relationships} enforces READ on
 * {@code dsPath}; a caller without it gets 403 and no provider call is made. Modeled directly on
 * {@link MetadataApiServiceNonJdbcBranchTest}'s established pattern for this exact class.
 */
@Tag("core")
class MetadataApiServiceDatasourceRelationshipsTest {

   @Test
   void deniedReadPermission_throwsSecurityException_andNeverCallsTheProvider() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("Secret/Source"), eq(ResourceAction.READ), any()))
         .thenReturn(false);

      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      assertThrows(SecurityException.class, () -> service.getDatasourceRelationships(
         "Secret/Source", List.of("A", "B"), mock(Principal.class)));

      verifyNoInteractions(tabularCatalogService);
   }

   @Test
   void grantedReadPermission_delegatesToTabularCatalogServiceAndReturnsItsResult() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(eq("OData Source"), eq(ResourceAction.READ), any()))
         .thenReturn(true);

      OsiRelationship rel = new OsiRelationship();
      rel.setName("R_A_B");
      rel.setFrom("A");
      rel.setTo("B");
      TabularCatalogService tabularCatalogService = mock(TabularCatalogService.class);
      when(tabularCatalogService.listRelationships("OData Source", List.of("A", "B")))
         .thenReturn(List.of(rel));

      MetadataApiService service = new MetadataApiService(xrepository, dataSourceService,
         mock(AssetRepository.class), mock(AssetTreeService.class), new ObjectMapper(),
         tabularCatalogService);

      DatasourceRelationshipsResponse response = service.getDatasourceRelationships(
         "OData Source", List.of("A", "B"), mock(Principal.class));

      assertEquals(1, response.relationships().size());
      assertEquals("R_A_B", response.relationships().get(0).getName());
      verify(tabularCatalogService).listRelationships("OData Source", List.of("A", "B"));
   }
}
