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

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.ColorFrame;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.IntervalElement;
import inetsoft.report.composition.RuntimeViewsheet;
import inetsoft.report.composition.graph.MultiMeasureColorFrame;
import inetsoft.test.*;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.web.viewsheet.controller.chart.VSChartLegendsVisibilityService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Bug #80505 item 3: a hidden automatic "Measures" legend (no colour field bound, several
 * measures) read as "no legends". The legend frame is a real {@link MultiMeasureColorFrame} on a
 * real {@link EGraph} element, the descriptors are written by the real hide-all
 * ({@code VSChartLegendsVisibilityService.showAllDescriptorLegends}), and the renderer's
 * descriptor-to-frame step is applied the way {@code GraphGenerator.processColorLegend} does it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ChartVisibilityStateAutomaticLegendTest {
   private static final String TOTAL = "Sum(Total)";
   private static final String QUANTITY = "Sum(Quantity)";

   private static VSChartAggregateRef sum(String column) {
      VSChartAggregateRef m = new VSChartAggregateRef();
      m.setDataRef(new AttributeRef(column));
      m.setColumnValue(column);
      m.setFormula(AggregateFormula.SUM);
      return m;
   }

   /** A merged bar chart with the given measures on y and nothing on the colour channel. */
   private static VSChartInfo info(String... measures) {
      VSChartInfo info = new DefaultVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      VSChartDimensionRef category = new VSChartDimensionRef(new AttributeRef("Category"));
      category.setGroupColumnValue("Category");
      info.addXField(category);

      for(String m : measures) {
         info.addYField(sum(m));
      }

      info.setSeparatedGraph(false);
      info.setRTXFields(info.getXFields());
      info.setRTYFields(info.getYFields());
      return info;
   }

   /**
    * The graph the renderer builds for that chart: one element whose colour frame is the combined
    * measures frame, its legend switched on or off from the descriptor as processColorLegend does.
    */
   private static EGraph graph(ChartDescriptor desc, boolean maxMode, String... measures) {
      Color[] colors = new Color[measures.length];

      for(int i = 0; i < colors.length; i++) {
         colors[i] = CategoricalColorFrame.COLOR_PALETTE.get(i);
      }

      MultiMeasureColorFrame frame = new MultiMeasureColorFrame(false, measures, colors);
      LegendDescriptor legend = desc.getLegendsDescriptor().getColorLegendDescriptor();
      frame.getLegendSpec().setVisible(maxMode ? legend.isMaxModeVisible() : legend.isVisible());
      IntervalElement elem = new IntervalElement();

      for(String m : measures) {
         elem.addVar(m);
      }

      elem.setColorFrame(frame);
      EGraph graph = new EGraph();
      graph.addElement(elem);
      return graph;
   }

   private static ChartDescriptor hideAll(VSChartInfo info) {
      ChartDescriptor desc = new ChartDescriptor();
      VSChartLegendsVisibilityService.showAllDescriptorLegends(desc, info, false, false);
      return desc;
   }

   // ── the defect ────────────────────────────────────────────────────────────

   /** The old, bound-only reader cannot see this legend: the reading the bug reports. */
   @Test
   void theBoundOnlyReaderStillReadsNothingHidden() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = hideAll(info);

      assertFalse(desc.getLegendsDescriptor().getColorLegendDescriptor().isVisible());
      assertFalse(ChartVisibilityState.hiddenLegends(desc, info, false).any());
   }

   @Test
   void aHiddenMeasuresLegendIsNamedByTheTitleTheVisibleLegendShows() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = hideAll(info);

      ChartVisibilityState.HiddenLegend hidden = ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, false, TOTAL, QUANTITY), info, desc, false, false);

      assertEquals(new ChartVisibilityState.HiddenLegend("color", "Measures"), hidden);
   }

   /** Both the channel and the title resolve the hidden legend, as for a bound one. */
   @Test
   void theHiddenMeasuresLegendIsFoundByChannelAndByTitle() {
      ChartVisibilityState.HiddenLegends hidden = new ChartVisibilityState.HiddenLegends(
         List.of(new ChartVisibilityState.HiddenLegend("color", "Measures")));

      assertNotNull(hidden.find("color"));
      assertNotNull(hidden.find("Measures"));
      assertNotNull(hidden.find(" measures "));
      assertNull(hidden.find("size"));
   }

   @Test
   void theNoLegendLayoutHidesTheMeasuresLegendToo() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().setLayout(LegendsDescriptor.NO_LEGEND);

      assertEquals(new ChartVisibilityState.HiddenLegend("color", "Measures"),
                   ChartVisibilityState.hiddenAutomaticColorLegend(
                      graph(desc, false, TOTAL, QUANTITY), info, desc, false, false));
   }

   @Test
   void maxModeReadsTheMaxModeTwinFlag() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = new ChartDescriptor();
      desc.getLegendsDescriptor().getColorLegendDescriptor().setMaxModeVisible(false);

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, false, TOTAL, QUANTITY), info, desc, false, false));
      assertNotNull(ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, true, TOTAL, QUANTITY), info, desc, true, false));
   }

   // ── controls ──────────────────────────────────────────────────────────────

   @Test
   void aVisibleMeasuresLegendIsNotHidden() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = new ChartDescriptor();

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, false, TOTAL, QUANTITY), info, desc, false, false));
   }

   /** A legend already in the laid-out list is drawn, whatever else says it is off. */
   @Test
   void aDrawnFieldLessColorLegendMeansNothingIsHidden() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = hideAll(info);

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, false, TOTAL, QUANTITY), info, desc, false, true));
   }

   /**
    * One measure draws no legend (a single item, not forced), so hiding every legend must not
    * make it "a hidden legend": the false positive the bound reader's single-value rule avoids.
    */
   @Test
   void aSingleMeasureChartWithTheDescriptorOffHasNoHiddenLegend() {
      VSChartInfo info = info(TOTAL);
      ChartDescriptor desc = hideAll(info);

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, false, TOTAL), info, desc, false, false));
   }

   @Test
   void aBoundColorLegendIsLeftToTheBoundReader() {
      VSChartInfo info = info(TOTAL);
      VSAestheticRef color = new VSAestheticRef();
      color.setDataRef(new AttributeRef("Region"));
      info.setColorField(color);
      ChartDescriptor desc = hideAll(info);

      assertEquals("Region", ChartVisibilityState.hiddenLegends(desc, info, false)
         .hidden().get(0).field());

      // the frame of a bound colour carries a field, so the automatic reader never claims it
      ColorFrame bound = new CategoricalColorFrame();
      bound.setField("Region");
      GraphElement elem = new IntervalElement();
      elem.setColorFrame(bound);
      EGraph graph = new EGraph();
      graph.addElement(elem);

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(graph, info, desc, false, false));
   }

   @Test
   void aSparklineIsLeftAlone() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = hideAll(info);
      desc.setSparkline(true);

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(
         graph(desc, false, TOTAL, QUANTITY), info, desc, false, false));
   }

   @Test
   void noGraphMeansNoClaim() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = hideAll(info);

      assertNull(ChartVisibilityState.hiddenAutomaticColorLegend(null, info, desc, false, false));
   }

   /** The caller's graph is not touched: forcing the legend on happens in a clone. */
   @Test
   void theLaidOutGraphIsLeftUntouched() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      ChartDescriptor desc = hideAll(info);
      EGraph graph = graph(desc, false, TOTAL, QUANTITY);

      ChartVisibilityState.hiddenAutomaticColorLegend(graph, info, desc, false, false);

      assertFalse(graph.getElement(0).getColorFrame().getLegendSpec().isVisible());
   }

   // ── what the by-name hide and the index read say once it is known ─────────

   private static final ChartVisibilityState.HiddenLegends MEASURES =
      new ChartVisibilityState.HiddenLegends(
         List.of(new ChartVisibilityState.HiddenLegend("color", "Measures")));

   /** Item 4's already-hidden path accepts the Measures legend by channel and by title. */
   @Test
   void hidingTheHiddenMeasuresLegendByNameIsAlreadyHidden() {
      ChartRegionResolver.Legends none = new ChartRegionResolver.Legends(List.of(), true);

      for(String target : new String[] { "color", "Measures" }) {
         String note = ChartElementService.alreadyHidden(none, MEASURES, target);

         assertNotNull(note, target);
         assertTrue(note.contains("already hidden"), note);
      }

      // a name that is no legend at all keeps the old refusal
      assertNull(ChartElementService.alreadyHidden(none, MEASURES, "shape"));
   }

   /** The index read says hidden, not "no legends" (the vcc-030 read). */
   @Test
   void readingLegendZeroOfTheHiddenMeasuresLegendSaysHidden() {
      ChartRegionResolver.Legends none = new ChartRegionResolver.Legends(List.of(), true);

      IllegalArgumentException hidden = assertThrows(IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(none, 0, MEASURES));
      assertTrue(hidden.getMessage().contains("hidden"), hidden.getMessage());
      assertFalse(hidden.getMessage().contains("has no legends"), hidden.getMessage());

      // control: nothing hidden keeps the "no legends" wording
      IllegalArgumentException absent = assertThrows(IllegalArgumentException.class,
         () -> ChartRegionResolver.requireLegend(
            none, 0, ChartVisibilityState.HiddenLegends.NONE));
      assertTrue(absent.getMessage().contains("has no legends"), absent.getMessage());
   }

   /** Without a laid-out graph there is no evidence, so only the bound reading remains. */
   @Test
   void withoutALaidOutGraphOnlyTheBoundReadingRemains() {
      VSChartInfo info = info(TOTAL, QUANTITY);
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.setVSChartInfo(info);
      vs.addAssembly(chart);
      RuntimeViewsheet rvs = mock(RuntimeViewsheet.class);
      when(rvs.getViewsheet()).thenReturn(vs);
      when(rvs.getViewsheetSandbox()).thenReturn(java.util.Optional.empty());

      assertFalse(ChartRegionResolver.hiddenLegends(rvs, chart).any());
   }
}
