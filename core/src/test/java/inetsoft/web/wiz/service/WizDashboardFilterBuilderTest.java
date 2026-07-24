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
import inetsoft.uql.viewsheet.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizDashboardFilterBuilderTest {
   private final WizDashboardFilterBuilder builder = new WizDashboardFilterBuilder();

   @Test
   void categoricalFieldMakesASelectionList() {
      Viewsheet vs = new Viewsheet();
      // A field on NO table binds to nothing -> skipped, but the control TYPE is still decided
      // by data type. Assert via a package-visible helper the builder exposes for the type choice:
      AbstractSelectionVSAssembly a = builder.createControlForType(vs, "string", column("Region", "string"));
      assertTrue(a instanceof SelectionListVSAssembly);
   }

   @Test
   void dateAndNumericFieldsMakeRangeSliders() {
      Viewsheet vs = new Viewsheet();
      assertTrue(builder.createControlForType(vs, "date", column("OrderDate", "date")) instanceof TimeSliderVSAssembly);
      assertTrue(builder.createControlForType(vs, "integer", column("Qty", "integer")) instanceof TimeSliderVSAssembly);
   }

   @Test
   void mergedChartTableNamesCollectsOnlyChartAssembliesOwnTables() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly radar = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(radar, "RADAR_FINAL");
      vs.addAssembly(radar);
      ChartVSAssembly mr = new ChartVSAssembly(vs, "MrChart");
      boundToTable(mr, "MR_FINAL");
      vs.addAssembly(mr);
      // A non-chart assembly must not contribute a table name, even though it also implements
      // BindableVSAssembly -- only ChartVSAssembly is considered (see the method's javadoc).
      TextVSAssembly text = new TextVSAssembly(vs, "Title");
      vs.addAssembly(text);

      List<String> names = builder.mergedChartTableNames(vs);

      assertEquals(List.of("RADAR_FINAL", "MR_FINAL"), names);
   }

   @Test
   void buildBindsOnlyToTheChartsOwnTableNotAnUnrelatedTableSharingTheColumn() {
      Worksheet ws = new Worksheet();
      ws.addAssembly(physicalTable(ws, "CHART_FINAL", "category_name", "norm_revenue"));
      // Simulates the shared global-aggregate table upstream of a chart's normalization step:
      // it also exposes category_name, but no chart is bound to it directly, so it must never
      // receive the filter (that was the root cause of the live "No data" regression: binding a
      // filter to this kind of table cascades into the aggregate it feeds).
      ws.addAssembly(physicalTable(ws, "GLOBAL_STATS", "category_name", "min_revenue", "max_revenue"));

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "RadarChart");
      boundToTable(chart, "CHART_FINAL");
      vs.addAssembly(chart);

      WizDashboardFilterBuilder.FilterResult result = builder.build(
         vs, ws, List.of(new WizDashboardFilterBuilder.FilterRequest("category_name", "string", "Category")));

      assertEquals(List.of("category_name"), result.applied());
      assertTrue(result.skipped().isEmpty());

      AbstractSelectionVSAssembly control = java.util.Arrays.stream(vs.getAssemblies())
         .filter(a -> a instanceof AbstractSelectionVSAssembly)
         .map(a -> (AbstractSelectionVSAssembly) a)
         .findFirst().orElseThrow();
      assertEquals(List.of("CHART_FINAL"), control.getTableNames(),
         "must bind only to the chart's own table, never GLOBAL_STATS");
   }

   // ChartVSAssembly.setTableName(String) silently no-ops when getSourceInfo() is still null (it
   // builds a local SourceInfo but never calls setSourceInfo(...) to store it back) -- harmless
   // in production, where a chart's SourceInfo is always initialized before setTableName is
   // ever called, but a real gap for a bare `new ChartVSAssembly(...)` test fixture. Set the
   // SourceInfo directly instead of relying on setTableName here.
   private static void boundToTable(ChartVSAssembly chart, String tableName) {
      SourceInfo source = new SourceInfo(SourceInfo.ASSET, null, tableName);
      chart.setSourceInfo(source);
   }

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

   // Helper mirrors AddFilterService.buildColumnRef (AttributeRef + ColumnRef with dataType).
   // NOTE: ColumnRef lives in inetsoft.uql.asset (not inetsoft.uql.erm as the task brief sketch
   // assumed) — confirmed against AddFilterService's imports in Step 1.
   private static inetsoft.uql.erm.DataRef column(String name, String dtype) {
      inetsoft.uql.erm.AttributeRef attr = new inetsoft.uql.erm.AttributeRef(name);
      attr.setDataType(dtype);
      inetsoft.uql.asset.ColumnRef col = new inetsoft.uql.asset.ColumnRef(attr);
      col.setDataType(dtype);
      return col;
   }
}
