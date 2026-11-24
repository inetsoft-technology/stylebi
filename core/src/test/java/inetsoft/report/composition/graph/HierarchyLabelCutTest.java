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
import inetsoft.graph.element.TreemapElement;
import inetsoft.graph.visual.TreemapVO;
import inetsoft.graph.visual.VOText;
import inetsoft.report.internal.binding.BaseField;
import inetsoft.sree.SreeEnv;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import inetsoft.uql.VariableTable;
import inetsoft.uql.util.XSourceInfo;
import inetsoft.uql.viewsheet.graph.*;
import inetsoft.uql.viewsheet.graph.aesthetic.LinearSizeFrameWrapper;
import inetsoft.uql.viewsheet.internal.ChartVSAssemblyInfo;
import inetsoft.uql.viewsheet.internal.VizMark;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A marked treemap, circle packing or icicle cuts a cell label with "…" inside its cell, with no
 * length limit. A classic chart keeps today's "..".
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class HierarchyLabelCutTest {
   @AfterEach
   void reset() {
      SreeEnv.setProperty("viewsheet.modernVisualization", null);
      SreeEnv.setProperty("viewsheet.darkMode", null);
      SreeEnv.setProperty("viewsheet.density", null);
   }

   @Test
   void aMarkedChartTurnsTheMarkOn() {
      for(int type : TYPES) {
         assertTrue(element(type, VizMark.MODERN_LIGHT).isLabelEllipsis(), "type " + type);
         assertFalse(element(type, null).isLabelEllipsis(), "classic type " + type);
      }
   }

   @Test
   void aMarkedCellHasNoLengthLimit() {
      assertEquals(0, element(GraphTypes.CHART_TREEMAP, VizMark.MODERN_LIGHT).getLabelLimit());
   }

   @Test
   void aMarkedTreemapCutsInsideTheCell() {
      List<Cell> cells = cells(GraphTypes.CHART_TREEMAP, VizMark.MODERN_LIGHT, 240, 200);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         if(cell.cut()) {
            assertTrue(cell.text().endsWith("…"), cell.toString());
            assertFalse(cell.text().contains(".."), cell.toString());
         }

         assertInside(cell);
      }
   }

   @Test
   void aSmallMarkedTreemapCutsInsideTheCell() {
      List<Cell> cells = cells(GraphTypes.CHART_TREEMAP, VizMark.MODERN_LIGHT, 200, 150);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         assertTrue(!cell.cut() || cell.text().endsWith("…"), cell.toString());
         assertInside(cell);
      }
   }

   @Test
   void aClassicTreemapKeepsTwoDotsAndItsWidth() {
      List<Cell> cells = cells(GraphTypes.CHART_TREEMAP, null, 240, 200);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         if(cell.cut()) {
            assertTrue(cell.text().endsWith(".."), cell.toString());
            assertFalse(cell.text().contains("…"), cell.toString());
            // the label starts inside the cell and is as wide as the cell
            assertTrue(cell.label().getMaxX() > cell.box().getMaxX(), cell.toString());
         }
      }
   }

   @Test
   void aMarkedCirclePackingCutsWithTheEllipsis() {
      List<Cell> cells = cells(GraphTypes.CHART_CIRCLE_PACKING, VizMark.MODERN_LIGHT, 400, 240);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         assertTrue(!cell.cut() || cell.text().endsWith("…"), cell.toString());
         assertInside(cell);
      }
   }

   @Test
   void aClassicCirclePackingKeepsTwoDots() {
      List<Cell> cells = cells(GraphTypes.CHART_CIRCLE_PACKING, null, 400, 240);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         assertTrue(!cell.cut() || cell.text().endsWith(".."), cell.toString());
      }
   }

   @Test
   void aMarkedIcicleCutsInsideTheCell() {
      List<Cell> cells = cells(GraphTypes.CHART_ICICLE, VizMark.MODERN_LIGHT, 240, 240);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         assertTrue(!cell.cut() || cell.text().endsWith("…"), cell.toString());
         assertInside(cell);
      }
   }

   @Test
   void aClassicIcicleKeepsTwoDots() {
      List<Cell> cells = cells(GraphTypes.CHART_ICICLE, null, 240, 240);
      assertTrue(cells.stream().anyMatch(Cell::cut), "premise: a cut label " + cells);

      for(Cell cell : cells) {
         assertTrue(!cell.cut() || cell.text().endsWith(".."), cell.toString());
      }
   }

   @Test
   void aWideMarkedTreemapKeepsFullNames() {
      List<Cell> cells = cells(GraphTypes.CHART_TREEMAP, VizMark.MODERN_LIGHT, 1200, 400);

      assertEquals(6, cells.size(), "premise: six cells " + cells);
      cells.forEach(cell -> assertFalse(cell.cut(), cell.toString()));
   }

   private static void assertInside(Cell cell) {
      assertTrue(cell.label().getMinX() >= cell.box().getMinX() - 0.5, "label " + cell);
      assertTrue(cell.label().getMaxX() <= cell.box().getMaxX() + 0.5, "label " + cell);
      assertTrue(cell.drawn().getMaxX() <= cell.box().getMaxX() + 0.5, "text " + cell);
   }

   private record Cell(String name, String text, Rectangle2D box, Rectangle2D label,
                       Rectangle2D drawn)
   {
      boolean cut() {
         return !text.equals(name);
      }
   }

   private static final int[] TYPES = {
      GraphTypes.CHART_TREEMAP, GraphTypes.CHART_CIRCLE_PACKING, GraphTypes.CHART_ICICLE };

   // the shown leaf labels, with the cell and the label bounds
   private static List<Cell> cells(int chartType, VizMark mark, int width, int height) {
      GraphGenerator gen = generator(chartType, mark);
      EGraph graph = gen.createEGraph();
      VGraph vgraph = Plotter.getPlotter(graph).plotAndLayout(gen.getData(), 0, 0, width, height);
      List<Cell> cells = new ArrayList<>();

      for(int i = 0; i < vgraph.getVisualCount(); i++) {
         if(vgraph.getVisual(i) instanceof TreemapVO vo && vo.getVOText() != null &&
            vo.getVOText().getZIndex() >= 0 && vo.getVOText().getSize() != null)
         {
            VOText vtext = vo.getVOText();

            if(!NAMES.contains(vtext.getText())) {
               continue;
            }

            cells.add(new Cell(vtext.getText(), String.join(" ", vtext.getDisplayLabel()),
                               vo.getShapes()[0].getBounds2D(), vtext.getBounds(),
                               vtext.getTransformedBounds().getBounds2D()));
         }
      }

      return cells;
   }

   private static TreemapElement element(int chartType, VizMark mark) {
      EGraph graph = generator(chartType, mark).createEGraph();
      GraphElement elem = graph.getElement(0);

      assertInstanceOf(TreemapElement.class, elem);
      return (TreemapElement) elem;
   }

   // the names on the leaves, sized by Sum(HSales); the icicle adds the division above them
   private static GraphGenerator generator(int chartType, VizMark mark) {
      VSAestheticRef size = new VSAestheticRef();
      size.setDataRef(agg("HSales"));
      size.setVisualFrameWrapper(new LinearSizeFrameWrapper());

      VSChartInfo cinfo = new DefaultVSChartInfo();
      cinfo.setChartType(chartType);
      cinfo.setRTChartType(chartType);

      if(chartType == GraphTypes.CHART_ICICLE) {
         cinfo.addGroupField(dim("HDiv"));
      }

      cinfo.addGroupField(dim("HName"));
      cinfo.setSizeField(size);
      cinfo.updateChartType(false);

      ChartVSAssemblyInfo assemblyInfo = new ChartVSAssemblyInfo();
      assemblyInfo.setVSChartInfo(cinfo);
      assemblyInfo.setChartDescriptor(new ChartDescriptor());
      assemblyInfo.setVizMark(mark);

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

   private static final List<String> NAMES = List.of(
      "Accounts", "Field Services", "Customer Success Team", "Regional Distribution Partners",
      "Healthcare Provider Services", "Authorised Reseller Network");

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
