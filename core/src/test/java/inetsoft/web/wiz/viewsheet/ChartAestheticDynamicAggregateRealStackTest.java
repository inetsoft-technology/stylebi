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

import inetsoft.analytic.composition.ViewsheetService;
import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.execution.ViewsheetSandbox;
import inetsoft.sree.AnalyticRepository;
import inetsoft.test.*;
import inetsoft.uql.asset.AssetRepository;
import inetsoft.uql.asset.ColumnRef;
import inetsoft.uql.asset.SourceInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.handler.VSAssemblyInfoHandler;
import inetsoft.web.binding.handler.VSChartHandler;
import inetsoft.web.binding.model.BindingModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.handler.CalculatorHandler;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.CalculateRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.model.ExpressionRefModel;
import inetsoft.web.binding.model.DateRangeRefModel;
import inetsoft.web.binding.model.NumericRangeRefModel;
import inetsoft.web.binding.service.DataRefModelFactory;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.DataRefModelWrapperFactory;
import inetsoft.web.binding.service.VSBindingFactory;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.AllChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.portal.controller.database.QueryManagerService;
import inetsoft.web.viewsheet.event.OpenViewsheetEvent;
import inetsoft.web.viewsheet.model.RuntimeViewsheetRef;
import inetsoft.web.viewsheet.service.CoreLifecycleService;
import inetsoft.web.wiz.binding.ChartAestheticAgentService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.dispatch.CapturingCommandDispatcher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.security.Principal;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bug #79026: {@code set_aesthetic_field} rebinding the colour channel from a dimension to a
 * measure whose aggregate is a dynamic {@code $(AggBox)} reference left the old categorical frame
 * on the measure, where a plain {@code Sum} gets the graduated default. Bug #78884 stamps the
 * source column's type on the measure so {@code GraphUtil.isCategorical} no longer falls back to
 * the "string" default; this test pins the whole path, not just that predicate.
 *
 * <p>Runs the real {@link ChartAestheticAgentService}, {@link ChangeChartAestheticService}
 * ({@code ChangeChartDataProcessor} / {@code GraphUtil.fixVisualFrame}), {@link VSBindingService}
 * and chart binding factory over EmbeddedVS1, whose {@code TableView1} worksheet table has a
 * string column ({@code type}) and a numeric one ({@code wind}); only the session and lifecycle
 * plumbing is a seam.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, IntegrationTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome(importResources = "/inetsoft/report/composition/EmbeddedVS1.zip")
@Tag("core")
@Tag("integration")
class ChartAestheticDynamicAggregateRealStackTest {
   private static final String CATEGORY_COLUMN = "type";
   private static final String NUMERIC_COLUMN = "wind";
   private static final FieldRef CATEGORY =
      new FieldRef(CATEGORY_COLUMN, "dimension", null, null, null);
   private static final FieldRef DYNAMIC =
      new FieldRef(NUMERIC_COLUMN, "measure", "$(AggBox)", null, null);

   /** The harness is only meaningful if the worksheet column really is numeric. */
   @Test
   void theWorksheetColumnsAreTheTypesTheTestAssumes() throws Exception {
      Stack s = stack(true);
      BindingModel.SourceTable table = s.model().getTables().get(0);

      assertEquals("string", typeOf(table, "type"), table.getColumns().toString());
      assertTrue(List.of("double", "float", "integer", "long", "short")
                    .contains(typeOf(table, "wind")), table.getColumns().toString());
   }

   /** The reporter's steps: colour = dimension, then the measure with a dynamic aggregate. */
   @Test
   void dimensionThenDynamicAggregateLeavesNoCategoricalFrame() throws Exception {
      Stack s = stack(true);
      s.setColor(CATEGORY);
      assertInstanceOf(CategoricalColorFrame.class, s.colorFrame(), "precondition");

      s.setColor(DYNAMIC);

      assertNotCategorical(s.colorFrame());
   }

   /** Control: a plain Sum swaps to the graduated default. */
   @Test
   void dimensionThenSumGivesTheGraduatedFrame() throws Exception {
      Stack s = stack(true);
      s.setColor(CATEGORY);

      s.setColor(new FieldRef(NUMERIC_COLUMN, "measure", "Sum", null, null));

      assertInstanceOf(BluesColorFrame.class, s.colorFrame());
   }

   @Test
   void dimensionDynamicDimensionDynamicAgain() throws Exception {
      Stack s = stack(true);
      s.setColor(CATEGORY);
      s.setColor(DYNAMIC);
      assertNotCategorical(s.colorFrame());

      s.setColor(CATEGORY);
      assertInstanceOf(CategoricalColorFrame.class, s.colorFrame(), "back to the dimension");

      s.setColor(DYNAMIC);
      assertNotCategorical(s.colorFrame());
   }

