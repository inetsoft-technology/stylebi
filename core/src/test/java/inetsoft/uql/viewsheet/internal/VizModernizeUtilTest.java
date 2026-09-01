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
package inetsoft.uql.viewsheet.internal;

import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.uql.viewsheet.graph.aesthetic.SharedFrameParameters;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.asset.Assembly;
import inetsoft.uql.viewsheet.*;
import inetsoft.uql.viewsheet.graph.ChartRef;
import inetsoft.uql.viewsheet.graph.GraphTypes;
import inetsoft.uql.viewsheet.graph.PlotDescriptor;
import inetsoft.uql.viewsheet.graph.VSChartAggregateRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.VSChartInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SreeHome
@Tag("core")
class VizModernizeUtilTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
   }

   private void gateOn() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
   }

   private void gateOff() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "false");
   }

   /** A sheet as a legacy asset would load: nothing marked anywhere. */
   private Viewsheet legacySheet() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));
      return vs;
   }

   /**
    * A legacy Area chart as one actually exists: created unmarked, then switched to Area, which is
    * what turns smoothing on - the chart-type transition does it, and the wizard does it for a
    * recommended Area chart.
    */
   private Viewsheet legacyAreaChartSheet() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      chart.getVSChartInfo().setChartType(GraphTypes.CHART_AREA);
      chart.getChartDescriptor().getPlotDescriptor().setSmoothLines(true);
      vs.addAssembly(chart);
      return vs;
   }

   @Test
   void aLegacySheetHasUnmarkedContent() {
      assertTrue(VizModernizeUtil.hasUnmarked(legacySheet()));
   }

   @Test
   void aFullyModernSheetHasNone() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      assertFalse(VizModernizeUtil.hasUnmarked(vs));
   }

   @Test
   void modernizeStampsEveryUnmarkedAssemblyAndTheSheet() {
      Viewsheet vs = legacySheet();
      gateOn();

      assertEquals(3, VizModernizeUtil.modernize(vs), "two assemblies plus the sheet itself");

      assertEquals(VizMark.MODERN_LIGHT, vs.getVSAssemblyInfo().getVizMark());

      for(Assembly assembly : vs.getAssemblies(true)) {
         assertEquals(VizMark.MODERN_LIGHT,
                      ((VSAssembly) assembly).getVSAssemblyInfo().getVizMark());
      }

      assertFalse(VizModernizeUtil.hasUnmarked(vs), "and the sheet is no longer modernizable");
   }

   @Test
   void modernizeAlsoAppliesTheSeeds() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      assertEquals(0, table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getRoundCornerValue(), "legacy: square");

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void modernizeIsANoOpWhenTheGateIsOff() {
      Viewsheet vs = legacySheet();
      gateOff();

      assertEquals(0, VizModernizeUtil.modernize(vs));
      assertNull(vs.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void modernizeIsIdempotent() {
      Viewsheet vs = legacySheet();
      gateOn();

      assertEquals(3, VizModernizeUtil.modernize(vs));
      assertEquals(0, VizModernizeUtil.modernize(vs), "a second run touches nothing");
   }

   @Test
   void modernizeLeavesMarkedSiblingsAlone() {
      // the mixed dashboard: an already-modern assembly keeps the mark it has. A brand
      // new assembly inherits its host's mark (absence included - see AbstractVSAssembly), so a
      // sibling already carrying a mark is simulated directly, standing in for one modernized (or
      // created) in an earlier dark-mode pass.
      Viewsheet vs = legacySheet();
      TextVSAssembly dark = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(dark);
      dark.getVSAssemblyInfo().setVizMark(VizMark.MODERN_DARK);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_DARK, dark.getVSAssemblyInfo().getVizMark(),
                   "an already-marked assembly is not re-stamped");
      assertEquals(VizMark.MODERN_LIGHT,
                   vs.getAssembly("Text1").getVSAssemblyInfo().getVizMark());
   }

   @Test
   void modernizeNeverTouchesAuthorProvenanceFlags() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setTitleHeightValue(44);
      info.setUserTitleHeight(true);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(info.isUserTitleHeight(), "userTitleHeight records an author's choice, not a mark");
      assertEquals(44, info.getTitleHeightValue(), "and the height they chose survives");
   }

   @Test
   void modernizeLeavesATypeThatInstallsItsOwnFormatAlone() {
      // Text builds its own object format and hardcodes the legacy border, so it never took the base
      // chrome at creation and must not take it from Modernize either
      Viewsheet vs = legacySheet();
      TextVSAssembly text = new TextVSAssembly(vs, "TextA");
      vs.addAssembly(text);
      // a real creation flow calls initDefaultFormat() after adding the assembly; without it
      // Text's own object format never installs a border to begin with
      text.getVSAssemblyInfo().initDefaultFormat();
      text.getVSAssemblyInfo().setVizMark(null);
      Color before = text.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getBorderColorsValue().topColor;

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_LIGHT, text.getVSAssemblyInfo().getVizMark(),
                   "it is still stamped - the mark records provenance for every assembly");
      assertEquals(before, text.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getBorderColorsValue().topColor, "but its chrome is untouched");
   }

   @Test
   void modernizeTakesTheSelectionListTitleRuleColour() {
      Viewsheet vs = legacySheet();
      SelectionListVSAssembly list = new SelectionListVSAssembly(vs, "SelA");
      vs.addAssembly(list);
      // a real creation flow calls initDefaultFormat() after adding the assembly; without it the
      // list never installs its own TITLEPATH composite to begin with
      list.getVSAssemblyInfo().initDefaultFormat();
      list.getVSAssemblyInfo().setVizMark(null);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VSTitleChromeDefaults.titleBorderColor(VizContext.ofGate()),
                   list.getVSAssemblyInfo().getFormatInfo()
                      .getFormat(VSAssemblyInfo.TITLEPATH).getDefaultFormat()
                      .getBorderColorsValue().bottomColor,
                   "a selection list installs its own title composite, but the hook re-runs " +
                   "against it, so modernize reaches it through the public entry point too");
   }

   @Test
   void modernizeGivesATableTheModernObjectChrome() {
      Viewsheet vs = legacySheet();
      TableVSAssembly table = (TableVSAssembly) vs.getAssembly("Table1");

      gateOn();
      VizModernizeUtil.modernize(vs);

      VizContext ctx = VizContext.ofGate();
      assertEquals(VSObjectChromeDefaults.objectBorderColor(ctx),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat()
                      .getBorderColorsValue().topColor);
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue());
   }

   @Test
   void modernizeReachesAnEmbeddedViewsheetContainerButNotItsChildren() {
      Viewsheet vs = legacySheet();
      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setVizMark(null);
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      TextVSAssembly inner = new TextVSAssembly(embedded, "InnerText");
      embedded.addAssembly(inner);
      inner.getVSAssemblyInfo().setVizMark(null);
      vs.addAssembly(embedded);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertEquals(VizMark.MODERN_LIGHT, embedded.getVSAssemblyInfo().getVizMark(),
                   "the container is this sheet's content");
      assertNull(inner.getVSAssemblyInfo().getVizMark(),
                 "its children are the embedded asset's content and stay untouched");
   }

   @Test
   void aSheetWhoseOnlyUnmarkedItemIsAnEmbeddedContainerHasUnmarkedContent() {
      // this is the case getAssemblies(true) drops entirely - it flattens the container away and
      // yields only its (already-marked, out-of-scope) children - so hasUnmarked() must reach the
      // container through the direct-children pass, or the composer would never offer the action
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      vs.addAssembly(embedded);
      embedded.getVSAssemblyInfo().setVizMark(null);

      assertTrue(VizModernizeUtil.hasUnmarked(vs),
                 "the container is the sheet's only unmarked item");
   }

   @Test
   void aLegacySheetHasNothingToRevert() {
      assertFalse(VizModernizeUtil.hasMarked(legacySheet()));
   }

   @Test
   void aModernSheetHasSomethingToRevert() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      assertTrue(VizModernizeUtil.hasMarked(vs));
   }

   @Test
   void revertClearsEveryMarkIncludingTheSheetsOwn() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      vs.addAssembly(new TableVSAssembly(vs, "Table1"));

      assertEquals(3, VizModernizeUtil.revert(vs), "two assemblies plus the sheet itself");

      assertNull(vs.getVSAssemblyInfo().getVizMark());

      for(Assembly assembly : vs.getAssemblies(true)) {
         assertNull(((VSAssembly) assembly).getVSAssemblyInfo().getVizMark());
      }

      assertFalse(VizModernizeUtil.hasMarked(vs), "and there is nothing left to revert");
   }

   @Test
   void revertAlsoUnSeedsTheChrome() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      table.getVSAssemblyInfo().initDefaultFormat();
      assertEquals(VSObjectChromeDefaults.cardCornerRadius(),
                   table.getVSAssemblyInfo().getFormat().getDefaultFormat().getRoundCornerValue(),
                   "modern: rounded");

      VizModernizeUtil.revert(vs);

      assertEquals(0, table.getVSAssemblyInfo().getFormat().getDefaultFormat()
         .getRoundCornerValue(), "legacy: square");
   }

   @Test
   void revertIsOfferedAndWorksWithTheGateOff() {
      // no gate floor, unlike modernize(): reverting is offered under both gate states on purpose
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      gateOff();

      assertTrue(VizModernizeUtil.hasMarked(vs));
      assertEquals(2, VizModernizeUtil.revert(vs));
      assertNull(vs.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void revertIsIdempotent() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));

      assertEquals(2, VizModernizeUtil.revert(vs));
      assertEquals(0, VizModernizeUtil.revert(vs), "a second run touches nothing");
   }

   @Test
   void revertLeavesUnmarkedSiblingsAlone() {
      // the mixed dashboard, backwards: reverting touches only what carries a mark
      Viewsheet vs = legacySheet();
      gateOn();
      TextVSAssembly modern = new TextVSAssembly(vs, "Text2");
      vs.addAssembly(modern);
      modern.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);

      assertEquals(1, VizModernizeUtil.revert(vs), "only the marked sibling");
      assertNull(modern.getVSAssemblyInfo().getVizMark());
   }

   @Test
   void revertReachesAnEmbeddedViewsheetContainerButNotItsChildren() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      Viewsheet embedded = new Viewsheet();
      embedded.getVSAssemblyInfo().setName("EmbeddedVS");
      embedded.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      TextVSAssembly inner = new TextVSAssembly(embedded, "InnerText");
      embedded.addAssembly(inner);
      inner.getVSAssemblyInfo().setVizMark(VizMark.MODERN_LIGHT);
      vs.addAssembly(embedded);

      VizModernizeUtil.revert(vs);

      assertNull(embedded.getVSAssemblyInfo().getVizMark(),
                 "the container is this sheet's content");
      assertEquals(VizMark.MODERN_LIGHT, inner.getVSAssemblyInfo().getVizMark(),
                   "its children are the embedded asset's content and stay untouched");
   }

   @Test
   void revertNeverTouchesAuthorProvenanceFlags() {
      gateOn();
      Viewsheet vs = new Viewsheet();
      TableVSAssembly table = new TableVSAssembly(vs, "Table1");
      vs.addAssembly(table);
      TableVSAssemblyInfo info = (TableVSAssemblyInfo) table.getVSAssemblyInfo();
      info.setTitleHeightValue(44);
      info.setUserTitleHeight(true);

      VizModernizeUtil.revert(vs);

      assertTrue(info.isUserTitleHeight(), "userTitleHeight records an author's choice, not a mark");
      assertEquals(44, info.getTitleHeightValue(), "and the height they chose survives");
   }

   @Test
   void aModernizedThenRevertedSheetMatchesOneNeverModernized() {
      // the phase's headline property, and the reason reversal is routed through the
      // creation path: there is no separate reverser whose behaviour could drift
      Viewsheet reverted = legacySheet();
      TableVSAssembly a = (TableVSAssembly) reverted.getAssembly("Table1");
      a.getVSAssemblyInfo().initDefaultFormat();
      gateOn();
      VizModernizeUtil.modernize(reverted);
      VizModernizeUtil.revert(reverted);

      Viewsheet untouched = legacySheet();
      TableVSAssembly b = (TableVSAssembly) untouched.getAssembly("Table1");
      b.getVSAssemblyInfo().initDefaultFormat();

      VSFormat af = a.getVSAssemblyInfo().getFormat().getDefaultFormat();
      VSFormat bf = b.getVSAssemblyInfo().getFormat().getDefaultFormat();
      assertEquals(bf.getRoundCornerValue(), af.getRoundCornerValue());
      assertEquals(bf.getBorderColorsValue().topColor, af.getBorderColorsValue().topColor);
      assertEquals(bf.getBackgroundValue(), af.getBackgroundValue());
   }

   @Test
   void aModernizedThenRevertedAreaChartMatchesOneNeverModernized() {
      // the table case above cannot see a seed whose legacy value depends on the chart type.
      // smoothLines is on for every legacy Area chart, so writing a constant false on the legacy
      // branch would leave the reverted chart straight-lined and the untouched one smooth.
      Viewsheet reverted = legacyAreaChartSheet();
      ChartVSAssembly a = (ChartVSAssembly) reverted.getAssembly("Chart1");
      gateOn();
      VizModernizeUtil.modernize(reverted);
      VizModernizeUtil.revert(reverted);

      Viewsheet untouched = legacyAreaChartSheet();
      ChartVSAssembly b = (ChartVSAssembly) untouched.getAssembly("Chart1");

      PlotDescriptor ap = a.getChartDescriptor().getPlotDescriptor();
      PlotDescriptor bp = b.getChartDescriptor().getPlotDescriptor();
      assertTrue(bp.isSmoothLines(), "a legacy Area chart is smooth; guards a false pass on false");
      assertEquals(bp.isSmoothLines(), ap.isSmoothLines());
      assertEquals(bp.getBarCornerRadius(), ap.getBarCornerRadius(), 0.0001);
   }

   /**
    * A design multi-styles chart whose overall type is not a smooth-lines default, but one of its
    * per-measure aggregates is. Multi-styles here is a design choice, not one forced by date
    * comparison.
    */
   private ChartVSAssembly multiStyleChartWithAggregate(Viewsheet vs, int aggregateChartType) {
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      VSChartInfo info = chart.getVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setMultiStyles(true);
      VSChartAggregateRef yRef = new VSChartAggregateRef();
      yRef.setChartType(aggregateChartType);
      info.addYField(yRef);
      vs.addAssembly(chart);
      return chart;
   }

   @Test
   void revertOnDesignMultiStylesChartWithSmoothAggregateLeavesSmoothLinesOn() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = multiStyleChartWithAggregate(vs, GraphTypes.CHART_AREA);

      gateOn();
      VizModernizeUtil.modernize(vs);
      VizModernizeUtil.revert(vs);

      assertTrue(chart.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                 "the overall type is Bar, but a design multi-styles Area aggregate still " +
                 "carries the smooth-lines default");
   }

   @Test
   void revertOnDesignMultiStylesChartWithNoSmoothAggregateTurnsSmoothLinesOff() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = multiStyleChartWithAggregate(vs, GraphTypes.CHART_BAR);

      gateOn();
      VizModernizeUtil.modernize(vs);
      VizModernizeUtil.revert(vs);

      assertFalse(chart.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                  "no aggregate carries a smooth-lines default, so the legacy value is off");
   }

   @Test
   void revertIgnoresDcRuntimeMultiStylesWhenDesignIsSingleStyle() {
      // date comparison can force a design single-style chart into runtime multi-styles.
      // legacySmoothLines must read the design answer, not the DC runtime one, or a chart
      // nobody made multi-styles would have its aggregates scanned anyway.
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      VSChartInfo info = chart.getVSChartInfo();
      info.setChartType(GraphTypes.CHART_BAR);
      info.setMultiStyles(false);

      // an Area aggregate that would flip smoothLines on if the aggregate scan ran
      VSChartAggregateRef yRef = new VSChartAggregateRef();
      yRef.setChartType(GraphTypes.CHART_AREA);
      info.addYField(yRef);

      // date comparison forces runtime multi-styles on a chart that is single-style by design
      info.setRuntimeDateComparisonRefs(new ChartRef[]{ new VSChartDimensionRef() });
      info.setRuntimeMulti(true);
      vs.addAssembly(chart);

      gateOn();
      VizModernizeUtil.modernize(vs);
      VizModernizeUtil.revert(vs);

      assertFalse(chart.getChartDescriptor().getPlotDescriptor().isSmoothLines(),
                  "design is single-style Bar; the DC-forced runtime multi-styles must not " +
                  "trigger the aggregate scan");
   }

   // ---- the sheet's shared colour frames outrank an assembly's own ---------------------------

   /**
    * A render clones the sheet's shared frame in preference to the assembly's own, and the cache
    * is not rebuilt just because a palette changed - so seeding a new palette without dropping the
    * cache leaves the old colours rendering. Every other path that mutates colour state clears it.
    */
   @Test
   void revertDropsTheSheetsSharedColorFrames() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);

      gateOn();
      VizModernizeUtil.modernize(vs);
      seedSharedFrame(vs);

      VizModernizeUtil.revert(vs);

      assertTrue(vs.getSharedFrames().isEmpty(),
                 "revert must drop the shared frames, or the modern palette keeps rendering");
   }

   @Test
   void modernizeDropsTheSheetsSharedColorFrames() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);
      seedSharedFrame(vs);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(vs.getSharedFrames().isEmpty(),
                 "modernize must drop them too, or the legacy palette keeps rendering");
   }

   @Test
   void aNoOpLeavesTheSharedFramesAlone() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      seedSharedFrame(vs);

      assertEquals(0, VizModernizeUtil.revert(vs), "precondition: nothing is marked");
      assertFalse(vs.getSharedFrames().isEmpty(),
                  "a revert that touched nothing must not invalidate another render's cache");
   }

   /**
    * The per-value colors are a second, separate runtime store: a render reloads them into the
    * color frame context and writes each back onto the frame, so a stale entry outranks the newly
    * seeded palette for the rest of the session even though nothing is persisted.
    */
   @Test
   void revertDropsTheSheetsPerValueDimensionColors() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);

      gateOn();
      VizModernizeUtil.modernize(vs);
      seedDimensionColors(vs);

      VizModernizeUtil.revert(vs);

      assertTrue(vs.getDimensionColors("Category").isEmpty(),
                 "revert must drop the per-value colors, or the modern ones keep being re-applied");
   }

   @Test
   void modernizeDropsTheSheetsPerValueDimensionColors() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      vs.addAssembly(chart);
      seedDimensionColors(vs);

      gateOn();
      VizModernizeUtil.modernize(vs);

      assertTrue(vs.getDimensionColors("Category").isEmpty(), "modernize must drop them too");
   }

   @Test
   void aNoOpLeavesThePerValueDimensionColorsAlone() {
      gateOff();
      Viewsheet vs = new Viewsheet();
      vs.addAssembly(new TextVSAssembly(vs, "Text1"));
      seedDimensionColors(vs);

      assertEquals(0, VizModernizeUtil.revert(vs), "precondition: nothing is marked");
      assertFalse(vs.getDimensionColors("Category").isEmpty(),
                  "a revert that touched nothing must not discard another chart's colors");
   }

   /** Per-value colors as a modern render leaves behind on the sheet. */
   private void seedDimensionColors(Viewsheet vs) {
      Map<String, Color> colors = new HashMap<>();
      colors.put("Business", new Color(0x00D4E8));
      vs.setDimensionColors("Category", colors);
   }

   /** A shared frame as a render leaves behind, keyed the way CategoricalColorFrameContext keys it. */
   private void seedSharedFrame(Viewsheet vs) {
      SharedFrameParameters params = new SharedFrameParameters();
      params.addParameter("Category");
      vs.getSharedFrames().put(params, new CategoricalColorFrame());
   }
}
