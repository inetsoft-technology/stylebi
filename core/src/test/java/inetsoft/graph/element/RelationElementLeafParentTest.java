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
import inetsoft.test.BaseTestConfiguration;
import inetsoft.test.ConfigurationContextInitializer;
import inetsoft.test.SreeHome;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for Bug #78519: a relation (tree/network) leaf node with several parents
 * is drawn under its first displayed parent, so its "representative row"
 * ({@link RelationGeometry#getSubRowIndex()}), which drives its color, size and label, must
 * be a row under that parent. The Node Color tie-break of #76417 ("smallest wins") may only
 * choose among rows of that one [source, target] group, never across parents.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BaseTestConfiguration.class,
                      initializers = ConfigurationContextInitializer.class)
@SreeHome
@Tag("core")
class RelationElementLeafParentTest {
   // CA, NY and WA have several parents. Code decreases as Month increases.
   private static final Object[][] ROWS = {
      { "Month", "State", "Paid", "Code" },
      { "2019-10", "CA", 18, "c3" },
      { "2019-10", "NY", 12, "c3" },
      { "2019-10", "TX", 20, "c3" },
      { "2019-11", "CA", 9, "c2" },
      { "2019-11", "NY", 16, "c2" },
      { "2019-11", "WA", 10, "c2" },
      { "2019-12", "CA", 20, "c1" },
      { "2019-12", "WA", 10, "c1" },
      { "2019-12", "OR", 8, "c1" },
   };

   /**
    * Node Color bound to the source (Month). The first displayed parent of CA and NY is
    * 2019-10, which does not hold the largest Month value, so the leaves must not take the
    * 2019-12/2019-11 rows.
    */
   @Test
   void nodeColorOnSource_leafUsesFirstDisplayedParentRow() {
      DefaultDataSet data = new DefaultDataSet(ROWS);
      GGraph ggraph = createGraph(data, "Month");

      assertLeafRow(ggraph, data, "CA", 0);
      assertLeafRow(ggraph, data, "NY", 1);
      assertLeafRow(ggraph, data, "TX", 2);
      assertLeafRow(ggraph, data, "WA", 5);
      assertLeafRow(ggraph, data, "OR", 8);

      // a leaf is painted like the parent it is drawn under
      Object oct = findNode(ggraph, "Month", "2019-10").getColor();
      assertEquals(oct, findNode(ggraph, "State", "CA").getColor());
      assertEquals(oct, findNode(ggraph, "State", "NY").getColor());
      assertEquals(findNode(ggraph, "Month", "2019-11").getColor(),
                   findNode(ggraph, "State", "WA").getColor());
   }

   /**
    * Months displayed in descending order: the first displayed parent is now 2019-12, and
    * Node Color is bound to Code, whose largest value is under 2019-10.
    */
   @Test
   void sourceSortedDesc_leafUsesFirstDisplayedParentRow() {
      DefaultDataSet data = new DefaultDataSet(ROWS);
      data.setComparator("Month", Comparator.reverseOrder());
      GGraph ggraph = createGraph(data, "Code");

      assertLeafRow(ggraph, data, "CA", 6);
      assertLeafRow(ggraph, data, "WA", 7);
      assertLeafRow(ggraph, data, "NY", 4);
      assertLeafRow(ggraph, data, "TX", 2);
   }

   /**
    * Rows tied on [source, target] under the first parent are still resolved by the smallest
    * Node Color value (#76417, #78519), but a smaller value under another parent is not
    * eligible.
    */
   @Test
   void tieWithinFirstParent_smallestNodeColorWinsOnlyInsideThatParent() {
      Object[][] rows = {
         { "Month", "State", "Paid", "Code" },
         { "2019-10", "CA", 18, "c2" },
         { "2019-10", "CA", 12, "c1" },
         { "2019-11", "CA", 9, "c0" },
      };
      DefaultDataSet data = new DefaultDataSet(rows);
      GGraph ggraph = createGraph(data, "Code");

      assertLeafRow(ggraph, data, "CA", 1);
   }

   private static void assertLeafRow(GGraph ggraph, DataSet data, String state, int row) {
      RelationGeometry leaf = findNode(ggraph, "State", state);
      RelationElement elem = (RelationElement) leaf.getElement();

      assertEquals(row, leaf.getSubRowIndex(), "representative row of " + state);
      assertEquals(elem.getNodeSizeFrame().getSize(data, "Paid", row), leaf.getSize(), 0.001,
                   "size of " + state);
      assertEquals(elem.getNodeColorFrame().getColor(data, "State", row), leaf.getColor(),
                   "color of " + state);
   }

   private static GGraph createGraph(DataSet data, String nodeColorField) {
      CategoricalScale sourceScale = new CategoricalScale("Month");
      sourceScale.init(data);
      CategoricalScale targetScale = new CategoricalScale("State");
      targetScale.init(data);

      RelationElement element = new RelationElement("Month", "State");
      element.setSizeFrame(new StaticSizeFrame());

      LinearScale sizeScale = new LinearScale("Paid");
      sizeScale.init(data);
      LinearSizeFrame sizeFrame = new LinearSizeFrame("Paid");
      sizeFrame.setScale(sizeScale);
      element.setNodeSizeFrame(sizeFrame);

      CategoricalScale colorScale = new CategoricalScale(nodeColorField);
      colorScale.init(data);
      CategoricalColorFrame colorFrame = new CategoricalColorFrame(nodeColorField);
      colorFrame.setScale(colorScale);
      element.setNodeColorFrame(colorFrame);

      EGraph egraph = new EGraph();
      egraph.addElement(element);
      RectCoord coord = new RectCoord(sourceScale, targetScale);
      egraph.setCoordinate(coord);
      return egraph.createGGraph(coord, data);
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
