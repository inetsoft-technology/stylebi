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

import inetsoft.sree.security.SecurityEngine;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.util.Config;
import inetsoft.web.admin.content.database.DatabaseTypeService;
import inetsoft.web.admin.content.repository.DatabaseDatasourcesService;
import inetsoft.web.admin.model.NameLabelTuple;
import inetsoft.web.portal.data.DataSourceBrowserService;
import inetsoft.web.portal.data.DataSourceInfo;
import inetsoft.web.portal.data.DatasourcesService;
import inetsoft.web.portal.service.datasource.DataSourceStatusService;
import inetsoft.web.wiz.model.WizDatasourceBrowserModel;
import inetsoft.web.wiz.model.WizDatasourceEntry;
import inetsoft.web.wiz.service.EndpointCatalogReader;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins design spec §3.3 (scaled annotation target selection): the annotation-target threshold
 * reaches {@link WizDatasourceEntry} through the real browse path, not just as a constant sitting
 * unused in the controller. Wiz issues its first paged listing request with
 * {@code limit = annotationTargetThreshold + 1} <em>before</em> that first request, so this is its
 * only chance to learn the value.
 */
@Tag("core")
class WizDatasourceAnnotationTargetThresholdTest {
   @Test
   void browseEntryCarriesTheGlobalAnnotationTargetThreshold() throws Exception {
      DataSourceInfo info = DataSourceInfo.builder()
         .name("ds1")
         .path("Test/ds1")
         .type(NameLabelTuple.builder().name("DATA_SOURCE").label("ds1").build())
         .createdBy("admin")
         .createdDateLabel("")
         .dateFormat("")
         .editable(true)
         .deletable(true)
         .hasSubFolder(false)
         .build();

      DataSourceBrowserService browserService = mock(DataSourceBrowserService.class);
      when(browserService.getDataSources(anyString(), eq(false), any(), any(Principal.class)))
         .thenReturn(List.of(info));
      when(browserService.getBreadcrumbs(anyString(), eq(true), any(Principal.class)))
         .thenReturn(List.of());

      // A bare mock, not a real JDBCDataSource -- constructing one needs a live Spring context
      // (CredentialService) this plain unit test does not stand up, and this test cares about the
      // threshold reaching the entry, not about which annotationClass a real JDBC source gets.
      XDataSource dataSource = mock(XDataSource.class);
      when(dataSource.getType()).thenReturn("TEST");
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource("Test/ds1")).thenReturn(dataSource);

      Config uqlConfig = mock(Config.class);
      when(uqlConfig.getQueryClass("TEST")).thenReturn(null); // classifies as UNKNOWN -- irrelevant here

      WizDatabaseController controller = new WizDatabaseController(
         browserService, mock(DataSourceStatusService.class),
         mock(DatabaseDatasourcesService.class), mock(DatabaseTypeService.class),
         mock(SecurityEngine.class), uqlConfig, xrepository,
         mock(EndpointCatalogReader.class), mock(DatasourcesService.class));

      WizDatasourceBrowserModel model = controller.getDatasourceBrowser("Test", mock(Principal.class));

      assertEquals(1, model.entries().size());
      WizDatasourceEntry entry = model.entries().get(0);
      assertEquals(WizDatabaseController.ANNOTATION_TARGET_THRESHOLD,
                   entry.annotationTargetThreshold(),
                   "the threshold must reach the entry wiz reads before its first paged " +
                      "listing request, not just exist as an unused constant");
   }
}
