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
 * projection of those components onto {@link OsiField} (description/label only) and the
 * field-level COMMON extension ({@code declaredIsDimension}). {@code isDimension} does NOT get a
 * top-level {@code OsiField} key: OSI's {@code Field} schema (`core-spec/ossie-schema.json`,
 * {@code $defs/Field}) sets {@code additionalProperties: false} over a fixed key list that does
 * not include it, so the declared kind's only conformant home is {@code custom_extensions} — the
 * spec's own sanctioned extension point. Driven by {@link FakeCatalogRuntime}, the same as
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

   // ----- #2: isDimension == null -> the COMMON extension key is omitted -----

   @Test
   void isDimensionNull_omitsCommonExtensionKey() throws Exception {
      OsiField field =
         describeSingleField(new TabularColumn("Name", XSchema.STRING, null, null, null));

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

   // ----- #4: declaredIsDimension and the unrelated is_time dimension flag do not collide, and
   //           no top-level "isDimension" key is ever serialized (OSI Field conformance) -----

   @Test
   void declaredIsDimensionKey_doesNotCollideWithTimeDimensionKey_andNoTopLevelIsDimensionKey()
      throws Exception
   {
      OsiField field = describeSingleField(
         new TabularColumn("EventDate", XSchema.DATE, null, null, Boolean.TRUE));

      assertNotNull(field.getDimension());
      assertTrue(field.getDimension().isTime());

      ObjectMapper mapper = new ObjectMapper();
      JsonNode json = mapper.valueToTree(field);
      // OSI's Field schema ($defs/Field, additionalProperties: false) does not list "isDimension"
      // among its allowed keys -- a top-level isDimension key would make this Field
      // non-conformant. The declared kind must live ONLY in custom_extensions.
      assertFalse(json.has("isDimension"));
      assertTrue(json.get("dimension").get("is_time").asBoolean());

      JsonNode extData = fieldExtensionData(field);
      assertTrue(extData.get("declaredIsDimension").asBoolean());
   }

   // ----- #5: declaredIsDimension is written in the COMMON extension, and nowhere else -----

   @Test
   void isDimensionFalse_writesDeclaredIsDimensionInCommonExtensionOnly() throws Exception {
      OsiField field = describeSingleField(
         new TabularColumn("activeUsers", XSchema.DOUBLE, "A count.", "Active users",
            Boolean.FALSE));

      assertEquals("A count.", field.getDescription());
      assertEquals("Active users", field.getLabel());

      ObjectMapper mapper = new ObjectMapper();
      assertFalse(mapper.valueToTree(field).has("isDimension"));

      JsonNode extData = fieldExtensionData(field);
      assertEquals(XSchema.DOUBLE, extData.get("type").asText());
      assertFalse(extData.get("declaredIsDimension").asBoolean());
   }
}
