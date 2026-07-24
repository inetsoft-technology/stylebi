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

import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.PhysicalBoundTableAssembly;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.asset.Worksheet;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for {@link AddFilterService#findColumnMatchingChartTables}: unlike
 * {@link AddFilterService#findColumnMatchingRootTables} (every root/physical table exposing the
 * column), this only considers the given candidate table names — so a table exposing the column
 * that is NOT one of the caller's candidates (e.g. a chart's own final bound table list) is never
 * returned, even if it would otherwise match. This scoping is what fixes a live bug: binding a
 * dashboard filter to a shared root table cascaded through a chart's cross-category normalization
 * (min/max) computation, collapsing it to the filtered subset and corrupting the chart.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class AddFilterServiceTest {
   private static PhysicalBoundTableAssembly physicalTable(Worksheet ws, String assemblyName, String... columns) {
      PhysicalBoundTableAssembly table = new PhysicalBoundTableAssembly(ws, assemblyName);
      SourceInfo si = new SourceInfo(SourceInfo.PHYSICAL_TABLE, "postgres", "public." + assemblyName);
      table.setSourceInfo(si);

      ColumnSelection cs = new ColumnSelection();

      for(String name : columns) {
         AttributeRef ref = new AttributeRef(null, name);
         ref.setDataType(XSchema.STRING);
         ColumnRef col = new ColumnRef(ref);
         col.setDataType(XSchema.STRING);
         cs.addAttribute(col);
      }

      table.setColumnSelection(cs, false);
      return table;
   }

   @Test
   void matchesOnlyCandidateTablesExposingTheColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name", "revenue"));
      ws.addAssembly(physicalTable(ws, "GLOBAL_STATS", "category_name", "min_revenue", "max_revenue"));
      ws.addAssembly(physicalTable(ws, "OTHER_TABLE", "product_name"));

      List<String> result = AddFilterService.findColumnMatchingChartTables(
         ws, List.of("CHART_FINAL"), "category_name");

      assertEquals(List.of("CHART_FINAL"), result,
         "must match the requested candidate, and must NOT include GLOBAL_STATS even though it " +
         "also exposes category_name -- it was not in the candidate list");
   }

   @Test
   void skipsACandidateThatDoesNotExposeTheColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "product_name"));

      List<String> result = AddFilterService.findColumnMatchingChartTables(
         ws, List.of("CHART_FINAL"), "category_name");

      assertTrue(result.isEmpty());
   }

   @Test
   void skipsACandidateNameThatDoesNotExistInTheWorksheet() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      List<String> result = AddFilterService.findColumnMatchingChartTables(
         ws, List.of("NONEXISTENT_TABLE"), "category_name");

      assertTrue(result.isEmpty());
   }

   @Test
   void returnsEmptyForNullOrEmptyInputs() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name"));

      assertTrue(AddFilterService.findColumnMatchingChartTables(null, List.of("CHART_FINAL"), "category_name").isEmpty());
      assertTrue(AddFilterService.findColumnMatchingChartTables(ws, null, "category_name").isEmpty());
      assertTrue(AddFilterService.findColumnMatchingChartTables(ws, List.of(), "category_name").isEmpty());
   }

   @Test
   void matchesMultipleCandidatesIndependently() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "RADAR_FINAL", "category_name", "norm_revenue"));
      ws.addAssembly(physicalTable(ws, "MR_FINAL", "product_name"));

      List<String> result = AddFilterService.findColumnMatchingChartTables(
         ws, List.of("RADAR_FINAL", "MR_FINAL"), "category_name");

      assertEquals(List.of("RADAR_FINAL"), result,
         "MR_FINAL is a valid candidate but doesn't expose category_name, so it's correctly excluded");
   }
}
