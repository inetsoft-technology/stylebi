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
import inetsoft.uql.XRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers charter assertions C1-C3 and C6: {@link TabularCatalogService} must not trust a
 * connector's {@link TabularCatalog}/{@link TabularDatasetSchema} at face value — a null
 * relationships list, a relationship pointing at an unknown dataset, a blank id/column name, a
 * dotted dataset id, a blank relationship name, or a mismatched/empty column pairing must each
 * produce a named exception, not an NPE or a silently-broken wire response.
 *
 * C6 was added after P5 review r1 (finding 1): the original C1-C3 pass checked only three of the
 * SPI's documented invariants, leaving the ones on {@code TabularDatasetRef.id} (no {@code .}) and
 * {@code TabularRelationship} (non-blank name, paired columns) unchecked — including the very
 * invariant ({@code id} must not contain {@code .}) that motivated {@code SharepointDatasetId}'s
 * whole escaping scheme in the sibling implementer.
 *
 * Kept out of {@link TabularCatalogServiceTest} deliberately — that file's stated scope is charter
 * assertion B5 (core carries zero OData knowledge), and charter assertion C5 requires it stay
 * unedited this round as a regression tripwire, so new coverage goes in this new file instead.
 */
@Tag("core")
class TabularCatalogServiceContractValidationTest {

   private static final String DS_NAME = "Fake Contract-Breaking Source";

   private TabularCatalogService createService(Function<String, TabularRuntime> runtimeResolver)
      throws Exception
   {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(new FakeTabularDataSource());
      return new TabularCatalogService(xrepository, new ObjectMapper(), runtimeResolver);
   }

   // ----- C1: null relationships() -----

   @Test
   void listTables_nullRelationships_throwsNamedExceptionNotNpe() throws Exception {
      TabularCatalog catalog = new TabularCatalog(List.of(new TabularDatasetRef("X")), null);
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      // The whole point of C1: distinguishing "actually fixed" from "happened not to crash".
      assertFalse(ex instanceof NullPointerException);
      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().toLowerCase().contains("relationship"));
   }

   // ----- C2: relationship endpoint not in datasets() -----

   @Test
   void listTables_relationshipFromDatasetMissing_throwsNamingTheViolation() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("B")),
         List.of(new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of("y"))));
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().contains("R_A_B") || ex.getMessage().contains("A"),
         "message must name the offending relationship or its missing endpoint: " +
         ex.getMessage());
   }

   @Test
   void listTables_relationshipToDatasetMissing_throwsNamingTheViolation() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A")),
         List.of(new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of("y"))));
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().contains("R_A_B") || ex.getMessage().contains("B"),
         "message must name the offending relationship or its missing endpoint: " +
         ex.getMessage());
   }

   // ----- C3: blank dataset id / column name (isBlank(), not isEmpty()) -----

   @Test
   void listTables_emptyStringDatasetId_throwsNamedException() throws Exception {
      TabularCatalog catalog =
         new TabularCatalog(List.of(new TabularDatasetRef("")), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().toLowerCase().contains("id"));
   }

   @Test
   void listTables_blankWhitespaceDatasetId_throwsNamedException() throws Exception {
      // Deliberately separate from the empty-string case: "   ".isEmpty() is false, so a
      // validator written with isEmpty() instead of isBlank() would let this one slip through.
      TabularCatalog catalog =
         new TabularCatalog(List.of(new TabularDatasetRef("   ")), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().toLowerCase().contains("id"));
   }

   @Test
   void describeTable_emptyStringColumnName_throwsNamedException() throws Exception {
      TabularDatasetSchema schema = new TabularDatasetSchema("Products",
         List.of(new TabularColumn("", XSchema.STRING)), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(
         new TabularCatalog(List.of(), List.of()), Map.of("Products", schema));
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class,
         () -> service.describeTable(DS_NAME, "Products"));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().toLowerCase().contains("column"));
   }

   @Test
   void describeTable_blankWhitespaceColumnName_throwsNamedException() throws Exception {
      // Same isBlank()-vs-isEmpty() concern as the dataset-id pair above, on the column-name half
      // of C3.
      TabularDatasetSchema schema = new TabularDatasetSchema("Products",
         List.of(new TabularColumn("   ", XSchema.STRING)), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(
         new TabularCatalog(List.of(), List.of()), Map.of("Products", schema));
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class,
         () -> service.describeTable(DS_NAME, "Products"));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().toLowerCase().contains("column"));
   }

   // ----- C6: TabularDatasetRef.id must not contain '.' -----

   @Test
   void listTables_dottedDatasetId_throwsNamedException() throws Exception {
      TabularCatalog catalog =
         new TabularCatalog(List.of(new TabularDatasetRef("contoso.sharepoint.com")), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().contains("contoso.sharepoint.com"),
         "message must name the offending id: " + ex.getMessage());
   }

   // ----- C6: TabularRelationship.name must be non-blank -----

   @Test
   void listTables_blankRelationshipName_throwsNamedException() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")),
         List.of(new TabularRelationship("   ", "A", "B", List.of("x"), List.of("y"))));
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().toLowerCase().contains("name"));
   }

   // ----- C6: TabularRelationship.fromColumns/toColumns must be non-empty and equal-length -----

   @Test
   void listTables_emptyFromColumns_throwsNamedException() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")),
         List.of(new TabularRelationship("R_A_B", "A", "B", List.of(), List.of("y"))));
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().contains("R_A_B"),
         "message must name the offending relationship: " + ex.getMessage());
   }

   @Test
   void listTables_emptyToColumns_throwsNamedException() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")),
         List.of(new TabularRelationship("R_A_B", "A", "B", List.of("x"), List.of())));
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().contains("R_A_B"),
         "message must name the offending relationship: " + ex.getMessage());
   }

   @Test
   void listTables_mismatchedColumnListSizes_throwsNamedException() throws Exception {
      TabularCatalog catalog = new TabularCatalog(
         List.of(new TabularDatasetRef("A"), new TabularDatasetRef("B")),
         List.of(new TabularRelationship("R_A_B", "A", "B",
            List.of("x1", "x2"), List.of("y1"))));
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(catalog, Map.of());
      TabularCatalogService service = createService(dsName -> runtime);

      Exception ex = assertThrows(Exception.class, () -> service.listTables(DS_NAME));

      assertFalse(ex instanceof UnsupportedDatasourceException);
      assertTrue(ex.getMessage().contains(DS_NAME));
      assertTrue(ex.getMessage().contains("R_A_B"),
         "message must name the offending relationship: " + ex.getMessage());
   }
}
