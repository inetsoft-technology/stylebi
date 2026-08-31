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
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.tabular.TabularColumn;
import inetsoft.uql.tabular.TabularDatasetSchema;
import inetsoft.web.wiz.model.osi.OsiCustomExtension;
import inetsoft.web.wiz.model.osi.OsiDataset;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Charter assertion B7, isolated from the rest of {@link TabularCatalogServiceTest}: a METADATA
 * target's {@link OsiDataset} carries a COMMON extension with {@code datasourceType: "tabular"}.
 *
 * The literal {@code "tabular"} is asserted written out below, deliberately not referenced from a
 * shared constant — pinning the literal is the entire point. There is no shared constant between
 * this repository and wiz-services; {@code TABULAR_DATASOURCE_TYPE} in
 * {@code wiz-services/src/types/osiTypes.ts} is the only other place this string is spelled out,
 * and {@code isTabularDatasourceType()} there compares against it verbatim.
 */
@Tag("core")
class TabularCatalogDatasetExtensionTest {
   @Test
   void datasetCommonExtensionMarksDatasourceTypeTabular() throws Exception {
      TabularDatasetSchema schema = new TabularDatasetSchema("Products",
         List.of(new TabularColumn("ID", XSchema.LONG)), List.of("ID"));

      OsiDataset dataset =
         TabularCatalogService.toDataset("Rest/Northwind", "OData", schema, new ObjectMapper());

      OsiCustomExtension ext = dataset.getCustomExtensions().get(0);
      assertEquals("COMMON", ext.getVendorName());

      JsonNode data = new ObjectMapper().readTree(ext.getData());
      assertEquals("tabular", data.get("datasourceType").asText());
      assertEquals("Rest/Northwind", data.get("dsName").asText());
      assertEquals("Rest/Northwind", data.get("path").asText());
      assertEquals("OData", data.get("datasourceSubtype").asText());
   }
}
