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
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.DateRangeRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CrosstabVSAssembly;
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.CreateViewsheetResult;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * collectChartSlots reports which slot each bound field landed in, reading the runtime
 * refs the renderer actually consumes (getRT*Fields()) and falling back to design refs
 * before execution. Dimensions are reported by field name; measures by their full
 * aggregate name ("Sum(amount)") to match the rankingCol convention.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class BindingSlotsTest {
   @Test
   void chartSlotsReportResolvedPlacement() {
      VSChartInfo info = new VSChartInfo();
      info.addXField(dimension("actor_name"));
      info.addYField(aggregate("amount", "Sum"));

      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(dimension("country"));
      info.setColorField(color);

      Map<String, Object> slots = WizVsService.collectChartSlots(info);

      assertEquals(List.of("actor_name"), slots.get("x"),
         "dimension on x reported by field name");
      // Measures are reported as their full aggregate name to match the rankingCol convention.
      assertEquals(List.of("Sum(amount)"), slots.get("y"),
         "measure on y reported as full aggregate name");
      assertEquals("country", slots.get("color"),
         "aesthetic dimension reported by field name");
      assertNull(slots.get("shape"), "unbound aesthetic slot is null");
   }

   @Test
   void runtimeRefsWinOverDesignRefs() {
      VSChartInfo info = new VSChartInfo();
      info.addXField(dimension("design_field"));
      info.setRTXFields(new ChartRef[]{ dimension("rt_field") });

      Map<String, Object> slots = WizVsService.collectChartSlots(info);

      assertEquals(List.of("rt_field"), slots.get("x"),
         "runtime refs (what the renderer reads) win over design refs");
   }

   @Test
   void crosstabAggregateSlotUsesFullAggregateName() {
      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[]{ crosstabDimension("actor_name") });
      cinfo.setDesignColHeaders(new DataRef[]{ crosstabDimension("country") });
      cinfo.setDesignAggregates(new DataRef[]{ crosstabAggregate("amount", "Sum") });

      Map<String, Object> slots = WizVsService.collectCrosstabSlots(cinfo);

      assertEquals(List.of("actor_name"), slots.get("rows"),
         "row header dimension reported by field name");
      assertEquals(List.of("country"), slots.get("cols"),
         "col header dimension reported by field name");
      // The fix: crosstab design aggregates are plain VSAggregateRef (not
      // VSChartAggregateRef); the aggregates slot must still report the full
      // aggregate name to line up with the measures list / rankingCol convention.
      assertEquals(List.of("Sum(amount)"), slots.get("aggregates"),
         "crosstab aggregate reported as full aggregate name, not the bare column");
   }

   @Test
   void crosstabDateLevelDimensionSlotUsesLevelQualifiedName() {
      // A crosstab design ref built from an explicit binding is a plain VSDimensionRef
      // with no backing ColumnRef (no setDataRef), so getAttribute() == "". Before the
      // fix, both the dimensions echo (createDimensionFieldInfo) and the rows slot name
      // (getChartRefFieldName) collapsed to "" for a date-level dim like this one.
      VSDimensionRef dim = new VSDimensionRef();
      dim.setGroupColumnValue("date_start");
      dim.setDataType(XSchema.TIME_INSTANT);
      dim.setDateLevelValue(String.valueOf(XConstants.DAY_OF_WEEK_DATE_GROUP));

      VSCrosstabInfo cinfo = new VSCrosstabInfo();
      cinfo.setDesignRowHeaders(new DataRef[]{ dim });
      cinfo.setDesignAggregates(new DataRef[]{ crosstabAggregate("amount", "Sum") });

      String expectedFullName =
         DateRangeRef.getName("date_start", XConstants.DAY_OF_WEEK_DATE_GROUP);

      // Fix 2: slots.rows must report the level-qualified name, not "".
      Map<String, Object> slots = WizVsService.collectCrosstabSlots(cinfo);
      assertEquals(List.of(expectedFullName), slots.get("rows"),
         "date-level crosstab row dimension slot falls back to the level-qualified name");

      // Fix 1: the dimensions echo (reached via the public collectFlatBinding entry
      // point, same as the create_viewsheet/apply_binding response path) must route
      // through createCrosstabDimensionFieldInfo, not the chart variant.
      CrosstabVSAssembly assembly = new CrosstabVSAssembly();
      assembly.setVSCrosstabInfo(cinfo);

      WizVsService service = new WizVsService(null, null);
      CreateViewsheetResult.FlatBinding binding = service.collectFlatBinding(assembly);

      assertEquals(1, binding.getDimensions().size());
      DimensionFieldInfo info = binding.getDimensions().get(0);
      assertEquals("date_start", info.getField());
      assertEquals(expectedFullName, info.getFullName(),
         "crosstab dimensions echo carries the level-qualified fullName");
   }

   private static VSDimensionRef crosstabDimension(String field) {
      VSDimensionRef dim = new VSDimensionRef();
      dim.setGroupColumnValue(field);
      // crosstab dimensions are plain VSDimensionRef, so the slot name resolves via
      // getAttribute() (not the VSChartDimensionRef branch); back it with the column.
      dim.setDataRef(new AttributeRef(field));
      return dim;
   }

   private static VSAggregateRef crosstabAggregate(String column, String formula) {
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      agg.setAggregated(true);
      return agg;
   }

   private static VSChartDimensionRef dimension(String field) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(field);
      return dim;
   }

   private static VSChartAggregateRef aggregate(String column, String formula) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue(column);
      agg.setFormulaValue(formula);
      agg.setAggregated(true);
      return agg;
   }
}
