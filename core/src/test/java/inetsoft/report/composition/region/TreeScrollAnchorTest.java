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
package inetsoft.report.composition.region;

import inetsoft.graph.EGraph;
import inetsoft.graph.Plotter;
import inetsoft.graph.VGraph;
import inetsoft.graph.aesthetic.StaticSizeFrame;
import inetsoft.graph.coord.RelationCoord;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.element.RelationElement;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.visual.RelationVO;
import inetsoft.report.composition.graph.GraphGenerator;
import inetsoft.report.internal.Region;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Where a fitted tree asks the browser to scroll when it is first drawn.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class TreeScrollAnchorTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void theAnchorIsTheRootsCentre() {
      Plot plot = plot(bigTree(), GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, null, 376, 190);
      List<Rectangle> roots = plot.rootBounds();

      assertEquals(1, roots.size(), "premise: one root");
      Point2D anchor = plot.anchor();
      assertEquals(roots.get(0).getCenterX(), anchor.getX(), 0.5);
      assertEquals(roots.get(0).getCenterY(), anchor.getY(), 0.5);
   }

   @Test
   void theAnchorFollowsTheOrientation() {
      assertRootSide(PlotDescriptor.TREE_LAYOUT_TOP_BOTTOM, false, 1);
      assertRootSide(PlotDescriptor.TREE_LAYOUT_BOTTOM_TOP, false, -1);
      assertRootSide(PlotDescriptor.TREE_LAYOUT_LEFT_RIGHT, true, 1);
      assertRootSide(PlotDescriptor.TREE_LAYOUT_RIGHT_LEFT, true, -1);
   }

   // every other node lies on the far side of the anchor, in the direction the tree grows
   private static void assertRootSide(String layout, boolean horizontal, int sign) {
      Plot plot = plot(bigTree(), GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, layout, 376, 190);
      Point2D anchor = plot.anchor();
      Rectangle plotBounds = plot.plotBounds();
      int others = 0;

      for(Rectangle node : plot.nodeBounds(false)) {
         double offset = horizontal ? node.getCenterX() - anchor.getX()
            : node.getCenterY() - anchor.getY();
         assertTrue(sign * offset > 0, layout + ": " + node + " vs " + anchor);
         others++;
      }

      assertEquals(60, others, "premise: every other node");
      assertTrue(anchor.getX() >= plotBounds.getMinX() && anchor.getX() <= plotBounds.getMaxX() &&
                 anchor.getY() >= plotBounds.getMinY() && anchor.getY() <= plotBounds.getMaxY(),
                 layout + ": the anchor lies in the plot " + anchor);
   }

   @Test
   void aForestFitsAndCentresItsRoots() {
      Object[][] rows = {
         { "Parent", "Child" },
         { "R1", "A1" }, { "R1", "A2" }, { "R1", "A3" },
         { "R2", "B1" }, { "R2", "B2" }, { "R2", "B3" } };
      Plot plot = plot(rows, GraphTypes.CHART_TREE, VizMark.MODERN_LIGHT, null, 376, 190);
      List<Rectangle> roots = plot.rootBounds();

      assertEquals(2, roots.size(), "premise: two roots");
      Rectangle2D union = roots.get(0).createUnion(roots.get(1));
      Point2D anchor = plot.anchor();
      assertEquals(union.getCenterX(), anchor.getX(), 0.5);
      assertEquals(union.getCenterY(), anchor.getY(), 0.5);
      assertTrue(union.getMinX() >= plot.plotBounds().getMinX() - 0.5 &&
                 union.getMaxX() <= plot.plotBounds().getMaxX() + 0.5,
                 "both roots lie in the plot width");
   }

   @Test
   void onlyAFittedTreeHasAnAnchor() {
      assertNull(plot(bigTree(), GraphTypes.CHART_TREE, null, null, 376, 190)
                    .area().getScrollAnchor(), "classic tree");
      assertNull(plot(bigTree(), GraphTypes.CHART_NETWORK, VizMark.MODERN_LIGHT, null, 376, 190)
                    .area().getScrollAnchor(), "marked network");
      assertNull(plot(bigTree(), GraphTypes.CHART_CIRCULAR, VizMark.MODERN_LIGHT, null, 376, 190)
                    .area().getScrollAnchor(), "marked circle");
   }

   @Test
   void rootsAreTheSourceOnlyNodes() {
      assertEquals(List.of("R"), rootNames(new Object[][] {
         { "From", "To" }, { "R", "A" }, { "R", "B" }, { "A", "C" } }));
      assertEquals(List.of("R1", "R2"), rootNames(new Object[][] {
         { "From", "To" }, { "R1", "A" }, { "R2", "B" }, { "A", "C" } }));
   }

   private static List<String> rootNames(Object[][] rows) {
      RelationElement elem = new RelationElement("From", "To");
      elem.setAlgorithm(RelationElement.Algorithm.COMPACT_TREE);
      elem.setSizeFrame(new StaticSizeFrame(1));
      elem.setNodeSizeFrame(new StaticSizeFrame(1));
      EGraph egraph = new EGraph();
      egraph.addElement(elem);
      egraph.setCoordinate(new RelationCoord());
      VGraph vgraph = Plotter.getPlotter(egraph)
         .plotAndLayout(new DefaultDataSet(rows), 0, 0, 376, 190);
      List<String> names = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof RelationVO vo &&
            vo.getGeometry() instanceof RelationGeometry geo && geo.isRoot())
         {
            names.add(String.valueOf(geo.getMxCell().getValue()));
         }
      }

      names.sort(null);
      return names;
   }

   // R with 6 children of 9 leaves each, 61 nodes
   private static Object[][] bigTree() {
      List<Object[]> rows = new ArrayList<>();
      rows.add(new Object[] { "Parent", "Child" });

      for(int i = 1; i <= 6; i++) {
         rows.add(new Object[] { "R", "N" + i });

         for(int j = 1; j <= 9; j++) {
            rows.add(new Object[] { "N" + i, "L" + i + "_" + j });
         }
      }

      return rows.toArray(new Object[0][]);
   }

   private static Plot plot(Object[][] rows, int chartType, VizMark mark, String layout,
                            int width, int height)
   {
      VSChartDimensionRef source = dim("Parent");
      VSChartDimensionRef target = dim("Child");
      RelationVSChartInfo cinfo = new RelationVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);
      cinfo.setSourceField(source);
      cinfo.setTargetField(target);
      cinfo.setRTSourceField(source);
      cinfo.setRTTargetField(target);
      cinfo.updateChartType(false);

      ChartDescriptor desc = new ChartDescriptor();

      if(layout != null) {
         desc.getPlotDescriptor().setTreeLayout(layout);
      }

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(desc);
      assemblyInfo.setVizMark(mark);
      GraphGenerator gen = GraphGenerator.getGenerator(
         assemblyInfo, null, new DefaultDataSet(rows), new VariableTable(), null,
         XSourceInfo.NONE, null);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, width, height);
      PlotArea area = new ChartArea(vgraph, graph, gen.getData(), null, cinfo).getPlotArea();
      return new Plot(area);
   }

   private static VSChartDimensionRef dim(String name) {
      VSChartDimensionRef dim = new VSChartDimensionRef(new BaseField(name));
      dim.setGroupColumnValue(name);
      return dim;
   }

   private record Plot(PlotArea area) {
      Point2D anchor() {
         Point2D anchor = area.getScrollAnchor();
         assertNotNull(anchor, "premise: an anchor");
         return anchor;
      }

      Rectangle plotBounds() {
         return new Rectangle(0, 0, area.getRegion().getBounds().width,
                              area.getRegion().getBounds().height);
      }

      // the regions of each root node's area, one box per root
      List<Rectangle> rootBounds() {
         return nodeBounds(true);
      }

      List<Rectangle> nodeBounds(boolean roots) {
         List<Rectangle> boxes = new ArrayList<>();

         for(DefaultArea child : area.getOriginalAreas()) {
            if(child.getVisualizable() instanceof RelationVO vo &&
               vo.getGeometry() instanceof RelationGeometry geo && geo.isRoot() == roots)
            {
               Rectangle box = null;

               for(Region region : child.getRegions()) {
                  box = box == null ? region.getBounds() : box.union(region.getBounds());
               }

               boxes.add(box);
            }
         }

         return boxes;
      }
   }
}
