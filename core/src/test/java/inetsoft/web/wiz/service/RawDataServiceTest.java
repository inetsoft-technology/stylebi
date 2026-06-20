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

import inetsoft.uql.XRepository;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.web.wiz.request.ExportDatabaseTableToCsvRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@Tag("core")
class RawDataServiceTest {
   @Test
   void rejectsRequestWithNullTableEntryWithClearMessageAndWithoutTouchingRepository() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      AssetRepository assetRepository = mock(AssetRepository.class);
      RawDataService service = new RawDataService(xrepository, assetRepository);

      ExportDatabaseTableToCsvRequest request = new ExportDatabaseTableToCsvRequest();
      request.setDatasourcePath("inventree");
      request.setTable(null); // the plugin/profiler can omit this — must fail cleanly, not NPE

      ResponseStatusException ex = assertThrows(
         ResponseStatusException.class,
         () -> service.writeDataSourceTableCsvStream(request, null, new ByteArrayOutputStream()));

      // A missing table asset entry must surface as a clean 400 Bad Request (not an opaque 500 NPE).
      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
      // The reason must name the data source so the failure is diagnosable.
      assertNotNull(ex.getReason());
      assertTrue(ex.getReason().contains("inventree"),
                 "exception reason should name the data source: " + ex.getReason());

      // The guard must run before any repository lookup.
      verifyNoInteractions(xrepository);
   }
}
