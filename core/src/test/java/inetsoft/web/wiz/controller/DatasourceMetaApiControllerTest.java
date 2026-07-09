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
package inetsoft.web.wiz.controller;

import inetsoft.uql.XRepository;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.osi.OsiDataset;
import inetsoft.web.wiz.request.GetDatabaseTableMetaRequest;
import inetsoft.web.wiz.service.MetadataApiService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that {@link DatasourceMetaApiController#getDatabaseTableMeta} passes the resolved
 * {@link Principal} through to {@link MetadataApiService#getMetaData} unchanged, so the
 * READ-permission check added there actually receives the requesting user.
 */
@Tag("core")
class DatasourceMetaApiControllerTest {

   @Test
   void getDatabaseTableMeta_passesRequestAndPrincipalThroughUnchanged() throws Exception {
      MetadataApiService metadataService = mock(MetadataApiService.class);
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);

      DatasourceMetaApiController controller =
         new DatasourceMetaApiController(metadataService, xrepository, dataSourceService);

      GetDatabaseTableMetaRequest request = new GetDatabaseTableMetaRequest();
      request.setDsName("Examples/Orders");
      request.setTableName("CUSTOMERS");
      Principal principal = mock(Principal.class);

      OsiDataset expected = new OsiDataset();
      when(metadataService.getMetaData(request, principal)).thenReturn(expected);

      OsiDataset result = controller.getDatabaseTableMeta(request, principal);

      assertSame(expected, result);
      verify(metadataService).getMetaData(request, principal);
   }
}
