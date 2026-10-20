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
package inetsoft.web.wiz.viewsheet;

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.CalculateRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSFormat;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.graph.handler.ChartRegionHandler;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #78910: {@code set_format(target:"field")} must format the chart's text-aesthetic data
 * labels for every aggregate kind, not only the same-type formulas (Sum, Max, ...) whose format
 * key is the bare column. Count / DistinctCount and an aggregate calc field key by a name that
 * equals a Y ref's full name, which used to redirect the write to the Y ref and skip the
 * chart-level text field the labels render from.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ViewsheetFormatServiceTextLabelTest {
   @BeforeEach
   void setUp() throws Exception {
      Viewsheet viewsheet = new Viewsheet();
      chart = new ChartVSAssembly(viewsheet, "Chart1");
      viewsheet.addAssembly(chart);
      info = new DefaultVSChartInfo();
      chart.setVSChartInfo(info);

      ViewsheetSandbox box = mock(ViewsheetSandbox.class);
      rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(viewsheet);
      when(rvs.getViewsheetSandbox()).thenReturn(Optional.of(box));

      Constructor<?> ctor = VSWizardBindingHandler.class.getConstructors()[0];
      Object[] args = new Object[ctor.getParameterCount()];

      for(int i = 0; i < args.length; i++) {
         Class<?> type = ctor.getParameterTypes()[i];
         // the name lookup under test lives in the real region handler; a mock returns null
         args[i] = type == ChartRegionHandler.class ? new ChartRegionHandler() : mock(type);
      }

      handler = (VSWizardBindingHandler) ctor.newInstance(args);
   }

   @Test
   void plainSumLabelsTakeTheFieldFormat() {
      assertTextLabelFormatted(aggregate("Revenue", "Sum"));
   }

   @Test
   void countLabelsTakeTheFieldFormat() {
      assertTextLabelFormatted(aggregate("Revenue", "Count"));
   }

   @Test
   void distinctCountLabelsTakeTheFieldFormat() {
      assertTextLabelFormatted(aggregate("Revenue", "DistinctCount"));
   }

   @Test
   void aggregateCalcFieldLabelsTakeTheFieldFormat() {
      assertTextLabelFormatted(calcAggregate("AvgPerDay", false));
   }

   @Test
   void detailCalcFieldLabelsTakeTheFieldFormat() {
      assertTextLabelFormatted(calcAggregate("Dbl", true));
   }

   /** Y measure and text-aesthetic measure are separate but equal refs, as the binding builds them. */
   private void assertTextLabelFormatted(VSChartAggregateRef measure) {
      info.addXField(dimension("Region"));
      info.addYField(measure);
      VSAestheticRef text = new VSAestheticRef();
      text.setDataRef((VSChartAggregateRef) measure.clone());
      info.setTextField(text);

      VSFormat format = new VSFormat();
      format.setFormatValue("DecimalFormat");
      format.setFormatExtentValue("0.0000");

      assertEquals(java.util.Set.of(),
                   handler.applyFieldFormats(rvs, chart, Map.of(measure.getFullName(), format)));

      VSChartAggregateRef textRef = (VSChartAggregateRef) info.getTextField().getDataRef();
      assertEquals("DecimalFormat",
                   textRef.getTextFormat().getUserDefinedFormat().getFormat().getFormat(),
                   "text-aesthetic data labels format for " + measure.getFullName());
      assertEquals("0.0000",
                   textRef.getTextFormat().getUserDefinedFormat().getFormat().getFormatSpec());
   }

   private static VSChartAggregateRef aggregate(String column, String formula) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
      ref.setDataType(XSchema.DOUBLE);
      return aggregateOf(ref, column, formula);
   }

   private static VSChartAggregateRef calcAggregate(String name, boolean baseOnDetail) {
      CalculateRef calc = new CalculateRef(baseOnDetail);
      calc.setDataRef(new AttributeRef(null, name));
      calc.setDataType(XSchema.DOUBLE);
      return aggregateOf(calc, name, baseOnDetail ? "Sum" : "None");
   }

   private static VSChartAggregateRef aggregateOf(ColumnRef ref, String column, String formula) {
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setDataRef(ref);
      aggregate.setColumnValue(column);
      aggregate.setFormulaValue(formula);
      return aggregate;
   }

   private static VSChartDimensionRef dimension(String column) {
      ColumnRef ref = new ColumnRef(new AttributeRef(null, column));
      ref.setDataType(XSchema.STRING);
      VSChartDimensionRef dimension = new VSChartDimensionRef();
      dimension.setDataRef(ref);
      dimension.setGroupColumnValue(column);
      return dimension;
   }

   private ChartVSAssembly chart;
   private VSChartInfo info;
   private RuntimeViewsheet rvs;
   private VSWizardBindingHandler handler;
}
