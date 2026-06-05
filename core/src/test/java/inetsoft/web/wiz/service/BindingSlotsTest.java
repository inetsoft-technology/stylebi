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
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
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
