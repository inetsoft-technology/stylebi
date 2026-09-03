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
import inetsoft.uql.XRepository;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.*;
import inetsoft.web.wiz.model.osi.OsiCustomExtension;
import inetsoft.web.wiz.model.osi.OsiDataset;
import inetsoft.web.wiz.model.osi.OsiField;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers the field-level part of GA4's contribution to the SPI: {@link TabularColumn}'s three new
 * components (description/label/isDimension), the compatibility constructor that keeps all six
 * pre-existing connectors' construction sites unchanged, and {@link TabularCatalogService}'s
 * projection of those components onto {@link OsiField} and its field-level COMMON extension
 * ({@code declaredIsDimension}). Driven by {@link FakeCatalogRuntime}, the same as
 * {@link TabularCatalogServiceTest} — this is not a GA4-specific test, it verifies the neutral SPI
 * type and the one service that projects it.
 */
@Tag("core")
class TabularCatalogFieldDeclarationTest {

   private static final String DS_NAME = "Fake Declaration Source";

   private TabularCatalogService createService(Function<String, TabularRuntime> runtimeResolver)
      throws Exception
   {
      XRepository xrepository = mock(XRepository.class);
      when(xrepository.getDataSource(DS_NAME)).thenReturn(new FakeTabularDataSource());
      return new TabularCatalogService(xrepository, new ObjectMapper(), runtimeResolver);
   }

   private OsiField describeSingleField(TabularColumn column) throws Exception {
      TabularDatasetSchema schema =
         new TabularDatasetSchema("Widgets", List.of(column), List.of());
      FakeCatalogRuntime runtime = new FakeCatalogRuntime(
         new TabularCatalog(List.of(), List.of()), Map.of("Widgets", schema));
      TabularCatalogService service = createService(dsName -> runtime);

      OsiDataset dataset = service.describeTable(DS_NAME, "Widgets");
      assertEquals(1, dataset.getFields().size());
      return dataset.getFields().get(0);
   }

   private JsonNode fieldExtensionData(OsiField field) throws Exception {
      OsiCustomExtension ext = field.getCustomExtensions().get(0);
      assertEquals("COMMON", ext.getVendorName());
      return new ObjectMapper().readTree(ext.getData());
   }

   // ----- #1: compatibility constructor -----

   @Test
   void compatibilityConstructor_leavesNewComponentsNull() {
      TabularColumn column = new TabularColumn("ID", XSchema.LONG);
      assertEquals("ID", column.name());
      assertEquals(XSchema.LONG, column.type());
      assertNull(column.description());
      assertNull(column.label());
      assertNull(column.isDimension());
   }

   // ----- #2: isDimension == null -> neither key written -----

   @Test
   void isDimensionNull_omitsBothOsiFieldKeyAndCommonExtensionKey() throws Exception {
      OsiField field =
         describeSingleField(new TabularColumn("Name", XSchema.STRING, null, null, null));

      assertNull(field.getIsDimension());
      assertFalse(fieldExtensionData(field).has("declaredIsDimension"));
   }

   // ----- #3: blank description/label are not written -----

   @Test
   void blankDescriptionAndLabel_areNotWrittenAsEmptyStrings() throws Exception {
      OsiField field =
         describeSingleField(new TabularColumn("Name", XSchema.STRING, "", "  ", null));

      assertNull(field.getDescription());
      assertNull(field.getLabel());
   }

   // ----- #4: isDimension and the unrelated is_time dimension flag do not collide -----

   @Test
   void isDimensionKey_doesNotCollideWithTimeDimensionKey() throws Exception {
      OsiField field = describeSingleField(
         new TabularColumn("EventDate", XSchema.DATE, null, null, Boolean.TRUE));

      assertEquals(Boolean.TRUE, field.getIsDimension());
      assertNotNull(field.getDimension());
      assertTrue(field.getDimension().isTime());

      ObjectMapper mapper = new ObjectMapper();
      JsonNode json = mapper.valueToTree(field);
      assertTrue(json.get("isDimension").asBoolean());
      assertTrue(json.get("dimension").get("is_time").asBoolean());
   }

   // ----- #5: declaredIsDimension and OsiField.isDimension are both written -----

   @Test
   void isDimensionFalse_writesBothOsiFieldKeyAndCommonExtensionKey() throws Exception {
      OsiField field = describeSingleField(
         new TabularColumn("activeUsers", XSchema.DOUBLE, "A count.", "Active users",
            Boolean.FALSE));

      assertEquals(Boolean.FALSE, field.getIsDimension());
      assertEquals("A count.", field.getDescription());
      assertEquals("Active users", field.getLabel());

      JsonNode extData = fieldExtensionData(field);
      assertEquals(XSchema.DOUBLE, extData.get("type").asText());
      assertFalse(extData.get("declaredIsDimension").asBoolean());
   }
}
