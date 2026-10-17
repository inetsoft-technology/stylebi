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
import inetsoft.uql.viewsheet.VSAggregateRef;
import inetsoft.uql.viewsheet.VSDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.uql.viewsheet.VSCrosstabInfo;
import inetsoft.web.wiz.model.DimensionFieldInfo;
import inetsoft.web.wiz.model.MeasureFieldInfo;
import inetsoft.web.wiz.model.SimpleFieldInfo;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78744: a column bound as both a dimension and a measure. The rebuild keeps one field per
 * column (the dimension), so changeType must say which measure was dropped, and the dynamic-column
 * restore / config reads must only ever use the config of the ref's own kind.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class WizAutoBindingServiceCrossKindColumnTest {
   private static final String DYNAMIC = "$(RB)";

   private static VSChartDimensionRef dim(String design, String runtime) {
      VSChartDimensionRef d = new VSChartDimensionRef();
      d.setGroupColumnValue(design);
      d.getDynamicValues().get(0).setRValue(runtime);
      d.setDataRef(new ColumnRef(new AttributeRef(null, runtime)));
      return d;
   }

   private static VSChartAggregateRef measure(String design, String runtime, String formula) {
      VSChartAggregateRef m = new VSChartAggregateRef();
      m.setColumnValue(design);
      m.setFormulaValue(formula);
      m.setDataRef(new ColumnRef(new AttributeRef(null, runtime)));
      return m;
   }

   private static ChartVSAssembly chart(VSChartDimensionRef x, VSChartAggregateRef y) {
      VSChartInfo info = new VSChartInfo();
      info.addXField(x);
      info.addYField(y);
      ChartVSAssembly chart = mock(ChartVSAssembly.class);
      when(chart.getVSChartInfo()).thenReturn(info);
      when(chart.getName()).thenReturn("Chart1");
      return chart;
   }

   private static WizAutoBindingService service() {
      WizVsService wizVsService = new WizVsService(null, null, null, null, null, null);
      return new WizAutoBindingService(null, null, null, null, null, wizVsService, null, null);
   }

   private static DimensionFieldInfo dimCfg(String col, String dynamic) {
      DimensionFieldInfo f = new DimensionFieldInfo();
      f.setField(col);
      f.setDynamicColumnValue(dynamic);
      return f;
   }

   private static MeasureFieldInfo meaCfg(String col, String dynamic) {
      MeasureFieldInfo f = new MeasureFieldInfo();
      f.setField(col);
      f.setAggregateFormula("Sum");
      f.setDynamicColumnValue(dynamic);
      return f;
   }

   private static List<String> warnings(List<SimpleFieldInfo> configs) {
      return WizAutoBindingService.crossKindDroppedFields(configs);
   }

   // -- the changeType fallback warning, from the real derived field list --

   @Test
   void p1DynamicDimensionPlusStaticMeasureWarns() {
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(
         chart(dim(DYNAMIC, "Sales"), measure("Sales", "Sales", "Sum")));
      List<String> w = warnings(configs);
      assertEquals(1, w.size(), w.toString());
      assertTrue(w.get(0).contains("Sum(Sales)") && w.get(0).contains("dimension"), w.get(0));
   }

   @Test
   void p2StaticDimensionPlusDynamicMeasureWarns() {
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(
         chart(dim("State", "State"), measure(DYNAMIC, "State", "Count")));
      assertEquals(1, warnings(configs).size());
   }

   @Test
   void n1NonDynamicPairWarns() {
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(
         chart(dim("State", "State"), measure("State", "State", "Count")));
      List<String> w = warnings(configs);
      assertEquals(1, w.size());
      assertTrue(w.get(0).contains("State"));
   }

   @Test
   void n2DifferentColumnsDoNotWarn() {
      List<SimpleFieldInfo> configs = service().deriveFieldConfigs(
         chart(dim("State", "State"), measure("Sales", "Sales", "Sum")));
      assertTrue(warnings(configs).isEmpty());
   }

   @Test
   void duplicateMeasureNamesWarnOnce() {
      List<String> w = warnings(List.of(dimCfg("State", null), meaCfg("State", null),
                                        meaCfg("State", null)));
      assertEquals(1, w.size());
   }

   // -- kind-aware restore --

   private static Map<String, SimpleFieldInfo> split(SimpleFieldInfo... configs) {
      return new WizAutoBindingService.KindSplitConfigMap(List.of(configs));
   }

   @Test
   void t1DynamicDimensionDoesNotLeakOntoStaticMeasure() {
      VSChartInfo info = new VSChartInfo();
      VSChartDimensionRef x = dim("Sales", "Sales");
      VSChartAggregateRef y = measure("Sales", "Sales", "Sum");
      info.addXField(x);
      info.addYField(y);

      WizAutoBindingService.restoreChartDynamicColumnValues(
         info, split(dimCfg("Sales", DYNAMIC), meaCfg("Sales", null)));

      assertEquals(DYNAMIC, x.getGroupColumnValue());
      assertEquals("Sales", y.getColumnValue());
   }

   @Test
   void t2DynamicMeasureIsRestoredEvenWhenStaticDimensionComesFirst() {
      VSChartInfo info = new VSChartInfo();
      VSChartDimensionRef x = dim("State", "State");
      VSChartAggregateRef y = measure("State", "State", "Count");
      info.addXField(x);
      info.addYField(y);

      WizAutoBindingService.restoreChartDynamicColumnValues(
         info, split(dimCfg("State", null), meaCfg("State", DYNAMIC)));

      assertEquals("State", x.getGroupColumnValue());
      assertEquals(DYNAMIC, y.getColumnValue());
   }

   @Test
   void plainMapIgnoresAConfigOfTheOtherKind() {
      VSChartInfo info = new VSChartInfo();
      VSChartAggregateRef y = measure("Sales", "Sales", "Sum");
      info.addYField(y);

      WizAutoBindingService.restoreChartDynamicColumnValues(
         info, Map.of("Sales", dimCfg("Sales", DYNAMIC)));

      assertEquals("Sales", y.getColumnValue());
   }

   @Test
   void t3CrosstabRestoreIsKindAware() {
      VSCrosstabInfo info = new VSCrosstabInfo();
      VSDimensionRef row = new VSDimensionRef();
      row.setGroupColumnValue("State");
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("State");
      agg.setFormulaValue("Count");
      info.setDesignRowHeaders(new DataRef[]{ row });
      info.setDesignAggregates(new DataRef[]{ agg });

      WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, split(dimCfg("State", null), meaCfg("State", DYNAMIC)));

      assertEquals("State", row.getGroupColumnValue());
      assertEquals(DYNAMIC, agg.getColumnValue());
   }

   @Test
   void t3CrosstabDynamicDimensionDoesNotLeakOntoMeasure() {
      VSCrosstabInfo info = new VSCrosstabInfo();
      VSDimensionRef row = new VSDimensionRef();
      row.setGroupColumnValue("Sales");
      VSAggregateRef agg = new VSAggregateRef();
      agg.setColumnValue("Sales");
      agg.setFormulaValue("Sum");
      info.setDesignRowHeaders(new DataRef[]{ row });
      info.setDesignAggregates(new DataRef[]{ agg });

      WizAutoBindingService.applyCrosstabAggregateFormulas(
         info, split(dimCfg("Sales", DYNAMIC), meaCfg("Sales", null)));

      assertEquals(DYNAMIC, row.getGroupColumnValue());
      assertEquals("Sales", agg.getColumnValue());
   }
}
