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
package inetsoft.report.composition.graph;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.GShape;
import inetsoft.graph.aesthetic.StaticShapeFrame;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.element.PolygonElement;
import inetsoft.graph.geo.GeoShapeFrame;
import inetsoft.graph.internal.GDefaults;
import inetsoft.graph.visual.PolygonVO;
import inetsoft.report.internal.Graphics2DWrapper;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.erm.AttributeRef;
import inetsoft.uql.schema.XSchema;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.TealColorFrameWrapper;
import inetsoft.uql.viewsheet.internal.*;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * A marked map outlines its regions in the card colour; classic and web maps keep the default
 * line, and a server border colour or a scripted line colour still draws.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class MapRegionOutlineTest {
   // the batik SVG support is not on the test classpath; seed a stub and restore the cache after
   private SVGSupport savedSvgSupport;

   @BeforeEach
   void stubSvgSupport() {
      savedSvgSupport = SVGSupport._CACHE[0];
      SVGSupport._CACHE[0] = mock(SVGSupport.class);
   }

   @AfterEach
   void reset() {
      SVGSupport._CACHE[0] = savedSvgSupport;
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
      SreeEnv.setProperty("map.border.color", null);
      SreeEnv.setProperty("webmap.service", null);
      SreeEnv.setProperty("mapbox.user", null);
      SreeEnv.setProperty("mapbox.token", null);
      SreeEnv.setProperty("mapbox.style", null);
   }

   @Test
   void aDarkMarkOutlinesRegionsInTheDarkCard() {
      assertEquals(DARK_CARD, VSObjectChromeDefaults.cardComposite(
         VizContext.of(VizMark.MODERN_DARK)), "premise: the dark card composite");
      assertEquals(DARK_CARD, regionFrame(graph(VizMark.MODERN_DARK, false, false)).getLineColor());
   }

   @Test
   void aLightMarkOutlinesRegionsInTheLightCard() {
      assertEquals(LIGHT_CARD, VSObjectChromeDefaults.cardComposite(
         VizContext.of(VizMark.MODERN_LIGHT)), "premise: the light card composite");
      assertEquals(LIGHT_CARD,
                   regionFrame(graph(VizMark.MODERN_LIGHT, false, false)).getLineColor());
   }

   @Test
   void aClassicMapKeepsTheDefaultLine() {
      assertEquals(new Color(0xEEEEEE), GDefaults.DEFAULT_LINE_COLOR, "premise: the default");
      assertEquals(GDefaults.DEFAULT_LINE_COLOR,
                   regionFrame(graph(null, false, false)).getLineColor());
      assertEquals(GDefaults.DEFAULT_LINE_COLOR,
                   regionFrame(graph(null, true, false)).getLineColor());
   }

   @Test
   void aClassicMapDrawsTheDefaultLineLast() {
      Drawn graph = graph(null, false, false);

      assertEquals(List.of(GDefaults.DEFAULT_LINE_COLOR), lastStrokes(graph));
   }

   @Test
   void aWebMapKeepsTheDefaultLine() {
      SreeEnv.setProperty("webmap.service", "mapbox");
      SreeEnv.setProperty("mapbox.user", "user");
      SreeEnv.setProperty("mapbox.token", "token");
      SreeEnv.setProperty("mapbox.style", "style");
      ChartVSAssemblyInfo info = chart(VizMark.MODERN_DARK, false, true);
      // a drawn map has cleared the facet flag, which starts true
      info.getVSChartInfo().setFacet(false);

      assertTrue(MapGenerator.isWebMap(info.getVSChartInfo(), info.getChartDescriptor(), null),
                 "premise: a web map");
      assertEquals(GDefaults.DEFAULT_LINE_COLOR, regionFrame(generate(info)).getLineColor());
   }

   @Test
   void everyRegionDrawsTheCardColourLast() {
      Drawn graph = graph(VizMark.MODERN_DARK, false, false);
      List<Color> last = new ArrayList<>();

      assertEquals(List.of(DARK_CARD), distinct(lastStrokes(graph, last)));
      assertTrue(last.size() > ROWS.length - 1, "premise: unmatched regions draw too");
   }

   @Test
   void aRampMapOutlinesEveryRegionInTheCardColour() {
      Drawn graph = graph(VizMark.MODERN_DARK, true, false);

      assertEquals(DARK_CARD, regionFrame(graph).getLineColor());
      assertEquals(List.of(DARK_CARD), lastStrokes(graph));
   }

   @Test
   void aServerBorderColourStillDrawsLast() {
      SreeEnv.setProperty("map.border.color", "#FF0000");
      Drawn graph = graph(VizMark.MODERN_DARK, false, false);

      assertEquals(DARK_CARD, regionFrame(graph).getLineColor());
      assertEquals(List.of(Color.RED), lastStrokes(graph));
   }

   @Test
   void aLineColourSetOnTheGeneratedGraphStillDraws() {
      // a chart script edits the graph the generator built
      Drawn graph = graph(VizMark.MODERN_DARK, false, false);
      regionFrame(graph).setLineColor(Color.RED);

      assertEquals(List.of(Color.RED), lastStrokes(graph));
   }

   private static Drawn graph(VizMark mark, boolean ramp, boolean webMap) {
      return generate(chart(mark, ramp, webMap));
   }

   private static Drawn generate(ChartVSAssemblyInfo info) {
      GraphGenerator gen = GraphGenerator.getGenerator(
         info, null, new DefaultDataSet(ROWS), new VariableTable(), null, XSourceInfo.NONE, null);
      EGraph graph = gen.createEGraph();
      return new Drawn(graph, gen.getData());
   }

   // a generated graph and the data it plots
   private record Drawn(EGraph graph, DataSet data) {
   }

   // a U.S. state map, its regions highlighted or coloured by a measure
   private static ChartVSAssemblyInfo chart(VizMark mark, boolean ramp, boolean webMap) {
      VSMapInfo map = new VSMapInfo();
      map.setMeasureMapType("U.S.");
      VSChartGeoRef geo = new VSChartGeoRef(new AttributeRef(null, "State"));
      geo.setGroupColumnValue("State");
      geo.setDataType(XSchema.STRING);
      geo.getGeographicOption().setLayerValue("2");
      map.addGeoField(geo);
      map.addRTGeoField(geo);
      ((StaticShapeFrame) map.getShapeFrame()).setShape(GShape.NIL);
      map.setChartType(GraphTypes.CHART_MAP);

      if(ramp) {
         VSChartAggregateRef var = new VSChartAggregateRef();
         var.setDataRef(new BaseField("Var"));
         var.setFormulaValue("Sum");
         VSAestheticRef color = new VSAestheticRef();
         color.setDataRef(var);
         color.setRTDataRef(var);
         color.setVisualFrameWrapper(new TealColorFrameWrapper());
         map.setColorField(color);
      }

      ChartDescriptor desc = new ChartDescriptor();
      desc.getPlotDescriptor().setWebMap(webMap);
      ChartVSAssemblyInfo info = new ChartVSAssemblyInfo();
      info.setVSChartInfo(map);
      info.setChartDescriptor(desc);
      info.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(info);
      return info;
   }

   private static GeoShapeFrame regionFrame(Drawn drawn) {
      EGraph graph = drawn.graph();

      for(int i = 0; i < graph.getElementCount(); i++) {
         GraphElement elem = graph.getElement(i);

         if(elem instanceof PolygonElement && elem.getShapeFrame() instanceof GeoShapeFrame) {
            return (GeoShapeFrame) elem.getShapeFrame();
         }
      }

      return fail("premise: the map draws a region element");
   }

   // the distinct last stroke colours of the regions
   private static List<Color> lastStrokes(Drawn drawn) {
      return distinct(lastStrokes(drawn, new ArrayList<>()));
   }

   // the colour each region strokes last, painted as a plotted graph
   private static List<Color> lastStrokes(Drawn drawn, List<Color> last) {
      VGraph vgraph = Plotter.getPlotter(drawn.graph()).plotAndLayout(drawn.data(), 0, 0, 400,
                                                                      300);
      BufferedImage img = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof PolygonVO vo) {
            List<Color> strokes = new ArrayList<>();
            vo.paint(new Recording(g, strokes));
            assertFalse(strokes.isEmpty(), "premise: the region strokes its outline");
            last.add(strokes.get(strokes.size() - 1));
         }
      }

      g.dispose();
      assertFalse(last.isEmpty(), "premise: regions draw");
      return last;
   }

   private static List<Color> distinct(List<Color> colors) {
      return colors.stream().distinct().toList();
   }

   // records the colour of every stroke, through every copy it makes
   private static final class Recording extends Graphics2DWrapper {
      Recording(Graphics2D g, List<Color> strokes) {
         super(g, true);
         this.strokes = strokes;
      }

      @Override
      public Graphics create() {
         return new Recording((Graphics2D) g.create(), strokes);
      }

      @Override
      public void draw(Shape s) {
         strokes.add(getColor());
         super.draw(s);
      }

      private final List<Color> strokes;
   }

   private static final Object[][] ROWS = {
      { "State", "Sum(Var)" }, { "California", 10.0 }, { "New York", 50.0 }, { "Texas", 90.0 } };
   private static final Color DARK_CARD = new Color(0x313034);
   private static final Color LIGHT_CARD = new Color(0xECEBE9);
}
