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
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.NamedRangeRef;
import inetsoft.uql.asset.SNamedGroupInfo;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
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
import inetsoft.web.wiz.binding.model.FieldRef.NamedGroupValues;
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
 * Bug #80332: {@code set_aesthetic_field} had no way to bind a field on one measure (or all) of a
 * multi-style chart, which the Composer's Aesthetic pane does through its measure selector, and
 * the chart-level write it offered was refused on such a chart. The request now carries an
 * optional {@code measure}, and the write goes onto that measure's own field.
 *
 * <p>Each case runs the real agent service against a real {@code DefaultVSChartInfo} and the real
 * model write path ({@code VSChartBindingFactory.createModel}/{@code updateAssembly}), and reads
 * the chart back through the same service.
 */
@WizAgentTestSupport
class ChartAestheticMeasureFieldTest {
   private static final FieldRef PHASE = new FieldRef("PHASE", "dimension", null, null, null);
   private static final FieldRef REGION = new FieldRef("REGION", "dimension", null, null, null);

   @Test
   void perMeasureWriteBindsOnlyThatMeasureAndLeavesTheChartLevelSlotEmpty() throws Exception {
      Pipeline p = pipeline(true);

      p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "M1", "");

      assertEquals("PHASE", fieldName(p.measure(0), "color"));
      assertNull(fieldName(p.measure(1), "color"), "the other measure is untouched");
      assertNull(p.info().getColorField(), "the chart-level slot is left alone (decision A3)");

