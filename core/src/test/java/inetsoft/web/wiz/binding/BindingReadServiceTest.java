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
import inetsoft.uql.viewsheet.CalcTableVSAssembly;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.VSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.web.binding.model.ChartBindingModel;
import inetsoft.web.binding.model.graph.ChartAggregateRefModel;
import inetsoft.web.binding.model.graph.ChartDimensionRefModel;
import inetsoft.web.binding.service.VSBindingService;
import inetsoft.web.wiz.binding.model.AssemblyBinding;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.web.wiz.binding.model.FieldRef;

@Tag("core")
class BindingReadServiceTest {
   @Test
   void readsAChartsShelvesIntoTheSharedVocabulary() {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(new ChartBindingModel());

      AssemblyBinding result = new BindingReadService(binding)
         .read(runtimeWith("Chart1", mock(ChartVSAssembly.class)), "Chart1");

      assertEquals("Chart1", result.assembly());
      assertTrue(result.shelves().containsKey("x"), "a chart exposes an x shelf");
      assertTrue(result.shelves().containsKey("y"));
      assertTrue(result.shelves().containsKey("group"));
   }

   /**
    * The four candlestick shelves were bound and visibly rendering on a live chart while this read
    * reported {@code {x, y, group}} and none of them, so a write could not be read back at all.
    * {@link ChartBindingMutator#readSingleShelf} already answers this, and
    * {@code ChartBindingService.requireNoBoundFields} already loops the whole list to name what a
    * forced repoint would delete — the read simply never did the same loop.
    */
   @Test
   void readsTheSingleValueShelvesAChartActuallyBinds() {
      ChartBindingModel model = new ChartBindingModel();
      model.setOpenField(aggregate("PAID", "Sum"));
      model.setCloseField(aggregate("DISCOUNT", "Average"));

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      AssemblyBinding result = new BindingReadService(binding)
         .read(runtimeWith("Chart1", mock(ChartVSAssembly.class)), "Chart1");

      assertTrue(result.shelves().containsKey("open"), "a bound open shelf must be reported");
      assertEquals("PAID", result.shelves().get("open").get(0).column());
      assertEquals("Sum", result.shelves().get("open").get(0).aggregate());
      assertEquals("DISCOUNT", result.shelves().get("close").get(0).column());
   }

   /**
    * One shelf from each of the four families, because the live run only exercised the OHLC four
    * and a fix that special-cased those would have looked identical.
    */
   @Test
   void coversEverySingleShelfFamilyRatherThanJustTheCandlestickFour() {
      ChartBindingModel model = new ChartBindingModel();
      model.setPathField(aggregate("ORDER_ID", "Count"));
      model.setSourceField(aggregate("CUSTOMER_ID", "Count"));
      model.setStartField(aggregate("QUANTITY", "Min"));
      model.setMilestoneField(aggregate("PAID", "Max"));

      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      AssemblyBinding result = new BindingReadService(binding)
         .read(runtimeWith("Chart1", mock(ChartVSAssembly.class)), "Chart1");

      for(String shelf : List.of("path", "source", "start", "milestone")) {
         assertTrue(result.shelves().containsKey(shelf), shelf + " must be reported when bound");
      }
   }

   /**
    * Only the shelves a chart actually binds are reported. Reporting all ten unconditionally would
    * advertise {@code milestone} on a pie chart — the same fabricated-capability shape as the
    * phantom {@code Dimensions} column and the phantom {@code y2} axis this plan already recorded.
    *
    * <p>Green on arrival: it drove nothing, and exists because the tempting symmetry with x/y/group
    * — which are present-and-empty precisely because they are always meaningful — is the wrong
    * precedent to copy here.
    */
   @Test
   void doesNotAdvertiseSingleValueShelvesNothingIsBoundTo() {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(new ChartBindingModel());

      AssemblyBinding result = new BindingReadService(binding)
         .read(runtimeWith("Chart1", mock(ChartVSAssembly.class)), "Chart1");

      assertEquals(Set.of("x", "y", "group"), result.shelves().keySet(),
                   "an unbound chart reports its three list shelves and nothing else");
   }

   /**
    * Under Multi Style each measure renders with its own type, so that type is a property of the
    * bound field rather than of the chart — {@code ChartAggregateRefModel} carries it, from the
    * {@code ChartAestheticModel} interface, and {@code ChartDimensionRefModel} has no such property
    * at all. Reported as the raw GraphTypes code; the plugin names it, since that vocabulary
    * already exists in three copies on this side.
    */
   @Test
   void reportsAPerMeasureChartTypeOnXAndYUnderMultiStyle() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(true);
      model.setXFields(List.of(withChartType(aggregate("PAID", "Sum"), 1)));
      model.setYFields(List.of(withChartType(aggregate("DISCOUNT", "Sum"), 5)));

      AssemblyBinding result = read(model);

