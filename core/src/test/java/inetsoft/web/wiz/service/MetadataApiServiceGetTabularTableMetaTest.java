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
import inetsoft.uql.tabular.TabularUtil;
import inetsoft.web.composer.AssetTreeService;
import inetsoft.web.portal.controller.database.DataSourceService;
import inetsoft.web.wiz.model.osi.OsiDataset;
import inetsoft.web.wiz.model.osi.OsiField;
import inetsoft.web.wiz.request.GetTabularTableMetaRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.Principal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Covers {@link MetadataApiService#getTabularTableMeta} -- the METADATA-class counterpart of
 * {@link MetadataApiService#getMetaData} added for the OData/SAP annotation-entry gap (charter
 * assertions B1/B5 and the reverse assertion that zero-column results must never look like a
 * successful, if empty, annotation).
 */
@Tag("core")
class MetadataApiServiceGetTabularTableMetaTest {

   private MetadataApiService createService(DataSourceService dataSourceService,
                                             XRepository xrepository)
   {
      return new MetadataApiService(
         xrepository, dataSourceService, mock(AssetRepository.class),
         mock(AssetTreeService.class), new ObjectMapper());
   }

   private GetTabularTableMetaRequest request(String dsName, String target) {
      GetTabularTableMetaRequest req = new GetTabularTableMetaRequest();
      req.setDsName(dsName);
      req.setTarget(target);
      return req;
   }

   @Test
   void throwsSecurityExceptionAndSkipsLookupWhenPermissionDenied() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      when(dataSourceService.checkPermission(eq("OData/Northwind"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(false);

      MetadataApiService service = createService(dataSourceService, xrepository);
      GetTabularTableMetaRequest req = request("OData/Northwind", "Products");

      SecurityException ex = assertThrows(SecurityException.class,
         () -> service.getTabularTableMeta(req, principal));

      assertTrue(ex.getMessage().contains("OData/Northwind"));
      verifyNoInteractions(xrepository);
   }

   @Test
   void throwsUnsupportedDatasourceExceptionWhenQueryDoesNotImplementAnnotatableQuery() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);
      XDataSource ds = mock(XDataSource.class);

      when(dataSourceService.checkPermission(eq("SharePoint/Docs"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(true);
      when(xrepository.getDataSource("SharePoint/Docs")).thenReturn(ds);
      when(ds.getType()).thenReturn("SharepointOnline");

      MetadataApiService service = createService(dataSourceService, xrepository);
      GetTabularTableMetaRequest req = request("SharePoint/Docs", "Documents");

      // A non-AnnotatableQuery TabularQuery (mirrors SharepointOnlineQuery, which deliberately
      // does not implement it -- see AnnotatableQuery's javadoc).
      inetsoft.uql.tabular.TabularQuery nonAnnotatable = mock(inetsoft.uql.tabular.TabularQuery.class);

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery("SharePoint/Docs")).thenReturn(nonAnnotatable);

         UnsupportedDatasourceException ex = assertThrows(UnsupportedDatasourceException.class,
            () -> service.getTabularTableMeta(req, principal));

         assertEquals("SharePoint/Docs", ex.getDatasourceName());
         assertEquals("SharepointOnline", ex.getDatasourceType());
      }
   }

   @Test
   void throwsWhenCreateQueryFindsNoConnectorPlugin() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      when(dataSourceService.checkPermission(eq("OData/Northwind"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(true);

      MetadataApiService service = createService(dataSourceService, xrepository);
      GetTabularTableMetaRequest req = request("OData/Northwind", "Products");

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery("OData/Northwind")).thenReturn(null);

         Exception ex = assertThrows(Exception.class,
            () -> service.getTabularTableMeta(req, principal));

         assertFalse(ex instanceof UnsupportedDatasourceException);
         assertTrue(ex.getMessage().contains("OData/Northwind"));
      }
   }

   @Test
   void throwsWhenTheAnnotationTargetPropertyDoesNotExistOnTheQueryClass() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      when(dataSourceService.checkPermission(eq("OData/Northwind"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(true);

      MetadataApiService service = createService(dataSourceService, xrepository);
      GetTabularTableMetaRequest req = request("OData/Northwind", "Products");

      FakeAnnotatableQuery query = new FakeAnnotatableQuery();
      query.setAnnotationTargetProperty("noSuchProperty");

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery("OData/Northwind")).thenReturn(query);

         Exception ex = assertThrows(Exception.class,
            () -> service.getTabularTableMeta(req, principal));

         assertTrue(ex.getMessage().contains("noSuchProperty"));
      }
   }

   @Test
   void throwsRatherThanSucceedingSilentlyWhenTheTargetHasNoColumns() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      when(dataSourceService.checkPermission(eq("OData/Northwind"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(true);

      MetadataApiService service = createService(dataSourceService, xrepository);
      // FakeAnnotatableQuery answers zero columns for target "empty" -- see its javadoc.
      GetTabularTableMetaRequest req = request("OData/Northwind", "empty");

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery("OData/Northwind"))
            .thenReturn(new FakeAnnotatableQuery());

         Exception ex = assertThrows(Exception.class,
            () -> service.getTabularTableMeta(req, principal));

         assertTrue(ex.getMessage().contains("no columns"));
      }
   }

   @Test
   void assemblesAnOsiDatasetFromTheConnectorsRealOutputColumns() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      DataSourceService dataSourceService = mock(DataSourceService.class);
      Principal principal = mock(Principal.class);

      when(dataSourceService.checkPermission(eq("OData/Northwind"), eq(ResourceAction.READ), eq(principal)))
         .thenReturn(true);

      MetadataApiService service = createService(dataSourceService, xrepository);
      GetTabularTableMetaRequest req = request("OData/Northwind", "Products");

      OsiDataset dataset;

      try(MockedStatic<TabularUtil> tabularUtil = mockStatic(TabularUtil.class, CALLS_REAL_METHODS)) {
         tabularUtil.when(() -> TabularUtil.createQuery("OData/Northwind"))
            .thenReturn(new FakeAnnotatableQuery());

         dataset = service.getTabularTableMeta(req, principal);
      }

      // Content-level assertions, not merely "non-empty" -- B1 requires the column structure to
      // actually come from the connector's getOutputColumns(), not a placeholder.
      assertEquals("Products", dataset.getName());
      assertEquals("Products", dataset.getSource());
      assertNotNull(dataset.getFields());
      assertEquals(2, dataset.getFields().size());

      List<String> fieldNames = dataset.getFields().stream().map(OsiField::getName).toList();
      assertEquals(List.of("Products_id", "Products_created"), fieldNames);

      // The date-typed column must come back flagged as a dimension (XSchema.isDateType branch).
      assertNull(dataset.getFields().get(0).getDimension());
      assertNotNull(dataset.getFields().get(1).getDimension());
      assertTrue(dataset.getFields().get(1).getDimension().isTime());
   }
}
