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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the wire contract of {@code POST /api/wiz/viewsheet/format}: the camelCase axis
 * property names the wiz-services client sends (xAxisTitle, yAxisMin, ...) must bind, even
 * though Jackson's default bean-naming would canonicalize getXAxisTitle to "xaxisTitle".
 * Regression test for the live 400 (HttpMessageNotReadableException) hit 2026-06-05.
 */
@Tag("core")
class ChartFormatRequestTest {
   @Test
   void camelCaseAxisPropertiesDeserialize() throws Exception {
      // FAIL_ON_UNKNOWN_PROPERTIES mirrors the server's strict binding that produced the 400
      ObjectMapper mapper = new ObjectMapper()
         .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

      ChartFormatRequest req = mapper.readValue(
         """
         { "wizRuntimeId": "rt-1", "assemblyName": "Chart1",
           "chartTitle": "Contacts per Account", "xAxisTitle": "Gross", "yAxisTitle": "Actor",
           "yAxisMin": 0.5, "yAxisMax": 100.0, "yAxisIncrement": 10.0,
           "yAxisLogarithmic": true, "legendPosition": "top" }
         """,
         ChartFormatRequest.class);

      assertEquals("rt-1", req.getWizRuntimeId());
      assertEquals("Contacts per Account", req.getChartTitle());
      assertEquals("Gross", req.getXAxisTitle());
      assertEquals("Actor", req.getYAxisTitle());
      assertEquals(0.5, req.getYAxisMin());
      assertEquals(100.0, req.getYAxisMax());
      assertEquals(10.0, req.getYAxisIncrement());
      assertEquals(Boolean.TRUE, req.getYAxisLogarithmic());
      assertEquals("top", req.getLegendPosition());
   }

   @Test
   void setterBindsThroughTheRenamedProperty() throws Exception {
      // The explicit @JsonProperty on the getter must rename the whole accessor group
      // (getter + setter), not split it into two properties.
      ObjectMapper mapper = new ObjectMapper();
      ChartFormatRequest req = mapper.readValue("{ \"xAxisTitle\": \"T\" }", ChartFormatRequest.class);
      assertEquals("T", req.getXAxisTitle());
      assertTrue(mapper.writeValueAsString(req).contains("\"xAxisTitle\":\"T\""));
   }

   @Test
   void copyDefaultsToFalseWhenOmitted() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ChartFormatRequest req = mapper.readValue("{ \"wizRuntimeId\": \"rt-1\" }", ChartFormatRequest.class);
      assertFalse(req.isCopy());
   }

   @Test
   void copyDeserializesWhenTrue() throws Exception {
      ObjectMapper mapper = new ObjectMapper();
      ChartFormatRequest req = mapper.readValue("{ \"copy\": true }", ChartFormatRequest.class);
      assertTrue(req.isCopy());
   }
}