      Map<String, Object> color = p.read("color");
      Map<?, ?> byMeasure = assertInstanceOf(Map.class, color.get("fieldsByMeasure"));
      assertEquals("PHASE", byMeasure.get("Sum(M1)"));
      assertNull(byMeasure.get("Sum(M2)"));
   }

   @Test
   void aSecondMeasureCanBeBoundToADifferentField() throws Exception {
      Pipeline p = pipeline(true);

      p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "M1", "");
      p.service.setField("tok", principal(), "Chart1", "color", REGION, null, "M2", "");

      assertEquals("PHASE", fieldName(p.measure(0), "color"));
      assertEquals("REGION", fieldName(p.measure(1), "color"));
   }

   @Test
   void everyChannelThatAnAggregateCarriesIsWrittenPerMeasure() throws Exception {
      for(String channel : List.of("color", "shape", "size", "text")) {
         Pipeline p = pipeline(true);
         p.service.setField("tok", principal(), "Chart1", channel, PHASE, null, "M2", "");

         assertNull(fieldName(p.measure(0), channel), channel);
         assertEquals("PHASE", fieldName(p.measure(1), channel), channel);
      }
   }

   @Test
   void allBindsEveryMeasureAndIsCaseInsensitive() throws Exception {
      for(String all : List.of("all", "ALL", "All", " all ")) {
         Pipeline p = pipeline(true);

         p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, all, "");

         assertEquals("PHASE", fieldName(p.measure(0), "color"), all);
         assertEquals("PHASE", fieldName(p.measure(1), "color"), all);
      }
   }

   /** A6: "all" replaces what each measure already holds, not only the empty ones. */
   @Test
   void allReplacesDifferentExistingFieldsOnEveryMeasure() throws Exception {
      Pipeline p = pipeline(true);
      p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "M1", "");
      p.service.setField("tok", principal(), "Chart1", "color", REGION, null, "M2", "");

      p.service.setField("tok", principal(), "Chart1", "color", REGION, null, "all", "");

      assertEquals("REGION", fieldName(p.measure(0), "color"));
      assertEquals("REGION", fieldName(p.measure(1), "color"));
   }

   /** A6: a measure bound twice on Y is one target, as it is to the renderer. */
   @Test
   void aMeasureBoundTwiceOnYIsOneTarget() throws Exception {
      Pipeline p = pipeline(true);
      p.info().addYField(measure("M1"));

      assertEquals(List.of(0, 1), ChartAestheticMutator.setMeasureField(
         p.model(), "color", PHASE, null, null, null, AestheticChannels.FRAME_CHANNELS, "all"));
   }

   @Test
   void anyNameTheMeasureAnswersToIsAccepted() throws Exception {
      for(String name : List.of("Sum(M1)", "M1")) {
         Pipeline p = pipeline(true);
         p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, name, "");
         assertEquals("PHASE", fieldName(p.measure(0), "color"), name);
         assertNull(fieldName(p.measure(1), "color"), name);
      }
   }

   @Test
   void anUnknownMeasureIsRefusedByNameListingTheValidOnesAndWritesNothing() throws Exception {
      Pipeline p = pipeline(true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "NOPE", ""));
      assertTrue(e.getMessage().contains("'NOPE'"), e.getMessage());
      assertTrue(e.getMessage().contains("Sum(M1)") && e.getMessage().contains("Sum(M2)"),
                 e.getMessage());
      assertNull(fieldName(p.measure(0), "color"));
      assertNull(fieldName(p.measure(1), "color"));
   }

   @Test
   void aBlankMeasureIsRefused() throws Exception {
      Pipeline p = pipeline(true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "  ", ""));
      assertTrue(e.getMessage().contains("'measure'"), e.getMessage());
   }

   /** Omitting measure keeps today's refusal, wording intact, with a pointer to the new param. */
   @Test
   void omittingMeasureOnAMultiStyleChartStillRefusesAndPointsToMeasure() throws Exception {
      Pipeline p = pipeline(true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, ""));
      assertTrue(e.getMessage().contains("Turn multi-style off"), e.getMessage());
      assertTrue(e.getMessage().contains("then turn multi-style back on"), e.getMessage());
      assertTrue(e.getMessage().contains("'measure'"), e.getMessage());
      assertNull(fieldName(p.measure(0), "color"));
   }

   @Test
   void measureOnASingleStyleChartIsRefusedByNameNotIgnored() throws Exception {
      Pipeline p = pipeline(false);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "M1", ""));
      assertTrue(e.getMessage().contains("multi-style"), e.getMessage());
      assertTrue(e.getMessage().contains("set_chart_type"), e.getMessage());
      assertNull(p.info().getColorField());
      assertNull(fieldName(p.measure(0), "color"));

      assertThrows(IllegalArgumentException.class,
         () -> p.service.clearField("tok", principal(), "Chart1", "color", "M1", ""));
   }

   @Test
   void measureOnAGanttChartIsRefusedByName() throws Exception {
      Pipeline p = pipeline(true);
      GanttVSChartInfo gantt = new GanttVSChartInfo();
      gantt.setChartType(GraphTypes.CHART_GANTT);
      gantt.setStartField(measure("START"));
      gantt.setEndField(measure("END"));
      p.chart.setVSChartInfo(gantt);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "START",
                                  ""));
      assertTrue(e.getMessage().contains("Gantt"), e.getMessage());
   }

   @Test
   void nodeChannelsHaveNoPerMeasureSlotAndAreRefusedByName() throws Exception {
      Pipeline p = pipeline(true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartAestheticMutator.setMeasureField(
            p.model(), "node-color", PHASE, null, null, null,
            AestheticChannels.FRAME_CHANNELS, "all"));
      assertTrue(e.getMessage().contains("node-color"), e.getMessage());
      assertTrue(e.getMessage().contains("color, shape, size and text"), e.getMessage());

      assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "node-color", PHASE, null, "M1",
                                  ""));
   }

   /** A5: a channel the chart type does not render per measure would be stored and never drawn. */
   @Test
   void aChannelTheChartDoesNotRenderPerMeasureIsRefused() throws Exception {
      Pipeline p = pipeline(true);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> ChartAestheticMutator.setMeasureField(
            p.model(), "size", PHASE, null, null, null, List.of("color", "shape"), "M1"));
      assertTrue(e.getMessage().contains("size"), e.getMessage());
      assertTrue(e.getMessage().contains("per measure"), e.getMessage());
   }

   /** A4: a date comparison that forces multi-style still refuses, with its own message. */
   @Test
   void aDateComparisonForcedMultiStyleChartIsRefusedForAMeasureWriteToo() throws Exception {
      Pipeline p = pipeline(true);
      VSChartInfo forced = mock(VSChartInfo.class);
      when(forced.isMultiAesthetic()).thenReturn(true);
      when(forced.isAppliedDateComparison()).thenReturn(true);
      when(forced.isDesignMultiStyles()).thenReturn(false);
      ChartVSAssembly mocked = mock(ChartVSAssembly.class);
      when(mocked.getVSChartInfo()).thenReturn(forced);
      Viewsheet forcedVs = mock(Viewsheet.class);
      when(forcedVs.getAssembly("Chart1")).thenReturn(mocked);
      when(p.rvs.getViewsheet()).thenReturn(forcedVs);

      Exception e = assertThrows(Exception.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "M1", ""));
      assertTrue(e.getMessage().contains("clear_date_comparison"), e.getMessage());
   }

   @Test
   void clearRemovesTheFieldFromOneMeasureOnly() throws Exception {
      Pipeline p = pipeline(true);
      p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "all", "");

      p.service.clearField("tok", principal(), "Chart1", "color", "M1", "");

      assertNull(fieldName(p.measure(0), "color"));
      assertEquals("PHASE", fieldName(p.measure(1), "color"));
   }

   @Test
   void clearAllRemovesTheFieldFromEveryMeasure() throws Exception {
      Pipeline p = pipeline(true);
      p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "all", "");

      p.service.clearField("tok", principal(), "Chart1", "color", "All", "");

      assertNull(fieldName(p.measure(0), "color"));
      assertNull(fieldName(p.measure(1), "color"));
   }

   @Test
   void clearWithAnUnknownMeasureIsRefusedAndRemovesNothing() throws Exception {
      Pipeline p = pipeline(true);
      p.service.setField("tok", principal(), "Chart1", "color", PHASE, null, "all", "");

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.clearField("tok", principal(), "Chart1", "color", "NOPE", ""));
      assertTrue(e.getMessage().contains("Sum(M1)"), e.getMessage());
      assertEquals("PHASE", fieldName(p.measure(0), "color"));
      assertEquals("PHASE", fieldName(p.measure(1), "color"));
   }

   @Test
   void clearWithoutMeasureOnAMultiStyleChartStillRefuses() throws Exception {
      Pipeline p = pipeline(true);

      assertThrows(IllegalArgumentException.class,
         () -> p.service.clearField("tok", principal(), "Chart1", "color", ""));
   }

   // ── A1: the validators that read the chart-level slot still fire for a measure-scoped write

   @Test
   void aNamedGroupThatConflictsWithTheShelfIsStillRefusedOnAMeasureWrite() throws Exception {
      Pipeline p = pipeline(true);
      p.info().addXField(groupedDimension("PHASE", "Fun", "Design"));
      NamedGroupValues sent = new NamedGroupValues(
         List.of(new NamedGroupValues.Clause("Creative", List.of("Build"))), null);
      FieldRef conflicting = new FieldRef("PHASE", "dimension", null, null, null, null, null,
                                          sent, null, null, null, null, null, null);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "color", conflicting, null, "M1",
                                  ""));
      assertTrue(e.getMessage().contains("Creative") && e.getMessage().contains("Fun"),
                 e.getMessage());
      assertNull(fieldName(p.measure(0), "color"), "a refused write leaves the chart as it was");
   }

   @Test
   void aNamedGroupDroppedByTheRuntimeIsStillRefusedOnAMeasureWriteReadBack() {
      VSAestheticRef plain = new VSAestheticRef();
      plain.setDataRef(dimension("PHASE"));
      NamedGroupValues sent = new NamedGroupValues(
         List.of(new NamedGroupValues.Clause("Creative", List.of("Build"))), null);
      FieldRef field = new FieldRef("PHASE", "dimension", null, null, null, null, null, sent,
                                    null, null, null, null, null, null);

      assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticNamedGroupKeptOn(plain, "color", field));
      assertDoesNotThrow(
         () -> ChartBindingService.requireAestheticNamedGroupKeptOn(null, "color", field));
   }

   // ── rollback of an `all` write refused part-way, and the per-measure calculator validator

   /**
    * M1 already holds a time-series PHASE (so re-sending it is an echo the time-series guard lets
    * through), M2 holds nothing (so the same write is refused there). The `all` write binds M1
    * first and is refused on M2; M1 must be put back exactly as it was, not left rebound or null.
    */
   @Test
   void anAllWriteRefusedOnALaterMeasureRestoresEarlierMeasuresExactly() throws Exception {
      Pipeline p = pipeline(true);
      ChartBindingModel model = p.model();
      ChartAestheticMutator.setMeasureField(
         model, "color", REGION, null, null, null, AestheticChannels.FRAME_CHANNELS, "M1");
      inetsoft.web.binding.model.graph.AestheticInfo m1 =
         ChartAestheticMutator.measureField(model, "color", 0);
      ((inetsoft.web.binding.model.graph.ChartDimensionRefModel) m1.getDataInfo())
         .setTimeSeries(true);
      Object dataBefore = m1.getDataInfo();
      Object frameBefore = m1.getFrame();
      String nameBefore = m1.getFullName();
      assertEquals("REGION", nameBefore);

      FieldRef timeSeries = new FieldRef("REGION", "dimension", null, null, null, null, null,
                                         null, null, null, null, null, Boolean.TRUE, null);

      assertThrows(IllegalArgumentException.class, () -> ChartAestheticMutator.setMeasureField(
         model, "color", timeSeries, null, null, null, AestheticChannels.FRAME_CHANNELS, "all"));

      assertSame(m1, ChartAestheticMutator.measureField(model, "color", 0));
      assertSame(dataBefore, m1.getDataInfo(), "M1's data is put back, not left rebound");
      assertSame(frameBefore, m1.getFrame());
      assertEquals(nameBefore, m1.getFullName());
      assertNull(ChartAestheticMutator.measureField(model, "color", 1),
                 "M2 was never bound");
   }

   /**
    * A running total whose breakBy passes the pre-write check (it contains a bound column) but
    * names no dimension exactly, so the runtime drops it: the measure write must still be refused
    * by the read-back validator, as the chart-level write is.
    */
   @Test
   void aCalculatorTheRuntimeDroppedIsStillRefusedOnAMeasureWrite() throws Exception {
      Pipeline p = pipeline(true, true);
      inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo calc =
         new inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo();
      calc.setAggregate("Sum");
      calc.setResetLevel(-1);
      calc.setBreakBy("Month(PHASE)");
      FieldRef field = new FieldRef("M2", "measure", "Sum", null, null, null, null, null, calc,
                                    null, null, null, null, null);

      IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
         () -> p.service.setField("tok", principal(), "Chart1", "size", field, null, "M1", ""));
      assertTrue(e.getMessage().contains("calculateInfo.breakBy"), e.getMessage());
   }

   @Test
   void theCalculatorValidatorReadsTheGivenRefNotTheChartLevelSlot() {
      VSAestheticRef dropped = new VSAestheticRef();
      VSChartAggregateRef aggregate = measure("M2");
      aggregate.setCalculator(new inetsoft.report.composition.graph.calc.RunningTotalCalc());
      dropped.setDataRef(aggregate);
      inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo calc =
         new inetsoft.web.binding.model.graph.calc.RunningTotalCalcInfo();
      calc.setBreakBy("Month(PHASE)");
      FieldRef field = new FieldRef("M2", "measure", "Sum", null, null, null, null, null, calc,
                                    null, null, null, null, null);

      assertThrows(IllegalArgumentException.class,
         () -> ChartBindingService.requireAestheticCalculatorResolvedOn(
            new DefaultVSChartInfo(), dropped, field));
      assertDoesNotThrow(() -> ChartBindingService.requireAestheticCalculatorResolvedOn(
         new DefaultVSChartInfo(), null, field));
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

   private static VSChartDimensionRef dimension(String column) {
      VSChartDimensionRef dim = new VSChartDimensionRef();
      dim.setGroupColumnValue(column);
      dim.setDataRef(new AttributeRef(column));
      return dim;
   }

   private static VSChartDimensionRef groupedDimension(String column, String group,
                                                       String... members)
   {
      VSChartDimensionRef dim = dimension(column);
      SNamedGroupInfo info = new SNamedGroupInfo();
      info.setGroupValue(group, new java.util.ArrayList<>(List.of((Object[]) members)));
      dim.setNamedGroupInfo(info);
      dim.setGroupType("" + NamedRangeRef.DATA_GROUP);
      return dim;
   }

   private static VSChartAggregateRef measure(String column) {
      VSChartAggregateRef aggregate = new VSChartAggregateRef();
      aggregate.setColumnValue(column);
      aggregate.setDataRef(new AttributeRef(column));
      aggregate.setFormula(AggregateFormula.SUM);
      return aggregate;
   }

   private static Principal principal() {
      return () -> "admin";
   }

   private record Pipeline(ChartAestheticAgentService service, ChartVSAssembly chart,
                           RuntimeViewsheet rvs, VSBindingService binding)
   {
      DefaultVSChartInfo info() {
         return (DefaultVSChartInfo) chart.getVSChartInfo();
      }

      /** The runtime measure ref at {@code i} on Y. */
      ChartRef measure(int i) {
         return info().getYFields()[i];
      }

      ChartBindingModel model() throws Exception {
         return (ChartBindingModel) binding.createModel(chart);
      }

      @SuppressWarnings("unchecked")
      Map<String, Object> read(String channel) throws Exception {
         return (Map<String, Object>) service.read("tok", principal(), "Chart1").get(channel);
      }
   }

   /**
    * A bar chart with measures M1 and M2 on Y and dimension PHASE on X, multi-style on or off. The
    * binding service reads and writes it through the real {@code VSChartBindingFactory}; the
    * change-ref service's write is its {@code updateAssembly}.
    */
   private static Pipeline pipeline(boolean multi) throws Exception {
      return pipeline(multi, false);
   }

   /**
    * @param dropBreakBy simulates {@code ChangeChartProcessor} clearing a calculator's dimension
    *        reference it cannot match, on every aesthetic field of every measure, after the write.
    */
   private static Pipeline pipeline(boolean multi, boolean dropBreakBy) throws Exception {
      VSChartBindingFactory factory = realVSChartBindingFactory();

      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      DefaultVSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
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

         if(dropBreakBy) {
            for(ChartAggregateRef aggregate :
               ((DefaultVSChartInfo) chart.getVSChartInfo()).getAestheticAggregateRefs(false))
            {
               for(AestheticRef field : List.of(
                  aggregate instanceof ChartBindable b && b.getColorField() != null
                     ? b.getColorField() : new VSAestheticRef(),
                  aggregate instanceof ChartBindable b2 && b2.getSizeField() != null
                     ? b2.getSizeField() : new VSAestheticRef()))
               {
                  if(field.getDataRef() instanceof inetsoft.uql.viewsheet.XAggregateRef xa &&
                     xa.getCalculator() instanceof
                        inetsoft.report.composition.graph.calc.RunningTotalCalc rt)
                  {
                     rt.setBreakBy(null);
                  }
               }
            }
         }

         return null;
      }).when(aesthetics).changeChartAesthetic(any(), any(), any(), any(), any());

      return new Pipeline(new ChartAestheticAgentService(
         sessions, binding, aesthetics, mock(DataRefModelFactoryService.class)), chart, rvs,
         binding);
   }

   /** The real bean graph, as {@code ChartAestheticGanttFieldTest} builds it. */
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