   /** The very first write on the channel (no frame carried), with the dynamic aggregate. */
   @Test
   void dynamicAggregateOnAnUnboundChannelIsNotCategorical() throws Exception {
      Stack s = stack(true);

      s.setColor(DYNAMIC);

      assertNotCategorical(s.colorFrame());
   }

   /** The dynamic reference names no assembly (as in the report): same outcome. */
   @Test
   void dynamicAggregateWithoutTheComboBoxIsNotCategorical() throws Exception {
      Stack s = stack(false);
      s.setColor(CATEGORY);

      s.setColor(DYNAMIC);

      assertNotCategorical(s.colorFrame());
   }

   @Test
   void dynamicAggregateWithASecondaryColumnIsNotCategorical() throws Exception {
      Stack s = stack(true);
      s.setColor(CATEGORY);

      s.setColor(new FieldRef(NUMERIC_COLUMN, "measure", "$(AggBox)", null, null, null, null,
                              null, null, null, null, null, null, NUMERIC_COLUMN, null));

      assertNotCategorical(s.colorFrame());
   }

   /** A dynamic aggregate over a string column is categorical by the engine's design. */
   @Test
   void dynamicAggregateOnAStringColumnStaysCategorical() throws Exception {
      Stack s = stack(true);
      s.setColor(CATEGORY);

      s.setColor(new FieldRef(CATEGORY_COLUMN, "measure", "$(AggBox)", null, null));

      assertInstanceOf(CategoricalColorFrame.class, s.colorFrame());
   }

   /** Same path on the size channel: a numeric dynamic measure gets a graduated size frame. */
   @Test
   void dynamicAggregateOnSizeIsNotCategorical() throws Exception {
      Stack s = stack(true);

      s.set("size", DYNAMIC);

      VisualFrame frame = s.chart.getVSChartInfo().getSizeField().getVisualFrame();
      assertFalse(frame instanceof inetsoft.graph.aesthetic.CategoricalSizeFrame,
                  String.valueOf(frame));
   }

   /**
    * A logical-model column is reported as {@code Entity:Attr}; the type must still resolve, so
    * the model the real service builds names the numeric column that way.
    */
   @Test
   void dynamicAggregateOnAnEntityQualifiedNumericColumnIsNotCategorical() throws Exception {
      Stack s = stack(true, model -> {
         for(BindingModel.SourceTable table : model.getTables()) {
            for(BindingModel.SourceTableColumn column : table.getColumns()) {
               column.setName("Orders:" + column.getName());
            }
         }

         return model;
      });
      s.setColor(new FieldRef("Orders:type", "dimension", null, null, null));

      s.setColor(new FieldRef("Orders:wind", "measure", "$(AggBox)", null, null));

      assertNotCategorical(s.colorFrame());
   }

   private static void assertNotCategorical(VisualFrame frame) {
      assertNotNull(frame);
      assertFalse(frame instanceof CategoricalColorFrame,
                  "a numeric dynamic aggregate kept the categorical frame: " + frame);
   }

   private static String typeOf(BindingModel.SourceTable table, String column) {
      return table.getColumns().stream()
         .filter(c -> column.equalsIgnoreCase(c.getName()))
         .map(BindingModel.SourceTableColumn::getDataType)
         .findFirst().orElse(null);
   }

   private Stack stack(boolean withComboBox) throws Exception {
      return stack(withComboBox, Function.identity());
   }

   /** @param modelTweak applied to every model the binding service builds. */
   private Stack stack(boolean withComboBox, Function<ChartBindingModel, ChartBindingModel> modelTweak)
      throws Exception
   {
      RuntimeViewsheet rvs = vsResource.getRuntimeViewsheet();
      user = rvs.getUser();
      ChartVSAssembly chart = addChart(rvs, withComboBox);

      refService = wiredRefService();
      VSChartBindingFactory chartFactory = chartFactory();
      VSBindingService binding = new VSBindingService(
         null, null, null, viewsheetService, List.<VSBindingFactory<?, ?>>of(chartFactory),
         refService, null, null, null, null, null, null, null, mock(QueryManagerService.class))
      {
         @Override
         public BindingModel createModel(VSAssembly assembly) {
            BindingModel model = super.createModel(assembly);
            return model instanceof ChartBindingModel chartModel ? modelTweak.apply(chartModel)
               : model;
         }
      };

      VSChartHandler chartHandler = new VSChartHandler(
         analyticRepository.unwrap(AssetRepository.class), viewsheetService,
         mock(CalculatorHandler.class));
      ChangeChartAestheticService aesthetics = new ChangeChartAestheticService(
         binding, mock(RuntimeViewsheetRef.class), coreLifecycleService,
         mock(VSAssemblyInfoHandler.class), chartHandler, viewsheetService);

      ChartAestheticAgentService service = new ChartAestheticAgentService(
         seam(rvs), binding, aesthetics, refService);
      return new Stack(service, chart, binding);
   }

