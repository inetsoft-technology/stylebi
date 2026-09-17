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
package inetsoft.web.vswizard.recommender.chart;

import inetsoft.graph.aesthetic.BluesColorFrame;
import inetsoft.graph.aesthetic.TealColorFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.ColumnSelection;
import inetsoft.uql.asset.AggregateFormula;
import inetsoft.uql.asset.AssetEntry;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.VizContext;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.web.vswizard.handler.VSWizardBindingHandler;
import inetsoft.web.vswizard.model.VSWizardConstants;
import inetsoft.web.vswizard.model.VSWizardData;
import inetsoft.web.vswizard.model.recommender.VSTemporaryInfo;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The object wizard's own seed path. The wizard builds its temp chart from the real runtime
 * viewsheet, so the assembly carries the host's mark; the recommender then hands every
 * recommendation its colour frame. Three links are covered here: the handler reading the mark off
 * the assembly, ChartCombinationUtil handing the resulting context to every filter it builds, and
 * the filters seeding through it.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class }, initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class WizardSeededLinearFrameTest {
   // no collaborator of VSWizardBindingHandler is touched by getTempChartContext, so nulls are
   // safe here
   private static final VSWizardBindingHandler HANDLER = new VSWizardBindingHandler(
      null, null, null, null, null, null, null, null, null, null, null, null, null);

   /**
    * The link the recommender used to drop: getTempChart hands back the info, which carries no
    * mark, so the context has to come off the assembly.
    */
   @Test
   void theHandlerReadsTheHostMarkOffTheTempChart() {
      assertTrue(contextOf(VizMark.MODERN_LIGHT).modern, "a marked temp chart reads modern");
      assertFalse(contextOf(VizMark.MODERN_LIGHT).dark, "a light mark is not dark");
      assertTrue(contextOf(VizMark.MODERN_DARK).dark, "a dark mark reads dark");
      assertFalse(contextOf(null).modern, "an unmarked temp chart reads legacy");
   }

   @Test
   void noTempChartReadsLegacy() {
      assertSame(VizContext.LEGACY, HANDLER.getTempChartContext(null));
      assertSame(VizContext.LEGACY, HANDLER.getTempChartContext(new VSWizardData()));
      assertSame(VizContext.LEGACY,
                 HANDLER.getTempChartContext(new VSWizardData(new AssetEntry[0],
                                                              new VSTemporaryInfo())));
   }

   /**
    * The handoff itself. Every filter in the recommender's list has to carry the context, not
    * just the ones this test seeds through below - a filter that misses it seeds the pre-modern
    * ramp with nothing to show for it.
    */
   @Test
   void everyFilterTheRecommenderBuildsTakesTheContext() {
      VizContext ctx = contextOf(VizMark.MODERN_LIGHT);
      VSChartInfo temp = tempInfo(0, 2);
      List<ChartTypeFilter> filters = ChartCombinationUtil.createFilters(
         new AssetEntry[0], temp, List.of(), new ColumnSelection(), false, ctx);

      assertFalse(filters.isEmpty(), "no filters built - the assertion would be vacuous");
      filters.forEach(filter -> assertSame(ctx, filter.getVizContext(), filter.getClass().getName()));
   }

   /** A filter nobody sets keeps pre-slice behaviour rather than something new. */
   @Test
   void aFilterNobodySetsKeepsTheLegacyDefault() {
      ChartTypeFilter filter =
         new ScatterChartFilter(new AssetEntry[0], tempInfo(0, 4), List.of(), false);

      assertSame(VizContext.LEGACY, filter.getVizContext());
      filter.setVizContext(null);
      assertSame(VizContext.LEGACY, filter.getVizContext(), "a null context falls back to legacy");
   }

   /** ScatterChartFilter's own addInsideField. */
   @Test
   void aModernMarkedScatterIsBornOnTeal() {
      assertInstanceOf(TealColorFrame.class, scatterColorFrame(VizMark.MODERN_LIGHT));
   }

   @Test
   void aModernDarkMarkedScatterIsBornOnTeal() {
      assertInstanceOf(TealColorFrame.class, scatterColorFrame(VizMark.MODERN_DARK));
   }

   @Test
   void anUnmarkedScatterIsBornOnBlues() {
      assertInstanceOf(BluesColorFrame.class, scatterColorFrame(null));
   }

   /** ChartTypeFilter's shared addInsideField, which most filters inherit. */
   @Test
   void aModernMarkedWordCloudIsBornOnTeal() {
      assertInstanceOf(TealColorFrame.class, wordCloudColorFrame(VizMark.MODERN_LIGHT));
   }

   @Test
   void anUnmarkedWordCloudIsBornOnBlues() {
      assertInstanceOf(BluesColorFrame.class, wordCloudColorFrame(null));
   }

   /** The two direct seeds, which write the info's own colour frame rather than an aesthetic. */
   @Test
   void aModernMarkedContourScatterIsBornOnTeal() {
      assertInstanceOf(TealColorFrame.class, contourScatterColorFrame(VizMark.MODERN_LIGHT));
   }

   @Test
   void anUnmarkedContourScatterIsBornOnBlues() {
      assertInstanceOf(BluesColorFrame.class, contourScatterColorFrame(null));
   }

   @Test
   void aModernMarkedContourMapIsBornOnTeal() {
      assertInstanceOf(TealColorFrame.class, contourMapColorFrame(VizMark.MODERN_LIGHT));
   }

   @Test
   void anUnmarkedContourMapIsBornOnBlues() {
      assertInstanceOf(BluesColorFrame.class, contourMapColorFrame(null));
   }

   /** x and y each take a measure, the last two land on size and colour. */
   private VisualFrame scatterColorFrame(VizMark mark) {
      VSChartInfo temp = tempInfo(0, 4);
      ScatterChartFilter filter =
         new ScatterChartFilter(new AssetEntry[0], temp, List.of(), false);
      filter.setVizContext(contextOf(mark));

      VSChartInfo info = filter.createChartInfo(combination(of(0), of(1), of(2, 3)));
      assertNotNull(info, "the filter rejected the fixture");

      return measureColorFrame(info);
   }

   /** the dimension lands on text, the first measure on size, the second on colour. */
   private VisualFrame wordCloudColorFrame(VizMark mark) {
      VSChartInfo temp = tempInfo(1, 2);
      WordCloudFilter filter = new WordCloudFilter(new AssetEntry[0], temp, List.of(), false);
      filter.setVizContext(contextOf(mark));

      VSChartInfo info = filter.createChartInfo(combination(of(), of(), of(0, 1, 2)));
      assertNotNull(info, "the filter rejected the fixture");

      return measureColorFrame(info);
   }

   private VisualFrame contourScatterColorFrame(VizMark mark) {
      VSChartInfo temp = tempInfo(0, 2);
      ContourScatterChartFilter filter =
         new ContourScatterChartFilter(new AssetEntry[0], temp, List.of(), false);
      filter.setVizContext(contextOf(mark));

      VSChartInfo info = filter.createChartInfo(combination(of(0), of(1), of()));
      assertNotNull(info, "the filter rejected the fixture");
      assertEquals(GraphTypes.CHART_SCATTER_CONTOUR, info.getChartType());

      return info.getColorFrame();
   }

   private VisualFrame contourMapColorFrame(VizMark mark) {
      VSChartInfo temp = tempInfo(0, 2);
      ContourMapChartFilter filter = new ContourMapChartFilter(
         new AssetEntry[0], temp, new ColumnSelection(), List.of(), false);
      filter.setVizContext(contextOf(mark));

      VSChartInfo info = filter.createChartInfo(combination(of(), of(0), of(1)));
      assertNotNull(info, "the filter rejected the fixture");
      assertEquals(GraphTypes.CHART_MAP_CONTOUR, info.getChartType());

      return info.getColorFrame();
   }

   /**
    * Reads the seeded frame, refusing a fixture whose colour binding drifted off a measure - the
    * linear seed only fires for one, so a dimension there would make the assertion vacuous.
    */
   private VisualFrame measureColorFrame(VSChartInfo info) {
      AestheticRef ref = info.getColorField();
      assertNotNull(ref, "nothing bound to colour - nothing was seeded");
      assertInstanceOf(ChartAggregateRef.class, ref.getDataRef(),
                       "colour must carry a measure or the linear seed is never reached");

      return ref.getVisualFrame();
   }

   /**
    * The context a wizard temp chart stamped with the given mark resolves to, read the way the
    * recommender reads it.
    */
   private VizContext contextOf(VizMark mark) {
      Viewsheet vs = new Viewsheet();
      vs.getVSAssemblyInfo().setVizMark(mark);

      // the wizard creates its temp chart exactly this way, which is what stamps the host's mark
      ChartVSAssembly tempChart = new ChartVSAssembly(vs, VSWizardConstants.TEMP_CHART_NAME);
      assertEquals(mark, tempChart.getVSAssemblyInfo().getVizMark(),
                   "temp chart must inherit the host mark or the fixture proves nothing");

      VSTemporaryInfo temporaryInfo = new VSTemporaryInfo();
      temporaryInfo.setTempChart(tempChart);

      return HANDLER.getTempChartContext(new VSWizardData(new AssetEntry[0], temporaryInfo));
   }

   /** x holds the dimensions and y the measures, which is how the wizard fills its temp chart. */
   private VSChartInfo tempInfo(int dimensions, int measures) {
      VSChartInfo temp = new VSChartInfo();

      for(int i = 0; i < dimensions; i++) {
         VSChartDimensionRef dim = new VSChartDimensionRef();
         dim.setGroupColumnValue("D" + i);
         dim.setDataType(XSchema.STRING);
         temp.addXField(dim);
      }

      for(int i = 0; i < measures; i++) {
         VSChartAggregateRef aggr = new VSChartAggregateRef();
         aggr.setColumnValue("M" + i);
         aggr.setFormula(AggregateFormula.SUM);
         temp.addYField(aggr);
      }

      return temp;
   }

   private ChartRefCombination combination(IntList x, IntList y, IntList inside) {
      return new ChartRefCombination(x, y, inside);
   }

   private IntList of(int... indexes) {
      IntList list = new IntArrayList();

      for(int index : indexes) {
         list.add(index);
      }

      return list;
   }
}
