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
package inetsoft.web.wiz.binding;

import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.composition.graph.calc.ChangeCalc;
import inetsoft.report.composition.graph.calc.ValueOfCalc;
import inetsoft.uql.viewsheet.graph.AbstractCalc;
import inetsoft.web.binding.model.graph.calc.ChangeCalcInfo;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.report.internal.graph.ChangeChartDataProcessor;
import inetsoft.report.internal.graph.ChangeChartProcessor;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.DefaultVSChartInfo;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.service.graph.aesthetic.VisualFrameModelFactoryService;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Design doc {@code 2026-09-11-bug-76608-secondary-y-axis} S7 StyleBI case 6 / S8's first named
 * residual risk: does anything downstream of {@code VSChartBindingFactory.updateAssembly} --
 * {@code VSChartInfo.updateChartType}, {@code GraphUtil.fixVisualFrames},
 * {@code ChangeChartDataProcessor.sortRefs}, {@code ChangeChartProcessor.fixMapFrame}/
 * {@code fixNamedGroup}/{@code syncTopN}/{@code fixAggregateRefs} -- reset a freshly-written
 * {@code secondaryY} before it reaches the runtime {@code VSChartAggregateRef}?
 *
 * <p>Deliberately does not go through {@code ChangeChartRefService.changeChartRef} itself --
 * that method additionally needs a live {@code RuntimeViewsheet}/{@code ViewsheetSandbox}/
 * {@code ViewsheetService} with a real (non-empty) sandbox, which no test in this codebase stands
 * up for real (confirmed by search: every existing caller of {@code ChangeChartRefService} in this
 * test tree, including {@code ChartBindingServiceTest}, mocks it outright). What is exercised here
 * for real, unmocked, is the exact sequence of calls {@code changeChartRef} itself makes once it
 * has a model and an assembly in hand -- {@code bindingFactory.updateAssembly(cmodel, clone)}
 * followed by the same seven post-processing calls, in the same order, reading their inputs from
 * the same places ({@code ncinfo}/{@code ocinfo} obtained the same way). The one piece left out is
 * the sandbox-requiring wrapper around that sequence, which touches nothing this fix wrote.
 *
 * <p>The model-side {@code secondaryY} here comes directly from
 * {@code ChartAggregateRefModel.setSecondaryY}, not from {@code FieldRefFactory.toChartRef} --
 * this test's job is to validate the pipeline this fix's write depends on, independently of
 * whether the write itself (a one-line, separately unit-tested change) is correct.
 */
@WizAgentTestSupport
class SecondaryYPostProcessingPipelineTest {
   @Test
   void secondaryYSurvivesUpdateAssemblyAndEveryNamedPostProcessingStep() throws Exception {
      VSChartBindingFactory bindingFactory = realVSChartBindingFactory();

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly assembly = new ChartVSAssembly(vs, "Chart1");
      VSChartInfo ocinfo = new DefaultVSChartInfo();
      ocinfo.setChartType(GraphTypes.CHART_LINE);
      assembly.setVSChartInfo(ocinfo);
      vs.addAssembly(assembly);

      ChartAggregateRefModel salesField = new ChartAggregateRefModel();
      salesField.setColumnValue("Sales");
      salesField.setName("Sales");
      salesField.setFormula("Sum");
      salesField.setSecondaryY(true);

      ChartBindingModel model = new ChartBindingModel();
      model.setYFields(List.<inetsoft.web.binding.model.graph.ChartRefModel>of(salesField));

      // The write this design traced: VSChartBindingFactory.updateAssembly ->
      // VSChartInfoModelBuilder.updateChartInfo -> ChartInfoModelBuilder.getChartRefs ->
      // ChartRefModelFactoryService.pasteChartRef -> ChartAggregateInfoFactory.pasteChartRef.
      ChartVSAssembly updated = bindingFactory.updateAssembly(model, assembly);
      VSChartInfo ncinfo = updated.getVSChartInfo();

      assertTrue(((VSChartAggregateRef) ncinfo.getYField(0)).isSecondaryY(),
                 "updateAssembly alone must have pasted secondaryY onto the runtime ref");

      // The seven post-processing calls ChangeChartRefService.changeChartRef makes, in its own
      // order, on its own inputs -- none of these are exercised by the updateAssembly call above.
      ncinfo.updateChartType(!ncinfo.isMultiStyles());
      GraphUtil.fixVisualFrames(ncinfo);
      new ChangeChartDataProcessor().sortRefs(updated.getVSChartInfo());
      ChangeChartProcessor process = new ChangeChartProcessor();
      process.fixMapFrame(ocinfo, ncinfo);
      process.fixNamedGroup(ocinfo, ncinfo);
      process.syncTopN(ncinfo);
      process.fixAggregateRefs(ncinfo);

      VSChartAggregateRef sales = (VSChartAggregateRef) updated.getVSChartInfo().getYField(0);
      assertTrue(sales.isSecondaryY(),
                 "none of the seven post-processing steps changeChartRef runs after " +
                 "updateAssembly may reset secondaryY back to false");
   }

