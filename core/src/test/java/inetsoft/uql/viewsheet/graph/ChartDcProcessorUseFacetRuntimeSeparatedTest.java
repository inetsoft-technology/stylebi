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
package inetsoft.uql.viewsheet.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.coord.Coordinate;
import inetsoft.graph.coord.FacetCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.graph.DefaultGraphGenerator;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.composition.graph.SeparateGraphGenerator;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.XConstants;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInfo;
import inetsoft.uql.viewsheet.internal.DateComparisonInterval;
import inetsoft.uql.viewsheet.internal.StandardPeriods;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DCG-007 (bug 76522/76849): {@code ChartDcProcessor.updateDateComparisonChartType()} hardcoded
 * {@code vsChartInfo.setRuntimeSeparated(false)} regardless of {@code dcInfo.isUseFacet()}, so
 * {@code useFacet:true} could never route rendering through {@code SeparateGraphGenerator}'s real
 * {@code FacetCoord} panel-splitting mechanism -- the flag was read two lines later for an
 * unrelated Line-vs-Point marker choice, but never used to drive {@code runtimeSeparated}.
 *
 * <p>The first test drives the real production entry point ({@code updateDateComparisonChartType})
 * and confirms {@code VSChartInfo.isSeparatedGraph()} now follows {@code useFacet} for a value-plus
 * comparison. That alone only proves a boolean flag flips, so the second test goes further: it
 * feeds the resulting {@code VSChartInfo} through the real {@code GraphGenerator.getGenerator()}
 * dispatch and {@code createEGraph()}, and inspects the actual returned {@link Coordinate} --
 * confirming {@code useFacet:true} genuinely produces a {@link FacetCoord} wrapping one
 * sub-coordinate per measure, and {@code useFacet:false} produces a plain, unwrapped coordinate
 * through {@link DefaultGraphGenerator}.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class ChartDcProcessorUseFacetRuntimeSeparatedTest {
   @Test
   void useFacetTrueSetsRuntimeSeparatedForAValuePlusComparison() {
      VSChartInfo info = chartInfo();
      DateComparisonInfo dcInfo = dcInfo(DateComparisonInfo.CHANGE_VALUE, true);

      new ChartDcProcessor(info, dcInfo).updateDateComparisonChartType(info);

      assertTrue(info.isSeparatedGraph(),
                 "useFacet:true on a value-plus comparison must drive runtimeSeparated -- the " +
                 "field that gates SeparateGraphGenerator/FacetCoord -- instead of the old " +
                 "hardcoded false");
   }

   @Test
   void useFacetFalseLeavesRuntimeSeparatedOffForAValuePlusComparison() {
      VSChartInfo info = chartInfo();
      DateComparisonInfo dcInfo = dcInfo(DateComparisonInfo.CHANGE_VALUE, false);

      new ChartDcProcessor(info, dcInfo).updateDateComparisonChartType(info);

      assertFalse(info.isSeparatedGraph(),
                  "useFacet:false must still resolve to unseparated rendering, not accidentally " +
                  "flip to separated");
   }

   /**
    * Goes past the boolean: runs the real graph-generation dispatch/coordinate-building code
    * (not a hand-built Coordinate) and checks the concrete generator type and coordinate shape.
    */
   @Test
   void useFacetTrueProducesARealFacetCoordViaSeparateGraphGenerator() {
      VSChartInfo info = chartInfo();
      DateComparisonInfo dcInfo = dcInfo(DateComparisonInfo.CHANGE_VALUE, true);
      new ChartDcProcessor(info, dcInfo).updateDateComparisonChartType(info);

      GraphGenerator gen = generatorFor(info, dcInfo);
      assertInstanceOf(SeparateGraphGenerator.class, gen,
                        "runtimeSeparated=true must dispatch to SeparateGraphGenerator, the " +
                        "generator that produces FacetCoord");

      EGraph egraph = gen.createEGraph();
      Coordinate coord = egraph.getCoordinate();
      assertInstanceOf(FacetCoord.class, coord,
                        "SeparateGraphGenerator with two bound measures must genuinely wrap them " +
                        "in a FacetCoord, not just report isSeparatedGraph()=true");
      FacetCoord facet = (FacetCoord) coord;
      assertEquals(2, facet.getInnerCoordinates().length,
                   "one sub-coordinate per bound measure (Sales, Profit)");
   }

   @Test
   void useFacetFalseProducesAPlainCoordinateViaDefaultGraphGenerator() {
      VSChartInfo info = chartInfo();
      DateComparisonInfo dcInfo = dcInfo(DateComparisonInfo.CHANGE_VALUE, false);
      new ChartDcProcessor(info, dcInfo).updateDateComparisonChartType(info);

      GraphGenerator gen = generatorFor(info, dcInfo);
      assertInstanceOf(DefaultGraphGenerator.class, gen,
                        "runtimeSeparated=false must dispatch to DefaultGraphGenerator, not the " +
                        "facet-producing SeparateGraphGenerator");

      EGraph egraph = gen.createEGraph();
      assertFalse(egraph.getCoordinate() instanceof FacetCoord,
                  "without useFacet, rendering must stay a single, unwrapped coordinate");
   }

   /**
    * Uses the {@code ChartVSAssemblyInfo} dispatch overload (the one real rendering actually
    * calls), not the plainer {@code ChartInfo} overload -- {@code isAppliedDateComparison()} is
    * true (needed so {@code isSeparatedGraph()} consults {@code runtimeSeparated} instead of the
    * unrelated default), and that in turn makes {@code fixCoordProperties()} require a real
    * {@code DateComparisonInfo} wired to the assembly info, which only this overload's
    * constructor supplies.
    */
   private static GraphGenerator generatorFor(VSChartInfo info, DateComparisonInfo dcInfo) {
      DataSet data = new DefaultDataSet(new Object[][] {
         { "CATEGORY", "Sum(Sales)", "Sum(Profit)" },
         { "A", 10.0, 5.0 },
         { "B", 20.0, 15.0 },
      });

      ChartVSAssemblyInfo chart = new ChartVSAssemblyInfo();
      chart.setVSChartInfo(info);
      chart.setDateComparisonEnabled(true);
      chart.setDateComparisonInfo(dcInfo);

      return GraphGenerator.getGenerator(chart, null, data, new VariableTable(), null, 0,
                                          new Dimension(400, 300));
   }

   private static VSChartInfo chartInfo() {
      VSChartInfo info = new VSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);

      VSChartDimensionRef category = new VSChartDimensionRef(new AttributeRef("CATEGORY"));

      VSChartAggregateRef sales = new VSChartAggregateRef();
      sales.setDataRef(new AttributeRef("Sales"));
      sales.setFormula(AggregateFormula.SUM);

      VSChartAggregateRef profit = new VSChartAggregateRef();
      profit.setDataRef(new AttributeRef("Profit"));
      profit.setFormula(AggregateFormula.SUM);

      info.setRTXFields(new ChartRef[]{ category });
      info.setRTYFields(new ChartRef[]{ sales, profit });
      // isSeparatedGraph() only consults runtimeSeparated when this is non-empty -- otherwise it
      // falls back to AbstractChartInfo's own "separated" default (true), which would mask
      // whether useFacet's wiring actually did anything.
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ category });

      return info;
   }

   /**
    * A minimally-mocked, but real-logic (CALLS_REAL_METHODS), {@code DateComparisonInfo}: the
    * period/interval objects are mocked because their real getters route through
    * {@code DynamicValue}, which needs a full Spring context unavailable here -- the same
    * technique {@code DateComparisonServiceTest.dcInfo()} uses.
    */
   private static DateComparisonInfo dcInfo(int comparisonOption, boolean useFacet) {
      StandardPeriods periods = mock(StandardPeriods.class);
      when(periods.getDateLevel()).thenReturn(XConstants.YEAR_DATE_GROUP);

      DateComparisonInterval interval = mock(DateComparisonInterval.class);
      when(interval.getGranularity()).thenReturn(DateComparisonInfo.YEAR);
      // satisfies invalid()'s "!isCompareAll() && !isEndDayAsToDate() && getIntervalEndDate()
      // == null" branch without needing a real end date.
      when(interval.isEndDayAsToDate()).thenReturn(true);

      DateComparisonInfo dcInfo = mock(DateComparisonInfo.class, CALLS_REAL_METHODS);
      dcInfo.setDateComparisonPeriods(periods);
      dcInfo.setDateComparisonInterval(interval);
      dcInfo.setComparisonOption(comparisonOption);
      dcInfo.setUseFacet(useFacet);
      return dcInfo;
   }
}
