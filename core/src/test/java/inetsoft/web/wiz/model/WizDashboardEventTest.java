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
package inetsoft.web.wiz.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core")
class WizDashboardEventTest {
   private final ObjectMapper mapper = new ObjectMapper();

   @Test
   void deserializesTilesAndLayoutColumns() throws Exception {
      String json = "{\"name\":\"B\",\"identifiers\":[\"v1\",\"v2\"]," +
         "\"layoutColumns\":2,\"tiles\":[{\"identifier\":\"v1\",\"spanCols\":1,\"spanRows\":1}," +
         "{\"identifier\":\"v2\",\"spanCols\":2,\"spanRows\":2}]}";
      WizDashboardEvent ev = mapper.readValue(json, WizDashboardEvent.class);
      assertEquals(2, ev.getLayoutColumns());
      assertEquals(2, ev.getTiles().size());
      assertEquals("v2", ev.getTiles().get(1).getIdentifier());
      assertEquals(2, ev.getTiles().get(1).getSpanCols());
      assertEquals(2, ev.getTiles().get(1).getSpanRows());
   }

   @Test
   void toleratesAbsentTiles() throws Exception {
      WizDashboardEvent ev = mapper.readValue("{\"name\":\"B\",\"identifiers\":[\"v1\"]}", WizDashboardEvent.class);
      assertNull(ev.getTiles());
      assertNull(ev.getLayoutColumns());
   }

   @Test
   void spanColsDefaultsToOneWhenOmitted() throws Exception {
      WizDashboardEvent ev = mapper.readValue(
         "{\"tiles\":[{\"identifier\":\"v1\"}]}", WizDashboardEvent.class);
      assertEquals(1, ev.getTiles().get(0).getSpanCols());
   }

   @Test
   void spanRowsDefaultsToOneWhenOmitted() throws Exception {
      WizDashboardEvent ev = mapper.readValue(
         "{\"tiles\":[{\"identifier\":\"v1\"}]}", WizDashboardEvent.class);
      assertEquals(1, ev.getTiles().get(0).getSpanRows());
   }

   @Test
   void deserializesFilters() throws Exception {
      String json = "{\"name\":\"B\",\"identifiers\":[\"v1\"]," +
         "\"filters\":[{\"field\":\"Region\",\"dataType\":\"string\",\"label\":\"Region\"}," +
         "{\"field\":\"OrderDate\",\"dataType\":\"date\"}]}";
      WizDashboardEvent ev = mapper.readValue(json, WizDashboardEvent.class);
      assertEquals(2, ev.getFilters().size());
      assertEquals("Region", ev.getFilters().get(0).getField());
      assertEquals("string", ev.getFilters().get(0).getDataType());
      assertEquals("Region", ev.getFilters().get(0).getLabel());
      assertEquals("date", ev.getFilters().get(1).getDataType());
      assertNull(ev.getFilters().get(1).getLabel());
   }
}
