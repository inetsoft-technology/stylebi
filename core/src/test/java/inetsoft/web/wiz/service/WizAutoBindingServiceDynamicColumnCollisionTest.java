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
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.erm.DataRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78535: a chart that binds one column both statically and through a "$(...)" reference that
 * currently resolves to it. The rebuild binds each column once, so only one binding can survive a
 * type change; it must be the dynamic one whichever ref the field list visits first, and changeType
 * reports the collapse.
 *
 * <p>Runs the real {@code WizVsService.collectFlatBinding} over a real {@link VSChartInfo} (needs the
 * Spring context). The design refs carry the runtime state {@code VSDimensionRef.update()} leaves
 * after execution: the runtime value and the resolved group ref.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizAutoBindingServiceDynamicColumnCollisionTest {
   private static final String DYNAMIC = "$(RB)";

   private static VSChartDimensionRef dim(String design, String runtime) {
      VSChartDimensionRef d = new VSChartDimensionRef();
      d.setGroupColumnValue(design);
      d.getDynamicValues().get(0).setRValue(runtime);
      d.setDataRef(new ColumnRef(new AttributeRef(null, runtime)));
      return d;
   }

   private static VSChartAggregateRef measure(String design, String runtime) {
      VSChartAggregateRef m = new VSChartAggregateRef();
      m.setColumnValue(design);
      m.setFormulaValue("Count");
      m.setDataRef(new ColumnRef(new AttributeRef(null, runtime)));
      return m;
   }

   /** x = {@code x}, y = Sum(Sales), color = {@code color}. */
   private static ChartVSAssembly chart(DataRef x, DataRef color) {
      VSChartInfo info = new VSChartInfo();

      if(x instanceof VSChartDimensionRef xdim) {
         info.addXField(xdim);
      }

      VSChartAggregateRef sales = measure("Sales", "Sales");
      sales.setFormulaValue("Sum");
      info.addYField(sales);
      VSAestheticRef aref = new VSAestheticRef();
      aref.setDataRef(color);
      info.setColorField(aref);

      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      when(chart.getName()).thenReturn("Chart1");
      return chart;
   }

   private static WizAutoBindingService service() {
      WizVsService wizVsService = new WizVsService(null, null, null, null, null, null);
      return new WizAutoBindingService(null, null, null, null, null, wizVsService, null, null);
   }

   private static List<SimpleFieldInfo> stateConfigs(List<SimpleFieldInfo> configs) {
      return configs.stream().filter(c -> "State".equals(c.getField())).toList();
   }

   @Test
   void staticFirstStateFollowsTheReference() {
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(
         chart(dim("State", "State"), dim(DYNAMIC, "State")));

      List<SimpleFieldInfo> state = stateConfigs(configs);
      assertFalse(state.isEmpty());
      state.forEach(c -> assertEquals(DYNAMIC, c.getDynamicColumnValue()));
   }

   @Test
   void dynamicFirstStateFollowsTheReference() {
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(
         chart(dim(DYNAMIC, "State"), dim("State", "State")));

      List<SimpleFieldInfo> state = stateConfigs(configs);
      assertFalse(state.isEmpty());
      state.forEach(c -> assertEquals(DYNAMIC, c.getDynamicColumnValue()));
   }

   @Test
   void collisionIsReportedInEitherOrder() {
      WizAutoBindingService service = service();
      List<WizAutoBindingService.DynamicColumnCollision> expected =
         List.of(new WizAutoBindingService.DynamicColumnCollision("State", DYNAMIC, true));

      assertEquals(expected, service.dynamicColumnCollisions(
         chart(dim("State", "State"), dim(DYNAMIC, "State"))));
      assertEquals(expected, service.dynamicColumnCollisions(
         chart(dim(DYNAMIC, "State"), dim("State", "State"))));
   }

   @Test
   void dynamicReferenceAloneIsNoCollision() {
      ChartVSAssembly chart = chart(dim("Region", "Region"), dim(DYNAMIC, "State"));

      assertTrue(service().dynamicColumnCollisions(chart).isEmpty());
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(chart);
      SimpleFieldInfo region = configs.stream()
         .filter(c -> "Region".equals(c.getField())).findFirst().orElseThrow();
      assertNull(region.getDynamicColumnValue());
   }

   @Test
   void dynamicMeasureDoesNotMakeAStaticDimensionDynamic() {
      // x = static State dimension, color = Count($(RB)) resolving to State: different kinds.
      ChartVSAssembly chart = chart(dim("State", "State"), measure(DYNAMIC, "State"));

      assertTrue(service().dynamicColumnCollisions(chart).isEmpty());
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(chart);
      SimpleFieldInfo stateDim = configs.stream()
         .filter(c -> c instanceof DimensionFieldInfo && "State".equals(c.getField()))
         .findFirst().orElseThrow();
      assertNull(stateDim.getDynamicColumnValue());
   }

   @Test
   void dynamicDimensionDoesNotMakeAStaticMeasureDynamic() {
      // x = $(RB) dimension resolving to Sales, y = static Sum(Sales): different kinds.
      ChartVSAssembly chart = chart(dim(DYNAMIC, "Sales"), dim("Region", "Region"));

      assertTrue(service().dynamicColumnCollisions(chart).isEmpty());
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(chart);
      SimpleFieldInfo sales = configs.stream()
         .filter(c -> c instanceof MeasureFieldInfo && "Sales".equals(c.getField()))
         .findFirst().orElseThrow();
      assertNull(sales.getDynamicColumnValue());
   }

   @Test
   void measureCollisionFollowsTheReferenceAndLeavesTheDimensionStatic() {
      // x = static Sales dimension, y = static Sum(Sales), color = Count($(RB)) resolving to Sales:
      // the measures collide, the dimension of the same column is a different kind.
      ChartVSAssembly chart = chart(dim("Sales", "Sales"), measure(DYNAMIC, "Sales"));

      assertEquals(List.of(new WizAutoBindingService.DynamicColumnCollision("Sales", DYNAMIC, false)),
                   service().dynamicColumnCollisions(chart));
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(chart);
      List<SimpleFieldInfo> measures = configs.stream()
         .filter(c -> c instanceof MeasureFieldInfo && "Sales".equals(c.getField())).toList();
      assertFalse(measures.isEmpty());
      measures.forEach(c -> assertEquals(DYNAMIC, c.getDynamicColumnValue()));
      SimpleFieldInfo salesDim = configs.stream()
         .filter(c -> c instanceof DimensionFieldInfo && "Sales".equals(c.getField()))
         .findFirst().orElseThrow();
      assertNull(salesDim.getDynamicColumnValue());
   }
}