   /**
    * Bug #78403: a CHANGE calculator written with the bare column of a date-grouped dimension was
    * nulled by {@code fixAggregateRefs} because only the full name ({@code Month(Order Date)})
    * matched. It must resolve to that dimension's runtime full name instead.
    */
   @Test
   void changeCalculatorBareDateColumnResolvesToTheDimensionFullName() {
      assertEquals("Month(Order Date)", changeCalcColumnAfterFix("Order Date"));
      assertEquals("Month(Order Date)", changeCalcColumnAfterFix("Month(Order Date)"));
      assertEquals("Month(Order Date)", changeCalcColumnAfterFix("month(order date)"));
      assertNull(changeCalcColumnAfterFix("Nope"));
   }

   /**
    * The post-write read-back: a bare column bound at several levels is nulled by the processor
    * (it designates no single dimension), and the service refuses it listing the full names.
    */
   @Test
   void readBackRefusesABareColumnBoundAtSeveralLevelsListingFullNames() {
      VSChartInfo info = twoLevelDateChart("Order Date");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireCalculatorsResolved(info, "y", List.of(changeField("Order Date"))));
      assertTrue(e.getMessage().contains("calculateInfo.columnName") &&
                 e.getMessage().contains("Year(Order Date)") &&
                 e.getMessage().contains("Month(Order Date)"), e.getMessage());
   }

   @Test
   void readBackAcceptsAFullNameAtSeveralLevelsAndSentinels() {
      VSChartInfo info = twoLevelDateChart("Month(Order Date)");
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorsResolved(
         info, "y", List.of(changeField("Month(Order Date)"))));
      assertDoesNotThrow(() -> ChartBindingService.requireCalculatorsResolved(
         info, "y", List.of(changeField(AbstractCalc.ROW_INNER))));
   }

   @Test
   void refusalSaysRolledBackOnlyWhenTheRestoreSucceeded() {
      assertTrue(ChartBindingService.refusalMessage("reason.", true).endsWith("The write was rolled back."));

      String failed = ChartBindingService.refusalMessage("reason.", false);
      assertTrue(failed.contains("may be applied"), failed);
      assertFalse(failed.contains("rolled back"), failed);
   }

   @Test
   void readBackMessageDoesNotClaimTheWriteWasUndone() {
      VSChartInfo info = twoLevelDateChart("Order Date");
      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireCalculatorsResolved(info, "y", List.of(changeField("Order Date"))));
      assertFalse(e.getMessage().contains("not applied"), e.getMessage());
   }

   private static FieldRef changeField(String columnName) {
      ChangeCalcInfo calc = new ChangeCalcInfo();
      calc.setColumnName(columnName);
      return new FieldRef("Sales", "measure", "Sum", null, null, null, null, null, calc);
   }

   private static VSChartInfo twoLevelDateChart(String columnName) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);

      for(int level : new int[] {XConstants.YEAR_DATE_GROUP, XConstants.MONTH_DATE_GROUP}) {
         AttributeRef attribute = new AttributeRef(null, "Order Date");
         attribute.setDataType(XSchema.DATE);
         VSChartDimensionRef date = new VSChartDimensionRef();
         date.setDataRef(new ColumnRef(attribute));
         date.setDateLevelValue(String.valueOf(level));
         info.addXField(date);
      }

      AttributeRef salesAttribute = new AttributeRef(null, "Sales");
      salesAttribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new ColumnRef(salesAttribute));
      sales.setFormulaValue("Sum");
      ChangeCalc calc = new ChangeCalc();
      calc.setColumnName(columnName);
      sales.setCalculator(calc);
      info.addYField(sales);
      new ChangeChartProcessor().fixAggregateRefs(info);
      return info;
   }

   private static String changeCalcColumnAfterFix(String columnName) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_LINE);

      AttributeRef attribute = new AttributeRef(null, "Order Date");
      attribute.setDataType(XSchema.DATE);
      VSChartDimensionRef date = new VSChartDimensionRef();
      date.setDataRef(new ColumnRef(attribute));
      date.setDateLevelValue(String.valueOf(XConstants.MONTH_DATE_GROUP));
      info.addXField(date);

      AttributeRef salesAttribute = new AttributeRef(null, "Sales");
      salesAttribute.setDataType(XSchema.DOUBLE);
      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new ColumnRef(salesAttribute));
      sales.setFormulaValue("Sum");
      ChangeCalc calc = new ChangeCalc();
      calc.setColumnName(columnName);
      sales.setCalculator(calc);
      info.addYField(sales);

      assertEquals("Month(Order Date)", date.getFullName());
      new ChangeChartProcessor().fixAggregateRefs(info);

      return ((ValueOfCalc) sales.getCalculator()).getColumnName();
   }

   /**
    * Builds the real (unmocked) bean graph {@code VSChartBindingFactory.updateAssembly} needs,
    * breaking the one production circular dependency ({@code ChartAestheticService} <->
    * {@code AestheticRefModelFactory} <-> {@code ChartRefModelFactoryService} <->
    * {@code ChartAggregateInfoFactory}, resolved in production by {@code @Lazy} on
    * {@code ChartAggregateInfoFactory.setChartAestheticService}) the same way Spring's container
    * does -- via a plain {@code AnnotationConfigApplicationContext} registering exactly these
    * classes, letting Spring's own {@code @Lazy} handling break the cycle rather than
    * reimplementing that resolution by hand.
    */
   private static VSChartBindingFactory realVSChartBindingFactory() {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.register(EmptyFactoryLists.class, ChartAestheticService.class,
                          AestheticRefModelFactory.class, ChartRefModelFactoryService.class,
                          ChartAggregateInfoFactory.VSChartAggregateInfoFactory.class,
                          VSChartBindingFactory.class);
         context.refresh();
         return context.getBean(VSChartBindingFactory.class);
      }
   }

   @Configuration
   static class EmptyFactoryLists {
      // No DataRefModel/VisualFrameModel factory implementations are registered: this test's
      // model carries no dataRefModel/visual-frame properties (matching what
      // FieldRefFactory.toChartRef's measure branch actually sets today), so the paste path never
      // looks either service's factory map up -- confirmed by reading
      // ChartAggregateInfoFactory.pasteChartRef and ChartAestheticService.updateVisualFrames,
      // both of which short-circuit on a null model property before touching either factory.
      @Bean
      DataRefModelFactoryService dataRefModelFactoryService() {
         return new DataRefModelFactoryService(List.of());
      }

      @Bean
      VisualFrameModelFactoryService visualFrameModelFactoryService() {
         return new VisualFrameModelFactoryService(List.of());
      }
   }
}
