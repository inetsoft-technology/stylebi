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
class WizDashboardResultTest {
   @Test
   void serializesCoverageFields() throws Exception {
      WizDashboardResult r = new WizDashboardResult();
      r.setSavedViewsheetIdentifier("d1");
      r.setSkipped(java.util.List.of());
      r.setFiltersApplied(java.util.List.of("Region"));
      r.setFiltersSkipped(java.util.List.of("OrderDate"));
      String json = new ObjectMapper().writeValueAsString(r);
      assertTrue(json.contains("\"filtersApplied\":[\"Region\"]"));
      assertTrue(json.contains("\"filtersSkipped\":[\"OrderDate\"]"));
   }
}