   private static ViewsheetSessionService seam(RuntimeViewsheet rvs) throws Exception {
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any())).thenReturn(rvs);
      when(sessions.mutate(anyString(), any(), any())).thenAnswer(inv -> {
         ViewsheetSessionService.Mutation mutation = inv.getArgument(2);
         return CapturingCommandDispatcher.withCapturingDispatcher(inv.getArgument(1), d -> {
            mutation.run(rvs, rvs.getID(), d);
            return d.getWarnings();
         });
      });
      return sessions;
   }

   /**
    * The data-ref model factories the way Spring wires them in production: the wrapper factories
    * delegate to the service, which the shared test bean (built with {@code new}) never injects.
    */
   private static DataRefModelFactoryService wiredRefService() {
      List<DataRefModelFactory<?, ?>> factories = List.of(
         new AttributeRefModel.AttributeRefModelFactory(),
         new ColumnRefModel.ColumnRefModelFactory(),
         new CalculateRefModel.CalculateRefModelFactory(),
         new ExpressionRefModel.ExpressionRefModelFactory(),
         new DateRangeRefModel.DateRangeRefModelFactory(),
         new NumericRangeRefModel.NumericRangeRefModelFactory());
      DataRefModelFactoryService service = new DataRefModelFactoryService(factories);

      for(DataRefModelFactory<?, ?> factory : factories) {
         if(factory instanceof DataRefModelWrapperFactory<?, ?> wrapper) {
            wrapper.setDataRefModelFactoryService(service);
         }
      }

      return service;
   }

   /** The production chart factory, over the production model factories. */
   private VSChartBindingFactory chartFactory() {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.registerBean(DataRefModelFactoryService.class, () -> refService);
         context.register(ChartAestheticService.class, AestheticRefModelFactory.class,
                          ChartRefModelFactoryService.class,
                          ChartAggregateInfoFactory.VSChartAggregateInfoFactory.class,
                          ChartDimensionInfoFactory.VSChartDimensionInfoFactory.class,
                          AllChartAggregateInfoFactory.class, VSChartBindingFactory.class);
         context.scan("inetsoft.web.binding.service.graph.aesthetic");
         context.refresh();
         return context.getBean(VSChartBindingFactory.class);
      }
   }

   /** x = type, y = Sum(wind), over TableView1's worksheet table; optionally a ComboBox AggBox. */
   private static ChartVSAssembly addChart(RuntimeViewsheet rvs, boolean withComboBox) {
      ViewsheetSandbox box = rvs.getViewsheetSandbox().orElseThrow();
      Viewsheet vs = box.getViewsheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly(TABLE);
      ChartVSAssembly chart = new ChartVSAssembly(vs, CHART);
      chart.setSourceInfo((SourceInfo) table.getSourceInfo().clone());
      VSChartInfo info = chart.getVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      ColumnRef typeColumn = (ColumnRef) table.getColumnSelection().getAttribute("type");
      VSChartDimensionRef dim = new VSChartDimensionRef(typeColumn);
      dim.setGroupColumnValue("type");
      info.addXField(dim);
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setColumnValue("wind");
      agg.setDataRef(new AttributeRef(null, "wind"));
      agg.setFormulaValue("Sum");
      info.addYField(agg);
      vs.addAssembly(chart);

      if(withComboBox) {
         vs.addAssembly(new ComboBoxVSAssembly(vs, "AggBox"));
      }

      box.resetDataMap(CHART);
      return chart;
   }

   private final class Stack {
      Stack(ChartAestheticAgentService service, ChartVSAssembly chart, VSBindingService binding) {
         this.service = service;
         this.chart = chart;
         this.binding = binding;
      }

      void setColor(FieldRef field) throws Exception {
         set("color", field);
      }

      void set(String channel, FieldRef field) throws Exception {
         service.setField("tok", (Principal) user, CHART, channel, field, null, "");
      }

      VisualFrame colorFrame() {
         AestheticRef color = chart.getVSChartInfo().getColorField();
         return color == null ? null : color.getVisualFrame();
      }

      ChartBindingModel model() {
         return (ChartBindingModel) binding.createModel(chart);
      }

      final ChartAestheticAgentService service;
      final ChartVSAssembly chart;
      final VSBindingService binding;
   }

   private static OpenViewsheetEvent openViewsheetEvent() {
      OpenViewsheetEvent event = new OpenViewsheetEvent();
      event.setEntryId("1^128^__NULL__^EmbeddedVS1^host-org");
      event.setViewer(true);
      return event;
   }

   @RegisterExtension
   RuntimeViewsheetExtension vsResource = new RuntimeViewsheetExtension(openViewsheetEvent());

   @Autowired ViewsheetService viewsheetService;
   @Autowired CoreLifecycleService coreLifecycleService;
   @Autowired AnalyticRepository analyticRepository;

   private Principal user;
   private DataRefModelFactoryService refService;
   private static final String TABLE = "TableView1";
   private static final String CHART = "Chart1";
}
