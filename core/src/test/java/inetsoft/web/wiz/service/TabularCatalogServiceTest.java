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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import inetsoft.uql.XDataSource;
import inetsoft.uql.XRepository;
import inetsoft.uql.tabular.*;
import inetsoft.uql.schema.XSchema;
import inetsoft.web.wiz.model.DatabaseTableInfo;
import inetsoft.web.wiz.model.DatasourceTablesResponse;
import inetsoft.web.wiz.model.osi.OsiCustomExtension;
import inetsoft.web.wiz.model.osi.OsiDataset;
import inetsoft.web.wiz.model.osi.OsiRelationship;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers {@link TabularCatalogService}, the non-JDBC counterpart of
 * {@link MetadataApiService#getDatabaseTables} / {@link MetadataApiService#getMetaData}.
 *
 * Driven entirely by {@link FakeCatalogRuntime} and friends — connectors this test source tree
 * invented and that have never heard of OData. This class IS charter assertion B5: core's non-JDBC
 * branch and both neutral-to-wire mappings run to completion, driven by a connector that could not
 * possibly have satisfied a hidden OData assumption.
 */
@Tag("core")
class TabularCatalogServiceTest {

   private static final String DS_NAME = "Fake OData Source";

   private TabularCatalogService createService(XRepository xrepository,
                                                Function<String, TabularRuntime> runtimeResolver)
   {
      return new TabularCatalogService(xrepository, new ObjectMapper(), runtimeResolver);
   }

   private XRepository repositoryWithFakeDataSource() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(new FakeTabularDataSource());
      return xrepository;
   }

   // ----- B1 / B5: listTables -----

   @Test
   void listTables_mapsFakeDatasetsAndRelationshipsToWireTypes() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("Products"), new TabularDatasetRef("Categories")),
         List.of(new TabularRelationship("Products_Category", "Products", "Categories",
            List.of("CategoryID"), List.of("ID"))));

      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> runtime);

      DatasourceTablesResponse response = service.listTables(DS_NAME);

      assertEquals(2, response.getTables().size());
      DatabaseTableInfo products = response.getTables().get(0);
      assertEquals("Products", products.getTable());
      assertEquals(DS_NAME, products.getDatabase());
      assertNull(products.getCatalog());
      assertNull(products.getSchema());

      assertEquals(1, response.getRelationships().size());
      OsiRelationship rel = response.getRelationships().get(0);
      assertEquals("Products_Category", rel.getName());
      assertEquals("Products", rel.getFrom());
      assertEquals("Categories", rel.getTo());
      assertEquals(List.of("CategoryID"), rel.getFromColumns());
      assertEquals(List.of("ID"), rel.getToColumns());
   }

   @Test
   void listTables_emptyCatalog_throwsInsteadOfSilentlyEmptyResult() throws Exception {
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(
         new TabularCatalog(List.of(), List.of()), Map.of());
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertFalse(ex instanceof UnsupportedDatasourceException);
   }

   @Test
   void listTables_providerThrows_propagatesRatherThanSwallowing() throws Exception {
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> new FakeFailingCatalogRuntime());

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));
      assertEquals("fake catalog listing failure", ex.getMessage());
   }

   @Test
   void listTables_runtimeDoesNotImplementSpi_throwsUnsupportedDatasourceException()
      throws Exception
   {
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> new FakeNonCatalogRuntime());

      UnsupportedDatasourceException ex = assertThrows(UnsupportedDatasourceException.class,
         () -> service.listTables(DS_NAME));
      assertEquals(DS_NAME, ex.getDatasourceName());
   }

   @Test
   void listTables_noRuntimeAtAll_throwsUnsupportedDatasourceException() throws Exception {
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> null);

      assertThrows(UnsupportedDatasourceException.class, () -> service.listTables(DS_NAME));
   }

   @Test
   void listTables_dataSourceNotTabular_throwsUnsupportedDatasourceException() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      XDataSource notTabular = mock(XDataSource.class);
      when(notTabular.getType()).thenReturn("Mongo");
      when(xrepository.getDataSource(DS_NAME)).thenReturn(notTabular);

      TabularCatalog catalog =
         new TabularCatalog(List.of(new TabularDatasetRef("X")), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(xrepository, dsName -> runtime);

      UnsupportedDatasourceException ex = assertThrows(UnsupportedDatasourceException.class,
         () -> service.listTables(DS_NAME));
      assertEquals("Mongo", ex.getDatasourceType());
   }

   @Test
   void listTables_dataSourceDoesNotExist_throwsPlainException() throws Exception {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(null);
      TabularCatalogService service = createService(xrepository, dsName -> null);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));
      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
   }

   // ----- B1 / B7: describeTable -----

   @Test
   void describeTable_mapsFakeSchemaToOsiDatasetWithTabularExtension() throws Exception {
      TabularDatasetSchema schema = new TabularDatasetSchema("Products",
         List.of(new TabularColumn("ID", XSchema.LONG), new TabularColumn("Name", XSchema.STRING),
                 new TabularColumn("Updated", XSchema.TIME_INSTANT)),
         List.of("ID"));

      FakeCatalogRuntime runtime = new FakeCatalogRuntime(
         new TabularCatalog(List.of(), List.of()), Map.of("Products", schema));
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> runtime);

      OsiDataset dataset = service.describeTable(DS_NAME, "Products");

      assertEquals("Products", dataset.getName());
      assertEquals("Products", dataset.getSource());
      assertEquals(List.of("ID"), dataset.getPrimaryKey());
      assertEquals(3, dataset.getFields().size());
      assertEquals("ID", dataset.getFields().get(0).getName());
      assertNull(dataset.getFields().get(0).getDimension());
      assertNotNull(dataset.getFields().get(2).getDimension());
      assertTrue(dataset.getFields().get(2).getDimension().isTime());

      OsiCustomExtension ext = dataset.getCustomExtensions().get(0);
      assertEquals("COMMON", ext.getVendorName());
      ObjectMapper mapper = new ObjectMapper();
      JsonNode data = mapper.readTree(ext.getData());
      assertEquals("tabular", data.get("datasourceType").asText());
      assertEquals(DS_NAME, data.get("dsName").asText());
      assertEquals(DS_NAME, data.get("path").asText());
      assertEquals("FakeCatalog", data.get("datasourceSubtype").asText());
   }

   @Test
   void describeTable_noKeyColumns_primaryKeyIsNullNotEmptyList() throws Exception {
      TabularDatasetSchema schema =
         new TabularDatasetSchema("Categories", List.of(new TabularColumn("ID", XSchema.LONG)),
            List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(
         new TabularCatalog(List.of(), List.of()), Map.of("Categories", schema));
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> runtime);

      OsiDataset dataset = service.describeTable(DS_NAME, "Categories");
      assertNull(dataset.getPrimaryKey());
   }

   @Test
   void describeTable_unknownTarget_throwsRatherThanEmptyColumns() throws Exception {
      FakeCatalogRuntime runtime =
         new FakeCatalogRuntime(new TabularCatalog(List.of(), List.of()), Map.of());
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> runtime);

      Exception ex = assertThrows(Exception.class,
         () -> service.describeTable(DS_NAME, "NoSuchTarget"));
      assertTrue(ex.getMessage().contains("NoSuchTarget"));
   }

   @Test
   void describeTable_providerThrows_propagatesRatherThanSwallowing() throws Exception {
      TabularCatalogService service =
         createService(repositoryWithFakeDataSource(), dsName -> new FakeFailingCatalogRuntime());

      Exception ex = assertThrows(Exception.class,
         () -> service.describeTable(DS_NAME, "Products"));
      assertEquals("fake dataset description failure", ex.getMessage());
   }
}
