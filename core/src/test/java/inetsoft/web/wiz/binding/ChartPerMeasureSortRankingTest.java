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

import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.uql.XConstants;
import inetsoft.uql.XCondition;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.binding.controller.ChangeChartAestheticService;
import inetsoft.web.binding.drm.AttributeRefModel;
import inetsoft.web.binding.drm.ColumnRefModel;
import inetsoft.web.binding.event.ChangeChartRefEvent;
import inetsoft.web.binding.model.BDimensionRefModel;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.AestheticInfo;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.service.DataRefModelFactoryService;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.binding.service.VSChartBindingFactory;
import inetsoft.web.binding.service.graph.AestheticRefModelFactory;
import inetsoft.web.binding.service.graph.AllChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartAestheticService;
import inetsoft.web.binding.service.graph.ChartAggregateInfoFactory;
import inetsoft.web.binding.service.graph.ChartDimensionInfoFactory;
import inetsoft.web.binding.service.graph.ChartRefModelFactoryService;
import inetsoft.web.binding.controller.ChangeChartRefService;
import inetsoft.web.binding.controller.ChangeChartTypeService;
import inetsoft.web.binding.controller.ChangeSeparateStatusService;
import inetsoft.web.binding.controller.SwapXYBindingService;
import inetsoft.web.wiz.binding.model.FieldRef;
import inetsoft.web.wiz.pairing.WizAgentTestSupport;
import inetsoft.web.wiz.viewsheet.ViewsheetSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Bug #81983 (BUG-46): the colour, shape, size and text dimension of a measure of a multi-style
 * chart could not be sorted or ranked -- the Composer's Edit Dimension popup, with a measure
 * selected in the Aesthetic pane, can. {@code forMeasure} on the sort and ranking requests now
 * addresses that measure's own dimension; the order governs that measure's colour assignment and
 * stack order.
 *
 * <p>Each case runs the real {@code ChartBindingService} against a real {@code DefaultVSChartInfo}
 * and the real model write path ({@code VSChartBindingFactory.createModel}/{@code
 * updateAssembly}), and reads the order back from the stored chart info.
 */
@WizAgentTestSupport
class ChartPerMeasureSortRankingTest {
   private static final FieldRef TEAM = new FieldRef("TEAM", "dimension", null, null, null);
   private static final DimensionSortRanking.Sort DESC =
      new DimensionSortRanking.Sort("desc", null, null);

   // -- writes ------------------------------------------------------------------------------

   @Test
   void sortsOnlyThatMeasuresColorDimensionAndSurvivesTheStoredRoundTrip() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");
      p.bind("color", "M2");

      p.sort("color", "TEAM", DESC, "Sum(M1)");

