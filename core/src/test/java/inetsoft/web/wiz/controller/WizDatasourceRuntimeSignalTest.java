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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Charter A5 and D2: the declared-runtime resolution step this round adds to
 * {@code annotationClassOf}, and the reachable test seam for it (03-reconcile.md Δ8).
 *
 * <p>{@link WizDatabaseController#resolveRuntimeClass} is package-private specifically so this
 * class can call it directly -- the same reason {@code classifyQueryClass} was already pulled out
 * of {@code annotationClassOf} "so it can be exercised without standing up a plugin registry".
 * {@code Config} is mocked rather than built from a real plugin registry, following the pattern
 * {@code WizDatasourceAnnotationTargetThresholdTest} already established for this controller.</p>
 */
@Tag("core")
class WizDatasourceRuntimeSignalTest {
   /** D2: a type that declares no runtime at all resolves to {@code null}, not an exception. */
   @Test
   void resolveRuntimeClass_returnsNullWhenNoRuntimeIsDeclared() throws Exception {
      Config uqlConfig = mock(Config.class);
      when(uqlConfig.getRuntime("TEST")).thenReturn(null);

      WizDatabaseController controller = newController(uqlConfig);

      assertNull(controller.resolveRuntimeClass("TEST"));
   }

   /** A declared runtime that resolves cleanly is returned as the {@code Class} itself. */
   @Test
   void resolveRuntimeClass_returnsTheClassWhenTheRuntimeLoads() throws Exception {
      Config uqlConfig = mock(Config.class);
      when(uqlConfig.getRuntime("TEST")).thenReturn("java.lang.Object");
      when(uqlConfig.getClass("TEST", "java.lang.Object")).thenReturn((Class) Object.class);

      WizDatabaseController controller = newController(uqlConfig);

      assertEquals(Object.class, controller.resolveRuntimeClass("TEST"));
   }

   /**
    * A5's converse at the resolution seam: a declared runtime whose class cannot be loaded
    * propagates the failure rather than silently returning null (which {@code annotationClassOf}
    * would then be unable to distinguish from "no runtime declared" -- D2's whole point).
    */
   @Test
   void resolveRuntimeClass_propagatesFailureWhenTheDeclaredRuntimeCannotLoad() throws Exception {
      Config uqlConfig = mock(Config.class);
      when(uqlConfig.getRuntime("TEST")).thenReturn("does.not.Exist");
      when(uqlConfig.getClass("TEST", "does.not.Exist"))
         .thenThrow(new ClassNotFoundException("does.not.Exist"));

      WizDatabaseController controller = newController(uqlConfig);

      assertThrows(ClassNotFoundException.class, () -> controller.resolveRuntimeClass("TEST"));
   }

   /**
    * A5, the full round trip: a query class that loads fine but a runtime class that fails to
    * load must classify UNKNOWN -- never DOCUMENT_REQUIRED or METADATA. "Plugin absent" must stay
    * distinct from "classified as not annotatable", which the portal reports very differently
    * (the same reasoning the existing query-class load-failure branch already documents).
    *
    * Exercised through the real {@code getDatasourceBrowser} -> toEntries -> toEntry ->
    * annotationClassOf path (mirroring {@code WizDatasourceAnnotationTargetThresholdTest}'s
    * pattern), not by calling a private method via reflection -- this is the one case that cannot
    * be pinned purely through {@code classifyQueryClass}'s pure {@code Class}-based signature,
    * because "the runtime class failed to load" is a fact {@code annotationClassOf} resolves
    * before {@code classifyQueryClass} is ever reached.
    */
   @Test
   void aRuntimeClassThatFailsToLoadClassifiesUnknownNotDocumentRequiredOrMetadata() throws Exception {
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

      // A bare mock, not a real JDBCDataSource -- see WizDatasourceAnnotationTargetThresholdTest
      // for why (constructing one needs a live Spring context this plain unit test does not
      // stand up).
      XDataSource dataSource = mock(XDataSource.class);
      when(dataSource.getType()).thenReturn("TEST");
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource("Test/ds1")).thenReturn(dataSource);

      Config uqlConfig = mock(Config.class);
      // The query class loads fine -- this is NOT the failure this test is about.
      when(uqlConfig.getQueryClass("TEST")).thenReturn("java.lang.Object");
      when(uqlConfig.getClass("TEST", "java.lang.Object")).thenReturn((Class) Object.class);
      // The declared runtime class does not exist -- THIS is the failure under test.
      when(uqlConfig.getRuntime("TEST")).thenReturn("does.not.Exist");
      when(uqlConfig.getClass("TEST", "does.not.Exist"))
         .thenThrow(new ClassNotFoundException("does.not.Exist"));

      WizDatabaseController controller = new WizDatabaseController(
         browserService, mock(DataSourceStatusService.class),
         mock(DatabaseDatasourcesService.class), mock(DatabaseTypeService.class),
         mock(SecurityEngine.class), uqlConfig, xrepository,
         mock(EndpointCatalogReader.class), mock(DatasourcesService.class));

      WizDatasourceBrowserModel model = controller.getDatasourceBrowser("Test", mock(Principal.class));

      assertEquals(1, model.entries().size());
      WizDatasourceEntry entry = model.entries().get(0);
      assertEquals("UNKNOWN", entry.annotationClass(),
                   "a runtime class that fails to load must classify UNKNOWN, not fall through " +
                      "to DOCUMENT_REQUIRED/METADATA via classifyQueryClass");
   }

   private static WizDatabaseController newController(Config uqlConfig) {
      return new WizDatabaseController(
         mock(DataSourceBrowserService.class), mock(DataSourceStatusService.class),
         mock(DatabaseDatasourcesService.class), mock(DatabaseTypeService.class),
         mock(SecurityEngine.class), uqlConfig, mock(XRepository.class),
         mock(EndpointCatalogReader.class), mock(DatasourcesService.class));
   }
}
