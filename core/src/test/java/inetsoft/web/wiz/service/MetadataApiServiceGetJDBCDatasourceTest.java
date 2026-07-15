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
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.jdbc.JDBCDataSource;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link MetadataApiService#getJDBCDatasource}, which must tell apart two distinct
 * failure modes that previously shared one generic "not found" message:
 * <ul>
 *   <li>the datasource genuinely doesn't exist (no XDataSource registered under that name)</li>
 *   <li>the datasource exists but is not relational (e.g. MongoDB extends TabularDataSource,
 *       not JDBCDataSource) — a distinct, expected condition that
 *       {@link inetsoft.web.wiz.controller.DatasourceMetaApiController} maps to a friendly
 *       HTTP 422 instead of letting it leak out as a raw 500.</li>
 * </ul>
 */
@Tag("core")
class MetadataApiServiceGetJDBCDatasourceTest {

   private MetadataApiService createService(XRepository xrepository) {
      return new MetadataApiService(
         xrepository, mock(DataSourceService.class), mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper());
   }

   @Test
   void getJDBCDatasource_throwsPlainExceptionWhenDatasourceDoesNotExist() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource("nope")).thenReturn(null);

      MetadataApiService service = createService(xrepository);

      Exception ex = assertThrows(Exception.class, () -> service.getJDBCDatasource("nope"));

      assertFalse(ex instanceof UnsupportedDatasourceException,
         "a missing datasource is a plain not-found, not an unsupported-type condition");
      assertTrue(ex.getMessage().contains("nope"));
   }

   @Test
   void getJDBCDatasource_throwsUnsupportedDatasourceExceptionWhenNotRelational() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      XDataSource mongoDataSource = mock(XDataSource.class);
      when(mongoDataSource.getType()).thenReturn("Mongo");
      when(xrepository.getDataSource("MongoDB REST")).thenReturn(mongoDataSource);

      MetadataApiService service = createService(xrepository);

      UnsupportedDatasourceException ex = assertThrows(UnsupportedDatasourceException.class,
         () -> service.getJDBCDatasource("MongoDB REST"));

      assertEquals("MongoDB REST", ex.getDatasourceName());
      assertEquals("Mongo", ex.getDatasourceType());
      assertTrue(ex.getMessage().contains("'Mongo' datasources"),
         "message should name the actual datasource type, not a hardcoded one");
      assertTrue(ex.getMessage().toLowerCase().contains("not supported"));
   }

   @Test
   void getJDBCDatasource_returnsTheJDBCDatasourceWhenRelational() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      JDBCDataSource jdbcDataSource = mock(JDBCDataSource.class);
      when(xrepository.getDataSource("Examples/Orders")).thenReturn(jdbcDataSource);

      MetadataApiService service = createService(xrepository);

      assertSame(jdbcDataSource, service.getJDBCDatasource("Examples/Orders"));
   }
}
