/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
package inetsoft.graph.element;

import inetsoft.graph.EGraph;
import inetsoft.graph.GGraph;
import inetsoft.graph.aesthetic.*;
import inetsoft.graph.coord.RectCoord;
import inetsoft.graph.data.DataSet;
import inetsoft.graph.data.DefaultDataSet;
import inetsoft.graph.geometry.RelationGeometry;
import inetsoft.graph.scale.CategoricalScale;
import inetsoft.graph.scale.LinearScale;
import inetsoft.report.composition.graph.BrushDataSet;
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #78569: a brushed relation (tree/network/circular) chart with an
 * aggregate edge size is drawn with two elements over one {@link BrushDataSet} that holds
 * the brushed rows first and the all-data rows after them. The all-data element reads its
 * node size and label from the {@code __all__} columns, which are null on the brushed rows,
 * and the brush element reads its edge size from the base measures, which are null on the
 * all-data rows. A leaf's representative row ({@link RelationGeometry#getSubRowIndex()})
 * must be a row the element draws the leaf from, i.e. a row whose size is not null.
 * Otherwise a brushed leaf in the all-data element is drawn at the minimum size without a
 * label (#76417 regression), and a brush leaf reads its tooltip from an all-data row.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationElementBrushRepresentativeRowTest {
   private static final String ALL = BrushDataSet.ALL_HEADER_PREFIX;
   private static final String[] CATEGORIES = { "Games", "Hardware", "Personal" };

   // every category has a USA East and a USA West parent, so USA East is the first displayed
   // parent of every leaf
   private static final Object[][] ALL_ROWS = {
      { "Region", "Category", "Qty", "Price" },
      { "USA East", "Games", 10, 100 },
      { "USA East", "Hardware", 20, 5000 },
      { "USA East", "Personal", 5, 80 },
      { "USA West", "Games", 4, 50 },
      { "USA West", "Hardware", 6, 3000 },
      { "USA West", "Personal", 3, 20 },
   };

   // brushing one USA East state on another chart
   private static final Object[][] EAST_BRUSHED_ROWS = {
      { "Region", "Category", "Qty", "Price" },
      { "USA East", "Games", 1, 10 },
      { "USA East", "Hardware", 2, 500 },
      { "USA East", "Personal", 1, 8 },
   };

   // brushing one USA West state on another chart
   private static final Object[][] WEST_BRUSHED_ROWS = {
      { "Region", "Category", "Qty", "Price" },
      { "USA West", "Games", 1, 5 },
      { "USA West", "Hardware", 2, 300 },
      { "USA West", "Personal", 1, 2 },
   };

   @Test
   void eastBrush_allDataLeavesKeepAllDataRowSizeAndLabel() {
      checkAllDataLeaves(EAST_BRUSHED_ROWS);
   }

   @Test
   void westBrush_allDataLeavesKeepAllDataRowSizeAndLabel() {
      checkAllDataLeaves(WEST_BRUSHED_ROWS);
   }

   @Test
   void eastBrush_brushLeavesUseBrushedRows() {
      checkBrushLeaves(EAST_BRUSHED_ROWS, "USA East");
   }

   /**
    * The leaves' first displayed parent (USA East) has no brushed rows, so the brush element's
    * leaves must not take USA East's all-data rows.
    */
   @Test
   void westBrush_brushLeavesUseBrushedRows() {
      checkBrushLeaves(WEST_BRUSHED_ROWS, "USA West");
   }

   /**
    * Roots are auto-sized from their children and keep their pre-existing representative
    * row, the first row of the root in the sorted data, which is a brushed row here.
    */
   @Test
   void eastBrush_allDataRootKeepsFirstRow() {
      BrushDataSet data = brushDataSet(EAST_BRUSHED_ROWS);
      Graphs graphs = createBrushGraph(data);
      RelationGeometry east = findNode(graphs.ggraph, graphs.all, "Region", "USA East");

      assertEquals(0, east.getSubRowIndex());
   }

   private void checkAllDataLeaves(Object[][] brushedRows) {
      BrushDataSet data = brushDataSet(brushedRows);
      Graphs graphs = createBrushGraph(data);
      DefaultDataSet unbrushedData = new DefaultDataSet(ALL_ROWS);
      RelationElement unbrushedElem = createElement(unbrushedData, "Qty", "Price", "Price");
      GGraph unbrushed = createGGraph(unbrushedData, unbrushedElem);

      for(String category : CATEGORIES) {
         RelationGeometry leaf = findNode(graphs.ggraph, graphs.all, "Category", category);
         RelationGeometry expected = findNode(unbrushed, unbrushedElem, "Category", category);
         int sub = leaf.getSubRowIndex();

         assertTrue(sub >= data.getBaseRowCount(),
                    category + " should use an all-data row, got brushed row " + sub);
         assertEquals(category, data.getData("Category", sub));
         assertEquals(expected.getSize(), leaf.getSize(), 0.001, category + " size");
         assertNotNull(leaf.getText(), category + " label");
         assertEquals(String.valueOf(expected.getText()), String.valueOf(leaf.getText()),
                      category + " label");
      }
   }

   private void checkBrushLeaves(Object[][] brushedRows, String brushedRegion) {
      BrushDataSet data = brushDataSet(brushedRows);
      Graphs graphs = createBrushGraph(data);

      for(String category : CATEGORIES) {
         RelationGeometry leaf = findNode(graphs.ggraph, graphs.brush, "Category", category);
         int sub = leaf.getSubRowIndex();

         assertTrue(sub < data.getBaseRowCount(),
                    category + " should use a brushed row, got all-data row " + sub);
         assertEquals(category, data.getData("Category", sub));
         assertEquals(brushedRegion, data.getData("Region", sub));
         assertNotNull(data.getData("Qty", sub));
      }
   }

   private static BrushDataSet brushDataSet(Object[][] brushedRows) {
      return new BrushDataSet(new DefaultDataSet(ALL_ROWS), new DefaultDataSet(brushedRows));
   }

   /**
    * Create the all-data and brush elements the way GraphGenerator does for a brushed
    * relation chart with an aggregate edge size: the all-data element reads its edge size,
    * node size and label from the __all__ columns, and the brush element reads its edge size
    * from the base measure and its node size from the __all__ column.
    */
   private static Graphs createBrushGraph(BrushDataSet data) {
      RelationElement all = createElement(data, ALL + "Qty", ALL + "Price", ALL + "Price");
      RelationElement brush = createElement(data, "Qty", ALL + "Price", null);
      brush.setHint("overlay", all);
      all.setHint("overlaid", brush);

      return new Graphs(createGGraph(data, all, brush), all, brush);
   }

   private static RelationElement createElement(DataSet data, String edgeSize,
                                                String nodeSize, String text)
   {
      RelationElement element = new RelationElement("Region", "Category");
      element.setSizeFrame(linearSizeFrame(data, edgeSize));
      element.setNodeSizeFrame(linearSizeFrame(data, nodeSize));
      element.setNodeColorFrame(new CategoricalColorFrame("Category"));

      if(text != null) {
         element.setTextFrame(new DefaultTextFrame(text));
      }

      return element;
   }

   private static LinearSizeFrame linearSizeFrame(DataSet data, String field) {
      LinearScale scale = new LinearScale(field);
      scale.init(data);
      LinearSizeFrame frame = new LinearSizeFrame(field);
      frame.setScale(scale);
      return frame;
   }

   private static GGraph createGGraph(DataSet data, RelationElement... elements) {
      CategoricalScale sourceScale = new CategoricalScale("Region");
      sourceScale.init(data);
      CategoricalScale targetScale = new CategoricalScale("Category");
      targetScale.init(data);

      EGraph egraph = new EGraph();

      for(RelationElement element : elements) {
         egraph.addElement(element);
      }

      RectCoord coord = new RectCoord(sourceScale, targetScale);
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, data);
   }

   private static RelationGeometry findNode(GGraph ggraph, RelationElement elem, String var,
                                            String value)
   {
      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         if(ggraph.getGeometry(i) instanceof RelationGeometry rgeom &&
            rgeom.getElement() == elem && var.equals(rgeom.getVar()) &&
            value.equals(String.valueOf(rgeom.getMxCell().getValue())))
         {
            return rgeom;
         }
      }

      throw new AssertionError("No " + var + " node found for value=" + value);
   }

   private record Graphs(GGraph ggraph, RelationElement all, RelationElement brush) {
   }
}
