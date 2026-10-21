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

import inetsoft.graph.aesthetic.*;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.graph.element.PointElement;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.GanttGraphGenerator;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSDataRef;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.AllChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.awt.Dimension;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #78918: {@code set_aesthetic_field} on a Gantt chart was refused as "multi-style" although
 * {@code get_chart_type} reported {@code multiStyles: false}, and the offered remedy
 * ({@code set_chart_type multi:false}) could not help: {@code GanttVSChartInfo.isMultiAesthetic()}
 * is hard-coded true. A Gantt chart renders every aesthetic field from its start and milestone
 * measures, never from the chart-level slot, so the write now goes onto those measures.
 *
 * <p>Each case runs the real agent service against a real {@code GanttVSChartInfo} and the real
 * model write path ({@code VSChartBindingFactory.createModel}/{@code updateAssembly}, the core of
 * {@code ChangeChartRefService.changeChartRef}), reads the chart back through the same service,
 * and then renders the resulting info with the real {@code GanttGraphGenerator}.
 */
@WizAgentTestSupport
class ChartAestheticGanttFieldTest {
   private static final FieldRef PHASE = new FieldRef("PHASE", "dimension", null, null, null);

   @Test
   void colorIsBoundOnStartAndMilestoneAndRendersOnBarsAndMarkers() throws Exception {
      Pipeline gantt = pipeline(true);

      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      GanttVSChartInfo info = gantt.info();
      assertEquals("PHASE", fieldName(info.getStartField(), "color"));
      assertEquals("PHASE", fieldName(info.getMilestoneField(), "color"));
      assertNull(fieldName(info.getEndField(), "color"),
                 "no Gantt element is drawn from the end field's aesthetics");
      assertNull(info.getColorField(), "the chart-level slot is ignored on a Gantt chart");

      Map<String, Object> color = gantt.read("color");
      assertEquals("PHASE", color.get("field"));
      assertEquals(true, color.get("acceptsField"));
      FieldRef ref = assertInstanceOf(FieldRef.class, color.get("fieldRef"));
      assertEquals("PHASE", ref.column());
      assertNull(color.get("fieldsByMeasure"));

      List<GraphElement> elements = render(info, "color");
      assertField(CategoricalColorFrame.class, elements.get(0).getColorFrame(), "the bars");
      assertField(CategoricalColorFrame.class, elements.get(1).getColorFrame(), "the markers");
   }

   @Test
   void sizeAndTextAreBoundOnStartAndMilestoneAndRender() throws Exception {
      for(String channel : List.of("size", "text")) {
         Pipeline gantt = pipeline(true);

         gantt.service.setField("tok", principal(), "Chart1", channel, PHASE, null, "");

         GanttVSChartInfo info = gantt.info();
         assertEquals("PHASE", fieldName(info.getStartField(), channel), channel);
         assertEquals("PHASE", fieldName(info.getMilestoneField(), channel), channel);
         assertEquals("PHASE", gantt.read(channel).get("field"), channel);
         assertEquals(true, gantt.read(channel).get("acceptsField"), channel);

         List<GraphElement> elements = render(info, channel);
         Function<GraphElement, VisualFrame> frame = "size".equals(channel)
            ? GraphElement::getSizeFrame : GraphElement::getTextFrame;
         assertEquals(GraphUtil.getName(dimension()), frame.apply(elements.get(0)).getField(),
                      channel + " on the bars");
         assertEquals(GraphUtil.getName(dimension()), frame.apply(elements.get(1)).getField(),
                      channel + " on the markers");
      }
   }

   /**
    * A Gantt bar draws its shape field as a texture; the milestone marker never reads a shape
    * field, so shape goes onto the start field only.
    */
   @Test
   void shapeIsBoundOnTheStartFieldOnlyAndRendersAsTheBarsTexture() throws Exception {
      Pipeline gantt = pipeline(true);

      gantt.service.setField("tok", principal(), "Chart1", "shape", PHASE, null, "");

      GanttVSChartInfo info = gantt.info();
      assertEquals("PHASE", fieldName(info.getStartField(), "shape"));
      assertNull(fieldName(info.getMilestoneField(), "shape"));
      Map<String, Object> shape = gantt.read("shape");
      assertEquals("PHASE", shape.get("field"));
      assertEquals(true, shape.get("acceptsField"));
      assertNull(shape.get("fieldsByMeasure"));

      List<GraphElement> elements = render(info, "shape");
      IntervalElement bars = assertInstanceOf(IntervalElement.class, elements.get(0));
      assertField(CategoricalTextureFrame.class, bars.getTextureFrame(), "the bars");
   }

   @Test
   void aGanttWithoutAMilestoneBindsTheStartFieldOnly() throws Exception {
      Pipeline gantt = pipeline(false);

      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      assertEquals("PHASE", fieldName(gantt.info().getStartField(), "color"));
      assertNull(gantt.info().getMilestoneField());
   }

