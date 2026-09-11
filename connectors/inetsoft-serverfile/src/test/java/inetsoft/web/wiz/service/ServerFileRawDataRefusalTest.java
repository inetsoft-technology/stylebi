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

import inetsoft.sree.security.ResourceAction;
import inetsoft.uql.XPrincipal;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.serverfile.ServerFileDataSource;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.request.ExportDatabaseTableToCsvRequest;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers charter A8's hinge fact (03-reconcile.md D-3): {@code RawDataService} refuses anything
 * that is not a {@code JDBCDataSource} (:131-135), which is exactly why a ServerFile target --
 * once reclassified to METADATA -- can no longer reach the CSV-export raw-data route wiz used for
 * it on the file pipeline. This was one seat's source reading only; pinned here as a real,
 * executable test in the connector whose data source class is the one under test (core cannot
 * depend on it directly).
 */
class ServerFileRawDataRefusalTest {
   @Test
   void aServerFileDataSourceIsRefused_notAJdbcDataSource() throws Exception {
      ServerFileDataSource serverFileDs = new ServerFileDataSource();
      serverFileDs.setName("server-file-ds");

      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource("server-file-ds")).thenReturn(serverFileDs);

      AssetRepository assetRepository = mock(AssetRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      when(dataSourceService.checkPermission(anyString(), any(ResourceAction.class), any(XPrincipal.class)))
         .thenReturn(true);

      RawDataService service = new RawDataService(xrepository, assetRepository, dataSourceService);

      ExportDatabaseTableToCsvRequest request = new ExportDatabaseTableToCsvRequest();
      request.setDatasourcePath("server-file-ds");
      request.setTable(new AssetEntry());   // never read: the JDBCDataSource refusal fires first

      // A mock, not a real XPrincipal: its no-arg-adjacent constructors reach XSessionService,
      // which (like TabularSchemaExtractor's LayoutCreator) needs a Spring bean this plain unit
      // test has none for -- and the JDBCDataSource refusal below fires before the principal's
      // contents are ever read, so a mock is exactly as good as a real one here.
      XPrincipal principal = mock(XPrincipal.class);
      ByteArrayOutputStream out = new ByteArrayOutputStream();

      Exception ex = assertThrows(Exception.class,
         () -> service.writeDataSourceTableCsvStream(request, principal, out));
      assertTrue(ex.getMessage().contains("not found"),
         "expected RawDataService's '... not found.' refusal (:131-135) for a non-JDBCDataSource, " +
         "got: " + ex.getMessage());
   }
}
