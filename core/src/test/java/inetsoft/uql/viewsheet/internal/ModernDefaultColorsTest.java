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

import inetsoft.graph.EGraph;
import inetsoft.graph.aesthetic.CategoricalColorFrame;
import inetsoft.graph.aesthetic.ColorValueColorFrame;
import inetsoft.graph.element.PointElement;
import inetsoft.report.composition.graph.ChartFormatDefaults;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.viewsheet.ChartVSAssembly;
import inetsoft.uql.viewsheet.Viewsheet;
import inetsoft.uql.viewsheet.graph.VSAestheticRef;
import inetsoft.uql.viewsheet.graph.VSChartDimensionRef;
import inetsoft.uql.viewsheet.graph.aesthetic.CategoricalColorFrameWrapper;
import inetsoft.uql.viewsheet.graph.aesthetic.VisualFrameWrapper;
import inetsoft.util.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Color;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Default colours that used to read the classic first slot or list directly follow the chart's
 * palette: the first-slot test a map conversion makes, the colour a script chart's combined frame
 * takes, and the fallback of a "use column values as colors" frame.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class ModernDefaultColorsTest {
   private static final Color LEGACY_1 = new Color(0x518DB9);
   private static final Color LEGACY_2 = new Color(0xB9DBF4);
   private static final Color MODERN_1 = new Color(0x0490FF);
   private static final Color MODERN_2 = new Color(0xFF5A35);
   private static final Color DARK_1 = new Color(0x4FA5FF);

   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      VSChartPaletteDefaults.clearMemo();
   }

   @Test
   void theFirstSlotOfEveryPaletteIsADefault() {
      assertTrue(VSChartPaletteDefaults.isFirstSlotColor(LEGACY_1));
      assertTrue(VSChartPaletteDefaults.isFirstSlotColor(MODERN_1));
      assertTrue(VSChartPaletteDefaults.isFirstSlotColor(DARK_1));
   }

   @Test
   void otherColoursAreNotADefault() {
      assertFalse(VSChartPaletteDefaults.isFirstSlotColor(LEGACY_2));
      assertFalse(VSChartPaletteDefaults.isFirstSlotColor(MODERN_2));
      assertFalse(VSChartPaletteDefaults.isFirstSlotColor(null));
   }

   @Test
   void aScriptChartTakesTheGivenPalette() {
      CategoricalColorFrame frame = combined(Arrays.asList(MODERN_1, MODERN_2));

      assertEquals(MODERN_1, frame.getColor(0));
      assertEquals(MODERN_2, frame.getColor(1));
   }

   @Test
   void aScriptChartWrapsTheGivenPalette() {
      CategoricalColorFrame frame = combined(Arrays.asList(MODERN_1));

      assertEquals(MODERN_1, frame.getColor(0));
      assertEquals(MODERN_1, frame.getColor(1));
   }

   @Test
   void aScriptChartWithoutAPaletteStaysClassic() {
      EGraph graph = twoElements();
      CategoricalColorFrame frame = (CategoricalColorFrame) GraphGenerator.createCombinedColorFrame(graph);

      assertEquals(LEGACY_1, frame.getColor(0));
      assertEquals(LEGACY_2, frame.getColor(1));
   }

   @Test
   void aModernColourValueFrameFallsBackToTheFirstModernColour() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      VSAestheticRef ref = bindColorValueFrame(info);

      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(MODERN_1, valueFrame(ref).getDefaultColor());

      info.setVizMark(VizMark.MODERN_DARK);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(DARK_1, valueFrame(ref).getDefaultColor());
   }

   @Test
   void revertingPutsTheClassicFallbackBack() {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      VSAestheticRef ref = bindColorValueFrame(info);
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));

      info.setVizMark(null);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(LEGACY_1, valueFrame(ref).getDefaultColor());
   }

   // the fallback frame is rebuilt from the saved XML with the classic default, and the seeding
   // above runs only on a transition, so a reopened modern chart must get it when it renders
   @Test
   void aReopenedModernColourValueFrameStillFallsBackToTheFirstModernColour() throws Exception {
      SreeEnv.setProperty("viewsheet.modernVisualization", "true");
      ChartVSAssemblyInfo info = newChart();
      VSAestheticRef saved = bindColorValueFrame(info);
      info.setVizMark(VizMark.MODERN_LIGHT);
      info.seedChromeDefaults(VizContext.of(info));
      assertEquals(MODERN_1, valueFrame(saved).getDefaultColor());

      ChartVSAssemblyInfo reopened = newChart();
      VSAestheticRef ref = bindOnAField(reopened, roundTrip(saved.getVisualFrameWrapper()));
      assertEquals(LEGACY_1, valueFrame(ref).getDefaultColor(),
                   "premise: the saved wrapper does not keep the fallback");

      reopened.setVizMark(VizMark.MODERN_LIGHT);
      ChartFormatDefaults.fix(reopened);
      assertEquals(MODERN_1, valueFrame(ref).getDefaultColor());

      reopened.setVizMark(VizMark.MODERN_DARK);
      ChartFormatDefaults.fix(reopened);
      assertEquals(DARK_1, valueFrame(ref).getDefaultColor());
   }

   @Test
   void aClassicColourValueFrameKeepsItsOwnFallbackWhenItRenders() {
      ChartVSAssemblyInfo info = newChart();
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setColorValueFrame(true);
      VSAestheticRef ref = bindOnAField(info, wrapper);
      info.setVizMark(null);

      ChartFormatDefaults.fix(info);
      assertEquals(LEGACY_1, valueFrame(ref).getDefaultColor());
   }

   private static CategoricalColorFrame combined(List<Color> palette) {
      return (CategoricalColorFrame) GraphGenerator.createCombinedColorFrame(twoElements(), palette);
   }

   private static EGraph twoElements() {
      EGraph graph = new EGraph();
      graph.addElement(new PointElement("a"));
      graph.addElement(new PointElement("b"));
      return graph;
   }

   // a chart field that the format pass can read (a plain AttributeRef is not a VSDataRef)
   private static VSAestheticRef bindOnAField(ChartVSAssemblyInfo info,
                                              CategoricalColorFrameWrapper wrapper)
   {
      VSAestheticRef ref = bindColorValueFrame(info, wrapper);
      ref.setDataRef(new VSChartDimensionRef(new BaseField("c")));
      return ref;
   }

   private static VSAestheticRef bindColorValueFrame(ChartVSAssemblyInfo info) {
      CategoricalColorFrameWrapper wrapper = new CategoricalColorFrameWrapper();
      wrapper.setColorValueFrame(true);
      return bindColorValueFrame(info, wrapper);
   }

   // the wrapper as it is after a save and a reopen
   private static CategoricalColorFrameWrapper roundTrip(VisualFrameWrapper wrapper)
      throws Exception
   {
      StringWriter xml = new StringWriter();
      PrintWriter writer = new PrintWriter(xml);
      wrapper.writeXML(writer);
      writer.flush();
      CategoricalColorFrameWrapper copy = new CategoricalColorFrameWrapper();
      copy.parseXML(Tool.parseXML(new StringReader(xml.toString())).getDocumentElement());
      return copy;
   }

   private static VSAestheticRef bindColorValueFrame(ChartVSAssemblyInfo info,
                                                     CategoricalColorFrameWrapper wrapper)
   {
      VSAestheticRef ref = new VSAestheticRef();
      ref.setDataRef(new AttributeRef(null, "c"));
      ref.setVisualFrameWrapper(wrapper);
      info.getVSChartInfo().setColorField(ref);
      return ref;
   }

   private static ColorValueColorFrame valueFrame(VSAestheticRef ref) {
      return (ColorValueColorFrame) ref.getVisualFrame();
   }

   private static ChartVSAssemblyInfo newChart() {
      Viewsheet vs = new Viewsheet();
      ChartVSAssembly chart = new ChartVSAssembly(vs, "Chart1");
      chart.getVSAssemblyInfo().initDefaultFormat();
      return (ChartVSAssemblyInfo) chart.getVSAssemblyInfo();
   }
}