   @Test
   void clearRemovesTheFieldFromTheMeasuresAndAnyChartLevelLeftover() throws Exception {
      Pipeline gantt = pipeline(true);
      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");
      // A chart-level leftover a Gantt chart ignores, from before this fix or another path.
      VSAestheticRef leftover = new VSAestheticRef();
      leftover.setDataRef(dimension());
      gantt.info().setColorField(leftover);

      gantt.service.clearField("tok", principal(), "Chart1", "color", "");

      GanttVSChartInfo info = gantt.info();
      assertNull(fieldName(info.getStartField(), "color"));
      assertNull(fieldName(info.getMilestoneField(), "color"));
      assertNull(info.getColorField());
      assertNull(gantt.read("color").get("field"));
   }

   /** Rebinding on a Gantt chart also clears a chart-level leftover, which still joins the query. */
   @Test
   void setReplacesAChartLevelLeftover() throws Exception {
      Pipeline gantt = pipeline(true);
      VSAestheticRef leftover = new VSAestheticRef();
      leftover.setDataRef(dimension());
      gantt.info().setColorField(leftover);

      gantt.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "");

      assertNull(gantt.info().getColorField());
      assertEquals("PHASE", fieldName(gantt.info().getStartField(), "color"));
   }

   /** With no start field there is no measure to bind onto; the refusal says so. */
   @Test
   void aGanttWithNoStartFieldIsRefusedWithTheTrueReason() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_GANTT);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartAestheticMutator.setGanttField(model, "color", PHASE, null, null, null));
      assertTrue(e.getMessage().contains("no start field"), e.getMessage());
      assertFalse(e.getMessage().contains("multi:false"), e.getMessage());
      @SuppressWarnings("unchecked")
      Map<String, Object> color = (Map<String, Object>) ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS, true, true, true, true).get("color");
      assertEquals(false, color.get("acceptsField"));
   }

   /**
    * The read agrees with the write on every other multi-aesthetic chart too: there the field
    * write is still refused, so acceptsField reports false instead of true.
    */
   @Test
   void aNonGanttMultiAestheticChartReportsNoFieldWrite() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_BAR);
      model.setMultiStyles(true);

      Map<String, Object> view = ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS, true, true, true, true);

      for(String channel : AestheticChannels.FIELD_CHANNELS) {
         assertEquals(false, ((Map<?, ?>) view.get(channel)).get("acceptsField"), channel);
      }

      Map<String, Object> single = ChartAestheticMutator.describe(
         model, false, AestheticChannels.FRAME_CHANNELS, true, true, true, false);
      assertEquals(true, ((Map<?, ?>) single.get("color")).get("acceptsField"));
   }

   /** A field-less non-static frame on a Gantt measure is refused without the multi:false remedy. */
   @Test
   void aGanttFrameRefusalDoesNotOfferTurningMultiStyleOff() throws Exception {
      Pipeline gantt = pipeline(true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> gantt.service.setFrame("tok", principal(), "Chart1", "color",
                                      Map.of("type", "palette", "palette", "Blues"), ""));
      assertFalse(e.getMessage().contains("multi:false"), e.getMessage());
      assertTrue(e.getMessage().contains("Gantt"), e.getMessage());
   }

   private static String fieldName(ChartRef ref, String channel) {
      ChartBindable bindable = (ChartBindable) ref;
      AestheticRef field = switch(channel) {
         case "color" -> bindable.getColorField();
         case "shape" -> bindable.getShapeField();
         case "size" -> bindable.getSizeField();
         default -> bindable.getTextField();
      };

      return field == null ? null : field.getFullName();
   }

   private static void assertField(Class<?> type, VisualFrame frame, String where) {
      assertInstanceOf(type, frame, where);
      assertEquals(GraphUtil.getName(dimension()), frame.getField(), where);
   }

   /**
    * Renders a written info with the real {@code GanttGraphGenerator}. The runtime refs a live
    * query would build are filled in by hand: the RT start/end/milestone fields, and the column
    * behind the aesthetic dimension (the change-ref service resolves it from the source table).
    */
   private static List<GraphElement> render(GanttVSChartInfo info, String channel) {
      info.setRTStartField((VSChartRef) info.getStartField());
      info.setRTEndField((VSChartRef) info.getEndField());
      info.setRTMilestoneField((VSChartRef) info.getMilestoneField());

      for(ChartRef ref : new ChartRef[] { info.getStartField(), info.getMilestoneField() }) {
         ChartBindable bindable = (ChartBindable) ref;
         AestheticRef field = switch(channel) {
            case "color" -> bindable.getColorField();
            case "shape" -> bindable.getShapeField();
            case "size" -> bindable.getSizeField();
            default -> bindable.getTextField();
         };

         if(field instanceof VSAestheticRef aesthetic &&
            aesthetic.getDataRef() instanceof VSChartDimensionRef dim)
         {
            dim.setDataRef(new AttributeRef("PHASE"));
            aesthetic.setRTDataRef((VSDataRef) dim);
         }
      }

      String start = GraphUtil.getName(info.getStartField());
      String end = GraphUtil.getName(info.getEndField());
      String milestone = GraphUtil.getName(info.getMilestoneField());
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { start, end, milestone, GraphUtil.getName(dimension()) },
         { 100.0, 200.0, 150.0, "Design" },
         { 200.0, 300.0, 250.0, "Build" },
      });
      Generator gen = new Generator(info, data);
      gen.build(new String[] { start, end, milestone });
      return List.of(gen.element(0), gen.element(1));
   }

   /** Exposes the element step and the graph, which {@code GraphGenerator} keeps protected. */
   private static final class Generator extends GanttGraphGenerator {
      Generator(GanttVSChartInfo info, DefaultDataSet data) {
         super(info, new ChartDescriptor(), null, data, null, null, 0, new Dimension(400, 300));
      }

      void build(String[] names) {
         createElement(GraphTypes.CHART_GANTT, names, null);
      }

      GraphElement element(int i) {
         return graph.getElement(i);
      }
   }

   private static VSChartDimensionRef dimension() {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue("PHASE");
      dim.setDataRef(new AttributeRef("PHASE"));
      return dim;
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setColumnValue(column);
      aggregate.setDataRef(new AttributeRef(column));
      aggregate.setFormula(AggregateFormula.NONE);
      return aggregate;
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private record Pipeline(ChartAestheticAgentService service, ChartVSAssembly chart) {
      GanttVSChartInfo info() {
         return (GanttVSChartInfo) chart.getVSChartInfo();
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> read(String channel) throws Exception {
         return (Map<String, Object>) service.read("tok", principal(), "Chart1").get(channel);
      }
   }

   /**
    * A Gantt chart in the state the bug report describes: multi-style off, start/end bound,
    * optionally a milestone. The binding service reads and writes it through the real
    * {@code VSChartBindingFactory}; the change-ref service's write is its
    * {@code updateAssembly}.
    */
   private static Pipeline pipeline(boolean milestone) throws Exception {
      VSChartBindingFactory factory = realVSChartBindingFactory();

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      GanttVSChartInfo info = new GanttVSChartInfo();
      info.setChartType(GraphTypes.CHART_GANTT);
      info.setMultiStyles(false);
      info.setStartField(measure("START"));
      info.setEndField(measure("END"));

      if(milestone) {
         info.setMilestoneField(measure("MILESTONE"));
      }

      chart.setVSChartInfo(info);
      vs.addAssembly(chart);
      assertTrue(info.isMultiAesthetic() && !info.isMultiStyles(),
                 "the reported state: multiStyles false, yet multi-aesthetic");

      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      ViewsheetSessionService sessions = mock(ViewsheetSessionService.class);
      when(sessions.resolve(anyString(), any(Principal.class))).thenReturn(rvs);
      doAnswer(invocation -> {
         ViewsheetSessionService.Mutation mutation = invocation.getArgument(2);
         mutation.run(rvs, "rt1", null);
         return List.of();
      }).when(sessions).mutate(anyString(), any(Principal.class), any());

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenAnswer(
         invocation -> factory.createModel(invocation.getArgument(0)));
      ChangeChartAestheticService aesthetics = mock(ChangeChartAestheticService.class);
      doAnswer(invocation -> {
         ChangeChartRefEvent event = invocation.getArgument(1);
         factory.updateAssembly(event.getModel(), chart);
         return null;
      }).when(aesthetics).changeChartAesthetic(any(), any(), any(), any(), any());

      return new Pipeline(new ChartAestheticAgentService(
         sessions, binding, aesthetics, mock(DataRefModelFactoryService.class)), chart);
   }

   /** The real bean graph, as {@code SecondaryYPostProcessingPipelineTest} builds it. */
   private static VSChartBindingFactory realVSChartBindingFactory() {
      try(AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
         context.register(Beans.class, ChartAestheticService.class,
                          AestheticRefModelFactory.class, ChartRefModelFactoryService.class,
                          ChartAggregateInfoFactory.VSChartAggregateInfoFactory.class,
                          ChartDimensionInfoFactory.VSChartDimensionInfoFactory.class,
                          AllChartAggregateInfoFactory.class, VSChartBindingFactory.class);
         context.scan("inetsoft.web.binding.service.graph.aesthetic");
         context.refresh();
         return context.getBean(VSChartBindingFactory.class);
      }
   }

   @Configuration
   static class Beans {
      @Bean
      DataRefModelFactoryService dataRefModelFactoryService() {
         return new DataRefModelFactoryService(List.of(
            new AttributeRefModel.AttributeRefModelFactory(),
            new ColumnRefModel.ColumnRefModelFactory()));
      }
   }
}