      assertEquals(XConstants.SORT_DESC, p.storedOrder(0, "color"));
      assertEquals(XConstants.SORT_ASC, p.storedOrder(1, "color"),
                   "the other measure's field is untouched");
      assertNull(p.info().getColorField(), "the chart-level slot stays empty");
   }

   @Test
   void everyChannelThatAMeasureCarriesCanBeSorted() throws Exception {
      // color and text are rendered per measure on a bar chart; shape and size on a point chart.
      Map<String, Integer> types = new java.util.LinkedHashMap<>();
      types.put("color", GraphTypes.CHART_BAR);
      types.put("text", GraphTypes.CHART_BAR);
      types.put("shape", GraphTypes.CHART_POINT);
      types.put("size", GraphTypes.CHART_POINT);

      for(Map.Entry<String, Integer> c : types.entrySet()) {
         String channel = c.getKey();
         Pipeline p = pipeline(c.getValue(), true);
         p.bind(channel, "M2");

         p.sort(channel, "TEAM", DESC, "M2");

         assertEquals(XConstants.SORT_DESC, p.storedOrder(1, channel), channel);
         assertNull(p.storedField(0, channel), channel + ": measure 0 has no field");
      }
   }

   @Test
   void ascendingNoneManualAndByValueAreStoredAsTheUiStoresThem() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");

      p.sort("color", "TEAM", DESC, "Sum(M1)");
      p.sort("color", "TEAM", new DimensionSortRanking.Sort("asc", null, null), "Sum(M1)");
      assertEquals(XConstants.SORT_ASC, p.storedOrder(0, "color"));

      p.sort("color", "TEAM", new DimensionSortRanking.Sort("none", null, null), "Sum(M1)");
      assertEquals(XConstants.SORT_NONE, p.storedOrder(0, "color"));

      p.sort("color", "TEAM",
             new DimensionSortRanking.Sort("manual", null, List.of("T2", "T1")), "Sum(M1)");
      assertEquals(XConstants.SORT_SPECIFIC, p.storedOrder(0, "color"));
      assertEquals(List.of("T2", "T1"), p.storedDimension(0, "color").getManualOrder());

      p.sort("color", "TEAM", new DimensionSortRanking.Sort("value_desc", "Sum(M2)", null),
             "Sum(M1)");
      assertEquals(XConstants.SORT_VALUE_DESC, p.storedOrder(0, "color"));
      assertEquals("Sum(M2)", p.storedDimension(0, "color").getSortByCol());
      assertNull(p.storedDimension(0, "color").getManualOrder(),
                 "the previous manual order is cleared, as the Composer does");
   }

   @Test
   void ranksOnlyThatMeasuresDimension() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");
      p.bind("color", "M2");

      p.rank("color", "TEAM", new DimensionSortRanking.Ranking("top", 3, "Sum(M1)", true),
             "M1");

      BDimensionRefModel ranked = p.storedDimension(0, "color");
      assertEquals(String.valueOf(XCondition.TOP_N), ranked.getRankingOption());
      assertEquals("3", ranked.getRankingN());
      assertEquals("Sum(M1)", ranked.getRankingCol());
      assertTrue(ranked.isGroupOthers());
      assertEquals(String.valueOf(XCondition.NONE),
                   String.valueOf(p.storedDimension(1, "color").getRankingOption()));
   }

   @Test
   void aMeasureNameIsAcceptedInEitherSpellingAndAColumnIsOptional() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");

      p.sort("color", null, DESC, "M1");
      assertEquals(XConstants.SORT_DESC, p.storedOrder(0, "color"));

      p.sort("color", "team", new DimensionSortRanking.Sort("asc", null, null), "Sum(M1)");
      assertEquals(XConstants.SORT_ASC, p.storedOrder(0, "color"));
   }

   @Test
   void aLaterRebindOfTheSameFieldKeepsTheOrder() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");
      p.sort("color", "TEAM", DESC, "Sum(M1)");

      p.bind("color", "M1");

      assertEquals(XConstants.SORT_DESC, p.storedOrder(0, "color"));
   }

   // -- read-back ---------------------------------------------------------------------------

   @Test
   void getBindingSortsAndTheAestheticViewReportThePerMeasureOrder() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");
      p.bind("color", "M2");
      p.sort("color", "TEAM", DESC, "Sum(M1)");
      p.rank("color", "TEAM", new DimensionSortRanking.Ranking("top", 2, "Sum(M2)", null), "M2");

      Map<String, Object> sorts =
         ChartBindingMutator.describeAestheticSorts(p.model(), true);
      assertEquals("desc", ((Map<?, ?>) sorts.get("TEAM [color:Sum(M1)]")).get("direction"));
      assertEquals("top", ((Map<?, ?>) sorts.get("TEAM [color:Sum(M2)]")).get("ranking"));
      assertEquals("2", ((Map<?, ?>) sorts.get("TEAM [color:Sum(M2)]")).get("rankingN"));
      assertTrue(ChartBindingMutator.describeAestheticSorts(p.model(), false).isEmpty(),
                 "a chart with chart-level channels reports no per-measure entries");

      Map<?, ?> view = (Map<?, ?>) p.read("color").get("sortsByMeasure");
      assertEquals("desc", ((Map<?, ?>) view.get("Sum(M1)")).get("direction"));
      assertEquals("top", ((Map<?, ?>) view.get("Sum(M2)")).get("ranking"));
   }

   // -- refusals ----------------------------------------------------------------------------

   @Test
   void withoutForMeasureAMultiStyleChartRefusesAndNamesTheArgumentAndTheMeasures()
      throws Exception
   {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M2");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> p.sort("color", "TEAM", DESC, null));
      assertTrue(e.getMessage().contains("forMeasure"), e.getMessage());
      assertTrue(e.getMessage().contains("Sum(M2)"), e.getMessage());
      assertFalse(e.getMessage().contains("Sum(M1)"), e.getMessage());

      assertThrows(IllegalArgumentException.class, () -> p.rank(
         "color", "TEAM", new DimensionSortRanking.Ranking("top", 3, "Sum(M1)", null), null));
   }

   @Test
   void anUnknownMeasureIsRefusedListingTheOnesThatExist() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> p.sort("color", "TEAM", DESC, "Nope"));
      assertTrue(e.getMessage().contains("No measure 'Nope'"), e.getMessage());
      assertTrue(e.getMessage().contains("Sum(M1)"), e.getMessage());
      assertEquals(XConstants.SORT_ASC, p.storedOrder(0, "color"), "nothing was written");
   }

   @Test
   void aMeasureWithoutThatChannelsFieldIsRefused() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> p.sort("color", "TEAM", DESC, "Sum(M2)"));
      assertTrue(e.getMessage().contains("has no color field bound"), e.getMessage());
      assertTrue(e.getMessage().contains("Sum(M1)"), e.getMessage());
   }

   @Test
   void aMeasuresMeasureFieldAndAWrongColumnAreRefused() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.aesthetics.setField("tok", principal(), "Chart1", "color",
                            new FieldRef("M2", "measure", "Sum", null, null), null, "M1", "");
      p.bind("color", "M2");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> p.sort("color", null, DESC, "Sum(M1)"));
      assertTrue(e.getMessage().contains("not a dimension"), e.getMessage());

      e = assertThrows(IllegalArgumentException.class,
                       () -> p.sort("color", "OTHER", DESC, "Sum(M2)"));
      assertTrue(e.getMessage().contains("It holds: TEAM"), e.getMessage());
   }

   @Test
   void allIsRefusedBecauseTheComposerOrdersOneMeasureAtATime() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");

      IllegalArgumentException e = assertThrows(
         IllegalArgumentException.class, () -> p.sort("color", "TEAM", DESC, " ALL "));
      assertTrue(e.getMessage().contains("ONE measure"), e.getMessage());
   }

   @Test
   void aChannelTheChartDoesNotRenderPerMeasureIsRefusedNotStoredAndNeverDrawn() throws Exception {
      Pipeline p = pipeline(GraphTypes.CHART_BAR, true);
      p.bind("color", "M1");
      // As on a contour chart: only the color frame is per measure.
      ChartBindingModel model = p.model();

      for(String channel : List.of("shape", "size")) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ChartBindingMutator.setSort(model, channel, "TEAM", null, DESC, true, "M1",
                                              List.of("color")), channel);
         assertTrue(e.getMessage().contains("does not render the " + channel), e.getMessage());
      }

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.sort("node-color", "TEAM", DESC, "M1"));
      assertTrue(e.getMessage().contains("node-color"), e.getMessage());
   }

   @Test
   void forMeasureIsRefusedByNameOnASingleStyleChartAndOnAShelf() throws Exception {
      Pipeline single = pipeline(GraphTypes.CHART_BAR, false);
      single.bind("color", null);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> single.sort("color", "TEAM", DESC, "Sum(M1)"));
      assertTrue(e.getMessage().contains("multi-style"), e.getMessage());

      Pipeline multi = pipeline(GraphTypes.CHART_BAR, true);
      e = assertThrows(IllegalArgumentException.class,
                       () -> multi.sort("x", "PHASE", DESC, "Sum(M1)"));
      assertTrue(e.getMessage().contains("color, shape, size and text channels only"),
                 e.getMessage());
   }

   @Test
   void aSingleStyleChartKeepsSortingItsChartLevelChannel() throws Exception {
      Pipeline single = pipeline(GraphTypes.CHART_BAR, false);
      single.bind("color", null);

      single.sort("color", "TEAM", DESC, null);

      assertEquals(XConstants.SORT_DESC,
                   ((VSChartDimensionRef) single.info().getColorField().getDataRef()).getOrder());
   }

   @Test
   void aGanttChartIsRefusedByNameWithAndWithoutForMeasure() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_GANTT);

      for(String forMeasure : new String[] {null, "Sum(M1)"}) {
         IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ChartBindingMutator.setSort(model, "color", "TEAM", null, DESC, true, forMeasure,
                                              List.of("color")));
         assertTrue(e.getMessage().contains("Gantt"), e.getMessage());

         e = assertThrows(IllegalArgumentException.class,
            () -> ChartBindingMutator.setRanking(
               model, "color", "TEAM", null,
               new DimensionSortRanking.Ranking("top", 3, "Sum(M1)", null), true, forMeasure,
               List.of("color")));
         assertTrue(e.getMessage().contains("Gantt"), e.getMessage());
      }
   }

   @Test
   void aStoredTimeSeriesFlagThatIsNotInForceIsClearedSoTheOrderTakesEffect() {
      ChartBindingModel model = new ChartBindingModel();
      model.setChartType(GraphTypes.CHART_BAR);
      ChartAggregateRefModel measure = new ChartAggregateRefModel();
      measure.setColumnValue("M1");
      measure.setFormula("Sum");
      measure.setFullName("Sum(M1)");
      ChartDimensionRefModel date = new ChartDimensionRefModel();
      date.setColumnValue("D");
      date.setTimeSeries(true);
      AestheticInfo info = new AestheticInfo();
      info.setDataInfo(date);
      measure.setColorField(info);
      model.setYFields(new java.util.ArrayList<>(List.of(measure)));

      ChartBindingMutator.setSort(model, "color", "D", null, DESC, true, "Sum(M1)",
                                  List.of("color"));

      assertEquals(XConstants.SORT_DESC, date.getOrder());
      assertFalse(date.isTimeSeries());
   }

   // -- harness -----------------------------------------------------------------------------

   private static Principal principal() {
      return () -> "admin";
   }

   private static VSChartDimensionRef dimension(String column) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(column);
      dim.setDataRef(new AttributeRef(column));
      return dim;
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setColumnValue(column);
      aggregate.setDataRef(new AttributeRef(column));
      aggregate.setFormula(AggregateFormula.SUM);
      return aggregate;
   }

   /** A chart with dimension PHASE on X and measures M1, M2 on Y. */
   private static Pipeline pipeline(int type, boolean multi) throws Exception {
      VSChartBindingFactory factory = realVSChartBindingFactory();

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(type);
      info.setMultiStyles(multi);
      info.addXField(dimension("PHASE"));
      info.addYField(measure("M1"));
      info.addYField(measure("M2"));
      chart.setVSChartInfo(info);
      vs.addAssembly(chart);

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
      ChangeChartRefService refs = mock(ChangeChartRefService.class);
      doAnswer(invocation -> {
         ChangeChartRefEvent event = invocation.getArgument(1);
         factory.updateAssembly(event.getModel(), chart);
         return null;
      }).when(refs).changeChartRef(any(), any(), any(), any(), any());

      ChartAestheticAgentService aestheticService = new ChartAestheticAgentService(
         sessions, binding, aesthetics, mock(DataRefModelFactoryService.class));
      ChartBindingService service = new ChartBindingService(
         sessions, binding, refs, mock(ChangeChartTypeService.class),
         mock(SwapXYBindingService.class), mock(ChangeSeparateStatusService.class),
         mock(DataRefModelFactoryService.class));

      return new Pipeline(service, aestheticService, chart, binding);
   }

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

   private record Pipeline(ChartBindingService service, ChartAestheticAgentService aesthetics,
                           ChartVSAssembly chart, VSBindingService binding)
   {
      DefaultVSChartInfo info() {
         return (DefaultVSChartInfo) chart.getVSChartInfo();
      }

      /** Binds TEAM on {@code channel} of {@code measure}, or chart-level when it is null. */
      void bind(String channel, String measure) throws Exception {
         aesthetics.setField("tok", principal(), "Chart1", channel, TEAM, null, measure, "");
      }

      void sort(String channel, String column, DimensionSortRanking.Sort sort, String forMeasure)
         throws Exception
      {
         service.setSort("tok", principal(), "Chart1", channel, column, null, sort, forMeasure,
                         "");
      }

      void rank(String channel, String column, DimensionSortRanking.Ranking ranking,
                String forMeasure) throws Exception
      {
         service.setRanking("tok", principal(), "Chart1", channel, column, null, ranking,
                            forMeasure, "");
      }

      ChartBindingModel model() throws Exception {
         return (ChartBindingModel) binding.createModel(chart);
      }

      /** The dimension a measure carries on a channel, read back through a fresh model. */
      BDimensionRefModel storedDimension(int measure, String channel) throws Exception {
         AestheticInfo field = storedField(measure, channel);
         return field == null ? null : (BDimensionRefModel) field.getDataInfo();
      }

      AestheticInfo storedField(int measure, String channel) throws Exception {
         ChartAggregateRefModel aggregate =
            (ChartAggregateRefModel) model().getYFields().get(measure);
         return switch(channel) {
            case "color" -> aggregate.getColorField();
            case "shape" -> aggregate.getShapeField();
            case "size" -> aggregate.getSizeField();
            default -> aggregate.getTextField();
         };
      }

      int storedOrder(int measure, String channel) throws Exception {
         return storedDimension(measure, channel).getOrder();
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> read(String channel) throws Exception {
         return (Map<String, Object>) aesthetics.read("tok", principal(), "Chart1").get(channel);
      }
   }
}
