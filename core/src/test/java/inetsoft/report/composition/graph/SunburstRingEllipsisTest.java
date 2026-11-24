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
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.GraphElement;
import inetsoft.graph.internal.GTool;
import inetsoft.graph.visual.CircularVOText;
import inetsoft.graph.visual.TreemapVO;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.LibManagerTestConfiguration;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import inetsoft.uql.viewsheet.internal.VizModernizeUtil;
import inetsoft.util.graphics.SVGSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * A marked sunburst cuts a ring label too long for its segment and ends it with an ellipsis;
 * a classic sunburst clips it as before.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = { BaseTestConfiguration.class, LibManagerTestConfiguration.class },
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class SunburstRingEllipsisTest {
   // Batik is not on the core test classpath, but GTool.drawString asks SVGSupport whether
   // the graphics is SVG. Seed a stub (not SVG) and restore the cache after.
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
   }

   @Test
   void aMarkedSunburstAsksForTheEllipsis() {
      assertEquals("true", element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_LIGHT)
         .getHint(CircularVOText.HINT_ELLIPSIS));
      assertEquals("true", element(GraphTypes.CHART_SUNBURST, VizMark.MODERN_DARK)
         .getHint(CircularVOText.HINT_ELLIPSIS));
   }

   @Test
   void aClassicSunburstDoesNotAskForTheEllipsis() {
      assertNull(element(GraphTypes.CHART_SUNBURST, null).getHint(CircularVOText.HINT_ELLIPSIS));
   }

   @Test
   void aMarkedIcicleDoesNotAskForTheEllipsis() {
      assertNull(element(GraphTypes.CHART_ICICLE, VizMark.MODERN_LIGHT)
                    .getHint(CircularVOText.HINT_ELLIPSIS));
   }

   @Test
   void aMarkedSunburstCutsItsLongRingLabelsWithTheEllipsis() {
      Map<String, String> drawn = drawnLabels(VizMark.MODERN_LIGHT);

      assertFalse(drawn.isEmpty(), "premise: the rings draw labels");
      assertTrue(drawn.values().stream().anyMatch(s -> s.endsWith(ELLIPSIS)),
                 "a long label is cut: " + drawn);

      for(Map.Entry<String, String> e : drawn.entrySet()) {
         String s = e.getValue();
         String head = s.endsWith(ELLIPSIS) ? s.substring(0, s.length() - 1) : s;

         assertTrue(s.equals(e.getKey()) || !head.isEmpty() && e.getKey().startsWith(head),
                    "the label or a head of it with the ellipsis: " + drawn);
      }
   }

   @Test
   void aClassicSunburstDrawsItsRadialLabelsWholeWithNoMark() {
      Map<String, String> drawn = drawnLabels(null);

      assertFalse(drawn.isEmpty(), "premise: the rings draw labels");
      assertTrue(drawn.values().stream().noneMatch(s -> s.contains(ELLIPSIS)),
                 "no mark: " + drawn);
      assertTrue(drawn.entrySet().stream().anyMatch(e -> e.getKey().equals(e.getValue()) &&
                                                         e.getKey().length() > 20),
                 "a long radial label is drawn whole and left to the ring clip: " + drawn);
   }

   // the label text of each ring segment, and what it draws
   private static Map<String, String> drawnLabels(VizMark mark) {
      GraphGenerator gen = generator(GraphTypes.CHART_SUNBURST, mark);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, 400, 250);
      Map<String, String> drawn = new LinkedHashMap<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof TreemapVO vo &&
            vo.getVOText() instanceof CircularVOText text)
         {
            drawn.put(text.getText(), paint(text));
         }
      }

      return drawn;
   }

   // the characters the label draws
   private static String paint(CircularVOText text) {
      BufferedImage img = new BufferedImage(400, 250, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = img.createGraphics();
      StringBuilder drawn = new StringBuilder();
      Answer<Object> record = inv -> {
         if("drawString".equals(inv.getMethod().getName())) {
            drawn.append((String) inv.getArgument(1));
         }

         return inv.callRealMethod();
      };

      try(MockedStatic<GTool> ignored = mockStatic(GTool.class, record)) {
         text.paint(g);
      }
      finally {
         g.dispose();
      }

      return drawn.toString();
   }

   private static GraphElement element(int chartType, VizMark mark) {
      EGraph graph = generator(chartType, mark).createEGraph();
      return graph.getElement(0);
   }

   // HDiv then HName in the tree, sized by Sum(HSales)
   private static GraphGenerator generator(int chartType, VizMark mark) {
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(agg("HSales"));
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.addGroupField(dim("HDiv"));
      cinfo.addGroupField(dim("HName"));
      cinfo.setSizeField(size);
      cinfo.updateChartType(false);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);
      VizModernizeUtil.reseedAfterRestore(assemblyInfo);

      return GraphGenerator.getGenerator(assemblyInfo, null, new DefaultDataSet(ROWS),
                                         new VariableTable(), null, XSourceInfo.NONE, null);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private static VSChartAggregateRef agg(String name) {
      VSChartAggregateRef agg = new VSChartAggregateRef();
      agg.setDataRef(new BaseField(name));
      agg.setFormulaValue("Sum");
      return agg;
   }

   private static final String ELLIPSIS = "…";
   private static final Object[][] ROWS = {
      { "HDiv", "HName", "Sum(HSales)" },
      { "Core", "Accounts", 30.0 },
      { "Core", "Field Services", 28.0 },
      { "Core", "Customer Success Team", 26.0 },
      { "Partner", "Regional Distribution Partners", 24.0 },
      { "Partner", "Healthcare Provider Services", 22.0 },
      { "Partner", "Authorised Reseller Network", 20.0 },
   };
}
