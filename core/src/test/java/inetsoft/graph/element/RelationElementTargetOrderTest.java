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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #78457: a relation (tree) chart root node takes its size, color,
 * label and tooltip from its "representative row" ({@link RelationGeometry#getSubRowIndex()}),
 * which must be its first row in the [source, target] sorted (display) order. The sort uses
 * the dataset's own comparators (sort by value, descending, original order for brushing),
 * so the root must not be re-ranked by the natural order of the target values (#76417
 * regression).
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationElementTargetOrderTest {
   // deliberately not sorted by region, state or value
   private static final Object[][] ROWS = {
      { "Region", "State", "Paid" },
      { "USA West", "TX", 80 },
      { "USA East", "NY", 70 },
      { "USA East", "CT", 10 },
      { "USA West", "AZ", 15 },
      { "USA East", "NJ", 100 },
      { "USA West", "CA", 90 },
      { "USA East", "MA", 60 },
   };

   @Test
   void targetSortedByValueDesc_rootUsesFirstDisplayedChild_noColor() {
      checkValueDescOrder(null, null);
   }

   @Test
   void targetSortedByValueDesc_rootUsesFirstDisplayedChild_withColor() {
      checkValueDescOrder(new CategoricalColorFrame("Region"),
                          new CategoricalColorFrame("State"));
   }

   @Test
   void targetSortedAlphaDesc_rootUsesFirstDisplayedChild() {
      DefaultDataSet data = new DefaultDataSet(ROWS);
      data.setComparator("State", Comparator.reverseOrder());
      GGraph ggraph = createGraph(data, "Region", "State", null, null, "Paid");

      assertEquals("NY", data.getData("State", subRow(ggraph, "Region", "USA East")));
      assertEquals("TX", data.getData("State", subRow(ggraph, "Region", "USA West")));
   }

   @Test
   void naturalOrder_rootUsesNaturalFirstChild() {
      DefaultDataSet data = new DefaultDataSet(ROWS);
      GGraph ggraph = createGraph(data, "Region", "State", null, null, "Paid");

      assertEquals("CT", data.getData("State", subRow(ggraph, "Region", "USA East")));
      assertEquals("AZ", data.getData("State", subRow(ggraph, "Region", "USA West")));
   }

   /**
    * The color tie-break from #76417 still applies, but only among rows of the first target
    * in display order, not the natural-order smallest target.
    */
   @Test
   void colorTieBreak_onlyAmongFirstDisplayedTarget() {
      Object[][] rows = {
         { "Root", "Child", "Q" },
         { "R", "B", "q1" },
         { "R", "A", "q2" },
         { "R", "B", "q3" },
         { "R", "A", "q9" },
      };

      DefaultDataSet natural = new DefaultDataSet(rows);
      GGraph ggraph = createGraph(natural, "Root", "Child",
                                  new CategoricalColorFrame("Q"), null, null);
      int row = subRow(ggraph, "Root", "R");
      assertEquals("A", natural.getData("Child", row));
      assertEquals("q9", natural.getData("Q", row));

      DefaultDataSet desc = new DefaultDataSet(rows);
      desc.setComparator("Child", Comparator.reverseOrder());
      ggraph = createGraph(desc, "Root", "Child", new CategoricalColorFrame("Q"), null, null);
      row = subRow(ggraph, "Root", "R");
      assertEquals("B", desc.getData("Child", row));
      assertEquals("q3", desc.getData("Q", row));
   }

   /**
    * A value comparator ties distinct targets with equal values. A different target must not
    * take part in the color tie-break just because the target comparator returns 0.
    */
   @Test
   void colorTieBreak_ignoresDistinctTargetsWithEqualValues() {
      Object[][] rows = {
         { "Root", "Child", "Paid", "Q" },
         { "R", "A", 10, "q1" },
         { "R", "B", 10, "q9" },
      };

      DefaultDataSet data = new DefaultDataSet(rows);
      data.setComparator("Child", valueDesc(data, "Child", "Paid"));
      GGraph ggraph = createGraph(data, "Root", "Child",
                                  new CategoricalColorFrame("Q"), null, null);
      int row = subRow(ggraph, "Root", "R");

      assertEquals("A", data.getData("Child", row));
      assertEquals("q1", data.getData("Q", row));
   }

   /**
    * Brushed charts sort dimensions by the original (query) order when there is no sort,
    * see BrushDataSet.getComparator0(), so the root must follow that order too.
    */
   @Test
   void brushDataSet_rootFollowsOriginalOrder() {
      DefaultDataSet adata = new DefaultDataSet(ROWS);
      DefaultDataSet bdata = new DefaultDataSet(new Object[][] {
         { "Region", "State", "Paid" },
         { "USA East", "CT", 10 },
         { "USA East", "NJ", 100 },
      });
      BrushDataSet data = new BrushDataSet(adata, bdata);
      GGraph ggraph = createGraph(data, "Region", "State", null, null,
                                  BrushDataSet.ALL_HEADER_PREFIX + "Paid");

      assertEquals("NY", data.getData("State", subRow(ggraph, "Region", "USA East")));
      assertEquals("TX", data.getData("State", subRow(ggraph, "Region", "USA West")));
   }

   /**
    * When the brushed rows hold the natural-order smallest targets, their brushed copies
    * (with a null _all_ size) must not become the root's row. The root keeps the size of
    * its first displayed child, as in the brush regression run's baseline.
    */
   @Test
   void brushDataSet_brushedNaturalFirstTargets_rootKeepsFirstDisplayedChildSize() {
      DefaultDataSet adata = new DefaultDataSet(ROWS);
      DefaultDataSet bdata = new DefaultDataSet(new Object[][] {
         { "Region", "State", "Paid" },
         { "USA East", "CT", 10 },
         { "USA West", "AZ", 15 },
      });
      BrushDataSet data = new BrushDataSet(adata, bdata);
      GGraph ggraph = createGraph(data, "Region", "State",
                                  new CategoricalColorFrame("Region"),
                                  new CategoricalColorFrame("State"),
                                  BrushDataSet.ALL_HEADER_PREFIX + "Paid");

      RelationGeometry east = findNode(ggraph, "Region", "USA East");
      RelationGeometry west = findNode(ggraph, "Region", "USA West");

      assertEquals("NY", data.getData("State", east.getSubRowIndex()));
      assertEquals("TX", data.getData("State", west.getSubRowIndex()));
      assertEquals(findNode(ggraph, "State", "NY").getSize(), east.getSize(), 0.001);
      assertEquals(findNode(ggraph, "State", "TX").getSize(), west.getSize(), 0.001);
   }

   private void checkValueDescOrder(ColorFrame color, ColorFrame nodeColor) {
      DefaultDataSet data = new DefaultDataSet(ROWS);
      data.setComparator("State", valueDesc(data, "State", "Paid"));
      GGraph ggraph = createGraph(data, "Region", "State", color, nodeColor, "Paid");

      RelationGeometry east = findNode(ggraph, "Region", "USA East");
      RelationGeometry west = findNode(ggraph, "Region", "USA West");

      assertEquals("NJ", data.getData("State", east.getSubRowIndex()));
      assertEquals("CA", data.getData("State", west.getSubRowIndex()));
      // the root is drawn the same size as its first displayed child
      assertEquals(findNode(ggraph, "State", "NJ").getSize(), east.getSize(), 0.001);
      assertEquals(findNode(ggraph, "State", "CA").getSize(), west.getSize(), 0.001);
      assertTrue(east.getSize() > findNode(ggraph, "State", "CT").getSize());
   }

   private static Comparator<Object> valueDesc(DataSet data, String dim, String measure) {
      Map<Object, Double> values = new HashMap<>();

      for(int i = 0; i < data.getRowCount(); i++) {
         values.put(data.getData(dim, i), ((Number) data.getData(measure, i)).doubleValue());
      }

      return (a, b) -> Double.compare(values.get(b), values.get(a));
   }

   private static GGraph createGraph(DataSet data, String source, String target,
                                     ColorFrame color, ColorFrame nodeColor, String nodeSize)
   {
      CategoricalScale sourceScale = new CategoricalScale(source);
      sourceScale.init(data);
      CategoricalScale targetScale = new CategoricalScale(target);
      targetScale.init(data);

      RelationElement element = new RelationElement(source, target);
      element.setSizeFrame(new StaticSizeFrame());

      if(nodeSize != null) {
         LinearScale sizeScale = new LinearScale(nodeSize);
         sizeScale.init(data);
         LinearSizeFrame sizeFrame = new LinearSizeFrame(nodeSize);
         sizeFrame.setScale(sizeScale);
         element.setNodeSizeFrame(sizeFrame);
      }
      else {
         element.setNodeSizeFrame(new StaticSizeFrame());
      }

      if(color != null) {
         element.setColorFrame(color);
      }

      if(nodeColor != null) {
         element.setNodeColorFrame(nodeColor);
      }

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RectCoord coord = new RectCoord(sourceScale, targetScale);
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, data);
   }

   private static int subRow(GGraph ggraph, String var, String value) {
      return findNode(ggraph, var, value).getSubRowIndex();
   }

   private static RelationGeometry findNode(GGraph ggraph, String var, String value) {
      for(int i = 0; i < ggraph.getGeometryCount(); i++) {
         if(ggraph.getGeometry(i) instanceof RelationGeometry rgeom &&
            var.equals(rgeom.getVar()) &&
            value.equals(String.valueOf(rgeom.getMxCell().getValue())))
         {
            return rgeom;
         }
      }

      throw new AssertionError("No " + var + " node found for value=" + value);
   }
}
