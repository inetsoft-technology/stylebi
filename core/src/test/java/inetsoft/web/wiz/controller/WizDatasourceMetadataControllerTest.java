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

import inetsoft.sree.security.ResourceAction;
import inetsoft.sree.security.ResourceType;
import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.WizMetadataRefreshResult;
import inetsoft.web.wiz.request.WizMetadataRefreshRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("core")
class WizDatasourceMetadataControllerTest {
   @Test
   void theRouteIsThePublishedOne() throws Exception {
      PostMapping mapping = WizDatasourceMetadataController.class
         .getDeclaredMethod("refreshMetadata", WizMetadataRefreshRequest.class, Principal.class)
         .getAnnotation(PostMapping.class);

      assertArrayEquals(new String[]{ "/datasources/refresh-metadata" }, mapping.value());
   }

   @Test
   void isDeniedWithoutReadAndNeverRefreshes() throws Exception {
      Fixture f = new Fixture();
      when(f.security.checkPermission(f.principal, ResourceType.DATA_SOURCE, "db/orders",
                                      ResourceAction.READ)).thenReturn(false);

      assertThrows(SecurityException.class, () -> f.controller.refreshMetadata(
         new WizMetadataRefreshRequest("db/orders"), f.principal));

      verify(f.repository, never()).refreshMetaData(anyString());
      verify(f.dataSourceService, never()).removeDefaultMetaDataProviderCache(any());
   }

   @Test
   void refreshesTheEngineThenDropsTheCachedProvider() throws Exception {
      Fixture f = new Fixture();
      f.readable();
      XDataSource ds = mock(XDataSource.class);
      when(f.repository.getDataSource("db/orders")).thenReturn(ds);

      WizMetadataRefreshResult result = f.controller.refreshMetadata(
         new WizMetadataRefreshRequest("db/orders"), f.principal);

      assertTrue(result.ok());
      assertNull(result.reason());
      verify(f.repository).refreshMetaData("db/orders");
      verify(f.dataSourceService).removeDefaultMetaDataProviderCache(ds);
   }

   @Test
   void reportsAnEngineFailureInsteadOfSwallowingIt() throws Exception {
      Fixture f = new Fixture();
      f.readable();
      when(f.repository.getDataSource("db/orders")).thenReturn(mock(XDataSource.class));
      doThrow(new RuntimeException("connection pool exhausted"))
         .when(f.repository).refreshMetaData("db/orders");

      WizMetadataRefreshResult result = f.controller.refreshMetadata(
         new WizMetadataRefreshRequest("db/orders"), f.principal);

      assertFalse(result.ok(), "the Portal returns false here; success would be a wrong answer");
      assertEquals(WizMetadataRefreshResult.UNKNOWN, result.reason());
      assertEquals("connection pool exhausted", result.message());
   }

   @Test
   void refusesAnXmlaSourceInsteadOfReportingASuccessfulRefresh() throws Exception {
      Fixture f = new Fixture();
      f.readable();
      when(f.repository.getDataSource("olap/sales"))
         .thenReturn(mock(inetsoft.uql.xmla.XMLADataSource.class));

      WizMetadataRefreshResult result = f.controller.refreshMetadata(
         new WizMetadataRefreshRequest("olap/sales"), f.principal);

      assertFalse(result.ok(), "no cube was loaded, so this must not read as refreshed");
      assertEquals(WizMetadataRefreshResult.XMLA_DATASOURCE, result.reason());
      verify(f.repository, never()).refreshMetaData(anyString());
   }

   @Test
   void reportsAMissingSourceAndRejectsABlankPath() throws Exception {
      Fixture f = new Fixture();
      f.readable();

      WizMetadataRefreshResult result = f.controller.refreshMetadata(
         new WizMetadataRefreshRequest("db/none"), f.principal);

      assertFalse(result.ok());
      assertEquals(WizMetadataRefreshResult.DATASOURCE_LOST, result.reason());
      assertThrows(ResponseStatusException.class, () -> f.controller.refreshMetadata(
         new WizMetadataRefreshRequest(" "), f.principal));
   }

   private static final class Fixture {
      void readable() throws Exception {
         when(security.checkPermission(any(), any(ResourceType.class), anyString(),
                                       any(ResourceAction.class))).thenReturn(true);
      }

      final XRepository repository = mock(XRepository.class);
      final DataSourceService dataSourceService = mock(DataSourceService.class);
      final SecurityEngine security = mock(SecurityEngine.class);
      final Principal principal = mock(Principal.class);
      final WizDatasourceMetadataController controller =
         new WizDatasourceMetadataController(repository, dataSourceService, security);
   }
}
