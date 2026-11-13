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
package inetsoft.graph.guide.legend;

import inetsoft.graph.EGraph;
import inetsoft.graph.LegendSpec;
import inetsoft.graph.Plotter;
import inetsoft.graph.aesthetic.LinearSizeFrame;
import inetsoft.graph.aesthetic.SizeFrame;
import inetsoft.graph.aesthetic.VisualFrame;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.composition.graph.GraphUtil;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RectangularShape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * The size legend's symbol colours follow the legend spec when it sets them.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SizeLegendSymbolTest {
   // the batik SVG support is not on the test classpath; seed a stub and restore the cache after
   private SVGSupport savedSvgSupport;

   @BeforeEach
   void stubSvgSupport() {
      savedSvgSupport = SVGSupport._CACHE[0];
      SVGSupport._CACHE[0] = mock(SVGSupport.class);
   }

   @AfterEach
   void restoreSvgSupport() {
      SVGSupport._CACHE[0] = savedSvgSupport;
   }

   @Test
   void anUnsetSpecKeepsTheOldColours() {
      LegendItem item = firstItem(new LegendSpec());
      assertEquals(new Color(0x759595), item.getSymbolColor());
      assertEquals(new Color(0xA3A3A3), boxEdge(item));
   }

   @Test
   void aSetFallbackColoursTheSymbol() {
      LegendSpec spec = new LegendSpec();
      spec.setSymbolFallbackColor(new Color(0x6A685F));
      assertEquals(new Color(0x6A685F), firstItem(spec).getSymbolColor());
   }

   @Test
   void aSetBorderDrawsTheBox() {
      LegendSpec spec = new LegendSpec();
      spec.setSymbolBorderColor(new Color(0xD6D2C8));
      assertEquals(new Color(0xD6D2C8), boxEdge(firstItem(spec)));
   }

   @Test
   void theNewColoursAreCloned() {
      LegendSpec spec = new LegendSpec();
      spec.setSymbolFallbackColor(Color.RED);
      spec.setSymbolBorderColor(Color.BLUE);
      LegendSpec copy = spec.clone();
      assertEquals(Color.RED, copy.getSymbolFallbackColor());
      assertEquals(Color.BLUE, copy.getSymbolBorderColor());
      assertEquals(spec, copy);
      copy.setSymbolBorderColor(Color.GREEN);
      assertNotEquals(spec, copy);
   }

   @Test
   void aModernSizeBoundIntervalDrawsFullSymbols() {
      EGraph graph = modernSizeBoundInterval();
      SizeFrame frame = graph.getElement(0).getSizeFrame();
      assertEquals(frame.getLargest(), frame.getSmallest(), "premise: no size range");

      for(Drawn drawn : innerBars(frame, graph)) {
         Rectangle2D bar = drawn.bar();
         assertTrue(Double.isFinite(bar.getX()) && Double.isFinite(bar.getWidth()),
                    "symbol " + bar);
         assertEquals(drawn.item().getSymbolSize(), bar.getWidth(), 1e-9, "a full symbol");
      }
   }

   @Test
   void aSizeRangeDrawsAsBefore() {
      LinearSizeFrame frame = new LinearSizeFrame("Total");
      frame.init(new DefaultDataSet(new Object[][]{ { "Total" }, { 1.0 }, { 5.0 }, { 10.0 } }));
      List<Drawn> drawn = innerBars(frame, new EGraph());
      assertTrue(drawn.size() > 1, "premise: several sizes");

      for(Drawn d : drawn) {
         int sz = d.item().getSymbolSize();
         double ratio = (frame.getSize(d.item().getValue()) - frame.getSmallest()) /
            (frame.getLargest() - frame.getSmallest());
         double r = Math.min(1 + (sz - 1) * ratio, sz);

         assertEquals(r, d.bar().getWidth(), 1e-9, "width of " + d.item().getValue());
         assertEquals(X + sz / 2.0 - r / 2 + 0.5, d.bar().getX(), 1e-9, "x");
      }
   }

   // a modern interval chart with a measure on Size, as GraphGenerator builds it
   private static EGraph modernSizeBoundInterval() {
      VSChartDimensionRef state = new VSChartDimensionRef(new BaseField("State"));
      state.setGroupColumnValue("State");
      VSChartAggregateRef total = new VSChartAggregateRef();
      total.setDataRef(new BaseField("Total"));
      total.setFormulaValue("Sum");
      VSChartAggregateRef qty = new VSChartAggregateRef();
      qty.setDataRef(new BaseField("Qty"));
      qty.setFormulaValue("Sum");
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(qty);
      size.setRTDataRef(qty);
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());
      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(GraphTypes.CHART_INTERVAL);
      cinfo.setRTChartType(GraphTypes.CHART_INTERVAL);
      cinfo.addXField(state);
      cinfo.addYField(total);
      cinfo.setSizeField(size);
      cinfo.updateChartType(false);
      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(VizMark.MODERN_LIGHT);
      DefaultDataSet data = new DefaultDataSet(new Object[][] {
         { "State", GraphUtil.getName(total), GraphUtil.getName(qty) },
         { "CA", 200.0, 3.0 }, { "NY", 50.0, 7.0 }, { "TX", 1200.0, 11.0 } });
      GraphGenerator gen = GraphGenerator.getGenerator(
         assemblyInfo, null, data, new VariableTable(), null, XSourceInfo.NONE, null);
      EGraph graph = gen.createEGraph();
      // lays out the graph, which initializes its frames
      Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 240);
      return graph;
   }

   // paint each item's symbol of a size legend and keep the inner bar it draws
   private static List<Drawn> innerBars(SizeFrame frame, EGraph graph) {
      SizeLegend legend = new SizeLegend(frame, graph) {
         @Override
         protected LegendItem createLegendItem(Object label, Object value) {
            return new RecordingItem(label, value, getVisualFrame());
         }
      };
      List<Drawn> drawn = new ArrayList<>();
      BufferedImage img = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof RecordingItem item) {
            Graphics2D g = img.createGraphics();

            try {
               item.rects.clear();
               item.paintSymbol(g, X, X);
            }
            finally {
               g.dispose();
            }

            drawn.add(new Drawn(item, item.rects.get(0)));
         }
      }

      assertFalse(drawn.isEmpty(), "premise: the legend has items");
      return drawn;
   }

   private record Drawn(LegendItem item, Rectangle2D bar) {
   }

   // a size legend item that keeps the rects its symbol draws, the inner bar first
   private static class RecordingItem extends SizeLegendItem {
      RecordingItem(Object label, Object value, VisualFrame frame) {
         super(label, value, frame);
      }

      @Override
      protected RectangularShape createSymbolRect(double x, double y, double w, double h,
                                                  boolean allowRound)
      {
         rects.add(new Rectangle2D.Double(x, y, w, h));
         return super.createSymbolRect(x, y, w, h, allowRound);
      }

      private final List<Rectangle2D> rects = new ArrayList<>();
   }

   private static LegendItem firstItem(LegendSpec spec) {
      DefaultDataSet data = new DefaultDataSet(new Object[][]{
         { "Total" }, { 1.0 }, { 5.0 }, { 10.0 } });
      LinearSizeFrame frame = new LinearSizeFrame("Total");
      frame.init(data);
      frame.setLegendSpec(spec);
      SizeLegend legend = new SizeLegend(frame, new EGraph());
      LegendItem last = null;

      for(int i = 0; i < legend.getVisualCount(); i++) {
         if(legend.getVisual(i) instanceof LegendItem) {
            last = (LegendItem) legend.getVisual(i);
         }
      }

      assertNotNull(last, "premise: the legend has items");
      return last;
   }

   // the colour of the box's left edge, painted at the symbol's origin
   private static Color boxEdge(LegendItem item) {
      BufferedImage img = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();

      try {
         item.paintSymbol(g, 10, 10);
      }
      finally {
         g.dispose();
      }

      int size = item.getSymbolSize();
      return new Color(img.getRGB(10, 10 + size / 2), true);
   }

   private static final double X = 10;
}