      assertEquals(Integer.valueOf(1), result.shelves().get("x").get(0).chartType());
      assertEquals(Integer.valueOf(5), result.shelves().get("y").get(0).chartType());
   }

   /**
    * The case a design-time-only read serves worst: a measure left at {@code auto} answers
    * {@code auto} forever, and the runtime type is the only thing that says what it drew. This is
    * also the runtime type that survives on a merged chart, where the assembly-level one is not
    * maintained — and merged is the multi-style case.
    */
   @Test
   void reportsWhatAMeasureActuallyDrewWhenThatDiffersFromWhatWasStored() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(true);
      model.setYFields(List.of(
         withTypes(aggregate("PAID", "Sum"), GraphTypes.CHART_AUTO, GraphTypes.CHART_BAR)));

      FieldRef ref = read(model).shelves().get("y").get(0);

      assertEquals(Integer.valueOf(GraphTypes.CHART_AUTO), ref.chartType());
      assertEquals(Integer.valueOf(GraphTypes.CHART_BAR), ref.runtimeChartType());
   }

   /** Divergence-only: reported on every field it would be noise rather than a signal. */
   @Test
   void withholdsTheRuntimeTypeWhenItMatchesWhatWasStored() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(true);
      model.setYFields(List.of(withTypes(aggregate("PAID", "Sum"), 5, 5)));

      assertNull(read(model).shelves().get("y").get(0).runtimeChartType());
   }

   @Test
   void neverClaimsARuntimeTypeWhereItWithholdsTheStoredOne() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(false);
      model.setYFields(List.of(withTypes(aggregate("PAID", "Sum"), 1, 5)));

      FieldRef ref = read(model).shelves().get("y").get(0);

      assertNull(ref.chartType());
      assertNull(ref.runtimeChartType());
   }

   /**
    * With multi-style off the per-field value is inert — the chart draws as one type — so reporting
    * it would hand back a setting that does not describe what renders. That is the shape of the
    * inert-frame finding this lane already recorded against set_visual_frame.
    */
   @Test
   void withholdsThePerMeasureTypeWhenTheChartIsNotMultiStyle() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(false);
      model.setXFields(List.of(withChartType(aggregate("PAID", "Sum"), 5)));

      assertNull(read(model).shelves().get("x").get(0).chartType(),
                 "a per-measure type that cannot render must not be reported");
   }

   /** A dimension has no chart type in the model, so claiming one would be fabricating it. */
   @Test
   void neverClaimsAPerMeasureTypeForADimension() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(true);
      model.setXFields(List.of(new ChartDimensionRefModel()));

      assertNull(read(model).shelves().get("x").get(0).chartType());
   }

   /**
    * The write side searches x and y only — {@code ChangeChartTypeProcessor} loops
    * {@code getXFieldCount()}/{@code getYFieldCount()} and nothing else — so an aggregate sitting on
    * `group`, or on one of the ten single-field shelves, has no per-measure type even though the
    * model would happily answer for it. Reporting one there is the phantom-capability shape this
    * plan has recorded three times.
    */
   @Test
   void neverClaimsAPerMeasureTypeOffXAndY() {
      ChartBindingModel model = new ChartBindingModel();
      model.setMultiStyles(true);
      model.setGroupFields(List.of(withChartType(aggregate("PAID", "Sum"), 5)));
      model.setOpenField(withChartType(aggregate("DISCOUNT", "Max"), 5));

      AssemblyBinding result = read(model);

      assertNull(result.shelves().get("group").get(0).chartType(), "group carries no chart type");
      assertNull(result.shelves().get("open").get(0).chartType(),
                 "a single-field shelf carries no chart type");
   }

   private AssemblyBinding read(ChartBindingModel model) {
      VSBindingService binding = mock(VSBindingService.class);
      when(binding.createModel(any())).thenReturn(model);

      return new BindingReadService(binding)
         .read(runtimeWith("Chart1", mock(ChartVSAssembly.class)), "Chart1");
   }

   private static ChartAggregateRefModel withChartType(ChartAggregateRefModel ref, int type) {
      ref.setChartType(type);
      // Runtime mirrors design time unless a test says otherwise, so the divergence-only rule does
      // not add a runtime type to every fixture that only cares about the stored one.
      ref.setRTChartType(type);
      return ref;
   }

   private static ChartAggregateRefModel withTypes(ChartAggregateRefModel ref, int type,
                                                   int runtime)
   {
      ref.setChartType(type);
      ref.setRTChartType(runtime);
      return ref;
   }

   private static ChartAggregateRefModel aggregate(String column, String formula) {
      ChartAggregateRefModel ref = new ChartAggregateRefModel();
      ref.setColumnValue(column);
      ref.setFormula(formula);
      return ref;
   }

   @Test
   void refusesACalcTablePointingAtTheLayoutTool() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> new BindingReadService(mock(VSBindingService.class))
            .read(runtimeWith("Calc1", mock(CalcTableVSAssembly.class)), "Calc1"));

      assertTrue(thrown.getMessage().contains("get_calc_layout"),
                 "must point at the calc-table tool, got: " + thrown.getMessage());
   }

   @Test
   void namesTheUnknownAssemblyRatherThanReturningAnEmptyBinding() {
      Exception thrown = assertThrows(
         IllegalArgumentException.class,
         () -> new BindingReadService(mock(VSBindingService.class))
            .read(runtimeWith("Ghost", null), "Ghost"));

      assertTrue(thrown.getMessage().contains("Ghost"));
   }

   private static RuntimeViewsheet runtimeWith(String name, VSAssembly assembly) {
      Viewsheet vs = mock(Viewsheet.class);
      when(vs.getAssembly(name)).thenReturn(assembly);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      return rvs;
   }
}
